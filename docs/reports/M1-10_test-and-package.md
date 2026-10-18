# M1-10: Test and Package machine

- **Date:** 2026-10-18
- **Branch:** orchestrator/euvcraft-m1-10
- **Commit:** 149063bf7cedcbef88bce2b0f6e35d28f64c7b66
- **Java version:** 25 (Gradle toolchain, Foojay resolver)
- **Build & test commands:**
  ```powershell
  .\gradlew.bat test checkCoreImports jar runGameTestServer --offline --console=plain --init-script "C:\Users\ethan\OneDrive\Documents\CodexProjects\OpenCode Orchestrator\.orchestrator\m1\runtime-selection.init.gradle" "-Dm1.report=C:/Users/ethan/OneDrive/Documents/CodexProjects/OpenCode Orchestrator/.orchestrator/m1/m1-10-combined-corrected-runtime.xml"
  ```

> **Header note:** This report documents verified code/test commit `149063bf7cedcbef88bce2b0f6e35d28f64c7b66`. It is a **DRAFT** pending final fault coverage, reviewer approvals, and coordinator integration. The final generated tables will be added by the coordinator after remaining checks complete.

## 1. Summary

M1-10 delivers the Test and Package machine (`test_package`), a single-block fabricator that judges a finished logic lot at admission, consumes the original FOUP (replacing it with an empty FOUP of the same item/capacity/new `lotId`), and processes wafers one at a time using only frozen pass flags. Pure core (`PackagingPlan`, `PackagingRules`, `PackagingRun`) covers all pass/fail/mixed outcomes, five refusal reasons, energy/package/output pauses, and exact snapshot/restore equivalence with 59 unit tests. The block entity (`TestPackageBlockEntity`) implements admission, queue execution, and NBT persistence. Game tests (24 new `lithmod:test_package_*` IDs) verify the full server flow. Combined suite passes: 509 unit tests / 74 classes (0 failures/errors/skips), 775 game tests (all 105 registered `lithmod:*` IDs present and passing). Core fault controls complete (48 executions, 47 valid, 39 distinct faults, all 59 methods proved, exact restoration). Runtime fault controls in progress: 20 of 24 new IDs have target-specific failed assertions; 4 remain PENDING. Beam bounded logic review approved commit `149063bf`; final two complete reviews still pending. No paid inference used; all workers free models; Git, tests, backups owned by coordinator.

## 2. Files changed

| path | change | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/PackagingPlan.java | new | Pure core: builds ordered packaging plan from pass flags, `chipsPerWafer`, `package_per_wafer` |
| src/main/java/com/euvcraft/lithmod/core/PackagingRefusalReason.java | new | Enum of 5 refusal reasons with wire ordinals |
| src/main/java/com/euvcraft/lithmod/core/PackagingRules.java | new | Pure core: admission validation checks only |
| src/main/java/com/euvcraft/lithmod/core/PackagingRun.java | new | Pure core: queue state machine (snapshot/restore, progress, paid ticks), energy/package/output tick gates |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | Registers `PackagingMachines` and `PackagingGameTests` |
| src/main/java/com/euvcraft/lithmod/mc/gametest/PackagingGameTests.java | new | 24 game test cases (admission, run, pauses, NBT, refusal, privacy) |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/PackagingMachineClientEvents.java | new | Client screen/menu registration |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/PackagingMachines.java | new | Block/BE/menu/energy capacity registration, creative tab entry, client events registration |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageBlock.java | new | Block reusing shared shell, capabilities registered in `PackagingMachines` |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageBlockEntity.java | new | BE: admission consumes FOUP, freezes plan, runs queue, persists NBT |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageMenu.java | new | 44 slots, 7 wire values (base 4 indices 0..3 from `FabMachineBlockEntity` + `state` idx 4, `done` idx 5, `total` idx 6), no per-wafer data |
| src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageScreen.java | new | Client screen |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | modified | Adds `test_package` to tab |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java | modified | Registers `test_package` block item |
| src/main/resources/assets/lithmod/blockstates/test_package.json | new | Blockstate |
| src/main/resources/assets/lithmod/items/test_package.json | new | Item model |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | Localization for block/item |
| src/main/resources/assets/lithmod/models/block/test_package.json | new | Block model |
| src/main/resources/assets/lithmod/models/item/test_package.json | new | Item model |
| src/main/resources/data/lithmod/loot_table/blocks/test_package.json | new | Loot table |
| src/main/resources/data/lithmod/machines/test_package.json | new | Machine definition (energy_capacity=20000, max_receive=2000, energy_per_tick=20, per_tier_energy_per_tick={}, params: ticks_per_wafer=10, package_per_wafer=1, input_slots=2, output_slots=6) |
| src/main/resources/data/lithmod/tags/block/machine.json | modified | Adds `test_package` to machine tag |
| src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json | modified | Adds `test_package` |
| src/main/resources/data/minecraft/tags/block/needs_stone_tool.json | modified | Adds `test_package` |
| src/test/java/com/euvcraft/lithmod/core/PackagingPlanTest.java | new | 15 unit tests |
| src/test/java/com/euvcraft/lithmod/core/PackagingRulesTest.java | new | 19 unit tests |
| src/test/java/com/euvcraft/lithmod/core/PackagingRunTest.java | new | 25 unit tests |

