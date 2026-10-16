package com.euvcraft.lithmod.mc.block.entity;

import com.euvcraft.lithmod.core.EnergyBuffer;
import com.euvcraft.lithmod.core.multiblock.EnergyBufferProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Block entity for the Process Cell Power Port.
 * Exposes the controller's energy buffer when formed.
 */
public class ProcessCellPowerPortBlockEntity extends BlockEntity {

    private ProcessCellControllerBlockEntity controller = null;
    private boolean formed = false;

    public ProcessCellPowerPortBlockEntity(BlockPos pos, BlockState state) {
        super(com.euvcraft.lithmod.mc.registry.ModBlocks.PROCESS_CELL_POWER_PORT_BE.get(), pos, state);
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
            player.sendSystemMessage(Component.literal("Process Cell power port at " + worldPosition + " has no controller"));
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

    /**
     * Gets the energy buffer from the controller when formed.
     * Returns null if not formed or no controller.
     */
    @Nullable
    public EnergyBuffer getEnergyBuffer() {
        if (formed && controller != null) {
            EnergyBufferProvider provider = controller;
            return provider.getEnergyBuffer();
        }
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getInt("formed").orElse(0) != 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", formed ? 1 : 0);
    }
}