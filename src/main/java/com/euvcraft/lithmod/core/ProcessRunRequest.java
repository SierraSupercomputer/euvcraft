package com.euvcraft.lithmod.core;

import java.util.List;
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
        if (installedGenerations == null) {
            installedGenerations = Map.of();
        } else {
            installedGenerations = Map.copyOf(installedGenerations);
        }
    }
}
