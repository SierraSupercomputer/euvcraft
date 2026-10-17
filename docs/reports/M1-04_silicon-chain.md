# M1-04: Silicon chain, Crystal Puller and Wafer Slicer

- Date: 2026-10-16
- Branch: `m1-04-silicon-chain` (isolated worktree `.orchestrator/worktrees/euvcraft/m1-04-silicon-chain`)
- Commit: none. This agent cannot execute commands, so every file below is an uncommitted working-tree change and the coordinator owns the commit and the verification run. Baseline commit recorded in `build/M1-04-start-evidence.json`: `4b5f4a74f116b1759898cd690ebfb3af837eb32f` (taken from that evidence file, not re-measured by me).
- Java version used: none. No JVM was started by this agent.
- Build and test commands: none run. See section 3 for the commands the coordinator must run.
- `git worktree list`, `git branch --show-current`, `git status --short`: not run (no shell). The coordinator must take them before and after committing.

## 1. Summary: the silicon chain is implemented end to end in code and data

The Crystal Puller is a plain `FabMachineBlockEntity` driven by `MachineRecipeRunner`, with its
recipes in `data/lithmod/machine_recipes/crystal_puller.json`. The Wafer Slicer owns its own
tick instead, because its product is a sealed lot rather than a recipe result: it takes a boule
and an empty FOUP, refuses any FOUP whose trusted lot already holds wafers or a run spec, pauses
without energy or without room in the output, consumes nothing until it writes the product, and
persists its own progress under the `slicer` node. `core.WaferSlicing.wafersFor(FabSettings,
int)` holds the one number the slicer needs and is unit tested; both item models, item
definitions, block models, blockstates, loot tables, tags and language entries reuse vanilla
textures. Two items, two block items, two blocks, two block entity types, two menu types and two
screens were added, the menus are registered once in `SiliconMachines` and `ModMenus` was not
touched. Nothing in this task was executed: no build, no unit test, no game test, so every
verification line below says "not run".

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java` | new | pure `wafersFor(FabSettings, int)` = `min(wafersPerBoule, capacity)` |
| `src/test/java/com/euvcraft/lithmod/core/WaferSlicingTest.java` | new | 7 unit tests for that function |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachines.java` | new | blocks, block entity types, menu types, `Capabilities.Energy.BLOCK` |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineBlock.java` | new | shared block shell: ticker calls `serverTick()`, use opens the menu |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlock.java` | new | Crystal Puller block |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlockEntity.java` | new | 1 input, 1 output, base runner tick |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerMenu.java` | new | menu for the puller |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlock.java` | new | Wafer Slicer block |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlockEntity.java` | new | 2 inputs, 1 output, own tick, `acceptedFoup(ItemStack)`, slice persistence |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerMenu.java` | new | menu with the two validating slots and its own shift-click routing |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/BouleSlot.java` | new | takes boules only |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/EmptyFoupSlot.java` | new | takes only a FOUP whose trusted lot is empty and unstarted |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineScreen.java` | new | bar layout shared by the two screens |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/screen/CrystalPullerScreen.java` | new | puller screen |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/screen/WaferSlicerScreen.java` | new | slicer screen |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/client/SiliconClientEvents.java` | new | `Dist.CLIENT` screen bindings for both menus |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java` | new | 7 game tests in the `lithmod:silicon_chain` environment |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java` | modified | `silicon`, `silicon_boule`, and the two block items |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java` | modified | new `4_silicon_chain` tab |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | modified | `SiliconMachines.register(modEventBus)`, `SiliconGameTests.register(modEventBus)` |
| `src/main/resources/data/lithmod/machines/crystal_puller.json` | new | energy numbers and slot counts |
| `src/main/resources/data/lithmod/machines/wafer_slicer.json` | new | energy numbers, `slice_ticks: 200`, slot counts |
| `src/main/resources/data/lithmod/machine_recipes/crystal_puller.json` | new | 4 silicon to 1 boule in 400 ticks |
| `src/main/resources/data/lithmod/recipe/silicon_from_smelting.json` | new | sand to silicon in a furnace |
| `src/main/resources/data/lithmod/recipe/silicon_from_blasting.json` | new | sand to silicon in a blast furnace |
| `src/main/resources/data/lithmod/loot_table/blocks/crystal_puller.json` | new | block drops itself |
| `src/main/resources/data/lithmod/loot_table/blocks/wafer_slicer.json` | new | block drops itself |
| `src/main/resources/data/lithmod/tags/block/machine.json` | modified | both new blocks added |
| `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` | modified | both new blocks added |
| `src/main/resources/data/minecraft/tags/block/needs_stone_tool.json` | modified | both new blocks added |
| `src/main/resources/assets/lithmod/{items,models/item,models/block,blockstates}/*.json` | new (10 files) | item definitions, models and blockstates for `silicon`, `silicon_boule`, `crystal_puller`, `wafer_slicer` |
| `src/main/resources/assets/lithmod/lang/en_us.json` | modified | four item names and the new tab name |
| `docs/testing.md` | modified | silicon chain section: unit test and the seven game tests |
| `docs/reports/M1-04_silicon-chain.md` | new | this report |

