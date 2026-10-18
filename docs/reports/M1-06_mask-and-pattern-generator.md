# M1-06: Mask and Pattern Generator

Independent final reviews approved `8a6c5a2430c4c9e6f0db29854c37ebcdf0427bf9`; both records are listed below. The coordinator attached review documentation without changing code verified at `433a8f59533137787b51fcf7cce037f3e34bfa5b`.

- Date: October 17, 2026 (client date). Branch: `orchestrator/euvcraft-m1-06`. Verified code revision `433a8f5` (`433a8f59533137787b51fcf7cce037f3e34bfa5b`), base `5002607` (`50026071e1df5f8e92af0be24bf779d428ef639f`). `433a8f5` is the verified code revision this report documents, not the current documentation branch tip.
- Toolchain: Java 25, Minecraft 26.3, NeoForge 26.3.0.69-beta, Gradle 9.2.1.
- Start and end state: the base and branch values are retained in the coordinator's task metadata, and the literal start worktree-list/status transcript was **not retained**, so no earlier command or git output is reconstructed here. The end verification state was clean at the verified revision `433a8f5`; the worktree listing recorded in the evidence is the actual end listing with the home directory redacted. No fresh command output is reconstructed.

## 1. Summary

- Core. `MaskData` requires a die type that is one of the bare `DieTypes.ALL` names, a `tierIndex` of at least 1 with no upper cap, and `usesRemaining` of 0 or more; `consumeUse()` returns a new record with one use fewer and never mutates, `isSpent()` is true only at zero, and consuming a spent mask throws. `MaskRules.canPattern(...)` is the Gen 1 mask-shop gate: the **tier** must exist in both the die-rules and the generation-matrix tier registries, the die type is separately validated against the exact `DieTypes.ALL` and by `DieRules.canMake`, and then a real `GenerationMatrix` decision runs for the `mask_shop`. `MaskRules` takes the installed generation as its third argument; the concrete machine passes `1`. The task prose and the source disagree on the matrix argument order; the source wins (see §6).
- Items. `blank_mask` is stackable (64) and `mask` is stack size 1 with no default design, carrying a public persistent component (`lithmod:mask_data`) networked through its registered stream codec; a present component's `uses_remaining` defaults to 0, but a bare mask has no component at all and is refused. Three translatable public tooltip lines (die type, tier, uses remaining) and an own MASKS creative tab with five entries (blank mask, three configured sample masks, generator).
- Generator. A single hand-fed block on the generic `FabMachineBlockEntity` and `MachineRecipeRunner`, with two ordinary ingredient inputs and one output. The Gen 1 gate runs before FE, input and progress; `usesRemaining` is stamped from the `maskUses` config value only at completion; a refusal produces no output and a synced status the menu shows. Energy-only: no item or fluid capability, no hopper access, external extraction refused.
- Persistence and verification. The running recipe snapshot is a private `spec_text` JSON string, so NbtOps cannot rewrite object keys or numerics, and a broken new key is authoritative rather than falling back to a stale legacy identity. Verification: 71 unit suites / 450 tests / 0 failures, errors or skips, and 751 passing game-test case records that include all 81 registered project IDs; every new unit method (41) and new server case (19) has an executed negative control. These measurements are from the pre-integration source checkpoint.

## 2. Files changed

