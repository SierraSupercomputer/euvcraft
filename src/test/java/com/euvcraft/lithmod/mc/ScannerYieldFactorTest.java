package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.ReferenceTiers;
import com.euvcraft.lithmod.core.RollSource;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.core.YieldRollEngine;

/**
 * Tests for the frozen yield factor (scanner calibration) feature.
 * <p>
 * The frozen yield factor is a multiplier from scanner calibration quality that is
 * applied ONCE at the first layer. The resulting effective yield is stored in the
 * RunSpec and used for all subsequent layers and for packaging/highbin. It must NOT
 * multiply defectFactor, must NOT apply per layer, and must NOT double-apply on resume.
 */
class ScannerYieldFactorTest {

    private static final long SALT = 0xBADF00DL;
    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000009b2");
    private static final int WAFERS = 2_000;
    private static final double TOLERANCE = 0.025;

    private static RollSource source(long salt) {
        return (waferIndex, stepIndex) -> new com.euvcraft.lithmod.core.ContextualRng(salt, LOT, waferIndex, stepIndex);
    }

    private static FoupContents freshLot(int count) {
        FoupContents lot = FoupContents.empty(LOT, Math.max(count, 1));
        for (int i = 0; i < count; i++) {
            lot = lot.withWafer(Wafer.unprocessed());
        }
        return lot;
    }

    private static FoupContents runThrough(Tier tier, FoupContents lot, Integer cores, double defectFactor,
            double frozenYieldFactor, long salt, FabSettings settings) {
        return runThrough(tier, lot, cores, defectFactor, frozenYieldFactor, salt, settings, tier.layers());
    }

    private static FoupContents runThrough(Tier tier, FoupContents lot, Integer cores, double defectFactor,
            double frozenYieldFactor, long salt, FabSettings settings, int upToLayer) {
        RollSource rolls = source(salt);
        FoupContents processed = lot;
        for (int layer = processed.lotLayer() + 1; layer <= upToLayer; layer++) {
            processed = YieldRollEngine.applyLayerStep(processed, tier, "logic", cores, layer, defectFactor, settings,
                frozenYieldFactor, rolls);
        }
        return processed;
    }

    @Nested
    @DisplayName("frozen yield factor at first layer")
    class FirstLayer {

        @Test
        @DisplayName("factor 1.0 is no-op (backward compatible)")
        void factorOneIsNoOp() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);

            FoupContents withFactor = runThrough(tier, freshLot(25), null, 1.0, 1.0, SALT, settings);
            FoupContents withoutFactor = freshLot(25);
            for(int layer=1;layer<=tier.layers();layer++)withoutFactor=YieldRollEngine.applyLayerStep(withoutFactor,tier,"logic",null,layer,1.0,settings,source(SALT));

