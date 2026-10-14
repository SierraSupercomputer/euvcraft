package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YieldCalibration}: that the gamma functions behind it are right, that
 * a calibrated weight really produces the wanted pass rate for every tier, that a worse
 * defect factor really lowers it, and that more rolls really lower the mean cumulative
 * roll.
 *
 * <p>Every simulation here is seeded, so the numbers are the same on every run. The
 * accuracy requirement is plus or minus 1.5 percentage points over at least 100,000
 * wafers; the observed deviations are two orders of magnitude smaller than that, so
 * these tests cannot flake.</p>
 */
class YieldCalibrationTest {

    /** Wafers per simulation; the accuracy requirement asks for at least 100,000. */
    private static final int WAFERS = 100_000;

    /** Seed for the bulk simulations. */
    private static final long SEED = 0x5EEDFAB0L;

    /** How far a simulated pass rate may sit from its target, in fractions. */
    private static final double TOLERANCE = 0.015;

    @Nested
    @DisplayName("the gamma functions behind the calibration")
    class GammaFunctions {

        @Test
        @DisplayName("P(1, x) is 1 - e^-x")
        void shapeOneIsExponential() {
            for (double x : new double[] {0.05, 0.5, 1.0, 2.0, 4.0, 12.0}) {
                assertEquals(1.0 - Math.exp(-x), RegularizedGamma.lowerRegularized(1.0, x), 1e-12,
                    "P(1, " + x + ") disagreed");
            }
        }

        @Test
        @DisplayName("P(2, x) is 1 - e^-x (1 + x)")
        void shapeTwoHasAClosedForm() {
            for (double x : new double[] {0.5, 1.0, 3.0, 7.5, 20.0}) {
                assertEquals(1.0 - Math.exp(-x) * (1.0 + x), RegularizedGamma.lowerRegularized(2.0, x), 1e-12,
                    "P(2, " + x + ") disagreed");
            }
        }

        @Test
        @DisplayName("the quantile of shape 1 is -ln(1 - p)")
        void shapeOneQuantileIsClosedForm() {
            for (double p : new double[] {0.1, 0.5, 0.9, 0.99}) {
                assertEquals(-Math.log(1.0 - p), RegularizedGamma.quantile(1.0, p), 1e-11,
                    "the " + p + " quantile of Gamma(1, 1) disagreed");
            }
        }

        @Test
        @DisplayName("P is the lower tail: 0 at zero, 1 far out, and increasing in between")
        void lowerRegularizedIsATail() {
            assertEquals(0.0, RegularizedGamma.lowerRegularized(24.0, 0.0), 0.0);
            assertEquals(1.0, RegularizedGamma.lowerRegularized(24.0, 500.0), 1e-12);

            double previous = -1.0;
            for (double x = 0.5; x <= 60.0; x += 0.5) {
                double value = RegularizedGamma.lowerRegularized(24.0, x);
                assertTrue(value > previous, "P(24, " + x + ") did not increase");
                previous = value;
            }
        }

        @Test
        @DisplayName("the quantile inverts P at every tier's roll count")
        void quantileInvertsTheLowerTail() {
            for (Tier tier : ReferenceTiers.all()) {
                for (double p : new double[] {0.60, 0.80, 0.85, 0.90, 0.93}) {
                    double q = RegularizedGamma.quantile(tier.layers(), p);
                    assertEquals(p, RegularizedGamma.lowerRegularized(tier.layers(), q), 1e-10,
                        "the " + p + " quantile of Gamma(" + tier.layers() + ", 1) did not invert");
                }
            }
        }

        @Test
        @DisplayName("the quantile sits near the Wilson-Hilferty median")
        void quantileIsNearTheMedian() {
            // A Gamma(k, 1) variable is half a chi-square with 2k degrees of freedom,
            // whose median is about nu * (1 - 2 / (9 * nu))^3.
            for (int shape : new int[] {24, 42, 90}) {
                double q = RegularizedGamma.quantile(shape, 0.5);
                double wilson = shape * Math.pow(1.0 - 1.0 / (9.0 * shape), 3.0);
                assertEquals(wilson, q, 0.01 * shape,
                    "the median of Gamma(" + shape + ", 1) was far from the Wilson-Hilferty value");
            }
        }

        @Test
        @DisplayName("bad arguments are rejected")
        void rejectsBadArguments() {
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.lowerRegularized(0.0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.lowerRegularized(-2.0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.lowerRegularized(2.0, -1.0));
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.quantile(2.0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.quantile(2.0, -0.1));
            assertThrows(IllegalArgumentException.class, () -> RegularizedGamma.quantile(2.0, Double.NaN));
        }
    }

    @Nested
    @DisplayName("calibration accuracy")
    class Accuracy {

