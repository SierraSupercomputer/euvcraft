# T4 Content Seed

This document lists the resources added by the T4 content seed phase (M1 pack completion). The native Process Cell machine, its loaders, ProcessCell guards, preparation/binning systems, and compiler/runtime/generator support remain pending coordinator integration.

## Added Registry Items (10)

### Chips (4)
| Registry ID | Translation Key | Die Type | Node Tier |
|-------------|-----------------|----------|-----------|
| `lithmod:logic_chip_t4` | `item.lithmod.logic_chip_t4` = "T4 Logic Chip" | logic | 4 |
| `lithmod:memory_chip_t4` | `item.lithmod.memory_chip_t4` = "T4 Memory Chip" | memory | 4 |
| `lithmod:compute_chip_t4` | `item.lithmod.compute_chip_t4` = "T4 Compute Chip" | compute | 4 |
| `lithmod:nand_chip_t4` | `item.lithmod.nand_chip_t4` = "T4 NAND Chip" | nand | 4 |

All four chips are registered in `ChipCatalog` with design `(dieType, tierIndex=4)`.

### Resist (1)
| Registry ID | Translation Key | RESIST_TIER |
|-------------|-----------------|-------------|
| `lithmod:resist_t4` | `item.lithmod.resist_t4` = "T4 Photoresist" | 4 |

### Line Components (5)
| Registry ID | Translation Key | Component Kind | Node Tier |
|-------------|-----------------|----------------|-----------|
| `lithmod:arf_excimer_laser` | `item.lithmod.arf_excimer_laser` = "ArF Excimer Laser" | light_source | 4 |
| `lithmod:arf_dry_lens` | `item.lithmod.arf_dry_lens` = "ArF Dry Lens" | optics | 4 |
| `lithmod:nanopositioning_stage` | `item.lithmod.nanopositioning_stage` = "Nanopositioning Stage" | stage | 4 |
| `lithmod:ulpa_filter` | `item.lithmod.ulpa_filter` = "ULPA Filter" | cleanroom | 4 |
| `lithmod:precision_overlay_scope` | `item.lithmod.precision_overlay_scope` = "Precision Overlay Scope" | metrology | 4 |

All line components use `LineComponentItem(kind, tier=4)` and stack to 1.

## Creative Tab Entries

The 10 T4 items are appended to the existing `MAIN` creative tab in this order:
1. `arf_excimer_laser`
2. `arf_dry_lens`
3. `nanopositioning_stage`
4. `ulpa_filter`
5. `precision_overlay_scope`
6. `resist_t4`
7. `logic_chip_t4`
8. `memory_chip_t4`
9. `compute_chip_t4`
10. `nand_chip_t4`

Previous entries (T1–T3) retain their exact order.

## Crafting Recipes (6 files in `data/lithmod/recipe/crafting/`)

All recipes follow the modern 26.3 shaped recipe format (`type: minecraft:crafting_shaped`, `category: equipment|misc`). Each T4 component recipe uses the corresponding T3 component plus `logic_chip_t3` and ordinary vanilla materials — **no T4 item is required to craft the first T4 equipment** (no circular dependency).

| Output | Pattern | Key Ingredients (non-vanilla) |
|--------|---------|-------------------------------|
| `arf_excimer_laser` | `" A ","CLC"," D "` | `krf_excimer_laser` (T3), `logic_chip_t3`, `diamond`, `netherite_ingot` |
| `arf_dry_lens` | `" A ","CLC"," D "` | `duv_projection_lens` (T3), `logic_chip_t3`, `glass`, `diamond` |
| `nanopositioning_stage` | `" A ","CLC"," D "` | `precision_stepper_stage` (T3), `logic_chip_t3`, `iron_ingot`, `redstone` |
| `ulpa_filter` | `" A ","CLC"," D "` | `hepa_filter` (T3), `logic_chip_t3`, `paper`, `redstone` |
| `precision_overlay_scope` | `" A ","CLC"," D "` | `laser_interferometer` (T3), `logic_chip_t3`, `gold_ingot`, `quartz` |
| `resist_t4` (count 16) | `"RGR","BLB","GSG"` | `resist_t3` (T3), `logic_chip_t3`, `redstone`, `glowstone_dust`, `glass_bottle` |

## E-beam Mask Writer Support Recipes (4 files in `data/lithmod/support_recipes/`)

All four T4 designs share the same record schema as existing e-beam recipes. The loader derives the design from the filename (`ebeam_{logic,memory,compute,nand}_t4.json`). No new output data fields are invented.

| File | Duration | Energy/tick | Redstone | Water |
|------|----------|-------------|----------|-------|
| `ebeam_logic_t4.json` | 1600 | 2500 | 48 | 0 |
| `ebeam_memory_t4.json` | 1600 | 2500 | 48 | 0 |
| `ebeam_compute_t4.json` | 1600 | 2500 | 48 | 0 |
| `ebeam_nand_t4.json` | 1600 | 2500 | 48 | 0 |

Input: `blank_mask` ×1. Secondary: `redstone` ×48. Output: `mask` ×1 (carries `MaskData` with the selected design and G2 provenance).

## Native Textures (starter-textures.json)

Extended with:
- **New palette**: `T4` (cyan/teal: `#003D4D` → `#80E4DD` with `glow`)
- **10 new item entries** using existing shapes:
  - `logic_chip_t4` → `chip` shape
  - `memory_chip_t4` → `chip_mem` shape
  - `compute_chip_t4` → `chip` shape
  - `nand_chip_t4` → `chip_mem` shape
  - `arf_excimer_laser` → `lamp` shape
  - `arf_dry_lens` → `lens` shape
  - `nanopositioning_stage` → `stage` shape
  - `ulpa_filter` → `filter` shape
  - `precision_overlay_scope` → `scope` shape
  - `resist_t4` → `bottle` shape
- **Updated `itemModelsToUpdate`** list includes all 10 new IDs (total 42 items).
- No established sprites altered. Generator schema unchanged; `tools/generate-starter-textures.mjs` validation list updated only.

## Translations (en_us.json)

Added:
- `tier.lithmod.t4` = "T4"
- 10 item translations (see registry table above)
- `tooltip.lithmod.foup.preparation` = "Preparation: G%s" (for preparation worker)

Existing translations unchanged.

## Test Coverage

Added `T4ContentResourcesTest.java` (JUnit, pure JVM) validating:
- All 10 T4 items registered in `ModItems` with correct IDs
- All 4 T4 chips present in `ChipCatalog` with correct `(dieType, tier=4)` design
- `RESIST_T4` carries `RESIST_TIER=4` component
- All 5 T4 line components have correct `kind` and `tier=4`
- Crafting recipes use only T3-or-lower ingredients (no circular T4)
- All 4 e-beam support recipes decode with expected schema (duration=1600, ept=2500, redstone=48, water=0)
- E-beam codec round-trips
- Previous T1–T3 registry entries unchanged
- `MaskData` schema works for T4 designs (G2 provenance)
- Sample masks (G1) still include T1/T2

## Pending (Coordinator-Owned)

- Native Process Cell machine for T4 (admission logic, substrate grade, mask generation)
- ProcessCell line guards and preparation/binning workers
- E-beam loader support for T4 designs (design → recipe selection)
- Compiler/runtime/generator integration for T4
- Game-test level acceptance (server integration)

## Verification

Unit tests pass at `T4ContentResourcesTest`. No existing tests weakened. Texture generator `--check` validates 42 item assets. Creative tab entry count increased by 10 (tested by `PresentationResourceTest` if extended).