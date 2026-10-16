package com.euvcraft.lithmod.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

public final class ComponentTooltipAppenders {

    private ComponentTooltipAppenders() {
    }

    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.HEAD,
            (stack, context, display, player, flag, builder) ->
                ComponentCatalog.kindOf(stack).ifPresent(kind -> {
                    int tier = ComponentCatalog.tierOf(stack);
                    builder.accept(Component.translatable("tooltip.lithmod.component",
                        Component.translatable("componentKind.lithmod." + kind),
                        Component.translatable("tier.lithmod.t" + tier))
                        .withStyle(ChatFormatting.GRAY));
                }));
    }
}