package com.euvcraft.lithmod.core;

/** Converts a saved preparation penalty into a threshold for the original rolls. */
public final class PreparedYieldRules {
    private PreparedYieldRules() {}

    public static double multiplier(FoupContents lot, int waferIndex) {
        if (lot.preparationGeneration() != FoupContents.PREPARATION_G3) return 1.0;
        if (!lot.g3Preparation().hasFactors()) throw new IllegalArgumentException("G3 result is redacted");
        return lot.g3Preparation().factorPermilleAt(waferIndex) / 1000.0;
    }

    /** The roll weights were calibrated at the source yield, so retain that scale. */
    public static double threshold(RunSpec spec, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0 || multiplier > 1)
            throw new IllegalArgumentException("yield multiplier must be 0..1");
        if (multiplier == 0) return 1.0;
        if (multiplier == 1) return 1.0 - spec.effectiveYield();
        double weight = YieldCalibration.baseWeight(spec.rollCount(), spec.effectiveYield());
        double quantile = RegularizedGamma.quantile(spec.rollCount(), spec.effectiveYield() * multiplier);
        return StrictMath.exp(-weight * quantile);
    }
}
