package com.euvcraft.lithmod.mc;

import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

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
     * Returns the lot of a stack, refusing anything that cannot be trusted.
     *
     * <p>Every gameplay read of a lot should go through this and not through
     * {@link #contentsOf(ItemStack)}. It answers for three cases:</p>
     *
     * <ul>
     *   <li>the stack carries no lot at all: a fresh empty lot is returned. This is
     *       what a plain {@code /give} of a FOUP item produces, so a FOUP item without
     *       a lot in it is logged as a warning while a stack of something else is not,
     *       because asking "is this a FOUP" must not fill the log;</li>
     *   <li>the lot is a client view that still holds wafers: the wafer count and the
     *       rolls are the client's invention and every roll reads as
     *       {@link Wafer#NO_ROLLS}, which passes any yield check. A warning is logged
     *       and a fresh empty lot is returned;</li>
     *   <li>the lot is a client view with no wafers: the capacity came from the
     *       client, so a fresh lot with a new lot id and the configured capacity is
     *       returned instead of the client's value. Nothing was lost, so this case is
     *       not a warning.</li>
     * </ul>
     *
     * <p>A warning names the item and the wafer count and nothing else: rolls are
     * hidden values and must never reach a log line.</p>
     *
     * <p>This is the part of the creative-mode spike that is cheap enough to do now:
     * it stops a client-replaced stack from feeding fabricated wafers into a machine.
     * It does not stop a client from choosing a lot id or duplicating a lot; see the
     * M0-08 report for the rest.</p>
     *
     * @param stack the stack to read, not null
     * @return a lot the caller may read rolls from, never null
     */
    public static FoupContents trustedContentsOf(ItemStack stack) {
        FoupContents contents = contentsOf(stack);
        if (contents == null) {
            if (stack.is(ModItems.FOUP_MK1.get()) || stack.is(ModItems.FOUP_MK2.get())) {
                EUVCRAFTMod.LOGGER.warn(
                    "EUVCRAFT discarded a FOUP stack of {} that carries no sealed lot", stack.getItem());
            }
            return freshEmptyLot();
        }

        if (!FoupContentsCodec.isClientView(contents)) {
            return contents;
        }

        if (!contents.isEmpty()) {
            EUVCRAFTMod.LOGGER.warn(
                "EUVCRAFT discarded a FOUP stack of {} whose sealed lot is a client view holding {} wafers;"
                    + " the hidden rolls were never filled in by the server",
                stack.getItem(), contents.size());
        }

        return freshEmptyLot();
    }

    private static FoupContents freshEmptyLot() {
        return FoupContents.empty(UUID.randomUUID(), configuredCapacity());
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

    /**
     * Initializes a FOUP stack with a fresh lot if it lacks FOUP_CONTENTS.
     *
     * <p>MUST be called server-side only. Guards against non-FOUP items and
     * never replaces an existing component (trusted lot OR client-view).
     * Uses {@link #emptyFoup(Item)} to obtain a fresh UUID/configured capacity
     * and copies ONLY the generated component onto the original stack.
     * Keeps other components and stack count.</p>
     *
     * @param stack the stack to initialize, not null
     */
    public static void initializeIfMissing(ItemStack stack) {
        // Exact registered FOUP item guard
        Item item = stack.getItem();
        if (!(item == ModItems.FOUP_MK1.get() || item == ModItems.FOUP_MK2.get())) {
            return;
        }

        // Only initialize when FOUP_CONTENTS is ABSENT
        if (!stack.has(ModDataComponents.FOUP_CONTENTS.get())) {
            ItemStack fresh = emptyFoup(item);
            FoupContents contents = fresh.get(ModDataComponents.FOUP_CONTENTS.get());
            if (contents != null) {
                stack.set(ModDataComponents.FOUP_CONTENTS.get(), contents);
            }
        }
    }
}