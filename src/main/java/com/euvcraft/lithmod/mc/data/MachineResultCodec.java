package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/** Minecraft serialization adapter; the core record stays independent of game libraries. */
public final class MachineResultCodec {
    private MachineResultCodec() { }

    /**
     * Reads one arbitrary JSON value and keeps it as compact text.
     *
     * <p>Written with explicit types because the inference through
     * {@code Codec.PASSTHROUGH.comapFlatMap} does not resolve on its own.</p>
     */
    private static final Codec<String> JSON_TEXT_CODEC =
        Codec.PASSTHROUGH.flatComapMap(
            (Dynamic<?> dynamic) -> dynamic.convert(JsonOps.INSTANCE).getValue().toString(),
            (String text) -> DataResult.success(
                new Dynamic<JsonElement>(JsonOps.INSTANCE, JsonParser.parseString(text))));

    public static final Codec<MachineResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("item").forGetter(MachineResult::itemId),
        Codec.INT.optionalFieldOf("count", 1).forGetter(MachineResult::count),
        Codec.unboundedMap(Codec.STRING, JSON_TEXT_CODEC)
            .optionalFieldOf("components", Map.of()).forGetter(MachineResult::components)
    ).apply(instance, MachineResult::new));


}
