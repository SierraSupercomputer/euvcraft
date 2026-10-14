package com.euvcraft.lithmod.core;

/**
 * Rules for computing a fab line's stats from its installed components.
 * Kept behind an interface so rule sets can be replaced.
 */
public interface StatRules {

    /**
     * Computes line stats for the given components when running at targetTier.
     */
    LineStats stats(LineComponents components, int targetTier);

    /**
     * True when the line can run the given target tier based on resolution and overlay.
     */
    boolean canRunTier(LineComponents components, int targetTier);
}
