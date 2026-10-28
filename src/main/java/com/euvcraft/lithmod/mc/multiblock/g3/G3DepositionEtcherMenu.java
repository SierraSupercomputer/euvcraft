package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * G3 Deposition Etcher menu.
 * Binds within GUI and no inventory overlap.
 * Buttons server-authoritative and formed/distance checked.
 * Shows prep phase progress, vacuum pressure, water amount, contamination state.
 */
public class G3DepositionEtcherMenu extends FabMachineMenu {

    public G3DepositionEtcherMenu(int id, Inventory inventory, G3DepositionEtcherControllerBlockEntity controller) {
        this(id, inventory, controller, ContainerLevelAccess.create(controller.getLevel(), controller.getBlockPos()));
    }

    public G3DepositionEtcherMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, getController(inventory, buf));
    }

    private static G3DepositionEtcherControllerBlockEntity getController(Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Level level = inventory.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof G3DepositionEtcherControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("G3 Deposition Etcher controller not found at " + pos);
    }

    @Override
    protected void addMachineSlots(G3DepositionEtcherControllerBlockEntity controller) {
        // Slot 0: FOUP input (fresh electronic-grade lot)
        this.addSlot(new Slot(controller, G3DepositionEtcherControllerBlockEntity.SLOT_FOUP_INPUT, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Only FOUPs allowed
                return stack.is(net.minecraft.world.item.Items.NAME_TAG); // placeholder - actual FOUP item
            }
        });
        // Slot 1: FOUP output (prepared G3 lot)
        this.addSlot(new Slot(controller, G3DepositionEtcherControllerBlockEntity.SLOT_FOUP_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // output only
            }
            @Override
            public boolean mayPickup(Player player) {
                return super.mayPickup(player);
            }
        });
    }

    @Override
    protected int getMachineSlotCount() {
        return 2;
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && this.controller.formed && this.controller.getLevel().getBlockEntity(this.controller.getBlockPos()) == this.controller;
    }
}