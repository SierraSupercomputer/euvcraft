package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class ViewerClientEvents {
    @SubscribeEvent public static void register(RegisterClientPayloadHandlersEvent event){event.register(ViewerPayload.TYPE,(payload,context)->ViewerClientStore.accept(payload.json()));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ViewerClientStore.clear();}
    private ViewerClientEvents(){}
}
