package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The block entity behind {@link ProcessCellPowerPortBlock}.
 *
 * <p>The port is the multiblock's window on the controller's energy. It stores no energy of its own:
 * {@link #energy()} returns the controller's handler, and returns null while the cell is not formed,
 * which is what makes {@code Level.getCapability} answer null for an unformed port.</p>
 */
public class ProcessCellPowerPortBlockEntity extends ProcessCellMemberBlockEntity {

    public ProcessCellPowerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_POWER_PORT_BE.get(), pos, state);
    }

    /**
     * The handler this port lends, or null when there is nothing to lend.
     *
     * <p>Null unless the port belongs to a formed cell, so a port sitting in a half-built or
     * dismantled cell has no energy capability on any side. Registration itself happens in
     * {@link ModMultiblocks#registerCapabilities} on the mod bus, because a
     * {@code getCapability} override on a block entity is not consulted for capabilities in 26.3.</p>
     *
     * @return the controller's handler while formed, otherwise null
     */
    public @Nullable EnergyHandler energy() {
        if (!isMemberOfFormedCell()) {
            return null;
        }
        ProcessCellControllerBlockEntity controller = controller();
        return controller == null ? null : controller.energy();
    }
}