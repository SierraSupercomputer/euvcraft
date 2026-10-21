package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
public final class AdvancedOperatingPanelBlock extends AdvancedCasingBlock {
    public AdvancedOperatingPanelBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var below=context.getLevel().getBlockState(context.getClickedPos().below());
        return below.getBlock() instanceof AdvancedControllerBlock?defaultBlockState().setValue(FACING,below.getValue(FACING)):super.getStateForPlacement(context);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof AdvancedMemberBlockEntity panel&&panel.belowOwner()!=null){
            player.openMenu(panel,buffer->{buffer.writeBlockPos(pos);buffer.writeBoolean(panel.belowOwner().large());});return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
