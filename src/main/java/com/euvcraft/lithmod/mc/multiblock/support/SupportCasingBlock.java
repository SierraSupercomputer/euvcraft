package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * A support machine casing: the filler of the other blocks of the 2x2x3 support structure.
 *
 * <p>Deliberately a <em>different block</em> from {@code ProcessCellCasingBlock}: a physical
 * support casing can never be mistaken for a fab casing, so no support machine can accidentally
 * form out of an already-built Process Cell. Shared member block entity type with the power port
 * ({@code SupportMachines.SUPPORT_MEMBER_BE}); the members' separate visual flags use the exact
 * shared {@link ProcessCellVisualProperties} instances.</p>
 *
 * <p>The constructor takes the {@link BlockBehaviour.Properties} handed over by
 * {@code registerBlock}, which already carry the registry id.</p>
 */
public class SupportCasingBlock extends BaseEntityBlock {

    /** True while this casing is part of a formed support machine. */
    public static final BooleanProperty FORMED = ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..11 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = ProcessCellVisualProperties.PART;
    /** Horizontal facing of the machine this member belongs to. */
    public static final Property<Direction> FACING = ProcessCellVisualProperties.FACING;

    public SupportCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, Boolean.FALSE)
            .setValue(PART, Integer.valueOf(0))
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, PART, FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupportMemberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof SupportMemberBlockEntity member) {
                member.serverTick();
            }
        };
    }

    /** A neighbouring edit can break the machine; the owning controller re-validates at once. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof SupportMemberBlockEntity member && member.controller() != null) {
            member.controller().revalidate();
        }
    }
}
