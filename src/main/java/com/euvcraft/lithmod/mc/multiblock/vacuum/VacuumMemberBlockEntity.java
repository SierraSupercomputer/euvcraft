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
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
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
        super(pos, state);
        this.kind = computeKind();
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

    public boolean supportsOwner(Object owner) {
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
    public @Nullable EnergyHandler energy() {
        var expected = controller();
        if (expected == null) return null;
        if (kind != BlockKind.POWER_PORT) return null;
        var level = getLevel();
        if (level == null || level.isClientSide() || isRemoved()) return null;
        if (!energyPort() || !supportsOwner(expected)) return null;
        var expectedMachine = expected.machine();
        if (expectedMachine.isRemoved() || level.getBlockEntity(expectedMachine.getBlockPos()) != expectedMachine) return null;
        expected.revalidate();
        // Use controller() instead of private owner field from parent
        if (controller() != expected || !formed || !expected.isFormed() || !expected.members().contains(getBlockPos())) return null;
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                var level = getLevel();
                if (level == null || level.isClientSide() || isRemoved()) return 0;
                var expected = controller();
                if (expected == null) return 0;
                var expectedMachine = expected.machine();
                if (expectedMachine.isRemoved() || level.getBlockEntity(expectedMachine.getBlockPos()) != expectedMachine) return 0;
                return expectedMachine.energy().getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                var level = getLevel();
                if (level == null || level.isClientSide() || isRemoved()) return 0;
                var expected = controller();
                if (expected == null) return 0;
                var expectedMachine = expected.machine();
                if (expectedMachine.isRemoved() || level.getBlockEntity(expectedMachine.getBlockPos()) != expectedMachine) return 0;
                return expectedMachine.energy().getCapacityAsLong();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                var level = getLevel();
                if (level == null || level.isClientSide() || isRemoved()) return 0;
                var expected = controller();
                if (expected == null) return 0;
                var expectedMachine = expected.machine();
                if (expectedMachine.isRemoved() || level.getBlockEntity(expectedMachine.getBlockPos()) != expectedMachine) return 0;
                return expectedMachine.energy().insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return 0; // vacuum plant does not allow extraction through member ports
            }
        };
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