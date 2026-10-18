# M1-10: Test and Package machine

Date:2026-10-18. Branch:`orchestrator/euvcraft-m1-10`. Verified code/tests:`149063bf7cedcbef88bce2b0f6e35d28f64c7b66`. Report status: accepted. See [final acceptance](M1-10_acceptance.md) for both reviews, the tested merge candidate and integration. Nemotron authored documentation drafts; coordinator reconciled identifiers, signatures, tables and claims against source and saved evidence.
## 1. Summary

The Test and Package machine judges a complete logic or memory lot once, consumes it immediately, and returns an empty carrier with a fresh identity. It processes frozen pass flags one wafer at a time, producing catalog chips or scrap and consuming packages only on passing completions. Energy, package and output shortages preserve paid progress. Breaking the machine permanently loses unfinished queued wafers and cannot recover the original lot. The measured build passed 509 unit tests and 775 server cases; all 59 new core method and 24 new server case has valid deliberate-fault evidence. Two independent Beam approvals and a separate full merge-candidate check preceded integration; see the final acceptance record.
## 2. Files changed

| Path | Change | Purpose |
| --- | --- | --- |
| `docs/reports/M1-10_test-and-package.md` | new | Operational notes and evidence report |
| `docs/testing.md` | modified | Operational notes and evidence report |
| `src/main/java/com/euvcraft/lithmod/core/PackagingPlan.java` | new | Pure packaging validation, plan or queue |
| `src/main/java/com/euvcraft/lithmod/core/PackagingRefusalReason.java` | new | Pure packaging validation, plan or queue |
| `src/main/java/com/euvcraft/lithmod/core/PackagingRules.java` | new | Pure packaging validation, plan or queue |
| `src/main/java/com/euvcraft/lithmod/core/PackagingRun.java` | new | Pure packaging validation, plan or queue |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | modified | Mod/item/tab wiring |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/PackagingGameTests.java` | new | 24 server integration tests |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/PackagingMachineClientEvents.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/PackagingMachines.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageBlock.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageBlockEntity.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageMenu.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageScreen.java` | new | Packaging machine, menu, screen or registration |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java` | modified | Mod/item/tab wiring |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java` | modified | Mod/item/tab wiring |
| `src/main/resources/assets/lithmod/blockstates/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/assets/lithmod/items/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/assets/lithmod/lang/en_us.json` | modified | Data, tags, localization or placeholder models |
| `src/main/resources/assets/lithmod/models/block/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/assets/lithmod/models/item/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/data/lithmod/loot_table/blocks/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/data/lithmod/machines/test_package.json` | new | Data, tags, localization or placeholder models |
| `src/main/resources/data/lithmod/tags/block/machine.json` | modified | Data, tags, localization or placeholder models |
| `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` | modified | Data, tags, localization or placeholder models |
| `src/main/resources/data/minecraft/tags/block/needs_stone_tool.json` | modified | Data, tags, localization or placeholder models |
| `src/test/java/com/euvcraft/lithmod/core/PackagingPlanTest.java` | new | Core regression tests |
| `src/test/java/com/euvcraft/lithmod/core/PackagingRulesTest.java` | new | Core regression tests |
| `src/test/java/com/euvcraft/lithmod/core/PackagingRunTest.java` | new | Core regression tests |
## 3. Verification

The coordinator ran the following command from the task worktree using the installed JDK25. The model writers did not execute tests.

```powershell
gradlew.bat test checkCoreImports jar runGameTestServer --offline --console=plain --init-script "C:\Users\ethan\OneDrive\Documents\CodexProjects\OpenCode Orchestrator\.orchestrator\m1\runtime-selection.init.gradle" "-Dm1.report=C:/Users/ethan/OneDrive/Documents/CodexProjects/OpenCode Orchestrator/.orchestrator/m1/m1-10-combined-corrected-runtime.xml"
```

Literal relevant console output:

