# EUVCRAFT playtest progression guide — T1 to T8

Current guide for the **0.9.0 Alpha release**. This document replaces the separate historical walkthroughs. Back up worlds and finish active lots before updating. Older T4+ processed lots may lack paid-stage evidence: use fresh lots or discard and clean them. Existing worlds and panel geometry remain usable.

Follow this route to test current progression. Use Survival to check crafting costs, or Creative to speed up construction. Keep chips from each generation: later recipes consume earlier chips. Exact crafting ingredients are in the [recipe reference](recipes.md); quantities below assume bundled defaults.

## Contents

- [Before starting](#basics)
- [Build reference](#build-reference)
- [Tier 1 — first logic chips](#tier-1)
- [Tier 2 — stepper and memory](#tier-2)
- [Tier 3 — pure substrates and selectable masks](#tier-3)
- [Tier 4 — preparation chamber and calibrated scanner](#tier-4)
- [Tier 5 — ultrapure water and diagnostic tuning](#tier-5)
- [Tier 6](#tier-6)
- [Tier 7](#tier-7)
- [Tier 8 — High-NA EUV](#tier-8)
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

<a id="build-reference"></a>
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
| G3 Deposition Chamber / G3 Etch Cell, T7 | 5×5×7 each | G3 Operating Panel above the cabinet. Tune compute and memory etch modules in the lower cabinet. |
| Lithography Scanner | 3×3×5 | Scanner Operating Panel above cabinet; separate Calibration and Diagnostic Panels. |
| Advanced Test & Package / High-Bin Test & Package | 2×2×3 each | Advanced Operating Panel above the matching lower controller. Shared Advanced Casings and Power Ports. |
| Utility Plant | 2×2×3 | Controller contains inputs; no upper panel. |
| Pick And Place | 2×2×2 | Controller; no upper panel. |
| FOUP Cleaner | Single block | Machine itself |

Choose any bottom-front controller column, facing outward; required upper panel goes directly above. Use 1–2 matching power ports for early/advanced machines and 1–4 for G3/EUV/hydrogen/vacuum machines. Fill remaining cells with casing except required modules/inlets below. Hold Shift over a controller for its build requirements. Put calibration interfaces on accessible faces. Physical modules remain flexible within allowed middle layers; the proposed bottom-only equipment bays are not implemented.

| Additional machine | Size | Required frame, interfaces and modules |
| --- | --- | --- |
| Zone Refiner | 3×3×5 | Zone Casing, 1–2 Zone Power Ports, one Zone Water Inlet, upper Zone Operating Panel. |
| Reflow Oven | 3×3×2 | Reflow Casing, 1–2 power ports, upper Operating Panel, one Calibration Panel in another middle-layer cell. |
| G3 Reticle Writer | 3×3×5 | G3 Casing, 1–4 power ports, upper G3 Operating Panel; one Writing Module and Metrology Module in middle layer. |
| G3 Deposition Chamber / Etch Cell | 5×5×7 | G3 Casing, 1–4 power ports, upper G3 Operating Panel; one Deposition Module, Rinse Module, Vacuum Module and UPW Inlet in the three middle layers. |
| EUV Scanner | 5×5×7 | EUV Scanner Casing, 1–4 matching power ports, upper Operating Panel; one Exposure, Optics, Stage, Filtration, Metrology and Vacuum Module, Calibration Panel, Diagnostic Panel and UPW Inlet in the three middle layers. Exposure must be accessible for beamline. |
| EUV Light Source | 3×3×5 | Source Casing, 1–4 matching power ports, upper Operating Panel; one Tin Inlet, Hydrogen Inlet, Vacuum Module, Pulse Control Module and Source Calibration Panel in middle layer. |
| Hydrogen Purifier | 2×2×3 | Hydrogen Casing, 1–4 matching power ports, upper Operating Panel, one Gas Inlet and Gas Outlet. |
| Central Vacuum Plant | 3×3×5 | Vacuum Casing, 1–4 matching power ports, upper Operating Panel, one Service Outlet and Service Conduit. |
| Scrap Recycler | Single block | Direct FE and input/output GUI; no multiblock or water. |

G2 deposition/etch and advanced packaging use Advanced Casing/Power Ports. Utility Plant additionally needs exactly one Utility Water Inlet and Pure Outlet. Coater optionally takes a Fab UPW Inlet for repair; Wet-Clean requires one. These inlets can replace spare casings. Scanner modules and panels belong in the middle layer; its UPW inlet may occupy a spare frame position. With one power port, T4/T5/T6 scanner uses 36/34/33 casings, Zone Refiner 41 and Reflow Oven 14.

Use Java 25, Minecraft 26.3, NeoForge 26.3.0.99, Arcforge 2.8.1+26.3 and Encoded Logistics 1.5.0+26.3. Do not install the developer Arcforge API JAR. Matching client/server EUVCRAFT builds are required. Creative handling now preserves intact FOUPs; already stripped old processed data cannot be reconstructed. Substrate grade is separate from G2/G3 preparation. Fresh/deposited FOUPs can have multiple destinations; coating commits tier and the mask commits family/cores.

<a id="tier-1"></a>
## Tier 1 — first logic chips

1. **Crystal Puller:** Four Silicon → ordinary Silicon Boule.
2. **Wafer Slicer:** Boule + empty FOUP Mk1 → wafer FOUP. Default batch: 25 wafers, limited by carrier capacity.
3. **Pattern Generator:** Blank Mask + Copper Ingot → T1 Logic Mask.
4. Build the **Process Cell**. Install five items in its lower cabinet: **Mercury Lamp, Contact Plate, Manual Aligner, Basic Air Filter, Optical Microscope**.
5. Upper Operating Panel: wafer FOUP + T1 Logic Mask + T1 Photoresist. It starts when requirements are met. Resist is consumed per layer.
6. **Test & Package:** Completed FOUP + Chip Packages + FE → chips or Chip Scrap. Successful wafers consume packages; failed wafers do not. Collect the reusable empty FOUP.

**Pass:** Obtain T1 Logic Chips and reuse the carrier. Some scrap is normal.

<a id="tier-2"></a>
## Tier 2 — stepper and memory

Keep the Process Cell. Replace its five equipment items with **I-line Lamp, Reduction Lens, Stepper Stage, Fine Air Filter, Overlay Scope**.

Use ordinary wafers. The Pattern Generator makes **T2 Logic Masks from Blank Mask + Iron Ingot**, and **T2 Memory Masks from Blank Mask + Redstone**. Use T2 Photoresist; this tier has more layers, so stock extra resist. Package in the original Test & Package.

**Pass:** Produce T2 Logic and Memory Chips. Save logic chips for the next machines.

<a id="tier-3"></a>
## Tier 3 — pure substrates and selectable masks

Build a **Boule Purifier** and **E-beam Mask Writer**, each 2×2×3 with Support Casings/Power Ports. Their controllers contain the inputs.

1. Purifier: ordinary boule + ordinary water → **Pure Silicon Boule**. Use buckets initially; an optional Support Water Inlet replaces a spare casing for piped input.
2. Slice the pure boule. Confirm the FOUP tooltip identifies pure wafers.
3. E-beam writer: supply Blank Mask, Redstone and substantial FE. Select the desired T3 design, then **Write mask**.
4. Upgrade the Process Cell's five equipment items: **KrF Excimer Laser, DUV Projection Lens, Precision Stepper Stage, HEPA Filter, Laser Interferometer**.
5. Run pure wafers with T3 mask/resist, then use the original Test & Package.

**Pass:** Produce T3 Logic, Memory, Compute and NAND chips. Compute currently has one core.

<a id="tier-4"></a>
## Tier 4 — preparation chamber and calibrated scanner

Build separate **Deposition Chamber and Etch Cell (3×3×5 each)**, **Lithography Scanner (3×3×5)** and **Advanced Test & Package (2×2×3)** with Advanced Fab Casings/Power Ports. The scanner replaces the small Process Cell for T4 processing.

The scanner's **middle layer** needs one each of the physical **Exposure, Optics, Stage, Filtration and Metrology Modules**, plus a **Calibration Panel**. They can occupy any valid middle-layer positions; keep calibration accessible. Fill unused cells with casings/ports. Top/bottom layers form the frame, with the controller and operating panel arranged with the controller in the bottom front row and the operating panel directly above it. Physical modules replace the earlier cabinet equipment items.

Run this route:

1. Pure boule → fresh pure-wafer FOUP.
2. Deposition **upper panel:** FOUP + two Silicon → **G2-prepared, Film deposited** pure-wafer FOUP.
3. E-beam writer: write the desired T4 mask.
4. Scanner Calibration Panel: adjust beam X, Y and Focus. Reach at least the default quality threshold of 50, then **Accept alignment**. Better alignment improves yield; changing modules invalidates calibration.
5. Scanner upper panel: prepared FOUP + T4 mask + T4 resist.
6. Etch Cell upper panel: exposed FOUP + one Etchant Charge → **Etched** FOUP. Then Advanced Test & Package upper panel: etched FOUP + Chip Packages.

**Pass:** Produce all four T4 chip families through the full scanner route. Follow this route for current progression.

**Optional high binning:** Route eligible finished FOUPs and Chip Packages to a dedicated **High-Bin Test & Package** to turn completed **T3 Logic/Compute lots into T4**, or **T4 Logic/Compute lots into T5**. Craft its controller from an Advanced Test & Package, two T3 Logic Chips, one T3 Compute Chip, four Gold Ingots and one Redstone Block. It uses the same Advanced frame and upper panel. The original Advanced Test & Package completes any high-bin queue already accepted before the update and processes new lots at their original tier. The default packaging time/energy doubles and the success threshold is only 0.15 times the source lot's effective yield: expect heavy losses. It reuses existing wafer rolls. Memory/NAND cannot be high-binned; logic stops at T5. This shortcut does not test the next tier's full machinery. T3→T4 follows the T3 source route; T4→T5 follows T4 etching without T5 coating/cleaning. T5→T6 compute requires the full T5 route; T6→T7 compute requires the full T6 route and T7 enclosure at packaging. There is no T7→T8 binning.

<a id="tier-5"></a>
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

<a id="tier-6"></a>
## Tier 6 — electronic-grade wafers and multi-patterning

1. Purify a **Silicon Boule** in the **Boule Purifier** with normal water first. Put the resulting **Pure Silicon Boule** in the left input slot of the upper **Zone Operating Panel**. Supply FE and pipe **ultrapure water** into the Zone Refiner’s water inlet. The default batch makes one **Electronic Grade Boule** in 600 paid ticks at 480 FE/t and uses 4,000 mB of UPW. If the input refuses a Pure Silicon Boule, check whether the panel shows an active batch. The slot stays locked while a batch is paused.
2. Slice it into a FOUP. Its electronic-grade substrate is distinct from preparation: the T6 route correctly uses G2 preparation.
3. Retrofit the T5 scanner with one Multi-Patterning Module in a spare middle-layer cell. Keep Immersion, water inlet and both calibration interfaces. Recalibrate.
4. E-beam writer offers T6 compute, memory and NAND. Compute cores default to 1, 2, 4, 8, 12 or 24; select before writing. Masks retain this choice; memory/NAND have no core selection.

**Route:** Electronic-grade boule → slicer → G2 deposition → Coating Track (**70 T6 resist**) → multi-patterning scanner → G2 Etch (**one Etchant Charge**) → Wet-Clean Station → advanced packaging → reflow CPU/DIMM.

The scanner performs two internal exposure passes per layer; the carrier does not loop backward through the fab. Leave scanner resist empty. Refill the same-grade resist upstream beyond a stack of64. Core count must survive mask exposure, packaging and assembly, and different core counts must stay in distinct stacks.

T6 rooms remain optional with yield penalties outside. Scanner/reflow count dirty **paid work ticks**: default threshold 1,200 ticks outside or above 2,500 PPM. Current batch retains admitted calibration; next batch requires recalibration. Scanner needs beam and diagnostic acceptance with renewed targets; reflow needs thermal acceptance. Idle/unpaid pause does not spend the grace period. Recalibration restores it even outside.

Pass: all three chip families, at least two compute-core selections, CPU/DIMM assembly and dirty-work recalibration. Logic ends at T5. See [assembly](#assembly) for reflow.

<a id="tier-7"></a>
## Tier 7 — EUV, Fab Hydrogen and vacuum

Use electronic-grade wafers. Build the new G3 reticle writer, **separate G3 deposition and G3 etch chambers**, EUV scanner and independent source using the build table. Every T7 wafer-processing structure must fit strictly inside one enclosed, powered cleanroom. The source and utilities may remain outside. G3 reticle production outside a room loses uses, without a downstream yield factor.

### Supply utilities and reticles

Hydrogen Purifier accepts Arcforge electrolyzer hydrogen through **pressurized gas conduits**. Upper panel takes Utility Filters. Default batch reserves 8,000 mB raw hydrogen +1 filter, pays 400 ticks at600 FE/t and outputs 6,000 mB Fab Hydrogen. Extract via Gas Outlet into compatible gas transport/storage.

G3 writer upper panel selects T7 compute/memory/NAND and compute cores. Starting cost:1 Blank Mask +64 redstone +3,600 paid ticks at8,000 FE/t. Selection locks while working; lower cabinet is status-only.

### Couple and calibrate source

Supply source FE, Fab Hydrogen and Tin Feedstock. Starter recipe makes 4 feedstock from 4 copper,4 redstone and 1 iron; other tagged tin can satisfy c:ingots/tin. Pump down source/scanner vacuum. Source pulse calibration can be performed independently before a scanner is connected.

Connect the scanner Exposure Module by **1–16 straight, loaded Beamline blocks** to a **plain source casing face**, not its inlet/module. Beamline supports X/Y/Z alignment and seals a cleanroom penetration. Avoid multiple source candidates. One source serves one scanner installation at a time; linked coordinates are distinct from readiness/reservation. Accept scanner beam/diagnostics and source pulse calibration before processing.

**Route:** Electronic-grade boule → slicer → G3 deposition → Coating Track (**80 T7 resist**) → EUV scanner → G3 Etch (**one Etchant Charge**) → Wet-Clean → advanced packaging → reflow.

For compute or memory, replace the G3 Etch Cell’s standard Deposition Module with the matching G3 Compute or Memory Etch Module in any middle-layer module position. Keep the rinse and vacuum modules. The exposed FOUP tooltip names the required setup. Keep the standard module for NAND.

Open the lower cabinet while the cell is idle. Use the minus and plus controls to bring depth error (compute) or center-to-edge error (memory) toward zero, then accept tuning. Compute tolerances tighten at higher core counts. Better memory tuning narrows the wafer-to-wafer yield variation. Swapping modules requires new tuning. Load the wafer FOUP at the upper panel and the Etchant Charge at the lower cabinet. T6-to-T7 compute high binning uses the dedicated High-Bin Test & Package inside a powered cleanroom and bypasses the T7 compute etch branch.

Finish active T7 lots before updating and start fresh lots afterward. Finished chips and assemblies remain usable.

Source takes no FOUP. Scanner takes coated G3-prepared FOUP + G3 T7 reticle, **no physical resist**. Eighty layers require ongoing FE/UPW/Fab Hydrogen/tin credit/pump service. Deposition and etch share the preparation quality budget; rinse is paid at productive cleaning, with room yield finalized once. Do not apply old combined-machine water/energy totals a second time. Shortages pause the admitted lot without rerolling calibration or source binding.

### Breach and repair

T7 containment loss, including controller-power loss during an active FE-paused job, contaminates wafers and leaves affected equipment needing repair. Supply shortages alone do not trigger this. Seal the room and dispose of contaminated wafers in FOUP Cleaner; repairs cannot restore them.

Starting repair bill: **2 Utility Filters +16 redstone +8,000 mB UPW +200,000 FE**, editable per machine. G3/scanner cabinets hold repair materials. Coater/Wet-Clean repair uses upper input 1 filters and input 2 redstone plus UPW/FE, then Repair. Coater may need optional UPW inlet installed. Scanner requires fresh beam/diagnostics afterward; targets change. Machine contamination survives save/controller pickup.

### Optional central vacuum

Build Central Vacuum Plant after local pumps work. Connect Service Outlet via Service Conduits to accessible Vacuum Modules. **Cleanroom Vacuum Port** seals wall crossings; ordinary service conduit does not. Each chamber retains pressure/reserve. Machine isolation falls back to its local pump. Default plant supplies up to20,000 service units/t at350 FE/t under load, versus three local pumps at200 FE/t each. Check demand/client status and disconnect a branch without disturbing others; networks do not load distant chunks.

Pass: T7 compute/memory/NAND, retained CPU cores, calibrated source, shortage/reload recovery, and a deliberate test-world breach → disposal → repair → recalibration cycle.

<a id="tier-8"></a>
## Tier 8 — High-NA EUV

Use electronic-grade wafers, G3 deposition, and the T7 cleanroom and utility network. There is no T7→T8 high-bin shortcut. All T8 wafer stations need a powered, enclosed cleanroom.

1. **Planarize** deposited wafers in a **3×3×5 Chemical Mechanical Planarizer**. Use CMP Slurry and Polishing Pads in the lower cabinet and the FOUP in the upper panel. Install a Memory Uniformity Head for memory lots, or a standard G3 Deposition Module for compute and NAND. This commits the T8 route and the memory or standard program. Before running a production FOUP, load a separate G3-deposited electronic-grade FOUP in the cabinet’s Sample slot. Adjust pressure and platen speed, then use **Test polish** to see removal and center-to-edge errors. Each test spends one wafer and 1,500 FE at default settings. Tune both errors toward zero and accept the calibration. The memory head has a tighter edge tolerance. The remaining wafers and empty carrier are reusable. Replacing a head clears calibration, and replenishing slurry or pads leaves the accepted settings in place.
2. **Coat** with Photoresist T8. A default full lot takes 90 resist. The G3 Reticle Writer's Tier button switches between T7 and T8. A T8 write takes two Blank Masks and a full stack of Redstone by default.
3. **Expose** in a **5×5×7 High-NA EUV Scanner**. Use the EUV scanner structure, replacing its controller and optics with the High-NA versions. It shares the T7 light source, beamline and calibration workflow. Load the compatible T8 reticle and coated FOUP in the upper panel.
4. **Etch** in the T7 G3 Etch Cell with the same family-specific modules and one Etchant Charge.
5. **Implant and activate** in a **5×3×7 Ion Implant and Activation Cell**. Compute requires the Compute Precision-Dose Head. Memory and NAND use the standard G3 Deposition Module. Adjust dose error toward zero and accept tuning in the lower cabinet, load Dopant Charges there, then insert the FOUP in the upper panel.
6. **Wet-clean**, then send the FOUP and Chip Packages to Advanced Test & Package. Compute and memory chips can go to the calibrated Reflow Oven for CPUs and DIMMs.

The planarizer and implant cell both use G3 Casings, 1–4 G3 Power Ports, a G3 Operating Panel directly above the lower controller, one G3 Water Inlet, and a rinse module, vacuum module and process head in the middle layer. They use piped UPW. Their head can go in any free middle-layer module position.

**God Chip challenge:** A cleaned T8 compute FOUP can instead go to the dedicated High-Bin Test & Package inside a powered cleanroom. It uses Chip Packages and the original wafer rolls with a much stricter, editable yield threshold. A passing wafer yields one God Chip, and a failure yields scrap. The trophy unlocks the God Chip Challenge advancement on pickup and has no production recipe.

<a id="calibration"></a>
## Calibration reference

**Beam alignment (T4+):** Scanner Calibration Panel adjusts X, Y, Focus. Default threshold: quality ≥ 50. Better alignment improves yield. Changing any physical module invalidates calibration.

**Diagnostic fitting (T5+):** Scanner Diagnostic Panel. Target curve: hidden **y = Ax² + bx + c** over x ∈ [−2, 2]. Adjust A (curvature), b (tilt), c (vertical offset) in hundredths. Each **Plot** consumes one **Burn Paper**; editing is free. Accept at score ≥ 50 (default). Both beam and diagnostic factors multiply into lot yield: each factor = 0.5 + 0.5 × quality/100. Calibration persists through reload; module replacement requires fresh calibration.

**Multi-patterning (T6):** Retains T5 beam + diagnostic. Scanner gains one **Multi-Patterning Module** in a middle-layer casing slot. Two exposure passes per layer; core count selected at mask write (1, 2, 4, 8, 12, 24).

**EUV source pulse (T7):** Separate **Source Calibration Panel** on the EUV Light Source. Three knobs: **Droplet Phase**, **Main-pulse Delay**, **Pulse Amplitude**. Each **Test Pulse** costs **12,000 FE + 50 mB Fab H₂ + 25 tin credits** (1 feedstock = 1,000 credits). Accept at quality ≥ 60 (default). Source factor: 0.85 at Q60 → 1.00 at Q100. Survives reload; replacing Pulse Control Module or rebuilding source requires fresh calibration.

**T6 contamination:** invalidation renews beam and diagnostic targets once and persists them. Old solutions cannot simply be reaccepted. No recurring per-mask calibration rule is added. Active lots keep frozen qualities; controls lock during operation.

<a id="cleanrooms"></a>
## Cleanrooms

Cleanrooms are **hollow sealed cuboids** built from cleanroom floor, wall, and window blocks (including ceiling and corners). Put one **Cleanroom Controller** and one or more **Cleanroom Filters** in the shell. Use **Cleanroom Power Port**, **Cleanroom Fluid Port**, **Cleanroom Item Port** for through-wall connections.

- **Room yield factor:** Outside = 0.5× (T5+). Inside, filtration reduces PPM and improves the yield factor toward 1.0×. Controller reports **enclosed** when sealed.
- **Power:** Controller and each filter need FE. Insert **Cleanroom Filter Medium** into filters.
- **Filtration time:** Chunks must stay loaded. Larger rooms need more filters. Doors leak when open (redstone-controlled).
- **Gowns:** Four-piece set reduces occupant contamination; does not eliminate it.
- **T5/T6:** Outside operation is permitted with yield penalties. T6 additionally invalidates calibration after sustained dirty paid work, as described above. T7-style wafer scrapping/equipment repair does not apply to T5/T6.
- **T7:** **Hard requirement.** All T7 wafer-processing multiblocks (G3 Deposition, EUV Scanner, G3 Etch, Wet-Clean, Advanced Packager, Reflow) must be strictly inside an enclosed powered room. Coater and cleaner only hard-require enclosure for T7 lots.
- **Breach during T7 run:** Active wafers become **Contaminated FOUP**. Machine contamination persists through saves and controller pickup. Discard via FOUP Cleaner (returns Wafer Scrap). Repair via lower cabinet: 2 Utility Filters + 16 Redstone + 8,000 mB UPW + 200,000 FE (configurable). Repairs never restore contaminated wafers. Scanner also requires fresh beam + diagnostic calibration (targets change).
- **Central Vacuum (exists):** 3×3×5 Central Vacuum Plant with Vacuum Casing, Service Outlet, Service Conduits. Connect to machine Vacuum Modules. Cleanroom Vacuum Port seals through walls. Plant: up to 20,000 service units/t at 350 FE/t vs. 200 FE/t per local pump. Isolated chambers fall back to local pump.

### Filter maintenance from the controller

Open the Cleanroom Controller and select **Filters**. The tab lists the room’s discovered filters with **absolute X/Y/Z coordinates**, status, stored/capacity FE, spare Filter Medium items and the remaining life of medium already in use (seconds, rounded up). A filter can have no spare items and still be filtering with its paid medium.

Filters needing attention appear first; coordinate order breaks ties. Five rows fit per page. Scroll over the list with the mouse wheel or use the arrows. The summary counts the entire list, including filters on other pages. Values refresh about once per second while the tab is open. Return to **Room** for enclosure/PPM and the player inventory; a filter’s own GUI is unchanged.

Previously discovered filters in unloaded chunks show **Chunk unavailable** and unknown resource values. Reporting never loads chunks; on a room rescan, removed filters disappear. The tab is read-only: refill medium and power at the filter or through its normal automation. Use matching updated EUVCRAFT builds on client and server. No world conversion is needed.

<a id="automation"></a>
## Automation and item/fluid ports

**Fab Item Port** (Advanced) / **G3 Fab Item Port** / **EUV Fab Item Port**: replace any casing in a formed multiblock. Exposes machine inventory on all faces. Works with Arcforge item conduits (configure pull/push at endpoints). Queued/active wafers and installed equipment are protected; finished outputs and empty packaging return FOUPs are extractable. Power still enters via power ports. Breaking the shell stops transfers.

**Fluid/gas:** Arcforge-compatible pipes/conduits. UPW from Utility Plant → Scanner/Coater/Wet-Clean inlets. Fab Hydrogen via pressurized gas conduits from Hydrogen Purifier. Ordinary water refused by UPW inlets.

**Transport:** Dedicated item capabilities do not promise generic vanilla hopper support. Recycler has all-face guarded input/output automation; queued/active input cannot be extracted. Cleanroom power/fluid/item ports bridge real Arcforge networks across walls: configure the axis and both endpoint sides. Fluid ports also support compatible gas transfer with gas conduits; vacuum service uses its separate sealed port.

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
| T6 CPU / T7 CPU | 1 same-tier Compute Chip + 1 CPU Blank | T6:240 ticks; T7:360 ticks, both 240 FE/t |
| T6 DIMM / T7 DIMM | 4 same-tier Memory Chips + 1 DIMM Blank | T6:240 ticks; T7:360 ticks, both 240 FE/t |

Successful CPU preserves core count. Failed assembly consumes inputs → 1 Assembly Scrap. No T6/T7 microcontroller or NAND assembly. T1–T5 Pick And Place remains available.

**Open report:** [Reflow dwell/cooling clarity](issues/T6_REFLOW_DWELL_COOLING_CLARITY.md) is unreproduced and awaits exact settings, measured values and configured bands. No thermal mechanics changed in this release.

<a id="recycling"></a>
## Recycling (new single-block Recycler)

**Recycler** — single block, craftable: **6 Iron + 2 Redstone + Furnace**. GUI: input slot, output slot, FE buffer. Direct guarded item capability on all faces for Arcforge conduit automation; no separate item port is needed. Vanilla hopper support is not provided.

**Recipes** (three editable `machine_recipes` JSON files):

- 64 **Wafer Scrap** → 1 Silicon
- 64 **Chip Scrap** → 1 Silicon
- 64 **Assembly Scrap** → 1 Silicon

**Process:** 400 ticks @ 40 FE/t = 16,000 FE. Capacity 40,000 FE, max receive 2,000 FE/insertion. A partial batch waits without payment; blocked output or insufficient FE pauses paid progress, including across reload. Only finished output is extractable; inputs lock while active. No water. Default recovery **below 100%**; check loops after config edits. Does not accept live FOUPs, chips, or masks.

**FOUP Cleaner** (existing): FE + ordinary water → **Discard & Clean** destroys all wafers in carrier, returns empty FOUP + 1 Wafer Scrap per wafer (default 1,000 mB water). Does not selectively remove failures. Wafer Scrap ≠ Chip Scrap. Loose scrap can then go to Recycler.

<a id="jei"></a>
## JEI integration (optional)

Requires **JEI 31.14.0.72+** for Minecraft 26.3 / NeoForge. Install JEI and its required dependencies (including matching MezzConfig) on the client; dedicated server works without JEI. Matching EUVCRAFT client/server builds provide the process catalog.

- **Station = actual controller block** (not casings/panels). Multiblocks accessed via operating panel still list controller.
- **Categories:** slicing, preparation, coating, scanning, etching, productive cleaning, disposal, packaging/high binning, utility services, vacuum/light-source services.
- **Fluids/gases:** real registered fluid ingredients (look up fluid entry, not bucket placeholder).
- **Masks:** spend **1 use per completed lot**. Design lookup ignores remaining uses and core selections. Writing still requires GUI selection.
- **FOUP icons:** generic stage-labelled carrier placeholders, not finished lots. Hover for stage/tier/substrate. Multiple compatible pages shown; private lot data never exposed.
- **Notes:** abbreviated; hover details for full explanation. FE/t and ticks reflect current server new-job settings. Variable/phase-specific costs in notes. Admitted lots retain frozen budgets (may differ after config edit).
- **Yields:** packaging/reflow output counts illustrative. High binning follows source tier's completed route; T7 targets additionally require enclosure. Wet-Clean retains wafers; FOUP Cleaner explicitly destroys.
- **Photomasks:** Search `mask` in JEI. Hover a Photomask entry to see its die family and tier. Press `R` to find the writer recipe or `U` to find the lithography or scanner step that uses it. EUV reflective reticles use the same Photomask item.
- **Server sync:** Catalog sent on login/datapack reload/settings change. JEI refreshes changed pages; clears previous server's catalog on disconnect. Bundled: 133 pages across 30 controller stations. Datapacks/settings can change this. Oversized/unsupported data fails closed to empty pages.
- **Verification:** Start with matching builds + supported JEI. Check all controller stations have processes. Find Pure Silicon Boule, patterned mask, UPW, Fab Hydrogen via JEI. Follow FOUPs through T4–T8. Inspect normal/high-bin packaging and reflow failure outputs. Change recipe via datapack + `/reload`; check updates. Reconnect to second server; first server's overrides gone. Remove JEI → EUVCRAFT still starts and machines operate. No Ponder or auto-crafting.

<a id="troubleshooting"></a>
## Troubleshooting

| Symptom | Checks |
|---|---|
| **Unformed** | Dimensions, all cells filled, matching casings/ports, controller facing outward, required panel directly above controller. |
| **No input slots** | Deposition/Etch/Advanced Packager use upper panel; lower cabinet status-only. Purifier, mask writer, utility plant use controller. |
| **Input refused** | FOUP substrate/preparation, matching mask/resist tier, equipment formed, accepted calibration (T4+). T4+ needs large scanner + advanced packager. |
| **Paused** | FE, resist, UPW, filter media, output space. Same-resist top-ups allowed; swapping resist mid-run not allowed. |
| **Low yield** | Check calibration quality, T5+ room cleanliness, and whether the lot went to the dedicated High-Bin station. Small batches vary. |
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
7. **Breach/recovery:** Pause T7 run → break cleanroom wall → verify Contaminated FOUP → FOUP Cleaner → repair machine → fresh calibration → run a fresh lot.
8. **Automation:** Two successive FOUPs per tier via Fab Item Ports + Arcforge conduits. Verify active wafers protected, empty carriers return.
9. **JEI:** With JEI 31.14.0.72+, verify all controller stations appear, fluid entries correct, FOUP icons generic, mask use = 1/lot, server sync on reload/reconnect.
10. **Recycler:** Craft recycler. Feed 64 Wafer Scrap / Chip Scrap / Assembly Scrap → 1 Silicon each. Verify 400 ticks @ 40 FE/t, no water, direct Arcforge item conduit transfer works, active input protected.
11. **Config edits:** Change a recipe/timing/yield via JSON + `/reload`. Verify JEI updates, in-progress lots retain frozen budgets, new lots use new values. Check recovery loops.

<a id="known-issues"></a>
## Known issues and deferred work

The [issue index](issues/README.md) distinguishes one unreproduced reflow report, seven implemented T6 repairs awaiting explicit owner confirmation, and completed fixes. It is not a list of currently proven defects. Cleanroom resource-port repairs were owner-confirmed.

NAND assembly, Encoded Logistics hardware/server progression, Ponder, airlocks/arbitrary rooms, equipment-bay remodeling and recovery of other raw materials remain separate work. Recheck material loops after changing wafer/chip/scrap/recovery settings.

Report version, tier/machine, exact status, FOUP tooltip, inputs, utility supply, settings/datapacks, structure/GUI screenshots and relevant latest.log/crash report. Private debug data is not required for normal play.
