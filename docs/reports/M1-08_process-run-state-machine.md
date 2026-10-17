> Historical branch report. Current integration status, corrected APIs, and fresh verification evidence are in [the consolidation report](CONSOLIDATION_2026-10-16.md).

# M1-08: Process run state machine (core)

- Date: 2026-10-16
- Branch: m1-08-process-run-state-machine
- Commit hash: 363efbb75ab977c6bbce5a14ab9280850127ec44
- Java version used: 25 (Gradle toolchain)
- Build/test commands: .\gradlew.bat test checkCoreImports --offline

## 1. Summary: 3 to 6 sentences.
Added the pure core state machine for one Process Cell run. Refusal reasons, process states, environment interface, run request, events, tick advancement, pauses, layer completion, mask consumption, and compact snapshot/restore are implemented without Minecraft types. A partly processed lot continues from its layer; T1 and T2 runs finish after exactly layerTicks per layer with the expected applyLayer calls; resist is paid once per layer and survives pauses and snapshot restore. Abandonment loses only in-progress layer ticks and that layer's resist; completed layers stay in the lot. All unit and import checks pass.

## 2. Files changed: a table with columns path, new/modified/deleted, purpose.
| path | new/modified/deleted | purpose |
|------|--------------------|---------|
| src/main/java/com/euvcraft/lithmod/core/ProcessState.java | new | Process run state enum |
| src/main/java/com/euvcraft/lithmod/core/ProcessRunEvent.java | new | Tick event record and factory helpers |
| src/main/java/com/euvcraft/lithmod/core/ProcessTickResult.java | new | Tick result record |
| src/main/java/com/euvcraft/lithmod/core/ProcessEnvironment.java | new | Caller environment interface |
| src/main/java/com/euvcraft/lithmod/core/RefusalReason.java | new | Refusal reason with unmet generation names |
| src/main/java/com/euvcraft/lithmod/core/ProcessRunRequest.java | new | Run inputs record |
| src/main/java/com/euvcraft/lithmod/core/ProcessRun.java | new | State machine, tick, start, snapshot, restore |
| src/test/java/com/euvcraft/lithmod/core/ProcessRunTest.java | new | 19 unit tests for refusals, runs, pauses, snapshot, continuation |

## 3. Verification: the exact commands you ran with their real output trimmed to the relevant lines, plus a test table with columns test class, tests, failures, errors, skipped, and a total that equals the sum of the rows.
Commands:

.\gradlew.bat test checkCoreImports --offline

Relevant output:

> Task :checkCoreImports
Core import check passed: no Minecraft/NeoForge imports found in core package.

BUILD SUCCESSFUL in 44s
386 tests completed, 0 failed


Test table (386 tests, 0 failures, 0 errors, 0 skipped):

| test class | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| core.ContextualRngTest | 14 | 0 | 0 | 0 |
| core.CoreTest | 2 | 0 | 0 | 0 |
| core.DieRulesTest$Validation | 3 | 0 | 0 | 0 |
| core.DieRulesTest | 5 | 0 | 0 | 0 |
| Builder pattern | 3 | 0 | 0 | 0 |
| Clamping of simple values | 12 | 0 | 0 | 0 |
| Comfortable core overrides parsing | 2 | 0 | 0 | 0 |
| Core count options validation | 3 | 0 | 0 | 0 |
| Default values | 2 | 0 | 0 | 0 |
| Equality and hashCode | 2 | 0 | 0 | 0 |
| core.FabSettingsTest$Immutability | 2 | 0 | 0 | 0 |
| List-based parsing (for NeoForge config) | 8 | 0 | 0 | 0 |
| Round-trip: map to list and back | 3 | 0 | 0 | 0 |
| Tier yield overrides parsing | 6 | 0 | 0 | 0 |
| capacity limits | 4 | 0 | 0 | 0 |
| immutability | 6 | 0 | 0 | 0 |
| invalid packed data | 4 | 0 | 0 | 0 |
| packing round trip | 6 | 0 | 0 | 0 |
| public view | 4 | 0 | 0 | 0 |
| Wafer | 4 | 0 | 0 | 0 |
| core.FoupContentsTest | 2 | 0 | 0 | 0 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| core.LineLoadoutTest | 7 | 0 | 0 | 0 |
| energyPerTick resolution | 9 | 0 | 0 | 0 |
| parsing | 7 | 0 | 0 | 0 |
| validation | 6 | 0 | 0 | 0 |
| an ingredient with a count | 3 | 0 | 0 | 0 |
| the energy override | 3 | 0 | 0 | 0 |
| a single item ingredient | 5 | 0 | 0 | 0 |
| slot order | 2 | 0 | 0 | 0 |
| a tag ingredient | 5 | 0 | 0 | 0 |
| construction validation | 6 | 0 | 0 | 0 |
| core.MachineRecipeSpecTest | 6 | 0 | 0 | 0 |
| core.multiblock.StructureMatcherTest | 45 | 0 | 0 | 0 |
| refusals | 9 | 0 | 0 | 0 |
| runs | 9 | 0 | 0 | 0 |
| snapshot | 1 | 0 | 0 | 0 |
| codec round trip | 2 | 0 | 0 | 0 |
| validation | 3 | 0 | 0 | 0 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 |
| calibration accuracy | 6 | 0 | 0 | 0 |
| the cumulative roll | 3 | 0 | 0 | 0 |
| the defect factor | 2 | 0 | 0 | 0 |
| the gamma functions behind the calibration | 7 | 0 | 0 | 0 |
| clamping | 2 | 0 | 0 | 0 |
| core count penalty | 9 | 0 | 0 | 0 |
| pass or fail | 5 | 0 | 0 | 0 |
| resolveEffectiveYield | 10 | 0 | 0 | 0 |
| judging a lot | 3 | 0 | 0 | 0 |
| applying a layer | 8 | 0 | 0 | 0 |
| rejecting a step | 7 | 0 | 0 | 0 |
| mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| mc.FabYieldServiceTest | 11 | 0 | 0 | 0 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| mc.ItemAssetResourceTest | 3 | 0 | 0 | 0 |
| **Total** | **386** | **0** | **0** | **0** |