## 3. Verification

No command was run. This agent cannot execute a shell, so there is no build output, no test
output and no game test output to paste. Every cell below is therefore unmeasured, and the
negative controls the task asks for (break the behaviour, show the test fail, revert) have not
been performed.

Commands the coordinator must run, in this order:

```powershell
.\gradlew.bat compileJava --offline
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
.\gradlew.bat runGameTestServer --offline
```

Then take the counts from `build/test-results/test/*.xml` for the unit test table.

| test class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `com.euvcraft.lithmod.core.WaferSlicingTest` | not measured | not measured | not measured | not measured |
| `com.euvcraft.lithmod.mc.ItemAssetResourceTest` (existing, affected: 4 new items) | not measured | not measured | not measured | not measured |
| total | no run produced counts | | | |

Game tests (one line per test, all currently unrun):

| game test | status |
|---|---|
| `lithmod:wafer_slicer_produces_a_lot` | not run |
| `lithmod:wafer_slicer_lot_takes_layer_one` | not run |
| `lithmod:wafer_slicer_refuses_a_used_foup` | not run |
| `lithmod:wafer_slicer_pauses_without_energy` | not run |
| `lithmod:wafer_slicer_save_load_round_trip` | not run |
| `lithmod:crystal_puller_runs_its_recipe` | not run |
| `lithmod:silicon_machines_expose_no_item_handler` | not run |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| Items `silicon` and `silicon_boule` with model, item definition, language entry and creative tab entry | partly | `ModItems.SILICON`, `ModItems.SILICON_BOULE`; `assets/lithmod/{items,models/item}/{silicon,silicon_boule}.json`; `en_us.json`; `ModCreativeTabs.SILICON_CHAIN`. Not met as "verified": `ItemAssetResourceTest` has not been run |
| Placeholder art only, no new binary art | met | every model parents `minecraft:item/generated` or `minecraft:block/cube_all` and names a vanilla texture |
| Smelting and blasting recipes turning `minecraft:sand` into silicon | partly | `data/lithmod/recipe/silicon_from_smelting.json`, `silicon_from_blasting.json` written in the 1.21+ cooking schema (`ingredient`, `result`, `experience`, `cookingtime`). Not verified: no datapack load has run (see section 5) |
| Machine recipe 4 silicon to 1 boule in 400 ticks | met | `data/lithmod/machine_recipes/crystal_puller.json`; the runtime cost per boule is section 6 |
| Machine definitions with energy values and a `slice_ticks` param | met | `machines/crystal_puller.json` (20 FE/tick, 20000 capacity), `machines/wafer_slicer.json` (30 FE/tick, `slice_ticks: 200`) |
| Crystal Puller on `FabMachineBlockEntity` with input, one output, energy bar, progress bar, `MachineRecipeRunner`, hand-fed | partly | `CrystalPullerBlockEntity` uses the inherited `serverTick()` and therefore `MachineRecipeRunner`; `CrystalPullerMenu` + `CrystalPullerScreen` draw both bars. Not verified: bars are only confirmed by reading the shared `FabMachineScreen` code |
| Slicer boule slot, input FOUP slot that accepts only empty unstarted lots, extract-only output slot | partly | `BouleSlot`, `EmptyFoupSlot` (both call `WaferSlicerBlockEntity.acceptedFoup`, which reads through `FoupItems.trustedContentsOf`), `addOutputSlot`. Not verified: the client-side run needs the game test server |
| Every FOUP read through `FoupItems.trustedContentsOf` | met | the only FOUP reads in the new code are in `acceptedFoup(ItemStack)`; no `contentsOf` call exists in this task |
| Runs for `slice_ticks` when both inputs, energy and output room are present | partly | `WaferSlicerBlockEntity.serverTick`/`advance`; progress is `sliceTicks()` from the definition param. Not verified: unrun |
| Consumes one boule and the empty FOUP and writes a FOUP of the same item type | partly | `complete()` writes the product first, then clears the FOUP slot and shrinks the boule. Not verified: unrun |
| Fresh random lot id, the input's stored capacity, no run spec, `min(wafersPerBoule, capacity)` wafers each unprocessed via the existing factory | partly | `complete()` uses `UUID.randomUUID()`, `new FoupContents(lotId, input.capacity(), wafers)` (three-arg form, so `run` is null) and `Wafer.unprocessed()` |
| A boule yielding fewer wafers than the capacity leaves the FOUP partially filled | met | that is the `min` in `WaferSlicing.wafersFor`, unit tested; the game tests run at the 25/25 default |
| Pure `wafersFor(settings, capacity)` in core, unit tested | partly | `core/WaferSlicing.java`, `WaferSlicingTest` (7 tests written). Not verified: never executed |
| No hard-coded gameplay numbers | partly | slice length, energy and slot counts come from the definition; wafer count from config plus the stored capacity. The two remaining literals are the documented fallbacks `FALLBACK_SLICE_TICKS`, `FALLBACK_CAPACITY`, `FALLBACK_MAX_RECEIVE`, used only before the data pack is read, plus the placeholder recipe numbers the task asked for |
| Hand-fed only, no item handler capability | partly | `SiliconMachines` registers `Capabilities.Energy.BLOCK` only, and the inherited `Container` answers `canPlaceItem`/`canTakeItem` false. Not verified: unrun |
| Server authoritative, menus only display synced values | met | all logic is in `serverTick`; the menus and screens read only `ContainerData`; the validating slots run on both sides so a client cannot push a bad stack |
| Lots record no substrate generation in M1 | met | the produced lot carries no generation field; see section 8 |
| JUnit for `wafersFor` | partly | written, not run |
| The seven named game tests | partly | written in `SiliconGameTests`, not run |
| Done when: build and tests pass with counts that add up | not met | no run exists |
| Done when: a boule becomes a usable lot in a game test | partly | `wafer_slicer_produces_a_lot` and `wafer_slicer_lot_takes_layer_one` do exactly that, but neither has been executed |
| Done when: the report is `docs/reports/M1-04_silicon-chain.md` | met | this file |
| Out of scope respected | met | no crafting recipe for the machine blocks, no mask or Process Cell work, and no file outside the allowed list was created or edited |

