# JEI process guide

EUVCRAFT optionally integrates with **JEI 31.14.0.72 or newer for Minecraft 26.3 / NeoForge**. Install JEI and its required dependencies on the client. Neither the full JEI mod nor its API is embedded in EUVCRAFT. The mod and dedicated server still work with JEI absent. Matching EUVCRAFT client/server builds provide the process catalog; an older server without the new channel does not provide these pages.

## Finding a process

- Use JEI's recipe/uses controls on an item or fluid to find producers and consumers. Each process category uses **the actual controller block** as its crafting station, including multiblocks whose inventory is accessed through a separate operating panel. Casings/panels are not listed as stations.
- Examples: Pure Silicon Boule → Boule Purifier; Ultrapure Water → Utility Plant; Fab Hydrogen → Hydrogen Purifier; CPUs/DIMMs → Pick And Place or Reflow Oven, according to tier.
- Ordinary crafting/smelting remains in JEI's existing vanilla categories. Arcforge's own JEI plugin supplies its raw-gas processes; EUVCRAFT adds Fab Hydrogen purification rather than duplicating Arcforge categories.
- Machine and support JSON recipes are shown along with definition-driven slicing, preparation, coating, scanning, etching, productive cleaning, disposal, packaging/high binning and utility services. Vacuum/light-source service pages explain their requirements without inventing an item output.
- Fluid/gas slots use their real registered fluid ingredients. Look up the fluid entry rather than an unrelated bucket placeholder.

## Reading a page

Inputs and outputs are above reusable/installed ingredients. Masks appear separately and spend **one use per completed lot**, rather than one whole mask. Mask design lookup ignores remaining uses and optional compute core selections. Writing a mask still requires selecting its design in the writer's GUI.

FOUP icons are **carrier placeholders**, not supplied wafer lots. Hover each slot for the required/produced stage, tier and substrate information. Generic FOUP lookup intentionally shows multiple compatible process pages; it does not index private lot identities/rolls or infer a destination from a live carrier. Public recipe data never contains live FOUP bodies. The machine's status and FOUP tooltip remain authoritative for actual admission.

Visible notes are abbreviated; hover the bottom details area for the complete explanation. FE/t and ticks describe the current server's new-job settings where fixed. Variable or phase-specific costs are explained in notes. Existing admitted/coated lots retain their frozen budgets, which may differ after a configuration edit. Pump-down and auxiliary source/pump/cleanroom costs are additional where stated.

Packaging and reflow output counts are illustrative because yields, core count and calibration affect actual results. Failure outputs are identified as such. High binning follows the **source tier's completed route** and has lower yield; T7 targets additionally require enclosure. Productive Wet-Clean Station pages retain wafers; FOUP Cleaner pages explicitly destroy them.

The catalog is sent from the server on login/datapack reload and server settings changes. JEI refreshes changed pages and clears the previous server's catalog on disconnect. The initial bundled catalog has 114 pages across 25 controller stations; datapacks/settings can change this. Unsupported/oversized catalog data fails closed to empty pages without changing machine behavior.

## Verification walkthrough

1. Start with matching EUVCRAFT builds and the supported JEI installed. Check all controller stations have processes; no operating panel is used as a station.
2. Find Pure Silicon Boule, a patterned mask, Ultrapure Water and Fab Hydrogen through JEI recipes/uses. Check amounts, station and notes against their machine GUIs.
3. Follow a FOUP through T4 and T5–T7. Verify T5+ resist belongs to the coater, scanners list the reusable mask, etching precedes productive cleaning and packaging requires the appropriate completed stage.
4. Inspect normal/high-bin packaging and reflow failure outputs. These are possibilities, not guaranteed simultaneous outputs.
5. Change a recipe/definition via a datapack and `/reload`; check the displayed result/cost updates. Reconnect to a second server and confirm the first server's overrides are gone.
6. Remove JEI and restart a client/dedicated server: EUVCRAFT still starts and machines operate. No Ponder integration or recipe-transfer/automatic crafting is included in this increment.
