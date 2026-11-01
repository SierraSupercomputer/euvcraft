package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import com.google.gson.*;
import org.junit.jupiter.api.Test;

class BeamlineModelResourceTest {
    @Test void ownerGeometryRotationsAndUvsArePreserved()throws Exception {
        var source=JsonParser.parseString(Files.readString(Path.of("tools/models/beamline_pipe.json"))).getAsJsonObject();
        var model=CleanroomAssetResourceTest.json("/assets/lithmod/models/block/euv_beam_line.json");
        assertEquals(source.get("elements"),model.get("elements"));assertEquals(14,model.getAsJsonArray("elements").size());
        assertEquals(source.get("credit"),model.get("credit"));assertFalse(model.has("groups"));
        assertEquals("lithmod:block/euv_beam_line",model.getAsJsonObject("textures").get("1").getAsString());
        for(var element:model.getAsJsonArray("elements"))for(var face:element.getAsJsonObject().getAsJsonObject("faces").entrySet())assertEquals("#1",face.getValue().getAsJsonObject().get("texture").getAsString());
    }

    @Test void separatePngIsPackagedByteForByteWithCorrectDimensions()throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/lithmod/textures/block/euv_beam_line.png")){
            assertNotNull(in);assertArrayEquals(Files.readAllBytes(Path.of("tools/models/beamline_pipe.png")),in.readAllBytes());
        }
        try(var in=getClass().getResourceAsStream("/assets/lithmod/textures/block/euv_beam_line.png")){
            var image=ImageIO.read(in);assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());
        }
    }

    @Test void northSouthBaseHasBothOtherAxisVariantsAndSharedItemModel()throws Exception {
        var variants=CleanroomAssetResourceTest.json("/assets/lithmod/blockstates/euv_beam_line.json").getAsJsonObject("variants");
        assertEquals(Set.of("axis=x","axis=y","axis=z"),variants.keySet());
        assertEquals(90,variants.getAsJsonObject("axis=x").get("y").getAsInt());assertEquals(90,variants.getAsJsonObject("axis=y").get("x").getAsInt());
        assertFalse(variants.getAsJsonObject("axis=z").has("x"));assertFalse(variants.getAsJsonObject("axis=z").has("y"));
        assertEquals("lithmod:block/euv_beam_line",CleanroomAssetResourceTest.json("/assets/lithmod/models/item/euv_beam_line.json").get("parent").getAsString());
    }
}
