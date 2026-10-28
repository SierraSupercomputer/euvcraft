package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellMenu;
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
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * G3 Operating Panel menu.
 * Opens the controller's UI from the panel block (directly above controller).
 * Forwards to the controller's menu implementation.
 */
public class G3OperatingPanelMenu extends FabMachineMenu {

    public G3OperatingPanelMenu(int id, Inventory inventory, G3ControllerBlockEntity controller, BlockPos panelPos) {
        this(id, inventory, controller, ContainerLevelAccess.create(controller.getLevel(), controller.getBlockPos()));
    }

    public G3OperatingPanelMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, getController(inventory, buf), buf.readBlockPos());
    }

    private static G3ControllerBlockEntity getController(Inventory inventory, FriendlyByteBuf buf) {
        BlockPos controllerPos = buf.readBlockPos();
        BlockPos panelPos = buf.readBlockPos();
        Level level = inventory.player.level();
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be instanceof G3ControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("G3 controller not found at " + controllerPos);
    }

    @Override
    protected void addMachineSlots(G3ControllerBlockEntity controller) {
        // Operating panel has no direct slots; it forwards to the controller's slots
        // The actual slots are defined in the controller's specific menu
        // This menu is just a pass-through for the panel block entity
    }

    @Override
    protected int getMachineSlotCount() {
        return 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.controller != null && this.controller.isFormed()
                && this.controller.getLevel().getBlockEntity(this.controller.getBlockPos()) == this.controller;
    }
}