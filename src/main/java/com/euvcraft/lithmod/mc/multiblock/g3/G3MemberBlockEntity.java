package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Shared behaviour of all G3 member blocks (casing, power port, operating panel, water inlet,
 * writing module, metrology module, deposition module, rinse module, vacuum module).
 *
 * <p>A member never decides whether it is part of a formed machine: the controller does,
 * and pushes the answer here. The controller link is deliberately not persisted; it is
 * rebuilt by the next controller revalidation.</p>
 *
 * <p>{@code isPowerPort()} filters the capability provider on the actual block state,
 * which is what keeps the FE capability on the port only — never on the casing,
 * the controller, or an unformed machine.</p>
 */
public class G3MemberBlockEntity extends BlockEntity implements com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumAccess {

    private @Nullable G3ControllerBlockEntity controller;
    private boolean formed;
    private int part;
    private @Nullable Direction facing;

    protected G3MemberBlockEntity(BlockPos pos, BlockState state) {
        super(G3Machines.MEMBER_BE.get(), pos, state);
    }

    /** Whether the block here is a {@link G3PowerPortBlock}; the only member that lends FE. */
    public final boolean isPowerPort() {
        return this.getBlockState().getBlock() instanceof G3PowerPortBlock;
    }

    /** Whether the block here is a {@link G3WaterInletBlock}. */
    public final boolean isWaterInlet() {
        return this.getBlockState().getBlock() instanceof G3WaterInletBlock;
    }

    /** Whether the block here is a {@link G3OperatingPanelBlock}. */
    public final boolean isOperatingPanel() {
        return this.getBlockState().getBlock() instanceof G3OperatingPanelBlock;
    }

    /** Returns the structure kind of this block for the matcher. */
    public BlockKind structureKind() {
        var block = getBlockState().getBlock();
        if (block == G3Machines.POWER_PORT.get()) return BlockKind.POWER_PORT;
        if (block == G3Machines.OPERATING_PANEL.get()) return BlockKind.OPERATING_PANEL;
        if (block == G3Machines.WATER_INLET.get()) return BlockKind.WATER_INLET;
        if (block == G3Machines.WRITING_MODULE.get()) return BlockKind.WRITING_MODULE;
        if (block == G3Machines.METROLOGY_MODULE.get()) return BlockKind.METROLOGY_MODULE;
        if (block == G3Machines.DEPOSITION_MODULE.get()) return BlockKind.DEPOSITION_MODULE;
        if (block == G3Machines.RINSE_MODULE.get()) return BlockKind.RINSE_MODULE;
        if (block == G3Machines.VACUUM_MODULE.get()) return BlockKind.VACUUM_MODULE;
        if (block == G3Machines.CASING.get()) return BlockKind.CASING;
        return BlockKind.OTHER;
    }

    /** The controller this member belongs to, or null when it belongs to none. */
    public @Nullable G3ControllerBlockEntity controller() {
        return this.controller;
    }

    /** Whether this member type can be owned by the given controller. */
    public boolean supportsOwner(G3ControllerBlockEntity controller) {
        var kind = controller.getKind();
        var block = getBlockState().getBlock();
        if (kind == G3MachineKind.RETICLE_WRITER) {
            return block == G3Machines.CASING.get()
                    || block == G3Machines.POWER_PORT.get()
                    || block == G3Machines.OPERATING_PANEL.get()
                    || block == G3Machines.WRITING_MODULE.get()
                    || block == G3Machines.METROLOGY_MODULE.get();
        } else {
            return block == G3Machines.CASING.get()
                    || block == G3Machines.POWER_PORT.get()
                    || block == G3Machines.OPERATING_PANEL.get()
                    || block == G3Machines.WATER_INLET.get()
                    || block == G3Machines.DEPOSITION_MODULE.get()
                    || block == G3Machines.RINSE_MODULE.get()
                    || block == G3Machines.VACUUM_MODULE.get();
        }
    }

    /** Whether this member is part of a formed machine. */
    public boolean isMemberOfFormedMachine() {
        return this.formed;
    }

