package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block class for the four reflow member roles. The role is fixed at registration; the
 * block entity reports it through {@link ReflowMemberBlockEntity#structureKind()}.
 */
public final class ReflowMemberBlock extends AdvancedCasingBlock {

    private final BlockKind kind;

    public ReflowMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties);
        this.kind = kind;
    }

    public BlockKind kind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReflowMemberBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != BlockKind.OPERATING_PANEL && kind != BlockKind.CALIBRATION_PANEL) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReflowMemberBlockEntity member) {
            ReflowControllerBlockEntity oven = member.reflowController();
            if (oven == null) {
                player.sendSystemMessage(Component.translatable("message.lithmod.reflow.form_first"));
            } else {
                player.openMenu(member, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
