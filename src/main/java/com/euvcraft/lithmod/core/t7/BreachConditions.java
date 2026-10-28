package com.euvcraft.lithmod.core.t7;

/**
 * Physical circumstances of a cleanroom-breach notification.
 *
 * <p>An empty or unpowered machine is deliberately distinguishable from a
 * structural breach: only a powered, loaded machine that is actively processing a
 * T7 wafer lot can become contaminated. This type carries no world references.</p>
 *
 * @param powered           whether the machine has power
 * @param loaded            whether a wafer carrier is present
 * @param activeT7Processing whether a T7 wafer process is actually running
 */
public record BreachConditions(boolean powered, boolean loaded, boolean activeT7Processing) {

    /** Returns whether these conditions can contaminate the equipment. */
    public boolean warrantsContamination() {
        return this.powered && this.loaded && this.activeT7Processing;
    }
}
