package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityPortBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

/**
 * Shared member block for the hydrogen purifier: casing, power port, gas inlet, gas outlet, operating panel.
 * Extends the same pattern as UtilityPortBlock but with hydrogen-specific kinds.
 */
public final class HydrogenMemberBlock extends UtilityPortBlock {

    public HydrogenMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties, kind);
    }

    @Override
    public @Nullable BlockEntityType<?> getBlockEntityType() {
        return HydrogenMachines.MEMBER_BE.get();
    }
}