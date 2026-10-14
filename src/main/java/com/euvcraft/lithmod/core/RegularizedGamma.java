package com.euvcraft.lithmod.core;

/**
 * The regularized lower incomplete gamma function and its inverse, used only by
 * {@link YieldCalibration}.
 *
 * <p>Java ships no gamma function, so both are implemented here: {@code P(a, x)} by a
 * series expansion for {@code x < a + 1} and by a continued fraction otherwise, and
 * the quantile by bisection on {@code P}. Everything is pure double arithmetic with a
 * fixed iteration count, so the same input always produces the same bits.</p>
 *
 * <p>This class is package private on purpose: it is an implementation detail of the
 * calibration and is not part of the engine's public surface. Its accuracy is pinned
 * by the gamma self-checks in {@code YieldCalibrationTest}.</p>
 */
final class RegularizedGamma {

    /**
     * The fifteen Lanczos coefficients, the published {@code g = 607/128} form. They
     * reproduce {@code logGamma} to about one ulp over the whole range this class is
     * called with.
     */
    private static final double[] LANCZOS = {
        0.99999999999999709182,
        57.156235665862923517,
        -59.597960355475491248,
        14.136097974741747174,
        -0.49191381609762019978,
        0.33994649984811888699e-4,
        0.46523628927048575665e-4,
        -0.98374475304879564677e-4,
        0.15808870322491248884e-3,
        -0.21026444172410488319e-3,
        0.21743961811521264320e-3,
        -0.16431810653676389022e-3,
        0.84418223983852743293e-4,
        -0.26190838401581408670e-4,
        0.36899182659531622704e-5
    };

    /** The Lanczos {@code g} those coefficients belong to. */
    private static final double LANCZOS_G = 607.0 / 128.0;

    /** {@code 0.5 * ln(2 pi)}. */
    private static final double HALF_LN_TWO_PI = 0.91893853320467274178;

    /** Largest value used instead of a zero denominator in the continued fraction. */
    private static final double TINY = 1e-300;

    /** Relative convergence of both the series and the continued fraction. */
    private static final double EPSILON = 1e-15;

    /** Hard iteration cap, only reached when the input is degenerate. */
    private static final int MAX_ITERATIONS = 1000;

    /** How many bisection steps the quantile search takes, and the cap above that. */
    private static final int BISECTION_STEPS = 200;
    private static final double BISECTION_TOLERANCE = 1e-13;

    private RegularizedGamma() {
    }

    /**
     * Returns the regularized lower incomplete gamma function {@code P(shape, x)}, the
     * probability that a {@code Gamma(shape, 1)} variable is at most {@code x}.
     *
     * @param shape the gamma shape, strictly positive
     * @param x     the argument, not negative
     * @return a value in {@code [0, 1]}
     * @throws IllegalArgumentException if the shape is not positive or the argument is
     *                                  negative or not a number
     */
    static double lowerRegularized(double shape, double x) {
        if (!Double.isFinite(shape) || shape <= 0.0) {
            throw new IllegalArgumentException("shape must be finite and > 0, got " + shape);
        }
        if (Double.isNaN(x) || x < 0.0) {
            throw new IllegalArgumentException("x must be >= 0, got " + x);
        }
        if (x == 0.0) {
            return 0.0;
        }
        if (x < shape + 1.0) {
            return series(shape, x);
        }
        double upper = continuedFractionUpper(shape, x);
        return Double.isNaN(upper) ? 1.0 : 1.0 - upper;
    }

    /**
     * Returns the natural logarithm of the gamma function by the Lanczos approximation.
     *
     * <p>Only the range {@code value > 0} is supported, which is all the engine asks
     * for: the shape of a gamma is a roll count and is therefore at least one.</p>
     *
     * @param value strictly positive
     * @return {@code log(Gamma(value))}
     * @throws IllegalArgumentException if the value is not positive
     */
    static double logGamma(double value) {
        if (!(value > 0.0)) {
            throw new IllegalArgumentException("value must be > 0, got " + value);
        }
        double shifted = value - 1.0;
        double series = LANCZOS[0];
        for (int i = 1; i < LANCZOS.length; i++) {
            series += LANCZOS[i] / (shifted + i);
        }
        double pivot = shifted + LANCZOS_G + 0.5;
        return HALF_LN_TWO_PI + (shifted + 0.5) * StrictMath.log(pivot) - pivot + StrictMath.log(series);
    }

    /**
     * Returns the {@code probability}-quantile of the {@code Gamma(shape, 1)}
     * distribution, found by bisection on {@link #lowerRegularized(double, double)}.
     *
     * @param shape      the gamma shape, strictly positive
     * @param probability the wanted quantile, in {@code [0, 1)}
     * @return the {@code x} with {@code P(shape, x)} equal to {@code probability}, or
     *         {@code 0} for a probability of zero
     * @throws IllegalArgumentException if the shape is not positive or the
     *                                  probability is outside {@code [0, 1)}
     */
    static double quantile(double shape, double probability) {
        if (!Double.isFinite(shape) || shape <= 0.0) {
            throw new IllegalArgumentException("shape must be finite and > 0, got " + shape);
        }
        if (Double.isNaN(probability) || probability < 0.0 || probability >= 1.0) {
            throw new IllegalArgumentException("probability must be in [0, 1), got " + probability);
        }
        if (probability == 0.0) {
            return 0.0;
        }

        double low = 0.0;
        double high = 1.0;
        while (lowerRegularized(shape, high) < probability) {
            high *= 2.0;
        }

        for (int step = 0; step < BISECTION_STEPS && StrictMath.abs(high - low) > BISECTION_TOLERANCE * high; step++) {
            double middle = 0.5 * (low + high);
            if (lowerRegularized(shape, middle) < probability) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return 0.5 * (low + high);
    }

    /**
     * The series expansion of {@code P(shape, x)}, used while {@code x < shape + 1}.
     *
     * <p>The sum is {@code sum(x^n / ((shape + 1) ... (shape + n)))} over {@code n}
     * from zero, and the prefactor carries the {@code 1 / Gamma(shape + 1)} of the
     * expansion, so the two together are {@code P}.</p>
     */
    private static double series(double shape, double x) {
        double argument = shape;
        double sum = 1.0;
        double term = 1.0;
        for (int step = 0; step < MAX_ITERATIONS; step++) {
            argument += 1.0;
            term *= x / argument;
            sum += term;
            if (StrictMath.abs(term) < StrictMath.abs(sum) * EPSILON) {
                break;
            }
        }
        return sum * StrictMath.exp(-x + shape * StrictMath.log(x) - logGamma(shape + 1.0));
    }

    /**
     * The continued fraction for the regularized <em>upper</em> incomplete gamma, used
     * while {@code x >= shape + 1}. Modified Lentz method.
     */
    private static double continuedFractionUpper(double shape, double x) {
        double b = x + 1.0 - shape;
        double c = 1.0 / TINY;
        double d = 1.0 / b;
        double h = d;
        for (int step = 1; step <= MAX_ITERATIONS; step++) {
            double an = -step * (step - shape);
            b += 2.0;
            d = an * d + b;
            if (StrictMath.abs(d) < TINY) {
                d = TINY;
            }
            c = b + an / c;
            if (StrictMath.abs(c) < TINY) {
                c = TINY;
            }
            d = 1.0 / d;
            double delta = d * c;
            h *= delta;
            if (StrictMath.abs(delta - 1.0) < EPSILON) {
                break;
            }
        }
        return StrictMath.exp(-x + shape * StrictMath.log(x) - logGamma(shape)) * h;
    }
}