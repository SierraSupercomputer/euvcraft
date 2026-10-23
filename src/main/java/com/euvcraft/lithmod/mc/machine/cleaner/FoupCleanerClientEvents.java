package com.euvcraft.lithmod.mc.machine.cleaner;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class FoupCleanerClientEvents { @SubscribeEvent public static void menus(RegisterMenuScreensEvent e){e.register(FoupCleanerMachines.MENU.get(),FoupCleanerScreen::new);}private FoupCleanerClientEvents(){} }
