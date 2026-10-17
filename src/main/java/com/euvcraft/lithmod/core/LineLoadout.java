package com.euvcraft.lithmod.core;

/**
 * The installed fab line components, expressed as node tiers per kind.
 *
 * <p>Each kind field is a tier index (1 to 8) or 0 meaning "not installed". The
 * mask and resist tiers are kept separate because a mask and a resist are not
 * fused into the same component slot, and {@code maintenance} is a multiplier
 * that stays 1.0 in M1 but is kept as a parameter for later milestones.</p>
 *
 * <p>{@link #toLineComponents()} turns this into the M0 {@link LineComponents}
 * record that {@link DefaultStatRules} consumes.</p>
 */
public record LineLoadout(
    int lightSourceTier,
    int opticsTier,
    int stageTier,
    int cleanroomTier,
    int metrologyTier,
    int maskTier,
    int resistTier,
    double maintenance
) {

    public LineLoadout {
        requireTier("lightSourceTier", lightSourceTier);
        requireTier("opticsTier", opticsTier);
        requireTier("stageTier", stageTier);
        requireTier("cleanroomTier", cleanroomTier);
        requireTier("metrologyTier", metrologyTier);
        requireTier("maskTier", maskTier);
        requireTier("resistTier", resistTier);
        if (!Double.isFinite(maintenance) || maintenance < 0.0 || maintenance > 1.0) {
            throw new IllegalArgumentException("maintenance must be in [0.0, 1.0], got " + maintenance);
        }
    }

    private static void requireTier(String name, int value) {
        if (value < 0 || value > 8) {
            throw new IllegalArgumentException(name + " must be in [0, 8], got " + value);
        }
    }

    /**
     * Converts this loadout into the {@link LineComponents} record.
     *
     * @return the equivalent installed components
     */
    public LineComponents toLineComponents() {
        return new LineComponents(
            lightSourceTier,
            opticsTier,
            maskTier,
            resistTier,
            stageTier,
            cleanroomTier,
            metrologyTier,
            maintenance
        );
    }
}