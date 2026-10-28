package com.euvcraft.lithmod.mc.multiblock.vacuum;

/**
 * Interface for owned member ports (conduits, passthroughs, service outlets) that expose
 * a {@link VacuumConsumer} for the central plant's BFS discovery.
 *
 * <p>A port returns its owning consumer, or {@code null} if unowned or the consumer
 * is not currently formed. The plant uses this to discover live consumers on the network.</p>
 */
public interface VacuumAccess {

    /**
     * Returns the vacuum consumer owning this port, or {@code null} if none.
     *
     * @return the consumer, or {@code null}
     */
    VacuumConsumer vacuumConsumer();
}