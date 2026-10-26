package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block class for the four zone member kinds, carrying its BlockKind like a scanner
 * member does. Only the operating panel reacts to use: it opens the upper panel menu.
 */
public final class ZoneMemberBlock extends AdvancedCasingBlock {
    private final BlockKind kind;

    public ZoneMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties);
        this.kind = kind;
    }

    public BlockKind kind() {
        return kind;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZoneMemberBlockEntity(pos, state);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (kind != BlockKind.OPERATING_PANEL) {
            return super.getStateForPlacement(context);
        }
        // A panel above the zone controller keeps the controller's orientation.
        var below = context.getLevel().getBlockState(context.getClickedPos().below());
        return below.getBlock() instanceof AdvancedControllerBlock
            ? defaultBlockState().setValue(FACING, below.getValue(FACING))
            : super.getStateForPlacement(context);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != BlockKind.OPERATING_PANEL) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ZoneMemberBlockEntity panel) {
            var owner = panel.belowOwner();
            if (owner == null) {
                player.sendSystemMessage(Component.translatable("message.lithmod.zone.form_first"));
            } else {
                player.openMenu(panel, buffer -> buffer.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
