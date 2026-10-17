package com.euvcraft.lithmod.mc.machine.silicon;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Wafer Slicer's boule slot: takes boules and nothing else.
 *
 * <p>Validation is in {@link #mayPlace}, which is the single hook
 * {@code AbstractContainerMenu.moveItemStackTo} consults for every candidate destination,
 * including during a shift-click, so nothing gets around it.</p>
 */
public class BouleSlot extends Slot {

    public BouleSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.is(WaferSlicerBlockEntity.bouleItem());
    }

    @Override
    public boolean mayPickup(@Nullable Player player) {
        return true;
    }
}