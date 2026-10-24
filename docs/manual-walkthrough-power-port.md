# Cleanroom power port with Arcforge conduits

The power port is now a buffered FE endpoint, allowing separate conduit networks on either side of a sealed wall to exchange power. The previous forwarding-only implementation could reach a directly adjacent machine but could not bridge two Arcforge conduits.

1. Keep the power port's axis aligned through the wall. It accepts/transfers FE on both opposite axis faces, including floor/ceiling placement, and rejects the other four faces. No new world or port replacement is required.
2. Build: charged source → outside energy conduit → cleanroom power port → inside energy conduit → receiving machine/power port.
3. Configure the outside conduit to extract from the source and insert into the cleanroom port. Configure the inside conduit to extract from the cleanroom port and insert into the machine. Do not assume both conduit sides should have the same mode. In the installed Arcforge API, endpoint OUTPUT extracts from that endpoint; endpoint INPUT supplies it.
4. Confirm the receiving machine gains FE and the source loses it. The port may temporarily retain energy while the inside network is blocked, disabled or unloaded; that charge survives save/reload. A directly adjacent receiver also receives buffered power automatically.
5. Disconnect the destination, let the port charge, save/reload, then reconnect. Verify delivery resumes without duplicated charge. Breaking a charged port loses its stored FE, like a machine energy buffer; it does not drop an energy item.

Defaults are editable in `data/lithmod/machines/cleanroom_power_port.json`: 200,000 FE capacity; `max_receive` limits each insertion to 20,000 FE; `transfer_per_tick` limits each extraction to 20,000 FE and sets the shared 20,000 FE per-tick budget for automatic direct-neighbor delivery. The two limits can be edited independently. Conduit throughput remains subject to its tier and settings. The port does not generate or consume power for itself.

Item/fluid forwarding and cleanroom contamination rules are unchanged by this repair. Their capabilities are not energy-network bridges.
