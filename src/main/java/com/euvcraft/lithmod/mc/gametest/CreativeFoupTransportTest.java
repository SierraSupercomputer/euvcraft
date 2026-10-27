package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.*;

/** Exercises the actual mixin-wrapped packet codecs, not a manually privileged component codec. */
final class CreativeFoupTransportTest {
    static void run(GameTestHelper h, boolean local) {
        var level=h.getLevel();
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"foup-wire-test"),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var serverConnection=new Connection(PacketFlow.SERVERBOUND);
        var server=new EmbeddedChannel();
        Connection.configureSerialization(server.pipeline(),PacketFlow.SERVERBOUND,local,null);
        List<Object> serverPackets=new ArrayList<>();
        server.pipeline().addLast("capture",capture(serverPackets));
        serverConnection.configurePacketHandler(server.pipeline());
        server.pipeline().fireChannelActive();
        var listener=new ServerGamePacketListenerImpl(level.getServer(),serverConnection,player,cookie);
        var decorate=RegistryFriendlyByteBuf.decorator(level.registryAccess());
        var clientbound=GameProtocols.CLIENTBOUND_TEMPLATE.bind(decorate);
        var serverbound=GameProtocols.SERVERBOUND_TEMPLATE.bind(decorate,listener);
        serverConnection.setupInboundProtocol(serverbound,listener);
        serverConnection.setupOutboundProtocol(clientbound);
        var clientConnection=new Connection(PacketFlow.CLIENTBOUND);
        var client=new EmbeddedChannel();
        Connection.configureSerialization(client.pipeline(),PacketFlow.CLIENTBOUND,local,null);
        client.pipeline().replace("inbound_config","decoder",new PacketDecoder<>(clientbound));
        List<Object> clientPackets=new ArrayList<>();
        client.pipeline().addLast("capture",capture(clientPackets));
        clientConnection.configurePacketHandler(client.pipeline());
        client.pipeline().fireChannelActive();
        var creativeContext=new GameProtocols.Context(){public boolean hasInfiniteMaterials(){return true;}public boolean canUseCommandBlocks(){return false;}};
        clientConnection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(decorate,creativeContext));
        player.initInventoryMenu();pump(server,client);
        try {
            for(var item:List.of(ModItems.FOUP_MK1.get(),ModItems.FOUP_MK2.get())) {
                int capacity=item==ModItems.FOUP_MK1.get()?25:64;
                var lot=new FoupContents(UUID.randomUUID(),capacity,List.of(new Wafer(3,0.31415927f,0),new Wafer(3,0.8123456f,0)),new RunSpec("compute",6,8,0.72,12),WaferSubstrate.ELECTRONIC_GRADE,2,new com.euvcraft.lithmod.core.cleanroom.CleanroomExposure(20,19000),950);
                var stack=new ItemStack(item);stack.set(ModDataComponents.FOUP_CONTENTS.get(),lot);
                player.inventoryMenu.getSlot(36).set(stack);
                // Survival client receives a public view, while the server lot is unchanged.
                player.setGameMode(GameType.SURVIVAL);clientPackets.clear();
                server.writeOutbound(new ClientboundContainerSetSlotPacket(0,0,36,stack));pump(server,client);
                var publicStack=lastSlot(clientPackets,36);
                h.assertTrue(FoupContentsCodec.isClientView(publicStack.get(ModDataComponents.FOUP_CONTENTS.get())),"Survival wire leaked private data");
                h.assertTrue(FoupItems.trustedContentsOf(stack).equals(lot),"public encoding changed server lot");
                // Successful mode change refreshes unchanged slots; test the refresh rather than sending a test packet.
                clientPackets.clear();player.setGameMode(GameType.CREATIVE);pump(server,client);
                ItemStack creativeStack=lastSlot(clientPackets,36);
                h.assertTrue(lot.equals(creativeStack.get(ModDataComponents.FOUP_CONTENTS.get())),"Creative mode refresh did not preserve full processed lot");
                // E-inventory move and clone: real replacement packets preserve results in two slots.
                for(int slot:new int[]{36,37}) {
                    serverPackets.clear();client.writeOutbound(new ServerboundSetCreativeModeSlotPacket(slot,creativeStack.copy()));pump(client,server);
                    var packet=(ServerboundSetCreativeModeSlotPacket)serverPackets.getLast();
                    listener.handleSetCreativeModeSlot(packet);
                    h.assertTrue(lot.equals(FoupItems.trustedContentsOf(player.inventoryMenu.getSlot(slot).getItem())),"Creative move/clone rerolled or lost state");
                }
                // Main-thread handler rejects a decoded Creative packet after permission changes.
                var stale=new ServerboundSetCreativeModeSlotPacket(38,creativeStack.copy());
                player.inventoryMenu.getSlot(38).set(ItemStack.EMPTY);clientPackets.clear();
                player.setGameMode(GameType.SURVIVAL);listener.handleSetCreativeModeSlot(stale);pump(server,client);
                h.assertTrue(player.inventoryMenu.getSlot(38).getItem().isEmpty(),"mode-change stale Creative packet changed Survival inventory");
                h.assertTrue(FoupContentsCodec.isClientView(lastSlot(clientPackets,36).get(ModDataComponents.FOUP_CONTENTS.get())),"Survival mode refresh did not redact");
                h.assertTrue(lot.equals(FoupItems.trustedContentsOf(player.inventoryMenu.getSlot(36).getItem())),"mode refresh changed server lot");
            }
            h.succeed();
        } finally {server.finishAndReleaseAll();client.finishAndReleaseAll();}
    }
    private static ChannelInboundHandlerAdapter capture(List<Object> packets){return new ChannelInboundHandlerAdapter(){@Override public void channelRead(ChannelHandlerContext ctx,Object msg){packets.add(msg);}};}
    private static void pump(EmbeddedChannel from,EmbeddedChannel to){from.runPendingTasks();Object msg;while((msg=from.readOutbound())!=null)to.writeInbound(msg);to.runPendingTasks();from.checkException();to.checkException();}
    private static ItemStack lastSlot(List<Object> packets,int slot){
        ItemStack result=null;
        for(Object packet:packets){
            if(packet instanceof ClientboundContainerSetSlotPacket p&&p.getSlot()==slot)result=p.getItem();
            if(packet instanceof ClientboundContainerSetContentPacket p)result=p.items().get(slot);
        }
        if(result==null)throw new AssertionError("No synchronized slot "+slot+" among "+packets.stream().map(p->p.getClass().getSimpleName()).toList());
        return result;
    }
}
