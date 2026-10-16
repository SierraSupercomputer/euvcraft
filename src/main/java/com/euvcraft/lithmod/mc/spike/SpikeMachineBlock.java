package com.euvcraft.lithmod.mc.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The spike machine block.
 *
 * <p>Its only job is to take the {@link BlockBehaviour.Properties} the registration helper
 * hands it and to pass them to {@code super}. Building properties with
 * {@code Properties.of()} inside the constructor instead leaves the registry id unset, which
 * is what produces {@code NullPointerException: Block id not set}.</p>
 */
public final class SpikeMachineBlock extends BaseEntityBlock {

    public SpikeMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpikeMachineBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpikeMachineBlockEntity machine) {
            player.openMenu(machine, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
