package com.euvcraft.lithmod.mc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T7GasResourceTest {
    private JsonObject json(String resource) throws IOException {
        try (var input=getClass().getResourceAsStream(resource)) {
            assertNotNull(input,resource);
            return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void sourceAndFlowingVariantsArePressurizedGasesWithDisplayMetadata() throws Exception {
        var tag=json("/data/arcforge/tags/fluid/gases.json");
        assertFalse(tag.get("replace").getAsBoolean(),"Do not replace Arcforge's gas catalogue");
        var properties=json("/data/arcforge/data_maps/fluid/gas_properties.json").getAsJsonObject("values");
        for (String id : new String[]{"lithmod:fab_hydrogen","lithmod:flowing_fab_hydrogen"}) {
            assertTrue(tag.getAsJsonArray("values").contains(new JsonPrimitive(id)),id);
            assertTrue(properties.getAsJsonObject(id).get("color").getAsString().matches("#[0-9A-Fa-f]{6}"));
        }
    }
    @Test void vacuumWallPassageIsDistinctFromTheGeneralServiceConduit() throws Exception {
        var tag=json("/data/lithmod/tags/block/cleanroom_shell_extensions.json");
        assertFalse(tag.get("replace").getAsBoolean());
        assertTrue(tag.getAsJsonArray("values").contains(new JsonPrimitive("lithmod:cleanroom_vacuum_port")));
        assertFalse(tag.getAsJsonArray("values").contains(new JsonPrimitive("lithmod:vacuum_service_conduit")),"An ordinary pipe is not a sealed room wall");
    }
}
