package com.euvcraft.lithmod.mc.multiblock.assembly;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;import net.neoforged.api.distmarker.Dist;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class AssemblyClientEvents{@SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(AssemblyMachines.MENU.get(),AssemblyScreen::new);}private AssemblyClientEvents(){}}

