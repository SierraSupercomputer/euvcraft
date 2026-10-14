package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * The pass or fail rule of a fab lot and the core count penalty that feeds it.
 *
 * <p>A wafer's hidden cumulative roll is the product of every step roll drawn for it so
 * far and lives in {@code (0, 1]}, so a wafer passes when that product is large. The
 * rule is therefore a single threshold: at a yield of {@code y} a wafer passes when its
 * cumulative roll is greater than {@code 1 - y}, which is why a 90% yield process needs
 * a cumulative roll above {@code 0.10}.</p>
 *
 * <p>From T6 a compute die may be built with more cores than the process is comfortable
 * with, and that costs yield. The cost is
 * {@code effectiveYield = tierYield ^ (1 + k * max(0, cores / comfortableCores - 1))},
 * which leaves every core count at or below the comfortable one at the tier's own yield
 * and falls away faster the further past it the die goes.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class YieldMath {

    /** Smallest effective yield the engine ever resolves, as a fraction. */
    public static final double MIN_EFFECTIVE_YIELD = 0.01;

    /** Largest effective yield the engine ever resolves, as a fraction. */
    public static final double MAX_EFFECTIVE_YIELD = 0.99;

    private YieldMath() {
    }

    /**
     * Returns the cumulative roll a wafer has to beat at a given yield.
     *
     * @param yield the target pass rate as a fraction, {@code 0.9} for 90%
     * @return {@code 1 - yield}, so {@code 0.10} at a 90% yield
     * @throws IllegalArgumentException if the yield is not a number
     */
    public static double passThreshold(double yield) {
        if (Double.isNaN(yield)) {
            throw new IllegalArgumentException("yield must be a number");
        }
        return 1.0 - yield;
    }

    /**
     * Returns whether a cumulative roll passes at a given yield.
     *
     * <p>The comparison is strict, so a roll exactly on the threshold fails. That keeps
     * the rule identical to {@code cumulative > passThreshold(yield)}.</p>
     *
     * @param cumulative the wafer's cumulative roll
     * @param yield      the target pass rate as a fraction
     * @return whether the wafer passes
     */
    public static boolean passes(double cumulative, double yield) {
        return cumulative > passThreshold(yield);
    }

    /**
     * Returns the yield a lot is actually judged against, given the tier's own yield,
     * the core count a compute die was made with and the steepness of the penalty.
     *
     * <p>A null core count or a null, zero or negative comfortable core count means the
     * process has no core count to judge, so the tier yield is returned unchanged.</p>
     *
     * @param tierYield        the tier's yield as a fraction
     * @param cores            the core count of the die, or {@code null} for none
     * @param comfortableCores the core count the process is comfortable with, or
     *                         {@code null} when the tier has none
     * @param steepness        the penalty steepness {@code k}
     * @return the effective yield as a fraction
     * @throws IllegalArgumentException if the tier yield is not a number
     */
    public static double effectiveYield(double tierYield, Integer cores, Integer comfortableCores, double steepness) {
        if (Double.isNaN(tierYield)) {
            throw new IllegalArgumentException("tierYield must be a number");
        }
        if (cores == null || comfortableCores == null || comfortableCores <= 0) {
            return tierYield;
        }
        double exponent = penaltyExponent(cores, comfortableCores, steepness);
        return Math.pow(tierYield, exponent);
    }

    /**
     * Returns the exponent {@link #effectiveYield} raises the tier yield to.
     *
     * <p>It is {@code 1} whenever the die is at or below the comfortable core count, so
     * the effective yield is then exactly the tier yield.</p>
     *
     * @param cores            the core count of the die, or {@code null} for none
     * @param comfortableCores the core count the process is comfortable with
     * @param steepness        the penalty steepness {@code k}
     * @return an exponent of at least {@code 1}
     */
    static double penaltyExponent(Integer cores, int comfortableCores, double steepness) {
        if (cores == null || cores <= 0 || comfortableCores <= 0 || steepness <= 0.0) {
            return 1.0;
        }
        double overComfort = (double) cores / comfortableCores - 1.0;
        double penalty = Math.max(0.0, overComfort);
        return 1.0 + steepness * penalty;
    }

    /**
     * Clamps a base yield into the range the engine accepts.
     *
     * @param yield the base yield as a fraction
     * @return the yield limited to {@link #MIN_EFFECTIVE_YIELD} and
     *         {@link #MAX_EFFECTIVE_YIELD}
     * @throws IllegalArgumentException if the yield is not a number
     */
    public static double clampYield(double yield) {
        if (Double.isNaN(yield)) {
            throw new IllegalArgumentException("yield must be a number");
        }
        return Math.min(MAX_EFFECTIVE_YIELD, Math.max(MIN_EFFECTIVE_YIELD, yield));
    }

    /**
     * Resolves the effective yield of a tier from the tier data and the settings in
     * force, in four steps:
     *
     * <ol>
     *   <li>the base is the {@code tierYieldOverrides} entry for the tier when there is
     *       one, used exactly as written; otherwise it is the tier's own default yield
     *       from tier data scaled by {@code yieldMultiplier};</li>
     *   <li>the base is clamped to {@link #MIN_EFFECTIVE_YIELD} and
     *       {@link #MAX_EFFECTIVE_YIELD}, so a multiplier or a tier above 99% cannot
     *       demand a certain pass or a certain failure;</li>
     *   <li>the comfortable core count is the {@code comfortableCoreOverrides} entry for
     *       the tier when there is one, otherwise the tier's own value;</li>
     *   <li>the core count penalty is applied with {@code coreCountPenaltySteepness}
     *       from the settings.</li>
     * </ol>
     *
     * @param tier     the tier being run, not null
     * @param settings the settings in force, not null
     * @param cores    the core count of the die, or {@code null} for none
     * @return the effective yield as a fraction, within the clamped range
     */
    public static double resolveEffectiveYield(Tier tier, FabSettings settings, Integer cores) {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        Integer override = settings.tierYieldOverrides().get(tier.id());
        double base = override != null
            ? override / 100.0
            : tier.defaultYieldFraction() * settings.yieldMultiplier();

        Integer comfortable = settings.comfortableCoreOverrides().get(tier.id());
        int comfortableCores = comfortable != null ? comfortable
            : (tier.comfortableCores() != null ? tier.comfortableCores() : 0);

        return effectiveYield(clampYield(base), cores, comfortableCores <= 0 ? null : comfortableCores,
            settings.coreCountPenaltySteepness());
    }
}