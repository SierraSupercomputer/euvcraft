package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class LinearClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(LinearMachines.STAGE_PANEL_MENU.get(),LinearStageScreen::new);}
    private LinearClientEvents(){}
}
