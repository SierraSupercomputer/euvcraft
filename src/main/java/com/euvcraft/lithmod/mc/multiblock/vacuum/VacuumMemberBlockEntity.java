package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Shared member block entity for the central vacuum plant: casing, power port, operating panel,
 * conduit, passthrough, service outlet.
 * Extends AdvancedMemberBlockEntity directly (UtilityPortBlockEntity is final).
 */
public final class VacuumMemberBlockEntity extends AdvancedMemberBlockEntity implements VacuumAccess {

    private final BlockKind kind;
    private VacuumConsumer consumer; // set by the controller during network discovery

    public VacuumMemberBlockEntity(BlockPos pos, BlockState state) {
        super(VacuumMachines.MEMBER_BE.get(), pos, state);
        this.kind = computeKind();
    }


    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        if (structureKind() != BlockKind.OPERATING_PANEL) return null;
        var below = belowOwner();
        return below instanceof VacuumControllerBlockEntity owner ? new VacuumMenu(id, inventory, owner) : null;
    }

    private BlockKind computeKind() {
        var block = getBlockState().getBlock();
        if (block instanceof VacuumMemberBlock vmb) {
            return vmb.kind();
        }
        return BlockKind.OTHER;
    }

    public BlockKind kind() {
        return kind;
    }

    @Override public boolean supportsOwner(AdvancedOwner owner) {
        return owner instanceof VacuumControllerBlockEntity;
    }

    @Override
    protected boolean energyPort() {
        return kind == BlockKind.POWER_PORT;
    }

    @Override
    public com.euvcraft.lithmod.core.multiblock.BlockKind structureKind() {
        return kind;
    }

    @Override
    public VacuumConsumer vacuumConsumer() {
        return consumer;
    }

    /** Called by the controller during network discovery to bind this port to a consumer. */
    public void bindConsumer(VacuumConsumer consumer) {
        this.consumer = consumer;
    }

    /** Called by the controller when the network changes or the port is removed. */
    public void clearConsumer() {
        this.consumer = null;
    }
}