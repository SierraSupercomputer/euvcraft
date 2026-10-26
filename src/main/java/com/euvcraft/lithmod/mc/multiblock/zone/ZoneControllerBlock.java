package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The zone refiner's lower controller block; it creates the machine, nothing else differs. */
public final class ZoneControllerBlock extends AdvancedControllerBlock {
    public ZoneControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZoneControllerBlockEntity(pos, state);
    }
}
