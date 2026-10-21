package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The advanced casing block entity: holds the controller link and visual state.
 */
public class AdvancedCasingBlockEntity extends AdvancedMemberBlockEntity {

    public AdvancedCasingBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedMachines.ADVANCED_MEMBER_BE.get(), pos, state);
    }

    @Override
    public BlockKind blockKind() {
        return BlockKind.CASING;
    }
}