        @Test
        @DisplayName("every tier's weight passes at the tier's own yield within 1.5 points")
        void everyTierHitsItsYield() {
            StringBuilder table = new StringBuilder();
            table.append(System.lineSeparator())
                .append("tier | K | target | baseWeight | simulated | deviation (pp)")
                .append(System.lineSeparator());

            for (Tier tier : ReferenceTiers.all()) {
                int rollCount = tier.layers() * FabSettings.DEFAULT_ROLLS_PER_LAYER;
                double yield = tier.defaultYieldFraction();
                double weight = YieldCalibration.calibrateBaseWeight(rollCount, yield);
                double simulated = simulatePassRate(rollCount, yield, weight, SEED);

                table.append(String.format("%-3s | %3d | %5.1f%% | %10.6f | %9.4f%% | %+8.4f",
                    tier.id(), rollCount, yield * 100.0, weight, simulated * 100.0,
                    (simulated - yield) * 100.0)).append(System.lineSeparator());

                assertTrue(Math.abs(simulated - yield) <= TOLERANCE,
                    tier.id() + " simulated " + (simulated * 100.0) + "% against a target of "
                        + (yield * 100.0) + "% with K=" + rollCount + " and weight " + weight);
            }
            System.out.println(table);
        }

        @Test
        @DisplayName("every offered core count option lands on its reference yield")
        void coreCountsLandOnTheReferenceYields() {
            StringBuilder table = new StringBuilder();
            table.append(System.lineSeparator())
                .append("tier | cores | target | baseWeight | simulated | deviation (pp)")
                .append(System.lineSeparator());

            FabSettings settings = FabSettings.defaults();
            for (String id : new String[] {"T6", "T7", "T8"}) {
                Tier tier = ReferenceTiers.byId(id);
                for (int cores : settings.coreCountOptions()) {
                    double yield = YieldMath.resolveEffectiveYield(tier, settings, cores);
                    int rollCount = tier.layers() * settings.rollsPerLayer();
                    double weight = YieldCalibration.calibrateBaseWeight(rollCount, yield);
                    double simulated = simulatePassRate(rollCount, yield, weight, SEED);

                    table.append(String.format("%-3s | %5d | %5.1f%% | %10.6f | %9.4f%% | %+8.4f",
                        id, cores, yield * 100.0, weight, simulated * 100.0,
                        (simulated - yield) * 100.0)).append(System.lineSeparator());

                    assertTrue(Math.abs(simulated - yield) <= TOLERANCE,
                        id + " with " + cores + " cores simulated " + (simulated * 100.0)
                            + "% against a target of " + (yield * 100.0) + "%");
                }
            }
            System.out.println(table);
        }

        @Test
        @DisplayName("the weight is deterministic down to the last bit")
        void calibrationIsDeterministic() {
            for (Tier tier : ReferenceTiers.all()) {
                double first = YieldCalibration.calibrateBaseWeight(tier.layers(), tier.defaultYieldFraction());
                double second = YieldCalibration.calibrateBaseWeight(tier.layers(), tier.defaultYieldFraction());
                assertEquals(first, second, 0.0, tier.id() + " calibrated twice with different bits");
            }
        }

        @Test
        @DisplayName("a cached weight equals the computed one")
        void cachedWeightMatches() {
            YieldCalibration.clearCache();

            double first = YieldCalibration.baseWeight(24, 0.90);
            double second = YieldCalibration.baseWeight(24, 0.90);

            assertEquals(YieldCalibration.calibrateBaseWeight(24, 0.90), first, 0.0);
            assertEquals(first, second, 0.0, "the second lookup did not return the same weight");

            YieldCalibration.baseWeight(24, 0.80);

            YieldCalibration.clearCache();
        }

