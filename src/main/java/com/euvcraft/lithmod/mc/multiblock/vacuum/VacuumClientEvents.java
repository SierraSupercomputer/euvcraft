package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the central vacuum plant multiblock.
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class VacuumClientEvents {

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(VacuumMachines.MENU.get(), VacuumScreen::new);
    }

    private VacuumClientEvents() {}
}