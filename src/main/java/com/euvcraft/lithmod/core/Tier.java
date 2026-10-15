package com.euvcraft.lithmod.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A lithography tier definition (T1 through T8).
 * All values are data-driven and loaded from JSON.
 */
public record Tier(
    String id,
    int index,
    String displayName,
    String lightSource,
    int resolutionNm,
    int overlayNm,
    int throughputWph,
    int layers,
    int layerTicks,
    double defaultYieldPercent,
    int targetSpeedMhz,
    Integer comfortableCores,
    boolean optional
) {
    public Tier {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(lightSource, "lightSource must not be null");
        
        if (index < 1) {
            throw new IllegalArgumentException("index must be >= 1, got " + index);
        }
        if (resolutionNm <= 0) {
            throw new IllegalArgumentException("resolutionNm must be positive, got " + resolutionNm);
        }
        if (overlayNm <= 0) {
            throw new IllegalArgumentException("overlayNm must be positive, got " + overlayNm);
        }
        if (throughputWph <= 0) {
            throw new IllegalArgumentException("throughputWph must be positive, got " + throughputWph);
        }
        if (layers <= 0) {
            throw new IllegalArgumentException("layers must be positive, got " + layers);
        }
        if (layerTicks <= 0) {
            throw new IllegalArgumentException("layerTicks must be positive, got " + layerTicks);
        }
        if (defaultYieldPercent <= 0 || defaultYieldPercent > 100) {
            throw new IllegalArgumentException("defaultYieldPercent must be in (0, 100], got " + defaultYieldPercent);
        }
        if (targetSpeedMhz <= 0) {
            throw new IllegalArgumentException("targetSpeedMhz must be positive, got " + targetSpeedMhz);
        }
        if (comfortableCores != null && comfortableCores <= 0) {
            throw new IllegalArgumentException("comfortableCores must be positive when present, got " + comfortableCores);
        }
    }

    /**
     * Returns the default yield as a fraction (e.g., 90% -> 0.9).
     */
    public double defaultYieldFraction() {
        return defaultYieldPercent / 100.0;
    }

    /**
     * Returns whether this tier supports core counts (T6+).
     */
    public boolean hasCoreCounts() {
        return comfortableCores != null;
    }

    /**
     * Returns the comfortable core count, or empty if not applicable.
     */
    public Optional<Integer> comfortableCoresOpt() {
        return Optional.ofNullable(comfortableCores);
    }

    /**
     * Returns the layer cycle length in ticks for this tier under the given settings,
     * honoring a configured override when present.
     */
    public int layerTicks(FabSettings settings) {
        return FabSettings.layerTicks(this, settings);
    }

    public static int layerTicks(Tier tier, FabSettings settings) {
        return FabSettings.layerTicks(tier, settings);
    }

    @Override
    public String toString() {
        return "Tier[" + id + "]";
    }
}

