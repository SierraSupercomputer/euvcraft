package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;

/**
 * Shared ownership contract for G3 controllers.
 * Inventories and energy remain on the actual controller block entity.
 */
public interface G3ControllerOwner {

    FabMachineBlockEntity machine();
    G3Formation formation();
    boolean large();
    G3MachineKind getKind();

    default MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        if (large()) {
            return G3StructureMatcher.matchDepositionEtcher(anchor, facing, lookup);
        } else {
            return G3StructureMatcher.matchWriter(anchor, facing, lookup);
        }
    }

    default boolean isFormed() {
        return formation().formed();
    }

    default void revalidate() {
        formation().validate();
    }

    default List<BlockPos> members() {
        return formation().members();
    }

    default ContainerData data() {
        return machine().getData();
    }
}