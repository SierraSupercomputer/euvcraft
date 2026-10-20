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
 * {@code data/lithmod/support_recipes} codec for T3 e-beam recipes: the validator is the
 * constructor, so a valid recipe is one that survives construction and a malformed one
 * is one that throws — the same fail-closed answer the loader inherits.
 *
 * <p>No recipe numbers are invented here: the valid cases are the shipped
 * e-beam recipes, decoded exactly as the reload listener decodes them.</p>
 */
class T3SupportRecipeTest {

    /** A valid e-beam recipe for logic_t1, decoded as the loader decodes it. */
    private static final String EBEAM_LOGIC_T1_JSON = """
        {
          "machine": "ebeam_mask_writer",
          "input": { "item": "lithmod:blank_mask", "count": 1 },
          "secondary": { "item": "minecraft:redstone", "count": 32 },
          "output": "lithmod:mask",
          "count": 1,
          "duration": 1200,
          "energy_per_tick": 2000,
          "water_mb": 0
        }
        """;

    @Test
    @DisplayName("the shipped e-beam logic_t1 recipe decodes and keeps every field")
    void shippedEbeamLogicT1RecipeDecodes() {
        SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString(EBEAM_LOGIC_T1_JSON))
            .getOrThrow(message -> new JsonParseException(message));

        assertEquals("ebeam_mask_writer", recipe.machine());
        assertEquals(MachineIngredient.item("lithmod:blank_mask", 1), recipe.input());
        assertEquals(MachineIngredient.item("minecraft:redstone", 32), recipe.secondary());
        assertEquals("lithmod:mask", recipe.outputItem());
        assertEquals(1, recipe.outputCount());
        assertEquals(1200, recipe.duration());
        assertEquals(2000, recipe.energyPerTick());
        assertEquals(0, recipe.waterMb());
    }

    @Test
    @DisplayName("an e-beam recipe encoded back and forth round-trips")
    void ebeamCodecRoundTrips() {
        SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString(EBEAM_LOGIC_T1_JSON))
            .getOrThrow(message -> new JsonParseException(message));
        var encoded = SupportRecipeLoader.CODEC.encodeStart(JsonOps.INSTANCE, recipe);
        SupportRecipe decoded = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE, encoded.getOrThrow())
            .getOrThrow(message -> new JsonParseException(message));
        assertEquals(recipe, decoded, "the codec did not round-trip its own record");
    }

    @Test
    @DisplayName("e-beam recipes must have zero water")
    void ebeamWaterMustBeZero() {
        new SupportRecipe("ebeam_mask_writer",
            MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:redstone", 32),
            "lithmod:mask", 1, 1200, 2000, 0);
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "ebeam_mask_writer", MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:redstone", 32),
            "lithmod:mask", 1, 1200, 2000, 500),
            "an e-beam recipe with water was accepted");
    }

    @Test
    @DisplayName("e-beam recipes require blank_mask input")
    void ebeamInputMustBeBlankMask() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "ebeam_mask_writer", MachineIngredient.item("lithmod:iron_ingot", 1),
            MachineIngredient.item("minecraft:redstone", 32),
            "lithmod:mask", 1, 1200, 2000, 0),
            "non-blank-mask input was accepted");
    }

    @Test
    @DisplayName("e-beam recipes require redstone secondary")
    void ebeamSecondaryMustBeRedstone() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "ebeam_mask_writer", MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:quartz", 32),
            "lithmod:mask", 1, 1200, 2000, 0),
            "non-redstone secondary was accepted");
    }

    @Test
    @DisplayName("e-beam recipes require mask output")
    void ebeamOutputMustBeMask() {
        assertThrows(IllegalArgumentException.class, () -> new SupportRecipe(
            "ebeam_mask_writer", MachineIngredient.item("lithmod:blank_mask", 1),
            MachineIngredient.item("minecraft:redstone", 32),
            "lithmod:pure_silicon_boule", 1, 1200, 2000, 0),
            "non-mask output was accepted");
    }

    @Test
    @DisplayName("all seven e-beam design recipes are valid")
    void allSevenDesignRecipesValid() {
        String[] designs = {"logic_t1", "logic_t2", "memory_t2", "logic_t3", "memory_t3", "compute_t3", "nand_t3"};
        for (String design : designs) {
            String json = """
                {
                  "machine": "ebeam_mask_writer",
                  "input": { "item": "lithmod:blank_mask", "count": 1 },
                  "secondary": { "item": "minecraft:redstone", "count": 32 },
                  "output": "lithmod:mask",
                  "count": 1,
                  "duration": 1200,
                  "energy_per_tick": 2000,
                  "water_mb": 0
                }
                """.replace("logic_t1", design); // design name not in JSON but for clarity
            SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(json))
                .getOrThrow(message -> new JsonParseException(message));
            assertEquals("ebeam_mask_writer", recipe.machine());
            assertEquals(0, recipe.waterMb());
        }
    }
}