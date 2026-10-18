package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations for the Process Cell.
 * <p>
 * Its own class rather than a line in the framework's client events, so
 * {@link ProcessCellScreen} — and the whole client screen package it extends — is
 * referenced from exactly one place, and that place is annotated
 * {@code value = Dist.CLIENT}, which is what keeps the screen off a dedicated server.
 * </p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class ProcessCellClientEvents {

    private ProcessCellClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), ProcessCellScreen::new);
    }
}