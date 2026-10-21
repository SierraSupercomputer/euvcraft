# EUVCRAFT Tier 4 Full Chain Walkthrough

> **Scope**: This document is a concise, player-facing reference grounded in the current source code and JSON data (Minecraft 1.21, NeoForge). It does **not** claim a manual walkthrough passed; the coordinator runs server tests separately. All numbers come from actual data files; assumptions are stated explicitly.

---

## 1. Overview

Tier 4 (ArF dry, 193 nm) introduces a new **Deposition / Etch Chamber** (3 × 3 × 5) and an **Advanced Test and Package** machine (2 × 2 × 3), both built on the shared **Advanced Formation** framework. The chain runs:

1. **Crystal Puller** → ordinary Silicon Boule
2. **Boule Purifier** (support 2×2×3) → Pure Silicon Boule (consumes water buckets)
3. **Wafer Slicer** → FOUP of 3 unprocessed PURE wafers (configurable; default 25)
4. **Deposition / Etch Chamber** → preparation generation 2 (consumes 4 Silicon, 1 200 FE/t × 1 200 ticks = 1.2 M FE/lot)
5. **E-beam Mask Writer** (support 2×2×3) → GEN 2 photomask (1 blank mask + 48 redstone)
6. **Process Cell** (T4 components) → 32 lithography layers (100 ticks/layer base)
7. **Advanced Test and Package** → chips + scrap (normal) or high-binned T4 (logic/compute only)

No T5 outputs exist. High-binning promotes T3 logic/compute → T4 at 15 % yield, 2× time, 2× energy; admission is frozen at queue start.

---

## 2. Multiblock Structures

### 2.1 Deposition / Etch Chamber — **3 wide × 3 tall × 5 deep**

| Property | Value |
|----------|-------|
| Shape ID | `PANEL_3x3x5` (`StructureShape(3,3,5)`) |
| Volume | 45 blocks |
| Controller | **Deposition Etcher Controller** — lower front face, any column (`u0 = 0..2`, `v0 = 0` fixed) |
| Operating Panel | **Advanced Operating Panel** — **mandatory**, exactly one, directly above controller at `(u0, 1, 0)` |
| Power Ports | **Advanced Power Port** — **1 or 2**, freely placed anywhere except controller/panel positions |
| Casing | **Advanced Fab Casing** — all remaining cells |
| Forbidden | Water inlet, extra controller, extra operating panel, any non-structure block |

**Facing**: Horizontal only (NORTH/SOUTH/EAST/WEST). The structure extends **backward** from the controller face (`d = 0..4`). The matcher tries each front column `u0` in order; first full match wins.

**Member order (canonical)**: `u * (height × length) + v * length + d` — u outer, v middle, d inner.

### 2.2 Advanced Test and Package — **2 wide × 2 tall × 3 deep**

| Property | Value |
|----------|-------|
| Shape ID | `PANEL_2x2x3` (`StructureShape(2,2,3)`) |
| Volume | 12 blocks |
| Controller | **Advanced Test Package Controller** — lower front face, either column (`u0 = 0..1`, `v0 = 0`) |
| Operating Panel | **Advanced Operating Panel** — mandatory, exactly one, directly above controller |
| Power Ports | **Advanced Power Port** — 1 or 2, freely placed |
| Casing | **Advanced Fab Casing** — all remaining cells |

Same facing rules; volume 12.

### 2.3 Support Machines (Purifier & E-beam) — **2 × 2 × 3**

Shared `SupportStructureMatcher`: 12 blocks, controller on lower front row (either column), **1 or 2 power ports**, **no operating panel**, rest casing. Purifier additionally allows **Support Water Inlet** (E-beam rejects it).

---

## 3. Slot Layouts (Owner Checks)

### 3.1 Deposition / Etch Chamber (Controller GUI)

| Slot | Index | Role | Accepts |
|------|-------|------|---------|
| Input FOUP | 0 | Unprocessed PURE lot, prep gen 1, layer 0 | FOUP Mk1/2 with matching lot |
| Silicon Reagent | 1 | Consumable | **Silicon** × 4 per lot (from JSON `silicon_per_lot`) |
| Output FOUP | 2 | Prepared lot (prep gen 2) | Extract only |

**Job start conditions** (checked every tick):
- Formed structure
- Loaded definition (`deposition_etcher.json`)
- Energy buffer ≥ 1 000 FE/t (base) × FE multiplier
- Output slot empty
- Input FOUP: PURE substrate, prep gen 1, not started, all wafers at layer 0
- Silicon slot: ≥ 4 Silicon