```text
> Task :jarJar NO-SOURCE
> Task :jar
> Task :checkCoreImports
> Task :test
> Task :test
[04:47:25] [Server thread/INFO] [minecraft/GameTestServer]: ========= 775 GAME TESTS COMPLETE IN 2.938 min ======================
[04:47:25] [Server thread/INFO] [minecraft/GameTestServer]: All 775 required tests passed :)
BUILD SUCCESSFUL in 5m 23s
```

Parsed XML totals:509tests in74 unit classes,775 distinct server cases, all 105registered project IDs present and passing; zero failures/errors/skips. Unit counts and per-class rows come from `m1-10-combined-corrected-unit-counts.json`; full output and XML are retained under the coordinator `.orchestrator/m1/` directory.

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
| com.euvcraft.lithmod.core.PackagingPlanTest | 15 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.PackagingRulesTest | 19 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.PackagingRunTest | 25 | 0 | 0 | 0 |
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

Core negative controls:48 executions,47 valid runs,39 distinct fault labels; one initialization error excluded. The frozen core proof at1b698e4 is retained through verified unchanged production/test bodies. All59methods have an actual failed control below, followed by exact restoration.

| New unit method | Executed fault | Result |
| --- | --- | --- |
| `PackagingPlanTest.allPassAtChips1Packages1TotalsThreeOfEach()` | `plan-order` | Failed; source restored |
| `PackagingPlanTest.mixedAtChips1Packages1TotalsThreeChipsThreeScrap()` | `plan-count` | Failed; source restored |
| `PackagingPlanTest.perWaferFlagOrderMatchesTheFixture()` | `plan-order` | Failed; source restored |
| `PackagingPlanTest.allPassAtChips4Packages2TotalsTwelveChipsSixPackages()` | `plan-order` | Failed; source restored |
| `PackagingPlanTest.allFailAtChips4YieldsScrapOnlyAndConsumesNoPackages()` | `plan-order` | Failed; source restored |
| `PackagingPlanTest.mixedAtChips4Packages2AlternatesPackagesPerWafer()` | `plan-order` | Failed; source restored |
| `PackagingPlanTest.waferIndexesOutsideThePlanAreRejected()` | `plan-bounds` | Failed; source restored |
| `PackagingPlanTest.emptyFlagListIsRejected()` | `plan-empty` | Failed; source restored |
| `PackagingPlanTest.nullFlagListAndNullFlagEntriesAreRejected()` | `plan-mutable` | Failed; source restored |
| `PackagingPlanTest.nonPositivePerWaferAmountsAreRejected()` | `plan-minimum-packages` | Failed; source restored |
| `PackagingPlanTest.perWaferAmountsThatWouldOverflowTotalsAreRejected()` | `plan-overflow` | Failed; source restored |
| `PackagingPlanTest.flagsAreCopiedOutOfTheSourceList()` | `plan-alias` | Failed; source restored |
| `PackagingPlanTest.returnedFlagListIsImmutable()` | `plan-mutable` | Failed; source restored |
| `PackagingPlanTest.planDescriptionNeverShowsOutcomes()` | `plan-private-text` | Failed; source restored |
| `PackagingPlanTest.singleWaferMayReachTheExactIntegerLimit()` | `plan-limit-boundary` | Failed; source restored |
| `PackagingRulesTest.lineIsThePackagingSupportLine()` | `rules-line` | Failed; source restored |
| `PackagingRulesTest.completeLotIsAdmitted()` | `rules-admit` | Failed; source restored |
| `PackagingRulesTest.completeLotAtHigherTierIsAdmittedWhenGenerationMeetsTheRealGate()` | `rules-line` | Failed; source restored |
| `PackagingRulesTest.nullLotIsRefused()` | `rules-no-lot` | Failed; source restored |
| `PackagingRulesTest.emptyLotIsRefused()` | `rules-no-lot` | Failed; source restored |
| `PackagingRulesTest.lotAndTierProblemsAreOnlyJudgedAfterTheLotItself()` | `rules-no-spec` | Failed; source restored |
| `PackagingRulesTest.lotWithoutRunSpecIsRefused()` | `rules-no-spec` | Failed; source restored |
| `PackagingRulesTest.incompleteLotIsRefused()` | `rules-lot-incomplete` | Failed; source restored |
| `PackagingRulesTest.completenessIsCheckBeforeTheCatalogSoOneReasonPerCall()` | `rules-lot-incomplete` | Failed; source restored |
| `PackagingRulesTest.missingChipItemIsRefused()` | `rules-no-chip-for-die` | Failed; source restored |
| `PackagingRulesTest.missingChipItemWinsOverTheGenerationGate()` | `rules-no-chip-for-die` | Failed; source restored |
| `PackagingRulesTest.generationGateIsDataDrivenNotAHardcodedCeiling()` | `rules-line` | Failed; source restored |
| `PackagingRulesTest.generationGateFailsClosedBelowOne()` | `rules-generation` | Failed; source restored |
| `PackagingRulesTest.nullTierThrowsForAnAdmissibleLot()` | `rules-null-tier` | Failed; source restored |
| `PackagingRulesTest.tierMismatchWithTheFrozenSpecThrows()` | `rules-tier-match` | Failed; source restored |
| `PackagingRulesTest.nullMatrixThrowsInsteadOfFailingOpen()` | `rules-null-matrix` | Failed; source restored |
| `PackagingRulesTest.earlierRefusalsRemainAuthoritativeWhenTheMatrixIsAbsent()` | `rules-no-spec` | Failed; source restored |
| `PackagingRulesTest.missingSpecWinsForAnIncompleteLot()` | `rules-no-spec` | Failed; source restored |
| `PackagingRulesTest.wrongResolvedTierThrowsEvenWhenTheLotIsIncomplete()` | `rules-tier-match` | Failed; source restored |
| `PackagingRunTest.allPassAtChips4EmitsThreeOrderedChipEventsOfNinetyFe()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.allPassAtChips1Packages1FinishesOnZeroEnergy()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.allFailAtChips4NeverTouchesPackagesAndYieldsScrapOnly()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.allFailAtChips1Packages1ConsumesNoPackages()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.mixedRunAtChips4MatchesIndependentlyComputedTotals()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.mixedRunAtChips1MatchesIndependentlyComputedTotals()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.missingPackagesPauseBeforeEnergyOrOutputAreTouched()` | `run-packages` | Failed; source restored |
| `PackagingRunTest.fullOutputPausesBeforeEnergyIsPaid()` | `run-output-room` | Failed; source restored |
| `PackagingRunTest.energyPauseConservesProgressAndDrawsOnlyPaidTicks()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.zeroEnergyPaymentIsStillAllOrNothing()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.finishedRunDoesNothingOnExtraTicks()` | `run-finished` | Failed; source restored |
| `PackagingRunTest.everyWaferIsEmittedExactlyOnceInOrder()` | `run-finished` | Failed; source restored |
| `PackagingRunTest.unfinishedRunRejectsMissingPredicates()` | `run-missing-predicate` | Failed; source restored |
| `PackagingRunTest.midWaferSnapshotRestoresTheIdenticalResult()` | `run-restore` | Failed; source restored |
| `PackagingRunTest.repeatedSnapshotsBeforeTheNextTickRestoreToTheSameRun()` | `run-restore` | Failed; source restored |
| `PackagingRunTest.finishedSnapshotRestoresAFinishedRun()` | `run-finished` | Failed; source restored |
| `PackagingRunTest.invalidSnapshotsAreRejected()` | `run-null-plan` | Failed; source restored |
| `PackagingRunTest.invalidRunSchedulesAreRejected()` | `run-null-plan` | Failed; source restored |
| `PackagingRunTest.runDescriptionNeverShowsOutcomes()` | `run-private-text` | Failed; source restored |
| `PackagingRunTest.snapshotDescriptionNeverShowsOutcomes()` | `plan-private-text` | Failed; source restored |
| `PackagingRunTest.passingAndFailingCompletionsHaveTheSamePublicDescription()` | `run-duplicate` | Failed; source restored |
| `PackagingRunTest.missingPackagesMidWaferPreservePaidProgressAndResumeWithoutExtraCost()` | `run-finished` | Failed; source restored |
| `PackagingRunTest.fullOutputMidWaferPreservesPaidProgressAndResumesWithoutExtraCost()` | `run-finished` | Failed; source restored |
| `PackagingRunTest.failingWaferPausesMidProgressOnFullScrapOutputWithoutPackages()` | `run-scrap-room` | Failed; source restored |
| `PackagingRunTest.finishedRunAlsoIgnoresNullPredicates()` | `run-finished` | Failed; source restored |

