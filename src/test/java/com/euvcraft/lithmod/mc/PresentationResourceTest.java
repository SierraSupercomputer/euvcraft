package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeSpecCodec;
import com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JUnit resource tests for M1-11 recipe presentation: validates crafting JSON files,
 * pattern generator JSON files, and generated docs/recipes.md.
 *
 * <p>Runs without Minecraft registry bootstrap. Actual registry matching belongs to GameTests.</p>
 */
class PresentationResourceTest {

    private static final String CRAFTING_DIR = "data/lithmod/recipe/crafting/";
    private static final String MACHINE_RECIPES_DIR = "data/lithmod/machine_recipes/";
    private static final String DOCS_RECIPES = "docs/recipes.md";

    /**
     * Validates all 22 crafting JSON files use current ingredient strings/vanilla IDs,
     * valid patterns, and result item matching filename and allowed outputs.
     */
    @Test
    @DisplayName("all 22 crafting JSON use valid ingredients, patterns, and results")
    void craftingJsonFilesAreValid() throws Exception {
        List<String> expectedFiles = List.of(
            "crystal_puller.json", "wafer_slicer.json", "pattern_generator.json",
            "test_package.json", "process_cell_controller.json", "process_cell_casing.json",
            "process_cell_power_port.json", "foup_mk1.json", "blank_mask.json",
            "mercury_lamp.json", "i_line_lamp.json", "contact_plate.json",
            "reduction_lens.json", "manual_aligner.json", "stepper_stage.json",
            "basic_air_filter.json", "fine_air_filter.json", "optical_microscope.json",
            "overlay_scope.json", "resist_t1.json", "resist_t2.json", "chip_package.json"
        );

        assertEquals(22, expectedFiles.size(), "Expected 22 crafting recipe files");

        Set<String> allowedResultItems = Set.of(
            "lithmod:crystal_puller", "lithmod:wafer_slicer", "lithmod:pattern_generator",
            "lithmod:test_package", "lithmod:process_cell_controller", "lithmod:process_cell_casing",
            "lithmod:process_cell_power_port", "lithmod:foup_mk1", "lithmod:blank_mask",
            "lithmod:mercury_lamp", "lithmod:i_line_lamp", "lithmod:contact_plate",
            "lithmod:reduction_lens", "lithmod:manual_aligner", "lithmod:stepper_stage",
            "lithmod:basic_air_filter", "lithmod:fine_air_filter", "lithmod:optical_microscope",
            "lithmod:overlay_scope", "lithmod:resist_t1", "lithmod:resist_t2",
            "lithmod:chip_package"
        );

        int validated = 0;
        for (String fileName : expectedFiles) {
            String resourcePath = "/" + CRAFTING_DIR + fileName;
            try (InputStream input = PresentationResourceTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, "Missing resource: " + resourcePath);
                JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                assertNotNull(root, "Failed to parse: " + fileName);

                // Validate type
                JsonElement typeElem = root.getAsJsonObject().get("type");
                assertNotNull(typeElem, "Missing type in " + fileName);
                String type = typeElem.getAsString();
                assertTrue(type.equals("minecraft:crafting_shaped"),
                    "Unexpected recipe type in " + fileName + ": " + type);

                // Validate result item matches filename
                JsonElement resultElem = root.getAsJsonObject().get("result");
                assertNotNull(resultElem, "Missing result in " + fileName);
                String resultId = resultElem.getAsJsonObject().get("id").getAsString();
                assertEquals("lithmod:" + fileName.substring(0, fileName.length() - 5), resultId);
                assertTrue(allowedResultItems.contains(resultId),
                    "Result item not in allowed list in " + fileName + ": " + resultId);

                // For shaped recipes, validate pattern and key
                if (type.equals("minecraft:crafting_shaped")) {
                    JsonElement patternElem = root.getAsJsonObject().get("pattern");
                    assertNotNull(patternElem, "Missing pattern in " + fileName);
                    JsonElement keyElem = root.getAsJsonObject().get("key");
                    assertNotNull(keyElem, "Missing key in " + fileName);

                    // Validate each key entry uses allowed vanilla items
                    for (Map.Entry<String, JsonElement> entry : keyElem.getAsJsonObject().entrySet()) {
                        String itemId = entry.getValue().getAsString();
                        assertTrue(itemId.matches("#?minecraft:[a-z0-9_/.]+"),
                            "Invalid ingredient item in " + fileName + ": " + itemId);
                    }

                    // Validate pattern dimensions (all rows same length)
                    List<String> pattern = new ArrayList<>();
                    for (JsonElement row : patternElem.getAsJsonArray()) {
                        pattern.add(row.getAsString());
                    }
                    int width = pattern.get(0).length();
                    for (String row : pattern) {
                        assertEquals(width, row.length(), "Inconsistent row width in " + fileName);
                    }
                    assertTrue(width > 0 && width <= 3 && pattern.size() > 0 && pattern.size() <= 3, "Pattern too large in " + fileName);
                }

                validated++;
            }
        }
        assertEquals(22, validated, "Validated " + validated + " crafting files, expected 22");
    }

    /**
     * Validates all 3 custom pattern JSON files decode via MachineRecipeSpecCodec.CODEC
     * and MaskDataCodec.CODEC for the inner encoded JSON, and carry exact user
     * design/material choices.
     */
    @Test
    @DisplayName("all 3 pattern generator JSON decode via MachineRecipeSpecCodec and MaskDataCodec")
    void patternGeneratorJsonFilesDecodeCorrectly() throws Exception {
        List<String> expectedFiles = List.of(
            "pattern_logic_t1.json", "pattern_logic_t2.json", "pattern_memory_t2.json"
        );

        assertEquals(3, expectedFiles.size(), "Expected 3 pattern generator recipe files");

        for (String fileName : expectedFiles) {
            String resourcePath = "/" + MACHINE_RECIPES_DIR + fileName;
            try (InputStream input = PresentationResourceTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, "Missing resource: " + resourcePath);
                JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                assertNotNull(root, "Failed to parse: " + fileName);

                // Decode via MachineRecipeSpecCodec.CODEC
                MachineRecipeSpec spec = MachineRecipeSpecCodec.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE, root).getOrThrow();

                // Validate machine ID
                assertEquals("pattern_generator", spec.machineId(),
                    "Wrong machine ID in " + fileName);

                // Validate ingredients: blank_mask + one vanilla material
                assertEquals(2, spec.ingredients().size(), "Expected 2 ingredients in " + fileName);
                assertEquals("lithmod:blank_mask", spec.ingredients().get(0).id(),
                    "First ingredient must be blank_mask in " + fileName);
                assertEquals(1, spec.ingredients().get(0).count(), "Blank mask count must be 1 in " + fileName);

                String designIngredient = spec.ingredients().get(1).id();
                assertTrue(Set.of("minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:redstone").contains(designIngredient),
                    "Unexpected design ingredient in " + fileName + ": " + designIngredient);
                assertEquals(1, spec.ingredients().get(1).count(), "Design ingredient count must be 1 in " + fileName);

                // Validate result: mask with mask_data component
                assertEquals("lithmod:mask", spec.result().itemId(), "Result must be mask in " + fileName);
                assertEquals(1, spec.result().count(), "Result count must be 1 in " + fileName);

                // The resource stores structured JSON; the codec retains its compact JSON text.
                Map<String, String> components = spec.result().components();
                assertTrue(components.containsKey("lithmod:mask_data"), "Missing mask_data component in " + fileName);
                String maskDataJson = components.get("lithmod:mask_data");
                assertNotNull(maskDataJson, "mask_data component value is null in " + fileName);

                // Decode inner JSON via MaskDataCodec.CODEC
                JsonElement maskDataElement = JsonParser.parseString(maskDataJson);
                com.euvcraft.lithmod.core.MaskData maskData = MaskDataCodec.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE, maskDataElement).getOrThrow();

                // Validate exact user design/material choices
                if (fileName.equals("pattern_logic_t1.json")) {
                    assertEquals(DieTypes.LOGIC, maskData.dieType(), "Wrong die type in " + fileName);
                    assertEquals(1, maskData.tierIndex(), "Wrong tier in " + fileName);
                    assertEquals("minecraft:copper_ingot", designIngredient, "Wrong material for logic T1");
                } else if (fileName.equals("pattern_logic_t2.json")) {
                    assertEquals(DieTypes.LOGIC, maskData.dieType(), "Wrong die type in " + fileName);
                    assertEquals(2, maskData.tierIndex(), "Wrong tier in " + fileName);
                    assertEquals("minecraft:iron_ingot", designIngredient, "Wrong material for logic T2");
                } else if (fileName.equals("pattern_memory_t2.json")) {
                    assertEquals(DieTypes.MEMORY, maskData.dieType(), "Wrong die type in " + fileName);
                    assertEquals(2, maskData.tierIndex(), "Wrong tier in " + fileName);
                    assertEquals("minecraft:redstone", designIngredient, "Wrong material for memory T2");
                }

                // Validate uses_remaining is 0 (template value, gets stamped at runtime)
                assertEquals(0, maskData.usesRemaining(), "Template uses_remaining must be 0 in " + fileName);

                // Validate ticks
                assertEquals(200, spec.ticks(), "Ticks must be 200 in " + fileName);
            }
        }
    }

    /**
     * Validates generated docs/recipes.md contains all 30 actual resource IDs and
     * source paths, correct per-recipe counts/components from source where useful.
     */
    @Test
    @DisplayName("generated docs/recipes.md contains all 30 resource IDs and source paths")
    void generatedRecipesMarkdownIsComplete() throws Exception {
        Path docPath = Path.of(DOCS_RECIPES);
        assertTrue(Files.exists(docPath), "docs/recipes.md does not exist");

        String content = Files.readString(docPath, StandardCharsets.UTF_8);
        assertNotNull(content, "docs/recipes.md is empty");

        // Expected 30 resource IDs: 22 crafting + 2 cooking + 6 machine recipes
        List<String> expectedResourceIds = List.of(
            // 22 crafting
            "lithmod:crafting/crystal_puller", "lithmod:crafting/wafer_slicer",
            "lithmod:crafting/pattern_generator", "lithmod:crafting/test_package",
            "lithmod:crafting/process_cell_controller", "lithmod:crafting/process_cell_casing",
            "lithmod:crafting/process_cell_power_port", "lithmod:crafting/foup_mk1",
            "lithmod:crafting/blank_mask", "lithmod:crafting/mercury_lamp",
            "lithmod:crafting/i_line_lamp", "lithmod:crafting/contact_plate",
            "lithmod:crafting/reduction_lens", "lithmod:crafting/manual_aligner",
            "lithmod:crafting/stepper_stage", "lithmod:crafting/basic_air_filter",
            "lithmod:crafting/fine_air_filter", "lithmod:crafting/optical_microscope",
            "lithmod:crafting/overlay_scope", "lithmod:crafting/resist_t1",
            "lithmod:crafting/resist_t2", "lithmod:crafting/chip_package",
            // 2 cooking
            "lithmod:silicon_from_smelting", "lithmod:silicon_from_blasting",
            // 6 machine recipes
            "lithmod:crystal_puller", "lithmod:pattern_logic_t1",
            "lithmod:pattern_logic_t2", "lithmod:pattern_memory_t2",
            "lithmod:test_machine_from_planks", "lithmod:test_machine_from_ingot"
        );

        // Each expected resource ID must appear in the markdown
        for (String id : expectedResourceIds) {
            assertTrue(content.contains(id),
                "Missing resource ID in docs/recipes.md: " + id);
        }

        // Validate source paths are mentioned (at least the directory prefixes)
        assertTrue(content.contains("data/lithmod/recipe/crafting/"),
            "Missing crafting source path in docs/recipes.md");
        assertTrue(content.contains("data/lithmod/recipe/"),
            "Missing cooking source path in docs/recipes.md");
        assertTrue(content.contains("data/lithmod/machine_recipes/"),
            "Missing machine_recipes source path in docs/recipes.md");

        // Validate no absolute user paths or timestamps or manual placeholders
        assertTrue(!content.contains("/Users/"), "Found absolute user path in docs/recipes.md");
        assertTrue(!content.contains("C:\\"), "Found absolute Windows path in docs/recipes.md");
        assertTrue(!content.contains("TODO"), "Found TODO placeholder in docs/recipes.md");
        assertTrue(!content.contains("FIXME"), "Found FIXME placeholder in docs/recipes.md");
        assertTrue(!content.contains("PLACEHOLDER"), "Found PLACEHOLDER in docs/recipes.md");

        // Validate per-recipe counts: 30 total
        long recipeCount = content.lines()
            .filter(line -> line.startsWith("| lithmod:"))
            .count();
        // Allow some flexibility in markdown formatting, but should have at least 30 entries
        assertEquals(30L, recipeCount, "Expected at least 30 recipe entries in docs/recipes.md, found " + recipeCount);

        // Validate components from source where useful (mask_data for pattern recipes)
        assertTrue(content.contains("mask_data"), "Missing mask_data component documentation");
        assertTrue(content.contains("die_type"), "Missing die_type documentation");
        assertTrue(content.contains("tier_index"), "Missing tier_index documentation");
        assertTrue(content.contains("uses_remaining"), "Missing uses_remaining documentation");
    }

    /**
     * Code translation coverage: scans production Java source for literal
     * {@code Component.translatable("...")} keys and verifies they exist in en_us.json.
     *
     * <p>Dynamic prefixes (concatenated expressions) are NOT complete keys and are not
     * flagged as missing. Keys ending with a dot are treated as prefixes. The test
     * identifies concatenated expressions rather than blanket-ignoring arbitrary
     * missing keys.</p>
     *
     * <p>Independently enumerates {@link RefusalReason.Code} and {@link ProcessState}
     * values and requires actual {@code state.lithmod.process_cell.<lowercase>} keys
     * for each. Checks dynamic tier t1/t2 and die logic/memory used by current
     * registered M1 items. Does not load client classes or Minecraft registry bootstrap.</p>
     */
    @Test
    @DisplayName("all literal Component.translatable keys exist in en_us.json and core enum keys covered")
    void translationKeysAreComplete() throws Exception {
        // ---- Load en_us.json ----
        Map<String, String> lang = loadLang();

        // ---- Scan production Java source files for Component.translatable("...") ----
        Set<String> translatableKeys = findTranslatableKeysInSource();

        // ---- Verify each literal key exists in language file ----
        Set<String> missingKeys = new HashSet<>();
        for (String key : translatableKeys) {
            if (!lang.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        assertTrue(missingKeys.isEmpty(),
                "Missing language entries for literal translatable keys: " + missingKeys);

        // ---- Verify RefusalReason.Code enum keys ----
        for (RefusalReason.Code code : RefusalReason.Code.values()) {
            String key = "state.lithmod.process_cell." + code.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(lang.containsKey(key),
                    "Missing language entry for RefusalReason.Code: " + key);
        }

        // ---- Verify ProcessState enum keys ----
        for (ProcessState state : ProcessState.values()) {
            String key = "state.lithmod.process_cell." + state.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(lang.containsKey(key),
                    "Missing language entry for ProcessState: " + key);
        }

        // ---- Verify dynamic tier keys (t1, t2) ----
        assertTrue(lang.containsKey("tier.lithmod.t1"), "Missing tier.lithmod.t1");
        assertTrue(lang.containsKey("tier.lithmod.t2"), "Missing tier.lithmod.t2");

        // ---- Verify die type keys used by current registered M1 items ----
        assertTrue(lang.containsKey("dieType.lithmod.logic"), "Missing dieType.lithmod.logic");
        assertTrue(lang.containsKey("dieType.lithmod.memory"), "Missing dieType.lithmod.memory");
        for (String kind : com.euvcraft.lithmod.core.SupportLines.ALL) {
            assertTrue(lang.containsKey("componentKind.lithmod." + kind), "Missing component kind translation: " + kind);
        }
        // compute and nand exist in DieTypes but not yet used by registered M1 items
    }

    private static Map<String, String> loadLang() throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (InputStream input = PresentationResourceTest.class.getResourceAsStream("/assets/lithmod/lang/en_us.json")) {
            assertNotNull(input, "Missing bundled language file: /assets/lithmod/lang/en_us.json");
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                entries.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return entries;
    }

    /**
     * Scans src/main/java for literal Component.translatable("...") calls.
     * Returns only complete literal string keys, not dynamic/concatenated expressions.
     */
    private static Set<String> findTranslatableKeysInSource() throws IOException {
        Path sourceRoot = findProjectRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "Production source tree is unavailable: " + sourceRoot);
        Set<String> keys = new HashSet<>();
        Pattern pattern = Pattern.compile("Component\\.translatable(?:Escape)?\\(\\s*\"([^\"\\r\\n]+)\"\\s*([,)+])");
        List<Path> files;
        try (Stream<Path> sourceFiles = Files.walk(sourceRoot)) {
            files = sourceFiles.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertTrue(!files.isEmpty(), "No production Java files were scanned");
        for (Path file : files) {
            Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                // A plus denotes a dynamic prefix, covered by the explicit catalog checks.
                if (!matcher.group(2).equals("+")) keys.add(matcher.group(1));
            }
        }
        assertTrue(!keys.isEmpty(), "No literal translation calls found");
        return keys;
    }

    private static Path findProjectRoot() {
        // Try current working directory
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/java"))) {
            return cwd;
        }
        // Try parent directories
        Path current = cwd;
        while (current != null) {
            if (Files.exists(current.resolve("src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        return cwd;
    }
}

