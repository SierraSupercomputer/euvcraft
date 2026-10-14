package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatModelVerificationTest {

    private TierRegistry registry() {
        // TierRegistry requires exactly 8 tiers
        List<Tier> all = List.of(
            new Tier("T1", 1, "Contact", "mercury arc lamp", 5000, 1000, 75, 6, 60.0, 1, null, false),
            new Tier("T2", 2, "Stepper", "g-line then i-line lamp", 500, 150, 45, 12, 80.0, 15, null, false),
            new Tier("T3", 3, "KrF DUV", "248 nm excimer laser", 180, 40, 80, 24, 90.0, 600, null, false),
            new Tier("T4", 4, "ArF dry", "193 nm excimer laser", 65, 15, 110, 32, 92.0, 2600, null, false),
            new Tier("T5", 5, "ArF immersion", "193 nm, water immersion", 28, 4, 250, 42, 93.0, 3400, null, false),
            new Tier("T6", 6, "Multi-patterned", "193 nm, several passes", 7, 3, 125, 70, 85.0, 3900, 4, true),
            new Tier("T7", 7, "EUV", "13.5 nm tin plasma", 13, 2, 140, 80, 85.0, 5000, 8, false),
            new Tier("T8", 8, "High-NA EUV", "13.5 nm tin plasma, NA 0.55", 8, 1, 170, 90, 80.0, 5500, 12, false)
        );
        return TierRegistry.build(all, "test");
    }

    @Test
    void allComponentsT3() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(3,3,3,3,3,3,3,1.0);
        LineStats s = rules.stats(c, 3);
        assertEquals(180, s.resolutionNm());
        assertEquals(40, s.overlayNm());
        assertEquals(80, s.throughputWph());
        assertEquals(1.0, s.defectFactor());
        assertEquals(3, s.resolutionTier());
        assertEquals(3, s.overlayTier());
        assertEquals(3, s.throughputTier());
    }

    @Test
    void mixedComponentsTiers() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(5,4,5,5,3,3,5,1.0);
        LineStats s = rules.stats(c, 3);
        assertEquals(4, s.resolutionTier());
        assertEquals(3, s.overlayTier());
    }

    @Test
    void deficitCleanroom() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg, new StatRulesParams(0.15));
        LineComponents c = new LineComponents(4,4,4,4,4,2,4,1.0);
        LineStats s = rules.stats(c, 4);
        assertEquals(0.7, s.defectFactor(), 1e-9);
    }

    @Test
    void deficitMask() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(4,4,1,4,4,4,4,1.0);
        LineStats s = rules.stats(c, 4);
        assertEquals(0.55, s.defectFactor(), 1e-9);
    }

    @Test
    void maintenanceHalves() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(3,3,3,3,3,3,3,0.5);
        LineStats s = rules.stats(c, 3);
        assertEquals(0.5, s.defectFactor(), 1e-9);
    }

    @Test
    void zeroBlocksCanRun() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(0,3,3,3,3,3,3,1.0);
        assertFalse(rules.canRunTier(c, 1));
    }

    @Test
    void penaltyNeverBelowZero() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        // cleanroom 0 running 8 => 1 - 0.15*8 = -0.2 -> clamped 0
        LineComponents c = new LineComponents(8,8,8,8,8,0,8,1.0);
        LineStats s = rules.stats(c, 8);
        // defectFactor = 1.0 * 0 * 1 = 0
        assertEquals(0.0, s.defectFactor(), 1e-9);
    }

    @Test
    void boundaryCanRun() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        // components all 4, target 4 => can run
        LineComponents c4 = new LineComponents(4,4,4,4,4,4,4,1.0);
        assertTrue(rules.canRunTier(c4, 4));
        // components all 3, target 4 => cannot
        LineComponents c3 = new LineComponents(3,3,3,3,3,3,3,1.0);
        assertFalse(rules.canRunTier(c3, 4));
    }

    @Test
    void zeroTierGivesZeroStats() {
        TierRegistry reg = registry();
        StatRules rules = new DefaultStatRules(reg);
        LineComponents c = new LineComponents(0,0,0,0,0,0,0,1.0);
        LineStats s = rules.stats(c, 1);
        assertEquals(0, s.resolutionNm());
        assertEquals(0, s.overlayNm());
        assertEquals(0, s.throughputWph());
    }

    @Test
    void tier0Unknown() {
        TierRegistry reg = registry();
        assertEquals(0, reg.byIndex(0).map(Tier::resolutionNm).orElse(0));
    }
}
