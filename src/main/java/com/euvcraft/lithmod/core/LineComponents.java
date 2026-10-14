package com.euvcraft.lithmod.core;

/**
 * Installed components for a single lithography line.
 * Each tier field is a tier index (1 to 8) or 0 meaning "not installed".
 * maintenance is in [0.0, 1.0].
 */
public record LineComponents(
    int lightSourceTier,
    int opticsTier,
    int maskTier,
    int resistTier,
    int stageTier,
    int cleanroomTier,
    int metrologyTier,
    double maintenance
) {
    public LineComponents {
        if (lightSourceTier < 0 || lightSourceTier > 8) {
            throw new IllegalArgumentException("lightSourceTier must be in [0, 8], got " + lightSourceTier);
        }
        if (opticsTier < 0 || opticsTier > 8) {
            throw new IllegalArgumentException("opticsTier must be in [0, 8], got " + opticsTier);
        }
        if (maskTier < 0 || maskTier > 8) {
            throw new IllegalArgumentException("maskTier must be in [0, 8], got " + maskTier);
        }
        if (resistTier < 0 || resistTier > 8) {
            throw new IllegalArgumentException("resistTier must be in [0, 8], got " + resistTier);
        }
        if (stageTier < 0 || stageTier > 8) {
            throw new IllegalArgumentException("stageTier must be in [0, 8], got " + stageTier);
        }
        if (cleanroomTier < 0 || cleanroomTier > 8) {
            throw new IllegalArgumentException("cleanroomTier must be in [0, 8], got " + cleanroomTier);
        }
        if (metrologyTier < 0 || metrologyTier > 8) {
            throw new IllegalArgumentException("metrologyTier must be in [0, 8], got " + metrologyTier);
        }
        if (maintenance < 0.0 || maintenance > 1.0) {
            throw new IllegalArgumentException("maintenance must be in [0.0, 1.0], got " + maintenance);
        }
        // range validated above
    }
}
