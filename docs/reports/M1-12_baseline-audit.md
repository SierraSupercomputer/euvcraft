# M1-12 Baseline Audit

**Date:** 2026-10-18  
**Scope:** Historical audit of M1-00 through M1-11 per REPORT-INDEX.json; not an M1-12 acceptance run.  
**Baseline:** Accepted M1-11 master `b6272bff7e8d7bbec7d08722933ce0efad5cd0a4`; separate candidate `dceae2d09754874b18a72d10d2bda2fffd05959c` passed 513 unit tests (75 classes), 801 distinct server cases, all 131 project IDs. Source-phase had 800 pass + optional `arcforge:creosote_moves` FAILED represented as skipped; candidate 801 all pass. Neither erased/retried for outcome. Final 513 class rows available in M1-11 report. No `src/tools/builddelta` changes; baseline is documentation-only handoff, not a new M1-12 test run.

---

## Report Inventory and Implications

| Report | Status / Implication for M1-12 |
|--------|--------------------------------|
| **M1-00_data-additions-and-docs-repair.md** | Verified. Tier `layer_ticks` added; T1 layers 6→1; Gen 1 config keys (`chipsPerWafer`, `maskUses`, `resistPerLayer`, `layerTicksOverrides`). Script `tools/generate-m0-api.ps1` extracts signatures. `docs/m0-foundation.md` repaired. 250 unit tests + import check + game test server pass. T1 calibration 60.20% vs 60.0% target. |
| **M1-01_die-type-in-runspec.md** + **_r2.md** | Verified. `RunSpec` now carries `dieType` (String, one of `DieTypes.ALL`). Codec field `die_type`. `YieldRollEngine`/`FabYieldService` signatures updated; layer 1 validates via `DieRules.canMake`. 260 unit tests (was 250), 688 game tests pass. |
| **M1-02_machine-framework_r3.md** | Verified. 26.3 registration patterns from spike proved: `DeferredRegister.Blocks.registerBlock` with properties carrying id, `Capabilities.Energy.BLOCK` on mod bus, `SimpleEnergyHandler` with `maxExtract=0`, `BlockEntityType` factory, `IMenuTypeExtension.create`, `ContainerHelper.loadAllItems/saveAllItems`. 7 machine game tests; `yield_run_spec` flaky (pre-existing, ±10pp on 25 wafers ≈31% fail). 360 unit tests. |
| **M1-02c_machine-contents-drop.md** | Verified. `FabMachineBlockEntity implements Container`; `preRemoveSideEffects` calls `super` + `dropExtraContents`. Loot tables + mineable tags for 4 blocks. `MachineContainer` deleted. 6 drop game tests pass. 711 game tests total. Latent menu bug fixed (`machineSlotCount` tallied not assumed). |
| **M1-02d_yield-run-spec-determinism.md** | Verified. Flaky `yield_run_spec` made structural: assertions now check frozen spec yield and roll count exactly. New core test `aLargeRunStaysWithinFourStandardDeviations` (1200 wafers, 4σ computed). 5/5 game test runs clean. No production code changed. |
| **M1-02s_energy-spike.md** | Reference spike. 10 patterns copied into M1-02b. 3 spike game tests pass. `arcforge:creosote_moves` optional failure noted. |
| **M1-03_multiblock-framework_r3.md** + **M1-03c_matcher-fix.md** + **M1-03d_git-proof-and-concerns.md** | Verified. 2×2×3 Process Cell (12 members, 4 facings). Matcher rewritten to vector basis; tests now use independent oracle. 7 multiblock game tests: formation, removal, swap, contention, energy only while formed, hooks, model textures. 6 block/item models using verified vanilla textures (`smooth_stone`/`iron_block`, `stone`/`deepslate`, `lapis_block`/`redstone_block`). `quartz_block` confirmed absent from jar; `quartz_block_bottom` used. 705 game tests, 360 unit tests. Two defects only game tests found: member `FORMED` flag repair on unchanged re-validation; `failureReason` refreshed on every pass. |
| **M1-04_silicon-chain.md** | Verified. Crystal Puller (4 silicon → 1 boule) + Wafer Slicer (boule + empty FOUP → lot). `WaferSlicing.wafersFor` pure core function. 16 silicon game tests. Quartz recipes (user decision: quartz not sand). 409 unit tests, 732 game tests, 62 project IDs. GUI bars not visually verified (missing assets). |
| **M1-05_chips-and-consumables.md** | Verified. 7 items: `logic_chip_t1/t2`, `memory_chip_t2`, `chip_scrap`, `resist_t1/t2`, `chip_package`. `ChipCatalog`/`ResistCatalog`/`ChipTooltipAppenders`. Tooltip: translatable line "Die type: %s, node tier: %s" — no wafer/roll/yield data. 263 unit tests, 691 game tests. Creative tab `lithmod:1_chips_and_consumables` separate (M0-08 test pins main tab at 2). |
| **M1-06_mask-and-pattern-generator.md** + reviews | Verified. `MaskData` record (dieType, tierIndex, usesRemaining), `MaskRules.canPattern` (Gen 1 gate: die rules + `mask_shop` generation matrix). `blank_mask` stackable 64; `mask` stack 1 with `lithmod:mask_data` component (codec + stream codec). Pattern Generator: hand-fed, Gen 1 gate before FE/progress, `usesRemaining` stamped from `maskUses` at completion. 450 unit tests, 751 game tests, 81 project IDs. 41 new unit methods + 19 new server cases all have executed negative controls. Client screens unverified. |
| **M1-07_line-components.md** | Verified. 10 component items (5 kinds × T1/T2) as `LineComponentItem`. `ComponentCatalog`, `LineLoadout` record (tier per kind + mask/resist/maintenance), `LineLoadoutReader`, `ComponentSlot`. `DefaultStatRules` reproduces T1/T2 behaviour. 367 unit tests, 709 game tests. |
| **M1-08_process-run-state-machine.md** | Verified (core only). Pure core `ProcessRun` state machine: `ProcessState`, `ProcessRunEvent`, `ProcessTickResult`, `ProcessEnvironment`, `RefusalReason`, `ProcessRunRequest`. T1/T2 exact tick counts, pause/resume, resist paid once per layer, mask consumed once, snapshot/restore. 386 unit tests. No game tests (core-only). |
| **M1-09_process-cell-controller.md** + acceptance + reviews | Verified. Process Cell controller extends `FabMachineBlockEntity`, implements `EnergyBufferProvider` + `ProcessEnvironment`. 8 input slots (FOUP, mask, resist, 5 components). 44 menu slots, 12 public wire values. `ProcessCellRunPersistence` with `Contract`/`Saved` codec. 16 new game tests covering conservation, abort latch, empty resist, structure pause, menu locks, malformed state, tamper, partial break, hand-fed capabilities. 509 unit tests, 791 server cases (790 pass + 1 optional Arcforge skip), 121 project IDs. 5 backend + 6 menu + 1 combined safety fault groups verified. |
| **M1-10_test-and-package.md** + acceptance + reviews | Verified. Test and Package machine: judges complete lot once, consumes immediately, returns empty FOUP with fresh UUID. `PackagingPlan`/`PackagingRules`/`PackagingRun` pure core. 5 refusal reasons, Gen 1 gate. 4 output chip banks + 2 scrap banks. 509 unit tests, 775 server cases (774 pass + 1 optional Arcforge skip), 105 project IDs. 59 new core methods + 24 server cases all with executed negative controls. |
| **M1-11_recipes-and-presentation.md** + acceptance + review | Verified. 30 recipes (28 production + 2 test fixtures). `tools/generate-m1-recipes.ps1` generates guide. FOUP initialization server hooks (absent component only). GUI bars: solid fill, different empty/fill colors, localized tooltips — **no visual claim**. Creative tab `lithmod:0_main`: 32 stacks, 30 unique production types + 2 mask variants. `test_machine` fixture still registered (31 items + 8 blocks incl. fixture). Block name fixes for 6 blocks. Datapack override probe with real reload. Survival drops verified for Crystal Puller + Process Cell Casing. 513 unit tests, 801 server cases (800 pass + 1 optional Arcforge skip), 131 project IDs. GLM low findings: stale silicon javadoc (sand), placeholder sand texture, "at least 30" comment vs exact 30 assertion. |

