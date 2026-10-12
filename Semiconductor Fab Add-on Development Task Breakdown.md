# Semiconductor Fab Add-on: Development Task Breakdown

Oct 12, 2026 · @Sierra

## Scope, assumptions and open questions

The first release covers lithography tiers T1 to T8, five support lines and FOUP-based hidden yield, as an addon for Encoded Logistics and Arcforge on NeoForge 26.3; metrology and wafer screening are deferred to a later update.

Assumptions this plan makes:

- FOUP size (default 25) and wafers per boule are config values.
- Yield is stored per wafer inside the FOUP's item data, hidden from tooltips, and rolled server-side.
- Wafers exist as entries inside a FOUP, not as 25 loose items, until packaging turns them into chips.
- Gen 1 FOUPs move by hand; Mk2 FOUPs use a dedicated FOUP transit layer that Encoded Logistics will provide later.
- T6 is optional: T7 must be reachable from T5 without it.
- Real step counts are a derived display stat only; the player manages about 8 to 10 stations plus a layer loop.

Confirmed decisions:

- Platform: NeoForge 26.3, as an addon for Encoded Logistics and Arcforge. Encoded Logistics is co-developed with this mod and will be expanded to support it, so changes there are in scope.
- Chips are recipe ingredients with a fixed speed and thread count per tier. The end yield check is pass/fail, chips are stackable with no grades, and failed chips become scrap that can be recycled. Target speeds follow historical values, and the pass/fail check follows each tier's yield percentage (a 90% yield process needs a cumulative roll above 0.10), both configurable.
- The four chip types replace Encoded Logistics' existing chip implementations: logic is a simple crafting ingredient, compute lets servers process more items at a time, memory lets servers hold more items in memory (larger craft jobs), and NAND allows higher-density storage disks.
- Fab utilities run on FE and reuse Arcforge where possible; power numbers are config values to balance later.
- Yield is the product of per-step rolls, with modifiers from decisions made during mask-making. Threads begin at T6, where mask-making asks for a core count (1, 2, 4, 8, 12 or 24) and strongly punishes high core counts on immature processes; the curve is configurable.

What the co-mods already provide, from their public READMEs and changelog (check the code for exact names):

- Encoded Logistics (Minecraft 26.3, NeoForge 26.3.0.0-beta or later) already has Compute, Memory and Fabrication Servers in its Server Rack with a thread model for jobs, storage drives for items, fluids, gases and energy, network cables with lanes, Ingress and Egress Ports, and a Lithography Press, Processor Dies and neodymium, tantalum and gallium ores. The Lithography Press and Processor Dies appear to be the chip implementations this addon replaces.
- Arcforge supplies FE generation, heat, water, and Hydrogen and Oxygen gases through standard NeoForge capabilities, which fits fab utilities and the EUV hydrogen purge. Encoded Logistics already links to Arcforge 2.5.0 or later through its Small Wireless Bridge.

Open questions to settle before coding:

1. How does a chip's speed translate into server throughput inside Encoded Logistics (items per craft, craft rate)?
2. Are the proposed historical speed anchors, per-tier yields and comfortable core counts acceptable as starting defaults?
3. Does the core-count choice apply only to compute chips, and can it change per mask set or only once per tier?
4. Which Arcforge machines and gases should the fab reuse first (for example Electrolyzer hydrogen, water intake, Firebox heat)?
5. When will Encoded Logistics' FOUP transit layer land relative to milestone M2, and is hand-carrying the fallback until then?
6. What does recycling scrap return: which materials, and what fraction?

## Milestones and gates

Build in six milestones, proving the FOUP and layer-loop model on T1 and T2 before taking on the EUV cliff.

&#91;embedded content: milestone roadmap · 6 milestones, 5 gates\]

Each gate is a playable check rather than a date; the epic tables below carry the rough sizes.

## Pass/fail rule, tier targets and the core-count curve

A chip passes when its cumulative roll, the product of its per-step rolls, is above one minus its tier's yield percentage; a 90% yield process needs a cumulative roll above 0.10. The per-step roll distribution is tuned so a typical line passes at about the tier's yield (task B4). Every number here is a config value, and the figures are proposed defaults.

