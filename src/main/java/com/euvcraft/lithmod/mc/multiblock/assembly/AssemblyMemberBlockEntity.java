package com.euvcraft.lithmod.mc.multiblock.assembly;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity;import com.euvcraft.lithmod.core.multiblock.BlockKind;import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.state.BlockState;
public final class AssemblyMemberBlockEntity extends AdvancedMemberBlockEntity{public AssemblyMemberBlockEntity(BlockPos p,BlockState s){super(AssemblyMachines.MEMBER_BE.get(),p,s);}@Override public BlockKind structureKind(){return energyPort()?BlockKind.POWER_PORT:getBlockState().getBlock()==AssemblyMachines.CASING.get()?BlockKind.CASING:BlockKind.OTHER;}@Override protected boolean energyPort(){return getBlockState().getBlock()==AssemblyMachines.POWER_PORT.get();}}

