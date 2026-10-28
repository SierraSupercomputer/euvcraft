package com.euvcraft.lithmod.core.t7;

import java.util.Objects;

/**
 * Pure, persistent contamination state of one T7 machine.
 *
 * <p>A structural cleanroom breach during active T7 wafer processing moves the
 * machine to {@link Phase#CONTAMINATED}. It stays there until a repair bill of
 * material, UPW and FE is supplied once, then moves to
 * {@link Phase#AWAITING_RECALIBRATION}. Only a successful recalibration returns it
 * to {@link Phase#CLEAN}.</p>
 *
 * <p>The state machine is deliberately idempotent at its boundaries:</p>
 * <ul>
 *   <li>a repeated breach notification never consumes anything and never restarts
 *       the repair;</li>
 *   <li>{@link #repair(ContaminationRepairCost)} reports {@code charged = true}
 *       exactly once, on the transition out of {@link Phase#CONTAMINATED};</li>
 *   <li>{@link #recalibrate()} reports {@code recalibrated = true} exactly once.</li>
 * </ul>
 *
 * <p>The caller owns the actual inventory/energy removal; this type only says
 * whether a charge is due. Empty and unpowered machines are distinct outcomes of
 * {@link #notifyStructuralBreach(BreachConditions, long)} and never become
 * contaminated. The existing T6 models are not changed.</p>
 *
 * <p>This type has no Minecraft or NeoForge dependencies and no world access.</p>
 *
 * @param phase          current condition, never null
 * @param lastBreachTick game tick of the last contaminating breach, or {@code -1}
 */
public record MachineContaminationState(Phase phase, long lastBreachTick) {

    /** The three persistent phases. */
    public enum Phase {
        /** Machine is clean and may run. */
        CLEAN,
        /** Structural breach; repair has not been completed. */
        CONTAMINATED,
        /** Repair materials were supplied; recalibration is still required. */
        AWAITING_RECALIBRATION
    }

    /**
     * Result of a breach notification.
     *
     * @param state        state after the notification
     * @param notification what the notification did
     */
    public record BreachResult(MachineContaminationState state, BreachNotification notification) {
        public BreachResult {
            Objects.requireNonNull(state, "state must not be null");
            Objects.requireNonNull(notification, "notification must not be null");
        }
    }

    /**
     * Result of a repair attempt.
     *
     * @param state   state after the attempt
     * @param charged whether the caller must consume the supplied bill now
     */
    public record RepairResult(MachineContaminationState state, boolean charged) {
        public RepairResult {
            Objects.requireNonNull(state, "state must not be null");
        }
    }

    /**
     * Result of a recalibration attempt.
     *
     * @param state        state after the attempt
     * @param recalibrated whether recalibration completed
     */
    public record RecalibrationResult(MachineContaminationState state, boolean recalibrated) {
        public RecalibrationResult {
            Objects.requireNonNull(state, "state must not be null");
        }
    }

    /**
     * Validates the state.
     *
     * @throws NullPointerException     if {@code phase} is null
     * @throws IllegalArgumentException if the breach tick is negative and the phase
     *                                  is not CLEAN
     */
    public MachineContaminationState {
        Objects.requireNonNull(phase, "phase must not be null");
        if (phase != Phase.CLEAN && lastBreachTick < 0) {
            throw new IllegalArgumentException("a contaminated state requires a non-negative breach tick");
        }
        if (phase == Phase.CLEAN && lastBreachTick < -1) {
            throw new IllegalArgumentException("lastBreachTick must be >= -1, got " + lastBreachTick);
        }
    }

    /** Returns a clean machine that has never been breached. */
    public static MachineContaminationState clean() {
        return new MachineContaminationState(Phase.CLEAN, -1);
    }

    /**
     * Returns a contaminated machine.
     *
     * @param tick tick of the contaminating breach, {@code >= 0}
     * @return a contaminated state
     */
    public static MachineContaminationState contaminated(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, got " + tick);
        }
        return new MachineContaminationState(Phase.CONTAMINATED, tick);
    }

    /** Returns whether the machine may run without repair or recalibration. */
    public boolean isOperational() {
        return this.phase == Phase.CLEAN;
    }

    /** Returns whether the machine is contaminated and a repair bill is due. */
    public boolean requiresRepair() {
        return this.phase == Phase.CONTAMINATED;
    }

    /** Returns whether the machine was repaired and still needs recalibration. */
    public boolean requiresRecalibration() {
        return this.phase == Phase.AWAITING_RECALIBRATION;
    }

    /**
     * Applies a structural cleanroom breach notification.
     *
     * <p>Only a powered, loaded, actively processing T7 machine can become newly
     * contaminated. The result is idempotent: once contaminated, every later
     * notification returns {@link BreachNotification#ALREADY_CONTAMINATED} with the
     * same state and consumes nothing.</p>
     *
     * @param conditions physical circumstances, not null
     * @param tick       current game tick, {@code >= 0}
     * @return the notification outcome
     * @throws NullPointerException     if {@code conditions} is null
     * @throws IllegalArgumentException if {@code tick} is negative
     */
    public BreachResult notifyStructuralBreach(BreachConditions conditions, long tick) {
        Objects.requireNonNull(conditions, "conditions must not be null");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, got " + tick);
        }
        if (this.phase != Phase.CLEAN) {
            return new BreachResult(this, BreachNotification.ALREADY_CONTAMINATED);
        }
        if (!conditions.powered()) {
            return new BreachResult(this, BreachNotification.IGNORED_UNPOWERED);
        }
        if (!conditions.loaded()) {
            return new BreachResult(this, BreachNotification.IGNORED_EMPTY);
        }
        if (!conditions.activeT7Processing()) {
            return new BreachResult(this, BreachNotification.IGNORED_NOT_PROCESSING);
        }
        return new BreachResult(contaminated(tick), BreachNotification.NEWLY_CONTAMINATED);
    }

    /**
     * Applies a completed material + UPW + FE repair.
     *
     * <p>The returned {@code charged} flag is the single-use contract: it is true
     * only for the transition out of {@link Phase#CONTAMINATED}. A repeated call
     * while awaiting recalibration, or a call on a clean machine, changes nothing
     * and returns false, so a duplicated notification or retry cannot consume a
     * second bill.</p>
     *
     * @param cost configured repair bill, not null
     * @return the resulting phase and whether to consume the bill
     * @throws NullPointerException if {@code cost} is null
     */
    public RepairResult repair(ContaminationRepairCost cost) {
        Objects.requireNonNull(cost, "cost must not be null");
        if (this.phase != Phase.CONTAMINATED) {
            return new RepairResult(this, false);
        }
        return new RepairResult(new MachineContaminationState(Phase.AWAITING_RECALIBRATION, this.lastBreachTick), true);
    }

    /**
     * Completes recalibration after a repair.
     *
     * @return the clean state and whether recalibration actually happened
     */
    public RecalibrationResult recalibrate() {
        if (this.phase != Phase.AWAITING_RECALIBRATION) {
            return new RecalibrationResult(this, false);
        }
        return new RecalibrationResult(new MachineContaminationState(Phase.CLEAN, this.lastBreachTick), true);
    }

    /**
     * Prints only the phase and tick, never any hidden cost or inventory detail.
     */
    @Override
    public String toString() {
        return "MachineContaminationState[phase=" + this.phase + ", breachTick=" + this.lastBreachTick + "]";
    }
}