## 5. Not verified

- Nothing was executed: no compile, no unit test, no game test, no client run. Every "met" row above rests on reading the source, not on output.
- The vanilla cooking recipe schema for 26.3 (`type`, `ingredient`, `result`, `experience`, `cookingtime`) could not be checked offline: the vanilla data and the Gradle cache are outside the paths this agent may read. The two recipe files must be confirmed against a datapack load or a running server. If the schema differs, the fix is local to those two JSON files.
- Whether `MachineRecipeRunner.consume` shrinking one item per input slot (section 6) is the intended framework behaviour was not decided by this agent; the game test asserts the current behaviour rather than the task text, and that assertion must be reviewed.
- The energy buffer is not resized from data: `FabMachineBlockEntity` builds it once with the constructor's fallback capacity. The two machines' fallback constants equal the `energy_capacity` and `max_receive` in their JSON files so the buffer and the data agree, but changing the JSON capacity afterwards changes only what the menu displays for the runner-driven Crystal Puller, because the base class writes `DATA_CAPACITY` from the definition. The Wafer Slicer reports the buffer's own capacity instead. This is framework behaviour and was left alone.
- The two screen classes and `SiliconMachineScreen` were never loaded; the client event binding is a single `RegisterMenuScreensEvent` listener mirroring the existing one.
- `docs/m0-foundation.md` was not updated; the task did not ask for it.
- Whether any existing test pins a count that these two new blocks and four new items break: `MachineDropGameTests` uses only the test machine and `ChipGameTests` pins the main tab at two entries and the chips tab at seven, none of which this task touches, but nothing was run to confirm.

