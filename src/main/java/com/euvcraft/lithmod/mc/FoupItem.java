package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        // Creative-mode fix: replace empty client-view lot with server-generated empty lot
        maybeReplaceCreativeEmptyClientView(stack, owner);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        this.onCraftedPostProcess(stack, player.level());
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        // Only initialize on server; inherited onCraftedBy invokes this override
        if (level instanceof ServerLevel) {
            FoupItems.initializeIfMissing(stack);
        }
    }

    /**
     * Replaces an empty client-view FOUP lot with a server-generated empty lot for Creative players.
     *
     * <p>When a Creative player receives a FOUP via the creative inventory or /give, the server
     * receives a {@code ServerboundSetCreativeModeSlotPacket} containing a client-view-only stack.
     * The packet handler replaces the server slot with this client-view stack when the player has
     * infinite materials (Creative mode). This method detects that case and replaces the client-view
     * lot with a proper server-generated empty lot (fresh UUID, configured capacity), preserving
     * the custom name, other components, and stack count.</p>
     *
     * <p>Conditions (all must be true):
     * <ul>
     *   <li>Owner is a {@link ServerPlayer} with {@link ServerPlayer#hasInfiniteMaterials()} true</li>
     *   <li>Stack is a registered FOUP item (Mk1 or Mk2)</li>
     *   <li>Stack carries a {@link ModDataComponents#FOUP_CONTENTS} component</li>
     *   <li>The lot is a client view ({@link FoupContentsCodec#isClientView})</li>
     *   <li>The lot is empty (no wafers)</li>
     *   <li>The lot has no run spec</li>
     * </ul>
     *
     * <p>Sealed lots (empty or filled), non-empty client views, malformed overstack views,
     * Survival/non-player owners, and non-FOUP items are all untouched.</p>
     *
     * @param stack the stack to potentially fix, not null
     * @param owner the entity holding the stack, may be null
     */
    private void maybeReplaceCreativeEmptyClientView(ItemStack stack, @Nullable Entity owner) {
        // Only for ServerPlayer in Creative mode (hasInfiniteMaterials)
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            return;
        }

        // Exact registered FOUP item guard (Mk1 or Mk2)
        Item item = stack.getItem();
        if (!(item == com.euvcraft.lithmod.mc.registry.ModItems.FOUP_MK1.get()
                || item == com.euvcraft.lithmod.mc.registry.ModItems.FOUP_MK2.get())) {
            return;
        }

        // Must have FOUP_CONTENTS component
        var contents = stack.get(com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS.get());
        if (contents == null) {
            return; // initializeIfMissing already handled the absent case
        }

        // Must be a client-view lot
        if (!com.euvcraft.lithmod.mc.FoupContentsCodec.isClientView(contents)) {
            return;
        }

        // Must be empty (no wafers) and have no run spec
        if (!contents.isEmpty() || contents.run() != null) {
            return;
        }

        // All conditions met: replace with server-generated empty lot
        // Uses configured capacity, fresh UUID, preserves custom name/other components/count
        var fresh = com.euvcraft.lithmod.mc.FoupItems.emptyFoup(item);
        var freshContents = fresh.get(com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS.get());
        if (freshContents != null) {
            stack.set(com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS.get(), freshContents);
        }
    }
}
