package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.euvcraft.lithmod.core.viewer.ViewerPage.*;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewerCatalogCodecTest {
    private static ViewerPage page(String id){return new ViewerPage(id,"hydrogen_purifier",List.of(new Ingredient(Kind.FLUID,"arcforge:hydrogen",8000,"Raw gas","",0,0,0)),List.of(new Ingredient(Kind.GAS,"lithmod:fab_hydrogen",6000,"Fab gas","",0,0,0)),List.of(),List.of("Public process description"),400,600);}
    @Test void roundTripIsOnlyPublicDescriptorData(){
        var page=page("lithmod:hydrogen");String json=ViewerCatalogCodec.encode(List.of(page));assertEquals(List.of(page),ViewerCatalogCodec.decode(json));
        assertEquals(Set.of("id","machine","inputs","outputs","catalysts","notes","ticks","fePerTick"),JsonParser.parseString(json).getAsJsonArray().get(0).getAsJsonObject().keySet());
        assertFalse(json.contains("lotId"));assertFalse(json.contains("cumulativeRoll"));assertFalse(json.contains("g3Preparation"));
    }
    @Test void rejectDuplicateRowsAndUnknownStations(){
        var p=page("lithmod:hydrogen");assertThrows(IllegalArgumentException.class,()->ViewerCatalogCodec.encode(List.of(p,p)));
        String one=ViewerCatalogCodec.encode(List.of(p));assertThrows(IllegalArgumentException.class,()->ViewerCatalogCodec.decode(one.substring(0,one.length()-1)+","+one.substring(1)));
        String bad=ViewerCatalogCodec.encode(List.of(p)).replace("hydrogen_purifier","hydrogen_operating_panel");assertThrows(IllegalArgumentException.class,()->ViewerCatalogCodec.decode(bad));
    }
    @Test void schemaBoundsApplyWhenDecoding(){
        String valid=ViewerCatalogCodec.encode(List.of(page("lithmod:hydrogen")));
        assertThrows(RuntimeException.class,()->ViewerCatalogCodec.decode(valid.replace("\"amount\":8000","\"amount\":0")));
        assertThrows(RuntimeException.class,()->ViewerCatalogCodec.decode(valid.replace("\"notes\":[\"Public process description\"]","\"notes\":null")));
        assertThrows(RuntimeException.class,()->ViewerCatalogCodec.decode(valid.replace("\"kind\":\"FLUID\"","\"kind\":\"UNKNOWN\"")));
        assertThrows(IllegalArgumentException.class,()->ViewerCatalogCodec.decode(" ".repeat(ViewerCatalogCodec.MAX_CHARS+1)));
        assertEquals(List.of(),ViewerCatalogCodec.decode("[]"));
    }
    @Test void limitsIncludeTheNamespace(){
        assertThrows(IllegalArgumentException.class,()->new ViewerPage("a".repeat(193)+":b","hydrogen_purifier",List.of(),List.of(),List.of(),List.of(),0,0));
        assertThrows(IllegalArgumentException.class,()->Ingredient.item("a".repeat(193)+":b",1));
    }
}
