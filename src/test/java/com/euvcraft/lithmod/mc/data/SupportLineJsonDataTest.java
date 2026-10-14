package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupportLineJsonDataTest {

    @Test
    void bundledJsonMatchesReferenceTable() throws Exception {
        AbstractGenerationMatrixDataLoader loader = new AbstractGenerationMatrixDataLoader("support_lines") {
        };
        // invoke fallback to exercise bundled file
        GenerationMatrix gm = loader.fallback();
        assertNotNull(gm);
        // default reference checks
        TierRegistry tr = tierRegistry();
        assertEquals(1, gm.requiredGeneration("substrate", tr.getByIndex(1)));
        assertEquals(3, gm.requiredGeneration("substrate", tr.getByIndex(8)));
        assertEquals(1, gm.requiredGeneration("test_packaging", tr.getByIndex(3)));
        assertEquals(2, gm.requiredGeneration("test_packaging", tr.getByIndex(4)));
    }

    private TierRegistry tierRegistry() {
        List<Tier> tiers = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            tiers.add(new Tier("T" + i, i, "Tier" + i, "src", 100, 10, 10, i, 90.0, 100, (i >= 6 ? 4 : null), i == 6));
        }
        return TierRegistry.build(tiers, "test");
    }
}
