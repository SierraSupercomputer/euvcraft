package com.euvcraft.lithmod.core;


import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a single-block fab machine, loaded from
 * {@code data/lithmod/machines/<id>.json}.
 *
 * <p>Every gameplay number lives here, so nothing about a machine is hard-coded in Java.
 * Construction validates the data and throws {@link IllegalArgumentException} naming the
 * offending field, which is what makes bad data fail loudly. Cross-reference checks that
 * need other registries (is this a real tier id?) belong in
 * {@code MachineDefinitionLoader}, which is the only place that knows the file name.</p>
 *
 * <p>Salvaged from the WIP branch, with the hard-coded {@code T1..T8} list removed: tier ids
 * are data, not code, and are validated against {@link TierRegistry} at load time.</p>
 */
public record MachineDefinition(
    String id,
    int energyCapacity,
    int maxReceive,
    int energyPerTick,
    Map<String, Integer> perTierEnergyPerTick,
    Map<String, Integer> params
) {

    /** Minimum energy per tick once {@link #energyPerTick} is above zero. */
    public static final int MIN_ENERGY_PER_TICK = 1;

    public MachineDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(perTierEnergyPerTick, "perTierEnergyPerTick must not be null");
        Objects.requireNonNull(params, "params must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (energyCapacity <= 0) {
            throw new IllegalArgumentException("energy_capacity must be positive, got " + energyCapacity);
        }
        if (maxReceive <= 0) {
            throw new IllegalArgumentException("max_receive must be positive, got " + maxReceive);
        }
        if (energyPerTick < 0) {
            throw new IllegalArgumentException("energy_per_tick must be non-negative, got " + energyPerTick);
        }

        for (Map.Entry<String, Integer> entry : perTierEnergyPerTick.entrySet()) {
            String tierId = entry.getKey();
            Integer value = entry.getValue();
            if (tierId == null || tierId.isBlank()) {
                throw new IllegalArgumentException("per_tier_energy_per_tick has a blank tier id");
            }
            if (value == null || value < 0) {
                throw new IllegalArgumentException(
                    "per_tier_energy_per_tick['" + tierId + "'] must be non-negative, got " + value);
            }
        }

        for (Map.Entry<String, Integer> entry : params.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("params has a blank key");
            }
            if (value == null || value <= 0) {
                throw new IllegalArgumentException(
                    "params['" + key + "'] must be a positive integer, got " + value);
            }
        }
    }



    /**
     * The energy this machine draws per tick.
     *
     * <p>A per-tier override wins over the base value. The result is scaled by the configured
     * FE power multiplier, rounded to the nearest whole FE, and never drops below
     * {@link #MIN_ENERGY_PER_TICK} when the base is above zero, so a small multiplier can
     * never make a machine free to run.</p>
     *
     * @param tier the tier the machine is working at, or {@code null} for the base value
     * @param settings the fab settings holding {@code fePowerMultiplier}
     * @return energy per tick, or 0 when the machine declares none
     */
    public int energyPerTick(Tier tier, FabSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");

        int base = this.energyPerTick;
        if (tier != null) {
            Integer override = perTierEnergyPerTick.get(tier.id());
            if (override != null) {
                base = override;
            }
        }

        if (base <= 0) {
            return 0;
        }

        int scaled = (int) Math.round(base * settings.fePowerMultiplier());
        return Math.max(MIN_ENERGY_PER_TICK, scaled);
    }

    /** The raw energy per tick, without the tier override and without the multiplier. */
    public int baseEnergyPerTick(Tier tier) {
        if (tier == null) {
            return this.energyPerTick;
        }
        Integer override = perTierEnergyPerTick.get(tier.id());
        return override != null ? override : this.energyPerTick;
    }

    /** The param under {@code name}, if the definition declares one. */
    public Optional<Integer> param(String name) {
        return Optional.ofNullable(params.get(name));
    }

    /** The param under {@code name}, or {@code defaultValue} when it is absent. */
    public int paramOrDefault(String name, int defaultValue) {
        return params.getOrDefault(name, defaultValue);
    }

    @Override
    public String toString() {
        return "MachineDefinition[" + id + "]";
    }
}
