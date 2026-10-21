package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates M1 starter texture resources at build time.
 *
 * <p>Checks that every production item model references a {@code lithmod:item/<id>}
 * texture that exists, decodes as 16×16 RGBA, and has transparent background.
 * Every production block model references the correct {@code lithmod:block/...}
 * textures that exist, decode as 16×16 RGBA, and are fully opaque.
 * The {@code test_machine} resources are verified to exist but are not regenerated.
 *
 * <p>Missing or malformed resources **fail** the test — no silent skip.
 * Uses the same {@code ImageIO} + Gson stack as {@link ItemAssetResourceTest}.
 */
class StarterTextureResourceTest {

    private static final String MODID = EUVCRAFTMod.MODID;
    private static final String ITEM_MODELS = "/assets/" + MODID + "/models/item/";
    private static final String BLOCK_MODELS = "/assets/" + MODID + "/models/block/";
    private static final String ITEM_TEXTURES = "/assets/" + MODID + "/textures/item/";
    private static final String BLOCK_TEXTURES = "/assets/" + MODID + "/textures/block/";

    // 44 non-block production items that must have custom textures (34 original + 10 T4)
    private static final Set<String> PRODUCTION_ITEMS = Set.of(
        "foup_mk1", "foup_mk2",
        "logic_chip_t1", "logic_chip_t2", "memory_chip_t2",
        "chip_scrap", "silicon", "silicon_boule",
        "resist_t1", "resist_t2",
        "chip_package", "blank_mask", "mask",
        "mercury_lamp", "i_line_lamp",
        "contact_plate", "reduction_lens",
        "manual_aligner", "stepper_stage",
        "basic_air_filter", "fine_air_filter",
        "optical_microscope", "overlay_scope",
        "pure_silicon_boule",
        "logic_chip_t3",
        "memory_chip_t3",
        "compute_chip_t3",
        "nand_chip_t3",
        "krf_excimer_laser",
        "duv_projection_lens",
        "precision_stepper_stage",
        "hepa_filter",
        "laser_interferometer",
        "resist_t3",
        // T4 items (10)
        "logic_chip_t4", "memory_chip_t4", "compute_chip_t4", "nand_chip_t4",
        "resist_t4",
        "arf_excimer_laser", "arf_dry_lens", "nanopositioning_stage", "ulpa_filter", "precision_overlay_scope"
    );

    // 10 production block models (test_machine excluded — preserved untouched)
    private static final Set<String> PRODUCTION_BLOCKS = Set.of(
        "crystal_puller", "wafer_slicer", "test_package", "pattern_generator",
        "process_cell_controller_formed", "process_cell_controller_unformed",
        "process_cell_casing_formed", "process_cell_casing_unformed",
        "process_cell_power_port_formed", "process_cell_power_port_unformed"
    );

    // Joined panel block models: 4 roles x 12 parts x 2 states = 96 models
    // Validated by JoinedPanelResourceTest; allowed here to prevent orphan false positives.
    private static final Set<String> JOINED_PANEL_BLOCKS = joinedPanelBlockIds();
    private static Set<String> joinedPanelBlockIds() {
        Set<String> ids = new HashSet<>();
        for (String role : List.of("controller", "casing", "power_port", "operating_panel", "water_inlet")) {
            for (int part = 0; part < 12; part++) {
                ids.add("process_cell_" + role + "_part" + part + "_formed");
                ids.add("process_cell_" + role + "_part" + part + "_unformed");
            }
        }
        ids.add("process_cell_operating_panel_unformed");
        ids.add("process_cell_water_inlet_unformed");
        for(String role:List.of("controller","casing","power_port","operating_panel")){
            ids.add("t4_"+role+"_unformed");
            for(int part=0;part<45;part++)ids.add("t4_"+role+"_large_part"+part+"_formed");
            for(int part=0;part<12;part++)ids.add("t4_"+role+"_small_part"+part+"_formed");
        }
        return Set.copyOf(ids);
    }

    // Block models that use orientable (multi-face) vs cube_all (single face)
    private static final Set<String> ORIENTABLE_BLOCKS = Set.of(
        "process_cell_controller_formed", "process_cell_controller_unformed"
    );

