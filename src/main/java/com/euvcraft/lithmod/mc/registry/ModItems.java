package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

/**
 * Items registered by EUVCRAFT.
 *
 * <p>Both FOUP variants carry the same {@link ModDataComponents#FOUP_CONTENTS}
 * component and are otherwise identical in this milestone. Mk2 exists so that the
 * later Encoded Logistics transit support has an item to hang off; nothing about it
 * differs yet.</p>
 */
public final class ModItems {

    /** Registry of EUVCRAFT items. */
    public static final Items ITEMS = DeferredRegister.createItems(EUVCRAFTMod.MODID);

    /** FOUP Mk1: the basic sealed lot carrier. */
    public static final DeferredItem<Item> FOUP_MK1 = ITEMS.registerSimpleItem("foup_mk1", properties -> properties.stacksTo(1));

    /** FOUP Mk2: reserved for Encoded Logistics transit support. */
    public static final DeferredItem<Item> FOUP_MK2 = ITEMS.registerSimpleItem("foup_mk2", properties -> properties.stacksTo(1));

    private ModItems() {
    }
}