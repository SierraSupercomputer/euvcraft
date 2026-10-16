package com.euvcraft.lithmod.mc.machine;

import com.euvcraft.lithmod.mc.FoupItems;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A slot that accepts only FOUP stacks, one at a time.
 *
 * <p>Validation lives in {@link #mayPlace}, which is the single hook
 * {@code AbstractContainerMenu.moveItemStackTo} consults for every candidate destination,
 * including during a shift-click. A shift-click therefore cannot put a non-FOUP stack here:
 * the move walks the destination slots and skips any whose {@code mayPlace} returns false.
 * Nothing overrides {@code quickMoveStack}, so there is no second path around the check.</p>
 */
public class FoupSlot extends Slot {

    public FoupSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return FoupItems.isFoup(stack);
    }

    /** One FOUP per slot; a FOUP is a carrier, not a stack of goods. */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPickup(@Nullable Player player) {
        return true;
    }
}
