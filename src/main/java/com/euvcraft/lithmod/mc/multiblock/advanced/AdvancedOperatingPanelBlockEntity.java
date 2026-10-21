package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The advanced operating panel block entity: holds the controller link and visual state.
 * The panel owns no inventory or energy — it's a visual and menu access point only.
 */
public class AdvancedOperatingPanelBlockEntity extends AdvancedMemberBlockEntity {

    public AdvancedOperatingPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedMachines.ADVANCED_MEMBER_BE.get(), pos, state);
    }

    @Override
    public BlockKind blockKind() {
        return BlockKind.OPERATING_PANEL;
    }
}