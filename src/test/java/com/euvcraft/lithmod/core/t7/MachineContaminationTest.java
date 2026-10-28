package com.euvcraft.lithmod.core.t7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the persistent T7 equipment-contamination state machine.
 */
class MachineContaminationTest {

    private static final ContaminationRepairCost BILL = new ContaminationRepairCost(4, 1000, 2000);

    private static BreachConditions processing() {
        return new BreachConditions(true, true, true);
    }

    @Nested
    @DisplayName("breach notification")
    class Breach {

        @Test
        @DisplayName("a powered, loaded, active T7 machine becomes contaminated")
        void activeT7BreachContaminates() {
            MachineContaminationState clean = MachineContaminationState.clean();
            MachineContaminationState.BreachResult result = clean.notifyStructuralBreach(processing(), 40);

            assertEquals(BreachNotification.NEWLY_CONTAMINATED, result.notification());
            assertEquals(MachineContaminationState.Phase.CONTAMINATED, result.state().phase());
            assertEquals(40, result.state().lastBreachTick());
            assertTrue(result.state().requiresRepair());
            assertFalse(result.state().requiresRecalibration());
            assertFalse(result.state().isOperational());
        }

        @Test
        @DisplayName("empty, unpowered and idle machines are distinguished and stay clean")
        void inactiveBreachesAreDistinct() {
            MachineContaminationState clean = MachineContaminationState.clean();

            assertEquals(BreachNotification.IGNORED_UNPOWERED,
                clean.notifyStructuralBreach(new BreachConditions(false, true, true), 1).notification());
            assertEquals(BreachNotification.IGNORED_EMPTY,
                clean.notifyStructuralBreach(new BreachConditions(true, false, true), 1).notification());
            assertEquals(BreachNotification.IGNORED_NOT_PROCESSING,
                clean.notifyStructuralBreach(new BreachConditions(true, true, false), 1).notification());

            assertSame(clean, clean.notifyStructuralBreach(new BreachConditions(false, true, true), 1).state());
            assertEquals(MachineContaminationState.Phase.CLEAN,
                clean.notifyStructuralBreach(new BreachConditions(true, false, true), 1).state().phase());
        }

        @Test
        @DisplayName("repeated notifications return the same state and consume nothing")
        void repeatedNotificationsAreIdempotent() {
            MachineContaminationState contaminated =
                MachineContaminationState.clean().notifyStructuralBreach(processing(), 5).state();

            MachineContaminationState.BreachResult repeated =
                contaminated.notifyStructuralBreach(processing(), 6);

            assertEquals(BreachNotification.ALREADY_CONTAMINATED, repeated.notification());
            assertSame(contaminated, repeated.state());
            assertTrue(repeated.state().requiresRepair());
        }
    }

    @Nested
    @DisplayName("repair and recalibration")
    class Repair {

        @Test
        @DisplayName("repair is charged exactly once, then recalibration completes once")
        void repairThenRecalibrate() {
            MachineContaminationState contaminated =
                MachineContaminationState.clean().notifyStructuralBreach(processing(), 10).state();

            MachineContaminationState.RepairResult repaired = contaminated.repair(BILL);
            assertTrue(repaired.charged());
            assertEquals(MachineContaminationState.Phase.AWAITING_RECALIBRATION, repaired.state().phase());
            assertTrue(repaired.state().requiresRecalibration());
            assertFalse(repaired.state().requiresRepair());

            MachineContaminationState.RepairResult repeated = repaired.state().repair(BILL);
            assertFalse(repeated.charged());
            assertSame(repaired.state(), repeated.state());

            MachineContaminationState.RecalibrationResult recalibrated = repeated.state().recalibrate();
            assertTrue(recalibrated.recalibrated());
            assertTrue(recalibrated.state().isOperational());
            assertEquals(MachineContaminationState.Phase.CLEAN, recalibrated.state().phase());

            MachineContaminationState.RecalibrationResult second = recalibrated.state().recalibrate();
            assertFalse(second.recalibrated());
            assertSame(recalibrated.state(), second.state());
        }

        @Test
        @DisplayName("repair on a clean machine changes nothing and charges nothing")
        void cleanRepairIsFree() {
            MachineContaminationState clean = MachineContaminationState.clean();
            MachineContaminationState.RepairResult result = clean.repair(BILL);

            assertFalse(result.charged());
            assertSame(clean, result.state());
            assertFalse(clean.recalibrate().recalibrated());
            assertSame(clean, clean.recalibrate().state());
        }

        @Test
        @DisplayName("a new breach while awaiting recalibration does not restart or charge")
        void breachDuringRecalibrationDoesNotRestart() {
            MachineContaminationState awaiting = MachineContaminationState.clean()
                .notifyStructuralBreach(processing(), 3).state().repair(BILL).state();

            MachineContaminationState.BreachResult result = awaiting.notifyStructuralBreach(processing(), 4);

            assertEquals(BreachNotification.ALREADY_CONTAMINATED, result.notification());
            assertSame(awaiting, result.state());
            assertTrue(result.state().requiresRecalibration());
        }

        @Test
        @DisplayName("a negative repair cost is rejected")
        void rejectsNegativeCost() {
            assertThrows(IllegalArgumentException.class, () -> new ContaminationRepairCost(-1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new ContaminationRepairCost(0, -1, 0));
            assertThrows(IllegalArgumentException.class, () -> new ContaminationRepairCost(0, 0, -1));
            assertThrows(NullPointerException.class, () -> MachineContaminationState.clean().repair(null));
        }

        @Test
        @DisplayName("a contaminated state needs a breach tick")
        void invalidStateIsRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> new MachineContaminationState(MachineContaminationState.Phase.CONTAMINATED, -1));
            assertThrows(IllegalArgumentException.class,
                () -> new MachineContaminationState(MachineContaminationState.Phase.CLEAN, -2));
        }
    }
}
