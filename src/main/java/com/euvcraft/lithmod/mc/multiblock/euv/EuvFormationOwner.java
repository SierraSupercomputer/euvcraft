package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;

/** Shared ownership contract for EUV multiblocks; inventories and energy remain on the actual controller. */
public interface EuvFormationOwner {
    FabMachineBlockEntity machine();
    Formation formation();
    default MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return EuvStructureMatcher.match(anchor, facing, lookup);
    }
    default boolean isFormed() { return formation().formed(); }
    default void revalidate() { formation().validate(); }
    default List<BlockPos> members() { return formation().members(); }
    void onFormed();
    void onUnformed();
}