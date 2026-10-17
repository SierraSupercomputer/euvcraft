# M1-04 Silicon chain — Crystal Puller and Wafer Slicer

Verified source head: `ca7822c650328bc26ed04f517099ceace24be081`. DeepSeek authored the report; Nemotron supplied the quartz update and the coordinator refreshed measured evidence and corrected the test's recipe-holder types. Workers ran no commands. Original full mutation controls below ran at `477c602`; the quartz control and latest positive checks ran at this source head.

## 1. Summary

Task M1-04 delivered the first two silicon-chain machines: the Crystal Puller (silicon item to silicon boule through a plain JSON machine recipe) and the Wafer Slicer (boule plus an empty FOUP to a FOUP of wafers, where real lots first enter the game), with a pure core function `WaferSlicing.wafersFor(settings, capacity)`, 16 new GameTest cases in `SiliconGameTests` and 7 new unit tests. The latest full normal run at `ca7822c` passed after the quartz fault was restored: `BUILD SUCCESSFUL in 3m 38s`, 409 unit tests in 68 suites (0 failures/errors/skips), 732 distinct XML GameTest results: 732 passed, 0 optional skipped after failure, 0 required failures, and all 62 project case IDs present and passing. Authorship was mixed: initial fallback implementation by SpaceBunny, partial repairs by Nemotron, continued repairs by GLM after an NVIDIA 503, production frozen and reviewed by Beam at `e59`, with DeepSeek approving the fixture `3d45` and the harness `477c602`. Both cooking recipes now consume user-selected `minecraft:quartz`, and ordinary quartz lookup plus sand-to-glass behavior passed the regression test. Client visuals and missing bar assets remain disclosed M1-11 follow-ups; this is a source verification checkpoint prepared for integration. All workers were free models ($0).

## 2. Files changed

All 51 paths are the complete baseline-to-source inventory at head `ca7822c` (new = added, modified = changed; none deleted).

