# Semiconductor Fab Add-on: Player Guide

Oct 12, 2026 · @Sierra

## Overview

The Fab Add-on, an addon for Encoded Logistics and Arcforge, lets you manufacture the chips your storage network's servers and disks are built from: you build a chip fab, push silicon wafers through it, and the chips that come out become ingredients for better servers and storage disks.

Chips come in four kinds: compute, logic, memory and NAND. Each starts as wafers carried in 25-wafer lots through a lithography line and a set of support lines. Across eight lithography tiers, from a mercury-lamp contact printer to a High-NA EUV scanner, your chips get faster and denser, and every tier adds a new challenge: cleaner rooms, more power, better masks, tighter yield.

You are never forced to scrap old tech. Cheap logic chips for SSD and industrial controllers stop improving at mid-tier DUV, so an older line stays the best choice for them while newer lines feed your leading-edge compute.

## The four chip types

Each chip type improves a different part of your network and leans on lithography in a different way, which is what keeps older lines useful.

| Chip | Role in your network | How it improves | What it asks of the fab |
| --- | --- | --- | --- |
| Compute | Ingredient for servers that process more items at a time; thread counts arrive at a later tier (EUV proposed) | Gains at every tier | The best lithography you can build, strictest yield |
| Logic | A simple crafting ingredient (control silicon for SSDs and industrial controls) | Gains up to mid-tier DUV (T5), then stops | A cheap DUV line is enough; newer tiers only add cost |
| Memory (DRAM) | Ingredient for servers that hold more items in memory, so you can run larger craft jobs | Gains at every tier | Dense, regular layouts; benefits from multi-patterning and EUV; a more forgiving yield floor |
| NAND | Ingredient for higher-density storage disks | Planar gains early, then layer count takes over | Deposition and etch lines matter more than the scanner once stacking unlocks |

Logic is available from the first tier, memory from T2, and compute and NAND from T3. Every chip is a stackable ingredient with a fixed speed for its tier.

## The eight lithography tiers

Every tier swaps in a better light source and optics, and each adds one new problem to solve. Feature sizes below are rough real-world anchors.

| Tier | Light source | Smallest feature | What it unlocks | New challenge |
| --- | --- | --- | --- | --- |
| T1 Contact | Mercury arc lamp | \~5 µm | First logic chips | Masks touch wafers, so defects are high |
| T2 Stepper | g-line then i-line lamp | \~1 to 0.5 µm | Memory chips, reduction optics | Flat wafers and precise alignment |
| T3 KrF DUV | 248 nm excimer laser | \~250 to 180 nm | Compute and NAND begin; industrial logic | Mask correction (OPC) needs computing power |
| T4 ArF dry | 193 nm excimer laser | \~90 to 65 nm | Denser everything | Hitting the limit of dry lenses |
| T5 ArF immersion | 193 nm with water between lens and wafer | \~45 to 28 nm | Best logic (SSD controllers); 3D NAND unlocks | Ultra-pure water handling and bubble defects |
| T6 Multi-patterning (optional) | Same ArF source, several passes per layer | \~22 to 7 nm effective | A stepping stone to near-EUV density | Many more masks, slower runs, compounding yield loss |
| T7 EUV | 13.5 nm tin plasma, mirrors in vacuum | \~13 nm half-pitch | Thread count for compute | Megawatt power, tin and collector upkeep, vacuum, pellicles |
| T8 High-NA EUV | Same source, higher power, larger optics | \~8 nm half-pitch | Top-end compute and memory | Half-size exposures that must be stitched, extreme cost |

T7 is the biggest jump in the game, which is intentional. T6 exists to soften it, and players who are ready can skip T6 entirely.

## Support lines and fab utilities

Lithography is only one part of the fab: five support lines feed it, each with three generations, so progress is spread across the whole factory instead of one machine.

| Line | Gen 1 | Gen 2 | Gen 3 |
| --- | --- | --- | --- |
| Substrate (silicon refining, crystal growth, slicing, polish) | Small boules, 50 to 150 mm | 200 to 300 mm, epitaxial wafers | Ultra-flat, low-defect 300 mm |
| Mask shop (makes your reticles) | Optical pattern generator | E-beam mask writer | Multibeam writer, EUV blanks, actinic inspection |
| Deposition and etch | Thermal oxide, sputtering, wet etch | CVD, plasma etch, copper plating | Atomic-layer deposition, high-k gates, high-aspect-ratio etch |
| Fab utilities (power, ultra-pure water, gases, cooling) | Basic hookups | Ultra-pure water and chemical delivery | Megawatt power, hydrogen, chilled-water loop |
| Test and packaging | Probe test, wire bond | Binning, flip-chip packaging | Advanced packaging for stacked chips |

