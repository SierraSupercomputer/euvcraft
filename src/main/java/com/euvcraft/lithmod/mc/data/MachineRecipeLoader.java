package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code data/lithmod/machine_recipes/*.json} into a {@link Registry} on every server
 * resource reload.
 *
 * <p>This is deliberately not the vanilla recipe system: machine recipes live in their own
 * data pack directory with their own loader, so a datapack can retune a fab machine without
 * touching the crafting tree.</p>
 *
 * <p><b>Two stages.</b> The reload listener can only parse: it decodes each file to a pure
 * {@link MachineRecipeSpec} and hands the pair (spec, file id) to {@link #setSpecs}. Resolving
 * an item, an item tag or a component against the game registries is deferred to the first
 * {@link #getRegistry()} call, because at {@code AddServerReloadListenersEvent} time the item
 * registry is not yet bound and every tag lookup comes back empty. The first call happens on
 * the server thread after the world is up, where the registries are live.</p>
 *
 * <p><b>Keeping the previous recipes.</b> Extending the project's own
 * {@link JsonResourceDataLoader} gives both halves: a file that fails to parse never replaces
 * the parsed specs, and a resolution failure logs the offending file and leaves the previously
 * resolved registry in place.</p>
 */
public final class MachineRecipeLoader
    extends JsonResourceDataLoader<MachineRecipeSpec, MachineRecipeLoader.Parsed> {

    /** What the reload listener produces: the parsed specs with the file each came from. */
    public record Parsed(Map<Identifier, MachineRecipeSpec> specs) {
        public Parsed {
            specs = Map.copyOf(specs);
        }
    }

    /** The live parsed state, replaced only when a reload parses cleanly. */
    private static volatile Parsed currentSpecs;
    /** The resolved registry, rebuilt only when the specs change or a resolution fails. */
    private static volatile Registry resolved;
    /** The file ids behind {@link #resolved}, so a failure can be compared against them. */
    private static volatile Map<Identifier, MachineRecipeSpec> resolvedFrom;

    public MachineRecipeLoader() {
        super("machine_recipes", MachineRecipeSpec.CODEC,
            MachineRecipeLoader::parse, EUVCRAFTMod.LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        EUVCRAFTMod.LOGGER.debug("Registering MachineRecipeLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "machine_recipes"),
            new MachineRecipeLoader());
    }

    private static Parsed parse(Map<Identifier, MachineRecipeSpec> entries, String source) {
        Parsed parsed = new Parsed(entries);
        setSpecs(parsed);
        return parsed;
    }

    /** Publishes freshly parsed specs, invalidating the resolved registry. */
    private static void setSpecs(Parsed parsed) {
        currentSpecs = parsed;
        resolved = null;
        resolvedFrom = null;
    }

    /**
     * The registry of resolved recipes, resolving on first use.
     *
     * <p>A resolution failure logs the file and field and keeps whatever was resolved before,
     * so one bad file cannot wipe a working machine.</p>
     */
    public static Registry getRegistry() {
        Registry cached = resolved;
        if (cached != null) {
            return cached;
        }
        Parsed specs = currentSpecs;
        if (specs == null) {
            return null;
        }
        try {
            Registry built = buildResolved(specs.specs());
            resolved = built;
            resolvedFrom = specs.specs();
            EUVCRAFTMod.LOGGER.info("Resolved {} machine recipes: {}", built.size(),
                built.all().stream().map(MachineRecipe::toString)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
            return built;
        } catch (RuntimeException failure) {
            EUVCRAFTMod.LOGGER.error("Failed to resolve machine recipes, keeping the previous set: {}",
                failure.getMessage());
            if (resolvedFrom != null) {
                return resolved;
            }
            return null;
        }
    }

    /** Public entry point for tests: resolve a registry from already-parsed specs. */
    public static Registry build(List<MachineRecipeSpec> specs, String sourceDescription) {
        Map<Identifier, MachineRecipeSpec> entries = new LinkedHashMap<>();
        for (int index = 0; index < specs.size(); index++) {
            entries.put(Identifier.fromNamespaceAndPath("lithmod", "recipe_" + index), specs.get(index));
        }
        return buildResolved(entries);
    }

    /**
     * Resolves every recipe against the game registries, naming the file on failure.
     *
     * @throws IllegalArgumentException naming the source file and the offending field
     */
    public static Registry buildResolved(Map<Identifier, MachineRecipeSpec> entries) {
        Map<String, List<MachineRecipe>> byMachine = new LinkedHashMap<>();
        int count = 0;

        // Iterate in file-id order so recipe priority is deterministic rather than whatever
        // order the resource manager happened to hand the entries over in.
        List<Map.Entry<Identifier, MachineRecipeSpec>> ordered = new ArrayList<>(entries.entrySet());
        ordered.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Identifier, MachineRecipeSpec> entry : ordered) {
            Identifier fileId = entry.getKey();
            MachineRecipe resolved;
            try {
                resolved = MachineRecipe.resolve(entry.getValue(), fileId);
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException(failure.getMessage(), failure);
            }
            byMachine.computeIfAbsent(resolved.machineId(), key -> new ArrayList<>()).add(resolved);
            count++;
        }

        return new Registry(byMachine, count);
    }

    /** Immutable registry of resolved machine recipes, keyed by machine id. */
    public static final class Registry {

        private final Map<String, List<MachineRecipe>> byMachine;
        private final int size;

        private Registry(Map<String, List<MachineRecipe>> byMachine, int size) {
            Map<String, List<MachineRecipe>> copy = new LinkedHashMap<>();
            byMachine.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            this.byMachine = Map.copyOf(copy);
            this.size = size;
        }

        /** Every recipe for {@code machineId}, in a stable order. */
        public List<MachineRecipe> forMachine(String machineId) {
            return byMachine.getOrDefault(machineId, List.of());
        }

        /** Every loaded recipe. */
        public List<MachineRecipe> all() {
            return byMachine.values().stream().flatMap(List::stream).toList();
        }

        public boolean hasMachine(String machineId) {
            return byMachine.containsKey(machineId);
        }

        /** The first recipe of {@code machineId}, for tests and logging. */
        public Optional<MachineRecipe> firstFor(String machineId) {
            List<MachineRecipe> recipes = forMachine(machineId);
            return recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.get(0));
        }

        public int size() {
            return size;
        }

        @Override
        public String toString() {
            return "MachineRecipeRegistry" + byMachine.keySet() + " size=" + size;
        }
    }
}