| Path | Change | Purpose |
| --- | --- | --- |
| docs/reports/M1-04_silicon-chain.md | new | M1-04 task report (this file), staged for coordinator integration. |
| docs/testing.md | modified | Test documentation updated for the new silicon cases. |
| src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java | new | Pure `wafersFor(settings, capacity)` slicing math (unit tested). |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | Registers the silicon blocks/menus/screens and the new GameTest types. |
| src/main/java/com/euvcraft/lithmod/mc/gametest/MachineDropGameTests.java | modified | Drop tests kept consistent with the new machine registration. |
| src/main/java/com/euvcraft/lithmod/mc/gametest/MachineGameTests.java | modified | Adjusted to shared machine/GameTest hooks. |
| src/main/java/com/euvcraft/lithmod/mc/gametest/MultiblockGameTests.java | modified | Adjusted to shared machine hooks. |
| src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java | new | The 16 new silicon-chain GameTest cases. |
| src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java | modified | Shared machine BE hooks (machineId, menu creation, definition apply/close-down). |
| src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineMenu.java | modified | Menu base supports the machine slot layout. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/BouleSlot.java | new | Boule-only input slot filter. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlock.java | new | Crystal Puller block (single, hand-fed). |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlockEntity.java | new | Crystal Puller BE: input/output slots, recipe execution via MachineRecipeRunner. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerMenu.java | new | Crystal Puller menu (slots, energy/progress bars). |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/EmptyFoupSlot.java | new | FOUP slot accepting only empty FOUPs (no wafers, no RunSpec). |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineBlock.java | new | Shared machine-block behavior for the two machines. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineScreen.java | new | Shared screen behavior for both silicon GUIs. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachines.java | new | Registration/identifier helper for the two machines. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlock.java | new | Wafer Slicer block. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlockEntity.java | new | Wafer Slicer BE: slots, `slice_ticks` progression, lot creation, save/load. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerMenu.java | new | Wafer Slicer menu (boule slot, FOUP slot, extract-only output, bars). |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/client/SiliconClientEvents.java | new | Client wiring (screen registration). |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/screen/CrystalPullerScreen.java | new | Crystal Puller GUI screen. |
| src/main/java/com/euvcraft/lithmod/mc/machine/silicon/screen/WaferSlicerScreen.java | new | Wafer Slicer GUI screen. |
| src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java | modified | Recipe execution reused by the Crystal Puller. |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | modified | Creative tab entries for the new items/machines. |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java | modified | `silicon` and `silicon_boule` item registrations. |
| src/main/resources/assets/lithmod/blockstates/crystal_puller.json | new | Blockstate definition (placeholder art). |
| src/main/resources/assets/lithmod/blockstates/wafer_slicer.json | new | Blockstate definition (placeholder art). |
| src/main/resources/assets/lithmod/items/crystal_puller.json | new | Item definition for the Crystal Puller. |
| src/main/resources/assets/lithmod/items/silicon.json | new | Item definition for silicon. |
| src/main/resources/assets/lithmod/items/silicon_boule.json | new | Item definition for silicon_boule. |
| src/main/resources/assets/lithmod/items/wafer_slicer.json | new | Item definition for the Wafer Slicer. |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | Language entries for the new items/machines. |
| src/main/resources/assets/lithmod/models/block/crystal_puller.json | new | Block model (placeholder art). |
| src/main/resources/assets/lithmod/models/block/wafer_slicer.json | new | Block model (placeholder art). |
| src/main/resources/assets/lithmod/models/item/crystal_puller.json | new | Item model for the Crystal Puller. |
| src/main/resources/assets/lithmod/models/item/silicon.json | new | Item model for silicon (placeholder). |
| src/main/resources/assets/lithmod/models/item/silicon_boule.json | new | Item model for silicon_boule (placeholder). |
| src/main/resources/assets/lithmod/models/item/wafer_slicer.json | new | Item model for the Wafer Slicer. |
| src/main/resources/data/lithmod/loot_table/blocks/crystal_puller.json | new | Block loot table. |
| src/main/resources/data/lithmod/loot_table/blocks/wafer_slicer.json | new | Block loot table. |
| src/main/resources/data/lithmod/machine_recipes/crystal_puller.json | new | Machine recipe: 4 silicon to 1 silicon_boule, 400 ticks (placeholder). |
| src/main/resources/data/lithmod/machines/crystal_puller.json | new | Machine definition with energy values (placeholder). |
| src/main/resources/data/lithmod/machines/wafer_slicer.json | new | Machine definition with energy value and `slice_ticks` (placeholder). |
| src/main/resources/data/lithmod/recipe/silicon_from_blasting.json | new | Blasting recipe producing silicon (now from `minecraft:quartz` per user QUARTZ decision). |
| src/main/resources/data/lithmod/recipe/silicon_from_smelting.json | new | Smelting recipe producing silicon (now from `minecraft:quartz` per user QUARTZ decision). |
| src/main/resources/data/lithmod/tags/block/machine.json | modified | Machine block tag updated for the new machines. |
| src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json | modified | Pickaxe-mineable tag for the new blocks. |
| src/main/resources/data/minecraft/tags/block/needs_stone_tool.json | modified | Stone-tool requirement tag for the new blocks. |
| src/test/java/com/euvcraft/lithmod/core/WaferSlicingTest.java | new | 7 JUnit tests for `wafersFor`. |

## 3. Verification

### 3.1 Commands and environment

The coordinator ran the exact command `gradlew.bat test checkCoreImports jar runGameTestServer --offline --console=plain`, plus the coordinator's init script and report routing. Latest positive verification was executed by the coordinator at `ca7822c`; the original mutation evidence retains its original `477c602` provenance. No worker ran commands. Environment: Java 25, Minecraft 26.3, NeoForge 26.3.0.69-beta, Gradle 9.2.1.

### 3.2 Final restored full run (current status)

The latest full normal run at `ca7822c`, after restoring the quartz ingredient fault, passed: `BUILD SUCCESSFUL in 3m 38s`.

- Unit: 409 tests in 68 suites, 0 failures, 0 errors, 0 skipped.
- GameTest: 732 distinct XML GameTest results: 732 passed, 0 optional skipped after failure, 0 required failures. Optional skipped cases: none.
- Project: all 62 project case IDs present and passing.

Evidence materialized by the coordinator: `build/M1-04-verification/m1-04-quartz-restored-positive-check.json`, `-unit-rows.json`, `-runtime.xml`, `-project-runtime.json`.

Historical (not current status): an earlier full positive run showed 731 passed plus 1 optional Arcforge skip; the final restored run above supersedes it.

