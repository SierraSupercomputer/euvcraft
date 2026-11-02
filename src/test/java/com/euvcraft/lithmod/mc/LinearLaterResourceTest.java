package com.euvcraft.lithmod.mc;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LinearLaterResourceTest {
    static final Set<String> BLOCKS=Set.of("coating_track","wet_cleaner","fab_upw_inlet","g3_etch_cell","g3_fab_item_port","euv_fab_item_port");
    static final Set<String> MODELS=Set.of("coating_track","coating_track_formed","wet_cleaner","wet_cleaner_formed","fab_upw_inlet","fab_upw_inlet_formed","g3_etch_cell","g3_fab_item_port","g3_fab_item_port_formed","euv_fab_item_port","euv_fab_item_port_formed");
    private JsonObject json(String path)throws Exception{try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
    @Test void everyNewBlockHasNativeItemLootAndRecipe()throws Exception{
        var lang=json("/assets/lithmod/lang/en_us.json");for(var id:BLOCKS){assertTrue(lang.has("block.lithmod."+id)&&lang.has("item.lithmod."+id));json("/assets/lithmod/blockstates/"+id+".json");json("/assets/lithmod/models/item/"+id+".json");assertEquals("lithmod:item/"+id,json("/assets/lithmod/items/"+id+".json").getAsJsonObject("model").get("model").getAsString());assertEquals("lithmod:"+id,json("/data/lithmod/loot_table/blocks/"+id+".json").getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());json("/data/lithmod/recipe/crafting/"+id+".json");}
    }
    @Test void g3ProcessTimeAndEnergyBudgetIsSplitWithRinseMoved()throws Exception{
        var dep=json("/data/lithmod/machines/g3_deposition_etcher.json").getAsJsonObject("params");var etch=json("/data/lithmod/machines/g3_etch_cell.json").getAsJsonObject("params");
        int original=dep.get("vacuum_duration").getAsInt()+dep.get("rinse_duration").getAsInt()+dep.get("dry_duration").getAsInt();
        assertEquals(original,dep.get("linear_vacuum_duration").getAsInt()+dep.get("linear_dry_duration").getAsInt()+etch.get("linear_vacuum_duration").getAsInt()+etch.get("linear_dry_duration").getAsInt()+dep.get("linear_moved_rinse_duration").getAsInt());
        assertEquals(0,dep.get("linear_rinse_water_mb").getAsInt());assertEquals(4000,dep.get("linear_moved_rinse_water_mb").getAsInt());assertEquals(0,etch.get("linear_rinse_duration").getAsInt());
    }
    @Test void persistentDamageSurvivesControllerDrops()throws Exception{
        for(var id:List.of("coating_track","wet_cleaner","g3_etch_cell"))assertTrue(json("/data/lithmod/loot_table/blocks/"+id+".json").toString().contains("lithmod:contaminated_equipment"));
    }
}
