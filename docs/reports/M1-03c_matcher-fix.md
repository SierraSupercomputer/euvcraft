# M1-03c: Fix StructureMatcher for EAST and WEST

- Date: 2026-10-15
- Worktree: `~/.local/share/opencode/worktree/fef470/m1-03c-matcher-fix`
- Base: `master` @ `2137c62`
- Commands: `.\gradlew.bat test --offline`, `.\gradlew.bat checkCoreImports --offline`
- Scope: `com.euvcraft.lithmod.core.multiblock` only. No Minecraft types.

## 1. Verdict

**The matcher was the bug, and the tests were worse — they could not have caught it.**

Both sides were changed, for different reasons:

- `StructureMatcher` computed the box with four hand-written `switch` cases that were wrong for
  **all four** facings. Rewritten to evaluate the documented vector definition.
- `StructureMatcherTest` derived its expected world by calling its own private copies of the
  matcher's own `switch` formulas. That is a tautological oracle: it agreed with the matcher by
  construction, so it could only ever detect *disagreement* between two hand-written copies of the
  same formula, never a wrong formula. Replaced with an independent oracle derived from the
  definition.

No expectation was changed to make a test pass. The new expectations are computed from the geometry,
and the old matcher fails them.

## 2. Geometry, derived independently

For facing `f`, with `up = +Y`:

- `back = -f`
- `right = f` rotated 90 degrees clockwise seen from above. Looking down with north up and east
  right, clockwise runs `NORTH -> EAST -> SOUTH -> WEST -> NORTH`. On the XZ plane that maps a step
  `(x, z)` to `(-z, x)`.
- The box extends from the controller's face along `back` for length 3, along `right` for width 2
  and along `up` for height 2.
- Origin is `controller - u0*right - v0*up`; cell `(u, v, d)` is `origin + u*right + v*up + d*back`,
  for `u,v in {0,1}` and `d in {0,1,2}`.

Note `d = 0` is the controller's own cell, so the box always contains the controller.

### 2.1 The basis, tabulated

| facing | `f` | `back` | `right` |
|--------|-----|--------|---------|
| NORTH | (0, 0, −1) | (0, 0, +1) | (+1, 0, 0) |
| SOUTH | (0, 0, +1) | (0, 0, −1) | (−1, 0, 0) |
| EAST | (+1, 0, 0) | (−1, 0, 0) | (0, 0, +1) |
| WEST | (−1, 0, 0) | (+1, 0, 0) | (0, 0, −1) |

### 2.2 Resulting box extents, relative to the controller

Each cell set is the axis-aligned bounding interval on each axis, for controller at the origin.

| facing | x | y | z |
|--------|---|---|---|
| NORTH | `[−u0, 1−u0]` | `[−v0, 1−v0]` | `[0, 2]` |
| SOUTH | `[u0−1, u0]` | `[−v0, 1−v0]` | `[−2, 0]` |
| EAST | `[−2, 0]` | `[−v0, 1−v0]` | `[−u0, 1−u0]` |
| WEST | `[0, 2]` | `[−v0, 1−v0]` | `[u0−1, u0]` |

The invariant that makes the table trustworthy: **the length-3 extent always runs from the controller
in direction `−f`**, i.e. always behind the controller's face, never in front of it, and `u0` always
displaces the box along the axis perpendicular to `f`.

### 2.3 Literal world bounds, controller at (10, 5, 10), offset (u0, v0) = (1, 1)

This table is asserted verbatim in `match_returnsExactlyTheDocumentedBoundingBox`.

| facing | x min..max | y min..max | z min..max |
|--------|-----------|-----------|-----------|
| NORTH | 9..10 | 4..5 | 10..12 |
| SOUTH | 10..11 | 4..5 | 8..10 |
| EAST | 8..10 | 4..5 | 9..10 |
| WEST | 10..12 | 4..5 | 10..11 |

## 3. What was actually broken

### 3.1 The matcher, before

`calculateStructureOrigin` plus a fixed `getControllerLocalPos` produced these boxes, relative to a
controller at the origin, against the correct box from section 2.2:

