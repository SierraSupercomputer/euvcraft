# Controller tooltips and presentation walkthrough

FOUP Cleaner owner walkthrough accepted October 24, 2026: works as intended.

1. Hover each fab multiblock controller in inventory/creative search. The short tooltip names its frame class. Hold Shift for individual dimensions, casing, power and panel/module requirements. Pick And Place should show Assembly and, with Shift, 2×2×2 / Assembly Casing / 1–2 Assembly Power Ports. Check Process Cell, purifier, E-beam writer, deposition/etch, advanced packager, scanner, utility plant and cleanroom controller too. Cleanroom shows a variable hollow cuboid, not a fixed machine size.
2. Search the creative inventory with and without Shift held. Vanilla and mod items should still be searchable. Shift details are interactive hover information; background search indexing deliberately uses the short form.
3. Place all six scanner modules (Exposure, Optics, Stage, Filtration, Metrology, Immersion) and the Diagnostic Panel. Each has distinct opaque block art on all horizontal faces, with the existing casing on top/bottom. Check inventory icons and existing formed scanner placement/calibration.
4. Wear the complete gown and inspect third-person/front view: white hood surrounds a face opening; suit and trousers remain white; boots have blue covers. The opening reveals the central face area with a hood border. Gown contamination behavior is unchanged.
5. Build a 3×3 cleanroom window panel. Shared borders disappear; its perimeter remains framed. Remove a center/edge pane and verify adjoining edges regain borders. Repeat in perpendicular walls and a horizontal ceiling/floor. Windows connect only to cleanroom windows, not vanilla glass. Four-edge joining is supported; this is not a diagonal-corner/47-tile CTM implementation.
6. Check the same windows still seal a cleanroom, and removing/replacing one breaches/repairs the room as before. No new texture dependency or configuration is needed.

Existing windows from an older world may need replacement or adjacent block updates to initialize their new connection flags. New placement computes connections immediately. No world conversion or progression balance changes are included.
