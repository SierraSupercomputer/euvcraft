package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.BaseEntityBlock;

import org.jspecify.annotations.Nullable;

/**
 * The hydrogen purifier controller block.
 */
public final class HydrogenControllerBlock extends BaseEntityBlock {

    public HydrogenControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntityType<?> getBlockEntityType() {
        return HydrogenMachines.CONTROLLER_BE.get();
    }
}