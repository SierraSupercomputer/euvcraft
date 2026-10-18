package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-wafer-at-a-time queue of the test and package machine.
 *
 * <p>Every expected event, energy total and package total is computed by hand from the
 * fixture schedule, never read back from the run: for example three passing wafers of
 * 3 ticks each at 10 FE per tick draw 3 x 3 x 10 = 90 FE and emit three events of
 * {@code chipsPerWafer}. Blocked ticks must buy nothing, extra ticks on a finished run
 * must do nothing, and a restored run must replay the identical tail of events.</p>
 */
class PackagingRunTest {

    private static final List<Boolean> ALL_PASS = List.of(true, true, true);
    private static final List<Boolean> ALL_FAIL = List.of(false, false, false);

    /** Four wafers: passing at slots 0 and 3. */
    private static final List<Boolean> MIXED = List.of(true, false, false, true);

    /** A room/availability gate that counts how often the run asked it. */
    private static final class Gate implements IntPredicate {
        private int calls;
        private boolean open;

        Gate with(boolean open) {
            this.open = open;
            return this;
        }

        @Override
        public boolean test(int amount) {
            this.calls++;
            return this.open;
        }

        int calls() {
            return this.calls;
        }
    }

    /** All-or-nothing energy book: it records what was actually drawn, not what was asked. */
    private static final class EnergyBook implements IntPredicate {
        private int calls;
        private long feDrawn;
        private boolean paid;

        EnergyBook with(boolean paid) {
            this.paid = paid;
            return this;
        }

        @Override
        public boolean test(int amount) {
            this.calls++;
            if (this.paid) {
                this.feDrawn += amount;
            }
            return this.paid;
        }

        int calls() {
            return this.calls;
        }

        long feDrawn() {
            return this.feDrawn;
        }
    }

    private static Gate alwaysOpen() {
        return new Gate().with(true);
    }

    private static EnergyBook alwaysPaying() {
        return new EnergyBook().with(true);
    }

    /** Ticks the run to the end through an all-open schedule, collecting its events. */
    private static List<PackagingRun.Completion> runToCompletion(PackagingRun run,
                                                                 Gate packages,
                                                                 Gate outputRoom,
                                                                 EnergyBook energy) {
        List<PackagingRun.Completion> events = new ArrayList<>();
        for (int tick = 0; tick < 1000 && !run.isFinished(); tick++) {
            PackagingRun.Completion event = run.tick(packages, outputRoom, energy);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    // ---------- full runs, chipsPerWafer 1 and 4 ----------

    @Test
    void allPassAtChips4EmitsThreeOrderedChipEventsOfNinetyFe() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_PASS, 4, 2), 3, 10);
        Gate packages = alwaysOpen();
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        List<PackagingRun.Completion> events = runToCompletion(run, packages, outputRoom, energy);

