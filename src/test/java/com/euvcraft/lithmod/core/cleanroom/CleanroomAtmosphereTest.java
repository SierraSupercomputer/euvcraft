/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CleanroomAtmosphere}: step, ppm, yieldPermille, sizeMultiplier.
 */
class CleanroomAtmosphereTest {

    private static final CleanroomRules RULES = CleanroomRules.defaults();
    private static final int SCALE = CleanroomAtmosphere.SCALE;

    @Nested
    @DisplayName("step - basic boundaries and clamping")
    class StepBoundaries {
        @Test
        @DisplayName("clamps to 0 at lower bound")
        void clampsAtZero() {
            int result = CleanroomAtmosphere.step(0, 256, 0, 0, 0, 0, true, false, RULES);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("clamps to SCALE at upper bound")
        void clampsAtScale() {
            int result = CleanroomAtmosphere.step(SCALE, 256, 100, 0, 0, 0, true, false, RULES);
            assertEquals(SCALE, result);
        }

        @Test
        @DisplayName("rejects cleanliness < 0")
        void rejectsNegativeCleanliness() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(-1, 256, 0, 0, 0, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects cleanliness > SCALE")
        void rejectsAboveScale() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE + 1, 256, 0, 0, 0, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects volume < 1")
        void rejectsVolumeBelowOne() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 0, 0, 0, 0, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects negative poweredFilters")
        void rejectsNegativeFilters() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 256, -1, 0, 0, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects negative ungowned")
        void rejectsNegativeUngowned() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 256, 0, -1, 0, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects negative gowned")
        void rejectsNegativeGowned() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 256, 0, 0, -1, 0, true, false, RULES));
        }

