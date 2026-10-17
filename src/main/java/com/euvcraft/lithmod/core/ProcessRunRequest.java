package com.euvcraft.lithmod.core;

import java.util.Objects;
import java.util.Map;

/**
 * The inputs for one attempted Process Cell run.
 */
public record ProcessRunRequest(
        FoupContents lot,
        String dieType,
        Tier tier,
        Integer cores,
        int usesRemaining,
        LineStats lineStats,
        boolean lineCanRunTier,
        boolean dieMakeable,
        Map<String, Integer> installedGenerations,
        GenerationMatrix generationMatrix,
        FabSettings settings,
        int energyPerTick
) {

    public ProcessRunRequest {
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(lineStats, "lineStats must not be null");
        Objects.requireNonNull(generationMatrix, "generationMatrix must not be null");
        if (energyPerTick < 0 || usesRemaining < 0 || !Double.isFinite(lineStats.defectFactor())) {
            throw new IllegalArgumentException("Invalid energy, mask uses, or line statistics");
        }
        if (dieType != null && !DieTypes.ALL.contains(dieType)) {
            throw new IllegalArgumentException("Unknown die type: " + dieType);
        }
        if (installedGenerations == null) {
            installedGenerations = Map.of();
        } else {
            installedGenerations = Map.copyOf(installedGenerations);
        }
    }
}
