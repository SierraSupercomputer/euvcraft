package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The advanced power port block entity: exposes the controller's energy buffer when formed and owned.
 */
public class AdvancedPowerPortBlockEntity extends AdvancedMemberBlockEntity {

    public AdvancedPowerPortBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedMachines.ADVANCED_MEMBER_BE.get(), pos, state);
    }

    @Override
    public BlockKind blockKind() {
        return BlockKind.POWER_PORT;
    }

    /**
     * The energy handler for this power port.
     * Returns the controller's buffer handler when formed and owned, null otherwise.
     */
    public @Nullable EnergyHandler energy() {
        if (controller() != null && formed && controller().isFormed()) {
            return controller().energy();
        }
        return null;
    }
}