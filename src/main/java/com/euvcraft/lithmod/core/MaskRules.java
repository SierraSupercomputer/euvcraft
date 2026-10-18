package com.euvcraft.lithmod.core;

/**
 * Static gating rules for using a mask on the mask_shop support line.
 *
 * <p>All checks fail closed: invalid, unknown, or out-of-range input returns
 * {@code false} instead of throwing, so callers can safely gate crafting and
 * machine UIs without exception handling.
 */
public final class MaskRules {

    /** The support line whose installed generation gates mask patterning. */
    private static final String MASK_SHOP_LINE = "mask_shop";

    private MaskRules() {
    }

    /**
     * Returns whether a mask for the given die type and tier may be patterned
     * given the currently installed mask_shop generation.
     *
     * <p>Requires both:
     * <ul>
     *   <li>{@code dieRules.canMake(dieType, tierIndex)}, and</li>
     *   <li>{@code generationMatrix.isSatisfied("mask_shop", installedMaskShopGeneration, tierIndex)}.</li>
     * </ul>
     *
     * @param dieType                   canonical die type id (exactly one of {@link DieTypes#ALL})
     * @param tierIndex                 1-based tier index, must exist in BOTH the
     *                                  die rules registry and the matrix registry
     * @param installedMaskShopGeneration installed mask_shop generation, must be >= 1
     * @param dieRules                  die rules bound to a tier registry, may be null
     * @param generationMatrix          generation matrix bound to a tier registry, may be null
     * @return {@code true} only when every check passes; {@code false} (never an
     *         exception) for null/unknown/noncanonical die ids, nonpositive or
     *         absent tiers in either registry, nonpositive generation, or any
     *         failed gate
     */
    public static boolean canPattern(String dieType,
                                     int tierIndex,
                                     int installedMaskShopGeneration,
                                     DieRules dieRules,
                                     GenerationMatrix generationMatrix) {
        // fail closed on unknown/null/noncanonical die ids (no normalization:
        // "COMPUTE" or " compute " is not the canonical id "compute")
        if (dieType == null || !DieTypes.ALL.contains(dieType)) {
            return false;
        }
        // fail closed on nonpositive tiers and nonpositive generation
        if (tierIndex < 1) {
            return false;
        }
        if (installedMaskShopGeneration < 1) {
            return false;
        }
        if (dieRules == null || generationMatrix == null) {
            return false;
        }
        // the tier must exist in BOTH supplied registries; absent in either fails closed
        if (dieRules.tierRegistry().byIndex(tierIndex).isEmpty()) {
            return false;
        }
        if (generationMatrix.tierRegistry().byIndex(tierIndex).isEmpty()) {
            return false;
        }
        // die availability gate
        if (!dieRules.canMake(dieType, tierIndex)) {
            return false;
        }
        // mask_shop generation gate
        return generationMatrix.isSatisfied(MASK_SHOP_LINE, installedMaskShopGeneration, tierIndex);
    }
}
