package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.multiblock.g3.G3ReticleWriterMenu;
import com.euvcraft.lithmod.mc.multiblock.g3.G3DepositionEtcherMenu;
import com.euvcraft.lithmod.mc.multiblock.g3.G3OperatingPanelMenu;
import com.euvcraft.lithmod.mc.multiblock.g3.G3ReticleWriterControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.g3.G3DepositionEtcherControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlockEntity;
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
        event.register(G3Machines.RETICLE_WRITER_MENU.get(), G3ReticleWriterScreen::new);
        event.register(G3Machines.DEPOSITION_ETCHER_MENU.get(), G3DepositionEtcherScreen::new);
        event.register(G3Machines.OPERATING_PANEL_MENU.get(), G3OperatingPanelScreen::new);
    }
}