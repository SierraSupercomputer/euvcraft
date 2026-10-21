package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Registered instances shared by every fixed-cell role; StateHolder lookup requires identity. */
public final class ProcessCellVisualProperties {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 11);
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private ProcessCellVisualProperties() { }
}
