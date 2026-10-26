package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the zone multiblock.
 *
 * <p>Its own class rather than a line in the framework's client events, so
 * {@link ZoneStatusScreen} and {@link ZoneOperatingPanelScreen} are referenced from exactly
 * one place, and that place is annotated {@code value = Dist.CLIENT}, which keeps the
 * screens off a dedicated server.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class ZoneClientEvents {

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ZoneMachines.STATUS_MENU.get(), ZoneStatusScreen::new);
        event.register(ZoneMachines.PANEL_MENU.get(), ZoneOperatingPanelScreen::new);
    }

    private ZoneClientEvents() {
    }
}
