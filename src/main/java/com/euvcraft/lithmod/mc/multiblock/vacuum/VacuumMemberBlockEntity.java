package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Shared member block entity for the central vacuum plant: casing, power port, operating panel,
 * conduit, passthrough, service outlet.
 */
public final class VacuumMemberBlockEntity extends UtilityPortBlockEntity implements VacuumAccess {

    private VacuumConsumer consumer; // set by the controller during network discovery

    public VacuumMemberBlockEntity(BlockPos pos, BlockState state) {
        super(VacuumMachines.MEMBER_BE.get(), pos, state);
    }

    @Override
    public boolean supportsOwner(Object owner) {
        return owner instanceof VacuumControllerBlockEntity;
    }

    @Override
    protected IFluidHandler createFluidHandler() {
        return null; // vacuum plant has no fluid handlers
    }

    @Override
    protected EnergyHandler createEnergyHandler() {
        return kind == BlockKind.POWER_PORT ? new SimpleEnergyHandler(0, 0, 0) : new SimpleEnergyHandler(0, 0, 0);
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