package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.machine.packaging.*;
import com.euvcraft.lithmod.mc.machine.silicon.*;
import com.euvcraft.lithmod.mc.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.support.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** All intermediate carriers and masks come from actual native production machines. */
final class T4FullChainGameTests {
 static void run(GameTestHelper h,String family,int design){
  int oldWafers=FabConfig.WAFERS_PER_BOULE.get();
  var oldTicks=new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
  try{
   FabConfig.WAFERS_PER_BOULE.set(3);FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T4=2"));EUVCRAFTMod.config().onConfigReload();
   ItemStack boule;
   try(var fixture=build(h,Kind.PURIFIER)){
    var machine=(SupportMachineBlockEntity)fixture.machine();
    machine.setSlot(0,new ItemStack(ModItems.SILICON_BOULE.get()));
    machine.setSlot(1,new ItemStack(Items.WATER_BUCKET));
    for(int tick=0;tick<10_000&&machine.slot(3).isEmpty();tick++){replenish(machine);machine.serverTick();}
    check(h,machine.slot(3).is(ModItems.PURE_SILICON_BOULE.get()),"Actual water purification produces pure boule");
    check(h,machine.slot(0).isEmpty()&&machine.slot(1).isEmpty()&&machine.slot(2).is(Items.BUCKET),"Boule and bucket conservation");
    boule=machine.slot(3).copy();
   }
   ItemStack foup;var pos=h.absolutePos(new BlockPos(1,1,1));
   h.getLevel().setBlock(pos,SiliconMachines.WAFER_SLICER.get().defaultBlockState(),3);
   var slicer=(WaferSlicerBlockEntity)h.getLevel().getBlockEntity(pos);
   try{
    slicer.setSlot(0,boule);slicer.setSlot(1,FoupItems.with(ModItems.FOUP_MK1.get(),FoupContents.empty(UUID.randomUUID(),25)));
    for(int tick=0;tick<10_000&&slicer.slot(2).isEmpty();tick++){replenish(slicer);slicer.serverTick();}
    check(h,!slicer.slot(2).isEmpty(),"Actual slicer produces carrier");foup=slicer.slot(2).copy();
   }finally{slicer.clearContent();h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
   var unprepared=foup.copy();var initial=FoupItems.trustedContentsOf(foup);eq(h,3,initial.size(),"Real sliced wafer count");
   check(h,initial.grade()==WaferSubstrate.PURE&&initial.preparationGeneration()==1,"Purity does not invent preparation");
   try(var fixture=build(h,Kind.DEPOSITION)){
    var machine=(DepositionEtcherControllerBlockEntity)fixture.machine();machine.setSlot(0,foup);
    machine.setSlot(1,new ItemStack(ModItems.SILICON.get(),4));runPreparation(h,machine);foup=machine.slot(2).copy();
   }
   var prepared=FoupItems.trustedContentsOf(foup);eq(h,2,prepared.preparationGeneration(),"Native preparation installed");
   check(h,prepared.lotId().equals(initial.lotId())&&prepared.wafers().equals(initial.wafers()),"Preparation preserves actual sliced identity and rolls");
   ItemStack mask;
   try(var fixture=build(h,Kind.EBEAM)){
    var machine=(SupportMachineBlockEntity)fixture.machine();
    machine.setSlot(0,new ItemStack(ModItems.BLANK_MASK.get()));machine.setSlot(1,new ItemStack(Items.REDSTONE,48));
    check(h,machine.selectDesign(design)&&machine.startEbeamJob(),"Actual T4 design starts in E-beam");
    for(int tick=0;tick<10_000&&machine.slot(2).isEmpty();tick++){replenish(machine);machine.serverTick();}
    check(h,!machine.slot(2).isEmpty(),"Actual E-beam produces mask");mask=machine.slot(2).copy();
    check(h,machine.slot(0).isEmpty()&&machine.slot(1).isEmpty(),"Actual T4 recipe consumes blank plus 48 redstone");
   }
   var maskData=mask.get(ModDataComponents.MASK_DATA.get());
   check(h,maskData!=null&&maskData.tierIndex()==4&&maskData.dieType().equals(family)&&maskData.shopGeneration()==2,"Actual mask target and provenance");
   try(var fixture=build(h,Kind.LITHOGRAPHY)){
    var machine=(ProcessCellControllerBlockEntity)fixture.machine();
    Item[] components={ModItems.ARF_EXCIMER_LASER.get(),ModItems.ARF_DRY_LENS.get(),ModItems.NANOPOSITIONING_STAGE.get(),ModItems.ULPA_FILTER.get(),ModItems.PRECISION_OVERLAY_SCOPE.get()};
    for(int n=0;n<5;n++)machine.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE+n,new ItemStack(components[n]));
    machine.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP,foup);machine.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK,mask);machine.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST,new ItemStack(ModItems.RESIST_T4.get(),64));
    machine.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP,unprepared);replenish(machine);machine.serverTick();
    check(h,machine.processState()==ProcessState.IDLE&&machine.lastRefusal()!=null,"Unprepared PURE wafer lot cannot bypass deposition");
    eq(h,maskData.usesRemaining(),machine.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"Refused source consumes no mask use");
    machine.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP,foup);replenish(machine);machine.serverTick();
    // Recreate every member, as an actual chunk reload does: ephemeral owner links are not saved.
    var oldMembers=fixture.positions().stream().map(p0->fixture.level().getBlockEntity(p0)).toList();
    var savedMembers=new HashMap<BlockPos,net.minecraft.nbt.CompoundTag>();
    for(var member:oldMembers)savedMembers.put(member.getBlockPos(),save(fixture.level(),member));
    for(var member:oldMembers)fixture.level().removeBlockEntity(member.getBlockPos());
    for(var member:oldMembers){var restored=member.getType().create(member.getBlockPos(),fixture.level().getBlockState(member.getBlockPos()));fixture.level().setBlockEntity(restored);load(fixture.level(),restored,savedMembers.get(member.getBlockPos()));}
    machine=(ProcessCellControllerBlockEntity)fixture.level().getBlockEntity(fixture.anchor());machine.revalidate();
    for(int tick=0;tick<1000&&machine.processState()!=ProcessState.FINISHED;tick++){replenish(machine);machine.serverTick();}
    check(h,machine.processState()==ProcessState.FINISHED,"Actual T4 lithography completes "+family+" state="+machine.processState()+" refusal="+machine.lastRefusal()+" formed="+machine.isFormed()+" reason="+machine.failureReason());
    foup=machine.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).copy();
    var remaining=machine.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get());
    check(h,remaining!=null&&remaining.usesRemaining()==maskData.usesRemaining()-1,"One mask use per full lot");
   }
   var completed=FoupItems.trustedContentsOf(foup);
   eq(h,32,completed.lotLayer(),"All 32 native layers complete");eq(h,2,completed.preparationGeneration(),"Preparation remains through exposure");
   check(h,completed.run()!=null&&completed.run().tierIndex()==4&&completed.run().dieType().equals(family),"Frozen actual T4 design");
   if(family.equals(DieTypes.COMPUTE))eq(h,1,completed.run().cores(),"T4 compute remains one core");
   // A standalone G1 packager must not admit this higher-generation lot.
   h.getLevel().setBlock(pos,PackagingMachines.TEST_PACKAGE.get().defaultBlockState(),3);
   var legacy=(TestPackageBlockEntity)h.getLevel().getBlockEntity(pos);
   try{
    legacy.setSlot(0,foup.copy());legacy.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));replenish(legacy);legacy.serverTick();
    check(h,!legacy.isQueueActive()&&FoupItems.trustedContentsOf(legacy.slot(0)).size()==3,"G1 refuses real T4 lot without consumption");
   }finally{legacy.clearContent();h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
   int passing=FabYieldService.countPassing(h.getLevel(),completed,TierDataLoader.getRegistry().getByIndex(4),family,family.equals(DieTypes.COMPUTE)?1:null);
   try(var fixture=build(h,Kind.PACKAGING)){
    var machine=(AdvancedTestPackageControllerBlockEntity)fixture.machine();
    machine.setSlot(0,foup);machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));
    var result=packageAll(h,machine,ChipCatalog.chipFor(family,4).orElseThrow(),3);
    eq(h,passing*EUVCRAFTMod.settings().chipsPerWafer(),result.chips(),"Exact normal T4 chip judgment");
    eq(h,(3-passing)*EUVCRAFTMod.settings().chipsPerWafer(),result.scrap(),"Exact normal T4 scrap judgment");
    check(h,FoupItems.trustedContentsOf(machine.slot(0)).isEmpty(),"Packaging consumed original lot");
   }
  }finally{FabConfig.WAFERS_PER_BOULE.set(oldWafers);FabConfig.LAYER_TICKS_OVERRIDES.set(oldTicks);EUVCRAFTMod.config().onConfigReload();}
  h.succeed();
 }
 private T4FullChainGameTests(){}
}


