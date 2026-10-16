package com.euvcraft.lithmod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result a machine recipe produces, still as pure data.
 *
 * <p>Component values are kept as compact JSON text so that {@code core} needs no Minecraft
 * types. {@code MachineRecipe} in the {@code mc} package parses that text back and feeds the
 * whole object to {@code DataComponentType.VALUE_MAP_CODEC}, which is what turns it into
 * real component values and what makes an unknown component id fail loudly at load time.</p>
 *
 * @param itemId namespaced item id of the product
 * @param count how many the recipe produces; must be positive
 * @param components component id to the compact JSON text of its value
 */
public record MachineResult(String itemId, int count, Map<String, String> components) {

    public MachineResult {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(components, "components must not be null");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("result.item must not be blank");
        }
        if (!itemId.contains(":")) {
            throw new IllegalArgumentException(
                "result.item must be namespaced (namespace:path), got '" + itemId + "'");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("result.count must be positive, got " + count);
        }
        for (Map.Entry<String, String> entry : components.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("result.components has a blank component id");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                    "result.components['" + entry.getKey() + "'] has no value");
            }
        }
    }

    public static MachineResult of(String itemId, int count) {
        return new MachineResult(itemId, count, Map.of());
    }

    /** The raw JSON text of the component under {@code componentId}, if the recipe sets one. */
    public Optional<String> component(String componentId) {
        return Optional.ofNullable(components.get(componentId));
    }

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

    @Override
    public String toString() {
        return resultCount() + "x " + itemId + (components.isEmpty() ? "" : " +" + components.size() + " components");
    }

    /** How many the recipe produces; a small alias that reads better in log lines. */
    public int resultCount() {
        return count;
    }
}
