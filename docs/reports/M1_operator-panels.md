# Required Operating Panel and joined textures

Source execution: `34eb4d385b68d708e933d6de171cd952a56e9d83`. Whole-feature final reviews, independent candidate verification and integration are pending at report freeze. The subsequent acceptance report records the final outcome. Actual rendered-client verification remains owner-pending.

The required Operating Panel sits directly above a lower-row Equipment Cabinet inside the existing2×2×3 cell. One cabinet, one panel, one or two ports and nine or eight casings fill twelve positions. Existing controller IDs and saved inventory/run/FE remain at the lower cabinet; no automatic conversion. Existing lower-row cells need manual casing replacement; old top-row cabinets need rebuilding.

The lower menu exposes backing slots3–7: light, optics, stage, filter/cleanroom, metrology. The upper menu exposes backing0–2: FOUP, mask, resist, plus Abort. Both expose only existing coarse public state. The panel has its own block/BE/menu registration but owns no inventory, energy or run. Removal unforms safely; old menu instances reject removed/replaced panel/core identities. Existing lock/top-up/Abort semantics and no-automation policy remain.

All four native block types share the exact registered formed/part/facing Property instances. Formation publishes ordered part0–11 and facing; stale/removed/foreign owners cannot update another machine, and detach resets visual state. Native finite models cover all six faces and four facings. Fixed joined steel/teal panels retain outer borders and cabinet/screen/port markings. This is the approved fixed-cell scope.

## Editable art

The generator computes and validates both output sets before writing. The manifest palette and pixel operations remain editable; destinations are fixed in code. Both CLI Check modes are nonwriting. There are73new opaque16×16blockPNGs,110managedPNGs total, and138managedJSON outputs. The original37PNGs remain unchanged. The new block item inherits the unformed block model. Native FaceBakery UV flips are independently checked; see docs/textures.md for the exact mapping and preview. The assembled PNG preview is not an in-game render.

## Verified execution

-579unit tests in80classes: zero failures/errors/skips; core-import and release-jar checks pass.
-834distinct complete server cases:834passes, zero required failures or optional skips. All164project IDs pass; all146prior IDs retained.
-Source run used --no-build-cache, offline dependency guard and only three hash-verified Minecraft/NeoForge jars. No compiled mod/test classes were reused for the independent controls or later candidate.
-Generated M1 reference updated from fresh compiled signatures; recipe census31total/29production/2fixture. Byte-identical Check passes; recipe guide Check sees31JSON recipes.

New actual project server IDs:
- `lithmod:operator_panel_visual_parts`
- `lithmod:operator_panel_visual_reset`
- `lithmod:operator_panel_visual_reload_owner`
- `lithmod:operator_panel_horizontal_placement`
- `lithmod:operator_panel_block_use`
- `lithmod:operator_panel_menu_open`
- `lithmod:operator_panel_slot_mapping`
- `lithmod:operator_panel_equipment_retains`
- `lithmod:operator_panel_no_hidden_access`
- `lithmod:operator_panel_stage_no_bypass`
- `lithmod:operator_panel_player_slots_usable`
- `lithmod:operator_panel_no_lower_abort`
- `lithmod:operator_panel_upper_abort_valid`
- `lithmod:operator_panel_invalid_after_removal`
- `lithmod:operator_panel_adjacent_controller`
- `lithmod:operator_panel_missing_reforms`
- `lithmod:operator_panel_save_reload`
- `lithmod:operator_panel_upper_no_inventory`

## Actual fault and generator controls

