package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.machine.packaging.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import io.netty.buffer.Unpooled;

/** Real native authority, conservation, persistence and frozen-queue checks. */
public final class T4RuntimeGameTests {
 private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
 static {
  FUNCTIONS.register("t4_panel_authority",()->T4MenuGameTests::run);
  FUNCTIONS.register("t4_full_chain_logic",()->h->T4FullChainGameTests.run(h,DieTypes.LOGIC,7));
  FUNCTIONS.register("t4_full_chain_memory",()->h->T4FullChainGameTests.run(h,DieTypes.MEMORY,8));
  FUNCTIONS.register("t4_full_chain_compute",()->h->T4FullChainGameTests.run(h,DieTypes.COMPUTE,9));
  FUNCTIONS.register("t4_full_chain_nand",()->h->T4FullChainGameTests.run(h,DieTypes.NAND,10));
  FUNCTIONS.register("t4_advanced_geometry",()->T4RuntimeGameTests::geometry);
  FUNCTIONS.register("t4_advanced_capabilities",()->T4RuntimeGameTests::capabilities);
  FUNCTIONS.register("t4_preparation_conservation",()->T4RuntimeGameTests::preparation);
  FUNCTIONS.register("t4_preparation_refusals",()->T4RuntimeGameTests::preparationRefusals);
  FUNCTIONS.register("t4_preparation_pause_reload",()->T4RuntimeGameTests::preparationPauseReload);
  FUNCTIONS.register("t4_preparation_break",()->T4RuntimeGameTests::preparationBreak);
  FUNCTIONS.register("t4_high_binning_logic",()->h->binning(h,DieTypes.LOGIC));
  FUNCTIONS.register("t4_high_binning_compute",()->h->binning(h,DieTypes.COMPUTE));
  FUNCTIONS.register("t4_high_binning_refusals",()->T4RuntimeGameTests::binningRefusals);
  FUNCTIONS.register("t4_high_binning_frozen_reload",()->T4RuntimeGameTests::binningReload);
 }
 public static void register(IEventBus bus){
  FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{
   Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.parse("lithmod:t4_runtime"),new TestEnvironmentDefinition[0]);
   var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),300,0,true,Rotation.NONE,false,1,1,false,0);
   for(String name:List.of("t4_panel_authority","t4_full_chain_logic","t4_full_chain_memory","t4_full_chain_compute","t4_full_chain_nand","t4_advanced_geometry","t4_advanced_capabilities","t4_preparation_conservation",
     "t4_preparation_refusals","t4_preparation_pause_reload","t4_preparation_break","t4_high_binning_logic",
     "t4_high_binning_compute","t4_high_binning_refusals","t4_high_binning_frozen_reload")){
    var id=Identifier.fromNamespaceAndPath("lithmod",name);
    event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));
   }
  });
 }
 private static void geometry(GameTestHelper h){
  int count=0;
  for(var kind:List.of(Kind.DEPOSITION,Kind.PACKAGING))
   for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))
    for(int column=0;column<(kind==Kind.DEPOSITION?3:2);column++){
     try(var c=build(h,kind,facing,column)){
      var owner=(AdvancedOwner)c.machine();eq(h,kind==Kind.DEPOSITION?45:12,owner.members().size(),"Full shape membership");
      for(int n=0;n<c.positions().size();n++)eq(h,n,c.level().getBlockState(c.positions().get(n)).getValue(AdvancedVisualProperties.PART),"Canonical visual part");
      var casing=c.positions().stream().filter(p->c.level().getBlockState(p).getBlock()==AdvancedMachines.ADVANCED_CASING.get()).findFirst().orElseThrow();
      c.level().setBlock(casing,Blocks.AIR.defaultBlockState(),3);owner.revalidate();check(h,!owner.isFormed(),"Missing casing refuses");
      c.level().setBlock(casing,AdvancedMachines.ADVANCED_CASING.get().defaultBlockState(),3);owner.revalidate();check(h,owner.isFormed(),"Reform retains geometry");
      c.level().setBlock(casing,AdvancedMachines.ADVANCED_OPERATING_PANEL.get().defaultBlockState(),3);owner.revalidate();check(h,!owner.isFormed(),"Extra panel rejected");
      count++;
     }
    }
  eq(h,20,count,"All shapes, facings and front columns checked");h.succeed();
 }
 private static void capabilities(GameTestHelper h){
  for(var kind:List.of(Kind.DEPOSITION,Kind.PACKAGING))try(var c=build(h,kind)){
   for(var pos:c.positions())for(var side:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN)){
    check(h,c.level().getCapability(Capabilities.Item.BLOCK,pos,side)==null,"No item automation");
    check(h,c.level().getCapability(Capabilities.Fluid.BLOCK,pos,side)==null,"No fluid automation");
    var energy=c.level().getCapability(Capabilities.Energy.BLOCK,pos,side);
    check(h,(energy!=null)==pos.equals(c.port()),"FE only on owned port");
   }
   check(h,c.level().getCapability(Capabilities.Item.BLOCK,c.port(),null)==null,"No null-side items");
   check(h,c.level().getCapability(Capabilities.Fluid.BLOCK,c.port(),null)==null,"No null-side fluids");
   var handler=c.level().getCapability(Capabilities.Energy.BLOCK,c.port(),null);check(h,handler!=null,"Null-side FE");
   try(var tx=Transaction.openRoot()){eq(h,500,handler.insert(500,tx),"FE simulation");}
   eq(h,0,c.machine().buffer().getAmountAsLong(),"Aborted FE is not stored");
   try(var tx=Transaction.openRoot()){eq(h,500,handler.insert(500,tx),"FE insert");eq(h,0,handler.extract(10,tx),"No external extraction");tx.commit();}
   eq(h,500,c.machine().buffer().getAmountAsLong(),"Committed FE");
   c.level().setBlock(c.anchor().above(),Blocks.AIR.defaultBlockState(),3);
   try(var tx=Transaction.openRoot()){eq(h,0,handler.insert(100,tx),"Retained handle cannot charge an unformed owner");tx.commit();}
   eq(h,500,c.machine().buffer().getAmountAsLong(),"Stale transfer conserves FE");
  }
  h.succeed();
 }
 private static void preparation(GameTestHelper h){
  try(var c=build(h,Kind.DEPOSITION)){
   var machine=(DepositionEtcherControllerBlockEntity)c.machine();
   var original=fresh(3,WaferSubstrate.PURE);original.set(DataComponents.CUSTOM_NAME,Component.literal("preserved carrier"));
   var lot=FoupItems.trustedContentsOf(original);
   int silicon=machine.definition().orElseThrow().param("silicon_per_lot").orElseThrow();
   int duration=machine.definition().orElseThrow().param("duration").orElseThrow();
   int cost=machine.definition().orElseThrow().energyPerTick(null,EUVCRAFTMod.settings());
   machine.setSlot(0,original.copy());machine.setSlot(1,new ItemStack(ModItems.SILICON.get(),silicon+2));
   long paid=runPreparation(h,machine);var output=machine.slot(2);var prepared=FoupItems.trustedContentsOf(output);
   check(h,prepared.lotId().equals(lot.lotId())&&prepared.wafers().equals(lot.wafers()),"Preparation retains original identity and hidden wafer data");
   check(h,prepared.grade()==WaferSubstrate.PURE&&prepared.preparationGeneration()==2,"Pure G2 preparation output");
   eq(h,lot.capacity(),prepared.capacity(),"Carrier capacity retained");
   check(h,original.get(DataComponents.CUSTOM_NAME).equals(output.get(DataComponents.CUSTOM_NAME)),"Carrier components retained");
   eq(h,2,machine.slot(1).getCount(),"Exact reagent debit");eq(h,(long)duration*cost,paid,"Exact JSON energy");
   check(h,machine.slot(0).isEmpty()&&!machine.hasJob(),"No duplicate input or pending output");
  }h.succeed();
 }
 private static void preparationRefusals(GameTestHelper h){
  try(var c=build(h,Kind.DEPOSITION)){
   var machine=(DepositionEtcherControllerBlockEntity)c.machine();
   var prepared=fresh(2,WaferSubstrate.PURE);prepared.set(ModDataComponents.FOUP_CONTENTS.get(),FoupItems.trustedContentsOf(prepared).withPreparationGeneration(2));
   var untrusted=fresh(2,WaferSubstrate.PURE);var buffer=Unpooled.buffer();
   FoupContentsCodec.STREAM_CODEC.encode(buffer,FoupItems.trustedContentsOf(untrusted));
   untrusted.set(ModDataComponents.FOUP_CONTENTS.get(),FoupContentsCodec.STREAM_CODEC.decode(buffer));buffer.release();
   for(var invalid:List.of(fresh(2,WaferSubstrate.ORDINARY),fresh(0,WaferSubstrate.PURE),complete(3,DieTypes.LOGIC,0),prepared,untrusted)){
    machine.setSlot(0,invalid.copy());machine.setSlot(1,new ItemStack(ModItems.SILICON.get(),16));replenish(machine);
    var before=FoupItems.contentsOf(machine.slot(0));int energy=machine.buffer().getAmountAsInt();machine.serverTick();
    check(h,FoupItems.contentsOf(machine.slot(0)).equals(before),"Refusal leaves original lot");
    eq(h,16,machine.slot(1).getCount(),"Refusal consumes no silicon");eq(h,energy,machine.buffer().getAmountAsInt(),"Refusal consumes no power");
    check(h,!machine.hasJob()&&machine.slot(2).isEmpty(),"Refusal creates no job/output");
   }
  }h.succeed();
 }
 private static void preparationPauseReload(GameTestHelper h){
  try(var c=build(h,Kind.DEPOSITION)){
   var machine=(DepositionEtcherControllerBlockEntity)c.machine();
   machine.setSlot(0,fresh(2,WaferSubstrate.PURE));machine.setSlot(1,new ItemStack(ModItems.SILICON.get(),4));
   replenish(machine);for(int i=0;i<5;i++)machine.serverTick();
   int progress=machine.jobProgress(),duration=machine.jobDuration();
   var tag=save(c.level(),machine);var state=c.level().getBlockState(c.anchor());
   c.level().removeBlockEntity(c.anchor());
   var replacement=AdvancedMachines.DEPOSITION_ETCHER_CONTROLLER_BE.get().create(c.anchor(),state);
   c.level().setBlockEntity(replacement);load(c.level(),replacement,tag);replacement.revalidate();
   eq(h,progress,replacement.jobProgress(),"Pending progress survives reload");eq(h,duration,replacement.jobDuration(),"Frozen duration survives reload");
   replacement.setStoredEnergy(0);replacement.serverTick();eq(h,progress,replacement.jobProgress(),"Out of power pauses");
   replenish(replacement);replacement.setSlot(2,new ItemStack(Items.STONE));int energy=replacement.buffer().getAmountAsInt();
   replacement.serverTick();eq(h,progress,replacement.jobProgress(),"Full output pauses");eq(h,energy,replacement.buffer().getAmountAsInt(),"Blocked tick costs no FE");
   replacement.clearSlot(2);c.level().setBlock(c.anchor().above(),Blocks.AIR.defaultBlockState(),3);replacement.serverTick();
   eq(h,progress,replacement.jobProgress(),"Unformed pauses accepted job");
   c.level().setBlock(c.anchor().above(),AdvancedMachines.ADVANCED_OPERATING_PANEL.get().defaultBlockState(),3);
   runPreparation(h,replacement);eq(h,2,FoupItems.trustedContentsOf(replacement.slot(2)).preparationGeneration(),"Reform/refill resumes to prepared output");
  }h.succeed();
 }
 private static void preparationBreak(GameTestHelper h){
  try(var c=build(h,Kind.DEPOSITION)){
   var machine=(DepositionEtcherControllerBlockEntity)c.machine();var original=fresh(2,WaferSubstrate.PURE);
   var id=FoupItems.trustedContentsOf(original).lotId();machine.setSlot(0,original);machine.setSlot(1,new ItemStack(ModItems.SILICON.get(),4));
   replenish(machine);machine.serverTick();check(h,machine.hasJob(),"Actual pending job exists before break");
   c.level().destroyBlock(c.anchor(),true);
   var drops=c.level().getEntitiesOfClass(ItemEntity.class,new AABB(c.anchor()).inflate(1),
    e->FoupItems.isFoup(e.getItem())&&FoupItems.trustedContentsOf(e.getItem()).lotId().equals(id));
   eq(h,1,drops.size(),"Pending original carrier drops exactly once");
   eq(h,1,FoupItems.trustedContentsOf(drops.get(0).getItem()).preparationGeneration(),"Break cannot grant unearned preparation");
  }h.succeed();
 }
 private static int expectedPassing(ItemStack source,boolean high){
  var lot=FoupItems.trustedContentsOf(source);var spec=lot.run();
  if(!high)return YieldRollEngine.countPassing(YieldRollEngine.evaluate(lot,TierDataLoader.getRegistry().getByIndex(spec.tierIndex()),spec.dieType(),spec.coresOrNull()));
  var transition=HighBinningDataLoader.getRegistry().orElseThrow().forSource(spec.dieType(),spec.tierIndex()).orElseThrow().transition();
  double probability=spec.effectiveYield()*transition.yieldMultiplier();
  double quantile=-StrictMath.log1p(-probability)/YieldCalibration.calibrateBaseWeight(spec.rollCount(),probability);
  double threshold=StrictMath.exp(-YieldCalibration.baseWeight(spec.rollCount(),spec.effectiveYield())*quantile);
  return (int)lot.wafers().stream().filter(w->w.cumulativeRoll()>threshold).count();
 }
 private static void binning(GameTestHelper h,String family){
  try(var c=build(h,Kind.PACKAGING)){
   var machine=(AdvancedTestPackageControllerBlockEntity)c.machine();check(h,machine.toggleHighBinning(),"Idle mode can be selected");
   var source=complete(3,family,DieTypes.COMPUTE.equals(family)?1:0);var lot=FoupItems.trustedContentsOf(source);
   int passes=expectedPassing(source,true),chips=EUVCRAFTMod.settings().chipsPerWafer();
   int packages=machine.definition().orElseThrow().param("package_per_wafer").orElseThrow();
   var transition=HighBinningDataLoader.getRegistry().orElseThrow().forSource(family,3).orElseThrow().transition();
   var tier=TierDataLoader.getRegistry().getByIndex(3);
   int cost=transition.scaledEnergyPerTick(machine.definition().orElseThrow().energyPerTick(tier,EUVCRAFTMod.settings()));
   int ticks=transition.scaledTicksPerWafer(machine.definition().orElseThrow().param("ticks_per_wafer").orElseThrow());
   machine.setSlot(0,source);machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));replenish(machine);machine.serverTick();
   check(h,machine.isQueueActive(),"High bin queue is admitted");check(h,!machine.toggleHighBinning(),"Active queue refuses mode changes");
   var emptied=FoupItems.trustedContentsOf(machine.slot(0));check(h,emptied.isEmpty()&&!emptied.lotId().equals(lot.lotId()),"Lot consumed immediately with fresh empty carrier");
   eq(h,lot.capacity(),emptied.capacity(),"Empty carrier capacity preserved");eq(h,1,emptied.preparationGeneration(),"Empty carrier retains no preparation");
   var result=packageAll(h,machine,ChipCatalog.chipFor(family,4).orElseThrow(),lot.size());
   eq(h,passes*chips,result.chips(),"High bin target output");eq(h,(lot.size()-passes)*chips,result.scrap(),"Failures are scrap, never normal-tier chips");
   eq(h,64-passes*packages,machine.slot(1).getCount(),"Packages consumed only for successful wafers");
   eq(h,(long)ticks*cost*lot.size(),result.energy(),"Frozen high-bin energy and duration");
  }h.succeed();
 }
 private static void binningRefusals(GameTestHelper h){
  try(var c=build(h,Kind.PACKAGING)){
   var machine=(AdvancedTestPackageControllerBlockEntity)c.machine();machine.toggleHighBinning();
   var client=complete(3,DieTypes.LOGIC,0);var buf=Unpooled.buffer();FoupContentsCodec.STREAM_CODEC.encode(buf,FoupItems.trustedContentsOf(client));
   client.set(ModDataComponents.FOUP_CONTENTS.get(),FoupContentsCodec.STREAM_CODEC.decode(buf));buf.release();
   for(var source:List.of(complete(3,DieTypes.MEMORY,0),complete(3,DieTypes.NAND,0),complete(4,DieTypes.LOGIC,0),fresh(3,WaferSubstrate.PURE),client,complete(3,DieTypes.COMPUTE,0))){
    machine.setSlot(0,source.copy());machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));replenish(machine);
    var before=FoupItems.contentsOf(machine.slot(0));machine.serverTick();check(h,!machine.isQueueActive(),"Unsupported source cannot start");
    check(h,before.equals(FoupItems.contentsOf(machine.slot(0))),"Unsupported source is not consumed");eq(h,64,machine.slot(1).getCount(),"Unsupported source consumes no packages");
   }
  }h.succeed();
 }
 private static void binningReload(GameTestHelper h){
  double oldPower=FabConfig.FE_POWER_MULTIPLIER.get();int oldChips=FabConfig.CHIPS_PER_WAFER.get();
  try(var c=build(h,Kind.PACKAGING)){
   var machine=(AdvancedTestPackageControllerBlockEntity)c.machine();machine.toggleHighBinning();
   var source=complete(3,DieTypes.LOGIC,0);int passes=expectedPassing(source,true),count=FoupItems.trustedContentsOf(source).size();
   machine.setSlot(0,source);machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));replenish(machine);machine.serverTick();
   var tag=save(c.level(),machine);var state=c.level().getBlockState(c.anchor());c.level().removeBlockEntity(c.anchor());
   var replacement=AdvancedMachines.ADVANCED_TEST_PACKAGE_CONTROLLER_BE.get().create(c.anchor(),state);c.level().setBlockEntity(replacement);
   load(c.level(),replacement,tag);replacement.revalidate();check(h,replacement.isQueueActive(),"Accepted plan survives actual save/load");
   FabConfig.FE_POWER_MULTIPLIER.set(oldPower<99?oldPower+1:oldPower-1);FabConfig.CHIPS_PER_WAFER.set(oldChips==1?2:1);EUVCRAFTMod.config().onConfigReload();
   replacement.setStoredEnergy(0);replacement.serverTick();check(h,replacement.isPaused(),"Reloaded queue pauses without power");
   var result=packageAll(h,replacement,ModItems.LOGIC_CHIP_T4.get(),count);
   eq(h,passes*oldChips,result.chips(),"Config reload cannot reroll or change frozen output amounts");
   eq(h,(count-passes)*oldChips,result.scrap(),"Saved failures remain failures");
  } finally {FabConfig.FE_POWER_MULTIPLIER.set(oldPower);FabConfig.CHIPS_PER_WAFER.set(oldChips);EUVCRAFTMod.config().onConfigReload();}
  h.succeed();
 }
 private T4RuntimeGameTests(){}
}

