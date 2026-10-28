package com.euvcraft.lithmod.mc.gametest;

import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.check;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.eq;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.save;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.load;
import static com.euvcraft.lithmod.mc.gametest.T7RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import com.euvcraft.lithmod.mc.multiblock.euv.*;
import com.euvcraft.lithmod.mc.multiblock.hydrogen.*;
import com.euvcraft.lithmod.mc.multiblock.vacuum.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Actual T7 jobs, resource conservation and saved transitions. Each large fixture runs alone. */
public final class T7RuntimeGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
    private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
    static{
        add("geometry",T7RuntimeGameTests::geometry);add("hydrogen_transactions",T7RuntimeGameTests::hydrogenTransactions);add("hydrogen_saved_job",T7RuntimeGameTests::hydrogenJob);add("g3_water_transactions",T7RuntimeGameTests::water);
        add("reticle_saved_job",T7RuntimeGameTests::writer);add("reticle_break",T7RuntimeGameTests::writerBreak);add("prep_saved_job",T7RuntimeGameTests::prep);add("prep_breach_repair",T7RuntimeGameTests::prepBreach);
        add("source_atomic_exposure",T7RuntimeGameTests::source);add("euv_complete_lot",T7RuntimeGameTests::euvLot);
    }
    private static void add(String name,Consumer<GameTestHelper> body){String id="t7_runtime_"+name;CASES.put(id,body);FUNCTIONS.register(id,()->body);}
    public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{for(String name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);Holder<TestEnvironmentDefinition<?>> environment=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);var data=new TestData<>(environment,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),400,0,true,Rotation.NONE,false,1,1,false,0);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
    private static void geometry(GameTestHelper h){
        for(int group=0;group<6;group++)try(var arena=new Arena(h)){
            var cell=switch(group){case 0->arena.writer();case 1->arena.prep();case 2->arena.hydrogen();case 3->arena.plant();case 4->arena.scanner();default->arena.source();};
            boolean formed=cell.machine() instanceof G3ControllerOwner g?g.isFormed():cell.machine() instanceof com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner a?a.isFormed():((EuvFormationOwner)cell.machine()).isFormed();
            check(h,formed,"Real machine forms in group "+group);eq(h,group==1||group==4?175:group==2?12:45,cell.positions().size(),"Exact native footprint");
        }h.succeed();
    }
    private static void hydrogenTransactions(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.hydrogen();var c=(HydrogenControllerBlockEntity)cell.machine();var inlet=arena.block(cell,HydrogenMachines.GAS_INLET.get());var outlet=arena.block(cell,HydrogenMachines.GAS_OUTLET.get());
        var input=arena.level.getCapability(Capabilities.Fluid.BLOCK,inlet,Direction.UP);var output=arena.level.getCapability(Capabilities.Fluid.BLOCK,outlet,Direction.UP);var raw=FluidResource.of(HydrogenFluids.rawHydrogenFluid().orElseThrow());var pure=FluidResource.of(HydrogenFluids.FAB_HYDROGEN.get());
        check(h,input!=null&&output!=null,"Gas port lenses exist");try(var tx=Transaction.openRoot()){eq(h,1000,input.insert(0,raw,1000,tx),"Raw H2 offered");}eq(h,0,c.rawHydrogenAmount(),"Aborted insert rolls back");
        try(var tx=Transaction.openRoot()){eq(h,0,input.insert(0,FluidResource.of(Fluids.WATER),1000,tx),"No water in gas inlet");eq(h,1000,input.insert(0,raw,1000,tx),"Commit raw H2");tx.commit();}
        c.pureHydrogen.fill(1000,false);try(var tx=Transaction.openRoot()){eq(h,1000,output.extract(0,pure,1000,tx),"Fab H2 offered");}eq(h,1000,c.pureHydrogenAmount(),"Aborted extraction rolls back");
        var frame=cell.anchor().offset(1,1,2);arena.put(frame,Blocks.AIR);try(var tx=Transaction.openRoot()){eq(h,0,input.insert(0,raw,1,tx),"Retained handle closes on unform");eq(h,0,output.extract(0,pure,1,tx),"Retained outlet closes");tx.commit();}
    }h.succeed();}
    private static void hydrogenJob(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.hydrogen();var c=(HydrogenControllerBlockEntity)cell.machine();c.setStoredEnergy(4000000);c.rawHydrogen.fill(8000,false);c.setSlot(0,new ItemStack(ModItems.UTILITY_FILTER.get(),2));c.serverTick();check(h,c.hasJob(),"Fluid-only batch admits");eq(h,0,c.rawHydrogenAmount(),"Raw batch reserved once");
        for(int i=0;i<150;i++)c.serverTick();var tag=save(arena.level,c);arena.level.removeBlockEntity(cell.anchor());c=new HydrogenControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));arena.level.setBlockEntity(c);load(arena.level,c,tag);eq(h,150,c.progress(),"Paid progress persists");
        c.setStoredEnergy(0);c.serverTick();eq(h,150,c.progress(),"Power outage pauses");c.setStoredEnergy(1000000);for(int i=0;i<250;i++)c.serverTick();check(h,!c.hasJob(),"One batch completes");eq(h,6000,c.pureHydrogenAmount(),"Frozen distinct output amount");eq(h,1,c.slot(0).getCount(),"One filter consumed");eq(h,850000,c.buffer().getAmountAsLong(),"Exactly 250 remaining paid ticks");
    }h.succeed();}
    private static void water(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.prep();var c=(G3ControllerBlockEntity)cell.machine();var inlet=arena.block(cell,G3Machines.WATER_INLET.get());var water=arena.level.getCapability(Capabilities.Fluid.BLOCK,inlet,Direction.EAST);check(h,water!=null,"G3 UPW inlet");var upw=FluidResource.of(com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids.ULTRAPURE_WATER.get());
        try(var tx=Transaction.openRoot()){eq(h,1000,water.insert(0,upw,1000,tx),"UPW offered");}eq(h,0,c.waterTank().amount(),"Shared G3 journal restores aborted insert");
        try(var tx=Transaction.openRoot()){eq(h,0,water.insert(0,FluidResource.of(Fluids.WATER),1000,tx),"Normal water refused");eq(h,1000,water.insert(0,upw,1000,tx),"UPW commits");tx.commit();}eq(h,1000,c.waterTank().amount(),"Exact committed amount");
    }h.succeed();}
    private static void writer(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.writer();var c=(G3ControllerBlockEntity)cell.machine();int cores=EUVCRAFTMod.settings().coreCountOptions().getLast();c.setSlot(0,new ItemStack(ModItems.BLANK_MASK.get()));c.setSlot(1,new ItemStack(Items.REDSTONE,64));c.setStoredEnergy(0);check(h,!c.startWriterJob("lithmod:g3_compute_t7",cores),"No FE leaves real inputs intact");eq(h,1,c.slot(0).getCount(),"Blank unspent on refusal");c.setStoredEnergy(4000000);check(h,c.startWriterJob("lithmod:g3_compute_t7",cores),"Outside cleanroom writer is allowed");check(h,c.slot(0).isEmpty()&&c.slot(1).isEmpty(),"Exact inputs physically reserved");
        for(int i=0;i<1200;i++){if(i%100==0)c.setStoredEnergy(4000000);c.serverTick();}var tag=save(arena.level,c);arena.level.removeBlockEntity(cell.anchor());c=new G3ReticleWriterControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));arena.level.setBlockEntity(c);load(arena.level,c,tag);
        for(int i=0;i<2400;i++){if(i%100==0)c.setStoredEnergy(4000000);c.serverTick();}var mask=c.slot(2).get(ModDataComponents.MASK_DATA.get());check(h,mask!=null&&mask.shopGeneration()==3&&mask.tierIndex()==7&&mask.dieType().equals("compute"),"Real G3 reflective reticle output");eq(h,cores,c.slot(2).getOrDefault(ModDataComponents.CORE_COUNT.get(),1),"Frozen compute core count");int outside=CleanroomRulesLoader.rules().outsideYieldPermille();eq(h,Math.max(1,EUVCRAFTMod.settings().maskUses()*Math.clamp(outside,250,1000)/1000),mask.usesRemaining(),"Outside penalty affects uses only");
    }h.succeed();}
    private static void writerBreak(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.writer();var c=(G3ControllerBlockEntity)cell.machine();c.setSlot(0,new ItemStack(ModItems.BLANK_MASK.get()));c.setSlot(1,new ItemStack(Items.REDSTONE,64));c.setStoredEnergy(4000000);check(h,c.startWriterJob("lithmod:g3_memory_t7",1),"Admitted memory reticle");for(int i=0;i<10;i++)c.serverTick();arena.put(cell.anchor(),Blocks.AIR);int blanks=0,redstone=0,masks=0;for(var entity:arena.level.getEntitiesOfClass(ItemEntity.class,new AABB(cell.anchor()).inflate(3))){var stack=entity.getItem();if(stack.is(ModItems.BLANK_MASK.get()))blanks+=stack.getCount();if(stack.is(Items.REDSTONE))redstone+=stack.getCount();if(stack.is(ModItems.MASK.get()))masks+=stack.getCount();}eq(h,1,blanks,"One genuine reserved blank");eq(h,64,redstone,"One genuine reserved reagent stack");eq(h,0,masks,"No unearned product drops");
    }h.succeed();}
    private static void warm(G3ControllerBlockEntity c){c.setStoredEnergy(4000000);for(int i=0;i<250;i++)c.serverTick();}
    private static void prep(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.prep();arena.room(cell,5,5,7);var c=(G3ControllerBlockEntity)cell.machine();warm(c);var input=lot(3,false);var original=FoupItems.trustedContentsOf(input);c.setSlot(0,input);check(h,!c.startPrepJob(),"No UPW does not reserve carrier");check(h,ItemStack.isSameItemSameComponents(input,c.slot(0)),"Mk2 name/data intact on refusal");c.waterTank().fill(4000,false);check(h,c.startPrepJob(),"Fresh electronic-grade FOUP admits");check(h,c.slot(0).isEmpty(),"Real carrier held during prep");
        for(int i=0;i<300;i++)c.serverTick();var tag=save(arena.level,c);arena.level.removeBlockEntity(cell.anchor());c=new G3DepositionEtcherControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));arena.level.setBlockEntity(c);load(arena.level,c,tag);for(int i=0;i<500;i++)c.serverTick();var result=c.slot(3);var prepared=FoupItems.trustedContentsOf(result);
        check(h,result.is(ModItems.FOUP_MK2.get())&&prepared!=null&&prepared.preparationGeneration()==3,"Prepared Mk2 returned");check(h,prepared.lotId().equals(original.lotId())&&result.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).equals(input.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)),"Exact carrier identity/name retained");eq(h,3,prepared.g3Preparation().factorCount(),"One frozen prep factor per wafer");eq(h,0,c.waterTank().amount(),"One frozen rinse charge");c.clearSlot(3);c.setSlot(0,result.copy());check(h,!c.startPrepJob(),"Prepared lot cannot be rerolled");check(h,FoupItems.trustedContentsOf(c.slot(0)).equals(prepared),"Preparation evidence unchanged");
    }h.succeed();}
    private static void prepBreach(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.prep();var room=arena.room(cell,5,5,7);var c=(G3ControllerBlockEntity)cell.machine();warm(c);c.waterTank().fill(16000,false);c.setSlot(0,lot(3,false));check(h,c.startPrepJob(),"Lot admits inside room");for(int i=0;i<10;i++)c.serverTick();c.setStoredEnergy(0);room.open();c.serverTick();var contaminated=FoupItems.trustedContentsOf(c.slot(3));check(h,contaminated!=null&&contaminated.isContaminated()&&contaminated.size()==3,"Power-paused active lot scraps into same carrier");eq(h,1,c.publicData(7),"Persistent machine contamination");room.seal();c.setSlot(1,new ItemStack(ModItems.UTILITY_FILTER.get(),2));c.setSlot(2,new ItemStack(Items.REDSTONE,16));c.setStoredEnergy(200000);int water=c.waterTank().amount();check(h,c.repair(),"Actual material/UPW/FE repair");eq(h,0,c.publicData(7),"Prep repaired without adding scanner minigame");eq(h,water-8000,c.waterTank().amount(),"Exact repair UPW cost");eq(h,0,c.buffer().getAmountAsLong(),"Exact repair FE");check(h,c.slot(1).isEmpty()&&c.slot(2).isEmpty()&&!c.repair(),"Bill charged once");
    }h.succeed();}
    private static void prepareLine(Arena arena,Cell scanner,Cell source){arena.beam();arena.room(scanner,5,5,7);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();s.setStoredEnergy(4000000);light.setStoredEnergy(4000000);s.upwTank.fill(32000,false);light.hydrogenTank.fill(32000,false);light.setSlot(0,new ItemStack(tin(),16));for(int i=0;i<250;i++){s.serverTick();light.serverTick();}}
    private static void source(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var source=arena.source();prepareLine(arena,scanner,source);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();check(h,s.hasReservedLightSource(),"Loaded straight beamline sees powered independent source");long scannerFe=s.buffer().getAmountAsLong(),sourceFe=light.buffer().getAmountAsLong();int water=s.upwTank.amount(),gas=light.hydrogenTank.amount(),tin=light.tinStored();
        eq(h,960,s.extractEnergy(960,true),"Readiness simulation succeeds");eq(h,gas,light.hydrogenTank.amount(),"Simulation spends no hydrogen");eq(h,tin,light.tinStored(),"Simulation spends no tin");
        try(var tx=Transaction.openRoot()){check(h,light.payExposure(s,tx),"Source can participate in transaction");}eq(h,sourceFe,light.buffer().getAmountAsLong(),"Aborted source FE rolls back");eq(h,gas,light.hydrogenTank.amount(),"Aborted source gas rolls back");eq(h,tin,light.tinStored(),"Aborted tin reservation rolls back");
        eq(h,960,s.extractEnergy(960,false),"One paid exposure");eq(h,scannerFe-960,s.buffer().getAmountAsLong(),"Scanner FE paid once");eq(h,sourceFe-1200,light.buffer().getAmountAsLong(),"Source FE paid once");eq(h,water-5,s.upwTank.amount(),"Scanner UPW paid once");eq(h,gas-10,light.hydrogenTank.amount(),"Source gas paid once");eq(h,tin-1,light.tinStored(),"One tin ingot opens prepaid exposure budget");eq(h,999,light.tinCreditTicks(),"Remaining tin budget persists");
        light.hydrogenTank.restoreAmount(0);eq(h,0,s.extractEnergy(960,false),"Gas outage pauses");eq(h,scannerFe-960,s.buffer().getAmountAsLong(),"Gas outage consumes no scanner FE");eq(h,water-5,s.upwTank.amount(),"Gas outage consumes no UPW");
    }h.succeed();}
    private static void euvLot(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var source=arena.source();prepareLine(arena,scanner,source);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();align(s);check(h,s.operatingCalibrated(),"Both physical calibration panels accepted");s.setSlot(0,lot(2,true));var mask=new ItemStack(ModItems.MASK.get());mask.set(ModDataComponents.MASK_DATA.get(),new MaskData("compute",7,5,3));mask.set(ModDataComponents.CORE_COUNT.get(),1);s.setSlot(1,mask);s.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));
        for(int i=0;i<8500&&s.processState()!=ProcessState.FINISHED;i++){if(i%100==0){s.setStoredEnergy(4000000);light.setStoredEnergy(4000000);s.upwTank.fill(32000,false);light.hydrogenTank.fill(32000,false);if(s.slot(2).getCount()<20)s.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));}light.serverTick();s.serverTick();}
        check(h,s.processState()==ProcessState.FINISHED,"Full T7 single-pass lot completes: "+s.processState()+" issue="+s.operatingIssue());var result=FoupItems.trustedContentsOf(s.slot(0));check(h,result!=null&&result.preparationGeneration()==3&&result.g3Preparation().factorCount()==2&&result.wafers().getFirst().layerIndex()==80,"Frozen G3 evidence retained through all 80 layers");eq(h,4,s.slot(1).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"One mask use per completed lot");
    }h.succeed();}
    private T7RuntimeGameTests(){}
}
