# M1-09 Process Cell Controller — Task Report

Accepted after two independent final reviews and a separate full merge-candidate check. See [final acceptance](M1-09_acceptance.md) for exact results and the optional Arcforge result.

## Summary
M1-09 delivers the Process Cell multiblock controller: a 2×2×3 structure (12 members, four horizontal facings) with a single inherited energy buffer, inventory, drop handling, and menu helpers. The controller block entity extends `FabMachineBlockEntity` and implements `EnergyBufferProvider` and `ProcessEnvironment`. The menu (`ProcessCellMenu.java`/`ProcessCellScreen.java`) exposes 44 slots (8 machine + 36 player) and 12 public integer fields for energy, capacity, progress, status, refusal, layer, tier, die, and lock state. Server-side logic uses the existing mutable `ProcessRun` state machine and `FabYieldService` for layer application; `ProcessCellRunPersistence` provides a disk-only validated `Contract`/`Saved` codec. Game tests live in `ProcessCellGameTests.java`.

## Verified Behavior
- **Formation & Admission**: Server automatically admits a trusted, filled FOUP while the multiblock is formed, idle, and not abort-latched. Admission reads `LineLoadoutReader`, `DefaultStatRules`, mask, and the generation matrix. The named map `M1_STUB_GENERATIONS` is the M1 stub, with entries of all five support lines at generation 1 (not a geometry version).
- **FOUP & Mask**: Exactly 8 input slots (FOUP 0, mask 1, resist 2, light 3, optics 4, stage 5, cleanroom 6, metrology 7); no output slots. Active FOUP, mask, and components are locked. Resist top-up uses only the same item + components as the accepted template, even when empty; no extraction, swap, or creative clone.
- **Energy**: Data file `process_cell.json` defines `energy_capacity=100000`, `max_receive=10000`, `energy_per_tick=20`, `per_tier_energy_per_tick: T1=20 T2=40`. Resolved FE uses `MachineDefinition.energyPerTick(tier, settings)`; no whole-run prepayment. Only formed power ports accept FE; external extract is 0; no item/fluid capabilities anywhere.
- **Run States**: `IDLE`, `RUNNING`, `PAUSED_NO_POWER`, `PAUSED_NO_RESIST`, `PAUSED_STRUCTURE`, `FINISHED`. Refusal reason is separate from state ordinals.
- **Execution**: Each paid tick uses frozen FE; resist once per layer; layer applied by `FabYieldService.applyLayerStep` then physical FOUP updated (same item/UUID). Finished lot stays; one mask use consumed at whole-run finish; spent mask removed; no further terminal charges or replay.
- **Abort**: Unlocks inputs, keeps applied layers, discards partial progress without FE/resist refund, no mask use. Stays stopped across reload until actual FOUP removal/reinsertion (even two actions between ticks). Reinsertion restarts from zero and pays new work.
- **Persistence**: Private accepted contract, timing, resist, FE, line, tiers, matrix, input templates, UUID plus a pure snapshot (state/layer/progress/paid/finished). No private NBT network. Re-form/missing-definition pauses preserve progress/paid layer; reload before world validation cannot bypass formation. Changed live energy/settings do not rewrite accepted timing/cost. Yield settings read by `FabYieldService` at first applied layer; not all yield globals frozen at admission. Malformed/contradictory save flags or private codec/input tamper latch → stopped rather than fresh admission; legacy all-keys-absent → idle allowed. Break drops remaining inventory once; FOUP keeps completed layers. No chips/scrap created.
- **Menu/Client**: Server button 0 validates player/current menu/world/range/live controller/active state. Ordinary left/right, shift, normal drag work. Creative clone blocked. Public 12 fields wire to client; client receives only coarse status and public FOUP data; trusted physical FOUP remains server. Headless client menu wire test is not actual rendering proof.
- **T2 Fixture**: 3 ticks/layer, 2 resist/layer, 10000 FE, 64 resist, 4 uses. Example: tick 4 → layer 1 progress 1, resist 60, mask 4, FE 10000-4 cost; finish at 36 paid ticks, resist 40, mask 3.

## Defects Found & Fixed
- Stripped FOUP template validation used component-dependent `isFoup` (wrong); changed to actual MK1/MK2 check.
- Vanilla occupied-slot pickup guard blocked resist merge; fixed with insertion-only `safeInsert`.
- Single-target drag `private doClick` bypassed public override; fixed with temporary insertion-only resist pickup flag cleared in `finally`; normal types 0/1 only, creative type 2 blocked.
- Malformed `getBooleanOr` flags restarted work; fixed with `Codec.PASSTHROUGH` + strict `Codec.BOOL` + contradiction checks.
- Nemotron drafts contained compiler/API errors and bad test expectations; coordinator repaired and integrated.

