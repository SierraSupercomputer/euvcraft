package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * Immutable record describing one crafted photomask.
 *
 * <p>A mask is bound to a canonical die type and a 1-based tier index, and
 * carries a countdown of remaining uses. When no uses remain the mask is
 * "spent" and can no longer pattern wafers.
 *
 * @param dieType       canonical die type id, exactly one of {@link DieTypes#ALL}
 * @param tierIndex     1-based tier index the mask targets (no upper cap here;
 *                      registry existence is enforced by {@link MaskRules})
 * @param usesRemaining remaining uses, {@code >= 0}; zero means spent
 */
public record MaskData(String dieType, int tierIndex, int usesRemaining) {

    public MaskData {
        Objects.requireNonNull(dieType, "dieType must not be null");
        if (!DieTypes.ALL.contains(dieType)) {
            throw new IllegalArgumentException(
                "dieType must be one of " + DieTypes.ALL + ", got '" + dieType + "'");
        }
        if (tierIndex < 1) {
            throw new IllegalArgumentException("tierIndex must be >= 1, got " + tierIndex);
        }
        if (usesRemaining < 0) {
            throw new IllegalArgumentException("usesRemaining must be >= 0, got " + usesRemaining);
        }
    }

    /**
     * @return {@code true} when no uses remain.
     */
    public boolean isSpent() {
        return usesRemaining == 0;
    }

    /**
     * @return a new {@code MaskData} with one fewer use; this record is unchanged.
     * @throws IllegalStateException when this mask is already spent
     */
    public MaskData consumeUse() {
        if (isSpent()) {
            throw new IllegalStateException(
                "mask for die type '" + dieType + "' at tier " + tierIndex + " is spent");
        }
        return new MaskData(dieType, tierIndex, usesRemaining - 1);
    }
}
