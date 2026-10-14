package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenerationMatrixTest {

    private static TierRegistry tiers() {
        return TierRegistry.build(TestTiers.all(), "test");
    }

    // minimal tiers helper? Reuse createDefault not available; build straight list
    static class TestTiers {
        static List<Tier> all() {
            List<Tier> list = new java.util.ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                list.add(new Tier("T" + i, i, "Tier" + i, "src", 100, 10, 10, i, 90.0, 100, (i >= 6 ? 4 : null), i == 6));
            }
            return list;
        }
    }

    @Test
    void defaultMatrixCellByCell() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(1,1,2,2,2,3,3,3));
        m.put("mask_shop", List.of(1,1,2,2,2,2,3,3));
        m.put("deposition_etch", List.of(1,1,1,2,2,2,3,3));
        m.put("utilities", List.of(1,1,1,1,2,2,3,3));
        m.put("test_packaging", List.of(1,1,1,2,2,2,2,3));
        GenerationMatrix gm = GenerationMatrix.build(tr, m, "default");
        // spot checks
        Tier t1 = tr.getByIndex(1);
        Tier t8 = tr.getByIndex(8);
        assertEquals(1, gm.requiredGeneration("substrate", t1));
        assertEquals(3, gm.requiredGeneration("substrate", t8));
        assertEquals(2, gm.requiredGeneration("test_packaging", tr.getByIndex(4)));
        assertEquals(3, gm.requiredGeneration("mask_shop", t8));
    }

    @Test
    void isSatisfiedEqualAboveBelow() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(2,2,2,2,2,2,2,2));
        for (String l : List.of("mask_shop","deposition_etch","utilities","test_packaging")) {
            m.put(l, List.of(1,1,1,1,1,1,1,1));
        }
        GenerationMatrix gm = GenerationMatrix.build(tr, m, "x");
        Tier t1 = tr.getByIndex(1);
        assertTrue(gm.isSatisfied("substrate", 3, t1));
        assertTrue(gm.isSatisfied("substrate", 2, t1));
        assertFalse(gm.isSatisfied("substrate", 1, t1));
    }

    @Test
    void unmetLinesOrdering() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(2,2,2,2,2,2,2,2));
        m.put("mask_shop", List.of(2,2,2,2,2,2,2,2));
        m.put("deposition_etch", List.of(1,1,1,1,1,1,1,1));
        m.put("utilities", List.of(1,1,1,1,1,1,1,1));
        m.put("test_packaging", List.of(1,1,1,1,1,1,1,1));
        GenerationMatrix gm = GenerationMatrix.build(tr, m, "x");
        Tier t1 = tr.getByIndex(1);
        Map<String,Integer> installed = Map.of("substrate", 1, "mask_shop", 2, "deposition_etch", 1, "utilities", 1, "test_packaging", 1);
        List<UnmetLine> unmet = gm.unmetLines(installed, t1);
        assertEquals(1, unmet.size());
        assertEquals("substrate", unmet.get(0).lineId());
        assertEquals(2, unmet.get(0).requiredGeneration());
        assertEquals(1, unmet.get(0).installedGeneration());
    }

    @Test
    void validationDecreasing() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(2,1,2,2,2,2,2,2));
        for (String l : List.of("mask_shop","deposition_etch","utilities","test_packaging")) m.put(l, List.of(1,1,1,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class, () -> GenerationMatrix.build(tr, m, "x"));
    }

    @Test
    void validationMissingEntry() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(1,1,1,1,1,1,1)); // 7
        for (String l : List.of("mask_shop","deposition_etch","utilities","test_packaging")) m.put(l, List.of(1,1,1,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class, () -> GenerationMatrix.build(tr, m, "x"));
    }

    @Test
    void validationMissingLine() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(1,1,1,1,1,1,1,1));
        for (String l : List.of("deposition_etch","utilities","test_packaging")) m.put(l, List.of(1,1,1,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class, () -> GenerationMatrix.build(tr, m, "x"));
    }

    @Test
    void t6DisabledMatrixUnchanged() {
        TierRegistry tr = tiers();
        Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
        m.put("substrate", List.of(1,1,2,2,2,3,3,3));
        m.put("mask_shop", List.of(1,1,2,2,2,2,3,3));
        m.put("deposition_etch", List.of(1,1,1,2,2,2,3,3));
        m.put("utilities", List.of(1,1,1,1,2,2,3,3));
        m.put("test_packaging", List.of(1,1,1,2,2,2,2,3));
        GenerationMatrix gm = GenerationMatrix.build(tr, m, "x");
        // tier6 disabled doesn't affect matrix; required gen at T6 still present
        assertEquals(3, gm.requiredGeneration("substrate", tr.getByIndex(6)));
    }

    @Test
    void entriesFactory() {
        TierRegistry tr = tiers();
        List<GenerationEntry> entries = new java.util.ArrayList<>();
        for (int t = 1; t <= 8; t++) {
            entries.add(new GenerationEntry("substrate", t, (t>=3?2:1)));
        }
        for (String l : List.of("mask_shop","deposition_etch","utilities","test_packaging")) {
            for (int t = 1; t <= 8; t++) entries.add(new GenerationEntry(l, t, 1));
        }
        // fix substrate t6+ to 3? not needed; validation passes as nondecreasing
        GenerationMatrix gm = GenerationMatrix.build(entries, tr, "entries");
        assertNotNull(gm);
    }
}
