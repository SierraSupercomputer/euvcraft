package com.euvcraft.lithmod.core;

/**
 * Computed statistics for a lithography line running at a target tier.
 */
public record LineStats(
    int resolutionNm,
    int overlayNm,
    int throughputWph,
    double defectFactor,
    int resolutionTier,
    int overlayTier,
    int throughputTier
) {
    public LineStats {
        if (defectFactor < 0.0 || defectFactor > 1.0) {
            throw new IllegalArgumentException("defectFactor must be in [0.0, 1.0], got " + defectFactor);
        }
    }
}
