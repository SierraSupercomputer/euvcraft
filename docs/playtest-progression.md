# EUVCRAFT Playtest Progression Guide

A sequential, player-facing walkthrough from raw quartz through Tier 5 chips. For detailed machine build diagrams, slot-by-slot recipes, and data-driven balance values, see the linked reference walkthroughs.

---

## Quick Goal & Setup

**Objective:** Produce logic, memory, compute, and NAND chips at Tiers 1–5, optionally assemble them into CPUs, DIMMs, and microcontrollers, and (for T5) evaluate the cleanroom yield improvement.

**Game mode:** Survival for true cost checking; Creative allowed for faster structure setup. FE (Forge Energy) must be sustained throughout; any power loss pauses work. Use Arcforge-compatible generators/conduits.

**Core supplies you need at every tier:**
- FE power
- Ordinary water (buckets or piped)
- Quartz (Nether quartz, **not sand**)
- Silicon (from smelting/blasting quartz)
- FOUP Mk1 carriers (craftable; Mk2 is creative-only, no recipe)
- Tier-matched masks (Pattern Generator T1/T2, E-beam Writer T3+)
- Tier-matched photoresist
- Chip packages (for Test & Package / Advanced Test & Package)

**Reference links (relative):**
- [T1/T2 equipment & basic chain](progression-m1.md)
- [T3 pure substrates & E-beam masks](manual-walkthrough-t3.md)
- [T4 Deposition/Etch, Scanner, beam alignment](manual-walkthrough-scanner.md)
- [T4 production & high binning](manual-walkthrough-t4.md)
- [T5 utilities, diagnostics, assembly](manual-walkthrough-t5.md)
- [T5 cleanroom](manual-walkthrough-cleanroom.md)
- [FOUP cleaner](manual-walkthrough-foup-cleaner.md)
- [Full generated recipe table](recipes.md)

---

## Machine Size & Panel Quick Reference

| Machine | Structure (W×H×D) | Lower Cabinet / Controller | Upper Operating Panel | Power Ports |
|---------|-------------------|----------------------------|------------------------|-------------|
| Process Cell (T1/T2) | 2×2×3 (solid) | Equipment Cabinet (5 component slots) | FOUP / Mask / Resist / Abort | 1–2 Process Cell Power Ports |
| Crystal Puller | 1 block | — | — | — |
| Wafer Slicer | 1 block | — | — | — |
| Pattern Generator | 1 block | — | — | — |
| Test & Package | 1 block | — | — | — |
| Boule Purifier (T3) | 2×2×3 (support) | Controller (status only) | — | 1–2 Support Power Ports |
| E-beam Mask Writer (T3+) | 2×2×3 (support) | Controller (status only) | — | 1–2 Support Power Ports |
| Deposition / Etch (T4) | 3×3×5 (solid) | Controller (status only) | FOUP + 4 Silicon → G2 FOUP | 1–2 Advanced Power Ports |
| Lithography Scanner (T4/T5) | 3×3×5 (solid, modules inside) | Equipment Cabinet (status only) | FOUP / Mask / Resist | 1–2 Advanced Power Ports |
| Scanner Calibration Panel | Middle layer, any face | — | Beam X/Y/Focus, Accept | — |
| Scanner Diagnostic Panel (T5) | Middle layer, any free cell | — | Quadratic fit A/b/c, Plot, Accept Fit | — |
| Scanner Water Inlet (T5) | Any free casing cell | — | UPW input (Arcforge) | — |
| Advanced Test & Package (T4+) | 2×2×3 (solid) | Controller (status only) | FOUP / Packages / 4 chip out / 2 scrap / High Binning toggle | 1–2 Advanced Power Ports |
| Utility Plant (T5) | 2×2×3 (support) | Controller (Filter + Water → UPW) | — | 1–2 Advanced Power Ports |
| Pick And Place (assembly) | 2×2×2 (solid) | Controller (3 slots) | — | 1–2 Assembly Power Ports |
| Cleanroom Controller | 1 shell block | — | Status, PPM, yield factor, filters | FE required |
| Cleanroom Filter | 1 shell block | — | Filter Medium slot | FE required |
| FOUP Cleaner | 1 block | — | FOUP / Water / Discard & Clean → Clean FOUP + Wafer Scrap | FE required |

