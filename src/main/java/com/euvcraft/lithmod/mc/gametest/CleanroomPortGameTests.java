package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.mc.multiblock.support.*;
import net.minecraft.core.*;import net.minecraft.core.registries.Registries;import net.minecraft.resources.*;import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;import net.minecraft.world.level.block.*;import net.minecraft.world.item.Items;import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;import net.neoforged.neoforge.event.RegisterGameTestsEvent;import net.neoforged.neoforge.registries.DeferredRegister;import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;import net.neoforged.neoforge.transfer.item.ItemResource;import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitCapabilities;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import java.util.*;import java.util.function.Consumer;
public final class CleanroomPortGameTests {
 private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
 private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
 static{add("t5_cleanroom_port_power_persistence",CleanroomPortGameTests::powerPersistence);add("t5_cleanroom_port_power",CleanroomPortGameTests::power);add("t5_cleanroom_port_items",CleanroomPortGameTests::items);add("t5_cleanroom_port_fluids",CleanroomPortGameTests::fluids);add("t5_cleanroom_port_chain_stale",CleanroomPortGameTests::chain);add("t5_cleanroom_port_arcforge_network",CleanroomPortGameTests::arcforgeNetwork);}
 private static void add(String id,Consumer<GameTestHelper> run){CASES.put(id,run);FUNCTIONS.register(id,()->run);}
 public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.parse("lithmod:cleanroom_ports"),new TestEnvironmentDefinition[0]);var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),400,0,true,Rotation.NONE,false,1,1,false,0);for(var name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
 private static void clear(GameTestHelper h,List<BlockPos> positions){for(var p:positions){if(h.getLevel().getBlockEntity(p) instanceof net.minecraft.world.Container c)c.clearContent();h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);}}
 private static void power(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,2,2));var points=new ArrayList<BlockPos>();points.add(p);for(var d:Direction.values())points.add(p.relative(d));try{
  l.setBlock(p,CleanroomBlocks.POWER_PORT.get().defaultBlockState(),3);
  for(var side:Direction.values()){l.setBlock(p,l.getBlockState(p).setValue(CleanroomPortBlock.FACING,side),3);var target=p.relative(side.getOpposite());l.setBlock(target,CleanroomBlocks.FILTER.get().defaultBlockState(),3);var filter=(CleanroomFilterBlockEntity)l.getBlockEntity(target);filter.setStoredEnergy(0);var cap=l.getCapability(Capabilities.Energy.BLOCK,p,side);check(h,cap!=null,"Both axis faces expose FE");try(var tx=Transaction.openRoot()){eq(h,1000,cap.insert(1000,tx),"FE tentative insertion");}eq(h,0,filter.buffer().getAmountAsInt(),"FE rollback");try(var tx=Transaction.openRoot()){eq(h,1000,cap.insert(1000,tx),"FE insertion");tx.commit();}var port=(CleanroomPortBlockEntity)l.getBlockEntity(p);eq(h,1000,cap.getAmountAsLong(),"Committed FE buffered exactly once");port.serverTick();eq(h,0,cap.getAmountAsLong(),"Buffer paid direct delivery");eq(h,1000,filter.buffer().getAmountAsInt(),"Exactly once FE");l.setBlock(target,Blocks.AIR.defaultBlockState(),3);}
  l.setBlock(p,l.getBlockState(p).setValue(CleanroomPortBlock.FACING,Direction.NORTH),3);check(h,l.getCapability(Capabilities.Energy.BLOCK,p,Direction.EAST)==null&&l.getCapability(Capabilities.Item.BLOCK,p,Direction.NORTH)==null,"Transverse and wrong roles refused");
 }finally{clear(h,points);}h.succeed();}
 private static void items(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,2,2));var points=new ArrayList<BlockPos>();points.add(p);for(var d:Direction.values())points.add(p.relative(d));try{
  l.setBlock(p,CleanroomBlocks.ITEM_PORT.get().defaultBlockState(),3);var medium=ItemResource.of(CleanroomItems.MEDIUM.get());
  for(var side:Direction.values()){l.setBlock(p,l.getBlockState(p).setValue(CleanroomPortBlock.FACING,side),3);var target=p.relative(side.getOpposite());l.setBlock(target,CleanroomBlocks.FILTER.get().defaultBlockState(),3);var f=(CleanroomFilterBlockEntity)l.getBlockEntity(target);var cap=l.getCapability(Capabilities.Item.BLOCK,p,side);check(h,cap!=null,"Both axis faces expose item handler");try(var tx=Transaction.openRoot()){eq(h,3,cap.insert(0,medium,3,tx),"Tentative media insertion");}check(h,f.slot(0).isEmpty(),"Media rollback");try(var tx=Transaction.openRoot()){eq(h,0,cap.insert(0,ItemResource.of(Items.STONE),1,tx),"Typed medium slot rejects stone");eq(h,3,cap.insert(0,medium,3,tx),"Media commit");tx.commit();}eq(h,3,f.slot(0).getCount(),"One committed insertion");try(var tx=Transaction.openRoot()){eq(h,2,cap.extract(0,medium,2,tx),"Extraction across seal");}eq(h,3,f.slot(0).getCount(),"Extraction rollback");f.clearContent();l.setBlock(target,Blocks.AIR.defaultBlockState(),3);eq(h,0,cap.size(),"Retained port refuses removed target");}
 }finally{clear(h,points);}h.succeed();}
 private static void fluids(GameTestHelper h){BlockPos extra=null;try(var c=build(h,Kind.PURIFIER)){
  var l=c.level();var inlet=c.positions().stream().filter(p->l.getBlockState(p).getBlock()==SupportMachines.SUPPORT_CASING.get()).max(Comparator.comparingInt(BlockPos::getZ)).orElseThrow();l.setBlock(inlet,SupportMachines.SUPPORT_WATER_INLET.get().defaultBlockState(),3);var owner=(SupportMachineBlockEntity)c.machine();owner.revalidate();check(h,owner.isFormed(),"Purifier endpoint forms");var p=inlet.south();extra=p;l.setBlock(p,CleanroomBlocks.FLUID_PORT.get().defaultBlockState(),3);var cap=l.getCapability(Capabilities.Fluid.BLOCK,p,Direction.SOUTH);check(h,cap!=null,"Sealed fluid lens");var water=FluidResource.of(Fluids.WATER);try(var tx=Transaction.openRoot()){eq(h,1000,cap.insert(0,water,1000,tx),"Tentative fluid");}eq(h,0,owner.waterAmount(),"Fluid rollback");try(var tx=Transaction.openRoot()){eq(h,1000,cap.insert(0,water,1000,tx),"Committed water");tx.commit();}eq(h,1000,owner.waterAmount(),"Same endpoint tank");check(h,l.getCapability(Capabilities.Fluid.BLOCK,p,Direction.EAST)==null,"Fluid transverse refused");
 }finally{if(extra!=null)h.getLevel().setBlock(extra,Blocks.AIR.defaultBlockState(),3);}h.succeed();}
 private static void chain(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,2,2));var points=new ArrayList<BlockPos>();
  try{
   for(int i=0;i<3;i++){var q=p.south(i);points.add(q);l.setBlock(q,CleanroomBlocks.POWER_PORT.get().defaultBlockState(),3);}
   var end=p.south(3);points.add(end);l.setBlock(end,CleanroomBlocks.FILTER.get().defaultBlockState(),3);var filter=(CleanroomFilterBlockEntity)l.getBlockEntity(end);
   var cap=l.getCapability(Capabilities.Energy.BLOCK,p,Direction.NORTH);
   try(var tx=Transaction.openRoot()){eq(h,1000,cap.insert(1000,tx),"Buffered chain admission");tx.commit();}
   for(int i=0;i<3;i++)((CleanroomPortBlockEntity)l.getBlockEntity(p.south(i))).serverTick();
   eq(h,1000,filter.buffer().getAmountAsLong(),"Adjacent buffered ports deliver without recursive capability calls");
   for(int i=0;i<3;i++)eq(h,0,l.getCapability(Capabilities.Energy.BLOCK,p.south(i),Direction.NORTH).getAmountAsLong(),"No duplicated FE in chain");
   l.setBlock(p,l.getBlockState(p).setValue(CleanroomPortBlock.FACING,Direction.EAST),3);
   try(var tx=Transaction.openRoot()){eq(h,0,cap.insert(1000,tx),"Retained handler refuses changed axis");tx.commit();}
   var live=l.getCapability(Capabilities.Energy.BLOCK,p,Direction.EAST);l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
   try(var tx=Transaction.openRoot()){eq(h,0,live.insert(1000,tx),"Removed port refuses stale insert");eq(h,0,live.extract(1000,tx),"Removed port refuses stale extraction");tx.commit();}
  }finally{clear(h,points);}h.succeed();
 }

 private static void arcforgeNetwork(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,2,4));var points=List.of(p.west(2),p.west(),p,p.east(),p.east(2));
  try{
   var cellBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_energy_cell"));
   var pipeBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_energy_conduit"));
   check(h,cellBlock!=Blocks.AIR&&pipeBlock!=Blocks.AIR,"Real Arcforge energy fixtures registered");
   l.setBlock(p.west(2),cellBlock.defaultBlockState(),3);l.setBlock(p.west(),pipeBlock.defaultBlockState(),3);
   l.setBlock(p,CleanroomBlocks.POWER_PORT.get().defaultBlockState().setValue(CleanroomPortBlock.FACING,Direction.WEST),3);
   l.setBlock(p.east(),pipeBlock.defaultBlockState(),3);l.setBlock(p.east(2),CleanroomBlocks.FILTER.get().defaultBlockState(),3);
   var cell=(EnergyCellBlockEntity)l.getBlockEntity(p.west(2));
   for(var side:RelativeSide.values())cell.setSideMode(side,SideMode.OUTPUT);
   var seed=cell.getEnergyHandler(null);check(h,seed!=null,"Actual cell charge view");int supplied=0;
   for(int i=0;i<200&&supplied<10000;i++){try(var tx=Transaction.openRoot()){int accepted=seed.insert(10000-supplied,tx);tx.commit();supplied+=accepted;}}
   eq(h,10000,supplied,"Source cell charged exactly");
   var outside=(ConduitBlockEntity)l.getBlockEntity(p.west());var inside=(ConduitBlockEntity)l.getBlockEntity(p.east());
   for(var side:Direction.values()){outside.setSetting(side,SideSetting.DISABLED);inside.setSetting(side,SideSetting.DISABLED);}
   // Arcforge OUTPUT extracts from an endpoint; INPUT supplies an endpoint (same as the proven water-inlet test).
   outside.setSetting(Direction.WEST,SideSetting.OUTPUT);outside.setSetting(Direction.EAST,SideSetting.INPUT);
   inside.setSetting(Direction.WEST,SideSetting.OUTPUT);inside.setSetting(Direction.EAST,SideSetting.INPUT);
   net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(l,p.west());net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(l,p.east());
   var manager=ConduitNetworkManager.get(l);manager.markDirty(p.west());manager.markDirty(p.east());
   for(int i=0;i<100;i++)manager.tick();
   var filter=(CleanroomFilterBlockEntity)l.getBlockEntity(p.east(2));var bridge=l.getCapability(Capabilities.Energy.BLOCK,p,Direction.EAST);
   long total=(long)cell.getEnergy()+outside.getStored()+inside.getStored()+filter.buffer().getAmountAsLong()+bridge.getAmountAsLong();
   eq(h,10000,total,"Conserved FE across both real networks and all buffers");
   check(h,filter.buffer().getAmountAsInt()>0,"Real Arcforge conduits on BOTH sides must transmit FE through cleanroom port");
  }finally{clear(h,points);}h.succeed();
 }

 private static void powerPersistence(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,2,2));var points=List.of(p,p.south());
  try{
   l.setBlock(p,CleanroomBlocks.POWER_PORT.get().defaultBlockState(),3);var cap=l.getCapability(Capabilities.Energy.BLOCK,p,Direction.NORTH);
   try(var tx=Transaction.openRoot()){eq(h,1500,cap.insert(1500,tx),"Initial persisted charge");tx.commit();}
   var tag=save(l,l.getBlockEntity(p));l.removeBlockEntity(p);var fresh=new CleanroomPortBlockEntity(p,l.getBlockState(p));l.setBlockEntity(fresh);load(l,fresh,tag);
   eq(h,0,cap.getAmountAsLong(),"Old entity view stale after restore");cap=l.getCapability(Capabilities.Energy.BLOCK,p,Direction.NORTH);eq(h,1500,cap.getAmountAsLong(),"Charge survives save/reload exactly");
   try(var tx=Transaction.openRoot()){eq(h,500,cap.extract(500,tx),"Tentative extraction");}eq(h,1500,cap.getAmountAsLong(),"Extraction rollback");
   var overflow=tag.copy();overflow.putInt("portEnergy",300000);load(l,fresh,overflow);eq(h,300000,cap.getAmountAsLong(),"Paid surplus preserved above configured capacity");
   try(var tx=Transaction.openRoot()){eq(h,0,cap.insert(1,tx),"Overcapacity cannot accept more");}load(l,fresh,tag);
   l.setBlock(p.south(),CleanroomBlocks.FILTER.get().defaultBlockState(),3);fresh.serverTick();eq(h,1500,((CleanroomFilterBlockEntity)l.getBlockEntity(p.south())).buffer().getAmountAsLong(),"Restored charge delivered exactly once");eq(h,0,cap.getAmountAsLong(),"No duplicate retained charge");
  }finally{clear(h,points);}h.succeed();
 }
 private CleanroomPortGameTests(){}
}
