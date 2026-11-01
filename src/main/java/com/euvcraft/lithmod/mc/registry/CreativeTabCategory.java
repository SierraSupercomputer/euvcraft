package com.euvcraft.lithmod.mc.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Classification of items into the three EUVCRAFT creative tabs.
 *
 * <p>The classification is based purely on the item's registry path and whether it is a
 * {@link BlockItem}. No world or config access is performed.</p>
 */
public enum CreativeTabCategory {
    RESOURCES,
    FAB_BLOCKS,
    ITEMS;

    /**
     * Returns the category for the given item.
     *
     * <p>Classification rules (in order):</p>
     * <ol>
     *   <li>If the item is a {@link BlockItem}, it is {@code FAB_BLOCKS}.</li>
     *   <li>Otherwise, the registry path is inspected to decide between {@code RESOURCES} and {@code ITEMS}.</li>
     * </ol>
     *
     * @param item the item to classify, not null
     * @return the creative tab category
     */
    public static CreativeTabCategory of(Item item) {
        if (item instanceof BlockItem) {
            return FAB_BLOCKS;
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();

        // RESOURCES: raw materials, feedstocks, consumables, scrap
        if (isResource(path)) {
            return RESOURCES;
        }
        // Everything else (FOUPs, masks, finished chips/assemblies, line components, armor) -> ITEMS
        return ITEMS;
    }

    private static boolean isResource(String path) {
        // Silicon chain feedstocks and boules
        if (path.equals("silicon") ||
            path.equals("silicon_boule") ||
            path.equals("pure_silicon_boule") ||
            path.equals("electronic_grade_boule")) {
            return true;
        }
        // Resists (all tiers)
        if (path.startsWith("resist_")) {
            return true;
        }
        // Blank chip packages and assembly blanks
        if (path.equals("chip_package") ||
            path.equals("cpu_blank") ||
            path.equals("dimm_blank") ||
            path.equals("microcontroller_blank")) {
            return true;
        }
        // Filter media
        if (path.equals("utility_filter") || path.endsWith("_filter_medium") || path.endsWith("_bucket")) {
            return true;
        }
        // Tin feedstock
        if (path.equals("tin_feedstock") || path.equals("etchant_charge")) {
            return true;
        }
        // Burn paper and thermal coupons
        if (path.equals("burn_paper") ||
            path.equals("thermal_test_coupon")) {
            return true;
        }
        // Scrap items
        if (path.equals("chip_scrap") ||
            path.equals("wafer_scrap") ||
            path.equals("assembly_scrap")) {
            return true;
        }
        return false;
    }
}
