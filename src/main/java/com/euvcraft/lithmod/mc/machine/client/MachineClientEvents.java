package com.euvcraft.lithmod.mc.machine.client;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.screen.TestMachineScreen;
import com.euvcraft.lithmod.mc.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations.
 *
 * <p>Annotated {@code value = Dist.CLIENT}, which is what keeps {@code TestMachineScreen} and
 * everything it pulls in off a dedicated server. This class is the only reference to the
 * screen package from common code.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class MachineClientEvents {

    private MachineClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TEST_MACHINE.get(), TestMachineScreen::new);
    }
}
