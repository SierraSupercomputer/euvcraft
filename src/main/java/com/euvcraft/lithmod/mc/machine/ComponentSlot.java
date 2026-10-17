package com.euvcraft.lithmod.mc.machine;

import com.euvcraft.lithmod.mc.ComponentCatalog;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A slot that accepts only items of one component kind, one item at a time.
 */
public class ComponentSlot extends Slot {

    private final String kind;

    public ComponentSlot(Container container, int index, int x, int y, String kind) {
        super(container, index, x, y);
        if (!ComponentCatalog.knownKinds().contains(kind)) {
            throw new IllegalArgumentException("unknown component kind: " + kind);
        }
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && ComponentCatalog.kindOf(stack).map(k -> k.equals(kind)).orElse(false);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPickup(@Nullable Player player) {
        return true;
    }
}