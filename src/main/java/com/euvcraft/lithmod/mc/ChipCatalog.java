package com.euvcraft.lithmod.mc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The lookup from a die type and a node tier to the chip item that carries it.
 *
 * <p>There is exactly one chip item per die type and tier, and the pair is what a lot
 * needs to say "what came off the wafer": {@link com.euvcraft.lithmod.core.RunSpec}
 * freezes the die type at layer 1, and the Test and Package machine asks this catalog
 * for the item to put in its output slot. Asking the catalog keeps that machine free
 * of a switch over tiers.</p>
 *
 * <p>The list of entries is the only thing a later milestone has to touch to add a die
 * type: one line per new chip, no change here and no change at the call sites. Chips
 * that do not exist have no entry, so they answer empty rather than throwing, which is
 * what a die type that is not available yet (memory at T1) or not implemented yet
 * (T4 and later) gets.</p>
 *
 * <p>This lives in the mc package because an answer is an {@link Item}, and core stays
 * free of Minecraft imports. Nothing here reads tier data: a tier index is all a
 * catalog entry carries.</p>
 */
public final class ChipCatalog {

    /**
     * The design a chip item stands for: the die type it was cut from and the node tier
     * it was cut at.
     *
     * @param dieType   a canonical {@link DieTypes} string
     * @param tierIndex the node tier index, 1 for T1
     */
    public record Design(String dieType, int tierIndex) {
    }

    private record Entry(Design design, DeferredItem<? extends Item> item) {
    }

    private record Tables(Map<Design, Item> byDesign, Map<Item, Design> byItem) {
    }

    private static final List<Entry> ENTRIES = List.of(
        new Entry(new Design(DieTypes.LOGIC, 1), ModItems.LOGIC_CHIP_T1),
        new Entry(new Design(DieTypes.LOGIC, 2), ModItems.LOGIC_CHIP_T2),
        new Entry(new Design(DieTypes.MEMORY, 2), ModItems.MEMORY_CHIP_T2),
        new Entry(new Design(DieTypes.LOGIC, 3), ModItems.LOGIC_CHIP_T3),
        new Entry(new Design(DieTypes.MEMORY, 3), ModItems.MEMORY_CHIP_T3),
        new Entry(new Design(DieTypes.COMPUTE, 3), ModItems.COMPUTE_CHIP_T3),
        new Entry(new Design(DieTypes.NAND, 3), ModItems.NAND_CHIP_T3),
        new Entry(new Design(DieTypes.LOGIC, 4), ModItems.LOGIC_CHIP_T4),
        new Entry(new Design(DieTypes.MEMORY, 4), ModItems.MEMORY_CHIP_T4),
        new Entry(new Design(DieTypes.COMPUTE, 4), ModItems.COMPUTE_CHIP_T4),
        new Entry(new Design(DieTypes.NAND, 4), ModItems.NAND_CHIP_T4));

    private static volatile Tables tables;

    private ChipCatalog() {
    }

    /**
     * Returns the chip item for a die type at a node tier.
     *
     * @param dieType   a canonical {@link DieTypes} string, not null
     * @param tierIndex the node tier index, 1 for T1
     * @return the chip item, or empty when no chip carries that design yet
     */
    public static Optional<Item> chipFor(String dieType, int tierIndex) {
        return Optional.ofNullable(tables().byDesign().get(new Design(dieType, tierIndex)));
    }

    /**
     * Returns the design a chip item stands for.
     *
     * @param item the item to look up, not null
     * @return the design, or empty when the item is not a chip
     */
    public static Optional<Design> designOf(Item item) {
        return Optional.ofNullable(tables().byItem().get(item));
    }

    /**
     * Resolves the entries against the item registry on first use.
     *
     * <p>Building is idempotent and the tables never change, so two threads racing here
     * both get the same answers; only one of the two maps is kept.</p>
     */
    private static Tables tables() {
        Tables current = tables;
        if (current != null) {
            return current;
        }

        Map<Design, Item> byDesign = new HashMap<>();
        Map<Item, Design> byItem = new HashMap<>();
        for (Entry entry : ENTRIES) {
            Item item = entry.item().get();
            if (byDesign.putIfAbsent(entry.design(), item) != null) {
                throw new IllegalStateException("two chip items claim the design " + entry.design());
            }
            byItem.putIfAbsent(item, entry.design());
        }

        current = new Tables(Map.copyOf(byDesign), Map.copyOf(byItem));
        tables = current;
        return current;
    }
}