Each line steps up at a different tier, so no tier asks you to upgrade everything at once:

| Line | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Substrate | G1 | G1 | G2 | G2 | G2 | G3 | G3 | G3 |
| Mask shop | G1 | G1 | G2 | G2 | G2 | G2 | G3 | G3 |
| Deposition and etch | G1 | G1 | G1 | G2 | G2 | G2 | G3 | G3 |
| Fab utilities | G1 | G1 | G1 | G1 | G2 | G2 | G3 | G3 |
| Test and packaging | G1 | G1 | G1 | G2 | G2 | G2 | G2 | G3 |

Older generations keep earning their place. Cheap logic chips still run well on Gen 2 wafers and packaging long after you have built Gen 3 for compute. All fab utilities run on FE, and their power costs are configurable.

## Wafers, FOUPs and yield

Your wafers travel in FOUPs, sealed lots of 25 (the size is configurable), and every process step quietly rolls a yield value for each wafer inside.

1. One silicon boule is sliced into wafers that fill a FOUP.
2. In early fabs you carry FOUPs between stations by hand, next to your utility hookups. Mk2 FOUPs travel through AE2-style pipes.
3. At each lithography layer the lot passes through coat, expose, develop and etch or deposit, then repeats for the next mask layer. Higher tiers need more layers, so runs take longer and carry more risk.
4. Wafers leave the FOUP only at packaging, where they are tested, diced and become chips.

Yield works like this:

- Each step rolls a hidden yield value for every wafer, stored on the wafer. You do not see it directly.
- The rolls are shaped by your line: cleanliness, how well your light source and optics are maintained, and which generation of each support line you run.
- At test, the accumulated rolls decide how many chips pass the tier's target speed. A chip either passes or fails, there are no grades, and passing chips stack, so a rough line costs you quantity.
- Once thread counts appear, mask-making includes a deliberate decision that applies yield modifiers, so a bold choice can pay off or cost you.
- Yield is the product of every step's roll, so more layers mean more chances to lose wafers.

The game shows a realistic step count for each recipe (about 15 at T1 up to roughly 1,500 at T8) as a stat, but you only manage a handful of stations and the layer loop.

## Progression and choices

The game pushes you up the tiers, but it lets you decide how, and it never makes your older lines worthless.

- **The EUV cliff.** T7 asks for a new light source, vacuum, megawatt power, hydrogen, a better mask shop and Gen 3 utilities at once. That is meant to feel like a real step change.
- **Optional T6.** If the cliff is too steep, multi-patterning lets you reach near-EUV density on your existing ArF hardware, at the cost of many more masks, slower runs and compounding yield loss. Players who are ready can skip T6 and go straight from T5 to T7.
- **Hard gates.** Thread count and top-end compute require EUV or better, so you cannot stay on old lithography forever.
- **Logic stays cheap.** Logic chips stop improving at T5, so an old DUV line remains the best place to make them.
- **Choose where to spend.** One support module can serve several scanners, so you upgrade modules to remove bottlenecks rather than duplicating machines.

## A sample playthrough

A typical run from first wafer to leading-edge compute:

1. **T1.** Refine silicon, grow a small Gen 1 boule, and carry the first FOUP by hand to a contact printer. You make basic logic chips and your first upgrade to the network.
2. **T2.** A stepper and i-line lamp unlock memory chips. Alignment and flat wafers start to matter.
3. **T3.** KrF DUV unlocks compute and NAND. Gen 2 substrate and mask shop arrive, and mask correction starts consuming your own network's compute.
4. **T4 to T5.** Mk2 FOUPs move through pipes, immersion needs ultra-pure water, and 3D NAND unlocks. Logic reaches its best form here, so you keep a cheap line for it.
5. **T6 (optional).** Multi-patterning gets you near-EUV density on old hardware, with slow, risky runs. Skip it if you are ready.
6. **T7.** The EUV jump: tin source, vacuum, hydrogen, megawatt power and Gen 3 utilities. Compute gains thread count.
7. **T8.** High-NA optics, stitched exposures and advanced packaging produce your best compute and memory.

## Coming later: metrology and screening

A later update plans to let you unload wafers from a FOUP into a metrology device that reads the yield rolled in earlier steps, so you can discard poor wafers before they reach expensive steps. Metrology devices will be tiered like the rest of the fab, with better tools revealing more of what happened to each wafer. This is a future goal and is not part of the first release.
