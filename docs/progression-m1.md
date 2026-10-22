# M1 Progression: From Quartz to Chips

This guide covers the early T1/T2 owner/player progression path, updated for the accepted panel layout. It describes the four single-block machines, the Process Cell multiblock, and the flow from raw quartz to finished chips. Editable placeholder ingredients and timings are generated in [docs/recipes.md](recipes.md) — this guide does not duplicate the full recipe table.

## 1. Quartz → Silicon

**Ingredient:** `minecraft:quartz` (sand smelts to glass and is rejected by silicon recipes).  
**Two parallel recipes**, both produce `lithmod:silicon`:
- `lithmod:silicon_from_smelting` — furnace, 200 ticks
- `lithmod:silicon_from_blasting` — blast furnace, 100 ticks

Both are data-driven and configurable via datapack. The user explicitly chose both smelting and blasting; older pack wording about sand is superseded.

## 2. Crystal Puller — Silicon → Boule

**Machine:** `lithmod:crystal_puller` (single block, hand-fed).  
**Recipe:** `lithmod:crystal_puller` — 4 × silicon → 1 × `lithmod:silicon_boule`, 400 ticks.  
**Energy:** configured per-tick cost and buffer in `data/lithmod/machines/crystal_puller.json`.  
**Behavior:** full-or-zero FE draw; the run pauses if energy is insufficient.

## 3. Wafer Slicer — Boule → FOUP of Wafers

**Machine:** `lithmod:wafer_slicer` (single block, hand-fed).  
**Input:** 1 × `silicon_boule` + 1 empty `lithmod:foup_mk1`.  
**Output:** a fresh layer-0 lot in the same FOUP item type; configured yield defaults to 25 wafers.  
**Capacity rule:** a FOUP smaller than the yield fills to its own capacity; a larger FOUP comes back partially filled. Wafer count follows `min(configured wafersPerBoule, stored FOUP capacity)`. Both inputs consumed only when the product appears.  
**Energy:** same full-or-zero FE model as Crystal Puller, per the actual wafer_slicer definition.

## 4. Pattern Generator — Blank Mask → Patterned Mask

**Machine:** `lithmod:pattern_generator` (single block).  
**Three pattern designs** (data-driven, `data/lithmod/machine_recipes/`):
| Recipe ID | Die Type | Tier | Design Material |
|---|---|---|---|
| `lithmod:pattern_logic_t1` | logic | T1 | 1 × copper_ingot |
| `lithmod:pattern_logic_t2` | logic | T2 | 1 × iron_ingot |
| `lithmod:pattern_memory_t2` | memory | T2 | 1 × redstone |

**Input:** 1 × `lithmod:blank_mask` + design material.  
**Output:** 1 × `lithmod:mask` with `MaskData` component: `die_type`, `tier_index`, `uses_remaining = configured maskUses` (stamped at completion, not at start).  
**Uses:** configured `maskUses` per design; **one use consumed for the entire completed Process Cell lot**, not per wafer.  
**GUI:** 3 slots (mask, ingredient, output); energy-only exposure; pauses on full output or missing energy.

## 5. Process Cell — Formed Multiblock Structure

**Structure:** **two blocks wide, two tall and three long**, **twelve total members** (see M1-09 report for exact orientation).  
**Components** are **five item slots** in the controller, not placed component blocks. Required per tier:

| Slot | T1 | T2 |
|---|---|---|
| Lamp | `mercury_lamp` | `i_line_lamp` |
| Stage | `manual_aligner` | `stepper_stage` |
| Optics | `contact_plate` | `reduction_lens` |
| Cleanroom | `basic_air_filter` | `fine_air_filter` |
| Metrology | `optical_microscope` | `overlay_scope` |

Plus same-tier resist, mask, and FOUP. FE via the formed power port.

### 5.1 Two-Access Design: Equipment Cabinet (Lower) and Operating Panel (Upper)

The Process Cell has **two distinct access blocks**, both connected to the **same eight-slot inventory owned by the lower Equipment Cabinet**:

- **Lower: Equipment Cabinet** (formerly called "controller") — sits in the lower front row. Its menu exposes the **five equipment slots** (indices 3–7): light source, optics, stage, cleanroom/filter, metrology.
- **Upper: Operating Panel** — sits directly above the Equipment Cabinet. Its menu exposes the **three consumable slots** (indices 0–2): FOUP, mask, resist, **plus the Abort button**. The Operating Panel has its own block, block entity, and menu registration but **owns no inventory, energy, or run state**; it is a view onto the lower cabinet's inventory and run.

Both views show shared coarse status, FE, and progress. Only the upper view has the Abort control.

### 5.2 Slot Ownership and Locking

