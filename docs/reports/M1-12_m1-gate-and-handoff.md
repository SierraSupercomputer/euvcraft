# M1-12 Gate Verification and Handoff Report

**Source head:** `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33` (game source)
**Report head:** `c35f972f043a67b9ed4fdc86aea9d5a16a6aaf41` (this document)
**Base:** `b6272bff7e8d7bbec7d08722933ce0efad5cd0a4`
**Date:** 2026-10-19

---

## Scope

This report covers the complete M1-12 pack: end-to-end chain verification, 200-lot statistics, privacy and boundary audits, tier definition reload, debug energy command, fixture shipping gate, generated handoff proof, and three separate full server process runs. M1-12 does not add new higher-tier features; T6 core-count decisions remain a later owner decision per EUVCRAFT_TAKEOVER.md.

---

## Phase Execution Order and Source Precedence

Each phase below is an actual, independently reviewed step. Owner Quartz override authority is confirmed per EUVCRAFT_TAKEOVER.md M1 source precedence; T6 core-count decision is deferred. All counts are from phase receipts, not guessed targets.

### Phase 1: Baseline Audit (M1-12-baseline-audit)
- **Head:** `a51df79896ccbd7764a4da0d5b6ac912044f2851`
- **Scope:** 28 actual reports audited; later review prevails where contradictory.
- **Result:** Cataloged all prior M1 reports, FACTS lineage, and unit-row baselines. No code changes.

### Phase 2: Chain Verified (m1-12-chain-verified.json)
- **Head:** `a51df79896ccbd7764a4da0d5b6ac912044f2851`
- **Scope:** M1-12 step 2 only; quartz smelt AND blast → actual puller/slicer/JSON mask/formed cell/TestPackage chains T1 logic / T2 memory (12 layers).
- **Runtime:** 2 case records, 2 unique names, 2 passed (`lithmod:m1_gate_logic_t1_chain`, `lithmod:m1_gate_memory_t2_chain`).
- **Conservation:** Exact conservation / FE cost / mask one whole lot / new empty FOUP verified.
- **Review:** Beam (opencode/beam-free) — approve. Static review of chain tests and registration found no medium+ defects. Furnace-fuel concern invalid under `AbstractFurnaceBlockEntity.consumeFuel`.
- **Status:** Compile OK; full regression and final three separate runs pending.

### Phase 3: Statistics Verified (m1-12-statistics-verified.json)
- **Head:** `d3eaf2bfd14cea74d742c38a967df899aa4308d9`
- **Scope:** M1-12 step 3 only; 200 lots × 64 wafers = 12,800 wafers per tier; plus 10 machine chain lots per tier.
- **Observed samples (phase receipt):**
  - T1: 7,649 passing / 12,800 total = 0.597578125 (target 0.6, diff 0.002421875)
  - T2: 10,239 passing / 12,800 total = 0.799921875 (target 0.8, diff 0.000078125)
- **Machine lots:** 20 (10 per tier, full chains)
- **Units:** 513 tests / 75 classes
- **Review:** Beam — approve. Implementation uses real `FabYieldService` calls, aggregate-only logging, config try/finally restoration, reset machines between 20 full chains, unique lot identities.
- **Status:** Full regression, three separate runs, manual walkthrough pending. Owner singleplayer entry confirmed.

### Phase 4/5: Boundaries Verified (m1-12-boundaries-verified.json)
- **Head:** `fe02005c2b2f8e46a830f731b99c67b84c59bd80`
- **Scope:** Steps 4/5 plus test-fixture teardown fix; privacy actual wire/public menu/tooltip invariance and routine capture plus bounded source lexical audit (not formal flow proof); raw access diagnostic-only.
- **Hand-fed:** Actual 8 fixture-on-blocks (7 sides) + 12 formed members.
- **Shipping removes fixture:** Leaves 7 production blocks (source inventory). Off-probe checks fixture absence with several production positive controls — NOT an additional 7-block all-side capability execution.
- **Runtime:** 138 case records, 138 unique names, 138 passed; 138 project cases.
- **Units:** 517 tests / 76 classes.
- **Prior failure retained:** m1-12-boundaries-live 137/138; existing drop assertion saw 20-lot fixture leftovers.
- **Production drop assertions unchanged.** Coordinator repairs disclosed.
- **Review:** Beam — approve. Independent read-only review of boundary-phase diff, dependencies, and supplied evidence found no blocking security, privacy, or test-meaningfulness issues within focused scope.

