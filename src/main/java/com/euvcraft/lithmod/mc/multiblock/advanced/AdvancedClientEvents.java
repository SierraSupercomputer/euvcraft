package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.screen.AdvancedOperatingPanelScreen;
import com.euvcraft.lithmod.mc.machine.screen.DepositionEtcherScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the T4 advanced multiblocks.
 * <p>
 * Its own class rather than a line in the framework's client events, so
 * {@link DepositionEtcherScreen} and {@link AdvancedOperatingPanelScreen} are
 * referenced from exactly one place, and that place is annotated
 * {@code value = Dist.CLIENT}, which is what keeps the screens off a dedicated server.
 * </p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class AdvancedClientEvents {

    private AdvancedClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AdvancedMachines.DEPOSITION_ETCHER_MENU.get(), DepositionEtcherScreen::new);
        event.register(AdvancedMachines.ADVANCED_TEST_PACKAGE_MENU.get(), AdvancedTestPackageScreen::new);
        event.register(AdvancedMachines.ADVANCED_OPERATING_PANEL_MENU.get(), AdvancedOperatingPanelScreen::new);
    }
}