Server negative controls:11 completed runs with 17 mutation entries cover all 24 new IDs. Extra failures are retained in the ledger; the table maps only required target assertions. The original all-gates bypass crashed on null spec and is excluded; head/anchor preflight stops executed no tests. Earlier9case evidence remains valid after a verified one-line break-fixture correction; production was unchanged.

| Registered GameTest ID | Fault group | Observed assertion failure |
| --- | --- | --- |
| `lithmod:test_package_judges_a_complete_logic_t1_lot` | judgment | Expected the logic at T1 chip bank holds 60 catalog chips rather than 48 to be 60: was 48 on tick 0 |
| `lithmod:test_package_judges_a_complete_logic_t2_lot` | judgment | Expected the logic at T2 chip bank holds 110 catalog chips rather than 88 to be 110: was 88 on tick 0 |
| `lithmod:test_package_judges_a_complete_memory_t2_lot` | judgment | Expected the memory at T2 chip bank holds 90 catalog chips rather than 72 to be 90: was 72 on tick 0 |
| `lithmod:test_package_returns_a_fresh_foup_of_the_same_item` | carrier | Expected the returned carrier rebuilt its capacity from the config (1) instead of keeping the 25 the judged lot had to be 26: was 25 on tick 0 |
| `lithmod:test_package_refuses_an_incomplete_lot` | gates | Expected the machine did not publish the expected refusal for a lot one layer short of finished to be null: was LOT_INCOMPLETE on tick 0 |
| `lithmod:test_package_refuses_a_die_with_no_catalog_chip` | gates | the machine started a queue for a complete compute lot whose die has no catalog chip on tick 0 |
| `lithmod:test_package_refuses_a_lot_without_a_run_spec` | gates | Expected the machine did not publish the expected refusal for a lot that was never processed to be GENERATION_UNMET: was NO_SPEC on tick 0 |
| `lithmod:test_package_enforces_the_packaging_generation_gate` | gates | the machine judged a lot while its generation was below the matrix requirement on tick 0 |
| `lithmod:test_package_never_judges_a_client_view_lot` | trust | a client view reached judgment instead of being ignored by the trusted read on tick 0 |
| `lithmod:test_package_pays_nothing_without_energy` | power | Expected the buffer was not drained exactly by the ticks that were paid for to be 100: was 0 on tick 0 |
| `lithmod:test_package_spends_packages_only_on_completion` | resources | Expected the completing tick did not take exactly package_per_wafer packages to be 17: was 16 on tick 0 |
| `lithmod:test_package_pauses_when_its_packages_are_taken` | resources | a machine with no packages for its next passing wafer is not marked paused on tick 0 |
| `lithmod:test_package_pauses_on_a_full_chip_output` | resources | a machine with a full chip bank is not marked paused on tick 0 |
| `lithmod:test_package_pauses_on_a_full_scrap_output` | resources | a machine with a full scrap bank is not marked paused on tick 0 |
| `lithmod:test_package_exposes_energy_only` | resources | Expected the energy capability let FE be pulled back out to be 500: was 0 on tick 0 |
| `lithmod:test_package_block_use_and_server_ticker` | resources | the test_package block has no ticker on a server level on tick 0 |
| `lithmod:test_package_refuses_plain_chips_behind_named_chips` | components | a bank of named chip stacks did not pause the machine on tick 0 |
| `lithmod:test_package_malformed_queue_nbt_fails_closed` | components | a snapshot with more chips per wafer than a slot holds was adopted as a live queue on tick 0 |
| `lithmod:test_package_menu_layout_and_public_values` | components | Expected the test package machine is not expected to sync exactly seven values to be 7: was 8 on tick 0 |
| `lithmod:test_package_keeps_a_second_lot_until_the_first_finishes` | isolation | Expected the queue stopped advancing once a second FOUP was in the slot to be 1: was 4 on tick 0 |
| `lithmod:test_package_break_drops_no_queued_outputs` | break | the dropped FOUP still holds a lot: the judged lot must never come back on tick 0 |
| `lithmod:test_package_save_load_round_trip_mid_wafer` | persistence | Expected save/load cycle 1 lost the paid progress to be 10: was 15 on tick 0 |
| `lithmod:test_package_keeps_frozen_parameters_for_an_accepted_run` | reload | Expected the machine did not report every wafer of the lot as finished to be 0: was 25 on tick 0 |
| `lithmod:test_package_resumes_when_its_definition_returns` | reload | a missing definition dropped the accepted queue on tick 0 |

