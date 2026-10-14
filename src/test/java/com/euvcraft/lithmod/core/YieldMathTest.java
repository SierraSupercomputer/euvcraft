package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YieldMath}: the pass or fail threshold, the core count penalty
 * against the reference table, and the rules that resolve a tier's effective yield out
 * of the tier data and the settings.
 */
class YieldMathTest {

    /** Tolerance for the rounded percentages of the reference table. */
    private static final double PERCENT_TOLERANCE = 0.005;

    @Nested
    @DisplayName("pass or fail")
    class PassThreshold {

        @Test
        @DisplayName("a 90% yield needs a cumulative roll above 0.10")
        void ninetyPercentNeedsAbovePointOne() {
            assertEquals(0.10, YieldMath.passThreshold(0.90), 1e-12);

            assertTrue(YieldMath.passes(0.11, 0.90), "0.11 should pass a 90% yield");
            assertTrue(YieldMath.passes(0.9, 0.90), "0.9 should pass a 90% yield");
            assertFalse(YieldMath.passes(0.09, 0.90), "0.09 should fail a 90% yield");
        }

        @Test
        @DisplayName("the threshold itself fails, because the comparison is strict")
        void theThresholdItselfFails() {
            double threshold = YieldMath.passThreshold(0.90);
            assertFalse(YieldMath.passes(threshold, 0.90),
                "a roll exactly on the threshold must fail, the rule is cumulative > 1 - yield");
        }

        @Test
        @DisplayName("an unprocessed wafer passes every yield")
        void unprocessedWaferPasses() {
            assertTrue(YieldMath.passes(Wafer.NO_ROLLS, 0.60), "a wafer with no rolls should pass T1");
            assertTrue(YieldMath.passes(Wafer.NO_ROLLS, 0.01), "a wafer with no rolls should pass the worst yield");
        }

        @Test
        @DisplayName("the other tiers need the roll the reference table implies")
        void otherTierThresholds() {
            assertEquals(0.40, YieldMath.passThreshold(0.60), 1e-12);
            assertEquals(0.20, YieldMath.passThreshold(0.80), 1e-12);
            assertEquals(0.07, YieldMath.passThreshold(0.93), 1e-12);
            assertEquals(0.83, YieldMath.passThreshold(0.17), 1e-12);
        }

        @Test
        @DisplayName("a yield that is not a number is rejected")
        void rejectsNaN() {
            assertThrows(IllegalArgumentException.class, () -> YieldMath.passThreshold(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> YieldMath.clampYield(Double.NaN));
        }
    }

    @Nested
    @DisplayName("core count penalty")
    class CoreCountPenalty {

        private static final double K = 2.0;

        @Test
        @DisplayName("T6 at or below 4 cores keeps the tier yield of 85%")
        void t6AtOrBelowComfortable() {
            Tier t6 = ReferenceTiers.byId("T6");
            for (int cores : new int[] {1, 2, 4}) {
                assertEquals(0.85, YieldMath.effectiveYield(t6.defaultYieldFraction(), cores,
                    t6.comfortableCores(), K), 1e-12, cores + " cores should keep the tier yield");
            }
        }

        @Test
        @DisplayName("T6 past 4 cores gives 61%, 44% and 17%")
        void t6PastComfortable() {
            Tier t6 = ReferenceTiers.byId("T6");
            assertEquals(0.61, YieldMath.effectiveYield(0.85, 8, 4, K), PERCENT_TOLERANCE);
            assertEquals(0.44, YieldMath.effectiveYield(0.85, 12, 4, K), PERCENT_TOLERANCE);
            assertEquals(0.17, YieldMath.effectiveYield(0.85, 24, 4, K), PERCENT_TOLERANCE);
        }

        @Test
        @DisplayName("T7 at or below 8 cores keeps 85%, 12 cores gives 72% and 24 gives 44%")
        void t7PastComfortable() {
            Tier t7 = ReferenceTiers.byId("T7");
            for (int cores : new int[] {1, 2, 4, 8}) {
                assertEquals(0.85, YieldMath.effectiveYield(0.85, cores, 8, K), 1e-12,
                    cores + " cores should keep the tier yield");
            }
            assertEquals(0.72, YieldMath.effectiveYield(0.85, 12, 8, K), PERCENT_TOLERANCE);
            assertEquals(0.44, YieldMath.effectiveYield(0.85, 24, 8, K), PERCENT_TOLERANCE);
        }

        @Test
        @DisplayName("T8 at or below 12 cores keeps 80% and 24 cores gives 51%")
        void t8PastComfortable() {
            Tier t8 = ReferenceTiers.byId("T8");
            for (int cores : new int[] {1, 2, 4, 8, 12}) {
                assertEquals(0.80, YieldMath.effectiveYield(0.80, cores, 12, K), 1e-12,
                    cores + " cores should keep the tier yield");
            }
            assertEquals(0.51, YieldMath.effectiveYield(0.80, 24, 12, K), PERCENT_TOLERANCE);
        }

