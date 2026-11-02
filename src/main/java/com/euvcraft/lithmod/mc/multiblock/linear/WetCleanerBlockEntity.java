package com.euvcraft.lithmod.mc.multiblock.linear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class WetCleanerBlockEntity extends LinearStageBlockEntity {
    public WetCleanerBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.WET_CLEANER_BE.get(),pos,state);}
    @Override public boolean coating(){return false;}
    @Override protected String machineId(){return "wet_cleaner";}
}
