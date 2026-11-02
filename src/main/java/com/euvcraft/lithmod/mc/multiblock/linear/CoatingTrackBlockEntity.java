package com.euvcraft.lithmod.mc.multiblock.linear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class CoatingTrackBlockEntity extends LinearStageBlockEntity {
    public CoatingTrackBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.COATING_TRACK_BE.get(),pos,state);}
    @Override public boolean coating(){return true;}
    @Override protected String machineId(){return "coating_track";}
}
