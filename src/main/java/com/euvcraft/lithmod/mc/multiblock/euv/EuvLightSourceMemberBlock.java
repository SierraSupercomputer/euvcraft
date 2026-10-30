package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** EUV Light Source member block - casing, inlets, modules, ports. */
public class EuvLightSourceMemberBlock extends BaseEntityBlock {

    // Formation and orphan ticks use the controller properties; share their exact instances.
    public static final BooleanProperty FORMED = EuvLightSourceControllerBlock.FORMED;
    public static final IntegerProperty PART = EuvLightSourceControllerBlock.PART;
    public static final net.minecraft.world.level.block.state.properties.Property<Direction> FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.block();

    private final BlockKind kind;

    public EuvLightSourceMemberBlock(BlockBehaviour.Properties properties, BlockKind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FORMED, false)
                .setValue(PART, 0)
                .setValue(FACING, Direction.NORTH));
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, PART, FACING);
    }

    public BlockKind kind() {
        return kind;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EuvLightSourceMemberBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, EuvMachines.LIGHT_SOURCE_MEMBER_BE.get(),
                (l,p,s,be) -> be.serverTick());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(kind!=BlockKind.OPERATING_PANEL&&kind!=BlockKind.CALIBRATION_PANEL&&kind!=BlockKind.DIAGNOSTIC_PANEL)return net.minecraft.world.InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider)player.openMenu(provider,buffer->buffer.writeBlockPos(pos));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}