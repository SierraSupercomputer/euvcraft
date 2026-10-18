package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/**
 * Hooks the photomask tooltip into NeoForge's data component tooltip appenders.
 *
 * <p>Same pattern as {@link com.euvcraft.lithmod.mc.FoupTooltipAppenders}: the
 * appender is registered for {@link ModDataComponents#MASK_DATA}, so the tooltip
 * lives with the data it renders and the item itself needs no tooltip code.</p>
 */
public final class MaskTooltipAppenders {

    private MaskTooltipAppenders() {
    }

    /**
     * Registers the photomask tooltip appender.
     *
     * <p>Called on the mod event bus. This event fires after every registry entry
     * is registered, so the component holder can be resolved here.</p>
     *
     * @param event the appender registration event
     */
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(ModDataComponents.MASK_DATA,
            TooltipAppender.createComponentAppender(ModDataComponents.MASK_DATA.get(),
                MaskTooltip::new));
    }
}