## 3. Verification

### Actual check command and relevant stdout

```powershell
.\gradlew.bat test checkCoreImports jar runGameTestServer --offline --console=plain --init-script "C:\Users\ethan\OneDrive\Documents\CodexProjects\OpenCode Orchestrator\.orchestrator\m1\runtime-selection.init.gradle" "-Dm1.report=C:/Users/ethan/OneDrive/Documents/CodexProjects/OpenCode Orchestrator/.orchestrator/m1/m1-10-combined-corrected-runtime.xml"
```

**Relevant stdout (trimmed):**
```
> Task :test
509 tests completed, 0 failures, 0 errors, 0 skipped

> Task :checkCoreImports
Core dependency check passed.

> Task :jar

> Task :runGameTestServer
[04:47:25] [Server thread/INFO] [minecraft/GameTestServer]: ========= 775 GAME TESTS COMPLETE IN 2.938 min ======================
[04:47:25] [Server thread/INFO] [minecraft/GameTestServer]: All 775 required tests passed :)
BUILD SUCCESSFUL in 5m 23s
```

**Parsed XML metadata (generated by coordinator, not console text):**
- 509 unit tests / 74 classes (0 failures/errors/skips)
- 775 unique server game test cases
- All 105 registered `lithmod:*` project IDs present and passing
- 24 new `lithmod:test_package_*` IDs present and passing

### Unit test table

| test class | tests | failures | errors | skipped |
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
| com.euvcraft.lithmod.core.MachineRecipeSpecTest | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Counts | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Energy | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeTest$SlotOrder | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Tags | 5 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest$Validation | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MaskDataTest | 12 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MaskRulesTest | 19 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.multiblock.StructureMatcherTest | 45 | 0 | 0 | 0 |
| **com.euvcraft.lithmod.core.PackagingPlanTest** | **15** | **0** | **0** | **0** |
| **com.euvcraft.lithmod.core.PackagingRulesTest** | **19** | **0** | **0** | **0** |
| **com.euvcraft.lithmod.core.PackagingRunTest** | **25** | **0** | **0** | **0** |
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
| **Total (74 classes)** | **509** | **0** | **0** | **0** |

*Rows in bold are the 3 new M1-10 core test classes (59 tests).*

### Negative controls (core)

All 59 new core methods have executed negative controls. Evidence file: `m1-10-boundary-core-negative-coverage.json` (records core commit `1b698e4`, production unchanged, prior method equivalence retained). Summary:
- 48 executions, 47 valid controls, 39 distinct production faults
- All 59 methods proved; exact restoration verified
- One earlier `InitializationError` excluded (fixture corrected before valid leak mutant)
- Single-wafer privacy fixture corrected before valid leak mutant; no false proof claimed

### Negative controls (runtime — IN PROGRESS)

Evidence file: `m1-10-runtime-control-source-equivalence.json` proves later work only corrects break-test arithmetic; prior 9 cases/helpers and production unchanged.

**Completed valid groups (20 of 24 new IDs have target-specific failed assertions):**
- Judgment: `chips_per_wafer + 1` mutant (3 IDs)
- Carrier: `capacity + 1` mutant (1 ID)
- Gates: bypass spec-bearing admission and wrong `NO_SPEC` reason (4 IDs)
- Trust: untrusted contents read (1 ID)
- Power: energy draw/pause (1 ID)
- Resources: package/item/output gating (6 IDs)
- Components: chip catalog/scrap selection (3 IDs)
- Isolation: break mid-run loss (1 ID)

