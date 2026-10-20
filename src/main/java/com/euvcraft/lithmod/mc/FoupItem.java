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
 * fresh server lot when the component is absent. The second hook covers the one
 * untrusted case the creative inventory creates: when a Creative player takes a FOUP,
 * {@code ServerboundSetCreativeModeSlotPacket} places a client-view stack
 * ({@link FoupContentsCodec#fromPublicView(int, int, int)}) on the server slot, with
 * whatever capacity the network form carried. A registered FOUP stack of exactly one,
 * held by a Creative owner, whose lot is an empty client view with no run spec, is
 * repaired in place to a server empty lot: fresh lot id, configured capacity, same
 * custom name, components and stack count.
 *
 * <p>Everything else stays untouched: sealed lots (empty or filled), non-empty or
 * over-stacked client views, Survival owners and non-player owners.
 * {@link FoupItems#trustedContentsOf(ItemStack)} still discards every client view it
 * is ever asked to trust.
 */
public class FoupItem extends Item {

    public FoupItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        FoupItems.initializeIfMissing(stack);
        maybeReplaceCreativeEmptyClientView(stack, owner);
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
     * Replaces an empty client-view lot with a server empty lot for a Creative owner.
     *
     * <p>All guards must pass: a ServerPlayer with {@code hasInfiniteMaterials()},
     * a registered FOUP item, a stack of exactly one, a present client-view lot,
     * no wafers and no run spec. Sealed lots, non-empty or over-stacked client
     * views and Survival or non-player owners are never repaired.</p>
     *
     * @param stack the stack to potentially fix, never null
     * @param owner the entity holding the stack, may be null
     */
    private void maybeReplaceCreativeEmptyClientView(ItemStack stack, @Nullable Entity owner) {
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
        if (contents == null
            || contents.run() != null
            || !contents.isEmpty()
            || !FoupContentsCodec.isClientView(contents)) {
            return;
        }
        var repaired = FoupItems.emptyFoup(item).get(ModDataComponents.FOUP_CONTENTS.get());
        if (repaired != null) {
            stack.set(ModDataComponents.FOUP_CONTENTS.get(), repaired);
        }
    }
}
