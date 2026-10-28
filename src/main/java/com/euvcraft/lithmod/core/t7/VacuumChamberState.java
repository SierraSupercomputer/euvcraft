package com.euvcraft.lithmod.core.t7;

import java.util.Objects;

/**
 * Immutable pressure and local-reserve state of one independent vacuum chamber.
 *
 * <p>Every chamber owns its own instance; there is no shared instantaneous
 * pressure. A central plant only contributes integer service units through
 * {@link #tick(VacuumPumpSpec, VacuumMode, int)}, so one chamber can never vent,
 * equalise with, or borrow pressure from another. A disconnected chamber simply
 * receives zero central service and continues on its local pump and reserve.</p>
 *
 * <p><strong>Units.</strong> {@code pressureMicroTorr} and {@code reserveMicroTorr}
 * are microtorr ({@code µTorr}). {@link VacuumPumpSpec} says how many microtorr per
 * tick the local pump, leak, process and reserve add or remove. Pressure is bounded
 * to {@code [0, MAX_PRESSURE_MICROTORR]}. The reserve is bounded by the spec's
 * reserve capacity.</p>
 *
 * <p>This type has no Minecraft or NeoForge dependencies and no world access.</p>
 *
 * @param pressureMicroTorr chamber pressure, {@code 0..MAX}
 * @param reserveMicroTorr  service stored in the local reserve, {@code >= 0}
 */
public record VacuumChamberState(int pressureMicroTorr, int reserveMicroTorr) {

    /** Model ceiling: no vacuum at all. Lower pressure is better. */
    public static final int MAX_PRESSURE_MICROTORR = 1_000_000;

    /**
     * Validates the bounds.
     *
     * @throws IllegalArgumentException if the pressure is outside
     *                                  {@code [0, MAX]} or the reserve is negative
     */
    public VacuumChamberState {
        if (pressureMicroTorr < 0 || pressureMicroTorr > MAX_PRESSURE_MICROTORR) {
            throw new IllegalArgumentException(
                "pressureMicroTorr must be in [0, " + MAX_PRESSURE_MICROTORR + "], got " + pressureMicroTorr);
        }
        if (reserveMicroTorr < 0) {
            throw new IllegalArgumentException("reserveMicroTorr must be >= 0, got " + reserveMicroTorr);
        }
    }

    /** Returns an evacuated chamber with an empty reserve. */
    public static VacuumChamberState evacuated() {
        return new VacuumChamberState(0, 0);
    }

    /** Returns an evacuated chamber with the given local reserve. */
    public static VacuumChamberState evacuated(int reserveMicroTorr) {
        return new VacuumChamberState(0, reserveMicroTorr);
    }

    /** Returns a chamber at the given pressure and reserve. */
    public static VacuumChamberState at(int pressureMicroTorr, int reserveMicroTorr) {
        return new VacuumChamberState(pressureMicroTorr, reserveMicroTorr);
    }

    /** Returns whether processing is allowed at the current pressure. */
    public boolean canProcess(VacuumPumpSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return this.pressureMicroTorr <= spec.operatingPressureMicroTorr();
    }

    /** Returns whether the chamber is above its operating pressure and needs pumping down. */
    public boolean isAboveOperatingPressure(VacuumPumpSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return this.pressureMicroTorr > spec.operatingPressureMicroTorr();
    }

    /**
     * Returns the integer service this chamber would ask from a central plant for
     * one tick, after subtracting its own local pump. The local reserve is not
     * included: it is the fallback spent only when the allocation is short.
     *
     * @param spec configuration of this chamber, not null
     * @param mode what the chamber is doing this tick, not null
     * @return request in microtorr per tick, never negative
     */
    public int centralDemandMicroTorrPerTick(VacuumPumpSpec spec, VacuumMode mode) {
        int required = requiredServiceMicroTorrPerTick(spec, mode);
        return Math.max(0, required - spec.pumpRateMicroTorrPerTick());
    }

