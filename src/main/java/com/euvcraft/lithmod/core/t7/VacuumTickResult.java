package com.euvcraft.lithmod.core.t7;

import java.util.Objects;

/**
 * Result of advancing one chamber by one tick.
 *
 * <p>{@link #paused()} is the only consequence of a routine vacuum loss: the
 * caller holds the current process. This type deliberately has no scrap or damage
 * operation, so a vacuum shortage can never destroy a lot inside this pure model.</p>
 *
 * @param state                     chamber state after the tick
 * @param demandMicroTorrPerTick    service the tick asked for
 * @param suppliedMicroTorrPerTick  service actually supplied from local pump,
 *                                  central allocation and local reserve
 * @param processingAllowed         whether the chamber is at or below its operating pressure
 * @param paused                    whether a PROCESS tick was not allowed to run
 */
public record VacuumTickResult(
    VacuumChamberState state,
    int demandMicroTorrPerTick,
    int suppliedMicroTorrPerTick,
    boolean processingAllowed,
    boolean paused) {

    /**
     * Validates the result and the integer conservation between demand and supply.
     *
     * @throws NullPointerException     if {@code state} is null
     * @throws IllegalArgumentException if the demand is negative, the supply is
     *                                  outside {@code [0, demand]}, or a paused
     *                                  result claims processing was allowed
     */
    public VacuumTickResult {
        Objects.requireNonNull(state, "state must not be null");
        if (demandMicroTorrPerTick < 0) {
            throw new IllegalArgumentException("demandMicroTorrPerTick must be >= 0, got " + demandMicroTorrPerTick);
        }
        if (suppliedMicroTorrPerTick < 0 || suppliedMicroTorrPerTick > demandMicroTorrPerTick) {
            throw new IllegalArgumentException(
                "suppliedMicroTorrPerTick must be in [0, " + demandMicroTorrPerTick + "], got "
                    + suppliedMicroTorrPerTick);
        }
        if (paused && processingAllowed) {
            throw new IllegalArgumentException("a paused result cannot allow processing");
        }
    }

    /** Returns the unmatched service for this tick, {@code demand - supplied}. */
    public int shortfallMicroTorrPerTick() {
        return this.demandMicroTorrPerTick - this.suppliedMicroTorrPerTick;
    }
}