---

## Concrete Implementation Risks for M1-12

| Risk | Source | Mitigation |
|------|--------|------------|
| **`arcforge:creosote_moves` intermittent failure** | M1-02s, M1-03d, M1-09, M1-10, M1-11 all report it as optional skip/fail | M1-12 Step 10 requires three `runGameTestServer` runs; record pass/fail each time. Do not modify Arcforge. |
| **GUI bars / client rendering never verified** | M1-04, M1-06, M1-09, M1-10, M1-11 all explicitly state no client visual proof | M1-12 manual walkthrough (Step 9) is owner-run in real client; do not claim render pass. |
| **`test_machine` fixture still ships** | M1-11: 31 items + 8 blocks includes fixture; creative tab has 32 stacks | M1-12 Step 11: gate/remove test-only content so it does not ship; creative tabs must list no test blocks. |
| **Per-wafer roll data must not leak** | M1-05 tooltip design, M1-09 public wire (12 coarse values only), M1-10 menu (7 wire values) | M1-12 Step 4: audit by test — no menu slot, tooltip, log line, or stream codec contains per-wafer roll. Search for `FoupItems.contentsOf` — diagnostics only. |
| **Hand-fed only: no item/fluid capability on any machine side** | M1-02b energy_capability test asserts null for Item/Fluid; M1-09 `hand_fed_capabilities` test | M1-12 Step 5: game test over every registered machine block and Process Cell block asserts no item handler or fluid capability on any side. |
| **Data pack reload / definition override** | M1-11 PresentationGameTests proves actual reload/restoration; older javadoc claim "impossible" is stale | M1-12 Step 6: show machine definitions reload from data pack override (e.g., changed energy per tick). |
| **Debug energy command gating** | Not yet implemented | M1-12 Step 7: command registered only when `features.debugCommands=true` AND permission level 3; fills energy of looked-at machine. |
| **StructureShape correction** | M1-11 acceptance corrected guide: `StructureShape(2,2,3)` = 2 wide, 2 TALL, 3 LONG; 1 controller, 1 or 2 ports | M1-12 walkthrough must describe layer-by-layer layout matching this exact geometry. |
| **Mask use = whole lot** | BRIEF: "one mask use WHOLE lot" | Process Cell consumes one mask use at whole-run finish (M1-09 verified); Test and Package does not consume masks. |
| **T6 cores deferred** | BRIEF: "T6 cores later NOT M1" | Do not implement T6 core logic in M1-12. |
| **Same-type components resist top-ups running/paused** | BRIEF: "same type+components resist topups running/paused" | M1-09 verified: resist top-up uses only same item + components as accepted template. |
| **Abort locks startup until actual remove/reinsert** | BRIEF: "Abort locks startup until actual remove/reinsert, retain completed layers" | M1-09 verified: abort latches until FOUP removal/reinsertion; completed layers retained. |
| **Creative tab: 30 unique + 2 mask variants** | M1-11: exactly one tab `lithmod:0_main` with 32 stacks | M1-12 must verify creative tabs list no test blocks after fixture gating. |

