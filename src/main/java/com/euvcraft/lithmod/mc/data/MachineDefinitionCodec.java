package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minecraft serialization adapter; the core record stays independent of game libraries. */
public final class MachineDefinitionCodec {
    private MachineDefinitionCodec() { }

    public static final Codec<MachineDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(MachineDefinition::id),
        Codec.INT.fieldOf("energy_capacity").forGetter(MachineDefinition::energyCapacity),
        Codec.INT.fieldOf("max_receive").forGetter(MachineDefinition::maxReceive),
        Codec.INT.fieldOf("energy_per_tick").forGetter(MachineDefinition::energyPerTick),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("per_tier_energy_per_tick", Map.of()).forGetter(MachineDefinition::perTierEnergyPerTick),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("params", Map.of()).forGetter(MachineDefinition::params)
    ).apply(instance, MachineDefinition::new));

    public static final Codec<java.util.List<MachineDefinition>> LIST_CODEC = CODEC.listOf();
}
