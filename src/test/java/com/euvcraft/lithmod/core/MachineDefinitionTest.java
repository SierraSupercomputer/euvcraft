package com.euvcraft.lithmod.core;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Machine definition parsing and validation, plus the {@code energyPerTick} resolution rule.
 *
 * <p>Every positive assertion is paired with a negative control so a validator that accepted
 * everything, or a resolver that ignored its inputs, could not pass.</p>
 */
class MachineDefinitionTest {

    private static MachineDefinition definition(int energyPerTick, Map<String, Integer> perTier,
                                               Map<String, Integer> params) {
        return new MachineDefinition("test_machine", 10_000, 1_000, energyPerTick, perTier, params);
    }

    /** Builds settings carrying the given FE power multiplier, everything else default. */
    private static FabSettings settings(double fePowerMultiplier) {
        return FabSettings.builder().fePowerMultiplier(fePowerMultiplier).build();
    }

    private static com.google.gson.JsonElement tree(String json) {
        return JsonParser.parseString(json);
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        void parsesEveryFieldFromJson() {
            String json = """
                {
                  "id": "test_machine",
                  "energy_capacity": 10000,
                  "max_receive": 1000,
                  "energy_per_tick": 40,
                  "per_tier_energy_per_tick": { "T1": 20, "T2": 60 },
                  "params": { "input_slots": 1 }
                }
                """;
            MachineDefinition parsed = MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree(json))
                .getOrThrow(AssertionError::new);

            assertEquals("test_machine", parsed.id());
            assertEquals(10_000, parsed.energyCapacity());
            assertEquals(1_000, parsed.maxReceive());
            assertEquals(40, parsed.energyPerTick());
            assertEquals(2, parsed.perTierEnergyPerTick().size());
            assertEquals(Optional.of(1), parsed.param("input_slots"));
        }

        @Test
        void theOptionalMapsDefaultToEmpty() {
            String json = """
                {
                  "id": "bare",
                  "energy_capacity": 100,
                  "max_receive": 50,
                  "energy_per_tick": 5
                }
                """;
            MachineDefinition parsed = MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree(json))
                .getOrThrow(AssertionError::new);