---

## Mapping M1-12 Eleven Steps to Bounded Phases

| M1-12 Step | Description | Depends On | Risk Notes |
|------------|-------------|------------|------------|
| **1** | Read all M1-00..M1-11 reports + TASK.txt + BASELINE.json; run full build + every test; take counts from XML | Historical reading recorded here; new M1-12 implementation/checks pending | Per-class rows must sum to total. Scripted XML parsing required. |
| **2** | End-to-end game test: quartz→silicon→boule→lot→mask→Process Cell (T1 resist, small layerTicks)→Test Package→chips+scrap; assert chip count = `chipsPerWafer` × `FabYieldService.countPassing`; scrap/package match; FOUP empty new lotId; mask -1 use. Repeat T2 memory (12 layers). | M1-04, M1-06, M1-09, M1-10 all verified | Requires a real server level with FE supplied by the test; the debug command is a separate later phase. T1 logic 1 layer, T2 memory 12 layers. |
| **3** | Statistics: 200 lots through `FabYieldService` at T1 and T2 → pass rates within 2pp of 60% and 80%. 10 lots each tier through real machines → counts agree with service. | M1-01, M1-08, M1-09, M1-10 | Sample size and reproducibility controls remain to be implemented and disclosed; do not cherry-pick seeds or retry statistical outcomes. |
| **4** | Hidden-information audit: test that no menu slot, tooltip, log, stream codec contains per-wafer roll. Search `FoupItems.contentsOf` — diagnostics only. | M1-05, M1-09, M1-10 tooltip/wire designs | Must distinguish privileged debug inspection from public channels. |
| **5** | Hand-fed audit: game test over every registered machine block + Process Cell block asserts no item handler or fluid capability on any side. | M1-02b, M1-09 `hand_fed_capabilities` | Covers all `ModMachines` + `ModMultiblocks` registrations. |
| **6** | Core purity: import check passes. Machine definitions reload from data pack override (e.g., changed energy/tick). | M1-11 PresentationGameTests proved reload | `checkCoreImports` task + data pack override test. |
| **7** | Debug command: registered only when `features.debugCommands=true`, requires permission 3, fills energy of looked-at machine. | New | Owner uses this for walkthrough without generators. |
| **8** | Documentation: `docs/m1-slice.md` with script output (extend M1-00 signature script): package layout, public signatures, data file locations/formats, config keys, machines/definitions, recipe summary, updated Open Design Decisions, 26.3 API notes with source report each came from. | M1-00 script, all M1 reports | Script `tools/generate-m0-api.ps1` exists; extend for M1. |
| **9** | `docs/manual-walkthrough-m1.md`: numbered checklist for owner in real client. Build Process Cell layer-by-layer, place machines, feed FE via debug command, run T1 logic + T2 memory lots, check tooltips/menus for no roll info, verify every item/block renders, watch structure form/unform, save/quit mid-run → relaunch resume, override recipe via data pack + `/reload`. Exact commands + pass criteria. | Steps 2, 4, 5, 6, 7, 8 | No display in this session — owner walkthrough only. |
| **10** | Run `runGameTestServer` three times; report how many times `arcforge:creosote_moves` failed. Change nothing in Arcforge. | Known flaky optional test | Record pass/fail per run; do not retry to hide. |
| **11** | Fix small defects; stop and report larger ones. Search committed files for absolute local paths/usernames. Commit all review files in `docs/reports`. Remove/gate test-only content (`test_machine` block, menu, definition, recipes) so it does not ship; keep only where game tests need it. Creative tabs list no test blocks. | M1-11 fixture still present | Shipping gate for `test_machine` is M1-12 responsibility. |

