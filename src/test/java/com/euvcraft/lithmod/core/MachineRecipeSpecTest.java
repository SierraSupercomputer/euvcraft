package com.euvcraft.lithmod.core;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ingredient matching, which the task requires to be pure core logic.
 *
 * <p>Every positive assertion is paired with a negative control: the same inputs with one
 * thing changed must fail to match. Without those, a matcher that returned true for
 * everything would pass the file.</p>
 */
class MachineRecipeSpecTest {

    private static final String TAG = "forge:gems";

    private static MachineRecipeSpec.TagMemberResolver tags(Map<String, Set<String>> members) {
        return tagId -> members.getOrDefault(tagId, Set.of());
    }

    private static MachineRecipeSpec spec(List<MachineIngredient> ingredients) {
        return new MachineRecipeSpec("test_machine", ingredients,
            MachineResult.of("minecraft:gold_ingot", 1), 4, null);
    }

    private static com.google.gson.JsonElement tree(String json) {
        return JsonParser.parseString(json);
    }

    @Nested
    @DisplayName("a single item ingredient")
    class SingleItem {

        private final MachineRecipeSpec spec = spec(List.of(MachineIngredient.item("minecraft:iron_ingot", 1)));

        @Test
        void matchesItsExactIdAtTheRequiredCount() {
            assertTrue(spec.matches(List.of("minecraft:iron_ingot"), List.of(1),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void negativeControlADifferentItemDoesNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:dirt"), List.of(1),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void negativeControlAStackBelowTheRequiredCountDoesNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:iron_ingot"), List.of(0),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void negativeControlTheWrongNumberOfSlotsDoesNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:iron_ingot", "minecraft:gold_ingot"),
                List.of(1, 1), MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void aLongerStackThanRequiredStillMatches() {
            assertTrue(spec.matches(List.of("minecraft:iron_ingot"), List.of(9),
                MachineRecipeSpec.TagMemberResolver.none()));
        }
    }

    @Nested
    @DisplayName("an ingredient with a count")
    class Counts {

        private final MachineRecipeSpec spec = spec(List.of(MachineIngredient.item("minecraft:iron_ingot", 3)));

        @Test
        void matchesWhenTheStackHoldsExactlyTheRequiredCount() {
            assertTrue(spec.matches(List.of("minecraft:iron_ingot"), List.of(3),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void negativeControlOneShortOfTheRequiredCountDoesNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:iron_ingot"), List.of(2),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void aCountOfZeroOrLessIsRejectedAtConstruction() {
            assertTrue(message(() -> MachineIngredient.item("minecraft:iron_ingot", 0))
                .contains("count must be positive"));
            assertTrue(message(() -> MachineIngredient.item("minecraft:iron_ingot", -1))
                .contains("count must be positive"));
        }
    }

    @Nested
    @DisplayName("a tag ingredient")
    class Tags {

        private final MachineRecipeSpec.TagMemberResolver resolver = tags(Map.of(
            TAG, Set.of("minecraft:emerald", "minecraft:diamond")));

        private final MachineRecipeSpec spec = spec(List.of(MachineIngredient.tag(TAG, 1)));

        @Test
        void matchesAnyMemberOfTheTag() {
            assertTrue(spec.matches(List.of("minecraft:emerald"), List.of(1), resolver));
            assertTrue(spec.matches(List.of("minecraft:diamond"), List.of(1), resolver));
        }

        @Test
        void negativeControlANonMemberDoesNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:iron_ingot"), List.of(1), resolver));
        }

        @Test
        void negativeControlAnUnknownTagResolvesToNothingAndSoMatchesNothing() {
            MachineRecipeSpec unknownTag = spec(List.of(MachineIngredient.tag("forge:nothing", 1)));
            assertFalse(unknownTag.matches(List.of("minecraft:emerald"), List.of(1), resolver));
        }

        @Test
        void negativeControlAResolverThatKnowsNoTagsMatchesNothing() {
            assertFalse(spec.matches(List.of("minecraft:emerald"), List.of(1),
                MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void aTagIngredientReportsItselfAsATag() {
            assertTrue(MachineIngredient.tag(TAG, 1).isTag());
            assertFalse(MachineIngredient.item("minecraft:emerald", 1).isTag());
        }
    }

    @Nested
    @DisplayName("slot order")
    class SlotOrder {

        private final MachineRecipeSpec spec = spec(List.of(
            MachineIngredient.item("minecraft:iron_ingot", 1),
            MachineIngredient.item("minecraft:redstone", 2)));

        @Test
        void matchesWhenTheSlotsAreInOrder() {
            assertTrue(spec.matches(List.of("minecraft:iron_ingot", "minecraft:redstone"),
                List.of(1, 2), MachineRecipeSpec.TagMemberResolver.none()));
        }

        @Test
        void negativeControlTheSameItemsInTheWrongSlotsDoNotMatch() {
            assertFalse(spec.matches(List.of("minecraft:redstone", "minecraft:iron_ingot"),
                List.of(2, 1), MachineRecipeSpec.TagMemberResolver.none()));
        }
    }

    @Nested
    @DisplayName("the energy override")
    class Energy {

        @Test
        void anAbsentOverrideFallsThroughToTheMachineValue() {
            MachineRecipeSpec spec = new MachineRecipeSpec("m", List.of(MachineIngredient.item("a:b", 1)),
                MachineResult.of("minecraft:gold_ingot", 1), 4, null);
            assertTrue(spec.usesMachineEnergy());
            assertEquals(40, spec.energyPerTick(40));
        }

        @Test
        void aPresentOverrideWinsOverTheMachineValue() {
            MachineRecipeSpec spec = new MachineRecipeSpec("m", List.of(MachineIngredient.item("a:b", 1)),
                MachineResult.of("minecraft:gold_ingot", 1), 4, 7);
            assertFalse(spec.usesMachineEnergy());
            assertEquals(7, spec.energyPerTick(40));
        }

        @Test
        void negativeControlANonPositiveOverrideIsRejectedAtConstruction() {
            assertTrue(message(() -> new MachineRecipeSpec("m",
                List.of(MachineIngredient.item("a:b", 1)), MachineResult.of("minecraft:gold_ingot", 1),
                4, 0)).contains("energy_per_tick must be positive"));
        }
    }

    @Nested
    @DisplayName("construction validation")
    class Validation {

        @Test
        void aBlankMachineIdIsRejected() {
            assertTrue(message(() -> new MachineRecipeSpec(" ",
                List.of(MachineIngredient.item("a:b", 1)), MachineResult.of("minecraft:gold_ingot", 1),
                4, null)).contains("machine must not be blank"));
        }

        @Test
        void anEmptyIngredientListIsRejected() {
            assertTrue(message(() -> new MachineRecipeSpec("m", List.of(),
                MachineResult.of("minecraft:gold_ingot", 1), 4, null))
                .contains("at least one ingredient"));
        }

        @Test
        void negativeControlZeroTicksIsRejected() {
            assertTrue(message(() -> new MachineRecipeSpec("m",
                List.of(MachineIngredient.item("a:b", 1)), MachineResult.of("minecraft:gold_ingot", 1),
                0, null)).contains("ticks must be positive"));
        }

        @Test
        void negativeControlANonNamespacedIngredientIdIsRejected() {
            assertTrue(message(() -> MachineIngredient.item("iron_ingot", 1))
                .contains("must be namespaced"));
        }

        @Test
        void negativeControlANonNamespacedResultIdIsRejected() {
            assertTrue(message(() -> MachineResult.of("gold_ingot", 1))
                .contains("must be namespaced"));
        }

        @Test
        void negativeControlANonPositiveResultCountIsRejected() {
            assertTrue(message(() -> MachineResult.of("minecraft:gold_ingot", 0))
                .contains("result.count must be positive"));
        }
    }

    @Test
    void theCodecParsesAnItemRecipeFromJson() {
        String json = """
            {
              "machine": "test_machine",
              "ingredients": [ { "item": "minecraft:iron_ingot", "count": 2 } ],
              "result": { "item": "minecraft:gold_ingot", "count": 1 },
              "ticks": 4
            }
            """;
        MachineRecipeSpec parsed = MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json))
            .getOrThrow(AssertionError::new);

        assertEquals("test_machine", parsed.machineId());
        assertEquals(4, parsed.ticks());
        assertEquals(2, parsed.ingredients().get(0).count());
        assertEquals("minecraft:gold_ingot", parsed.result().itemId());
        assertTrue(parsed.usesMachineEnergy(),
            "energy_per_tick was absent so the machine value should be used");
    }

    @Test
    void theCodecParsesATagRecipeWithAResultComponentFromJson() {
        String json = """
            {
              "machine": "test_machine",
              "ingredients": [ { "tag": "minecraft:planks", "count": 2 } ],
              "result": {
                "item": "minecraft:stick",
                "count": 4,
                "components": { "minecraft:custom_name": "Charred Plank" }
              },
              "ticks": 6,
              "energy_per_tick": 5
            }
            """;
        MachineRecipeSpec parsed = MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json))
            .getOrThrow(AssertionError::new);

        assertTrue(parsed.ingredients().get(0).isTag(), "the ingredient should have parsed as a tag");
        assertEquals(6, parsed.ticks());
        assertFalse(parsed.usesMachineEnergy(), "energy_per_tick was present");
        assertEquals(5, parsed.energyPerTick(999), "the override should win");
        assertEquals("\"Charred Plank\"", parsed.result().component("minecraft:custom_name").orElseThrow(),
            "the component should be carried through as the json text of its value");
    }

    @Test
    void negativeControlTheCodecRejectsARecipeWithANonPositiveTickCount() {
        String json = """
            {
              "machine": "m",
              "ingredients": [ { "item": "minecraft:iron_ingot", "count": 1 } ],
              "result": { "item": "minecraft:gold_ingot", "count": 1 },
              "ticks": 0
            }
            """;
        // Validation runs in the canonical constructor, so bad data throws rather than
        // producing a DataResult error. The loader catches the throw, which is what keeps
        // the previously loaded recipes.
        assertTrue(message(() -> MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json)))
            .contains("ticks must be positive"));
    }

