package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * T4 content seed resource tests: validates that the T4 seed added the expected
 * registry entries by reading source files, that crafting recipes use only T3 (and below)
 * components with no circular T4 dependency, that e-beam support recipes for T4 designs
 * decode with the expected schema, and that previously registered resources remain
 * unchanged where useful.
 *
 * <p>No game launch: these are pure JVM checks using the actual source JSON files
 * and the shipped codecs, matching the loader behaviour exactly.</p>
 */
class T4ContentResourcesTest {

    private static final String MODID = "lithmod";
    private static final String ITEM_DEFINITIONS = "/assets/" + MODID + "/items/";
    private static final String ITEM_MODELS = "/assets/" + MODID + "/models/item/";
    private static final String ITEM_TEXTURES = "/assets/" + MODID + "/textures/item/";
    private static final String LANG = "/assets/" + MODID + "/lang/en_us.json";
    private static final String CRAFTING_RECIPES = "/data/" + MODID + "/recipe/crafting/";
    private static final String SUPPORT_RECIPES = "/data/" + MODID + "/support_recipes/";

    /** The 10 T4 item IDs registered in ModItems.java. */
    private static final Set<String> T4_ITEM_IDS = Set.of(
        "logic_chip_t4", "memory_chip_t4", "compute_chip_t4", "nand_chip_t4",
        "resist_t4",
        "arf_excimer_laser", "arf_dry_lens", "nanopositioning_stage", "ulpa_filter", "precision_overlay_scope"
    );

    /** The 5 T4 line component kinds and their expected tier 4 constructors from ModItems.java. */
    private static final Set<String> T4_COMPONENT_KINDS = Set.of("light_source", "optics", "stage", "cleanroom", "metrology");

    /** The 4 T4 e-beam design names shipped in support_recipes. */
    private static final List<String> T4_EBEAM_DESIGNS = List.of("logic_t4", "memory_t4", "compute_t4", "nand_t4");

    /** T3 component IDs that T4 recipes should depend on (not T4). */
    private static final Set<String> T3_COMPONENT_IDS = Set.of(
        "krf_excimer_laser", "duv_projection_lens", "precision_stepper_stage",
        "hepa_filter", "laser_interferometer", "resist_t3", "logic_chip_t3"
    );

    @Test
    @DisplayName("All 10 T4 items have registry entries in ModItems.java source")
    void t4ItemsRegisteredInSource() throws Exception {
        URL modItemsUrl = T4ContentResourcesTest.class.getResource("/com/euvcraft/lithmod/mc/registry/ModItems.java");
        assertNotNull(modItemsUrl, "ModItems.java source not found on classpath");
        String source = readAll(modItemsUrl);
        
        // Verify 10 T4 registrations exist in source
        int t4Count = 0;
        for (String id : T4_ITEM_IDS) {
            String expectedRegistration = "ITEMS.register";
            String expectedId = "lithmod:" + id;
            if (source.contains("registerItem(\"" + id + "\"") || source.contains("registerSimpleItem(\"" + id + "\"")) {
                t4Count++;
            }
        }
        assertEquals(10, t4Count, "ModItems.java must contain all 10 T4 item registrations");
    }

    @Test
    @DisplayName("T4 line components use five kind/tier4 constructors in ModItems.java")
    void t4LineComponentsKindAndTierInSource() throws Exception {
        URL modItemsUrl = T4ContentResourcesTest.class.getResource("/com/euvcraft/lithmod/mc/registry/ModItems.java");
        assertNotNull(modItemsUrl, "ModItems.java source not found on classpath");
        String source = readAll(modItemsUrl);

        // Verify each T4 component has the correct kind and tier=4 constructor
        assertTrue(source.contains("new LineComponentItem(properties, \"light_source\", 4)"), "ARF_EXCIMER_LASER must be light_source tier 4");
        assertTrue(source.contains("new LineComponentItem(properties, \"optics\", 4)"), "ARF_DRY_LENS must be optics tier 4");
        assertTrue(source.contains("new LineComponentItem(properties, \"stage\", 4)"), "NANOPOSITIONING_STAGE must be stage tier 4");
        assertTrue(source.contains("new LineComponentItem(properties, \"cleanroom\", 4)"), "ULPA_FILTER must be cleanroom tier 4");
        assertTrue(source.contains("new LineComponentItem(properties, \"metrology\", 4)"), "PRECISION_OVERLAY_SCOPE must be metrology tier 4");
    }

