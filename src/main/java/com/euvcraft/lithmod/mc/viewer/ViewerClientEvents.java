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
    @SubscribeEvent public static void fluidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event){
        event.register(model(0xffb8eaff),com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids.ULTRAPURE_WATER.get(),com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids.FLOWING.get());
        event.register(model(0xff8eeedb),com.euvcraft.lithmod.mc.multiblock.hydrogen.HydrogenFluids.FAB_HYDROGEN.get(),com.euvcraft.lithmod.mc.multiblock.hydrogen.HydrogenFluids.FLOWING.get());
    }
    private static net.minecraft.client.renderer.block.FluidModel.Unbaked model(int color){
        return new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.parse("minecraft:block/water_still")),new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.parse("minecraft:block/water_flow")),new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.parse("minecraft:block/water_overlay")),(net.neoforged.neoforge.client.fluid.FluidTintSource)(state->color));
    }
    private ViewerClientEvents(){}
}