## 4. Spec conformance: one row for every bullet in your task prompt and every Done-when criterion, with columns item, met/partly/not met, evidence (file, test name or output line).
| item | met/partly/not met | evidence |
|------|-------------------|----------|
| request/inputs type | met | ProcessRunRequest.java |
| RefusalReason with all listed codes and unmet names | met | RefusalReason.java; ProcessRunTest.Refusals.generationUnmetUsingCustomMatrix |
| ProcessState with all listed states | met | ProcessState.java |
| ProcessEnvironment interface | met | ProcessEnvironment.java |
| ProcessRun start returns run or refusal | met | ProcessRun.start; all ProcessRunTest.Refusals |
| first layer = lot current layer + 1 | met | ProcessRun constructor; ProcessRunTest.Runs.partWayLotContinuesFromLayer |
| run ends at tier.layers | met | ProcessRun.tick; ProcessRunTest.Runs.t2FinishesAfterExpectedTicks |
| each layer needs layerTicks(layer, settings) ticks | met | ProcessRun.tick; t1/t2 tests |
| full energy per tick or PAUSED_NO_POWER | met | ProcessRun.tick; ProcessRunTest.Runs.pauseNoPowerDoesNotAdvance |
| structure not formed -> PAUSED_STRUCTURE | met | ProcessRun.tick; ProcessRunTest.Runs.pauseStructureDoesNotAdvance |
| resist paid once per layer through environment | met | ProcessRun.tick; resist test and snapshot test |
| resist across pause and restore | met | ProcessRunTest.SnapshotOps.restoreMidLayerKeepsResistPaidFlag |
| applyLayer on layer completion | met | ProcessRun.tick; applied list assertions |
| MASK_USE_CONSUMED once per finished run | met | ProcessRun.tick; ProcessRunTest.Runs.maskConsumedOnceWithEvents |
| snapshot and restore | met | ProcessRun.snapshot/restore; ProcessRunTest snapshot tests |
| T1 run exact ticks and applyLayer calls | met | ProcessRunTest.Runs.t1FinishesAfterExpectedTicks |
| T2 run exact ticks and applyLayer calls | met | ProcessRunTest.Runs.t2FinishesAfterExpectedTicks |
| pause/resume power/resist/structure | met | pause tests |
| snapshot mid-run identical uninterrupted run | met | ProcessRunTest.Runs.snapshotMidRunProducesIdenticalResult |
| part-way lot continues; complete refused | met | partWay... and lotAlreadyComplete tests |
| mask with one use consumed once | met | ProcessRunTest.Runs.maskConsumedOnceWithEvents |
| abandonment documented | met | report section 6 |
| unit tests and import check pass | met | gradle output; 386 tests |

## 5. Not verified: everything you did not run or could not check, with the reason.
- Real Minecraft game tests were not run for this task because it is core-only; the core and import checks were run.

## 6. Deviations: a table with columns spec says, what you did, why.
| spec says | what you did | why |
|-----------|--------------|-----|
| start returns a run or a RefusalReason | returned Object with ProcessRun or RefusalReason | Java has no built-in either type; documented Object |
| resist consumed even on failure? | resist paid only when enough available | requirement says without enough resist state pauses, no payment |
| snapshot is compact | stores progress flags only; request and lot come from caller | avoids duplicating lot/inputs |
| abandonment loses in-progress progress and that layer's resist | layers already applied remain; lot is mutated in run | specified behavior |

## 7. Open questions: a numbered list, or "none".
1. The M1-09 controller must pass the current lot back into the request when reattaching a restored run, because the snapshot does not store the lot.

## 8. Handoff: the public classes, method signatures, data file paths and config keys that later tasks will use, extracted from the source (not written from memory).
Core public API:
- com.euvcraft.lithmod.core.ProcessState
- com.euvcraft.lithmod.core.ProcessRunEvent(Type type, int layerNumber), ProcessRunEvent.Type {LAYER_COMPLETED, MASK_USE_CONSUMED, FINISHED} (actual MASK_USE_CONSUMED)
- com.euvcraft.lithmod.core.ProcessTickResult(ProcessState, List<ProcessRunEvent>)
- com.euvcraft.lithmod.core.ProcessEnvironment
- com.euvcraft.lithmod.core.RefusalReason(Code code, List<String> unmetLines), static constants and generationUnmet(List<String>)
- com.euvcraft.lithmod.core.ProcessRunRequest
- com.euvcraft.lithmod.core.ProcessRun, methods start(Object), tick(ProcessTickResult), lot, state, snapshot, restore
- config keys: FabSettings.resistPerLayer, FabSettings.layerTicks (override), FabSettings.energyPerTick resolved by caller

No new data files or config keys.