**Job**: 1 200 ticks (JSON `duration`), 1 000 FE/t (JSON `energy_per_tick`), consumes 4 Silicon, writes output FOUP with `preparationGeneration = 2`. Progress persists across chunk unload; break drops pending FOUP.

### 3.2 E-beam Mask Writer (Support GUI)

| Slot | Index | Role | Accepts |
|------|-------|------|---------|
| Blank Mask | 0 | Input | Blank Mask × 1 |
| Redstone | 1 | Consumable | Redstone × 48 per mask (from recipe `ebeam_logic_t4.json` etc.) |
| Output Mask | 2 | GEN 2 Photomask | Extract only |

**Designs available** (selector indices 7–10 for T4):
- `logic_t4` (tier 4, LOGIC)
- `memory_t4` (tier 4, MEMORY)
- `compute_t4` (tier 4, COMPUTE)
- `nand_t4` (tier 4, NAND)

**Job**: Recipe-defined duration & energy/tick; consumes 1 blank + 48 redstone; produces mask with `MaskData(tierIndex=4, dieType, uses=config.maskUses, shopGeneration=2)`. Mask uses decrement by 1 per full lot in lithography.

### 3.3 Process Cell (T4 Lithography)

**Required components** (slots 0–4):
- ArF Excimer Laser
- ArF Dry Lens
- Nanopositioning Stage
- ULPA Filter
- Precision Overlay Scope

**Input slots**:
- Slot 5: Prepared FOUP (prep gen 2)
- Slot 6: GEN 2 Photomask (matching die type)
- Slot 7: T4 Photoresist × 64

**T4 tier data** (`data/lithmod/tiers/t4.json`):
- `layers: 32`
- `layer_ticks: 100` (overridable via `FabConfig.LAYER_TICKS_OVERRIDES`)
- `default_yield_percent: 92.0`
- `energy_per_tick: 20` (base; no T4 override in `per_tier_energy_per_tick`)

**Run**: 32 layers sequentially; each layer consumes resist, draws FE, advances lot layer. Mask uses −1 per lot. Finished lot carries frozen `RunSpec(tier=4, dieType, cores=1 for compute)`.

### 3.4 Advanced Test and Package (Controller GUI)

| Slot | Index | Role |
|------|-------|------|
| FOUP Input | 0 | Finished lot (any tier ≤ installed generation) |
| Chip Package | 1 | Consumable (1 per passing wafer) |
| Chip Outputs | 2–5 | T4 Logic / Memory / Compute / NAND chips |
| Scrap Outputs | 6–7 | Chip Scrap |

**Installed generation**: 2 (hard-coded). Admits lots with `run.spec.generation ≤ 2`.

**Definition** (`advanced_test_package.json`):
- `ticks_per_wafer: 10`
- `package_per_wafer: 1`
- `energy_per_tick: 100`
- `energy_capacity: 1 000 000`

**Normal packaging** (high binning OFF):
- Judges each wafer once via `FabYieldService` (92 % base yield → binomial per wafer).
- Passing wafer → `chipsPerWafer` chips (config) + 1 package consumed.
- Failing wafer → `chipsPerWafer` scrap.
- Lot consumed atomically at queue start; empty FOUP returned.

**High binning** (toggle via Operating Panel button, only when queue idle):
- **Allowed**: Logic & Compute only (T3 → T4). Memory & NAND refused.
- **Recipe**: `high_binning/logic_t3_t4.json` & `compute_t3_t4.json`
  - `yield_multiplier: 0.15` → effective yield = 92 % × 15 % ≈ **13.8 %**
  - `ticks_multiplier: 2.0` → 20 ticks/wafer
  - `energy_multiplier: 2.0` → 200 FE/t
- **Admission frozen** at queue start: toggling mid-run has no effect on wafers already judged.
- **Output tier**: T4 (target tier from recipe). No T5 output.

---

## 4. Wafer Preparation vs. Lithography

| Stage | Machine | Input Lot | Output Lot | Key Change |
|-------|---------|-----------|------------|------------|
| **Preparation** | Deposition / Etch | PURE, prep gen 1, layer 0, not started | PURE, **prep gen 2**, layer 0, not started | Installs native preparation; lot ID & wafer rolls preserved |
| **Lithography** | Process Cell | PURE, prep gen 2, layer 0, not started | PURE, prep gen 2, **layer 32**, started | 32 exposure steps; mask use −1; run spec frozen |

The test verifies: `prepared.preparationGeneration() == 2`, `completed.lotLayer() == 32`, `completed.preparationGeneration() == 2`.

