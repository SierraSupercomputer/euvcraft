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

    /** High-NA optics and the upgraded chassis raise the validated EUV hardware rating. */
    public static LineLoadout highNa(LineLoadout euv) {
        if (euv.lightSourceTier() != 7 || euv.opticsTier() != 7 || euv.stageTier() != 7
                || euv.cleanroomTier() != 7 || euv.metrologyTier() != 7 || euv.resistTier() != 8) {
            throw new IllegalArgumentException("High-NA requires the complete EUV hardware set and T8 resist");
        }
        return new LineLoadout(8, 8, 8, 8, 8, euv.maskTier(), euv.resistTier(), euv.maintenance());
    }

    private static int tierFor(ItemStack stack, String kind) {
        return ComponentCatalog.kindOf(stack).filter(kind::equals).isPresent()
            ? ComponentCatalog.tierOf(stack) : 0;
    }
}
