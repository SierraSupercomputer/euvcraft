package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The immutable per-wafer plan of the test and package machine.
 *
 * <p>Every expected total is written out from the fixture arithmetic, not read back
 * from the plan: a mixed plan of six wafers with three passing ones at chipsPerWafer 1
 * gives 3 chips, 3 scrap and 3 packages, and at chipsPerWafer 4 gives 12 chips, 12
 * scrap and (at packagesPerWafer 2) 6 packages.</p>
 */
class PackagingPlanTest {

    private static final List<Boolean> THREE_PASS = List.of(true, true, true);
    private static final List<Boolean> THREE_FAIL = List.of(false, false, false);

    /** Six wafers: the passing ones sit at slots 0, 2 and 5, so order matters. */
    private static final List<Boolean> MIXED = List.of(
        true, false, true, false, false, true);

    // ---------- arithmetic at chipsPerWafer 1 ----------

    @Test
    void allPassAtChips1Packages1TotalsThreeOfEach() {
        PackagingPlan plan = new PackagingPlan(THREE_PASS, 1, 1);
        assertEquals(3, plan.size());
        assertEquals(3, plan.passingCount());
        assertEquals(0, plan.failingCount());
        assertEquals(3, plan.totalChips());
        assertEquals(0, plan.totalScrap());
        assertEquals(3, plan.totalPackages());
        for (int i = 0; i < 3; i++) {
            assertTrue(plan.passes(i));
            assertEquals(1, plan.packagesFor(i));
        }
    }

    @Test
    void mixedAtChips1Packages1TotalsThreeChipsThreeScrap() {
        PackagingPlan plan = new PackagingPlan(MIXED, 1, 1);
        assertEquals(6, plan.size());
        assertEquals(3, plan.passingCount());
        assertEquals(3, plan.failingCount());
        assertEquals(3, plan.totalChips());
        assertEquals(3, plan.totalScrap());
        assertEquals(3, plan.totalPackages());
    }

    @Test
    void perWaferFlagOrderMatchesTheFixture() {
        PackagingPlan plan = new PackagingPlan(MIXED, 1, 1);
        // Independently transcribed from the fixture: pass at 0, 2, 5 and nowhere else.
        assertTrue(plan.passes(0));
        assertFalse(plan.passes(1));
        assertTrue(plan.passes(2));
        assertFalse(plan.passes(3));
        assertFalse(plan.passes(4));
        assertTrue(plan.passes(5));
    }

    // ---------- arithmetic at chipsPerWafer 4 ----------

    @Test
    void allPassAtChips4Packages2TotalsTwelveChipsSixPackages() {
        PackagingPlan plan = new PackagingPlan(THREE_PASS, 4, 2);
        assertEquals(12, plan.totalChips());
        assertEquals(0, plan.totalScrap());
        assertEquals(6, plan.totalPackages());
        for (int i = 0; i < 3; i++) {
            assertEquals(2, plan.packagesFor(i));
        }
    }

    @Test
    void allFailAtChips4YieldsScrapOnlyAndConsumesNoPackages() {
        PackagingPlan plan = new PackagingPlan(THREE_FAIL, 4, 2);
        assertEquals(0, plan.totalChips());
        assertEquals(12, plan.totalScrap());
        assertEquals(0, plan.totalPackages());
        for (int i = 0; i < 3; i++) {
            assertFalse(plan.passes(i));
            assertEquals(0, plan.packagesFor(i));
        }
    }

    @Test
    void mixedAtChips4Packages2AlternatesPackagesPerWafer() {
        PackagingPlan plan = new PackagingPlan(MIXED, 4, 2);
        assertEquals(12, plan.totalChips());
        assertEquals(12, plan.totalScrap());
        assertEquals(6, plan.totalPackages());
        // Passing slots 0, 2, 5 use the frozen two packages; the rest use none.
        assertEquals(2, plan.packagesFor(0));
        assertEquals(0, plan.packagesFor(1));
        assertEquals(2, plan.packagesFor(2));
        assertEquals(0, plan.packagesFor(3));
        assertEquals(0, plan.packagesFor(4));
        assertEquals(2, plan.packagesFor(5));
    }

    // ---------- boundaries ----------

    @Test
    void waferIndexesOutsideThePlanAreRejected() {
        PackagingPlan plan = new PackagingPlan(THREE_PASS, 1, 1);
        assertThrows(IndexOutOfBoundsException.class, () -> plan.passes(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.passes(3));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.packagesFor(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.packagesFor(3));
    }

    @Test
    void emptyFlagListIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingPlan(List.of(), 1, 1));
    }

    @Test
    void nullFlagListAndNullFlagEntriesAreRejected() {
        assertThrows(NullPointerException.class,
            () -> new PackagingPlan(null, 1, 1));
        assertThrows(NullPointerException.class,
            () -> new PackagingPlan(Arrays.asList(Boolean.TRUE, null), 1, 1));
    }

    @Test
    void nonPositivePerWaferAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PackagingPlan(THREE_PASS, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PackagingPlan(THREE_PASS, -4, 1));
        assertThrows(IllegalArgumentException.class, () -> new PackagingPlan(THREE_PASS, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PackagingPlan(THREE_PASS, 1, -2));
    }

    @Test
    void perWaferAmountsThatWouldOverflowTotalsAreRejected() {
        // 2 wafers at Integer.MAX_VALUE chips each would overflow the queue totals.
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingPlan(THREE_PASS, Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new PackagingPlan(THREE_PASS, 1, Integer.MAX_VALUE));
    }

    // ---------- defensive copying ----------

    @Test
    void flagsAreCopiedOutOfTheSourceList() {
        List<Boolean> source = new ArrayList<>(List.of(true, false, true));
        PackagingPlan plan = new PackagingPlan(source, 4, 1);
        source.set(0, false);
        source.add(Boolean.FALSE);
        // The plan judged the lot at construction: later edits to the caller's list
        // must not rewrite judged outcomes.
        assertEquals(3, plan.size());
        assertTrue(plan.passes(0));
        assertFalse(plan.passes(1));
        assertTrue(plan.passes(2));
    }

    @Test
    void returnedFlagListIsImmutable() {
        PackagingPlan plan = new PackagingPlan(THREE_PASS, 1, 1);
        List<Boolean> flags = plan.passFlags();
        assertThrows(UnsupportedOperationException.class, () -> flags.add(Boolean.FALSE));
        assertThrows(UnsupportedOperationException.class, () -> flags.set(0, Boolean.FALSE));
    }

    // ---------- privacy ----------

    @Test
    void planDescriptionNeverShowsOutcomes() {
        // Two plans identical except for the hidden flags must print identically, and
        // no textual outcome may appear at all.
        String passText = new PackagingPlan(THREE_PASS, 4, 2).toString();
        String failText = new PackagingPlan(THREE_FAIL, 4, 2).toString();
        assertEquals(failText, passText);
        assertFalse(passText.contains("true"));
        assertFalse(passText.contains("false"));
    }
}
