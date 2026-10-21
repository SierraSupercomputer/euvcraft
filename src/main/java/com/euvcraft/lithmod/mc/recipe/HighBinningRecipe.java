package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.HighBinningRules;

import java.util.Objects;

/**
 * One high-binning transition recipe as it appears in
 * {@code data/lithmod/high_binning/*.json}, kept as pure data so validation can
 * be unit tested with no game running.
 *
 * <p>This record pairs a die family (logic or compute) with a validated
 * {@link HighBinningRules.Transition}. The constructor enforces the product
 * scope for this increment: only logic and compute families, source tier at
 * least 3, target tier exactly {@code source + 1}, logic target tier capped at
 * 5. Core-count rules are already enforced by the {@link HighBinningRules.Transition}
 * constructor and by {@link HighBinningRules#judge} at admission time.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 *
 * @param dieType    the die family, either "logic" or "compute"
 * @param transition the explicit transition configuration, not null
 */
public record HighBinningRecipe(
    String dieType,
    HighBinningRules.Transition transition
) {

    private static final String LOGIC = "logic";
    private static final String COMPUTE = "compute";

    public HighBinningRecipe {
        Objects.requireNonNull(dieType, "dieType must not be null");
        Objects.requireNonNull(transition, "transition must not be null");

        // Only logic and compute families are supported
        if (!LOGIC.equals(dieType) && !COMPUTE.equals(dieType)) {
            throw new IllegalArgumentException("high binning only supports logic and compute, got " + dieType);
        }

        // Source tier must be at least 3 (T3+)
        if (transition.sourceTier() < 3) {
            throw new IllegalArgumentException("high binning requires source tier T3 or higher, got T" + transition.sourceTier());
        }

        // Target tier must be exactly source + 1 (already validated by Transition, but reaffirmed)
        if (transition.targetTier() != transition.sourceTier() + 1) {
            throw new IllegalArgumentException("target tier must be source + 1");
        }

        // Logic target tier must not exceed 5 (per OWNER_SPEC: logic ends at T5)
        if (LOGIC.equals(dieType) && transition.targetTier() > 5) {
            throw new IllegalArgumentException("logic high binning target tier must not exceed 5, got T" + transition.targetTier());
        }
    }

    /**
     * Returns a canonical string representation for logging and debugging.
     * Does not leak internal state beyond the public fields.
     */
    @Override
    public String toString() {
        return "HighBinningRecipe[" + dieType + ": " + transition + "]";
    }
}