    /** Visual part index 0..174 for formed members, 0 when unformed. */
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
     * registered owner. Null for other member types by construction (the capability provider
     * never asks them), which is what makes the capability answer null there.</p>
     */
    public @Nullable EnergyHandler energy() {
        if (!this.isPowerPort() || !this.isMemberOfFormedMachine()) {
            return null;
        }
        G3ControllerBlockEntity owner = this.controller;
        if (owner == null || owner.isRemoved()
                || (this.level != null && this.level.getBlockEntity(owner.getBlockPos()) != owner)) {
            return null;
        }
        return com.euvcraft.lithmod.mc.multiblock.T7EnergyPort.view(this,owner,()->{owner.revalidate();return controller==owner&&formed&&isPowerPort()&&owner.isFormed()&&owner.members().contains(worldPosition);});
    }

    /**
     * The fluid handler this water inlet exposes, or null when there is nothing to lend.
     * Only a formed prep machine's inlet exposes its insertion lens.
     * No storage lives in a member; the tank lives on the controller.
     */
    public @Nullable ResourceHandler<FluidResource> waterInput() {
        if (!this.isWaterInlet() || !this.formed || this.controller == null
                || this.controller.getKind() != G3MachineKind.DEPOSITION_ETCHER
                || !this.controller.isFormed()
                || this.isRemoved() || this.level == null || this.level.isClientSide()
                || this.level.getBlockEntity(this.worldPosition) != this
                || this.controller.isRemoved()
                || this.level.getBlockEntity(this.controller.getBlockPos()) != this.controller
                || !this.controller.members().contains(this.worldPosition)) {
            return null;
        }
        return new G3WaterInletHandler(this, this.controller);
    }

    /**
     * Records a new formed state, controller link, part index, and facing. Called by the controller, never by a player.
     *
     * @param controller the owning controller, or null to detach this member
     * @param formed whether the machine is formed
     * @param part visual part index 0..174 (ignored when formed is false)
     * @param facing horizontal facing of the machine (ignored when formed is false)
     */
    public void attach(@Nullable G3ControllerBlockEntity controller, boolean formed, int part,
                       @Nullable Direction facing) {
        if (this.controller == controller && this.formed == formed && this.part == part && this.facing == facing) {
            return;
        }
        this.controller = controller;
        this.formed = formed;
        this.part = formed ? part : 0;
        this.facing = formed ? facing : null;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) this.level.invalidateCapabilities(this.worldPosition);
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
        if (this.level == null || this.level.isClientSide()) return;
        boolean live = this.controller != null && !this.controller.isRemoved()
                && this.level.getBlockEntity(this.controller.getBlockPos()) == this.controller
                && this.controller.isFormed() && this.controller.members().contains(this.worldPosition);
        BlockState state = this.level.getBlockState(this.worldPosition);
        boolean persistedFormed = state.hasProperty(G3CasingBlock.FORMED)
                && state.getValue(G3CasingBlock.FORMED);
        boolean stalePart = state.hasProperty(G3CasingBlock.PART)
                && state.getValue(G3CasingBlock.PART) != 0;
        if (!live && (this.formed || this.controller != null || persistedFormed || stalePart)) {
            attach(null, false, 0, null);
            clearFormedState();
        }
    }

    /** Clear coarse visual state even if no ephemeral owner was restored from disk. */
    private void clearFormedState() {
        BlockState state = this.level.getBlockState(this.worldPosition);
        BlockState next = state;
        if (next.hasProperty(G3CasingBlock.FORMED)) next = next.setValue(G3CasingBlock.FORMED, false);
        if (next.hasProperty(G3CasingBlock.PART)) next = next.setValue(G3CasingBlock.PART, 0);
        if (next != state) this.level.setBlock(this.worldPosition, next, 3);
    }
    @Override public com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumConsumer vacuumConsumer(){return structureKind()==BlockKind.VACUUM_MODULE&&formed&&controller!=null&&controller.isFormed()?controller:null;}
}