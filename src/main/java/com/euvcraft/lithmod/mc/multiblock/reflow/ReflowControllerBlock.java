package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The lower controller of the reflow oven. Opens the status-only controller view. */
public final class ReflowControllerBlock extends AdvancedControllerBlock {

    public ReflowControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReflowControllerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReflowControllerBlockEntity oven) {
            player.openMenu(oven, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
