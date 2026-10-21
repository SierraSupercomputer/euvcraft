package com.euvcraft.lithmod.mc.multiblock.advanced;

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