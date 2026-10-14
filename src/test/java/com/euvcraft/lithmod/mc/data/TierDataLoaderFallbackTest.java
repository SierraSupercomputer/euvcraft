package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TierDataLoaderFallbackTest {

    @Test
    void failingBuilderOnFirstLoadFallsBackToDefaults() {
        Logger logger = LoggerFactory.getLogger(TierDataLoaderFallbackTest.class);
        JsonResourceDataLoader<String, String> loader =
            new JsonResourceDataLoader<>("demo", Codec.STRING, (values, source) -> {
                throw new IllegalArgumentException("boom");
            }, logger) {
                @Override
                protected String fallback() {
                    return "DEFAULTS";
                }
            };

        Map<Identifier, String> prepared = Map.of(
            Identifier.fromNamespaceAndPath("lithmod", "x"), "bad");

        loader.apply(prepared, null, null);
        assertNotNull(loader.getCurrent());
        assertEquals("DEFAULTS", loader.getCurrent());
    }

    @Test
    void bundledTierFallbackIsNeverNull() {
        TierDataLoader loader = new TierDataLoader();
        TierRegistry fallback = loader.fallback();
        assertNotNull(fallback);
        assertEquals(8, fallback.all().size());
    }
}