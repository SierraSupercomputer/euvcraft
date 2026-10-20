# M1-12 gate verification and handoff

Game source: `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`. Accepted M1-11 baseline: `b6272bff7e8d7bbec7d08722933ce0efad5cd0a4`.
This report was reconciled against saved coordinator receipts after preserving the Nemotron draft at `d62deed965d91da99db1bc903ae93249f11f22db`. Final whole-task reviews, separate candidate verification and integration are pending at authoring time. The owner has confirmed initial singleplayer entry; the full rendered-client walkthrough is pending.

## Scope and precedence

The October 14 M1 task pack takes precedence over the older October 12 design references. The owner chose quartz for silicon smelting/blasting, preserving sand-to-glass; copper/iron/redstone plus blank masks make T1 logic/T2 logic/T2 memory masks. Same-type-and-components resist top-ups are permitted; Abort remains latched until FOUP removal/reinsertion. The owner already selected **T6** as the beginning of future core-count choices. That decision is settled and remains outside this T1/T2 playable slice.

The [baseline audit](M1-12_baseline-audit.md) inventories 28 prior reports/revisions/reviews and records precedence. It is documentation based on accepted M1-11 evidence (513 units / 75 classes, 801 server cases, all 131 project IDs), not a new M1-12 execution.

## Chain, statistics and boundary gates

- At `a51df79896ccbd7764a4da0d5b6ac912044f2851`, two real server cases passed quartz smelting and blasting, Crystal Puller, Wafer Slicer, JSON-driven Pattern Generator, a formed Process Cell and Test and Package for T1 logic and twelve-layer T2 memory. They check item conservation, actual FE costs, one mask use per whole lot and the returned empty FOUP's new identity. Focused Beam review approved.
- At `d3eaf2bfd14cea74d742c38a967df899aa4308d9`, the four chain/statistics cases passed. The statistical sample is 200 lots of 64 wafers per tier (12,800 wafers each); twenty additional complete machine lots comprise ten per tier with distinct lot identities. Phase samples: T1 7649/12,800; T2 10239/12,800. Targets are 0.6 and 0.8, absolute tolerance 0.02. Focused Beam review approved.
- At `fe02005c2b2f8e46a830f731b99c67b84c59bd80`, all 138 project cases and 517 units / 76 classes passed. Actual stream serialization, real public menu tuples, tooltips and captured routine logs remain invariant when hidden wafer data changes. The companion lexical source audit is bounded, not a formal information-flow proof. Diagnostic/raw access does not establish player-visible access. Capability checks cover all eight fixture-enabled registered blocks on six directions plus null, and twelve formed Process Cell members. Focused Beam review approved.
- A retained earlier 137/138 run exposed inventory drops from the twenty-lot fixture contaminating a later existing drop case. Inventory-first local teardown fixed the contamination; production drop behavior and existing drop assertions were preserved. That failure is not counted as a pass.

## Reload and debug energy

At `fb540218dd620aa578a632145d58f7a893302758`, all 140 project server IDs and 517 units / 76 classes passed. A real data-pack machine-definition override measures the Crystal Puller's complete 400 paid ticks at 20 FE/tick, then 27, then restored 20; original resource priority and configuration are restored. The config-gated debug command is `/debug fab energy`: permission levels 0/2/3, real player look target, formed controller, miss/non-machine/console, redirected command dimension and runtime disabling are exercised. The target belongs to the player's world. Logger configuration and appenders are restored exactly. Focused Beam review approved this combined phase.

## Shipping fixtures

At `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`, fixtures require the explicit `lithmodFixtures` JVM property **and** a non-production loaded FML environment. Normal client/server runs omit test-machine block/item/block-entity/menu/capability/screen registration; runtime loaders filter its recipe/definition payloads while pure builder APIs remain usable by tests. The production Test and Package machine remains present.

The release jar has 410 entries and omits exactly eight test-machine JSON resources, using jar-only exclusions rather than altering development resources. Three shared block tags retain all seven production blocks and optional fixture references. Fixture loot uses NeoForge's registered-item condition. Fixture class files may remain in the jar; physical class stripping is not claimed. Verified source-phase jar SHA-256: `b4dddc8072951348cbe3f9b8a078e84be56d78fb7c304fe8fab5b5f9d4050956`.

