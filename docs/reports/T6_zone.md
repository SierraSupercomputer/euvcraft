# T6 zone — structure matcher tests and report

Task: `build/t6/zone-tests/BRIEF.txt` (worker scope `build/t6/zone-tests/SCOPE.json`).
Constraint honored: no commands, no Git operations, no network, no delegation. No
execution was run and none is claimed.

## Files touched

| File | Change |
| --- | --- |
| `src/main/java/com/euvcraft/lithmod/core/multiblock/ZoneStructureMatcher.java` | **Unchanged.** Read in full; no bug found, so no narrow fix was needed. |
| `src/test/java/com/euvcraft/lithmod/core/multiblock/ZoneStructureMatcherTest.java` | Extended. A prior draft (from the timed-out worker) already covered the core case matrix; four gap-filling tests and helpers were appended. No existing test was rewritten or removed. |
| `docs/reports/T6_zone.md` | Created (this report). |

## Matcher review (read-only)

Read in full along with its framework dependencies: `StructureShape`, `MatchResult`,
`MultiblockPos`, `MultiblockDirection`, `BlockKind`. Verified behavior:

- Shape fixed at 3 wide x 3 tall x 5 deep = 45 cells, fully filled; anchor search is
  deterministic: `u0 = 0, 1, 2` with `v0 = 0`, first full match wins.
- Controller must be `CONTROLLER` at the anchor cell `(u0, 0, 0)`; anchor sits on the
  front face (`d = 0`), no mirror bug.
- Exactly one `OPERATING_PANEL`, reserved at `(u0, 1, 0)` — directly above the controller.
  `CASING` there is rejected by the final count check (missing panel).
- Exactly one `WATER_INLET` and one or two `POWER_PORT` anywhere else in the volume; all
  remaining cells `CASING`. Non-reserved positions refuse extra controllers, misplaced
  panels, extra/missing inlets, 0/3+ ports, `OTHER`, and zone-foreign kinds.
- Member positions are returned in canonical `u*(h*l) + v*l + d` order (45 members).

No defect was identified (boundary indices, anchor determinism, count checks, and the
facing basis `right()/back()/up()` were all re-derived from source). One observed, accepted
nuance (not a bug): `match()` reports the failure reason of the *first* anchor column
attempted rather than the most specific one.

## Purity (verified statically)

`grep` over `src/main/java/com/euvcraft/lithmod/core/multiblock`: no `net.minecraft.*`,
no `com.mojang.*`, no `com.euvcraft.lithmod.mc.*` imports. The new reflection-based test
`matcherStaysPureCoreJava` asserts this at runtime: superclass is `Object`, and every
declared non-synthetic field/method has only JDK (`java.*`) or core-package types.

## Test coverage matrix (brief requirement -> test)

| Requirement | Test |
| --- | --- |
| Accepts all 4 facings / all 3 front columns; 45 unique ordered members | `everyFacingAndFrontColumnAcceptsAndEveryMissingCellRejects` |
| Missing cells refused per cell (also covers OTHER blocks) | same (each cell replaced by `OTHER`) |
| Exactly one inlet anywhere except controller/panel; 1–2 power ports anywhere | `exactlyOneInletAndOneOrTwoPortsAreAdmitted`, `inletAndPortsMaySitOnAnyNonReservedCell` (new) |
| 0 or 3 power ports refused | `exactlyOneInletAndOneOrTwoPortsAreAdmitted` |
| 0 or 2 inlets refused / inlet may not replace controller or panel | `exactlyOneInletAndOneOrTwoPortsAreAdmitted`, `inletAndPanelPositionsAreReserved` |
| Required panel directly above; missing / extra / misplaced panel refused | `inletAndPanelPositionsAreReserved`, `extraControllerPanelInletAndForeignKindsAreRefused` |
| Extra controller, foreign kinds refused | `extraControllerPanelInletAndForeignKindsAreRefused` |
| Wrong height / depth / width refused | `wrongDimensionsAreRefused` |
| Structure is facing-specific (built for facing `f`, refused under any other facing; failure reason reported non-blank) | `structureBuiltForOneFacingIsRefusedUnderAnyOtherFacing` (new) |
| Null controller/facing/reporter rejected (`NullPointerException`) | `nullArgumentsAreRejected` (new) |
| Pure core, no Minecraft leakage | `matcherStaysPureCoreJava` (new) |

## Zone package inventory (read-only; not modified in this task)

Core pure matcher: `src/main/java/com/euvcraft/lithmod/core/multiblock/ZoneStructureMatcher.java`.

`src/main/java/com/euvcraft/lithmod/mc/multiblock/zone/` (Minecraft side, untouched):
`ZoneClientEvents.java`, `ZoneControllerBlock.java`, `ZoneControllerBlockEntity.java`,
`ZoneFluidHandler.java`, `ZoneMachines.java`, `ZoneMemberBlock.java`,
`ZoneMemberBlockEntity.java`, `ZoneOperatingPanelMenu.java`,
`ZoneOperatingPanelScreen.java`, `ZoneStatusMenu.java`, `ZoneStatusScreen.java`.

## Limitations / known omissions (no execution claims)

- **Nothing was executed.** The brief forbade commands, so no `gradle test`/compile run
  and no pass counts are claimed. Tests were verified by reading the framework sources
  (`StructureShape`, `MatchResult`, `MultiblockPos`, `MultiblockDirection`, `BlockKind`)
  and by manual trace of each fixture against the matcher's iteration order; the
  coordinator must run the JUnit suite to confirm.
- The existing draft tests were kept as-is after line-by-line re-derivation; only the
  four gap-filling tests plus the `isCoreJvmType` helper were appended.
- Member-order assertion (`ArrayList` of `LinkedHashMap` key set vs. matcher output) is
  exercised for the one-port fixture and uniqueness for the relocated inlet / two-port
  fixtures; exhaustive order assertions for every acceptance variant are not present.
- The matcher's accepted nuance (first-anchor failure reason surfaces on multi-anchor
  misses) is documented, not asserted against a specific reason string, to keep tests
  decoupled from message wording.