## Test Evidence (Verified)
- **Production commit 12c3ebf**: Full offline Gradle `test checkCoreImports jar runGameTestServer --offline --console=plain` passed 509 unit tests in 74 classes and all 791 distinct server cases including all 121 registered project IDs; no failures/errors/skips.
- **Current commit 632d867**: Adds stronger test assertions only. 17 selected `ProcessCell` prefix cases (16 new + 1 old block drop) pass.
- **Fault injection**: Five backend production fault groups at `fe1da41f126b221304929c4227115dd0604889f1` cover 6 new cases with 9 expected failures/exact restore. Six menu fault groups at 86307 each run 12 prefix cases and caused expected assertion failures/exact restore. Five additional new-safety faults completed at 632d867: all 5 corresponding new cases failed intended runtime assertions in one combined mutant run; all 17 selected cases executed; exact source bytes restored in `finally` and HEAD clean. This is one combined five-fault control, not six independent runs.
- **Meaningful server tests**: T1 logic 1 layer / T2 memory 12 layers + resource conservation; weak T1 line T2 refusal; abort/reload/reinsert; empty resist template/top-up/reload/changed settings; structure pause/reload; active click locks / ordinary top-ups / normal drag / creative clone blocked; empty top-ups; invalid abort ID/menu/distance/world/stale block; public 12 field wire / client locks / refusal; actual block use; missing definition pause/reload/changed FE; 9 malformed variants + valid resume + legacy idle; tamper all 8 input slots; partial break exact metadata and counts; capabilities all 12 members 6 sides + null.

## Status & Acceptance

| Criterion | Status |
|-----------|--------|
| Core controller, persistence, menu, screen, client events, block, registration, game tests | Implemented |
| Production 12c3ebf full offline Gradle suite (509 units / 74 classes, 791 server / 121 project IDs) | Passed |
| Current 632d867 17-case `ProcessCell` prefix check | Passed |
| Five backend fault groups (`fe1da41f126b221304929c4227115dd0604889f1`) — 6 cases, 9 expected failures, exact restore | Verified |
| Six menu fault groups (86307) — 12 prefix cases each, expected failures, exact restore | Verified |
| Five new-safety faults combined mutant run — 5 cases failed assertions, 17 cases executed, exact restore | Verified |
| Post-restoration positive check & candidate checks | Passed: 509 units / 74 classes; all 121 project IDs; 790 server passes plus one optional Arcforge result |
| Two final independent whole-task reviews | Approved: distinct free Nemotron sessions; final Beam attempts were quota-limited |
| Candidate checks & master merge | Verified and integrated; see final acceptance |
| mk4 untouched; no background daemon/push | Verified |
| Verified backup bundles/state at checkpoints | Verified |

## Follow-ups (Not in M1-09)
- **M1-11**: Missing GUI bar assets / visual fill check; recipes / empty FOUP initialization / creative tab order.
- **M1-12**: Whole T1/T2 machine chains; privacy audit; hand-fed audit; 3 independent server runs; real client walkthrough.

No full playable or visual claim. All workers free-only attested zero cost; coordinator owns Git/tests/backups; Beam reviews; Nemotron code/docs drafts. Follow user Abort/top-up decisions.


## Exact public menu wire

| Index | Public value |
| --- | --- |
| 0 | Energy |
| 1 | Energy capacity |
| 2 | Current-layer progress ticks |
| 3 | Current-layer total ticks |
| 4 | Coarse status (ProcessState ordinal; Abort=6; missing definition=7) |
| 5 | Refusal code ordinal + 1, or 0 for none |
| 6 | Current layer (clamped for display) |
| 7 | Total layers |
| 8 | Structure formed (0/1) |
| 9 | Mask tier (one based, or 0) |
| 10 | DieTypes.ALL index + 1, or 0 |
| 11 | Active inputs locked (0/1) |

## Registered new server cases

Derived from the actual registered constants in `ProcessCellGameTests.java`; the pre-existing `lithmod:process_cell_blocks_drop_themselves` is additional to these 16.

| Actual GameTest ID |
| --- |
| `lithmod:process_cell_logic_t1_conservation` |
| `lithmod:process_cell_memory_t2_conservation` |
| `lithmod:process_cell_weak_line_refused` |
| `lithmod:process_cell_abort_latch_round_trip` |
| `lithmod:process_cell_empty_resist_restore_and_top_up` |
| `lithmod:process_cell_structure_pause_restore` |
| `lithmod:process_cell_menu_active_locks` |
| `lithmod:process_cell_menu_empty_resist_top_up` |
| `lithmod:process_cell_abort_button_validation` |
| `lithmod:process_cell_menu_public_wire` |
| `lithmod:process_cell_block_use_opens_menu` |
| `lithmod:process_cell_definition_pause_restore` |
| `lithmod:process_cell_malformed_state_stays_stopped` |
| `lithmod:process_cell_active_input_tamper_stops` |
| `lithmod:process_cell_partial_break_conserves_inventory` |
| `lithmod:process_cell_hand_fed_capabilities` |