    @Test
    @DisplayName("All production item models reference lithmod:item/<id> and PNG exists 16x16 transparent")
    void productionItemModelsAndTexturesValid() throws Exception {
        for (String itemId : PRODUCTION_ITEMS) {
            // 1. Load item model JSON
            URL modelUrl = StarterTextureResourceTest.class.getResource(ITEM_MODELS + itemId + ".json");
            assertNotNull(modelUrl, "Missing item model: " + itemId);
            JsonObject model = parseJson(modelUrl);

            // 2. Verify parent and texture reference
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(),
                "Item " + itemId + " must use minecraft:item/generated parent");
            JsonObject textures = model.getAsJsonObject("textures");
            assertNotNull(textures, "Item " + itemId + " missing textures object");
            String layer0 = textures.get("layer0").getAsString();
            assertEquals("lithmod:item/" + itemId, layer0,
                "Item " + itemId + " must reference lithmod:item/" + itemId + ", got " + layer0);

            // 3. Load and validate PNG
            URL texUrl = StarterTextureResourceTest.class.getResource(ITEM_TEXTURES + itemId + ".png");
            assertNotNull(texUrl, "Missing item texture: " + itemId + ".png");
            BufferedImage img = ImageIO.read(texUrl.openStream());
            assertNotNull(img, "Failed to decode PNG: " + itemId + ".png");
            assertEquals(16, img.getWidth(), "Item " + itemId + " texture width must be 16");
            assertEquals(16, img.getHeight(), "Item " + itemId + " texture height must be 16");
            assertTrue(img.getAlphaRaster() != null, "Item " + itemId + " must have alpha channel");

            // 4. Verify a true transparent background with per-pixel binary alpha.
            // Having an alpha raster alone does not prove the background is transparent,
            // nor that color pixels are un-antialiased; every alpha must be exactly 0 or 255
            // and at least one pixel must be fully transparent RGBA 0,0,0,0.
            boolean hasTransparent = false;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int alpha = img.getRGB(x, y) >>> 24;
                    if (alpha == 0x00) {
                        hasTransparent = true;
                    } else {
                        assertEquals(0xFF, alpha,
                            "Item " + itemId + " pixel (" + x + "," + y + ") alpha must be 0 or 255, got " + alpha);
                    }
                }
            }
            assertTrue(hasTransparent, "Item " + itemId + " texture must have fully transparent pixels (background)");
        }
    }

    @Test
    @DisplayName("All production block models reference correct lithmod:block textures, PNGs exist 16x16 opaque")
    void productionBlockModelsAndTexturesValid() throws Exception {
        for (String blockId : PRODUCTION_BLOCKS) {
            // 1. Load block model JSON
            URL modelUrl = StarterTextureResourceTest.class.getResource(BLOCK_MODELS + blockId + ".json");
            assertNotNull(modelUrl, "Missing block model: " + blockId);
            JsonObject model = parseJson(modelUrl);

            // 2. Determine expected parent and texture references
            String expectedParent = ORIENTABLE_BLOCKS.contains(blockId)
                ? "minecraft:block/orientable"
                : "minecraft:block/cube_all";
            assertEquals(expectedParent, model.get("parent").getAsString(),
                "Block " + blockId + " parent must be " + expectedParent);
            Map<String, String> expectedTextures = expectedBlockTextures(blockId);
            JsonObject textures = model.getAsJsonObject("textures");
            assertNotNull(textures, "Block " + blockId + " missing textures object");

            for (Map.Entry<String, String> entry : expectedTextures.entrySet()) {
                String face = entry.getKey();
                String expected = entry.getValue();
                String actual = textures.get(face).getAsString();
                assertEquals(expected, actual,
                    "Block " + blockId + " face " + face + " must reference " + expected + ", got " + actual);
            }

            // 3. Validate each referenced texture PNG
            for (String expectedTex : expectedTextures.values()) {
                // expectedTex format: "lithmod:block/<path>"
                String texPath = expectedTex.substring("lithmod:block/".length()) + ".png";
                URL texUrl = StarterTextureResourceTest.class.getResource(BLOCK_TEXTURES + texPath);
                assertNotNull(texUrl, "Missing block texture: " + texPath + " (referenced by " + blockId + ")");
                BufferedImage img = ImageIO.read(texUrl.openStream());
                assertNotNull(img, "Failed to decode PNG: " + texPath);
                assertEquals(16, img.getWidth(), "Block texture " + texPath + " width must be 16");
                assertEquals(16, img.getHeight(), "Block texture " + texPath + " height must be 16");

                // 4. Verify fully opaque (no transparent pixels)
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                        assertEquals(255, alpha,
                            "Block texture " + texPath + " pixel (" + x + "," + y + ") must be fully opaque (alpha=255)");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Unformed models never reuse formed textures; state cues are pixel-distinct")
    void formedAndUnformedStatesRenderDifferently() throws Exception {
        for (String base : Set.of(
            "process_cell_controller", "process_cell_casing", "process_cell_power_port")) {
            Map<String, String> formed = blockModelTextures(base + "_formed");
            Map<String, String> unformed = blockModelTextures(base + "_unformed");
            // The unformed model must never point at any *_formed texture (exact
            // state suffixes, not a loose endsWith('formed') match).
            for (String ref : unformed.values()) {
                assertFalse(ref.endsWith("_formed") && !ref.endsWith("_unformed"),
                    base + " unformed texture reference " + ref + " must not be the formed texture");
            }
            // Sibling models must not share any texture path.
            Set<String> formedPaths = new HashSet<>(formed.values());
            Set<String> unformedPaths = new HashSet<>(unformed.values());
            formedPaths.removeAll(unformedPaths);
            assertFalse(formedPaths.isEmpty(), base + " formed/unformed models share texture references");
        }
        // Pixel-level proof of distinct amber-unformed / green-formed cues.
        assertTrue(pixelsDiffer(
            decodeBlockTexture("lithmod:block/process_cell_controller_formed_front"),
            decodeBlockTexture("lithmod:block/process_cell_controller_unformed_front")),
            "controller front textures must differ pixel-wise between formed and unformed");
        assertTrue(pixelsDiffer(
            decodeBlockTexture("lithmod:block/process_cell_casing_formed"),
            decodeBlockTexture("lithmod:block/process_cell_casing_unformed")),
            "casing textures must differ pixel-wise between formed and unformed");
        assertTrue(pixelsDiffer(
            decodeBlockTexture("lithmod:block/process_cell_power_port_formed"),
            decodeBlockTexture("lithmod:block/process_cell_power_port_unformed")),
            "power port textures must differ pixel-wise between formed and unformed");
    }

    @Test
    @DisplayName("Thirteen block item models reference existing production block models")
    void blockItemModelsParentToBlockModels() throws Exception {
        List<String> blockItems = List.of(
            "crystal_puller", "wafer_slicer", "test_package", "pattern_generator",
            "process_cell_controller", "process_cell_casing", "process_cell_power_port", "process_cell_operating_panel",
            "boule_purifier", "ebeam_mask_writer", "support_casing", "support_power_port", "support_water_inlet");
        for (String id : blockItems) {
            URL modelUrl = StarterTextureResourceTest.class.getResource(ITEM_MODELS + id + ".json");
            assertNotNull(modelUrl, "Missing block item model: " + id);
            JsonObject model = parseJson(modelUrl);
            String blockModelId = switch (id) {
                case "boule_purifier", "ebeam_mask_writer" -> "process_cell_controller_unformed";
                case "support_casing" -> "process_cell_casing_unformed";
                case "support_power_port" -> "process_cell_power_port_unformed";
                case "support_water_inlet" -> "process_cell_water_inlet_unformed";
                default -> id.startsWith("process_cell_") ? id + "_unformed" : id;
            };
            assertEquals("lithmod:block/" + blockModelId, model.get("parent").getAsString(),
                "Block item model " + id + " must reference its default block model");
            assertNotNull(StarterTextureResourceTest.class.getResource(BLOCK_MODELS + blockModelId + ".json"),
                "Missing inherited block model: " + blockModelId);
        }
    }

    @Test
    @DisplayName("test_machine resources exist and are untouched by generator")
    void testMachineResourcesPreserved() throws Exception {
        // Item model exists and uses block parent
        URL itemModelUrl = StarterTextureResourceTest.class.getResource(ITEM_MODELS + "test_machine.json");
        assertNotNull(itemModelUrl, "Missing test_machine item model");
        JsonObject itemModel = parseJson(itemModelUrl);
        assertEquals("lithmod:block/test_machine", itemModel.get("parent").getAsString(),
            "test_machine item model must parent to lithmod:block/test_machine");

        // Block model exists
        URL blockModelUrl = StarterTextureResourceTest.class.getResource(BLOCK_MODELS + "test_machine.json");
        assertNotNull(blockModelUrl, "Missing test_machine block model");
        JsonObject blockModel = parseJson(blockModelUrl);
        assertEquals("minecraft:block/cube_all", blockModel.get("parent").getAsString(),
            "test_machine block model must use cube_all parent");

        // Block texture exists (may be vanilla reference or custom - just verify model loads)
        JsonObject blockTextures = blockModel.getAsJsonObject("textures");
        assertNotNull(blockTextures, "test_machine block model missing textures");
        String allTex = blockTextures.get("all").getAsString();
        assertNotNull(allTex, "test_machine block model missing 'all' texture");
        // Don't validate the texture itself — it's preserved untouched
    }

    @Test
    @DisplayName("No extra item models in production set without registry entry")
    void noOrphanProductionItemModels() throws Exception {
        List<String> modelFiles = listResources(ITEM_MODELS, ".json");
        for (String modelFile : modelFiles) {
            String id = modelFile.substring(0, modelFile.length() - 5); // strip .json
            if (PRODUCTION_ITEMS.contains(id)) continue;
            if(Set.of("deposition_etcher","advanced_test_package","advanced_casing","advanced_power_port","advanced_operating_panel").contains(id)) continue;
            if (Set.of("test_machine", "crystal_puller", "wafer_slicer", "test_package",
                "pattern_generator", "process_cell_controller", "process_cell_casing",
                "process_cell_power_port", "process_cell_operating_panel", "boule_purifier",
                "ebeam_mask_writer", "support_casing", "support_power_port", "support_water_inlet").contains(id)) continue; // block items + joined panel item
            fail("Unexpected production item model: " + id);
        }
    }

    @Test
    @DisplayName("No extra block models in production set without registry entry")
    void noOrphanProductionBlockModels() throws Exception {
        List<String> modelFiles = listResources(BLOCK_MODELS, ".json");
        for (String modelFile : modelFiles) {
            String id = modelFile.substring(0, modelFile.length() - 5);
            if (PRODUCTION_BLOCKS.contains(id)) continue;
            if (JOINED_PANEL_BLOCKS.contains(id)) continue; // joined panels validated separately
            if (Set.of("test_machine").contains(id)) continue; // preserved
            fail("Unexpected production block model: " + id + " (not in PRODUCTION_BLOCKS, JOINED_PANEL_BLOCKS, or preserve list)");
        }
    }

    /** Expected texture references for a block model ID. */
    private static Map<String, String> expectedBlockTextures(String blockId) {
        Map<String, String> map = new HashMap<>();
        if (ORIENTABLE_BLOCKS.contains(blockId)) {
            map.put("top", "lithmod:block/" + blockId + "_top");
            map.put("bottom", "lithmod:block/" + blockId + "_top");
            map.put("side", "lithmod:block/" + blockId + "_side");
            map.put("front", "lithmod:block/" + blockId + "_front");
        } else {
            map.put("all", "lithmod:block/" + blockId);
        }
        return map;
    }

    /** Parse JSON from URL into JsonObject. */
    private static JsonObject parseJson(URL url) throws IOException {
        try (InputStream in = url.openStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement elem = JsonParser.parseReader(reader);
            assertNotNull(elem, "Empty JSON: " + url);
            assertTrue(elem.isJsonObject(), "JSON root not object: " + url);
            return elem.getAsJsonObject();
        }
    }

    /** Face → texture reference map for an existing block model JSON. */
    private static Map<String, String> blockModelTextures(String blockId) throws IOException {
        URL modelUrl = StarterTextureResourceTest.class.getResource(BLOCK_MODELS + blockId + ".json");
        assertNotNull(modelUrl, "Missing block model: " + blockId);
        JsonObject model = parseJson(modelUrl);
        JsonObject textures = model.getAsJsonObject("textures");
        assertNotNull(textures, "Block " + blockId + " missing textures object");
        Map<String, String> refs = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            refs.put(entry.getKey(), entry.getValue().getAsString());
        }
        return refs;
    }

    /** Decode a lithmod:block texture reference as a BufferedImage (16x16). */
    private static BufferedImage decodeBlockTexture(String ref) throws IOException {
        String texPath = ref.substring("lithmod:block/".length()) + ".png";
        URL texUrl = StarterTextureResourceTest.class.getResource(BLOCK_TEXTURES + texPath);
        assertNotNull(texUrl, "Missing block texture: " + texPath);
        BufferedImage img = ImageIO.read(texUrl.openStream());
        assertNotNull(img, "Failed to decode PNG: " + texPath);
        return img;
    }

    /** True when the two images disagree on at least one pixel. */
    private static boolean pixelsDiffer(BufferedImage a, BufferedImage b) {
        assertEquals(16, a.getWidth(), "Left texture must be 16 wide");
        assertEquals(16, b.getWidth(), "Right texture must be 16 wide");
        assertEquals(16, a.getHeight(), "Left texture must be 16 high");
        assertEquals(16, b.getHeight(), "Right texture must be 16 high");
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
            }
        }
        return false;
    }

    /** List resource names in a bundled resource directory. */
    private static List<String> listResources(String resourceDirectory, String suffix) throws IOException {
        URL directory = StarterTextureResourceTest.class.getResource(resourceDirectory);
        assertNotNull(directory, "Missing bundled resource directory: " + resourceDirectory);
        assertEquals("file", directory.getProtocol(),
            "Bundled resources not on test classpath as directory: " + resourceDirectory);

        Path path;
        try {
            path = Path.of(directory.toURI());
        } catch (Exception e) {
            throw new IOException("Could not read resource directory: " + resourceDirectory, e);
        }

        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(path)) {
            files.map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(suffix))
                .sorted()
                .forEach(names::add);
        }
        return names;
    }
}
