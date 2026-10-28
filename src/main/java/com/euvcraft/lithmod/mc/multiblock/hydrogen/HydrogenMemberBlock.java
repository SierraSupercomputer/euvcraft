package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydrogenMemberBlockEntity(pos, state);
    }

    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (kind != BlockKind.OPERATING_PANEL) return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HydrogenMemberBlockEntity panel && panel.belowOwner() != null) player.openMenu(panel, buffer -> buffer.writeBlockPos(pos));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}