### Phase 6/7: Reload + Debug Verified (m1-12-reload-debug-verified.json)
- **Head:** `fb540218dd620aa578a632145d58f7a893302758`
- **Scope:** Steps 6/7 and logger isolation.
- **Runtime:** 140 case records, 140 unique names, 140 passed; 140 project cases.
- **Units:** 517 tests / 76 classes.
- **Definition reload:** Baseline 20, override 27, restore 20 FE each full 400 paid ticks — actual datapack priority and full recipe measurement.
- **Debug energy command:** Actual flag / ADMINS3 / redirected source / player look target / formed controller / miss / console / runtime off. Dispatch checks real permissions/config/runtime gates and ray targets.
- **Logger:** Configuration and appenders restored.
- **Review:** Beam — approve. No genuine defects in supplied source, dependencies, or evidence.

### Phase 8: Fixtures Verified (m1-12-fixtures-verified.json)
- **Head:** `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33` (game source head)
- **Scope:** Shipping fixture gate only.
- **Units:** 517 tests / 76 classes.
- **All server cases:** 810.
- **Project cases:** 140.
- **Shipping-off probe:** 1 case (`lithmod:m1_gate_shipping_no_fixtures`).
- **Release JAR:**
  - Mode: release
  - JAR: `lithmod-1.0.0.jar`
  - SHA-256: `b4dddc8072951348cbe3f9b8a078e84be56d78fb7c304fe8fab5b5f9d4050956`
  - Entries: 410
  - Missing production entries: 0
  - Fixture entries: 0 (excluded ONLY from JAR, not processResources)
  - Unexpected fixture entries: 0
  - Missing production harvest tags: 0
  - Required fixture tag reference: false
  - Passed: true
  - Baseline is not release acceptance: false
- **Three shared tags:** Fixture optional.
- **Conditional fixture loot:** Using installed NeoForge registered condition.
- **ProductionTestPackage preserved.**
- **Historical fixture classes may remain unreachable;** do not claim physically stripped class files.
- **Source pathname audit:** Classified copyright + intentional path detectors + historical reports; no mass delete history.
- **First full wrapper queue-event collision after saved positive Gradle result** reconciled from actual XML without Minecraft rerun — disclosed briefly.
- **Preserved failed off run:** m1-12-shipping-off. **Preserved failed compiler run:** m1-12-fixtures-unit.
- **Actual failed writer API drafts / compiler errors NOT accepted as test passes.** External completion not evidence.
- **Review:** Beam — approve. Fixture registration gated by explicit `lithmodFixtures` property AND `FMLEnvironment.isProduction()`. Production and ordinary dev runs leave `test_machine` absent from block/item/BE/menu/capability/screen registries.

### Phase 9: Three Full Server Runs (m1-12-three-full-runs.json)
- **Source head:** `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`
- **Three separate, complete `runGameTestServer` processes executed.** Each run's game-test XML checked: every registered `lithmod:*` case ID appears, all passed, in every run.
- **Run 1 (m1-12-final-full-1):** 810 case records, 810 unique names, 810 passed. Arcforge creosote failed: false.
- **Run 2 (m1-12-final-full-2-cached):** 810 case records, 810 unique names, 810 passed. Arcforge creosote failed: false. Compile: FROM-CACHE.
- **Run 3 (m1-12-final-full-3-cached):** 810 case records, 810 unique names, 810 passed. Arcforge creosote failed: false. Compile: UP-TO-DATE.
- **Full separate server processes:** 3.
- **Arcforge creosote failed runs:** 0.
- **Dependency changes:** false.
- **Preserved bootstrap failure:** m1-12-final-full-2 (failed bootstrap attempt BEFORE any server cases).
- **Cached prebuilt Minecraft NeoForge dependency jars:** Hash guard verified (actual wrong hash control).
- **Verified prebuilt dependency receipt:** `m1-12-local-dependency-cache.json`.
- **Normal mod GameTest compile task / real server runs:** No Arcforge edits.
- **Manual client verification pending.**

