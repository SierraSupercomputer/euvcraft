package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * G3 Deposition Etcher controller block.
 * 5x5x7 structure with deposition + rinse + vacuum modules in middle layers.
 */
public class G3DepositionEtcherControllerBlock extends G3ControllerBlock {

    public G3DepositionEtcherControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new G3DepositionEtcherControllerBlockEntity(pos, state);
    }
}