### 3.3 Unit suites (68 classes, exact counts from the coordinator)

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| com.euvcraft.lithmod.core.ContextualRngTest | 14 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.CoreTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.DieRulesTest | 5 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.DieRulesTest$Validation | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$BuilderPattern | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$Clamping | 12 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$ComfortableCoreOverrides | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$CoreCountOptions | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$Defaults | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$Equality | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$Immutability | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$ListParsing | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$RoundTrip | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$TierYieldOverrides | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$CapacityTest | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$ImmutabilityTest | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$InvalidDataTest | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$PackingTest | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$PublicViewTest | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest$WaferTest | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.ImportCheckerTest | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.LineLoadoutTest | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineDefinitionTest$EnergyPerTick | 9 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineDefinitionTest$Parsing | 7 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineDefinitionTest$Validation | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Counts | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Energy | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$SingleItem | 5 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$SlotOrder | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Tags | 5 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Validation | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.multiblock.StructureMatcherTest | 45 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.ProcessRunTest | 10 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.ProcessRunTest$Refusals | 9 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.ProcessRunTest$Runs | 9 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.ProcessRunTest$SnapshotOps | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.RunSpecTest$CodecRoundTrip | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.RunSpecTest$Validation | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.TierRegistryTest | 34 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.WaferSlicingTest | 7 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldCalibrationTest$Accuracy | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldCalibrationTest$CumulativeRoll | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldCalibrationTest$DefectFactor | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldCalibrationTest$GammaFunctions | 7 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldMathTest$Clamping | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldMathTest$CoreCountPenalty | 9 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldMathTest$PassThreshold | 5 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldMathTest$ResolveEffectiveYield | 10 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldRollEngineTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldRollEngineTest$Judging | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldRollEngineTest$LayerStep | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.YieldRollEngineTest$Rejections | 7 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabConfigTest | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabYieldServiceTest | 11 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.ItemAssetResourceTest | 3 | 0 | 0 | 0 |
| TOTAL (68 suites) | 409 | 0 | 0 | 0 |

### 3.4 GameTest runtime

- 732 distinct XML GameTest results: 732 passed, 0 optional skipped after failure, 0 required failures at `ca7822c`.
- The framework/engine summary line labels all 732 results "required"; that is a mislabel — the per-case XML totals are authoritative.
- All 62 registered project IDs present and passing; the 16 new silicon-chain case IDs and the 4 yield cases are inside the 62.

### 3.5 Negative (mutation) testing

Original controls at `477c602` cover the unchanged slicing/machine source. Git comparison shows the subsequent production change is the two quartz recipe ingredients, with their cooking-test update and documentation.

Unit constant-zero mutation: 7 `WaferSlicingTest` cases, all 7 failed, 0 errors; restored, restoration verified, head clean (run `m1-04-negative-unit-1792238636670`). The 7 case names:

1. a capacity below one is refused
2. the default settings give the reference count of 25
3. a FOUP smaller than the yield comes back filled to its own capacity
4. the count follows a changed wafersPerBoule and stops caring about capacity above it
5. a FOUP larger than the yield comes back partially filled
6. a null settings object is refused
7. a boule fills a FOUP of the same size exactly

Runtime: all 16 new IDs failed in their group's run, each group restored, head clean. Groups: `guards` (`m1-04-negative-guards-1792238999367`), `lot` (`m1-04-negative-lot-1792238651392`), `slots` (`m1-04-negative-slots-1792238767439`), `snapshots` (`m1-04-negative-snapshots-1792238877109`).

| New GameTest ID | Mutant group that made it fail |
| --- | --- |
| lithmod:wafer_slicer_pauses_without_energy | guards |
| lithmod:wafer_slicer_save_load_round_trip | guards, snapshots |
| lithmod:crystal_puller_runs_its_recipe | guards |
| lithmod:silicon_machines_expose_no_item_handler | guards |
| lithmod:crystal_puller_rechecks_its_inputs | guards |
| lithmod:wafer_slicer_rechecks_its_inputs | guards |
| lithmod:silicon_energy_follows_machine_definitions | guards |
| lithmod:silicon_machines_fail_closed_without_a_definition | guards |
| lithmod:crystal_puller_resumes_only_the_same_recipe | guards, snapshots |
| lithmod:silicon_cooking_recipes_load_and_match | guards at `477c602`; quartz at `ca7822c` |
| lithmod:wafer_slicer_produces_a_lot | lot |
| lithmod:wafer_slicer_lot_takes_layer_one | lot |
| lithmod:wafer_slicer_lot_size_follows_the_foup_capacity | lot |
| lithmod:wafer_slicer_refuses_a_used_foup | slots |
| lithmod:wafer_slicer_menu_layout_and_shift_click | slots |
| lithmod:wafer_slicer_foup_slot_client_boundary | slots |

