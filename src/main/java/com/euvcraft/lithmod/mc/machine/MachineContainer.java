package com.euvcraft.lithmod.mc.machine;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link Container} view over a {@link FabMachineBlockEntity}'s slot list.
 *
 * <p>26.3 has no {@code SimpleContainer}, and {@code Slot} needs a real {@code Container},
 * so the menu wraps the block entity's {@code NonNullList} in this. It is the only path the
 * menu has onto the slots, which is what keeps the inventory reachable through the menu and
 * not through a capability.</p>
 */
public final class MachineContainer implements Container {

    private final FabMachineBlockEntity machine;

    public MachineContainer(FabMachineBlockEntity machine) {
        this.machine = machine;
    }

    @Override
    public int getContainerSize() {
        return this.machine.slotCount();
    }

    @Override
    public boolean isEmpty() {
        for (int index = 0; index < getContainerSize(); index++) {
            if (!this.machine.slot(index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.machine.slot(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack stack = this.machine.slot(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.split(Math.min(count, stack.getCount()));
        if (stack.isEmpty()) {
            this.machine.clearSlot(index);
        }
        this.machine.setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = this.machine.slot(index);
        this.machine.clearSlot(index);
        return stack;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.machine.setSlot(index, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        this.machine.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int index = 0; index < getContainerSize(); index++) {
            this.machine.clearSlot(index);
        }
    }
}
