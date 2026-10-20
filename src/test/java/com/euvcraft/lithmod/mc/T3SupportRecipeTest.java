package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;

import com.mojang.serialization.JsonOps;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure JVM checks on the {@link SupportRecipe} model and the
 * {@code data/lithmod/support_recipes} codec: the validator is the constructor, so a valid
 * recipe is one that survives construction and a malformed one is one that throws — the same
 * fail-closed answer the loader inherits.
 *
 * <p>No recipe numbers are invented here: the valid case is the shipped
 * {@code boule_purifier.json}, decoded exactly as the reload listener decodes it.</p>
 */
class T3SupportRecipeTest {

    /** The shipped default purifier recipe, decoded as the loader decodes it. */
    private static final String SHIPPED_PURIFIER_JSON = """
        {
          "machine": "boule_purifier",
          "input": { "item": "lithmod:silicon_boule", "count": 1 },
          "output": "lithmod:pure_silicon_boule",
          "count": 1,
          "duration": 400,
          "energy_per_tick": 80,
          "water_mb": 1000
        }
        """;

    @Test
    @DisplayName("the shipped boule_purifier recipe decodes and keeps every field")
    void shippedPurifierRecipeDecodes() {
        SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString(SHIPPED_PURIFIER_JSON))
            .getOrThrow(message -> new JsonParseException(message));

        assertEquals("boule_purifier", recipe.machine());
        assertEquals(MachineIngredient.item("lithmod:silicon_boule", 1), recipe.input());
        assertEquals("lithmod:pure_silicon_boule", recipe.outputItem());
        assertEquals(1, recipe.outputCount());
        assertEquals(400, recipe.duration());
        assertEquals(80, recipe.energyPerTick());
        assertEquals(1000, recipe.waterMb());
        assertTrue(recipe.secondary() == null, "the purifier recipe took no secondary");
    }

    @Test
    @DisplayName("a recipe encoded back and forth round-trips")
    void codecRoundTrips() {
        SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString(SHIPPED_PURIFIER_JSON))
            .getOrThrow(message -> new JsonParseException(message));
        var encoded = SupportRecipeLoader.CODEC.encodeStart(JsonOps.INSTANCE, recipe);
        SupportRecipe decoded = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE, encoded.getOrThrow())
            .getOrThrow(message -> new JsonParseException(message));
        assertEquals(recipe, decoded, "the codec did not round-trip its own record");
    }

    @Test
    @DisplayName("an unknown machine id is refused")
    void unknownMachineRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "litho_press", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, 1000));
    }

    @Test
    @DisplayName("the purifier takes only ordinary boules into pure boules")
    void purifierInputsAndOutputsAreLocked() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:iron_ingot", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, 1000),
            "a non-boule input was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:iron_ingot", 1, 400, 80, 1000),
            "a non-pure-boule output was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1),
            MachineIngredient.item("minecraft:redstone", 1),
            "lithmod:pure_silicon_boule", 1, 400, 80, 1000),
            "a secondary ingredient was accepted");
    }

    @Test
    @DisplayName("the purifier's water cost must be positive and bounded")
    void purifierWaterIsPositiveAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, 0),
            "a zero water cost was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, -1),
            "a negative water cost was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, 2_000_000),
            "an over-bound water cost was accepted");
    }

    @Test
    @DisplayName("the e-beam recipes carry no water while they do not exist yet")
    void ebeamWaterMustBeZero() {
        new SupportRecipe("ebeam_mask_writer",
            MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:redstone", 1),
            "lithmod:mask", 1, 400, 80, 0);
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "ebeam_mask_writer", MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:redstone", 1),
            "lithmod:mask", 1, 400, 80, 500),
            "an e-beam recipe with water was accepted");
    }

    @Test
    @DisplayName("ticks, cost and counts are positive and bounded")
    void numbersArePositiveAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 0, 80, 1000), "zero ticks was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 2_000_001, 80, 1000),
            "over-bound ticks was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 1, 400, 0, 1000), "zero FE/tick was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "lithmod:pure_silicon_boule", 0, 400, 80, 1000), "zero output count was accepted");
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 0), null,
            "lithmod:pure_silicon_boule", 1, 400, 80, 1000), "zero input count was accepted");
    }

    @Test
    @DisplayName("the output id must be a namespaced item id")
    void outputIdIsNamespaced() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "boule_purifier", MachineIngredient.item("lithmod:silicon_boule", 1), null,
            "pure_silicon_boule", 1, 400, 80, 1000),
            "a non-namespaced output was accepted");
    }
}
