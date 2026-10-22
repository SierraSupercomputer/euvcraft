# Issue Progression Documentation Update

## Summary

Updated `docs/progression-m1.md` to reflect the current accepted source state (post M1-11, pre M1-12 handoff) against actual framework source. The guide now correctly documents:

### Fixed Items

1. **Upper Operating Panel and Lower Equipment Cabinet** — Renamed "controller" to "Equipment Cabinet" (lower) and added "Operating Panel" (upper) as the required two-access design. The panel sits directly above the cabinet in the lower front row.

2. **Slot Ownership** — Clarified that all eight slots belong to the Equipment Cabinet's inventory. The Operating Panel menu is a view onto slots 0–2 (FOUP, mask, resist) of that same inventory; the Equipment Cabinet menu exposes slots 3–7 (light, optics, stage, cleanroom, metrology). Both views share coarse status/FE/progress.

3. **Upper Abort Only** — The Abort button exists only on the Operating Panel (upper) menu. The Equipment Cabinet (lower) menu has no Abort. Verified by game tests `lithmod:operator_panel_no_lower_abort` and `lithmod:operator_panel_upper_abort_valid`.

4. **Upgrade Instructions** — Documented manual migration for existing worlds:
   - Lower-row cabinets: replace the casing above with the new Operating Panel; saved contents/FE/run remain.
   - Old top-row controllers: require rebuilding with cabinet in lower row and panel above; no automatic conversion.

5. **Creative Tab Census** — Removed historical creative census. Replaced with source-verified count: the single tab `lithmod:0_main` now contains **112 stacks** total (M1 progression + T3 support + T4 advanced/scanner + T5 utility/assembly/scanner), derived directly from `ModCreativeTabs.MAIN` displayItems registration order. The M1 progression subset remains 32 stacks (30 unique production items + 2 extra mask variants).

### Source Verification

All changes verified against actual source:
- `ProcessCellControllerBlockEntity.java` — slot constants, formation logic, abortRun, inputsLocked
- `ProcessCellOperatingPanelBlockEntity.java` — MenuProvider for upper view, no inventory ownership
- `ProcessCellMenu.java` / `OperatingPanelMenu.java` — slot layouts, Abort button gating, lock semantics
- `ModMultiblocks.java` — block/menu registration for both access blocks
- `ModCreativeTabs.java` — 112 `output.accept()` calls counted
- `M1_operator-panels.md` report — verified test IDs and fault controls

### Limitations

- No commands, Git, network, or delegation performed (per task scope).
- No invented execution or tests; source verification only.
- Rendered-client walkthrough remains owner-pending (per M1-12 status).
- Three final whole-task reviews and integration remain pending at authoring time.