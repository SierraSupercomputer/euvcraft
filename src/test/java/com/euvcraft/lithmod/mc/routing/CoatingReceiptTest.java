package com.euvcraft.lithmod.mc.routing;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoatingReceiptTest {
    private CoatingReceipt sample(){return new CoatingReceipt(6,70,2,1,190,350,350,14700,480,0,1);}
    @Test void jsonNbtAndNetworkRetainThePaidBudget(){var r=sample();var json=CoatingReceipt.CODEC.encodeStart(JsonOps.INSTANCE,r).result().orElseThrow();assertEquals(r,CoatingReceipt.CODEC.parse(JsonOps.INSTANCE,json).result().orElseThrow());var nbt=CoatingReceipt.CODEC.encodeStart(NbtOps.INSTANCE,r).result().orElseThrow();assertEquals(r,CoatingReceipt.CODEC.parse(NbtOps.INSTANCE,nbt).result().orElseThrow());var buffer=Unpooled.buffer();try{CoatingReceipt.STREAM_CODEC.encode(buffer,r);assertEquals(r,CoatingReceipt.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}}
    @Test void onlyPublicCostFactsAreEncoded(){var j=CoatingReceipt.CODEC.encodeStart(JsonOps.INSTANCE,sample()).result().orElseThrow().getAsJsonObject();assertEquals(java.util.Set.of("tier","layers","passes","resist_per_layer","scanner_ticks_per_layer","coat_ticks","clean_dry_ticks","clean_water_mb","energy_per_tick","rinse_ticks","rinse_energy_per_tick"),j.keySet());assertEquals(70,sample().totalResist());}
    @Test void invalidBudgetsAreRejected(){assertThrows(IllegalArgumentException.class,()->new CoatingReceipt(4,70,2,1,190,350,350,14700,480,0,1));assertThrows(IllegalArgumentException.class,()->new CoatingReceipt(6,70,2,1,0,350,350,14700,480,0,1));var j=CoatingReceipt.CODEC.encodeStart(JsonOps.INSTANCE,sample()).result().orElseThrow().getAsJsonObject();j.addProperty("clean_water_mb",-1);assertTrue(CoatingReceipt.CODEC.parse(JsonOps.INSTANCE,j).error().isPresent());}
}