        assertEquals(3, events.size());
        int chips = 0;
        int used = 0;
        for (int i = 0; i < 3; i++) {
            PackagingRun.Completion event = events.get(i);
            assertEquals(i, event.waferIndex());
            assertTrue(event.passing());
            assertEquals(4, event.amount());
            assertEquals(2, event.packagesUsed());
            chips += event.amount();
            used += event.packagesUsed();
        }
        // 3 passing wafers x 4 chips, 3 x 2 packages, 9 paid ticks x 10 FE.
        assertEquals(12, chips);
        assertEquals(6, used);
        assertEquals(90L, energy.feDrawn());
        assertTrue(run.isFinished());
        assertEquals(3, run.wafersDone());
        assertEquals(0, run.ticksPaidOnCurrentWafer());
    }

    @Test
    void allPassAtChips1Packages1FinishesOnZeroEnergy() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_PASS, 1, 1), 1, 0);
        EnergyBook energy = alwaysPaying();
        List<PackagingRun.Completion> events =
            runToCompletion(run, alwaysOpen(), alwaysOpen(), energy);

        assertEquals(3, events.size());
        for (PackagingRun.Completion event : events) {
            assertTrue(event.passing());
            assertEquals(1, event.amount());
            assertEquals(1, event.packagesUsed());
        }
        // A zero payment is trivially paid but is still asked each tick.
        assertEquals(3, energy.calls());
        assertEquals(0L, energy.feDrawn());
    }

    @Test
    void allFailAtChips4NeverTouchesPackagesAndYieldsScrapOnly() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_FAIL, 4, 2), 2, 10);
        Gate packages = new Gate().with(false); // closed: a failing wafer must never ask
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        List<PackagingRun.Completion> events = runToCompletion(run, packages, outputRoom, energy);

        assertEquals(3, events.size());
        int scrap = 0;
        for (int i = 0; i < 3; i++) {
            PackagingRun.Completion event = events.get(i);
            assertEquals(i, event.waferIndex());
            assertFalse(event.passing());
            assertEquals(4, event.amount());
            assertEquals(0, event.packagesUsed());
            scrap += event.amount();
        }
        assertEquals(12, scrap);
        assertEquals(0, packages.calls());
        // 3 failing wafers of 2 ticks each still work: 6 paid ticks x 10 FE.
        assertEquals(60L, energy.feDrawn());
    }

    @Test
    void allFailAtChips1Packages1ConsumesNoPackages() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_FAIL, 1, 3), 1, 0);
        Gate packages = new Gate().with(false);
        List<PackagingRun.Completion> events = runToCompletion(run, packages, alwaysOpen(), alwaysPaying());

        assertEquals(3, events.size());
        for (PackagingRun.Completion event : events) {
            assertFalse(event.passing());
            assertEquals(1, event.amount());
            assertEquals(0, event.packagesUsed());
        }
        assertEquals(0, packages.calls());
    }

    @Test
    void mixedRunAtChips4MatchesIndependentlyComputedTotals() {
        PackagingRun run = new PackagingRun(new PackagingPlan(MIXED, 4, 2), 1, 5);
        EnergyBook energy = alwaysPaying();
        List<PackagingRun.Completion> events = runToCompletion(run, alwaysOpen(), alwaysOpen(), energy);

        assertEquals(4, events.size());
        int passingAmount = 0;
        int failingAmount = 0;
        int packages = 0;
        for (int i = 0; i < 4; i++) {
            PackagingRun.Completion event = events.get(i);
            assertEquals(i, event.waferIndex());
            // The fixture mix: slots 0 and 3 pass, slots 1 and 2 fail.
            assertEquals(i == 0 || i == 3, event.passing());
            assertEquals(4, event.amount());
            if (event.passing()) {
                passingAmount += event.amount();
                packages += event.packagesUsed();
                assertEquals(2, event.packagesUsed());
            } else {
                failingAmount += event.amount();
                assertEquals(0, event.packagesUsed());
            }
        }
        // 2 passing and 2 failing wafers: 8 chips, 8 scrap, 4 packages, 20 FE.
        assertEquals(8, passingAmount);
        assertEquals(8, failingAmount);
        assertEquals(4, packages);
        assertEquals(20L, energy.feDrawn());
    }

    @Test
    void mixedRunAtChips1MatchesIndependentlyComputedTotals() {
        PackagingRun run = new PackagingRun(new PackagingPlan(MIXED, 1, 1), 1, 0);
        List<PackagingRun.Completion> events = runToCompletion(run, alwaysOpen(), alwaysOpen(), new EnergyBook().with(true));

        assertEquals(4, events.size());
        int passingAmount = 0;
        int packages = 0;
        for (PackagingRun.Completion event : events) {
            assertEquals(1, event.amount());
            if (event.passing()) {
                passingAmount += event.amount();
                packages += event.packagesUsed();
                assertEquals(1, event.packagesUsed());
            } else {
                assertEquals(0, event.packagesUsed());
            }
        }
        // 2 passing of 4 wafers: 2 chips and 2 packages, 2 scrap without packages.
        assertEquals(2, passingAmount);
        assertEquals(2, packages);
    }

    // ---------- pauses: packages, output room, energy ----------

    @Test
    void missingPackagesPauseBeforeEnergyOrOutputAreTouched() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 1, 3), 1, 7);
        Gate packages = new Gate().with(false);
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = alwaysPaying();

        assertNull(run.tick(packages, outputRoom, energy));
        assertEquals(0, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        assertEquals(1, packages.calls());
        assertEquals(0, outputRoom.calls());
        assertEquals(0, energy.calls());
        assertEquals(0L, energy.feDrawn());

        packages.with(true);
        PackagingRun.Completion event = run.tick(packages, outputRoom, energy);
        assertNotNull(event);
        assertEquals(0, event.waferIndex());
        assertTrue(event.passing());
        assertEquals(1, event.amount());
        assertEquals(3, event.packagesUsed());
        assertEquals(7L, energy.feDrawn());
        assertTrue(run.isFinished());
    }

    @Test
    void fullOutputPausesBeforeEnergyIsPaid() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 5, 2), 1, 10);
        Gate packages = alwaysOpen();
        Gate outputRoom = new Gate().with(false);
        EnergyBook energy = alwaysPaying();

        assertNull(run.tick(packages, outputRoom, energy));
        assertEquals(0, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        // The blocked tick buys nothing: packages were asked, energy was not.
        assertEquals(1, packages.calls());
        assertEquals(1, outputRoom.calls());
        assertEquals(0, energy.calls());
        assertEquals(0L, energy.feDrawn());

        outputRoom.with(true);
        PackagingRun.Completion event = run.tick(packages, outputRoom, energy);
        assertNotNull(event);
        assertEquals(5, event.amount());
        assertEquals(2, event.packagesUsed());
        assertEquals(10L, energy.feDrawn());
    }

    @Test
    void energyPauseConservesProgressAndDrawsOnlyPaidTicks() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 4, 2), 3, 10);
        Gate packages = alwaysOpen();
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = new EnergyBook();

        energy.with(false);
        assertNull(run.tick(packages, outputRoom, energy));
        assertEquals(0, run.ticksPaidOnCurrentWafer());
        assertEquals(0L, energy.feDrawn());

        energy.with(true);
        assertNull(run.tick(packages, outputRoom, energy)); // one paid tick of three
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        assertEquals(10L, energy.feDrawn());

        energy.with(false);
        assertNull(run.tick(packages, outputRoom, energy)); // a blocked tick buys nothing
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        assertEquals(10L, energy.feDrawn());

        energy.with(true);
        assertNull(run.tick(packages, outputRoom, energy)); // second paid tick
        assertEquals(2, run.ticksPaidOnCurrentWafer());
        PackagingRun.Completion event = run.tick(packages, outputRoom, energy); // third completes
        assertNotNull(event);
        assertEquals(0, event.waferIndex());
        assertTrue(event.passing());
        assertEquals(4, event.amount());
        assertEquals(2, event.packagesUsed());
        // Only the three paid ticks drew anything: 3 x 10 FE.
        assertEquals(30L, energy.feDrawn());
        assertTrue(run.isFinished());
        assertEquals(1, run.wafersDone());
        assertEquals(0, run.ticksPaidOnCurrentWafer());
    }

    @Test
    void zeroEnergyPaymentIsStillAllOrNothing() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 2, 1), 1, 0);
        Gate packages = alwaysOpen();
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = new EnergyBook().with(false);

        assertNull(run.tick(packages, outputRoom, energy));
        assertEquals(0, run.wafersDone());
        assertEquals(0, run.ticksPaidOnCurrentWafer());

        energy.with(true);
        PackagingRun.Completion event = run.tick(packages, outputRoom, energy);
        assertNotNull(event);
        assertEquals(2, event.amount());
        assertEquals(1, event.packagesUsed());
        assertEquals(0L, energy.feDrawn());
        assertTrue(run.isFinished());
    }

    // ---------- finished runs and emission boundaries ----------

    @Test
    void finishedRunDoesNothingOnExtraTicks() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_PASS, 4, 2), 1, 5);
        Gate packages = alwaysOpen();
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        runToCompletion(run, packages, outputRoom, energy);
        assertEquals(3, run.wafersDone());

        int packagesBefore = packages.calls();
        int outputBefore = outputRoom.calls();
        int energyBefore = energy.calls();
        long feBefore = energy.feDrawn();
        for (int i = 0; i < 5; i++) {
            assertNull(run.tick(packages, outputRoom, energy));
        }
        // A finished run consults nothing and emits nothing, ever.
        assertEquals(packagesBefore, packages.calls());
        assertEquals(outputBefore, outputRoom.calls());
        assertEquals(energyBefore, energy.calls());
        assertEquals(feBefore, energy.feDrawn());
        assertTrue(run.isFinished());
        assertEquals(3, run.wafersDone());
        assertEquals(0, run.ticksPaidOnCurrentWafer());

        PackagingRun.Snapshot finished = run.save();
        assertEquals(3, finished.currentIndex());
        assertEquals(0, finished.paidTicks());
    }

    @Test
    void everyWaferIsEmittedExactlyOnceInOrder() {
        PackagingRun run = new PackagingRun(new PackagingPlan(MIXED, 4, 2), 1, 1);
        List<PackagingRun.Completion> events = runToCompletion(run, alwaysOpen(), alwaysOpen(), alwaysPaying());

        List<Integer> indexes = new ArrayList<>(4);
        for (PackagingRun.Completion event : events) {
            indexes.add(event.waferIndex());
        }
        assertEquals(List.of(0, 1, 2, 3), indexes);
        assertNull(run.tick(alwaysOpen(), alwaysOpen(), alwaysPaying()));
    }

    @Test
    void unfinishedRunRejectsMissingPredicates() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 1, 1), 1, 0);
        assertThrows(NullPointerException.class, () -> run.tick(null, alwaysOpen(), alwaysPaying()));
        assertThrows(NullPointerException.class, () -> run.tick(alwaysOpen(), null, alwaysPaying()));
        assertThrows(NullPointerException.class, () -> run.tick(alwaysOpen(), alwaysOpen(), null));
    }

    // ---------- snapshots restore the identical result ----------

    @Test
    void midWaferSnapshotRestoresTheIdenticalResult() {
        PackagingPlan plan = new PackagingPlan(MIXED, 4, 2);

        // Master: run straight through an open schedule. 4 wafers x 3 ticks x 10 FE.
        PackagingRun master = new PackagingRun(plan, 3, 10);
        List<PackagingRun.Completion> masterEvents =
            runToCompletion(master, alwaysOpen(), alwaysOpen(), alwaysPaying());
        assertEquals(4, masterEvents.size());

        // Copy: five ticks in, wafer 0 is done and wafer 1 has paid 2 of its 3 ticks.
        PackagingRun copy = new PackagingRun(plan, 3, 10);
        for (int i = 0; i < 5; i++) {
            copy.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());
        }
        assertEquals(1, copy.wafersDone());
        assertEquals(2, copy.ticksPaidOnCurrentWafer());

        PackagingRun.Snapshot snapshot = copy.save();
        assertEquals(1, snapshot.currentIndex());
        assertEquals(2, snapshot.paidTicks());
        assertEquals(3, snapshot.ticksPerWafer());
        assertEquals(10, snapshot.energyPerTick());
        assertEquals(plan, snapshot.plan());

        PackagingRun restored = new PackagingRun(snapshot);
        assertEquals(1, restored.wafersDone());
        assertEquals(2, restored.ticksPaidOnCurrentWafer());
        List<PackagingRun.Completion> restoredEvents =
            runToCompletion(restored, alwaysOpen(), alwaysOpen(), alwaysPaying());
        assertEquals(masterEvents.size() - 1, restoredEvents.size());
        assertEquals(masterEvents.subList(1, masterEvents.size()), restoredEvents);
        assertTrue(restored.isFinished());
        assertEquals(4, restored.wafersDone());
        assertEquals(0, restored.ticksPaidOnCurrentWafer());
    }

    @Test
    void repeatedSnapshotsBeforeTheNextTickRestoreToTheSameRun() {
        PackagingPlan plan = new PackagingPlan(List.of(true, false, true), 4, 2);

        PackagingRun master = new PackagingRun(plan, 2, 5);
        List<PackagingRun.Completion> masterEvents =
            runToCompletion(master, alwaysOpen(), alwaysOpen(), alwaysPaying());
        assertEquals(3, masterEvents.size());

        // Copy: three ticks, wafer 0 done and one tick of wafer 1 paid.
        PackagingRun copy = new PackagingRun(plan, 2, 5);
        for (int i = 0; i < 3; i++) {
            copy.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());
        }
        assertEquals(1, copy.wafersDone());
        assertEquals(1, copy.ticksPaidOnCurrentWafer());

        // Two snapshots taken before anything more happens, then two independent rests.
        PackagingRun.Snapshot first = copy.save();
        PackagingRun.Snapshot second = copy.save();
        PackagingRun r1 = new PackagingRun(first);
        PackagingRun r2 = new PackagingRun(second);
        assertEquals(1, r1.wafersDone());
        assertEquals(1, r1.ticksPaidOnCurrentWafer());
        assertEquals(1, r2.wafersDone());
        assertEquals(1, r2.ticksPaidOnCurrentWafer());

        List<PackagingRun.Completion> e1 = runToCompletion(r1, alwaysOpen(), alwaysOpen(), alwaysPaying());
        List<PackagingRun.Completion> e2 = runToCompletion(r2, alwaysOpen(), alwaysOpen(), alwaysPaying());
        assertEquals(masterEvents.subList(1, masterEvents.size()), e1);
        assertEquals(masterEvents.subList(1, masterEvents.size()), e2);
        assertTrue(r1.isFinished());
        assertTrue(r2.isFinished());
    }

    @Test
    void finishedSnapshotRestoresAFinishedRun() {
        PackagingRun run = new PackagingRun(new PackagingPlan(ALL_PASS, 4, 2), 1, 5);
        runToCompletion(run, alwaysOpen(), alwaysOpen(), alwaysPaying());
        assertTrue(run.isFinished());

        PackagingRun restored = new PackagingRun(run.save());
        assertTrue(restored.isFinished());
        assertEquals(3, restored.wafersDone());
        assertEquals(0, restored.ticksPaidOnCurrentWafer());

        // A restored finished run still consults nothing on extra ticks.
        Gate packages = alwaysOpen();
        Gate outputRoom = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        assertNull(restored.tick(packages, outputRoom, energy));
        assertEquals(0, packages.calls());
        assertEquals(0, outputRoom.calls());
        assertEquals(0, energy.calls());
    }

    // ---------- invalid states ----------

    @Test
    void invalidSnapshotsAreRejected() {
        PackagingPlan plan = new PackagingPlan(ALL_PASS, 4, 2); // three wafers
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, 5, -1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, 5, 4, 0)); // past the last wafer
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, 5, 3, 1)); // finished but holding progress
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, 5, 1, 2)); // progress beyond the wafer length
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, 5, 0, -1));
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 0, 5, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingRun.Snapshot(plan, 2, -5, 0, 0));
        assertThrows(NullPointerException.class,
            () -> new PackagingRun.Snapshot(null, 2, 5, 0, 0));
    }

    @Test
    void invalidRunSchedulesAreRejected() {
        PackagingPlan plan = new PackagingPlan(List.of(true), 1, 1);
        assertThrows(NullPointerException.class, () -> new PackagingRun(null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PackagingRun(plan, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new PackagingRun(plan, 1, -1));
        // Valid at its boundary: zero energy per tick is allowed and covered above.
    }

    // ---------- privacy ----------

    @Test
    void runDescriptionNeverShowsOutcomes() {
        PackagingRun passing = new PackagingRun(new PackagingPlan(ALL_PASS, 4, 2), 3, 10);
        PackagingRun failing = new PackagingRun(new PackagingPlan(ALL_FAIL, 4, 2), 3, 10);
        passing.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());
        failing.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());

        String text = passing.toString();
        assertEquals(failing.toString(), text);
        assertFalse(text.contains("true"));
        assertFalse(text.contains("false"));
    }

    @Test
    void snapshotDescriptionNeverShowsOutcomes() {
        PackagingRun passing = new PackagingRun(new PackagingPlan(ALL_PASS, 4, 2), 3, 10);
        PackagingRun failing = new PackagingRun(new PackagingPlan(ALL_FAIL, 4, 2), 3, 10);
        passing.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());
        failing.tick(alwaysOpen(), alwaysOpen(), alwaysPaying());

        String text = passing.save().toString();
        assertEquals(failing.save().toString(), text);
        assertFalse(text.contains("true"));
        assertFalse(text.contains("false"));
    }

    @Test
    void passingAndFailingCompletionsHaveTheSamePublicDescription() {
        // Two otherwise identical runs (same chips, packages, ticks and energy) whose
        // plans differ only in the hidden flags. The description of a completion event
        // must not let anyone tell which wafer passed: the passing flag is invisible,
        // but a redaction that keeps per-wafer package counts (a passing wafer uses the
        // frozen packages, a failing one none) still reveals the result by side
        // channel. Such an otherwise-identical pair must therefore print identically.
        List<PackagingRun.Completion> passing =
            runToCompletion(new PackagingRun(new PackagingPlan(List.of(true), 4, 2), 1, 1),
                alwaysOpen(), alwaysOpen(), alwaysPaying());
        List<PackagingRun.Completion> failing =
            runToCompletion(new PackagingRun(new PackagingPlan(List.of(false), 4, 2), 1, 1),
                alwaysOpen(), alwaysOpen(), alwaysPaying());

        assertEquals(1, passing.size());
        assertEquals(1, failing.size());
        PackagingRun.Completion passEvent = passing.get(0);
        PackagingRun.Completion failEvent = failing.get(0);
        assertEquals(0, passEvent.waferIndex());
        assertEquals(0, failEvent.waferIndex());
        assertEquals(4, passEvent.amount());
        assertEquals(4, failEvent.amount());

        assertFalse(passEvent.toString().contains("passing"));
        assertFalse(failEvent.toString().contains("passing"));
        assertFalse(passEvent.toString().contains("true"));
        assertFalse(failEvent.toString().contains("false"));
        // Honest boundary: this equality is exactly what the draft event description
        // breaks, because it prints per-wafer packages (2 vs 0).
        assertEquals(failEvent.toString(), passEvent.toString());
    }

    @Test
    void missingPackagesMidWaferPreservePaidProgressAndResumeWithoutExtraCost() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 4, 2), 3, 10);
        Gate packages = alwaysOpen();
        Gate room = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        assertNull(run.tick(packages, room, energy));
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        packages.with(false);
        assertNull(run.tick(packages, room, energy));
        assertNull(run.tick(packages, room, energy));
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        assertEquals(10, energy.feDrawn());
        assertEquals(1, energy.calls());
        packages.with(true);
        assertNull(run.tick(packages, room, energy));
        assertEquals(new PackagingRun.Completion(0, true, 4, 2), run.tick(packages, room, energy));
        assertTrue(run.isFinished());
        assertEquals(30, energy.feDrawn());
        assertNull(run.tick(packages, room, energy));
        assertEquals(30, energy.feDrawn());
    }

    @Test
    void fullOutputMidWaferPreservesPaidProgressAndResumesWithoutExtraCost() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(true), 4, 2), 3, 10);
        Gate packages = alwaysOpen();
        Gate room = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        assertNull(run.tick(packages, room, energy));
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        room.with(false);
        assertNull(run.tick(packages, room, energy));
        assertNull(run.tick(packages, room, energy));
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        assertEquals(10, energy.feDrawn());
        assertEquals(1, energy.calls());
        room.with(true);
        assertNull(run.tick(packages, room, energy));
        assertEquals(new PackagingRun.Completion(0, true, 4, 2), run.tick(packages, room, energy));
        assertTrue(run.isFinished());
        assertEquals(30, energy.feDrawn());
        assertNull(run.tick(packages, room, energy));
        assertEquals(30, energy.feDrawn());
    }


    @Test
    void failingWaferPausesMidProgressOnFullScrapOutputWithoutPackages() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(false), 4, 2), 3, 10);
        Gate packages = new Gate().with(false);
        Gate room = alwaysOpen();
        EnergyBook energy = alwaysPaying();
        assertNull(run.tick(packages, room, energy));
        room.with(false);
        assertNull(run.tick(packages, room, energy));
        assertNull(run.tick(packages, room, energy));
        assertEquals(1, run.ticksPaidOnCurrentWafer());
        assertEquals(0, run.wafersDone());
        assertEquals(10, energy.feDrawn());
        assertEquals(1, energy.calls());
        assertEquals(0, packages.calls());
        room.with(true);
        assertNull(run.tick(packages, room, energy));
        assertEquals(new PackagingRun.Completion(0, false, 4, 0), run.tick(packages, room, energy));
        assertTrue(run.isFinished());
        assertEquals(30, energy.feDrawn());
        assertEquals(0, packages.calls());
    }

    @Test
    void finishedRunAlsoIgnoresNullPredicates() {
        PackagingRun run = new PackagingRun(new PackagingPlan(List.of(false), 1, 1), 1, 0);
        assertNotNull(run.tick(amount -> false, amount -> true, amount -> true));
        assertTrue(run.isFinished());
        assertNull(run.tick(null, null, null));
        assertEquals(1, run.wafersDone());
        assertEquals(0, run.ticksPaidOnCurrentWafer());
    }

}
