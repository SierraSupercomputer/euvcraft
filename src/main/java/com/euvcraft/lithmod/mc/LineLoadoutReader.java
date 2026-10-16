package com.euvcraft.lithmod.mc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.euvcraft.lithmod.core.LineLoadout;

import net.minecraft.world.item.ItemStack;

/**
 * Builds a {@link LineLoadout} from the stacks installed in a fab line.
 *
 * <p>The resist tier comes from {@link ResistCatalog}; component kinds come from
 * {@link ComponentCatalog}. This reader never inspects masks, it only takes the
 * mask tier its caller passes in.</p>
 */
public final class LineLoadoutReader {

    private LineLoadoutReader() {
    }

    public static LineLoadout read(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return new LineLoadout(
            ComponentCatalog.tierOf(lightSource),
            ComponentCatalog.tierOf(optics),
            ComponentCatalog.tierOf(stage),
            ComponentCatalog.tierOf(cleanroom),
            ComponentCatalog.tierOf(metrology),
            maskTier,
            ResistCatalog.tierOf(resist),
            1.0
        );
    }

    public static LineLoadout read(List<ItemStack> componentStacks, ItemStack resist, int maskTier) {
        int light = 0, optics = 0, stage = 0, cleanroom = 0, metrology = 0;
        if (componentStacks != null) {
            for (ItemStack stack : componentStacks) {
                Optional<String> kind = ComponentCatalog.kindOf(stack);
                int tier = ComponentCatalog.tierOf(stack);
                if (kind.isEmpty()) continue;
                switch (kind.get()) {
                    case "light_source" -> {
                        if (light == 0) light = tier;
                    }
                    case "optics" -> {
                        if (optics == 0) optics = tier;
                    }
                    case "stage" -> {
                        if (stage == 0) stage = tier;
                    }
                    case "cleanroom" -> {
                        if (cleanroom == 0) cleanroom = tier;
                    }
                    case "metrology" -> {
                        if (metrology == 0) metrology = tier;
                    }
                    default -> {
                    }
                }
            }
        }
        return new LineLoadout(light, optics, stage, cleanroom, metrology, maskTier,
            ResistCatalog.tierOf(resist), 1.0);
    }

    public static LineLoadout read(Map<String, ItemStack> componentStacks, ItemStack resist, int maskTier) {
        Map<String, ItemStack> safe = (componentStacks == null) ? Map.of() : componentStacks;
        return read(List.of(
            safe.getOrDefault("light_source", ItemStack.EMPTY),
            safe.getOrDefault("optics", ItemStack.EMPTY),
            safe.getOrDefault("stage", ItemStack.EMPTY),
            safe.getOrDefault("cleanroom", ItemStack.EMPTY),
            safe.getOrDefault("metrology", ItemStack.EMPTY)
        ), resist, maskTier);
    }

    // Alias method names.
    public static LineLoadout from(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

    public static LineLoadout from(List<ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout from(Map<String, ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout fromStacks(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

    public static LineLoadout fromStacks(List<ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout fromStacks(Map<String, ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout of(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

    public static LineLoadout of(List<ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout of(Map<String, ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout build(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

    public static LineLoadout build(List<ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout build(Map<String, ItemStack> componentStacks, ItemStack resist, int maskTier) {
        return read(componentStacks, resist, maskTier);
    }

    public static LineLoadout readLoadout(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

    public static LineLoadout loadout(ItemStack lightSource, ItemStack optics, ItemStack stage,
            ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier) {
        return read(lightSource, optics, stage, cleanroom, metrology, resist, maskTier);
    }

}
