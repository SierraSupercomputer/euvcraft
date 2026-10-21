package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.core.multiblock.BlockKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour of the advanced multiblock members that are not the controller.
 *
 * <p>A member does not decide whether it is part of a formed cell: the controller does, and pushes
 * the answer here. What a member keeps is the controller it currently belongs to, which is what lets
 * a power port reach the buffer and what lets a member notice that its controller is gone.</p>
 *
 * <p>The controller link is deliberately not persisted. It is rebuilt by the next
 * controller revalidate, and persisting a reference to another block entity across a reload would
 * need stale-position bookkeeping for no gain.</p>
 */
public abstract class AdvancedMemberBlockEntity extends BlockEntity {

    private @Nullable AdvancedControllerBlockEntity controller;
    private boolean formed;
    private int part;
    private @Nullable Direction facing;
    private boolean large;

    protected AdvancedMemberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The controller this member belongs to, or null when it belongs to none. */
    public @Nullable AdvancedControllerBlockEntity controller() {
        return this.controller;
    }

    /** Whether this member is part of a formed advanced multiblock. */
    public boolean isMemberOfFormedCell() {
        return this.formed;
    }

    /** Visual part index 0..44 for formed members, 0 when unformed. */
    public int part() {
        return this.part;
    }

    /** Horizontal facing of the cell this member belongs to, or null when unformed. */
    public @Nullable Direction facing() {
        return this.facing;
    }

    /** Whether this member belongs to a large (deposition_etcher) or small (advanced_test_package) multiblock. */
    public boolean isLarge() {
        return this.large;
    }

    /**
     * Records a new formed state, controller link, part index, and facing. Called by the controller, never by a player.
     *
     * @param controller the owning controller, or null to detach this member
     * @param formed whether the cell is formed
     * @param part visual part index 0..44 (ignored when formed is false)
     * @param facing horizontal facing of the cell (ignored when formed is false)
     * @param large whether the multiblock is large (deposition_etcher)
     */
    public void attach(@Nullable AdvancedControllerBlockEntity controller, boolean formed, int part, @Nullable Direction facing, boolean large) {
        if (this.controller == controller && this.formed == formed && this.part == part && this.facing == facing && this.large == large) {
            return;
        }
        this.controller = controller;
        this.formed = formed;
        this.part = formed ? part : 0;
        this.facing = formed ? facing : null;
        this.large = formed ? large : false;
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
        if (level == null || level.isClientSide()) return;
        boolean live = controller != null && !controller.isRemoved()
            && level.getBlockEntity(controller.getBlockPos()) == controller
            && controller.isFormed() && controller.members().contains(worldPosition);
        BlockState state = level.getBlockState(worldPosition);
        // All member classes share equal named properties, including after an actual BE reload.
        boolean persistedFormed = state.hasProperty(AdvancedCasingBlock.FORMED)
            && state.getValue(AdvancedCasingBlock.FORMED);
        boolean stalePart = state.hasProperty(AdvancedCasingBlock.PART)
            && state.getValue(AdvancedCasingBlock.PART) != 0;
        if (!live && (formed || controller != null || persistedFormed || stalePart)) {
            attach(null, false, 0, null, false);
            clearFormedState();
        }
    }

    /** Clear coarse visual state even if no ephemeral owner was restored from disk. */
    private void clearFormedState() {
        BlockState state = level.getBlockState(worldPosition);
        BlockState next = state;
        if (next.hasProperty(AdvancedCasingBlock.FORMED)) next = next.setValue(AdvancedCasingBlock.FORMED, false);
        if (next.hasProperty(AdvancedCasingBlock.PART)) next = next.setValue(AdvancedCasingBlock.PART, 0);
        if (next.hasProperty(AdvancedCasingBlock.FACING)) next = next.setValue(AdvancedCasingBlock.FACING, Direction.NORTH);
        if (next.hasProperty(AdvancedCasingBlock.LARGE)) next = next.setValue(AdvancedCasingBlock.LARGE, false);
        if (next != state) level.setBlock(worldPosition, next, 3);
    }

    /**
     * Returns the BlockKind this member represents for the matcher.
     * Subclasses override to return their specific kind.
     */
    public BlockKind blockKind() {
        return BlockKind.CASING;
    }
}