package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scans the bundled client resources of the chips and consumables.
 *
 * <p>In Minecraft 26.3 an item renders through two files and names itself through a
 * third: the client item definition in {@code assets/lithmod/items/<id>.json}, the item
 * model in {@code assets/lithmod/models/item/<id>.json} and the
 * {@code item.lithmod.<id>} entry in {@code assets/lithmod/lang/en_us.json}. A missing
 * definition or model shows as the missing-model cube, a missing language entry shows
 * as the raw registry path in the creative tab, and neither fails a build.</p>
 *
 * <p>This is the resource side of the check and runs without the game. The registry
 * side, which asks the item registry what actually exists, is the game test
 * {@code lithmod:item_assets} in {@code ChipGameTests}.</p>
 */
class ItemAssetResourceTest {

    private static final String ITEM_DEFINITIONS = "/assets/" + EUVCRAFTMod.MODID + "/items/";
    private static final String ITEM_MODELS = "/assets/" + EUVCRAFTMod.MODID + "/models/item/";
    private static final String LANG = "/assets/" + EUVCRAFTMod.MODID + "/lang/en_us.json";

    @Test
    @DisplayName("every client item definition has a model and a language entry")
    void everyItemDefinitionIsComplete() throws Exception {
        Map<String, String> lang = langEntries();
        List<String> definitions = resourceNames(ITEM_DEFINITIONS, ".json");

        assertTrue(definitions.size() >= 9,
            "expected the two FOUP variants and the seven items this milestone registers, found "
                + definitions.size() + " item definitions");

        for (String id : definitions) {
            assertNotNull(ItemAssetResourceTest.class.getResource(ITEM_MODELS + id + ".json"),
                "no item model for " + id + ", the item renders as the missing-model cube");
            assertTrue(lang.containsKey("item." + EUVCRAFTMod.MODID + "." + id),
                "no language entry for " + id + ", the creative tab shows its registry path");
        }
    }

    @Test
    @DisplayName("no item model is left without a client item definition")
    void noOrphanModels() throws Exception {
        List<String> definitions = resourceNames(ITEM_DEFINITIONS, ".json");
        List<String> models = resourceNames(ITEM_MODELS, ".json");

        assertTrue(models.size() >= 9, "expected at least nine item models, found " + models.size());
        for (String id : models) {
            assertTrue(definitions.contains(id), "item model " + id + " has no client item definition");
        }
    }

    @Test
    @DisplayName("the tab, tooltip, die type and tier language entries exist")
    void tabAndTooltipKeysExist() throws Exception {
        Map<String, String> lang = langEntries();

        for (String key : List.of("itemGroup.lithmod", "itemGroup.lithmod.chips", "tooltip.lithmod.chip",
            "dieType.lithmod.logic", "dieType.lithmod.memory", "tier.lithmod.t1", "tier.lithmod.t2")) {
            assertTrue(lang.containsKey(key), "missing language entry " + key);
        }
    }

    private static Map<String, String> langEntries() throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (InputStream input = ItemAssetResourceTest.class.getResourceAsStream(LANG)) {
            assertNotNull(input, "missing bundled language file " + LANG);
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                entries.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return entries;
    }

    /**
     * Lists the resource names in a bundled resource directory.
     *
     * <p>The resources are on the test classpath as a directory, which is what Gradle
     * puts there, so the classloader URL is a file URL that can be listed.</p>
     */
    private static List<String> resourceNames(String resourceDirectory, String suffix) throws IOException {
        URL directory = ItemAssetResourceTest.class.getResource(resourceDirectory);
        assertNotNull(directory, "missing bundled resource directory " + resourceDirectory);
        assertEquals("file", directory.getProtocol(),
            "the bundled resources are not on the test classpath as a directory, so they cannot be listed");

        Path path;
        try {
            path = Path.of(directory.toURI());
        } catch (Exception e) {
            throw new IOException("Could not read the bundled resource directory " + resourceDirectory, e);
        }

        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(path)) {
            files.map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(suffix))
                .map(name -> name.substring(0, name.length() - suffix.length()))
                .sorted()
                .forEach(names::add);
        }
        return names;
    }
}