        @Test
        @DisplayName("rejects negative openDoors")
        void rejectsNegativeDoors() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 256, 0, 0, 0, -1, true, false, RULES));
        }

        @Test
        @DisplayName("rejects null rules")
        void rejectsNullRules() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.step(SCALE / 2, 256, 0, 0, 0, 0, true, false, null));
        }
    }

    @Nested
    @DisplayName("step - overflow safety with large values")
    class StepOverflow {
        @Test
        @DisplayName("handles large baselineLeak * volume without overflow")
        void largeBaselineVolume() {
            CleanroomRules bigRules = CleanroomRules.builder()
                .baselineLeak(1_000_000)
                .referenceVolume(1)
                .maxVolume(262144)
                .maxSpan(32)
                .filterMediaTicks(12000)
                .controllerFePerTick(100)
                .filterFePerTick(50)
                .ungownedLeak(2000)
                .gownedLeak(200)
                .doorLeak(2500)
                .powerLossLeak(30000)
                .breachLeak(60000)
                .filterStrength(5000)
                .outsideYieldPermille(500)
                .dirtyYieldPermille(500)
                .cleanPpm(1)
                .dirtyPpm(10001)
                .build();

            // volume = 262144, baselineLeak = 1_000_000, referenceVolume = 1
            // baselineDelta = 1_000_000 * 262144 / 1 = 262,144,000,000 (fits in long)
            int result = CleanroomAtmosphere.step(SCALE, 262144, 0, 0, 0, 0, true, false, bigRules);
            assertEquals(0, result); // should clamp to 0
        }

        @Test
        @DisplayName("handles large filterStrength * poweredFilters without overflow")
        void largeFilterStrength() {
            CleanroomRules bigRules = CleanroomRules.builder()
                .filterStrength(1_000_000)
                .referenceVolume(256)
                .maxVolume(16384)
                .maxSpan(32)
                .filterMediaTicks(12000)
                .controllerFePerTick(100)
                .filterFePerTick(50)
                .baselineLeak(800)
                .ungownedLeak(2000)
                .gownedLeak(200)
                .doorLeak(2500)
                .powerLossLeak(30000)
                .breachLeak(60000)
                .outsideYieldPermille(500)
                .dirtyYieldPermille(500)
                .cleanPpm(1)
                .dirtyPpm(10001)
                .build();

            // filterStrength = 1_000_000, poweredFilters = 100
            // filter contribution = 100,000,000 (fits in long)
            int result = CleanroomAtmosphere.step(0, 256, 100, 0, 0, 0, true, false, bigRules);
            assertEquals(SCALE, result); // should clamp to SCALE
        }
    }

    @Nested
    @DisplayName("step - volume multiplier applied exactly once")
    class VolumeMultiplier {
        @Test
        @DisplayName("baseline leak scales with volume/referenceVolume")
        void baselineScalesWithVolume() {
            // referenceVolume = 256, baselineLeak = 800
            // volume = 256 -> baselineDelta = 800 * 256 / 256 = 800
            int result256 = CleanroomAtmosphere.step(SCALE, 256, 0, 0, 0, 0, true, false, RULES);

            // volume = 512 -> baselineDelta = 800 * 512 / 256 = 1600
            int result512 = CleanroomAtmosphere.step(SCALE, 512, 0, 0, 0, 0, true, false, RULES);

            // volume = 128 -> baselineDelta = 800 * 128 / 256 = 400
            int result128 = CleanroomAtmosphere.step(SCALE, 128, 0, 0, 0, 0, true, false, RULES);

            assertEquals(SCALE - 800, result256);
            assertEquals(SCALE - 1600, result512);
            assertEquals(SCALE - 400, result128);
        }

        @Test
        @DisplayName("filter rate does NOT have volume scaling")
        void filterNoVolumeScaling() {
            // filterStrength = 5000, poweredFilters = 1
            // Should add exactly 5000 regardless of volume
            int result256 = CleanroomAtmosphere.step(0, 256, 1, 0, 0, 0, true, false, RULES);
            int result512 = CleanroomAtmosphere.step(0, 512, 1, 0, 0, 0, true, false, RULES);
            int result128 = CleanroomAtmosphere.step(0, 128, 1, 0, 0, 0, true, false, RULES);

            assertEquals(4200, result256);
            assertEquals(3400, result512);
            assertEquals(4600, result128);
        }
    }

    @Nested
    @DisplayName("step - filter only when powered && !breached")
    class FilterPowerBreach {
        @Test
        @DisplayName("filter adds when powered && !breached")
        void filterWhenPoweredNotBreached() {
            int result = CleanroomAtmosphere.step(0, 256, 1, 0, 0, 0, true, false, RULES);
            assertEquals(4200, result);
        }

        @Test
        @DisplayName("filter does NOT add when !powered")
        void noFilterWhenUnpowered() {
            int result = CleanroomAtmosphere.step(0, 256, 1, 0, 0, 0, false, false, RULES);
            // Power loss adds contamination, lowering cleanliness
            assertEquals(0, result);
        }

        @Test
        @DisplayName("filter does NOT add when breached")
        void noFilterWhenBreached() {
            int result = CleanroomAtmosphere.step(0, 256, 1, 0, 0, 0, true, true, RULES);
            // Breach adds contamination, lowering cleanliness
            assertEquals(0, result);
        }

        @Test
        @DisplayName("filter does NOT add when !powered && breached")
        void noFilterWhenUnpoweredAndBreached() {
            int result = CleanroomAtmosphere.step(0, 256, 1, 0, 0, 0, false, true, RULES);
            // Both powerLossLeak and breachLeak added
            assertEquals(30000 + 60000, result);
        }
    }

    @Nested
    @DisplayName("step - door vs breach difference")
    class DoorVsBreach {
        @Test
        @DisplayName("doorLeak subtracts per open door")
        void doorLeakSubtracts() {
            // openDoors = 1 -> subtract 2500
            int result = CleanroomAtmosphere.step(SCALE, 256, 0, 0, 0, 1, true, false, RULES);
            assertEquals(SCALE - 800 - 2500, result);

            // openDoors = 2 -> subtract 5000
            int result2 = CleanroomAtmosphere.step(SCALE, 256, 0, 0, 0, 2, true, false, RULES);
            assertEquals(SCALE - 800 - 5000, result2);
        }

        @Test
        @DisplayName("breachLeak subtracts once when breached")
        void breachLeakAddsOnce() {
            int result = CleanroomAtmosphere.step(SCALE, 256, 0, 0, 0, 0, true, true, RULES);
            // breachLeak = 60000 subtracted
            assertEquals(SCALE - 800 - 60000, result); // actual breach worsens cleanliness
        }
    }

    @Nested
    @DisplayName("step - gown difference")
    class GownDifference {
        @Test
        @DisplayName("ungownedLeak > gownedLeak")
        void ungownedWorseThanGowned() {
            int ungownedResult = CleanroomAtmosphere.step(SCALE, 256, 0, 1, 0, 0, true, false, RULES);
            int gownedResult = CleanroomAtmosphere.step(SCALE, 256, 0, 0, 1, 0, true, false, RULES);

            // ungownedLeak = 2000, gownedLeak = 200
            assertEquals(SCALE - 800 - 2000, ungownedResult);
            assertEquals(SCALE - 800 - 200, gownedResult);
            assertTrue(ungownedResult < gownedResult);
        }
    }

    @Nested
    @DisplayName("step - linear normalized updates")
    class LinearUpdates {
        @Test
        @DisplayName("multiple people/doors/filters accumulate linearly")
        void linearAccumulation() {
            // baseline: -800
            // 2 ungowned: -4000
            // 3 gowned: -600
            // 1 door: -2500
            // 2 filters (powered): +10000
            // net = -800 -4000 -600 -2500 + 10000 = +2100
            int result = CleanroomAtmosphere.step(SCALE / 2, 256, 2, 2, 3, 1, true, false, RULES);
            assertEquals(SCALE / 2 + 2100, result);
        }
    }

    @Nested
    @DisplayName("ppm - nonlinear conversion")
    class PpmTests {
        @Test
        @DisplayName("score = SCALE -> cleanPpm")
        void perfectScoreIsCleanPpm() {
            int result = CleanroomAtmosphere.ppm(SCALE, RULES);
            assertEquals(1, result); // cleanPpm = 1
        }

        @Test
        @DisplayName("score = 0 -> dirtyPpm")
        void zeroScoreIsDirtyPpm() {
            int result = CleanroomAtmosphere.ppm(0, RULES);
            assertEquals(10001, result); // dirtyPpm = 10001
        }

        @Test
        @DisplayName("score = SCALE/2 -> nonlinear interpolation")
        void midScoreNonlinear() {
            // ppm = cleanPpm + (dirtyPpm - cleanPpm) * (1 - 0.5)^2
            //     = 1 + 10000 * 0.25 = 2501
            int result = CleanroomAtmosphere.ppm(SCALE / 2, RULES);
            assertEquals(2501, result);
        }

        @Test
        @DisplayName("rejects score < 0")
        void rejectsNegativeScore() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.ppm(-1, RULES));
        }

        @Test
        @DisplayName("rejects score > SCALE")
        void rejectsAboveScale() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.ppm(SCALE + 1, RULES));
        }

        @Test
        @DisplayName("rejects null rules")
        void rejectsNullRules() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.ppm(SCALE / 2, null));
        }
    }

    @Nested
    @DisplayName("yieldPermille - nonlinear conversion")
    class YieldPermilleTests {
        @Test
        @DisplayName("score = SCALE -> 1000 permille")
        void perfectScoreIsMaxYield() {
            int result = CleanroomAtmosphere.yieldPermille(SCALE, RULES);
            assertEquals(1000, result);
        }

        @Test
        @DisplayName("score = 0 -> dirtyYieldPermille")
        void zeroScoreIsDirtyYield() {
            int result = CleanroomAtmosphere.yieldPermille(0, RULES);
            assertEquals(500, result); // dirtyYieldPermille = 500
        }

        @Test
        @DisplayName("score = SCALE/2 -> nonlinear interpolation")
        void midScoreNonlinear() {
            // yield = dirtyYieldPermille + (1000 - dirtyYieldPermille) * (0.5)^2
            //       = 500 + 500 * 0.25 = 625
            int result = CleanroomAtmosphere.yieldPermille(SCALE / 2, RULES);
            assertEquals(625, result);
        }

        @Test
        @DisplayName("rejects score < 0")
        void rejectsNegativeScore() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.yieldPermille(-1, RULES));
        }

        @Test
        @DisplayName("rejects score > SCALE")
        void rejectsAboveScale() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.yieldPermille(SCALE + 1, RULES));
        }

        @Test
        @DisplayName("rejects null rules")
        void rejectsNullRules() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.yieldPermille(SCALE / 2, null));
        }
    }

    @Nested
    @DisplayName("sizeMultiplier")
    class SizeMultiplierTests {
        @Test
        @DisplayName("referenceVolume / volume")
        void computesRatio() {
            double mult256 = CleanroomAtmosphere.sizeMultiplier(256, RULES);
            double mult512 = CleanroomAtmosphere.sizeMultiplier(512, RULES);
            double mult128 = CleanroomAtmosphere.sizeMultiplier(128, RULES);

            assertEquals(1.0, mult256, 1e-12);
            assertEquals(0.5, mult512, 1e-12);
            assertEquals(2.0, mult128, 1e-12);
        }

        @Test
        @DisplayName("rejects volume < 1")
        void rejectsVolumeBelowOne() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.sizeMultiplier(0, RULES));
        }

        @Test
        @DisplayName("rejects null rules")
        void rejectsNullRules() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomAtmosphere.sizeMultiplier(256, null));
        }
    }
}