package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads {@code data/lithmod/support_recipes/*.json} into a {@link Registry} on every server
 * resource reload.
 *
 * <p>The support machines' own loader, deliberately separate from
 * {@link MachineRecipeLoader}: a support job has a duration, a water cost and an explicit
 * per-tick FE cost of its own, and the generic machine-recipe runner cannot express a job
 * that pins its cost to the recipe instead of the machine definition. The support block
 * entities never call the generic runner, so they get their own directory and their own
 * loader.</p>
 *
 * <p><b>Two stages</b>, the same split as {@link MachineRecipeLoader}: the reload listener
 * can only parse (the item registry is not yet bound at
 * {@code AddServerReloadListenersEvent} time), and resolving item ids against the game
 * registries is deferred to the first {@link #apply} result, which parse produces and which
 * the loader stores unchanged. Extending the project's own {@link JsonResourceDataLoader}
 * gives both halves of fail-closed error handling: a file that fails to parse never replaces
 * the parsed recipes, and a resolution failure logs the offending file and keeps the
 * previously resolved registry in place.</p>
 *
 * <p>The Boule Purifier and the later E-beam Mask Writer share this loader; delivering
 * e-beam recipes is later work, and none exist today.</p>
 */
public final class SupportRecipeLoader
    extends JsonResourceDataLoader<SupportRecipe, SupportRecipeLoader.Registry> {

    /** Minecraft serialization adapter; the record stays independent of game registries. */
    public static final Codec<SupportRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("machine").forGetter(SupportRecipe::machine),
        MachineIngredientCodec.FLEXIBLE_CODEC.fieldOf("input").forGetter(SupportRecipe::input),
        MachineIngredientCodec.FLEXIBLE_CODEC.optionalFieldOf("secondary")
            .forGetter(recipe -> Optional.ofNullable(recipe.secondary())),
        Codec.STRING.fieldOf("output").forGetter(SupportRecipe::outputItem),
        Codec.INT.optionalFieldOf("count", 1).forGetter(SupportRecipe::outputCount),
        Codec.INT.fieldOf("duration").forGetter(SupportRecipe::duration),
        Codec.INT.fieldOf("energy_per_tick").forGetter(SupportRecipe::energyPerTick),
        Codec.INT.fieldOf("water_mb").forGetter(SupportRecipe::waterMb)
    ).apply(instance, (machine, input, secondary, output, count, duration, energyPerTick, waterMb) ->
        new SupportRecipe(machine, input, secondary.orElse(null), output, count, duration,
            energyPerTick, waterMb)));

    /** The live resolved registry, replaced only when a reload resolves cleanly. */
    private static volatile Registry resolved;

    public SupportRecipeLoader() {
        super("support_recipes", CODEC, SupportRecipeLoader::buildCurrent, EUVCRAFTMod.LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        EUVCRAFTMod.LOGGER.debug("Registering SupportRecipeLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "support_recipes"),
            new SupportRecipeLoader());
    }

    private static Registry buildCurrent(Map<Identifier, SupportRecipe> entries, String source) {
        Registry built = buildResolved(entries);
        resolved = built;
        return built;
    }

    /**
     * The registry of resolved support recipes.
     *
     * <p>Every published registry was fully resolved at that reload, so callers never see a
     * half-resolved state. {@code null} when no reload has ever succeeded, which every
     * caller must treat as "no support machine may run": fail closed, not run on a guess.</p>
     */
    public static @Nullable Registry getRegistry() {
        return resolved;
    }

    /**
     * Resolves every recipe against the game registries, naming the file on failure.
     *
     * <p>The semantic rules (machine id, output ids, water) were already enforced by the
     * record's constructor; the missing half is the registry side: every named item must be
     * registered, or the whole load throws and
     * {@link JsonResourceDataLoader} keeps the previous registry.</p>
     *
     * @throws IllegalArgumentException naming the source file and the offending field
     */
    public static Registry buildResolved(Map<Identifier, SupportRecipe> entries) {
        Map<String, List<Resolved>> byMachine = new LinkedHashMap<>();
        int count = 0;

        // Iterate in file-id order so recipe priority is deterministic rather than whatever
        // order the resource manager happened to hand the entries over in.
        List<Map.Entry<Identifier, SupportRecipe>> ordered = new ArrayList<>(entries.entrySet());
        ordered.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Identifier, SupportRecipe> entry : ordered) {
            Identifier fileId = entry.getKey();
            SupportRecipe recipe = entry.getValue();
            Item inputItem = itemOf(recipe.input().id(), "input", fileId);
            Item outputItem = itemOf(recipe.outputItem(), "output", fileId);
            if (recipe.secondary() != null) {
                itemOf(recipe.secondary().id(), "secondary", fileId);
            }
            byMachine.computeIfAbsent(recipe.machine(), key -> new ArrayList<>())
                .add(new Resolved(recipe, inputItem, outputItem));
            count++;
        }

        return new Registry(byMachine, count);
    }

    private static Item itemOf(String rawId, String field, Identifier sourceFile) {
        Identifier parsed = Identifier.tryParse(rawId);
        if (parsed == null) {
            throw new IllegalArgumentException(
                sourceFile + ": " + field + " '" + rawId + "' is not a valid namespaced id");
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException(
                sourceFile + ": " + field + " '" + rawId + "' is not a registered item");
        }
        return item;
    }

    /** A recipe whose ids have been resolved against the live item registry. */
    public record Resolved(SupportRecipe recipe, Item inputItem, Item outputItem) {
        public Resolved {
            Objects.requireNonNull(recipe, "recipe must not be null");
            Objects.requireNonNull(inputItem, "inputItem must not be null");
            Objects.requireNonNull(outputItem, "outputItem must not be null");
        }

        public String machine() {
            return this.recipe.machine();
        }
    }

    /** Immutable registry of resolved support recipes, keyed by machine id. */
    public static final class Registry {

        private final Map<String, List<Resolved>> byMachine;
        private final int size;

        private Registry(Map<String, List<Resolved>> byMachine, int size) {
            Map<String, List<Resolved>> copy = new LinkedHashMap<>();
            byMachine.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            this.byMachine = Map.copyOf(copy);
            this.size = size;
        }

        /** Every resolved recipe for {@code machineId}, in a stable order. */
        public List<Resolved> forMachine(String machineId) {
            return this.byMachine.getOrDefault(machineId, List.of());
        }

        /** Every loaded recipe. */
        public List<Resolved> all() {
            return this.byMachine.values().stream().flatMap(List::stream).toList();
        }

        public boolean hasMachine(String machineId) {
            return this.byMachine.containsKey(machineId);
        }

        /** The first recipe of {@code machineId}, for the machines and for tests. */
        public Optional<Resolved> firstFor(String machineId) {
            List<Resolved> recipes = this.forMachine(machineId);
            return recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.get(0));
        }

        public int size() {
            return this.size;
        }

        @Override
        public String toString() {
            return "SupportRecipeRegistry" + this.byMachine.keySet() + " size=" + this.size;
        }
    }
}