    @Test
    void negativeControlTheCodecRejectsARecipeWhoseResultItemIsNotNamespaced() {
        String json = """
            {
              "machine": "m",
              "ingredients": [ { "item": "minecraft:iron_ingot", "count": 1 } ],
              "result": { "item": "gold_ingot", "count": 1 },
              "ticks": 4
            }
            """;
        assertTrue(message(() -> MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json)))
            .contains("must be namespaced"));
    }

    @Test
    void negativeControlTheCodecRejectsARecipeWithNoIngredients() {
        String json = """
            {
              "machine": "m",
              "ingredients": [],
              "result": { "item": "minecraft:gold_ingot", "count": 1 },
              "ticks": 4
            }
            """;
        assertTrue(message(() -> MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json)))
            .contains("at least one ingredient"));
    }

    @Test
    void negativeControlTheCodecRejectsAMissingRequiredField() {
        String json = """
            { "machine": "m", "ingredients": [ { "item": "a:b", "count": 1 } ], "ticks": 4 }
            """;
        // A missing field is a DataResult error, not a throw, because the record is never
        // constructed at all.
        DataResult<MachineRecipeSpec> parsed = MachineRecipeSpec.CODEC.parse(JsonOps.INSTANCE, tree(json));
        assertTrue(parsed.error().isPresent(), "a recipe with no result should not parse");
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