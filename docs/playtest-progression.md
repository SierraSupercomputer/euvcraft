# EUVCRAFT playtest progression guide — T1 to T7

**Linear-fab development update:** T4 follows Deposition → Scanner → Etch Cell → Advanced Test & Package. Its checkpoint was accepted on November 2. T5–T7 now add coating before exposure and productive wet cleaning after etching; T7 has separate G3 deposition and etch chambers. Use fresh later-tier batches. Earlier tier walkthroughs remain useful for modules/calibration, but the routes here supersede their older combined processing steps.

Follow this route to test current progression. Use Survival to check crafting costs, or Creative to speed up construction. Keep chips from each generation: later recipes consume earlier chips. Exact crafting ingredients are in the [recipe reference](recipes.md); quantities below assume bundled defaults.

## Contents

- [Before starting](#basics)
- [Build reference](#build-reference)
- [Tier 1 — first logic chips](#tier-1)
- [Tier 2 — stepper and memory](#tier-2)
- [Tier 3 — pure substrates and selectable masks](#tier-3)
- [Tier 4 — preparation chamber and calibrated scanner](#tier-4)
- [Tier 5 — ultrapure water and diagnostic tuning](#tier-5)
- [T5 cleanroom — optional, but worthwhile](#cleanrooms)
- [Optional assembly and disposal](#assembly)
- [Tier 6 and 7 linear routes](#tier-6-and-tier-7)
- [Calibration reference](#calibration)
- [Cleanrooms](#cleanrooms)
- [Automation and item/fluid ports](#automation)
- [Assembly: Pick And Place and Reflow](#assembly)
- [Recycling (new single-block Recycler)](#recycling)
- [JEI integration (optional)](#jei)
- [Troubleshooting](#troubleshooting)
- [Playtest checklist](#playtest-checklist)
- [Known issues](#known-issues)

<a id="basics"></a>
## Before starting

- Provide continuous FE power. A full machine buffer may not cover an entire job.
- Gather quartz, crafting materials, empty **FOUP Mk1** carriers, Blank Masks and Chip Packages. **Smelt or blast quartz into Silicon; sand makes glass.** Mk2 carriers currently have no crafting recipe.
- A FOUP carries a batch of wafers between machines. Its tooltip shows wafer count, substrate, preparation and processing state.
- Masks select chip family and tier. Match mask and Photoresist tier to installed equipment. A completed batch consumes **one mask use**, regardless of wafer count.
- Missing power, consumables or output space can pause work. Check machine status before assuming it is broken.

## Build reference

Dimensions are **width × height × depth**, facing the front. Production multiblocks are filled cuboids; cleanrooms are hollow. Use matching casings and power ports.

| Machine | Size | Where to open it |
|---|---|---|
| Crystal Puller, Wafer Slicer, Pattern Generator, Test & Package | Single blocks | Machine itself |
| Process Cell, T1–T3 | 2×2×3 | Lower Equipment Cabinet: permanent equipment. Operating Panel directly above: FOUP, mask, resist. |
| Boule Purifier and E-beam Mask Writer | 2×2×3 each | Controller contains inputs; no upper panel. Support Casings/Power Ports. |
| G2 Deposition Chamber / Etch Cell | 3×3×5 each | Advanced Operating Panel above the lower controller; lower cabinet is status-only. |
| Coating Track, T5–T7 | 3×2×5 | Advanced Operating Panel above controller; Advanced Casing/Power Ports. |
| Wet-Clean Station, T5–T7 | 2×2×3 | Advanced Operating Panel above controller; one Fab UPW Inlet required. |
| G3 Deposition Chamber / G3 Etch Cell, T7 | 5×5×7 each | Existing G3 module/panel layout; separate controllers. |
| Lithography Scanner | 3×3×5 | Scanner Operating Panel above cabinet; separate Calibration and Diagnostic Panels. |
| Advanced Test & Package | 2×2×3 | Advanced Operating Panel above lower controller. |
| Utility Plant | 2×2×3 | Controller contains inputs; no upper panel. |
| Pick And Place | 2×2×2 | Controller; no upper panel. |
| FOUP Cleaner | Single block | Machine itself |

Place production multiblock controllers in the bottom front row, facing outward, with **one or two matching power ports** elsewhere. Fill remaining cells with casings except the modules, panels and fluid ports below. Confirm the machine is formed. Detailed walkthroughs linked at each tier show placement requirements.

## Tier 1 — first logic chips

1. **Crystal Puller:** Four Silicon → ordinary Silicon Boule.
2. **Wafer Slicer:** Boule + empty FOUP Mk1 → wafer FOUP. Default batch: 25 wafers, limited by carrier capacity.
3. **Pattern Generator:** Blank Mask + Copper Ingot → T1 Logic Mask.
4. Build the **Process Cell**. Install five items in its lower cabinet: **Mercury Lamp, Contact Plate, Manual Aligner, Basic Air Filter, Optical Microscope**.
5. Upper Operating Panel: wafer FOUP + T1 Logic Mask + T1 Photoresist. It starts when requirements are met. Resist is consumed per layer.
6. **Test & Package:** Completed FOUP + Chip Packages + FE → chips or Chip Scrap. Successful wafers consume packages; failed wafers do not. Collect the reusable empty FOUP.

**Pass:** Obtain T1 Logic Chips and reuse the carrier. Some scrap is normal.

## Tier 2 — stepper and memory

Keep the Process Cell. Replace its five equipment items with **I-line Lamp, Reduction Lens, Stepper Stage, Fine Air Filter, Overlay Scope**.

Use ordinary wafers. The Pattern Generator makes **T2 Logic Masks from Blank Mask + Iron Ingot**, and **T2 Memory Masks from Blank Mask + Redstone**. Use T2 Photoresist; this tier has more layers, so stock extra resist. Package in the original Test & Package.

**Pass:** Produce T2 Logic and Memory Chips. Save logic chips for the next machines.

## Tier 3 — pure substrates and selectable masks

Build a **Boule Purifier** and **E-beam Mask Writer**, each 2×2×3 with Support Casings/Power Ports. Their controllers contain the inputs.

1. Purifier: ordinary boule + ordinary water → **Pure Silicon Boule**. Use buckets initially; an optional Support Water Inlet replaces a spare casing for piped input.
2. Slice the pure boule. Confirm the FOUP tooltip identifies pure wafers.
3. E-beam writer: supply Blank Mask, Redstone and substantial FE. Select the desired T3 design, then **Write mask**.
4. Upgrade the Process Cell's five equipment items: **KrF Excimer Laser, DUV Projection Lens, Precision Stepper Stage, HEPA Filter, Laser Interferometer**.
5. Run pure wafers with T3 mask/resist, then use the original Test & Package.

**Pass:** Produce T3 Logic, Memory, Compute and NAND chips. Compute currently has one core.

## Tier 4 — preparation chamber and calibrated scanner

Build separate **Deposition Chamber and Etch Cell (3×3×5 each)**, **Lithography Scanner (3×3×5)** and **Advanced Test & Package (2×2×3)** with Advanced Fab Casings/Power Ports. The scanner replaces the small Process Cell for T4 processing.

The scanner's **middle layer** needs one each of the physical **Exposure, Optics, Stage, Filtration and Metrology Modules**, plus a **Calibration Panel**. They can occupy any valid middle-layer positions; keep calibration accessible. Fill unused cells with casings/ports. Top/bottom layers form the frame, with the controller and operating panel arranged with the controller in the bottom front row and the operating panel directly above it. Physical modules replace the earlier cabinet equipment items.

Run this route:

1. Pure boule → fresh pure-wafer FOUP.
2. Deposition **upper panel:** FOUP + two Silicon → **G2-prepared, Film deposited** pure-wafer FOUP.
3. E-beam writer: write the desired T4 mask.
4. Scanner Calibration Panel: adjust beam X, Y and Focus. Reach at least the default quality threshold of 50, then **Accept alignment**. Better alignment improves yield; changing modules invalidates calibration.
5. Scanner upper panel: prepared FOUP + T4 mask + T4 resist.
6. Etch Cell upper panel: exposed FOUP + one Etchant Charge → **Etched** FOUP. Then Advanced Test & Package upper panel: etched FOUP + Chip Packages. Leave **High Binning off** for normal production.

**Pass:** Produce all four T4 chip families through the full scanner route. Older T4 walkthroughs predate the scanner; follow this route for current progression.

**Optional high binning:** The advanced packager can turn completed **T3 Logic/Compute lots into T4**, or **T4 Logic/Compute lots into T5**. Enable its toggle before admission. The default success threshold is only 0.15 times the source lot's effective yield: expect heavy losses. It reuses existing wafer rolls. Memory/NAND cannot be high-binned; logic stops at T5. This shortcut does not test the next tier's full machinery.

## Tier 5 — ultrapure water and diagnostic tuning

Keep the pure-boule, G2 preparation and advanced packaging chain.

1. Build a **Utility Plant**, 2×2×3, with Advanced Fab Casings/Power Ports, exactly one **Utility Water Inlet** and one **Utility Pure Outlet**. Supply Utility Filter media and ordinary water.
2. Pipe its **Ultrapure Water (UPW)** through Arcforge-compatible transport into a **Scanner Water Inlet**, replacing a spare scanner casing. The scanner rejects ordinary water.
3. Replace the scanner's Optics Module with an **Immersion Module**. Add a **Scanner Diagnostic Panel** in a spare middle-layer position. Keep other modules and the Calibration Panel.
4. Accept beam alignment again. In the Diagnostic Panel, match the target curve using **y = Ax² + bx + c**: A controls curvature, b tilt and c vertical offset. Target coefficients are hidden.
5. Insert **Burn Paper**. Each **Plot** attempt consumes one paper; editing coefficients is free. Reach the default score threshold of 50, then **Accept Fit**. Both accepted calibration qualities affect wafer yield.
6. Run deposited FOUP + 42 T5 resist through the **Coating Track**. Its resist grade commits the tier; family/cores stay unassigned until exposure. Feed coated FOUP + T5 mask into the scanner, leaving its resist slot empty. Then Etch Cell + one Etchant Charge → Wet-Clean Station supplied with UPW → advanced packaging.

One default utility batch makes **8,000 mB UPW**, while a default T5 lot needs **8,400 mB**. Make two batches or replenish during processing; maintain FE throughout.

**Pass:** Produce all four T5 chip families with both calibrations accepted.

## T5 cleanroom — optional, but worthwhile

T5 works outside a cleanroom with a **0.5× room yield factor**. A clean room can raise this toward 1.0×; earlier tiers and Pick And Place have no room penalty.

Build a sealed, hollow **cuboid** using cleanroom floor/wall/window blocks, including ceiling and corners. An outer **9×7×11** gives a **7×5×9 interior**, enough for a scanner and access space. Every scanner block must be strictly inside. Put one Cleanroom Controller and Cleanroom Filters in the shell; use sealed power/fluid/item ports for through-wall connections.

Power the controller **and each filter**, insert Cleanroom Filter Medium, and allow filtration time while chunks stay loaded. New rooms start dirty; larger rooms need more filters. Doors open by redstone and leak when open. A complete four-piece gown reduces occupant contamination but does not eliminate it.

**Pass:** Controller reports enclosed; PPM falls and the room yield factor improves. Compare production under better cleanliness. Current T5 breaches worsen yield; they do **not** abort lots or damage equipment. See the [cleanroom walkthrough](manual-walkthrough-cleanroom.md).

## Optional assembly and disposal

**Pick And Place:** Build a solid 2×2×2 with Assembly Casings, controller and Assembly Power Ports. Supply FE and universal blanks:

| Product | Inputs | Tiers |
|---|---|---|
| Microcontroller | One Logic Chip + Microcontroller Blank | T1–T5 |
| DIMM | Four Memory Chips + DIMM Blank | T2–T5 |
| CPU | One Compute Chip + CPU Blank | T3–T5 |

Outputs retain chip tier. Assembly has no calibration requirement or extra yield roll. NAND assembly is future content; T6 uses the calibrated reflow successor described below.

**FOUP Cleaner:** Supply FE and ordinary water, insert an unwanted FOUP and choose **Discard & Clean**. This destroys **all wafers** in that carrier. It returns the same empty FOUP and **one Wafer Scrap per wafer**; it does not selectively remove failures. Default water cost: 1,000 mB. No Chip Packages or UPW needed. Wafer Scrap differs from packaging's Chip Scrap; recycling is not implemented. See the [cleaner walkthrough](manual-walkthrough-foup-cleaner.md).

## Troubleshooting and reporting

- **Unformed:** Check dimensions, filled cells, matching casings/ports, controller facing and required panel directly above it.
- **No input slots:** Deposition/Etch and advanced packaging use the upper panel; lower cabinets are status-only. Purifier, mask writer and utility plant use their controller.
- **Input refused:** Check FOUP substrate/preparation, matching mask/resist tier, equipment and accepted calibration. T4+ needs the large scanner and advanced packager.
- **Paused:** Check FE, resist, UPW, filter media and output space. Same-resist top-ups are allowed; swapping resist during a run is not.
- **Low yield:** Check calibration and T5 room cleanliness; turn off High Binning for normal production. Small batches can vary.

Report tier/machine, visible status, FOUP tooltip, inputs, power/fluid supply and screenshots of the structure and GUI. Follow the [T6 walkthrough](manual-walkthrough-t6.md) after T5, then the [T7 EUV walkthrough](manual-walkthrough-t7.md). T7 adds Fab Hydrogen, a separate tin-fed source and scanner, G3 preparation/reticles, local or central vacuum, hard cleanroom enclosure, contaminated FOUP disposal and persistent equipment repair. T8, NAND assembly, server hardware compatibility and scrap recycling remain deferred.

<a id="tier-6"></a>
<a id="tier-7"></a>
## Tier 6 and 7 linear routes

T6: Electronic-grade boule → slicer → G2 deposition → Coating Track (70 T6 resist) → calibrated multi-patterning scanner → G2 Etch Cell (one Etchant Charge) → Wet-Clean Station → advanced packaging → existing reflow assembly. Refill resist beyond 64; it is paid gradually. Existing core selection and two internal exposure passes remain.

T7: Electronic-grade boule → slicer → G3 deposition → Coating Track (80 T7 resist) → EUV scanner with its separate calibrated source → G3 Etch Cell (one Etchant Charge) → Wet-Clean Station → advanced packaging → reflow. Every T7 wafer-processing structure must fit strictly inside an enclosed cleanroom; the shared coater/cleaner only require enclosure for T7. The G3 preparation rinse is now paid at the Wet-Clean Station. The source takes no FOUP. See the [new route walkthrough](manual-walkthrough-linear-t5-t7.md) for layouts, automation, shortages and breach/repair tests.

<a id="calibration"></a>
## Calibration reference

**Beam alignment (T4+):** Scanner Calibration Panel adjusts X, Y, Focus. Default threshold: quality ≥ 50. Better alignment improves yield. Changing any physical module invalidates calibration.

**Diagnostic fitting (T5+):** Scanner Diagnostic Panel. Target curve: hidden **y = Ax² + bx + c** over x ∈ [−2, 2]. Adjust A (curvature), b (tilt), c (vertical offset) in hundredths. Each **Plot** consumes one **Burn Paper**; editing is free. Accept at score ≥ 50 (default). Both beam and diagnostic factors multiply into lot yield: each factor = 0.5 + 0.5 × quality/100. Calibration persists through reload; module replacement requires fresh calibration.

**Multi-patterning (T6):** Retains T5 beam + diagnostic. Scanner gains one **Multi-Patterning Module** in a middle-layer casing slot. Two exposure passes per layer; core count selected at mask write (1, 2, 4, 8, 12, 24).

**EUV source pulse (T7):** Separate **Source Calibration Panel** on the EUV Light Source. Three knobs: **Droplet Phase**, **Main-pulse Delay**, **Pulse Amplitude**. Each **Test Pulse** costs **12,000 FE + 50 mB Fab H₂ + 25 tin credits** (1 feedstock = 1,000 credits). Accept at quality ≥ 60 (default). Source factor: 0.85 at Q60 → 1.00 at Q100. Survives reload; replacing Pulse Control Module or rebuilding source requires fresh calibration.

**T6 contamination / T7 reticle:** T6 contamination invalidates and reshuffles diagnostic targets. T7 reticle beam calibration follows existing approved-per-mask policy; source instructions shown in panel.

<a id="cleanrooms"></a>
## Cleanrooms

Cleanrooms are **hollow sealed cuboids** built from cleanroom floor, wall, and window blocks (including ceiling and corners). One **Cleanroom Controller** and one or more **Cleanroom Filters** inside the shell. Use **Cleanroom Power Port**, **Cleanroom Fluid Port**, **Cleanroom Item Port** for through-wall connections.

- **Room yield factor:** Outside = 0.5× (T5+). Inside, PPM falls over time toward 1.0×. Controller reports **enclosed** when sealed.
- **Power:** Controller and each filter need FE. Insert **Cleanroom Filter Medium** into filters.
- **Filtration time:** Chunks must stay loaded. Larger rooms need more filters. Doors leak when open (redstone-controlled).
- **Gowns:** Four-piece set reduces occupant contamination; does not eliminate it.
- **T5/T6:** Soft penalty only. Breaches worsen yield; do not abort lots or damage equipment.
- **T7:** **Hard requirement.** All T7 wafer-processing multiblocks (G3 Deposition, EUV Scanner, G3 Etch, Wet-Clean, Advanced Packager, Reflow) must be strictly inside an enclosed powered room. Coater and cleaner only hard-require enclosure for T7 lots.
- **Breach during T7 run:** Active wafers become **Contaminated FOUP**. Machine contamination persists through saves and controller pickup. Discard via FOUP Cleaner (returns Wafer Scrap). Repair via lower cabinet: 2 Utility Filters + 16 Redstone + 8,000 mB UPW + 200,000 FE (configurable). Repairs never restore contaminated wafers. Scanner also requires fresh beam + diagnostic calibration (targets change).
- **Central Vacuum (exists):** 3×3×5 Central Vacuum Plant with Vacuum Casing, Service Outlet, Service Conduits. Connect to machine Vacuum Modules. Cleanroom Vacuum Port seals through walls. Plant: up to 20,000 service units/t at 350 FE/t vs. 200 FE/t per local pump. Isolated chambers fall back to local pump.

<a id="automation"></a>
## Automation and item/fluid ports

**Fab Item Port** (Advanced) / **G3 Fab Item Port** / **EUV Fab Item Port**: replace any casing in a formed multiblock. Exposes machine inventory on all faces. Works with Arcforge item conduits (configure pull/push at endpoints). Inputs, installed masks, and scanner modules cannot be extracted. Power still enters via power ports. Breaking the shell stops transfers.

**Fluid/gas:** Arcforge-compatible pipes/conduits. UPW from Utility Plant → Scanner/Coater/Wet-Clean inlets. Fab Hydrogen via pressurized gas conduits from Hydrogen Purifier. Ordinary water refused by UPW inlets.

**Hoppers:** Not supported on any EUVCRAFT machine.

**Automation checklist:** Process two successive FOUPs through each tier. Confirm finished outputs + empty return-pack FOUPs extract; active/queued wafers and modules protected.

<a id="assembly"></a>
## Assembly: Pick And Place and Reflow

**Pick And Place** (2×2×2 solid): Assembly Casings, controller (bottom front), 1–2 Assembly Power Ports. No panel, no calibration. GUI: left = chip, middle = universal blank, right = result. FE via port.

| Product | Inputs | Tiers |
|---|---|---|
| Microcontroller | 1 Logic Chip + Microcontroller Blank | T1–T5 |
| DIMM | 4 Memory Chips + DIMM Blank | T2–T5 |
| CPU | 1 Compute Chip + CPU Blank | T3–T5 |

Outputs retain chip tier. No yield roll. NAND assembly deferred.

**Reflow Oven** (T6/T7, 3×3×2 solid): Reflow Casings, controller (bottom front), Operating Panel directly above, **Reflow Calibration Panel** in another middle-layer cell, 1–2 Reflow Power Ports. Upper panel: chips, blanks, finished assemblies, scrap. Calibration panel: Thermal Test Coupons.

**Thermal tuning:** Adjust Heater (temp/ramp), Conveyor (dwell), Airflow (cooling). Gray = free nominal preview. **Test** consumes 1 coupon + 2,000 FE → draws actual cyan response. Amber = target guidance. Numerical bounds show exact requirements. Default target bands: ramp 1–3, peak 230–245°C, dwell 30–70, cooling 1–4. Accept at quality ≥ 750/1000. Editing requires another paid test + accept. Controls lock during assembly. Calibration quality freezes yield multiplier per assembly, combined with cleanroom exposure.

| Result | Admitted inputs | Default work |
|---|---|---|
| T6/T7 CPU | 1 Compute Chip + 1 CPU Blank | 240 ticks, 240 FE/t |
| T6/T7 DIMM | 4 Memory Chips + 1 DIMM Blank | 240 ticks, 240 FE/t |

Successful CPU preserves core count. Failed assembly consumes inputs → 1 Assembly Scrap. No T6/T7 microcontroller or NAND assembly. T1–T5 Pick And Place remains available.

**Open issue:** Reflow controls/metrics clustering issue still open. Do not adjust targets or present unverified workarounds.

<a id="recycling"></a>
## Recycling (new single-block Recycler)

**Recycler** — single block, craftable: **6 Iron + 2 Redstone + Furnace**. GUI: input slot, output slot, FE buffer. Optional **Fab Item Port** on any face for conduit automation (all faces). Hoppers not supported.

**Recipes** (three editable `machine_recipes` JSON files):
- 64 **Wafer Scrap** → 1 Silicon
- 64 **Chip Scrap** → 1 Silicon
- 64 **Assembly Scrap** → 1 Silicon

**Process:** 400 ticks @ 40 FE/t. No water. Default recovery **below 100%**; check loops after config edits. Does not accept live FOUPs, chips, or masks.

**FOUP Cleaner** (existing): FE + ordinary water → **Discard & Clean** destroys all wafers in carrier, returns empty FOUP + 1 Wafer Scrap per wafer (default 1,000 mB water). Does not selectively remove failures. Wafer Scrap ≠ Chip Scrap. Loose scrap can then go to Recycler.

<a id="jei"></a>
## JEI integration (optional)

Requires **JEI 31.14.0.72+** for Minecraft 26.3 / NeoForge. Client-side install; dedicated server works without JEI. Matching EUVCRAFT client/server builds provide the process catalog.

- **Station = actual controller block** (not casings/panels). Multiblocks accessed via operating panel still list controller.
- **Categories:** slicing, preparation, coating, scanning, etching, productive cleaning, disposal, packaging/high binning, utility services, vacuum/light-source services.
- **Fluids/gases:** real registered fluid ingredients (look up fluid entry, not bucket placeholder).
- **Masks:** spend **1 use per completed lot**. Design lookup ignores remaining uses and core selections. Writing still requires GUI selection.
- **FOUP icons:** generic stage-labelled carrier placeholders, not finished lots. Hover for stage/tier/substrate. Multiple compatible pages shown; private lot data never exposed.
- **Notes:** abbreviated; hover details for full explanation. FE/t and ticks reflect current server new-job settings. Variable/phase-specific costs in notes. Admitted lots retain frozen budgets (may differ after config edit).
- **Yields:** packaging/reflow output counts illustrative. High binning follows source tier's completed route; T7 targets additionally require enclosure. Wet-Clean retains wafers; FOUP Cleaner explicitly destroys.
- **Server sync:** Catalog sent on login/datapack reload/settings change. JEI refreshes changed pages; clears previous server's catalog on disconnect. Bundled: ~114 pages across 25 controller stations. Datapacks/settings can change this. Oversized/unsupported data fails closed to empty pages.
- **Verification:** Start with matching builds + supported JEI. Check all controller stations have processes. Find Pure Silicon Boule, patterned mask, UPW, Fab Hydrogen via JEI. Follow FOUP through T4 and T5–T7. Inspect normal/high-bin packaging and reflow failure outputs. Change recipe via datapack + `/reload`; check updates. Reconnect to second server; first server's overrides gone. Remove JEI → EUVCRAFT still starts and machines operate. No Ponder or auto-crafting.

<a id="troubleshooting"></a>
## Troubleshooting

| Symptom | Checks |
|---|---|
| **Unformed** | Dimensions, all cells filled, matching casings/ports, controller facing outward, required panel directly above controller. |
| **No input slots** | Deposition/Etch/Advanced Packager use upper panel; lower cabinet status-only. Purifier, mask writer, utility plant use controller. |
| **Input refused** | FOUP substrate/preparation, matching mask/resist tier, equipment formed, accepted calibration (T4+). T4+ needs large scanner + advanced packager. |
| **Paused** | FE, resist, UPW, filter media, output space. Same-resist top-ups allowed; swapping resist mid-run not allowed. |
| **Low yield** | Calibration quality, T5+ room cleanliness, High Binning off for normal production. Small batches vary. |
| **Scanner rejects water** | Must be UPW via Scanner Water Inlet; ordinary water refused. |
| **Source won't test** | FE + Fab H₂ + tin credits all required; refusal with no partial payment. |
| **Cleanroom not enclosed** | All 6 faces sealed, ceiling + corners, controller + filters powered, filter medium inserted, doors closed. |
| **T7 breach** | Contaminated FOUP → FOUP Cleaner → repair via lower cabinet (2 Utility Filters + 16 Redstone + 8 buckets UPW + 200k FE). Scanner needs fresh beam + diagnostic calibration. |
| **Reflow calibration lost** | Module replacement requires fresh thermal test + accept. Power loss/reload preserves accepted calibration. |

Report: tier/machine, visible status, FOUP tooltip, inputs, power/fluid supply, screenshots of structure and GUI.

<a id="playtest-checklist"></a>
## Playtest checklist

Run these practical checks in order. Use Survival for costs, Creative for speed.

1. **T1–T3 baseline:** Produce T1 Logic, T2 Logic/Memory, T3 Logic/Memory/Compute/NAND. Verify FOUP reuse, mask use decrement, resist consumption.
2. **T4 full route:** Pure boule → slicer → Deposition (2 Si) → beam calibration → Scanner (T4 mask + resist) → Etch Cell (1 charge) → Advanced Packager. All 4 families. High bin T3→T4 logic/compute.
3. **T5 full route:** Utility Plant → UPW to scanner → Immersion Module + Diagnostic Panel → beam + diagnostic calibration → Coating Track (42 T5 resist) → Scanner (mask, resist slot empty) → Etch → Wet-Clean (UPW) → Advanced Packager. All 4 families. High bin T4→T5 logic/compute.
4. **Cleanroom:** Build 9×7×11 outer / 7×5×9 inner. Power controller + filters. Verify PPM drops, room yield factor improves. Compare T5 yield inside vs. outside.
5. **T6 full route:** Zone Refiner (pure boule + UPW → electronic-grade boule) → slicer → G2 Deposition → Coating Track (70 T6 resist, refill >64) → Scanner + Multi-Patterning Module (beam + diagnostic calib) → G2 Etch (1 charge) → Wet-Clean → Advanced Packager → Reflow (thermal calibration). Test compute core selection (1/2/4/8/12/24). High bin T5→T6 compute.
6. **T7 full route:** Hydrogen Purifier (gas conduits) → G3 Reticle Writer (cleanroom affects reticle uses only) → Zone Refiner → slicer → G3 Deposition (in cleanroom) → Coating Track (80 T7 resist) → EUV Source (tin + Fab H₂ + pulse calibration) → EUV Scanner (in cleanroom, beam + diagnostic calib) → G3 Etch (1 charge) → Wet-Clean → Advanced Packager (in cleanroom) → Reflow (in cleanroom). High bin T6→T7 compute (requires cleanroom at packaging).
7. **Breach/recovery:** Pause T7 run → break cleanroom wall → verify Contaminated FOUP → FOUP Cleaner → repair machine → fresh calibration → resume.
8. **Automation:** Two successive FOUPs per tier via Fab Item Ports + Arcforge conduits. Verify active wafers protected, empty carriers return.
9. **JEI:** With JEI 31.14.0.72+, verify all controller stations appear, fluid entries correct, FOUP icons generic, mask use = 1/lot, server sync on reload/reconnect.
10. **Recycler:** Craft recycler. Feed 64 Wafer Scrap / Chip Scrap / Assembly Scrap → 1 Silicon each. Verify 400 ticks @ 40 FE/t, no water, Fab Item Port works, hoppers ignored.
11. **Config edits:** Change a recipe/timing/yield via JSON + `/reload`. Verify JEI updates, in-progress lots retain frozen budgets, new lots use new values. Check recovery loops.

<a id="known-issues"></a>
## Known issues

**Open (current):**
- Reflow controls/metrics clustering issue — do not adjust targets or present unverified workarounds.
- Default recovery rates below 100%; config edits may create loops — verify after changes.
- JEI catalog oversized/unsupported data fails closed to empty pages.

**Fixed (awaiting owner confirmation / already resolved in current build):**
- Game crash on shutdown — fixed.
- Creative inventory search/grouping — fixed.
- Panel placement/rendering — fixed.
- FOUP tooltip stage/preparation hints — fixed.
- Scanner ordinary water refusal — fixed.
- Source pulse calibration persistence — fixed.
- Cleanroom controller enclosed detection — fixed.
- High binning route guards (T3→T4, T4→T5, T5→T6, T6→T7) — implemented per spec.
- Central vacuum plant — implemented.
- Coating Track resist tier commit / mask family commit — implemented.
- Wet-Clean productive (retains wafers) vs FOUP Cleaner destructive — implemented.
- T7 hard cleanroom enclosure for all wafer-processing stages — implemented.
- G3 Deposition/Etch shared quality budget, room yield finalized once after Wet-Clean — implemented.
- G3 Reticle Writer non-clean fewer uses, no downstream yield penalty — implemented.

**Deferred (not in M1 scope):**
- NAND assembly.
- T8 progression.
- Recycler extra outputs (beyond 3 scrap types → Silicon).
- EL (Encoded Logistics) hardware integration beyond Arcforge conduit verification.
- Ponder integration.
- Airlocks / arbitrary room shapes.
- Equipment bay model redesign.
- Reflow oven model/layout redesign beyond 3×3×2 calibrated unit.
- Global recipe rewrite to require assembled components.

---

**Reporting problems:** Include tier, machine, visible status text, FOUP tooltip, exact inputs, power/fluid supply, and screenshots of the multiblock structure and GUI. Reference [issues/README.md](issues/README.md) for the issue template.
