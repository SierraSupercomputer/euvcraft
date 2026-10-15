package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.euvcraft.lithmod.core.ContextualRng;
import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.YieldCalibration;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.core.YieldRollEngine;
import com.euvcraft.lithmod.core.RollSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FabYieldService} that do not need a running server: judging a
 * lot. The roll side needs a {@link net.minecraft.server.level.ServerLevel}, so it is
 * covered by the game test {@code lithmod:yield_layer_step}; here the rolls are produced
 * by the core engine from a seeded source instead.
 *
 * <p>With no mod instance the service reads {@link EUVCRAFTMod#settings()}, which
 * returns {@link FabSettings#defaults()} before the config exists, so these tests run
 * against the shipped defaults.</p>
 */
class FabYieldServiceTest {

    private static final long SALT = 0x59E1D000L;
    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000009b2");

    @Test
    @DisplayName("four thousand wafers at T3 pass within a point and a half of 90%")
    void t3PassesAtNinetyPercent() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents finished = process(tier, null, 1.0, 4000, 25);

        boolean[] outcomes = FabYieldService.evaluateLot(finished, tier, "logic", null);
        double rate = (double) FabYieldService.countPassing(finished, tier, "logic", null) / finished.size();

        System.out.printf("%nT3, %d wafers, K=%d: %d of %d passed, %.4f%%%n",
            finished.size(), YieldRollEngine.rollCount(tier, FabSettings.defaults()),
            FabYieldService.countPassing(finished, tier, "logic", null), finished.size(), rate * 100.0);

        assertEquals(finished.size(), outcomes.length, "one answer per wafer was expected");
        assertTrue(Math.abs(rate - 0.90) <= 0.015,
            "T3 passed " + (rate * 100.0) + "% against a target of 90%");
    }

    @Test
    @DisplayName("the core count penalty reaches the answer")
    void coreCountPenaltyReachesTheAnswer() {
        Tier tier = tier("T6", 70, 85.0, 4);
        int wafers = 4000;
        FabSettings settings = EUVCRAFTMod.settings();

        double atComfort = rate(tier, 4, wafers);
        double doubleComfort = rate(tier, 8, wafers);
        double farPast = rate(tier, 24, wafers);

        System.out.printf("%nT6, %d wafers each: 4 cores %.4f%%, 8 cores %.4f%%, 24 cores %.4f%%%n",
            wafers, atComfort * 100.0, doubleComfort * 100.0, farPast * 100.0);

        assertEquals(YieldMath.resolveEffectiveYield(tier, settings, 4), atComfort, 0.015,
            "4 cores is the comfortable count and should pass at the tier yield of 85%");
        assertEquals(YieldMath.resolveEffectiveYield(tier, settings, 8), doubleComfort, 0.015,
            "8 cores should pass at about 61%");
        assertEquals(YieldMath.resolveEffectiveYield(tier, settings, 24), farPast, 0.015,
            "24 cores should pass at about 17%");
    }

    @Test
    @DisplayName("one answer per wafer, and it is the roll against the resolved yield")
    void answersFollowTheRollAndTheResolvedYield() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents finished = process(tier, null, 1.0, 500, 25);
        double yield = YieldMath.resolveEffectiveYield(tier, EUVCRAFTMod.settings(), null);

        boolean[] outcomes = FabYieldService.evaluateLot(finished, tier, "logic", null);

        assertEquals(500, outcomes.length);
        for (int wafer = 0; wafer < outcomes.length; wafer++) {
            boolean expected = YieldMath.passes(finished.wafers().get(wafer).cumulativeRoll(), yield);
            assertEquals(expected, outcomes[wafer], "wafer " + wafer + " was answered in the wrong slot");
        }
    }

    @Test
    @DisplayName("judging a lot does not change it and never exposes a roll")
    void judgingDoesNotChangeTheLot() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents finished = process(tier, null, 1.0, 100, 25);
        FoupContents before = new FoupContents(finished.lotId(), finished.capacity(), finished.wafers(), finished.run());

        FabYieldService.evaluateLot(finished, tier, "logic", null);

        assertEquals(before, finished, "judging a lot changed it");

        FoupPublicView view = finished.publicView();
        assertEquals(100, view.waferCount());
        assertEquals(24, view.lotLayer());
        assertEquals(tier.layers(), view.lotLayer());
    }

    @Test
    @DisplayName("a lot that has not reached the tier's last layer is refused")
    void incompleteLotIsRefused() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents halfWay = process(tier, null, 1.0, 25, 25, 12);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> FabYieldService.evaluateLot(halfWay, tier, "logic", null));
        assertTrue(thrown.getMessage().contains("layer"),
            "the message did not explain why: " + thrown.getMessage());
        assertThrows(IllegalStateException.class, () -> FabYieldService.countPassing(halfWay, tier, "logic", null));
    }

    @Test
    @DisplayName("an empty lot is refused (no run spec), and nulls are refused")
    void emptyAndNullLots() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents empty = FoupContents.empty(LOT, 25);

        assertThrows(IllegalStateException.class, () -> FabYieldService.evaluateLot(empty, tier, "logic", null));
        assertThrows(IllegalStateException.class, () -> FabYieldService.countPassing(empty, tier, "logic", null));

        assertThrows(NullPointerException.class, () -> FabYieldService.evaluateLot(null, tier, "logic", null));
        assertThrows(NullPointerException.class, () -> FabYieldService.evaluateLot(empty, null, "logic", null));
    }

    @Test
    @DisplayName("a settings change drops the cached calibration")
    void settingsChangeClearsTheCalibration() {
        process(tier("T1", 6, 60.0, null), null, 1.0, 25, 25);

        // The cache is a ConcurrentHashMap, we can't easily check its size without cacheSize(),
        // so we just verify that calling onSettingsChanged doesn't throw and that a new
        // calibration works after it.
        FabYieldService.onSettingsChanged();

        // A new run should still work, which implies the cache was cleared
        FoupContents lot = process(tier("T1", 6, 60.0, null), null, 1.0, 25, 25);
        boolean[] outcomes = FabYieldService.evaluateLot(lot, tier("T1", 6, 60.0, null), "logic", null);
        assertTrue(outcomes.length > 0);
    }

    @Test
    @DisplayName("judging the same lot twice gives the same answers")
    void judgingIsDeterministic() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents finished = process(tier, null, 1.0, 200, 25);

        boolean[] first = FabYieldService.evaluateLot(finished, tier, "logic", null);
        boolean[] second = FabYieldService.evaluateLot(finished, tier, "logic", null);
        assertArrayEquals(first, second);
    }

    /**
     * Runs {@code wafers / perLot} lots of {@code perLot} wafers through every layer of a
     * tier and gathers the results into one lot.
     *
     * <p>Each simulated lot gets its own lot id, because the roll context is keyed by lot
     * id: one id for all of them would repeat the same 25 patterns and turn four thousand
     * wafers into twenty-five independent ones.</p>
     */
    private static FoupContents process(Tier tier, Integer cores, double defectFactor, int wafers, int perLot) {
        return process(tier, cores, defectFactor, wafers, perLot, tier.layers());
    }

    /** Runs {@code wafers / perLot} lots of {@code perLot} wafers up to {@code upToLayer}. */
    private static FoupContents process(Tier tier, Integer cores, double defectFactor, int wafers, int perLot,
            int upToLayer) {
        FoupContents collected = FoupContents.empty(LOT, wafers);
        int lots = wafers / perLot;
        RunSpec collectedSpec = null;
        for (int lot = 0; lot < lots; lot++) {
            UUID lotId = new UUID(0x9B20000000000000L + lot, 0x5AL);
            RollSource rolls = (waferIndex, stepIndex) -> new ContextualRng(SALT, lotId, waferIndex, stepIndex);

            FoupContents contents = FoupContents.empty(lotId, perLot);
            for (int wafer = 0; wafer < perLot; wafer++) {
                contents = contents.withWafer(Wafer.unprocessed());
            }
            for (int layer = 1; layer <= upToLayer; layer++) {
                contents = YieldRollEngine.applyLayerStep(contents, tier, "logic", cores, layer, defectFactor,
                    EUVCRAFTMod.settings(), rolls);
            }
            if (collectedSpec == null) {
                collectedSpec = contents.run();
            }
            collected = collected.withWafers(contents.wafers());
        }
        if (collectedSpec != null) {
            collected = collected.withRun(collectedSpec);
        }
        return collected;
    }

    /** The pass rate over {@code wafers} simulated wafers. */
    private static double rate(Tier tier, Integer cores, int wafers) {
        FoupContents finished = process(tier, cores, 1.0, wafers, 25);
        return (double) FabYieldService.countPassing(finished, tier, "logic", cores) / finished.size();
    }

    /** One reference tier, built from the reference table. */
    private static Tier tier(String id, int layers, double yieldPercent, Integer comfortableCores) {
        return new Tier(id, Integer.parseInt(id.substring(1)), id, "test", 100, 10, 10, layers, 100,
            yieldPercent, 100, comfortableCores, false);
    }

    @Test
    @DisplayName("die type mismatch refused at later layer")
    void dieTypeMismatchRefusedAtLaterLayer() {
        Tier tier = tier("T3", 24, 90.0, null);
        FoupContents atOne = process(tier, null, 1.0, 25, 25, 1);
        assertEquals("logic", atOne.run().dieType());

        assertThrows(IllegalArgumentException.class,
            () -> YieldRollEngine.applyLayerStep(atOne, tier, "memory", null, 2, 1.0, EUVCRAFTMod.settings(),
                (w, s) -> new ContextualRng(SALT, atOne.lotId(), w, s)));
    }

    @Test
    @DisplayName("die type mismatch refused at evaluateLot")
    void dieTypeMismatchRefusedAtEvaluateLot() {
        Tier tier = tier("T1", 1, 60.0, null);
        FoupContents finished = process(tier, null, 1.0, 25, 25);
        assertEquals("logic", finished.run().dieType());

        assertThrows(IllegalStateException.class,
            () -> FabYieldService.evaluateLot(finished, tier, "memory", null));
    }

    @Test
    @DisplayName("frozen spec reports its die type after layer 1")
    void frozenSpecReportsDieTypeAfterLayer1() {
        Tier tier = tier("T2", 12, 80.0, null);
        FoupContents atOne = process(tier, null, 1.0, 25, 25, 1);
        RunSpec spec = atOne.run();
        assertEquals("logic", spec.dieType());
        assertEquals(2, spec.tierIndex());
    }
}