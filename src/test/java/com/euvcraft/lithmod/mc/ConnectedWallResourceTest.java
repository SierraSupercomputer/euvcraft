package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ConnectedWallResourceTest {
    private static final Map<String,List<String>> EDGES=Map.of(
        "north",List.of("east","west","up","down"),"south",List.of("west","east","up","down"),
        "west",List.of("north","south","up","down"),"east",List.of("south","north","up","down"),
        "up",List.of("west","east","north","south"),"down",List.of("west","east","south","north"));

    @Test void everyNeighborCombinationSelectsExactlyOneOpaqueModelPerFace()throws Exception {
        var parts=CleanroomAssetResourceTest.json("/assets/lithmod/blockstates/cleanroom_wall.json").getAsJsonArray("multipart");
        assertEquals(96,parts.size());
        var directions=List.of("north","south","east","west","up","down");
        for(int state=0;state<64;state++){
            var flags=new HashMap<String,String>();
            for(int i=0;i<6;i++)flags.put(directions.get(i),String.valueOf((state&(1<<i))!=0));
            var selected=new HashSet<String>();
            for(var part:parts){
                var p=part.getAsJsonObject();
                if(!p.getAsJsonObject("when").entrySet().stream().allMatch(e->e.getValue().getAsString().equals(flags.get(e.getKey()))))continue;
                String id=p.getAsJsonObject("apply").get("model").getAsString().substring("lithmod:block/".length());
                var model=CleanroomAssetResourceTest.json("/assets/lithmod/models/block/"+id+".json");
                assertFalse(model.has("render_type"),"Wall must stay opaque");
                var faces=model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
                assertEquals(1,faces.size());String face=faces.keySet().iterator().next();assertTrue(selected.add(face));
                assertEquals(face,faces.getAsJsonObject(face).get("cullface").getAsString());
                int mask=0;for(int i=0;i<4;i++)if(flags.get(EDGES.get(face).get(i)).equals("true"))mask|=1<<i;
                assertEquals("cleanroom_wall_"+face+"_"+mask,id);
            }
            assertEquals(EDGES.keySet(),selected);
        }
    }

    @Test void allJoinMasksRemoveOnlyTheCorrectBordersAndStayOpaque()throws Exception {
        for(int mask=0;mask<16;mask++)try(var in=getClass().getResourceAsStream("/assets/lithmod/textures/block/cleanroom_wall_join_"+mask+".png")){
            assertNotNull(in);var image=ImageIO.read(in);assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                boolean outer=(x==0&&(mask&1)==0)||(x==15&&(mask&2)==0)||(y==0&&(mask&4)==0)||(y==15&&(mask&8)==0);
                boolean border=(x<2&&(mask&1)==0)||(x>=14&&(mask&2)==0)||(y<2&&(mask&4)==0)||(y>=14&&(mask&8)==0);
                int expected=outer?0xff50646b:border?0xffd9e4e7:0xffedf3f4;
                assertEquals(expected,image.getRGB(x,y),"mask="+mask+" pixel="+x+","+y);
            }
        }
    }

    @Test void isolatedWallMatchesExistingArtAndFloorRemainsATiledCube()throws Exception {
        try(var a=getClass().getResourceAsStream("/assets/lithmod/textures/block/cleanroom_wall.png");var b=getClass().getResourceAsStream("/assets/lithmod/textures/block/cleanroom_wall_join_0.png")){
            var old=ImageIO.read(a);var joined=ImageIO.read(b);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(old.getRGB(x,y),joined.getRGB(x,y));
        }
        var floor=CleanroomAssetResourceTest.json("/assets/lithmod/blockstates/cleanroom_floor.json");
        assertFalse(floor.has("multipart"));assertEquals(1,floor.getAsJsonObject("variants").size());
        assertEquals("minecraft:block/cube_all",CleanroomAssetResourceTest.json("/assets/lithmod/models/block/cleanroom_floor.json").get("parent").getAsString());
    }
}