| Path | Change | Purpose |
| --- | --- | --- |
| `src/main/java/com/euvcraft/lithmod/core/MaskData.java` | A | Mask record: die type, tier index, uses remaining; validation, `consumeUse()`, `isSpent()`. |
| `src/main/java/com/euvcraft/lithmod/core/MaskRules.java` | A | Gen 1 mask-shop gate combining die rules with the `mask_shop` generation matrix. |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | M | Mod entry point wiring the new registrations. |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/MaskGameTests.java` | A | Server game tests for the mask items, tooltip, creative tab and pattern generator. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | M | Shared machine base: identity-default `filterRecipes`/`prepareOutput` hooks; `DATA_COUNT` stays 4. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskDataCodec.java` | A | Persistent codec and stream codec for `MaskData`. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskMachineClientEvents.java` | A | Client-side registration for the mask machine menu and screen. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskMachines.java` | A | Registration of the generator block, block entity, menu and related content. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskTooltip.java` | A | Builds the three translatable mask tooltip lines. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskTooltipAppenders.java` | A | Tooltip appenders attached to the mask item. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/PatternGeneratorBlock.java` | A | Generator block; `useItemOn` opens the machine menu. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/PatternGeneratorBlockEntity.java` | A | Machine logic: recipe filtering, Gen 1 gate, status slot, completion-time stamping, server tick. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/PatternGeneratorMenu.java` | A | Menu with the extra status data slot and the machine slots. |
| `src/main/java/com/euvcraft/lithmod/mc/machine/mask/PatternGeneratorScreen.java` | A | Client screen for the generator (not visually verified; see §5). |
| `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java` | M | Adds `setMachineEnergyPerTick` so a restored paid run keeps its frozen cost before adoption. |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java` | M | Own MASKS tab: blank mask, three configured sample masks, generator. |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModDataComponents.java` | M | Registers the mask data component. |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java` | M | Registers `blank_mask` and `mask`. |
| `src/main/resources/assets/lithmod/blockstates/pattern_generator.json` | A | Blockstate for the generator. |
| `src/main/resources/assets/lithmod/items/blank_mask.json` | A | Modern client item definition for the blank mask. |
| `src/main/resources/assets/lithmod/items/mask.json` | A | Modern client item definition for the mask. |
| `src/main/resources/assets/lithmod/items/pattern_generator.json` | A | Modern client item definition for the generator. |
| `src/main/resources/assets/lithmod/lang/en_us.json` | M | Item names, tooltip lines and tab title. |
| `src/main/resources/assets/lithmod/models/block/pattern_generator.json` | A | Placeholder block model on vanilla textures. |
| `src/main/resources/assets/lithmod/models/item/blank_mask.json` | A | Placeholder item model on vanilla textures. |
| `src/main/resources/assets/lithmod/models/item/mask.json` | A | Placeholder item model on vanilla textures. |
| `src/main/resources/assets/lithmod/models/item/pattern_generator.json` | A | Placeholder item model on vanilla textures. |
| `src/main/resources/data/lithmod/loot_table/blocks/pattern_generator.json` | A | Block loot table for the generator. |
| `src/main/resources/data/lithmod/machines/pattern_generator.json` | A | Machine definition for `pattern_generator`. |
| `src/main/resources/data/lithmod/tags/block/machine.json` | M | Adds the generator block to the machine tag. |
| `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` | M | Adds the generator block to the vanilla pickaxe tag. |
| `src/main/resources/data/minecraft/tags/block/needs_stone_tool.json` | M | Adds the generator block to the vanilla stone-tool tag. |
| `src/test/java/com/euvcraft/lithmod/core/MaskDataTest.java` | A | Unit tests for the mask record. |
| `src/test/java/com/euvcraft/lithmod/core/MaskRulesTest.java` | A | Unit tests for the Gen 1 mask-shop gate. |
| `src/test/java/com/euvcraft/lithmod/mc/machine/mask/MaskDataCodecTest.java` | A | Unit tests for the mask codec round trips. |
| `docs/reports/M1-06_mask-and-pattern-generator.md` | A | This report. |
| `docs/testing.md` | M | Appended M1-06 testing section. |
| docs/reports/M1-06_mask-and-pattern-generator-conservation_review.md | new | Independent conservation review record. |
| docs/reports/M1-06_mask-and-pattern-generator-conformance_review.md | new | Independent conformance review record. |

39 changed paths: 35 files under `src/` (main Java, resources, and the three new unit-test classes under `src/test/`) plus this report, the appended `docs/testing.md`, and two independent review records. No deletions; every entry is an addition (`A`) or a modification (`M`).

## 3. Verification

All checks below were run by the coordinator; workers ran no checks, and this report's author ran no commands and made no source or test changes. These are completed checks, not pending work. `FACTS.json.measuredCommand`, with the coordinator-owned angle-bracket paths redacted:

```text
gradlew.bat test checkCoreImports jar runGameTestServer --offline --console=plain --init-script <coordinator-init> -Dm1.report=<coordinator-runtime-report>
```

`FACTS.json.outputLines`:

```text
[20:18:49] [Server thread/INFO] [minecraft/GameTestServer]: ========= 751 GAME TESTS COMPLETE IN 1.216 min ======================
[20:18:49] [Server thread/INFO] [minecraft/GameTestServer]: All 751 required tests passed :)
BUILD SUCCESSFUL in 2m 51s
11 actionable tasks: 3 executed, 1 from cache, 7 up-to-date
```

