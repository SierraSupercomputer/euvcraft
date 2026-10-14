package com.euvcraft.lithmod.mc;

import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Creation and reading of FOUP stacks.
 *
 * <p>This is the only place that writes {@link ModDataComponents#FOUP_CONTENTS} onto
 * a stack. Everything else reads a lot with {@link #contentsOf(ItemStack)} and
 * rebuilds the stack with {@link #with(Item, FoupContents)}.</p>
 *
 * <p>Creation is server side by design: the lot id is drawn with
 * {@link UUID#randomUUID()} and the capacity comes from the server config, so a
 * client can never choose either. The one caller that also runs on a client is the
 * creative tab, because the game builds tab contents on both sides; see the
 * creative-mode spike in the M0-08 report.</p>
 */
public final class FoupItems {

    private FoupItems() {
    }

    /**
     * Returns the lot capacity a newly created FOUP gets, from the server config.
     *
     * <p>Existing FOUPs are not affected: they keep the capacity stored in their own
     * data, so changing the config only changes the capacity of FOUPs created after
     * the change.</p>
     *
     * @return the configured capacity, or the core default before the config loads
     */
    public static int configuredCapacity() {
        return EUVCRAFTMod.settings().foupCapacity();
    }

    /**
     * Creates a stack of an empty FOUP.
     *
     * @param item the FOUP item to create, not null
     * @return a stack of one FOUP holding no wafers, with a fresh lot id and the
     *         configured capacity
     */
    public static ItemStack emptyFoup(Item item) {
        return with(item, FoupContents.empty(UUID.randomUUID(), configuredCapacity()));
    }

    /**
     * Creates a stack of a FOUP holding the given lot.
     *
     * @param item     the FOUP item to create, not null
     * @param contents the lot to store, not null
     * @return a stack of one FOUP carrying the lot
     */
    public static ItemStack with(Item item, FoupContents contents) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), contents);
        return stack;
    }

    /**
     * Returns the lot carried by a stack.
     *
     * @param stack the stack to read, not null
     * @return the lot, or {@code null} when the stack carries none
     */
    @Nullable
    public static FoupContents contentsOf(ItemStack stack) {
        return stack.get(ModDataComponents.FOUP_CONTENTS.get());
    }

    /**
     * Returns whether a stack carries a lot.
     *
     * @param stack the stack to read, not null
     * @return {@code true} if the stack has the FOUP contents component
     */
    public static boolean isFoup(ItemStack stack) {
        return stack.has(ModDataComponents.FOUP_CONTENTS.get());
    }
}