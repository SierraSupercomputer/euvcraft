package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.core.cleanroom.FilterReport;
import com.euvcraft.lithmod.core.cleanroom.FilterReport.State;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FilterMonitorGameTests {
 private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
 private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
 static{add("snapshot",FilterMonitorGameTests::snapshot);add("menu",FilterMonitorGameTests::menu);add("unloaded",FilterMonitorGameTests::unloaded);add("prepaid",FilterMonitorGameTests::prepaid);}
 private static void add(String id,Consumer<GameTestHelper> run){id="filter_monitor_"+id;CASES.put(id,run);FUNCTIONS.register(id,()->run);}
 public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{for(var name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);var env=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),300,0,true,Rotation.NONE,false,1,1,false,0);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
 private static final AtomicInteger NEXT=new AtomicInteger();
 private static final class Room extends T7RuntimeFixtures.Arena {
  final int depth=32+7*NEXT.getAndIncrement();final List<CleanroomFilterBlockEntity> filters=new ArrayList<>();CleanroomControllerBlockEntity controller;
  @Override BlockPos anchor(){return super.anchor().below(depth);}
  Room(GameTestHelper h){super(h);if(depth>53)throw new IllegalStateException("Fixture capacity");var min=anchor();
   for(var mutable:BlockPos.betweenClosed(min,min.offset(4,4,4))){var p=mutable.immutable();boolean shell=p.getX()==min.getX()||p.getX()==min.getX()+4||p.getY()==min.getY()||p.getY()==min.getY()+4||p.getZ()==min.getZ()||p.getZ()==min.getZ()+4;put(p,shell?CleanroomBlocks.WALL.get():Blocks.AIR);}
   var cp=min.offset(1,1,0);put(cp,CleanroomBlocks.CONTROLLER.get());controller=(CleanroomControllerBlockEntity)level.getBlockEntity(cp);
   for(var delta:List.of(new BlockPos(2,1,0),new BlockPos(3,1,0),new BlockPos(4,1,1),new BlockPos(4,1,2),new BlockPos(4,1,3),new BlockPos(3,1,4),new BlockPos(2,1,4),new BlockPos(1,1,4))){var pos=min.offset(delta);put(pos,CleanroomBlocks.FILTER.get());var f=(CleanroomFilterBlockEntity)level.getBlockEntity(pos);f.setStoredEnergy(100000);f.setSlot(0,new ItemStack(CleanroomItems.MEDIUM.get(),4));filters.add(f);}
   controller.setStoredEnergy(200000);controller.revalidate();check(h,controller.state()==CleanroomControllerBlockEntity.ENCLOSED,"Test room formed");
  }
  void work(){for(int i=0;i<20;i++)controller.serverTick();}
 }
 private static void snapshot(GameTestHelper h){try(var r=new Room(h)){
  r.filters.get(0).setStoredEnergy(0);r.filters.get(1).clearSlot(0);r.work();
  var first=CleanroomFilterDiagnostics.snapshot(r.controller);var second=CleanroomFilterDiagnostics.snapshot(r.controller);check(h,first.equals(second),"Read-only snapshots do not consume FE or medium");
  var page=FilterReport.page(first,0);eq(h,8,page.total(),"All filters discovered");eq(h,6,page.working(),"Working filters counted beyond page");eq(h,2,page.attention(),"Both shortages counted");check(h,page.rows().get(0).state()==State.NO_POWER&&page.rows().get(1).state()==State.NO_MEDIUM,"Failures sorted before running units");
  for(var row:first){var pos=new BlockPos(row.x(),row.y(),row.z());check(h,r.controller.knownFilters().contains(pos),"Reported coordinates are absolute shell positions");var f=(CleanroomFilterBlockEntity)r.level.getBlockEntity(pos);eq(h,f.buffer().getAmountAsInt(),row.energy(),"Real stored FE");eq(h,f.remaining(),row.remainingTicks(),"Exact remaining paid medium");}
 }h.succeed();}
 private static void menu(GameTestHelper h){try(var r=new Room(h)){
  r.work();var player=T7RuntimeFixtures.player(h,r.controller.getBlockPos());var menu=new CleanroomMenu(7,player.getInventory(),r.controller,false);player.containerMenu=menu;
  check(h,menu.clickMenuButton(player,CleanroomMenu.FILTERS_TAB),"Open Filters tab");menu.broadcastChanges();eq(h,8,menu.reportTotal(),"Full list summary");eq(h,5,menu.reportRows().size(),"Bounded first page");check(h,menu.slots.stream().noneMatch(s->s.isActive()),"Player inventory hidden/inactive on diagnostics tab");
  check(h,menu.clickMenuButton(player,CleanroomMenu.NEXT_PAGE),"Next page");eq(h,1,menu.reportPage(),"Second page");eq(h,3,menu.reportRows().size(),"Remaining filters");for(int i=0;i<20;i++)menu.clickMenuButton(player,CleanroomMenu.NEXT_PAGE);eq(h,1,menu.reportPage(),"Repeated next clamps");
  var removed=r.filters.get(0).getBlockPos();r.put(removed,Blocks.AIR);r.controller.markTopologyDirty();r.controller.revalidate();menu.clickMenuButton(player,CleanroomMenu.FILTERS_TAB);eq(h,7,menu.reportTotal(),"Removed filter disappears after room rescan");check(h,menu.reportRows().stream().noneMatch(row->new BlockPos(row.x(),row.y(),row.z()).equals(removed)),"No stale removed row after rescan");
  check(h,!menu.clickMenuButton(player,999),"Unknown command rejected");player.setPos(player.getX()+100,player.getY(),player.getZ());check(h,!menu.clickMenuButton(player,CleanroomMenu.NEXT_PAGE),"Remote menu access rejected");player.setPos(r.controller.getBlockPos().getX()+.5,r.controller.getBlockPos().getY()+.5,r.controller.getBlockPos().getZ()+.5);check(h,menu.clickMenuButton(player,CleanroomMenu.ROOM_TAB),"Return to room");check(h,menu.slots.stream().allMatch(s->s.isActive()),"Inventory restored");
 }h.succeed();}
 private static void unloaded(GameTestHelper h){try(var r=new Room(h)){
  var far=new BlockPos(-20000000,-32,20000000);check(h,!r.level.hasChunkAt(far),"Remote chunk initially absent");
  try{var field=CleanroomControllerBlockEntity.class.getDeclaredField("filters");field.setAccessible(true);field.set(r.controller,List.of(far));}catch(ReflectiveOperationException bad){throw new IllegalStateException(bad);}
  var row=CleanroomFilterDiagnostics.snapshot(r.controller).getFirst();check(h,row.state()==State.UNAVAILABLE,"Missing chunk has explicit unavailable state");eq(h,-20000000,row.x(),"Negative absolute X preserved");eq(h,-1,row.energy(),"Unknown resources are not displayed as zero");check(h,!r.level.hasChunkAt(far),"Reporting never acquired remote chunk");
 }h.succeed();}
 private static void prepaid(GameTestHelper h){try(var r=new Room(h)){
  r.work();var f=r.filters.getFirst();f.clearSlot(0);int remaining=f.remaining();var before=save(r.level,f);var row=CleanroomFilterDiagnostics.snapshot(r.controller).stream().filter(x->new BlockPos(x.x(),x.y(),x.z()).equals(f.getBlockPos())).findFirst().orElseThrow();
  check(h,row.state()==State.FILTERING,"Prepaid medium keeps working with no spares");eq(h,0,row.spare(),"Empty spare slot is separate from paid lifetime");eq(h,remaining,row.remainingTicks(),"Snapshot retains exact paid medium");check(h,before.equals(save(r.level,f)),"Diagnostics do not change saved filter state");
  var tag=save(r.level,f);tag.putInt("mediumTicks",1);load(r.level,f,tag);row=CleanroomFilterDiagnostics.snapshot(r.controller).stream().filter(x->new BlockPos(x.x(),x.y(),x.z()).equals(f.getBlockPos())).findFirst().orElseThrow();check(h,row.state()==State.NO_MEDIUM,"Less than one work-second needs a refill");eq(h,1,f.remaining(),"Reporting does not spend partial paid medium");
 }h.succeed();}
 private FilterMonitorGameTests(){}
}
