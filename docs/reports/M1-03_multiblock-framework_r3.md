# M1-03 Multiblock framework r3: the blocks in the running game

- Date: 2026-10-16
- Worktree: `~/.local/share/opencode/worktree/fef470/m1-03d-multiblock-blocks`
- Branch: off `master` @ `290de5c`, with M1-02b merged in this worktree
- Commands: `.\gradlew.bat runGameTestServer --offline`, `.\gradlew.bat test checkCoreImports --offline`
- NeoForge `26.3.0.69-beta`, Minecraft `26.3`, Java toolchain 25

## 1. What this task did

M1-03's Minecraft layer had never been run. It is now built on the corrected M1-03c matcher and on
the registration patterns the M1-02s spike proved and M1-02b put into production. Three blocks, three
block item forms, one energy capability on the power port, six block models, seven game tests.

| # | requirement | how it is met |
|---|-------------|--------------|
| 1 | register blocks with `DeferredRegister.Blocks.registerBlock` | `ModMultiblocks`; every block constructor takes the `Properties` the helper hands it and passes them to `super` |
| 2 | `EnergyBufferProvider` moves to mc and returns `EnergyHandler`; controller owns a `SimpleEnergyHandler` with `maxExtract` 0; port capability on the mod bus returns the controller's handler only while formed | `mc/multiblock/EnergyBufferProvider`, `ProcessCellControllerBlockEntity.ControllerEnergy`, `ModMultiblocks.registerCapabilities` |
| 3 | controller, casing and power port use the corrected matcher; formation verified in game for all four facings | all three resolve through `ProcessCellControllerBlockEntity.revalidate()`; `multiblock_forms_in_all_four_facings` |
| 4 | formed and unformed use different vanilla textures that exist; a test resolves every model texture | six `cube_all` models, six distinct real textures; `multiblock_model_textures_all_resolve` |
| 5 | the four required game tests | section 5, plus three more |
| 6 | explicit `onFormed` and `onUnformed` for M1-09 | `protected void onFormed()` / `onUnformed()` on the controller; `multiblock_formation_hooks_fire_per_transition` |

## 2. Registration

### 2.1 The two id traps

Both bit during this task. They are the item-side and block-side twins of the same 26.3 rule.

| trap | symptom | fix |
|------|---------|-----|
| building block properties inside the constructor with `Properties.of()` | `NullPointerException: Block id not set` | `BLOCKS.registerBlock(name, Class::new, props -> ...)`, whose factory properties already carry the id |
| hand-building a `BlockItem` with `new Item.Properties()` | `NullPointerException: Item id not set` | `ITEMS.registerSimpleBlockItem(name, () -> BLOCK.get())` |

Both messages were hit for real here. The second is new to this repository's history: the M1-02s
spike documented the block-side rule but not its item-side twin.

### 2.2 What the power port actually registers

```java
modEventBus.addListener(ModMultiblocks::registerCapabilities);

event.registerBlockEntity(Capabilities.Energy.BLOCK, PROCESS_CELL_POWER_PORT_BE.get(),
    (blockEntity, side) -> blockEntity instanceof ProcessCellPowerPortBlockEntity port
        ? port.energy()
        : null);
```

`RegisterCapabilitiesEvent` implements `IModBusEvent`, so it is attached to the bus handed to the
`@Mod` constructor, never to `NeoForge.EVENT_BUS`. The power port has **no** `getCapability`
override: a block entity method of that name is not consulted for capabilities in 26.3. Only
`Capabilities.Energy.BLOCK` is registered, so `Item.BLOCK` and `Fluid.BLOCK` lookups answer `null`
on every side, which the energy test asserts.

`ProcessCellPowerPortBlockEntity.energy()` returns the controller's handler when the port belongs to a
formed cell and `null` otherwise. That null is what makes `Level.getCapability` answer null for an
unformed port, which is the "only while formed" requirement expressed at the registry rather than
inside the block entity.

## 3. Two defects the game tests found

Neither was visible to a compiler, to the unit tests, or to review.

### 3.1 A member's FORMED flag was never repaired

The controller short-circuited when a re-validation produced the same member set, which is the common
case when nothing changed. But replacing a member block resets that block's state to `FORMED=false`,
and the member *set* had not changed, so the flags were never put back. The casing and power port
swap test caught it:

