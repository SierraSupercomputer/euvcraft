# T5 utilities, diagnostic tuning and component assembly walkthrough

Use the new project build/libs/lithmod-1.0.0.jar after automated acceptance. Existing T4 scanner block IDs remain; adding T5 modules intentionally invalidates calibration. A fresh test world is fine. These checks establish client appearance/usability, which server tests cannot.

## 1. Utility plant (2 wide × 2 tall × 3 deep)

Build a solid box from Advanced Fab Casings. Use one Utility Plant controller in either bottom-front column, one or two Advanced Power Ports anywhere else, exactly one Utility Water Inlet and one Utility Pure Outlet in any remaining positions. Fill the rest with casing (8 casings with one power port; 7 with two). All horizontal orientations work. This small utility machine has one controller interface.

Supply FE through a power port. The GUI accepts Utility Filters in the left slot and ordinary water buckets in the middle; the right slot returns empty buckets. Alternatively connect an Arcforge fluid conduit/tank to the Water Inlet, from any side. Ordinary water belongs here. Shift-click water buckets and filters to confirm they route to their own inputs. The Pure Outlet is extraction-only and outputs a separate Ultrapure Water fluid; ordinary water must not substitute for it in the scanner.

Defaults: 16,000 mB raw and pure tanks. One filter + 8,000 mB water + 200 powered ticks at 80 FE/t produces 8,000 mB ultrapure water. Load eight buckets or pipe in water. No water/filter consumption should repeat after an active job is saved/reloaded. Insufficient power or blocked pure storage pauses work. Multiple scanners can share a plant/tank network; capacity and consumption are editable.

## 2. Upgrade the working T4 scanner

Keep its 3×3×5 structure, controller, upper Operating Panel, separate Beam Calibration Panel, exposure/stage/filtration/metrology modules and Advanced Power Port(s). Replace the Optics Module with the Immersion Module. Craft its five immersion components from the existing T4 components plus T4 logic and diamonds; the module combines these with the existing optics module and a T4 compute chip. This is an immersion retrofit to the existing physical stack, not a new support-machine generation.

Add exactly one Scanner Diagnostic Panel to an unused middle-layer cell and one Scanner Water Inlet to any unused casing cell (the inlet may also be in the top/bottom frame). Install the diagnostic panel and inlet together. All other unused cells are casing. T5 needs 34 casings with one power port or 33 with two. The other five modules and beam panel retain their flexible middle-layer placement. Connect the plant's pure output through Arcforge fluid conduits/tanks to the scanner inlet. A filled buffer holds 16,000 mB.

Verify ordinary water is refused by the scanner. The new immersion hardware requires beam alignment again. The equipment cabinet remains status-only; FOUP/mask/resist go into the upper Operating Panel, beam controls into the existing Calibration Panel, and quadratic fitting into the new Diagnostic Panel.

## 3. Diagnostic fitting

The target is y = Ax² + bx + c. Its coefficients are hidden and randomized in hundredths. The amber curve is the target; cyan is the last plotted candidate. The fixed graph spans x = -2..2 and y = -8..8. Enter A, b and c between -2.00 and 2.00. Values are rounded to hundredths. A changes curvature, b tilts the curve, and c shifts it vertically.

Place Burn Paper in the diagnostic interface's paper slot. Press Plot: each valid attempt consumes exactly one paper and updates the plotted curve/score. Editing text alone is free. Invalid inputs, missing paper, a closed/distant/wrong interface, an unformed machine or active processing must not spend paper. Once the score reaches the editable minimum (default 50), press Accept Fit. Acceptance consumes no extra paper and requires a paid plot. Reopening and saving must preserve the target, last plotted candidate, accepted result and unused paper.

