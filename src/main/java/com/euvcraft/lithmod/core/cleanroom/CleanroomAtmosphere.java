/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

/**
 * Cleanroom atmosphere computation.
 * Each call to {@link #step} represents ONE second (20 loaded ticks).
 * This class has no Minecraft or NeoForge dependencies.
 */
public final class CleanroomAtmosphere {

    /** Score scale: 1,000,000 = perfectly clean, 0 = completely dirty. */
    public static final int SCALE = 1_000_000;

    private CleanroomAtmosphere() {
    }

    /**
     * Advances the cleanliness score by one second (20 loaded ticks).
     *
     * <p>Linear score update per second:</p>
     * <ul>
     *   <li>Subtract {@code baselineLeak * (volume / referenceVolume)} (volume multiplier applied exactly once)</li>
     *   <li>Subtract {@code ungownedLeak * ungowned}</li>
     *   <li>Subtract {@code gownedLeak * gowned}</li>
     *   <li>Subtract {@code doorLeak * openDoors}</li>
     *   <li>Add {@code powerLossLeak} if {@code !powered}</li>
     *   <li>Add {@code breachLeak} if {@code breached}</li>
     *   <li>Add {@code filterStrength * poweredFilters} only if {@code powered && !breached}</li>
     * </ul>
     * <p>No double volume scaling on filter rate. Safe overflow handling; negative counts rejected; volume must be >= 1.
     * Returns clamped score in [0, SCALE] without NaN.</p>
     *
     * @param cleanliness       current cleanliness score in [0, SCALE]
     * @param volume            room volume in blocks, must be >= 1
     * @param poweredFilters    number of powered filters
     * @param ungowned          number of ungowned people in the room
     * @param gowned            number of gowned people in the room
     * @param openDoors         number of open doors
     * @param powered           whether the cleanroom has power
     * @param breached          whether the cleanroom is breached (wall/door broken)
     * @param rules             cleanroom configuration rules
     * @return new cleanliness score clamped to [0, SCALE]
     * @throws IllegalArgumentException if inputs are invalid (negative counts, volume < 1, NaN score)
     */
    public static int step(int cleanliness, int volume, int poweredFilters, int ungowned, int gowned,
                           int openDoors, boolean powered, boolean breached, CleanroomRules rules) {
        if (cleanliness < 0 || cleanliness > SCALE) {
            throw new IllegalArgumentException("cleanliness must be in range [0, " + SCALE + "], got " + cleanliness);
        }
        if (volume < 1) {
            throw new IllegalArgumentException("volume must be >= 1, got " + volume);
        }
        if (poweredFilters < 0) {
            throw new IllegalArgumentException("poweredFilters must be >= 0, got " + poweredFilters);
        }
        if (ungowned < 0) {
            throw new IllegalArgumentException("ungowned must be >= 0, got " + ungowned);
        }
        if (gowned < 0) {
            throw new IllegalArgumentException("gowned must be >= 0, got " + gowned);
        }
        if (openDoors < 0) {
            throw new IllegalArgumentException("openDoors must be >= 0, got " + openDoors);
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }

        // Use long for intermediate computation to avoid overflow
        long score = cleanliness;

        // Baseline leak with volume multiplier (applied exactly once)
        // baselineLeak * (volume / referenceVolume)
        long baselineDelta = (long) rules.baselineLeak() * volume / rules.referenceVolume();
        score -= baselineDelta;

        // People leaks
        score -= (long) rules.ungownedLeak() * ungowned;
        score -= (long) rules.gownedLeak() * gowned;

        // Door leaks
        score -= (long) rules.doorLeak() * openDoors;

        // Power loss leak
        if (!powered) {
            score -= rules.powerLossLeak();
        }

        // Breach leak
        if (breached) {
            score -= rules.breachLeak();
        }

        // Filter contribution (only if powered and not breached)
        // No volume scaling on filter rate - fixed multiplier applied baseline exactly once
        if (powered && !breached) {
            score += (long) rules.filterStrength() * poweredFilters;
        }

        // Clamp to [0, SCALE]
        if (score < 0) {
            return 0;
        }
        if (score > SCALE) {
            return SCALE;
        }
        return (int) score;
    }

    /**
     * Converts a cleanliness score to parts per million (PPM) contamination.
     *
     * <p>Formula: {@code round(cleanPpm + (dirtyPpm - cleanPpm) * (1 - score/SCALE)^2)}</p>
     *
     * @param score current cleanliness score in [0, SCALE]
     * @param rules cleanroom configuration rules
     * @return PPM contamination level
     * @throws IllegalArgumentException if score is outside [0, SCALE] or rules is null
     */
    public static int ppm(int score, CleanroomRules rules) {
        if (score < 0 || score > SCALE) {
            throw new IllegalArgumentException("score must be in range [0, " + SCALE + "], got " + score);
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }

        double normalized = (double) score / SCALE;
        double oneMinusScore = 1.0 - normalized;
        double ppm = rules.cleanPpm() + (rules.dirtyPpm() - rules.cleanPpm()) * oneMinusScore * oneMinusScore;
        return (int) Math.round(ppm);
    }

    /**
     * Converts a cleanliness score to yield per mille (permille).
     *
     * <p>Formula: {@code round(dirtyYieldPermille + (1000 - dirtyYieldPermille) * (score/SCALE)^2)}</p>
     *
     * @param score current cleanliness score in [0, SCALE]
     * @param rules cleanroom configuration rules
     * @return yield in permille (1..1000)
     * @throws IllegalArgumentException if score is outside [0, SCALE] or rules is null
     */
    public static int yieldPermille(int score, CleanroomRules rules) {
        if (score < 0 || score > SCALE) {
            throw new IllegalArgumentException("score must be in range [0, " + SCALE + "], got " + score);
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }

        double normalized = (double) score / SCALE;
        double yield = rules.dirtyYieldPermille() + (1000.0 - rules.dirtyYieldPermille()) * normalized * normalized;
        return (int) Math.round(yield);
    }

    /**
     * Computes the size multiplier based on room volume.
     *
     * <p>The multiplier is {@code referenceVolume / volume}, so larger rooms have a smaller multiplier.
     * This affects how quickly the baseline leak degrades cleanliness.</p>
     *
     * @param volume room volume in blocks, must be >= 1
     * @param rules cleanroom configuration rules
     * @return size multiplier as double
     * @throws IllegalArgumentException if volume < 1 or rules is null
     */
    public static double sizeMultiplier(int volume, CleanroomRules rules) {
        if (volume < 1) {
            throw new IllegalArgumentException("volume must be >= 1, got " + volume);
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }
        return (double) rules.referenceVolume() / volume;
    }
}