| facing | old box | correct box | defect |
|--------|---------|-------------|--------|
| NORTH | x `[−u0,1−u0]`, z `[−2,0]` | x `[−u0,1−u0]`, z `[0,2]` | length runs the **wrong way**: in front of a north-facing controller |
| SOUTH | x `[−u0,1−u0]`, z `[0,2]` | x `[u0−1,u0]`, z `[−2,0]` | wrong length direction **and** the `u0` displacement is not mirrored |
| EAST | x `[0,1]`, z `[0,2]` | x `[−2,0]`, z `[−u0,1−u0]` | width and length are on the **wrong axes**; `u0` is ignored entirely |
| WEST | x `[−2,−1]`, z `[−2,0]` | x `[0,2]`, z `[u0−1,u0]` | width and length on the wrong axes; box lands on the controller's west side |

EAST and WEST also had two mutually inconsistent helpers: the matcher mapped the controller to local
`(0, v0, u0)` for EAST and `(2, v0, u0)` for WEST, i.e. it put the controller on the front face of a
north-south-oriented box. That is why EAST and WEST failed loudly while NORTH and SOUTH failed
silently — the loud failures came from the matcher contradicting itself, not from the tests detecting
the geometry.

### 3.2 The tests, before

`StructureMatcherTest` had its own `perfectStructure`, `calculateOrigin` and `getControllerLocalPos`
helpers that re-implemented the matcher's four `switch` cases. Consequences:

- NORTH and SOUTH passed while **both** the test and the matcher placed the box in front of the
  controller. A shared error is invisible to a duplicated oracle.
- The test's `getControllerLocalPos` returned `(offsetX, offsetY, 0)` for EAST and
  `(offsetX, offsetY, length−1)` for WEST, where the matcher returned `(0, offsetY, offsetX)` and
  `(length−1, offsetY, offsetX)`. The two disagreed, which is what surfaced as 5 failures.

The tests were not wrong about the spec; they were structurally incapable of testing it.

## 4. The fix

`StructureMatcher` no longer contains any per-facing arithmetic. It evaluates
`controller + (u−u0)*right + (v−v0)*up + d*back`, with the basis owned by
`MultiblockDirection`:

- `MultiblockDirection` now carries `vector()`, `back()` (`= −vector()`), `right()` (`= (−z, 0, x)`)
  and `up()` (`= +Y`).
- The four old `switch` cases in `calculateStructureOrigin` and `getControllerLocalPos` are gone, so
  a fifth facing could not be added with the axis mix-ups the old ones carried.

Public API is unchanged: `match(controllerPos, facing, shape, blockKindAt)` returning `MatchResult`,
with the same member ordering `(u, v, d)` and the same failure strings. Iteration order and the
first-match-wins offset search are unchanged, so the result is deterministic as before.

### 4.1 One test-side clarification, stated rather than papered over

`match()` tries all four front-face offsets and reports the failure of the **first offset it tried**,
`(0, 0)`. For a defect injected into the box of a *later* offset, the reported reason belongs to an
earlier, differently placed box. The tests therefore pin the exact reason only at offset `(0, 0)`,
and require every offset not to form. This is asserted in `assertRejects`, whose javadoc says so.
No reason string was loosened: the `(0, 0)` assertions are exact substring matches, and they failed
during development until the matcher was fixed.

## 5. Verification

### 5.1 Before

```
.\gradlew.bat test --tests "com.euvcraft.lithmod.core.multiblock.StructureMatcherTest" --offline
...
26 tests completed, 5 failed

match_perfectStructure_allFacings [3] EAST FAILED
  facing=EAST offset=(1,0): wrong block at [10, 5, 12]: expected structure block
match_perfectStructure_allFacings [4] WEST FAILED
  facing=WEST offset=(0,0): wrong block at [8, 5, 8]: expected structure block
match_threePowerPorts_allFacings [4] WEST FAILED
match_extraController_allFacings  [4] WEST FAILED
match_noPowerPorts_allFacings     [4] WEST FAILED
```

### 5.2 After

```
.\gradlew.bat test checkCoreImports --offline --rerun-tasks
BUILD SUCCESSFUL

> Task :checkCoreImports
Core import check passed: no Minecraft/NeoForge imports found in core package.
```

### 5.3 Test table — `StructureMatcherTest`, sums to 45 cases

Every method except `match_isDeterministic` is `@ParameterizedTest @EnumSource(MultiblockDirection.class)`,
so it runs once per facing. Methods marked "x4 offsets" additionally loop over all four controller
positions inside the test, so each case covers 4 placements.

