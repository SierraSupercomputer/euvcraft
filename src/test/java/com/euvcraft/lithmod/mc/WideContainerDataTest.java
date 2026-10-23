package com.euvcraft.lithmod.mc;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import io.netty.buffer.Unpooled;
class WideContainerDataTest {
 @Test void actualShortPacketRoundTripPreservesEveryInt(){
  for(int value:new int[]{0,1,65535,65536,200000,-90000,30000000,-30000000,Integer.MIN_VALUE,Integer.MAX_VALUE}){
   var server=new SimpleContainerData(1);server.set(0,value);var client=new SimpleContainerData(1);var source=new WideContainerData(server);var target=new WideContainerData(client);
   for(int i=0;i<2;i++){var buf=new FriendlyByteBuf(Unpooled.buffer());try{ClientboundContainerSetDataPacket.STREAM_CODEC.encode(buf,new ClientboundContainerSetDataPacket(7,i,source.get(i)));var packet=ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buf);target.set(packet.getId(),packet.getValue());}finally{buf.release();}}
   assertEquals(value,client.get(0));
  }
 }
 @Test void changedHalvesPreserveOtherHalfAndBounds(){var v=new SimpleContainerData(1);var wire=new WideContainerData(v);wire.set(0,-1);wire.set(1,1);assertEquals(131071,v.get(0));assertEquals(2,wire.getCount());assertThrows(IndexOutOfBoundsException.class,()->wire.get(2));}
}
