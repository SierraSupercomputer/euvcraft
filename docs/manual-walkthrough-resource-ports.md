# Cleanroom item and fluid port recheck

Restart with the updated project jar. Existing ports need no replacement or new world. Their face normal is still chosen by the face clicked during placement; connect only the two opposite faces on that axis.

1. Place an item port in the cleanroom wall, with an Arcforge item conduit touching each axis face. Connect a chest on each side. Configure the outside network to extract from the source chest and insert into the port; configure the inside network to extract from the port and insert into the destination chest. Transfer a known number of items, then reverse those modes and confirm they return exactly once. Arcforge's OUTPUT setting extracts from an endpoint; INPUT supplies an endpoint.
2. Repeat with a fluid port, fluid conduits, two tanks and a known quantity of water. Reverse the modes and check the quantity again. Also test the room's normal water/ultrapure-water arrangement, using separate ports or fully draining the buffer before changing fluid.
3. Block the destination and supply items/fluid. The port keeps its buffered contents; transfer resumes when destination space becomes available. Save/reopen while contents are buffered and confirm that they persist.
4. Break a loaded item port: all buffered items drop once. Fluid and FE contents are lost on breaking their ports, as for ordinary tanks/buffers.
5. Check that the cleanroom remains enclosed with the ports installed. Ports do not need their own FE and do not change room cleanliness rules.

The item port is a shared nine-slot inventory. The fluid port is a shared single-fluid 16,000 mB tank; different fluids cannot mix. Ports do not pull from adjacent inventories/tanks automatically. External automation must insert/extract; ports can automatically deliver to directly touching compatible receivers on either axis face. Use sided inventories or explicit conduit modes to control direction. Neither face is a permanently designated input/output.

Limits are editable in data/lithmod/machines/cleanroom_item_port.json and cleanroom_fluid_port.json. transfer_per_tick is the shared automatic-delivery budget (16 items/tick and 1,000 mB/tick); external insert/extract operations use inventory/tank capacity. fluid_capacity is the tank capacity. Lowering it preserves stored surplus and refuses further insertion until space exists. The shared machine schema requires positive energy_capacity/max_receive placeholders (1); these ports expose no FE capability and charge no energy.

Automated server checks are recorded separately; this walkthrough remains owner-pending.
