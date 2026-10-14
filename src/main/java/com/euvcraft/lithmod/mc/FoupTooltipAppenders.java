package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/**
 * Hooks the FOUP tooltip into NeoForge 26.3's data component tooltip appenders.
 *
 * <p>Registering an appender for {@link ModDataComponents#FOUP_CONTENTS} rather than
 * overriding {@code Item#appendHoverText} keeps the tooltip with the data it belongs
 * to, and keeps it off the deprecated vanilla hook.</p>
 */
public final class FoupTooltipAppenders {

    private FoupTooltipAppenders() {
    }

    /**
     * Registers the FOUP tooltip appender.
     *
     * <p>Called on the mod event bus. This event fires after every registry entry is
     * registered, so the component holder can be resolved here.</p>
     *
     * @param event the appender registration event
     */
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(ModDataComponents.FOUP_CONTENTS,
            TooltipAppender.createComponentAppender(ModDataComponents.FOUP_CONTENTS.get(),
                contents -> new FoupTooltip(contents.publicView())));
    }
}