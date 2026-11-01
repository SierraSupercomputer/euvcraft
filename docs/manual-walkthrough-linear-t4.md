# Linear fabrication: T4 checkpoint

This development increment separates T4 deposition and etching and adds FOUP routing hints. Test this checkpoint before the planned T5 coating/cleaning and T7 chamber split. T1–T3 remain integrated; T5–T7 currently retain their existing external routes. Existing scanner calibration, masks, cleanrooms and high-binning rules still apply.

## Compatibility

Use a fresh T4 batch. An old already-exposed T4 FOUP has no trustworthy completion marker for the new route and cannot be silently treated as etched. The FOUP Cleaner can discard it. Existing machine structures retain their current panel positions; no equipment-bay remodeling is included. Other tiers and worlds do not need replacing just to try this increment.

## Creative inventory

1. Open **EUVCRAFT: Resources**: silicon/boules, resists, etchant charges, filter media, blanks, packages and scrap.
2. Open **EUVCRAFT: Fab Blocks**: machines, casings, ports, panels, scanner modules and cleanroom blocks.
3. Open **EUVCRAFT: Items**: FOUPs, blank/patterned masks, chips, assembled products, line equipment and gowns.
4. Search for FOUP, scanner, etch and filter. Existing sample masks remain distinct; sample FOUPs are real empty carriers. Move a processed FOUP through Creative inventory and check that its stage, wafer count and program remain intact.

## T4 production

1. Produce a pure boule and slice it into a FOUP as before. Its tooltip should show fresh wafers, no assigned program and multiple eligible destinations. These are compatible operations, not promises that a nearby machine is formed or powered.
2. Build the existing **Deposition Chamber** (old registry ID `deposition_etcher`), retaining the 3×3×5 Advanced Casing layout and Operating Panel directly above its controller. Feed the fresh pure-wafer FOUP and **2 Silicon** into its upper panel. One lot takes 800 powered ticks at 1,000 FE/tick.
3. The output keeps its carrier and wafers, shows G2 preparation and **Film deposited**, and still has no assigned tier. Its current next options include the T4–T6 scanner routes allowed by its substrate; pure wafers cannot run T6.
4. Run the deposited FOUP through the calibrated T4 scanner with a T4 mask and matching resist. Existing beam calibration and one-mask-use-per-completed-lot behavior remain. During exposure, routing says exposure in progress. On completion it says **Exposed/developed**, identifies the T4 family/compute cores, and directs you to the Etch Cell.
5. Try that FOUP in Advanced Test & Package with packages and power. It must say **Needs Etch Cell processing**, preserving the FOUP, packages and FE.
6. Build a separate **Etch Cell**, 3 wide × 3 high × 5 deep. Use one Etch Cell controller on the lower front row, one Advanced Operating Panel directly above it, one or two Advanced Power Ports, and Advanced Casing everywhere else. With one power port this is 42 casings; optional Fab Item Ports replace casings. The front can face any horizontal direction, with the controller in any front-row column. No scanner modules are required in this chamber.
7. Craft **Etchant Charge** (editable JSON: 2 Silicon + 1 Redstone → 1 charge). Put the exposed T4 FOUP and one charge into the etch cell's upper operating panel. The lower cabinet is status-only. Etching takes 400 powered ticks at 1,000 FE/tick.
8. Remove power partway through: progress pauses. Restore power: it resumes. Save/reload partway through another run: progress and the paid charge persist.
9. Output must say **Etched** and point to Advanced Test & Package. Carrier identity, wafer count, preparation and private yield rolls are preserved; etching performs no second exposure or new yield roll.
10. Package normally. Repeat for logic, compute, memory and NAND. Also verify T3→T4 high binning still uses the T3 source route, while T4→T5 high binning requires the T4 etch step. Memory/NAND remain ineligible for high binning.

The original combined 1,200 ticks, 4 Silicon and 1.2 million FE are distributed across the two chambers; the new charge adds Redstone and the second structure adds capital cost. Definitions/recipes remain editable JSON. Until the next slice, legacy T5/T6 use the shorter deposition operation without the new T4 etch requirement; this is an intermediate balance checkpoint.

## Optional item automation

Replace any ordinary Advanced Casing in a formed G2 deposition chamber, T4 scanner, etch cell or advanced packager with a **Fab Item Port**. This port is different from the Cleanroom Item Port, which bridges a room wall. A Fab Item Port exposes its machine inventory directly and works on all faces.

Use Arcforge item conduits with their endpoint pull/push modes configured. Feed valid FOUPs/reagents and pull finished outputs. Inputs, installed masks and scanner modules cannot be extracted through this port. Stock masks through the operating panel as usual; mask removal remains manual. Power still enters through the power port. Breaking the shell must stop transfers through retained handlers.

For the first automation check, process two T4 FOUPs successively through etching, then packaging. Confirm both carriers arrive, no active FOUP is stolen and charges/packages are consumed once. This increment does not add direct item capabilities to the standalone wafer slicer or every older/T7 machine. The shared item-transfer protocol is also used by Encoded Logistics, but this checklist's end-to-end network verification targets Arcforge; no new server hardware integration is included.

## Report back

Check creative search/grouping, readable FOUP hints, all T4 families, power pause/reload, high-binning boundaries and sustained item transport. Graphical layout and manual gameplay acceptance remain pending until this walkthrough is completed.
