# Tier 4 production and owner walkthrough

Minecraft 26.3 / NeoForge 26.3.0.69-beta. This is a source-checked walkthrough; rendered client operation remains an owner check. The coordinator substantially corrected the free Nemotron draft, including platform, slots, costs and yield descriptions.

## Build the new machines

Use Advanced Fab Casing, Advanced Power Ports and an Advanced Operating Panel for both new structures. These are solid cuboids, including the interior positions.

| Machine | Width × height × depth | Controller | Panel | Power ports | Remaining casings |
| --- | --- | --- | --- | --- | --- |
| Deposition / Etch Chamber | 3 × 3 × 5 | 1 | 1 | 1–2 | 42–41 |
| Advanced Test and Package | 2 × 2 × 3 | 1 | 1 | 1–2 | 9–8 |

Place the controller in any column of the bottom front row, facing outward horizontally. Put its operating panel directly above it. The structure extends behind the controller. Replace any other casing positions with one or two power ports. Additional panels, controllers or water inlets are invalid for these machines. Supply FE through the ports.

Right-click the lower controller to see cabinet status. Use the upper operating panel for inputs and outputs. Both new machines share these construction blocks, but each has its own controller. Existing lithography and support machines retain their own blocks and dimensions.

## Run normal T4 production

1. Purify an ordinary Silicon Boule with 1,000 mB water. Buckets or the existing purifier water inlet work. Slice the Pure Silicon Boule into an empty FOUP. Default yield is 25 PURE wafers, subject to config and carrier capacity.
2. At the new Deposition / Etch Chamber's upper panel, insert the fresh PURE FOUP and four Silicon. It must contain unprocessed wafers with no prior preparation. The machine consumes the lot and Silicon at admission and returns the same carrier with G2 preparation after 1,200 powered ticks. Identity, substrate and private wafer data are preserved. The tooltip shows preparation separately from substrate. Base cost is 1,000 FE/t, totaling 1,200,000 FE per lot, so continued charging is required beyond the 1,000,000 FE buffer.
3. At the E-beam Mask Writer, select one of the four new T4 designs: Logic, Memory, Compute or NAND. Each consumes one Blank Mask and 48 Redstone, runs for 1,600 ticks at 2,500 FE/t (4,000,000 FE total), and produces a generation-2 mask with configured uses.
4. Install the ArF Excimer Laser, ArF Dry Lens, Nanopositioning Stage, ULPA Filter and Precision Overlay Scope in the existing lithography cabinet. At its upper panel, load the G2-prepared PURE FOUP, matching T4 mask and T4 Photoresist. T4 has 32 layers at 100 base ticks per layer. Keep power and resist supplied. One mask use is consumed per completed lot. T4 compute remains one core.
5. Try the completed T4 lot in the old standalone Test and Package machine: it must refuse the lot without consuming it. Move it to Advanced Test and Package, leave High Binning OFF and supply Chip Packages. Its upper panel has FOUP input, package input, four chip output slots and two scrap slots. Normal packaging takes 10 ticks per wafer at base 100 FE/t. Successful wafers consume packages and produce T4 chips; failed wafers produce scrap. The original lot is consumed at admission and the carrier becomes empty.
6. Repeat the chain for each of the four families. Output counts vary according to the actual frozen wafer rolls and configured yield. The server chain tests temporarily use three wafers and two ticks per layer; those are test settings, not player defaults.

Lithography's internal inventory indices are FOUP 0, mask 1, resist 2, then the five equipment slots 3–7. Preparation uses FOUP 0, Silicon 1, output 2. Packaging uses FOUP 0, packages 1, chips 2–5, scrap 6–7. Slot indices are provided only for diagnostics.

## Try high binning

Use an ordinary completed **T3 Logic or T3 Compute** lot. No T4 lithography or G2 preparation is required for this alternate path, but the G2 packaging machine is required. Toggle High Binning ON at the upper operating panel while idle, then insert the lot and packages.

The two bundled JSON transitions promote T3 to T4. The target yield is 0.15 times the source lot's frozen effective yield; it is not a flat 15% success rate, and it uses the source's existing private rolls without rerolling. Failures become scrap. Memory and NAND are refused. T4 lots cannot high-bin to an unavailable T5 item. Logic's eventual ceiling remains T5, and no T5 content is implemented here.

High binning doubles ticks per wafer and FE per tick: base 20 ticks at 200 FE/t, or four times the normal total FE per wafer. Package costs remain per successful wafer. Mode, target, judgments, output quantities and costs freeze when the queue accepts the lot. The mode button cannot change an active queue. Return the machine to normal mode before packaging the four ordinary T4 families.

## Check recovery and presentation

- Remove power during preparation or packaging; progress should pause and resume after charging.
- Remove a casing or upper panel during work; restore it and confirm the job resumes.
- Save/reload during work and confirm the accepted job retains its progress and costs.
- Block the output slots and confirm processing waits without losing the original job.
- Break an active preparation controller: its original unprepared carrier is recovered once; consumed reagent and FE are not refunded.
- Confirm lower controllers expose status without operational slots, upper panels show the correct slots, and status text stays inside the GUI with JEI installed.
- Confirm a removed panel closes or invalidates its old menu, and an active high-bin job refuses mode changes.

## Editable balance and recipes

Paths are relative to `src/main/resources/` and can be overridden by data packs:

- `data/lithmod/machines/deposition_etcher.json`: duration, Silicon quantity, FE and buffer.
- `data/lithmod/machines/advanced_test_package.json`: packaging time, packages and FE.
- `data/lithmod/high_binning/logic_t3_t4.json` and `compute_t3_t4.json`: source/target and yield/time/FE multipliers.
- `data/lithmod/support_recipes/ebeam_*_t4.json`: blank-mask, Redstone, duration and FE costs.
- `data/lithmod/recipe/crafting/`: T4 equipment, resist and new structure recipes; these consume previous-generation logic chips.
- `data/lithmod/tiers/t4.json`: T4 layer count, timing and nominal yield.

Server config controls mask uses, wafers per boule, chips per wafer, yield/timing overrides and the global FE multiplier. All numbers above assume bundled data and FE multiplier 1. The nominal T4 yield is 92%; actual effective yield also depends on the configured line and run settings.
