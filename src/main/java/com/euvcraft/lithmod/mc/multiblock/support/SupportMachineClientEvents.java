package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the support machines.
 *
 * <p>Its own class — the same shape as {@code ProcessCellClientEvents} — so
 * {@link SupportMachineScreen} is referenced from exactly one place, and that place is annotated
 * {@code Dist.CLIENT}, which keeps the screen off a dedicated server. One screen class covers both
 * shell menu types.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class SupportMachineClientEvents {

    private SupportMachineClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SupportMachines.SUPPORT_PURIFIER_MENU.get(), SupportMachineScreen::new);
        event.register(SupportMachines.SUPPORT_EBEAM_MENU.get(), SupportMachineScreen::new);
    }
}