Unit tests (`UNIT-TABLE.md`, head `433a8f5`): 71 XML suites, 71 classes, 450 tests, 0 failures, 0 errors, 0 skipped.

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
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
| com.euvcraft.lithmod.core.MaskDataTest | 12 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MaskRulesTest | 19 | 0 | 0 | 0 |
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
| com.euvcraft.lithmod.mc.machine.mask.MaskDataCodecTest | 10 | 0 | 0 | 0 |
| **Total** | **450** | **0** | **0** | **0** |

Runtime (`FACTS.json.runtime`): 751 case records, 751 distinct case names, 751 passed, no required failures, no optional cases skipped. All 81 registered project IDs are present and passing (`missing`, `unexpectedProjectCases` and `nonPassingProjectCases` are all empty in `FACTS.json.inventory`). The 751 are game-test case records, not project unit tests, and the 81 are registered case IDs, not assertions.

```text
lithmod:chip_catalog_lookups, lithmod:chip_tooltip_lines, lithmod:item_assets, lithmod:component_catalog_lookups, lithmod:component_reader_t1, lithmod:component_reader_t2, lithmod:component_slot_validation, lithmod:trivial, lithmod:fab_random_salt_round_trip, lithmod:fab_random_client_side_rejected, lithmod:foup_stack_round_trip, lithmod:foup_network_view_is_public_only,
lithmod:foup_encoded_size, lithmod:foup_creative_tab_entries, lithmod:foup_debug_commands, lithmod:foup_trusted_contents, lithmod:foup_stored_capacity_wins, lithmod:foup_shulker_box_round_trip, lithmod:foup_ender_chest_round_trip, lithmod:foup_hopper_transfer, lithmod:foup_shift_click_move, lithmod:machine_drops_contents_on_break_in_survival, lithmod:machine_drops_contents_when_block_replaced, lithmod:machine_contents_do_not_duplicate,
lithmod:machine_unloading_drops_nothing, lithmod:machine_extra_inventory_drops_on_break, lithmod:process_cell_blocks_drop_themselves, lithmod:machine_energy_capability, lithmod:machine_rejects_hopper_transfers, lithmod:machine_runs_a_recipe, lithmod:machine_pauses_without_energy, lithmod:machine_save_load_round_trip, lithmod:machine_recipe_from_data, lithmod:machine_menu_opens_server_side, lithmod:machine_foup_slot_rejects_plain_items, lithmod:pattern_generator_cuts_a_logic_t1_mask,
lithmod:pattern_generator_cuts_a_logic_t2_mask, lithmod:pattern_generator_cuts_a_memory_t2_mask, lithmod:pattern_generator_uses_the_config_at_completion, lithmod:pattern_generator_refuses_a_memory_t1_design, lithmod:pattern_generator_refuses_a_compute_t3_design, lithmod:pattern_generator_refuses_a_product_without_a_design, lithmod:pattern_generator_refuses_a_product_that_is_not_a_mask, lithmod:pattern_generator_pauses_on_a_full_output_slot, lithmod:pattern_generator_pauses_without_energy, lithmod:pattern_generator_cancels_when_an_input_is_withdrawn, lithmod:pattern_generator_save_load_round_trip, lithmod:pattern_generator_exposes_energy_only,
lithmod:pattern_generator_block_use_opens_the_menu, lithmod:pattern_generator_ticker_is_server_side, lithmod:pattern_generator_menu_layout_and_shift_click, lithmod:mask_catalog_and_creative_tab, lithmod:mask_data_component_round_trip, lithmod:mask_tooltip_lines, lithmod:multiblock_forms_in_all_four_facings, lithmod:multiblock_removing_any_of_twelve_unforms_the_rest, lithmod:multiblock_survives_a_casing_and_port_swap, lithmod:multiblock_cells_do_not_share_blocks, lithmod:multiblock_energy_reaches_controller_only_while_formed, lithmod:multiblock_formation_hooks_fire_per_transition,
lithmod:multiblock_model_textures_all_resolve, lithmod:wafer_slicer_produces_a_lot, lithmod:wafer_slicer_lot_takes_layer_one, lithmod:wafer_slicer_refuses_a_used_foup, lithmod:wafer_slicer_pauses_without_energy, lithmod:wafer_slicer_save_load_round_trip, lithmod:crystal_puller_runs_its_recipe, lithmod:silicon_machines_expose_no_item_handler, lithmod:crystal_puller_rechecks_its_inputs, lithmod:wafer_slicer_rechecks_its_inputs, lithmod:wafer_slicer_lot_size_follows_the_foup_capacity, lithmod:wafer_slicer_menu_layout_and_shift_click,
lithmod:wafer_slicer_foup_slot_client_boundary, lithmod:silicon_energy_follows_machine_definitions, lithmod:silicon_machines_fail_closed_without_a_definition, lithmod:crystal_puller_resumes_only_the_same_recipe, lithmod:silicon_cooking_recipes_load_and_match, lithmod:yield_layer_step, lithmod:yield_pass_rate, lithmod:yield_end_to_end, lithmod:yield_run_spec
```