### Phase 10: Generator Controls (m1-12-generator-controls.json)
- **Generator head:** `7e06f0ef7dc7c1a3b2f9d1feba6b9325e5b5fca9`
- **SHA-256:** `851c692ad00ef12da1c349ced00080be42e7e4eefca944a1f1785927d1a72fcf`
- **Ordinary M0 byte-identical:** true (M0 SHA-256: `b2807c9d1925705fdfc499ca1f9b7d1b65422a5ca104697ab5f46df38d7f25f6`)
- **Two generations identical:** true.
- **French culture identical:** true.
- **Missing output fails without writing:** true.
- **Missing compiled API fails without writing:** true.
- **Actual changed machine input makes check fail:** true.
- **Source bytes exactly restored:** true.
- **Fresh check passes:** true.
- **Check does not mutate document:** true.
- **Deterministic + stale + missing input controls only if verified FACTS.**
- **Generated handoff proof and preserved ordinary M0 output.**
- **Client manual execution pending.**

### Phase 11: Final Statistics Logs (m1-12-final-statistics-logs.json)
- **Source head:** `be5670fe3a8261b98f20f22e6d29db7d6d7d9f33`
- **Observed per-run samples (from three full runs):**

| Run | Tier | Passing | Total | Observed | Target | Difference |
|-----|------|---------|-------|----------|--------|------------|
| 1 | T1 | 7,696 | 12,800 | 0.60125 | 0.6 | +0.00125 |
| 1 | T2 | 10,231 | 12,800 | 0.799296875 | 0.8 | -0.000703125 |
| 2 | T1 | 7,586 | 12,800 | 0.59265625 | 0.6 | -0.00734375 |
| 2 | T2 | 10,195 | 12,800 | 0.796484375 | 0.8 | -0.003515625 |
| 3 | T1 | 7,682 | 12,800 | 0.60015625 | 0.6 | +0.00015625 |
| 3 | T2 | 10,169 | 12,800 | 0.794453125 | 0.8 | -0.005546875 |

---

## Unit Test Summary (Final Head)

- **Classes:** 76
- **Tests:** 517
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Per-class breakdown:** See `build/M1-12-report/UNIT-ROWS.json` (mechanically appended by coordinator from XML).

---

## Registered Project GameTest IDs (140 total)

Mechanically available from FACTS.json `projectIds`:

1. `lithmod:chip_catalog_lookups`
2. `lithmod:chip_tooltip_lines`
3. `lithmod:item_assets`
4. `lithmod:component_catalog_lookups`
5. `lithmod:component_reader_t1`
6. `lithmod:component_reader_t2`
7. `lithmod:component_slot_validation`
8. `lithmod:trivial`
9. `lithmod:fab_random_salt_round_trip`
10. `lithmod:fab_random_client_side_rejected`
11. `lithmod:foup_stack_round_trip`
12. `lithmod:foup_network_view_is_public_only`
13. `lithmod:foup_encoded_size`
14. `lithmod:foup_creative_tab_entries`
15. `lithmod:foup_debug_commands`
16. `lithmod:foup_trusted_contents`
17. `lithmod:foup_stored_capacity_wins`
18. `lithmod:foup_shulker_box_round_trip`
19. `lithmod:foup_ender_chest_round_trip`
20. `lithmod:foup_hopper_transfer`
21. `lithmod:foup_shift_click_move`
22. `lithmod:m1_gate_privacy_wire`
23. `lithmod:m1_gate_privacy_tooltip_logs`
24. `lithmod:m1_gate_all_hand_fed`
25. `lithmod:m1_gate_debug_energy`
26. `lithmod:m1_gate_logic_t1_chain`
27. `lithmod:m1_gate_memory_t2_chain`
28. `lithmod:m1_gate_yield_statistics`
29. `lithmod:m1_gate_twenty_machine_lots`
30. `lithmod:m1_gate_machine_definition_reload`
31. `lithmod:machine_drops_contents_on_break_in_survival`
32. `lithmod:machine_drops_contents_when_block_replaced`
33. `lithmod:machine_contents_do_not_duplicate`
34. `lithmod:machine_unloading_drops_nothing`
35. `lithmod:machine_extra_inventory_drops_on_break`
36. `lithmod:process_cell_blocks_drop_themselves`
37. `lithmod:machine_energy_capability`
38. `lithmod:machine_rejects_hopper_transfers`
39. `lithmod:machine_runs_a_recipe`
40. `lithmod:machine_pauses_without_energy`
41. `lithmod:machine_save_load_round_trip`
42. `lithmod:machine_recipe_from_data`
43. `lithmod:machine_menu_opens_server_side`
44. `lithmod:machine_foup_slot_rejects_plain_items`
45. `lithmod:pattern_generator_cuts_a_logic_t1_mask`
46. `lithmod:pattern_generator_cuts_a_logic_t2_mask`
47. `lithmod:pattern_generator_cuts_a_memory_t2_mask`
48. `lithmod:pattern_generator_uses_the_config_at_completion`
49. `lithmod:pattern_generator_refuses_a_memory_t1_design`
50. `lithmod:pattern_generator_refuses_a_compute_t3_design`
51. `lithmod:pattern_generator_refuses_a_product_without_a_design`
52. `lithmod:pattern_generator_refuses_a_product_that_is_not_a_mask`
53. `lithmod:pattern_generator_pauses_on_a_full_output_slot`
54. `lithmod:pattern_generator_pauses_without_energy`
55. `lithmod:pattern_generator_cancels_when_an_input_is_withdrawn`
56. `lithmod:pattern_generator_save_load_round_trip`
57. `lithmod:pattern_generator_exposes_energy_only`
58. `lithmod:pattern_generator_block_use_opens_the_menu`
49. `lithmod:pattern_generator_ticker_is_server_side`
50. `lithmod:pattern_generator_menu_layout_and_shift_click`
51. `lithmod:mask_catalog_and_creative_tab`
52. `lithmod:mask_data_component_round_trip`
53. `lithmod:mask_tooltip_lines`
54. `lithmod:multiblock_forms_in_all_four_facings`
55. `lithmod:multiblock_removing_any_of_twelve_unforms_the_rest`
56. `lithmod:multiblock_survives_a_casing_and_port_swap`
57. `lithmod:multiblock_cells_do_not_share_blocks`
58. `lithmod:multiblock_energy_reaches_controller_only_while_formed`
59. `lithmod:multiblock_formation_hooks_fire_per_transition`
59. `lithmod:multiblock_model_textures_all_resolve`
60. `lithmod:test_package_judges_a_complete_logic_t1_lot`
61. `lithmod:test_package_judges_a_complete_logic_t2_lot`
62. `lithmod:test_package_judges_a_complete_memory_t2_lot`
63. `lithmod:test_package_returns_a_fresh_foup_of_the_same_item`
64. `lithmod:test_package_refuses_an_incomplete_lot`
65. `lithmod:test_package_refuses_a_die_with_no_catalog_chip`
66. `lithmod:test_package_refuses_a_lot_without_a_run_spec`
67. `lithmod:test_package_never_judges_a_client_view_lot`
68. `lithmod:test_package_enforces_the_packaging_generation_gate`
69. `lithmod:test_package_pays_nothing_without_energy`
70. `lithmod:test_package_spends_packages_only_on_completion`
71. `lithmod:test_package_pauses_when_its_packages_are_taken`
72. `lithmod:test_package_pauses_on_a_full_chip_output`
73. `lithmod:test_package_pauses_on_a_full_scrap_output`
74. `lithmod:test_package_refuses_plain_chips_behind_named_chips`
75. `lithmod:test_package_keeps_a_second_lot_until_the_first_finishes`
76. `lithmod:test_package_break_drops_no_queued_outputs`
77. `lithmod:test_package_save_load_round_trip_mid_wafer`
78. `lithmod:test_package_keeps_frozen_parameters_for_an_accepted_run`
79. `lithmod:test_package_resumes_when_its_definition_returns`
80. `lithmod:test_package_malformed_queue_nbt_fails_closed`
81. `lithmod:test_package_menu_layout_and_public_values`
82. `lithmod:test_package_exposes_energy_only`
83. `lithmod:test_package_block_use_and_server_ticker`
84. `lithmod:presentation_recipe_registry`
85. `lithmod:presentation_crafting_lookup`
86. `lithmod:presentation_pattern_production`
87. `lithmod:presentation_obtainability`
88. `lithmod:presentation_foup_crafting`
89. `lithmod:presentation_foup_initialization`
90. `lithmod:presentation_creative_order`
91. `lithmod:presentation_datapack_override`
92. `lithmod:presentation_assets_and_harvest`
93. `lithmod:presentation_survival_drops`
94. `lithmod:process_cell_logic_t1_conservation`
95. `lithmod:process_cell_memory_t2_conservation`
96. `lithmod:process_cell_weak_line_refused`
97. `lithmod:process_cell_abort_latch_round_trip`
98. `lithmod:process_cell_empty_resist_restore_and_top_up`
99. `lithmod:process_cell_structure_pause_restore`
100. `lithmod:process_cell_menu_active_locks`
101. `lithmod:process_cell_menu_empty_resist_top_up`
102. `lithmod:process_cell_abort_button_validation`
103. `lithmod:process_cell_menu_public_wire`
104. `lithmod:process_cell_definition_pause_restore`
105. `lithmod:process_cell_malformed_state_stays_stopped`
106. `lithmod:process_cell_active_input_tamper_stops`
107. `lithmod:process_cell_partial_break_conserves_inventory`
108. `lithmod:process_cell_hand_fed_capabilities`
109. `lithmod:process_cell_block_use_opens_menu`
110. `lithmod:wafer_slicer_produces_a_lot`
111. `lithmod:wafer_slicer_lot_takes_layer_one`
112. `lithmod:wafer_slicer_refuses_a_used_foup`
113. `lithmod:wafer_slicer_pauses_without_energy`
114. `lithmod:wafer_slicer_save_load_round_trip`
115. `lithmod:crystal_puller_runs_its_recipe`
116. `lithmod:silicon_machines_expose_no_item_handler`
117. `lithmod:crystal_puller_rechecks_its_inputs`
118. `lithmod:wafer_slicer_rechecks_its_inputs`
119. `lithmod:wafer_slicer_lot_size_follows_the_foup_capacity`
120. `lithmod:wafer_slicer_menu_layout_and_shift_click`
121. `lithmod:wafer_slicer_foup_slot_client_boundary`
122. `lithmod:silicon_energy_follows_machine_definitions`
123. `lithmod:silicon_machines_fail_closed_without_a_definition`
124. `lithmod:crystal_puller_resumes_only_the_same_recipe`
125. `lithmod:silicon_cooking_recipes_load_and_match`
126. `lithmod:yield_layer_step`
127. `lithmod:yield_pass_rate`
128. `lithmod:yield_end_to_end`
129. `lithmod:yield_run_spec`
130. `lithmod:m1_gate_shipping_no_fixtures`