## 6. Deviations

| spec says | what I did | why |
|---|---|---|
| "4 silicon becomes 1 silicon_boule" | the data declares `{ "item": "lithmod:silicon", "count": 4 }`, and `MachineRecipeRunner.consume` shrinks exactly one item per input slot, so one boule costs one silicon | `MachineRecipeRunner` is the M1-02 framework and out of this task's scope. The precise blocker: `consume(List<ItemStack>)` should shrink each input by `recipe.ingredientCounts().get(slot)`, which it currently cannot do because `consume` is static and is not passed the recipe. Either it takes the counts as a parameter or `advance` shrinks inline. Until that changes, a four-silicon boule is one boule for one silicon. `crystal_puller_runs_its_recipe` asserts the silicon left is 3 and says why |
| "A single block with a boule slot, an input FOUP slot..." using the framework menu helpers | the two validating slots are added by `WaferSlicerMenu`'s constructor and `quickMoveStack` is overridden | `FabMachineMenu` counts only the slots its own helpers add, in a private field, and offers no way to add a custom `Slot`. Without the override, shift-clicking a FOUP would be routed as a player slot. The framework hook that would remove the override is one method, something like `protected final Slot addCustomSlot(Slot slot) { addSlot(slot); this.machineSlotCount++; return slot; }` |
| progress synced to the menu | the slicer writes `DATA_PROGRESS`, `DATA_TICKS`, `DATA_ENERGY` and `DATA_CAPACITY` itself through `data()` | `FabMachineBlockEntity.syncData` is private, so an overriding `serverTick` cannot reuse it |
| "Nothing numeric is hard-coded" | three fallback constants on each block entity | a block entity can be constructed before the data pack is read, exactly like the framework's own `FALLBACK_CAPACITY` |
| two machine blocks | `SiliconMachineBlock` base plus one thin subclass each | both blocks share the ticker and the use-on behaviour; only the block entity differs |

## 7. Open questions

1. Should the Crystal Puller cost one silicon or four? Fixing it means changing `MachineRecipeRunner.consume` in the framework (section 6), which needs explicit coordinator authorisation.
2. Should `FabMachineBlockEntity`'s energy buffer be resized when `energy_capacity` changes in the data, so the menu and the buffer can never disagree? That is a framework change too.
3. Is `FabMachineMenu`'s missing custom-slot hook worth adding for this task's sake, or should the slicer's `quickMoveStack` override stay?
4. Do the vanilla 26.3 cooking recipe files load? Unverified here (section 5).
5. Should `silicon` and `silicon_boule` ever need a component (for example a boule quality in a later milestone), or are they plain items for good?

