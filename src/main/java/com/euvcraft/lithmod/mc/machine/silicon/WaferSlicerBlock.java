package com.euvcraft.lithmod.mc.machine.silicon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Wafer Slicer block: one boule slot, one empty-FOUP slot, one output FOUP slot.
 */
public class WaferSlicerBlock extends SiliconMachineBlock {

    public WaferSlicerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WaferSlicerBlockEntity(pos, state);
    }
}