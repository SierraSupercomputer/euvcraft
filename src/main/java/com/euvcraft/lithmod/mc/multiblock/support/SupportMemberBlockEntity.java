package com.euvcraft.lithmod.mc.multiblock.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour of the support members that are not the controller — casing, power port and water inlet
 * share the ONE member block entity type ({@code SupportMachines.SUPPORT_MEMBER_BE}), which is
 * registered against all three support member blocks.
 *
 * <p>A member never decides whether it is part of a formed machine: the controller does, and pushes
 * the answer here. The controller link is deliberately not persisted; it is rebuilt by the next
 * {@link SupportMachineBlockEntity#revalidate()}.</p>
 *
 * <p>{@code isPowerPort()} filters the capability provider on the actual block state, which is what
 * keeps the FE capability on the port only — never on the casing, the controller, or an unformed
 * machine.</p>
 */
public class SupportMemberBlockEntity extends BlockEntity {

    private @Nullable SupportMachineBlockEntity controller;
    private boolean formed;
    private int part;
    private @Nullable Direction facing;

    protected SupportMemberBlockEntity(BlockPos pos, BlockState state) {
        super(SupportMachines.SUPPORT_MEMBER_BE.get(), pos, state);
    }

    /** Whether the block here is a {@link SupportPowerPortBlock}; the only member that lends FE. */
    public final boolean isPowerPort() {
        return this.getBlockState().getBlock() instanceof SupportPowerPortBlock;
    }

    /** The controller this member belongs to, or null when it belongs to none. */
    public @Nullable SupportMachineBlockEntity controller() {
        return this.controller;
    }

    /** Whether this member is part of a formed machine. */
    public boolean isMemberOfFormedMachine() {
        return this.formed;
    }

    /** Visual part index 0..11 for formed members, 0 when unformed. */
    public int part() {
        return this.part;
    }

    /** Horizontal facing of the machine this member belongs to, or null when unformed. */
    public @Nullable Direction facing() {
        return this.facing;
    }

    /**
     * The FE handler this port lends, or null when there is nothing to lend.
     *
     * <p>Null unless this block is a power port of a formed machine whose live controller is the
     * registered owner. Null for a casing by construction (the capability provider never asks a
     * casing), which is what makes the capability answer null there.</p>
     */
    public net.neoforged.neoforge.transfer.energy.@Nullable EnergyHandler energy() {
        if (!this.isPowerPort() || !this.isMemberOfFormedMachine()) {
            return null;
        }
        SupportMachineBlockEntity owner = this.controller;
        if (owner == null || owner.isRemoved()
                || (this.level != null && this.level.getBlockEntity(owner.getBlockPos()) != owner)) {
            return null;
        }
        return owner.energy();
    }

    /** Only a formed purifier inlet exposes its insertion lens. No storage lives in a member. */
    public net.neoforged.neoforge.transfer.@Nullable ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> waterInput() {
        if (!(getBlockState().getBlock() instanceof SupportWaterInletBlock) || !formed || controller == null
                || controller.kind() != SupportMachineKind.PURIFIER || !controller.isFormed()
                || isRemoved() || level == null || level.isClientSide()
                || level.getBlockEntity(worldPosition) != this || controller.isRemoved()
                || level.getBlockEntity(controller.getBlockPos()) != controller
                || !controller.members().contains(worldPosition)) return null;
        return new SupportWaterInletHandler(this, controller);
    }

    /**
     * Records a new formed state, controller link, part index, and facing. Called by the controller, never by a player.
     *
     * @param controller the owning controller, or null to detach this member
     * @param formed whether the machine is formed
     * @param part visual part index 0..11 (ignored when formed is false)
     * @param facing horizontal facing of the machine (ignored when formed is false)
     */
    public void attach(@Nullable SupportMachineBlockEntity controller, boolean formed, int part,
                       @Nullable Direction facing) {
        if (this.controller == controller && this.formed == formed && this.part == part && this.facing == facing) {
            return;
        }
        this.controller = controller;
        this.formed = formed;
        this.part = formed ? part : 0;
        this.facing = formed ? facing : null;
        setChanged();
        if (level != null && !level.isClientSide()) level.invalidateCapabilities(worldPosition);
    }

    /**
     * Drops the formed state when the owning controller is gone or has unformed.
     *
     * <p>Called once per server tick. The controller normally clears this itself the moment it
     * re-validates; this is the backstop for the case where the controller block itself was removed
     * and there is nobody left to push an update. The departed controller must never clear a
     * <em>foreign</em> live owner's membership.</p>
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        boolean live = controller != null && !controller.isRemoved()
            && level.getBlockEntity(controller.getBlockPos()) == controller
            && controller.isFormed() && controller.members().contains(worldPosition);
        BlockState state = level.getBlockState(worldPosition);
        // All member classes share equal named properties, including after an actual BE reload.
        boolean persistedFormed = state.hasProperty(SupportCasingBlock.FORMED)
            && state.getValue(SupportCasingBlock.FORMED);
        boolean stalePart = state.hasProperty(SupportCasingBlock.PART)
            && state.getValue(SupportCasingBlock.PART) != 0;
        if (!live && (formed || controller != null || persistedFormed || stalePart)) {
            attach(null, false, 0, null);
            clearFormedState();
        }
    }

    /** Clear coarse visual state even if no ephemeral owner was restored from disk. */
    private void clearFormedState() {
        BlockState state = level.getBlockState(worldPosition);
        BlockState next = state;
        if (next.hasProperty(SupportCasingBlock.FORMED)) next = next.setValue(SupportCasingBlock.FORMED, false);
        if (next.hasProperty(SupportCasingBlock.PART)) next = next.setValue(SupportCasingBlock.PART, 0);
        if (next != state) level.setBlock(worldPosition, next, 3);
    }
}