## Executed production controls

Each baseline passed before its controls. Only completed server assertion failures count; compiler/launch failures are excluded. The last row is one combined five-fault run, with five independently targeted assertions. Every run restored the exact original bytes and verified clean HEAD.

| Group | Frozen source | Expected failing cases | Receipt label |
| --- | --- | --- | --- |
| abort | `fe1da41f` | `lithmod:process_cell_abort_latch_round_trip` | `m1-09-negative-abort-1792338566644` |
| admission | `fe1da41f` | `lithmod:process_cell_weak_line_refused` | `m1-09-negative-admission-1792338425910` |
| energy | `fe1da41f` | `lithmod:process_cell_logic_t1_conservation`, `lithmod:process_cell_memory_t2_conservation`, `lithmod:process_cell_abort_latch_round_trip`, `lithmod:process_cell_empty_resist_restore_and_top_up`, `lithmod:process_cell_structure_pause_restore` | `m1-09-negative-energy-1792338287181` |
| frozen | `fe1da41f` | `lithmod:process_cell_empty_resist_restore_and_top_up` | `m1-09-negative-frozen-1792338842189` |
| structure | `fe1da41f` | `lithmod:process_cell_structure_pause_restore` | `m1-09-negative-structure-1792338706851` |
| abort | `86307f64` | `lithmod:process_cell_abort_button_validation` | `m1-09-menu-negative-abort-1792343667299` |
| block | `86307f64` | `lithmod:process_cell_block_use_opens_menu` | `m1-09-menu-negative-block-1792344185746` |
| clone | `86307f64` | `lithmod:process_cell_menu_active_locks` | `m1-09-menu-negative-clone-1792343434157` |
| drag | `86307f64` | `lithmod:process_cell_menu_active_locks` | `m1-09-menu-negative-drag-1792344404998` |
| topup | `86307f64` | `lithmod:process_cell_menu_active_locks`, `lithmod:process_cell_menu_empty_resist_top_up` | `m1-09-menu-negative-topup-1792343224834` |
| wire | `86307f64` | `lithmod:process_cell_menu_public_wire` | `m1-09-menu-negative-wire-1792343991736` |
| all | `632d8674` | `lithmod:process_cell_definition_pause_restore`, `lithmod:process_cell_malformed_state_stays_stopped`, `lithmod:process_cell_active_input_tamper_stops`, `lithmod:process_cell_partial_break_conserves_inventory`, `lithmod:process_cell_hand_fed_capabilities` | `m1-09-safety-negative-all-1792345931411` |

## Unit XML arithmetic

Measured at `12c3ebf4c141bd50388fa8063038be6439f3bd59`. Each row counts actual JUnit XML testcase records. No failures, errors or skips occurred in these 74 classes.

