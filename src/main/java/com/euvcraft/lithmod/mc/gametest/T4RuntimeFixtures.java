package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.machine.*;
import com.euvcraft.lithmod.mc.machine.packaging.*;
import com.euvcraft.lithmod.mc.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.multiblock.support.*;
import com.euvcraft.lithmod.mc.registry.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

/** Independent fixtures keep every orientation within a five-by-five footprint. */
final class T4RuntimeFixtures {
 enum Kind { DEPOSITION, PACKAGING, LITHOGRAPHY, SCANNER, PURIFIER, EBEAM }
 record Cell(ServerLevel level,List<BlockPos> positions,BlockPos anchor,FabMachineBlockEntity machine) implements AutoCloseable {
  BlockPos port(){return positions.stream().filter(p->level.getBlockState(p).getBlock()==AdvancedMachines.ADVANCED_POWER_PORT.get()
      ||level.getBlockState(p).getBlock()==ModMultiblocks.PROCESS_CELL_POWER_PORT.get()
      ||level.getBlockState(p).getBlock()==com.euvcraft.lithmod.mc.multiblock.assembly.AssemblyMachines.POWER_PORT.get()||level.getBlockState(p).getBlock()==SupportMachines.SUPPORT_POWER_PORT.get()).findFirst().orElseThrow();}
  @Override public void close(){
   int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
   for(var pos:positions){
    minX=Math.min(minX,pos.getX());minY=Math.min(minY,pos.getY());minZ=Math.min(minZ,pos.getZ());
    maxX=Math.max(maxX,pos.getX());maxY=Math.max(maxY,pos.getY());maxZ=Math.max(maxZ,pos.getZ());
    if(level.getBlockEntity(pos) instanceof Container inventory)inventory.clearContent();
   }
   for(var pos:positions)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
   var area=new AABB(minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
   for(var entity:level.getEntitiesOfClass(ItemEntity.class,area))entity.discard();
  }
 }
 static void check(GameTestHelper h,boolean value,String message){h.assertTrue(value,message);}
 static void eq(GameTestHelper h,long expected,long actual,String message){h.assertTrue(expected==actual,message+" expected="+expected+" actual="+actual);}
 static Cell build(GameTestHelper h,Kind kind,Direction facing,int column){
  boolean big=kind==Kind.DEPOSITION||kind==Kind.SCANNER,advanced=big||kind==Kind.PACKAGING,panel=advanced||kind==Kind.LITHOGRAPHY;
  int width=big?3:2,height=big?3:2,depth=big?5:3;
  int rx=switch(facing){case NORTH->1;case SOUTH->-1;default->0;};
  int rz=switch(facing){case EAST->1;case WEST->-1;default->0;};
  int bx=switch(facing){case EAST->-1;case WEST->1;default->0;};
  int bz=switch(facing){case NORTH->1;case SOUTH->-1;default->0;};
  var anchor=h.absolutePos(new BlockPos(1+rx*column+(rx<0?width-1:0)+(bx<0?depth-1:0),1,
      1+rz*column+(rz<0?width-1:0)+(bz<0?depth-1:0)));
  var level=h.getLevel();var positions=new ArrayList<BlockPos>();
  Block casing=advanced?AdvancedMachines.ADVANCED_CASING.get():kind==Kind.LITHOGRAPHY?ModMultiblocks.PROCESS_CELL_CASING.get():SupportMachines.SUPPORT_CASING.get();
  Block power=advanced?AdvancedMachines.ADVANCED_POWER_PORT.get():kind==Kind.LITHOGRAPHY?ModMultiblocks.PROCESS_CELL_POWER_PORT.get():SupportMachines.SUPPORT_POWER_PORT.get();
  for(int u=0;u<width;u++)for(int v=0;v<height;v++)for(int d=0;d<depth;d++){
   var pos=anchor.offset(rx*(u-column)+bx*d,v,rz*(u-column)+bz*d);positions.add(pos);
   level.setBlock(pos,casing.defaultBlockState(),3);
  }
  var port=anchor.offset(bx,0,bz);level.setBlock(port,power.defaultBlockState(),3);
  if(panel)level.setBlock(anchor.above(),(kind==Kind.SCANNER?com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.OPERATING_PANEL.get():advanced?AdvancedMachines.ADVANCED_OPERATING_PANEL.get():ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get()).defaultBlockState(),3);
  Block controller=switch(kind){
   case SCANNER->com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.CONTROLLER.get();
   case DEPOSITION->AdvancedMachines.DEPOSITION_ETCHER_CONTROLLER.get();
   case PACKAGING->AdvancedMachines.ADVANCED_TEST_PACKAGE_CONTROLLER.get();
   case LITHOGRAPHY->ModMultiblocks.PROCESS_CELL_CONTROLLER.get();
   case PURIFIER->SupportMachines.BOULE_PURIFIER.get();
   case EBEAM->SupportMachines.E_BEAM_MASK_WRITER.get();
  };
  level.setBlock(anchor,controller.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing),3);
  if(kind==Kind.SCANNER){
   var middle=positions.stream().filter(p->p.getY()==anchor.getY()+1&&!p.equals(anchor.above())).toList();
   var blocks=List.of(com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.CALIBRATION_PANEL.get(),com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.EXPOSURE_MODULE.get(),com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.OPTICS_MODULE.get(),com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.STAGE_MODULE.get(),com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.FILTRATION_MODULE.get(),com.euvcraft.lithmod.mc.multiblock.scanner.ScannerMachines.METROLOGY_MODULE.get());
   for(int i=0;i<blocks.size();i++)level.setBlock(middle.get(i),blocks.get(i).defaultBlockState(),3);
  }
  var machine=(FabMachineBlockEntity)level.getBlockEntity(anchor);
  if(machine instanceof AdvancedOwner owner){owner.revalidate();check(h,owner.isFormed(),"Advanced fixture forms "+kind+" "+facing+" "+column);}
  else if(machine instanceof ProcessCellControllerBlockEntity owner){owner.revalidate();check(h,owner.isFormed(),"Lithography fixture forms");}
  else if(machine instanceof SupportMachineBlockEntity owner){owner.revalidate();check(h,owner.isFormed(),"Support fixture forms");}
  return new Cell(level,List.copyOf(positions),anchor,machine);
 }
 static Cell build(GameTestHelper h,Kind kind){return build(h,kind,Direction.NORTH,0);}
 static ItemStack fresh(int count,WaferSubstrate grade){
  var wafers=new ArrayList<Wafer>();for(int i=0;i<count;i++)wafers.add(Wafer.unprocessed());
  return FoupItems.with(ModItems.FOUP_MK1.get(),FoupContents.of(UUID.randomUUID(),Math.max(25,count),wafers,grade));
 }
 static ItemStack complete(int tier,String family,int cores){
  var t=TierDataLoader.getRegistry().getByIndex(tier);
  var wafers=List.of(new Wafer(t.layers(),1f,0),new Wafer(t.layers(),Float.MIN_VALUE,0),new Wafer(t.layers(),.5f,0));
  var spec=new RunSpec(family,tier,cores,.90,t.layers()*EUVCRAFTMod.settings().rollsPerLayer());
  return FoupItems.with(ModItems.FOUP_MK1.get(),new FoupContents(UUID.randomUUID(),25,wafers,spec,WaferSubstrate.PURE));
 }
 static CompoundTag save(ServerLevel level,BlockEntity machine){
  var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());machine.saveWithId(output);return output.buildResult();
 }
 static void load(ServerLevel level,BlockEntity machine,CompoundTag tag){
  machine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));
 }
 static void replenish(FabMachineBlockEntity machine){machine.setStoredEnergy(machine.buffer().getCapacityAsInt());}
 static long runPreparation(GameTestHelper h,DepositionEtcherControllerBlockEntity machine){
  long paid=0;
  for(int i=0;i<10_000&&machine.slot(2).isEmpty();i++){
   replenish(machine);int before=machine.buffer().getAmountAsInt();machine.serverTick();paid+=before-machine.buffer().getAmountAsInt();
  }
  check(h,!machine.slot(2).isEmpty(),"Preparation reaches real output");return paid;
 }
 record Packaged(int chips,int scrap,long energy){}
 static Packaged packageAll(GameTestHelper h,AdvancedTestPackageControllerBlockEntity machine,Item expected,int count){
  int chips=0,scrap=0;long energy=0;boolean done=false;
  for(int tick=0;tick<20_000;tick++){
   replenish(machine);int before=machine.buffer().getAmountAsInt();machine.serverTick();energy+=before-machine.buffer().getAmountAsInt();
   for(int slot=2;slot<8;slot++){
    var stack=machine.slot(slot);
    if(!stack.isEmpty()){
     check(h,stack.is(expected)||stack.is(ModItems.CHIP_SCRAP.get()),"Only requested-tier chips or scrap can leave queue");
     if(stack.is(expected))chips+=stack.getCount();else scrap+=stack.getCount();machine.clearSlot(slot);
    }
   }
   if(tick>0&&!machine.isQueueActive()&&machine.wafersTotal()==count&&machine.wafersDone()==count){done=true;break;}
  }
  check(h,done,"Packaging queue finishes");return new Packaged(chips,scrap,energy);
 }
 private T4RuntimeFixtures(){}
}

