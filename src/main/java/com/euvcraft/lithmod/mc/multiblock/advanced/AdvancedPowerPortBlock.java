package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

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
 * Advanced power port: the only advanced multiblock member that exposes anything to the world.
 *
 * <p>The port owns no energy of its own. Its {@code Capabilities.Energy.BLOCK} handler is
 * the controller's, and it is handed over only while the cell is formed; see
 * {@link AdvancedPowerPortBlockEntity#energy()} and {@link AdvancedMachines}.</p>
 *
 * <p>See {@link ProcessCellControllerBlock} for why the constructor takes
 * {@link BlockBehaviour.Properties}.</p>
 */
public class AdvancedPowerPortBlock extends BaseEntityBlock {

    /** True while this port is part of a formed advanced multiblock. */
    public static final BooleanProperty FORMED = AdvancedVisualProperties.FORMED;
    /** Visual part index 0..44 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = AdvancedVisualProperties.PART;
    /** Horizontal facing of the cell this member belongs to. */
    public static final Property<Direction> FACING = AdvancedVisualProperties.FACING;
    /** True for deposition_etcher (3x3x5), false for advanced_test_package (2x2x3). */
    public static final BooleanProperty LARGE = AdvancedVisualProperties.LARGE;

    public AdvancedPowerPortBlock(BlockBehaviour.Properties properties) {
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
        return new AdvancedPowerPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof AdvancedPowerPortBlockEntity port) {
                port.serverTick();
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
        if (level.getBlockEntity(pos) instanceof AdvancedPowerPortBlockEntity port && port.controller() != null) {
            port.controller().revalidate();
        }
    }
}