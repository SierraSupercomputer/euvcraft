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
        add("unformed_member_ticks",T7RuntimeGameTests::unformedMemberTicks);add("member_formation_visuals",T7RuntimeGameTests::memberFormationVisuals);
        add("pulse_trial_lifecycle",T7RuntimeGameTests::sourcePulseTrial);add("pulse_idle_link_menu",T7RuntimeGameTests::sourcePulseLink);add("pulse_frozen_quality",T7RuntimeGameTests::sourcePulseFreeze);add("placement_facing",T7RuntimeGameTests::euvPlacementFacing);
        add("geometry",T7RuntimeGameTests::geometry);add("hydrogen_transactions",T7RuntimeGameTests::hydrogenTransactions);add("hydrogen_saved_job",T7RuntimeGameTests::hydrogenJob);add("g3_water_transactions",T7RuntimeGameTests::water);
        add("reticle_saved_job",T7RuntimeGameTests::writer);add("reticle_break",T7RuntimeGameTests::writerBreak);add("prep_saved_job",T7RuntimeGameTests::prep);add("prep_breach_repair",T7RuntimeGameTests::prepBreach);
        add("arcforge_gas_transport",T7RuntimeGameTests::gasTransport);add("contaminated_pickup",T7RuntimeGameTests::pickup);add("central_vacuum",T7RuntimeGameTests::central);
        add("scanner_breach_repair",T7RuntimeGameTests::scannerRepair);add("source_saved_pairing",T7RuntimeGameTests::pairing);add("menu_authority",T7RuntimeGameTests::menus);add("packaging_and_binning",T7RuntimeGameTests::packaging);
        add("source_atomic_exposure",T7RuntimeGameTests::source);add("euv_complete_lot",T7RuntimeGameTests::euvLot);
    }
    private static void add(String name,Consumer<GameTestHelper> body){String id="t7_runtime_"+name;CASES.put(id,body);FUNCTIONS.register(id,()->body);}
    public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{for(String name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);Holder<TestEnvironmentDefinition<?>> environment=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);var data=new TestData<>(environment,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),400,0,true,Rotation.NONE,false,1,1,false,0);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
    /** Standalone placed members must survive real world ticks, including stale formed state. */
    private static void unformedMemberTicks(GameTestHelper h) {
        var arena = new Arena(h);
        var positions = new ArrayList<BlockPos>();
        try {
            var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                    .filter(b -> b instanceof EuvScannerMemberBlock || b instanceof EuvLightSourceMemberBlock).toList();
            check(h, blocks.size() >= 18, "Every registered EUV member family is present");
            int index = 0;
            for (var facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                for (var block : blocks) {
                    var pos = arena.anchor().offset(index % 9, 0, index / 9); index++;
                    var state = block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                    arena.put(pos, state); positions.add(pos);
                    var member = arena.level.getBlockEntity(pos);
                    if (member instanceof EuvScannerMemberBlockEntity scanner) scanner.serverTick();
                    else ((EuvLightSourceMemberBlockEntity) member).serverTick();
                    // A saved formed block reloads before its transient controller association is rebuilt.
                    var formed = block instanceof EuvScannerMemberBlock ? EuvScannerMemberBlock.FORMED : EuvLightSourceMemberBlock.FORMED;
                    var part = block instanceof EuvScannerMemberBlock ? EuvScannerMemberBlock.PART : EuvLightSourceMemberBlock.PART;
                    arena.put(pos, state.setValue(formed, true).setValue(part, 2));
                    var saved = save(arena.level, member);
                    arena.level.removeBlockEntity(pos);
                    var restored = ((BaseEntityBlock) block).newBlockEntity(pos, arena.level.getBlockState(pos));
                    arena.level.setBlockEntity(restored); load(arena.level, restored, saved);
                }
            }
        } catch (RuntimeException | Error error) { arena.close(); throw error; }
        h.runAfterDelay(3, () -> {
            try (arena) {
                for (var pos : positions) {
                    var member = arena.level.getBlockEntity(pos);
                    if (member instanceof EuvScannerMemberBlockEntity scanner) scanner.serverTick();
                    else ((EuvLightSourceMemberBlockEntity) member).serverTick();
                    var state = arena.level.getBlockState(pos);
                    var formed = state.getBlock() instanceof EuvScannerMemberBlock ? EuvScannerMemberBlock.FORMED : EuvLightSourceMemberBlock.FORMED;
                    var part = state.getBlock() instanceof EuvScannerMemberBlock ? EuvScannerMemberBlock.PART : EuvLightSourceMemberBlock.PART;
                    check(h, !state.getValue(formed), "Unowned member clears stale formed flag: " + pos);
                    eq(h, 0, state.getValue(part), "Unowned member resets panel part");
                }
            }
            h.succeed();
        });
    }

    /** Every rendered member gets formation state and loses it after a structure breach. */
    private static void memberFormationVisuals(GameTestHelper h) {
        for (var facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            for (int group = 0; group < 2; group++) try (var arena = new Arena(h, facing)) {
                var cell = group == 0 ? arena.scanner() : arena.source();
                var owner = (EuvFormationOwner) cell.machine();
                var formed = group == 0 ? EuvScannerControllerBlock.FORMED : EuvLightSourceControllerBlock.FORMED;
                var part = group == 0 ? EuvScannerControllerBlock.PART : EuvLightSourceControllerBlock.PART;
                var parts = new HashSet<Integer>();
                for (var pos : cell.positions()) {
                    var state = arena.level.getBlockState(pos);
                    check(h, state.getValue(formed), "Every formed member has its joined visual state");
                    check(h, parts.add(state.getValue(part)), "Every member has a distinct panel part");
                    check(h, facing == state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING), "Formed facing retained");
                }
                eq(h, cell.positions().size(), parts.size(), "All joined panels assigned");
                var breach = cell.positions().getLast(); arena.put(breach, Blocks.AIR); owner.revalidate();
                check(h, !owner.isFormed(), "Removing a frame member unforms the machine");
                for (var pos : cell.positions()) if (!pos.equals(breach)) {
                    var state = arena.level.getBlockState(pos);
                    check(h, !state.getValue(formed), "Controller and remaining members clear their formed state");
                    eq(h, 0, state.getValue(part), "Unformed panel part resets");
                }
            }
        }
        h.succeed();
    }

    private static void sourcePulseTrial(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.source();var light=(EuvLightSourceControllerBlockEntity)cell.machine();light.setStoredEnergy(4000000);
        for(int i=0;i<250;i++)light.serverTick();light.hydrogenTank.fill(32000,false);light.setSlot(0,new ItemStack(tin(),4));
        check(h,!light.pulses.calibrated()&&!light.pulses.accept(),"Fresh source requires a paid accepted test");
        light.setStoredEnergy(0);int gas=light.hydrogenTank.amount(),tin=light.tinStored();check(h,!light.pulses.test(),"Missing FE refuses test");eq(h,gas,light.hydrogenTank.amount(),"No partial gas payment");eq(h,tin,light.tinStored(),"No partial tin payment");
        light.setStoredEnergy(4000000);long fe=light.buffer().getAmountAsLong();check(h,light.pulses.test(),"Standalone source tests without a scanner");
        eq(h,fe-light.trialFe(),light.buffer().getAmountAsLong(),"Exact trial FE");eq(h,gas-light.trialHydrogen(),light.hydrogenTank.amount(),"Exact trial gas");eq(h,3,light.tinStored(),"Only one tin opened");eq(h,1000-light.trialTin(),light.tinCreditTicks(),"Remainder retained as credit");
        var measured=light.pulses.measurement();var controls=light.pulses.controls();var saved=save(arena.level,light);
        arena.level.removeBlockEntity(cell.anchor());light=new EuvLightSourceControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));arena.level.setBlockEntity(light);load(arena.level,light,saved);light.revalidate();
        check(h,controls.equals(light.pulses.controls())&&measured.equals(light.pulses.measurement()),"Same target and paid measurement after reload");
        check(h,light.pulses.adjust(0,1)&&!light.pulses.accept(),"Changed settings cannot accept stale measurement");
        calibrateSource(light);var identity=light.pulses.installation();int quality=light.pulses.value(4);saved=save(arena.level,light);
        arena.level.removeBlockEntity(cell.anchor());light=new EuvLightSourceControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));arena.level.setBlockEntity(light);load(arena.level,light,saved);light.revalidate();
        check(h,light.pulses.calibrated()&&identity.equals(light.pulses.installation()),"Accepted calibration/identity survive reload");eq(h,quality,light.pulses.value(4),"Accepted quality stable");
        var module=arena.block(cell,EuvMachines.LIGHT_SOURCE_PULSE_MODULE.get());arena.put(module,Blocks.AIR);arena.put(module,EuvMachines.LIGHT_SOURCE_PULSE_MODULE.get());light.revalidate();check(h,!light.pulses.calibrated(),"Replacing physical pulse module invalidates calibration");
    }h.succeed();}
    private static void sourcePulseLink(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var cell=arena.source();arena.beam();var light=(EuvLightSourceControllerBlockEntity)cell.machine();
        check(h,light.physicalScanner()==scanner.machine(),"Idle physical link exists before calibration/resources/exposure");eq(h,2,light.readiness(),"Physical link reports source calibration rather than disconnected");
        var panel=arena.block(cell,EuvMachines.LIGHT_SOURCE_CALIBRATION_PANEL.get());var player=player(h,panel);var menu=new EuvSourceCalibrationMenu(42,player.getInventory(),light,panel);
        check(h,!menu.clickMenuButton(player,0),"Wrong active menu rejected");player.containerMenu=menu;check(h,!menu.clickMenuButton(player,999),"Unknown button rejected");
        check(h,menu.clickMenuButton(player,0),"Authorized tuning through source panel");arena.put(panel,Blocks.AIR);check(h,!menu.clickMenuButton(player,1),"Stale panel/menu cannot mutate source");
    }h.succeed();}
    private static void sourcePulseFreeze(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var source=arena.source();prepareLine(arena,scanner,source);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();
        for(int i=0;i<10;i++)light.pulses.adjust(2,1);check(h,light.pulses.test()&&light.pulses.accept(),"Deliberately imperfect accepted source");int multiplier=light.pulses.multiplier();check(h,multiplier>=850&&multiplier<1000,"Bounded source yield penalty");align(s);
        s.setSlot(0,lot(2,true));var mask=new ItemStack(ModItems.MASK.get());mask.set(ModDataComponents.MASK_DATA.get(),new MaskData("memory",7,5,3));s.setSlot(1,mask);s.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));s.serverTick();
        check(h,s.inputsLocked(),"Lot admitted with source calibration");eq(h,multiplier,s.sourceMultiplier(),"Source quality frozen once");check(h,light.pulses.locked()&&!light.pulses.adjust(0,1)&&!light.pulses.test(),"Active source rejects tuning and new trials");
        var snapshot=save(arena.level,s);arena.level.removeBlockEntity(scanner.anchor());s=new EuvScannerControllerBlockEntity(scanner.anchor(),arena.level.getBlockState(scanner.anchor()));arena.level.setBlockEntity(s);load(arena.level,s,snapshot);s.revalidate();eq(h,multiplier,s.sourceMultiplier(),"Frozen source yield survives scanner reload");
        light.setStoredEnergy(0);s.serverTick();eq(h,multiplier,s.sourceMultiplier(),"Utility outage preserves frozen quality");
        var sourceId=light.pulses.installation();arena.level.removeBlockEntity(source.anchor());light=new EuvLightSourceControllerBlockEntity(source.anchor(),arena.level.getBlockState(source.anchor()));arena.level.setBlockEntity(light);light.revalidate();
        check(h,!sourceId.equals(light.pulses.installation()),"New controller has a new installation identity");eq(h,2,s.sourceIssue(),"In-flight lot requires original source, cannot gain new quality");
    }h.succeed();}
    private static void euvPlacementFacing(GameTestHelper h){try(var arena=new Arena(h)){
        var pos=arena.anchor();var player=player(h,pos);
        for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            player.setYRot(facing.toYRot());
            for(var block:List.of(EuvMachines.LIGHT_SOURCE_OPERATING_PANEL.get(),EuvMachines.SCANNER_CALIBRATION_PANEL.get())){
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
                var context=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(block),hit);
                var state=block.getStateForPlacement(context);check(h,state!=null&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)==context.getHorizontalDirection().getOpposite(),"Standalone placement faces the player");
            }
        }
    }h.succeed();}
    private static void geometry(GameTestHelper h){
        for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(int group=0;group<6;group++)try(var arena=new Arena(h,facing)){
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
    private static void calibrateSource(EuvLightSourceControllerBlockEntity source){
        if(!source.pulses.test())throw new IllegalStateException("Trial not ready: "+source.pulses.trialIssue());
        var measured=source.pulses.measurement();int[] errors={measured.phaseError(),measured.delayError(),measured.amplitudeError()};
        for(int axis=0;axis<3;axis++)for(int n=0;n<Math.abs(errors[axis]);n++)source.pulses.adjust(axis,-Integer.signum(errors[axis]));
        if(!source.pulses.test())throw new IllegalStateException("Solved trial failed");
        // Existing exposure tests begin with a fresh paid tin budget; calibration pays the rest of its ingot.
        for(int n=0;source.tinCreditTicks()!=0&&n<1000;n++)if(!source.pulses.test())throw new IllegalStateException("Credit exhaustion failed");
        if(!source.pulses.accept())throw new IllegalStateException("Source acceptance failed");
        source.setStoredEnergy(4000000);source.hydrogenTank.fill(32000,false);source.setSlot(0,new ItemStack(tin(),16));
    }
    private static Room prepareLine(Arena arena,Cell scanner,Cell source){arena.beam();var room=arena.room(scanner,5,5,7);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();s.setStoredEnergy(4000000);light.setStoredEnergy(4000000);s.upwTank.fill(32000,false);light.hydrogenTank.fill(32000,false);light.setSlot(0,new ItemStack(tin(),16));for(int i=0;i<250;i++){s.serverTick();light.serverTick();}calibrateSource(light);return room;}
    private static void source(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var source=arena.source();prepareLine(arena,scanner,source);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();check(h,s.hasReservedLightSource(),"Loaded straight beamline sees powered independent source");long scannerFe=s.buffer().getAmountAsLong(),sourceFe=light.buffer().getAmountAsLong();int water=s.upwTank.amount(),gas=light.hydrogenTank.amount(),tin=light.tinStored();
        eq(h,960,s.extractEnergy(960,true),"Readiness simulation succeeds");eq(h,gas,light.hydrogenTank.amount(),"Simulation spends no hydrogen");eq(h,tin,light.tinStored(),"Simulation spends no tin");
        try(var tx=Transaction.openRoot()){check(h,light.payExposure(s,tx),"Source can participate in transaction");}eq(h,sourceFe,light.buffer().getAmountAsLong(),"Aborted source FE rolls back");eq(h,gas,light.hydrogenTank.amount(),"Aborted source gas rolls back");eq(h,tin,light.tinStored(),"Aborted tin reservation rolls back");
        eq(h,960,s.extractEnergy(960,false),"One paid exposure");eq(h,scannerFe-960,s.buffer().getAmountAsLong(),"Scanner FE paid once");eq(h,sourceFe-1200,light.buffer().getAmountAsLong(),"Source FE paid once");eq(h,water-5,s.upwTank.amount(),"Scanner UPW paid once");eq(h,gas-10,light.hydrogenTank.amount(),"Source gas paid once");eq(h,tin-1,light.tinStored(),"One tin ingot opens prepaid exposure budget");eq(h,999,light.tinCreditTicks(),"Remaining tin budget persists");
        light.hydrogenTank.restoreAmount(0);eq(h,0,s.extractEnergy(960,false),"Gas outage pauses");eq(h,scannerFe-960,s.buffer().getAmountAsLong(),"Gas outage consumes no scanner FE");eq(h,water-5,s.upwTank.amount(),"Gas outage consumes no UPW");
    }h.succeed();}
    private static void euvLot(GameTestHelper h){try(var arena=new Arena(h)){
        var scanner=arena.scanner();var source=arena.source();prepareLine(arena,scanner,source);var s=(EuvScannerControllerBlockEntity)scanner.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();align(s);check(h,s.operatingCalibrated(),"Both physical calibration panels accepted");s.setSlot(0,lot(2,true));var mask=new ItemStack(ModItems.MASK.get());mask.set(ModDataComponents.MASK_DATA.get(),new MaskData("compute",7,5,3));mask.set(ModDataComponents.CORE_COUNT.get(),1);s.setSlot(1,mask);s.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));
        for(int i=0;i<8500&&s.processState()!=ProcessState.FINISHED;i++){if(i%100==0){s.setStoredEnergy(4000000);light.setStoredEnergy(4000000);s.upwTank.fill(32000,false);light.hydrogenTank.fill(32000,false);if(s.slot(2).getCount()<20)s.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));}light.serverTick();s.serverTick();if(i==250){var snapshot=save(arena.level,s);arena.level.removeBlockEntity(scanner.anchor());s=new EuvScannerControllerBlockEntity(scanner.anchor(),arena.level.getBlockState(scanner.anchor()));arena.level.setBlockEntity(s);load(arena.level,s,snapshot);s.revalidate();}check(h,!s.aborted(),"EUV transition must not abort at iteration "+i+" layer="+FoupItems.trustedContentsOf(s.slot(0)).lotLayer());}
        check(h,s.processState()==ProcessState.FINISHED,"Full T7 single-pass lot completes: "+s.processState()+" issue="+s.operatingIssue()+" refusal="+s.lastRefusal());var result=FoupItems.trustedContentsOf(s.slot(0));check(h,result!=null&&result.preparationGeneration()==3&&result.g3Preparation().factorCount()==2&&result.wafers().getFirst().layerIndex()==80,"Frozen G3 evidence retained through all 80 layers");eq(h,4,s.slot(1).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"One mask use per completed lot");
    }h.succeed();}

    private static void gasTransport(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.hydrogen();var c=(HydrogenControllerBlockEntity)cell.machine();
        var pipeBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_pressurized_conduit"));
        var cylinderBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_pressurized_cylinder"));
        check(h,pipeBlock!=Blocks.AIR&&cylinderBlock!=Blocks.AIR,"Real Arcforge pressurized fixtures registered");
        var inlet=arena.block(cell,HydrogenMachines.GAS_INLET.get());var pipePos=inlet.west();var cylinderPos=pipePos.west();arena.put(pipePos,pipeBlock);arena.put(cylinderPos,cylinderBlock);
        var cylinder=(net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity)arena.level.getBlockEntity(cylinderPos);
        var raw=FluidResource.of(HydrogenFluids.rawHydrogenFluid().orElseThrow());
        try(var tx=Transaction.openRoot()){eq(h,8000,cylinder.getTank().insert(0,raw,8000,tx),"Actual gas cylinder seeds raw H2");tx.commit();}
        for(var side:net.zagdrath.arcforge.machine.config.RelativeSide.values())cylinder.setSideMode(side,net.zagdrath.arcforge.machine.config.SideMode.OUTPUT);
        var pipe=(net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity)arena.level.getBlockEntity(pipePos);
        for(var side:Direction.values())pipe.setSetting(side,net.zagdrath.arcforge.conduit.SideSetting.DISABLED);
        pipe.setSetting(Direction.WEST,net.zagdrath.arcforge.conduit.SideSetting.OUTPUT);pipe.setSetting(Direction.EAST,net.zagdrath.arcforge.conduit.SideSetting.INPUT);
        net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(arena.level,pipePos);var manager=net.zagdrath.arcforge.conduit.network.ConduitNetworkManager.get(arena.level);manager.markDirty(pipePos);for(int i=0;i<200;i++)manager.tick();
        check(h,c.rawHydrogenAmount()>0,"Real pressurized conduit supplies purifier inlet");eq(h,8000,c.rawHydrogenAmount()+cylinder.getGas().getAmount()+pipe.getStored(),"Raw gas conserved including conduit buffer");
        // Reverse flow uses the same actual gas API for the registered fab-H2 fluid.
        for(var side:net.zagdrath.arcforge.machine.config.RelativeSide.values())cylinder.setSideMode(side,net.zagdrath.arcforge.machine.config.SideMode.INPUT);
        c.pureHydrogen.fill(6000,false);var outlet=arena.block(cell,HydrogenMachines.GAS_OUTLET.get());var outPipe=outlet.east();var outCylinder=outPipe.east();arena.put(outPipe,pipeBlock);arena.put(outCylinder,cylinderBlock);
        var target=(net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity)arena.level.getBlockEntity(outCylinder);for(var side:net.zagdrath.arcforge.machine.config.RelativeSide.values())target.setSideMode(side,net.zagdrath.arcforge.machine.config.SideMode.INPUT);
        var output=(net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity)arena.level.getBlockEntity(outPipe);for(var side:Direction.values())output.setSetting(side,net.zagdrath.arcforge.conduit.SideSetting.DISABLED);output.setSetting(Direction.WEST,net.zagdrath.arcforge.conduit.SideSetting.OUTPUT);output.setSetting(Direction.EAST,net.zagdrath.arcforge.conduit.SideSetting.INPUT);net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(arena.level,outPipe);manager.markDirty(outPipe);for(int i=0;i<200;i++)manager.tick();
        check(h,target.getGas().getAmount()>0&&target.getGas().getFluid()==HydrogenFluids.FAB_HYDROGEN.get(),"Actual pressurized cylinder accepts fab-H2 gas");eq(h,6000,c.pureHydrogenAmount()+target.getGas().getAmount()+output.getStored(),"Fab gas conserved through real transport");
    }h.succeed();}
    private static void pickup(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.prep();var c=(G3ControllerBlockEntity)cell.machine();warm(c);arena.room(cell,5,5,7);c.waterTank().fill(16000,false);c.setSlot(0,lot(2,false));check(h,c.startPrepJob(),"Pickup fixture admits");check(h,c.notifyStructuralBreach(arena.level.getGameTime()),"Marks actual active equipment dirty");
        check(h,c.collectComponents().getOrDefault(ModDataComponents.CONTAMINATED_EQUIPMENT.get(),false),"Dirty controller exposes its narrow implicit marker");var drops=Block.getDrops(arena.level.getBlockState(cell.anchor()),arena.level,cell.anchor(),c);check(h,drops.size()==1,"One controller loot stack");var drop=drops.getFirst();check(h,drop.getOrDefault(ModDataComponents.CONTAMINATED_EQUIPMENT.get(),false),"Loot copies only persistent condition");check(h,!drop.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),"No copied job/inventory/energy payload");
        var fresh=new G3DepositionEtcherControllerBlockEntity(cell.anchor(),arena.level.getBlockState(cell.anchor()));fresh.applyComponentsFromItemStack(drop);check(h,fresh.contaminationState().requiresRepair(),"Replaced prep still requires repair");eq(h,0,fresh.buffer().getAmountAsLong(),"Pickup does not clone stored power");check(h,fresh.slot(0).isEmpty(),"Pickup does not clone carrier");
        var scannerPos=cell.anchor().offset(15,0,0);var scanner=new EuvScannerControllerBlockEntity(scannerPos,EuvMachines.SCANNER_CONTROLLER.get().defaultBlockState());scanner.applyComponentsFromItemStack(drop);check(h,scanner.contaminationState().requiresRepair(),"Scanner applies identical condition marker");
    }h.succeed();}
    private static void central(GameTestHelper h){try(var arena=new Arena(h)){
        var consumers=new ArrayList<G3ControllerBlockEntity>();
        for(int n=0;n<3;n++){var cell=arena.prep(arena.anchor().offset(n*10,0,0));var c=(G3ControllerBlockEntity)cell.machine();arena.put(cell.anchor().offset(2,1,1),G3Machines.CASING.get());arena.put(cell.anchor().offset(4,1,2),G3Machines.VACUUM_MODULE.get());c.revalidate();check(h,c.isFormed(),"Flexible vacuum module at shell");c.setStoredEnergy(1000000);consumers.add(c);for(int z=2;z<=8;z++)arena.put(cell.anchor().offset(5,1,z),VacuumMachines.CONDUIT.get());}
        for(int x=2;x<=25;x++)arena.put(arena.anchor().offset(x,1,8),VacuumMachines.CONDUIT.get());for(int z=9;z<=11;z++)arena.put(arena.anchor().offset(2,1,z),VacuumMachines.CONDUIT.get());
        var plant=arena.box(arena.anchor().offset(0,0,12),3,3,5,VacuumMachines.CASING.get(),VacuumMachines.CONTROLLER.get(),VacuumMachines.OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),VacuumMachines.POWER_PORT.get(),new BlockPos(2,1,0),VacuumMachines.SERVICE_OUTLET.get(),new BlockPos(1,1,1),VacuumMachines.CONDUIT.get()));var c=(VacuumControllerBlockEntity)plant.machine();c.setStoredEnergy(1000000);c.serverTick();eq(h,3,c.networkClientCount(),"All three independently owned chambers discovered");check(h,!c.networkConflict(),"One valid bounded network");eq(h,999650,c.buffer().getAmountAsLong(),"One central paid tick costs less than three 200-FE local pumps");
        for(var consumer:consumers){long before=consumer.buffer().getAmountAsLong();int pressure=consumer.vacuumState().pressureMicroTorr();consumer.serverTick();eq(h,before,consumer.buffer().getAmountAsLong(),"Central service displaces local pump FE");check(h,consumer.vacuumState().pressureMicroTorr()<pressure,"Independent chamber pumps down");}
        var isolated=consumers.getFirst();isolated.toggleIsolation();long before=isolated.buffer().getAmountAsLong();isolated.serverTick();eq(h,before-200,isolated.buffer().getAmountAsLong(),"Isolation returns to dedicated pump");
    }h.succeed();}

    private static void scannerRepair(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.scanner();var source=arena.source();var room=prepareLine(arena,cell,source);var scanner=(EuvScannerControllerBlockEntity)cell.machine();align(scanner);var oldBeam=scanner.alignment();var oldFit=scanner.diagnostic();scanner.setSlot(0,lot(2,true));var mask=new ItemStack(ModItems.MASK.get());mask.set(ModDataComponents.MASK_DATA.get(),new MaskData("memory",7,5,3));scanner.setSlot(1,mask);scanner.setSlot(2,new ItemStack(ModItems.RESIST_T7.get(),64));scanner.serverTick();check(h,scanner.inputsLocked(),"Actual T7 run admitted before breach");
        scanner.setStoredEnergy(0);scanner.serverTick();check(h,scanner.inputsLocked(),"No-power run stays active");room.open();scanner.serverTick();var damaged=FoupItems.trustedContentsOf(scanner.slot(0));check(h,damaged!=null&&damaged.isContaminated()&&!scanner.inputsLocked()&&scanner.contaminationState().requiresRepair(),"Physical breach scraps power-paused lot and dirties scanner");eq(h,5,scanner.slot(1).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),"Breach never spends completed mask use");
        var dirtySave=save(arena.level,scanner);load(arena.level,scanner,dirtySave);scanner.revalidate();check(h,scanner.contaminationState().requiresRepair(),"Equipment condition survives reload");room.seal();scanner.clearSlot(0);scanner.serverTick();scanner.repairItems.setItem(0,new ItemStack(ModItems.UTILITY_FILTER.get(),2));scanner.repairItems.setItem(1,new ItemStack(Items.REDSTONE,16));scanner.upwTank.fill(32000,false);scanner.setStoredEnergy(200000);check(h,scanner.repair(),"Actual scanner repair bill paid");eq(h,24000,scanner.upwTank.amount(),"Exact scanner repair UPW");eq(h,0,scanner.buffer().getAmountAsLong(),"Exact scanner repair FE");check(h,scanner.repairItems.isEmpty()&&scanner.contaminationState().requiresRecalibration(),"Paid repair requires both new calibrations");
        var beam=scanner.alignment();var fit=scanner.diagnostic();check(h,beam.targetX()!=oldBeam.targetX()||beam.targetY()!=oldBeam.targetY()||beam.targetFocus()!=oldBeam.targetFocus(),"Beam target changed");check(h,fit.targetA()!=oldFit.targetA()||fit.targetB()!=oldFit.targetB()||fit.targetC()!=oldFit.targetC(),"Quadratic target changed");check(h,new BeamAlignment(oldBeam.x(),oldBeam.y(),oldBeam.focus(),beam.targetX(),beam.targetY(),beam.targetFocus()).quality()<scanner.minimumQuality(),"Old beam solution rejected");check(h,fit.plot(oldFit.a(),oldFit.b(),oldFit.c()).quality()<scanner.diagnosticMinimum(),"Old diagnostic solution rejected");scanner.setStoredEnergy(4000000);align(scanner);check(h,scanner.contaminationState().isOperational(),"Both accepted new calibrations finish recovery");
    }h.succeed();}
    private static void pairing(GameTestHelper h){try(var arena=new Arena(h)){
        var first=arena.scanner();var source=arena.source();prepareLine(arena,first,source);var a=(EuvScannerControllerBlockEntity)first.machine();var light=(EuvLightSourceControllerBlockEntity)source.machine();try(var tx=Transaction.openRoot()){check(h,light.payExposure(a,tx),"First actual source exposure reserves pairing");tx.commit();}
        // A second complete scanner shares the source's opposite shell face, with its exposure module on the west edge.
        var second=arena.box(arena.anchor().offset(16,0,0),5,5,7,EuvMachines.SCANNER_CASING.get(),EuvMachines.SCANNER_CONTROLLER.get(),EuvMachines.SCANNER_OPERATING_PANEL.get(),Map.ofEntries(Map.entry(new BlockPos(1,0,0),EuvMachines.SCANNER_POWER_PORT.get()),Map.entry(new BlockPos(0,1,1),EuvMachines.SCANNER_EXPOSURE_MODULE.get()),Map.entry(new BlockPos(4,1,1),EuvMachines.SCANNER_OPTICS_MODULE.get()),Map.entry(new BlockPos(1,1,1),EuvMachines.SCANNER_STAGE_MODULE.get()),Map.entry(new BlockPos(2,1,1),EuvMachines.SCANNER_FILTRATION_MODULE.get()),Map.entry(new BlockPos(3,1,1),EuvMachines.SCANNER_METROLOGY_MODULE.get()),Map.entry(new BlockPos(0,1,2),EuvMachines.SCANNER_VACUUM_MODULE.get()),Map.entry(new BlockPos(1,1,2),EuvMachines.SCANNER_DIAGNOSTIC_PANEL.get()),Map.entry(new BlockPos(2,1,2),EuvMachines.SCANNER_WATER_INLET.get()),Map.entry(new BlockPos(3,1,2),EuvMachines.SCANNER_CALIBRATION_PANEL.get())));for(int x=12;x<=15;x++)arena.put(arena.anchor().offset(x,1,1),EuvMachines.BEAM_LINE.get());var b=(EuvScannerControllerBlockEntity)second.machine();arena.put(source.anchor().offset(2,1,1),EuvMachines.LIGHT_SOURCE_CASING.get());arena.put(source.anchor().offset(2,1,3),EuvMachines.LIGHT_SOURCE_HYDROGEN_INLET.get());light.revalidate();check(h,b.isFormed()&&!light.canExpose(b),"One source refuses a second connected scanner");
        var tag=save(arena.level,light);arena.level.removeBlockEntity(source.anchor());light=new EuvLightSourceControllerBlockEntity(source.anchor(),arena.level.getBlockState(source.anchor()));arena.level.setBlockEntity(light);load(arena.level,light,tag);light.revalidate();check(h,light.canExpose(a)&&!light.canExpose(b),"Saved installation identity preserves reservation");eq(h,999,light.tinCreditTicks(),"Tin credit survives reload");arena.put(arena.anchor().offset(6,1,1),Blocks.AIR);check(h,!light.canExpose(a)&&light.canExpose(b),"Disconnected beamline permits deliberate reassignment");try(var tx=Transaction.openRoot()){check(h,light.payExposure(b,tx),"New valid scanner can claim released physical route");tx.commit();}eq(h,998,light.tinCreditTicks(),"Reassignment does not mint tin credit");
    }h.succeed();}
    private static void menus(GameTestHelper h){try(var arena=new Arena(h)){
        var cell=arena.scanner();var scanner=(EuvScannerControllerBlockEntity)cell.machine();var p=player(h,cell.anchor());var equipment=new EuvScannerEquipmentMenu(1,p.getInventory(),scanner,cell.anchor());check(h,equipment.stillValid(p)&&!equipment.clickMenuButton(p,1),"Unopened cabinet cannot invoke remote controls");p.containerMenu=equipment;check(h,equipment.clickMenuButton(p,1)&&scanner.vacuumIsolated(),"Exact open cabinet authority");equipment.slots.get(0).set(new ItemStack(ModItems.UTILITY_FILTER.get(),2));eq(h,2,scanner.repairItems.getItem(0).getCount(),"GUI repair slot writes saved real inventory");var snapshot=save(arena.level,scanner);load(arena.level,scanner,snapshot);scanner.revalidate();eq(h,2,scanner.repairItems.getItem(0).getCount(),"Maintenance slot survives persistence");
        var calibration=arena.block(cell,EuvMachines.SCANNER_CALIBRATION_PANEL.get());p.setPos(calibration.getX()+.5,calibration.getY()+.5,calibration.getZ()+.5);var menu=new EuvScannerCalibrationMenu(2,p.getInventory(),scanner,calibration);check(h,!menu.clickMenuButton(p,0),"Other open menu cannot control beam");p.containerMenu=menu;check(h,menu.clickMenuButton(p,0),"Correct physical calibration panel operates");p.setPos(calibration.getX()+30,calibration.getY(),calibration.getZ());check(h,!menu.clickMenuButton(p,1),"Remote calibration packet rejected");
    }h.succeed();}

    private static void packaging(GameTestHelper h){
        for(String family:List.of("compute","memory","nand","high"))try(var arena=new Arena(h)){
            boolean high=family.equals("high");int tier=high?6:7;String die=high?"compute":family;var node=com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry().getByIndex(tier);
            var cell=arena.box(arena.anchor(),2,2,3,com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines.ADVANCED_CASING.get(),com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines.ADVANCED_TEST_PACKAGE_CONTROLLER.get(),com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines.ADVANCED_OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines.ADVANCED_POWER_PORT.get()));
            var machine=(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedTestPackageControllerBlockEntity)cell.machine();var stack=lot(1,!high);var contents=FoupItems.trustedContentsOf(stack);if(high)contents=contents.withPreparationGeneration(2);contents=contents.withProcessedWafers(List.of(new Wafer(node.layers(),1f,0))).withRun(new RunSpec(die,tier,die.equals("compute")?8:0,.85,node.layers()));stack.set(ModDataComponents.FOUP_CONTENTS.get(),contents);machine.setSlot(0,stack);machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));if(high)check(h,machine.toggleHighBinning(),"T6 high-binning toggle available");machine.setStoredEnergy(4000000);machine.serverTick();check(h,!machine.isQueueActive(),"T7 target refuses outside enclosure");check(h,FoupItems.trustedContentsOf(machine.slot(0)).size()==1,"Refusal preserves wafer");arena.room(cell,2,2,3);
            for(int i=0;i<5000&&machine.slot(2).isEmpty();i++){machine.setStoredEnergy(4000000);machine.serverTick();}var chip=machine.slot(2);check(h,chip.is(ChipCatalog.chipFor(die,7).orElseThrow()),"Native packaging creates T7 "+family);if(die.equals("compute"))eq(h,8,chip.getOrDefault(ModDataComponents.CORE_COUNT.get(),0),"Eight-core metadata survives normal/high-bin T7 packaging");
        }h.succeed();
    }
    private T7RuntimeGameTests(){}



}
