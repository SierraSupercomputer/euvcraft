# M1-03d: git proof and concerns for review

- Date: 2026-10-16
- Branch: `m1-03d-multiblock-blocks`
- Worktree: `~/.local/share/opencode/worktree/fef470/m1-03d-multiblock-blocks`
- Full task report: [`M1-03_multiblock-framework_r3.md`](M1-03_multiblock-framework_r3.md)

This is the short review companion to r3: the before and after git state, and the three things a
reviewer should look at before merging.

---

## 1. Git proof, start of task

Taken in the primary checkout before the worktree was created.

```
$ git --no-pager log --oneline -8
290de5c Merge M1-02s: energy capability and registration spike
c1553fa Merge M1-05: chips and consumables
aedfde2 Merge M1-03c: StructureMatcher facings fix
2836c3e M1-03c: fix StructureMatcher for EAST and WEST facings
7f56b2f M1-02s: report on the 26.3 energy capability and registration spike
f5a7504 M1-02s: 26.3 energy capability and registration spike
2137c62 M1-01: add the task report that was left untracked
3071412 M1-05 report: evidence blocks and header corrections

$ git branch --show-current
master

$ git worktree list
C:/Users/ethan/OneDrive/Documents/Minecraft/euvcraftmk4nemo                   290de5c [master]
C:/Users/ethan/.local/share/opencode/worktree/4b213d/m0-07-server-randomness   334732a [m0-07-server-randomness]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-03c-matcher-fix       2836c3e [m1-03c-matcher-fix]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-05-chips               3071412 [m1-05-chips-and-consumables]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02b-framework                   1fa6bcc [m1-02b-machine-framework]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02s-energy-spike                7f56b2f [m1-02s-energy-spike]

$ git --no-pager branch -a -v
  m0-04-support-line-generations       72c64c8 M0-04b report
  m0-05-stat-model                     289016b M0-05 report
  m0-06-die-type-definitions           443be6c M0-07b fix pass: idiomatic saved-data codec, private salt, regeneration warning
+ m0-07-server-randomness              334732a M0-07 report
  m0-08-foup-item                      59d5c5b M0-08b report: record the hash-fix commit in the header
  m0-09-yield-roll-engine              a5b7082 M0-09 report: yield roll engine
  m0-09b-run-spec-and-review-fixes     2f13585 M0-09b report: RunSpec and review fixes
  m1-00-data-additions-and-docs-repair 2089af3 M1-00: fill report commit hash
  m1-01-die-type-in-runspec            d0e32a3 Merge M1-00 data additions and docs repair
  m1-01b-die-type-tests                abf867d M1-01b: add required die type tests (r2)
  m1-02-machine-framework              b2ae5fd M1-01: die type frozen from the mask into the lot
+ m1-02b-machine-framework             1fa6bcc M1-02b: report on the machine framework
+ m1-02s-energy-spike                  7f56b2f M1-02s: report on the 26.3 energy capability and registration spike
+ m1-03c-matcher-fix                   2836c3e M1-03c: fix StructureMatcher for EAST and WEST facings
+ m1-05-chips-and-consumables          3071412 M1-05 report: evidence blocks and header corrections
* master                               290de5c Merge M1-02s: energy capability and registration spike
  throwaway/m1-02-m1-03-uncommitted    fdb0ccb WIP: uncommitted M1-02 machine framework and M1-03 multiblock framework work
```

### 1.1 What that state says

| fact | consequence |
|------|-------------|
| `aedfde2` / `2836c3e` present | M1-03c **was** merged; the corrected matcher was already on master |
| `m1-02b-machine-framework` present but not merged | M1-02b was **not** on master; merged as `158c820` in this worktree |
| `m1-02b` had `core/multiblock` (7 files, the pre-M1-03c matcher) | the merge had to preserve master's corrected matcher |
| `m1-02b` had no `mc/spike`; the merge deleted 5 spike files | see concern 0 below |

`m1-02b` merged without conflict and master's corrected `StructureMatcher` survived: after
`158c820`, `MultiblockDirection.right()` still returns `new MultiblockPos(-vector.z(), 0, vector.x())`.

