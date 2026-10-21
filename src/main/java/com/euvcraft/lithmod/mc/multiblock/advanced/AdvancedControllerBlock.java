package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Advanced Controller Block (Equipment Cabinet): the lower block of the two-panel machine.
 *
 * <p>This is the shared base for both deposition_etcher and advanced_test_package controllers.
 * The actual machine ID and behaviour come from the block entity.</p>
 */
public class AdvancedControllerBlock extends BaseEntityBlock {

    /** Which way the cell looks. The structure extends along the opposite side. */
    public static final Property<Direction> FACING = ProcessCellVisualProperties.FACING;
    /** True while all cells of the multiblock are in place. */
    public static final BooleanProperty FORMED = ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..44 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = ProcessCellVisualProperties.PART;
    /** True for deposition_etcher (3x3x5), false for advanced_test_package (2x2x3). */
    public static final BooleanProperty LARGE = ProcessCellVisualProperties.LARGE;

    public AdvancedControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, Boolean.FALSE)
            .setValue(PART, Integer.valueOf(0))
            .setValue(LARGE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, PART, LARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Rotating a cell changes which cells make it up, so the formed flag cannot simply
     * ride along; the controller re-validates on its next tick and drops or regains formed.
     */
    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // The actual BE type is determined by the block registry entry
        // This method should not be called directly for the two specific controllers
        // since they have their own BE types registered.
        throw new UnsupportedOperationException("Use specific controller blocks");
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof AdvancedControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    /**
     * Opens the lower cabinet menu on right-click. Shows equipment/status only if no permanent items.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof FabMachineBlockEntity machine) {
            player.openMenu(machine, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * A neighbour change can complete or break the cell, so the controller re-validates at once.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AdvancedControllerBlockEntity controller) {
            controller.revalidate();
        }
    }
}