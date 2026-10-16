package com.euvcraft.lithmod.core;

/**
 * Somewhere a machine can draw Forge Energy from.
 *
 * <p>Pure core on purpose: {@code MachineRecipeRunner} depends on this rather than on a
 * NeoForge capability, so the "pause when there is no energy" rule can be unit tested with a
 * two-line fake. The block entity implements it by delegating to its
 * {@code SimpleEnergyHandler} inside a transaction.</p>
 */
@FunctionalInterface
public interface EnergySource {

    /**
     * Takes up to {@code amount} FE, returning how much was actually taken.
     *
     * <p>Taking less than {@code amount} means the machine cannot advance this tick: a buffer
     * with a per-operation limit legitimately returns a partial amount, and an empty buffer
     * returns zero. Either way the runner pauses.</p>
     */
    int draw(int amount);
}