Fixes discovered during verification: Completion descriptions leaked package use; accepted queues incorrectly depended on current invalid admission settings; output preflight/insertion needed component equality; saved quantities required bounds. Test repairs separately corrected current Minecraft APIs, admission-tick accounting, missing success signals and a pre-break total that omitted scrap. The first full combined preview passed774/775 because of that fixture arithmetic; corrected code/test149063bf passed775/775. The final restored source is checked clean; the separate merge candidate subsequently passed a fresh full positive run; see the final acceptance record.
The final candidate contains 775 distinct server cases: 774 passed, no required failures, and one optional Arcforge failure (rcforge:creosote_moves) recorded as skipped. All 105 project IDs passed. See the acceptance record for this result; the earlier measured source run above had all775passing.\n\n## 4. Spec conformance

| Requirement | Status | Evidence |
| --- | --- | --- |
| Definition and configurable timing/packages/FE | met | Machine JSON below; `resolveParams`, frozen energy from actual definition/settings. |
| Pure ordered plan, pass chips/packages and fail scrap/no packages | met | `PackagingPlan`; 15 plan tests, including all-pass/all-fail/mixed and quantities1/4. |
| One-wafer queue, atomic energy, package/output pauses and snapshot restore | met | `PackagingRun`;25 run tests; real resource/persistence server cases. |
| Five refusal reasons and real generation1 gate | met | `PackagingRules.validate`;19 rule tests; refusal and generation server cases. |
| FOUP/package input restrictions, four chip and two scrap extract-only outputs | met | `TestPackageMenu`;44 slots, slot predicates and shift-click assertions in menu GameTest. |
| Trusted input, frozen RunSpec tier/die/cores, judge exactly once and retain flags only | met | `attemptAdmission`, `admissionRefusal`, `startLot`; client-view, lot-judgment and second-lot cases. |
| Immediate original-lot consumption; fresh empty same-item/stored-capacity FOUP | met | `startLot`; returned-carrier test includes Mk1/Mk2 and changed configured capacity. |
| Breaking loses queued wafers and cannot recover original judged lot | met | `break_drops_no_queued_outputs`: actual block removal and dropped item contents; original-lot fault caught. |
| Catalog chips, chip_scrap and full-output pause/resume | met | `advance`, `bankRoom`, `insertIntoBank`;logicT1/T2/memoryT2 and both output-bank pause cases. |
| Menu state/counts/energy/progress without per-wafer results | partly | Exactly7server/client wire values; opposite private flags at matching public state; empty update tag/null packet. Actual rendering remains unverified. |
| Persist flags/progress/inventory | met | `saveAdditional`/`loadAdditional`; paid-progress roundtrip, frozen settings, missing definition and malformed queue cases. |
| Complete T1 lot: exact chip/scrap/package counts and empty new-ID carrier | met | `test_package_judges_a_complete_logic_t1_lot`; independent service oracle and configured arithmetic. T2 logic/memory also tested. |
| Incomplete and uncatalogued-die lots refused | met | `test_package_refuses_an_incomplete_lot`, `test_package_refuses_a_die_with_no_catalog_chip`. |
| Break, NBT resume, full outputs, no item handler and private menu data | met | Corresponding break/roundtrip/output/capability/menu cases, each with an executed failed production fault. |
| Hand-fed/server authority/core isolation/placeholder art/no dependency edits | met | Energy-only and hopper tests; server ticker/detached guard; checkCoreImports; only project source/resources changed. |
| Build/tests pass with reconciled counts and this report exists | met | 509 unit/74classes and 775 distinct server cases; all 105project IDs present/pass. Final acceptance is recorded in the linked acceptance report. |
| Every new test has an executed negative control | met | 59 core methods and 24 server IDs mapped below; exact restoration verified; crashing/preflight attempts excluded. |
## 5. Not verified

