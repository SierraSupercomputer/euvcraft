package com.euvcraft.lithmod.mc.routing;
import static org.junit.jupiter.api.Assertions.*;
import com.euvcraft.lithmod.core.routing.FabProgress;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import org.junit.jupiter.api.Test;

class FoupRoutingDataTest {
    @Test void saveAndStreamKeepSafeStagesAndProgramExactly(){
        var cases=List.of(FoupRoutingData.initial(),new FoupRoutingData(FabProgress.initial().deposited(),"",0,false),new FoupRoutingData(FabProgress.initial().deposited().coated(7),"",0,false),new FoupRoutingData(new FabProgress(FabProgress.Stage.EXPOSED,4,1),"logic",0,false),new FoupRoutingData(new FabProgress(FabProgress.Stage.CLEANED,7,1),"compute",24,true));
        for(var data:cases){
            var json=FoupRoutingData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow();assertEquals(data,FoupRoutingData.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
            assertEquals(Set.of("stage","tier","version","die_type","cores"),json.getAsJsonObject().keySet().stream().filter(k->!k.equals("contaminated")).collect(java.util.stream.Collectors.toSet()));
            var nbt=FoupRoutingData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow();assertEquals(data,FoupRoutingData.CODEC.parse(NbtOps.INSTANCE,nbt).getOrThrow());
            var buffer=Unpooled.buffer();try{FoupRoutingData.STREAM_CODEC.encode(buffer,data);assertEquals(data,FoupRoutingData.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
        }
    }

    @Test void invalidVersionsStagesTiersAndMissingRequiredFieldsFailClosed(){
        var base=FoupRoutingData.CODEC.encodeStart(JsonOps.INSTANCE,FoupRoutingData.initial()).getOrThrow().getAsJsonObject();
        for(var field:List.of("stage","tier","version","die_type","cores")){var bad=base.deepCopy();bad.remove(field);assertTrue(FoupRoutingData.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent(),field);}
        for(String stage:List.of("invented","EXPOSED","cleaned")){var bad=base.deepCopy();bad.addProperty("stage",stage);assertTrue(FoupRoutingData.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent());}
        var bad=base.deepCopy();bad.addProperty("version",2);assertTrue(FoupRoutingData.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent());
        bad=base.deepCopy();bad.addProperty("tier",5);assertTrue(FoupRoutingData.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent());
    }

    @Test void familyAndCoreChecksCannotExposeNonsensePrograms(){
        var exposed=new FabProgress(FabProgress.Stage.EXPOSED,6,1);
        assertThrows(IllegalArgumentException.class,()->new FoupRoutingData(exposed,"",0,false));
        assertThrows(IllegalArgumentException.class,()->new FoupRoutingData(exposed,"compute",0,false));
        assertThrows(IllegalArgumentException.class,()->new FoupRoutingData(exposed,"memory",1,false));
        assertThrows(IllegalArgumentException.class,()->new FoupRoutingData(exposed,"compute",257,false));
        assertThrows(IllegalArgumentException.class,()->new FoupRoutingData(FabProgress.initial(),"logic",0,false));
    }
}