Negative controls for the new unit methods (`UNIT-CONTROLS.md`, pasted exactly):

| Test class | New methods observed failing | Deliberate production faults |
| --- | --- | --- |
| MaskDataCodecTest | `aMissingRequiredFieldIsRejected()` | missing-design-default |
| MaskDataCodecTest | `aMissingUseCountDefaultsToZero()` | missing-use-default, roundtrip-values |
| MaskDataCodecTest | `aNegativeUseCountIsRejected()`, `streamCodecRejectsInvalidData()` | invalid-data-accepted, roundtrip-values |
| MaskDataCodecTest | `aTierBelowOneIsRejected()`, `anUnknownDieTypeIsRejected()` | invalid-data-accepted |
| MaskDataCodecTest | `jsonRoundTripKeepsThePublicFields()` | missing-design-default, roundtrip-values |
| MaskDataCodecTest | `nbtRoundTripIsStableWhenRepeated()`, `nbtUsesThePublicFieldNames()`, `streamCodecRoundTripsTheSamePublicData()` | roundtrip-values |
| MaskDataTest | `acceptsEachCanonicalDieType()` | canonical-value |
| MaskDataTest | `consumeUseIsImmutable()`, `consumeUseToSpentThenThrows()` | consume-count, spent-state |
| MaskDataTest | `consumeUseOnAlreadySpentThrows()`, `isSpentOnlyAtZero()` | spent-state |
| MaskDataTest | `consumeUseReturnsNewRecordWithOneFewer()` | consume-count |
| MaskDataTest | `rejectsNoncanonicalDieType()`, `rejectsUnknownDieType()` | invalid-design |
| MaskDataTest | `rejectsNullDieType()` | null-design |
| MaskDataTest | `tierIndexHasNoUpperCap()` | tier-cap |
| MaskDataTest | `tierIndexMustBePositive()` | tier-minimum |
| MaskDataTest | `usesRemainingMustBeNonNegative()` | uses-minimum |
| MaskRulesTest | `absentHugeTierRefused()` | absent-tier |
| MaskRulesTest | `canPatternAcceptsMaskDataFields()`, `computeAndNandT3AllowedAtGeneration2()`, `computeT7AllowedAtGeneration3()`, `logicT1AllowedAtGeneration1()`, `logicT2AllowedAtGeneration1()`, `memoryT2AllowedAtGeneration1()` | all-patterns-denied |
| MaskRulesTest | `computeAndNandT3RefusedAtGeneration1()`, `computeT7RefusedAtGeneration2()` | generation-gate |
| MaskRulesTest | `memoryT1Refused()` | die-gate |
| MaskRulesTest | `noncanonicalDieTypeRefused()`, `nullDieTypeRefused()`, `unknownDieTypeRefused()` | invalid-pattern-design |
| MaskRulesTest | `nonpositiveGenerationRefused()` | nonpositive-generation |
| MaskRulesTest | `nonpositiveTierRefused()` | nonpositive-tier |
| MaskRulesTest | `nullRegistriesRefused()` | null-rules |
| MaskRulesTest | `swappedTierAndGenerationCaughtByComputeRule()`, `swappedTierAndGenerationCaughtByMemoryRule()` | all-patterns-denied, die-gate |
| MaskRulesTest | `tierAndGenerationAreNotInterchangeableAcrossTiers()` | all-patterns-denied, generation-gate |

Negative controls for the new server cases (`RUNTIME-CONTROLS.md`, pasted exactly):