The separate fixtures-disabled server probe passes exactly `lithmod:m1_gate_shipping_no_fixtures`, checking registry absence plus nonempty production/creative controls. It is excluded from the 140 ordinary project IDs and 810 ordinary full-suite counts. It does not repeat a seven-block all-side capability test. The first off-mode registry-loading failure and its repair are preserved. Focused Beam fixture review approved.

## Three complete server processes

All three actual processes used identical game source `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`. Each recorded 810 distinct cases, all passing, including all 140 ordinary project IDs. Arcforge `creosote_moves` failed **0/3**; no cases were skipped in these three runs and no Arcforge code was changed.

| Process receipt | Cases passed | compileJava outcome |
| --- | ---: | --- |
| m1-12-final-full-1 | 810/810 | Normal source-phase build |
| m1-12-final-full-2-cached | 810/810 | FROM-CACHE |
| m1-12-final-full-3-cached | 810/810 | UP-TO-DATE |

A separate fresh-checkout metadata-bootstrap attempt failed before any server cases and is excluded from the denominator. Two later runs reused only three hash-verified Minecraft/NeoForge dependency jars; an executed wrong-hash control rejected a changed receipt. Mod/GameTest compilation tasks remained normal, and each new server JVM executed its cases. Fresh javac execution is not claimed for the cached/up-to-date runs. A first-run wrapper queue-lease collision occurred after successful saved Gradle/XML output; bookkeeping was reconciled from unchanged source and saved results without rerunning Minecraft.

Aggregate statistics from those actual processes:

| Run | Tier | Passing / sampled | Observed | Target | Absolute difference |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | T1 | 7696 / 12800 | 0.60125 | 0.6 | 0.001250000 |
| 1 | T2 | 10231 / 12800 | 0.799296875 | 0.8 | 0.000703125 |
| 2 | T1 | 7586 / 12800 | 0.59265625 | 0.6 | 0.007343750 |
| 2 | T2 | 10195 / 12800 | 0.796484375 | 0.8 | 0.003515625 |
| 3 | T1 | 7682 / 12800 | 0.60015625 | 0.6 | 0.000156250 |
| 3 | T2 | 10169 / 12800 | 0.794453125 | 0.8 | 0.005546875 |

## Generated reference and owner handoff

[The generated reference](../m1-slice.md) contains javap-derived signatures for 181 compiled production types, all 15 config definitions and all 30 source recipe resources (28 production and two fixture). The historical ordinary M0 generator output remains byte-identical. Two generations across cultures match, missing output/compiled API checks fail without writing, an actual stale machine input fails Check, source bytes are restored exactly, and fresh Check passes without mutating the document. Generated reference SHA-256: `851c692ad00ef12da1c349ced00080be42e7e4eefca944a1f1785927d1a72fcf`.

[The owner checklist](../manual-walkthrough-m1.md) covers supplies, both quartz routes, physical machine transfers, the 2-wide/2-tall/3-long cell, T1/T2, FE, top-ups, Abort, save/resume, formation, model/tooltips and data-pack override/restoration. It is a source-derived manual for owner execution, not evidence of rendered-client observations. Only launch/singleplayer entry is owner-confirmed.

The pathname audit distinguishes historical report paths, copyright attribution and intentional source-audit detectors; no bulk deletion or rewrite of historical evidence occurred. No new higher-tier feature, remote push, original mk4 edit or daemon activation occurred.

## Authorship, evidence and remaining gates

Nemotron authored implementation and documentation drafts. Approved free GLM completed bounded fallback drafts after provider failures; free DeepSeek completed the manual draft after the later manual transport failures. The coordinator preserved incomplete drafts and compiler failures, repaired actual APIs, test isolation and generators, and reconciled report/manual facts. Worker completion claims were not accepted as test evidence. Beam's chain/statistics/boundary/reload-debug/fixture reviews are focused reviews, not the two pending final whole-task approvals.

Final source-phase unit/core/jar evidence is 517 units across 76 classes, no failures/errors/skips, plus all 140 ordinary project server IDs and the separate shipping probe. The mechanically generated appendix below supplies the exact class rows and new ordinary IDs. Full raw XML/logs, worker completions, focused review receipts and verified bundles remain with the local coordinator.

Next gates are two fresh independent whole-task reviews, separate identical-tree candidate verification (fresh mod/test compilation without build-cache reuse, full unit/core/jar/server checks, and generated-reference Check), integration and verified backups. The complete owner client walkthrough remains pending independently of these automated gates. No complete rendered-client or full playable-M1 acceptance is claimed here.

## Coordinator-generated XML verification appendix

