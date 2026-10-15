package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.RunSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Codec for {@link RunSpec} in the persistent form.
 *
 * <p>This lives in the mc package because it uses Mojang's {@link RecordCodecBuilder},
 * keeping the core package free of Minecraft/NeoForge dependencies.
 */
public final class RunSpecCodec {

    private RunSpecCodec() {
    }

    /** Codec for the persistent form of a run spec. */
    public static final Codec<RunSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("die_type").forGetter(RunSpec::dieType),
        Codec.INT.fieldOf("tierIndex").forGetter(RunSpec::tierIndex),
        Codec.INT.fieldOf("cores").forGetter(RunSpec::cores),
        Codec.DOUBLE.fieldOf("effectiveYield").forGetter(RunSpec::effectiveYield),
        Codec.INT.fieldOf("rollCount").forGetter(RunSpec::rollCount)
    ).apply(instance, RunSpec::new));
}