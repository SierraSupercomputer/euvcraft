package com.euvcraft.lithmod.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a single-block fab machine, loaded from JSON.
 * All gameplay numbers are data-driven; invalid data fails loudly.
 */
public record MachineDefinition(
    String id,
    int energyCapacity,
    int maxReceive,
    int energyPerTick,
    Map<String, Integer> perTierEnergyPerTick,
    Map<String, Integer> params
) {
    public MachineDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(perTierEnergyPerTick, "perTierEnergyPerTick must not be null");
        Objects.requireNonNull(params, "params must not be null");

        if (energyCapacity <= 0) {
            throw new IllegalArgumentException("energyCapacity must be positive, got " + energyCapacity);
        }
        if (maxReceive <= 0) {
            throw new IllegalArgumentException("maxReceive must be positive, got " + maxReceive);
        }
        if (energyPerTick < 0) {
            throw new IllegalArgumentException("energyPerTick must be non-negative, got " + energyPerTick);
        }

        // Validate per-tier energy values
        for (Map.Entry<String, Integer> entry : perTierEnergyPerTick.entrySet()) {
            String tierId = entry.getKey();
            Integer value = entry.getValue();
            if (value == null || value < 0) {
                throw new IllegalArgumentException("perTierEnergyPerTick[" + tierId + "] must be non-negative, got " + value);
            }
            if (!VALID_TIER_IDS.contains(tierId.toUpperCase())) {
                throw new IllegalArgumentException("perTierEnergyPerTick contains invalid tier id: " + tierId + " (valid: " + VALID_TIER_IDS + ")");
            }
        }

        // Validate params are positive integers
        for (Map.Entry<String, Integer> entry : params.entrySet()) {
            Integer value = entry.getValue();
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("params[" + entry.getKey() + "] must be a positive integer, got " + value);
            }
        }
    }

    private static final java.util.List<String> VALID_TIER_IDS = java.util.List.of("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8");

    public static final Codec<MachineDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(MachineDefinition::id),
        Codec.INT.fieldOf("energy_capacity").forGetter(MachineDefinition::energyCapacity),
        Codec.INT.fieldOf("max_receive").forGetter(MachineDefinition::maxReceive),
        Codec.INT.fieldOf("energy_per_tick").forGetter(MachineDefinition::energyPerTick),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("per_tier_energy_per_tick", Map.of()).forGetter(MachineDefinition::perTierEnergyPerTick),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("params", Map.of()).forGetter(MachineDefinition::params)
    ).apply(instance, (id, energyCapacity, maxReceive, energyPerTick, perTierEnergyPerTick, params) ->
        new MachineDefinition(id, energyCapacity, maxReceive, energyPerTick, perTierEnergyPerTick, params)
    ));

    public static final Codec<java.util.List<MachineDefinition>> LIST_CODEC = CODEC.listOf();

    /**
     * Returns the energy per tick for the given tier, or the base value if no override exists.
     * The result is multiplied by the FE power multiplier from settings, rounded, and clamped to at least 1.
     *
     * @param tier the tier to get energy for, or null for base
     * @param settings the fabric settings
     * @return the energy cost per tick, at least 1 when base > 0
     */
    public int energyPerTick(Tier tier, FabSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");

        int base = energyPerTick;
        if (tier != null) {
            Integer override = perTierEnergyPerTick.get(tier.id());
            if (override != null) {
                base = override;
            }
        }

        if (base <= 0) {
            return 0;
        }

        double multiplied = base * settings.fePowerMultiplier();
        int result = (int) Math.round(multiplied);
        return Math.max(1, result);
    }

    /**
     * Returns a parameter value by name, or empty if not set.
     */
    public Optional<Integer> param(String name) {
        return Optional.ofNullable(params.get(name));
    }

    /**
     * Returns a parameter value by name, or a default if not set.
     */
    public int paramOrDefault(String name, int defaultValue) {
        return params.getOrDefault(name, defaultValue);
    }
}