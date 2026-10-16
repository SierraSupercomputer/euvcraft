package com.euvcraft.lithmod.mc.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity for the Process Cell Casing.
 * Simple member block that forwards interactions to the controller.
 */
public class ProcessCellCasingBlockEntity extends BlockEntity {

    private ProcessCellControllerBlockEntity controller = null;
    private boolean formed = false;

    public ProcessCellCasingBlockEntity(BlockPos pos, BlockState state) {
        super(com.euvcraft.lithmod.mc.registry.ModBlocks.PROCESS_CELL_CASING_BE.get(), pos, state);
    }

    public void setController(ProcessCellControllerBlockEntity controller) {
        this.controller = controller;
    }

    public ProcessCellControllerBlockEntity getController() {
        return controller;
    }

    public boolean isFormed() {
        return formed;
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
    }

    public void serverTick() {
        // Periodic check - if controller is gone or unformed, unform this block
        if (formed && (controller == null || !controller.isFormed())) {
            formed = false;
            setChanged();
        }
    }

    public void onInteract(Player player) {
        if (controller != null) {
            controller.onInteract(player);
        } else {
            player.sendSystemMessage(Component.literal("Process Cell casing at " + worldPosition + " has no controller"));
        }
    }

    public void onStructureBroken() {
        formed = false;
        controller = null;
        setChanged();
    }

    /**
     * Called when a neighbor block changes.
     * Notifies the controller to revalidate the structure.
     */
    public void onNeighborChange() {
        if (controller != null) {
            controller.onNeighborChange();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getInt("formed").orElse(0) != 0;
        // Controller reference is not saved - it's rebuilt on revalidation
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", formed ? 1 : 0);
    }
}