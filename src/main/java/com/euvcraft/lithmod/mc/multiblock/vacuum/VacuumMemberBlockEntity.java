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

    @Override
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
        if (expected == null || !valid(expected)) return null;
        if (kind != BlockKind.POWER_PORT) return null;
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return valid(expected) ? expected.machine().energy().getAmountAsLong() : 0;
            }

            @Override
            public long getCapacityAsLong() {
                return valid(expected) ? expected.machine().energy().getCapacityAsLong() : 0;
            }

            @Override
            public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                return valid(expected) ? expected.machine().energy().insert(amount, transaction) : 0;
            }

            @Override
            public int extract(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
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