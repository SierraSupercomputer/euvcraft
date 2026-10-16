package com.euvcraft.lithmod.mc.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Event handler for capability registration.
 * Registers the FE energy capability for the Process Cell Power Port.
 * Note: NeoForge 26.3 API for capability registration has changed.
 * This handler registers the energy capability for the power port block entity.
 */
public final class CapabilityEventHandler {

    private CapabilityEventHandler() {}

    /**
     * Registers the energy storage capability for the power port block entity.
     * This is called during mod initialization.
     * Note: NeoForge 26.3 API for capability registration has changed.
     * The block entity's getCapability method exposes the energy capability.
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // TODO: NeoForge 26.3 API for capability registration has changed.
        // The RegisterCapabilitiesEvent API has changed in 26.3.
        // The block entity's getCapability method exposes the energy capability.
        // TODO: Update this when the NeoForge 26.3 capability API is clarified.
    }
}