package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** EUV Beamline block entity - simple transport, no inventory. */
public class EuvBeamlineBlockEntity extends BlockEntity {
    private boolean visualChecked;

    public EuvBeamlineBlockEntity(BlockPos pos, BlockState state) {
        this(EuvMachines.BEAM_LINE_BE.get(), pos, state);
    }

    protected EuvBeamlineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void serverTick() {
        // Refresh legacy saved states once; subsequent neighbor changes update the block normally.
        if (visualChecked || level == null || level.isClientSide()) return;
        visualChecked = true;
        var state = getBlockState();
        if (state.getBlock() instanceof EuvBeamlineBlock block) {
            var aligned = block.alignToNeighbors(state, level, worldPosition);
            if (aligned != state) level.setBlock(worldPosition, aligned, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    }
}