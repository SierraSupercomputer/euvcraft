# EUVCRAFT playtest progression guide — T1 to T5

Follow this route to test current progression. Use Survival to check crafting costs, or Creative to speed up construction. Keep chips from each generation: later recipes consume earlier chips. Exact crafting ingredients are in the [recipe reference](recipes.md); quantities below assume bundled defaults.

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
| Deposition/Etch Chamber | 3×3×5 | Advanced Operating Panel above the lower controller; lower cabinet is status-only. |
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

**Pass:** Obtain T1 Logic Chips and reuse the carrier. Some scrap is normal. See the [early progression reference](progression-m1.md).

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

**Pass:** Produce T3 Logic, Memory, Compute and NAND chips. Compute currently has one core. See the [T3 walkthrough](manual-walkthrough-t3.md).

## Tier 4 — preparation chamber and calibrated scanner

Build **Deposition/Etch (3×3×5)**, **Lithography Scanner (3×3×5)** and **Advanced Test & Package (2×2×3)** with Advanced Fab Casings/Power Ports. The scanner replaces the small Process Cell for T4 processing.

The scanner's **middle layer** needs one each of the physical **Exposure, Optics, Stage, Filtration and Metrology Modules**, plus a **Calibration Panel**. They can occupy any valid middle-layer positions; keep calibration accessible. Fill unused cells with casings/ports. Top/bottom layers form the frame, with the controller and operating panel arranged as in the [scanner walkthrough](manual-walkthrough-scanner.md). Physical modules replace the earlier cabinet equipment items.

Run this route:

1. Pure boule → fresh pure-wafer FOUP.
2. Deposition/Etch **upper panel:** FOUP + four Silicon → **G2-prepared** pure-wafer FOUP.
3. E-beam writer: write the desired T4 mask.
4. Scanner Calibration Panel: adjust beam X, Y and Focus. Reach at least the default quality threshold of 50, then **Accept alignment**. Better alignment improves yield; changing modules invalidates calibration.
5. Scanner upper panel: prepared FOUP + T4 mask + T4 resist.
6. Advanced Test & Package upper panel: completed FOUP + Chip Packages. Leave **High Binning off** for normal production.

**Pass:** Produce all four T4 chip families through the full scanner route. Older T4 walkthroughs predate the scanner; follow this route for current progression.

**Optional high binning:** The advanced packager can turn completed **T3 Logic/Compute lots into T4**, or **T4 Logic/Compute lots into T5**. Enable its toggle before admission. The default success threshold is only 0.15 times the source lot's effective yield: expect heavy losses. It reuses existing wafer rolls. Memory/NAND cannot be high-binned; logic stops at T5. This shortcut does not test the next tier's full machinery.

## Tier 5 — ultrapure water and diagnostic tuning

Keep the pure-boule, G2 preparation and advanced packaging chain.

1. Build a **Utility Plant**, 2×2×3, with Advanced Fab Casings/Power Ports, exactly one **Utility Water Inlet** and one **Utility Pure Outlet**. Supply Utility Filter media and ordinary water.
2. Pipe its **Ultrapure Water (UPW)** through Arcforge-compatible transport into a **Scanner Water Inlet**, replacing a spare scanner casing. The scanner rejects ordinary water.
3. Replace the scanner's Optics Module with an **Immersion Module**. Add a **Scanner Diagnostic Panel** in a spare middle-layer position. Keep other modules and the Calibration Panel.
4. Accept beam alignment again. In the Diagnostic Panel, match the target curve using **y = Ax² + bx + c**: A controls curvature, b tilt and c vertical offset. Target coefficients are hidden.
5. Insert **Burn Paper**. Each **Plot** attempt consumes one paper; editing coefficients is free. Reach the default score threshold of 50, then **Accept Fit**. Both accepted calibration qualities affect wafer yield.
6. Run the prepared FOUP with T5 mask/resist, then advanced packaging.

One default utility batch makes **8,000 mB UPW**, while a default T5 lot needs **8,400 mB**. Make two batches or replenish during processing; maintain FE throughout.

**Pass:** Produce all four T5 chip families with both calibrations accepted. See the [T5 walkthrough](manual-walkthrough-t5.md).

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

Outputs retain chip tier. Assembly has no calibration requirement or extra yield roll. NAND assembly and the reflow successor are future content.

**FOUP Cleaner:** Supply FE and ordinary water, insert an unwanted FOUP and choose **Discard & Clean**. This destroys **all wafers** in that carrier. It returns the same empty FOUP and **one Wafer Scrap per wafer**; it does not selectively remove failures. Default water cost: 1,000 mB. No Chip Packages or UPW needed. Wafer Scrap differs from packaging's Chip Scrap; recycling is not implemented. See the [cleaner walkthrough](manual-walkthrough-foup-cleaner.md).

## Troubleshooting and reporting

- **Unformed:** Check dimensions, filled cells, matching casings/ports, controller facing and required panel directly above it.
- **No input slots:** Deposition/Etch and advanced packaging use the upper panel; lower cabinets are status-only. Purifier, mask writer and utility plant use their controller.
- **Input refused:** Check FOUP substrate/preparation, matching mask/resist tier, equipment and accepted calibration. T4+ needs the large scanner and advanced packager.
- **Paused:** Check FE, resist, UPW, filter media and output space. Same-resist top-ups are allowed; swapping resist during a run is not.
- **Low yield:** Check calibration and T5 room cleanliness; turn off High Binning for normal production. Small batches can vary.

Report tier/machine, visible status, FOUP tooltip, inputs, power/fluid supply and screenshots of the structure and GUI. The endpoint is the full T5 route; T6+ consequences, selectable compute cores and reflow are not implemented yet.
