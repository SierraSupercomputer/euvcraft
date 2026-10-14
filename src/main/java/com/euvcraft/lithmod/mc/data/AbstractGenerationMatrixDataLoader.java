package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base loader for the support-line generation matrix.
 */
public abstract class AbstractGenerationMatrixDataLoader
    extends JsonResourceDataLoader<Map<String, List<Integer>>, GenerationMatrix> {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    protected static final Codec<Map<String, List<Integer>>> MATRIX_CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT.listOf());

    private static GenerationMatrix matrix;

    protected AbstractGenerationMatrixDataLoader(String directory) {
        super(directory, MATRIX_CODEC, (values, source) -> {
            TierRegistry tiers = TierDataLoader.getRegistry();
            if (tiers == null) {
                tiers = new TierDataLoader().fallback();
            }
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("no support-line data found");
            }
            Map<String, List<Integer>> merged = new java.util.LinkedHashMap<>();
            for (Map<String, List<Integer>> v : values) {
                if (v != null) merged.putAll(v);
            }
            return GenerationMatrix.build(tiers, merged, source);
        }, LOGGER);
    }

    @Override
    protected void apply(java.util.Map<Identifier, Map<String, List<Integer>>> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        super.apply(prepared, resourceManager, profiler);
        GenerationMatrix next = getCurrent();
        if (next != null) {
            matrix = next;
            StringBuilder sb = new StringBuilder("Loaded support-line generations: ");
            for (String line : com.euvcraft.lithmod.core.SupportLines.ALL) {
                sb.append(line).append("=");
                List<Integer> gens = new ArrayList<>();
                for (int i = 1; i <= next.tierRegistry().all().size(); i++) {
                    gens.add(next.requiredGeneration(line, i));
                }
                sb.append(gens).append(", ");
            }
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            LOGGER.info(sb.toString());
        }
    }

    @Override
    protected GenerationMatrix fallback() {
        TierRegistry tiers = TierDataLoader.getRegistry();
        if (tiers == null) {
            tiers = new TierDataLoader().fallback();
        }
        String resource = "/data/lithmod/" + getDirectory() + "/matrix.json";
        try (InputStream input = AbstractGenerationMatrixDataLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                // try default.json
                String alt = "/data/lithmod/" + getDirectory() + "/default.json";
                try (InputStream in2 = AbstractGenerationMatrixDataLoader.class.getResourceAsStream(alt)) {
                    if (in2 == null) throw new IllegalStateException("Missing bundled file: " + resource);
                    return parseFallback(in2, tiers, getDirectory());
                }
            }
            return parseFallback(input, tiers, getDirectory());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read bundled support-line file: " + resource, e);
        }
    }

    private GenerationMatrix parseFallback(InputStream input, TierRegistry tiers, String directory) throws Exception {
        com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
            new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        Map<String, List<Integer>> parsed = MATRIX_CODEC.parse(
            com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        return GenerationMatrix.build(tiers, parsed, "bundled default support lines (" + directory + ")");
    }

    public static GenerationMatrix getMatrix() {
        return matrix;
    }

    public static GenerationMatrix getGenerationMatrix() {
        return matrix;
    }

    public static GenerationMatrix getCurrentMatrix() {
        return matrix;
    }
}