- All eight slots belong to the **Equipment Cabinet's inventory**. The Operating Panel menu is a read/write view onto slots 0–2 of that same inventory.
- When inputs are locked (run active, paused, or pending), **both menus lock pickup and placement** on their visible slots.
- **Resist top-up**: while running or paused, the player may add more of the **exact same resist item** (same item and components as the accepted contract) via the Operating Panel resist slot. Swaps to a different resist or removal of the accepted resist are locked until Abort or completion.
- **FOUP and mask**: locked while inputs are locked; ordinary active FOUP removal does NOT trigger Abort — it is simply prevented.
- **Equipment components** (slots 3–7): locked while inputs are locked; same-type/component top-ups are not a concept here — the five components are fixed at admission.

### 5.3 Operation

- Advances the inserted FOUP through the design's layers using the configured layer timing.
- Hidden roll per wafer; yield is server-hidden, not displayed to the player.
- **Abort button (upper Operating Panel only):** pressing Abort stops the run; inputs unlock, completed layers retain, incomplete work and paid costs are discarded without refund. The cell stays stopped until the FOUP is physically removed and reinserted. The lower Equipment Cabinet menu has **no Abort button**.
- **Ordinary active FOUP removal is locked** — it does NOT trigger Abort.
- Same-type/component resist top-ups allowed while running/paused; extraction/swap locked until Abort or completion.
- FE via power port; full-or-zero draw.

### 5.4 Upgrade Instructions (Existing Worlds)

- **Existing lower-row cabinets:** manually replace the casing directly above the cabinet with the new `process_cell_operating_panel`. The cabinet keeps its saved contents, FE, and run state.
- **Old top-row controllers:** require rebuilding the cell with the cabinet in the lower row and the panel directly above it. No automatic world conversion is performed; saved contents, FE, and run remain with the old cabinet block entity.

## 6. Test & Package Machine — Judged Lot → Chips/Scrap

**Machine:** `lithmod:test_package` (single block).  
**Input:** judged FOUP (has run spec) + `lithmod:chip_package` + FE.  
**Admission:** immediately consumes the judged lot, returns an empty FOUP of the same item/stored capacity with a new lot ID; paid work begins next tick.  
**Per wafer:** pass → `chipsPerWafer` catalog chips + `package_per_wafer` packages consumed (default 1); fail → `chipsPerWafer` scrap, **zero packages used**.  
**Yield:** server-hidden; player sees only chips vs scrap counts.  
**Breaking the machine:** permanently loses all remaining queued wafers; only finished outputs, actual inventory, and the returned empty FOUP drop.

## 7. Creative Tab (M1-11)

There is one EUVCRAFT creative tab, registered as lithmod:0_main. Its current source includes later T3–T5 machines, materials, assembly products and mask variants; the historical32-stack M1 census is no longer the full tab. The authoritative ordering is ModCreativeTabs.MAIN displayItems, checked against CreativeCatalog by the presentation tests. Fixture test_machine is excluded from normal production play. Do not derive a current total by adding historical item groups or confusing unique item types with mask variants.

## 8. Later generations and deferred features

This guide's small Process Cell covers early progression; it is not a description of every current machine. T3 adds pure substrates and the E-beam Mask Writer. T4 adds G2 preparation/packaging, the physical-module scanner and beam alignment. T5 adds ultrapure-water utilities, immersion and diagnostic fitting. Pick-and-place assembles existing chip families using universal blanks (CPU1compute, DIMM4memory, MCU1logic) at their available tiers.

- [T3 walkthrough](manual-walkthrough-t3.md)
- [T4 walkthrough](manual-walkthrough-t4.md)
- [Scanner walkthrough](manual-walkthrough-scanner.md)
- [T5 utilities, diagnostics and assembly](manual-walkthrough-t5.md)

T6+ selectable compute cores, reflow, cleanrooms, FOUP cleaning and scrap recycling are future plans, not implemented by this guide. FOUP Mk2 remains available for development/creative use without a crafting recipe. Debug energy fill is a config-gated development aid, disabled by default. Private wafer rolls remain server-only; do not interpret the guide as exposing those values.

## 9. References

- Full recipe table (generated): [docs/recipes.md](recipes.md)
- Machine definitions: `data/lithmod/machines/*.json`
- Machine recipes: `data/lithmod/machine_recipes/*.json`
- Crafting recipes: `data/lithmod/recipe/crafting/*.json`
- Tier data: `data/lithmod/tiers/*.json`

## Verification history

Historical M1-12 acceptance and test counts belong to [the original acceptance report](reports/M1-12_acceptance.md); they are not current full-suite or creative-inventory counts. Later reports and the walkthroughs linked above document subsequent increments. The owner confirmed the functional T5 walkthrough on October22; diagnostic/utility text clipping and a shutdown hang are separate follow-up issues. This documentation update does not claim rendered fixes or new executed tests.
