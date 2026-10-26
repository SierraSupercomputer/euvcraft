# Tier 6 playtest walkthrough

Use the new `build/libs/lithmod-1.0.0.jar`. Start from a working T5 fab; earlier walkthroughs remain relevant. Dimensions below are width × height × depth, and boxes are solid rather than hollow. Existing worlds retain their block/item IDs. Installing the new scanner hardware intentionally requires calibration again.

## 1. Zone refining and G3 wafers

Build a 3×3×5 Zone Refiner using one Zone Refiner controller in any bottom-front column, its Zone Operating Panel directly above it, exactly one Zone Water Inlet, and one or two Zone Power Ports in any remaining positions. Fill the rest with Zone Casings: 41 with one port, 40 with two. All horizontal orientations work. The lower cabinet shows status; input/output slots are in the upper panel.

Pipe **ultrapure water** from the existing utility plant into the inlet using Arcforge-compatible plumbing. Ordinary water must be refused. Supply FE through a power port. Insert one Pure Silicon Boule. Defaults: 4,000 mB UPW, 600 powered ticks at 480 FE/t produce one Electronic-Grade Boule. Water and input are reserved once. Power loss or a missing casing should pause progress; repair/refill and resume. Save/reload halfway and check there is exactly one output and no second water charge.

Slice the Electronic-Grade Boule in the existing Wafer Slicer with an empty FOUP. Its tooltip should show electronic-grade/G3 substrate. Process that FOUP through the existing G2 deposition/etch machine; the tooltip should retain G3 substrate and show G2 preparation. No replacement etcher is required.

## 2. Retrofit the scanner

Keep the T5 3×3×5 immersion scanner, its five existing physical modules, upper operating panel, beam panel, diagnostic panel, water inlet and power ports. Replace one unused **middle-layer casing** with exactly one Scanner Multi-Patterning Module. Its recipe uses the new multi-patterning components made from T5 resources. This leaves 33 casings with one power port, 32 with two. Retain the immersion module and both calibration panels.

Reperform and accept beam alignment and a paid quadratic diagnostic fit. Connect continuous UPW and FE. The lower cabinet remains status-only; FOUP, mask and resist belong in the upper operating panel.

## 3. Masks and cores

The existing E-beam Mask Writer now offers T6 memory, compute and NAND; logic stops at T5. Select T6 compute and use the Cores control before starting. Default choices: 1, 2, 4, 8, 12, 24. Selection locks during production. Default T6 mask cost: one blank mask, 64 redstone, 2,400 ticks at 4,000 FE/t. Verify the result's tooltip records the selected core count. Memory/NAND do not offer a core choice.

Changing a selection later must not change an already produced mask. Keep two compute masks with different core counts for testing.

## 4. T6 wafer fabrication and packaging

Load a **G2-prepared electronic-grade FOUP**, matching T6 mask and T6 resist into the upgraded scanner. A merely pure FOUP must be refused. Defaults: 70 layers, two passes per layer, 100 ticks per pass, 480 FE/t and 2 mB UPW per paid tick. Provide at least 70 resist over the run: one stack is insufficient, so top up as needed. Default total: 14,000 powered ticks, 6,720,000 FE and 28,000 mB UPW. Continuous supply exceeds the scanner's stored buffers.

Test power/water pause and resume, resist top-up, save/reload and a temporarily missing casing. The admitted mask core count, calibration and pass timing must survive reload. Complete each family and package it in the existing G2 packager. T6 compute chip tooltips must preserve the chosen core count. Different core counts must stay separate in outputs rather than merging into one stack. Memory and NAND package normally.

T5 compute high binning can now yield T6 compute at a substantial editable yield penalty (default ×0.15, double time and energy). It retains the source's one core. Memory/NAND cannot high-bin; no T6 logic exists.

## 5. Reflow assembly and thermal tuning

Build a **3×3×2** solid Reflow Oven using one controller in any bottom-front column, its Operating Panel directly above, exactly one Reflow Calibration Panel in another middle-layer cell, and one or two Reflow Power Ports anywhere else. Fill the rest with Reflow Casings: 14 with one port, 13 with two. Use the upper panel for chips, blanks, finished assemblies and scrap. The separate calibration panel holds coupons and tuning controls.

Supply FE and insert Thermal Test Coupons into the calibration panel. Adjust Heater, Conveyor and Airflow. Gray is the free nominal preview; **Test** consumes one coupon and 2,000 FE and draws the actual cyan response. Amber indicates target guidance; numerical bounds give the exact requirements. Heater raises temperature/ramp; conveyor changes dwell and slightly affects peak; airflow changes cooling. Test, inspect the measured ramp/peak/dwell/cooling and adjust until every metric is in range and quality reaches at least 750/1000. Press **Accept**. Editing controls requires another paid test and acceptance. Identical tests must not reroll the equipment response. Controls lock during an assembly.

Default target bands: ramp 1–3, peak 230–245°C, dwell 30–70, cooling 1–4. Calibration quality freezes a fixed yield multiplier for each admitted assembly, combined with accumulated cleanroom exposure. Better tuning improves yield; it does not guarantee success outside a cleanroom.

| Result | Admitted inputs | Default work |
|---|---|---|
| T6 CPU | 1 T6 compute chip + 1 universal CPU Blank | 240 ticks, 240 FE/t |
| T6 DIMM | 4 T6 memory chips + 1 universal DIMM Blank | 240 ticks, 240 FE/t |

A successful CPU preserves its input chip's core count. A failed assembly consumes those admitted inputs and produces one Assembly Scrap instead. No T6 microcontroller or NAND assembly is added. Earlier pick-and-place recipes remain available for T1–T5.

Test power pause, a full output, save/reload mid-assembly and panel replacement. Reload must preserve the reserved inputs and single frozen success judgment. Breaking an unfinished oven returns reserved inputs, never an unearned product. Keep scrap: a future raw-material recycler is planned but is not implemented here.

## 6. Cleanroom pressure at T6

Cleanrooms remain optional, with the existing outside-room yield penalty. Both the T6 scanner and reflow oven count sustained dirty **paid processing ticks**. Default threshold: 1,200 ticks when outside a room or above 2,500 PPM. Idle time and unpaid pauses do not count. Crossing the threshold lets the current batch finish with its admitted calibration, but blocks the next batch until recalibration. Scanner recovery requires both beam and diagnostic acceptance; reflow recovery requires thermal acceptance. Recalibration restores a fresh grace period even outside a room.

Check this first outside, then in a filtered cleanroom below the threshold. Cleanroom yield is sampled during work. No T7 equipment damage, breach scrapping lifecycle or recycler is included. Costs, pass count, thresholds and thermal bands are editable in machine/recipe JSONs and the existing config.
