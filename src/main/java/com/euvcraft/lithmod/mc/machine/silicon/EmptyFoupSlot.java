package com.euvcraft.lithmod.mc.machine.silicon;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Wafer Slicer's input slot: takes only a FOUP that carries an empty lot.
 *
 * <p>The rule itself lives in {@link WaferSlicerBlockEntity#acceptedFoup(ItemStack)} so the
 * slot and the server tick cannot disagree. Validation is in {@link #mayPlace}, which runs
 * on the client menu as well as the server one, so the slot refuses a full lot on both
 * sides of the wire and a stack-click is turned away before a packet is sent.</p>
 */
public class EmptyFoupSlot extends Slot {

    public EmptyFoupSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return WaferSlicerBlockEntity.acceptedFoup(stack) != null;
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