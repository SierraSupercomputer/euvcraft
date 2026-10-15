package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * The parameters a lot was processed under, decided when its first layer was applied
 * and carried with it from there.
 *
 * <p>A lot's hidden cumulative rolls are only meaningful against one calibration, so
 * the calibration has to travel with the lot. Storing it here is what stops the two
 * ends of a run from disagreeing: the layers are rolled against
 * {@link #effectiveYield()} and the lot is judged against the same value, even if the
 * config was reloaded in between.</p>
 *
 * <p>The four values are:</p>
 *
 * <ul>
 *   <li>{@code tierIndex} - the tier the lot was started at, so a later call that names
 *       a different tier is refused rather than silently recalibrating;</li>
 *   <li>{@code cores} - the core count of the die, {@link #NO_CORES} when the die has
 *       none;</li>
 *   <li>{@code effectiveYield} - the pass rate the rolls were calibrated for and the lot
 *       is judged at, as a fraction;</li>
 *   <li>{@code rollCount} - {@code layers * rollsPerLayer} at the time the lot started, which
 *       is the roll count the calibration was computed for.</li>
 * </ul>
 *
 * <p>The spec is hidden data like the rolls are: it is stored in the lot, it is not
 * part of {@link FoupPublicView} and it never goes over the network. It is not a secret
 * in the way a roll is, since a player can work out the tier yield and the core count
 * penalty from public information, but no client is told the value either.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param tierIndex      the tier the lot was started at, at least {@code 1}
 * @param cores          the core count of the die, {@code 0} for none, otherwise positive
 * @param effectiveYield the pass rate the rolls were calibrated for, in {@code (0, 1)}
 * @param rollCount      the number of rolls the whole process draws, at least one
 */
public record RunSpec(int tierIndex, int cores, double effectiveYield, int rollCount) {

    /** Core count of a die that has no core count. */
    public static final int NO_CORES = 0;

    /**
     * Validates the four components.
     *
     * @throws IllegalArgumentException if the tier index or the roll count is below one,
     *                                  if the core count is negative, or if the yield is
     *                                  not a number in {@code (0, 1)}
     */
    public RunSpec {
        if (tierIndex < 1) {
            throw new IllegalArgumentException("tierIndex must be >= 1, got " + tierIndex);
        }
        if (cores < 0) {
            throw new IllegalArgumentException("cores must be >= 0, got " + cores);
        }
        if (!Double.isFinite(effectiveYield) || effectiveYield <= 0.0 || effectiveYield >= 1.0) {
            throw new IllegalArgumentException("effectiveYield must be in (0, 1), got " + effectiveYield);
        }
        if (rollCount < 1) {
            throw new IllegalArgumentException("rollCount must be >= 1, got " + rollCount);
        }
    }

    /**
     * Resolves the spec of a lot that is about to take its first layer.
     *
     * @param tier     the tier the lot is started at, not null
     * @param cores    the core count of the die, or {@code null} for none
     * @param settings the settings in force, not null
     * @return the spec to store on the lot
     * @throws NullPointerException     if the tier or the settings are null
     * @throws IllegalArgumentException if the core count is not positive
     */
    public static RunSpec of(Tier tier, Integer cores, FabSettings settings) {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (cores != null && cores <= 0) {
            throw new IllegalArgumentException("cores must be > 0 when present, got " + cores);
        }
        return new RunSpec(tier.index(), coresOrZero(cores),
            YieldMath.resolveEffectiveYield(tier, settings, cores),
            YieldRollEngine.rollCount(tier, settings));
    }

    /**
     * Returns a core count as the stored form, {@link #NO_CORES} for none.
     *
     * @param cores the core count, or {@code null} for none
     * @return the stored core count
     * @throws IllegalArgumentException if the core count is negative
     */
    public static int coresOrZero(Integer cores) {
        if (cores == null) {
            return NO_CORES;
        }
        if (cores < 0) {
            throw new IllegalArgumentException("cores must be > 0 when present, got " + cores);
        }
        return cores;
    }

    /**
     * Returns whether the die carries a core count.
     *
     * @return {@code true} when the core count is not {@link #NO_CORES}
     */
    public boolean hasCores() {
        return this.cores != NO_CORES;
    }

    /**
     * Returns whether this spec belongs to a given tier and core count.
     *
     * <p>{@code null} and {@link #NO_CORES} mean the same thing here, so a caller may
     * pass whichever it has.</p>
     *
     * @param tier  the tier the caller names, not null
     * @param cores the core count the caller names, or {@code null} for none
     * @return whether both agree with this spec
     */
    public boolean matches(Tier tier, Integer cores) {
        return tier != null && this.tierIndex == tier.index() && this.cores == coresOrZero(cores);
    }

    /**
     * Returns the core count the way a caller passes it, {@code null} for none.
     *
     * @return the core count, or {@code null} when the die has none
     */
    public Integer coresOrNull() {
        return hasCores() ? this.cores : null;
    }

    /**
     * Prints the spec without the effective yield.
     *
     * <p>The yield is the one number here that is not public information, so it is left
     * out of the one string a log line is likely to carry.</p>
     *
     * @return a description of the tier, core count and roll count
     */
    @Override
    public String toString() {
        return "RunSpec[tier=" + this.tierIndex + ", cores=" + (hasCores() ? this.cores : "none")
            + ", rolls=" + this.rollCount + "]";
    }
}