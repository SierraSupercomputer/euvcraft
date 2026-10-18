package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the mask chain.
 *
 * <p>Its own class rather than a line in the framework's client events, so
 * {@link PatternGeneratorScreen} — and the whole client screen package it extends — is
 * referenced from exactly one place, and that place is annotated
 * {@code value = Dist.CLIENT}, which is what keeps the screen off a dedicated server.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class MaskMachineClientEvents {

    private MaskMachineClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MaskMachines.PATTERN_GENERATOR_MENU.get(), PatternGeneratorScreen::new);
    }
}
