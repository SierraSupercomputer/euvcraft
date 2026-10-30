# T7 EUV playtest

Use a backed-up test world. T1–T6 machines keep their existing roles. Dimensions below are **width × height × depth**; structures are filled volumes, not hollow rooms. Hold Shift over a controller for its build requirements. These are playtest instructions, not a record of manual acceptance.

## 1. Build the utilities

Build a **2×2×3 Hydrogen Purifier** using Hydrogen Casing, its controller on the lower front face, its Operating Panel directly above, one Gas Inlet, one Gas Outlet, and 1–4 Hydrogen Power Ports. Ports can occupy remaining positions. Put Utility Filters in the panel and supply FE. Feed Arcforge hydrogen from an electrolyzer through **pressurized gas conduits**, with extraction/insertion configured at the endpoints.

The starting JSON recipe reserves 8 buckets of raw hydrogen and one filter, pays 600 FE per tick for 400 ticks, then produces 6 buckets of Fab Hydrogen. Interrupt power: progress should pause and resume. Extract Fab Hydrogen through the outlet into an Arcforge pressurized cylinder. Ordinary fluid pipes/water are not substitutes for gas transport.

Keep the existing Utility Plant supplying UPW. New machines have dedicated local vacuum pumps: give them FE and time to pump down before their first job. No vacuum gas needs to be made or stored.

## 2. Write a reflective reticle

Build a **3×3×5 G3 Reticle Writer** with G3 Casing. Use a lower-front controller, Operating Panel directly above, 1–4 G3 Power Ports, one Writing Module and one Metrology Module. Modules go anywhere in the middle layer; remaining cells are casing. Top and bottom are casing except the controller and allowed power ports.

In the upper Operating Panel, select compute, memory or NAND. Compute also offers the configured core counts. Supply one Blank Mask, 64 redstone and FE; press Write. Selection is frozen while the job runs. The lower cabinet is a status interface.

The starting job takes 3,600 paid ticks at 8,000 FE/t. Outside a cleanroom it still works, but the resulting reticle has fewer uses. This penalty changes durability only. Compare two jobs under different cleanroom conditions; do not expect cleanroom quality on the mask to multiply downstream wafer yield.

## 3. Prepare electronic-grade wafers

Make Electronic-grade Boule in the existing zone refiner and slice it into a FOUP. Build the **5×5×7 G3 Deposition/Etch Chamber** with G3 Casing, lower-front controller, Operating Panel directly above, 1–4 G3 Power Ports, and exactly one each of Deposition Module, Rinse Module, Vacuum Module and UPW Inlet. Place modules/inlets anywhere in the three middle layers. Put the whole machine inside one enclosed cleanroom.

Supply FE and UPW. Insert the fresh electronic-grade FOUP through the upper panel. The starting phases are vacuum deposition/etch (400 ticks), a single 4-bucket UPW rinse charge (200 ticks), and powered drying (200 ticks). Missing utilities pause the job. Retrieve the same FOUP variant and name from the output. Its tooltip should say **G3 preparation**. Reinserting it must not reroll preparation. Per-wafer preparation evidence remains private in Survival; Creative debugging preserves it.

The lower cabinet has repair-material slots and central vacuum isolation controls. It is not the wafer input.

## 4. Couple the source and scanner

Build a **3×3×5 EUV Light Source** from its casing, lower-front controller, upper Operating Panel, 1–4 matching Power Ports, one Tin Inlet, one Hydrogen Inlet and one Vacuum Module in the middle layer. It may stand outside the cleanroom. Supply FE, Fab Hydrogen and Tin Feedstock through its upper panel or tin inlet.

Tin Feedstock is a provisional craftable supply: four copper ingots, four redstone and one iron ingot make four feedstock. The editable JSON recipe can be replaced by an ore/refining chain later. It also fills `c:ingots/tin`, so another mod's tagged tin can be used.

