/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable settings for the EUVCRAFT semiconductor fabrication system.
 * All values are validated and clamped to their allowed ranges on construction.
 * This class has no Minecraft or NeoForge dependencies.
 */
public record FabSettings(
    int foupCapacity,
    int wafersPerBoule,
    int chipsPerWafer,
    int maskUses,
    int resistPerLayer,
    int rollsPerLayer,
    double yieldMultiplier,
    Map<String, Integer> tierYieldOverrides,
    Map<String, Integer> layerTicksOverrides,
    List<Integer> coreCountOptions,
    Map<String, Integer> comfortableCoreOverrides,
    double coreCountPenaltySteepness,
    boolean tier6Enabled,
    boolean debugCommands,
    double fePowerMultiplier
) {

    // Default values
    public static final int DEFAULT_FOUP_CAPACITY = 25;
    public static final int DEFAULT_WAFERS_PER_BOULE = 25;
    public static final int DEFAULT_CHIPS_PER_WAFER = 4;
    public static final int DEFAULT_MASK_USES = 4;
    public static final int DEFAULT_RESIST_PER_LAYER = 1;
    public static final int DEFAULT_ROLLS_PER_LAYER = 1;
    public static final double DEFAULT_YIELD_MULTIPLIER = 1.0;
    public static final List<Integer> DEFAULT_CORE_COUNT_OPTIONS = List.of(1, 2, 4, 8, 12, 24);
    public static final double DEFAULT_CORE_COUNT_PENALTY_STEEPNESS = 2.0;
    public static final boolean DEFAULT_TIER_6_ENABLED = true;
    public static final boolean DEFAULT_DEBUG_COMMANDS = false;
    public static final double DEFAULT_FE_POWER_MULTIPLIER = 1.0;

    // Valid ranges
    private static final int MIN_FOUP_CAPACITY = 1;
    private static final int MAX_FOUP_CAPACITY = 64;
    private static final int MIN_WAFERS_PER_BOULE = 1;
    private static final int MAX_WAFERS_PER_BOULE = 64;
    private static final int MIN_CHIPS_PER_WAFER = 1;
    private static final int MAX_CHIPS_PER_WAFER = 64;
    private static final int MIN_MASK_USES = 1;
    private static final int MAX_MASK_USES = 1000;
    private static final int MIN_RESIST_PER_LAYER = 0;
    private static final int MAX_RESIST_PER_LAYER = 64;
    private static final int MIN_ROLLS_PER_LAYER = 1;
    private static final int MAX_ROLLS_PER_LAYER = 8;
    private static final double MIN_YIELD_MULTIPLIER = 0.1;
    private static final double MAX_YIELD_MULTIPLIER = 2.0;
    private static final int MIN_TIER_YIELD_OVERRIDE = 1;
    private static final int MAX_TIER_YIELD_OVERRIDE = 99;
    private static final int MIN_LAYER_TICKS_OVERRIDE = 1;
    private static final int MAX_LAYER_TICKS_OVERRIDE = 72000;
    private static final int MIN_CORE_COUNT = 1;
    private static final int MAX_CORE_COUNT = 256;
    private static final double MIN_CORE_COUNT_PENALTY_STEEPNESS = 0.0;
    private static final double MAX_CORE_COUNT_PENALTY_STEEPNESS = 10.0;
    private static final double MIN_FE_POWER_MULTIPLIER = 0.0;
    private static final double MAX_FE_POWER_MULTIPLIER = 100.0;

    private static final List<String> VALID_TIER_IDS = List.of("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8");

    /**
     * Creates a new FabSettings with validation and clamping.
     * Invalid override entries are logged and ignored.
     */
    public FabSettings {
        // Clamp simple values
        foupCapacity = clampInt(foupCapacity, MIN_FOUP_CAPACITY, MAX_FOUP_CAPACITY, DEFAULT_FOUP_CAPACITY, "foupCapacity");
        wafersPerBoule = clampInt(wafersPerBoule, MIN_WAFERS_PER_BOULE, MAX_WAFERS_PER_BOULE, DEFAULT_WAFERS_PER_BOULE, "wafersPerBoule");
        chipsPerWafer = clampInt(chipsPerWafer, MIN_CHIPS_PER_WAFER, MAX_CHIPS_PER_WAFER, DEFAULT_CHIPS_PER_WAFER, "chipsPerWafer");
        maskUses = clampInt(maskUses, MIN_MASK_USES, MAX_MASK_USES, DEFAULT_MASK_USES, "maskUses");
        resistPerLayer = clampInt(resistPerLayer, MIN_RESIST_PER_LAYER, MAX_RESIST_PER_LAYER, DEFAULT_RESIST_PER_LAYER, "resistPerLayer");
        rollsPerLayer = clampInt(rollsPerLayer, MIN_ROLLS_PER_LAYER, MAX_ROLLS_PER_LAYER, DEFAULT_ROLLS_PER_LAYER, "rollsPerLayer");
        yieldMultiplier = clampDouble(yieldMultiplier, MIN_YIELD_MULTIPLIER, MAX_YIELD_MULTIPLIER, DEFAULT_YIELD_MULTIPLIER, "yieldMultiplier");
        coreCountPenaltySteepness = clampDouble(coreCountPenaltySteepness, MIN_CORE_COUNT_PENALTY_STEEPNESS, MAX_CORE_COUNT_PENALTY_STEEPNESS, DEFAULT_CORE_COUNT_PENALTY_STEEPNESS, "coreCountPenaltySteepness");
        fePowerMultiplier = clampDouble(fePowerMultiplier, MIN_FE_POWER_MULTIPLIER, MAX_FE_POWER_MULTIPLIER, DEFAULT_FE_POWER_MULTIPLIER, "fePowerMultiplier");

        // Validate and clean coreCountOptions
        if (coreCountOptions == null || coreCountOptions.isEmpty()) {
            coreCountOptions = DEFAULT_CORE_COUNT_OPTIONS;
        } else {
            coreCountOptions = coreCountOptions.stream()
                .map(v -> clampInt(v, MIN_CORE_COUNT, MAX_CORE_COUNT, DEFAULT_CORE_COUNT_OPTIONS.get(0), "coreCountOptions entry"))
                .distinct()
                .sorted()
                .collect(Collectors.collectingAndThen(Collectors.toList(), List::copyOf));
            if (coreCountOptions.isEmpty()) {
                coreCountOptions = DEFAULT_CORE_COUNT_OPTIONS;
            }
        }

        // Parse and validate tierYieldOverrides (format: "T5=90")
        tierYieldOverrides = parseAndValidateTierOverrides(tierYieldOverrides, MIN_TIER_YIELD_OVERRIDE, MAX_TIER_YIELD_OVERRIDE, "tierYieldOverrides");

        // Parse and validate layerTicksOverrides (format: "T2=80")
        layerTicksOverrides = parseAndValidateTierOverrides(layerTicksOverrides, MIN_LAYER_TICKS_OVERRIDE, MAX_LAYER_TICKS_OVERRIDE, "layerTicksOverrides");

        // Parse and validate comfortableCoreOverrides (format: "T7=10")
        comfortableCoreOverrides = parseAndValidateTierOverrides(comfortableCoreOverrides, MIN_CORE_COUNT, MAX_CORE_COUNT, "comfortableCoreOverrides");
    }

    /**
     * Creates a FabSettings with default values.
     */
    public static FabSettings defaults() {
        return new FabSettings(
            DEFAULT_FOUP_CAPACITY,
            DEFAULT_WAFERS_PER_BOULE,
            DEFAULT_CHIPS_PER_WAFER,
            DEFAULT_MASK_USES,
            DEFAULT_RESIST_PER_LAYER,
            DEFAULT_ROLLS_PER_LAYER,
            DEFAULT_YIELD_MULTIPLIER,
            Map.of(),
            Map.of(),
            DEFAULT_CORE_COUNT_OPTIONS,
            Map.of(),
            DEFAULT_CORE_COUNT_PENALTY_STEEPNESS,
            DEFAULT_TIER_6_ENABLED,
            DEFAULT_DEBUG_COMMANDS,
            DEFAULT_FE_POWER_MULTIPLIER
        );
    }

    /**
     * Builder for creating FabSettings with custom values.
     */
    public static class Builder {
        private int foupCapacity = DEFAULT_FOUP_CAPACITY;
        private int wafersPerBoule = DEFAULT_WAFERS_PER_BOULE;
        private int chipsPerWafer = DEFAULT_CHIPS_PER_WAFER;
        private int maskUses = DEFAULT_MASK_USES;
        private int resistPerLayer = DEFAULT_RESIST_PER_LAYER;
        private int rollsPerLayer = DEFAULT_ROLLS_PER_LAYER;
        private double yieldMultiplier = DEFAULT_YIELD_MULTIPLIER;
        private Map<String, Integer> tierYieldOverrides = Map.of();
        private Map<String, Integer> layerTicksOverrides = Map.of();
        private List<Integer> coreCountOptions = DEFAULT_CORE_COUNT_OPTIONS;
        private Map<String, Integer> comfortableCoreOverrides = Map.of();
        private double coreCountPenaltySteepness = DEFAULT_CORE_COUNT_PENALTY_STEEPNESS;
        private boolean tier6Enabled = DEFAULT_TIER_6_ENABLED;
        private boolean debugCommands = DEFAULT_DEBUG_COMMANDS;
        private double fePowerMultiplier = DEFAULT_FE_POWER_MULTIPLIER;

        public Builder foupCapacity(int value) { this.foupCapacity = value; return this; }
        public Builder wafersPerBoule(int value) { this.wafersPerBoule = value; return this; }
        public Builder chipsPerWafer(int value) { this.chipsPerWafer = value; return this; }
        public Builder maskUses(int value) { this.maskUses = value; return this; }
        public Builder resistPerLayer(int value) { this.resistPerLayer = value; return this; }
        public Builder rollsPerLayer(int value) { this.rollsPerLayer = value; return this; }
        public Builder yieldMultiplier(double value) { this.yieldMultiplier = value; return this; }
        public Builder tierYieldOverrides(Map<String, Integer> value) { this.tierYieldOverrides = value; return this; }
        public Builder layerTicksOverrides(Map<String, Integer> value) { this.layerTicksOverrides = value; return this; }
        public Builder coreCountOptions(List<Integer> value) { this.coreCountOptions = value; return this; }
        public Builder comfortableCoreOverrides(Map<String, Integer> value) { this.comfortableCoreOverrides = value; return this; }
        public Builder coreCountPenaltySteepness(double value) { this.coreCountPenaltySteepness = value; return this; }
        public Builder tier6Enabled(boolean value) { this.tier6Enabled = value; return this; }
        public Builder debugCommands(boolean value) { this.debugCommands = value; return this; }
        public Builder fePowerMultiplier(double value) { this.fePowerMultiplier = value; return this; }

        public FabSettings build() {
            return new FabSettings(
                foupCapacity, wafersPerBoule, chipsPerWafer, maskUses, resistPerLayer, rollsPerLayer, yieldMultiplier,
                tierYieldOverrides, layerTicksOverrides, coreCountOptions, comfortableCoreOverrides,
                coreCountPenaltySteepness, tier6Enabled, debugCommands, fePowerMultiplier
            );
        }
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
            .foupCapacity(foupCapacity)
            .wafersPerBoule(wafersPerBoule)
            .chipsPerWafer(chipsPerWafer)
            .maskUses(maskUses)
            .resistPerLayer(resistPerLayer)
            .rollsPerLayer(rollsPerLayer)
            .yieldMultiplier(yieldMultiplier)
            .tierYieldOverrides(tierYieldOverrides)
            .layerTicksOverrides(layerTicksOverrides)
            .coreCountOptions(coreCountOptions)
            .comfortableCoreOverrides(comfortableCoreOverrides)
            .coreCountPenaltySteepness(coreCountPenaltySteepness)
            .tier6Enabled(tier6Enabled)
            .debugCommands(debugCommands)
            .fePowerMultiplier(fePowerMultiplier);
    }

    private static int clampInt(int value, int min, int max, int defaultValue, String name) {
        if (value < min || value > max) {
            FabSettingsProvider.logWarning("Config value '%s'=%d out of range [%d, %d], clamping to %d".formatted(name, value, min, max, 
                Math.max(min, Math.min(max, value))));
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }

    private static double clampDouble(double value, double min, double max, double defaultValue, String name) {
        if (value < min || value > max) {
            FabSettingsProvider.logWarning("Config value '%s'=%.2f out of range [%.2f, %.2f], clamping to %.2f".formatted(name, value, min, max,
                Math.max(min, Math.min(max, value))));
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }

    private static Map<String, Integer> parseAndValidateTierOverrides(Map<String, Integer> input, int minValue, int maxValue, String configName) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            String key = entry.getKey().trim().toUpperCase();
            Integer value = entry.getValue();

            if (!VALID_TIER_IDS.contains(key)) {
                FabSettingsProvider.logWarning("Invalid tier ID '%s' in %s (valid: %s), ignoring".formatted(key, configName, VALID_TIER_IDS));
                continue;
            }

            if (result.containsKey(key)) {
                FabSettingsProvider.logWarning("Duplicate tier ID '%s' in %s, ignoring".formatted(key, configName));
                continue;
            }

            int clamped = clampInt(value, minValue, maxValue, minValue, configName + "." + key);
            result.put(key, clamped);
        }

        return Map.copyOf(result);
    }

    public static Map<String, Integer> parseTierOverrideList(List<String> entries, int minValue, int maxValue, String configName) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                FabSettingsProvider.logWarning("Malformed entry '%s' in %s (expected format: T5=90), ignoring".formatted(entry, configName));
                continue;
            }

            String key = parts[0].trim().toUpperCase();
            String valueStr = parts[1].trim();

            int value;
            try {
                value = Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                FabSettingsProvider.logWarning("Invalid number '%s' in entry '%s' in %s, ignoring".formatted(valueStr, entry, configName));
                continue;
            }

            if (!VALID_TIER_IDS.contains(key)) {
                FabSettingsProvider.logWarning("Invalid tier ID '%s' in %s (valid: %s), ignoring".formatted(key, configName, VALID_TIER_IDS));
                continue;
            }

            if (result.containsKey(key)) {
                FabSettingsProvider.logWarning("Duplicate tier ID '%s' in %s, ignoring".formatted(key, configName));
                continue;
            }

            int clamped = clampInt(value, minValue, maxValue, minValue, configName + "." + key);
            result.put(key, clamped);
        }

        return Map.copyOf(result);
    }

    public static List<String> tierOverrideMapToList(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return map.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.toList());
    }

    /**
     * Returns the layer cycle length in ticks for the given tier, honoring a configured
     * override when present and falling back to the tier's data value otherwise.
     */
    public static int layerTicks(Tier tier, FabSettings settings) {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Integer override = settings.layerTicksOverrides().get(tier.id());
        return override != null ? override : tier.layerTicks();
    }
}