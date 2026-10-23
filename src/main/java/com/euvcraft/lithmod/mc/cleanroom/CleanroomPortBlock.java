package com.euvcraft.lithmod.mc.cleanroom;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Cleanroom port block: a full-cube, fixed-facing portal that forwards transfer queries
 * through to the adjacent block on the opposite side.
 *
 * <p>Three registry entries share this class: {@link CleanroomBlocks#POWER_PORT},
 * {@link CleanroomBlocks#FLUID_PORT}, and {@link CleanroomBlocks#ITEM_PORT}. The role is
 * detected at runtime by comparing the block state's block to the registry fields.</p>
 *
 * <p>Placement facing follows {@link BlockPlaceContext#getHorizontalDirection()}, which points
 * from the placement player through the wall into the room (i.e. the destination side).</p>
 */
public class CleanroomPortBlock extends BaseEntityBlock {

    /** Horizontal facing of the port; points from outside into the cleanroom. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    public CleanroomPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Horizontal direction from the player, pointing through the wall into the room
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleanroomPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof CleanroomPortBlockEntity port) {
                port.serverTick();
            }
        };
    }

    /** Determines which port role this block instance represents. */
    public enum PortRole {
        POWER,
        FLUID,
        ITEM,
        UNKNOWN
    }

    /** Detects the port role by comparing against the CleanroomBlocks registry. */
    public static PortRole detectRole(Block block) {
        if (block == CleanroomBlocks.POWER_PORT.get()) return PortRole.POWER;
        if (block == CleanroomBlocks.FLUID_PORT.get()) return PortRole.FLUID;
        if (block == CleanroomBlocks.ITEM_PORT.get()) return PortRole.ITEM;
        return PortRole.UNKNOWN;
    }

    /** Detects the port role from a block state. */
    public static PortRole detectRole(BlockState state) {
        return detectRole(state.getBlock());
    }
}