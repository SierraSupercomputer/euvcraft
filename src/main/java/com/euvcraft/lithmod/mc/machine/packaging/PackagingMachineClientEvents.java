package com.euvcraft.lithmod.mc.machine.packaging;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the packaging chain.
 *
 * <p>Its own class rather than a line in the framework's client events, so
 * {@link TestPackageScreen} — and the whole client screen package it extends — is referenced
 * from exactly one place, and that place is annotated {@code value = Dist.CLIENT}, which is
 * what keeps the screen off a dedicated server.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class PackagingMachineClientEvents {

    private PackagingMachineClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PackagingMachines.TEST_PACKAGE_MENU.get(), TestPackageScreen::new);
    }
}
