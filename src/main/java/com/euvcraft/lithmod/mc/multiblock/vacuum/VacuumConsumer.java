package com.euvcraft.lithmod.mc.multiblock.vacuum;

import net.minecraft.core.BlockPos;

/**
 * Interface for vacuum consumers (G3 prep, EUV source, scanner) to participate in the optional
 * central vacuum service network.
 *
 * <p>Each consumer maintains its own local pump and reserve baseline. The central plant allocates
 * capacity fairly; the consumer receives grants and applies them through its own
 * {@link com.euvcraft.lithmod.core.t7.VacuumChamberState} tick.</p>
 *
 * <p>The coordinator integrates these into other groups later. This interface is public API
 * for consumer controllers to implement; do not reference non-existent concrete groups directly.</p>
 */
public interface VacuumConsumer {

    /**
     * Returns the anchor position of this consumer's controller, used for deduplication
     * when multiple ports/loops belong to the same consumer.
     *
     * @return the controller position, never null
     */
    BlockPos vacuumOwnerPos();

    /**
     * Returns the integer vacuum demand this consumer requests from a central plant for the
     * current tick, in microtorr per tick. This is the shortfall after the local pump's share,
     * as computed by the consumer's own {@link com.euvcraft.lithmod.core.t7.VacuumChamberState}.
     *
     * @return demand in µTorr/tick, {@code >= 0}
     */
    int vacuumDemand();

    /**
     * Accepts a vacuum grant from the central plant for the given tick.
     *
     * <p>The grant is valid only for the matching {@code tick} or the immediately previous tick
     * (epoch tolerance for desynchronized tick loops). The consumer must apply this grant to its
     * own chamber tick and return whether processing is allowed (pressure at or below operating
     * pressure). A grant of zero means no central service this tick.</p>
     *
     * @param tick   the plant's current tick counter (epoch)
     * @param grant  central service granted this tick, in µTorr/tick, {@code >= 0}
     * @return whether processing is allowed after applying the grant
     */
    boolean acceptVacuumGrant(long tick, int grant);
}