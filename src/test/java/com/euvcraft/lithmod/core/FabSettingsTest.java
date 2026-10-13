/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FabSettings}.
 * Tests validation, clamping, parsing, and round-trip behavior.
 */
class FabSettingsTest {

    @Nested
    @DisplayName("Default values")
    class Defaults {
        @Test
        void defaultsHaveCorrectValues() {
            FabSettings settings = FabSettings.defaults();

            assertEquals(FabSettings.DEFAULT_FOUP_CAPACITY, settings.foupCapacity());
            assertEquals(FabSettings.DEFAULT_WAFERS_PER_BOULE, settings.wafersPerBoule());
            assertEquals(FabSettings.DEFAULT_ROLLS_PER_LAYER, settings.rollsPerLayer());
            assertEquals(FabSettings.DEFAULT_YIELD_MULTIPLIER, settings.yieldMultiplier());
            assertTrue(settings.tierYieldOverrides().isEmpty());
            assertEquals(FabSettings.DEFAULT_CORE_COUNT_OPTIONS, settings.coreCountOptions());
            assertTrue(settings.comfortableCoreOverrides().isEmpty());
            assertEquals(FabSettings.DEFAULT_CORE_COUNT_PENALTY_STEEPNESS, settings.coreCountPenaltySteepness());
            assertEquals(FabSettings.DEFAULT_TIER_6_ENABLED, settings.tier6Enabled());
            assertEquals(FabSettings.DEFAULT_FE_POWER_MULTIPLIER, settings.fePowerMultiplier());
        }

        @Test
        void builderDefaultsMatchRecordDefaults() {
            FabSettings fromBuilder = FabSettings.builder().build();
            FabSettings fromRecord = FabSettings.defaults();

            assertEquals(fromRecord, fromBuilder);
        }
    }

    @Nested
    @DisplayName("Clamping of simple values")
    class Clamping {
        @ParameterizedTest
        @CsvSource({
            "foupCapacity, 0, 1",
            "foupCapacity, 65, 64",
            "wafersPerBoule, 0, 1",
            "wafersPerBoule, 65, 64",
            "rollsPerLayer, 0, 1",
            "rollsPerLayer, 9, 8"
        })
        void intValuesClamped(String field, int input, int expected) {
            FabSettings.Builder builder = FabSettings.builder();
            switch (field) {
                case "foupCapacity" -> builder.foupCapacity(input);
                case "wafersPerBoule" -> builder.wafersPerBoule(input);
                case "rollsPerLayer" -> builder.rollsPerLayer(input);
            }
            FabSettings settings = builder.build();

            int actual = switch (field) {
                case "foupCapacity" -> settings.foupCapacity();
                case "wafersPerBoule" -> settings.wafersPerBoule();
                case "rollsPerLayer" -> settings.rollsPerLayer();
                default -> throw new IllegalArgumentException();
            };
            assertEquals(expected, actual, "Field " + field + " should clamp " + input + " to " + expected);
        }

        @ParameterizedTest
        @CsvSource({
            "yieldMultiplier, 0.05, 0.10",
            "yieldMultiplier, 2.5, 2.00",
            "coreCountPenaltySteepness, -1.0, 0.00",
            "coreCountPenaltySteepness, 15.0, 10.00",
            "fePowerMultiplier, -5.0, 0.00",
            "fePowerMultiplier, 150.0, 100.00"
        })
        void doubleValuesClamped(String field, double input, double expected) {
            FabSettings.Builder builder = FabSettings.builder();
            switch (field) {
                case "yieldMultiplier" -> builder.yieldMultiplier(input);
                case "coreCountPenaltySteepness" -> builder.coreCountPenaltySteepness(input);
                case "fePowerMultiplier" -> builder.fePowerMultiplier(input);
            }
            FabSettings settings = builder.build();

            double actual = switch (field) {
                case "yieldMultiplier" -> settings.yieldMultiplier();
                case "coreCountPenaltySteepness" -> settings.coreCountPenaltySteepness();
                case "fePowerMultiplier" -> settings.fePowerMultiplier();
                default -> throw new IllegalArgumentException();
            };
            assertEquals(expected, actual, 0.001, "Field " + field + " should clamp " + input + " to " + expected);
        }
    }

    @Nested
    @DisplayName("Core count options validation")
    class CoreCountOptions {
        @Test
        void nullOrEmptyDefaultsToStandardOptions() {
            FabSettings settings1 = FabSettings.builder().coreCountOptions(null).build();
            FabSettings settings2 = FabSettings.builder().coreCountOptions(List.of()).build();

            assertEquals(FabSettings.DEFAULT_CORE_COUNT_OPTIONS, settings1.coreCountOptions());
            assertEquals(FabSettings.DEFAULT_CORE_COUNT_OPTIONS, settings2.coreCountOptions());
        }