        @Test
        @DisplayName("the exact exponents are 1 + k * max(0, cores / comfortable - 1)")
        void exactExponents() {
            assertEquals(1.0, YieldMath.penaltyExponent(4, 4, 2.0), 1e-12);
            assertEquals(3.0, YieldMath.penaltyExponent(8, 4, 2.0), 1e-12);
            assertEquals(5.0, YieldMath.penaltyExponent(12, 4, 2.0), 1e-12);
            assertEquals(11.0, YieldMath.penaltyExponent(24, 4, 2.0), 1e-12);
            assertEquals(2.0, YieldMath.penaltyExponent(12, 8, 2.0), 1e-12);
            assertEquals(5.0, YieldMath.penaltyExponent(24, 8, 2.0), 1e-12);
            assertEquals(3.0, YieldMath.penaltyExponent(24, 12, 2.0), 1e-12);
        }

        @Test
        @DisplayName("no core count means no penalty")
        void noCoresMeansNoPenalty() {
            assertEquals(0.85, YieldMath.effectiveYield(0.85, null, 4, 2.0), 1e-12,
                "a die with no core count should keep the tier yield");
        }

        @Test
        @DisplayName("a tier without comfortable cores keeps its yield at any core count")
        void noComfortableCoresMeansNoPenalty() {
            Tier t3 = ReferenceTiers.byId("T3");
            assertNull(t3.comfortableCores(), "T3 is not supposed to have comfortable cores");
            assertEquals(0.90, YieldMath.effectiveYield(t3.defaultYieldFraction(), 24, null, 2.0), 1e-12);
            assertEquals(0.90, YieldMath.effectiveYield(0.90, 24, 0, 2.0), 1e-12,
                "a comfortable count of zero must not divide by zero");
            assertEquals(0.90, YieldMath.effectiveYield(0.90, 24, -4, 2.0), 1e-12,
                "a negative comfortable count must not invert the penalty");
        }

        @Test
        @DisplayName("a steepness of zero turns the penalty off")
        void zeroSteepnessMeansNoPenalty() {
            assertEquals(0.85, YieldMath.effectiveYield(0.85, 24, 4, 0.0), 1e-12);
        }

        @Test
        @DisplayName("a steeper penalty hurts more")
        void steeperIsWorse() {
            double gentle = YieldMath.effectiveYield(0.85, 24, 4, 1.0);
            double defaultSteepness = YieldMath.effectiveYield(0.85, 24, 4, 2.0);
            double steep = YieldMath.effectiveYield(0.85, 24, 4, 4.0);

            assertTrue(gentle > defaultSteepness, "k=1 gave " + gentle + ", k=2 gave " + defaultSteepness);
            assertTrue(defaultSteepness > steep, "k=2 gave " + defaultSteepness + ", k=4 gave " + steep);
        }
    }

    @Nested
    @DisplayName("clamping")
    class Clamping {

        @Test
        @DisplayName("a yield is clamped into 1%..99%")
        void clampsBothEnds() {
            assertEquals(YieldMath.MIN_EFFECTIVE_YIELD, YieldMath.clampYield(0.0), 1e-12);
            assertEquals(YieldMath.MIN_EFFECTIVE_YIELD, YieldMath.clampYield(0.001), 1e-12);
            assertEquals(YieldMath.MAX_EFFECTIVE_YIELD, YieldMath.clampYield(1.0), 1e-12);
            assertEquals(YieldMath.MAX_EFFECTIVE_YIELD, YieldMath.clampYield(1.8), 1e-12);
            assertEquals(0.9, YieldMath.clampYield(0.9), 1e-12);
        }

        @Test
        @DisplayName("a clamp still applies after the core count penalty")
        void clampHoldsUnderPenalty() {
            double clamped = YieldMath.effectiveYield(YieldMath.MAX_EFFECTIVE_YIELD, 1, 4, 2.0);
            assertEquals(YieldMath.MAX_EFFECTIVE_YIELD, clamped, 1e-12);
        }
    }

    @Nested
    @DisplayName("resolveEffectiveYield")
    class ResolveEffectiveYield {

        private final Tier t3 = ReferenceTiers.byId("T3");
        private final Tier t5 = ReferenceTiers.byId("T5");
        private final Tier t6 = ReferenceTiers.byId("T6");

        @Test
        @DisplayName("with default settings a tier is judged at its own yield")
        void defaultSettingsUseTheTierYield() {
            FabSettings settings = FabSettings.defaults();
            assertEquals(0.60, YieldMath.resolveEffectiveYield(ReferenceTiers.byId("T1"), settings, null), 1e-12);
            assertEquals(0.90, YieldMath.resolveEffectiveYield(t3, settings, null), 1e-12);
            assertEquals(0.93, YieldMath.resolveEffectiveYield(t5, settings, null), 1e-12);
            assertEquals(0.85, YieldMath.resolveEffectiveYield(ReferenceTiers.byId("T6"), settings, null), 1e-12);
        }

