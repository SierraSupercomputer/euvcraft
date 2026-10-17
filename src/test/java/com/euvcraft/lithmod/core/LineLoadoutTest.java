package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineLoadoutTest {

    private StatRules rules() {
        return new DefaultStatRules(ReferenceTiers.registry());
    }

    private static final double M = 1.0;

    @Test
    void allT1CanRunT1NotT2() {
        LineLoadout loadout = new LineLoadout(1, 1, 1, 1, 1, 1, 1, M);
        assertTrue(rules().canRunTier(loadout.toLineComponents(), 1));
        assertFalse(rules().canRunTier(loadout.toLineComponents(), 2));
    }

    @Test
    void allT2CanRunT2() {
        LineLoadout loadout = new LineLoadout(2, 2, 2, 2, 2, 2, 2, M);
        assertTrue(rules().canRunTier(loadout.toLineComponents(), 2));
    }

    @Test
    void t2OpticsWithT1StageCannotRunT2AndOverlayTier1() {
        LineLoadout loadout = new LineLoadout(2, 2, 1, 2, 2, 2, 2, M);
        assertEquals(1, rules().stats(loadout.toLineComponents(), 2).overlayTier());
        assertFalse(rules().canRunTier(loadout.toLineComponents(), 2));
    }

    @Test
    void emptyKindGivesTier0AndCannotRun() {
        LineLoadout loadout = new LineLoadout(0, 1, 1, 1, 1, 1, 1, M);
        assertEquals(0, rules().stats(loadout.toLineComponents(), 1).resolutionTier());
        assertFalse(rules().canRunTier(loadout.toLineComponents(), 1));
    }

    @Test
    void defectFactorDropsWhenCleanroomBelowTarget() {
        LineLoadout full = new LineLoadout(2, 2, 2, 2, 2, 2, 2, M);
        LineLoadout low = new LineLoadout(2, 2, 2, 1, 2, 2, 2, M);
        assertEquals(1.0, rules().stats(full.toLineComponents(), 2).defectFactor(), 1e-9);
        assertTrue(rules().stats(low.toLineComponents(), 2).defectFactor() < 1.0);
    }

    @Test
    void defectFactorDropsWhenMaskBelowTarget() {
        LineLoadout low = new LineLoadout(2, 2, 2, 2, 2, 1, 2, M);
        assertTrue(rules().stats(low.toLineComponents(), 2).defectFactor() < 1.0);
    }

    @Test
    void toLineComponentsMatchesFields() {
        LineLoadout l = new LineLoadout(1, 2, 3, 4, 5, 6, 7, 0.5);
        LineComponents c = l.toLineComponents();
        assertEquals(1, c.lightSourceTier());
        assertEquals(2, c.opticsTier());
        assertEquals(6, c.maskTier());
        assertEquals(7, c.resistTier());
        assertEquals(3, c.stageTier());
        assertEquals(4, c.cleanroomTier());
        assertEquals(5, c.metrologyTier());
        assertEquals(0.5, c.maintenance());
    }
}