---

## Config Keys (15 total)

All 15 config keys are generated reference; avoid stale independent tables.

---

## Source Recipes (30 total: 28 production + 2 fixture)

All resources are generated reference; avoid stale independent tables.

---

## Authorship and Review Trail

- **Nemotron authored chunks** across multiple phases.
- **GLM fallback completed after provider 504 timeouts** (approved free fallback).
- **Beam focused reviews** (scope vs whole final reviews still pending).
- **Full unit per-class table mechanically appended by coordinator from XML;** no invented row table.
- **Manual owner checklist written but execution pending;** owner confirmed launch only.

---

## Verification Limits and Pending Work

### Actual Verification Limits
1. **Privacy audit:** Bounded source lexical audit plus actual wire/public menu/tooltip/log invariance; NOT a formal information-flow proof.
2. **No actual rendered client check** — client visual pass not claimed.
3. **No three-process repeat for M1-06, M1-09, M1-10, M1-11** — only M1-12 achieved three full separate runs.
4. **Arcforge creosote skip** — honest representation; 0 failed runs across three processes but not a comprehensive Arcforge test.
5. **Historical fixture classes may remain unreachable** — not physically stripped from JAR.
6. **Source pathname audit** — classification only; no mass history deletion.
7. **Generator controls** — deterministic/stale/missing input controls verified; client manual execution pending.

### Pending Owner Walkthrough and Reviews
- **Manual walkthrough:** Pending (owner confirmed singleplayer launch only).
- **Full final reviews (candidate):** Pending.
- **Master integration / merge:** Pending.
- **Final three separate server runs:** COMPLETED (3/3).
- **Manual client verification:** Pending.

---

## References

- Baseline audit: `build/M1-12-baseline-audit/`
- Chain verified: `build/M1-12-chain-verified/`
- Statistics verified: `build/M1-12-statistics/`
- Boundaries verified: `build/M1-12-boundaries/`
- Reload/debug verified: `build/M1-12-reload/`
- Fixtures verified: `build/M1-12-fixtures/`
- Three full runs: `build/M1-12-three-full-runs/`
- Generator controls: `build/M1-12-generator-controls/`
- Final statistics logs: `build/M1-12-final-statistics-logs/`
- FACTS: `build/M1-12-report/FACTS.json`
- UNIT-ROWS: `build/M1-12-report/UNIT-ROWS.json`
- Testing doc: `docs/testing.md`
- Progression doc: `docs/progression-m1.md`