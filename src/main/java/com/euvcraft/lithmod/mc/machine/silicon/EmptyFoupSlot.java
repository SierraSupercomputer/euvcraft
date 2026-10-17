package com.euvcraft.lithmod.mc.machine.silicon;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Wafer Slicer's input slot: takes only a FOUP that carries an empty lot.
 *
 * <p>The rule itself lives in
 * {@link WaferSlicerBlockEntity#acceptsFoupStack(ItemStack)} so the slot cannot disagree
 * with itself between the two sides of the wire. Validation is in {@link #mayPlace},
 * which runs on the client menu as well as the server one, so a stack-click is turned away
 * before a packet is sent.</p>
 *
 * <p>What the two sides agree on is exactly what the client can see: the wafer count. A
 * client cannot know about a lot's hidden run spec because the network codec does not send
 * one, so a client-side FOUP with no wafers is predicted acceptable even when the server's
 * lot is started. The server tick re-checks with the trusted read, which is where that case
 * is refused. See the full boundary on
 * {@link WaferSlicerBlockEntity#acceptsFoupStack(ItemStack)}.</p>
 */
public class EmptyFoupSlot extends Slot {

    public EmptyFoupSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return WaferSlicerBlockEntity.acceptsFoupStack(stack);
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
