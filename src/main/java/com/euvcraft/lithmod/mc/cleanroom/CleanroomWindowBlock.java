package com.euvcraft.lithmod.mc.cleanroom;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Six local neighbor flags drive border-free window faces; no room scans or block entities. */
public final class CleanroomWindowBlock extends TransparentBlock {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
        Direction.DOWN, BlockStateProperties.DOWN, Direction.UP, BlockStateProperties.UP,
        Direction.NORTH, BlockStateProperties.NORTH, Direction.SOUTH, BlockStateProperties.SOUTH,
        Direction.WEST, BlockStateProperties.WEST, Direction.EAST, BlockStateProperties.EAST);

    public CleanroomWindowBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (var property : CONNECTIONS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (var property : CONNECTIONS.values()) builder.add(property);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (var entry : CONNECTIONS.entrySet()) {
            BlockPos neighbor = context.getClickedPos().relative(entry.getKey());
            state = state.setValue(entry.getValue(), context.getLevel().hasChunkAt(neighbor)
                && context.getLevel().getBlockState(neighbor).is(this));
        }
        return state;
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return state.setValue(CONNECTIONS.get(direction), neighbor.is(this));
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (var entry : CONNECTIONS.entrySet())
            result = result.setValue(CONNECTIONS.get(rotation.rotate(entry.getKey())), state.getValue(entry.getValue()));
        return result;
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (var entry : CONNECTIONS.entrySet())
            result = result.setValue(CONNECTIONS.get(mirror.mirror(entry.getKey())), state.getValue(entry.getValue()));
        return result;
    }
}
