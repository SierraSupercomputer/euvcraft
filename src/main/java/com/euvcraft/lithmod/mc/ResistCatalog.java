package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.minecraft.world.item.ItemStack;

/**
 * The lookup from a stack to the node tier of the photoresist in it.
 *
 * <p>A resist is only usable at its own node tier, so every consumer of a resist asks
 * this and not the item: {@link #tierOf(ItemStack)} answers 0 for anything that is not
 * a resist, which covers a non-resist item, an empty stack and a resist whose component
 * was stripped.</p>
 *
 * <p>The tier comes from {@link ModDataComponents#RESIST_TIER}, which the resist items
 * set at registration. Adding a resist for a later tier is a new item with that
 * component set; nothing here changes.</p>
 */
public final class ResistCatalog {

    private ResistCatalog() {
    }

    /**
     * Returns the node tier of the resist in a stack.
     *
     * @param stack the stack to look at, not null
     * @return the node tier index of the resist, 1 for T1, or 0 when the stack holds
     *         no resist
     */
    public static int tierOf(ItemStack stack) {
        Integer tier = stack.get(ModDataComponents.RESIST_TIER.get());
        return tier == null ? 0 : tier;
    }
}