---

## 5. High-Binning: T3 Logic/Compute → T4 Only

| Source | Target | Yield Mult. | Time Mult. | Energy Mult. | Allowed Families |
|--------|--------|-------------|------------|--------------|------------------|
| Logic T3 | Logic T4 | 0.15 | 2.0 | 2.0 | Logic, Compute |
| Compute T3 | Compute T4 | 0.15 | 2.0 | 2.0 | Logic, Compute |

**Rules enforced in `TestPackageBlockEntity.admissionRefusal` & `HighBinningRules`:**
- Installed generation ≥ 2 (Advanced Test Package is gen 2).
- Source tier 3, target tier 4.
- Die type LOGIC or COMPUTE only.
- Target chip item must exist in catalog.
- High-binning toggle **only works when queue idle** (`toggleHighBinning()` returns false if `isQueueActive()`).

**No T5 outputs** — recipes only define T3→T4 transitions.

---

## 6. Exact Numeric Costs (from JSON)

| Machine / Step | Parameter | Value | Source |
|----------------|-----------|-------|--------|
| Crystal Puller | energy/tick | 20 FE | `crystal_puller.json` |
| Boule Purifier | energy/tick | 80 FE | `boule_purifier.json` |
|  | water capacity | 16 000 mB | `boule_purifier.json` |
|  | recipe: 1 boule + 1000 mB → 1 pure boule | (from support recipe) | `support_recipes/boule_purifier.json` |
| Wafer Slicer | energy/tick | 30 FE | `wafer_slicer.json` |
|  | slice_ticks | 200 | `wafer_slicer.json` |
|  | wafers/boule (config) | 25 default, 3 in test | `FabConfig.WAFERS_PER_BOULE` |
| Deposition / Etch | energy/tick | 1 000 FE | `deposition_etcher.json` |
|  | duration | 1 200 ticks | `deposition_etcher.json` |
|  | silicon_per_lot | 4 | `deposition_etcher.json` |
|  | energy capacity | 1 000 000 FE | `deposition_etcher.json` |
| E-beam Mask Writer | energy/tick | 2 000 FE | `ebeam_mask_writer.json` |
|  | energy capacity | 4 000 000 FE | `ebeam_mask_writer.json` |
|  | redstone per mask (T4) | 48 | recipe `ebeam_logic_t4.json` etc. |
| Process Cell (T4) | energy/tick | 20 FE | `process_cell.json` (no T4 override) |
|  | layers | 32 | `t4.json` |
|  | layer_ticks | 100 | `t4.json` |
|  | default yield | 92 % | `t4.json` |
| Advanced Test Package | energy/tick | 100 FE | `advanced_test_package.json` |
|  | ticks_per_wafer | 10 | `advanced_test_package.json` |
|  | package_per_wafer | 1 | `advanced_test_package.json` |
|  | energy capacity | 1 000 000 FE | `advanced_test_package.json` |
| High Binning (T3→T4) | yield multiplier | 0.15 | `high_binning/logic_t3_t4.json` |
|  | ticks multiplier | 2.0 | same |
|  | energy multiplier | 2.0 | same |

**Assumptions**:
- FE multiplier from config = 1× (default).
- `chipsPerWafer` config = 1 (test uses `EUVCRAFTMod.settings().chipsPerWafer()`; default not in JSON).
- `maskUses` config = 64 (default; test does not override).
- `rollsPerLayer` config = 1 (default; `RunSpec` expects `layers * rollsPerLayer` total rolls).

---

## 7. Editable JSON Paths

| Purpose | Path |
|---------|------|
| T4 tier definition | `data/lithmod/tiers/t4.json` |
| Deposition / Etch machine | `data/lithmod/machines/deposition_etcher.json` |
| Advanced Test Package machine | `data/lithmod/machines/advanced_test_package.json` |
| E-beam Mask Writer machine | `data/lithmod/machines/ebeam_mask_writer.json` |
| Process Cell machine | `data/lithmod/machines/process_cell.json` |
| Boule Purifier machine | `data/lithmod/machines/boule_purifier.json` |
| Wafer Slicer machine | `data/lithmod/machines/wafer_slicer.json` |
| Crystal Puller machine | `data/lithmod/machines/crystal_puller.json` |
| High-binning Logic T3→T4 | `data/lithmod/high_binning/logic_t3_t4.json` |
| High-binning Compute T3→T4 | `data/lithmod/high_binning/compute_t3_t4.json` |
| E-beam recipes (T4) | `data/lithmod/support_recipes/ebeam_logic_t4.json` (etc.) |
| Boule Purifier recipe | `data/lithmod/support_recipes/boule_purifier.json` |
| Localization (EN) | `assets/lithmod/lang/en_us.json` |