**Note for the reviewer.** M1-02b commit `8f3dfc9` ("M1-02b: machine framework on the verified 26.3
patterns") deleted the whole `mc/spike` package — `SpikeGameTests`, `SpikeMachineBlock`,
`SpikeMachineBlockEntity`, `SpikeMachineMenu`, `SpikeRegistries` — because it promoted the spike
into production as `mc/machine/*`. That happened in the merge and is visible in the merge commit's
file list. The M1-02s *report* survives and is the documentation of those patterns; the spike *code* is
gone. That is M1-02b's intent, not damage from this task, but it means there is no longer a small
reference implementation in the tree to read.

Because of that, the templates this task actually copied were M1-02b's `ModMachines`,
`TestMachineBlock`, `FabMachineBlockEntity` and `MachineGameTests`, not the spike sources.

---

## 2. Git proof, end of task

```
$ git --no-pager log --oneline -3
b5fab34 M1-03d report: the multiblock blocks in the running game
4f37944 M1-03d: Process Cell multiblock blocks in the running game
158c820 Merge M1-02b: machine framework

$ git branch --show-current
m1-03d-multiblock-blocks

$ git status --short
(empty)

$ git worktree list
C:/Users/ethan/OneDrive/Documents/Minecraft/euvcraftmk4nemo                    290de5c [master]
C:/Users/ethan/.local/share/opencode/worktree/4b213d/m0-07-server-randomness   334732a [m0-07-server-randomness]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-03c-matcher-fix        2836c3e [m1-03c-matcher-fix]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-03d-multiblock-blocks  b5fab34 [m1-03d-multiblock-blocks]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-05-chips               3071412 [m1-05-chips-and-consumables]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02b-framework                   1fa6bcc [m1-02b-machine-framework]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02s-energy-spike                 7f56b2f [m1-02s-energy-spike]

$ git --no-pager diff --stat master..HEAD
 70 files changed, 6559 insertions(+), 909 deletions(-)
```

The work was committed on a detached HEAD first and then pinned to `m1-03d-multiblock-blocks`; the
branch is what now holds it.

### 2.1 Verification state at the end

```
$ .\gradlew.bat runGameTestServer --offline
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: ========= 705 GAME TESTS COMPLETE IN 1.321 min ======================
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: All 705 required tests passed :)
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: 1 optional tests failed
BUILD SUCCESSFUL in 3m 4s

$ .\gradlew.bat test checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 47s

build/test-results/test/*.xml: classes=60 tests=360 failures=0 errors=0
```

| suite | tests | failures |
|-------|-------|----------|
| Game tests | 705 | 0 |
| Unit tests | 360 | 0 |
| **Total** | **1065** | **0** |

The one optional failure is `arcforge:creosote_moves`: an Arcforge test, not required, and the same
one the M1-02s report already saw.

---

## 3. Concern 1: two defects only the game tests could find

Neither showed up in the compiler, in the 360 unit tests, or in review. Both are the kind of bug that
a "it compiles and the old tests pass" check cannot reach, because both live in the transition code
between the matcher and the world.

### 3.1 A member's `FORMED` flag was never repaired

`revalidate()` short-circuited when a re-validation produced the same member set, which is the common
case when nothing changed:

```java
if (nowFormed == this.formed && this.members.equals(new LinkedHashSet<>(nextMembers))) {
    return;
}
```

But replacing a member block resets that block's state to `FORMED=false`, and the member *set* had
not changed, so nothing put the flag back. The cell would report itself formed while its casing and
port blocks all rendered unformed.

Caught by `multiblock_survives_a_casing_and_port_swap`:

```
lithmod:multiblock_survives_a_casing_and_port_swap failed:
  after the swap: BlockPos{x=1, y=1, z=3} does not carry the formed flag
```

Fixed by running `updateMembers` on the unchanged path as well. Because `updateMembers` only writes a
state that actually differs, re-running it is idempotent, and it also breaks the `setBlock` inside
`neighborChanged` feedback loop that the same test exposed.

**Worth checking in review:** `ProcessCellControllerBlockEntity.revalidate` — the "unchanged" branch
now repairs flags rather than returning.

### 3.2 A controller that had never formed could not say why

`failureReason` was assigned inside the transition branch only, so a controller that started unformed
and stayed unformed reported an empty string. Two consequences:

- a player right-clicking an unfinished cell would be told nothing;
- the two-cell test could not distinguish "refused because these blocks are already claimed" from
  "refused because some block is missing", because the reason was blank either way.

Caught by `multiblock_cells_do_not_share_blocks`:

```
lithmod:multiblock_cells_do_not_share_blocks failed:
  the refusal does not name the contested block:
```

Fixed by refreshing the reason on every pass while keeping the `onFormed`/`onUnformed` hooks tied to
the transition, so a re-validation that changes nothing is still not a transition.

---

## 4. Concern 2: the model texture names were wrong three times, and the obvious check is wrong

### 4.1 `quartz_block` is not a file either

This has now bitten the repository twice. Verified by reading the jar the server actually runs
(`build/moddev/artifacts/minecraft-patched-26.3.0.69-beta.jar`):

| name | M1-03 r1 | M1-03 r2 "fix" | M1-03d |
|------|----------|----------------|--------|
| `block_of_iron` | used | replaced | absent from the jar |
| `smooth_quartz` | used | replaced | absent from the jar |
| `quartz_block` | not used | **used as the fix** | **absent from the jar** |
| `quartz_block_bottom` | suggested | not used | present |

r2 replaced `smooth_quartz` with `quartz_block` and recorded it as fixed. That file does not exist.
The six names this task ships were each confirmed present before being written into a model.

### 4.2 The obvious way to check textures is wrong in both directions

The first version of `multiblock_model_textures_all_resolve` resolved a texture by asking whether
`minecraft:block/<name>` is a registered block id. That is not what "the texture resolves" means:

- **`smooth_quartz` is a real block.** Its textures are `smooth_quartz_top` and `smooth_quartz_side`,
  so the rule accepts a name that does not exist as a file. The test's own negative control caught it:

  ```
  lithmod:multiblock_model_textures_all_resolve failed:
  smooth_quartz resolves, so this test's rule is too weak to catch it (negative control)
  ```

- **`quartz_block_bottom` is not a block.** It is a legitimate texture for `minecraft:quartz_block`, so
  a block-registry rule would reject real content.

The shipped test opens the Minecraft jar and looks for the file. It keeps both negative controls:
`block_of_iron`, `smooth_quartz` and `quartz_block` must not resolve; `iron_block` and
`quartz_block_bottom` must.

### 4.3 The server `ResourceManager` cannot answer this at all

The natural-looking implementation is `level.getServer().getResourceManager()`. It does not work: it
is built from server-side resources, and vanilla client assets are masked out of it. The first version
of the test used it and failed on a texture that is definitely present:

```
lithmod:multiblock_model_textures_all_resolve failed:
  process_cell_controller_unformed texture 'all' = minecraft:block/smooth_stone does not resolve
```

`smooth_stone.png` is present in the patched jar. The fix reads the jar directly via
`Blocks.class.getProtectionDomain().getCodeSource()` and `ZipFile`.

**Worth carrying forward:** any future task that wants to validate client assets from a game test will
hit the same wall, and this is the working answer.

---

## 5. Concern 3: a second id trap, the item-side twin of the block one

| trap | symptom | fix |
|------|---------|-----|
| block properties built with `Properties.of()` inside the constructor | `NullPointerException: Block id not set` | `DeferredRegister.Blocks.registerBlock(name, Class::new, props -> ...)` |
| item properties built with `new Item.Properties()` by hand | `NullPointerException: Item id not set` | `DeferredRegister.Items.registerSimpleBlockItem(name, () -> BLOCK.get())` |

Both crashed this task for real, in this order:

```
[00:06:58] [modloading-sync-worker/ERROR] [ne.ne.fm.ja.FMLModContainer/]:
  Exception caught during firing event: Item id not set
java.lang.NullPointerException: Item id not set
[00:06:58] [modloading-sync-worker/ERROR] [ne.ne.fm.ModContainer/LOADING]:
  Caught exception during event net.neoforged.neoforge.registries.RegisterEvent dispatch for modid lithmod
[00:06:59] [modloading-sync-worker/ERROR] [ne.ne.re.GameData/]: Rolling back to VANILLA state
```

The block-side rule is documented in the M1-02s spike report, section 6.1. **The item-side rule is
not**, and the spike did not hit it because it registered no block items. The M1-03d report now
records both in section 2.1.

Only `registerSimpleBlockItem` stamps the item id onto the properties. Handing it a fresh
`Item.Properties()` fails, which is exactly the shape of the block-side mistake.

---

## 6. Concerns outside the three, briefly

| item | note |
|------|------|
| `onFormed`/`onUnformed` moved off the interface | they are `protected` on `ProcessCellControllerBlockEntity`, not on `EnergyBufferProvider`. Java forbids narrowing an interface method to `protected`, so M1-03's choice made them unusable as M1-09's internal hook. This is a deliberate deviation from r2's shape. |
| new creative tab | `2_process_cell`, rather than adding to `MAIN` or `CHIPS`. The M0-08 test pins the main tab to exactly 2 entries and the M1-05 test pins the chips tab to exactly 7; adding to either breaks a passing test. |
| geometry in the game tests is recomputed, not borrowed | the twelve positions come from the M1-03c definition with a literal basis-vector table, not from `StructureMatcher`. M1-03's unit test re-implemented the matcher's formulas and therefore agreed with all four of its bugs; that trap is not repeated here. |
| no loot tables | the three blocks drop nothing when broken in survival. `test_machine` has the same gap and it was out of scope for M1-02b. Worth a follow-up if these blocks are meant to be playable content rather than test furniture. |