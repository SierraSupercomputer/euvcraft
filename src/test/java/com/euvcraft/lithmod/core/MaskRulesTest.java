package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaskRulesTest {

    private static final String MASK_SHOP = "mask_shop";

    private static TierRegistry tiers;
    private static DieRules rules;
    private static GenerationMatrix matrix;

    @BeforeAll
    static void setUp() {
        tiers = TierRegistry.build(GenerationMatrixTest.TestTiers.all(), "mask-rules-test");
        rules = DieRules.defaults(tiers);
        // mask_shop requires G1 at T1-T2, G2 at T3-T6, G3 at T7-T8
        Map<String, List<Integer>> m = new LinkedHashMap<>();
        m.put("substrate", List.of(1, 1, 1, 1, 1, 1, 1, 1));
        m.put(MASK_SHOP, List.of(1, 1, 2, 2, 2, 2, 3, 3));
        m.put("deposition_etch", List.of(1, 1, 1, 1, 1, 1, 1, 1));
        m.put("utilities", List.of(1, 1, 1, 1, 1, 1, 1, 1));
        m.put("test_packaging", List.of(1, 1, 1, 1, 1, 1, 1, 1));
        matrix = GenerationMatrix.build(tiers, m, "mask-rules-test");
    }

    // ---------- allowed cases ----------

    @Test
    void logicT1AllowedAtGeneration1() {
        assertTrue(MaskRules.canPattern(DieTypes.LOGIC, 1, 1, rules, matrix));
    }

    @Test
    void logicT2AllowedAtGeneration1() {
        assertTrue(MaskRules.canPattern(DieTypes.LOGIC, 2, 1, rules, matrix));
    }

    @Test
    void memoryT2AllowedAtGeneration1() {
        // memory starts at T2 and mask_shop at T2 only requires G1
        assertTrue(MaskRules.canPattern(DieTypes.MEMORY, 2, 1, rules, matrix));
    }

    @Test
    void computeAndNandT3AllowedAtGeneration2() {
        assertTrue(MaskRules.canPattern(DieTypes.COMPUTE, 3, 2, rules, matrix));
        assertTrue(MaskRules.canPattern(DieTypes.NAND, 3, 2, rules, matrix));
    }

    @Test
    void computeT7AllowedAtGeneration3() {
        assertTrue(MaskRules.canPattern(DieTypes.COMPUTE, 7, 3, rules, matrix));
    }

    @Test
    void canPatternAcceptsMaskDataFields() {
        MaskData mask = new MaskData(DieTypes.MEMORY, 2, 3);
        assertTrue(MaskRules.canPattern(mask.dieType(), mask.tierIndex(), 1, rules, matrix));
    }

    // ---------- refused cases ----------

    @Test
    void memoryT1Refused() {
        assertFalse(MaskRules.canPattern(DieTypes.MEMORY, 1, 1, rules, matrix));
        // even a surplus generation cannot rescue an unavailable die rule
        assertFalse(MaskRules.canPattern(DieTypes.MEMORY, 1, 3, rules, matrix));
    }

    @Test
    void computeAndNandT3RefusedAtGeneration1() {
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 3, 1, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.NAND, 3, 1, rules, matrix));
    }

    @Test
    void computeT7RefusedAtGeneration2() {
        // mask_shop requires G3 at T7; G2 is not enough (asymmetric vs T3)
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 7, 2, rules, matrix));
    }

    @Test
    void absentHugeTierRefused() {
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 9, 1, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1000, 1, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, Integer.MAX_VALUE, 1, rules, matrix));
    }

    @Test
    void nonpositiveTierRefused() {
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 0, 1, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, -1, 1, rules, matrix));
    }

    @Test
    void nonpositiveGenerationRefused() {
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1, 0, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1, -1, rules, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 3, 0, rules, matrix));
    }

    @Test
    void nullDieTypeRefused() {
        assertFalse(MaskRules.canPattern(null, 1, 1, rules, matrix));
    }

    @Test
    void unknownDieTypeRefused() {
        assertFalse(MaskRules.canPattern("quantum", 1, 1, rules, matrix));
        assertFalse(MaskRules.canPattern("", 1, 1, rules, matrix));
    }

    @Test
    void noncanonicalDieTypeRefused() {
        // fail closed with no normalization: exact canonical ids only
        assertFalse(MaskRules.canPattern("LOGIC", 1, 1, rules, matrix));
        assertFalse(MaskRules.canPattern("Memory", 2, 1, rules, matrix));
        assertFalse(MaskRules.canPattern(" compute", 3, 2, rules, matrix));
        assertFalse(MaskRules.canPattern("nand ", 3, 2, rules, matrix));
    }

    @Test
    void nullRegistriesRefused() {
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1, 1, null, matrix));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1, 1, rules, null));
        assertFalse(MaskRules.canPattern(DieTypes.LOGIC, 1, 1, (DieRules) null, (GenerationMatrix) null));
    }

    // ---------- asymmetric cases catching swapped tier/generation args ----------

    @Test
    void swappedTierAndGenerationCaughtByMemoryRule() {
        // correct order: tier 2 with installed G1 is allowed
        assertTrue(MaskRules.canPattern(DieTypes.MEMORY, 2, 1, rules, matrix));
        // swapped: tier 1 with installed G2 must be refused twice over
        // (memory starts at T2, and tier 2 needs only G1 for mask_shop)
        assertFalse(MaskRules.canPattern(DieTypes.MEMORY, 1, 2, rules, matrix));
    }

    @Test
    void swappedTierAndGenerationCaughtByComputeRule() {
        // correct order: compute needs T3 and mask_shop G2 there
        assertTrue(MaskRules.canPattern(DieTypes.COMPUTE, 3, 2, rules, matrix));
        // swapped: tier 2 refutes compute's firstTier=3 even with surplus G3
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 2, 3, rules, matrix));
    }

    @Test
    void tierAndGenerationAreNotInterchangeableAcrossTiers() {
        // T3 needs G2: (tier=3, gen=1) refused, (tier=3, gen=2) allowed
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 3, 1, rules, matrix));
        assertTrue(MaskRules.canPattern(DieTypes.COMPUTE, 3, 2, rules, matrix));
        // T7 needs G3: (tier=7, gen=2) refused, (tier=7, gen=3) allowed
        assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 7, 2, rules, matrix));
        assertTrue(MaskRules.canPattern(DieTypes.COMPUTE, 7, 3, rules, matrix));
    }
}
