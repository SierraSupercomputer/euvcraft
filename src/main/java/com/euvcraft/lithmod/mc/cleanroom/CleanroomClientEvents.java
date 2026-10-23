package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class CleanroomClientEvents{
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(CleanroomBlocks.MENU.get(),CleanroomScreen::new);}
 private CleanroomClientEvents(){}
}
