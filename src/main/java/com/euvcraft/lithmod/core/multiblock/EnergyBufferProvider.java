package com.euvcraft.lithmod.core.multiblock;

import com.euvcraft.lithmod.core.EnergyBuffer;

/**
 * Interface for block entities that provide an energy buffer.
 * Implemented by multiblock controller block entities.
 */
public interface EnergyBufferProvider {
    /**
     * Returns the energy buffer for this multiblock.
     * Only valid while the structure is formed.
     */
    EnergyBuffer getEnergyBuffer();

    /**
     * Called when the multiblock structure becomes formed.
     * Override in subclasses to handle formation logic.
     */
    default void onFormed() {}

    /**
     * Called when the multiblock structure becomes unformed.
     * Override in subclasses to handle unformation logic.
     */
    default void onUnformed() {}
}