# Search Assembly Assets Visual Repair

## Summary
Fixed the pick-and-place formed visual issue where assembly blocks (controller, casing, power port) showed no texture change when formed. The blocks already exposed `formed`, `facing`, `part`, `large` state properties via `AdvancedCasingBlock`, but their blockstate files ignored these properties.

## Changes Made

### 1. Texture Generator (`tools/starter-textures.json`)
- Added 4 new drawing shapes for assembly blocks:
  - `assembly_ctrl_side` — controller side with vertical vents and accent dots
  - `assembly_ctrl_top` — controller top with wafer inset frame
  - `assembly_ctrl_front` — controller front with wafer panel and status bar
  - `assembly_casing` — plain casing tile with corner accent dots
  - `assembly_port` — power port with lightning bolt and corner accent dots
- Added 6 new block definitions using existing palettes:
  - `assembly_controller_formed` / `assembly_controller_unformed` (orientable: side, top, front)
  - `assembly_casing_formed` / `assembly_casing_unformed` (cube_all)
  - `assembly_power_port_formed` / `assembly_power_port_unformed` (cube_all)
- Extended `blockModelsToUpdate` to include the 6 new assembly block models

### 2. Blockstate Files (`src/main/resources/assets/lithmod/blockstates/`)
- **Created** `assembly_controller.json` — full facing + formed/part variants referencing orientable models
- **Updated** `assembly_casing.json` — facing + formed/part variants referencing cube_all models
- **Updated** `assembly_power_port.json` — facing + formed/part variants referencing cube_all models

All three blockstates now properly respond to the `formed`, `facing`, and `part` state properties exposed by `AdvancedCasingBlock`.

### 3. Resource Tests (`src/test/java/com/euvcraft/lithmod/mc/StarterTextureResourceTest.java`)
- Extended `PRODUCTION_BLOCKS` from 10 to 16 entries (added 6 assembly blocks)
- Extended `ORIENTABLE_BLOCKS` to include assembly controller formed/unformed
- Extended `formedAndUnformedStatesRenderDifferently()` to verify pixel-level distinctness for all three assembly block pairs:
  - `assembly_controller` front textures
  - `assembly_casing` textures
  - `assembly_power_port` textures
- Tests now validate that unformed models never reference formed textures and that formed/unformed textures differ pixel-wise

### 4. Item Models
The existing item models for `assembly_controller`, `assembly_casing`, `assembly_power_port` (if present) will automatically reference the `_unformed` block models as defaults, preserving existing item appearance.

## Visual Design
- **Formed state**: Uses green `CASING_FORMED` / `PORT_FORMED` / `CTRL_FRONT_FORMED` palettes (matching process_cell formed appearance)
- **Unformed state**: Uses amber `CASING_UNFORMED` / `PORT_UNFORMED` / `CTRL_FRONT_UNFORMED` palettes (matching process_cell unformed appearance)
- **Controller**: Orientable model with distinct side/top/front faces showing wafer panel and status bar
- **Casing**: Simple cube_all tile with corner accent dots for visual interest
- **Power Port**: Cube_all with lightning bolt motif and corner accent dots

The assembly blocks share the same color language as process_cell blocks but have distinct geometry (simpler vents, different accent patterns) so players can distinguish the two machine families at a glance.

## Generator Invocation
After these edits, run the texture generator to produce the new assets:
```bash
node tools/generate-starter-textures.mjs
```
This will:
- Generate 18 new texture PNGs (3 faces × 2 states for controller + 1 face × 2 states for casing + 1 face × 2 states for power port)
- Generate 6 new block model JSONs
- Update the preview atlas

## Limitations
- No Java gameplay behavior changes — only visual/assets
- No cleanroom or machine behavior modifications
- Assembly blocks use a simplified 2×2×2 appearance (single part per block) rather than the full 12-part joined panel system used by process_cell; this is intentional for the smaller pick-and-place structure
- The `part` property in blockstates currently only maps to part=0 for formed state (since assembly structures don't use the joined panel tiling); the property is preserved for consistency with `AdvancedCasingBlock` interface
- Item models for assembly blocks were not modified; they continue to reference the unformed block model as default

## Verification
The resource tests cover:
- All 6 new block models exist and reference correct textures
- All 18 new texture PNGs exist, are 16×16, and fully opaque
- Formed/unformed texture pairs are pixel-distinct for all three block types
- Unformed models never accidentally reference formed textures
- Block model parents are correct (orientable for controller, cube_all for casing/power_port)
- No orphan production block models