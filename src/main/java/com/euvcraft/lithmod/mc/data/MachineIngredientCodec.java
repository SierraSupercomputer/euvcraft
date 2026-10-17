package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minecraft serialization adapter; the core record stays independent of game libraries. */
public final class MachineIngredientCodec {
    private MachineIngredientCodec() { }

    public static final Codec<MachineIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("item").forGetter(ingredient -> ingredient.id()),
        Codec.INT.optionalFieldOf("count", 1).forGetter(MachineIngredient::count)
    ).apply(instance, MachineIngredient::item));

    public static final Codec<MachineIngredient> TAG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("tag").forGetter(ingredient -> ingredient.id()),
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