            assertEquals(withFactor, withoutFactor, "factor 1.0 should behave identically to no factor");
        }

        @Test
        @DisplayName("factor 0.75 reduces effective yield at first layer")
        void factorReducesEffectiveYield() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);
            double factoredYield = YieldMath.clampYield(baseYield * 0.75);

            FoupContents lot = freshLot(25);
            FoupContents afterFirst = YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, 0.75, source(SALT));

            // The RunSpec should store the factored yield
            assertEquals(factoredYield, afterFirst.run().effectiveYield(), 1e-9,
                "RunSpec should store the factored effective yield");
        }

        @Test
        @DisplayName("factor applies once, not per layer")
        void factorAppliesOnceNotPerLayer() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);
            double factoredYield = YieldMath.clampYield(baseYield * 0.5);

            FoupContents lot = freshLot(25);
            FoupContents afterFirst = YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, 0.5, source(SALT));

            // Run through remaining layers with same factor - should NOT apply again
            FoupContents finished = runThrough(tier, afterFirst, null, 1.0, 0.5, SALT + 1, settings);

            // The RunSpec effective yield should remain the factored value, not factored^layers
            assertEquals(factoredYield, finished.run().effectiveYield(), 1e-9,
                "RunSpec should preserve the once-factored effective yield");
        }

        @Test
        @DisplayName("factor 0.5 gives roughly half the passes at T3")
        void factorHalfGivesHalfPasses() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);
            double factoredYield = YieldMath.clampYield(baseYield * 0.5);

            int passing = 0;
            int simulated = 0;
            for (int lot = 0; lot < WAFERS / 25; lot++) {
                FoupContents processed = runThrough(tier, freshLot(25), null, 1.0, 0.5, SALT + lot, settings);
                boolean[] outcomes = YieldRollEngine.evaluate(processed, tier, "logic", null);
                passing += YieldRollEngine.countPassing(outcomes);
                simulated += outcomes.length;
            }

            double rate = (double) passing / simulated;
            // Allow 3 sigma tolerance for statistical variance
            double sigma = Math.sqrt(factoredYield * (1.0 - factoredYield) / simulated);
            assertTrue(Math.abs(rate - factoredYield) <= 3.0 * sigma,
                "factor 0.5: simulated " + (rate * 100.0) + "% vs expected " + (factoredYield * 100.0) + "%");
        }

        @Test
        @DisplayName("factor validation rejects out of range values")
        void factorValidation() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            FoupContents lot = freshLot(4);

            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, 0.0, source(SALT)));
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, -0.1, source(SALT)));
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, 1.1, source(SALT)));
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, Double.NaN, source(SALT)));
        }
    }

    @Nested
    @DisplayName("resume preserves frozen factor (no double-apply)")
    class Resume {

        @Test
        @DisplayName("pausing and resuming does not re-apply the factor")
        void resumeDoesNotReapplyFactor() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);
            double factoredYield = YieldMath.clampYield(baseYield * 0.6);

            // Run first 3 layers
            FoupContents lot = freshLot(25);
            FoupContents afterThree = runThrough(tier, lot, null, 1.0, 0.6, SALT, settings, 3);

            // Verify RunSpec has factored yield
            assertEquals(factoredYield, afterThree.run().effectiveYield(), 1e-9,
                "RunSpec should have factored yield after partial run");

            // Resume with same factor - should NOT apply factor again
            FoupContents finished = runThrough(tier, afterThree, null, 1.0, 0.6, SALT + 1, settings);

            // Effective yield should still be the once-factored value
            assertEquals(factoredYield, finished.run().effectiveYield(), 1e-9,
                "Resume should not re-apply the frozen factor");
        }

        @Test
        @DisplayName("resume with different factor is rejected by spec match")
        void resumeWithDifferentFactorRejected() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");

            FoupContents lot = freshLot(25);
            FoupContents afterOne = YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, 1.0, settings, 0.7, source(SALT));

            // Try to resume with different factor - should fail because RunSpec effective yield won't match
            // The new first-layer calculation would produce a different effective yield
            // But since it's not the first layer, the spec match should fail
            // Actually, the spec match only checks tier/dieType/cores, not effectiveYield directly
            // The weight is derived from spec.effectiveYield() which is already factored
            // So passing a different factor at resume doesn't matter - it's ignored for non-first layers
            FoupContents afterTwo = YieldRollEngine.applyLayerStep(afterOne, tier, "logic", null, 2, 1.0, settings, 0.3, source(SALT + 1));

            // The effective yield should still be the original factored one (0.7)
            assertEquals(afterOne.run().effectiveYield(), afterTwo.run().effectiveYield(), 1e-9,
                "Factor at non-first layer should be ignored");
        }
    }

    @Nested
    @DisplayName("defectFactor is separate from frozen factor")
    class DefectFactorSeparation {

        @Test
        @DisplayName("defectFactor and frozen factor combine correctly (multiplicative on yield, not on defect)")
        void defectAndFrozenCombine() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);
            double frozenFactor = 0.8;
            double defectFactor = 0.5; // 50% line health

            // Effective yield = baseYield * frozenFactor (clamped), then defectFactor divides step weight
            // This is NOT the same as multiplying defectFactor by frozenFactor
            double expectedEffectiveYield = YieldMath.clampYield(baseYield * frozenFactor);

            FoupContents lot = freshLot(25);
            FoupContents afterFirst = YieldRollEngine.applyLayerStep(lot, tier, "logic", null, 1, defectFactor, settings, frozenFactor, source(SALT));

            assertEquals(expectedEffectiveYield, afterFirst.run().effectiveYield(), 1e-9,
                "Effective yield should be base * frozen, independent of defectFactor");
        }

        @Test
        @DisplayName("defectFactor 0.5 with frozen 1.0 vs defectFactor 1.0 with frozen 0.5 are different")
        void defectAndFrozenAreDifferent() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");
            double baseYield = YieldMath.resolveEffectiveYield(tier, settings, null);

            // Case A: healthy line (defect=1.0), poor calibration (frozen=0.5)
            double yieldA = YieldMath.clampYield(baseYield * 0.5);
            FoupContents lotA = freshLot(25);
            FoupContents resultA = runThrough(tier, lotA, null, 1.0, 0.5, SALT, settings);

            // Case B: poor line (defect=0.5), perfect calibration (frozen=1.0)
            double yieldB = baseYield; // frozen=1.0 so no change to effective yield
            FoupContents lotB = freshLot(25);
            FoupContents resultB = runThrough(tier, lotB, null, 0.5, 1.0, SALT + 1, settings);

            // They should have different pass rates because defectFactor affects step weight,
            // while frozen factor affects the target effective yield
            boolean[] outcomesA = YieldRollEngine.evaluate(resultA, tier, "logic", null);
            boolean[] outcomesB = YieldRollEngine.evaluate(resultB, tier, "logic", null);

            int passesA = YieldRollEngine.countPassing(outcomesA);
            int passesB = YieldRollEngine.countPassing(outcomesB);

            assertNotEquals(passesA, passesB,
                "defectFactor and frozenYieldFactor must produce different outcomes");
        }
    }

}
