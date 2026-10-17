package com.euvcraft.lithmod.mc.machine.mask;

import java.util.function.Consumer;

import com.euvcraft.lithmod.core.MaskData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        consumer.accept(Component.translatable("tooltip.lithmod.mask.tier", tierName(this.data.tierIndex()))
            .withStyle(ChatFormatting.GRAY));
        consumer.accept(Component.translatable("tooltip.lithmod.mask.uses", this.data.usesRemaining())
            .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Resolves the display name of one die type.
     *
     * <p>The key is built from the path of the stored die type id, so
     * {@code lithmod:logic} resolves to {@code dieType.lithmod.logic} exactly like
     * a bare name would.</p>
     *
     * @param dieType canonical die type id, as {@link MaskData} stores it
     * @return the translated die name
     */
    private static Component dieName(String dieType) {
        return Component.translatable(
            "dieType." + EUVCRAFTMod.MODID + "." + ResourceLocation.parse(dieType).getPath());
    }

    /**
     * Resolves the display name of one node tier.
     *
     * @param tierIndex 1-based tier index the mask targets
     * @return the translated tier name
     */
    private static Component tierName(int tierIndex) {
        return Component.translatable("tier." + EUVCRAFTMod.MODID + ".t" + tierIndex);
    }
}