---

## Shared Core Loop (All Tiers)

**Quartz → Silicon → Boule → FOUP → Mask → Resist → Lithography → Package**

1. **Quartz → Silicon**  
   Smelt or blast `minecraft:quartz` → `lithmod:silicon`. Blast furnace (100 ticks) is twice as fast as furnace (200 ticks). Sand is rejected.

2. **Silicon → Boule**  
   Crystal Puller: 4 Silicon → 1 Silicon Boule (400 ticks, FE sustained).

3. **Boule → FOUP of Wafers**  
   Wafer Slicer: 1 Boule + 1 empty FOUP Mk1 → FOUP with wafers (default 25, capped by FOUP capacity). Ordinary boules give ordinary wafers; pure boules (T3+) give pure wafers.

4. **Mask Creation**  
   - **T1/T2:** Pattern Generator — Blank Mask + Copper (T1 Logic) / Iron (T2 Logic) / Redstone (T2 Memory) → patterned mask with configured uses.  
   - **T3–T5:** E-beam Mask Writer — Select design, insert Blank Mask + Redstone (32/48/64 depending on tier), run (1,200–2,000 ticks, high FE). Produces Generation-2 mask with configured uses.

5. **Photoresist**  
   Craft tier-matched resist (recipes in [recipes.md](recipes.md)). Resist is consumed **1 per layer**; top up same resist mid-run via Operating Panel.

6. **Lithography**  
   Load FOUP, mask, resist into the Operating Panel. Supply FE. Machine advances through its layer count at its layer tick rate. Mask loses **1 use per completed lot** (not per wafer). Output blockage pauses the run.

7. **Test & Package**  
   Insert judged FOUP + Chip Packages + FE. Per wafer: pass → chips + 1 package consumed; fail → Chip Scrap (no package used). Carrier returns empty. Yield rolls are server-hidden; you see only chip vs. scrap counts.

---

## Tier 1 — Contact (Mercury Lamp)

**Goal:** Produce T1 Logic chips.

**Equipment (Process Cell, 5 components in lower Equipment Cabinet):**
- Mercury Lamp
- Contact Plate
- Manual Aligner
- Basic Air Filter
- Optical Microscope

**Process:**
1. Quartz → Silicon → Boule → FOUP (ordinary wafers).
2. Pattern Generator: Blank Mask + Copper Ingot → T1 Logic Mask.
3. Craft T1 Photoresist (4 per recipe: glass bottle + 4 redstone + 4 sugar).
4. Process Cell: Load FOUP, T1 mask, T1 resist. 1 layer × 600 ticks. Supply FE.
5. Test & Package: FOUP + Chip Packages → T1 Logic Chips / Chip Scrap.

**Checkpoint:** You have T1 Logic Chips. These craft Microcontroller Blanks (T1–T5) and the Pattern Generator itself.

---

## Tier 2 — Stepper (I-line Lamp)

**Goal:** Produce T2 Logic and T2 Memory chips.

**Equipment upgrade (replace all 5 components in Equipment Cabinet):**
- I-line Lamp
- Reduction Lens
- Stepper Stage
- Fine Air Filter
- Overlay Scope

**Masks (Pattern Generator):**
- T2 Logic: Blank Mask + Iron Ingot
- T2 Memory: Blank Mask + Redstone

**Process:** Same flow. 12 layers × 100 ticks. T2 resist recipe adds glowstone dust.

**Checkpoint:** T2 Logic Chips enable Boule Purifier, E-beam Mask Writer, Support Casing, Fine Air Filter, Reduction Lens, Stepper Stage, Precision Stepper Stage, Laser Interferometer, HEP A Filter. T2 Memory Chips enable DIMM assembly (T2–T5).

