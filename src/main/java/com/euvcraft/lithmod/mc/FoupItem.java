package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * FOUP item lifecycle: initialize a missing lot and repair the creative-inventory
 * client view.
 *
 * <p>{@link FoupItems#initializeIfMissing(ItemStack)} gives crafted and /give FOUPs a
 * fresh server lot when the component is absent. The second hook covers the
 * untrusted cases the creative inventory creates: when a Creative player takes a
 * FOUP, {@code ServerboundSetCreativeModeSlotPacket} places a client-view stack
 * ({@link FoupContentsCodec#fromPublicView(int, int, int)}) on the server slot, with
 * whatever capacity the network form carried. A registered FOUP stack of exactly
 * one, held by a Creative owner, whose lot is a client view is repaired in place:
 * <ul>
 *   <li>an <em>empty</em> client view, with no run spec, is replaced by a server
 *       empty lot: fresh lot id, configured capacity, same custom name, components
 *       and stack count;</li>
 *   <li>an <em>unstarted filled</em> client view (no run spec, every wafer at layer
 *       0) is replaced by the server lot it stands for, through
 *       {@link FoupContentsCodec#unstartedServerLot}: for such a lot the hidden
 *       rolls are exactly {@link com.euvcraft.lithmod.core.Wafer#NO_ROLLS} by
 *       definition, so the recreated hidden state is server-created, nothing
 *       fabricated is trusted and no private rolls, flags or run data ever reach
 *       the client;</li>
 *   <li>anything else - a client view of a processed lot whose rolls the server
 *       cannot know again, a started view, an over-stacked view - is left
 *       untouched, and {@link FoupItems#trustedContentsOf(ItemStack)} still
 *       discards every client view it is ever asked to trust.</li>
 * </ul>
 *
 * <p>Everything else stays untouched: sealed lots (empty or filled) are never
 * network forms, Survival owners and non-player owners never trigger the repair.
 */
public class FoupItem extends Item {

    public FoupItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        FoupItems.initializeIfMissing(stack);
        maybeReplaceCreativeClientView(stack, owner);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        this.onCraftedPostProcess(stack, player.level());
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        if (level instanceof ServerLevel) {
            FoupItems.initializeIfMissing(stack);
        }
    }

    /**
     * Replaces an untrustworthy client-view lot with a server lot for a Creative
     * owner.
     *
     * <p>All guards must pass: a ServerPlayer with {@code hasInfiniteMaterials()},
     * a registered FOUP item, a stack of exactly one and a present client-view lot.
     * Two shapes are repaired: an empty view becomes a fresh server empty lot, and
     * an unstarted filled view (no run spec, all wafers at layer 0) is rebuilt by
     * {@link FoupContentsCodec#unstartedServerLot}. Sealed lots, started or
     * processed views and Survival or non-player owners are never repaired.</p>
     *
     * @param stack the stack to potentially fix, never null
     * @param owner the entity holding the stack, may be null
     */
    private void maybeReplaceCreativeClientView(ItemStack stack, @Nullable Entity owner) {
        if (!(owner instanceof ServerPlayer player) || !player.hasInfiniteMaterials()) {
            return;
        }
        Item item = stack.getItem();
        if (item != ModItems.FOUP_MK1.get() && item != ModItems.FOUP_MK2.get()) {
            return;
        }
        if (stack.getCount() != 1) {
            return;
        }
        var contents = stack.get(ModDataComponents.FOUP_CONTENTS.get());
        if (contents == null || !FoupContentsCodec.isClientView(contents)) {
            return;
        }
        if (contents.run() != null) {
            return;
        }
        var repaired = contents.isEmpty()
            ? FoupItems.emptyFoup(item).get(ModDataComponents.FOUP_CONTENTS.get())
            : FoupContentsCodec.unstartedServerLot(contents);
        if (repaired != null) {
            stack.set(ModDataComponents.FOUP_CONTENTS.get(), repaired);
        }
    }
}
