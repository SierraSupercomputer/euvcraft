package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * Data-driven rule for a single die/chip type: which tiers can produce it, whether
 * its effective tier is capped, and whether core counts apply.
 *
 * The die type is stored as a normalized lowercase string (e.g. "compute").
 * Indexes are 1-based tier indexes and are validated against a TierRegistry by DieRules.
 */
public record DieRule(String dieType, int firstTier, Integer capTier, boolean usesCoreCount) {
    public DieRule {
        Objects.requireNonNull(dieType, "dieType must not be null");
        String normalized = dieType.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("dieType must not be blank");
        }
        if (firstTier < 1) {
            throw new IllegalArgumentException("firstTier must be >= 1, got " + firstTier);
        }
        if (capTier != null) {
            if (capTier < 1) {
                throw new IllegalArgumentException("capTier must be >= 1 when present, got " + capTier);
            }
            if (firstTier > capTier) {
                throw new IllegalArgumentException("firstTier (" + firstTier + ") must be <= capTier (" + capTier + ")");
            }
        }
        dieType = normalized;
    }

    public DieRule(String dieType, int firstTier, boolean usesCoreCount) {
        this(dieType, firstTier, (Integer) null, usesCoreCount);
    }

    /**
     * Whether this rule has a tier cap.
     */
    public boolean hasCap() {
        return capTier != null;
    }
}
