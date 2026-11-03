package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** All clients get the authoritative server catalog at login and datapack reload, with or without JEI. */
public final class ViewerSync {
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.MAIN).optional().playToClient(ViewerPayload.TYPE,ViewerPayload.CODEC);}
    public static void sync(OnDatapackSyncEvent event){
        send(event.getRelevantPlayers());
    }
    public static void settingsChanged(){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&server.isRunning())server.execute(()->send(server.getPlayerList().getPlayers().stream()));
    }
    private static void send(java.util.stream.Stream<net.minecraft.server.level.ServerPlayer> players){
        ViewerPayload payload;
        try{payload=new ViewerPayload(ViewerCatalogCodec.encode(ViewerCatalog.build()));}
        catch(RuntimeException bad){EUVCRAFTMod.LOGGER.warn("Could not build recipe viewer catalog; hiding stale process pages",bad);payload=new ViewerPayload("[]");}
        final var sent=payload;players.forEach(player->{if(player.connection.hasChannel(ViewerPayload.TYPE))PacketDistributor.sendToPlayer(player,sent);});
    }
    private ViewerSync(){}
}
