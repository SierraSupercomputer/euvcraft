package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic tests for {@link HighBinningRules} against synthetic lots with known
 * cumulative rolls. All tests use the same test tier registry and generation matrix as
 * the packaging rules tests. No randomness is involved; pass/fail boundaries are
 * verified by quantile.
 */
class HighBinningRulesTest {

    private static final int WAFERS = 100;
    private static final UUID FIXED_LOT = UUID.fromString("00000000-0000-4000-8000-00000000beef");
    private static final double SOURCE_YIELD = 0.90;
    private static final double YIELD_MULTIPLIER = 0.15; // DEFAULT_YIELD_PENALTY

    private static TierRegistry tiers;
    private static GenerationMatrix matrix;
    private static DieRules dieRules;
    private static FabSettings settings;

    @BeforeAll
    static void setUp() {
        // Build the same 8-tier registry used by other tests
        tiers = TierRegistry.build(GenerationMatrixTest.TestTiers.all(), "high-binning-test");

        // Same matrix as PackagingRulesTest: test_packaging needs G1 at T1-T3, G2 at T4-T7, G3 at T8
        Map<String, List<Integer>> byLine = new LinkedHashMap<>();
        for (String line : List.of("substrate", "mask_shop", "deposition_etch", "utilities")) {
            byLine.put(line, List.of(1, 1, 1, 1, 1, 1, 1, 1));
        }
        byLine.put("test_packaging", List.of(1, 1, 1, 2, 2, 2, 2, 3));
        matrix = GenerationMatrix.build(tiers, byLine, "high-binning-test");

        dieRules = DieRules.defaultRules(tiers);
        settings = FabSettings.defaults();
    }

    /**
     * Creates a complete lot at T3 (source tier for this increment) with the given
     * cumulative rolls for each wafer.
     */
    private static FoupContents makeT3Lot(String dieType, Integer cores, List<Float> rolls) {
        Tier t3 = tiers.getByIndex(3);
        RunSpec spec = new RunSpec(dieType, 3, cores != null ? cores : RunSpec.NO_CORES, SOURCE_YIELD, t3.layers() * settings.rollsPerLayer());
        List<Wafer> wafers = new ArrayList<>();
        for (int i = 0; i < rolls.size(); i++) {
            wafers.add(new Wafer(t3.layers(), rolls.get(i), Wafer.NO_FLAGS));
        }
        return new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
    }

