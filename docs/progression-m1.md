# M1 Progression: From Quartz to Chips

This guide covers the owner/player progression path defined in M1-11. It describes the four single-block machines, the Process Cell multiblock, and the flow from raw quartz to finished chips. Editable placeholder ingredients and timings are generated in [docs/recipes.md](recipes.md) — this guide does not duplicate the full recipe table.

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

**Operation:**
- Advances the inserted FOUP through the design’s layers using the configured layer timing.
- Hidden roll per wafer; yield is server-hidden, not displayed to the player.
- **Abort button:** pressing Abort stops the run; inputs unlock, completed layers retain, incomplete work and paid costs are discarded without refund. The cell stays stopped until the FOUP is physically removed and reinserted.
- **Ordinary active FOUP removal is locked** — it does NOT trigger Abort.
- Same-type/component resist top-ups allowed while running/paused; extraction/swap locked until Abort or completion.
- FE via power port; full-or-zero draw.

## 6. Test & Package Machine — Judged Lot → Chips/Scrap

**Machine:** `lithmod:test_package` (single block).  
**Input:** judged FOUP (has run spec) + `lithmod:chip_package` + FE.  
**Admission:** immediately consumes the judged lot, returns an empty FOUP of the same item/stored capacity with a new lot ID; paid work begins next tick.  
**Per wafer:** pass → `chipsPerWafer` catalog chips + `package_per_wafer` packages consumed (default 1); fail → `chipsPerWafer` scrap, **zero packages used**.  
**Yield:** server-hidden; player sees only chips vs scrap counts.  
**Breaking the machine:** permanently loses all remaining queued wafers; only finished outputs, actual inventory, and the returned empty FOUP drop.

## 7. Creative Tab (M1-11)

Exactly **one creative tab** (`lithmod:0_main`) with **32 stacks** in requested group order:
- 30 unique production item types
- 2 extra MASK variants (logic T1, logic T2, memory T2 — `blank_mask` is a different item, not a base variant)
- `test_machine` fixture omitted from progression (M1-12 will gate/remove from shipping; **not already gated**)

## 8. What Is NOT in M1

- FOUP Mk2 has no crafting recipe. Higher progression, chip-consuming recipes, Encoded Logistics changes and scrap recycling remain outside M1.
- **T6 core choices:** later user decision; does not enable M1 core play.
- **Debug energy fill command:** M1-12 future; do not claim present.
- **Hidden roll/yield numbers:** never displayed to player.
- **Complete playable M1 or visual proof:** M1-12 covers end-to-end, client gates, statistics, privacy, hand-fed reload, debug command, shipping fixture gate, generated handoff, manual walkthrough.

## 9. M1-12 End-to-End Gates (Acceptance Summary)

M1-12 completes the M1 pack with end-to-end chain verification, 200-lot statistics, privacy/boundary audits, tier definition reload, debug energy command, fixture shipping gate, generated handoff proof, and **three separate full server process runs**.

### What M1-12 Verified (Actual Observed Counts)

- **Full quartz→silicon→boule→wafers→mask→formed cell→test/package chains:** T1 logic (1 layer) and T2 memory (12 layers) — exact conservation, FE cost, mask one whole lot, new empty FOUP returned.
- **200 lots × 64 wafers = 12,800 wafers per tier** (T1/T2), plus 10 machine chain lots per tier. Phase receipt samples: T1 ~59.8–60.1% (target 60%), T2 ~79.4–80.0% (target 80%).
- **Privacy:** Actual wire/public menu/tooltip/log invariance + bounded source lexical audit (not formal proof). Hand-fed 8 fixture-on-blocks (7 sides) + 12 formed members.
- **Shipping fixture gate:** Explicit `lithmodFixtures` config + `FMLEnvironment.isProduction()` — `test_machine` absent from block/item/BE/menu/capability/screen in production/dev. Release JAR excludes ONLY 8 fixture JSONs (not processResources); three shared tags fixture optional; conditional fixture loot via NeoForge condition. ProductionTestPackage preserved.
- **Definition reload:** Baseline 20, override 27, restore 20 FE each full 400 paid ticks — real datapack priority, full recipe measurement.
- **Debug energy command:** Actual flag / ADMINS3 / redirected source / player look target / formed controller / miss / console / runtime off.
- **Generator handoff:** Ordinary M0 byte-identical; two generations identical; French culture identical; deterministic/stale/missing-input controls verified.
- **Three separate server processes:** Each 810 cases, 810 unique, 810 passed; all 140 project IDs present in every run; Arcforge creosote 0/3 failed; cached prebuilt dependency hash guard verified; bootstrap failure preserved.

### What M1-12 Does NOT Claim

- No formal information-flow privacy proof.
- No actual rendered client check or visual pass.
- Historical fixture classes may remain unreachable (not physically stripped).
- Source pathname audit: classification only, no mass history deletion.
- Arcforge creosote skip honest (not comprehensive Arcforge test).
- Generator client manual execution pending.
- T6 core choices: later owner decision; does not enable M1 core play.
- Complete playable M1: M1-12 covers end-to-end, client gates, statistics, privacy, hand-fed reload, debug command, shipping fixture gate, generated handoff, manual walkthrough — but final reviews, candidate check, and master merge are PENDING.

### Manual Walkthrough

Owner checklist written; execution pending. Owner confirmed singleplayer launch only.

## 10. References

- Full recipe table (generated): [docs/recipes.md](recipes.md)
- Machine definitions: `data/lithmod/machines/*.json`
- Machine recipes: `data/lithmod/machine_recipes/*.json`
- Crafting recipes: `data/lithmod/recipe/crafting/*.json`
- Tier data: `data/lithmod/tiers/*.json`
- M1-12 Gate Verification and Handoff Report: `docs/reports/M1-12_m1-gate-and-handoff.md`