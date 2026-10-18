package com.euvcraft.lithmod.mc.machine.mask;

import java.util.function.Consumer;

import com.euvcraft.lithmod.core.MaskData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

/**
 * Renders the photomask tooltip from the public {@link MaskData} component.
 *
 * <p>Three lines, all of them built from values the client already holds: which
 * die the mask patterns, which node tier it was cut for and how many exposures
 * are left. There is no second, public-only view of a mask because a mask has no
 * hidden part to keep out of a tooltip, the same reason
 * {@link com.euvcraft.lithmod.mc.FoupTooltip} can read a public view instead of
 * a lot.</p>
 *
 * <p>This lives in the mc package tree because {@link TooltipProvider} is a
 * Minecraft interface and core stays free of Minecraft imports.</p>
 *
 * @param data the public design of the hovered mask
 */
record MaskTooltip(MaskData data) implements TooltipProvider {

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag,
        DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.lithmod.mask.die", dieName(this.data.dieType()))
            .withStyle(ChatFormatting.GRAY));
        consumer.accept(Component.translatable("tooltip.lithmod.mask.tier", this.data.tierIndex())
            .withStyle(ChatFormatting.GRAY));
        consumer.accept(Component.translatable("tooltip.lithmod.mask.uses", this.data.usesRemaining())
            .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Resolves the display name of one die type.
     *
     * <p>The key is built straight from the die id that {@link MaskData} already
     * validated against {@link com.euvcraft.lithmod.core.DieTypes#ALL}: the
     * canonical ids are bare, so {@code logic} resolves to
     * {@code dieType.lithmod.logic} with no id parsing at all.</p>
     *
     * @param dieType canonical die type id, as {@link MaskData} stores it
     * @return the translated die name
     */
    private static Component dieName(String dieType) {
        return Component.translatable("dieType." + EUVCRAFTMod.MODID + "." + dieType);
    }
}