| # | test method | cases | pass | fail | placements asserted |
|---|-------------|-------|------|------|----------------------|
| 1 | `match_formsForEveryFacingAndControllerOffset` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 2 | `match_returnsExactlyTheDocumentedBoundingBox` | 4 | 4 | 0 | 4 facings x offset (1,1) = 4 |
| 3 | `match_acceptsExactlyOnePowerPort` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 4 | `match_acceptsExactlyTwoPowerPorts` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 5 | `match_rejectsForeignBlock` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 6 | `match_rejectsExtraController` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 7 | `match_rejectsNoPowerPorts` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 8 | `match_rejectsThreePowerPorts` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 9 | `match_rejectsCompleteBoxBuiltInFrontOfController` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 10 | `expectedBox_containsTheControllerCell` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 11 | `expectedBox_hasNoDuplicateCells` | 4 | 4 | 0 | 4 facings x 4 offsets = 16 |
| 12 | `match_isDeterministic` | 1 | 1 | 0 | 1 |
| | **sum** | **45** | **45** | **0** | 161 placements |

### 5.4 Whole unit suite

| | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `StructureMatcherTest` | 1 | 45 | 0 | 0 | 0 |
| all other test classes | 48 | 260 | 0 | 0 | 0 |
| **total** | **49** | **305** | **0** | **0** | **0** |

### 5.5 Mutation check — do the new tests actually bite?

Two throwaway mutations of the production basis, reverted after each:

| mutation | result | which tests caught it |
|----------|--------|-----------------------|
| `back()` returns `vector()` (structure extends in front) | 45 run, **28 failed** | `formsForEveryFacingAndControllerOffset`, `returnsExactlyTheDocumentedBoundingBox`, `acceptsExactlyOnePowerPort`, `acceptsExactlyTwoPowerPorts`, `rejectsExtraController`, `rejectsNoPowerPorts`, `rejectsThreePowerPorts` — all 4 facings |
| `right()` rotated counter-clockwise `(z, 0, −x)` | 45 run, **8 failed** | `rejectsNoPowerPorts`, `rejectsThreePowerPorts` — all 4 facings |

Two things worth recording from this:

- The first mutation is caught on **NORTH and SOUTH too**, which is the concrete proof that the old
  NORTH/SOUTH green was wrong for the wrong reason.
- The counter-clockwise rotation is *not* caught by the bounding-box test, and that is inherent, not a
  test weakness. For a width-2 box, mirroring `right` maps offset `u0` to `1−u0` and yields the same
  12-cell set, so rotation handedness is unobservable from the member set alone. The port layout is
  what supplies the power here: `PORT_A` sits at `u=0` and `PORT_C` at `u=1`, so a mirrored `right`
  moves the ports relative to the box and the port counts change. `match_rejectsCompleteBoxBuiltInFrontOfController`
  also did not catch the first mutation, since it asserts rejection, which holds on either side.

## 6. Deviations from the brief

| brief | what was done | why |
|-------|---------------|-----|
| "fix the matcher or the tests, whichever is wrong" | fixed both | the matcher contradicted the spec; the tests could not express the spec. Fixing only the tests would have kept a wrong matcher behind a new, equally tautological oracle. |
| "never change an expectation just to make a test pass" | no expectation changed | the `(0, 0)`-only reason pinning is a narrowing of *which offset's reason* is asserted, documented in `assertRejects`, not a loosening of any reason string. |

## 7. Not verified / handoff

| item | note |
|------|------|
| in-game formation changes for **all four** facings | `ProcessCellControllerBlockEntity` consumes `StructureMatcher` and uses `memberPositions` to set `FORMED`. A process cell now forms behind its controller for NORTH and SOUTH as well as EAST and WEST. The MC layer is not in this worktree and was not compiled or run here, so its game tests from M1-03b are stale against this change and must be re-run. |
| unreachable matcher branches | `"missing block at ..."` cannot fire: `BlockKind.OTHER` returns earlier and is the only non-casing kind. `"expected exactly one controller"` cannot fire either: the controller's own cell always yields `CONTROLLER`, so the count is >= 1, and a second one is caught as `"extra controller"` first. Both were left in place as defensive code; distinguishing air from a foreign block would need a new `BlockKind` and a change to the `blockKindAt` contract, which is out of scope here. |
| `failureReason` selection policy | still reports the first offset's failure, which is often not the most informative one. Left unchanged deliberately; worth revisiting separately. |
| only `test` and `checkCoreImports` run | `runGameTestServer` was not run; the MC layer is absent from this worktree. |