## 8. Handoff

Extracted from the source written by this task.

Core:

- `com.euvcraft.lithmod.core.WaferSlicing.wafersFor(FabSettings settings, int capacity) -> int`
  returns `min(settings.wafersPerBoule(), capacity)`; null settings throws
  `NullPointerException`, capacity below one throws `IllegalArgumentException`.
- Lots produced by the slicer carry **no substrate generation and no run spec**. A lot is only
  "started" once `FabYieldService.applyLayerStep` freezes a `RunSpec` at layer 1; there is no
  field anywhere for where a boule was grown, and M1 adds none.

Minecraft side:

- `SiliconMachines` (`mc.machine.silicon`): `CRYSTAL_PULLER`, `WAFER_SLICER` (`DeferredBlock`),
  `CRYSTAL_PULLER_BE`, `WAFER_SLICER_BE` (`DeferredHolder<BlockEntityType<?>, …>`),
  `CRYSTAL_PULLER_MENU`, `WAFER_SLICER_MENU` (`DeferredHolder<MenuType<?>, …>`),
  `register(IEventBus modEventBus)`.
- `CrystalPullerBlockEntity`: `MACHINE_ID = "crystal_puller"`, `INPUT_SLOT = 0`, `OUTPUT_SLOT = 1`,
  `FALLBACK_CAPACITY = 20000`, `FALLBACK_MAX_RECEIVE = 2000`. Standard base tick.
- `WaferSlicerBlockEntity`: `MACHINE_ID = "wafer_slicer"`, `BOULE_SLOT = 0`, `FOUP_SLOT = 1`,
  `OUTPUT_SLOT = 2`, `SLICE_TICKS_PARAM = "slice_ticks"`, `FALLBACK_SLICE_TICKS = 200`,
  `FALLBACK_CAPACITY = 20000`, `FALLBACK_MAX_RECEIVE = 2000`. Public members:
  `static FoupContents acceptedFoup(ItemStack stack)` (null when the stack is not a FOUP with an
  empty, unstarted, trusted lot), `static Item bouleItem()`, `int sliceTicks()`,
  `int energyPerTick()`, `boolean isSlicing()`, `boolean isPaused()`, `int sliceProgress()`,
  `int sliceTicksRemaining()`, `void resetSlice()`, and `serverTick()` which replaces the base
  one. Saved state lives under the block entity's `slicer` node (`slicing`, `progress`, `ticks`).
- `SiliconMachineScreen` is the shared bar layout; `CrystalPullerScreen` and `WaferSlicerScreen`
  only bind their menu type.

Data and registries:

- `data/lithmod/machines/crystal_puller.json`, `data/lithmod/machines/wafer_slicer.json`
  (the slicer carries `slice_ticks`, `input_slots`, `output_slots`).
- `data/lithmod/machine_recipes/crystal_puller.json`: 4 silicon, 1 boule, 400 ticks, no energy
  override so it takes the machine's `energy_per_tick`.
- `data/lithmod/recipe/silicon_from_smelting.json`, `silicon_from_blasting.json`.
- `data/lithmod/loot_table/blocks/{crystal_puller,wafer_slicer}.json`; both blocks are in
  `lithmod:machine`, `minecraft:mineable/pickaxe` and `minecraft:needs_stone_tool`.
- Items `lithmod:silicon`, `lithmod:silicon_boule`, `lithmod:crystal_puller`, `lithmod:wafer_slicer`
  in `ModItems`; creative tab `ModCreativeTabs.SILICON_CHAIN` (registry id `4_silicon_chain`).
- Config keys used: `wafersPerBoule` and `foupCapacity` (from `FabSettings`, wired to the
  `lithmod-local.toml` config), plus the shared `fePowerMultiplier` that scales every machine's
  `energy_per_tick`.