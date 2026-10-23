package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AssemblyVisualResourceTest {
    static final Set<String> MODELS = Set.of("pick_and_place_formed", "assembly_casing_formed", "assembly_power_port_formed");
    private static final String ROOT = "/assets/lithmod/";

    private static JsonObject json(String path) throws IOException {
        try (var in = AssemblyVisualResourceTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void everyStateResolvesExactlyOnceWithCorrectRotationAndAppearance() throws Exception {
        for (String id : List.of("pick_and_place", "assembly_casing", "assembly_power_port")) {
            var variants = json("blockstates/" + id + ".json").getAsJsonObject("variants");
            var facings = List.of("north", "east", "south", "west");
            for (String facing : facings) for (boolean formed : List.of(false, true))
                for (boolean large : List.of(false, true)) for (int part = 0; part <= 44; part++) {
                    var state = Map.of("facing", facing, "formed", "" + formed, "large", "" + large, "part", "" + part);
                    var matches = variants.entrySet().stream().filter(e -> Arrays.stream(e.getKey().split(",")).allMatch(k -> {
                        var pair = k.split("="); return pair.length == 2 && pair[1].equals(state.get(pair[0]));
                    })).toList();
                    assertEquals(1, matches.size(), id + state);
                    var model = matches.getFirst().getValue().getAsJsonObject();
                    assertEquals("lithmod:block/" + id + (formed ? "_formed" : ""), model.get("model").getAsString());
                    assertEquals(facings.indexOf(facing) * 90, model.has("y") ? model.get("y").getAsInt() : 0);
                }
        }
    }

    @Test void formedTexturesExistAreOpaqueAndDifferFromUnformedItems() throws Exception {
        for (String id : List.of("pick_and_place", "assembly_casing", "assembly_power_port")) {
            var unformed = json("models/block/" + id + ".json").getAsJsonObject("textures");
            var formed = json("models/block/" + id + "_formed.json").getAsJsonObject("textures");
            assertEquals("lithmod:block/" + id, json("models/item/" + id + ".json").get("parent").getAsString());
            var oldPath = "textures/" + unformed.get("all").getAsString().substring(8) + ".png";
            byte[] old;
            try (var in = getClass().getResourceAsStream(ROOT + oldPath)) { assertNotNull(in); old = in.readAllBytes(); }
            for (var ref : formed.entrySet()) {
                String path = ROOT + "textures/" + ref.getValue().getAsString().substring(8) + ".png";
                try (var in = getClass().getResourceAsStream(path)) {
                    assertNotNull(in, path);
                    byte[] bytes = in.readAllBytes();
                    assertFalse(Arrays.equals(old, bytes), "Formed face still uses unformed art: " + id + ref.getKey());
                    var image = ImageIO.read(new ByteArrayInputStream(bytes));
                    assertNotNull(image); assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) assertEquals(255, image.getRGB(x,y) >>> 24);
                }
            }
        }
    }
}
