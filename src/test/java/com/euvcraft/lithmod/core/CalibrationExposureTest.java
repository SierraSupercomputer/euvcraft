package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CalibrationExposure}: state validation, that only paid ticks change
 * anything, dirty saturation and the invalidation latch, that one clean paid tick recovers the
 * counter but never the latch, and that recalibration restores a fresh grace budget anywhere.
 */
class CalibrationExposureTest {

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("negative dirty ticks are rejected")
        void negativeDirtyTicksRejected() {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new CalibrationExposure.State(-1, false)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new CalibrationExposure.State(-42, true)),
                () -> assertEquals(0, new CalibrationExposure.State(0, false).dirtyTicks()),
                () -> assertEquals(Integer.MAX_VALUE,
                    new CalibrationExposure.State(Integer.MAX_VALUE, false).dirtyTicks()));
        }

        @Test
        @DisplayName("the limit must be a positive tick count")
        void limitMustBePositive() {
            CalibrationExposure.State state = CalibrationExposure.recalibrated();
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> CalibrationExposure.advance(state, true, true, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> CalibrationExposure.advance(state, true, true, -3)));
        }

        @Test
        @DisplayName("a paid tick needs a state")
        void paidTickNeedsAState() {
            assertThrows(NullPointerException.class,
                () -> CalibrationExposure.advance(null, true, true, 5));
        }
    }

    @Nested
    @DisplayName("only paid ticks advance the tool")
    class PaidTicksOnly {

        @Test
        @DisplayName("an unpaid tick returns the same state, clean or dirty")
        void unpaidTicksChangeNothing() {
            CalibrationExposure.State fresh = new CalibrationExposure.State(2, false);
            CalibrationExposure.State latched = new CalibrationExposure.State(2, true);
            assertAll(
                () -> assertTrue(fresh == CalibrationExposure.advance(fresh, true, false, 5)),
                () -> assertTrue(latched == CalibrationExposure.advance(latched, true, false, 5)),
                () -> assertTrue(latched == CalibrationExposure.advance(latched, true, false, 1)),
                () -> assertEquals(fresh, CalibrationExposure.advance(fresh, false, false, 5)));
        }

        @Test
        @DisplayName("the state passed in is never mutated")
        void inputStatesArePreserved() {
            CalibrationExposure.State before = new CalibrationExposure.State(2, false);
            CalibrationExposure.advance(before, true, true, 5);
            CalibrationExposure.advance(before, false, true, 5);
            assertEquals(new CalibrationExposure.State(2, false), before);
        }
    }

    @Nested
    @DisplayName("dirty saturation and the invalidation latch")
    class SaturationAndLatch {

        @Test
        @DisplayName("dirty paid ticks count up and latch exactly at the limit")
        void dirtyTicksCountUpAndLatch() {
            CalibrationExposure.State s = CalibrationExposure.recalibrated();
            s = CalibrationExposure.advance(s, true, true, 3);
            assertEquals(new CalibrationExposure.State(1, false), s);
            s = CalibrationExposure.advance(s, true, true, 3);
            assertEquals(new CalibrationExposure.State(2, false), s);
            s = CalibrationExposure.advance(s, true, true, 3);
            assertEquals(new CalibrationExposure.State(3, true), s,
                "reaching the limit latches invalidation");
            s = CalibrationExposure.advance(s, true, true, 3);
            assertEquals(new CalibrationExposure.State(3, true), s,
                "the count saturates at the limit and the latch holds");
        }

        @Test
        @DisplayName("a crafted over-budget state snaps to the limit and latches on its next dirty tick")
        void overBudgetStateSnaps() {
            CalibrationExposure.State crafted = new CalibrationExposure.State(10, false);
            CalibrationExposure.State advanced =
                CalibrationExposure.advance(crafted, true, true, 3);
            assertEquals(new CalibrationExposure.State(3, true), advanced);
        }

        @Test
        @DisplayName("one clean paid tick clears the dirty counter but preserves the latch")
        void cleanTickClearsCounterNotLatch() {
            CalibrationExposure.State counting = new CalibrationExposure.State(2, false);
            assertEquals(new CalibrationExposure.State(0, false),
                CalibrationExposure.advance(counting, false, true, 5),
                "a clean paid tick resets the grace budget while not yet invalidated");

            CalibrationExposure.State latched = new CalibrationExposure.State(3, true);
            assertEquals(new CalibrationExposure.State(0, true),
                CalibrationExposure.advance(latched, false, true, 5),
                "clean processing may not clear an invalidated calibration by itself");
        }
    }

    @Nested
    @DisplayName("recalibration")
    class Recalibration {

        @Test
        @DisplayName("recalibration returns zero dirty ticks and clears the latch")
        void recalibratedIsFresh() {
            assertEquals(new CalibrationExposure.State(0, false),
                CalibrationExposure.recalibrated());
            assertEquals(CalibrationExposure.recalibrated(), CalibrationExposure.recalibrated());
        }

        @Test
        @DisplayName("recalibration ends an invalidation and restarts the grace counter")
        void recalibrationRestartsTheGrace() {
            CalibrationExposure.State s = new CalibrationExposure.State(3, true);
            s = CalibrationExposure.recalibrated();
            assertEquals(new CalibrationExposure.State(0, false), s);
            s = CalibrationExposure.advance(s, true, true, 3);
            assertEquals(new CalibrationExposure.State(1, false), s,
                "after recalibration the tool has its full dirty-tick budget again");
        }

        @Test
        @DisplayName("recalibration leaves no residue: the old state is untouched")
        void oldStateUnchanged() {
            CalibrationExposure.State latched = new CalibrationExposure.State(3, true);
            CalibrationExposure.recalibrated();
            assertEquals(new CalibrationExposure.State(3, true), latched);
        }
    }
}
