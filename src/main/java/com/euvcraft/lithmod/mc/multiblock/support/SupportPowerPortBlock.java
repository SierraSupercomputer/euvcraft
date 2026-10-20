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
 * A support machine power port: the only member whose block state can expose FE.
 *
 * <p>The port owns no energy of its own. Its {@code Capabilities.Energy.BLOCK} handler is the
 * owning controller's buffer, lent only while the machine is formed and the live controller is
 * still the registered owner; see {@link SupportMemberBlockEntity#energy()}. A casing shares the
 * same block entity type and therefore receives no capability at all — the capability provider in
 * {@code SupportMachines} filters on the actual block state.</p>
 *
 * <p>Never an item or fluid capability, and no capability on the controller or casing.</p>
 */
public class SupportPowerPortBlock extends BaseEntityBlock {

    /** True while this port is part of a formed support machine. */
    public static final BooleanProperty FORMED = ProcessCellVisualProperties.FORMED;
    /** Visual part index 0..11 for formed members, 0 when unformed. */
    public static final IntegerProperty PART = ProcessCellVisualProperties.PART;
    /** Horizontal facing of the machine this member belongs to. */
    public static final Property<Direction> FACING = ProcessCellVisualProperties.FACING;

    public SupportPowerPortBlock(BlockBehaviour.Properties properties) {
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
