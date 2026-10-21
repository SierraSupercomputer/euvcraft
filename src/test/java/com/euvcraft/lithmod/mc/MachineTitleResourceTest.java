package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JUnit resource test: the production machine definition ids seen in
 * {@code data/lithmod/machines} must be exactly the known seven, and each must
 * have a non-blank {@code block.lithmod.<id>} title in the bundled en_us.json,
 * with the Process Cell controller titled exactly "Lithography Machine".
 * A missing machine definition or language resource fails, it is never skipped.
 */
class MachineTitleResourceTest {

    private static final String MACHINES_DIR = "/data/lithmod/machines/";
    private static final String LANG_FILE = "/assets/lithmod/lang/en_us.json";
    /** The fixture machine, gated behind game-test fixtures and never production. */
    private static final String TEST_ONLY_ID = "test_machine";

    /** The seven production machine definition ids observed in data/lithmod/machines. */
    private static final Set<String> PRODUCTION_IDS = Set.of(
            "crystal_puller", "wafer_slicer", "pattern_generator", "test_package", "process_cell",
            "boule_purifier", "ebeam_mask_writer", "deposition_etcher", "advanced_test_package");

    @Test
    @DisplayName("every production machine definition has a non-blank block.lithmod.<id> title in en_us.json")
    void productionMachineTitlesExistInLanguageFile() throws Exception {
        JsonObject lang = loadEnUsLang();
        Set<String> machineIds = machineDefinitionIds();

        Set<String> unexpected = new HashSet<>(machineIds);
        unexpected.removeAll(PRODUCTION_IDS);
        assertTrue(unexpected.isEmpty(), "unexpected machine definitions on the classpath: " + unexpected);

        Set<String> missing = new HashSet<>(PRODUCTION_IDS);
        missing.removeAll(machineIds);
        assertTrue(missing.isEmpty(), "missing production machine definitions: " + missing);

        for (String id : PRODUCTION_IDS) {
            String key = "block.lithmod." + id;
            String title = lang.get(key) == null ? null : lang.get(key).getAsString();
            assertNotNull(title, "en_us.json is missing " + key);
            assertFalse(title.isBlank(), "en_us.json has a blank title for " + key);
            if (id.equals("process_cell")) {
                assertEquals("Lithography Machine", title,
                        "process_cell title must be exactly 'Lithography Machine'");
            }
        }
    }

    /** Loads the bundled en_us.json through Gson. */
    private static JsonObject loadEnUsLang() throws IOException {
        try (InputStream input = MachineTitleResourceTest.class.getResourceAsStream(LANG_FILE)) {
            assertNotNull(input, "missing bundled language file: " + LANG_FILE);
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            assertTrue(root.isJsonObject(), LANG_FILE + " must be a JSON object");
            return root.getAsJsonObject();
        }
    }

    /**
     * Lists every machine definition id under {@code data/lithmod/machines}
     * on the test classpath, excluding the fixture machine.
     */
    private static Set<String> machineDefinitionIds() throws IOException {
        URL directory = MachineTitleResourceTest.class.getResource(MACHINES_DIR);
        assertNotNull(directory, "missing bundled resource directory: " + MACHINES_DIR);
        assertEquals("file", directory.getProtocol(),
                "bundled resources must be on the test classpath as a directory: " + MACHINES_DIR);

        Path path;
        try {
            path = Path.of(directory.toURI());
        } catch (Exception e) {
            throw new IOException("could not read resource directory: " + MACHINES_DIR, e);
        }

        try (Stream<Path> files = Files.list(path)) {
            return files.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .filter(id -> !id.equals(TEST_ONLY_ID))
                    .collect(Collectors.toSet());
        }
    }
}
