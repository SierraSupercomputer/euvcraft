package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

/**
 * Loads the support-line generation matrix from data/lithmod/support_lines/.
 */
public class SupportLineDataLoader extends AbstractGenerationMatrixDataLoader {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    public SupportLineDataLoader() {
        super("support_lines");
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.debug("Registering SupportLineDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "support_lines"), new SupportLineDataLoader());
    }
}
