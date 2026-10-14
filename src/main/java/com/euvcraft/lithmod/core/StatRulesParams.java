package com.euvcraft.lithmod.core;

/**
 * Parameters used by {@link StatRules} implementations.
 * The penalty per tier deficit is the only configurable value for now.
 */
public record StatRulesParams(double penaltyPerTierDeficit) {

    /** Default penalty per tier deficit (15%). */
    public static final double DEFAULT_PENALTY_PER_TIER_DEFICIT = 0.15;

    public StatRulesParams {
        if (penaltyPerTierDeficit < 0.0) {
            throw new IllegalArgumentException("penaltyPerTierDeficit must be >= 0, got " + penaltyPerTierDeficit);
        }
    }

    /** Returns params with the default penalty. */
    public static StatRulesParams defaults() {
        return new StatRulesParams(DEFAULT_PENALTY_PER_TIER_DEFICIT);
    }
}
