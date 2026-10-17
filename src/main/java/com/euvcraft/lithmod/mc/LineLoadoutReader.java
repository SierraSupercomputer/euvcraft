package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.LineLoadout;
import net.minecraft.world.item.ItemStack;

/** Reads the five named component slots; mask tier is supplied by the caller. */
public final class LineLoadoutReader {
    private LineLoadoutReader() { }

    public static LineLoadout read(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return new LineLoadout(
            tierFor(lightSource, "light_source"), tierFor(optics, "optics"),
            tierFor(stage, "stage"), tierFor(cleanroom, "cleanroom"), tierFor(metrology, "metrology"),
            maskTier, ResistCatalog.tierOf(resist), 1.0);
    }

    private static int tierFor(ItemStack stack, String kind) {
        return ComponentCatalog.kindOf(stack).filter(kind::equals).isPresent()
            ? ComponentCatalog.tierOf(stack) : 0;
    }
}