            assertTrue(parsed.perTierEnergyPerTick().isEmpty(),
                "per_tier_energy_per_tick should default to empty");
            assertTrue(parsed.params().isEmpty(), "params should default to empty");
            assertEquals(Optional.empty(), parsed.param("anything"));
            assertEquals(7, parsed.paramOrDefault("anything", 7));
        }

        @Test
        void negativeControlANonPositiveCapacityDoesNotParse() {
            // Validation runs in the canonical constructor, so bad data throws naming the
            // field rather than producing a DataResult error. The loader catches the throw,
            // which is what keeps the previously loaded definitions.
            assertTrue(message(() -> MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree("""
                { "id": "m", "energy_capacity": 0, "max_receive": 10, "energy_per_tick": 1 }
                """))).contains("energy_capacity must be positive"));
        }

        @Test
        void negativeControlANegativeEnergyPerTickDoesNotParse() {
            assertTrue(message(() -> MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree("""
                { "id": "m", "energy_capacity": 10, "max_receive": 10, "energy_per_tick": -1 }
                """))).contains("energy_per_tick must be non-negative"));
        }

        @Test
        void negativeControlANonPositiveParamDoesNotParse() {
            assertTrue(message(() -> MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree("""
                { "id": "m", "energy_capacity": 10, "max_receive": 10, "energy_per_tick": 1,
                  "params": { "slots": 0 } }
                """))).contains("params['slots'] must be a positive integer"));
        }

        @Test
        void negativeControlAMissingRequiredFieldDoesNotParse() {
            // A missing field never reaches the constructor, so it is a DataResult error
            // rather than a throw.
            DataResult<MachineDefinition> parsed = MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree("""
                { "id": "m", "energy_capacity": 10, "energy_per_tick": 1 }
                """));
            assertTrue(parsed.error().isPresent(), "a missing max_receive should not parse");
        }

        @Test
        void negativeControlAMalformedPerTierValueDoesNotParse() {
            DataResult<MachineDefinition> parsed = MachineDefinition.CODEC.parse(JsonOps.INSTANCE, tree("""
                { "id": "m", "energy_capacity": 10, "max_receive": 10, "energy_per_tick": 1,
                  "per_tier_energy_per_tick": { "T1": "not a number" } }
                """));
            assertTrue(parsed.error().isPresent(), "a non-numeric per-tier value should not parse");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void aWellFormedDefinitionIsAccepted() {
            MachineDefinition definition = definition(40, Map.of("T1", 20), Map.of("slots", 1));
            assertEquals("test_machine", definition.id());
            assertEquals(40, definition.energyCapacity() / 250,
                "energy_capacity divided by 250 should be 40 for this fixture");
        }

        @Test
        void negativeControlANonPositiveCapacityIsRejectedNamingTheField() {
            assertTrue(message(() -> new MachineDefinition("m", 0, 10, 1, Map.of(), Map.of()))
                .contains("energy_capacity must be positive"));
        }

        @Test
        void negativeControlANonPositiveMaxReceiveIsRejectedNamingTheField() {
            assertTrue(message(() -> new MachineDefinition("m", 10, 0, 1, Map.of(), Map.of()))
                .contains("max_receive must be positive"));
        }

        @Test
        void negativeControlANegativePerTierOverrideIsRejectedNamingTheField() {
            assertTrue(message(() -> new MachineDefinition("m", 10, 10, 1, Map.of("T1", -5), Map.of()))
                .contains("per_tier_energy_per_tick['T1'] must be non-negative"));
        }

        @Test
        void negativeControlABlankIdIsRejected() {
            assertTrue(message(() -> new MachineDefinition(" ", 10, 10, 1, Map.of(), Map.of()))
                .contains("id must not be blank"));
        }

        @Test
        void zeroEnergyPerTickIsLegalForAMachineThatNeedsNone() {
            MachineDefinition free = new MachineDefinition("m", 10, 10, 0, Map.of(), Map.of());
            assertEquals(0, free.energyPerTick(null, settings(1.0)),
                "a machine declaring no energy should draw none");
        }
    }

    @Nested
    @DisplayName("energyPerTick resolution")
    class EnergyPerTick {

        private final MachineDefinition definition = definition(40, Map.of("T1", 20, "T2", 60), Map.of());

        @Test
        void withoutATierTheBaseValueIsUsed() {
            assertEquals(40, definition.energyPerTick(null, settings(1.0)));
        }

        @Test
        void aPerTierOverrideWinsOverTheBaseValue() {
            assertEquals(20, definition.energyPerTick(tier("T1"), settings(1.0)));
            assertEquals(60, definition.energyPerTick(tier("T2"), settings(1.0)));
        }

        @Test
        void aTierWithNoOverrideFallsThroughToTheBaseValue() {
            assertEquals(40, definition.energyPerTick(tier("T5"), settings(1.0)));
        }

        @Test
        void thePowerMultiplierScalesTheResult() {
            assertEquals(80, definition.energyPerTick(null, settings(2.0)));
            assertEquals(20, definition.energyPerTick(null, settings(0.5)));
        }

        @Test
        void theMultiplierAppliesToAPerTierOverrideToo() {
            assertEquals(40, definition.energyPerTick(tier("T1"), settings(2.0)));
        }

        @Test
        void theResultIsRoundedToTheNearestWholeFe() {
            assertEquals(41, definition.energyPerTick(null, settings(1.025)), "40 * 1.025 = 41");
            assertEquals(39, definition.energyPerTick(null, settings(0.975)), "40 * 0.975 = 39");
        }

        @Test
        void aPositiveBaseNeverRoundsDownBelowOneFe() {
            MachineDefinition tiny = definition(1, Map.of(), Map.of());
            assertEquals(1, tiny.energyPerTick(null, settings(0.001)),
                "a machine declaring 1 FE/tick must not become free");
        }

        @Test
        void negativeControlAZeroBaseStaysZeroEvenWithALargeMultiplier() {
            MachineDefinition free = definition(0, Map.of(), Map.of());
            assertEquals(0, free.energyPerTick(null, settings(1000.0)));
        }

        @Test
        void baseEnergyPerTickReportsTheUnscaledValue() {
            assertEquals(40, definition.baseEnergyPerTick(null));
            assertEquals(20, definition.baseEnergyPerTick(tier("T1")));
            assertEquals(40, definition.baseEnergyPerTick(tier("T5")));
        }

        private Tier tier(String id) {
            return new Tier(id, 1, "T", "minecraft:torch", 100, 100, 1, 1, 1, 50.0, 1, null, false);
        }
    }

    private static String message(Runnable action) {
        try {
            action.run();
            return "<no exception thrown>";
        } catch (RuntimeException failure) {
            String message = String.valueOf(failure.getMessage());
            return message == null ? "<null message>" : message;
        }
    }
}