    /**
     * Returns the total service this chamber needs for one tick, including the
     * local pump's share and any process/pump-down load.
     *
     * @param spec configuration of this chamber, not null
     * @param mode what the chamber is doing this tick, not null
     * @return required service in microtorr per tick
     */
    public int requiredServiceMicroTorrPerTick(VacuumPumpSpec spec, VacuumMode mode) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        int inflow = inflowMicroTorrPerTick(spec, mode);
        int pumpDown = pumpDownDemandMicroTorrPerTick(spec, mode);
        return inflow + pumpDown;
    }

    /**
     * Advances this chamber by one tick.
     *
     * <p>The local pump always runs. Central service is added on top. If the total
     * is still short, the local reserve covers as much of the shortfall as it can.
     * Any remaining shortfall raises the pressure; it never scraps a lot. A surplus
     * recharges the reserve up to its capacity. The new pressure is clamped to the
     * model bounds, so a hostile or extreme configuration cannot leave the state
     * space.</p>
     *
     * @param spec                         chamber configuration, not null
     * @param mode                         what the chamber is doing this tick, not null
     * @param centralServiceMicroTorrPerTick service granted by the optional central
     *                                     plant, {@code >= 0}; zero for a disconnected chamber
     * @return the next state and the pause signal
     * @throws NullPointerException     if {@code spec} or {@code mode} is null
     * @throws IllegalArgumentException if the central service is negative
     */
    public VacuumTickResult tick(VacuumPumpSpec spec, VacuumMode mode, int centralServiceMicroTorrPerTick) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (centralServiceMicroTorrPerTick < 0) {
            throw new IllegalArgumentException(
                "centralServiceMicroTorrPerTick must be >= 0, got " + centralServiceMicroTorrPerTick);
        }

        int inflow = inflowMicroTorrPerTick(spec, mode);
        int pumpDown = pumpDownDemandMicroTorrPerTick(spec, mode);
        long required = (long) inflow + pumpDown;
        // IDLE and PROCESS also recover toward the operating pressure; a dedicated
        // PUMP_DOWN tick additionally runs the local pump at full rate.

        long available = (long) spec.pumpRateMicroTorrPerTick() + centralServiceMicroTorrPerTick;
        long shortfall = Math.max(0L, required - available);
        int usedReserve = (int) Math.min(shortfall, this.reserveMicroTorr);
        long supplied = Math.min(required, available + usedReserve);

        long nextPressure = (long) this.pressureMicroTorr + inflow - supplied;
        int boundedPressure = (int) Math.max(0L, Math.min(MAX_PRESSURE_MICROTORR, nextPressure));

        int nextReserve;
        if (available > required) {
            long surplus = available - required;
            long recharged = Math.min(spec.reserveRechargeMicroTorrPerTick(), surplus);
            nextReserve = (int) Math.min(spec.reserveCapacityMicroTorr(), (long) this.reserveMicroTorr + recharged);
        } else {
            nextReserve = this.reserveMicroTorr - usedReserve;
        }

        VacuumChamberState next = new VacuumChamberState(boundedPressure, nextReserve);
        boolean allowed = next.canProcess(spec);
        boolean paused = mode == VacuumMode.PROCESS && !allowed;
        return new VacuumTickResult(next, (int) required, (int) supplied, allowed, paused);
    }

    private static int inflowMicroTorrPerTick(VacuumPumpSpec spec, VacuumMode mode) {
        long inflow = spec.idleLeakMicroTorrPerTick();
        if (mode == VacuumMode.PROCESS) {
            inflow += spec.processLoadMicroTorrPerTick();
        }
        return (int) Math.min(Integer.MAX_VALUE, inflow);
    }

    private int pumpDownDemandMicroTorrPerTick(VacuumPumpSpec spec, VacuumMode mode) {
        if (mode == VacuumMode.PUMP_DOWN) {
            return spec.pumpRateMicroTorrPerTick();
        }
        int excess = Math.max(0, this.pressureMicroTorr - spec.operatingPressureMicroTorr());
        return Math.min(spec.pumpRateMicroTorrPerTick(), excess);
    }
}
