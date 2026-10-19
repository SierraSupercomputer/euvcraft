# M1 Progression: From Quartz to Chips

This guide covers the owner/player progression path defined in M1-11. It describes the single-block machines, the Process Cell multiblock, and the flow from raw quartz to finished chips. Editable placeholder ingredients and timings are generated in [docs/recipes.md](recipes.md) — this guide does not duplicate the full recipe table.

## 1. Quartz → Silicon

**Ingredient:** `minecraft:quartz` (not sand — sand smelts to glass and is rejected by the silicon recipes).  
**Two parallel recipes**, both produce `lithmod:silicon`:
- `lithmod:silicon_from_smelting` — furnace, 200 ticks
- `lithmod:silicon_from_blasting` — blast furnace, 100 ticks

Both are data-driven and configurable via datapack. The user explicitly chose **both smelting and blasting**; older pack wording about sand is superseded.

## 2. Crystal Puller — Silicon → Boule

**Machine:** `lithmod:crystal_puller` (single block, hand-fed).  
**Recipe:** `lithmod:crystal_puller` — 4 × silicon → 1 × `lithmod:silicon_boule`, 400 ticks.  
**Energy:** configured per-tick cost and buffer in `data/lithmod/machines/crystal_puller.json`.  
**Behavior:** full-or-zero FE draw; run pauses if energy is insufficient; input rechecked each tick; removing the boule mid-run drops the run with no refund.

## 3. Wafer Slicer — Boule → FOUP of Wafers

**Machine:** `lithmod:wafer_slicer` (single block, hand-fed).  
**Input:** 1 × `silicon_boule` + 1 empty `lithmod:foup_mk1`.  
**Output:** 1 FOUP of the same item type holding `wafersPerBoule` wafers (config value, default 25), at layer 0, fresh lot ID.  
**Capacity rule:** a FOUP smaller than the yield fills to its own capacity; a larger FOUP comes back partially filled. Both inputs consumed only when the product appears.  
**Energy:** same full-or-zero FE model as Crystal Puller.  
**Run spec:** frozen at layer 1 by `FabYieldService.applyLayerStep` for the target die type.

## 4. Pattern Generator — Blank Mask → Patterned Mask

**Machine:** `lithmod:pattern_generator` (single block).  
**Three pattern designs** (data-driven, `data/lithmod/machine_recipes/`):
| Recipe ID | Die Type | Tier | Logic Ingredient |
|---|---|---|---|
| `lithmod:pattern_logic_t1` | logic | T1 | 1 × copper_ingot |
| `lithmod:pattern_logic_t2` | logic | T2 | 1 × iron_ingot |
| `lithmod:pattern_memory_t2` | memory | T2 | 1 × redstone |

**Input:** 1 × `lithmod:blank_mask` + logic ingredient.  
**Output:** 1 × `lithmod:mask` with `MaskData` component: `die_type`, `tier_index`, `uses_remaining = configured maskUses` (stamped at completion, not at start).  
**Uses:** configured `maskUses` per design; decremented per wafer in Process Cell.  
**GUI:** 3 slots (mask, ingredient, output); energy-only exposure; pauses on full output or missing energy.

## 5. Process Cell — Multiblock (Formed Structure)

**Structure:** 5 blocks in a formed structure (placed via `lithmod:process_cell_casing`, `controller`, `power_port`).  
**Required components per tier (T1 or T2):**
- Light/Optics: `i_line_lamp` (T1) / `mercury_lamp` (T2)
- Stage: `stepper_stage` (T1) / `manual_aligner` (T2)
- Cleanroom: `basic_air_filter` (T1) / `fine_air_filter` (T2)
- Metrology: `optical_microscope` (T1) / `overlay_scope` (T2)
- Resist: `resist_t1` / `resist_t2` (same tier as the cell)
- Power: `lithmod:process_cell_power_port` (FE input)
- Controller: `lithmod:process_cell_controller` (brain block)

All five component blocks + resist + power port + controller must be present and match tier. The structure forms around the controller.

**Operation:**
- Accepts a FOUP at layer N, processes one layer per `applyLayerStep` call.
- Hidden roll per wafer; yield is server-hidden, not displayed to the player.
- **Abort latch:** removing the FOUP mid-run latches abort — the run stops, completed layers retain, **spent partial costs are not refunded**, same-type/component resist top-ups allowed while active/paused, extraction/swaps locked until FOUP reinsertion or explicit abort.
- FE via power port; full-or-zero draw.

## 6. Test & Package Machine — Judged Lot → Chips/Scrap

**Machine:** `lithmod:test_package` (single block).  
**Input:** judged FOUP (has run spec) + `lithmod:chip_package` + FE.  
**Admission:** immediately consumes the judged lot, returns an empty FOUP of the same item/stored capacity with a new lot ID; paid work begins next tick.  
**Per wafer:** pass → `chipsPerWafer` catalog chips + 1 package consumed; fail → `chipsPerWafer` scrap, no package.  
**Yield:** server-hidden; player sees only chips vs scrap counts.  
**Breaking the machine:** permanently loses all remaining queued wafers; only finished outputs, actual inventory, and the returned empty FOUP drop.

## 7. Creative Tab (M1-11)

Exactly **one creative tab** (`lithmod:main`) with **32 stacks** in requested group order:
- 30 unique production item types
- 2 extra MASK variants (logic T1, logic T2, memory T2 — blank_mask is the base)
- `test_machine` fixture omitted from progression (M1-12 will gate/remove from shipping; **not already gated**)

## 8. What Is NOT in M1

- **Mk2 machines:** no craft, higher progression/core count choices, chip-consuming recipes, EL changes, scrap recycling — all outside M1.
- **T6 core choices:** later user decision; does not enable M1 core play.
- **Debug energy fill command:** `/debug foup fill` is M1-12 future; do not claim present.
- **Hidden roll/yield numbers:** never displayed to player.
- **Complete playable M1 or visual proof:** M1-12 covers end-to-end, client gates, statistics, privacy, hand-fed reload, debug command, shipping fixture gate, generated handoff, manual walkthrough.
- **Coordinate geometry / exact layout:** M1-12 manual will give exact layout; do not invent here.

## 9. References

- Full recipe table (generated): [docs/recipes.md](recipes.md)
- Machine definitions: `data/lithmod/machines/*.json`
- Machine recipes: `data/lithmod/machine_recipes/*.json`
- Crafting recipes: `data/lithmod/recipe/crafting/*.json`
- Tier data: `data/lithmod/tiers/*.json`