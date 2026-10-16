package com.euvcraft.lithmod.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * One ingredient of a machine recipe: either a single item id or an item tag id, plus how
 * many of it the recipe consumes.
 *
 * <p>Pure core data. Matching works on item id strings so it can be unit tested without a
 * game running; {@code MachineRecipeResolver} in the {@code mc} package is what turns ids
 * into real {@code ItemStack}s.</p>
 *
 * @param kind whether {@link #id()} names an item or a tag
 * @param id a namespaced item or item-tag id, e.g. {@code minecraft:iron_ingot}
 * @param count how many of the item this ingredient consumes; must be positive
 */
public record MachineIngredient(Kind kind, String id, int count) {

    /** Whether an ingredient names one item or a whole tag. */
    public enum Kind {
        ITEM,
        TAG
    }

    public MachineIngredient {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ingredient id must not be blank");
        }
        if (!id.contains(":")) {
            throw new IllegalArgumentException(
                "ingredient id must be namespaced (namespace:path), got '" + id + "'");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("ingredient count must be positive, got " + count);
        }
    }

    public static MachineIngredient item(String id, int count) {
        return new MachineIngredient(Kind.ITEM, id, count);
    }

    public static MachineIngredient tag(String id, int count) {
        return new MachineIngredient(Kind.TAG, id, count);
    }

    public boolean isTag() {
        return this.kind == Kind.TAG;
    }

    /**
     * Whether this ingredient accepts {@code stackItemId} in a stack of {@code stackCount}.
     *
     * <p>An {@link Kind#ITEM} ingredient matches only its exact id. A {@link Kind#TAG}
     * ingredient matches any id present in {@code tagMembers}, which the caller resolves from
     * the registry. Both require the stack to hold at least {@link #count}.</p>
     *
     * <p>Matching is exact on the id and never inspects item properties, so it is a pure
     * function of its arguments and fully covered by unit tests.</p>
     */
    public boolean matches(String stackItemId, int stackCount, Set<String> tagMembers) {
        Objects.requireNonNull(stackItemId, "stackItemId must not be null");
        Objects.requireNonNull(tagMembers, "tagMembers must not be null");
        if (stackItemId.isBlank() || stackCount < this.count) {
            return false;
        }
        if (this.kind == Kind.TAG) {
            return tagMembers.contains(stackItemId);
        }
        return this.id.equals(stackItemId);
    }

    @Override
    public String toString() {
        return this.count + "x" + this.kind.name().toLowerCase(Locale.ROOT) + "(" + this.id + ")";
    }

    public static final Codec<MachineIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("item").forGetter(ingredient -> ingredient.id),
        Codec.INT.optionalFieldOf("count", 1).forGetter(MachineIngredient::count)
    ).apply(instance, MachineIngredient::item));

    public static final Codec<MachineIngredient> TAG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("tag").forGetter(ingredient -> ingredient.id),
        Codec.INT.optionalFieldOf("count", 1).forGetter(MachineIngredient::count)
    ).apply(instance, MachineIngredient::tag));

    /**
     * Accepts either {@code {"item": ..., "count": ...}} or {@code {"tag": ..., "count": ...}},
     * so a datapack author picks the key that matches the shape of the ingredient. The item
     * form is tried first and falls through to the tag form when it does not apply.
     */
    public static final Codec<MachineIngredient> FLEXIBLE_CODEC =
        Codec.withAlternative(CODEC, TAG_CODEC);
}