---

## Tier 3 — KrF DUV (248 nm Excimer Laser)

**Goal:** Produce T3 Logic, Memory, Compute (1 fixed core), and NAND chips.

**New machines (2×2×3 support structures, no upper panel):**
- **Boule Purifier:** Ordinary Boule + 1,000 mB water → Pure Boule (400 ticks, 32,000 FE). Water via buckets or optional Support Water Inlet (any face, Arcforge-compatible).
- **E-beam Mask Writer:** Select design (Logic/Memory/Compute/NAND at T1–T3), insert Blank Mask + 32 Redstone → Gen-2 Mask (1,200 ticks, 2.4M FE).

**Process Cell upgrade (5 components):**
- KrF Excimer Laser
- DUV Projection Lens
- Precision Stepper Stage
- HEP A Filter
- Laser Interferometer

**Flow:**
1. Purify ordinary boule → pure boule → slice into FOUP (pure wafers).
2. E-beam Writer: write desired T3 mask design.
3. Process Cell: Load pure-wafer FOUP, T3 mask, T3 resist. 24 layers × 100 ticks.
4. Test & Package → T3 chips.

**Checkpoint:** T3 Logic Chips unlock Advanced Casing, Advanced Operating Panel, Advanced Power Port, Advanced Test & Package, Deposition/Etch, Scanner modules, ULPA Filter, Nanopositioning Stage, Precision Overlay Scope, CPU/DIMM/MCU blanks, T4 resist.

---

## Tier 4 — ArF Dry (193 nm Excimer Laser)

**Goal:** Produce T4 Logic, Memory, Compute (1 core), NAND chips. Optional: high-bin T3 Logic/Compute → T4.

**New solid multiblocks:**
- **Deposition / Etch Chamber** (3×3×5): Fresh PURE FOUP + 4 Silicon → G2-prepared PURE FOUP (1,200 ticks, 1M FE). Old small Process Cell **cannot** run new T4 lots.
- **Lithography Scanner** (3×3×5): Filled solid body. Middle layer = 5 module blocks (Exposure, Optics, Stage, Filtration, Metrology) + 1 Calibration Panel (any order, calibration panel on accessible face). Top/bottom = frame casing except cabinet/ports.
- **Advanced Test & Package** (2×2×3): Required for T4/T5. High Binning toggle (OFF for normal).

**Scanner modules (craft from T3 components + T3 Logic + diamonds):**
- ArF Excimer Laser (from KrF + T3 Logic + 2 diamond + netherite)
- ArF Dry Lens (from DUV Lens + T3 Logic + diamond)
- Nanopositioning Stage (from Precision Stepper + T3 Logic)
- ULPA Filter (from HEP A + T3 Logic)
- Precision Overlay Scope (from Laser Interferometer + T3 Logic)

**E-beam masks (T4 designs):** 48 Redstone, 1,600 ticks, 4M FE per mask.

**Process:**
1. Pure boule → PURE FOUP (25 wafers).
2. Deposition/Etch: PURE FOUP + 4 Silicon → G2-prepared PURE FOUP.
3. E-beam Writer: write T4 mask (Logic/Memory/Compute/NAND).
4. Scanner: Install 5 modules + Calibration Panel. **Calibrate first:** open Calibration Panel, center beam (X/Y), reduce focus error. Quality 50 → 0.75× yield multiplier; 100 → 1.0×. Press Accept. Calibration locks during run; module swap requires recalibration.
5. Scanner Operating Panel: G2-prepared FOUP + T4 mask + T4 resist. 32 layers × 100 ticks, 240 FE/t.
6. Advanced Test & Package (High Binning OFF) → T4 chips.

**High Binning (optional):** Advanced Test & Package, toggle ON, insert completed **T3 Logic or T3 Compute** lot + packages. Target yield = 0.15 × source lot's frozen effective yield (no reroll). Memory/NAND refused. T4 cannot high-bin to T5. Double time/FE per wafer.

