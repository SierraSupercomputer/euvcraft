# EUVCRAFT

EUVCRAFT is a semiconductor fabrication mod for Minecraft. Build a fab that turns silicon into wafers, processes them through increasingly demanding lithography equipment, and packages the results into logic, compute, memory and NAND chips.

Progression currently spans **T1–T7**, from a compact early process cell to a large EUV production line with a separate light source. Each generation adds infrastructure to manage: wafer preparation, physical machine modules, calibration, ultrapure water, cleanrooms, hydrogen and vacuum.

## What you can build

- **A complete wafer production chain:** grow and purify boules, slice wafers into FOUP carriers, prepare and expose them, then test and package the chips.
- **Larger multiblock machines:** deposition/etch chambers, lithography scanners, mask writers and assembly equipment, with separate operating and equipment panels where applicable.
- **Interactive calibration and yield management:** beam alignment, diagnostic curve fitting, and high binning that trades yield for a chance to produce higher-tier compute chips or early-generation logic chips.
- **Fab utilities and cleanrooms:** supply FE power and ultrapure water, control contamination with powered filtration and gowns, and protect high-tier production from containment breaches.
- **EUV infrastructure at T7:** route Fab Hydrogen, connect a light source to its scanner by beamline, and use dedicated pumping or a shared central vacuum plant.
- **Chip assembly and recovery:** assemble CPUs, DIMMs and microcontrollers at supported tiers; clean rejected or contaminated wafer lots without spending chip packages, and recover a small amount of Silicon from loose scrap.

Recipes and machine balance are defined in editable JSON resources. This is an **Alpha** project: progression and costs are still being playtested. Encoded Logistics hardware/server upgrades and T8 progression are planned, rather than included in the current release.

## Installation and dependencies

Download the JAR from [GitHub Releases](https://github.com/SierraSupercomputer/euvcraft/releases). The current release is [0.8.0 Alpha](https://github.com/SierraSupercomputer/euvcraft/releases/tag/v0.8.0).

| Requirement | Tested version for 0.8.0 |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| NeoForge | 26.3.0.99 |
| [Arcforge](https://github.com/zagdrath/arcforge) — required | 2.8.1+26.3 |
| [Encoded Logistics](https://github.com/zagdrath/encoded-logistics) — required | 1.5.0+26.3 |


Install EUVCRAFT, Arcforge and Encoded Logistics in the client and server's mods folders. Use matching Minecraft versions and remove older duplicate mod JARs when updating.

**Do not install arcforge-api-1.2.0+26.3.jar.** It is a developer compile-time dependency; the full Arcforge mod supplies the API during gameplay.

## Getting started

- [T1–T7 progression guide](docs/playtest-progression.md): current routes, construction, calibration, cleanrooms, assembly, recycling and playtest checks.
- [Recipe reference](docs/recipes.md): generated from editable crafting and machine JSON.
- [Issue index](docs/issues/README.md): open reports, implemented repairs awaiting confirmation and completed fixes.
- [0.8.0 release notes](docs/releases/0.8.0.md): additions and upgrade guidance.

Optional JEI process pages use each multiblock controller as the crafting station. Tested with JEI 31.14.0.72 for Minecraft 26.3 / NeoForge and its required MezzConfig dependency; the core mod works without JEI.

This remains an Alpha release. Report problems through [GitHub Issues](https://github.com/SierraSupercomputer/euvcraft/issues), including the mod version, reproduction steps and relevant logs.