Both beam alignment and diagnostics are required for T5. Default factor for each is 0.5 + 0.5 × quality/100; quality 50 gives ×0.75, quality 100 gives ×1.00. Multiply the accepted factors once into the lot's frozen yield. For example two quality-80 calibrations give ×0.9 ×0.9 = ×0.81; T5's 93% baseline becomes 75.33% before any other applicable factors. Paper spent does not itself improve quality. Packaging/high binning must not apply calibration a second time. Replacing calibrated hardware requires new calibration; ordinary frames, plumbing, power loss and reload do not.

## 4. T5 fabrication and recovery

Use the existing G2 E-beam Mask Writer to select T5 logic, memory, compute or NAND. Its T5 recipe uses one blank mask, 64 redstone and 2,000 ticks at 3,000 FE/t; provide continuous power beyond its buffer. Retain the pure-boule/Slicer/G2 deposition-and-etch preparation chain and G2 packaging.

Put a G2-prepared PURE wafer FOUP, the matching T5 mask and T5 resist in the scanner's upper panel. Supply power continuously: T5 uses 42 layers × 100 ticks, 240 FE/t and 2 mB ultrapure water per active processing tick. A default lot needs 8,400 mB, so a single plant batch of 8,000 mB is deliberately slightly short. More water or a second batch lets it finish. A full scanner FE buffer also falls slightly short of the default 1,008,000 FE processing cost. Verify safe pause/resume for either shortage without double charges or changed calibration.

Try saving/reloading mid-run, including while an ordinary frame is temporarily missing. Repair/replenish and confirm the accepted lot resumes. Module changes must not silently continue under a different hardware contract. Calibration controls and paper access are locked during active processing. Abort remains available.

Package each completed T5 family using the existing G2 packager and chip packages. T5 compute remains one core; selectable cores start at T6. T4 logic/compute high binning can produce T5 chips with the existing editable penalty; memory/NAND cannot high-bin and logic still caps at T5.

## 5. Pick-and-place assembly (2×2×2)

Build a solid cube using one Pick And Place controller in either bottom-front column, one or two Assembly Power Ports anywhere else, and Assembly Casings (6 with one port; 5 with two). No operating panel or calibration is required for this small machine. Supply FE through a port. In its GUI, place chips in the left input, a shared blank in the middle and take the result from the right.

| Product | Inputs | Available tiers |
|---|---|---|
| CPU | 1 same-tier compute chip + 1 CPU Blank | T3–T5 |
| DIMM | 4 same-tier memory chips + 1 DIMM Blank | T2–T5 |
| Microcontroller | 1 same-tier logic chip + 1 Microcontroller Blank | T1–T5 |

The chip families retain their existing first tiers. All generations use the same three blanks. Blank crafting recipes embody the assembly materials; there is no hidden additional material cost. Default assembly time is 80 + 20×tier ticks at 20 FE/t. Three memory chips must not produce a DIMM. Shift-click blanks and chips from the player inventory and confirm they route to the matching inputs; NAND and unrelated items are refused. Verify exact inputs, same-tier outputs, blocked-output behavior, power/formation pauses, reload and controller drops without duplication. Assembly does not reroll chip yield.

## Editable data and deferred work

- Utility/scanner/assembly cost and capacity defaults: data/lithmod/machines/*.json.
- Blank and immersion crafting: data/lithmod/recipe/crafting/*.json.
- CPU/DIMM/microcontroller assembly: data/lithmod/machine_recipes/{cpu,dimm,microcontroller}_t*.json.
- Mask recipes: data/lithmod/support_recipes/ebeam_*_t5.json.
- High binning: data/lithmod/high_binning/*_t4_t5.json.
- Tier definitions/support requirements: data/lithmod/tiers/t5.json and support_lines/matrix.json.

Starter visuals reuse existing textures; check labels, graph legibility, slot positions and JEI overlap in the client. Later wafer cleaning, NAND assembly and the calibrated 3×3×2 T6+ reflow oven are recorded future work and are not implemented. Existing downstream crafting recipes have not been globally rewritten to require assembled components.


