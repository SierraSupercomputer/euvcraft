package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YieldRollEngine} against a seeded {@link RollSource} instead of a
 * server: the same rolls the Minecraft side draws, produced from a fixed salt and a
 * fixed lot id.
 *
 * <p>Everything here is deterministic, so a failure means the engine changed rather than
 * that the draw went differently.</p>
 */
class YieldRollEngineTest {

    private static final long SALT = 0x0BADC0DEL;
    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000009a1");
    private static final int WAFERS = 2_000;
    private static final double TOLERANCE = 0.025;

    /** The roll source the Minecraft side uses, with a fixed salt. */
    private static RollSource source(long salt) {
        return (waferIndex, stepIndex) -> new ContextualRng(salt, LOT, waferIndex, stepIndex);
    }

    /** A lot of {@code count} unprocessed wafers. */
    private static FoupContents freshLot(int count) {
        FoupContents lot = FoupContents.empty(LOT, Math.max(count, 1));
        for (int i = 0; i < count; i++) {
            lot = lot.withWafer(Wafer.unprocessed());
        }
        return lot;
    }

    @Nested
    @DisplayName("applying a layer")
    class LayerStep {

        @Test
        @DisplayName("the same salt and lot give the same rolls every time")
        void deterministicForTheSameSaltAndLot() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");

            FoupContents first = runThrough(tier, freshLot(5), null, 1.0, SALT, settings);
            FoupContents second = runThrough(tier, freshLot(5), null, 1.0, SALT, settings);

            assertEquals(first, second, "two runs of the same lot at the same salt differed");
            for (int wafer = 0; wafer < first.size(); wafer++) {
                assertEquals(first.wafers().get(wafer).cumulativeRoll(),
                    second.wafers().get(wafer).cumulativeRoll(), 0.0f, "wafer " + wafer + " moved");
            }
        }

        @Test
        @DisplayName("a different salt gives a different set of rolls")
        void differentSaltGivesDifferentRolls() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T3");

            FoupContents first = runThrough(tier, freshLot(5), null, 1.0, SALT, settings);
            FoupContents other = runThrough(tier, freshLot(5), null, 1.0, SALT + 1, settings);

