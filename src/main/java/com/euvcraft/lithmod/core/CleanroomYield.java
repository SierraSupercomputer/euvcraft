package com.euvcraft.lithmod.core;

/** Recalibrates the existing latent roll product once; draws no new random values. */
public final class CleanroomYield {
    private CleanroomYield() {}
    public static FoupContents finish(FoupContents lot, Tier tier) {
        if(lot.roomYieldPermille()!=0)return lot;
        YieldRollEngine.requireCompleteLot(lot,tier);
        if(tier.index()!=5||lot.run()==null)return lot;
        int factor=lot.roomExposure().meanPermille();
        RunSpec old=lot.run();double nextYield=old.effectiveYield()*factor/1000.0;
        // Every layer uses the same frozen calibration and defect factor; the latter
        // cancels in this ratio. Reweighting the product preserves the underlying draws.
        double ratio=YieldCalibration.baseWeight(old.rollCount(),nextYield)/YieldCalibration.baseWeight(old.rollCount(),old.effectiveYield());
        var wafers=lot.wafers().stream().map(w->new Wafer(w.layerIndex(),
            Math.max(Float.MIN_VALUE,(float)StrictMath.pow(w.cumulativeRoll(),ratio)),w.flags())).toList();
        var spec=new RunSpec(old.dieType(),old.tierIndex(),old.cores(),nextYield,old.rollCount());
        return new FoupContents(lot.lotId(),lot.capacity(),wafers,spec,lot.grade(),lot.preparationGeneration(),lot.roomExposure(),factor);
    }
}
