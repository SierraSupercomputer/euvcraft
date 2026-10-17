package com.euvcraft.lithmod.mc;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A fab line component item.
 *
 * <p>Carries the kind id and the node tier it is rated for, so
 * {@link ComponentCatalog} can answer both from a stack without holding an
 * item id list. The kind is one of the five line component kinds and the tier
 * is a node tier index, 1 or higher for a real component.</p>
 */
public final class LineComponentItem extends Item {

    private final String kind;
    private final int tier;

    public LineComponentItem(Properties properties, String kind, int tier) {
        super(properties);
        if (!KNOWN_KINDS.contains(kind)) {
            throw new IllegalArgumentException("unknown component kind: " + kind);
        }
        if (tier < 1 || tier > 8) {
            throw new IllegalArgumentException("tier must be in [1, 8], got " + tier);
        }
        this.kind = kind;
        this.tier = tier;
    }

    /** The component kind id this item carries. */
    public String kind() {
        return kind;
    }

    /** The node tier this item is rated for. */
    public int tier() {
        return tier;
    }


    public static final List<String> KNOWN_KINDS =
        List.of("light_source", "optics", "stage", "cleanroom", "metrology");
}