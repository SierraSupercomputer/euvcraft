package com.euvcraft.lithmod.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Turns a wanted pass rate into the step weight the roll model uses.
 *
 * <p><strong>The roll model.</strong> One step roll is {@code r = u ^ w}, where
 * {@code u} is drawn from an {@link Rng} and {@code w} is the step weight. A wafer's
 * cumulative roll is the product of its step rolls. Taking negative logarithms turns
 * that product into a sum: {@code -ln(cumulative) = w * sum(-ln u)}, and every
 * {@code -ln u} is exponential with mean one, so {@code -ln(cumulative)} is
 * {@code w} times a {@code Gamma(K, 1)} variable, where {@code K} is the number of rolls
 * the whole process draws.</p>
 *
 * <p><strong>The calibration.</strong> A wafer passes when its cumulative roll exceeds
 * {@code 1 - Y}, that is when {@code w * G < -ln(1 - Y)} for a {@code Gamma(K, 1)}
 * variable {@code G}. Writing {@code q} for the {@code Y}-quantile of that gamma, the
 * weight that puts exactly a fraction {@code Y} of wafers below the threshold is
 * therefore {@code -ln(1 - Y) / q}. Finding {@code q} is a bisection on the regularized
 * lower incomplete gamma function in {@link RegularizedGamma}, so the result is pure
 * arithmetic: the same {@code (K, Y)} always gives the same weight down to the last bit,
 * and it does not depend on any random draw or on when it was computed.</p>
 *
 * <p><strong>The defect factor.</strong> A line that is not perfectly maintained has a
 * defect factor below one and is weighted {@code baseWeight / max(defectFactor, 0.05)}:
 * the same calibration then applies to more damage, which lowers the pass rate without
 * any extra table.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class YieldCalibration {

    /** Defect factor floor, so a line at zero health still runs instead of dividing by zero. */
    public static final double MIN_DEFECT_FACTOR = 0.05;

    private static final ConcurrentMap<Key, Double> CACHE = new ConcurrentHashMap<>();

    private YieldCalibration() {
    }

    /**
     * Returns the step weight that makes {@code rollCount} rolls pass at the given yield.
     *
     * <p>This is the uncached computation. Callers on a hot path want
     * {@link #baseWeight(int, double)} instead.</p>
     *
     * @param rollCount the number of rolls the whole process draws, at least one
     * @param yield     the wanted pass rate as a fraction, strictly between zero and one
     * @return the step weight, strictly positive
     * @throws IllegalArgumentException if the roll count is below one or the yield is
     *                                  outside {@code (0, 1)}
     */
    public static double calibrateBaseWeight(int rollCount, double yield) {
        if (rollCount < 1) {
            throw new IllegalArgumentException("rollCount must be >= 1, got " + rollCount);
        }
        if (!(yield > 0.0) || !(yield < 1.0)) {
            throw new IllegalArgumentException("yield must be in (0, 1), got " + yield);
        }

        double threshold = -Math.log1p(-yield);
        double quantile = RegularizedGamma.quantile(rollCount, yield);
        return threshold / quantile;
    }

    /**
     * Returns the cached {@link #calibrateBaseWeight(int, double)} for a roll count and a
     * yield.
     *
     * <p>The cache is keyed by the two numbers the weight actually depends on. A tier
     * contributes only through its layer count, which fixes the roll count, and through
     * its resolved yield, so keying on the pair is the same thing as keying on the tier
     * as well. {@link #clearCache()} drops everything, which the yield service does when
     * the settings change.</p>
     *
     * @param rollCount the number of rolls the whole process draws, at least one
     * @param yield     the wanted pass rate as a fraction, strictly between zero and one
     * @return the step weight, strictly positive
     * @throws IllegalArgumentException if the roll count is below one or the yield is
     *                                  outside {@code (0, 1)}
     */
    public static double baseWeight(int rollCount, double yield) {
        Key key = new Key(rollCount, yield);
        Double cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        double weight = calibrateBaseWeight(rollCount, yield);
        Double raced = CACHE.putIfAbsent(key, weight);
        return raced != null ? raced : weight;
    }

    /**
     * Returns the step weight of one step of a line running at the given defect factor.
     *
     * @param rollCount    the number of rolls the whole process draws, at least one
     * @param yield        the wanted pass rate as a fraction, strictly between zero and one
     * @param defectFactor how healthy the line is; values below
     *                     {@link #MIN_DEFECT_FACTOR} are raised to it
     * @return the step weight, strictly positive
     */
    public static double stepWeight(int rollCount, double yield, double defectFactor) {
        return baseWeight(rollCount, yield) / Math.max(defectFactor, MIN_DEFECT_FACTOR);
    }

    /**
     * Draws one step roll, {@code u ^ weight}.
     *
     * <p>The roll is narrowed to a float because that is what a wafer stores. Narrowing
     * a value that sits just above zero can round it to {@code 0.0f}, which
     * {@link Wafer} rejects, so that one value becomes {@link Float#MIN_VALUE} instead.</p>
     *
     * @param rng    the source to draw {@code u} from, not null
     * @param weight the step weight, strictly positive
     * @return a roll in {@code (0, 1]}
     */
    public static float stepRoll(Rng rng, double weight) {
        float roll = (float) Math.pow(rng.nextDouble(), weight);
        return roll <= 0.0f ? Float.MIN_VALUE : roll;
    }

    /**
     * Drops every cached weight.
     *
     * <p>The values are pure arithmetic and cannot go stale on their own, so this exists
     * to bound the cache and to make a settings change visible rather than to fix a
     * wrong value.</p>
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Returns how many weights are cached.
     *
     * @return the cache size
     */
    public static int cacheSize() {
        return CACHE.size();
    }

    /** Cache key: the roll count and the exact bits of the yield. */
    private record Key(int rollCount, long yieldBits) {
        Key(int rollCount, double yield) {
            this(rollCount, Double.doubleToLongBits(yield));
        }
    }
}