        @Test
        @DisplayName("bad calibration arguments are rejected")
        void rejectsBadArguments() {
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(0, 0.9));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(-3, 0.9));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(24, 0.0));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(24, 1.0));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(24, -0.2));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.calibrateBaseWeight(24, Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> YieldCalibration.baseWeight(0, 0.9));
        }

        @Test
        @DisplayName("rollCount = 1 has a closed form: weight = -ln(1 - y) / -ln(1 - y) = 1.0")
        void rollCountOneIsOne() {
            assertEquals(1.0, YieldCalibration.calibrateBaseWeight(1, 0.90), 1e-12);
            assertEquals(1.0, YieldCalibration.calibrateBaseWeight(1, 0.50), 1e-12);
            assertEquals(1.0, YieldCalibration.calibrateBaseWeight(1, 0.99), 1e-12);
        }
    }

    @Nested
    @DisplayName("the defect factor")
    class DefectFactor {

        @Test
        @DisplayName("a lower defect factor lowers the pass rate")
        void lowerDefectFactorLowersThePassRate() {
            Tier tier = ReferenceTiers.byId("T5");
            int rollCount = tier.layers();
            double yield = tier.defaultYieldFraction();
            double base = YieldCalibration.calibrateBaseWeight(rollCount, yield);

            double healthy = simulatePassRate(rollCount, yield, base, SEED);
            double worn = simulatePassRate(rollCount, yield, base / 0.9, SEED + 1);
            double broken = simulatePassRate(rollCount, yield, base / 0.7, SEED + 2);
            double floor = simulatePassRate(rollCount, yield,
                YieldCalibration.stepWeight(rollCount, yield, 0.0), SEED + 3);

            System.out.printf("%ntier T5, K=%d, yield %.1f%%: defect 1.0 -> %.4f%%, 0.9 -> %.4f%%,"
                    + " 0.7 -> %.4f%%, 0.0 (floored at %.2f) -> %.4f%%%n",
                rollCount, yield * 100.0, healthy * 100.0, worn * 100.0, broken * 100.0,
                YieldCalibration.MIN_DEFECT_FACTOR, floor * 100.0);

            assertTrue(worn < healthy, "defect 0.9 gave " + worn + " against " + healthy + " at defect 1.0");
            assertTrue(broken < worn, "defect 0.7 gave " + broken + " against " + worn + " at defect 0.9");
            assertTrue(floor < broken, "the floored defect factor gave " + floor + " against " + broken);
            assertTrue(healthy - floor > 0.05, "the whole defect range moved the pass rate by only "
                + (healthy - floor));
        }

        @Test
        @DisplayName("a defect factor below the floor is raised to it")
        void defectFactorHasAFloor() {
            double floored = YieldCalibration.stepWeight(24, 0.90, 0.0);
            double negative = YieldCalibration.stepWeight(24, 0.90, -5.0);
            double atFloor = YieldCalibration.stepWeight(24, 0.90, YieldCalibration.MIN_DEFECT_FACTOR);

            assertEquals(atFloor, floored, 0.0);
            assertEquals(atFloor, negative, 0.0);
            assertTrue(YieldCalibration.stepWeight(24, 0.90, 1.0) < floored,
                "a healthy line must use a smaller weight than a floored one");
        }
    }

    @Nested
    @DisplayName("the cumulative roll")
    class CumulativeRoll {

        @Test
        @DisplayName("more rolls at the same weight lower the mean cumulative roll")
        void moreRollsLowerTheMean() {
            double weight = YieldCalibration.calibrateBaseWeight(24, 0.90);
            double previous = Double.MAX_VALUE;
            StringBuilder means = new StringBuilder(System.lineSeparator())
                .append("weight fixed at ").append(weight).append(":");

            for (int rollCount : new int[] {1, 2, 5, 12, 24}) {
                double mean = simulateMeanCumulative(rollCount, weight, SEED);
                double expected = Math.pow(1.0 / (1.0 + weight), rollCount);
                means.append(String.format(" K=%d mean=%.5f (theory %.5f)", rollCount, mean, expected));

                assertTrue(mean < previous,
                    "K=" + rollCount + " had a mean cumulative roll of " + mean + ", not below " + previous);
                assertEquals(expected, mean, 0.005, "K=" + rollCount + " did not match (1 / (1 + w))^K");
                previous = mean;
            }
            System.out.println(means);
        }

        @Test
        @DisplayName("a step roll stays in (0, 1] and never reaches zero")
        void stepRollStaysInRange() {
            double weight = YieldCalibration.calibrateBaseWeight(24, 0.90);
            Rng rng = new SplitMix64(SEED);
            for (int draw = 0; draw < 100_000; draw++) {
                float roll = YieldCalibration.stepRoll(rng, weight);
                assertTrue(roll > 0.0f, "draw " + draw + " rounded to zero");
                assertTrue(roll <= 1.0f, "draw " + draw + " was above one: " + roll);
            }
        }

        @Test
        @DisplayName("a roll is exactly u to the power of the weight")
        void rollFollowsTheModelExactly() {
            Rng half = () -> 0.5;
            assertEquals(0.5, YieldCalibration.stepRoll(half, 1.0), 0.0f);
            assertEquals(0.25, YieldCalibration.stepRoll(half, 2.0), 0.0f);
            assertEquals(1.0f, YieldCalibration.stepRoll(half, 0.0), 0.0f,
                "a weight of zero leaves the draw at one");

            Rng one = () -> 1.0;
            assertEquals(1.0f, YieldCalibration.stepRoll(one, 7.0), 0.0f,
                "the top of the (0, 1] range must stay at one however heavy the weight");
        }
    }

    /** Draws {@link #WAFERS} wafers of {@code rollCount} rolls at one weight. */
    private static double simulatePassRate(int rollCount, double yield, double weight, long seed) {
        Rng rng = new SplitMix64(seed);
        double threshold = YieldMath.passThreshold(yield);
        int passing = 0;
        for (int wafer = 0; wafer < WAFERS; wafer++) {
            double cumulative = 1.0;
            for (int roll = 0; roll < rollCount; roll++) {
                cumulative *= YieldCalibration.stepRoll(rng, weight);
            }
            if (cumulative > threshold) {
                passing++;
            }
        }
        return (double) passing / WAFERS;
    }

    /** Draws {@link #WAFERS} wafers of {@code rollCount} rolls at one weight. */
    private static double simulateMeanCumulative(int rollCount, double weight, long seed) {
        Rng rng = new SplitMix64(seed);
        double total = 0.0;
        for (int wafer = 0; wafer < WAFERS; wafer++) {
            double cumulative = 1.0;
            for (int roll = 0; roll < rollCount; roll++) {
                cumulative *= YieldCalibration.stepRoll(rng, weight);
            }
            total += cumulative;
        }
        return total / WAFERS;
    }
}