- Actual client rendering, client-world ticker branch and visual layout; server tests prove menu data, server use/ticker and detached-level guard only.
- Final two complete independent reviews and the separate master merge candidate at this report revision.
- Production crafting recipes/finished GUI assets and empty-FOUP initialization (M1-11).
- The real Process Cell end-to-end T1/T2 loop, three independent full server repetitions and owner client walkthrough (M1-12).
## 6. Deviations

| Spec says | Implementation | Reason |
| --- | --- | --- |
| Definition prose uses camelCase names | Existing loader uses snake_case JSON below | Preserve the actual established schema. |
| Refusal enum lists NO_LOT | Pure rules return it; an empty/missing/untrusted FOUP is idle at the machine | Nothing is offered; keep finished counts and avoid displaying a refusal for the returned empty carrier. |
| Generation check prose can be read with reversed numeric arguments | `isSatisfied("test_packaging",1,tierIndex)` | Actual API is line,installedGeneration,tierIndex. |
| Report drafts contained guessed IDs/APIs | Tables and handoff reconciled by coordinator from actual source | Worker completion is not evidence of factual correctness. |
| Fresh client visual verification | Deferred, explicitly unverified | Dedicated server tests cannot establish rendering correctness. |
## 7. Open questions

None for this task.
## 8. Handoff

