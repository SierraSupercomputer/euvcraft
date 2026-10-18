package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entrance rules of the test and package machine.
 *
 * <p>The acceptance checks are independent of the implementation: the support matrix
 * used here says test_packaging needs generation 1 at T1-T3, generation 2 at T4-T7 and
 * generation 3 at T8, and the fixtures build complete lots whose wafers sit exactly on
 * the fixture tier's final layer ({@code Tier.layers()} of the shared test tiers).</p>
 */
class PackagingRulesTest {

    /** Wafer count of the complete-lot fixtures. */
    private static final int WAFERS = 3;

    private static TierRegistry tiers;
    private static GenerationMatrix matrix;

    @BeforeAll
    static void setUp() {
        tiers = TierRegistry.build(GenerationMatrixTest.TestTiers.all(), "packaging-rules-test");
        Map<String, List<Integer>> byLine = new LinkedHashMap<>();
        for (String line : List.of("substrate", "mask_shop", "deposition_etch", "utilities")) {
            byLine.put(line, List.of(1, 1, 1, 1, 1, 1, 1, 1));
        }
        // Data-driven gate: test_packaging needs G1 at T1-T3, G2 at T4-T7, G3 at T8.
        byLine.put("test_packaging", List.of(1, 1, 1, 2, 2, 2, 2, 3));
        matrix = GenerationMatrix.build(tiers, byLine, "packaging-rules-test");
    }

