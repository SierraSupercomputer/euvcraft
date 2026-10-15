package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TierJsonDataTest {

    @Test
    void bundledTierJsonMatchesReferenceTable() throws Exception {
        List<Tier> loaded = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String path = "/data/lithmod/tiers/t" + i + ".json";
            try (InputStream input = getClass().getResourceAsStream(path)) {
                assertNotNull(input, "Missing bundled tier file: " + path);
                com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
                Tier tier = TierCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                loaded.add(tier);
            }
        }

        TierRegistry registry = TierRegistry.build(loaded, "test bundled JSON");
        assertEquals(8, registry.all().size());

        // Explicit reference-data comparison guards against silent JSON drift.
        Tier t1 = registry.getById("T1");
        assertEquals("Contact", t1.displayName());
        assertEquals("mercury arc lamp", t1.lightSource());
        assertEquals(5000, t1.resolutionNm());
        assertEquals(1000, t1.overlayNm());
        assertEquals(75, t1.throughputWph());
        assertEquals(1, t1.layers());
        assertEquals(600, t1.layerTicks());
        assertEquals(60.0, t1.defaultYieldPercent());
        assertEquals(1, t1.targetSpeedMhz());
        assertEquals(false, t1.optional());
    }
}