Quartz-specific control `m1-04-negative-quartz-1792250280262`: deliberately changed both cooking ingredients back to sand; `lithmod:silicon_cooking_recipes_load_and_match` failed as expected. Both files were restored byte-for-byte, Git remained clean, and the full positive run above followed restoration.

### 3.6 Review proof and model authorship

Authorship was MIXED: initial fallback implementation by SpaceBunny, partial repairs by Nemotron, continued repairs by GLM after an NVIDIA 503. Non-test Java machine code was reviewed by Beam at `e59` and remains unchanged. The quartz recipe/test delta was written by Nemotron before NVIDIA503; the coordinator repaired its compiler-reported generic types. Beam429 required a fresh independent DeepSeek review, which approved this exact quartz source delta. DeepSeek approved the test-fixture delta `3d45` and the harness `477c602` after a Beam 429 outage, and authored this report as the free fallback. All workers were free-only models ($0); no paid model and no automatic router were used. The coordinator owns verification, integration and commits.

## 4. Spec conformance

| Item | Status | Evidence |
| --- | --- | --- |
| Items silicon and silicon_boule, each with a model, item definition, language entry and creative tab entry (placeholder art) | met | ModItems/ModCreativeTabs registrations, assets items/models, lang entry; placeholder art, not visually verified |
| Smelting + blasting recipes turning `minecraft:quartz` into silicon; machine recipe 4 silicon to 1 silicon_boule, 400 ticks (placeholders) | met | machine_recipes/crystal_puller.json present (4 to 1, 400 ticks); recipe/silicon_from_{smelting,blasting}.json now use `minecraft:quartz` per user QUARTZ decision |
| Machine definitions data/lithmod/machines/crystal_puller.json and wafer_slicer.json with energy values and slicer `slice_ticks` (placeholders) | met | both definitions present with placeholder values |
| Crystal Puller single block on FabMachineBlockEntity, input slots, one output slot, energy bar and progress bar, using MachineRecipeRunner, hand-fed only | partly not met | block/BE/menu exist and the recipe runs end to end (`crystal_puller_runs_its_recipe`); energy/progress bars implemented in code but not visually verified (missing bar assets) |
| Wafer Slicer single block with boule slot, input FOUP slot accepting only FOUPs with no wafers and no RunSpec, extract-only output FOUP slot, every FOUP read with FoupItems.trustedContentsOf | met | `wafer_slicer_refuses_a_used_foup`, `wafer_slicer_foup_slot_client_boundary`; BouleSlot/EmptyFoupSlot |
| Slicer progression: both inputs present, energy available, empty output, `slice_ticks` ticks; on completion consumes one boule and the empty FOUP and outputs a FOUP of the same item type with a fresh random lotId, the input FOUP's stored capacity, no RunSpec and min(wafersPerBoule, capacity) layer-0 wafers; partial fill when yield < capacity | met | `wafer_slicer_produces_a_lot`, `wafer_slicer_lot_size_follows_the_foup_capacity`, `wafer_slicer_lot_takes_layer_one`, `wafer_slicer_pauses_without_energy` |
| Pure function wafersFor(settings, capacity) in core, unit tested | met | WaferSlicing.java; WaferSlicingTest (7 tests) |
| Lots do not record a substrate generation in M1; say so in the handoff | met | see §8 Handoff |
| JUnit for wafersFor | met | WaferSlicingTest, 7 tests |
| GameTests: lot of expected size readable via FoupItems.trustedContentsOf; FabYieldService applies layer 1 at T1 for the logic die type; used/has-RunSpec FOUP refused; pause without energy and resume; NBT round trip mid-progress; Crystal Puller recipe end to end; neither machine exposes an item handler capability | met | the 16 silicon-chain cases, all passing and mutation-evidenced |
| Done when: build and all tests pass with counts that add up | met | final restored run §3.2: 409/68, 732/732, 62/62 |
| Done when: a boule becomes a usable lot in a game test | met | `wafer_slicer_lot_takes_layer_one` |
| Done when: report at docs/reports/M1-04_silicon-chain.md | partly not met | the report path is in the change set; coordinator integration/merge pending, so no completion claim |
| Out of scope: crafting recipes for the machine blocks (M1-11), masks, the Process Cell | met | none included |