---

## Contradictory Claims Resolved

| Conflict | Resolution |
|----------|------------|
| M1-02b: "26.3 removed `onRemove`, no hook" vs M1-02c: "`preRemoveSideEffects` exists on `BlockEntity`" | M1-02c verified against patched jar: `BlockEntity.preRemoveSideEffects` calls `Containers.dropContents`. M1-02b claim was author's mistake; M1-02c corrected it. |
| M1-03 r1/r2: `quartz_block` texture used | M1-03d verified against server jar: `quartz_block.png` does not exist. Correct textures: `iron_block`, `deepslate`, `redstone_block`, `smooth_stone`, `stone`, `lapis_block`, `quartz_block_bottom`. |
| M1-04: silicon from sand vs quartz | User decision recorded in M1-04: **quartz** (NOT sand) for both furnace/blast silicon, sand→glass. |
| M1-11 progression draft: `ProcessCell` footprint "2×2, three layers tall" vs actual | M1-11 acceptance corrected: `StructureShape(2,2,3)` = 2 wide, 2 **tall**, 3 **long** (12 members). |
| Packaging javadoc: "real datapack reload impossible" vs M1-11 `PresentationGameTests` | M1-11 proves actual reload/restoration; javadoc claim is stale. |

---

## Limitations

- **No commands executed** in this audit. All evidence comes from reading the 28 reports in REPORT-INDEX.json, BASELINE.json, and TASK.txt.
- **No code inspected** beyond what reports quote. Source-phase verification of contradictory claims (e.g., `quartz_block` texture) relies on M1-03d's jar inspection evidence.
- **No test runs claimed**. Counts are transcribed from reports.
- **Three separate `runGameTestServer` runs** (Step 10) and **owner client walkthrough** (Step 9) are future M1-12 work, not done here.
- **`arcforge:creosote_moves` behavior** across three runs is M1-12 Step 10 responsibility.
- **Shipping fixture gate** (Step 11) not yet applied; `test_machine` still registered per M1-11.
- **GLM cosmetic findings** (stale silicon javadoc, placeholder sand texture, "at least 30" comment) recorded but not inflated to source failures per BRIEF.

---

## Conclusion

The M1-00 through M1-11 baseline is **verified and accepted** at master `b6272bff7e8d7bbec7d08722933ce0efad5cd0a4` with candidate `dceae2d09754874b18a72d10d2bda2fffd05959c`. All 28 reports in REPORT-INDEX.json have been read. Later revisions/acceptance override historical pending statements. The eleven M1-12 steps are mapped to bounded phases with concrete risks identified. No new features, balance changes, or art are in scope. The audit output is this document: `docs/reports/M1-12_baseline-audit.md`.

## Source report index

The coordinator appended these 28 exact filenames from the dispatched index; grouped rows above summarize their implications.

- `M1-00_data-additions-and-docs-repair.md`
- `M1-01_die-type-in-runspec.md`
- `M1-01_die-type-in-runspec_r2.md`
- `M1-02_machine-framework_r3.md`
- `M1-02c_machine-contents-drop.md`
- `M1-02d_yield-run-spec-determinism.md`
- `M1-02s_energy-spike.md`
- `M1-03_multiblock-framework_r3.md`
- `M1-03c_matcher-fix.md`
- `M1-03d_git-proof-and-concerns.md`
- `M1-04_silicon-chain.md`
- `M1-05_chips-and-consumables.md`
- `M1-06_mask-and-pattern-generator-conformance_review.md`
- `M1-06_mask-and-pattern-generator-conservation_review.md`
- `M1-06_mask-and-pattern-generator.md`
- `M1-07_line-components.md`
- `M1-08_process-run-state-machine.md`
- `M1-09_acceptance.md`
- `M1-09_process-cell-controller.md`
- `M1-09_review_one.md`
- `M1-09_review_two.md`
- `M1-10_acceptance.md`
- `M1-10_review-conformance.md`
- `M1-10_review-conservation.md`
- `M1-10_test-and-package.md`
- `M1-11_acceptance.md`
- `M1-11_recipes-and-presentation.md`
- `M1-11_review.md`

