package com.euvcraft.lithmod.mc.multiblock.advanced;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The dedicated controller selects the existing high-bin admission path. */
public final class HighBinTestPackageBlockEntity extends AdvancedTestPackageControllerBlockEntity {
    public HighBinTestPackageBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedMachines.HIGH_BIN_CONTROLLER_BE.get(), pos, state);
    }

    @Override protected String machineId() { return "high_bin_test_package"; }
    @Override protected boolean highBinningEnabled() { return true; }
}