**Checkpoint:** T4 Logic Chips unlock cleanroom blocks, T5 resist, immersion components, utility plant, scanner diagnostic panel, scanner water inlet.

---

## Tier 5 — ArF Immersion (193 nm, Water Immersion)

**Goal:** Produce T5 Logic, Memory, Compute (1 core), NAND chips. Evaluate cleanroom yield improvement. Optional assembly.

**Utility Plant (2×2×3 support):** Utility Filter + 8,000 mB ordinary water → 8,000 mB Ultrapure Water (UPW) per batch (200 ticks, 80 FE/t). Pipe UPW via Arcforge to scanner. Scanner **refuses ordinary water**.

**Scanner retrofit (same 3×3×5 structure):**
- Replace Optics Module with **Immersion Module** (craft from ArF Dry Lens + T4 Logic + diamond + T4 Compute + Immersion Light Source/Stage/Filtration/Metrology components).
- Add **Scanner Diagnostic Panel** (middle layer free cell) + **Scanner Water Inlet** (any free casing/frame cell). Connect UPW.

**Calibration & Diagnostics (both required):**
1. **Beam alignment** (Calibration Panel): same as T4. Quality → factor (0.5 + 0.5×quality/100).
2. **Quadratic fit** (Diagnostic Panel): hidden target curve y = Ax² + bx + c (coefficients in hundredths). Enter A, b, c ∈ [−2.00, 2.00]. Burn Paper in slot → Plot (consumes 1 paper). Score ≥ 50 (default) → Accept Fit. Factor = same formula. Replacing calibrated hardware invalidates both.

**E-beam masks (T5):** 64 Redstone, 2,000 ticks, 6M FE per mask (all 4 families).

**Process:**
1. Pure boule → PURE FOUP → Deposition/Etch → G2-prepared PURE FOUP (same as T4).
2. E-beam Writer: write T5 mask.
3. Scanner: G2-prepared FOUP + T5 mask + T5 resist. 42 layers × 100 ticks, 240 FE/t, **2 mB UPW per active tick** (default lot ≈ 8,400 mB; one plant batch = 8,000 mB — make two batches or replenish).
4. Advanced Test & Package → T5 chips. High Binning: T4 Logic/Compute → T5 (penalty 0.15×). Memory/NAND no high bin. Logic caps at T5.

**Checkpoint:** Four T5 chip families in hand.

---

## Optional: Pick-and-Place Assembly (Any Tier After Unlock)

**Machine:** 2×2×2 solid Assembly Casing + Controller + 1–2 Assembly Power Ports. No panel, no calibration, no cleanroom penalty.

| Product | Inputs | Tiers |
|---------|--------|-------|
| CPU | 1 Compute Chip + 1 CPU Blank | T3–T5 |
| DIMM | 4 Memory Chips + 1 DIMM Blank | T2–T5 |
| Microcontroller | 1 Logic Chip + 1 MCU Blank | T1–T5 |

Blanks embody assembly materials (gold nuggets, iron, redstone). Default time: 80 + 20×tier ticks at 20 FE/t. No yield reroll. Downstream recipes don't yet require assembled parts; this is a playable optional step.

---

## Optional: T5 Cleanroom (Yield Improvement)

**Structure:** Hollow **sealed cuboid** of cleanroom shell blocks (floor/wall/window) including ceiling and corners. Suggested outer 9×7×11 → interior 7×5×9 (fits 3×3×5 scanner with margin). One Cleanroom Controller inside shell + several Cleanroom Filters. All scanner member blocks **strictly inside**.

