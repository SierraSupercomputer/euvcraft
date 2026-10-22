package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class ScannerClientEvents {
 @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event){event.register(ScannerMachines.OPERATING_MENU.get(),ScannerOperatingScreen::new);event.register(ScannerMachines.DASHBOARD_MENU.get(),ScannerDashboardScreen::new);}
}
