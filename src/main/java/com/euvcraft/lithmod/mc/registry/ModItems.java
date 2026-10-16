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
 *
 * <p>Everything from {@link #LOGIC_CHIP_T1} onwards is a plain item: no behaviour, no
 * hidden data and no machine. Chips are told apart by the design they stand for, which
 * {@link com.euvcraft.lithmod.mc.ChipCatalog} keeps next to them; resist items carry
 * their node tier in {@link ModDataComponents#RESIST_TIER}, which is how
 * {@link com.euvcraft.lithmod.mc.ResistCatalog} answers for a stack. The resist tier
 * number in an item definition is part of which resist that item is, not a tunable
 * gameplay number.</p>
 *
 * <p>None of these items sets a stack size, so they all use the vanilla maximum of
 * 64. Only the FOUPs stack to one.</p>
 */
public final class ModItems {

    /** Registry of EUVCRAFT items. */
    public static final Items ITEMS = DeferredRegister.createItems(EUVCRAFTMod.MODID);

    /** FOUP Mk1: the basic sealed lot carrier. */
    public static final DeferredItem<Item> FOUP_MK1 = ITEMS.registerSimpleItem("foup_mk1", properties -> properties.stacksTo(1));

    /** FOUP Mk2: reserved for Encoded Logistics transit support. */
    public static final DeferredItem<Item> FOUP_MK2 = ITEMS.registerSimpleItem("foup_mk2", properties -> properties.stacksTo(1));

    /** A logic die cut at node tier 1. */
    public static final DeferredItem<Item> LOGIC_CHIP_T1 = ITEMS.registerSimpleItem("logic_chip_t1");

    /** A logic die cut at node tier 2. */
    public static final DeferredItem<Item> LOGIC_CHIP_T2 = ITEMS.registerSimpleItem("logic_chip_t2");

    /** A memory die cut at node tier 2. */
    public static final DeferredItem<Item> MEMORY_CHIP_T2 = ITEMS.registerSimpleItem("memory_chip_t2");

    /** One failed chip, recycled back into a usable die. */
    public static final DeferredItem<Item> CHIP_SCRAP = ITEMS.registerSimpleItem("chip_scrap");

    /** Photoresist for node tier 1 lithography. */
    public static final DeferredItem<Item> RESIST_T1 =
        ITEMS.registerSimpleItem("resist_t1", properties -> properties.component(ModDataComponents.RESIST_TIER, 1));

    /** Photoresist for node tier 2 lithography. */
    public static final DeferredItem<Item> RESIST_T2 =
        ITEMS.registerSimpleItem("resist_t2", properties -> properties.component(ModDataComponents.RESIST_TIER, 2));

    /** The packaging consumable the Test and Package machine feeds on. */
    public static final DeferredItem<Item> CHIP_PACKAGE = ITEMS.registerSimpleItem("chip_package");

    private ModItems() {
    }
}