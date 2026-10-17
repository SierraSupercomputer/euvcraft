package com.euvcraft.lithmod.mc;

import java.util.List;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;

/**
 * The lookup from a stack to its line component kind id and node tier.
 *
 * <p>A component is recognised by being a {@link LineComponentItem}, so no item
 * ids are held here: adding a component for a later tier is a new registered
 * item with the right kind and tier, and this catalog answers for it on first
 * use.</p>
 */
public final class ComponentCatalog {

    private ComponentCatalog() {
    }

    /**
     * Returns the kind id of the component in a stack, or empty when the stack
     * is not a component.
     */
    public static Optional<String> kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof LineComponentItem item) {
            return Optional.of(item.kind());
        }
        return Optional.empty();
    }

    /**
     * Returns the node tier of the component in a stack, or 0 when the stack
     * is not a component.
     */
    public static int tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() instanceof LineComponentItem item) {
            return item.tier();
        }
        return 0;
    }

    /** The five line component kind ids. */
    public static List<String> knownKinds() {
        return LineComponentItem.KNOWN_KINDS;
    }
}