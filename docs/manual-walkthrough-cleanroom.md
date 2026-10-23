# T5 cleanroom foundation walkthrough

This increment adds cleanroom infrastructure and wafer exposure. The FOUP cleaner, contaminated FOUP state, T6 calibration resets, T7 repairs/abort/scrapping, reflow and airlocks remain deferred. Existing T1–T4 processing and pick-and-place assembly have no cleanroom penalty. Existing saves do not require conversion; newly placed controllers start dirty. An older partially processed T5 FOUP has no historical exposure samples, so only subsequent paid ticks are measured; an already-complete older lot is not retroactively changed.

## 1. Build a room

Use cleanroom floor, wall or window blocks for every boundary of a hollow cuboid, including ceiling and corners. Any approved shell block may occupy any boundary face. Start with an outer 9×7×11 room (interior7×5×9) to leave space around a 3×3×5 scanner. Default maximum outer span32 per axis and maximum interior volume16384. Standalone cuboids are supported; keep a one-block gap between separate room shells and avoid attached spare cleanroom panels that enlarge the discovered connected shell. Arbitrary shapes and shared-wall/airlock layouts are deferred.

Replace one shell block with one Cleanroom Controller and several shell blocks with Cleanroom Filters. All filters and the controller require their own FE supply. One controller per enclosed room; a controller inside the interior also invalidates enclosure. All scanner member blocks must lie strictly inside the room for its yield benefit. An opening for ordinary pipes is a breach; use sealed ports instead.

Open the controller. It should show Enclosed, interior dimensions/volume, contamination in PPM, cleanliness, yield factor, controller power, active filters, open doors, trend and approximate recovery time. All text belongs inside its opaque GUI. If a known room is breached, it retains its footprint and reports a fault coordinate. A new incomplete build may simply report Unformed until its geometry is recognizable.

## 2. Power and filtration

Supply continuous FE to the controller and every filter. Each controller costs100FE/t and each working filter50FE/t by default, charged in20-tick service intervals. Put Cleanroom Filter Medium into each filter GUI; the single slot also supports transactional item automation. One medium lasts12000 working ticks (10 minutes). FE buffers hold200000 by default.

New rooms start dirty (10001PPM,50% room factor). Let filtration run while chunks are loaded. Cleanliness rises linearly; PPM approaches1 and room yield approaches100% nonlinearly. Larger rooms have greater baseline leakage and need more powered filters. At the suggested interior315 blocks, baseline leakage is984 score per second and one filter provides5000; occupants and doors add further leakage. Several filters shorten the wait. There is no offline purification.

Stop a filter's FE or let medium expire: filtration stops and ordinary leakage slowly dirties the room. Stop controller FE: deterioration is much faster and all filtration stops. Refill FE/media and verify recovery resumes without resetting a valid room. Breaking/replacing the controller creates a fresh dirty controller.

## 3. Door and gown

Install a paired Cleanroom Door in a wall with support below it. It uses iron-door behavior: redstone opens it; hand use does not. An open intact door produces a small leak, counted once for both halves. Removing either half is a shell breach. Redstone wiring/buttons/levers can sit outside the shell.

Enter without a gown and observe worse trend. Equip Cleanroom Hood, Suit, Trousers and Boots in all four armor slots: occupant contamination is reduced tenfold by default but remains positive. Missing one piece loses protection. Automation remains preferable to player traffic.

## 4. Sealed utility ports

Place Cleanroom Power, Fluid and Item Ports anywhere on the shell. The clicked support face determines the through-wall axis; either face forwards to the opposite adjacent endpoint. Place against the appropriate support face for a wall, ceiling or floor. Transverse and null-side requests expose no handler; floor and ceiling ports also support vertical transport.

Connect Arcforge-compatible FE/fluid/item transport outside and a matching handler or conduit immediately inside. Each port exposes only its own resource type. These are pass-throughs, not tanks, batteries or inventories. Both directions are supported subject to the actual endpoint's rules (a water inlet may accept but refuse extraction). Wrong items/fluids are governed by the destination. Port chains are bounded to eight lenses. Avoid unnecessary chains. Verify water delivery to the immersion scanner/utility setup and FE across a closed shell; try medium supply through an item port to a filter. A broken shell port is a breach like any other shell block.

## 5. T5 wafer yield

Repeat the existing T5 scanner walkthrough, including both calibrations, masks, resist and ultrapure water. Outside a room, T5 processing still operates with a50% room factor. Inside a dirty room its factor starts at50%; a pristine room reaches100%. Calibration remains a separate frozen factor.

Each paid processing tick samples the room factor. Paused ticks do not count. A half-pristine/half-outside run has a75% room factor. Breaking a wall during a T5 run reduces subsequent yield; it does not yet scrap or abort wafers. The average exposure persists in the FOUP through saves, pauses, moves and completed layers, and is applied once at completion. Packaging/high binning uses the resulting wafer state without a second cleanroom charge. Emptying the carrier clears its exposure. No hidden rolls or exposure history are sent in public FOUP packets.

## 6. Persistence and safety

Save/quit with an enclosed working room, then reload. Cleanliness, FE and remaining medium life should persist; elapsed real time adds no purification. A known footprint with unavailable chunks reports Unknown and suspends room simulation and associated scanner processing. Chunk validation must never force-load missing room chunks. After all chunks are available, processing resumes. A scanner for which no room has ever been discovered is treated as outside; this loaded-room index does not search unloaded worlds for unknown rooms.

Check that the game closes normally. Revisit creative search and the pick-and-place formed texture to ensure the previously accepted fixes remain intact.

## Editable balance

Cleanroom rates, bounds and yield/PPM defaults: src/main/resources/data/lithmod/cleanroom/default.json. Service machine buffers: data/lithmod/machines/cleanroom_controller.json and cleanroom_filter.json. Crafting recipes: data/lithmod/recipe/crafting/cleanroom_*.json. Apply balance edits via a datapack and resource reload; new recipes/art are reproduced by tools/generate-cleanroom-assets.mjs, so deliberate art edits also need generator updates. Values are initial gameplay defaults, not physical cleanroom classifications.
