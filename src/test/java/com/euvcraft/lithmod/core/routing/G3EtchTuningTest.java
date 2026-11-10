package com.euvcraft.lithmod.core.routing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class G3EtchTuningTest {
    @Test
    void higherCoreCountsAreLessForgivingOfTheSameDepthError() {
        int oneCore = G3EtchTuning.computeTolerance(12, 1, 3);
        int manyCores = G3EtchTuning.computeTolerance(12, 24, 3);
        assertEquals(12, oneCore);
        assertEquals(5, manyCores);
        assertTrue(G3EtchTuning.quality(3, manyCores) < G3EtchTuning.quality(3, oneCore));
        assertEquals(1000, G3EtchTuning.quality(0, manyCores));
        assertEquals(1, G3EtchTuning.computeTolerance(12, 256, 3));
    }

    @Test
    void tuningScoresSignedErrorsSymmetricallyAndClampsAtZero() {
        assertEquals(500, G3EtchTuning.quality(-7, 14));
        assertEquals(500, G3EtchTuning.quality(7, 14));
        assertEquals(0, G3EtchTuning.quality(24, 14));
    }
}
