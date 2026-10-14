package com.euvcraft.lithmod.mc.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonResourceDataLoaderReloadTest {

    @Test
    void invalidReloadKeepsPreviousData() {
        Logger logger = LoggerFactory.getLogger(JsonResourceDataLoaderReloadTest.class);
        AtomicBoolean fail = new AtomicBoolean(false);
        JsonResourceDataLoader<String, String> loader =
            new JsonResourceDataLoader<>("demo", Codec.STRING, (values, source) -> {
                if (fail.get()) {
                    throw new IllegalArgumentException("boom");
                }
                return "PREVIOUS:" + values;
            }, logger) {
                @Override
                protected String fallback() {
                    return "DEFAULTS";
                }
            };

        Map<Identifier, String> first = Map.of(
            Identifier.fromNamespaceAndPath("lithmod", "x"), "good");
        loader.apply(first, null, null);
        assertNotNull(loader.getCurrent());
        assertEquals("PREVIOUS:[good]", loader.getCurrent());

        fail.set(true);
        Map<Identifier, String> second = Map.of(
            Identifier.fromNamespaceAndPath("lithmod", "y"), "bad");
        loader.apply(second, null, null);
        assertEquals("PREVIOUS:[good]", loader.getCurrent());
    }
}
