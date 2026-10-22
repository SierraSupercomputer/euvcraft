package com.euvcraft.lithmod.mc.compat;

import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** Background tooltip indexing needs text, never GPU-backed glyph measurements. */
public final class TooltipLineWrapping {
    private TooltipLineWrapping() {}

    public static List<FormattedText> wrap(StringSplitter splitter, FormattedText text,
            int width, Style style, boolean renderThread) {
        return renderThread ? splitter.splitLines(text, width, style) : List.of(text);
    }
}
