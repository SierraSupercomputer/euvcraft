package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.*;
public final class AdvancedVisualProperties {
 public static final BooleanProperty FORMED=BooleanProperty.create("formed"), LARGE=BooleanProperty.create("large");
 public static final IntegerProperty PART=IntegerProperty.create("part",0,44);
 public static final Property<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
 private AdvancedVisualProperties(){}
}
