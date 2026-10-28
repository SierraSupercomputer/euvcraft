package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side screen registration for G3 menus.
 */
@EventBusSubscriber(modid = "lithmod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class G3ClientEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(G3Machines.RETICLE_WRITER_MENU.get(), (menu, inventory, title) -> new G3ReticleWriterScreen(menu, inventory, title));
        event.register(G3Machines.DEPOSITION_ETCHER_MENU.get(), (menu, inventory, title) -> new G3DepositionEtcherScreen(menu, inventory, title));
        event.register(G3Machines.OPERATING_PANEL_MENU.get(), (menu, inventory, title) -> new G3OperatingPanelScreen(menu, inventory, title));
    }
}