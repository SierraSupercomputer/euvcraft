package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerData;
import java.util.List;
import java.util.function.Function;

/** Common interface for EUV multiblock formations. */
public interface Formation {
    boolean formed();
    String failureReason();
    List<BlockPos> members();
    ContainerData data();
    void validate();
    void unload();
    void detach();
    MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup);
}