package com.euvcraft.lithmod.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/**
 * Hooks the chip tooltip into NeoForge 26.3's tooltip appenders.
 *
 * <p>The line is built from {@link ChipCatalog} alone: the die type and the node tier
 * a chip stands for. It deliberately does not read tier data, because a tooltip is
 * built on the client and the client may have no tier data loaded, and it carries no
 * yield, wafer count or roll, because a chip is a finished die and nothing about the
 * lot it came out of is public.</p>
 *
 * <p>The appender is registered for every item and asks the catalog, so a chip item
 * needs no tooltip code of its own and a later chip gets its line by being registered
 * in the catalog.</p>
 */
public final class ChipTooltipAppenders {

    private ChipTooltipAppenders() {
    }

    /**
     * Registers the chip tooltip appender.
     *
     * <p>Called on the mod event bus.</p>
     *
     * @param event the appender registration event
     */
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.HEAD,
            (stack, context, display, player, flag, builder) -> ChipCatalog.designOf(stack.getItem())
                .ifPresent(design -> builder.accept(Component.translatable("tooltip.lithmod.chip",
                    Component.translatable("dieType.lithmod." + design.dieType()),
                    Component.translatable("tier.lithmod.t" + design.tierIndex()))
                    .withStyle(ChatFormatting.GRAY))));
    }
}