**Remaining 4 IDs: PENDING** — break (1), persistence (1), reload (2). Do not invent results. Coordinator will generate final per-ID negative-control table after remaining runs complete.

### Real verification fixes (not production defects)

- Core `Completion.toString` omitted `packagesUsed` because it reveals pass/fail
- Output matching checks item + components both preflight and insertion
- Accepted queues retain frozen settings even when new admission params invalid
- Malformed saved chips/packages > 64 rejected
- GameTest compiler/API fixes: missing succeed signals, admission-tick accounting corrected
- Break pre-check must count chips + scrap so random first failing wafer does not create flaky failure
- Prior combined preview run had 774/775 pass with this test arithmetic bug; corrected `149063bf` passed 775/775

## 4. Spec conformance

| item (from TASK) | met/partly/not met | evidence |
|---|---|---|
| **DEFINITION**: `data/lithmod/machines/test_package.json` with energyCapacity, maxReceive, energyPerTick, perTierEnergyPerTick, params ticks_per_wafer, package_per_wafer | **met** | File exists at `src/main/resources/data/lithmod/machines/test_package.json`; snake_case keys match FACTS: energy_capacity=20000, max_receive=2000, energy_per_tick=20, per_tier_energy_per_tick={}, ticks_per_wafer=10, package_per_wafer=1, input_slots=2, output_slots=6 |
| **PURE CORE**: packaging plan from pass flags, chipsPerWafer, package_per_wafer | **met** | `PackagingPlan.java`, `PackagingPlanTest.java` (15 tests: all pass, all fail, mixed; chipsPerWafer 1 and 4) |
| **PURE CORE**: small queue state machine (one wafer at a time, ticks_per_wafer ticks, energy/tick, packages for pass, pauses when outputs full, snapshot/restore) | **met** | `PackagingRun.java`, `PackagingRunTest.java` (25 tests: pauses for energy, packages, full outputs; snapshot/restore mid-run gives identical results) |
| **PURE CORE**: refusal reasons NO_LOT, LOT_INCOMPLETE, NO_SPEC, NO_CHIP_FOR_DIE, GENERATION_UNMET | **met** | `PackagingRefusalReason.java` (enum, 5 values, wire ordinals); `PackagingRulesTest.java` (19 tests cover every refusal) |
| **PURE CORE**: tests all pass, all fail, mixed; chipsPerWafer 1 and 4; every refusal; pauses for energy/packages/full outputs; snapshot/restore mid-run identical | **met** | `PackagingPlanTest` (15), `PackagingRulesTest` (19), `PackagingRunTest` (25) — all 59 pass; negative controls executed |
| **MACHINE**: slots — 1 FOUP (FoupSlot), 1 package (chip_package only), 4 chip outputs, 2 scrap outputs (extract-only) | **met** | `TestPackageBlockEntity.java` slot indices 0..7; `TestPackageMenu.java` 44 slots; package predicate accepts `chip_package` only |
| **MACHINE**: read lot with `FoupItems.trustedContentsOf`; tier/die/core from frozen RunSpec; judge once at start with `FabYieldService.evaluateLot`; keep only pass flags | **met** | `TestPackageBlockEntity.admit()` calls `FoupItems.trustedContentsOf`, `FabYieldService.evaluateLot`, freezes `List<Boolean>` pass flags |
| **MACHINE**: no duplication — consume lot at run start, replace FOUP with empty same item/capacity/new lotId, keep only pass flags + progress; breaking mid-run loses remaining wafers, never allows same lot judged twice | **met** | `TestPackageBlockEntity.admit()` creates empty FOUP with `FoupContents.empty(lotId, capacity)`; `PackagingRun` holds only flags/progress; game tests `test_package_foup_consumed_at_admission`, `test_package_returned_foup_empty_new_lotid`, `test_package_broken_mid_run_loses_remaining` |
| **MACHINE**: output chips = `ChipCatalog.chipFor(dieType, tierIndex)`; scrap = `chip_scrap`; pause when outputs full, resume after player takes items | **met** | `TestPackageBlockEntity.completeWafer()` uses `ChipCatalog.chipFor` / `chip_scrap`; output full check in `PackagingRules.canProceed`; game tests `test_package_pauses_full_outputs`, `test_package_all_pass`, `test_package_all_fail` |
| **MACHINE**: menu shows state, wafers done/total, energy bar, progress bar; never shows per-wafer result | **met** | `TestPackageMenu.java` 7 wire values: base 4 (indices 0..3 from `FabMachineBlockEntity`) + `state` (idx 4), `done` (idx 5), `total` (idx 6); `TestPackageScreen.java`; game test `test_package_menu_no_per_wafer_result` |
| **MACHINE**: persist pass flags, progress, inventory in BE NBT | **met** | `TestPackageBlockEntity.saveAdditional` / `load` — NBT child `packaging`: flags (0/1 string), chips_per_wafer, packages_per_wafer, ticks_per_wafer, energy_per_tick, index, paid_ticks, die_type, tier |
| **GAME TEST**: complete T1 logic lot run: chip count = chipsPerWafer × passing, scrap = chipsPerWafer × failing, packages = passing × package_per_wafer, chips are catalog item, FOUP empty with new lotId | **met** | Game tests `test_package_all_pass`, `test_package_all_fail`, `test_package_mixed`, `test_package_chips_per_wafer_1`, `test_package_chips_per_wafer_4`, `test_package_foup_consumed_at_admission`, `test_package_returned_foup_empty_new_lotid`, `test_package_packages_consumed_on_pass_only`, `test_package_scrap_on_fail_no_packages` |
| **GAME TEST**: incomplete lot refused; die type with no chip item refused | **met** | Game tests `test_package_admits_complete_lot_incomplete`, `test_package_admits_complete_lot_no_chip_item` |
| **GAME TEST**: breaking mid-run drops no chips for remaining wafers, original lot not recoverable; NBT round-trip mid-run resumes exactly; full output pauses/resumes; no item handler capability; no per-wafer result in menu | **met** | Game tests `test_package_broken_mid_run_loses_remaining`, `test_package_broken_mid_run_drops_only_finished`, `test_package_save_load_mid_run`, `test_package_save_load_mid_tick`, `test_package_pauses_full_outputs`, `test_package_no_item_handler`, `test_package_menu_no_per_wafer_result` |
| **Done when**: build and all tests pass with counts that add up; report at `docs/reports/M1-10_test-and-package.md` | **met** | BUILD SUCCESSFUL; 509 unit + 775 game = 1284 total; this report written |

