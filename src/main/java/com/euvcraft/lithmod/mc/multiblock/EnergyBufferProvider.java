package com.euvcraft.lithmod.mc.multiblock;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * The energy contract of a multiblock controller.
 *
 * <p>This interface lives in the Minecraft layer, not in {@code core}, because the thing it hands
 * out is a NeoForge {@link EnergyHandler}. M1-03 originally put an equivalent interface in
 * {@code com.euvcraft.lithmod.core.multiblock} returning a core {@code EnergyBuffer}; that was
 * impossible to expose as a capability, so the interface moved here and its return type became the
 * capability type.</p>
 *
 * <p>Only the controller implements this. A power port reaches the buffer through its
 * {@linkplain ProcessCellPowerPortBlockEntity controller}, not by implementing this itself, because
 * the port owns no energy: it lends the controller's.</p>
 *
 * <p>{@code onFormed} and {@code onUnformed} are deliberately <em>not</em> on this interface.
 * Java forbids an implementation from narrowing an interface method to {@code protected}, and M1-09
 * needs those hooks to be overridable from a subclass, so they are declared {@code protected} on
 * {@link ProcessCellControllerBlockEntity} instead.</p>
 */
public interface EnergyBufferProvider {

    /**
     * The controller's energy buffer.
     *
     * <p>Always present, formed or not: what a power port exposes through the
     * {@code Capabilities.Energy.BLOCK} capability depends on the formed state, not on the
     * controller withholding the buffer. The handler refuses extraction, so holding it is not the
     * same as being able to drain it.</p>
     *
     * @return the controller's energy handler, never null
     */
    EnergyHandler energy();
}