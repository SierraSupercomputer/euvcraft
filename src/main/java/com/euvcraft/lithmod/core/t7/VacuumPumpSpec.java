package com.euvcraft.lithmod.core.t7;

/**
 * Configurable vacuum behaviour of one chamber plus its dedicated local pump.
 *
 * <p><strong>Units.</strong> All rates are microtorr per tick ({@code µTorr/tick}).
 * A pressure of {@link VacuumChamberState#MAX_PRESSURE_MICROTORR} is the model's
 * bounded "no vacuum" ceiling; lower is better. Values are integers so that two
 * sides of an allocation always agree exactly and never drift through floating
 * point.</p>
 *
 * <p>The local pump is always available to its own chamber, even when the optional
 * central plant is disconnected or shut down. A central allocation is extra
 * capacity on top of {@link #pumpRateMicroTorrPerTick()}; it never creates vacuum
 * on its own. This type has no Minecraft or NeoForge dependencies.</p>
 *
 * @param pumpRateMicroTorrPerTick       local pump capacity, {@code 1..1_000_000}
 * @param idleLeakMicroTorrPerTick       pressure added per idle tick, {@code 0..1_000_000}
 * @param processLoadMicroTorrPerTick    extra pressure added per processing tick, {@code 0..1_000_000}
 * @param operatingPressureMicroTorr     highest pressure at which processing may run, {@code 1..MAX}
 * @param reserveCapacityMicroTorr       local reserve buffer size, {@code 0..1_000_000}
 * @param reserveRechargeMicroTorrPerTick reserve refill per surplus tick, {@code 0..reserveCapacity}
 */
public record VacuumPumpSpec(
    int pumpRateMicroTorrPerTick,
    int idleLeakMicroTorrPerTick,
    int processLoadMicroTorrPerTick,
    int operatingPressureMicroTorr,
    int reserveCapacityMicroTorr,
    int reserveRechargeMicroTorrPerTick) {

    /** Largest rate or pressure value accepted by this model. */
    public static final int MAX_RATE_MICROTORR_PER_TICK = 1_000_000;

    /**
     * Validates the configuration.
     *
     * @throws IllegalArgumentException if the pump rate is below one or any value is
     *                                  outside its documented bound, or the recharge
     *                                  exceeds the reserve capacity
     */
    public VacuumPumpSpec {
        if (pumpRateMicroTorrPerTick < 1 || pumpRateMicroTorrPerTick > MAX_RATE_MICROTORR_PER_TICK) {
            throw new IllegalArgumentException(
                "pumpRateMicroTorrPerTick must be in [1, " + MAX_RATE_MICROTORR_PER_TICK + "], got "
                    + pumpRateMicroTorrPerTick);
        }
        requireRate(idleLeakMicroTorrPerTick, "idleLeakMicroTorrPerTick");
        requireRate(processLoadMicroTorrPerTick, "processLoadMicroTorrPerTick");
        if (operatingPressureMicroTorr < 1
            || operatingPressureMicroTorr > VacuumChamberState.MAX_PRESSURE_MICROTORR) {
            throw new IllegalArgumentException(
                "operatingPressureMicroTorr must be in [1, "
                    + VacuumChamberState.MAX_PRESSURE_MICROTORR + "], got " + operatingPressureMicroTorr);
        }
        if (reserveCapacityMicroTorr < 0 || reserveCapacityMicroTorr > MAX_RATE_MICROTORR_PER_TICK) {
            throw new IllegalArgumentException(
                "reserveCapacityMicroTorr must be in [0, " + MAX_RATE_MICROTORR_PER_TICK + "], got "
                    + reserveCapacityMicroTorr);
        }
        requireRate(reserveRechargeMicroTorrPerTick, "reserveRechargeMicroTorrPerTick");
        if (reserveRechargeMicroTorrPerTick > reserveCapacityMicroTorr) {
            throw new IllegalArgumentException(
                "reserveRechargeMicroTorrPerTick (" + reserveRechargeMicroTorrPerTick
                    + ") cannot exceed reserveCapacityMicroTorr (" + reserveCapacityMicroTorr + ")");
        }
    }

    private static void requireRate(int value, String name) {
        if (value < 0 || value > MAX_RATE_MICROTORR_PER_TICK) {
            throw new IllegalArgumentException(
                name + " must be in [0, " + MAX_RATE_MICROTORR_PER_TICK + "], got " + value);
        }
    }
}
