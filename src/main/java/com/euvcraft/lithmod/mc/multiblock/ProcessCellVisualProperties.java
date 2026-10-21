package com.euvcraft.lithmod.mc.multiblock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Registered instances shared by every fixed-cell role; StateHolder lookup requires identity. */
public final class ProcessCellVisualProperties {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 44);
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LARGE = BooleanProperty.create("large");

    /** Part property for advanced multiblocks (delegates to shared PART). */
    public static class PartProperty {
        public IntegerProperty get() { return PART; }
    }

    /** Formed property for advanced multiblocks (delegates to shared FORMED). */
    public static class FormedProperty {
        public BooleanProperty get() { return FORMED; }
    }

    /** Facing property for advanced multiblocks (delegates to shared FACING). */
    public static class FacingProperty {
        public Property<Direction> get() { return FACING; }
    }

    /** Large property for advanced multiblocks (delegates to shared LARGE). */
    public static class LargeProperty {
        public BooleanProperty get() { return LARGE; }
    }

    private ProcessCellVisualProperties() { }
}
