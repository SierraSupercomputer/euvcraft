package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
public class AdvancedCasingBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED=AdvancedVisualProperties.FORMED,LARGE=AdvancedVisualProperties.LARGE;
    public static final IntegerProperty PART=AdvancedVisualProperties.PART;
    public static final Property<Direction> FACING=AdvancedVisualProperties.FACING;
    public AdvancedCasingBlock(BlockBehaviour.Properties properties){
        super(properties);registerDefaultState(stateDefinition.any().setValue(FORMED,false).setValue(LARGE,false).setValue(PART,0).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FORMED,LARGE,PART,FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AdvancedMemberBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:(l,p,s,b)->{if(b instanceof AdvancedMemberBlockEntity member)member.serverTick();};
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,@Nullable Orientation orientation,boolean moved){
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof AdvancedMemberBlockEntity member){
            var owner=member.controller();if(owner!=null)owner.revalidate();
        }
    }
}
