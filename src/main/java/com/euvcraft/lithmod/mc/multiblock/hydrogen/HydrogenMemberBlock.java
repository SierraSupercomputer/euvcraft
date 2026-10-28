package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

/**
 * Shared member block for the hydrogen purifier: casing, power port, gas inlet, gas outlet, operating panel.
 * Extends AdvancedCasingBlock directly (UtilityPortBlock is final).
 */
public final class HydrogenMemberBlock extends AdvancedCasingBlock {

    private final BlockKind kind;

    public HydrogenMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties);
        this.kind = kind;
    }

    public BlockKind kind() {
        return kind;
    }

    @Override
    public @Nullable BlockEntityType<?> getBlockEntityType() {
        return HydrogenMachines.MEMBER_BE.get();
    }
}