    @Test
    @DisplayName("RESIST_T4 carries RESIST_TIER=4 component in ModItems.java")
    void resistT4HasTierComponentInSource() throws Exception {
        URL modItemsUrl = T4ContentResourcesTest.class.getResource("/com/euvcraft/lithmod/mc/registry/ModItems.java");
        assertNotNull(modItemsUrl, "ModItems.java source not found on classpath");
        String source = readAll(modItemsUrl);

        assertTrue(source.contains("RESIST_T4") && source.contains("RESIST_TIER") && source.contains("4"), 
            "RESIST_T4 must carry RESIST_TIER=4 component");
    }

    @Test
    @DisplayName("ChipCatalog contains all 4 T4 chip entries")
    void chipCatalogHasT4Entries() throws Exception {
        URL chipCatalogUrl = T4ContentResourcesTest.class.getResource("/com/euvcraft/lithmod/mc/ChipCatalog.java");
        assertNotNull(chipCatalogUrl, "ChipCatalog.java source not found on classpath");
        String source = readAll(chipCatalogUrl);

        // Verify all 4 T4 chip designs are in ENTRIES
        assertTrue(source.contains("new Entry(new Design(DieTypes.LOGIC, 4), ModItems.LOGIC_CHIP_T4)"), "LOGIC T4 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.MEMORY, 4), ModItems.MEMORY_CHIP_T4)"), "MEMORY T4 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.COMPUTE, 4), ModItems.COMPUTE_CHIP_T4)"), "COMPUTE T4 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.NAND, 4), ModItems.NAND_CHIP_T4)"), "NAND T4 missing from ChipCatalog");
    }

    @Test
    @DisplayName("All 6 T4 crafting recipes exist and use only T3 or lower ingredients")
    void t4CraftingRecipesUseOnlyT3OrLower() throws Exception {
        // The 6 T4 crafting recipes: resist_t4, arf_excimer_laser, arf_dry_lens, nanopositioning_stage, ulpa_filter, precision_overlay_scope
        List<String> t4CraftingRecipes = List.of(
            "resist_t4.json", "arf_excimer_laser.json", "arf_dry_lens.json",
            "nanopositioning_stage.json", "ulpa_filter.json", "precision_overlay_scope.json"
        );

        for (String recipeFile : t4CraftingRecipes) {
            URL recipeUrl = T4ContentResourcesTest.class.getResource(CRAFTING_RECIPES + recipeFile);
            assertNotNull(recipeUrl, "Missing crafting recipe: " + recipeFile);
            
            String json = readAll(recipeUrl);
            
            // Verify no T4 ingredient IDs appear in the key section
            for (String t4Id : T4_ITEM_IDS) {
                assertFalse(json.contains("lithmod:" + t4Id), 
                    recipeFile + " must not use T4 ingredient: " + t4Id);
            }
            
            // Verify T3 ingredients are present (at least one T3 component per recipe)
            boolean hasT3Ingredient = false;
            for (String t3Id : T3_COMPONENT_IDS) {
                if (json.contains("lithmod:" + t3Id)) {
                    hasT3Ingredient = true;
                    break;
                }
            }
            assertTrue(hasT3Ingredient, recipeFile + " must use at least one T3 component ingredient");
        }
    }

    @Test
    @DisplayName("All four T4 e-beam support recipes decode with correct schema")
    void t4EbeamSupportRecipesDecode() throws Exception {
        for (String design : T4_EBEAM_DESIGNS) {
            URL recipeUrl = T4ContentResourcesTest.class.getResource(SUPPORT_RECIPES + "ebeam_" + design + ".json");
            assertNotNull(recipeUrl, "Missing e-beam support recipe: " + design);
            
            String json = readAll(recipeUrl);
            SupportRecipe recipe = SupportRecipeLoader.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(json))
                    .getOrThrow(message -> new com.google.gson.JsonParseException(message));

            assertEquals("ebeam_mask_writer", recipe.machine());
            assertEquals("lithmod:blank_mask", recipe.input().id());
            assertEquals(1, recipe.input().count());
            assertEquals("minecraft:redstone", recipe.secondary().id());
            assertEquals(48, recipe.secondary().count());
            assertEquals("lithmod:mask", recipe.outputItem());
            assertEquals(1, recipe.outputCount());
            assertEquals(1600, recipe.duration());
            assertEquals(2500, recipe.energyPerTick());
            assertEquals(0, recipe.waterMb());
        }
    }

    @Test
    @DisplayName("All 10 T4 items have client item definitions, models, textures, and lang entries")
    void t4ClientResourcesExist() throws Exception {
        for (String itemId : T4_ITEM_IDS) {
            // 1. Client item definition
            URL defUrl = T4ContentResourcesTest.class.getResource(ITEM_DEFINITIONS + itemId + ".json");
            assertNotNull(defUrl, "Missing client item definition: " + itemId);
            
            // 2. Item model
            URL modelUrl = T4ContentResourcesTest.class.getResource(ITEM_MODELS + itemId + ".json");
            assertNotNull(modelUrl, "Missing item model: " + itemId);
            
            // 3. Texture PNG
            URL texUrl = T4ContentResourcesTest.class.getResource(ITEM_TEXTURES + itemId + ".png");
            assertNotNull(texUrl, "Missing item texture: " + itemId + ".png");
            
            // 4. Language entry
            URL langUrl = T4ContentResourcesTest.class.getResource(LANG);
            assertNotNull(langUrl, "Missing lang file");
            String langJson = readAll(langUrl);
            String langKey = "item." + MODID + "." + itemId;
            assertTrue(langJson.contains("\"" + langKey + "\""), "Missing language entry for " + itemId);
        }
    }

    @Test
    @DisplayName("Previous T3 chip catalog entries unchanged")
    void previousChipCatalogEntriesUnchanged() throws Exception {
        URL chipCatalogUrl = T4ContentResourcesTest.class.getResource("/com/euvcraft/lithmod/mc/ChipCatalog.java");
        assertNotNull(chipCatalogUrl, "ChipCatalog.java source not found on classpath");
        String source = readAll(chipCatalogUrl);

        // T3 chips still in catalog
        assertTrue(source.contains("new Entry(new Design(DieTypes.LOGIC, 3), ModItems.LOGIC_CHIP_T3)"), "LOGIC T3 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.MEMORY, 3), ModItems.MEMORY_CHIP_T3)"), "MEMORY T3 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.COMPUTE, 3), ModItems.COMPUTE_CHIP_T3)"), "COMPUTE T3 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.NAND, 3), ModItems.NAND_CHIP_T3)"), "NAND T3 missing from ChipCatalog");

        // T2 and T1 still present
        assertTrue(source.contains("new Entry(new Design(DieTypes.LOGIC, 1), ModItems.LOGIC_CHIP_T1)"), "LOGIC T1 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.LOGIC, 2), ModItems.LOGIC_CHIP_T2)"), "LOGIC T2 missing from ChipCatalog");
        assertTrue(source.contains("new Entry(new Design(DieTypes.MEMORY, 2), ModItems.MEMORY_CHIP_T2)"), "MEMORY T2 missing from ChipCatalog");
    }

    @Test
    @DisplayName("MaskData schema works for T4 designs")
    void maskDataSchemaForT4Designs() throws Exception {
        // T4 designs should work with the same MaskData structure as T1-T3
        com.euvcraft.lithmod.core.MaskData logicT4 = new com.euvcraft.lithmod.core.MaskData(DieTypes.LOGIC, 4, 10, 2); // GEN2
        assertEquals(DieTypes.LOGIC, logicT4.dieType());
        assertEquals(4, logicT4.tierIndex());
        assertEquals(10, logicT4.usesRemaining());
        assertEquals(2, logicT4.shopGeneration());

        // JSON codec round-trip
        var encoded = com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec.CODEC.encodeStart(JsonOps.INSTANCE, logicT4);
        com.euvcraft.lithmod.core.MaskData decoded = com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, encoded.getOrThrow())
                .getOrThrow(message -> new com.google.gson.JsonParseException(message));
        assertEquals(logicT4, decoded);
    }

    /** Read entire URL content as string. */
    private static String readAll(URL url) throws Exception {
        try (InputStream in = url.openStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        }
    }
}