package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * FOUP item that initializes a sealed lot when the component is absent.
 *
 * <p>Fresh crafted and given FOUPs currently lack {@link ModDataComponents#FOUP_CONTENTS} and are unusable.
 * This item hooks into the standard Item lifecycle to initialize an actual FOUP stack when
 * FOUP_CONTENTS is ABSENT; it uses {@link FoupItems#emptyFoup(Item)} to obtain a fresh UUID and
 * configured capacity and copies ONLY the generated component onto the original stack.
 *
 * <p>Never replaces an existing component (trusted lot OR client-view); doesn't change
 * {@link FoupItems#trustedContentsOf(ItemStack)} reading/authority semantics. Avoids default
 * properties component or fixed UUID. Each separately initialized stack has fresh server identity.
 * Keeps other components/count.</p>
 */
public class FoupItem extends Item {

    public FoupItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        // Server-side only: initialize missing FOUP_CONTENTS for /give and similar
        FoupItems.initializeIfMissing(stack);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        this.onCraftedPostProcess(stack, player.level());
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        // Only initialize on server; inherited onCraftedBy invokes this override
        if (level instanceof ServerLevel serverLevel) {
            FoupItems.initializeIfMissing(stack);
        }
    }
}