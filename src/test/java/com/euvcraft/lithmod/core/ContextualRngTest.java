package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ContextualRng}: determinism, context separation, the {@code (0, 1]}
 * range contract and the absence of correlation between adjacent contexts.
 *
 * <p>Every generator here is seeded from fixed constants, so all of these checks are
 * deterministic and cannot flake.</p>
 */
class ContextualRngTest {

    private static final long SALT = 0x0123456789ABCDEFL;
    private static final UUID LOT_A = UUID.fromString("00000000-0000-4000-8000-00000000beef");
    private static final UUID LOT_B = UUID.fromString("00000000-0000-4000-8000-00000000f00d");
    private static final int SAMPLES = 100_000;

    private static double[] sample(ContextualRng rng, int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = rng.nextDouble();
        }
        return values;
    }

    @Test
    @DisplayName("the same context always produces the same sequence")
    void sameInputsGiveSameSequence() {
        ContextualRng first = new ContextualRng(SALT, LOT_A, 3, 7);
        ContextualRng second = new ContextualRng(SALT, LOT_A, 3, 7);

        double[] a = sample(first, 64);
        double[] b = sample(second, 64);

        assertEquals(LOT_A, first.lotId());
        assertEquals(3, first.waferIndex());
        assertEquals(7, first.stepIndex());

        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], "draw " + i + " differed for an identical context");
        }
    }

    @Test
    @DisplayName("raw 64-bit streams repeat for an identical context")
    void sameInputsGiveSameRawStream() {
        ContextualRng first = new ContextualRng(SALT, LOT_A, 0, 0);
        ContextualRng second = new ContextualRng(SALT, LOT_A, 0, 0);

        for (int i = 0; i < 32; i++) {
            assertEquals(first.nextLong(), second.nextLong(), "raw draw " + i + " differed");
        }
    }

    @Test
    @DisplayName("a different lot gives an unrelated sequence")
    void differentLotGivesDifferentSequence() {
        double[] a = sample(new ContextualRng(SALT, LOT_A, 3, 7), 32);
        double[] b = sample(new ContextualRng(SALT, LOT_B, 3, 7), 32);

        assertNotEquals(Arrays.toString(a), Arrays.toString(b));
    }

    @Test
    @DisplayName("a different wafer index gives an unrelated sequence")
    void differentWaferIndexGivesDifferentSequence() {
        double[] a = sample(new ContextualRng(SALT, LOT_A, 3, 7), 32);
        double[] b = sample(new ContextualRng(SALT, LOT_A, 4, 7), 32);

        assertNotEquals(Arrays.toString(a), Arrays.toString(b));
    }

    @Test
    @DisplayName("a different step index gives an unrelated sequence")
    void differentStepIndexGivesDifferentSequence() {
        double[] a = sample(new ContextualRng(SALT, LOT_A, 3, 7), 32);
        double[] b = sample(new ContextualRng(SALT, LOT_A, 3, 8), 32);

        assertNotEquals(Arrays.toString(a), Arrays.toString(b));
    }

    @Test
    @DisplayName("a different salt gives an unrelated sequence")
    void differentSaltGivesDifferentSequence() {
        double[] a = sample(new ContextualRng(SALT, LOT_A, 3, 7), 32);
        double[] b = sample(new ContextualRng(SALT + 1L, LOT_A, 3, 7), 32);

        assertNotEquals(Arrays.toString(a), Arrays.toString(b));
    }

    @Test
    @DisplayName("contexts never collapse onto the same seed")
    void seedDependsOnEveryComponent() {
        long base = ContextualRng.seedFor(SALT, LOT_A, 3, 7);
        assertEquals(base, ContextualRng.seedFor(SALT, LOT_A, 3, 7));

        assertNotEquals(base, ContextualRng.seedFor(SALT + 1L, LOT_A, 3, 7));
        assertNotEquals(base, ContextualRng.seedFor(SALT, LOT_B, 3, 7));
        assertNotEquals(base, ContextualRng.seedFor(SALT, LOT_A, 4, 7));
        assertNotEquals(base, ContextualRng.seedFor(SALT, LOT_A, 3, 8));
    }

    @Test
    @DisplayName("mean of 100000 samples is within 0.01 of 0.5")
    void meanIsHalf() {
        ContextualRng rng = new ContextualRng(SALT, LOT_A, 11, 2);
        double sum = 0.0;
        for (int i = 0; i < SAMPLES; i++) {
            sum += rng.nextDouble();
        }

        double mean = sum / SAMPLES;
        assertTrue(Math.abs(mean - 0.5) < 0.01,
            "expected mean within 0.01 of 0.5 but was " + mean);
    }

    @Test
    @DisplayName("no sample is 0 and no sample exceeds 1")
    void valuesStayInRange() {
        ContextualRng rng = new ContextualRng(SALT, LOT_A, 12, 5);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (int i = 0; i < SAMPLES; i++) {
            double value = rng.nextDouble();
            assertTrue(value > 0.0, "sample " + i + " was not above 0: " + value);
            assertTrue(value <= 1.0, "sample " + i + " was above 1: " + value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        assertTrue(min > 0.0, "smallest sample was " + min);
        assertTrue(max <= 1.0, "largest sample was " + max);
    }

    @Test
    @DisplayName("successive draws of one context are uncorrelated")
    void consecutiveDrawsAreUncorrelated() {
        double[] values = sample(new ContextualRng(SALT, LOT_A, 4, 1), SAMPLES);

        double correlation = pearson(pairsOf(values, values, 1));

        assertTrue(Math.abs(correlation) < 0.05,
            "consecutive draws correlate at r = " + correlation);
    }

    @Test
    @DisplayName("adjacent wafer contexts are uncorrelated")
    void adjacentContextsAreUncorrelated() {
        int wafers = 200;
        double[] left = new double[wafers];
        double[] right = new double[wafers];

        for (int wafer = 0; wafer < wafers; wafer++) {
            left[wafer] = new ContextualRng(SALT, LOT_A, wafer, 3).nextDouble();
            right[wafer] = new ContextualRng(SALT, LOT_A, wafer + 1, 3).nextDouble();
        }

        double correlation = pearson(left, right);

        assertTrue(Math.abs(correlation) < 0.2,
            "adjacent wafer contexts correlate at r = " + correlation);
    }

    @Test
    @DisplayName("adjacent step contexts are uncorrelated")
    void adjacentStepContextsAreUncorrelated() {
        int steps = 200;
        double[] left = new double[steps];
        double[] right = new double[steps];

        for (int step = 0; step < steps; step++) {
            left[step] = new ContextualRng(SALT, LOT_A, 5, step).nextDouble();
            right[step] = new ContextualRng(SALT, LOT_A, 5, step + 1).nextDouble();
        }

        double correlation = pearson(left, right);

        assertTrue(Math.abs(correlation) < 0.2,
            "adjacent step contexts correlate at r = " + correlation);
    }

    @Test
    @DisplayName("SplitMix64 finalizer is injective over neighbouring values")
    void mix64SpreadsNeighbouringValues() {
        for (long value = -1_000L; value <= 1_000L; value++) {
            assertNotEquals(SplitMix64.mix64(value), SplitMix64.mix64(value + 1L),
                "mix64 collapsed " + value + " and " + (value + 1L));
        }
    }

    @Test
    @DisplayName("rejects a null lot and negative indexes")
    void rejectsInvalidContexts() {
        assertThrows(NullPointerException.class, () -> new ContextualRng(SALT, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ContextualRng(SALT, LOT_A, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ContextualRng(SALT, LOT_A, 0, -1));
    }

    /** Builds a pair array by pairing element {@code i} of {@code x} with {@code lag} later elements of {@code y}. */
    private static double[][] pairsOf(double[] x, double[] y, int lag) {
        int length = x.length - lag;
        double[][] pairs = new double[2][length];
        for (int i = 0; i < length; i++) {
            pairs[0][i] = x[i];
            pairs[1][i] = y[i + lag];
        }
        return pairs;
    }

    private static double pearson(double[][] pairs) {
        return pearson(pairs[0], pairs[1]);
    }

    /** Plain Pearson product-moment correlation. */
    private static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;

        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }

        if (varianceX == 0.0 || varianceY == 0.0) {
            return 0.0;
        }
        return covariance / Math.sqrt(varianceX * varianceY);
    }
}