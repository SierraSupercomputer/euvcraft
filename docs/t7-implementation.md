# T7 implementation notes

The T7 increment introduces G3 deposition/etch preparation (5×5×7), a G3 reflective reticle writer (3×3×5), an EUV scanner (5×5×7), a separate tin/hydrogen light source (3×3×5), hydrogen purification (2×2×3), and optional central vacuum (3×3×5). Dimensions are width × height × depth. Controller, panel and flexible middle-layer module conventions continue from earlier tiers. Starter textures and controller Shift tooltips are included.

Ordinary dedicated vacuum pumps are built into the relevant machines. Central service is paid, bounded, allocated fairly and applied to independent chamber pressures/reserves; it is not a transported gas. Real Arcforge pressurized gas transport handles raw and Fab Hydrogen. The Arcforge developer API remains compile-only, with no runtime installation or embedding.

T7 reticle writing outside a cleanroom reduces mask uses only. Preparation and EUV exposure require enclosure and separately accumulate paid-tick room exposure. G3 preparation draws are saved once per wafer, kept private in Survival, retained through Creative handling and every exposure layer, and applied independently at judgment. Beam and quadratic quality are frozen at lot admission; final room yield is applied once. Supply shortages pause.

A physical room breach during active preparation/exposure scraps the wafers even if FE had already paused the lot. The FOUP retains its carrier/name and enters the contaminated state. Existing FE/ordinary-water cleaning disposes of all wafers at one Wafer Scrap per wafer. Equipment remains contaminated through reload and controller pickup. Its narrow pickup component copies no inventory, FE or unfinished output. Repair consumes real materials, UPW and FE once; the scanner then requires newly generated beam and quadratic targets. G3 preparation adds no new calibration minigame.

T7 compute/memory/NAND chips and existing reflow CPU/DIMM assembly are included. Compute T6→T7 high binning preserves selected core metadata and requires enclosed packaging, including its alternate progression route. Logic remains capped at T5. Essential equipment recipes use T6 ingredients; optional central vacuum may require T7 products. Distinct mirrored crafting arrangements avoid recipe collisions while preserving ingredient counts.

The owner approved provisional JSON-crafted Tin Feedstock because installed Arcforge has no tin item. It joins `c:ingots/tin`; another mod's tagged tin can also work. Its starter recipe is a balance placeholder for a later ore/refining chain.

All prior source and failed drafts are preserved in coordinator worktrees. Automated checks cover unit/domain behavior and real registered machines, including complete EUV exposure, saved jobs, source pairing, exact paid resources, gas transport, menu authority, packaging and breach recovery. Detailed execution receipts are maintained by the coordinator; this document makes no manual client acceptance claim. Follow [the manual walkthrough](manual-walkthrough-t7.md) for visual/layout and Survival playtesting.

No existing T1–T6 machine is intentionally replaced. T8, NAND assembly, recycling, airlocks, and Encoded Logistics hardware implementation are outside this increment. Server integration has only been researched. No GitHub push/release or client/world installation is part of this work.
