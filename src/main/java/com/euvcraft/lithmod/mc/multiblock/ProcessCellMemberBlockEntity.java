package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour of the two cell members that are not the controller.
 *
 * <p>A member does not decide whether it is part of a formed cell: the controller does, and pushes
 * the answer here. What a member keeps is the controller it currently belongs to, which is what lets
 * a power port reach the buffer and what lets a member notice that its controller is gone.</p>
 *
 * <p>The controller link is deliberately not persisted. It is rebuilt by the next
 * {@link ProcessCellControllerBlockEntity#revalidate()}, and persisting a reference to another block
 * entity across a reload would need stale-position bookkeeping for no gain.</p>
 */
public abstract class ProcessCellMemberBlockEntity extends BlockEntity {

    private @Nullable ProcessCellControllerBlockEntity controller;
    private boolean formed;

    protected ProcessCellMemberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The controller this member belongs to, or null when it belongs to none. */
    public @Nullable ProcessCellControllerBlockEntity controller() {
        return this.controller;
    }

    /** Whether this member is part of a formed cell. */
    public boolean isMemberOfFormedCell() {
        return this.formed;
    }

    /**
     * Records a new formed state and controller link. Called by the controller, never by a player.
     *
     * @param controller the owning controller, or null to detach this member
     * @param formed whether the cell is formed
     */
    public void attach(@Nullable ProcessCellControllerBlockEntity controller, boolean formed) {
        if (this.controller == controller && this.formed == formed) {
            return;
        }
        this.controller = controller;
        this.formed = formed;
        setChanged();
    }

    /**
     * Drops the formed state when the owning controller is gone or has unformed.
     *
     * <p>Called once per server tick. The controller normally clears this itself the moment it
     * re-validates; this is the backstop for the case where the controller block itself was removed
     * and there is nobody left to push an update.</p>
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (this.formed && (this.controller == null || !this.controller.isFormed())) {
            this.formed = false;
            this.controller = null;
            clearFormedState();
            setChanged();
        }
    }

    /**
     * Takes this member's block state back to unformed.
     *
     * <p>Needed for the one case the controller cannot cover: when the controller block itself is
     * removed there is no controller left to push an update, so each surviving member has to take
     * its own flag down.</p>
     */
    private void clearFormedState() {
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(ProcessCellCasingBlock.FORMED)
                && state.getValue(ProcessCellCasingBlock.FORMED)) {
            level.setBlock(worldPosition, state.setValue(ProcessCellCasingBlock.FORMED, Boolean.FALSE), 3);
        } else if (state.hasProperty(ProcessCellPowerPortBlock.FORMED)
                && state.getValue(ProcessCellPowerPortBlock.FORMED)) {
            level.setBlock(worldPosition, state.setValue(ProcessCellPowerPortBlock.FORMED, Boolean.FALSE), 3);
        }
    }
}