Build a **5×5×7 EUV Scanner** with EUV Scanner Casing, lower-front controller, upper Operating Panel, 1–4 matching Power Ports, one each of Exposure, Optics, Stage, Filtration, Metrology and Vacuum Modules, plus one Calibration Panel, Diagnostic Panel and UPW Inlet. Modules/panels may occupy the three middle layers. Enclose the whole scanner in one cleanroom.

Put the Exposure Module on an outer face. Run a straight line of **1–16 enclosed EUV Beamline blocks** from it to a **plain casing face** of the formed source. The endpoint must be source casing, not its hydrogen/tin port or vacuum module. Beamlines can penetrate the cleanroom shell without extending its detected size. Avoid connecting multiple source candidates. A source serves one scanner installation at a time.

Provide scanner FE and UPW; allow both chambers to pump down. Use the separate Calibration Panel for beam alignment, and the Diagnostic Panel for paid quadratic fitting with Burn Paper. Both must be accepted before operation. The lower equipment cabinet contains the saved repair inventory and isolation control; operating inputs are upstairs.

## 5. Expose and package

Load the G3-prepared FOUP, a G3 T7 reticle and T7 resist into the scanner's upper Operating Panel. This machine runs T7 only. It performs one 100-tick exposure per layer across 80 layers. Ensure enough resist for all layers, and top up the existing stack when needed.

Starting paid exposure costs are scanner 960 FE/t + 5 mB UPW/t, plus source 1,200 FE/t + 10 mB Fab Hydrogen/t. One tin feedstock opens 1,000 exposure ticks of saved tin credit. Local pumps have their own FE cost. Supply outages pause without scrapping. Save/reload mid-run and verify that progress, preparation, core count and tin credit persist.

Move the finished FOUP to an enclosed Advanced Test & Package machine with Chip Packages and FE. Produce T7 compute, memory or NAND chips. Existing calibrated reflow assembles T7 CPUs from one compute chip plus a CPU Blank, and T7 DIMMs from four memory chips plus a DIMM Blank. CPU core metadata must survive packaging and assembly. NAND assembly and the scrap recycler remain deferred.

Compute T6→T7 High Binning is an alternate path with the configured yield penalty; it still requires an enclosed cleanroom at packaging. Logic stops at T5; memory/NAND high binning is not added.

## 6. Breach and recovery

Start a preparation or scanner lot, remove its FE supply to pause it, then break a cleanroom wall. The active wafers must become a **Contaminated FOUP**, even though processing had paused. Machine contamination persists through saves and controller pickup; ordinary pickup must not create copied inventory or energy.

Seal the room. Use the existing FOUP Cleaner with ordinary water and FE to discard the contaminated wafers; it should return one Wafer Scrap per wafer and the reusable empty carrier, without Chip Packages.

Repair the affected machine through its lower cabinet with the starting bill: two Utility Filters, 16 redstone, 8 buckets of UPW and 200,000 FE. The preparation chamber returns to operation after repair. The scanner also requires fresh beam alignment and diagnostic fitting; both targets change so the old solutions cannot simply be reaccepted. Supply shortages alone should not trigger this repair bill.

## 7. Optional central vacuum

After dedicated pumping works, build a **3×3×5 Central Vacuum Plant** using Vacuum Casing, lower-front controller, upper Operating Panel, 1–4 Vacuum Power Ports, a Service Outlet and one Service Conduit. Connect Service Conduits to the machines' accessible Vacuum Modules. Use the sealed Cleanroom Vacuum Port through room walls; ordinary conduit does not seal a room.

Each chamber retains its own pressure and reserve. At the starting balance a plant supplies up to 20,000 service units/t for 350 FE/t under load, versus three separate local pumps at 200 FE/t each. Connect several chambers, inspect the plant's client/demand status, disconnect one branch and toggle isolation at a machine. Other chambers must keep their own state; an isolated chamber should fall back to its local pump. Networks are bounded and do not load distant chunks.

All costs, timings, yield settings and recipes are editable in `data/lithmod/machines/`, `support_recipes/`, `machine_recipes/`, `high_binning/` and `recipe/crafting/`. Starter textures are intentionally simple.
