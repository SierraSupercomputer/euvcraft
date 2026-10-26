package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side menu screen registration for the T6 reflow oven: the status-only controller view,
 * the upper operating panel and the thermal calibration console.
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class ReflowClientEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ReflowMachines.CONTROLLER_MENU.get(), ReflowControllerScreen::new);
        event.register(ReflowMachines.OPERATING_MENU.get(), ReflowOperatingScreen::new);
        event.register(ReflowMachines.CALIBRATION_MENU.get(), ReflowCalibrationScreen::new);
    }

    private ReflowClientEvents() {}
}