## 5. Not verified

- GUI energy/progress bars depend on assets that are known missing, so the rendered UI is NOT visually verified and no client walkthrough has been recorded; working rendered bars are not claimed.
- The two screen classes' client visuals were never loaded.
- A real mid-run `/reload` is untested; the GameTests swap the loaded registry synchronously (reflective) and restore it in `finally`.
- The three final M1-12 processes have not run; they are a separate phase and are not part of the M1-04 gate.
- Source and runtime quartz checks passed at `ca7822c`. The reviewed source is prepared for a separately tested integration candidate; this report does not claim the overall M1 gate or a client walkthrough is complete.

## 6. Deviations

| Spec / expectation | What was done | Why |
| --- | --- | --- |
| Recipes turn `minecraft:sand` into silicon | both now use `minecraft:quartz` | user explicitly selected Quartz; ordinary lookup is tested and sand still smelts to glass |
| Tests exercise a changed loaded registry | synchronous reflective registry swap restored in `finally` | loaders expose no setter and the loader file is outside the task scope |
| Beam: add a wafer-count clamp | not added | redundant with the existing FabSettings clamp |
| Restore a run against the current definition's cost | not adopted | a resumed run must cost what it would have cost uninterrupted |
| Assert exact 200/100 cooking times | asserted as positive values only | the values remain in JSON |
| Shift-click test reused one FOUP stack | corrected to a fresh FOUP | quick-move mutates the stack, so the product probe proved nothing |
| Sand cooking collided with vanilla glass | quartz is tested with ordinary three-argument recipe lookup | verifies the real selected recipe, output, cooking time, and preservation of sand-to-glass |

## 7. Open questions

None. The ingredient question was resolved by the user selecting Quartz.

## 8. Handoff

- No lot substrate generation exists in M1 (intentional per the task); add it outside this pack if wanted.
- API surface (source contracts extracted from source, no invented parameters; multiline signatures abbreviated):
  - `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java`: `public final class WaferSlicing { public static int wafersFor(FabSettings settings, int capacity) }`.
  - `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlockEntity.java`: `MACHINE_ID = "wafer_slicer"`, `BOULE_SLOT = 0`, `FOUP_SLOT = 1`, `OUTPUT_SLOT = 2`, `SLICE_TICKS_PARAM = "slice_ticks"`; `WaferSlicerBlockEntity(BlockPos, BlockState)`; `protected String machineId()`; `public static @Nullable FoupContents acceptedFoup(ItemStack)`; `public static boolean acceptsFoupStack(ItemStack)`; `serverTick()`, `resetSlice()`, `sliceTicks()`, `energyPerTick()`, `isSlicing()`, `isPaused()`, `sliceProgress()`, `sliceTicksRemaining()`, `static Item bouleItem()`, `saveAdditional(ValueOutput)`, `loadAdditional(ValueInput)`, `createMachineMenu(int, Inventory, ...)`; inherits FabMachineBlockEntity's public API.
  - `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlockEntity.java`: `MACHINE_ID = "crystal_puller"`, `INPUT_SLOT = 0`, `OUTPUT_SLOT = 1`; `CrystalPullerBlockEntity(BlockPos, BlockState)`; `protected String machineId()`; `createMachineMenu(int, Inventory, ...)`.
  - `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java`: public class present; only its constructor opening line is captured; the full body was not extracted and is not invented here.
- Data/config real paths: `src/main/resources/data/lithmod/machines/crystal_puller.json`, `.../machines/wafer_slicer.json`, `.../machine_recipes/crystal_puller.json`, `.../recipe/silicon_from_smelting.json`, `.../recipe/silicon_from_blasting.json` (both use `minecraft:quartz`).
- Follow-ups not done in this pack (M1-11): crafting recipes for the machine blocks; a crafted/given empty-FOUP server-initialization path; missing machine bar assets.
- Three final M1-12 processes not run (separate phase).
- No overall completion or merge until the distinct-ingredient recipe decision and the report integration are applied by the coordinator.
