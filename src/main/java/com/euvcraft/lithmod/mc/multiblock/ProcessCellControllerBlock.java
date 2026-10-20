package com.euvcraft.lithmod.mc.multiblock;

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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Process Cell controller: the one block of the multiblock that knows where the other eleven
 * are.
 *
 * <p>The constructor takes the {@link BlockBehaviour.Properties} that
 * {@code DeferredRegister.Blocks.registerBlock} hands over. Those already carry the registry id, so
 * this must pass them straight to {@code super} and must never build its own with
 * {@code Properties.of()}; doing that leaves the id unset and the constructor throws
 * {@code NullPointerException: Block id not set}.</p>
 *
 * <p>26.3 moved {@code BlockPlaceContext} to {@code net.minecraft.world.item.context} and made
 * {@code rotate} protected, which is why both overrides below land where they used to.</p>
 */
public class ProcessCellControllerBlock extends BaseEntityBlock {

    /** Which way the cell looks. The structure extends along the opposite side. */
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True while all twelve cells of the cell are in place. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ProcessCellControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Rotating a cell changes which twelve positions make it up, so the formed flag cannot simply
     * ride along; the controller re-validates on its next tick and drops or regains formed.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessCellControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof ProcessCellControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    /**
     * Opens the controller menu on right-click. Matches {@link SiliconMachineBlock#useItemOn}
     * signature exactly: server opens menu and returns SUCCESS, client returns PASS.
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
     * A neighbour change can complete or break the cell, so the controller re-validates at once
     * instead of waiting for its periodic pass. The 26.3 signature carries the neighbour's position.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ProcessCellControllerBlockEntity controller) {
            controller.revalidate();
        }
    }
}