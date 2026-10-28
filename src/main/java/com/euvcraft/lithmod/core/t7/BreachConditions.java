package com.euvcraft.lithmod.core.t7;

/**
 * Physical circumstances of a cleanroom-breach notification.
 *
 * <p>An empty or unpowered machine is deliberately distinguishable from a
 * structural breach. A loaded, admitted T7 wafer lot remains exposed while a
 * utility shortage pauses it. Turning off power cannot shield it from a physical
 * breach. This type carries no world references.</p>
 *
 * @param powered           whether the machine has power
 * @param loaded            whether a wafer carrier is present
 * @param activeT7Processing whether a T7 wafer process is actually running
 */
public record BreachConditions(boolean powered, boolean loaded, boolean activeT7Processing) {

    /** Returns whether these conditions can contaminate the equipment. */
    public boolean warrantsContamination() {
        return this.loaded && this.activeT7Processing;
    }
}
