package com.euvcraft.lithmod.mc.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Small generic reload listener for JSON-backed data that builds an immutable
 * registry/model. Reused by TierDataLoader now and later tasks.
 *
 * On every reload the prepared JSON is handed to {@code builder}; on failure the
 * last valid value is retained (or null on the very first, failed load).
 *
 * <p>{@link EntryBuilder} receives every parsed entry keyed by its file id, so a loader can
 * fail loudly and name the offending file. A loader that does not care about file ids wraps a
 * list-shaped builder in {@link #fromList}.</p>
 */
public abstract class JsonResourceDataLoader<T, R> extends SimpleJsonResourceReloadListener<T> {

    /** Builds a registry from every parsed entry, keeping each entry's file id. */
    @FunctionalInterface
    public interface EntryBuilder<T, R> {
        R build(Map<Identifier, T> entries, String source);
    }

    private final String directory;
    private final EntryBuilder<T, R> entryBuilder;
    private final Logger logger;

    protected R current;

    protected JsonResourceDataLoader(String directory,
                                    Codec<T> codec,
                                    EntryBuilder<T, R> entryBuilder,
                                    Logger logger) {
        super(codec, FileToIdConverter.json(directory));
        this.directory = directory;
        this.entryBuilder = entryBuilder;
        this.logger = logger;
    }

    /** Adapts a list-shaped builder to the id-preserving form. */
    protected static <T, R> EntryBuilder<T, R> fromList(BiFunction<List<T>, String, R> listBuilder) {
        return (entries, source) -> listBuilder.apply(List.copyOf(entries.values()), source);
    }

    @Override
    protected void apply(Map<Identifier, T> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        String source = "data/lithmod/" + directory + "/";

        if (prepared.isEmpty()) {
            logger.warn("No {} data loaded from {}, keeping previous value", directory, source);
            retainFallback("empty first load");
            return;
        }

        try {
            // Preserve the resource pack's iteration order. Map.copyOf would scramble it, and
            // the tier loader depends on file order to keep tier indices stable.
            R next = this.entryBuilder.build(Collections.unmodifiableMap(new LinkedHashMap<>(prepared)), source);
            current = next;
            logger.info("Loaded {} {} entries from {}", prepared.size(), directory, source);
        } catch (RuntimeException e) {
            logger.error("Failed to build {} from {}: {}", directory, source, e.getMessage());
            retainFallback("failed first load");
        }
    }

    private void retainFallback(String reason) {
        if (current == null) {
            R fallback = fallback();
            if (fallback != null) {
                current = fallback;
                logger.warn("Fell back to bundled defaults for {} after a {}", directory, reason);
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

