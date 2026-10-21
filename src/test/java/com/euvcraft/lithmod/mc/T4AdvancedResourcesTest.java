package com.euvcraft.lithmod.mc;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent registry-facing coverage of both shapes, every legal state and actual texture references. */
class T4AdvancedResourcesTest {
 private static final List<String> IDS=List.of("deposition_etcher","advanced_test_package","advanced_casing","advanced_power_port","advanced_operating_panel");
 private static final String ASSET="/assets/lithmod/";
 private final Set<String> visitedModels=new HashSet<>(),visitedTextures=new HashSet<>();
 private JsonObject json(String path) throws Exception {
  var resource=getClass().getResource(path);assertNotNull(resource,"Missing "+path);
  try(var reader=new InputStreamReader(resource.openStream(),StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
 }
 private void model(String reference) throws Exception {
  assertTrue(reference.startsWith("lithmod:"),"Unexpected model namespace "+reference);
  if(!visitedModels.add(reference))return;
  var object=json(ASSET+"models/"+reference.substring(8)+".json");
  if(object.has("parent")){
   String parent=object.get("parent").getAsString();
   if(parent.startsWith("lithmod:"))model(parent);
   else assertTrue(Set.of("minecraft:block/cube","minecraft:block/cube_all").contains(parent),"Unexpected parent "+parent);
  }
  if(object.has("textures"))for(var entry:object.getAsJsonObject("textures").entrySet()){
   String texture=entry.getValue().getAsString();
   if(texture.startsWith("#"))continue;
   assertTrue(texture.startsWith("lithmod:block/t4_"));
   if(!visitedTextures.add(texture))continue;
   var location=getClass().getResource(ASSET+"textures/"+texture.substring(8)+".png");assertNotNull(location,texture);
   var image=ImageIO.read(location);assertNotNull(image);assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
   for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(255,image.getRGB(x,y)>>>24,texture);
  }
 }
 @Test void everyLegalBlockStateSelectsExactlyOneExistingModel() throws Exception {
  for(String id:IDS){
   var variants=json(ASSET+"blockstates/"+id+".json").getAsJsonObject("variants");
   for(String facing:List.of("north","east","south","west"))for(boolean formed:List.of(false,true))
    for(boolean large:List.of(false,true))for(int part=0;part<45;part++){
     var state=Map.of("facing",facing,"formed",Boolean.toString(formed),"large",Boolean.toString(large),"part",Integer.toString(part));
     int matches=0;
     for(var entry:variants.entrySet()){
      boolean match=true;for(String condition:entry.getKey().split(",")){
       String[] term=condition.split("=");if(!Objects.equals(state.get(term[0]),term[1])){match=false;break;}
      }
      if(match){matches++;var variant=entry.getValue().getAsJsonObject();model(variant.get("model").getAsString());
       int expected=switch(facing){case "east"->90;case "south"->180;case "west"->270;default->0;};
       assertEquals(expected,variant.has("y")?variant.get("y").getAsInt():0);
      }
     }
     assertEquals(1,matches,id+" "+state);
    }
  }
  assertTrue(visitedModels.size()>=232);assertTrue(visitedTextures.size()>=100);
 }
 @Test void casingCoordinatesFollowIndependentShapeAndFaceGeometry() throws Exception {
  for(boolean large:List.of(false,true)){
   int width=large?3:2,height=large?3:2,depth=large?5:3;
   String size=large?"large":"small";
   for(int part=0;part<width*height*depth;part++){
    int u=part/(height*depth),v=part%(height*depth)/depth,d=part%depth;
    var textures=json(ASSET+"models/block/t4_casing_"+size+"_part"+part+"_formed.json").getAsJsonObject("textures");
    var expected=Map.of("north",new int[]{width-1-u,height-1-v},"south",new int[]{u,height-1-v},
     "west",new int[]{d,height-1-v},"east",new int[]{depth-1-d,height-1-v},
     "up",new int[]{u,d},"down",new int[]{u,depth-1-d});
    for(var face:expected.entrySet())assertEquals("lithmod:block/t4_"+size+"_"+face.getKey()+"_tx"+face.getValue()[0]+"_ty"+face.getValue()[1],textures.get(face.getKey()).getAsString());
   }
  }
 }
 @Test void allFiveBlocksHaveTitlesClientDefinitionsLootMiningAndNonCircularRecipes() throws Exception {
  var lang=json(ASSET+"lang/en_us.json");
  var pickaxe=json("/data/minecraft/tags/block/mineable/pickaxe.json").getAsJsonArray("values");
  var tools=json("/data/minecraft/tags/block/needs_stone_tool.json").getAsJsonArray("values");
  for(String id:IDS){
   assertFalse(lang.get("block.lithmod."+id).getAsString().isBlank());
   assertFalse(lang.get("item.lithmod."+id).getAsString().isBlank());
   var definition=json(ASSET+"items/"+id+".json").getAsJsonObject("model");
   assertEquals("lithmod:item/"+id,definition.get("model").getAsString());model("lithmod:item/"+id);
   assertTrue(pickaxe.contains(new JsonPrimitive("lithmod:"+id)));
   assertTrue(tools.contains(new JsonPrimitive("lithmod:"+id)));
   var loot=json("/data/lithmod/loot_table/blocks/"+id+".json");
   assertEquals("lithmod:"+id,loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
   var recipe=json("/data/lithmod/recipe/crafting/"+id+".json");
   assertEquals("lithmod:"+id,recipe.getAsJsonObject("result").get("id").getAsString());
   String ingredients=recipe.getAsJsonObject("key").toString();
   assertTrue(ingredients.contains("lithmod:logic_chip_t3"));
   assertFalse(ingredients.contains("_t4"));
   if(id.equals("advanced_test_package"))assertFalse(ingredients.contains("deposition_etcher"));
  }
 }
}