            assertNotEquals(first, other, "a different salt produced the same lot");
        }

        @Test
        @DisplayName("every wafer ends the layer at the layer number and below its old roll")
        void layerIndexAdvancesAndRollsOnlyFall() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T1");

            FoupContents before = freshLot(8);
            FoupContents after = YieldRollEngine.applyLayerStep(before, tier, null, 1, 1.0, settings, source(SALT));

            assertEquals(before.lotLayer(), 0, "the starting lot should be unprocessed");
            assertEquals(after.lotLayer(), 1, "the lot did not move to layer 1");
            assertEquals(after.lotId(), before.lotId(), "the lot id changed");
            assertEquals(after.capacity(), before.capacity(), "the capacity changed");
            assertEquals(after.size(), before.size(), "the wafer count changed");

            for (int wafer = 0; wafer < after.size(); wafer++) {
                Wafer moved = after.wafers().get(wafer);
                assertEquals(1, moved.layerIndex(), "wafer " + wafer + " is not at layer 1");
                assertEquals(Wafer.NO_FLAGS, moved.flags(), "wafer " + wafer + " lost its flags");
                assertTrue(moved.cumulativeRoll() < Wafer.NO_ROLLS,
                    "wafer " + wafer + " still holds " + moved.cumulativeRoll() + " after a layer");
                assertTrue(moved.cumulativeRoll() > 0.0f, "wafer " + wafer + " fell to zero");
            }

            assertEquals(0, before.wafers().get(0).layerIndex(),
                "the lot that was passed in was modified in place");
            assertEquals(Wafer.NO_ROLLS, before.wafers().get(0).cumulativeRoll(),
                "the lot that was passed in was modified in place");
        }

        @Test
        @DisplayName("wafers in one lot do not share a stream")
        void wafersAreIndependent() {
            FabSettings settings = FabSettings.defaults();
            Tier tier = ReferenceTiers.byId("T4");

            FoupContents after = runThrough(tier, freshLot(25), null, 1.0, SALT, settings);

            List<Float> seen = new ArrayList<>();
            for (Wafer wafer : after.wafers()) {
                assertTrue(!seen.contains(wafer.cumulativeRoll()),
                    "two wafers in one lot share the cumulative roll " + wafer.cumulativeRoll());
                seen.add(wafer.cumulativeRoll());
            }
        }

        @Test
        @DisplayName("each roll is drawn at the step index of its layer and position")
        void stepIndexFollowsTheFormula() {
            FabSettings settings = FabSettings.builder().rollsPerLayer(3).build();
            Tier tier = ReferenceTiers.byId("T1");
            List<int[]> asked = new ArrayList<>();

            YieldRollEngine.applyLayerStep(freshLot(2), tier, null, 1, 1.0, settings,
                (waferIndex, stepIndex) -> {
                    asked.add(new int[] {waferIndex, stepIndex});
                    return new ContextualRng(SALT, LOT, waferIndex, stepIndex);
                });

            assertEquals(6, asked.size(), "2 wafers over 3 rolls should have drawn 6 rolls");
            int next = 0;
            for (int wafer = 0; wafer < 2; wafer++) {
                for (int roll = 0; roll < 3; roll++) {
                    assertArrayEquals(new int[] {wafer, 1 * 3 + roll}, asked.get(next++),
                        "roll " + roll + " of wafer " + wafer + " was drawn at the wrong context");
                }
            }
        }

        @Test
        @DisplayName("the step index formula rejects a roll outside its layer")
        void stepIndexFormulaRejectsBadRolls() {
            assertEquals(6, YieldRollEngine.stepIndex(2, 3, 0));
            assertEquals(8, YieldRollEngine.stepIndex(2, 3, 2));
            assertThrows(IllegalArgumentException.class, () -> YieldRollEngine.stepIndex(2, 3, 3));
            assertThrows(IllegalArgumentException.class, () -> YieldRollEngine.stepIndex(2, 3, -1));
        }

        @Test
        @DisplayName("more rolls per layer draw more rolls")
        void moreRollsPerLayerDrawMoreRolls() {
            FabSettings oneRoll = FabSettings.defaults();
            FabSettings fourRolls = FabSettings.builder().rollsPerLayer(4).build();
            Tier tier = ReferenceTiers.byId("T3");

            assertEquals(24, YieldRollEngine.rollCount(tier, oneRoll));
            assertEquals(96, YieldRollEngine.rollCount(tier, fourRolls));

            FoupContents lot = freshLot(100);
            List<int[]> asked = new ArrayList<>();
            YieldRollEngine.applyLayerStep(lot, tier, null, 1, 1.0, fourRolls, (waferIndex, stepIndex) -> {
                asked.add(new int[] {waferIndex, stepIndex});
                return new ContextualRng(SALT, LOT, waferIndex, stepIndex);
            });

            assertEquals(400, asked.size(), "100 wafers over 4 rolls should have drawn 400 rolls");
        }

        @Test
        @DisplayName("a lot run through every layer passes at the tier's yield")
        void aFullRunPassesAtTheTierYield() {
            for (String id : new String[] {"T1", "T3", "T5"}) {
                Tier tier = ReferenceTiers.byId(id);
                FabSettings settings = FabSettings.defaults();
                double yield = YieldMath.resolveEffectiveYield(tier, settings, null);

                int passing = 0;
                int simulated = 0;
                for (int lot = 0; lot < WAFERS / 25; lot++) {
                    FoupContents processed = runThrough(tier, freshLot(25), null, 1.0, SALT + lot, settings);
                    boolean[] outcomes = YieldRollEngine.evaluate(processed, tier, null);
                    passing += YieldRollEngine.countPassing(outcomes);
                    simulated += outcomes.length;
                }

                double rate = (double) passing / simulated;
                assertTrue(Math.abs(rate - yield) <= TOLERANCE,
                    id + " simulated " + (rate * 100.0) + "% against a target of " + (yield * 100.0) + "%");
            }
        }
    }

    @Nested
    @DisplayName("rejecting a step")
    class Rejections {

        private final FabSettings settings = FabSettings.defaults();

        @Test
        @DisplayName("a layer that is not the next one is refused")
        void outOfOrderLayerIsRefused() {
            Tier tier = ReferenceTiers.byId("T3");

            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(freshLot(4), tier, null, 2, 1.0, settings, source(SALT)),
                "skipping to layer 2 should be refused");
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(freshLot(4), tier, null, 5, 1.0, settings, source(SALT)),
                "jumping to layer 5 should be refused");

            FoupContents atOne = YieldRollEngine.applyLayerStep(freshLot(4), tier, null, 1, 1.0, settings, source(SALT));
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(atOne, tier, null, 1, 1.0, settings, source(SALT)),
                "doing layer 1 twice should be refused");
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(atOne, tier, null, 3, 1.0, settings, source(SALT)),
                "doing layer 3 after layer 1 should be refused");
        }

        @Test
        @DisplayName("a layer past the tier's last one is refused")
        void pastTheLastLayerIsRefused() {
            Tier tier = ReferenceTiers.byId("T1");
            FoupContents finished = runThrough(tier, freshLot(2), null, 1.0, SALT, settings);

            assertEquals(tier.layers(), finished.lotLayer());
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(finished, tier, null, tier.layers() + 1, 1.0, settings, source(SALT)));
            assertTrue(thrown.getMessage().contains("past"), "message did not explain why: " + thrown.getMessage());
        }

        @Test
        @DisplayName("a longer tier refuses a lot started at a different tier")
        void tiersHaveDifferentLengths() {
            FoupContents lot = runThrough(ReferenceTiers.byId("T1"), freshLot(2), null, 1.0, SALT, settings);
            assertEquals(1, lot.lotLayer());

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(lot, ReferenceTiers.byId("T3"), null, lot.lotLayer() + 1, 1.0,
                    settings, source(SALT)));
            assertTrue(thrown.getMessage().contains("differ from the run spec"),
                "message did not explain why: " + thrown.getMessage());
        }

        @Test
        @DisplayName("an empty lot cannot take a step")
        void emptyLotIsRefused() {
            Tier tier = ReferenceTiers.byId("T1");
            assertThrows(IllegalArgumentException.class,
                () -> YieldRollEngine.applyLayerStep(FoupContents.empty(LOT, 25), tier, null, 1, 1.0, settings, source(SALT)));
        }

        @Test
        @DisplayName("nulls are refused")
        void nullsAreRefused() {
            Tier tier = ReferenceTiers.byId("T1");
            FoupContents lot = freshLot(2);
            assertThrows(NullPointerException.class,
                () -> YieldRollEngine.applyLayerStep(null, tier, null, 1, 1.0, settings, source(SALT)));
            assertThrows(NullPointerException.class,
                () -> YieldRollEngine.applyLayerStep(lot, null, null, 1, 1.0, settings, source(SALT)));
            assertThrows(NullPointerException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, null, 1, 1.0, null, source(SALT)));
            assertThrows(NullPointerException.class,
                () -> YieldRollEngine.applyLayerStep(lot, tier, null, 1, 1.0, settings, null));
            assertThrows(NullPointerException.class, () -> YieldRollEngine.rollCount(tier, null));
            assertThrows(NullPointerException.class, () -> YieldRollEngine.evaluate(null, tier, null));
            assertThrows(NullPointerException.class, () -> YieldRollEngine.countPassing(null));
            assertThrows(NullPointerException.class, () -> YieldRollEngine.requireCompleteLot(lot, null));
        }
    }

    @Nested
    @DisplayName("judging a lot")
    class Judging {

        @Test
        @DisplayName("only a lot at the tier's last layer may be judged")
        void incompleteLotIsRefused() {
            Tier tier = ReferenceTiers.byId("T3");
            FoupContents halfWay = runThrough(tier, freshLot(3), null, 1.0, SALT, FabSettings.defaults(), 12);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> YieldRollEngine.requireCompleteLot(halfWay, tier));
            assertTrue(thrown.getMessage().contains("layer"), "message did not explain why: " + thrown.getMessage());

            FoupContents finished = runThrough(tier, halfWay, null, 1.0, SALT, FabSettings.defaults());
            YieldRollEngine.requireCompleteLot(finished, tier);

            boolean[] outcomes = YieldRollEngine.evaluate(finished, tier, null);
            for (int wafer = 0; wafer < outcomes.length; wafer++) {
                boolean expected = YieldMath.passes(finished.wafers().get(wafer).cumulativeRoll(), 0.90);
                assertEquals(expected, outcomes[wafer], "wafer " + wafer + " was answered in the wrong slot");
            }
        }

        @Test
        @DisplayName("one answer per wafer, in slot order")
        void oneAnswerPerWafer() {
            Tier tier = ReferenceTiers.byId("T2");
            FoupContents finished = runThrough(tier, freshLot(9), null, 1.0, SALT, FabSettings.defaults());
            double yield = 0.80;

            boolean[] outcomes = YieldRollEngine.evaluate(finished, tier, null);

            assertEquals(finished.size(), outcomes.length);
            for (int wafer = 0; wafer < outcomes.length; wafer++) {
                boolean expected = YieldMath.passes(finished.wafers().get(wafer).cumulativeRoll(), yield);
                assertEquals(expected, outcomes[wafer], "wafer " + wafer + " was answered in the wrong slot");
            }
        }

        @Test
        @DisplayName("judging a lot does not change it")
        void judgingDoesNotChangeTheLot() {
            Tier tier = ReferenceTiers.byId("T2");
            FoupContents finished = runThrough(tier, freshLot(9), null, 1.0, SALT, FabSettings.defaults());
            FoupContents copy = new FoupContents(finished.lotId(), finished.capacity(), finished.wafers(), finished.run());

            YieldRollEngine.evaluate(finished, tier, null);

            assertEquals(copy, finished, "evaluating a lot changed it");
        }
    }

    /** Runs a lot through every layer of a tier at one salt. */
    private static FoupContents runThrough(Tier tier, FoupContents lot, Integer cores, double defectFactor,
            long salt, FabSettings settings) {
        return runThrough(tier, lot, cores, defectFactor, salt, settings, tier.layers());
    }

    /** Runs a lot up to {@code upToLayer} of a tier at one salt. */
    private static FoupContents runThrough(Tier tier, FoupContents lot, Integer cores, double defectFactor,
            long salt, FabSettings settings, int upToLayer) {
        RollSource rolls = source(salt);
        FoupContents processed = lot;
        for (int layer = processed.lotLayer() + 1; layer <= upToLayer; layer++) {
            processed = YieldRollEngine.applyLayerStep(processed, tier, cores, layer, defectFactor, settings, rolls);
        }
        return processed;
    }
}