package com.euvcraft.lithmod.mc.multiblock.euv;

import com.mojang.math.OctahedralGroup;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** EUV Beamline block - connects light source to scanner. */
public class EuvBeamlineBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    // Quarter-turn-transformed cuboids from tools/models/beamline_pipe.json (Euler X, then Y, then Z).
    private static final VoxelShape NORTH_SOUTH = Shapes.or(
        Block.box(4, 6, 1, 6, 10, 15),
        Block.box(10, 6, 1, 12, 10, 15),
        Block.box(6, 4, 1, 10, 6, 15),
        Block.box(6, 10, 1, 10, 12, 15),
        Block.box(5, 12, 0, 11, 13, 1),
        Block.box(4, 11, 0, 12, 12, 1),
        Block.box(3, 5, 0, 13, 11, 1),
        Block.box(5, 3, 0, 11, 4, 1),
        Block.box(4, 4, 0, 12, 5, 1),
        Block.box(5, 12, 15, 11, 13, 16),
        Block.box(4, 11, 15, 12, 12, 16),
        Block.box(3, 5, 15, 13, 11, 16),
        Block.box(5, 3, 15, 11, 4, 16),
        Block.box(4, 4, 15, 12, 5, 16));
    private static final VoxelShape EAST_WEST = Shapes.rotate(NORTH_SOUTH, OctahedralGroup.BLOCK_ROT_Y_90);
    private static final VoxelShape VERTICAL = Shapes.rotate(NORTH_SOUTH, OctahedralGroup.BLOCK_ROT_X_90);

    public EuvBeamlineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return alignToNeighbors(defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis()), context.getLevel(), context.getClickedPos());
    }

    /** Geometry only: admission retains the existing straight, loaded beamline check. */
    public BlockState alignToNeighbors(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis found = null;
        for (var direction : Direction.values()) {
            var neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            var other = level.getBlockState(neighbor);
            if (!other.is(this) && !other.is(EuvMachines.SCANNER_EXPOSURE_MODULE.get()) && !other.is(EuvMachines.LIGHT_SOURCE_CASING.get())) continue;
            if (found != null && found != direction.getAxis()) return state;
            found = direction.getAxis();
        }
        return found == null ? state : state.setValue(AXIS, found);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return alignToNeighbors(state, level, pos);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(AXIS, rotation.rotate(Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE)).getAxis());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EuvBeamlineBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, EuvMachines.BEAM_LINE_BE.get(),
                (l,p,s,be) -> be.serverTick());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) { case X -> EAST_WEST; case Y -> VERTICAL; case Z -> NORTH_SOUTH; };
    }
}
