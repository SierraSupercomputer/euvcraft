package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.core.MachineResult;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A machine recipe with its ids resolved to real game objects.
 *
 * <p>The matching decision stays in {@link MachineRecipeSpec}, which is pure core logic;
 * this class owns the resolved {@link ItemStack}s and tag member sets so
 * {@link MachineRecipeRunner} never deals with ids.</p>
 *
 * @param spec the pure-core recipe this was resolved from
 * @param ingredientCounts how many items each input slot must hold, in slot order
 * @param tagMembers item ids each tag ingredient accepts, keyed by tag id
 * @param resultItem the product item
 * @param resultCount how many the recipe produces
 * @param resultComponents the component patch to apply to the product
 */
public record MachineRecipe(
    MachineRecipeSpec spec,
    List<Integer> ingredientCounts,
    Map<String, Set<String>> tagMembers,
    Item resultItem,
    int resultCount,
    DataComponentPatch resultComponents
) {

    public MachineRecipe {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(ingredientCounts, "ingredientCounts must not be null");
        Objects.requireNonNull(tagMembers, "tagMembers must not be null");
        Objects.requireNonNull(resultItem, "resultItem must not be null");
        Objects.requireNonNull(resultComponents, "resultComponents must not be null");
    }

    /**
     * Resolves a pure-core spec against the live game registries.
     *
     * <p>Every failure names the source file and the offending field, so a bad datapack file
     * says which value is wrong rather than just that something is wrong. Resolution happens
     * here rather than in the codec because {@code SimpleJsonResourceReloadListener} decodes
     * with plain JSON ops, which cannot resolve registries or tags.</p>
     *
     * @throws IllegalArgumentException when an id does not resolve
     */
    public static MachineRecipe resolve(MachineRecipeSpec spec, Identifier sourceFile) {
        Map<String, Set<String>> tagMembers = new LinkedHashMap<>();
        List<Integer> counts = new ArrayList<>(spec.ingredients().size());

        for (MachineIngredient ingredient : spec.ingredients()) {
            counts.add(ingredient.count());
            if (ingredient.isTag()) {
                tagMembers.put(ingredient.id(), tagMembersOf(ingredient.id(), sourceFile));
            }
        }

        MachineResult result = spec.result();
        Item resultItem = itemOf(result.itemId(), "result.item", sourceFile);

        return new MachineRecipe(spec, List.copyOf(counts), Map.copyOf(tagMembers),
            resultItem, result.count(), componentsOf(result, sourceFile));
    }

    private static Item itemOf(String rawId, String field, Identifier sourceFile) {
        Identifier id = parse(rawId, field, sourceFile);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException(
                sourceFile + ": " + field + " '" + rawId + "' is not a registered item");
        }
        return item;
    }

    /** The item ids a tag ingredient accepts; fails loudly on an empty or unknown tag. */
    private static Set<String> tagMembersOf(String rawTagId, Identifier sourceFile) {
        Identifier tagId = parse(rawTagId, "ingredients.tag", sourceFile);
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        Set<String> members = new LinkedHashSet<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            Identifier memberId = BuiltInRegistries.ITEM.getKey(holder.value());
            if (memberId != null) {
                members.add(memberId.toString());
            }
        }
        if (members.isEmpty()) {
            throw new IllegalArgumentException(sourceFile + ": ingredients.tag '" + rawTagId
                + "' is empty or is not a registered item tag");
        }
        return Set.copyOf(members);
    }

    /**
     * The component patch for the product; fails loudly on a bad payload.
     *
     * <p>{@code DataComponentPatch.CODEC} is the same codec {@code ItemStack.CODEC} uses, so a
     * recipe component is spelled exactly the way an item stack component is, including the
     * {@code !name} form for removing one.</p>
     */
    private static DataComponentPatch componentsOf(MachineResult result, Identifier sourceFile) {
        if (result.components().isEmpty()) {
            return DataComponentPatch.EMPTY;
        }
        JsonObject object = new JsonObject();
        result.components().forEach((componentId, jsonText) -> {
            Identifier parsed = parse(componentId, "result.components", sourceFile);
            object.add(parsed.toString(), JsonParser.parseString(jsonText));
        });

        return DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, object)
            .getOrThrow(message -> new IllegalArgumentException(
                sourceFile + ": result.components is invalid: " + message));
    }

    private static Identifier parse(String raw, String field, Identifier sourceFile) {
        Identifier parsed = Identifier.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException(
                sourceFile + ": " + field + " '" + raw + "' is not a valid namespaced id");
        }
        return parsed;
    }

    /** The tag member set for {@code tagId}, or an empty set when this recipe has no such tag. */
    public Set<String> tagMembers(String tagId) {
        return tagMembers.getOrDefault(tagId, Set.of());
    }

    public int inputSlotCount() {
        return ingredientCounts.size();
    }

    public int ticks() {
        return spec.ticks();
    }

    public String machineId() {
        return spec.machineId();
    }

    /** The FE per tick, preferring the recipe override over the machine value. */
    public int energyPerTick(int machinePerTick) {
        return spec.energyPerTick(machinePerTick);
    }

    public boolean usesMachineEnergy() {
        return spec.usesMachineEnergy();
    }

    /** A fresh stack of the product with its component patch applied. */
    public ItemStack assemble() {
        ItemStack stack = new ItemStack(resultItem, resultCount);
        stack.applyComponents(this.resultComponents);
        return stack;
    }

    @Override
    public String toString() {
        return "MachineRecipe[" + spec.machineId() + " -> " + resultCount + "x " + resultItem
            + " in " + ticks() + " ticks]";
    }
}
