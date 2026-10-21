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
 * <p>The tooltip shows the wafer count, capacity, lot layer, wafer substrate
 * type, and preparation generation. An empty lot says "Empty (not pure wafers)".
 * A non-empty lot shows "Ordinary silicon" or "Pure silicon" along with
 * unprocessed/processed progress wording. It can show nothing else, because it is
 * built from the public view: the hidden {@link com.euvcraft.lithmod.core.Wafer#cumulativeRoll()}
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
        consumer.accept(Component.translatable("tooltip.lithmod.foup.preparation", this.view.preparationGeneration())
            .withStyle(ChatFormatting.GRAY));
        if (this.view.isEmpty()) {
            consumer.accept(Component.translatable("tooltip.lithmod.foup.empty")
                .withStyle(ChatFormatting.GRAY));
        } else {
            String substrateKey = this.view.grade() == com.euvcraft.lithmod.core.WaferSubstrate.PURE
                ? "tooltip.lithmod.foup.substrate.pure"
                : "tooltip.lithmod.foup.substrate.ordinary";
            consumer.accept(Component.translatable(substrateKey)
                .withStyle(ChatFormatting.GRAY));
            if (this.view.lotLayer() == 0) {
                consumer.accept(Component.translatable("tooltip.lithmod.foup.unprocessed")
                    .withStyle(ChatFormatting.DARK_GRAY));
            } else if (this.view.lotLayer() > 0) {
                consumer.accept(Component.translatable("tooltip.lithmod.foup.processed", this.view.lotLayer())
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}