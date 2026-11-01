package com.euvcraft.lithmod.mc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearFabResourceTest {
    private JsonObject json(String path)throws Exception{
        try(var stream=getClass().getResourceAsStream(path)){assertNotNull(stream,path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    @Test void splitRetainsOriginalCombinedProcessBudget()throws Exception{
        var dep=json("/data/lithmod/machines/deposition_etcher.json");var etch=json("/data/lithmod/machines/etch_cell.json");
        assertEquals(1200,dep.getAsJsonObject("params").get("duration").getAsInt()+etch.getAsJsonObject("params").get("duration").getAsInt());
        assertEquals(2,dep.getAsJsonObject("params").get("silicon_per_lot").getAsInt());assertEquals(1,etch.getAsJsonObject("params").get("etchant_per_lot").getAsInt());
        var charge=json("/data/lithmod/recipe/crafting/etchant_charge.json");assertEquals(1,charge.getAsJsonObject("result").get("count").getAsInt());
        assertEquals("lithmod:etchant_charge",charge.getAsJsonObject("result").get("id").getAsString());
    }
    @Test void everyNewBlockStateReferencesAnExistingModel()throws Exception{
        for(var id:new String[]{"etch_cell","fab_item_port"}){
            var state=json("/assets/lithmod/blockstates/"+id+".json");for(var entry:state.getAsJsonObject("variants").entrySet()){
                var variant=entry.getValue().getAsJsonObject();var model=variant.get("model").getAsString().replace("lithmod:","/assets/lithmod/models/")+".json";json(model);
            }
            json("/assets/lithmod/items/"+id+".json");json("/data/lithmod/loot_table/blocks/"+id+".json");json("/data/lithmod/recipe/crafting/"+id+".json");
        }
    }
    @Test void translatedTabsStagesAndItemsExist()throws Exception{
        var lang=json("/assets/lithmod/lang/en_us.json");for(var key:new String[]{"itemGroup.lithmod.resources","itemGroup.lithmod.fab_blocks","itemGroup.lithmod.items","item.lithmod.etch_cell","item.lithmod.fab_item_port","item.lithmod.etchant_charge","tooltip.lithmod.foup.stage.exposing"})assertTrue(lang.has(key),key);
    }
}
