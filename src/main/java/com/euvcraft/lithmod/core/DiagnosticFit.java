package com.euvcraft.lithmod.core;

import java.util.Random;

/**
 * Immutable pure record representing the diagnostic curve fit state.
 *
 * <p>State consists of three candidate coefficients (a, b, c) and three target
 * coefficients (targetA, targetB, targetC). All values are stored as integer
 * hundredths (e.g., 123 represents 1.23). Coefficients are bounded to [-200, 200]
 * (i.e., -2.00 to 2.00).</p>
 *
 * <p>The target coefficients are randomized at creation with deterministic
 * seeded generation in the range [-100, 100] (i.e., -1.00 to 1.00). The
 * initial candidate coefficients are zero.</p>
 *
 * <p>Quality is computed as the mean squared error between the target curve
 * and candidate curve over 41 samples (x from -2.0 to 2.0 inclusive, step 0.1),
 * then transformed to a 0..100 scale. No Minecraft types are involved.</p>
 */
public record DiagnosticFit(
        /** Candidate coefficient A (hundredths), bounded to [-200, 200]. */
        int a,
        /** Candidate coefficient B (hundredths), bounded to [-200, 200]. */
        int b,
        /** Candidate coefficient C (hundredths), bounded to [-200, 200]. */
        int c,
        /** Target coefficient A (hundredths), bounded to [-200, 200]. */
        int targetA,
        /** Target coefficient B (hundredths), bounded to [-200, 200]. */
        int targetB,
        /** Target coefficient C (hundredths), bounded to [-200, 200]. */
        int targetC
) {

    public DiagnosticFit {
        if (a < MIN || a > MAX || b < MIN || b > MAX || c < MIN || c > MAX
                || targetA < MIN || targetA > MAX || targetB < MIN || targetB > MAX
                || targetC < MIN || targetC > MAX) {
            throw new IllegalArgumentException("DiagnosticFit coefficients outside bounds [-200, 200]");
        }
    }

    /** Minimum allowed coefficient value (hundredths), -2.00. */
    public static final int MIN = -200;
    /** Maximum allowed coefficient value (hundredths), 2.00. */
    public static final int MAX = 200;

    /** Target coefficient range for seeded creation: [-100, 100] (hundredths). */
    private static final int TARGET_MIN = -100;
    private static final int TARGET_MAX = 100;

    /**
     * Creates a deterministic DiagnosticFit from a seed.
     *
     * <p>The seed initializes a {@link Random} instance which generates the three
     * target coefficients in range [-100, 100] (hundredths). Candidate coefficients
     * start at zero.</p>
     *
     * @param seed the seed for deterministic generation
     * @return a new DiagnosticFit with generated targets and zero candidates
     */
    public static DiagnosticFit create(long seed) {
        Random rng = new Random(seed);
        int targetA = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        int targetB = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        int targetC = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        return new DiagnosticFit(0, 0, 0, targetA, targetB, targetC);
    }

    /**
     * Returns a new DiagnosticFit with the specified candidate coefficients.
     *
     * <p>The new coefficients are clamped to the bounds [-200, 200]. The target
     * coefficients are retained unchanged.</p>
     *
     * @param a candidate coefficient A (hundredths)
     * @param b candidate coefficient B (hundredths)
     * @param c candidate coefficient C (hundredths)
     * @return a new DiagnosticFit with the bounded candidate coefficients and same targets
     */
    public DiagnosticFit plot(int a, int b, int c) {
        int na = clamp(a, MIN, MAX);
        int nb = clamp(b, MIN, MAX);
        int nc = clamp(c, MIN, MAX);
        return new DiagnosticFit(na, nb, nc, targetA, targetB, targetC);
    }

    /**
     * Evaluates the target curve at the given x.
     *
     * <p>Formula: y = A*x² + b*x + c, where coefficients are divided by 100.0
     * to convert from hundredths.</p>
     *
     * @param x the x value
     * @return the target curve y value
     */
    public double targetAt(double x) {
        double A = targetA / 100.0;
        double B = targetB / 100.0;
        double C = targetC / 100.0;
        return A * x * x + B * x + C;
    }

    /**
     * Evaluates the candidate curve at the given x.
     *
     * <p>Formula: y = A*x² + b*x + c, where coefficients are divided by 100.0
     * to convert from hundredths.</p>
     *
     * @param x the x value
     * @return the candidate curve y value
     */
    public double candidateAt(double x) {
        double A = a / 100.0;
        double B = b / 100.0;
        double C = c / 100.0;
        return A * x * x + B * x + C;
    }

    /**
     * Computes the fit quality score in [0, 100].
     *
     * <p>Quality is derived from the mean squared error (MSE) between the target
     * and candidate curves over exactly 41 samples: x = -2.0, -1.9, ..., 1.9, 2.0.
     *
     * <p>Formula: quality = clamp(round(100 * (1 - sqrt(MSE) / 4.0)), 0, 100)</p>
     *
     * @return the quality score, 0 to 100 inclusive
     */
    public int quality() {
        double mse = 0.0;
        int samples = 0;
        for (int i = -20; i <= 20; i++) {
            double x = i / 10.0;
            double targetY = targetAt(x);
            double candidateY = candidateAt(x);
            double diff = targetY - candidateY;
            mse += diff * diff;
            samples++;
        }
        mse /= samples; // samples == 41
        double rmse = Math.sqrt(mse);
        double raw = 100.0 * (1.0 - rmse / 4.0);
        int quality = (int) Math.round(raw);
        return clamp(quality, 0, 100);
    }

    /**
     * Computes a multiplier in the range [min, max] based on quality.
     *
     * <p>Formula: min + (max - min) * quality / 100</p>
     *
     * @param min the strictly positive finite lower bound (inclusive), at most 1
     * @param max the finite upper bound (inclusive), at least min and at most 1
     * @return the interpolated multiplier
     * @throws IllegalArgumentException if min or max is not finite, or if min <= 0,
     *                                  max > 1, or min > max
     */
    public double multiplier(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("multipliers must be finite");
        }
        if (min <= 0 || max > 1 || max < min) {
            throw new IllegalArgumentException("multipliers require 0 < min <= max <= 1");
        }
        return min + (max - min) * quality() / 100.0;
    }

    /** Clamps a value to the inclusive range [min, max]. */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}