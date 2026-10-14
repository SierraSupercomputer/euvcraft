package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * Default implementation of {@link StatRules}.
 */
public final class DefaultStatRules implements StatRules {

    private final TierRegistry tierRegistry;
    private final StatRulesParams params;

    public DefaultStatRules(TierRegistry tierRegistry) {
        this(tierRegistry, StatRulesParams.defaults());
    }

    public DefaultStatRules(TierRegistry tierRegistry, StatRulesParams params) {
        this.tierRegistry = Objects.requireNonNull(tierRegistry, "tierRegistry must not be null");
        this.params = (params == null) ? StatRulesParams.defaults() : params;
    }


    public TierRegistry tierRegistry() {
        return tierRegistry;
    }

    public StatRulesParams params() {
        return params;
    }

    @Override
    public LineStats stats(LineComponents components, int targetTier) {
        Objects.requireNonNull(components, "components must not be null");
        int resolutionTier = Math.min(components.lightSourceTier(),
            Math.min(components.opticsTier(), components.resistTier()));
        int overlayTier = Math.min(components.stageTier(), components.metrologyTier());
        int throughputTier = Math.min(components.lightSourceTier(), components.stageTier());

        int resolutionNm = tierStat(tierRegistry, resolutionTier, Tier::resolutionNm);
        int overlayNm = tierStat(tierRegistry, overlayTier, Tier::overlayNm);
        int throughputWph = tierStat(tierRegistry, throughputTier, Tier::throughputWph);

        double cleanroomFactor = tierFactor(components.cleanroomTier(), targetTier, params.penaltyPerTierDeficit());
        double maskFactor = tierFactor(components.maskTier(), targetTier, params.penaltyPerTierDeficit());
        double defectFactor = components.maintenance() * cleanroomFactor * maskFactor;
        if (defectFactor < 0.0) defectFactor = 0.0;
        if (defectFactor > 1.0) defectFactor = 1.0;

        return new LineStats(resolutionNm, overlayNm, throughputWph, defectFactor,
            resolutionTier, overlayTier, throughputTier);
    }

    @Override
    public boolean canRunTier(LineComponents components, int targetTier) {
        Objects.requireNonNull(components, "components must not be null");
        LineStats s = stats(components, targetTier);
        return s.resolutionTier() >= targetTier && s.overlayTier() >= targetTier;
    }

    private static int tierStat(TierRegistry registry, int tierIndex, java.util.function.ToIntFunction<Tier> getter) {
        if (tierIndex <= 0) return 0;
        return registry.byIndex(tierIndex).map(t -> getter.applyAsInt(t)).orElse(0);
    }

    private static double tierFactor(int installedTier, int targetTier, double penalty) {
        if (installedTier >= targetTier) return 1.0;
        int deficit = targetTier - installedTier;
        double value = 1.0 - penalty * deficit;
        if (value < 0.0) value = 0.0;
        return value;
    }
}
