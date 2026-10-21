package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.recipe.HighBinningRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads {@code data/lithmod/high_binning/*.json} into an immutable {@link Registry}
 * on every server resource reload.
 *
 * <p>The JSON format contains one entry per file with fields:
 * {@code die_type}, {@code source_tier}, {@code target_tier},
 * {@code yield_multiplier}, {@code ticks_multiplier}, {@code energy_multiplier}.</p>
 *
 * <p><b>Two-stage error handling</b> mirrors the project's other loaders:
 * the reload listener parses first (item registries are not yet bound at
 * {@code AddServerReloadListenersEvent} time), and building the registry from
 * the parsed recipes is deferred to {@link #apply}. A parse failure never
 * replaces the prepared map, and a build failure logs the offending file and
 * keeps the previous registry in place.</p>
 *
 * <p><b>Reload semantics:</b></p>
 * <ul>
 *   <li>Missing config BEFORE the first reload → no binning (registry is empty, {@code getRegistry()} returns empty {@code Optional}).</li>
 *   <li>Valid empty reload (no files) → clears registry (not generic keep-old behavior).</li>
 *   <li>Failed/invalid reload → preserves previous valid registry, consistent with {@link JsonResourceDataLoader} policy.</li>
 *   <li>Duplicate family/source entries in the same reload → reject the whole prepared registry (fail closed).</li>
 * </ul>
 *
 * <p>No Java fallback recipe or default numbers are provided; configuration is
 * entirely data-driven. The loader exposes a pure {@link #buildRegistry(Map)} for
 * direct unit testing without registry bootstrap.</p>
 */
public final class HighBinningDataLoader
    extends JsonResourceDataLoader<HighBinningRecipe, HighBinningDataLoader.Registry> {

    /** The directory under data/lithmod/ containing high binning JSON files. */
    private static final String DIRECTORY = "high_binning";

    /** Minecraft serialization adapter; the record stays independent of game registries. */
    public static final Codec<HighBinningRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("die_type").forGetter(HighBinningRecipe::dieType),
        Codec.INT.fieldOf("source_tier").forGetter(r -> r.transition().sourceTier()),
        Codec.INT.fieldOf("target_tier").forGetter(r -> r.transition().targetTier()),
        Codec.DOUBLE.fieldOf("yield_multiplier").forGetter(r -> r.transition().yieldMultiplier()),
        Codec.DOUBLE.fieldOf("ticks_multiplier").forGetter(r -> r.transition().ticksMultiplier()),
        Codec.DOUBLE.fieldOf("energy_multiplier").forGetter(r -> r.transition().energyMultiplier())
    ).apply(instance, (dieType, sourceTier, targetTier, yieldMultiplier, ticksMultiplier, energyMultiplier) ->
        new HighBinningRecipe(dieType,
            new com.euvcraft.lithmod.core.HighBinningRules.Transition(
                sourceTier, targetTier, yieldMultiplier, ticksMultiplier, energyMultiplier
            )
        )));

    /** The live resolved registry, replaced only when a reload resolves cleanly. */
    private static volatile @Nullable Registry resolved;

    public HighBinningDataLoader() {
        super(DIRECTORY, CODEC, HighBinningDataLoader::buildRegistry, EUVCRAFTMod.LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        EUVCRAFTMod.LOGGER.debug("Registering HighBinningDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", DIRECTORY), new HighBinningDataLoader());
    }

    @Override
    protected void apply(Map<Identifier, HighBinningRecipe> prepared,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        String source = "data/lithmod/" + DIRECTORY + "/";

        if (prepared.isEmpty()) {
            // Valid empty reload: clear registry (not keep old behavior)
            Registry empty = buildRegistry(Map.of());
            this.current = empty;
            resolved = empty;
            EUVCRAFTMod.LOGGER.warn("No {} data loaded from {}, clearing registry", DIRECTORY, source);
            return;
        }

        // Delegate to super for parse-failure handling, then buildRegistry runs in entryBuilder
        super.apply(prepared, resourceManager, profiler);

        // On successful build, publish the registry
        Registry next = getCurrent();
        if (next != null) {
            resolved = next;
            EUVCRAFTMod.LOGGER.info("Loaded {} high binning recipes from {}", prepared.size(), source);
        }
    }

    /**
     * Builds an immutable registry from the parsed recipes.
     *
     * <p>Validates for duplicate family/source combinations and rejects the
     * entire prepared map if any duplicates are found. This method is pure
     * and suitable for direct unit testing.</p>
     *
     * @param entries parsed recipes keyed by file identifier, in resource-pack order
     * @return an immutable registry
     * @throws IllegalArgumentException if duplicate family/source pairs are detected
     */
    public static Registry buildRegistry(Map<Identifier, HighBinningRecipe> entries) {
        Map<String, Map<Integer, HighBinningRecipe>> byFamilyAndSource = new LinkedHashMap<>();

        // Iterate in file-id order for deterministic error reporting
        List<Map.Entry<Identifier, HighBinningRecipe>> ordered = new ArrayList<>(entries.entrySet());
        ordered.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Identifier, HighBinningRecipe> entry : ordered) {
            Identifier fileId = entry.getKey();
            HighBinningRecipe recipe = entry.getValue();
            String family = recipe.dieType();
            int sourceTier = recipe.transition().sourceTier();

            byFamilyAndSource.computeIfAbsent(family, k -> new LinkedHashMap<>());
            Map<Integer, HighBinningRecipe> bySource = byFamilyAndSource.get(family);

            if (bySource.containsKey(sourceTier)) {
                throw new IllegalArgumentException(
                    fileId + ": duplicate high binning recipe for family '" + family
                        + "' source tier " + sourceTier
                        + " (already defined in another file)");
            }
            bySource.put(sourceTier, recipe);
        }

        // Flatten to a single map for the registry
        Map<String, HighBinningRecipe> flat = new LinkedHashMap<>();
        for (Map<Integer, HighBinningRecipe> bySource : byFamilyAndSource.values()) {
            for (HighBinningRecipe recipe : bySource.values()) {
                String key = recipe.dieType() + ":" + recipe.transition().sourceTier();
                flat.put(key, recipe);
            }
        }

        return new Registry(flat);
    }

    /**
     * Returns the current resolved registry, or an empty Optional if no reload
     * has succeeded yet (or the last successful reload was empty).
     *
     * <p>Callers must treat an empty Optional as "no high binning configured"
     * and fail closed — do not guess or fall back to defaults.</p>
     */
    public static Optional<Registry> getRegistry() {
        return Optional.ofNullable(resolved);
    }

    /**
     * Immutable registry of high binning recipes, keyed by "family:sourceTier".
     */
    public static final class Registry {

        private final Map<String, HighBinningRecipe> recipes;

        private Registry(Map<String, HighBinningRecipe> recipes) {
            this.recipes = Map.copyOf(recipes);
        }

        /**
         * Returns the recipe for the given die family and source tier, if configured.
         *
         * @param dieType    the die family ("logic" or "compute")
         * @param sourceTier the source tier index
         * @return the matching recipe, or empty if not configured
         */
        public Optional<HighBinningRecipe> forSource(String dieType, int sourceTier) {
            String key = dieType + ":" + sourceTier;
            return Optional.ofNullable(recipes.get(key));
        }

        /**
         * Returns all loaded recipes.
         */
        public List<HighBinningRecipe> all() {
            return List.copyOf(recipes.values());
        }

        /**
         * Returns the number of loaded recipes.
         */
        public int size() {
            return recipes.size();
        }

        @Override
        public String toString() {
            return "HighBinningRegistry{" + recipes.keySet() + "}";
        }
    }
}