Actual public signatures/accessors (Java record accessors are implicit; flags and completion result fields remain server-private):

```java
// com.euvcraft.lithmod.core
PackagingRules.validate(FoupContents lot, Tier tier, boolean chipItemAvailable,
    GenerationMatrix matrix, int installedGeneration) -> PackagingRefusalReason // null = accepted
PackagingPlan(List<Boolean> passFlags, int chipsPerWafer, int packagesPerWafer)
int size(); boolean passes(int waferIndex); int packagesFor(int waferIndex);
int passingCount(); int failingCount(); int totalChips(); int totalScrap(); int totalPackages();
PackagingRun(PackagingPlan plan, int ticksPerWafer, int energyPerTick)
PackagingRun(PackagingRun.Snapshot snapshot)
PackagingRun.Completion tick(IntPredicate packagesAvailable, IntPredicate outputRoom, IntPredicate payEnergy)
int totalWafers(); int wafersDone(); int ticksPaidOnCurrentWafer();
int ticksPerWafer(); int energyPerTick(); boolean isFinished(); PackagingRun.Snapshot save();
record Completion(int waferIndex, boolean passing, int amount, int packagesUsed)
record Snapshot(PackagingPlan plan, int ticksPerWafer, int energyPerTick, int currentIndex, int paidTicks)
// com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity
ContainerData queueData(); int state(); int wafersDone(); int wafersTotal();
boolean isQueueActive(); boolean isPaused(); PackagingRefusalReason refusalReason();
static boolean isPackage(ItemStack stack); void serverTick();
```

