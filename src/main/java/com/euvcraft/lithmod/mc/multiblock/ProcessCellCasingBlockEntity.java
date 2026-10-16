package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity behind {@link ProcessCellCasingBlock}.
 *
 * <p>All behaviour is in {@link ProcessCellMemberBlockEntity}; this type exists so the casing has a
 * block entity type of its own, which the block and capability registries both key on.</p>
 *
 * <p>The constructor is {@code (pos, state)} and looks its own type up from the registry holder,
 * which is what lets {@code BlockEntityType} be built with a method reference; see
 * {@code ModMachines} for the same pattern.</p>
 */
public class ProcessCellCasingBlockEntity extends ProcessCellMemberBlockEntity {

    public ProcessCellCasingBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_CASING_BE.get(), pos, state);
    }
}