**Operation:**
- Controller (100 FE/t) + each Filter (50 FE/t) need constant FE. Filter Medium lasts 12,000 working ticks (10 min).
- New room starts dirty: 10,001 PPM, 50% room yield factor. Filtration raises cleanliness → factor approaches 100% nonlinearly. Larger rooms need more filters.
- Cleanroom Door (paired, redstone-only open) leaks slightly when open. Removing either half = breach.
- Sealed Power/Fluid/Item Ports for through-wall connections (Arcforge endpoints inside).
- Gown (Hood/Suit/Trousers/Boots, all 4 slots) reduces occupant contamination 10× but doesn't eliminate it.

**T5 yield:** Outside/dirty room = 0.5× room factor. Pristine room → 1.0×. Each paid processing tick samples current room factor; paused ticks don't count. Average exposure persists in FOUP through saves/pauses/moves, applied once at completion. Packaging/high binning uses resulting wafer state **without a second cleanroom charge**. Breach during run lowers subsequent yield; does **not** abort/scrap/damage. Emptying carrier clears exposure.

**Chunk loading:** Keep chunks loaded. Unknown chunks suspend room simulation and associated scanner processing.

---

## Optional: FOUP Cleaner (Any Tier, Waste Disposal)

**Single block.** Recipe: iron, buckets, redstone, glass. FE + 1,000 mB ordinary water (bucket or piped). Insert any FOUP (partial/complete, any substrate/prep), press **Discard & Clean** (200 ticks, 20 FE/t). **Destroys all wafers.** Returns same empty carrier (fresh lot ID) + 1 Wafer Scrap per discarded wafer. No chip packages, no calibration, no UPW, no selective recovery. Wafer Scrap ≠ Chip Scrap. Carrier reusable. Blocked output pauses without extra FE. Save/reload preserves progress.

---

## Troubleshooting Quick Reference

| Symptom | Likely Cause |
|---------|--------------|
| Lower cabinet shows equipment slots, upper panel shows consumables | Correct — two views, same inventory. Equipment locked during run. |
| "Calibration required" on Operating Panel | Open separate Calibration Panel (middle layer), align beam, Accept. |
| Scanner refuses ordinary water | Scanner Water Inlet accepts **only UPW** from Utility Plant. |
| T4 lot refused by old Test & Package | Use Advanced Test & Package. |
| High Binning greyed out / wrong tier | Only T3 Logic/Compute → T4; T4 Logic/Compute → T5. Memory/NAND never. |
| Resist won't insert / swap mid-run | Only **exact same resist item** (same components) can be added while running/paused. |
| Mask use not decrementing | Mask loses 1 use **per completed lot**, not per wafer. |
| FOUP returns fewer wafers than configured | FOUP capacity caps yield: `min(configured, capacity)`. |
| Power loss mid-run | Progress pauses; resumes when FE restored. No double-charge. |
| Module replaced in scanner | Recalibration required (both beam and diagnostic for T5). |
| Cleanroom yield stuck at 50% | Room dirty, insufficient filters/FE, door open, breach, or scanner not fully inside. |
| Wafer Scrap vs Chip Scrap | Wafer Scrap = cleaner output (1 per wafer). Chip Scrap = packaging failure (chipsPerWafer per failed wafer). |

---

## Tier Checkpoints (Report These When Playtesting)

For each tier reached, note:
- **Tier / Machine** (e.g., "T3 Process Cell upgraded", "T4 Scanner calibrated")
- **Status** (running / paused / completed / error)
- **Input tooltip** (FOUP substrate, wafer count, layer progress, preparation state, mask uses remaining)
- **Structure screenshot** (verify dimensions, casing fill, panel/port placement, module positions)
- **Build coordinates** (for cleanroom: outer/interior dims, filter count, controller powered)
- **Expected vs. actual** (chip counts, scrap counts, calibration quality, diagnostic score, room yield factor)

**Playtest endpoint:** Four T5 chip families produced + assembled products (CPU/DIMM/MCU) + cleanroom yield improvement demonstrated. T6+ not part of this guide.

---

*No false claims of actual tester completion. Numbers reflect current bundled data (FE multiplier 1.0); all timings, yields, and costs are datapack-editable.*