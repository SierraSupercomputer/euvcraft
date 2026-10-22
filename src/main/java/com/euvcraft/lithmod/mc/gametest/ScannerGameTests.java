package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.multiblock.scanner.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
public final class ScannerGameTests {
 private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
 private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
 static {
  add("t4_scanner_geometry",ScannerGameTests::geometry);
  add("t4_scanner_calibration_gating",ScannerGameTests::gating);
  add("t4_scanner_panel_authority",ScannerGameTests::authority);
  add("t4_scanner_hardware_reload",ScannerGameTests::hardwareReload);
  add("t4_scanner_yield_freeze",ScannerGameTests::yieldFreeze);
  add("t4_scanner_power_resume",ScannerGameTests::powerResume);
  add("t4_scanner_break_inventory",ScannerGameTests::breakInventory);
  add("t4_scanner_legacy_refusal",ScannerGameTests::legacyRefusal);
  add("t4_scanner_capabilities",ScannerGameTests::capabilities);
 }
 private static void add(String id,Consumer<GameTestHelper> run){CASES.put(id,run);FUNCTIONS.register(id,()->run);}
 public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{
  Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.parse("lithmod:scanner_runtime"),new TestEnvironmentDefinition[0]);
  var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),300,0,true,Rotation.NONE,false,1,1,false,0);
  for(var name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}
 });}
 private static ServerPlayer player(GameTestHelper h,BlockPos pos){var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"scanner-test"),false);var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());p.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);return p;}
 private static BlockPos calibration(Cell c){return c.positions().stream().filter(p->c.level().getBlockState(p).getBlock()==ScannerMachines.CALIBRATION_PANEL.get()).findFirst().orElseThrow();}
 static void align(GameTestHelper h,Cell c,int quality){
  var scanner=(ScannerControllerBlockEntity)c.level().getBlockEntity(c.anchor());var pos=calibration(c);var p=player(h,pos);var menu=new ScannerDashboardMenu(77,p.getInventory(),scanner,pos,true);p.containerMenu=menu;var target=scanner.alignment();
  int[] goals={target.targetX(),target.targetY(),target.targetFocus()+(100-quality)/10};
  for(int knob=0;knob<3;knob++)for(int tries=0;tries<30;tries++){
   var a=scanner.alignment();int value=knob==0?a.x():knob==1?a.y():a.focus();if(value==goals[knob])break;
   check(h,menu.clickMenuButton(p,knob*2+(value<goals[knob]?1:0)),"Server panel accepts bounded alignment adjustment");
  }
  eq(h,quality,scanner.alignment().quality(),"Requested deliberate alignment quality");check(h,menu.clickMenuButton(p,6)&&scanner.calibrated(),"Actual separate calibration panel accepts alignment");p.containerMenu=p.inventoryMenu;
 }
 private static void inputs(ScannerControllerBlockEntity s){
  var f=fresh(3,WaferSubstrate.PURE);f.set(ModDataComponents.FOUP_CONTENTS.get(),FoupItems.trustedContentsOf(f).withPreparationGeneration(2));
  s.setSlot(0,f);var m=new ItemStack(ModItems.MASK.get());m.set(ModDataComponents.MASK_DATA.get(),new MaskData(DieTypes.LOGIC,4,5,2));s.setSlot(1,m);s.setSlot(2,new ItemStack(ModItems.RESIST_T4.get(),64));
 }
 private static void fast(Runnable run){var before=new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());try{FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T4=2"));EUVCRAFTMod.config().onConfigReload();run.run();}finally{FabConfig.LAYER_TICKS_OVERRIDES.set(before);EUVCRAFTMod.config().onConfigReload();}}
 private static void geometry(GameTestHelper h){
  int count=0;for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(int col=0;col<3;col++)try(var c=build(h,Kind.SCANNER,facing,col)){
   var s=(ScannerControllerBlockEntity)c.machine();eq(h,45,s.members().size(),"All scanner cells owned");eq(h,3,s.getContainerSize(),"Only real operation inventory");
   var old=calibration(c);var spare=c.positions().stream().filter(p->p.getY()==c.anchor().getY()+1&&c.level().getBlockState(p).getBlock()==AdvancedMachines.ADVANCED_CASING.get()).findFirst().orElseThrow();
   c.level().setBlock(old,AdvancedMachines.ADVANCED_CASING.get().defaultBlockState(),3);c.level().setBlock(spare,ScannerMachines.CALIBRATION_PANEL.get().defaultBlockState(),3);s.revalidate();check(h,s.isFormed(),"Calibration panel can move within middle layer");
   var bottom=c.positions().stream().filter(p->p.getY()==c.anchor().getY()&&c.level().getBlockState(p).getBlock()==AdvancedMachines.ADVANCED_CASING.get()).findFirst().orElseThrow();c.level().setBlock(bottom,ScannerMachines.CALIBRATION_PANEL.get().defaultBlockState(),3);s.revalidate();check(h,!s.isFormed(),"Extra bottom calibration panel refused");count++;
  }eq(h,12,count,"All scanner facings and front columns");h.succeed();
 }
 private static void gating(GameTestHelper h){try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();inputs(s);replenish(s);var lot=FoupItems.trustedContentsOf(s.slot(0));int energy=s.buffer().getAmountAsInt();s.serverTick();
  check(h,!s.inputsLocked()&&s.processState()==ProcessState.IDLE,"Uncalibrated machine refuses run");eq(h,energy,s.buffer().getAmountAsInt(),"No rejected energy debit");check(h,lot.equals(FoupItems.trustedContentsOf(s.slot(0))),"Refusal does not roll wafers");eq(h,5,s.slot(1).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"Refusal does not spend mask");
  align(h,c,50);s.serverTick();check(h,s.inputsLocked(),"Accepted calibration permits actual admission");eq(h,750,Math.round(s.calibratedMultiplier()*1000),"Quality50 multiplier0.75");
 }h.succeed();}
 private static void authority(GameTestHelper h){try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();var pos=calibration(c);var p=player(h,pos);var menu=new ScannerDashboardMenu(1,p.getInventory(),s,pos,true);p.containerMenu=menu;
  eq(h,0,menu.slots.size(),"Calibration panel owns no item slots");check(h,menu.stillValid(p),"Real panel valid");var before=s.alignment();
  check(h,!menu.clickMenuButton(p,-1)&&!menu.clickMenuButton(p,7),"Invalid controls rejected");
  p.containerMenu=p.inventoryMenu;check(h,!menu.clickMenuButton(p,0)&&before.equals(s.alignment()),"Closed menu cannot adjust");p.containerMenu=menu;
  p.setPos(pos.getX()+20,pos.getY(),pos.getZ());check(h,!menu.clickMenuButton(p,0),"Distant calibration refused");p.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
  var cabinet=new ScannerDashboardMenu(2,p.getInventory(),s,c.anchor(),false);p.containerMenu=cabinet;check(h,!cabinet.clickMenuButton(p,0),"Cabinet cannot calibrate");
  var operation=new ScannerOperatingMenu(3,p.getInventory(),s,c.anchor().above());eq(h,39,operation.slots.size(),"Only3 operational slots");
  c.level().setBlock(pos,Blocks.AIR.defaultBlockState(),3);c.level().setBlock(pos,ScannerMachines.CALIBRATION_PANEL.get().defaultBlockState(),3);s.revalidate();p.containerMenu=menu;check(h,!menu.stillValid(p)&&!menu.clickMenuButton(p,0),"Replacement panel invalidates old menu");
 }h.succeed();}
 private static void hardwareReload(GameTestHelper h){try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();align(h,c,100);var all=new LinkedHashMap<BlockPos,net.minecraft.nbt.CompoundTag>();for(var pos:c.positions())all.put(pos,save(c.level(),c.level().getBlockEntity(pos)));
  var types=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.entity.BlockEntityType<?>>();for(var pos:c.positions())types.put(pos,c.level().getBlockEntity(pos).getType());
  for(var pos:c.positions())c.level().removeBlockEntity(pos);
  for(var pos:c.positions()){var be=types.get(pos).create(pos,c.level().getBlockState(pos));c.level().setBlockEntity(be);load(c.level(),be,all.get(pos));}
  s=(ScannerControllerBlockEntity)c.level().getBlockEntity(c.anchor());s.revalidate();check(h,s.calibrated(),"Module identities and calibration survive full member reload");
  var frame=c.positions().stream().filter(p->c.level().getBlockState(p).getBlock()==AdvancedMachines.ADVANCED_CASING.get()).findFirst().orElseThrow();c.level().setBlock(frame,Blocks.AIR.defaultBlockState(),3);s.revalidate();c.level().setBlock(frame,AdvancedMachines.ADVANCED_CASING.get().defaultBlockState(),3);s.revalidate();check(h,s.calibrated(),"Ordinary frame reformation keeps calibration");
  var module=c.positions().stream().filter(p->c.level().getBlockState(p).getBlock()==ScannerMachines.EXPOSURE_MODULE.get()).findFirst().orElseThrow();c.level().setBlock(module,Blocks.AIR.defaultBlockState(),3);c.level().setBlock(module,ScannerMachines.EXPOSURE_MODULE.get().defaultBlockState(),3);s.revalidate();check(h,!s.calibrated(),"Same-type module replacement requires recalibration");
 }h.succeed();}
 private static void yieldFreeze(GameTestHelper h){fast(()->{try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();align(h,c,50);inputs(s);replenish(s);s.serverTick();check(h,s.inputsLocked(),"Lot admitted");
  var p=player(h,calibration(c));var menu=new ScannerDashboardMenu(3,p.getInventory(),s,calibration(c),true);p.containerMenu=menu;check(h,!menu.clickMenuButton(p,0)&&!menu.clickMenuButton(p,6),"Active run locks calibration controls");
  for(int i=0;i<5;i++){replenish(s);s.serverTick();}var partial=FoupItems.trustedContentsOf(s.slot(0));double base=YieldMath.resolveEffectiveYield(TierDataLoader.getRegistry().getByIndex(4),EUVCRAFTMod.settings(),null);check(h,partial.run()!=null&&Math.abs(partial.run().effectiveYield()-base*.75)<1e-12,"Real first layer freezes calibrated yield exactly once");
  var saved=save(c.level(),s);c.level().removeBlockEntity(c.anchor());var restored=ScannerMachines.CONTROLLER_BE.get().create(c.anchor(),c.level().getBlockState(c.anchor()));c.level().setBlockEntity(restored);load(c.level(),restored,saved);restored.revalidate();
  for(int i=0;i<200&&restored.processState()!=ProcessState.FINISHED;i++){replenish(restored);restored.serverTick();}
  check(h,restored.processState()==ProcessState.FINISHED,"Accepted calibrated run resumes through save/load");var lot=FoupItems.trustedContentsOf(restored.slot(0));check(h,lot.run().equals(partial.run()),"Frozen factor is neither re-applied nor rerolled on resume");eq(h,4,restored.slot(1).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"Mask spent exactly once");
 }});h.succeed();}
 private static void powerResume(GameTestHelper h){fast(()->{try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();align(h,c,100);inputs(s);replenish(s);s.serverTick();s.setStoredEnergy(0);int progress=s.progressTicks();var lot=FoupItems.trustedContentsOf(s.slot(0));for(int i=0;i<5;i++)s.serverTick();eq(h,progress,s.progressTicks(),"Out of power pauses");check(h,lot.equals(FoupItems.trustedContentsOf(s.slot(0)))&&s.calibrated(),"Power failure keeps rolls and calibration");for(int i=0;i<200&&s.processState()!=ProcessState.FINISHED;i++){replenish(s);s.serverTick();}check(h,s.processState()==ProcessState.FINISHED,"Power refill resumes");
 }});h.succeed();}
 private static void breakInventory(GameTestHelper h){try(var c=build(h,Kind.SCANNER)){
  var s=(ScannerControllerBlockEntity)c.machine();inputs(s);var center=c.anchor();c.level().destroyBlock(center,false);var drops=c.level().getEntitiesOfClass(ItemEntity.class,new AABB(center).inflate(2));int foup=0,mask=0,resist=0;for(var e:drops){var stack=e.getItem();if(FoupItems.isFoup(stack))foup+=stack.getCount();else if(stack.is(ModItems.MASK.get()))mask+=stack.getCount();else if(stack.is(ModItems.RESIST_T4.get()))resist+=stack.getCount();else check(h,false,"Controller must not drop phantom physical equipment");}eq(h,1,foup,"One real carrier drop");eq(h,1,mask,"One real mask drop");eq(h,64,resist,"Exact remaining resist drop");
 }h.succeed();}
 private static void legacyRefusal(GameTestHelper h){try(var c=build(h,Kind.LITHOGRAPHY)){
  var s=(ProcessCellControllerBlockEntity)c.machine();Item[] modules={ModItems.ARF_EXCIMER_LASER.get(),ModItems.ARF_DRY_LENS.get(),ModItems.NANOPOSITIONING_STAGE.get(),ModItems.ULPA_FILTER.get(),ModItems.PRECISION_OVERLAY_SCOPE.get()};for(int i=0;i<5;i++)s.setSlot(i+3,new ItemStack(modules[i]));var f=fresh(2,WaferSubstrate.PURE);f.set(ModDataComponents.FOUP_CONTENTS.get(),FoupItems.trustedContentsOf(f).withPreparationGeneration(2));s.setSlot(0,f);var mask=new ItemStack(ModItems.MASK.get());mask.set(ModDataComponents.MASK_DATA.get(),new MaskData(DieTypes.LOGIC,4,5,2));s.setSlot(1,mask);s.setSlot(2,new ItemStack(ModItems.RESIST_T4.get(),64));replenish(s);s.serverTick();check(h,!s.inputsLocked()&&s.lastRefusal()!=null,"Small cell cannot process T4 even with full equipment and prepared lot");
 }h.succeed();}
 private static void capabilities(GameTestHelper h){try(var c=build(h,Kind.SCANNER)){
  for(var pos:c.positions())for(var side:Direction.values()){
   check(h,c.level().getCapability(Capabilities.Item.BLOCK,pos,side)==null,"No item automation");check(h,c.level().getCapability(Capabilities.Fluid.BLOCK,pos,side)==null,"No fluid automation");check(h,(c.level().getCapability(Capabilities.Energy.BLOCK,pos,side)!=null)==pos.equals(c.port()),"FE only on owned port");
  }var handle=c.level().getCapability(Capabilities.Energy.BLOCK,c.port(),null);check(h,handle!=null,"Port null-side FE");try(var tx=Transaction.openRoot()){eq(h,100,handle.insert(100,tx),"Scanner port accepts FE");tx.commit();}c.level().setBlock(calibration(c),Blocks.AIR.defaultBlockState(),3);try(var tx=Transaction.openRoot()){eq(h,0,handle.insert(100,tx),"Retained port refuses unformed owner");tx.commit();}
 }h.succeed();}
 private ScannerGameTests(){}
}