        @Test
        void duplicatesRemovedAndSorted() {
            FabSettings settings = FabSettings.builder()
                .coreCountOptions(List.of(8, 1, 4, 1, 24, 2, 8, 12))
                .build();

            assertEquals(List.of(1, 2, 4, 8, 12, 24), settings.coreCountOptions());
        }

        @Test
        void outOfRangeValuesClamped() {
            FabSettings settings = FabSettings.builder()
                .coreCountOptions(List.of(0, 300, 4))
                .build();

            // 0 -> 1 (min), 300 -> 256 (max), 4 stays 4
            assertEquals(List.of(1, 4, 256), settings.coreCountOptions());
        }
    }

    @Nested
    @DisplayName("Tier yield overrides parsing")
    class TierYieldOverrides {
        @Test
        void emptyMapStaysEmpty() {
            FabSettings settings = FabSettings.builder().tierYieldOverrides(Map.of()).build();
            assertTrue(settings.tierYieldOverrides().isEmpty());
        }

        @Test
        void validEntriesParsedCorrectly() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("T5", 90, "T8", 75))
                .build();

            assertEquals(2, settings.tierYieldOverrides().size());
            assertEquals(90, settings.tierYieldOverrides().get("T5"));
            assertEquals(75, settings.tierYieldOverrides().get("T8"));
        }

        @Test
        void keysAreCaseInsensitiveAndNormalized() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("t5", 90, "T8", 75))
                .build();

            assertEquals(90, settings.tierYieldOverrides().get("T5"));
            assertEquals(75, settings.tierYieldOverrides().get("T8"));
        }

        @Test
        void invalidTierIdsIgnored() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("T5", 90, "T9", 80, "INVALID", 70))
                .build();

            assertEquals(1, settings.tierYieldOverrides().size());
            assertEquals(90, settings.tierYieldOverrides().get("T5"));
        }

        @Test
        void duplicateKeysIgnored() {
            // Test duplicate handling via list parsing (which is what NeoForge config uses)
            Map<String, Integer> result = FabSettings.parseTierOverrideList(
                List.of("T5=90", "T5=85"), 1, 99, "test");
            assertEquals(1, result.size());
            assertEquals(90, result.get("T5")); // first one wins
        }

        @Test
        void valuesClampedToRange() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("T1", 0, "T2", 100, "T3", 50))
                .build();

            assertEquals(1, settings.tierYieldOverrides().get("T1"));   // clamped to min 1
            assertEquals(99, settings.tierYieldOverrides().get("T2"));  // clamped to max 99
            assertEquals(50, settings.tierYieldOverrides().get("T3"));  // unchanged
        }
    }

    @Nested
    @DisplayName("Comfortable core overrides parsing")
    class ComfortableCoreOverrides {
        @Test
        void validEntriesParsedCorrectly() {
            FabSettings settings = FabSettings.builder()
                .comfortableCoreOverrides(Map.of("T6", 6, "T8", 16))
                .build();

            assertEquals(2, settings.comfortableCoreOverrides().size());
            assertEquals(6, settings.comfortableCoreOverrides().get("T6"));
            assertEquals(16, settings.comfortableCoreOverrides().get("T8"));
        }

        @Test
        void valuesClampedToRange() {
            FabSettings settings = FabSettings.builder()
                .comfortableCoreOverrides(Map.of("T6", 0, "T7", 300))
                .build();

            assertEquals(1, settings.comfortableCoreOverrides().get("T6"));   // clamped to min 1
            assertEquals(256, settings.comfortableCoreOverrides().get("T7")); // clamped to max 256
        }
    }

    @Nested
    @DisplayName("List-based parsing (for NeoForge config)")
    class ListParsing {
        @Test
        void parseTierOverrideListValidEntries() {
            List<String> entries = List.of("T5=90", "T8=75");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(2, result.size());
            assertEquals(90, result.get("T5"));
            assertEquals(75, result.get("T8"));
        }

        @Test
        void parseTierOverrideListCaseInsensitive() {
            List<String> entries = List.of("t5=90", "T8=75");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(90, result.get("T5"));
            assertEquals(75, result.get("T8"));
        }

        @Test
        void parseTierOverrideListWhitespaceHandled() {
            List<String> entries = List.of(" T5 = 90 ", "T8 = 75 ");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(90, result.get("T5"));
            assertEquals(75, result.get("T8"));
        }

        @Test
        void parseTierOverrideListMalformedIgnored() {
            List<String> entries = List.of("T5=90", "INVALID", "T8=75=extra", "T9=80");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(1, result.size());
            assertEquals(90, result.get("T5"));
        }

        @Test
        void parseTierOverrideListNonNumericIgnored() {
            List<String> entries = List.of("T5=90", "T8=abc");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(1, result.size());
            assertEquals(90, result.get("T5"));
        }

        @Test
        void parseTierOverrideListDuplicatesIgnored() {
            List<String> entries = List.of("T5=90", "T5=85", "T8=75");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(2, result.size());
            assertEquals(90, result.get("T5")); // first one wins
            assertEquals(75, result.get("T8"));
        }

        @Test
        void parseTierOverrideListValuesClamped() {
            List<String> entries = List.of("T1=0", "T2=100", "T3=50");
            Map<String, Integer> result = FabSettings.parseTierOverrideList(entries, 1, 99, "test");

            assertEquals(1, result.get("T1"));
            assertEquals(99, result.get("T2"));
            assertEquals(50, result.get("T3"));
        }

        @Test
        void parseTierOverrideListEmptyOrNullReturnsEmpty() {
            assertTrue(FabSettings.parseTierOverrideList(null, 1, 99, "test").isEmpty());
            assertTrue(FabSettings.parseTierOverrideList(List.of(), 1, 99, "test").isEmpty());
            assertTrue(FabSettings.parseTierOverrideList(List.of("", "  "), 1, 99, "test").isEmpty());
        }
    }

    @Nested
    @DisplayName("Round-trip: map to list and back")
    class RoundTrip {
        @Test
        void tierYieldOverridesRoundTrip() {
            Map<String, Integer> original = Map.of("T5", 90, "T8", 75);
            List<String> asList = FabSettings.tierOverrideMapToList(original);
            Map<String, Integer> parsed = FabSettings.parseTierOverrideList(asList, 1, 99, "test");

            assertEquals(original, parsed);
        }

        @Test
        void comfortableCoreOverridesRoundTrip() {
            Map<String, Integer> original = Map.of("T6", 6, "T8", 16);
            List<String> asList = FabSettings.tierOverrideMapToList(original);
            Map<String, Integer> parsed = FabSettings.parseTierOverrideList(asList, 1, 256, "test");

            assertEquals(original, parsed);
        }

        @Test
        void emptyMapRoundTrip() {
            Map<String, Integer> original = Map.of();
            List<String> asList = FabSettings.tierOverrideMapToList(original);
            Map<String, Integer> parsed = FabSettings.parseTierOverrideList(asList, 1, 99, "test");

            assertEquals(original, parsed);
        }
    }

    @Nested
    @DisplayName("Builder pattern")
    class BuilderPattern {
        @Test
        void builderCreatesIndependentInstances() {
            FabSettings s1 = FabSettings.builder().foupCapacity(10).build();
            FabSettings s2 = FabSettings.builder().foupCapacity(20).build();

            assertEquals(10, s1.foupCapacity());
            assertEquals(20, s2.foupCapacity());
        }

        @Test
        void toBuilderPreservesValues() {
            FabSettings original = FabSettings.builder()
                .foupCapacity(30)
                .wafersPerBoule(30)
                .tierYieldOverrides(Map.of("T5", 95))
                .coreCountOptions(List.of(1, 2, 4, 8))
                .build();

            FabSettings copied = original.toBuilder().build();

            assertEquals(original, copied);
        }

        @Test
        void toBuilderAllowsModification() {
            FabSettings original = FabSettings.builder().foupCapacity(25).build();
            FabSettings modified = original.toBuilder().foupCapacity(50).build();

            assertEquals(25, original.foupCapacity());
            assertEquals(50, modified.foupCapacity());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {
        @Test
        void returnedMapsAreUnmodifiable() {
            FabSettings settings = FabSettings.builder()
                .tierYieldOverrides(Map.of("T5", 90))
                .comfortableCoreOverrides(Map.of("T6", 6))
                .build();

            assertThrows(UnsupportedOperationException.class, 
                () -> settings.tierYieldOverrides().put("T6", 80));
            assertThrows(UnsupportedOperationException.class, 
                () -> settings.comfortableCoreOverrides().put("T7", 10));
        }

        @Test
        void returnedListsAreUnmodifiable() {
            FabSettings settings = FabSettings.defaults();

            assertThrows(UnsupportedOperationException.class, 
                () -> settings.coreCountOptions().add(16));
        }
    }

    @Nested
    @DisplayName("Equality and hashCode")
    class Equality {
        @Test
        void equalSettingsAreEqual() {
            FabSettings s1 = FabSettings.builder().foupCapacity(30).build();
            FabSettings s2 = FabSettings.builder().foupCapacity(30).build();

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        void differentSettingsNotEqual() {
            FabSettings s1 = FabSettings.builder().foupCapacity(30).build();
            FabSettings s2 = FabSettings.builder().foupCapacity(31).build();

            assertNotEquals(s1, s2);
        }
    }
}