| Actual class | Tests |
| --- | ---: |
| `com.euvcraft.lithmod.core.ContextualRngTest` | 14 |
| `com.euvcraft.lithmod.core.CoreTest` | 2 |
| `com.euvcraft.lithmod.core.DieRulesTest` | 5 |
| `com.euvcraft.lithmod.core.DieRulesTest$Validation` | 3 |
| `com.euvcraft.lithmod.core.FabSettingsTest$BuilderPattern` | 3 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Clamping` | 12 |
| `com.euvcraft.lithmod.core.FabSettingsTest$ComfortableCoreOverrides` | 2 |
| `com.euvcraft.lithmod.core.FabSettingsTest$CoreCountOptions` | 3 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Defaults` | 2 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Equality` | 2 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Immutability` | 2 |
| `com.euvcraft.lithmod.core.FabSettingsTest$ListParsing` | 8 |
| `com.euvcraft.lithmod.core.FabSettingsTest$RoundTrip` | 3 |
| `com.euvcraft.lithmod.core.FabSettingsTest$TierYieldOverrides` | 6 |
| `com.euvcraft.lithmod.core.FoupContentsTest` | 2 |
| `com.euvcraft.lithmod.core.FoupContentsTest$CapacityTest` | 4 |
| `com.euvcraft.lithmod.core.FoupContentsTest$ImmutabilityTest` | 6 |
| `com.euvcraft.lithmod.core.FoupContentsTest$InvalidDataTest` | 4 |
| `com.euvcraft.lithmod.core.FoupContentsTest$PackingTest` | 6 |
| `com.euvcraft.lithmod.core.FoupContentsTest$PublicViewTest` | 4 |
| `com.euvcraft.lithmod.core.FoupContentsTest$WaferTest` | 4 |
| `com.euvcraft.lithmod.core.GenerationMatrixTest` | 8 |
| `com.euvcraft.lithmod.core.ImportCheckerTest` | 4 |
| `com.euvcraft.lithmod.core.LineLoadoutTest` | 8 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$EnergyPerTick` | 9 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$Parsing` | 7 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$Validation` | 6 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest` | 6 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Counts` | 3 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Energy` | 3 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$SingleItem` | 5 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$SlotOrder` | 2 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Tags` | 5 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Validation` | 6 |
| `com.euvcraft.lithmod.core.MaskDataTest` | 12 |
| `com.euvcraft.lithmod.core.MaskRulesTest` | 19 |
| `com.euvcraft.lithmod.core.multiblock.StructureMatcherTest` | 45 |
| `com.euvcraft.lithmod.core.PackagingPlanTest` | 15 |
| `com.euvcraft.lithmod.core.PackagingRulesTest` | 19 |
| `com.euvcraft.lithmod.core.PackagingRunTest` | 25 |
| `com.euvcraft.lithmod.core.ProcessRunTest` | 10 |
| `com.euvcraft.lithmod.core.ProcessRunTest$Refusals` | 9 |
| `com.euvcraft.lithmod.core.ProcessRunTest$Runs` | 9 |
| `com.euvcraft.lithmod.core.ProcessRunTest$SnapshotOps` | 1 |
| `com.euvcraft.lithmod.core.RunSpecTest$CodecRoundTrip` | 2 |
| `com.euvcraft.lithmod.core.RunSpecTest$Validation` | 3 |
| `com.euvcraft.lithmod.core.StatModelVerificationTest` | 10 |
| `com.euvcraft.lithmod.core.TierRegistryTest` | 34 |
| `com.euvcraft.lithmod.core.WaferSlicingTest` | 7 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$Accuracy` | 6 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$CumulativeRoll` | 3 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$DefectFactor` | 2 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$GammaFunctions` | 7 |
| `com.euvcraft.lithmod.core.YieldMathTest$Clamping` | 2 |
| `com.euvcraft.lithmod.core.YieldMathTest$CoreCountPenalty` | 9 |
| `com.euvcraft.lithmod.core.YieldMathTest$PassThreshold` | 5 |
| `com.euvcraft.lithmod.core.YieldMathTest$ResolveEffectiveYield` | 10 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest` | 1 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$Judging` | 3 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$LayerStep` | 8 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$Rejections` | 7 |
| `com.euvcraft.lithmod.mc.data.DieRulesDataLoaderFallbackTest` | 1 |
| `com.euvcraft.lithmod.mc.data.DieRulesJsonDataTest` | 2 |
| `com.euvcraft.lithmod.mc.data.JsonResourceDataLoaderReloadTest` | 1 |
| `com.euvcraft.lithmod.mc.data.SupportLineDataLoaderFallbackTest` | 1 |
| `com.euvcraft.lithmod.mc.data.SupportLineJsonDataTest` | 1 |
| `com.euvcraft.lithmod.mc.data.TierDataLoaderFallbackTest` | 2 |
| `com.euvcraft.lithmod.mc.data.TierJsonDataTest` | 1 |
| `com.euvcraft.lithmod.mc.FabConfigTest` | 3 |
| `com.euvcraft.lithmod.mc.FabRandomServiceTest` | 8 |
| `com.euvcraft.lithmod.mc.FabYieldServiceTest` | 11 |
| `com.euvcraft.lithmod.mc.FoupContentsCodecTest` | 13 |
| `com.euvcraft.lithmod.mc.ItemAssetResourceTest` | 3 |
| `com.euvcraft.lithmod.mc.machine.mask.MaskDataCodecTest` | 10 |
| **Total (74 classes)** | **509** |

## Source handoff

- `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellControllerBlockEntity.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellRunPersistence.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellMenu.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellScreen.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellClientEvents.java`
- `src/main/java/com/euvcraft/lithmod/mc/gametest/ProcessCellGameTests.java`

The persistence helper's `Contract` records validated admission inputs; `Saved` carries that contract and the pure `ProcessRun.Snapshot`. Its codec is for server disk persistence. The controller owns the only server tick path; it does not tick the inherited generic recipe runner. `ProcessCellMenu` owns input protection and public synchronization; the screen uses those public values. Final review records and integration acceptance are committed beside this report.

