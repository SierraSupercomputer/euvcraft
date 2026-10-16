package com.euvcraft.lithmod.mc.client;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.block.entity.TestMachineMenu;
import com.euvcraft.lithmod.mc.client.screen.TestMachineScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side event handlers for EUVCRAFT.
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandlers {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(com.euvcraft.lithmod.mc.registry.ModBlocks.TEST_MACHINE_MENU.get(), TestMachineScreen::new);
    }
}