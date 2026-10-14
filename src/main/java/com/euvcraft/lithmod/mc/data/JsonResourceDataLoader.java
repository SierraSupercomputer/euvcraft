package com.euvcraft.lithmod.mc.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Small generic reload listener for JSON-backed data that builds an immutable
 * registry/model. Reused by TierDataLoader now and later tasks.
 *
 * On every reload the prepared JSON is handed to {@code builder}; on failure the
 * last valid value is retained (or null on the very first, failed load).
 */
public abstract class JsonResourceDataLoader<T, R> extends SimpleJsonResourceReloadListener<T> {

    private final String directory;
    private final BiFunction<List<T>, String, R> builder;
    private final Logger logger;

    protected R current;

    protected JsonResourceDataLoader(String directory,
                                    Codec<T> codec,
                                    BiFunction<List<T>, String, R> builder,
                                    Logger logger) {
        super(codec, FileToIdConverter.json(directory));
        this.directory = directory;
        this.builder = builder;
        this.logger = logger;
    }

    @Override
    protected void apply(Map<Identifier, T> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        List<T> values = List.copyOf(prepared.values());
        String source = "data/lithmod/" + directory + "/";

        if (values.isEmpty()) {
            logger.warn("No {} data loaded from {}, keeping previous value", directory, source);
            if (current == null) {
                R fb = fallback();
                if (fb != null) {
                    current = fb;
                    logger.warn("Fell back to bundled defaults for {} after an empty first load", directory);
                }
            }
            return;
        }

        try {
            R next = builder.apply(values, source);
            current = next;
            logger.info("Loaded {} {} entries from {}", values.size(), directory, source);
        } catch (RuntimeException e) {
            logger.error("Failed to build {} from {}: {}", directory, source, e.getMessage());
            if (current == null) {
                R fb = fallback();
                if (fb != null) {
                    current = fb;
                    logger.warn("Fell back to bundled defaults for {} after a failed first load", directory);
                }
            }
        }
    }

    /**
     * Used when a first load fails (or is empty) so the registry is never null.
     * Override in loaders that have bundled defaults; the base returns null.
     */
    protected R fallback() {
        return null;
    }

    public R getCurrent() {
        return current;
    }

    protected String getDirectory() {
        return directory;
    }

}

