package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.TierRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupportLineDataLoaderFallbackTest {

    @Test
    void bundledFallbackIsNeverNull() {
        SupportLineDataLoader loader = new SupportLineDataLoader();
        GenerationMatrix fallback = loader.fallback();
        assertNotNull(fallback);
        assertEquals(5, fallback.lineIds().size());
    }
}
