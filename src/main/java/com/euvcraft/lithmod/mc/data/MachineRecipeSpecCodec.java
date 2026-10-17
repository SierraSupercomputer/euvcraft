package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minecraft serialization adapter; the core record stays independent of game libraries. */
public final class MachineRecipeSpecCodec {
    private MachineRecipeSpecCodec() { }

    public static final Codec<MachineRecipeSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("machine").forGetter(MachineRecipeSpec::machineId),
        MachineIngredientCodec.FLEXIBLE_CODEC.listOf().fieldOf("ingredients").forGetter(MachineRecipeSpec::ingredients),
        MachineResultCodec.CODEC.fieldOf("result").forGetter(MachineRecipeSpec::result),
        Codec.INT.fieldOf("ticks").forGetter(MachineRecipeSpec::ticks),
        Codec.INT.optionalFieldOf("energy_per_tick")
            .forGetter(spec -> Optional.ofNullable(spec.energyPerTickOverride()))
    ).apply(instance, (machine, ingredients, result, ticks, energyPerTick) ->
        new MachineRecipeSpec(machine, ingredients, result, ticks, energyPerTick.orElse(null))));

    public static final Codec<List<MachineRecipeSpec>> LIST_CODEC = CODEC.listOf();
}
