/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CleanroomRules}: defaults, validation, and boundary conditions.
 */
class CleanroomRulesTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {
        @Test
        @DisplayName("defaults() returns the specified default values")
        void defaultsReturnsSpecifiedValues() {
            CleanroomRules rules = CleanroomRules.defaults();

            assertEquals(32, rules.maxSpan());
            assertEquals(16384, rules.maxVolume());
            assertEquals(256, rules.referenceVolume());
            assertEquals(100, rules.controllerFePerTick());
            assertEquals(50, rules.filterFePerTick());
            assertEquals(12000, rules.filterMediaTicks());
            assertEquals(800, rules.baselineLeak());
            assertEquals(2000, rules.ungownedLeak());
            assertEquals(200, rules.gownedLeak());
            assertEquals(2500, rules.doorLeak());
            assertEquals(30000, rules.powerLossLeak());
            assertEquals(60000, rules.breachLeak());
            assertEquals(5000, rules.filterStrength());
            assertEquals(500, rules.outsideYieldPermille());
            assertEquals(500, rules.dirtyYieldPermille());
            assertEquals(1, rules.cleanPpm());
            assertEquals(10001, rules.dirtyPpm());
        }

        @Test
        @DisplayName("builder with no overrides equals defaults()")
        void builderEqualsDefaults() {
            CleanroomRules fromBuilder = CleanroomRules.builder().build();
            CleanroomRules fromDefaults = CleanroomRules.defaults();

            assertEquals(fromDefaults.maxSpan(), fromBuilder.maxSpan());
            assertEquals(fromDefaults.maxVolume(), fromBuilder.maxVolume());
            assertEquals(fromDefaults.referenceVolume(), fromBuilder.referenceVolume());
            assertEquals(fromDefaults.controllerFePerTick(), fromBuilder.controllerFePerTick());
            assertEquals(fromDefaults.filterFePerTick(), fromBuilder.filterFePerTick());
            assertEquals(fromDefaults.filterMediaTicks(), fromBuilder.filterMediaTicks());
            assertEquals(fromDefaults.baselineLeak(), fromBuilder.baselineLeak());
            assertEquals(fromDefaults.ungownedLeak(), fromBuilder.ungownedLeak());
            assertEquals(fromDefaults.gownedLeak(), fromBuilder.gownedLeak());
            assertEquals(fromDefaults.doorLeak(), fromBuilder.doorLeak());
            assertEquals(fromDefaults.powerLossLeak(), fromBuilder.powerLossLeak());
            assertEquals(fromDefaults.breachLeak(), fromBuilder.breachLeak());
            assertEquals(fromDefaults.filterStrength(), fromBuilder.filterStrength());
            assertEquals(fromDefaults.outsideYieldPermille(), fromBuilder.outsideYieldPermille());
            assertEquals(fromDefaults.dirtyYieldPermille(), fromBuilder.dirtyYieldPermille());
            assertEquals(fromDefaults.cleanPpm(), fromBuilder.cleanPpm());
            assertEquals(fromDefaults.dirtyPpm(), fromBuilder.dirtyPpm());
        }
    }

    @Nested
    @DisplayName("validation - maxSpan")
    class MaxSpanValidation {
        @Test
        @DisplayName("rejects maxSpan < 3")
        void rejectsBelowMin() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(2, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }

        @Test
        @DisplayName("accepts maxSpan = 3 (min)")
        void acceptsMin() {
            CleanroomRules rules = new CleanroomRules(3, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001);
            assertEquals(3, rules.maxSpan());
        }

        @Test
        @DisplayName("accepts maxSpan = 64 (max)")
        void acceptsMax() {
            CleanroomRules rules = new CleanroomRules(64, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001);
            assertEquals(64, rules.maxSpan());
        }

        @Test
        @DisplayName("rejects maxSpan > 64")
        void rejectsAboveMax() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(65, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }
    }

    @Nested
    @DisplayName("validation - maxVolume")
    class MaxVolumeValidation {
        @Test
        @DisplayName("rejects maxVolume < 1")
        void rejectsBelowMin() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 0, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }

        @Test
        @DisplayName("accepts maxVolume = 1 (min)")
        void acceptsMin() {
            CleanroomRules rules = new CleanroomRules(32, 1, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001);
            assertEquals(1, rules.maxVolume());
        }

        @Test
        @DisplayName("accepts maxVolume = 262144 (max)")
        void acceptsMax() {
            CleanroomRules rules = new CleanroomRules(32, 262144, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001);
            assertEquals(262144, rules.maxVolume());
        }

        @Test
        @DisplayName("rejects maxVolume > 262144")
        void rejectsAboveMax() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 262145, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }
    }

    @Nested
    @DisplayName("validation - referenceVolume and filterMediaTicks")
    class PositiveValidation {
        @Test
        @DisplayName("rejects referenceVolume < 1")
        void rejectsReferenceVolumeBelowOne() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 0, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }

        @Test
        @DisplayName("rejects filterMediaTicks < 1")
        void rejectsFilterMediaTicksBelowOne() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 0, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }
    }

    @Nested
    @DisplayName("validation - rate bounds (0..1_000_000)")
    class RateValidation {
        @Test
        @DisplayName("accepts 0 for all rate fields")
        void acceptsZero() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 0, 0, 12000, 0, 0, 0, 0, 0, 0, 0, 500, 500, 1, 10001);
            assertEquals(0, rules.controllerFePerTick());
            assertEquals(0, rules.filterFePerTick());
            assertEquals(0, rules.baselineLeak());
            assertEquals(0, rules.ungownedLeak());
            assertEquals(0, rules.gownedLeak());
            assertEquals(0, rules.doorLeak());
            assertEquals(0, rules.powerLossLeak());
            assertEquals(0, rules.breachLeak());
            assertEquals(0, rules.filterStrength());
        }

        @Test
        @DisplayName("accepts 1_000_000 for all rate fields")
        void acceptsMax() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 1_000_000, 1_000_000, 12000,
                1_000_000, 1_000_000, 1_000_000, 1_000_000,
                1_000_000, 1_000_000, 1_000_000, 500, 500, 1, 10001);
            assertEquals(1_000_000, rules.controllerFePerTick());
            assertEquals(1_000_000, rules.filterFePerTick());
            assertEquals(1_000_000, rules.baselineLeak());
            assertEquals(1_000_000, rules.ungownedLeak());
            assertEquals(1_000_000, rules.gownedLeak());
            assertEquals(1_000_000, rules.doorLeak());
            assertEquals(1_000_000, rules.powerLossLeak());
            assertEquals(1_000_000, rules.breachLeak());
            assertEquals(1_000_000, rules.filterStrength());
        }

        @Test
        @DisplayName("rejects negative controllerFePerTick")
        void rejectsNegativeControllerFe() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, -1, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }

        @Test
        @DisplayName("rejects > 1_000_000 for baselineLeak")
        void rejectsAboveMaxBaseline() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 1_000_001, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 1, 10001));
        }
    }

    @Nested
    @DisplayName("validation - yield permille (1..1000)")
    class YieldPermilleValidation {
        @Test
        @DisplayName("accepts outsideYieldPermille = 1 (min)")
        void acceptsOutsideMin() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 1, 500, 1, 10001);
            assertEquals(1, rules.outsideYieldPermille());
        }

        @Test
        @DisplayName("accepts outsideYieldPermille = 1000 (max)")
        void acceptsOutsideMax() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 1000, 500, 1, 10001);
            assertEquals(1000, rules.outsideYieldPermille());
        }

        @Test
        @DisplayName("rejects outsideYieldPermille < 1")
        void rejectsOutsideBelowMin() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 0, 500, 1, 10001));
        }

        @Test
        @DisplayName("rejects outsideYieldPermille > 1000")
        void rejectsOutsideAboveMax() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 1001, 500, 1, 10001));
        }

        @Test
        @DisplayName("accepts dirtyYieldPermille = 1 (min)")
        void acceptsDirtyMin() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 1, 1, 10001);
            assertEquals(1, rules.dirtyYieldPermille());
        }

        @Test
        @DisplayName("accepts dirtyYieldPermille = 1000 (max)")
        void acceptsDirtyMax() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 1000, 1, 10001);
            assertEquals(1000, rules.dirtyYieldPermille());
        }

        @Test
        @DisplayName("rejects dirtyYieldPermille < 1")
        void rejectsDirtyBelowMin() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 0, 1, 10001));
        }

        @Test
        @DisplayName("rejects dirtyYieldPermille > 1000")
        void rejectsDirtyAboveMax() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 1001, 1, 10001));
        }
    }

    @Nested
    @DisplayName("validation - PPM bounds (dirtyPpm > cleanPpm >= 0)")
    class PpmValidation {
        @Test
        @DisplayName("accepts cleanPpm = 0")
        void acceptsCleanZero() {
            CleanroomRules rules = new CleanroomRules(
                32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 0, 10001);
            assertEquals(0, rules.cleanPpm());
        }

        @Test
        @DisplayName("rejects cleanPpm < 0")
        void rejectsCleanNegative() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, -1, 10001));
        }

        @Test
        @DisplayName("rejects dirtyPpm <= cleanPpm")
        void rejectsDirtyNotGreaterThanClean() {
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 100, 100));
            assertThrows(IllegalArgumentException.class,
                () -> new CleanroomRules(32, 16384, 256, 100, 50, 12000, 800, 2000, 200, 2500, 30000, 60000, 5000, 500, 500, 100, 50));
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {
        @Test
        @DisplayName("builder allows overriding individual fields")
        void builderOverridesIndividualFields() {
            CleanroomRules rules = CleanroomRules.builder()
                .maxSpan(40)
                .maxVolume(20000)
                .referenceVolume(512)
                .controllerFePerTick(150)
                .filterFePerTick(75)
                .filterMediaTicks(24000)
                .baselineLeak(1000)
                .ungownedLeak(2500)
                .gownedLeak(300)
                .doorLeak(3000)
                .powerLossLeak(40000)
                .breachLeak(80000)
                .filterStrength(6000)
                .outsideYieldPermille(600)
                .dirtyYieldPermille(400)
                .cleanPpm(2)
                .dirtyPpm(20000)
                .build();

            assertEquals(40, rules.maxSpan());
            assertEquals(20000, rules.maxVolume());
            assertEquals(512, rules.referenceVolume());
            assertEquals(150, rules.controllerFePerTick());
            assertEquals(75, rules.filterFePerTick());
            assertEquals(24000, rules.filterMediaTicks());
            assertEquals(1000, rules.baselineLeak());
            assertEquals(2500, rules.ungownedLeak());
            assertEquals(300, rules.gownedLeak());
            assertEquals(3000, rules.doorLeak());
            assertEquals(40000, rules.powerLossLeak());
            assertEquals(80000, rules.breachLeak());
            assertEquals(6000, rules.filterStrength());
            assertEquals(600, rules.outsideYieldPermille());
            assertEquals(400, rules.dirtyYieldPermille());
            assertEquals(2, rules.cleanPpm());
            assertEquals(20000, rules.dirtyPpm());
        }
    }
}