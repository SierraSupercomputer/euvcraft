package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.FoupItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A slot that accepts only FOUP items.
 * Validates on the server; shift-click cannot bypass validation.
 */
public class FoupSlot extends Slot {

    public FoupSlot(net.minecraft.world.Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // Server-side validation
        return FoupItems.isFoup(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1; // FOUPs stack to 1
    }

    @Override
    public boolean mayPickup(@Nullable Player player) {
        return true;
    }

    // Shift-click handling: the default Slot implementation calls mayPlace on the target slot
    // when shift-clicking from player inventory, so our validation is respected.
    // We don't need to override quickMoveStack because the target slot's mayPlace is checked.
}