All durations, energy costs, item counts, and multipliers are data-driven; no Java constants.

---

## 8. Sequential Owner Checks (Play-Through)

> Run each check in order; stop on failure. These mirror the game test assertions.

### 8.1 Preparation
1. **Boule Purifier** (formed 2×2×3, water inlet + 1–2 power ports): insert Silicon Boule + water bucket → Pure Silicon Boule + empty bucket.
2. **Wafer Slicer**: insert Pure Boule + empty FOUP Mk1 → FOUP with **3 PURE wafers** (test config), prep gen 1, layer 0, no run spec.
3. **Deposition / Etch** (formed 3×3×5, controller lower front, panel above, 1–2 power ports): insert FOUP (slot 0) + 4 Silicon (slot 1) → after 1 200 ticks @ 1 000 FE/t, output FOUP (slot 2) with **prep gen 2**, same lot ID, same wafer rolls.

### 8.2 Four Mask Designs / 32 Layers / One-Core Compute
4. **E-beam** (formed 2×2×3): select design `logic_t4` / `memory_t4` / `compute_t4` / `nand_t4` → insert Blank Mask + 48 Redstone → GEN 2 mask (tier 4, shop gen 2, uses = config).
5. **Process Cell** (T4 components in slots 0–4): insert prepared FOUP (slot 5), mask (slot 6), 64 T4 Resist (slot 7) → 32 layers complete (100 ticks each base) → finished lot, layer 32, frozen run spec (tier 4, correct die type, **compute = 1 core**).

### 8.3 G1 Refusal / G2 Normal
6. **Legacy Test Package (G1)**: insert finished T4 lot → **refused** (`generation_unmet`), lot not consumed.
7. **Advanced Test Package (G2, high binning OFF)**: insert same lot + chip packages → normal T4 chips + scrap per yield judgment (92 % base). Exact counts: `passing × chipsPerWafer` chips, `(wafers − passing) × chipsPerWafer` scrap.

### 8.4 High-Bin Toggles / Freeze / Refusals
8. Toggle **High Binning** ON (via Operating Panel button) while queue idle.
9. Insert **T3 Logic** or **T3 Compute** finished lot → admitted, judged at 15 % × 92 % ≈ 13.8 % yield, 20 ticks/wafer, 200 FE/t, outputs **T4** chips.
10. Insert T3 Memory / NAND → **refused** (`no_chip_for_die`).
11. Toggle High Binning mid-run → **no effect** on current queue (frozen admission).

### 8.5 Save / Power / Structure Pauses & JEI-Safe Labels
12. Break power → queue pauses (`paused = true`), progress retained.
13. Break structure block → formation lost, queue pauses, progress retained.
14. Reload world / chunk → jobs restore from NBT (pinned duration, energy, progress, inputs).
15. JEI / tooltip labels: masks show `Die type: X, Tier: T4, Uses remaining: N`; FOUPs show `Preparation: G2`, `Layer: 32`, substrate `Pure silicon`; high-binning button reads `High binning: On/Off`.

---

## 9. Limitations & Assumptions

- **No manual verification**: This document reflects source and JSON only; the coordinator runs server tests separately.
- **Config defaults**: `WAFERS_PER_BOULE=25`, `LAYER_TICKS_OVERRIDES={}`, `chipsPerWafer=1`, `maskUses=64`, `rollsPerLayer=1`, FE multiplier = 1×. Test overrides some (3 wafers, T4 layer_ticks=2).
- **Support recipes** (`boule_purifier`, `ebeam_*`) are data-driven; exact durations/energy not shown here — consult their JSON.
- **Process Cell T4 energy**: `per_tier_energy_per_tick` has no T4 entry; falls back to base 20 FE/t.
- **High-binning yield**: 15 % multiplier applies to the tier's default yield (92 % for T4), not a flat 15 %.
- **No T5**: High-binning recipes only define T3→T4; no T4→T5 path exists.
- **Single-core compute**: T4 compute dies frozen at 1 core (test asserts `completed.run().cores() == 1`).
- **FOUP capacity**: Mk1 = 25; slicer respects input FOUP capacity (test uses Mk1).
- **Structure matching**: Deterministic column order (u0 = 0..width-1); first valid match wins. Controller must be on bottom row (v=0) of front face.

---

*Generated from source as of 2026-10-21. Edit the JSON files above to tune numbers; no Java recompile needed for data changes.*