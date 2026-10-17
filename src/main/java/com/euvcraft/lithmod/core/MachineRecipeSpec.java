package com.euvcraft.lithmod.core;


import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A machine recipe as it appears in {@code data/lithmod/machine_recipes/*.json}, kept as
 * pure data so that matching can be unit tested with no game running.
 *
 * <p>{@link #matches} is the whole of PART C's "ingredient matching is pure core logic":
 * given the item id and count currently in each input slot, and a resolver that expands
 * tag ids to member ids, it decides whether this recipe applies. The {@code mc} package
 * wraps this in {@code MachineRecipe}, which owns the real {@code ItemStack}s.</p>
 *
 * @param machineId the {@link MachineDefinition#id()} this recipe runs in
 * @param ingredients one entry per input slot, in slot order
 * @param result what the machine produces when the recipe completes
 * @param ticks how many ticks the recipe takes; must be positive
 * @param energyPerTickOverride FE per tick for this recipe, or {@code null} to use the machine
 *     definition's {@link MachineDefinition#energyPerTick}
 */
public record MachineRecipeSpec(
    String machineId,
    List<MachineIngredient> ingredients,
    MachineResult result,
    int ticks,
    Integer energyPerTickOverride
) {

    public MachineRecipeSpec {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(ingredients, "ingredients must not be null");
        Objects.requireNonNull(result, "result must not be null");

        if (machineId.isBlank()) {
            throw new IllegalArgumentException("machine must not be blank");
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("a machine recipe needs at least one ingredient");
        }
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive, got " + ticks);
        }
        if (energyPerTickOverride != null && energyPerTickOverride <= 0) {
            throw new IllegalArgumentException(
                "energy_per_tick must be positive when present, got " + energyPerTickOverride);
        }
    }

    public int inputSlotCount() {
        return ingredients.size();
    }

    /**
     * Whether the given input slots satisfy this recipe.
     *
     * <p>Pure: the result depends only on the arguments. Slot order matters and must line
     * up with {@link #ingredients()}; a slot holding fewer items than the ingredient asks
     * for fails, as does a tag ingredient whose resolved member set is empty.</p>
     *
     * @param stackItemIds the item id in each input slot, in slot order
     * @param stackCounts the count in each input slot, in slot order
     * @param tagMembers resolves a tag id to the set of item ids it contains
     */
    public boolean matches(List<String> stackItemIds, List<Integer> stackCounts, TagMemberResolver tagMembers) {
        Objects.requireNonNull(stackItemIds, "stackItemIds must not be null");
        Objects.requireNonNull(stackCounts, "stackCounts must not be null");
        Objects.requireNonNull(tagMembers, "tagMembers must not be null");

        if (stackItemIds.size() != ingredients.size() || stackCounts.size() != ingredients.size()) {
            return false;
        }
        for (int slot = 0; slot < ingredients.size(); slot++) {
            MachineIngredient ingredient = ingredients.get(slot);
            Set<String> members = ingredient.isTag()
                ? tagMembers.membersOf(ingredient.id())
                : Set.of();
            if (!ingredient.matches(stackItemIds.get(slot), stackCounts.get(slot), members)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the recipe has no energy override and must take its cost from the machine. */
    public boolean usesMachineEnergy() {
        return this.energyPerTickOverride == null;
    }

    /**
     * The FE per tick this recipe draws.
     *
     * @param machinePerTick what {@link MachineDefinition#energyPerTick} resolved to
     * @return the recipe override when present, otherwise {@code machinePerTick}
     */
    public int energyPerTick(int machinePerTick) {
        return this.energyPerTickOverride != null ? this.energyPerTickOverride : machinePerTick;
    }

    /** Resolves an item-tag id to the item ids it contains. */
    @FunctionalInterface
    public interface TagMemberResolver {
        Set<String> membersOf(String tagId);

        /** A resolver that knows no tags, so every tag ingredient fails to match. */
        static TagMemberResolver none() {
            return tagId -> Set.of();
        }
    }

    @Override
    public String toString() {
        return "MachineRecipeSpec[" + machineId + " -> " + result + " in " + ticks + " ticks]";
    }


}
