package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.FabSettings;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrapRecyclerResourceTest {
    private JsonObject json(String path)throws Exception{try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
    @Test void recoveryCannotMultiplyBundledSilicon()throws Exception{
        var crystal=json("/data/lithmod/machine_recipes/crystal_puller.json");int silicon=crystal.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("count").getAsInt();
        var defaults=FabSettings.defaults();for(var kind:new String[]{"wafer","chip","assembly"}){
            var r=json("/data/lithmod/machine_recipes/recycle_"+kind+"_scrap.json");var ingredient=r.getAsJsonArray("ingredients").get(0).getAsJsonObject();var result=r.getAsJsonObject("result");
            assertEquals("scrap_recycler",r.get("machine").getAsString());assertEquals("lithmod:"+kind+"_scrap",ingredient.get("item").getAsString());assertEquals("lithmod:silicon",result.get("item").getAsString());
            int scrapPerBoule=defaults.wafersPerBoule()*(kind.equals("wafer")?1:defaults.chipsPerWafer());
            // Multiple lots include cross-lot remainder recovery; all-failure is the worst case.
            for(int boules:new int[]{1,16,64,1000}){long recovered=(long)boules*scrapPerBoule/ingredient.get("count").getAsInt()*result.get("count").getAsInt();assertTrue(recovered<(long)boules*silicon,"Recovery must be lossy across lot boundaries: "+kind);}
            assertEquals(400,r.get("ticks").getAsInt());assertEquals(64,ingredient.get("count").getAsInt());
        }
    }
    @Test void allAssetsResolveAndBlockDropsItself()throws Exception{
        var state=json("/assets/lithmod/blockstates/scrap_recycler.json");assertEquals("lithmod:block/scrap_recycler",state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        var model=json("/assets/lithmod/models/block/scrap_recycler.json");for(var texture:model.getAsJsonObject("textures").entrySet())assertNotNull(getClass().getResource("/assets/lithmod/textures/"+texture.getValue().getAsString().replace("lithmod:","")+".png"),texture.getKey());
        json("/assets/lithmod/models/item/scrap_recycler.json");json("/assets/lithmod/items/scrap_recycler.json");
        assertTrue(json("/data/lithmod/loot_table/blocks/scrap_recycler.json").toString().contains("lithmod:scrap_recycler"));assertEquals("lithmod:scrap_recycler",json("/data/lithmod/recipe/crafting/scrap_recycler.json").getAsJsonObject("result").get("id").getAsString());
        var d=json("/data/lithmod/machines/scrap_recycler.json");assertEquals(40000,d.get("energy_capacity").getAsInt());assertEquals(40,d.get("energy_per_tick").getAsInt());
        assertTrue(json("/data/minecraft/tags/block/mineable/pickaxe.json").toString().contains("lithmod:scrap_recycler"));assertTrue(json("/assets/lithmod/lang/en_us.json").has("block.lithmod.scrap_recycler"));
    }
}