| Tier | Target speed (historical anchor) | Real-world example | Default yield | Comfortable cores |
| --- | --- | --- | --- | --- |
| T1 | \~1 MHz | Intel 4004, 1971 | 60% | N/A |
| T2 | \~5 to 30 MHz | 8086 (1978) to 386 (1985) | 80% | N/A |
| T3 | \~200 MHz to 1 GHz | Pentium Pro to Pentium III | 90% | N/A |
| T4 | \~1.5 to 3.8 GHz | Pentium 4 | 92% | N/A |
| T5 | \~3 to 3.9 GHz | Core i7 era | 93% | N/A |
| T6 | \~3.5 to 4.2 GHz | 22 nm and 14 nm desktop parts | 85% | 4 |
| T7 | \~4.5 to 5.5 GHz | 7 nm and 5 nm parts | 85% | 8 |
| T8 | \~5.5 GHz and up (projected) | High-NA era | 80% | 12 |

Core counts begin at T6, where mask-making asks for 1, 2, 4, 8, 12 or 24 cores. A core count above the tier's comfortable level cuts the effective yield, using the standard model in which die area grows with core count:

```latex
Y_{eff} = Y_{tier}^{\,1 + k\,\max\left(0,\ \frac{c}{c_{tier}} - 1\right)}
```

Here c is the chosen core count, c\_tier the tier's comfortable core count, and k the steepness (default 2). With the defaults above, effective yield by core count is:

| Tier | 1 core | 2 cores | 4 cores | 8 cores | 12 cores | 24 cores |
| --- | --- | --- | --- | --- | --- | --- |
| T6 | 85% | 85% | 85% | 61% | 44% | 17% |
| T7 | 85% | 85% | 85% | 85% | 72% | 44% |
| T8 | 80% | 80% | 80% | 80% | 80% | 51% |

If a player skips T6, core counts begin at T7.

## Epic A: Foundation

Everything else reads from one data-driven model of tiers, stats and config, so build this first. Sizes: S is about a day, M two to four days, L one to two weeks.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| A1 | Tier registry for T1 to T8 (light source, optics, resolution, overlay, throughput), loaded from data files | M | none | Tiers load from data and show in game |
| A2 | Stat model: resolution, overlay, throughput, defectivity computed from installed components | M | A1 | A line reports its stats; unit tests pass |
| A3 | Die type definitions (compute, logic, memory, NAND) with per-tier target speeds, thread counts, stat thresholds and the logic cap at T5 | M | A1, A2 | Each die type reports which tiers can make it |
| A4 | Config: FOUP size, wafers per boule, yield multipliers, FE cost per machine, per-tier yield percentages, the core-count penalty curve, T6 toggle | S | none | Values change behavior without recompiling |
| A5 | Support-line generation registry (G1 to G3) with the per-tier requirement matrix | S | A1 | A tier can query which generations it needs |
| A6 | Server-authoritative state: all yield rolls server-side and saved with the world | S | none | Rolls persist across reloads and cannot be client-spoofed |

## Epic B: Wafers, FOUPs and yield

The FOUP is the core data structure, so its item data format (B3) is the highest-risk decision in the project.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| B1 | Silicon chain items: sand or quartz to refined silicon to boule | S | A4 | A boule can be crafted from raw inputs |
| B2 | Boule slicer: one boule becomes one full FOUP (size from config) | M | B1, B3 | Slicing yields a FOUP holding N wafers |
| B3 | FOUP item with compact per-wafer data (layer index, hidden yield) stored in item data, Mk1 and Mk2 variants | L | A4 | A FOUP survives save, load and inventory moves with data intact |
| B4 | Yield roll engine: per-step roll shaped by line stats, applied to each wafer | M | A2, B3 | Same inputs give a repeatable distribution of yields |
| B5 | Hide per-wafer yield from tooltips; show only lot-level info (layer, wafer count) | S | B3 | Tooltips never leak per-wafer values |
| B6 | Mk2 FOUP transport over the transit layer Encoded Logistics will provide (insert and extract); hand-carrying until then | L | B3, Encoded Logistics transit layer | A FOUP moves between machines over the transit layer |
| B7 | Layer loop controller: repeats the layer cycle N times and routes the lot between stations | L | B3, B4 | A lot completes N layers unattended |
| B8 | Core-count yield modifier: applies the configurable penalty curve to a chip's effective yield (from T6) | S | B4 | A mask choice measurably shifts yield |
| B9 | Derived display step count (about 15 at T1 to about 1,500 at T8) | S | A3 | Recipes show a step count that scales with tier and layers |
| B10 | Pass/fail check: a chip passes when its cumulative roll exceeds one minus its tier's yield percentage (both configurable) | S | B4, A4 | A 90% yield process needs a cumulative roll above 0.10 to pass |

