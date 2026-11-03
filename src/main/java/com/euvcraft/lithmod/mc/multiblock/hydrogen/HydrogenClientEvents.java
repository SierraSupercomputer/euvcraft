package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.FluidModel;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

/**
 * Client-only registrations for the hydrogen purifier multiblock.
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class HydrogenClientEvents {

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(HydrogenMachines.MENU.get(), HydrogenScreen::new);
    }

    @SubscribeEvent
    public static void fluids(RegisterFluidModelsEvent event) {
        // Use a simple gas-like model for the fabricated hydrogen
        var still = new Material(Identifier.withDefaultNamespace("block/water_still"), true);
        var flowing = new Material(Identifier.withDefaultNamespace("block/water_flow"), true);
        event.register(new FluidModel.Unbaked(still, flowing, null, (FluidTintSource)(state->0xff8eeedb)), HydrogenFluids.FAB_HYDROGEN, HydrogenFluids.FLOWING);
    }

    private HydrogenClientEvents() {}
}