        @Test
        @DisplayName("the multiplier scales the tier's own yield")
        void multiplierScalesTheTierYield() {
            FabSettings settings = FabSettings.builder().yieldMultiplier(1.05).build();
            assertEquals(0.90 * 1.05, YieldMath.resolveEffectiveYield(t3, settings, null), 1e-12);
            assertEquals(0.60 * 1.05, YieldMath.resolveEffectiveYield(ReferenceTiers.byId("T1"), settings, null), 1e-12);
        }

        @Test
        @DisplayName("an override is used as given and is not multiplied")
        void overrideIsUsedAsGiven() {
            FabSettings settings = FabSettings.builder()
                .yieldMultiplier(2.0)
                .tierYieldOverrides(Map.of("T3", 50))
                .build();
            assertEquals(0.50, YieldMath.resolveEffectiveYield(t3, settings, null), 1e-12,
                "T3=50 with a multiplier of 2.0 must stay 50%, not 100%");
        }

        @Test
        @DisplayName("an override replaces only the tier it names")
        void overrideIsPerTier() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("T5", 60))
                .build();
            assertEquals(0.60, YieldMath.resolveEffectiveYield(t5, settings, null), 1e-12);
            assertEquals(0.90, YieldMath.resolveEffectiveYield(t3, settings, null), 1e-12);
        }

        @Test
        @DisplayName("the base is clamped at both ends")
        void baseIsClamped() {
            FabSettings generous = FabSettings.builder().yieldMultiplier(2.0).build();
            assertEquals(YieldMath.MAX_EFFECTIVE_YIELD, YieldMath.resolveEffectiveYield(t5, generous, null), 1e-12,
                "93% doubled is 186%, which must clamp to 99%");

            // No reference tier reaches the lower clamp through the multiplier alone,
            // even at its lowest, so the tier below carries a 5% yield to reach it.
            Tier frail = new Tier("T9", 9, "Frail", "test", 100, 10, 10, 10, 5.0, 10, null, false);
            FabSettings harsh = FabSettings.builder().yieldMultiplier(0.1).build();
            assertEquals(YieldMath.MIN_EFFECTIVE_YIELD, YieldMath.resolveEffectiveYield(frail, harsh, null), 1e-12,
                "5% times 0.1 is 0.5%, which must clamp up to 1%");
        }

        @Test
        @DisplayName("an override is clamped too, though the setting range already is 1..99")
        void overrideIsClamped() {
            FabSettings settings = FabSettings.builder()
                .yieldMultiplier(2.0)
                .tierYieldOverrides(Map.of("T3", 99))
                .build();
            assertEquals(0.99, YieldMath.resolveEffectiveYield(t3, settings, null), 1e-12);
        }

        @Test
        @DisplayName("a comfortable core override replaces the tier's own value")
        void comfortableCoreOverrideIsUsed() {
            FabSettings settings = FabSettings.builder()
                .comfortableCoreOverrides(Map.of("T6", 8))
                .build();
            assertEquals(0.85, YieldMath.resolveEffectiveYield(t6, settings, 8), 1e-12,
                "with 8 cores comfortable, 8 cores must no longer be penalised");
            assertEquals(0.61, YieldMath.resolveEffectiveYield(t6, settings, 16), PERCENT_TOLERANCE,
                "twice the new comfortable count is the T6 8 core case");
        }

        @Test
        @DisplayName("the steepness comes from the settings")
        void steepnessComesFromSettings() {
            FabSettings flat = FabSettings.builder().coreCountPenaltySteepness(0.0).build();
            assertEquals(0.85, YieldMath.resolveEffectiveYield(t6, flat, 24), 1e-12);

            FabSettings defaultSettings = FabSettings.defaults();
            assertEquals(0.17, YieldMath.resolveEffectiveYield(t6, defaultSettings, 24), PERCENT_TOLERANCE);
        }

        @Test
        @DisplayName("the core penalty applies after the override and the multiplier")
        void corePenaltyIsLast() {
            FabSettings settings = FabSettings.builder()
                .yieldMultiplier(2.0)
                .tierYieldOverrides(Map.of("T6", 90))
                .build();
            assertEquals(0.729, YieldMath.resolveEffectiveYield(t6, settings, 8), 1e-12,
                "an overridden 90% base at 8 cores is 0.9^3, and the multiplier does not touch it");
        }

        @Test
        @DisplayName("nulls are rejected")
        void rejectsNulls() {
            assertThrows(NullPointerException.class,
                () -> YieldMath.resolveEffectiveYield(null, FabSettings.defaults(), null));
            assertThrows(NullPointerException.class,
                () -> YieldMath.resolveEffectiveYield(t3, null, null));
        }
    }
}