## Epic C: Lithography line

Build the scanner as one machine with upgradeable slots, then add per-tier components; the EUV subsystem (C9) is the largest single piece.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| C1 | Scanner machine with slots for light source, optics, mask, resist and stage | L | A2, B7 | A scanner runs a layer using slot stats |
| C2 | Light sources: mercury lamp, g/i-line, KrF, ArF, high-power ArF | M | C1 | Each tier's source changes resolution and throughput |
| C3 | Optics: lens, immersion lens, EUV mirrors, anamorphic mirrors | M | C1 | Each tier's optics changes resolution and defectivity |
| C4 | Mask item with layer count and grade, consumed per run | M | C1, D2 | Runs consume masks and fail without them |
| C5 | Resist consumables per tier | S | C1 | Wrong or missing resist blocks a run |
| C6 | Wafer stage tiers (overlay and throughput) | S | C1 | Stage tier changes overlay and speed |
| C7 | Immersion fluid handling (T5, T6): ultra-pure water supply and defect risk | M | C1, D4 | Immersion scanners need and use ultra-pure water |
| C8 | Multi-pass patterning loop for optional T6 | M | B7, C1 | A layer can run 2 to 4 passes with compounding yield loss |
| C9 | EUV subsystem: tin droplet source, CO2 drive laser, collector wear, vacuum, hydrogen purge, pellicle | L | C1, D4 | An EUV scanner runs only with all subsystems healthy |
| C10 | High-NA: half-field exposures that must be stitched per die | M | C9 | A full die takes two exposures unless upgraded |
| C11 | Throughput model: wafers per hour sets recipe time | S | C1, A2 | Run time scales with source power and stage tier |

## Epic D: Support lines and fab utilities

Three lines with three generations each, plus utilities (test and packaging sit in Epic E); these can be built in parallel with Epic C once the foundation exists.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| D1 | Substrate line G1 to G3: silicon refining, crystal puller, slicer and polisher | L | B1, A5 | Each generation makes larger, flatter, lower-defect wafers |
| D2 | Mask shop G1 to G3: pattern generator, e-beam writer, multibeam writer and EUV blanks; mask data prep consumes network compute | L | A5, base-mod API | A mask set can be crafted and gets costlier with layer count |
| D3 | Deposition and etch G1 to G3 as paired stations | L | A5 | Layer cycles require the right generation per tier |
| D4 | Fab utilities: FE power (costs configurable), ultra-pure water, process gases, cooling, hookup blocks, reusing Arcforge machines, fluids and gases where possible | L | A4 | Machines fail or slow when a utility is short |
| D5 | Cleanroom model: cleanliness tier changes defectivity | M | A2, D4 | A dirtier room measurably lowers yield |
| D6 | Support-module capacity: one module serves several scanners | S | D1, D3 | Throughput scales with modules, not duplicates |
| D7 | Enforce the staggered per-tier generation requirements | S | A5, D1 to D4 | A tier blocks runs when a line is below its required generation |
| D8 | Mask-making core-count choice (1, 2, 4, 8, 12, 24) from T6, with a configurable penalty that punishes high core counts on immature processes | M | D2, B4, A3 | The choice is offered from T6 and the penalty follows the config curve |

## Epic E: Test, packaging, chips and base-mod integration

