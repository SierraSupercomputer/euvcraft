package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DieRulesTest {
    private TierRegistry registry() {
        return TierRegistry.build(List.of(
            new Tier("T1", 1, "Contact", "src", 5000, 1000, 75, 6, 60.0, 1, null, false),
            new Tier("T2", 2, "Stepper", "src", 500, 150, 45, 12, 80.0, 15, null, false),
            new Tier("T3", 3, "KrF DUV", "src", 180, 40, 80, 24, 90.0, 600, null, false),
            new Tier("T4", 4, "ArF dry", "src", 65, 15, 110, 32, 92.0, 2600, null, false),
            new Tier("T5", 5, "ArF immersion", "src", 28, 4, 250, 42, 93.0, 3400, null, false),
            new Tier("T6", 6, "Multi-patterned", "src", 7, 3, 125, 70, 85.0, 3900, 4, true),
            new Tier("T7", 7, "EUV", "src", 13, 2, 140, 80, 85.0, 5000, 8, false),
            new Tier("T8", 8, "High-NA EUV", "src", 8, 1, 170, 90, 80.0, 5500, 12, false)
        ), "test");
    }

    @Test
    void canMakeBoundaries() {
        DieRules rules = DieRules.defaults(registry());
        assertFalse(rules.canMake("memory", 1));
        assertTrue(rules.canMake("memory", 2));
        assertFalse(rules.canMake("compute", 2));
        assertTrue(rules.canMake("compute", 3));
    }

    @Test
    void logicCappedAtT5() {
        DieRules rules = DieRules.defaults(registry());
        assertEquals(5, rules.effectiveTier("logic", 7));
        assertTrue(rules.isCapped("logic", 7));
        assertEquals(registry().getByIndex(5).targetSpeedMhz(), rules.targetSpeedMhz("logic", 7));
        System.out.println(rules.availabilityTable());
    }

    @Test
    void coreCountsAvailability() {
        DieRules rules = DieRules.defaults(registry());
        assertFalse(rules.coreCountsAvailable("compute", 5));
        assertTrue(rules.coreCountsAvailable("compute", 6));
        assertTrue(rules.coreCountsAvailable("compute", 7));
        assertTrue(rules.coreCountsAvailable("compute", 8));
        for (String die : List.of("logic", "memory", "nand", "compute")) {
            if ("compute".equals(die)) continue;
            for (int i = 1; i <= 8; i++) {
                assertFalse(rules.coreCountsAvailable(die, i), die + " at " + i);
            }
        }
        // T6 disabled case: T7 still true
        assertTrue(rules.coreCountsAvailable("compute", 7));
    }

    @Test
    void requirementsFromRegistry() {
        DieRules rules = DieRules.defaults(registry());
        Tier eff = rules.requirements("logic", 7);
        assertEquals(registry().getByIndex(5).resolutionNm(), eff.resolutionNm());
        assertEquals(registry().getByIndex(5).overlayNm(), eff.overlayNm());
    }

    @Test
    void dieTypeConstants() {
        DieRules rules = DieRules.defaults(registry());
        // accept null source? no, but defaults source ignored? ensure not throw for default builder via defaults
        assertNotNull(rules);
    }

    @Nested
    class Validation {
        @Test
        void firstGreaterThanCapFails() {
            TierRegistry r = registry();
            assertThrows(IllegalArgumentException.class, () -> {
                List<DieRule> bad = List.of(new DieRule("compute", 3, 2, true));
                DieRules.build(r, bad, "bad");
            });
        }

        @Test
        void missingTierIndexFails() {
            TierRegistry r = registry();
            assertThrows(IllegalArgumentException.class, () -> {
                List<DieRule> bad = List.of(new DieRule("compute", 0, null, true));
                DieRules.build(r, bad, "bad");
            });
        }

        @Test
        void capBeyondRegistryFails() {
            TierRegistry r = registry();
            List<DieRule> bad = List.of(new DieRule("compute", 3, 9, true));
            assertThrows(IllegalArgumentException.class, () -> DieRules.build(r, bad, "bad"));
        }
    }
}
