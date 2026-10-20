package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** JVM checks of finite native models, independent face coordinates and decoded joined pixels. */
class JoinedPanelResourceTest {
    private static final String ROOT="/assets/lithmod/";
    private static final List<String> ROLES=List.of("controller","casing","power_port","operating_panel");
    private static final List<String> FACES=List.of("north","south","west","east","up","down");
    private static final Map<String,Integer> ROTATIONS=Map.of("north",0,"east",90,"south",180,"west",270);
    private static final Set<String> KEYS=Set.of("north","south","west","east","up","down","particle");
    private final Map<String,JsonObject> cache=new HashMap<>();
    private JsonObject read(String path)throws Exception {
        if(cache.containsKey(path))return cache.get(path);var url=getClass().getResource(path);assertNotNull(url,"missing resource: "+path);
        try(var reader=new InputStreamReader(url.openStream(),StandardCharsets.UTF_8)){JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();cache.put(path,json);return json;}
    }
    private JsonObject model(String id)throws Exception{return read(ROOT+"models/block/"+id+".json");}
    private BufferedImage image(String ref)throws Exception {
        assertTrue(ref.startsWith("lithmod:block/"),"local block texture: "+ref);var url=getClass().getResource(ROOT+"textures/block/"+ref.substring(14)+".png");assertNotNull(url,"missing texture: "+ref);
        try(var stream=url.openStream()){BufferedImage image=ImageIO.read(stream);assertNotNull(image);assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());assertNotNull(image.getAlphaRaster());
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(255,image.getRGB(x,y)>>>24,"opaque pixel: "+ref);return image;}
    }
    private Set<String> files(String directory,String suffix)throws Exception {
        var url=getClass().getResource(directory);assertNotNull(url);try(var stream=Files.list(Path.of(url.toURI()))){return stream.map(p->p.getFileName().toString()).filter(n->n.endsWith(suffix)).map(n->n.substring(0,n.length()-suffix.length())).collect(Collectors.toSet());}
    }
    private static int[] dimensions(String f){return switch(f){case"north","south"->new int[]{2,2};case"west","east"->new int[]{3,2};default->new int[]{2,3};};}
    private static Set<String> modelIds(){Set<String> ids=new HashSet<>();for(String r:ROLES)for(int p=0;p<12;p++)for(String s:List.of("formed","unformed"))ids.add("process_cell_"+r+"_part"+p+"_"+s);return ids;}
    private static Set<String> tileIds(){Set<String> ids=new HashSet<>();for(String f:FACES){int[] size=dimensions(f);for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++)for(String r:List.of("shell","power_port"))ids.add("joined_"+r+"_"+f+"_u"+x+"v"+y);}
        for(int y=0;y<2;y++)for(int x=0;x<2;x++)for(String r:List.of("controller","operating_panel"))ids.add("joined_"+r+"_north_u"+x+"v"+y);ids.add("joined_operating_panel_unformed_front");return ids;}
    private Map<String,String> textures(String id,Set<String> seen)throws Exception {
        assertTrue(seen.add(id),"inheritance cycle");JsonObject j=model(id);Map<String,String> result=new HashMap<>();String parent=j.get("parent").getAsString();
        if(parent.startsWith("lithmod:block/"))result.putAll(textures(parent.substring(14),seen));else assertTrue(Set.of("minecraft:block/cube","minecraft:block/cube_all","minecraft:block/orientable").contains(parent),parent);
        if(j.has("textures"))for(var e:j.getAsJsonObject("textures").entrySet())result.put(e.getKey(),e.getValue().getAsString());
        for(String key:List.copyOf(result.keySet())){String value=result.get(key);Set<String> refs=new HashSet<>();while(value.startsWith("#")){assertTrue(refs.add(value));value=result.get(value.substring(1));assertNotNull(value);}result.put(key,value);}return result;
    }
    /** Literal FaceBakery oracle, independent of JavaScript generation. */
    private static String expected(String r,int p,String f){int u=p/6,v=p%6/3,d=p%3;int[]xy=switch(f){case"north"->new int[]{1-u,1-v};case"south"->new int[]{u,1-v};case"west"->new int[]{d,1-v};case"east"->new int[]{2-d,1-v};case"up"->new int[]{u,d};case"down"->new int[]{u,2-d};default->throw new AssertionError(f);};
        String tile=r.equals("power_port")||f.equals("north")&&(r.equals("controller")||r.equals("operating_panel"))?r:"shell";return"lithmod:block/joined_"+tile+"_"+f+"_u"+xy[0]+"v"+xy[1];}
    private static boolean differ(BufferedImage a,BufferedImage b){for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return true;return false;}

    @Test void joinedPanelTilesExistAndValid()throws Exception {
        Set<String> names=tileIds();assertEquals(73,names.size());assertEquals(names,files(ROOT+"textures/block/",".png").stream().filter(n->n.startsWith("joined_")).collect(Collectors.toSet()));for(String n:names)image("lithmod:block/"+n);
    }
    @Test void joinedPanelBlockModelsValid()throws Exception {
        assertEquals(96,modelIds().size());for(String r:ROLES)for(int p=0;p<12;p++){JsonObject j=model("process_cell_"+r+"_part"+p+"_formed");assertEquals("minecraft:block/cube",j.get("parent").getAsString());JsonObject t=j.getAsJsonObject("textures");assertEquals(KEYS,t.keySet());
            for(String f:FACES){assertEquals(expected(r,p,f),t.get(f).getAsString());image(t.get(f).getAsString());}assertEquals(t.get("north"),t.get("particle"));String id="process_cell_"+r+"_part"+p+"_unformed";
            assertEquals("lithmod:block/process_cell_"+r+"_unformed",model(id).get("parent").getAsString());Map<String,String> inherited=textures(id,new HashSet<>());assertFalse(inherited.isEmpty());for(String ref:new HashSet<>(inherited.values()))image(ref);}
    }
    @Test void joinedPanelBlockstatesValid()throws Exception {
        int states=0;for(String r:ROLES){JsonObject rules=read(ROOT+"blockstates/process_cell_"+r+".json").getAsJsonObject("variants");assertEquals(52,rules.size());
            for(boolean formed:List.of(false,true))for(int p=0;p<12;p++)for(var facing:ROTATIONS.entrySet()){Map<String,String> actual=Map.of("facing",facing.getKey(),"formed",Boolean.toString(formed),"part",Integer.toString(p));JsonObject selected=null;int matches=0;
                for(var e:rules.entrySet()){boolean match=true;for(String atom:e.getKey().split(",")){String[]kv=atom.split("=");assertEquals(2,kv.length);assertTrue(actual.containsKey(kv[0]));match&=actual.get(kv[0]).equals(kv[1]);}if(match){matches++;selected=e.getValue().getAsJsonObject();}}
                assertEquals(1,matches,"one selector for "+r+actual);assertNotNull(selected);String id="process_cell_"+r+"_part"+(formed?p:0)+"_"+(formed?"formed":"unformed");assertEquals("lithmod:block/"+id,selected.get("model").getAsString());model(id);
                assertEquals(facing.getValue().intValue(),selected.has("y")?selected.get("y").getAsInt():0);assertFalse(selected.has("uvlock")&&selected.get("uvlock").getAsBoolean());states++;}}
        assertEquals(384,states);
    }
    @Test void operatingPanelItemModelValid()throws Exception {
        JsonObject item=read(ROOT+"items/process_cell_operating_panel.json").getAsJsonObject("model");assertEquals("minecraft:model",item.get("type").getAsString());assertEquals("lithmod:item/process_cell_operating_panel",item.get("model").getAsString());
        assertEquals("lithmod:block/process_cell_operating_panel_unformed",read(ROOT+"models/item/process_cell_operating_panel.json").get("parent").getAsString());for(String ref:new HashSet<>(textures("process_cell_operating_panel_unformed",new HashSet<>()).values()))image(ref);
    }
    @Test void operatingPanelLootTableValid()throws Exception {
        JsonObject loot=read("/data/lithmod/loot_table/blocks/process_cell_operating_panel.json");assertEquals("minecraft:block",loot.get("type").getAsString());assertEquals(1,loot.getAsJsonArray("pools").size());JsonObject pool=loot.getAsJsonArray("pools").get(0).getAsJsonObject();assertEquals(1,pool.get("rolls").getAsInt());assertEquals(1,pool.getAsJsonArray("entries").size());
        JsonObject e=pool.getAsJsonArray("entries").get(0).getAsJsonObject();assertEquals("minecraft:item",e.get("type").getAsString());assertEquals("lithmod:process_cell_operating_panel",e.get("name").getAsString());
    }
    @Test void operatingPanelRecipeValid()throws Exception {
        JsonObject r=read("/data/lithmod/recipe/crafting/process_cell_operating_panel.json");assertEquals("minecraft:crafting_shaped",r.get("type").getAsString());assertEquals("lithmod:process_cell_operating_panel",r.getAsJsonObject("result").get("id").getAsString());assertEquals(1,r.getAsJsonObject("result").get("count").getAsInt());
        JsonObject key=r.getAsJsonObject("key");Set<String> used=new HashSet<>();assertEquals(3,r.getAsJsonArray("pattern").size());for(JsonElement row:r.getAsJsonArray("pattern")){assertEquals(3,row.getAsString().length());for(char c:row.getAsString().toCharArray())if(c!=' '){String symbol=Character.toString(c);assertTrue(key.has(symbol));used.add(symbol);}}assertEquals(key.keySet(),used);for(var e:key.entrySet())assertTrue(e.getValue().getAsString().startsWith("minecraft:"));
    }
    @Test void noExtraJoinedPanelBlockModels()throws Exception {assertEquals(modelIds(),files(ROOT+"models/block/",".json").stream().filter(n->n.startsWith("process_cell_")&&n.contains("_part")).collect(Collectors.toSet()));}
    @Test void noExtraJoinedPanelBlockstates()throws Exception {assertEquals(ROLES.stream().map(r->"process_cell_"+r).collect(Collectors.toSet()),files(ROOT+"blockstates/",".json").stream().filter(n->n.startsWith("process_cell_")).collect(Collectors.toSet()));}
    @Test void tileUVMappingIndependentVerification()throws Exception {
        for(String r:ROLES)for(int p=0;p<12;p++)for(String f:FACES)assertEquals(expected(r,p,f),model("process_cell_"+r+"_part"+p+"_formed").getAsJsonObject("textures").get(f).getAsString());
        JsonObject manifest=JsonParser.parseString(Files.readString(Path.of("tools/starter-textures.json"))).getAsJsonObject();int edge=0xff000000|Integer.parseInt(manifest.getAsJsonObject("joinedPanels").getAsJsonObject("palette").get("edge").getAsString().substring(1),16);
        for(String f:FACES){int[]size=dimensions(f);int w=size[0]*16,h=size[1]*16;BufferedImage canvas=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            for(int ty=0;ty<size[1];ty++)for(int tx=0;tx<size[0];tx++){BufferedImage tile=image("lithmod:block/joined_shell_"+f+"_u"+tx+"v"+ty);for(int y=0;y<16;y++)for(int x=0;x<16;x++)canvas.setRGB(tx*16+x,ty*16+y,tile.getRGB(x,y));}
            for(int x=0;x<w;x++){assertEquals(edge,canvas.getRGB(x,0));assertEquals(edge,canvas.getRGB(x,h-1));}for(int y=0;y<h;y++){assertEquals(edge,canvas.getRGB(0,y));assertEquals(edge,canvas.getRGB(w-1,y));}
            for(int seam=16;seam<w;seam+=16){int dark=0;for(int y=1;y<h-1;y++)if(canvas.getRGB(seam-1,y)==edge||canvas.getRGB(seam,y)==edge)dark++;assertTrue(dark<(h-2)/2,"no repeated vertical frame: "+f);}
            for(int seam=16;seam<h;seam+=16){int dark=0;for(int x=1;x<w-1;x++)if(canvas.getRGB(x,seam-1)==edge||canvas.getRGB(x,seam)==edge)dark++;assertTrue(dark<(w-2)/2,"no repeated horizontal frame: "+f);}}
        assertTrue(differ(image(expected("controller",0,"north")),image(expected("casing",0,"north"))));assertTrue(differ(image(expected("operating_panel",3,"north")),image(expected("casing",3,"north"))));assertTrue(differ(image(expected("power_port",2,"south")),image(expected("casing",2,"south"))));assertTrue(differ(image("lithmod:block/joined_operating_panel_unformed_front"),image(expected("operating_panel",3,"north"))));
    }
}