This epic turns wafers into chips and connects them to the compute and memory network; E5 builds on the co-mods' existing server and disk recipes, and E8 replaces the old chip items.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| E1 | Test station: reads each wafer's accumulated yield, applies a pass/fail check against the tier's target speed and outputs passing chips, with failed chips becoming scrap | M | B4 | Chip output reflects the rolled yields |
| E2 | Dicing: wafers leave the FOUP and become unpackaged dies | S | E1 | A tested wafer becomes dies |
| E3 | Packaging G1 to G3: wire bond, flip-chip, advanced packaging for stacked chips | M | A5, E2 | Each generation produces finished chips of rising quality |
| E4 | Chip items for compute, logic, memory and NAND: one stackable item per type and tier, with a fixed speed and thread count | M | A3 | All four chip types exist per tier and stack in inventories |
| E5 | Co-mod integration: chips become ingredients for Encoded Logistics servers and storage disks (compute: more items per craft; memory: more job memory; NAND: denser disks; logic: general ingredient) | L | E4, co-mod recipes | Servers and disks crafted from the new chips behave as designed |
| E6 | NAND 3D stack track: layer count driven by deposition and etch generation, not lithography tier | M | D3, E4 | NAND capacity grows with stack generation above T5 |
| E7 | Enforce the logic plateau at T5 | S | A3, E4 | Logic made above T5 is no better and costs more |
| E8 | Replace Encoded Logistics' existing chip items, Processor Dies and Lithography Press recipes (co-developed in that mod) | M | E4 | Old items convert or are removed, and server and disk recipes use the new chips |
| E9 | Scrap item for failed chips, with a recycling recipe | S | E1 | Failed chips come out as scrap that recycles into materials |

## Epic F: Recipes, balance and progression

The T7 cliff and the optional T6 are balance problems more than code problems, so reserve real playtest time here.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| F1 | Recipe set per tier for machines, components and consumables | L | Epics C, D, E | Every tier is craftable end to end |
| F2 | Balance pass on cost, power and speed per tier, with extra attention to the T7 cliff | L | F1 | Playtesters can reach T7 without a wall or a trivial jump |
| F3 | Make T6 optional: T7 stays reachable from T5, and T6 acts as a soft landing | S | C8, F1 | Both routes reach T7 |
| F4 | Progression gating (advancements or quests) that unlocks tiers | M | F1 | Tiers unlock in order with clear goals |
| F5 | Config presets, for example casual and realistic | S | A4, F2 | Presets change difficulty without editing files |

## Epic G: UX, tooling and testing

A fab has many moving parts, so players need clear feedback on what is limiting their line.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| G1 | In-game guidebook covering tiers, support lines, FOUPs and yield | M | F1 | Every machine has an entry |
| G2 | Machine UIs with status and bottleneck indicators per line | M | C1, D4 | A player can see why a line is slow |
| G3 | Recipe viewer integration (JEI or EMI style) | S | F1 | All recipes appear in the viewer |
| G4 | Automated tests for the stat model, yield engine and config | M | A2, B4 | Tests run on every build |
| G5 | Playtest saves and balance logging from T1 to T8 | M | F1 | Logs show time and resources per tier |
| G6 | Performance pass on FOUP data size and tick cost for large fabs | M | B3, B7 | A large fab holds stable tick time |

## Horizon: metrology and wafer screening

This work is outside the first release, but Epic B should not block it: keep per-wafer yield data readable by a future device without changing the FOUP format.

| ID | Task | Size | Depends on | Done when |
| --- | --- | --- | --- | --- |
| H1 | FOUP unload port that moves wafers into a metrology device | M | B3 | Wafers can leave and return to a FOUP |
| H2 | Tiered metrology devices (optical, scatterometry, e-beam, actinic) revealing more yield detail at higher tiers | L | H1, B4 | Better devices expose finer yield information |
| H3 | Discard and rework flow so poor wafers are removed before expensive steps | M | H2 | A discarded wafer never reaches later stations |
| H4 | Metrology UI showing per-wafer results | M | H2 | A player can act on measured results |
