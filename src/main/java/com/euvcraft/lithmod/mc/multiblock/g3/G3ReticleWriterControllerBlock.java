package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * G3 Reticle Writer controller block.
 * 3x3x5 structure with writing + metrology modules in middle layer.
 */
public class G3ReticleWriterControllerBlock extends G3ControllerBlock {

    public G3ReticleWriterControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new G3ReticleWriterControllerBlockEntity(pos, state);
    }
}