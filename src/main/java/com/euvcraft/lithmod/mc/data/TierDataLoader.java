package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/lithmod/tiers/*.json} through {@link TierCodec} and rebuilds
 * a {@link TierRegistry} on every server resource reload.
 */
public final class TierDataLoader extends JsonResourceDataLoader<Tier, TierRegistry> {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static TierRegistry currentRegistry;

    public TierDataLoader() {
        super("tiers", TierCodec.CODEC, fromList(TierRegistry::build), LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.debug("Registering TierDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "tiers"), new TierDataLoader());
    }

    @Override
    protected void apply(Map<Identifier, Tier> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        super.apply(prepared, resourceManager, profiler);
        TierRegistry next = getCurrent();
        if (next != null) {
            currentRegistry = next;
            LOGGER.info("Loaded {} tiers: {}", currentRegistry.all().size(),
                currentRegistry.all().stream()
                    .map(t -> t.id() + "(" + (int) t.defaultYieldPercent() + "%, " + t.layers() + " layers)")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
        }
    }

    @Override
    protected TierRegistry fallback() {
        java.util.List<Tier> defaults = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String resource = "/data/lithmod/tiers/t" + i + ".json";
            try (java.io.InputStream input = TierDataLoader.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IllegalStateException("Missing bundled tier file: " + resource);
                }
                com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
                Tier tier = TierCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                defaults.add(tier);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read bundled tier file: " + resource, e);
            }
        }
        return TierRegistry.build(defaults, "bundled default tiers");
    }

    public static TierRegistry getRegistry() {
        return currentRegistry;
    }
}
