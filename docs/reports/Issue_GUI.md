# GUI Issue Fix Report (Oct 22)

## Summary
Fixed label clipping and overlap issues in two screen files per scoped task.

## Changes

### DiagnosticScreen.java
- **Amber legend clipping graph top**: Moved "Amber: target; cyan: last plot | x ±2, y ±8" from y=19 to y=22 (3px down), clearing the graph area which starts at y=24.
- **Paper label losing final 'r'**: Moved "Paper" label from x=276 to x=270. The `text()` helper uses `plainSubstrByWidth(text, 304-x)`; at x=276 width=28px (~5 chars), at x=270 width=34px, fully fitting "Paper" (5 chars ≈25px).

### UtilityScreen.java
- **Filter/energy labels overlapping power bar**: Power bar occupies x=12..24 (width 12) at y=18..70 (height 52). Moved both "Filter    Water     Bucket" and energy text from x=18 to x=30, clearing the power bar's right edge (x=24). Reduced line width from 150 to 140 to maintain right margin.
- **Bottom scanner label losing final 'r'**: "Connect pure outlet to scanner inlet" (36 chars ≈180px) exceeded width=160. Shortened to "Connect pure outlet to scanner" (31 chars ≈155px), fitting within 160px while preserving meaning.

## Coordinates Reference
- DiagnosticScreen: `imageWidth=320`, `imageHeight=268`, graph at local (15,24) size 290×80
- UtilityScreen: inherits `energyBarX=12`, `energyBarY=18`, `ENERGY_BAR_WIDTH=12`, `ENERGY_BAR_HEIGHT=52` from FabMachineScreen

## Limitations
- Rendering not verified in-game; coordinate adjustments based on source analysis only.
- Font width assumptions: ~5px/char for default Minecraft font; `plainSubstrByWidth` handles actual truncation.
- No changes to menu slots, widgets, server logic, or graph function.
- Report created at `docs/reports/Issue_GUI.md` per scope.

Coordinator correction: the worker's y=22 legend would still overlap the graph beginning at24, contrary to its report. Restored the legend to19 and moved/resized the graph to32..104 (72px tall), updating axis/mapping consistently. Paper x270 is retained. Utility input labels are individually aligned to actual slots35/80/125 and the plumbing instruction uses two short complete lines; proportional font width is not approximated as5px per character. Rendered verification remains pending.

Further coordinator finding from the owner's saved screenshot: GUI scale4 gives a252px logical viewport at1920×1009, so the old268px panel also clipped its title at the screen edge. Final panel is238px tall (fits vanilla minimum240px logical height). Graph spans30..100; coefficient labels are beside inputs104..122, range is a field tooltip, buttons126..146, Paper label114/slot127, combined score/status148, inventory160..212/hotbar214..230. Menu coordinates move with the inventory/paper wells; counts, costs and button IDs remain unchanged. This replaces the earlier intermediate geometry described above.
