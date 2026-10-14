package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupportLineJsonDataTest {

    @Test
    void bundledJsonMatchesReferenceTable() {
        SupportLineDataLoader loader = new SupportLineDataLoader();
        GenerationMatrix gm = loader.fallback();
        assertNotNull(gm);
        TierRegistry tr = tierRegistry();
        assertEquals(1, gm.requiredGeneration("substrate", tr.getByIndex(1)));
        assertEquals(3, gm.requiredGeneration("substrate", tr.getByIndex(8)));
        assertEquals(1, gm.requiredGeneration("test_packaging", tr.getByIndex(3)));
        assertEquals(2, gm.requiredGeneration("test_packaging", tr.getByIndex(4)));
    }

    private TierRegistry tierRegistry() {
        java.util.List<Tier> tiers = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            tiers.add(new Tier("T" + i, i, "Tier" + i, "src", 100, 10, 10, i, 90.0, 100, (i >= 6 ? 4 : null), i == 6));
        }
        return TierRegistry.build(tiers, "test");
    }
}