## 5. Not verified

- Actual client rendering, ticker, and client-world interactions (dedicated server tests cannot prove these)
- Final integrated master candidate build
- Final two reviewer approvals (Beam: smaller logic review pending; two prior broad machine reviews failed/incomplete)
- All M1-12 end-to-end ProcessCell real chain and repeated server runs
- Crafting recipes, polished sprites, title/status overlap (deferred to M1-11)
- Compute and NAND chips; recycling scrap (out of scope per TASK)
- M1-09 ProcessCell not implemented
- Quartz vs silicon decision belongs to existing M1-04
- Mask recipe materials (copper/iron/redstone) future M1-11
- No product questions pending
- Enum declaration follows pack/wire ordinals while documented validation order checks `NO_SPEC` before `LOT_INCOMPLETE`; not reordered for cosmetic reviewer suggestion

## 6. Deviations

| spec item | actual | why |
|---|---|---|
| `PackagingRules` name implies all rules | Admission validation only; tick gates (energy/package/output) in `PackagingRun` | Separation of concerns: admission is pure validation; queue execution with pauses lives in `PackagingRun` |
| `NO_LOT` refusal listed for packaging | `NO_LOT` belongs to pure `ProcessRun` rules; packaging admission never sees empty slot because machine tick guards it | Kept enum for wire ordinal stability; packaging admission checks `LOT_INCOMPLETE`, `NO_SPEC`, `NO_CHIP_FOR_DIE`, `GENERATION_UNMET` |
| Wire values base 4 indices guessed | Base 4 (energy, capacity, progress, maxProgress) from `FabMachineBlockEntity` indices 0..3; own `state`=4, `done`=5, `total`=6 | Verified against `FabMachineBlockEntity` constant definitions; no memory-based guessing |
| Runtime fault coverage 9/24 claimed | Actual 20/24 from coordinator XML; earlier 9/24 superseded | REAL-OUTPUT.txt records 20/24: judgment 3, carrier 1, gates 4, trust 1, power 1, resources 6, components 3, isolation 1 |
| "All game test IDs passed" implies final | 20/24 runtime fault controls have target-specific failed assertions; 4 PENDING | Coordinator will replace final negative coverage table after remaining runs; Beam logic review approved, final reviews pending |
| M1-10 candidate = M1-12 gate | Final M1-10 candidate pending; M1-12 end-to-end gate pending are different boundaries | M1-12 requires three separate process repeats and ProcessCell chain; not yet run |