| New server case | Deliberate failing control(s) |
| --- | --- |
| lithmod:mask_catalog_and_creative_tab | behavior |
| lithmod:mask_data_component_round_trip | behavior |
| lithmod:mask_tooltip_lines | behavior |
| lithmod:pattern_generator_block_use_opens_the_menu | behavior |
| lithmod:pattern_generator_cancels_when_an_input_is_withdrawn | behavior |
| lithmod:pattern_generator_cuts_a_logic_t1_mask | behavior |
| lithmod:pattern_generator_cuts_a_logic_t2_mask | behavior |
| lithmod:pattern_generator_cuts_a_memory_t2_mask | behavior |
| lithmod:pattern_generator_exposes_energy_only | energy |
| lithmod:pattern_generator_menu_layout_and_shift_click | behavior |
| lithmod:pattern_generator_pauses_on_a_full_output_slot | behavior |
| lithmod:pattern_generator_pauses_without_energy | behavior |
| lithmod:pattern_generator_refuses_a_compute_t3_design | behavior |
| lithmod:pattern_generator_refuses_a_memory_t1_design | behavior |
| lithmod:pattern_generator_refuses_a_product_that_is_not_a_mask | behavior |
| lithmod:pattern_generator_refuses_a_product_without_a_design | behavior |
| lithmod:pattern_generator_save_load_round_trip | energy, persistence |
| lithmod:pattern_generator_ticker_is_server_side | behavior |
| lithmod:pattern_generator_uses_the_config_at_completion | behavior |

Every one of the 41 new unit methods (MaskDataTest 12 + MaskRulesTest 19 + MaskDataCodecTest 10) and all 19 new server cases therefore has an actually executed negative control, not a suggested future one. Proof runs. Core: 31 methods against 16 deliberate mutants at `66a17d9`, all proved, files restored with their hashes verified unchanged. Codec: 10 methods against 4 mutants at `d90cee1`, all proved and restored, plus an independent swapped stream-getter proof retained alongside those logs. The retained attribution note says the named JUnit assertion failures in these logs supersede an earlier XML-regex attribution that had paired self-closing passing cases with later failures; the regex was corrected for future runs. In the runtime behavior control the 17 expected target cases failed (completion stamp, Gen 1 gating, runner guards, menu, ticker, interaction, stack/tooltip and status-wire paths); five further cases outside that set also failed because they go through the same shared base hooks (`crystal_puller_runs_its_recipe`, `crystal_puller_rechecks_its_inputs`, `machine_runs_a_recipe`, `machine_pauses_without_energy`, `pattern_generator_save_load_round_trip`), and `missing` is empty, so no expected case was missed. The energy control caught the FE-cap and pending-cost faults. The persistence control changed only the snapshot writer back to the old NBT form and caught the exact recipe-identity loss. After the controls every touched file was restored byte-for-byte, HEAD was unchanged, and the final full positive run passed — it is the output quoted above.

## 4. Spec conformance

