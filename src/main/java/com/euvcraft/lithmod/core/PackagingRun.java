package com.euvcraft.lithmod.core;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * The single-lot queue of the test and package machine: it works one wafer at a time
 * for a frozen tick count under a frozen energy and package schedule.
 *
 * <p>The run holds a {@link PackagingPlan} and advances through it in wafer order. Every
 * call to {@link #tick(IntPredicate, IntPredicate, IntPredicate)} is one server tick
 * spent on the current wafer:</p>
 *
 * <ol>
 *   <li>a finished run does nothing, draws nothing and consumes nothing;</li>
 *   <li>a passing wafer pauses before anything is paid when the packages it needs are
 *       not available (a failing wafer works with zero packages and is never asked);</li>
 *   <li>the run pauses when the current wafer's target output lacks room;</li>
 *   <li>then the full energy for the tick must be paid at once; a partial draw would be
 *       unrefundable with a real Forge Energy buffer, so the payment is asked as an
 *       all-or-nothing predicate: {@code true} when the whole amount was taken, {@code
 *       false} when nothing was taken. A payment of zero is trivially paid but is still
 *       asked for;</li>
 *   <li>the wafer's progress moves by one tick; a blocked tick moves nothing and pays
 *       nothing.</li>
 * </ol>
 *
 * <p>On the tick that finishes a wafer the run returns one {@link Completion} event with
 * the exact output and package amounts and advances to the next wafer; the state moves
 * in the same step, so no wafer can ever be emitted twice. The wrapper performs the
 * package consumption and output writes atomically on the event. Pauses are
 * transparent: a paused run resumes with the same progress and the same costs as if it
 * never stopped.</p>
 *
 * <p>The run exposes only generic state: counts, progress and frozen parameters. The
 * current wafer's pass flag is hidden result data and is never readable from the run;
 * it appears only in the completion event, where the wafer is already output. What may
 * be stored and restored again is the {@link Snapshot}.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class PackagingRun {

    /** The plan this run works through; hidden result data lives inside it. */
    private final PackagingPlan plan;

    /** Ticks one wafer takes, frozen at construction. */
    private final int ticksPerWafer;

    /** Energy each active tick draws, frozen at construction. */
    private final int energyPerTick;

    /** Index of the wafer being worked, or {@code plan.size()} when finished. */
    private int currentIndex;

    /** Ticks already paid for on the current wafer. */
    private int paidTicks;

    /**
     * Validates and stores the frozen state of a run.
     *
     * @throws NullPointerException     if the plan is null
     * @throws IllegalArgumentException if ticks per wafer is below one, energy per tick
     *                                  is negative, the index is outside the plan, or
     *                                  the progress is inconsistent with the terminal state
     */
    private PackagingRun(PackagingPlan plan, int ticksPerWafer, int energyPerTick,
                         int currentIndex, int paidTicks) {
        checkState(plan, ticksPerWafer, energyPerTick, currentIndex, paidTicks);
        this.plan = plan;
        this.ticksPerWafer = ticksPerWafer;
        this.energyPerTick = energyPerTick;
        this.currentIndex = currentIndex;
        this.paidTicks = paidTicks;
    }

    /**
     * Creates a run that has not started any wafer.
     *
     * @param plan          the judged plan to work through, not null
     * @param ticksPerWafer ticks one wafer takes, at least one
     * @param energyPerTick energy each active tick draws, zero or more
     * @throws NullPointerException     if the plan is null
     * @throws IllegalArgumentException if a parameter is out of range
     */
    public PackagingRun(PackagingPlan plan, int ticksPerWafer, int energyPerTick) {
        this(plan, ticksPerWafer, energyPerTick, 0, 0);
    }

    /**
     * Restores a run from a snapshot, at exactly the progress it was saved at.
     *
     * @param snapshot the snapshot to restore, not null
     * @throws NullPointerException     if the snapshot or its plan is null
     * @throws IllegalArgumentException if the snapshot state is inconsistent
     */
    public PackagingRun(Snapshot snapshot) {
        this(Objects.requireNonNull(snapshot, "snapshot must not be null").plan(),
            snapshot.ticksPerWafer(), snapshot.energyPerTick(),
            snapshot.currentIndex(), snapshot.paidTicks());
    }

    private static void checkState(PackagingPlan plan, int ticksPerWafer, int energyPerTick,
                                   int currentIndex, int paidTicks) {
        Objects.requireNonNull(plan, "plan must not be null");
        if (ticksPerWafer < 1) {
            throw new IllegalArgumentException("ticksPerWafer must be >= 1, got " + ticksPerWafer);
        }
        if (energyPerTick < 0) {
            throw new IllegalArgumentException("energyPerTick must be >= 0, got " + energyPerTick);
        }
        int size = plan.size();
        if (currentIndex < 0 || currentIndex > size) {
            throw new IllegalArgumentException(
                "current index " + currentIndex + " is outside a plan of " + size + " wafers");
        }
        if (currentIndex == size) {
            if (paidTicks != 0) {
                throw new IllegalArgumentException("a finished run holds no partial progress");
            }
        } else {
            if (paidTicks < 0 || paidTicks >= ticksPerWafer) {
                throw new IllegalArgumentException(
                    "paid ticks " + paidTicks + " are outside a wafer of " + ticksPerWafer + " ticks");
            }
        }
    }

    /**
     * Works the current wafer for one tick.
     *
     * <p>The packages and the output room are checked before the energy payment, so a
     * blocked tick buys nothing. The energy payment must be all-or-nothing: {@code
     * true} means the full amount was taken and the tick counts, {@code false} means
     * nothing was taken and the run pauses. Neither predicate is consulted about the
     * current wafer's result; the package predicate is not asked at all for a failing
     * wafer.</p>
     *
     * @param packagesAvailable whether the requested number of packages is available;
     *                          the argument is the amount the current wafer will consume
     * @param outputRoom        whether the current wafer's target output can take the
     *                          argument-many items
     * @param payEnergy         whether a full payment of the argument-many energy was
     *                          taken from the buffer
     * @return the completion event when this tick finished the current wafer, or
     *         {@code null} when the run paused or only made partial progress
     * @throws NullPointerException if a predicate is null and the run is not finished
     */
    public Completion tick(IntPredicate packagesAvailable, IntPredicate outputRoom, IntPredicate payEnergy) {
        if (isFinished()) {
            return null;
        }
        Objects.requireNonNull(packagesAvailable, "packagesAvailable must not be null");
        Objects.requireNonNull(outputRoom, "outputRoom must not be null");
        Objects.requireNonNull(payEnergy, "payEnergy must not be null");

        int waferIndex = this.currentIndex;
        int packagesNeeded = this.plan.packagesFor(waferIndex);
        if (packagesNeeded > 0 && !packagesAvailable.test(packagesNeeded)) {
            return null;
        }
        int amount = this.plan.chipsPerWafer();
        if (!outputRoom.test(amount)) {
            return null;
        }
        if (!payEnergy.test(this.energyPerTick)) {
            return null;
        }
        this.paidTicks++;
        if (this.paidTicks < this.ticksPerWafer) {
            return null;
        }
        this.paidTicks = 0;
        this.currentIndex = waferIndex + 1;
        return new Completion(waferIndex, this.plan.passes(waferIndex), amount, packagesNeeded);
    }

    /**
     * Returns how many wafers the plan covers.
     *
     * @return the total wafer count
     */
    public int totalWafers() {
        return this.plan.size();
    }

    /**
     * Returns how many wafers have been fully completed, which is also the index of the
     * wafer currently being worked while the run is unfinished.
     *
     * @return the completed wafer count
     */
    public int wafersDone() {
        return this.currentIndex;
    }

    /**
     * Returns the ticks already paid for on the current wafer.
     *
     * @return the paid tick count, zero on a finished run
     */
    public int ticksPaidOnCurrentWafer() {
        return this.paidTicks;
    }

    /**
     * Returns the frozen ticks one wafer takes.
     *
     * @return the ticks per wafer
     */
    public int ticksPerWafer() {
        return this.ticksPerWafer;
    }

    /**
     * Returns the frozen energy each active tick draws.
     *
     * @return the energy per tick
     */
    public int energyPerTick() {
        return this.energyPerTick;
    }

    /**
     * Returns whether every wafer of the plan has been completed.
     *
     * @return {@code true} when nothing is left to work and extra ticks change nothing
     */
    public boolean isFinished() {
        return this.currentIndex >= this.plan.size();
    }

    /**
     * Saves the exact state of this run for storage and restore.
     *
     * @return an immutable snapshot of the plan, the schedule and the progress
     */
    public Snapshot save() {
        return new Snapshot(this.plan, this.ticksPerWafer, this.energyPerTick,
            this.currentIndex, this.paidTicks);
    }

    /**
     * Prints the run without its hidden result data. Only the generic progress counts
     * the menu shows appear here.
     *
     * @return a description with the done count, total, progress and finished state only
     */
    @Override
    public String toString() {
        return "PackagingRun[done=" + this.currentIndex + " of " + this.plan.size()
            + ", paid=" + this.paidTicks + " of " + this.ticksPerWafer + " ticks"
            + (isFinished() ? ", finished]" : "]");
    }

    /**
     * One wafer's completion event, carrying exactly what the wrapper must write and
     * consume in the same server tick.
     *
     * <p>The amount is chips for a passing wafer and scrap for a failing one; the
     * packages are the frozen package amount for a passing wafer and zero for a failing
     * one. The result flag is server data that becomes an output the moment the event
     * is handled, so it is never printed.</p>
     *
     * @param waferIndex   position of the completed wafer in the plan's wafer order
     * @param passing      whether the wafer passed the judgment
     * @param amount       chips a passing wafer outputs, or scrap a failing one outputs
     * @param packagesUsed packages the passing wafer consumes, zero for a failing one
     */
    public record Completion(int waferIndex, boolean passing, int amount, int packagesUsed) {

        /**
         * Prints the event without its result flag.
         *
         * @return a description with the index, amount and packages only
         */
        @Override
        public String toString() {
            return "Completion[wafer=" + this.waferIndex + ", amount=" + this.amount
                + ", packages=" + this.packagesUsed + "]";
        }
    }

    /**
     * Everything needed to restore a run at exactly the saved progress: the plan, the
     * frozen schedule, the current index and the paid ticks of the wafer in progress.
     *
     * <p>This is server-side data because the plan holds the hidden pass flags; it must
     * never reach a client. The flag list is immutably copied by the plan itself.</p>
     *
     * @param plan          the plan being worked, not null
     * @param ticksPerWafer ticks one wafer takes, at least one
     * @param energyPerTick energy each active tick draws, zero or more
     * @param currentIndex  index of the wafer in progress, {@code 0..plan.size()};
     *                      {@code plan.size()} means the run is finished
     * @param paidTicks     ticks already paid for on the wafer in progress; must be
     *                      zero on a finished snapshot and below {@code ticksPerWafer}
     *                      otherwise
     */
    public record Snapshot(PackagingPlan plan, int ticksPerWafer, int energyPerTick,
                           int currentIndex, int paidTicks) {

        /**
         * Validates the snapshot consistency.
         *
         * @throws NullPointerException     if the plan is null
         * @throws IllegalArgumentException if a frozen parameter is out of range, the
         *                                  index is outside the plan, or the progress
         *                                  is inconsistent with the terminal state
         */
        public Snapshot {
            checkState(plan, ticksPerWafer, energyPerTick, currentIndex, paidTicks);
        }
    }
}