`PackagingRules.LINE_ID` is `test_packaging`; installed generation is1. Validation order is no-lot,no-spec,tier-consistency,completeness,catalog,generation; enum declaration/wire ordinals retain the task pack order. Predicates in `PackagingRun.tick` must not partly pay; the real base adapter supplies transactional full-or-zero FE.
FOUP0,package1,chip outputs2..5,scrap6..7;44menu slots. Base wire indices:DATA_ENERGY0,DATA_CAPACITY1,DATA_PROGRESS2,DATA_TICKS3. Own STATE_WIRE_INDEX4,DONE_WIRE_INDEX5,TOTAL_WIRE_INDEX6. Public progress is overall paid ticks across wafers, not a private wafer result.
Machine definition:`src/main/resources/data/lithmod/machines/test_package.json`. Existing config `chipsPerWafer` permits1..64, default4.

```json
{
  "id": "test_package",
  "energy_capacity": 20000,
  "max_receive": 2000,
  "energy_per_tick": 20,
  "per_tier_energy_per_tick": {},
  "params": {
    "ticks_per_wafer": 10,
    "package_per_wafer": 1,
    "input_slots": 2,
    "output_slots": 6
  }
}
```

Private NBT child `packaging`:flags01string,chips_per_wafer,packages_per_wafer,ticks_per_wafer,energy_per_tick,index,paid_ticks,die_type,tier. Inventory and buffer use base persistence. A malformed queue is discarded without recreating the original lot; real finished inventory remains. Missing definition preserves the queue but closing base capacity can discard FE, so recharge the remaining cost after restoration. Invalid new admission settings do not alter accepted frozen parameters.
All actual new registered IDs:

| Actual registered GameTest ID |
| --- |
| `lithmod:test_package_judges_a_complete_logic_t1_lot` |
| `lithmod:test_package_judges_a_complete_logic_t2_lot` |
| `lithmod:test_package_judges_a_complete_memory_t2_lot` |
| `lithmod:test_package_returns_a_fresh_foup_of_the_same_item` |
| `lithmod:test_package_refuses_an_incomplete_lot` |
| `lithmod:test_package_refuses_a_die_with_no_catalog_chip` |
| `lithmod:test_package_refuses_a_lot_without_a_run_spec` |
| `lithmod:test_package_never_judges_a_client_view_lot` |
| `lithmod:test_package_enforces_the_packaging_generation_gate` |
| `lithmod:test_package_pays_nothing_without_energy` |
| `lithmod:test_package_spends_packages_only_on_completion` |
| `lithmod:test_package_pauses_when_its_packages_are_taken` |
| `lithmod:test_package_pauses_on_a_full_chip_output` |
| `lithmod:test_package_pauses_on_a_full_scrap_output` |
| `lithmod:test_package_refuses_plain_chips_behind_named_chips` |
| `lithmod:test_package_keeps_a_second_lot_until_the_first_finishes` |
| `lithmod:test_package_break_drops_no_queued_outputs` |
| `lithmod:test_package_save_load_round_trip_mid_wafer` |
| `lithmod:test_package_keeps_frozen_parameters_for_an_accepted_run` |
| `lithmod:test_package_resumes_when_its_definition_returns` |
| `lithmod:test_package_malformed_queue_nbt_fails_closed` |
| `lithmod:test_package_menu_layout_and_public_values` |
| `lithmod:test_package_exposes_energy_only` |
| `lithmod:test_package_block_use_and_server_ticker` |

Verification ledgers: `m1-10-boundary-core-negative-coverage.json`, the three core-negative summaries, `m1-10-runtime-negative-coverage.json`, `m1-10-runtime-control-source-equivalence.json` and the full positive check/XML/row tables in coordinator `.orchestrator/m1/`. Nemotron/GLM/DeepSeek/Space Bunny writer history and Beam sessions have saved receipts; incomplete worker streams were retained and never treated as verified implementation. All worker inference was restricted to approved free aliases. Original mk4 and remote Git are unchanged.
