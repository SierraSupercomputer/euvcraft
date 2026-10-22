package com.euvcraft.lithmod.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
class ScannerResourceTest {
 private static final Path ROOT=Path.of("src/main/resources");
 private static final List<String> IDS=List.of("scanner_controller","scanner_operating_panel","scanner_calibration_panel","scanner_exposure_module","scanner_optics_module","scanner_stage_module","scanner_filtration_module","scanner_metrology_module");
 private static JsonObject json(String path)throws Exception{return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();}
 @Test void everyBlockHasCompleteResourcesAndCraftableRecipe()throws Exception{
  var lang=json("assets/lithmod/lang/en_us.json");var tag=json("data/minecraft/tags/block/mineable/pickaxe.json").getAsJsonArray("values");var pickaxe=new HashSet<String>();for(var v:tag)pickaxe.add(v.isJsonPrimitive()?v.getAsString():v.getAsJsonObject().get("id").getAsString());
  for(var id:IDS){
   assertTrue(pickaxe.contains("lithmod:"+id));assertFalse(lang.get("block.lithmod."+id).getAsString().isBlank());assertEquals(lang.get("block.lithmod."+id),lang.get("item.lithmod."+id));
   var state=json("assets/lithmod/blockstates/"+id+".json");assertFalse(state.getAsJsonObject("variants").isEmpty());
   for(var entry:state.getAsJsonObject("variants").entrySet()){
    var ref=entry.getValue().getAsJsonObject().get("model").getAsString();assertTrue(Files.exists(ROOT.resolve("assets/lithmod/models/"+ref.substring(8)+".json")),ref);
   }
   var model=json("assets/lithmod/models/block/"+id+".json");for(var e:model.getAsJsonObject("textures").entrySet()){
    String ref=e.getValue().getAsString();var path=ROOT.resolve("assets/lithmod/textures/"+ref.substring(8)+".png");assertTrue(Files.exists(path),ref);var png=ImageIO.read(path.toFile());assertNotNull(png);assertEquals(16,png.getWidth());assertEquals(16,png.getHeight());
   }
   json("assets/lithmod/items/"+id+".json");json("assets/lithmod/models/item/"+id+".json");
   var recipe=json("data/lithmod/recipe/crafting/"+id+".json");assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());assertEquals("lithmod:"+id,recipe.getAsJsonObject("result").get("id").getAsString());assertEquals(1,recipe.getAsJsonObject("result").get("count").getAsInt());assertFalse(recipe.toString().contains("chip_t4"),"No circular T4 chip bootstrap");
   var loot=json("data/lithmod/loot_table/blocks/"+id+".json");assertTrue(loot.toString().contains("lithmod:"+id));
  }
 }
 @Test void physicalModulesConsumeTheirCorrespondingEquipment()throws Exception{
  String[] items={"arf_excimer_laser","arf_dry_lens","nanopositioning_stage","ulpa_filter","precision_overlay_scope"};
  for(int i=0;i<5;i++)assertEquals("lithmod:"+items[i],json("data/lithmod/recipe/crafting/"+IDS.get(i+3)+".json").getAsJsonObject("key").get("D").getAsString());
 }
 @Test void editableCalibrationParametersAreBounded()throws Exception{
  var def=json("data/lithmod/machines/lithography_scanner.json");assertEquals("lithography_scanner",def.get("id").getAsString());var p=def.getAsJsonObject("params");int quality=p.get("calibration_min_quality").getAsInt(),min=p.get("calibration_min_multiplier_permille").getAsInt(),max=p.get("calibration_max_multiplier_permille").getAsInt();assertTrue(quality>0&&quality<=100);assertTrue(min>0&&min<=max&&max<=1000);
 }
}