- `lower-abort-role`: unchanged `lithmod:operator_panel_no_lower_abort` failed as intended; production source restored exactly. Fault `49d0d70cece52c9e9535de955120795bd050af04`, restore `8b15c86a615b67d3e9b790d0dd205bd41201ac51`.
- `stale-upper-panel`: unchanged `lithmod:operator_panel_invalid_after_removal` failed as intended; production source restored exactly. Fault `ae2c9cd29daadcb611e53c4e179a080351660266`, restore `f8c2fbf33b769b3b41547fd2a8abac0f15b362a7`.
- `wrong-upper-role`: unchanged `lithmod:operator_panel_slot_mapping` failed as intended; production source restored exactly. Fault `ecc5ddeba1a93ac42cf4868dc9d1bc593a118cad`, restore `65cac44f839fa16fbde6180d23896ff18407e2bb`.
- `constant-visual-part`: unchanged `lithmod:operator_panel_visual_parts` failed as intended; production source restored exactly. Fault `ddb7a1fb0598073fbdb7d73034b6ccaa28653dd4`, restore `063e7f1fb2f3ff73114a9f4853f64519c37f1e90`.
- `stale-member-owner`: unchanged `lithmod:operator_panel_save_reload` failed as intended; production source restored exactly. Fault `a492180cd8f03e06373b84a389abe628bad5bc4c`, restore `8eb43d7b6adfb3f3119333c4afd9cfd6d9ec4ca6`.
- `vertical-panel-placement`: unchanged `lithmod:operator_panel_horizontal_placement` failed as intended; production source restored exactly. Fault `f8886fc0b8d540a642f3b966a8862f3e6aee610d`, restore `4482123e28655ee6e579428c729a9bb98fa7db7d`.
- Final-source visual-part recheck: `lithmod:operator_panel_visual_parts` failed under constant-part fault `c2272dd427c059c952b5cb0d5c92044b90715ee5`; exact restoration `252834824810e8d8a9d4159cb5460476b72311d9`. This covers the later-strengthened MultiblockGameTests fixture.
- Final-source `required-panel-final`: expected failed assertions {"com.euvcraft.lithmod.core.multiblock.StructureMatcherTest":4,"com.euvcraft.lithmod.core.multiblock.OperatingPanelMatcherTest":4}; all579cases ran with no errors/skips; exact tree restoration and unchanged tests.
- Final-source `native-uv-oracle`: expected failed assertions {"com.euvcraft.lithmod.mc.JoinedPanelResourceTest":2}; all579cases ran with no errors/skips; exact tree restoration and unchanged tests.
- Generator `generate-starter-textures.mjs-check`: passed with no byte/mtime/output writes.
- Generator `generate-joined-panels.mjs-check`: passed with no byte/mtime/output writes.
- Generator `missing-PNG`: passed with no byte/mtime/output writes.
- Generator `stale-PNG`: passed with no byte/mtime/output writes.
- Generator `invalid-manifest-normal-generation`: passed.
- Generator `two-normal-generations`: passed.

Generator sources restored exactly; assembled preview SHA256 `498cb71dba8c6473d6b7f37d5dfcd6f1f23946eb3b379ec8996624d356a7ab51`. Local receipts: `.orchestrator/operator-panels/{backend-controls,unit-controls,visual-final-control-r2,generator-controls}.json` in the coordinator workspace.

## Preserved development outcomes and limits

Nemotron authored scoped implementation/test/art/documentation drafts. Free Beam focused review found stale ownership; coordinator repairs addressed native API/compiler errors, shared Property instances, menu/test assumptions, stale ownership, complete resource generation and mirrored fixture geometry. The Nemotron report/manual draft is preserved separately; the coordinator corrected invented center-back geometry, eight-slot instructions, BE-registration and failure-history claims against actual source/receipts.

The initial property runtime crash came from distinct registered Property instances despite matching names; shared instances resolved it. The first complete server run then failed eight legacy fixture/count assumptions; the second passed833/834, with one mirrored fixture depth-offset error. The corrected full run passes834/834. Compiler drafts, provider504/timeout attempts and failed output-label dispatch remain preserved. The label collision executed no new server test and is excluded from passing controls. No retry was used to conceal an optional dependency failure; the passing source run had none.

Vanilla mining tags and the generated-reference census were omitted from original immutable worker scope. Separate coordinator tasks own those files; both supplements are part of final whole-feature reviews/candidate acceptance. The original parent scope remains unchanged.

Actual client titles, slot layout/legibility, panel placement and all-face joins need owner inspection. The earlier loading-window workaround remains a separate known open issue. Fresh Creative FOUPs still initialize on a server inventory tick; filled Creative copies remain untrusted. No dependency/world/client/mk4 changes, paid inference/image API, higher-tier expansion, push or daemon activation occurred.
