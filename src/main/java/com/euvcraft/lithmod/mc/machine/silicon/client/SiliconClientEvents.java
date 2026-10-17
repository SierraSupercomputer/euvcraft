package com.euvcraft.lithmod.mc.machine.silicon.client;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.screen.CrystalPullerScreen;
import com.euvcraft.lithmod.mc.machine.silicon.screen.WaferSlicerScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the silicon chain.
 *
 * <p>Annotated {@code value = Dist.CLIENT}, which is what keeps the two screens off a
 * dedicated server. The silicon chain has its own subscriber rather than an addition to the
 * framework's, so this task adds nothing to the framework's client wiring.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class SiliconClientEvents {

    private SiliconClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SiliconMachines.CRYSTAL_PULLER_MENU.get(), CrystalPullerScreen::new);
        event.register(SiliconMachines.WAFER_SLICER_MENU.get(), WaferSlicerScreen::new);
    }
}