| Prompt requirement | Evidence | Status |
| --- | --- | --- |
| `MaskData`: die type must be in `DieTypes.ALL`, `tierIndex` >= 1, `usesRemaining` >= 0, validation, a copy with one use consumed, `isSpent()` | `MaskDataTest` 12 tests; mutants canonical-value, invalid-design, null-design, tier-minimum, tier-cap (no upper cap), uses-minimum, consume-count, spent-state | Verified |
| `MaskRules.canPattern(...)` is true only when `DieRules.canMake` and the `mask_shop` generation check hold | Source and tests; the matrix argument order differs from the prose (see §6) | Verified, source order |
| Tests: logic at T1 and T2 pass, memory at T2 passes, memory at T1 fails, compute at T3 fails at Gen 1 (T3 needs mask_shop generation 2), NAND at T3 fails likewise | `logicT1AllowedAtGeneration1`, `logicT2AllowedAtGeneration1`, `memoryT2AllowedAtGeneration1`, `memoryT1Refused`, `computeAndNandT3RefusedAtGeneration1` | Verified |
| `blank_mask` is stackable | Stack 64; item model and client item definition; `lithmod:mask_catalog_and_creative_tab` | Verified (server) |
| `mask` is stack size 1 with a public codec and stream codec component, and tooltip lines for die type, tier and uses remaining | `MaskDataCodecTest` 10 tests / 4 mutants; `lithmod:mask_data_component_round_trip`, `lithmod:mask_tooltip_lines`; three lines in `lang/en_us.json` | Verified (server); client visuals not verified (§5) |
| Pattern generator is a single block on `FabMachineBlockEntity`, hand-fed, with a definition under `data/lithmod/machines/` | `PatternGeneratorBlock`/`PatternGeneratorBlockEntity`; the changed path `src/main/resources/data/lithmod/machines/pattern_generator.json`; `lithmod:pattern_generator_block_use_opens_the_menu` | Verified (server) |
| It runs machine recipes for machine id `pattern_generator`; the result is a mask whose components give die type and tier; `usesRemaining` may be omitted | `MACHINE_ID = "pattern_generator"`; the three cut cases; `aMissingUseCountDefaultsToZero()` | Verified |
| On completion `usesRemaining` is set from the `maskUses` config value; the Gen 1 shop calls `canPattern` with generation 1 before starting; an invalid recipe produces no output and a short status in the menu | `lithmod:pattern_generator_uses_the_config_at_completion`; the four refusal cases; status values 0..3 synced at wire index 4 | Verified |
| The blank mask is an ordinary ingredient of the recipe | The generator has two ordinary ingredient inputs and one output | Verified |
| GameTests: three masks with the right data and `usesRemaining` equal to `maskUses` including after a config change; memory T1 and compute T3 refused; a mid-progress NBT round trip; no item handler capability | The 19 new server cases, each with an executed negative control; `lithmod:pattern_generator_exposes_energy_only` | Verified (server) |
| Codec round trip of the mask component | `MaskDataCodecTest`: JSON, NBT and stream round trips; 4 mutants at `d90cee1` | Verified |
| Done when: the build and all tests pass with counts that add up; the report is `docs/reports/M1-06_mask-and-pattern-generator.md` | 450 unit tests / 0 failures and 751 passing game-test case records with all 81 project IDs present; this file | Verified |

Out of scope and untouched: the real recipes (M1-11), the Process Cell, and how a mask is consumed (M1-09).

## 5. Not verified

- Live client rendering and the screens in a real client, and the actual client-side ticker branch: the dedicated server tests cover the server-side ticker and the detached-level guard, not `ClientLevel`; no client-side or manual in-game check was made, so no visual pass is claimed.
- The pre-existing missing GUI bar assets remain a disclosed presentation follow-up for M1-06's screen, and the full playable processing and packaging path with the real recipes is outside this task and was not exercised.
- The literal start worktree/status transcript was not retained, so no earlier command or git output is reconstructed; the changed-path list, the unit and game-test counts, and the API/registry extracts are read from the retained evidence packet.
- No three-separate-process repeat of the game-test suite was run for M1-06 (that is M1-12 work), and no worker ran any check.

## 6. Deviations

- `GenerationMatrix` argument order: the task prose and the source disagree, and the source order is the one the tests prove (`isSatisfied("mask_shop", installedGeneration, tierIndex)`).
- Minimal shared seams were added because real tests caught defects: the identity-default `filterRecipes`/`prepareOutput` hooks on `FabMachineBlockEntity`, a private snapshot for the runner, and `MachineRecipeRunner.setMachineEnergyPerTick` so a restored, already-paid run keeps its frozen cost until it resumes. The base still syncs `DATA_COUNT` = 4.
- The generator's block shell is the shared silicon machine block package reused as-is rather than a new shell, and authorship was mixed: the preferred Nemotron model plus approved free fallbacks where the provider returned 503s/504s or rate limits, with the coordinator applying mechanical import fixes and the numeric-NBT test corrections. All workers ran at $0.

## 7. Open questions

None.

## 8. Handoff

Signatures and constants below were extracted from the source classes listed in section 2, the component/item registries, `FabConfig`, and the machine definition. The mask data component id is `lithmod:mask_data` via `ModDataComponents.MASK_DATA`.

