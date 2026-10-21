package com.euvcraft.lithmod.mc.multiblock.advanced;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Advanced casing: the filler that occupies most cells of the advanced multiblocks.
 *
 * <p>A casing owns nothing. It holds the controller it currently belongs to so that a power port
 * elsewhere in the cell can find the buffer, and so that the cell can drop its formed flag when the
 * controller goes away. See {@link ProcessCellControllerBlock} for why the constructor takes
 * {@link BlockBehaviour.Properties}.</p>
 */
public class AdvancedCasingBlock extends BaseEntityBlock {

    /** True while this casing is part of a formed advanced multiblock. */
    public static final BooleanProperty FORMED = ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..44 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = ProcessCellVisualProperties.PART;
    /** Horizontal facing of the cell this member belongs to. */
    public static final Property<Direction> FACING = ProcessCellVisualProperties.FACING;
    /** True for deposition_etcher (3x3x5), false for advanced_test_package (2x2x3). */
    public static final BooleanProperty LARGE = ProcessCellVisualProperties.LARGE;

    public AdvancedCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, Boolean.FALSE)
            .setValue(PART, Integer.valueOf(0))
            .setValue(FACING, Direction.NORTH)
            .setValue(LARGE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, PART, FACING, LARGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedCasingBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof AdvancedCasingBlockEntity casing) {
                casing.serverTick();
            }
        };
    }

    /** A neighbouring edit can break the cell, so the owning controller re-validates at once. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof AdvancedCasingBlockEntity casing && casing.controller() != null) {
            casing.controller().revalidate();
        }
    }
}