```
lithmod:multiblock_survives_a_casing_and_port_swap failed:
  after the swap: BlockPos{x=1, y=1, z=3} does not carry the formed flag
```

Fixed by making `updateMembers` idempotent and running it on the unchanged path too. It only writes a
state that actually differs, which also breaks the `setBlock` inside `neighborChanged` feedback loop
the same test exposed.

### 3.2 A controller that had never formed could not say why

`failureReason` was only written inside the transition branch, so a controller that started unformed
and stayed unformed reported an empty reason. That matters because the reason is what a player should
be told when they right-click an unfinished cell, and it is what the two-cell test uses to prove a
refusal was about contention rather than about a missing block.

Fixed by refreshing the reason on every pass, while keeping the hooks tied to the transition.

## 4. Model textures

Six block models, all `minecraft:block/cube_all`, each pair distinct.

| block | unformed | formed |
|-------|----------|--------|
| controller | `minecraft:block/smooth_stone` | `minecraft:block/iron_block` |
| casing | `minecraft:block/stone` | `minecraft:block/deepslate` |
| power port | `minecraft:block/lapis_block` | `minecraft:block/redstone_block` |

`multiblock_model_textures_all_resolve` reads all six block models and all three item models from the
classpath and resolves every `parent` and every `textures` value.

**Two things the earlier reports got wrong, both found here.**

1. `quartz_block` is **not** a file. The M1-03 report replaced `smooth_quartz` with `quartz_block`
   and called it fixed; `assets/minecraft/textures/block/quartz_block.png` does not exist. Neither do
   `block_of_iron.png` or `smooth_quartz.png`.
2. The obvious shortcut for checking textures does not work. "Is `minecraft:block/<name>` a
   registered block id?" is wrong in both directions: `smooth_quartz` **is** a real block, whose
   textures are `smooth_quartz_top` and `smooth_quartz_side`, and the legitimate `quartz_block_bottom`
   is not a block at all. The first version of this test used that rule, and its negative control
   failed with:

   ```
   smooth_quartz resolves, so this test's rule is too weak to catch it (negative control)
   ```

The check therefore opens the Minecraft jar the server is running and looks for the file.

**One API note worth carrying:** the server's `ResourceManager` cannot answer this. It is built from
server-side resources, and vanilla client assets are masked out of it, so
`getResource("minecraft:textures/block/iron_block.png")` returns empty for a texture that is
definitely present. The first version of the test used it and failed with:

```
process_cell_controller_unformed texture 'all' = minecraft:block/smooth_stone does not resolve
```

The jar is reached through `Blocks.class.getProtectionDomain().getCodeSource()` and read with
`ZipFile`.

## 5. Game tests

`lithmod:multiblock`, 7 tests. Every positive assertion is paired with a negative control.

| game test | what it proves | negative control |
|-----------|----------------|------------------|
| `multiblock_forms_in_all_four_facings` | all 4 facings x all 4 front-face offsets form; 12 members each; every member carries `FORMED` | the same 12 blocks mirrored onto the controller's **front** side are refused, 16 times |
| `multiblock_removing_any_of_twelve_unforms_the_rest` | each of the 12 positions is removed in turn; the other 11 lose `FORMED` | removing the controller itself is covered by the members' own tick, since no controller is left to push an update |
| `multiblock_survives_a_casing_and_port_swap` | a casing and a port exchange roles; the cell stays formed and the capability follows the port | removing **both** ports instead is not a swap and breaks the cell, with the reason naming the missing port |
| `multiblock_cells_do_not_share_blocks` | a second cell wanting 6 blocks the first already holds is refused; the first keeps all 12 and every member still points at it | a third cell sharing nothing forms; and the second cell forms once the contention is removed |
| `multiblock_energy_reaches_controller_only_while_formed` | 500 FE inserted through a port reaches the controller; capacity matches; both ports agree; extraction refused; energy survives breaking and re-forming | a port in no cell exposes nothing on any side; a casing exposes nothing inside a formed cell; item and fluid lookups are null; while broken the capability is null but the 500 FE is still stored |
| `multiblock_formation_hooks_fire_per_transition` | `onFormed`/`onUnformed` exist, are `protected`, no-arg, `void`; form, unform and re-form are three transitions; an unchanged re-validation is not | a controller that never formed reports zero transitions |
| `multiblock_model_textures_all_resolve` | 6 block models and 3 item models; every parent and texture resolves; formed differs from unformed; three blockstate files exist | `block_of_iron`, `smooth_quartz` and `quartz_block` do **not** resolve, while `iron_block` and `quartz_block_bottom` do |

