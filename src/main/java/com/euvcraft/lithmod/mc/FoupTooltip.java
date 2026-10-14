package com.euvcraft.lithmod.mc;

import java.util.function.Consumer;

import com.euvcraft.lithmod.core.FoupPublicView;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Renders the FOUP tooltip from a {@link FoupPublicView}.
 *
 * <p>The tooltip shows exactly two numbers: how many wafers the lot holds and the
 * lowest layer it has reached. It can show nothing else, because it is built from the
 * public view: the hidden {@link com.euvcraft.lithmod.core.Wafer#cumulativeRoll()}
 * and {@link com.euvcraft.lithmod.core.Wafer#flags()} values are not in that record
 * and are not sent to the client in the first place.</p>
 *
 * <p>This lives in the mc package rather than in core because
 * {@link TooltipProvider} is a Minecraft interface, and core stays free of
 * Minecraft imports.</p>
 *
 * @param view the client-safe view of the lot
 */
record FoupTooltip(FoupPublicView view) implements TooltipProvider {

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag,
        DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.lithmod.foup.wafers", this.view.waferCount(), this.view.capacity())
            .withStyle(ChatFormatting.GRAY));
        consumer.accept(Component.translatable("tooltip.lithmod.foup.layer", this.view.lotLayer())
            .withStyle(ChatFormatting.GRAY));
    }
}