package com.euvcraft.lithmod.core;

/**
 * Environment provided by the caller of a {@link ProcessRun} tick.
 */
public interface ProcessEnvironment {
    /** Whether the multiblock structure is currently formed. */
    boolean structureFormed();

    /**
     * Removes up to {@code maxAmount} FE from the environment's energy store.
     *
     * @param maxAmount amount to extract
     * @param simulate when true, no energy is actually removed
     * @return the amount actually extracted
     */
    int extractEnergy(int maxAmount, boolean simulate);

    /** Returns the available resist units. */
    int resistAvailable();

    /** Consumes the supplied resist units. */
    void consumeResist(int amount);

    /** Applies one layer to the lot and returns the updated lot. */
    FoupContents applyLayer(FoupContents lot, int layerNumber, double defectFactor);
}
