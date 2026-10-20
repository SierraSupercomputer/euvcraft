# T3 source verification

The T3 source at `1bdd94f1a1fdabc768464ca25e9053749fd160bf` passed 750 unit tests in 86 classes, core import checks, release-jar assembly and all 895 distinct server cases. All 225 source-registered project IDs were present and passed; there were no failures, errors or skips. All 165 previous project IDs are retained and passing; 60 project cases were added. The independent fixture-disabled shipping probe also passed its one required case with both new machines and all eight support recipes loaded. The independent integration candidate and final source reviews are subsequent gates, not claimed complete by this report.

## Playable increment

- Boule Purifier: a native 2x2x3 support cell, initially empty 16,000 mB water tank, exact bucket exchange, and ordinary boule + 1,000 mB water -> pure boule. Default cost is 400 ticks at 80 FE/tick (32,000 FE).
- E-beam Mask Writer: the same support geometry, seven GUI-selected designs, explicit Write mask action, and generation-2 provenance. Defaults are one blank mask + 32 redstone, 1,200 ticks at 2,000 FE/tick (2,400,000 FE). File IDs select the corresponding recipe, including independently editable quantities and costs.
- Pure substrate survives slicing, processing, save/load and public FOUP views. Tooltips expose substrate, quantity and layer progress without hidden rolls or lot identity. Old FOUP saves and three-field masks retain ordinary/G1 defaults; explicit unknown grades are rejected.
- T3 admits pure wafers, G2 masks, the five T3 line upgrades and T3 resist. Logic, memory, compute and NAND complete 24 layers and package into the corresponding T3 chips. Compute remains fixed at one core, including actual yield-layer calls and restored jobs. Pure wafers also complete T1/T2 with G1 masks. T4 remains unavailable; core choices still begin at T6.
- New crafting upgrades consume T2 logic chips. There is no chip-crafting or pure-boule-crafting bypass. All material, duration, FE and water balances remain editable JSON. The reference contains 49 recipe files: 47 production and two excluded test fixtures.

## Verification and repairs

The full server suite exposed a missing compute-core argument in the native layer call; the coordinator repaired it and reran the complete suite. Earlier incomplete worker drafts, provider failures, invalid fixture APIs, world-coordinate mistakes, stale inventories and failed attempts remain preserved in coordinator records. A provider504 admission run is retained as an unverified draft, not an accepted model completion. Only pinned free workers were selected; the coordinator performed integration and validation.

Actual server checks cover selected-recipe quantities and exact FE, bucket and output conservation, power/formation/output pauses, one-time completion, save/load, removed recipes and changed mask-use config, G1 template normalization, T3 admission refusals, same-lot grade tampering, malformed and legacy saved contracts, real support-matrix replacement, all four T3 chip chains, and older-tier compatibility. Source IDs were enumerated independently from registrations.

The release archive contains the new T3 items, textures, four support-block assets and crafting/loot, both machine definitions and all eight support recipes. Eight test-machine fixture resources are absent; required production harvesting tags are present. SHA-256 at the functional source check: `0585441f1dda49d4b49da05778c4dcc0e6d043b3751a97fdd8cbf768492a26f1`.

Both texture generators checked 121 PNGs and 180 generated JSON assets. The JSON-derived recipe and T3 references pass after restoring canonical LF bytes expanded by Windows checkout. No existing sprite was replaced. New support blocks reuse the joined lithography panels.

## Limits and owner checks

Rendered-client confirmation of the new T3 screens, formation panels and standalone output-slot repairs remains owner-pending. Automated menu/slot tests do not substitute for that visual check. See [the T3 walkthrough](../manual-walkthrough-t3.md) and [JSON balance table](../t3-slice.md).

The water tank is separate from bucket handling for later pipe support; fluid/item automation is not implemented. T4, recycling, FOUP Mk2 crafting and Encoded Logistics recipe replacements remain deferred. Balances are starter values for owner iteration. Nothing was pushed or installed into a client/world.

Coordinator receipts: `t3-combined-repaired-full-check.json`, `t3-combined-repaired-full-unit-counts.json`, `t3-combined-repaired-full-runtime-counts.json`, `t3-combined-repaired-full-project-coverage.json` and `t3-combined-repaired-full-jar-release.json` under the coordinator's private `.orchestrator/t3/` directory.