### 5.1 The geometry in these tests is not the matcher's

The twelve positions of a cell are recomputed from the M1-03c definition with a literal basis-vector
table, the same way `StructureMatcherTest` does, so a regression in `StructureMatcher` fails these
tests too instead of both sides agreeing. This is the trap M1-03 fell into: its test re-implemented
the matcher's formulas and therefore agreed with all four of its bugs.

### 5.2 Geometry used

For facing `f`, `back = -f`, `right = f` rotated 90 degrees clockwise seen from above, `up = +Y`; the
box starts at the controller's own cell and extends 3 along `back`, 2 along `right`, 2 along `up`.

| facing | box occupied, controller at (1,1,1), offset (0,0) |
|--------|--------------------------------------------------|
| NORTH | x 1..2, y 1..2, z 1..3 |
| SOUTH | x 0..1, y 1..2, z -1..1 |
| EAST | x -1..1, y 1..2, z 1..2 |
| WEST | x 1..3, y 1..2, z 0..1 |

## 6. Real output

### 6.1 Game tests

```
$ .\gradlew.bat runGameTestServer --offline

[00:30:18] [Server thread/INFO] [minecraft/GameTestRunner]: Running test environment 'lithmod:multiblock' batch 0 (7 tests)...
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] forms_in_all_four_facings: 16 controller placements formed, 16 mirrored placements refused
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] removing_any_of_twelve: 12 removals, each left the other 11 unflagged
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] casing_port_swap: one port moved from BlockPos{x=1, y=1, z=3} to BlockPos{x=2, y=1, z=2}, capability followed it
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] cells_do_not_share: second cell wanted 6 blocks the first already holds, was refused, and the first kept all 12
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] cells_do_not_share: second cell forms once the contention is removed
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] energy_only_while_formed: 500 FE through the port reached the controller, extraction refused, capability gone while broken, 250 more accepted after re-forming
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] formation_hooks: protected onFormed/onUnformed present, 3 transitions recorded for form/unform/re-form
[00:30:18] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-03d] model_textures_all_resolve: 6 block models and 3 item models, every parent and texture resolved against the vanilla assets
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: ========= 705 GAME TESTS COMPLETE IN 1.321 min ======================
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: All 705 required tests passed :)
[00:30:19] [Server thread/INFO] [minecraft/GameTestServer]: 1 optional tests failed
BUILD SUCCESSFUL in 3m 4s
```

The single optional failure is `arcforge:creosote_moves`, which belongs to Arcforge, is not a required
test, and is the same one the M1-02s report saw.

### 6.2 Game test totals, summed from the batch lines

| environment | tests | passed | failed |
|-------------|-------|--------|--------|
| `lithmod:multiblock` | 7 | 7 | 0 |
| `lithmod:machine` | 7 | 7 | 0 |
| `lithmod:foup_default` | 11 | 11 | 0 |
| `lithmod:chip_default` | 3 | 3 | 0 |
| `lithmod:yield_default` | 4 | 4 | 0 |
| `lithmod:fab_random_default` | 2 | 2 | 0 |
| `lithmod:default` | 1 | 1 | 0 |
| `minecraft:default` | 1 | 1 | 0 |
| `encodedlogistics:default` (5 batches: 50+50+50+50+17) | 217 | 217 | 0 |
| `encodedlogistics:arcforge_reload` | 1 | 1 | 0 |
| `arcforge:default` (9 batches: 50x8+42) | 442 | 442 | 0 |
| `arcforge:solar_*` (7 single tests) | 7 | 7 | 0 |
| `arcforge:solar_controller_rebuild` | 1 | 1 | 0 |
| **Total** | **705** | **705** | **0** |

The server printed `All 705 required tests passed :)`, which matches the table.

### 6.3 Unit tests and the core import check

```
$ .\gradlew.bat test checkCoreImports --offline

Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 47s
```

Totals read from `build/test-results/test/*.xml`: `classes=60 tests=360 failures=0 errors=0`.

### 6.4 Combined

| suite | tests | failures |
|-------|-------|----------|
| Game tests (`runGameTestServer`) | 705 | 0 |
| Unit tests (`test`) | 360 | 0 |
| **Total** | **1065** | **0** |

