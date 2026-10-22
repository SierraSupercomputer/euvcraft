package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
/** Shared ownership contract; inventories and energy remain on the actual controller. */
public interface AdvancedOwner {
    FabMachineBlockEntity machine();
    AdvancedFormation formation();
    boolean large();
    default com.euvcraft.lithmod.core.multiblock.MatchResult matchStructure(com.euvcraft.lithmod.core.multiblock.MultiblockPos anchor, com.euvcraft.lithmod.core.multiblock.MultiblockDirection facing, java.util.function.Function<com.euvcraft.lithmod.core.multiblock.MultiblockPos,com.euvcraft.lithmod.core.multiblock.BlockKind> lookup) {
        return com.euvcraft.lithmod.core.multiblock.PanelStructureMatcher.match(anchor,facing,large()?AdvancedFormation.DEPOSITION_ETCHER_SHAPE:AdvancedFormation.ADVANCED_TEST_PACKAGE_SHAPE,lookup);
    }
    default boolean isFormed() { return formation().formed(); }
    default void revalidate() { formation().validate(); }
    default List<BlockPos> members() { return formation().members(); }
}
