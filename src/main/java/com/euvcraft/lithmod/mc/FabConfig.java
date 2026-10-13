/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FabSettingsProvider;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * NeoForge server config binding for EUVCRAFT.
 * Follows the pattern used by Encoded Logistics and Arcforge.
 */
public class FabConfig implements FabSettingsProvider {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    // Config spec and values
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // General settings
    public static final ModConfigSpec.IntValue FOUP_CAPACITY;
    public static final ModConfigSpec.IntValue WAFERS_PER_BOULE;
    public static final ModConfigSpec.IntValue ROLLS_PER_LAYER;
    public static final ModConfigSpec.DoubleValue YIELD_MULTIPLIER;

    // Tier yield overrides (stored as list of "T5=90" strings)
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_YIELD_OVERRIDES;

    // Core count settings
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CORE_COUNT_OPTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMFORTABLE_CORE_OVERRIDES;
    public static final ModConfigSpec.DoubleValue CORE_COUNT_PENALTY_STEEPNESS;

    // Feature toggles
    public static final ModConfigSpec.BooleanValue TIER_6_ENABLED;

    // FE power multiplier
    public static final ModConfigSpec.DoubleValue FE_POWER_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        // General category
        BUILDER.comment("General fabrication settings").push("general");

        FOUP_CAPACITY = BUILDER
            .comment("Wafers a new FOUP (Front Opening Unified Pod) holds.")
            .defineInRange("foupCapacity", FabSettings.DEFAULT_FOUP_CAPACITY, 1, 64);

        WAFERS_PER_BOULE = BUILDER
            .comment("Wafers one silicon boule yields.")
            .defineInRange("wafersPerBoule", FabSettings.DEFAULT_WAFERS_PER_BOULE, 1, 64);

        ROLLS_PER_LAYER = BUILDER
            .comment("Yield rolls per layer cycle. Each roll is an independent chance to pass.")
            .defineInRange("rollsPerLayer", FabSettings.DEFAULT_ROLLS_PER_LAYER, 1, 8);

        YIELD_MULTIPLIER = BUILDER
            .comment("Scales each tier's default yield percentage. Overrides are used as given. Results are clamped to 1%..99%.")
            .defineInRange("yieldMultiplier", FabSettings.DEFAULT_YIELD_MULTIPLIER, 0.1, 2.0);

        TIER_YIELD_OVERRIDES = BUILDER
            .comment("Tier yield overrides as 'T5=90' (percent, 1-99). Replaces a tier's default yield from tier data. Invalid or duplicate entries are ignored.")
            .defineListAllowEmpty("tierYieldOverrides", List.of(), () -> "T5=90", entry -> entry instanceof String);

        BUILDER.pop();

        // Compute chip settings
        BUILDER.comment("Compute chip core count settings").push("compute");

        CORE_COUNT_OPTIONS = BUILDER
            .comment("Core counts offered at mask-making (from T6 onwards).")
            .defineList("coreCountOptions", FabSettings.DEFAULT_CORE_COUNT_OPTIONS, () -> 1, entry -> entry instanceof Integer value && value >= 1 && value <= 256);

        COMFORTABLE_CORE_OVERRIDES = BUILDER
            .comment("Comfortable core count overrides as 'T7=10'. Replaces a tier's comfortable core count from tier data. Invalid or duplicate entries are ignored.")
            .defineListAllowEmpty("comfortableCoreOverrides", List.of(), () -> "T7=10", entry -> entry instanceof String);

        CORE_COUNT_PENALTY_STEEPNESS = BUILDER
            .comment("Core count penalty steepness factor (k in the formula). 0 = no penalty, higher = steeper penalty for cores above comfortable count.")
            .defineInRange("coreCountPenaltySteepness", FabSettings.DEFAULT_CORE_COUNT_PENALTY_STEEPNESS, 0.0, 10.0);

        BUILDER.pop();

        // Feature toggles
        BUILDER.comment("Feature toggles").push("features");

        TIER_6_ENABLED = BUILDER
            .comment("When false, T6 (Multi-patterned) is skipped. Tiers go T5 -> T7.")
            .define("tier6Enabled", FabSettings.DEFAULT_TIER_6_ENABLED);

        BUILDER.pop();

        // FE power settings
        BUILDER.comment("FE power settings").push("power");

        FE_POWER_MULTIPLIER = BUILDER
            .comment("Global multiplier on every fab machine's FE cost (per-machine costs come in later tasks).")
            .defineInRange("fePowerMultiplier", FabSettings.DEFAULT_FE_POWER_MULTIPLIER, 0.0, 100.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // Runtime state
    private FabSettings currentSettings;
    private final java.util.List<Runnable> changeListeners = new java.util.ArrayList<>();

    public FabConfig() {
        // Register this as the provider
        FabSettingsProvider.setLogger(LOGGER);
        reloadSettings();
    }

    /**
     * Gets the ModConfigSpec for registration with NeoForge.
     */
    public static ModConfigSpec getSpec() {
        return SPEC;
    }

    /**
     * Called by NeoForge when the config is loaded or reloaded.
     */
    public void onConfigLoad(ModConfig config) {
        reloadSettings();
        LOGGER.info("EUVCRAFT config loaded: {}", currentSettings);
    }

    /**
     * Called when the config is reloaded (e.g. via /forge config).
     */
    public void onConfigReload() {
        reloadSettings();
        LOGGER.info("EUVCRAFT config reloaded: {}", currentSettings);
        notifyListeners();
    }

    private void reloadSettings() {
        // Parse tier yield overrides from list format
        Map<String, Integer> tierYieldOverrides = FabSettings.parseTierOverrideList(
            (List<String>) TIER_YIELD_OVERRIDES.get(),
            1, 99, "tierYieldOverrides"
        );

        // Parse comfortable core overrides from list format
        Map<String, Integer> comfortableCoreOverrides = FabSettings.parseTierOverrideList(
            (List<String>) COMFORTABLE_CORE_OVERRIDES.get(),
            1, 256, "comfortableCoreOverrides"
        );

        // Build settings
        this.currentSettings = new FabSettings.Builder()
            .foupCapacity(FOUP_CAPACITY.get())
            .wafersPerBoule(WAFERS_PER_BOULE.get())
            .rollsPerLayer(ROLLS_PER_LAYER.get())
            .yieldMultiplier(YIELD_MULTIPLIER.get())
            .tierYieldOverrides(tierYieldOverrides)
            .coreCountOptions((List<Integer>) CORE_COUNT_OPTIONS.get())
            .comfortableCoreOverrides(comfortableCoreOverrides)
            .coreCountPenaltySteepness(CORE_COUNT_PENALTY_STEEPNESS.get())
            .tier6Enabled(TIER_6_ENABLED.get())
            .fePowerMultiplier(FE_POWER_MULTIPLIER.get())
            .build();
    }

    @Override
    public FabSettings getSettings() {
        return currentSettings;
    }

    @Override
    public void addSettingsChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    @Override
    public void removeSettingsChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.error("Error in config change listener", e);
            }
        }
    }
}