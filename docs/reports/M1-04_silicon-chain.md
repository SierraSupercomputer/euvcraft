# M1-04: Silicon chain, Crystal Puller and Wafer Slicer

- Date: 2026-10-17 (repair pass, written into this same report file rather than a `_r2`
  copy, because the coordinator's allowed-file list names exactly this path)
- Branch: `m1-04-silicon-chain` (isolated worktree `.orchestrator/worktrees/euvcraft/m1-04-silicon-chain`).
  Commit: none from me. This agent cannot execute commands, so every file below is an
  uncommitted working-tree change and the coordinator owns the commit. Baseline commit recorded
  in `build/M1-04-start-evidence.json`: `4b5f4a74f116b1759898cd690ebfb3af837eb32f` (taken from
  that evidence file, not re-measured by me).
- Java version used: none. No JVM was started by this agent.
- Build and test commands run by me: none. Section 3 lists what the coordinator must run.
- `git worktree list`, `git branch --show-current`, `git status --short`: not run (no shell).
  The coordinator must take them before and after committing.

## 1. Summary

This is the repair pass for M1-04. It fixes the two compile blockers, corrects the recipe
runner so a boule costs the recipe's four silicon and so a running recipe re-validates its
inputs on every tick, makes both machines take their energy numbers and the slice length from
the loaded `MachineDefinition` instead of Java literals, moves the slicer's two validating
slots into `FabMachineMenu`'s own machine-slot count so shift-click routing works, makes the
slicer re-check its boule and FOUP on every tick, and replaces the slicer's FOUP slot rule
with a documented boundary between the public client view and the trusted server lot. The
game test suite grew from seven to fourteen tests and the one unit test file from seven to
seven cases with the misleading claims removed. Nothing here was executed: no compile, no
unit test, no game test, so every verification line in section 3 says "not run", and the test
counts and the negative-control executions are left explicitly pending.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java` | modified | javadoc corrected: a FOUP smaller than the yield comes back **full**, a FOUP larger comes back partially filled |
| `src/test/java/com/euvcraft/lithmod/core/WaferSlicingTest.java` | modified | removed the tautological "negative control" lines; each case now asserts the property its name claims |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachines.java` | new (first pass) | blocks, block entity types, menu types, `Capabilities.Energy.BLOCK` only |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineBlock.java` | new (first pass) | ticker calls `serverTick()`, use opens the menu |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlock.java` | new (first pass) | Crystal Puller block |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlockEntity.java` | modified | removed `FALLBACK_CAPACITY`/`FALLBACK_MAX_RECEIVE`; takes its buffer numbers from the loaded definition |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerMenu.java` | new (first pass) | input slot plus extract-only output slot, base helpers only |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlock.java` | new (first pass) | Wafer Slicer block |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlockEntity.java` | modified | added the missing `BlockState` import; no fallback constants; `slice_ticks` required; per-tick input revalidation; trusted gameplay read split from the public client-view read |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerMenu.java` | modified | the three slots are added in `addMachineSlots()` through `addCustomSlot`; deleted the copied `quickMoveStack` override and the extra client container |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/BouleSlot.java` | new (first pass) | takes boules only |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/EmptyFoupSlot.java` | modified | `mayPlace` uses the public client-view rule; javadoc states the real boundary |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineScreen.java` | new (first pass) | shared bar layout |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/screen/{CrystalPuller,WaferSlicer}Screen.java` | new (first pass) | menu-type bindings |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/client/SiliconClientEvents.java` | new (first pass) | `Dist.CLIENT` screen registration |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java` | modified | 7 → 14 tests: puller counts and input removal/swap, slicer input removal/swap and blocked output, lot size vs capacity, menu layout and shift-click, the client-view boundary, definition-driven energy, cooking recipe schema |
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | modified | data constructor for real machines, `applyDefinition`/`refreshDefinition`, in-place `MachineEnergy.reconfigure`, `DATA_CAPACITY` mirrored from the real buffer |
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineMenu.java` | modified | added `addCustomSlot(Slot)` and `machineSlots()`; the three existing helpers delegate to the hook |
| `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java` | modified | `consume` shrinks by the recipe's declared counts; `inputsMatch` re-validates before every tick and again before the commit; recipe identity kept across reloads |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java` | modified | added the missing final class brace (compile fix) |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java`, `EUVCRAFTMod.java` | new/modified (first pass) | items and registration, unchanged in this pass |
| `src/main/resources/data/lithmod/recipe/silicon_from_{smelting,blasting}.json` | modified | `result` is now the vanilla 26.3 object form `{"id": "lithmod:silicon"}` |
| `src/main/resources/data/lithmod/{machines,machine_recipes,loot_table,tags}/…` | new/modified (first pass) | machine definitions, puller recipe, loot tables, tags, unchanged in this pass |
| `src/main/resources/assets/lithmod/**` | new (first pass) | item definitions, models, blockstates, language, unchanged in this pass |
| `docs/testing.md` | modified | silicon chain unit and game test tables rewritten for the 14 tests and the corrected wording |
| `docs/reports/M1-04_silicon-chain.md` | modified | this report |

## 3. Verification

No command was run by this agent. No build output, no test output and no game test output
exists to paste, so every count below is unmeasured.

```powershell
.\gradlew.bat compileJava --offline
.\gradlew.bat compileTestJava --offline
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
.\gradlew.bat runGameTestServer --offline
```

Then take the counts from `build/test-results/test/*.xml`.

| test class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `com.euvcraft.lithmod.core.WaferSlicingTest` | 7 written, not run | not run | not run | not run |
| `com.euvcraft.lithmod.mc.ItemAssetResourceTest` (existing, 4 new items) | not run | not run | not run | not run |
| every other existing unit test class | not run | not run | not run | not run |
| total | no run produced counts | | | |

Game tests: 14 registered, **0 run**. Negative controls: 0 executed. Both are the
coordinator's; section 5 says why.

| game test | status |
|---|---|
| `lithmod:wafer_slicer_produces_a_lot` | not run |
| `lithmod:wafer_slicer_lot_takes_layer_one` | not run |
| `lithmod:wafer_slicer_refuses_a_used_foup` | not run |
| `lithmod:wafer_slicer_pauses_without_energy` | not run |
| `lithmod:wafer_slicer_save_load_round_trip` | not run |
| `lithmod:wafer_slicer_rechecks_its_inputs` (new) | not run |
| `lithmod:wafer_slicer_lot_size_follows_the_foup_capacity` (new) | not run |
| `lithmod:wafer_slicer_menu_layout_and_shift_click` (new) | not run |
| `lithmod:wafer_slicer_foup_slot_client_boundary` (new) | not run |
| `lithmod:crystal_puller_runs_its_recipe` | not run |
| `lithmod:crystal_puller_rechecks_its_inputs` (new) | not run |
| `lithmod:silicon_machines_expose_no_item_handler` | not run |
| `lithmod:silicon_energy_follows_machine_definitions` (new) | not run |
| `lithmod:silicon_cooking_recipe_files_match_the_vanilla_schema` (new) | not run |

Compile evidence that does exist, and its exact limits: the coordinator's temporary probe
added only the `ModCreativeTabs` brace and the `BlockState` import, compiled **main and test
sources** with them, and then restored the original bytes. That is evidence that those two
defects were the only *syntax/import* blockers of the first pass. It is not a build of the
sources now on disk, and this report does not claim otherwise; the compile of this repair pass
is pending.

## 4. Spec conformance

Repair scope, item by item. "met" means the code says the thing; "not verified" is recorded
where no run backs it.

| item | met/partly/not met | evidence |
|---|---|---|
| 1. `ModCreativeTabs` closing brace | met | `ModCreativeTabs.java` ends `private ModCreativeTabs() { }` + `}`; coordinator probe compiled with it |
| 1. `WaferSlicerBlockEntity` `BlockState` import | met | `import net.minecraft.world.level.block.state.BlockState;`; used by the constructor parameter |
| 1. No other compile error known | not verified | the probe predates this pass; the coordinator's `compileJava`/`compileTestJava` decides |
| 2. Consume the recipe's counts, not one item | met | `MachineRecipeRunner.consume(MachineRecipe, List<ItemStack>)` shrinks by `recipe.ingredientCounts().get(slot)` |
| 2. Re-validate before every progress tick | met | `advance` begins `if (!inputsMatch(current, inputs)) { reset(); return false; }` |
| 2. Re-validate before committing output | met | `advance` repeats `inputsMatch` after `progress` reaches `ticks` and before `consume`/`outputWriter` |
| 2. Recipe identity stable across reloads | met | `current` is never re-matched while running; class javadoc states it; `tick` only consults the candidate list when idle |
| 2. Abort without free output | met | the two `reset()` paths return `false` and write nothing |
| 2. `pullerRunsItsRecipe` no longer asserts 3 silicon left | met | asserts the input slot is empty after one boule |
| 2. Regression coverage for counts, removal and swap | met (not run) | `crystal_puller_rechecks_its_inputs`: 5 in → 1 boule with 1 left; 3 in → never starts; removal and swap mid-run reset with no FE spent |
| 3. No duplicated 20 000/2 000 in real BEs | met | `CrystalPullerBlockEntity.FALLBACK_*` and all three `WaferSlicerBlockEntity.FALLBACK_*` deleted |
| 3. Same `MachineEnergy` instance, small reconfigure | met | `MachineEnergy.reconfigure(int,int)` writes the inherited `capacity`/`maxInsert`; the `exposed` delegate is untouched |
| 3. Stored FE preserved when capacity shrinks | met | `reconfigure` never touches `energy`; the install's `SimpleEnergyHandler` documents energy above a reduced capacity and returns 0 from `insert` |
| 3. Init from the definition, refresh on tick and load | met | new data constructor calls `refreshDefinition()`; `serverTick` and `loadAdditional` call it; `DATA_CAPACITY` is read back from the buffer |
| 3. Fail closed without data | met | real machines start at capacity 0 / insert limit 0; the slicer returns `sliceTicks() == 0` and refuses to start or keep a run |
| 3. Slicer refresh without the generic runner | met | `WaferSlicerBlockEntity.serverTick` calls `refreshDefinition()` only, never `super.serverTick()` |
| 3. `slice_ticks` required | met | `definition.param(SLICE_TICKS_PARAM).orElse(0)`; `sliceTicks() <= 0` resets and stops |
| 3. Tests for buffer, limit and a definition update | met (not run) | `silicon_energy_follows_machine_definitions` |
| 3. Hopper guards and no-extraction kept | met | `canPlaceItem`/`canTakeItem` false; `WithdrawNothing` untouched; `silicon_machines_expose_no_item_handler` now also asserts extraction through the capability is refused |
| 4. Smallest base hook for a custom slot + machine `Container` | met | `FabMachineMenu.addCustomSlot(Slot)` and `machineSlots()`; `addMachineSlot`, `addFoupSlot`, `addOutputSlot` all delegate to the hook |
| 4. Slicer adds its slots in `addMachineSlots`, base routing | met | `WaferSlicerMenu.addMachineSlots` adds `BouleSlot`, `EmptyFoupSlot`, `addOutputSlot`; override and extra client container deleted |
| 4. No unused counters/getters/aliases | met | the slicer menu has two constructors and one method, nothing else |
| 4. Slot order/count/predicate and shift-click assertions | met (not run) | `wafer_slicer_menu_layout_and_shift_click`: 39 slots, index parity on both menus, machine slots bound to the slicer, four predicate pairs, five shift-click moves |
| 5. Slicer re-checks boule and FOUP every tick | met | `inputsValid()` is the first check in `advance`, and `complete()` re-checks both plus the output slot |
| 5. Pause with progress intact and no FE when blocked | met | the output check precedes the energy draw in `advance` |
| 5. Consume exactly one of each only on success | met | `complete()` writes the product, then clears the FOUP slot and shrinks the boule by one |
| 5. Input-removal, swap and blocked-output coverage | met (not run) | `wafer_slicer_rechecks_its_inputs`, plus the two existing persistence tests |
| 6. No false parity claim | met | `EmptyFoupSlot` javadoc states the boundary; `acceptsFoupStack` is documented as prediction only |
| 6. Authoritative reads stay trusted | met | `acceptedFoup` reads through `FoupItems.trustedContentsOf` and refuses client views; it is what the tick and `complete()` call |
| 6. Client prediction uses only the public count | met | `acceptsFoupStack` reads `contents.publicView().waferCount()` for a client view |
| 6. No per-wafer exposure or warning spam | met | no roll or flag is read in either method; `trustedContentsOf` is not called from `mayPlace`, so a normal placeholder logs nothing |
| 6. Server refusal and client-view rejection tested separately | met (not run) | `wafer_slicer_refuses_a_used_foup` (server) and `wafer_slicer_foup_slot_client_boundary` (client view) |
| 7. Vanilla result-object form | met | both files carry `"result": { "id": "lithmod:silicon" }`, copied from the coordinator's `build/api-reference/iron_ingot_from_{smelting,blasting}_raw_iron.json` |
| 7. Recipe-manager lookup in a game test | not met | no readable 26.3 recipe API in this worktree; see sections 5 and 6 |
| 7. No new binary assets or dependencies | met | placeholder models only; no dependency change |
| 8. Documentation wording | met | `WaferSlicing` javadoc, `WaferSlicingTest` display names, `docs/testing.md` all say small FOUP = full, large FOUP = partial |
| 8. No tautological "negative control" claims | met | the two tautological lines in `WaferSlicingTest` are gone; the surviving "negative control" labels are inputs that are refused, not executed mutants |
| 8. No fabricated open question about 4 silicon | met | section 7 |
| Report shape | met | this file, headings in the required order |

## 5. Not verified

- Nothing was executed: no compile, no unit test, no game test, no client run. Every row above
  rests on reading the source.
- No build of the current sources has run. The coordinator's probe compiled the *first* pass
  plus the two syntax fixes and then restored the bytes; this pass adds code that has never
  been through a compiler. Section 3's counts are therefore pending, not zero.
- No negative control was executed for any new test. The coordinator owns deliberate mutation
  runs; the "negative control" comments in the tests mark inputs that must be refused, not
  mutants that were broken and reverted.
- A real recipe-manager lookup was not written. The installed 26.3 sources are a jar under
  `build/moddev/artifacts/` and the Gradle cache is outside the paths this agent may read, so
  any `RecipeManager`/`RecipeType`/`RecipeHolder` call would have been a guess. A wrong guess
  breaks the whole build, which is worse than the gap. `silicon_cooking_recipe_files_match_the_vanilla_schema`
  checks the two files' key set and result shape against the vanilla example; the datapack load
  itself is still to be confirmed in the `runGameTestServer` log, which names any recipe that
  failed to parse.
- A definition *reload* was not exercised. There is no reload seam a game test can reach:
  `MachineDefinitionLoader.apply` is protected and the registry is a static. The
  `applyDefinition` path that a reload, a tick and a load all share is tested with a
  hand-built `MachineDefinition`.
- The two screen classes and `SiliconMachineScreen` were never loaded; the client binding is a
  single `RegisterMenuScreensEvent` listener.
- Whether my three framework edits disturb the existing M1-02 and M1-03 game tests was not
  checked by running them. What I read: `test_machine.json` declares `energy_capacity 10000`
  and `max_receive 1000`, identical to `TestMachineBlockEntity`'s fallbacks, so the
  definition-driven buffer changes nothing for it; both test recipes declare `count: 1` per
  input, so count-based consumption still empties those stacks; and no existing test removes
  an input mid-run (`MachineGameTests.saveLoadRoundTrip` wipes and reloads before it ticks).
  `ChipGameTests` pins creative tab entry counts and this pass added no entries.
- `docs/m0-foundation.md` was not updated; the task did not ask for it.

## 6. Deviations

| spec says | what I did | why |
|---|---|---|
| repair authorizes only `FabMachineBlockEntity.java`, `FabMachineMenu.java`, `MachineRecipeRunner.java` outside the machine package | exactly those three framework files, and nothing else outside the allowed list | the three repairs that reach the framework (consume counts, the definition refresh, the custom-slot hook) cannot be done inside `mc/machine/silicon`; `MachineRecipeRunner` and `FabMachineBlockEntity` are shared with M1-02, which is why every existing API was kept and only extended |
| "prefer keeping the SAME `MachineEnergy` instance" | `MachineEnergy.reconfigure(int,int)` mutates the inherited protected fields | the `exposed` capability delegate holds a reference to the buffer, so replacing it would have invalidated every cached capability query; in-place reconfiguration also matches the install's documented "stored energy may exceed a reduced capacity" contract |
| `FabMachineBlockEntity` change should be "minimal" | added one constructor overload, two methods and one package-private buffer method | the overload is what removes the Java literals from the real machines; `applyDefinition` is the single place a definition reaches a machine and is what the game test calls to simulate a data change; `refreshDefinition` is the tick/load/slicer hook; `reconfigure` is the only way to reach the inherited fields |
| "prefer keeping the SAME `MachineEnergy`" (Beam's alternative was buffer replacement) | in-place, as the task text and the installed library both allow | Beam's preflight noted the replacement suggestion was advisory |
| a recipe-manager lookup in a game test "if feasible" | a JSON schema check instead | see section 5; the alternative risked the build |
| `MachineRecipeRunner` revalidation resets the run | resets, rather than pausing, when inputs go invalid | a run whose ingredients were taken away is not a run that can be resumed; the task says abort/reset without free output |
| `acceptedFoup` documented as the trusted read | it now also refuses a client view outright, before the trusted read | `trustedContentsOf` sanitises a client view into a fresh empty lot, which would have let a placeholder pass as an empty FOUP; every authoritative read still goes through the trusted read |

## 7. Open questions

1. Should `MachineRecipeRunner` also re-validate after `hasRoomFor` on the tick that completes,
   that is three input checks per completing tick rather than two? Not a product decision; the
   coordinator may prefer the cheaper form.
2. The Crystal Puller and Wafer Slicer definitions both declare `energy_capacity 20000` and
   `max_receive 2000`. They are placeholders per the task. If a datapack override is wanted as
   evidence that the numbers really are per machine, one of the two files has to change, and
   that is a data decision rather than a repair.
3. Whether the recipe-manager lookup for the two cooking recipes is wanted once someone can
   compile: with a working compiler it is a few lines, and until then the datapack load is only
   visible in the game test server log.

## 8. Handoff

Extracted from the source written by this task and this repair.

Core:

- `com.euvcraft.lithmod.core.WaferSlicing.wafersFor(FabSettings settings, int capacity) -> int`
  returns `min(settings.wafersPerBoule(), capacity)`; null settings throws
  `NullPointerException`, capacity below one throws `IllegalArgumentException`.

Machine framework (extended by this repair, existing signatures unchanged):

- `FabMachineBlockEntity(BlockEntityType<?>, BlockPos, BlockState, int inputSlots, int outputSlots)`
  — new overload for a real machine: the buffer starts at capacity 0 / insert limit 0 and is
  sized from the loaded definition at the end of the constructor.
- `public final void applyDefinition(MachineDefinition definition)` — resizes the real buffer,
  sets the runner's machine cost and mirrors the buffer capacity into `DATA_CAPACITY`.
- `protected final void refreshDefinition()` — re-applies the loaded definition, does nothing
  when none is loaded. Called by the base `serverTick`, by `loadAdditional` and by the slicer's
  own tick.
- `MachineEnergy.reconfigure(int capacity, int maxInsert)` — package-private, in place, stored
  energy untouched.
- `FabMachineMenu.addCustomSlot(Slot)` and `FabMachineMenu.machineSlots()` — both
  `protected final`; `addMachineSlot`, `addFoupSlot` and `addOutputSlot` delegate to the first.
- `MachineRecipeRunner.inputsMatch(MachineRecipe, List<ItemStack>)` — new public static, the
  per-slot rule `firstMatch` and the tick both use.

Silicon chain:

- `CrystalPullerBlockEntity`: `MACHINE_ID = "crystal_puller"`, `INPUT_SLOT = 0`,
  `OUTPUT_SLOT = 1`. No fallback constants.
- `WaferSlicerBlockEntity`: `MACHINE_ID = "wafer_slicer"`, `BOULE_SLOT = 0`, `FOUP_SLOT = 1`,
  `OUTPUT_SLOT = 2`, `SLICE_TICKS_PARAM = "slice_ticks"`. Public members:
  `static @Nullable FoupContents acceptedFoup(ItemStack)` (trusted; null unless the stack is a
  FOUP carrying a real, empty, unstarted lot), `static boolean acceptsFoupStack(ItemStack)`
  (slot prediction; public wafer count for a client view, real lot otherwise),
  `static Item bouleItem()`, `int sliceTicks()` (0 when there is no usable definition),
  `int energyPerTick()` (0 when no definition), `boolean isSlicing()`, `boolean isPaused()`,
  `int sliceProgress()`, `int sliceTicksRemaining()`, `void resetSlice()`, and a
  `serverTick()` that replaces the base one. Saved state lives under the block entity's
  `slicer` node (`slicing`, `progress`, `ticks`).
- `WaferSlicerMenu(int, Inventory, FabMachineBlockEntity)` and
  `WaferSlicerMenu(int, Inventory, RegistryFriendlyByteBuf)`; slots are added only in
  `addMachineSlots()`.
- Lots produced by the slicer carry **no substrate generation and no run spec**. A lot is only
  "started" once `FabYieldService.applyLayerStep` freezes a `RunSpec` at layer 1; M1 adds no
  field for where a boule was grown.

Data, registries and config:

- `data/lithmod/machines/crystal_puller.json` (20000 FE, 2000 per insert, 20 FE/tick),
  `data/lithmod/machines/wafer_slicer.json` (20000 FE, 2000 per insert, 30 FE/tick,
  `slice_ticks: 200`); both also declare `input_slots`/`output_slots`.
- `data/lithmod/machine_recipes/crystal_puller.json`: 4 silicon, 1 boule, 400 ticks, no energy
  override, so it takes the machine's `energy_per_tick`.
- `data/lithmod/recipe/silicon_from_smelting.json` (200 ticks) and
  `silicon_from_blasting.json` (100 ticks): sand to `{"id": "lithmod:silicon"}`.
- Items `lithmod:silicon`, `lithmod:silicon_boule`, `lithmod:crystal_puller`,
  `lithmod:wafer_slicer`; creative tab `ModCreativeTabs.SILICON_CHAIN`
  (registry id `4_silicon_chain`).
- Config keys used: `wafersPerBoule` and `foupCapacity` from `FabSettings`, plus the shared
  `fePowerMultiplier` that scales every machine's `energy_per_tick`.
