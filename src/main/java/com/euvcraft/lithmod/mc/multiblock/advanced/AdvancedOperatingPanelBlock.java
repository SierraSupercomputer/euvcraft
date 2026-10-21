package com.euvcraft.lithmod.mc.multiblock.advanced;

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
 * The Advanced Operating Panel: the upper panel of the two-panel machine, placed directly
 * above the controller (Equipment Cabinet).
 *
 * <p>The panel owns nothing — no inventory, no energy, no run state. It is a casing member
 * that the controller recognises as the required {@link BlockKind#OPERATING_PANEL}. Opening
 * the panel resolves the core directly below it and opens the upper (operating) menu, which
 * exposes the consumable slots. The panel works while the cell is unformed, showing status/config;
 * removal or replacement of the panel notifies the core and pauses any active run without losing contents.</p>
 *
 * <p>Placement is horizontal-safe: it copies the facing of the controller directly below, or
 * uses the horizontal direction opposite the player if no controller is present.</p>
 */
public class AdvancedOperatingPanelBlock extends BaseEntityBlock {

    /** True while this panel is part of a formed advanced multiblock. */
    public static final BooleanProperty FORMED = ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..44 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = ProcessCellVisualProperties.PART;
    /** Horizontal facing of the cell this member belongs to. */
    public static final Property<Direction> FACING = ProcessCellVisualProperties.FACING;
    /** True for deposition_etcher (3x3x5), false for advanced_test_package (2x2x3). */
    public static final BooleanProperty LARGE = ProcessCellVisualProperties.LARGE;

    public AdvancedOperatingPanelBlock(BlockBehaviour.Properties properties) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos below = pos.below();

        // Try to copy facing from controller directly below
        Direction facing = null;
        if (level.getBlockEntity(below) instanceof AdvancedControllerBlockEntity) {
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock() instanceof AdvancedControllerBlock) {
                facing = belowState.getValue(AdvancedControllerBlock.FACING);
            }
        }
        // Fallback: horizontal opposite the player
        if (facing == null) {
            facing = context.getHorizontalDirection().getOpposite();
        }

        return defaultBlockState()
            .setValue(FORMED, Boolean.FALSE)
            .setValue(FACING, facing)
            .setValue(PART, Integer.valueOf(0))
            .setValue(LARGE, Boolean.FALSE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedOperatingPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof AdvancedOperatingPanelBlockEntity panel) {
                panel.serverTick();
            }
        };
    }

    /**
     * Opens the operating panel menu on right-click. The panel resolves the core directly
     * below and opens the upper menu if a valid controller is found at the same level.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        BlockPos corePos = pos.below();
        if (level.getBlockEntity(corePos) instanceof AdvancedControllerBlockEntity controller) {
            // Verify this is the correct controller for this panel (same level, directly below)
            if (controller.getBlockPos().equals(corePos)) {
                // Open menu using the panel block entity as the menu provider
                if (level.getBlockEntity(pos) instanceof AdvancedOperatingPanelBlockEntity panel) {
                    player.openMenu(panel, pos);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * A neighbour change can complete or break the cell, so the owning controller re-validates at once.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof AdvancedOperatingPanelBlockEntity panel && panel.controller() != null) {
            panel.controller().revalidate();
        }
    }
}