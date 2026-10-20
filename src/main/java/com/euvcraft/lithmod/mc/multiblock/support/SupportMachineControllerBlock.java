package com.euvcraft.lithmod.mc.multiblock.support;

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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The controller shell both support machines share: Boule Purifier and E-beam Mask Writer.
 *
 * <p>One registered controller block class, two registered block instances; the kind at a position
 * comes from the block instance, never from the block entity type — both kinds share the one
 * {@code SupportMachines.SUPPORT_CONTROLLER_BE} block entity type. See
 * {@link SupportMachineKind}.</p>
 *
 * <p>Block state properties are the exact shared instances
 * {@code ProcessCellVisualProperties.FORMED / PART / FACING}, not copies, because 26.3
 * {@code StateHolder} lookup requires identity.</p>
 *
 * <p>The constructor takes the {@link BlockBehaviour.Properties} that
 * {@code DeferredRegister.Blocks.registerBlock} hands over; those already carry the registry id,
 * so nothing here builds its own ({@code Properties.of()} leaves the id unset and throws
 * {@code NullPointerException: Block id not set}).</p>
 */
public class SupportMachineControllerBlock extends BaseEntityBlock {

    /** Which way the machine looks. The structure extends along the opposite side. */
    public static final Property<Direction> FACING =
        com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FACING;
    /** True while all twelve blocks of the machine are in place. */
    public static final BooleanProperty FORMED = com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..11 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.PART;

    public SupportMachineControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, Boolean.FALSE)
            .setValue(PART, Integer.valueOf(0)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, PART);
    }

    /**
     * Placement faces the player's opposite side, so the structure grows away from the view.
     * {@code getHorizontalDirection().getOpposite()} is safe at any pitch: a steep look direction
     * keeps the horizontal component untouched.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Rotating a machine re-aims its box; the controller re-validates and reforms on the next pass. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupportMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof SupportMachineBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    /**
     * Opens the owner GUI on right-click — formed or not, so the player can always see why the
     * machine is unformed. Matches {@code SiliconMachineBlock#useItemOn} exactly: server opens the
     * menu and returns SUCCESS, client returns PASS.
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

    /** A neighbouring edit can complete or break the machine; re-validate at once. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SupportMachineBlockEntity controller) {
            controller.revalidate();
        }
    }
}
