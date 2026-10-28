package com.euvcraft.lithmod.mc.multiblock.vacuum;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.BaseEntityBlock;

import org.jspecify.annotations.Nullable;

/**
 * The central vacuum plant controller block.
 */
public final class VacuumControllerBlock extends BaseEntityBlock {

    public VacuumControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntityType<?> getBlockEntityType() {
        return VacuumMachines.CONTROLLER_BE.get();
    }
}