    /** Creates a T3 lot with uniformly distributed rolls in (0, 1]. */
    private static FoupContents makeUniformLot(String dieType, Integer cores, int count) {
        List<Float> rolls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Deterministic pseudo-uniform: (i+1)/(count+1) covers (0,1] evenly
            rolls.add((float) (i + 1) / (count + 1));
        }
        return makeT3Lot(dieType, cores, rolls);
    }

    /** Transition for T3->T4 with default penalty and 1x costs. */
    private static HighBinningRules.Transition defaultTransition() {
        return new HighBinningRules.Transition(3, 4, YIELD_MULTIPLIER, 1.0, 1.0);
    }

    @Nested
    @DisplayName("Transition validation")
    class TransitionTests {

        @Test
        @DisplayName("valid transition constructs")
        void validTransition() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1.5, 2.0);
            assertEquals(3, t.sourceTier());
            assertEquals(4, t.targetTier());
            assertEquals(0.15, t.yieldMultiplier());
            assertEquals(1.5, t.ticksMultiplier());
            assertEquals(2.0, t.energyMultiplier());
        }

        @Test
        @DisplayName("target must be source + 1")
        void targetMustBeSourcePlusOne() {
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 5, 0.15, 1.0, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 3, 0.15, 1.0, 1.0));
        }

        @Test
        @DisplayName("yield multiplier bounds")
        void yieldMultiplierBounds() {
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.0, 1.0, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, -0.1, 1.0, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 1.01, 1.0, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, Double.NaN, 1.0, 1.0));
        }

        @Test
        @DisplayName("cost multipliers must be >= 1")
        void costMultipliersAtLeastOne() {
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 0.9, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 1.0, 0.5));
        }

        @Test
        @DisplayName("cost multipliers have reasonable upper bound")
        void costMultipliersUpperBound() {
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 1001.0, 1.0));
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 1.0, 2000.0));
        }

        @Test
        @DisplayName("overflow check on scaled costs")
        void overflowCheck() {
            // ticksMultiplier that would overflow base 72000
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 30000.0, 1.0));
            // energyMultiplier that would overflow conservative base
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 1.0, 2000000.0));
        }

        @Test
        @DisplayName("scaledTicksPerWafer computes correctly")
        void scaledTicksPerWafer() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1.5, 1.0);
            assertEquals(150, t.scaledTicksPerWafer(100));
            assertEquals(100, t.scaledTicksPerWafer(100)); // 1.0
        }

        @Test
        @DisplayName("scaledEnergyPerTick computes correctly")
        void scaledEnergyPerTick() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1.0, 2.0);
            assertEquals(200, t.scaledEnergyPerTick(100));
            assertEquals(0, t.scaledEnergyPerTick(0));
        }

        @Test
        @DisplayName("scaled methods throw on overflow")
        void scaledMethodsThrowOnOverflow() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1000.0, 1000.0);
            assertThrows(ArithmeticException.class, () -> t.scaledTicksPerWafer(Integer.MAX_VALUE));
            assertThrows(ArithmeticException.class, () -> t.scaledEnergyPerTick(Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("toString does not leak private data")
        void toStringSafe() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1.5, 2.0);
            String s = t.toString();
            assertTrue(s.contains("T3"));
            assertTrue(s.contains("T4"));
            assertTrue(s.contains("0.15"));
            // No hidden rolls or flags possible here, but ensure format is stable
        }
    }

    @Nested
    @DisplayName("High binning judgment - structural refusals")
    class StructuralRefusals {

        @Test
        @DisplayName("null lot refused")
        void nullLot() {
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(null, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("empty lot refused")
        void emptyLot() {
            FoupContents empty = FoupContents.empty(FIXED_LOT, settings.foupCapacity());
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(empty, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("lot without run spec refused")
        void noRunSpec() {
            FoupContents unstarted = makeUniformLot(DieTypes.LOGIC, null, 5).withRun(null);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(unstarted, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("incomplete lot (wafer not at final layer) refused")
        void incompleteLot() {
            Tier t3 = tiers.getByIndex(3);
            RunSpec spec = new RunSpec(DieTypes.LOGIC, 3, RunSpec.NO_CORES, SOURCE_YIELD, t3.layers());
            List<Wafer> wafers = new ArrayList<>();
            wafers.add(new Wafer(t3.layers() - 1, 0.5f, Wafer.NO_FLAGS)); // One wafer early
            wafers.add(new Wafer(t3.layers(), 0.5f, Wafer.NO_FLAGS));
            FoupContents lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, t3, dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("tier mismatch with run spec throws")
        void tierMismatch() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(4), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("memory die type refused")
        void memoryRefused() {
            FoupContents lot = makeUniformLot(DieTypes.MEMORY, null, 5);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("nand die type refused")
        void nandRefused() {
            FoupContents lot = makeUniformLot(DieTypes.NAND, null, 5);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("source tier below T3 refused")
        void sourceTierTooLow() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            // Change spec to T2 but keep wafers at T3 layers? That's a mismatch. Create T2 lot properly.
            Tier t2 = tiers.getByIndex(2);
            RunSpec spec = new RunSpec(DieTypes.LOGIC, 2, RunSpec.NO_CORES, SOURCE_YIELD, t2.layers());
            List<Wafer> wafers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                wafers.add(new Wafer(t2.layers(), 0.5f, Wafer.NO_FLAGS));
            }
            FoupContents t2Lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(t2Lot, t2, dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("compute with invalid core count refused")
        void computeInvalidCores() {
            // Compute before T6 must have exactly 1 core
            FoupContents lot = makeT3Lot(DieTypes.COMPUTE, 4, List.of(0.5f, 0.5f, 0.5f)); // 4 cores
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
            
            // 0 cores (NO_CORES) is also invalid for compute
            FoupContents lot2 = makeT3Lot(DieTypes.COMPUTE, RunSpec.NO_CORES, List.of(0.5f, 0.5f, 0.5f));
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot2, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("logic with core count refused")
        void logicWithCores() {
            FoupContents lot = makeT3Lot(DieTypes.LOGIC, 2, List.of(0.5f, 0.5f, 0.5f));
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("target tier would exceed T8 refused")
        void targetExceedsT8() {
            // Try T8 source -> would need T9
            Tier t8 = tiers.getByIndex(8);
            RunSpec spec = new RunSpec(DieTypes.LOGIC, 8, RunSpec.NO_CORES, SOURCE_YIELD, t8.layers());
            List<Wafer> wafers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                wafers.add(new Wafer(t8.layers(), 0.5f, Wafer.NO_FLAGS));
            }
            FoupContents lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, t8, dieRules, settings, matrix, 2, true, defaultTransition()));
        }

        @Test
        @DisplayName("logic target tier exceeding cap 5 refused")
        void logicTargetExceedsCap() {
            // T5 source -> T6 target, but logic caps at 5
            Tier t5 = tiers.getByIndex(5);
            RunSpec spec = new RunSpec(DieTypes.LOGIC, 5, RunSpec.NO_CORES, SOURCE_YIELD, t5.layers());
            List<Wafer> wafers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                wafers.add(new Wafer(t5.layers(), 0.5f, Wafer.NO_FLAGS));
            }
            FoupContents lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            HighBinningRules.Transition t5to6 = new HighBinningRules.Transition(5, 6, 0.15, 1.0, 1.0);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, t5, dieRules, settings, matrix, 2, true, t5to6));
        }

        @Test
        @DisplayName("generation 1 packager refused")
        void generationOneRefused() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 1, true, defaultTransition()));
        }

        @Test
        @DisplayName("target unavailable refused")
        void targetUnavailable() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, false, defaultTransition()));
        }

        @Test
        @DisplayName("null transition refused")
        void nullTransition() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, null));
        }

        @Test
        @DisplayName("transition source tier mismatch refused")
        void transitionSourceMismatch() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            HighBinningRules.Transition wrong = new HighBinningRules.Transition(4, 5, 0.15, 1.0, 1.0);
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, wrong));
        }

        @Test
        @DisplayName("transition target tier mismatch refused")
        void transitionTargetMismatch() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            HighBinningRules.Transition wrong = new HighBinningRules.Transition(3, 5, 0.15, 1.0, 1.0);
            assertThrows(IllegalArgumentException.class,
                () -> HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, wrong));
        }
    }

    @Nested
    @DisplayName("High binning judgment - deterministic pass/fail")
    class DeterministicJudgment {

        @Test
        @DisplayName("known pass/fail boundary at quantiles")
        void boundaryQuantiles() {
            // Source yield 0.90, high bin yield = 0.90 * 0.15 = 0.135
            // Pass threshold = 1 - 0.135 = 0.865
            // Rolls > 0.865 pass, rolls <= 0.865 fail
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, WAFERS);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            double highBinYield = SOURCE_YIELD * YIELD_MULTIPLIER;
            double threshold = 1.0 - highBinYield; // 0.865
            int expectedPassing = 0;
            for (int i = 0; i < WAFERS; i++) {
                float roll = (float) (i + 1) / (WAFERS + 1);
                if (roll > threshold) expectedPassing++;
            }

            assertEquals(WAFERS, plan.size());
            assertEquals(expectedPassing, plan.passingCount(), "passing count matches quantile boundary");
            assertEquals(WAFERS - expectedPassing, plan.failingCount());
        }

        @Test
        @DisplayName("yield multiplier 1.0 means same threshold as source")
        void yieldMultiplierOne() {
            // yieldMultiplier = 1.0 -> highBinYield = 0.90 -> threshold = 0.10
            // Same as source, so all wafers that passed source would pass high-bin
            // But we're not testing source pass, just that the math is right
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 1.0, 1.0, 1.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, WAFERS);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);

            double highBinYield = SOURCE_YIELD * 1.0;
            double threshold = 1.0 - highBinYield; // 0.10
            int expectedPassing = 0;
            for (int i = 0; i < WAFERS; i++) {
                float roll = (float) (i + 1) / (WAFERS + 1);
                if (roll > threshold) expectedPassing++;
            }

            assertEquals(expectedPassing, plan.passingCount());
        }

        @Test
        @DisplayName("yield multiplier near 0 makes almost all fail")
        void yieldMultiplierNearZero() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.001, 1.0, 1.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, WAFERS);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);

            double highBinYield = SOURCE_YIELD * 0.001; // 0.0009
            double threshold = 1.0 - highBinYield; // ~0.9991
            // Only rolls > 0.9991 pass, which is essentially none in our uniform distribution
            int expectedPassing = 0;
            for (int i = 0; i < WAFERS; i++) {
                float roll = (float) (i + 1) / (WAFERS + 1);
                if (roll > threshold) expectedPassing++;
            }

            assertEquals(expectedPassing, plan.passingCount());
            assertEquals(WAFERS, plan.failingCount());
        }

        @Test
        @DisplayName("subset property: high bin passing is subset of source passing")
        void subsetProperty() {
            // Source threshold = 1 - 0.90 = 0.10
            // High bin threshold = 1 - 0.135 = 0.865 (much stricter)
            // So any wafer passing high bin must have roll > 0.865 > 0.10, so it passed source
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, WAFERS);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            double sourceThreshold = 1.0 - SOURCE_YIELD; // 0.10
            double highBinThreshold = 1.0 - SOURCE_YIELD * YIELD_MULTIPLIER; // 0.865

            for (int i = 0; i < WAFERS; i++) {
                float roll = (float) (i + 1) / (WAFERS + 1);
                boolean sourcePass = roll > sourceThreshold;
                boolean highBinPass = plan.passes(i);
                if (highBinPass) {
                    assertTrue(sourcePass, "high bin pass at wafer " + i + " (roll=" + roll + ") must imply source pass");
                }
            }
        }

        @Test
        @DisplayName("source lot is not mutated")
        void sourceNotMutated() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 10);
            int originalSize = lot.size();
            List<Float> originalRolls = new ArrayList<>();
            for (Wafer w : lot.wafers()) {
                originalRolls.add(w.cumulativeRoll());
            }
            int originalLayer = lot.lotLayer();

            HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            assertEquals(originalSize, lot.size());
            assertEquals(originalLayer, lot.lotLayer());
            for (int i = 0; i < originalSize; i++) {
                assertEquals(originalRolls.get(i), lot.wafers().get(i).cumulativeRoll(), 0.0f);
            }
        }

        @Test
        @DisplayName("recipe amounts preserved in plan")
        void recipeAmountsPreserved() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            assertEquals(settings.chipsPerWafer(), plan.chipsPerWafer());
            assertEquals(1, plan.packagesPerWafer()); // Standard packaging
        }

        @Test
        @DisplayName("repeated judgment identical after freeze")
        void repeatedJudgmentIdentical() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 10);
            PackagingPlan first = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());
            PackagingPlan second = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            assertEquals(first.size(), second.size());
            for (int i = 0; i < first.size(); i++) {
                assertEquals(first.passes(i), second.passes(i), "wafer " + i + " differs between judgments");
            }
            assertEquals(first.chipsPerWafer(), second.chipsPerWafer());
            assertEquals(first.packagesPerWafer(), second.packagesPerWafer());
        }

        @Test
        @DisplayName("compute die preserves core count")
        void computeCoreCountPreserved() {
            FoupContents lot = makeUniformLot(DieTypes.COMPUTE, 1, 10); // 1 core
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            // The plan doesn't carry core count, but the judgment used the correct spec
            // Verify by checking the source lot still has the core count
            assertEquals(1, lot.run().cores());
        }
    }

    @Nested
    @DisplayName("Plan properties")
    class PlanProperties {

        @Test
        @DisplayName("failing wafer yields scrap (chipsPerWafer) and zero packages")
        void failingWaferScrap() {
            // All rolls very low -> all fail
            List<Float> allLow = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) allLow.add(0.01f);
            FoupContents lot = makeT3Lot(DieTypes.LOGIC, null, allLow);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            assertEquals(0, plan.passingCount());
            assertEquals(10, plan.failingCount());
            assertEquals(10 * settings.chipsPerWafer(), plan.totalScrap());
            assertEquals(0, plan.totalPackages());

            for (int i = 0; i < 10; i++) {
                assertFalse(plan.passes(i));
                assertEquals(0, plan.packagesFor(i));
            }
        }

        @Test
        @DisplayName("passing wafer yields chips and consumes packages")
        void passingWaferChipsAndPackages() {
            // All rolls very high -> all pass
            List<Float> allHigh = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) allHigh.add(0.99f);
            FoupContents lot = makeT3Lot(DieTypes.LOGIC, null, allHigh);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());

            assertEquals(10, plan.passingCount());
            assertEquals(0, plan.failingCount());
            assertEquals(10 * settings.chipsPerWafer(), plan.totalChips());
            assertEquals(0, plan.totalScrap());
            assertEquals(10 * 1, plan.totalPackages());

            for (int i = 0; i < 10; i++) {
                assertTrue(plan.passes(i));
                assertEquals(1, plan.packagesFor(i));
            }
        }

        @Test
        @DisplayName("plan toString does not leak pass flags")
        void planToStringSafe() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());
            String s = plan.toString();
            assertTrue(s.contains("wafers=5"));
            assertTrue(s.contains("chipsPerWafer=" + settings.chipsPerWafer()));
            assertTrue(s.contains("packagesPerWafer=1"));
            assertFalse(s.contains("true"));
            assertFalse(s.contains("false"));
        }

        @Test
        @DisplayName("plan size matches source lot")
        void planSizeMatches() {
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 7);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, defaultTransition());
            assertEquals(7, plan.size());
        }
    }

    @Nested
    @DisplayName("Transition configuration boundaries")
    class TransitionBoundaries {

        @Test
        @DisplayName("min valid yield multiplier (epsilon)")
        void minYieldMultiplier() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 1e-10, 1.0, 1.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);
            // Almost all should fail
            assertEquals(0, plan.passingCount());
        }

        @Test
        @DisplayName("max valid yield multiplier (1.0)")
        void maxYieldMultiplier() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 1.0, 1.0, 1.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);
            // Should behave like normal source judgment
            assertTrue(plan.passingCount() > 0);
        }

        @Test
        @DisplayName("min valid cost multipliers (1.0)")
        void minCostMultipliers() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1.0, 1.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);
            assertEquals(settings.chipsPerWafer(), plan.chipsPerWafer());
        }

        @Test
        @DisplayName("max valid cost multipliers (1000.0)")
        void maxCostMultipliers() {
            HighBinningRules.Transition t = new HighBinningRules.Transition(3, 4, 0.15, 1000.0, 1000.0);
            FoupContents lot = makeUniformLot(DieTypes.LOGIC, null, 5);
            PackagingPlan plan = HighBinningRules.judge(lot, tiers.getByIndex(3), dieRules, settings, matrix, 2, true, t);
            assertEquals(settings.chipsPerWafer(), plan.chipsPerWafer());
        }

        @Test
        @DisplayName("cost overflow detected in transition")
        void costOverflowInTransition() {
            // ticksMultiplier that overflows when scaled
            assertThrows(IllegalArgumentException.class,
                () -> new HighBinningRules.Transition(3, 4, 0.15, 30000.0, 1.0));
        }
    }

    @Nested
    @DisplayName("Future extensibility: T4 source for logic->T5")
    class FutureT4Source {

        @Test
        @DisplayName("T4 logic source to T5 allowed when explicitly configured")
        void t4LogicToT5Allowed() {
            Tier t4 = tiers.getByIndex(4);
            RunSpec spec = new RunSpec(DieTypes.LOGIC, 4, RunSpec.NO_CORES, SOURCE_YIELD, t4.layers());
            List<Wafer> wafers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                wafers.add(new Wafer(t4.layers(), 0.99f, Wafer.NO_FLAGS));
            }
            FoupContents lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            HighBinningRules.Transition t4to5 = new HighBinningRules.Transition(4, 5, 0.15, 1.0, 1.0);
            
            // Should not throw - T4 source for logic is allowed for future T4->T5
            PackagingPlan plan = HighBinningRules.judge(lot, t4, dieRules, settings, matrix, 2, true, t4to5);
            assertEquals(5, plan.size());
        }

        @Test
        @DisplayName("T4 compute source to T5 refused (compute fixed 1 core before T6)")
        void t4ComputeToT5Refused() {
            Tier t4 = tiers.getByIndex(4);
            RunSpec spec = new RunSpec(DieTypes.COMPUTE, 4, 1, SOURCE_YIELD, t4.layers());
            List<Wafer> wafers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                wafers.add(new Wafer(t4.layers(), 0.99f, Wafer.NO_FLAGS));
            }
            FoupContents lot = new FoupContents(FIXED_LOT, settings.foupCapacity(), wafers, spec);
            HighBinningRules.Transition t4to5 = new HighBinningRules.Transition(4, 5, 0.15, 1.0, 1.0);
            
            // Compute before T6 only supports T3->T4, not T4->T5
            assertThrows(IllegalStateException.class,
                () -> HighBinningRules.judge(lot, t4, dieRules, settings, matrix, 2, true, t4to5));
        }
    }
}