    /** A complete lot at the given tier: every wafer sits on the tier's final layer. */
    private static FoupContents lot(int tierIndex, String dieType, int waferCount) {
        Tier tier = tiers.getByIndex(tierIndex);
        RunSpec run = new RunSpec(dieType, tierIndex, 0, 0.9, tier.layers());
        List<Wafer> wafers = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers.add(new Wafer(tier.layers(), Wafer.NO_ROLLS, Wafer.NO_FLAGS));
        }
        return new FoupContents(UUID.randomUUID(), 25, wafers, run);
    }

    /** A lot where one wafer is still at an earlier layer, so the lot is unfinished. */
    private static FoupContents lotWithOneWaferAt(int tierIndex, int earlyLayer) {
        Tier tier = tiers.getByIndex(tierIndex);
        RunSpec run = new RunSpec(DieTypes.LOGIC, tierIndex, 0, 0.9, tier.layers());
        List<Wafer> wafers = new ArrayList<>();
        wafers.add(new Wafer(earlyLayer, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
        wafers.add(new Wafer(tier.layers(), Wafer.NO_ROLLS, Wafer.NO_FLAGS));
        return new FoupContents(UUID.randomUUID(), 25, wafers, run);
    }

    /** A lot with complete wafers but no frozen run spec, built without reflection. */
    private static FoupContents lotWithoutSpec(int tierIndex) {
        int finalLayer = tiers.getByIndex(tierIndex).layers();
        List<Wafer> wafers = new ArrayList<>();
        wafers.add(new Wafer(finalLayer, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
        return new FoupContents(UUID.randomUUID(), 25, wafers);
    }

    // ---------- admitted lots ----------

    @Test
    void lineIsThePackagingSupportLine() {
        assertEquals("test_packaging", PackagingRules.LINE_ID);
    }

    @Test
    void completeLotIsAdmitted() {
        assertNull(PackagingRules.validate(lot(1, DieTypes.LOGIC, WAFERS),
            tiers.getByIndex(1), true, matrix, 1));
    }

    @Test
    void completeLotAtHigherTierIsAdmittedWhenGenerationMeetsTheRealGate() {
        // T4 needs test_packaging generation 2: gen 2 admits it, gen 1 does not.
        assertNull(PackagingRules.validate(lot(4, DieTypes.COMPUTE, WAFERS),
            tiers.getByIndex(4), true, matrix, 2));
        assertEquals(PackagingRefusalReason.GENERATION_UNMET,
            PackagingRules.validate(lot(4, DieTypes.COMPUTE, WAFERS),
                tiers.getByIndex(4), true, matrix, 1));
    }

    // ---------- the five refusal reasons ----------

    @Test
    void nullLotIsRefused() {
        assertEquals(PackagingRefusalReason.NO_LOT,
            PackagingRules.validate(null, tiers.getByIndex(1), true, matrix, 1));
    }

    @Test
    void emptyLotIsRefused() {
        FoupContents empty = FoupContents.empty(UUID.randomUUID(), 25);
        assertEquals(PackagingRefusalReason.NO_LOT,
            PackagingRules.validate(empty, tiers.getByIndex(1), true, matrix, 1));
    }

    @Test
    void lotAndTierProblemsAreOnlyJudgedAfterTheLotItself() {
        // The checks run outward from the lot, so a lot problem wins over a bad caller
        // even to the point of a null tier: no lot, no judgment, no exception.
        assertEquals(PackagingRefusalReason.NO_LOT,
            PackagingRules.validate(null, null, true, matrix, 1));
        FoupContents empty = FoupContents.empty(UUID.randomUUID(), 25);
        assertEquals(PackagingRefusalReason.NO_LOT,
            PackagingRules.validate(empty, null, true, matrix, 1));
        FoupContents unstarted = lotWithoutSpec(1);
        assertEquals(PackagingRefusalReason.NO_SPEC,
            PackagingRules.validate(unstarted, null, true, matrix, 1));
    }

    @Test
    void lotWithoutRunSpecIsRefused() {
        assertEquals(PackagingRefusalReason.NO_SPEC,
            PackagingRules.validate(lotWithoutSpec(1), tiers.getByIndex(1), true, matrix, 1));
    }

    @Test
    void incompleteLotIsRefused() {
        // One wafer at layer 0 of a T1 lot whose final layer is 1: unfinished.
        assertEquals(PackagingRefusalReason.LOT_INCOMPLETE,
            PackagingRules.validate(lotWithOneWaferAt(1, Wafer.UNPROCESSED_LAYER),
                tiers.getByIndex(1), true, matrix, 1));
        // Same at T4: one wafer left at layer 3 of 4 refuses the whole lot.
        assertEquals(PackagingRefusalReason.LOT_INCOMPLETE,
            PackagingRules.validate(lotWithOneWaferAt(4, 3),
                tiers.getByIndex(4), true, matrix, 2));
    }

    @Test
    void completenessIsCheckBeforeTheCatalogSoOneReasonPerCall() {
        // An unfinished lot with an unavailable chip reports LOT_INCOMPLETE, the
        // earlier of the two reasons, never a combined story.
        assertEquals(PackagingRefusalReason.LOT_INCOMPLETE,
            PackagingRules.validate(lotWithOneWaferAt(1, Wafer.UNPROCESSED_LAYER),
                tiers.getByIndex(1), false, matrix, 1));
    }

    @Test
    void missingChipItemIsRefused() {
        assertEquals(PackagingRefusalReason.NO_CHIP_FOR_DIE,
            PackagingRules.validate(lot(1, DieTypes.LOGIC, WAFERS),
                tiers.getByIndex(1), false, matrix, 1));
    }

    @Test
    void missingChipItemWinsOverTheGenerationGate() {
        // Both fail-closed gates are unmet; the catalog check runs first.
        assertEquals(PackagingRefusalReason.NO_CHIP_FOR_DIE,
            PackagingRules.validate(lot(4, DieTypes.COMPUTE, WAFERS),
                tiers.getByIndex(4), false, matrix, 1));
    }

    @Test
    void generationGateIsDataDrivenNotAHardcodedCeiling() {
        // T2 needs test_packaging G1 only: gen 1 admits it.
        assertNull(PackagingRules.validate(lot(2, DieTypes.LOGIC, WAFERS),
            tiers.getByIndex(2), true, matrix, 1));
        // T8 needs test_packaging G3: gen 2 is short, gen 3 admits it.
        assertEquals(PackagingRefusalReason.GENERATION_UNMET,
            PackagingRules.validate(lot(8, DieTypes.MEMORY, WAFERS),
                tiers.getByIndex(8), true, matrix, 2));
        assertNull(PackagingRules.validate(lot(8, DieTypes.MEMORY, WAFERS),
            tiers.getByIndex(8), true, matrix, 3));
    }

    @Test
    void generationGateFailsClosedBelowOne() {
        Tier t1 = tiers.getByIndex(1);
        // The matrix requires G1 here, so anything below 1, including zero and
        // negative generations, is an unmet gate, never "compatible".
        assertEquals(PackagingRefusalReason.GENERATION_UNMET,
            PackagingRules.validate(lot(1, DieTypes.LOGIC, WAFERS), t1, true, matrix, 0));
        assertEquals(PackagingRefusalReason.GENERATION_UNMET,
            PackagingRules.validate(lot(1, DieTypes.LOGIC, WAFERS), t1, true, matrix, -1));
    }

    // ---------- structurally wrong callers fail closed ----------

    @Test
    void nullTierThrowsForAnAdmissibleLot() {
        FoupContents good = lot(1, DieTypes.LOGIC, WAFERS);
        assertThrows(IllegalArgumentException.class,
            () -> PackagingRules.validate(good, null, true, matrix, 1));
    }

    @Test
    void tierMismatchWithTheFrozenSpecThrows() {
        FoupContents t1Lot = lot(1, DieTypes.LOGIC, WAFERS);
        assertThrows(IllegalArgumentException.class,
            () -> PackagingRules.validate(t1Lot, tiers.getByIndex(2), true, matrix, 1));
    }

    @Test
    void nullMatrixThrowsInsteadOfFailingOpen() {
        FoupContents good = lot(1, DieTypes.LOGIC, WAFERS);
        assertThrows(IllegalArgumentException.class,
            () -> PackagingRules.validate(good, tiers.getByIndex(1), true, null, 1));
    }

    @Test
    void earlierRefusalsRemainAuthoritativeWhenTheMatrixIsAbsent() {
        assertEquals(PackagingRefusalReason.NO_LOT,
            PackagingRules.validate(null, null, true, null, 1));
        assertEquals(PackagingRefusalReason.NO_SPEC,
            PackagingRules.validate(lotWithoutSpec(1), null, true, null, 1));
        assertEquals(PackagingRefusalReason.LOT_INCOMPLETE,
            PackagingRules.validate(lotWithOneWaferAt(1, 0), tiers.getByIndex(1), true, null, 1));
        assertEquals(PackagingRefusalReason.NO_CHIP_FOR_DIE,
            PackagingRules.validate(lot(1, DieTypes.LOGIC, WAFERS), tiers.getByIndex(1), false, null, 1));
    }


    @Test
    void missingSpecWinsForAnIncompleteLot() {
        FoupContents unfinished = new FoupContents(UUID.randomUUID(), 25, List.of(Wafer.unprocessed()));
        assertEquals(PackagingRefusalReason.NO_SPEC,
            PackagingRules.validate(unfinished, tiers.getByIndex(1), true, matrix, 1));
        assertEquals(PackagingRefusalReason.NO_SPEC,
            PackagingRules.validate(unfinished, null, false, null, 0));
    }

    @Test
    void wrongResolvedTierThrowsEvenWhenTheLotIsIncomplete() {
        FoupContents unfinished = lotWithOneWaferAt(1, 0);
        assertThrows(IllegalArgumentException.class,
            () -> PackagingRules.validate(unfinished, null, true, matrix, 1));
        assertThrows(IllegalArgumentException.class,
            () -> PackagingRules.validate(unfinished, tiers.getByIndex(2), true, matrix, 1));
    }

}
