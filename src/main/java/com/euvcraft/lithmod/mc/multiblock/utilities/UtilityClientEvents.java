package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;import net.neoforged.api.distmarker.Dist;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class UtilityClientEvents{@SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(UtilityMachines.MENU.get(),UtilityScreen::new);}@SubscribeEvent public static void fluids(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent e){var still=new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.withDefaultNamespace("block/water_still"),true);var flowing=new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.withDefaultNamespace("block/water_flow"),true);e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(still,flowing,null,(net.neoforged.neoforge.client.fluid.FluidTintSource)null),UtilityFluids.ULTRAPURE_WATER,UtilityFluids.FLOWING);}private UtilityClientEvents(){}}