| Type | Selected verified signatures and constants (exact) |
| --- | --- |
| `MaskData` (`core`) | `record MaskData(String dieType, int tierIndex, int usesRemaining)`; `public boolean isSpent()`; `public MaskData consumeUse()` |
| `MaskRules` (`core`) | `public static boolean canPattern(String dieType, int tierIndex, int installedMaskShopGeneration, DieRules dieRules, GenerationMatrix generationMatrix)`; the installed generation is argument 3 |
| `MaskDataCodec` (`machine.mask`) | `public static final Codec<MaskData> CODEC = persistentCodec()`; `public static final StreamCodec<ByteBuf, MaskData> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MaskData::dieType, ByteBufCodecs.VAR_INT, MaskData::tierIndex, ByteBufCodecs.VAR_INT, MaskData::usesRemaining, MaskData::new)` |
| `PatternGeneratorBlockEntity` (`machine.mask`) | `MACHINE_ID = "pattern_generator"`; `BLANK_SLOT = 0`, `DESIGN_SLOT = 1`, `INPUT_SLOTS = 2`, `OUTPUT_SLOT = 2`; `MASK_SHOP_GENERATION = 1`; `STATUS_NONE = 0`, `STATUS_NOT_A_MASK = 1`, `STATUS_MISSING_DESIGN = 2`, `STATUS_DESIGN_REFUSED = 3`, `STATUS_COUNT = 1`; `PatternGeneratorBlockEntity(BlockPos, BlockState)`; `protected String machineId()`; `protected AbstractContainerMenu createMachineMenu(int, Inventory, FabMachineBlockEntity)`; `public final ContainerData statusData()`; `public final int status()`; `protected List<MachineRecipe> filterRecipes(List<MachineRecipe>)`; `protected ItemStack prepareOutput(ItemStack)`; `public void serverTick()` |
| `PatternGeneratorMenu` (`machine.mask`) | `STATUS_DATA_INDEX = 0`; `STATUS_WIRE_INDEX = FabMachineBlockEntity.DATA_COUNT + STATUS_DATA_INDEX`; two constructors `PatternGeneratorMenu(int, Inventory, FabMachineBlockEntity)` and `PatternGeneratorMenu(int, Inventory, RegistryFriendlyByteBuf)`; `protected void addMachineSlots()`; `public final int status()` |
| `FabMachineBlockEntity` (`machine`) | `DATA_ENERGY = 0`, `DATA_CAPACITY = 1`, `DATA_PROGRESS = 2`, `DATA_TICKS = 3`, `DATA_COUNT = 4` |
| `MachineRecipeRunner` (`recipe`) | `public void setMachineEnergyPerTick(int machineEnergyPerTick)` |
| `ModDataComponents` (`registry`) | `MASK_DATA` = component id `lithmod:mask_data`; `.persistent(MaskDataCodec.CODEC).networkSynchronized(MaskDataCodec.STREAM_CODEC)` |
| `ModItems` (`registry`) | `blank_mask`; `mask` (stack 1, no default `MASK_DATA`); `pattern_generator` |
| slots | `BLANK_SLOT` 0 and `DESIGN_SLOT` 1 are the two inputs, `OUTPUT_SLOT` 2 is the output |
| config | `general.maskUses` via `FabConfig.MASK_USES`, read as `settings().maskUses()`, `defineInRange("maskUses", FabSettings.DEFAULT_MASK_USES, 1, 1000)` |
| machine definition | `data/lithmod/machines/pattern_generator.json`: `id "pattern_generator"`, `energy_capacity 20000`, `max_receive 2000`, `energy_per_tick 20`, `per_tier_energy_per_tick {}`, `input_slots 2`, `output_slots 1` |

- Machine id `pattern_generator`. The definition is `src/main/resources/data/lithmod/machines/pattern_generator.json`, and the machine has no recipes in this milestone.
- Status values: `STATUS_NONE` 0, `STATUS_NOT_A_MASK` 1, `STATUS_MISSING_DESIGN` 2, `STATUS_DESIGN_REFUSED` 3, with the generator's own status synced at wire index `DATA_COUNT + STATUS_DATA_INDEX` = 4, so both sides receive five synced data values while `DATA_COUNT` stays 4.
- Config: on completion `usesRemaining` is stamped from `general.maskUses` (`FabConfig.MASK_USES`, read as `settings().maskUses()`); the run's per-tick cost is frozen, and a restored paid run must adopt the recipe at that frozen cost.
- Mask stack caveat: the mask item has no default design; a bare `mask` stack has **no** `MASK_DATA` component (null) and is refused, while `uses_remaining` defaults to 0 only inside a present component object that already carries the required die type and tier. The generator is energy-only: no item or fluid capability, no hopper access, and external extraction refused.
