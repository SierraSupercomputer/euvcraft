package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    @Test
    void fallbackAliasGenerations() {
        GenerationMatrixDataLoader loader = new GenerationMatrixDataLoader();
        GenerationMatrix fb = loader.fallback();
        assertNotNull(fb);
    }
}