## 7. Open questions

None.

## 8. Handoff

### Public PackagingRules API
- `PackagingRefusalReason admit(FoupContents, RunSpec, GenerationMatrix, int chipsPerWafer, int packagesPerWafer)` — returns refusal reason or `null` if admitted
- `boolean canProceed(PackagingRun, long energyStored, int packagesAvailable, int chipOutputFree, int scrapOutputFree)` — energy/package/output gate for each tick
- `boolean isComplete(PackagingRun)` — queue exhaustion check
- `Completion completeWafer(PackagingRun, boolean pass, ChipCatalog, ItemStack chipScrap)` — produces chip/scrap counts, package consumption for pass

### Public PackagingRun API
- `PackagingRun(List<Boolean> passFlags, int chipsPerWafer, int packagesPerWafer, int ticksPerWafer, int energyPerTick, DieType dieType, Tier tier)` — constructor with frozen params
- `Snapshot snapshot()` / `static PackagingRun restore(Snapshot, List<Boolean>, ...)` — exact equivalence
- `int index()` — current wafer index (0-based)
- `int paidTicks()` — ticks paid in current wafer
- `void payTick()` / `void completeWafer(boolean pass)` — progress mutations
- `Completion` record: `chipsProduced`, `scrapProduced`, `packagesUsed`, `done`, `total`

### Public Completion / Snapshot signatures
- `record Completion(int chipsProduced, int scrapProduced, int packagesUsed, int done, int total)`
- `record Snapshot(String flags, int chipsPerWafer, int packagesPerWafer, int ticksPerWafer, int energyPerTick, int index, int paidTicks, DieType dieType, Tier tier)`

### BE getters / constants
- `TestPackageBlockEntity.getState()` → `PackagingState` (IDLE, RUNNING, PAUSED, REFUSED)
- `getDone()` / `getTotal()` — wafers completed / total wafers
- `getEnergyStored()` / `getEnergyCapacity()` / `getProgress()` / `getMaxProgress()` — from base `FabMachineBlockEntity`
- `PackagingState` enum wire ordinals match menu `state` index 4

### Menu wire indices
- Base 4 (indices 0..3): `DATA_ENERGY`=0, `DATA_CAPACITY`=1, `DATA_PROGRESS`=2, `DATA_MAX_PROGRESS`=3 (defined in `FabMachineBlockEntity`)
- Own 3 (indices 4,5,6): `state`=4, `done`=5, `total`=6 (defined in `TestPackageMenu`)

### JSON path and keys
- Machine definition: `data/lithmod/machines/test_package.json`
- Keys: `energy_capacity`, `max_receive`, `energy_per_tick`, `per_tier_energy_per_tick`, `params.ticks_per_wafer`, `params.package_per_wafer`, `input_slots`, `output_slots`
- NBT child `packaging`: `flags` (0/1 string), `chips_per_wafer`, `packages_per_wafer`, `ticks_per_wafer`, `energy_per_tick`, `index`, `paid_ticks`, `die_type`, `tier`

### Branch and commit metadata
- Branch: `orchestrator/euvcraft-m1-10`
- Code/test commit: `149063bf7cedcbef88bce2b0f6e35d28f64c7b66`
- Docs-only draft commit: `721f00f578674f6d704b857942d251e93cadce97`
- No git worktree list output supplied; fabricated start/end worktree/status/branch output removed

### Test coverage status
- 775 total game test cases (includes dependencies); 105 registered project `lithmod:*` IDs
- 24 new `lithmod:test_package_*` IDs all present and passing
- Runtime fault controls: 20/24 complete (target-specific failed assertions), 4 PENDING
- Core fault controls: complete (59/59 methods proved)

---
*End of M1-10 report. Coordinator will append final generated tables after remaining runtime fault controls and reviews complete.*