`StructureMatcherTest` is unchanged at 45 tests, all passing, which is the check that M1-03d did not
disturb the corrected matcher.

### 6.5 Mutation check

To confirm the requirement most at risk of being silently untested is genuinely tested, the formed gate
was removed from `ProcessCellPowerPortBlockEntity.energy()` and the suite re-run:

```
[00:34:20] [Server thread/ERROR] [minecraft/LogTestReporter]:
  lithmod:multiblock_energy_reaches_controller_only_while_formed failed:
  a port of a broken cell still exposes energy on side down on tick 0
```

Exactly one test failed, and it is the one that owns the property. The gate was restored and the suite
returned to `All 705 required tests passed :)`.

## 7. Files

| path | status |
|------|--------|
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ModMultiblocks.java` | new: three blocks, three block entity types, the energy capability |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/EnergyBufferProvider.java` | new in mc, returns `EnergyHandler` |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellControllerBlock.java` | new |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellCasingBlock.java` | new |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellPowerPortBlock.java` | new |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellControllerBlockEntity.java` | new: matcher driver, energy, `onFormed`/`onUnformed` |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellMemberBlockEntity.java` | new: shared casing and power port behaviour |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellCasingBlockEntity.java` | new |
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellPowerPortBlockEntity.java` | new: the capability handler |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/MultiblockGameTests.java` | new: 7 tests |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | modified: two registration calls |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java` | modified: three block items |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java` | modified: a third tab |
| `src/main/resources/assets/lithmod/blockstates/*.json` | new: 3 |
| `src/main/resources/assets/lithmod/models/block/*.json` | new: 6 |
| `src/main/resources/assets/lithmod/models/item/process_cell_*.json` | new: 3 |
| `src/main/resources/assets/lithmod/items/process_cell_*.json` | new: 3 |
| `src/main/resources/assets/lithmod/lang/en_us.json` | modified: 4 keys |
| `src/main/java/com/euvcraft/lithmod/core/EnergyBuffer.java` | **deleted** |
| `src/main/java/com/euvcraft/lithmod/core/multiblock/EnergyBufferProvider.java` | **deleted** |

`core/EnergyBuffer` and `core/multiblock/EnergyBufferProvider` were deleted rather than left behind:
after this task they were reachable only from each other, and `checkCoreImports` confirms core still
has no Minecraft or NeoForge imports.

## 8. Decisions and deviations

| item | decision | why |
|------|----------|-----|
| `onFormed` / `onUnformed` placement | `protected` on the controller, **not** on `EnergyBufferProvider` | Java forbids narrowing an interface method to `protected`. M1-03 put them on the core interface as public defaults, which makes them unusable as an internal hook. They moved to the class when the interface moved. |
| new creative tab | `2_process_cell` | the M0-08 test pins the main tab to exactly 2 entries and the M1-05 test pins the chips tab to exactly 7, so adding to either would have broken a passing test |
| block items declared in `ModItems` | built from `ModMultiblocks` | keeps one item registry while the block items still depend on blocks registered in another class; the `.get()` calls sit in lambdas so neither class touches the other during static init |
| formation updates in game | `neighborChanged` re-validates immediately, and the controller re-validates every 20 ticks as a backstop | a member may be several blocks from the controller, so neighbour updates alone do not cover every edit |
| member controller links | not persisted | they are derived from the world; re-deriving on load is what keeps a saved cell honest if its neighbours moved |
| the twelve positions in the game tests | recomputed from the M1-03c definition, not taken from `StructureMatcher` | a shared-formula oracle is what let all four of M1-03's matcher bugs pass |

## 9. Not verified

| item | why |
|------|-----|
| client-side rendering of the formed and unformed models | `runGameTestServer` is a server. The models are checked as data, not as pixels. |
| placement facing in a live world | `getStateForPlacement` uses `context.getNearestLookingDirection()`, which a game test cannot meaningfully exercise because `helper.setBlock` bypasses placement. The four facings are set directly on the block state instead, which is what the matcher reads. |
| the multiblock in a running client or dedicated server | no client was launched; no loot table ships with these blocks, so breaking one in survival drops nothing |
| `arcforge:creosote_moves` | optional Arcforge test, unrelated, and not a required test |
| the `failureReason` text shown to a player | `useItemOn` is not implemented on these blocks, so the reason is not yet surfaced in game; it is asserted through the API instead |