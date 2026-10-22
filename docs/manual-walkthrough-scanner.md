# T4 scanner and beam alignment walkthrough

Use a fresh test world. Existing T4 small lithography cells no longer accept new T4 lots; T1–T3 still use the small machine. Existing equipment items are crafting ingredients for the new physical modules. No world conversion is provided. The earlier preparation and G2 packaging remain separate required machines.

## Build

Build a filled 3 wide × 3 tall × 5 deep scanner from Advanced Fab Casing. Choose any horizontal facing and any bottom-front column for the T4 Lithography Equipment Cabinet. Put the Scanner Operating Panel directly above it. In the middle layer, put one each of the five Scanner modules (Exposure, Optics, Stage, Filtration, Metrology), plus one Scanner Calibration Panel. Their order does not matter. Place the calibration panel on an accessible outside face so you can reach it. Use one or two Advanced Power Ports in any other available position. Fill every remaining position with casing. With one port this uses 36 casings; with two, 35. Top and bottom contain frames except the controller and ports.

The cabinet shows structure/equipment status. The upper Operating Panel has exactly three inputs: FOUP, mask, resist. The separate Calibration Panel has no inventory slots.

## Align and operate

1. Open the Operating Panel before calibrating. It should say Calibration required / Use the Calibration Panel. A prepared PURE FOUP, T4 mask and T4 resist must not start processing or spend resources yet.
2. Open the Calibration Panel. Use X and Y controls to center the beam on the target. Each control affects both axes. Use Focus controls to reduce focus error. There is no reflex timer or wait bar.
3. Quality and proposed yield multiplier update as you adjust. Default minimum quality is 50/100. At 50 quality the multiplier is 0.75; at perfect quality it is 1.0. Press Accept alignment. Adjusting again requires accepting the new result.
4. Return to the Operating Panel, supply FE through a power port and run the same prepared PURE FOUP + T4 mask + T4 resist from the T4 walkthrough. Calibration controls must be locked during the active lot; Abort remains on the Operating Panel.
5. Confirm all layers finish, mask loses one use, and G2 packaging produces the correct T4 chips/scrap. The calibration multiplier is included once in the lot's frozen private yield specification, including later high binning; it is not applied per layer or again by packaging. Individual private wafer rolls are never exposed by these panels.
6. Remove power and refill it: progress pauses/resumes without losing accepted calibration. Save/reload the formed machine: calibration and active work persist.
7. Replace a module, including with another of the same type: recalibration becomes required. Abort an existing interrupted lot before adjusting. Ordinary frame/power repair retains calibration when the same modules remain installed.
8. Break the controller: only the actual FOUP, mask and remaining resist drop from its inventory. Equipment modules remain physical blocks and must not be duplicated into item drops from the controller.

## Balance and future scope

Edit data/lithmod/machines/lithography_scanner.json in a datapack for FE and calibration minimum quality / multiplier range (permille). Existing accepted lots retain their frozen factor. Current defaults are provisional balance values. Diagnostic tuning comes with the next upgrade; later mask alignment, module diagnostics and independent cleanroom yield factors remain deferred.

Automated server checks complement this walkthrough. Actual client layout, JEI coexistence and calibration usability need an in-game check.
