package com.euvcraft.lithmod.mc.multiblock.assembly;
import com.euvcraft.lithmod.core.multiblock.*;import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import net.minecraft.core.BlockPos;import net.minecraft.network.chat.Component;import net.minecraft.world.entity.player.Inventory;import net.minecraft.world.inventory.AbstractContainerMenu;import net.minecraft.world.level.block.state.BlockState;
public final class AssemblyControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner{
 private final AdvancedFormation formation=new AdvancedFormation(this);
 public AssemblyControllerBlockEntity(BlockPos p,BlockState s){super(AssemblyMachines.CONTROLLER_BE.get(),p,s,2,1);}
 @Override protected String machineId(){return "pick_and_place";}@Override public Component getDisplayName(){return Component.translatable("block.lithmod.pick_and_place");}
 @Override public FabMachineBlockEntity machine(){return this;}@Override public AdvancedFormation formation(){return formation;}@Override public boolean large(){return false;}
 @Override public MatchResult matchStructure(MultiblockPos p,MultiblockDirection f,java.util.function.Function<MultiblockPos,BlockKind> lookup){return AssemblyStructureMatcher.match(p,f,lookup);}
 @Override public void serverTick(){if(level==null||level.isClientSide())return;revalidate();refreshDefinition();if(isFormed())super.serverTick();}
 @Override public void setRemoved(){formation.detach();super.setRemoved();}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inv,FabMachineBlockEntity machine){return new AssemblyMenu(id,inv,this);}
}
