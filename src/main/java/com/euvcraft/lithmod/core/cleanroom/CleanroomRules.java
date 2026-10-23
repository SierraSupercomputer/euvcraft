/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

/**
 * Immutable configuration rules for a cleanroom.
 * All values are validated on construction. This class has no Minecraft or NeoForge dependencies.
 */
public record CleanroomRules(
    int maxSpan,
    int maxVolume,
    int referenceVolume,
    int controllerFePerTick,
    int filterFePerTick,
    int filterMediaTicks,
    int baselineLeak,
    int ungownedLeak,
    int gownedLeak,
    int doorLeak,
    int powerLossLeak,
    int breachLeak,
    int filterStrength,
    int outsideYieldPermille,
    int dirtyYieldPermille,
    int cleanPpm,
    int dirtyPpm
) {

    // Default values
    public static final int DEFAULT_MAX_SPAN = 32;
    public static final int DEFAULT_MAX_VOLUME = 16384;
    public static final int DEFAULT_REFERENCE_VOLUME = 256;
    public static final int DEFAULT_CONTROLLER_FE_PER_TICK = 100;
    public static final int DEFAULT_FILTER_FE_PER_TICK = 50;
    public static final int DEFAULT_FILTER_MEDIA_TICKS = 12000;
    public static final int DEFAULT_BASELINE_LEAK = 800;
    public static final int DEFAULT_UNGOWNED_LEAK = 2000;
    public static final int DEFAULT_GOWNED_LEAK = 200;
    public static final int DEFAULT_DOOR_LEAK = 2500;
    public static final int DEFAULT_POWER_LOSS_LEAK = 30000;
    public static final int DEFAULT_BREACH_LEAK = 60000;
    public static final int DEFAULT_FILTER_STRENGTH = 5000;
    public static final int DEFAULT_OUTSIDE_YIELD_PERMILLE = 500;
    public static final int DEFAULT_DIRTY_YIELD_PERMILLE = 500;
    public static final int DEFAULT_CLEAN_PPM = 1;
    public static final int DEFAULT_DIRTY_PPM = 10001;

    // Valid ranges
    private static final int MIN_MAX_SPAN = 3;
    private static final int MAX_MAX_SPAN = 64;
    private static final int MIN_MAX_VOLUME = 1;
    private static final int MAX_MAX_VOLUME = 262144;
    private static final int MIN_REFERENCE_VOLUME = 1;
    private static final int MIN_FILTER_MEDIA_TICKS = 1;
    private static final int MAX_RATE = 1_000_000;
    private static final int MIN_YIELD_PERMILLE = 1;
    private static final int MAX_YIELD_PERMILLE = 1000;

    /**
     * Creates a new CleanroomRules with validation.
     *
     * @throws IllegalArgumentException if any value is outside its allowed range
     */
    public CleanroomRules {
        if (maxSpan < MIN_MAX_SPAN || maxSpan > MAX_MAX_SPAN) {
            throw new IllegalArgumentException("maxSpan must be in range [" + MIN_MAX_SPAN + ", " + MAX_MAX_SPAN + "], got " + maxSpan);
        }
        if (maxVolume < MIN_MAX_VOLUME || maxVolume > MAX_MAX_VOLUME) {
            throw new IllegalArgumentException("maxVolume must be in range [" + MIN_MAX_VOLUME + ", " + MAX_MAX_VOLUME + "], got " + maxVolume);
        }
        if (referenceVolume < MIN_REFERENCE_VOLUME) {
            throw new IllegalArgumentException("referenceVolume must be >= " + MIN_REFERENCE_VOLUME + ", got " + referenceVolume);
        }
        if (filterMediaTicks < MIN_FILTER_MEDIA_TICKS) {
            throw new IllegalArgumentException("filterMediaTicks must be >= " + MIN_FILTER_MEDIA_TICKS + ", got " + filterMediaTicks);
        }
        if (controllerFePerTick < 0 || controllerFePerTick > MAX_RATE) {
            throw new IllegalArgumentException("controllerFePerTick must be in range [0, " + MAX_RATE + "], got " + controllerFePerTick);
        }
        if (filterFePerTick < 0 || filterFePerTick > MAX_RATE) {
            throw new IllegalArgumentException("filterFePerTick must be in range [0, " + MAX_RATE + "], got " + filterFePerTick);
        }
        if (baselineLeak < 0 || baselineLeak > MAX_RATE) {
            throw new IllegalArgumentException("baselineLeak must be in range [0, " + MAX_RATE + "], got " + baselineLeak);
        }
        if (ungownedLeak < 0 || ungownedLeak > MAX_RATE) {
            throw new IllegalArgumentException("ungownedLeak must be in range [0, " + MAX_RATE + "], got " + ungownedLeak);
        }
        if (gownedLeak < 0 || gownedLeak > MAX_RATE) {
            throw new IllegalArgumentException("gownedLeak must be in range [0, " + MAX_RATE + "], got " + gownedLeak);
        }
        if (doorLeak < 0 || doorLeak > MAX_RATE) {
            throw new IllegalArgumentException("doorLeak must be in range [0, " + MAX_RATE + "], got " + doorLeak);
        }
        if (powerLossLeak < 0 || powerLossLeak > MAX_RATE) {
            throw new IllegalArgumentException("powerLossLeak must be in range [0, " + MAX_RATE + "], got " + powerLossLeak);
        }
        if (breachLeak < 0 || breachLeak > MAX_RATE) {
            throw new IllegalArgumentException("breachLeak must be in range [0, " + MAX_RATE + "], got " + breachLeak);
        }
        if (filterStrength < 0 || filterStrength > MAX_RATE) {
            throw new IllegalArgumentException("filterStrength must be in range [0, " + MAX_RATE + "], got " + filterStrength);
        }
        if (outsideYieldPermille < MIN_YIELD_PERMILLE || outsideYieldPermille > MAX_YIELD_PERMILLE) {
            throw new IllegalArgumentException("outsideYieldPermille must be in range [" + MIN_YIELD_PERMILLE + ", " + MAX_YIELD_PERMILLE + "], got " + outsideYieldPermille);
        }
        if (dirtyYieldPermille < MIN_YIELD_PERMILLE || dirtyYieldPermille > MAX_YIELD_PERMILLE) {
            throw new IllegalArgumentException("dirtyYieldPermille must be in range [" + MIN_YIELD_PERMILLE + ", " + MAX_YIELD_PERMILLE + "], got " + dirtyYieldPermille);
        }
        if (cleanPpm < 0) {
            throw new IllegalArgumentException("cleanPpm must be >= 0, got " + cleanPpm);
        }
        if (dirtyPpm <= cleanPpm) {
            throw new IllegalArgumentException("dirtyPpm must be > cleanPpm, got cleanPpm=" + cleanPpm + ", dirtyPpm=" + dirtyPpm);
        }
    }

    /**
     * Returns a CleanroomRules with default values.
     */
    public static CleanroomRules defaults() {
        return new CleanroomRules(
            DEFAULT_MAX_SPAN,
            DEFAULT_MAX_VOLUME,
            DEFAULT_REFERENCE_VOLUME,
            DEFAULT_CONTROLLER_FE_PER_TICK,
            DEFAULT_FILTER_FE_PER_TICK,
            DEFAULT_FILTER_MEDIA_TICKS,
            DEFAULT_BASELINE_LEAK,
            DEFAULT_UNGOWNED_LEAK,
            DEFAULT_GOWNED_LEAK,
            DEFAULT_DOOR_LEAK,
            DEFAULT_POWER_LOSS_LEAK,
            DEFAULT_BREACH_LEAK,
            DEFAULT_FILTER_STRENGTH,
            DEFAULT_OUTSIDE_YIELD_PERMILLE,
            DEFAULT_DIRTY_YIELD_PERMILLE,
            DEFAULT_CLEAN_PPM,
            DEFAULT_DIRTY_PPM
        );
    }

    /**
     * Builder for creating CleanroomRules with custom values.
     */
    public static class Builder {
        private int maxSpan = DEFAULT_MAX_SPAN;
        private int maxVolume = DEFAULT_MAX_VOLUME;
        private int referenceVolume = DEFAULT_REFERENCE_VOLUME;
        private int controllerFePerTick = DEFAULT_CONTROLLER_FE_PER_TICK;
        private int filterFePerTick = DEFAULT_FILTER_FE_PER_TICK;
        private int filterMediaTicks = DEFAULT_FILTER_MEDIA_TICKS;
        private int baselineLeak = DEFAULT_BASELINE_LEAK;
        private int ungownedLeak = DEFAULT_UNGOWNED_LEAK;
        private int gownedLeak = DEFAULT_GOWNED_LEAK;
        private int doorLeak = DEFAULT_DOOR_LEAK;
        private int powerLossLeak = DEFAULT_POWER_LOSS_LEAK;
        private int breachLeak = DEFAULT_BREACH_LEAK;
        private int filterStrength = DEFAULT_FILTER_STRENGTH;
        private int outsideYieldPermille = DEFAULT_OUTSIDE_YIELD_PERMILLE;
        private int dirtyYieldPermille = DEFAULT_DIRTY_YIELD_PERMILLE;
        private int cleanPpm = DEFAULT_CLEAN_PPM;
        private int dirtyPpm = DEFAULT_DIRTY_PPM;

        public Builder maxSpan(int value) { this.maxSpan = value; return this; }
        public Builder maxVolume(int value) { this.maxVolume = value; return this; }
        public Builder referenceVolume(int value) { this.referenceVolume = value; return this; }
        public Builder controllerFePerTick(int value) { this.controllerFePerTick = value; return this; }
        public Builder filterFePerTick(int value) { this.filterFePerTick = value; return this; }
        public Builder filterMediaTicks(int value) { this.filterMediaTicks = value; return this; }
        public Builder baselineLeak(int value) { this.baselineLeak = value; return this; }
        public Builder ungownedLeak(int value) { this.ungownedLeak = value; return this; }
        public Builder gownedLeak(int value) { this.gownedLeak = value; return this; }
        public Builder doorLeak(int value) { this.doorLeak = value; return this; }
        public Builder powerLossLeak(int value) { this.powerLossLeak = value; return this; }
        public Builder breachLeak(int value) { this.breachLeak = value; return this; }
        public Builder filterStrength(int value) { this.filterStrength = value; return this; }
        public Builder outsideYieldPermille(int value) { this.outsideYieldPermille = value; return this; }
        public Builder dirtyYieldPermille(int value) { this.dirtyYieldPermille = value; return this; }
        public Builder cleanPpm(int value) { this.cleanPpm = value; return this; }
        public Builder dirtyPpm(int value) { this.dirtyPpm = value; return this; }

        public CleanroomRules build() {
            return new CleanroomRules(
                maxSpan, maxVolume, referenceVolume, controllerFePerTick, filterFePerTick,
                filterMediaTicks, baselineLeak, ungownedLeak, gownedLeak, doorLeak,
                powerLossLeak, breachLeak, filterStrength, outsideYieldPermille,
                dirtyYieldPermille, cleanPpm, dirtyPpm
            );
        }
    }

    public static Builder builder() { return new Builder(); }
}