Rows below are copied mechanically from completed unit XML. They sum to 517 across 76 classes; all counts are zero for failures, errors and skips. Game source: `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`.

| Unit class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `com.euvcraft.lithmod.core.ContextualRngTest` | 14 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.CoreTest` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.DieRulesTest` | 5 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.DieRulesTest$Validation` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$BuilderPattern` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Clamping` | 12 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$ComfortableCoreOverrides` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$CoreCountOptions` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Defaults` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Equality` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$Immutability` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$ListParsing` | 8 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$RoundTrip` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FabSettingsTest$TierYieldOverrides` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$CapacityTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$ImmutabilityTest` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$InvalidDataTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$PackingTest` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$PublicViewTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.FoupContentsTest$WaferTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.GenerationMatrixTest` | 8 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.ImportCheckerTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.LineLoadoutTest` | 8 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$EnergyPerTick` | 9 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$Parsing` | 7 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineDefinitionTest$Validation` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Counts` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Energy` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$SingleItem` | 5 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$SlotOrder` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Tags` | 5 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MachineRecipeSpecTest$Validation` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MaskDataTest` | 12 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.MaskRulesTest` | 19 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.multiblock.StructureMatcherTest` | 45 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.PackagingPlanTest` | 15 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.PackagingRulesTest` | 19 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.PackagingRunTest` | 25 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.ProcessRunTest` | 10 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.ProcessRunTest$Refusals` | 9 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.ProcessRunTest$Runs` | 9 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.ProcessRunTest$SnapshotOps` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.RunSpecTest$CodecRoundTrip` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.RunSpecTest$Validation` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.StatModelVerificationTest` | 10 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.TierRegistryTest` | 34 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.WaferSlicingTest` | 7 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$Accuracy` | 6 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$CumulativeRoll` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$DefectFactor` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldCalibrationTest$GammaFunctions` | 7 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldMathTest$Clamping` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldMathTest$CoreCountPenalty` | 9 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldMathTest$PassThreshold` | 5 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldMathTest$ResolveEffectiveYield` | 10 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$Judging` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$LayerStep` | 8 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.core.YieldRollEngineTest$Rejections` | 7 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.DieRulesDataLoaderFallbackTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.DieRulesJsonDataTest` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.JsonResourceDataLoaderReloadTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.SupportLineDataLoaderFallbackTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.SupportLineJsonDataTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.TierDataLoaderFallbackTest` | 2 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.data.TierJsonDataTest` | 1 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.FabConfigTest` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.FabRandomServiceTest` | 8 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.FabYieldServiceTest` | 11 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.FoupContentsCodecTest` | 13 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.ItemAssetResourceTest` | 3 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.M1BoundaryResourceTest` | 4 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.machine.mask.MaskDataCodecTest` | 10 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.PresentationResourceTest` | 4 | 0 | 0 | 0 |
| **Total (76 classes)** | **517** | **0** | **0** | **0** |

### Newly registered ordinary M1-12 server cases

- `lithmod:m1_gate_privacy_wire`
- `lithmod:m1_gate_privacy_tooltip_logs`
- `lithmod:m1_gate_all_hand_fed`
- `lithmod:m1_gate_debug_energy`
- `lithmod:m1_gate_logic_t1_chain`
- `lithmod:m1_gate_memory_t2_chain`
- `lithmod:m1_gate_yield_statistics`
- `lithmod:m1_gate_twenty_machine_lots`
- `lithmod:m1_gate_machine_definition_reload`

The separate fixtures-disabled run contains only `lithmod:m1_gate_shipping_no_fixtures`; it is excluded from the 140 ordinary project IDs and the 810-case full-suite counts. Three actual ordinary full-suite processes each passed 810/810, including every project ID. Arcforge creosote failed 0/3. The failed pre-server metadata bootstrap attempt is preserved and is not included in this denominator.

The second full run used Gradle compileJava FROM-CACHE and the third UP-TO-DATE. Their dependency jars were hash-verified; the server processes and all reported server cases actually executed. No fresh-javac claim is made for those repeated runs.

Manual execution in a rendered client remains pending; only initial singleplayer launch is owner-confirmed. Final whole-task reviews, candidate verification and integration are recorded separately after they occur.

## Final acceptance

Accepted and integrated after the exact-source whole-task reviews and separate candidate verification recorded in `M1-12_review.md` and `M1-12_acceptance.md`. Earlier pending statements describe authoring time. Owner manual execution remains pending.
