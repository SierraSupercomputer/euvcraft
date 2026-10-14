package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the support-line generation matrix from data/lithmod/support_lines/.
 */
public class SupportLineDataLoader extends JsonResourceDataLoader<Map<String, List<Integer>>, GenerationMatrix> {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String DIRECTORY = "support_lines";

    public static final Codec<Map<String, List<Integer>>> MATRIX_CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT.listOf());

    private static GenerationMatrix currentMatrix;

    public SupportLineDataLoader() {
        super(DIRECTORY, MATRIX_CODEC, (values, source) -> {
            TierRegistry tiers = TierDataLoader.getRegistry();
            if (tiers == null) {
                tiers = new TierDataLoader().fallback();
            }
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("no support-line data found");
            }
            Map<String, List<Integer>> merged = new LinkedHashMap<>();
            for (Map<String, List<Integer>> value : values) {
                if (value != null) {
                    merged.putAll(value);
                }
            }
            return GenerationMatrix.build(tiers, merged, source);
        }, LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.debug("Registering SupportLineDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", DIRECTORY), new SupportLineDataLoader());
    }

    @Override
    protected void apply(Map<Identifier, Map<String, List<Integer>>> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        super.apply(prepared, resourceManager, profiler);
        GenerationMatrix next = getCurrent();
        if (next != null) {
            currentMatrix = next;
            LOGGER.info("Loaded support-line generations: {}", next);
        }
    }

    @Override
    protected GenerationMatrix fallback() {
        TierRegistry tiers = TierDataLoader.getRegistry();
        if (tiers == null) {
            tiers = new TierDataLoader().fallback();
        }
        String resource = "/data/lithmod/" + DIRECTORY + "/matrix.json";
        try (InputStream input = SupportLineDataLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled matrix file: " + resource);
            }
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
                new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, List<Integer>> parsed = MATRIX_CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            return GenerationMatrix.build(tiers, parsed, "bundled default support lines");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read bundled matrix file: " + resource, e);
        }
    }

    public static GenerationMatrix getMatrix() {
        return currentMatrix;
    }
}
