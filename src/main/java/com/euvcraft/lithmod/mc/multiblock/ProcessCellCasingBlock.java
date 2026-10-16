package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * A Process Cell casing: the filler that occupies the other nine or ten cells of the multiblock.
 *
 * <p>A casing owns nothing. It holds the controller it currently belongs to so that a power port
 * elsewhere in the cell can find the buffer, and so that the cell can drop its formed flag when the
 * controller goes away. See {@link ProcessCellControllerBlock} for why the constructor takes
 * {@link BlockBehaviour.Properties}.</p>
 */
public class ProcessCellCasingBlock extends BaseEntityBlock {

    /** True while this casing is part of a formed cell. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ProcessCellCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessCellCasingBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof ProcessCellCasingBlockEntity casing) {
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
        if (level.getBlockEntity(pos) instanceof ProcessCellCasingBlockEntity casing && casing.controller() != null) {
            casing.controller().revalidate();
        }
    }
}