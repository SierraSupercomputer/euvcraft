package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityPortBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

/**
 * Shared member block for the central vacuum plant: casing, power port, operating panel,
 * conduit, passthrough, service outlet.
 */
public final class VacuumMemberBlock extends UtilityPortBlock {

    public VacuumMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties, kind);
    }

    @Override
    public @Nullable BlockEntityType<?> getBlockEntityType() {
        return VacuumMachines.MEMBER_BE.get();
    }
}