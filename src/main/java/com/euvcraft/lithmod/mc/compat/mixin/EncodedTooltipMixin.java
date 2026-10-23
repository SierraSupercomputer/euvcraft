package com.euvcraft.lithmod.mc.compat.mixin;

import com.euvcraft.lithmod.mc.compat.TooltipLineWrapping;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Compatibility with Encoded Logistics 1.2.1 and Arcforge 2.5.1 async tooltip wrapping. */
@Mixin(targets = {"net.zagdrath.encodedlogistics.client.ItemInfoTooltips",
        "net.zagdrath.arcforge.client.ItemInfoTooltips"}, remap = false)
public abstract class EncodedTooltipMixin {
    @Redirect(method = "onTooltip", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/StringSplitter;splitLines(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/network/chat/Style;)Ljava/util/List;"), require = 1)
    private static List<FormattedText> lithmod$threadSafeDescription(StringSplitter splitter,
            FormattedText text, int width, Style style) {
        return TooltipLineWrapping.wrap(splitter, text, width, style, RenderSystem.isOnRenderThread());
    }
}
