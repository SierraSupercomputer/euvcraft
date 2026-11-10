package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.FabProgress;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.multiblock.linear.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.routing.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import java.util.*;

/** Explicit paid-stage seeds for legacy scanner-only probes; full chains use real operations below. */
final class LaterFabFixtures {
    static CoatingReceipt legacyScannerBudget(int tier){
        var t=TierDataLoader.getRegistry().getByIndex(tier);var settings=EUVCRAFTMod.settings();int passes=tier==6?2:1;
        return new CoatingReceipt(tier,t.layers(),passes,settings.resistPerLayer(),t.layerTicks(settings)*passes,1,1,1,tier==7?960:tier==6?480:240,0,1);
    }
    static ItemStack paidCoatingFixture(ItemStack stack,int tier){
        var copy=stack.copy();copy.set(ModDataComponents.COATING_RECEIPT.get(),legacyScannerBudget(tier));copy.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(new FabProgress(FabProgress.Stage.COATED,tier,1),"",0,false));FoupRouting.refresh(copy);return copy;
    }
    static ItemStack completedRouteFixture(ItemStack stack){
        var copy=stack.copy();var lot=FoupItems.trustedContentsOf(copy);var run=lot.run();if(run==null||run.tierIndex()<5)return copy;
        copy.set(ModDataComponents.COATING_RECEIPT.get(),legacyScannerBudget(run.tierIndex()));copy.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(new FabProgress(FabProgress.Stage.CLEANED,run.tierIndex(),1),run.dieType(),run.cores(),lot.isContaminated()));FoupRouting.refresh(copy);return copy;
    }
    static T7RuntimeFixtures.Cell stage(T7RuntimeFixtures.Arena arena,boolean coating){
        return arena.box(arena.anchor(),coating?3:2,2,coating?5:3,AdvancedMachines.ADVANCED_CASING.get(),coating?LinearMachines.COATING_TRACK.get():LinearMachines.WET_CLEANER.get(),AdvancedMachines.ADVANCED_OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),AdvancedMachines.ADVANCED_POWER_PORT.get(),new BlockPos(0,0,1),LinearMachines.UPW_INLET.get()));
    }
    record Operation(ItemStack output,long energy,int water,int resist,int ticks){}
    static Operation process(GameTestHelper h,ItemStack input,int tier,boolean coating){
        try(var arena=new LaterArena(h)){
            var c=stage(arena,coating);var m=(LinearStageBlockEntity)c.machine();T7RuntimeFixtures.Room room=null;if(tier>=7){room=arena.room(c,coating?3:2,2,coating?5:3);room.controller().setStoredEnergy(200000);room.controller().serverTick();}
            m.setSlot(0,input.copy());int supplied=0,water=0,ticks=0;long energy=0;int remaining=coating?TierDataLoader.getRegistry().getByIndex(tier).layers()*EUVCRAFTMod.settings().resistPerLayer():0;
            for(;ticks<100000&&m.slot(3).isEmpty();ticks++){
                if(room!=null){room.controller().setStoredEnergy(200000);room.controller().serverTick();}
                if(coating&&m.slot(1).isEmpty()&&supplied<remaining){int n=Math.min(64,remaining-supplied);m.setSlot(1,new ItemStack(resist(tier),n));supplied+=n;}
                m.upw.fill(m.upw.capacity()-m.upw.amount(),false);int beforeWater=m.upw.amount();replenish(m);int before=m.buffer().getAmountAsInt();m.serverTick();energy+=before-m.buffer().getAmountAsInt();water+=beforeWater-m.upw.amount();
            }
            check(h,!m.slot(3).isEmpty(),"Real "+(coating?"coating":"wet-cleaning")+" completes tier "+tier+" status "+m.stageData().get(0));
            var output=m.slot(3).copy();m.clearSlot(3);return new Operation(output,energy,water,supplied-m.slot(1).getCount(),ticks);
        }
    }
    static ItemStack deposited(GameTestHelper h,WaferSubstrate grade){try(var c=build(h,Kind.DEPOSITION)){var m=(DepositionEtcherControllerBlockEntity)c.machine();m.setSlot(0,fresh(3,grade));m.setSlot(1,new ItemStack(ModItems.SILICON.get(),2));runPreparation(h,m);return m.slot(2).copy();}}
    static ItemStack etched(GameTestHelper h,ItemStack input){try(var c=build(h,Kind.ETCH)){var m=(EtchCellBlockEntity)c.machine();m.setSlot(0,input.copy());m.setSlot(1,new ItemStack(ModItems.ETCHANT_CHARGE.get()));runPreparation(h,m);return m.slot(2).copy();}}
    static ItemStack coated(GameTestHelper h,ItemStack input,int tier){return process(h,input,tier,true).output();}
    static ItemStack cleaned(GameTestHelper h,ItemStack input,int tier){return process(h,input,tier,false).output();}
    static ItemStack g3(GameTestHelper h,ItemStack input,boolean etch){
        try(var arena=new LaterArena(h)){
            var c=arena.prep();if(etch)arena.oriented(c.anchor(),LinearMachines.G3_ETCH.get());
            var m=(com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlockEntity)arena.level.getBlockEntity(c.anchor());m.revalidate();var room=arena.room(c,5,5,7);
            m.setSlot(0,input.copy());
            if(etch){
                var cell=(G3EtchCellBlockEntity)m;
                var family=FoupItems.trustedContentsOf(input).run().dieType();
                var next=input.get(ModDataComponents.FOUP_NEXT_STEPS.get());
                var modulePos=c.anchor().offset(0,1,1);
                m.setSlot(1,new ItemStack(ModItems.ETCHANT_CHARGE.get()));
                room.controller().setStoredEnergy(200000);
                room.controller().serverTick();
                replenish(m);
                if(!family.equals(DieTypes.NAND)){
                    var block=family.equals(DieTypes.COMPUTE)
                            ?com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.COMPUTE_ETCH_MODULE.get()
                            :com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.MEMORY_ETCH_MODULE.get();
                    var wrong=family.equals(DieTypes.COMPUTE)
                            ?com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.MEMORY_ETCH_MODULE.get()
                            :com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.COMPUTE_ETCH_MODULE.get();
                    check(h,next.containsKey("g3_etch_"+family),"FOUP names the required family setup");
                    check(h,!m.startPrepJob(),"Standard module refuses a specialty lot");
                    arena.put(modulePos,wrong);
                    m.revalidate();
                    check(h,m.isFormed()&&!m.canReceiveCarrier(input)&&!m.startPrepJob(),"Other family module refuses manual and automated admission");
                    arena.put(modulePos,block);
                    m.revalidate();
                    check(h,m.isFormed()&&!m.startPrepJob(),"Matching module requires accepted tuning");
                    check(h,m.slot(1).getCount()==1&&!m.slot(0).isEmpty(),"Rejected starts preserve inputs");
                    for(int attempts=0;cell.publicData(20)!=0&&attempts<16;attempts++){
                        check(h,cell.tune(-Integer.signum(cell.publicData(20))),"Adjust specialty trim");
                    }
                    check(h,cell.publicData(20)==0&&cell.tune(0),"Accept zero-error specialty calibration");
                    check(h,m.startPrepJob()&&!cell.tune(1),"Accepted lot freezes tuning and locks controls");
                    arena.put(modulePos,wrong);
                    m.serverTick();
                    check(h,m.publicData(12)==0&&m.publicData(2)==1,"An active lot pauses when refitted for the other family");
                    arena.put(modulePos,block);
                    m.revalidate();
                    check(h,cell.publicData(22)==0,"A refit clears tuning for the next lot");
                }else{
                    check(h,next.containsKey("g3_etch_cell"),"NAND retains the standard etch destination");
                }
            }
            for(int i=0;i<5000&&m.slot(3).isEmpty();i++){room.controller().setStoredEnergy(200000);room.controller().serverTick();replenish(m);m.serverTick();}
            check(h,!m.slot(3).isEmpty(),"Real G3 "+(etch?"etch":"deposition")+" finishes");var result=m.slot(3).copy();m.clearSlot(3);return result;
        }
    }
    static ItemStack t8Stage(GameTestHelper h,ItemStack input,boolean planarizer,boolean specialty){
        try(var arena=new LaterArena(h)){
            var head=planarizer?com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.MEMORY_UNIFORMITY_HEAD.get():com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.COMPUTE_DOSE_HEAD.get();
            if(!specialty)head=com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.DEPOSITION_MODULE.get();
            var c=arena.box(arena.anchor(),planarizer?3:5,3,planarizer?5:7,com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.CASING.get(),
                planarizer?LinearMachines.PLANARIZER.get():LinearMachines.IMPLANTER.get(),com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.OPERATING_PANEL.get(),Map.of(
                    new BlockPos(1,0,0),com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.POWER_PORT.get(),
                    new BlockPos(0,1,1),head,
                    new BlockPos(1,1,1),com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.RINSE_MODULE.get(),
                    new BlockPos(2,1,1),com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.VACUUM_MODULE.get(),
                    new BlockPos(0,1,2),com.euvcraft.lithmod.mc.multiblock.g3.G3Machines.WATER_INLET.get()));
            var machine=(T8StageBlockEntity)c.machine();
            check(h,machine.isFormed(),"T8 stage forms with required shape and head");
            machine.setSlot(0,input.copy());
            machine.setSlot(1,new ItemStack(planarizer?ModItems.CMP_SLURRY.get():ModItems.DOPANT_CHARGE.get(),2));
            if(planarizer)machine.setSlot(2,new ItemStack(ModItems.POLISH_PAD.get(),2));
            machine.waterTank().fill(16000,false);replenish(machine);
            check(h,!machine.startPrepJob(),"T8 processing requires a cleanroom");
            var room=arena.room(c,planarizer?3:5,3,planarizer?5:7);
            room.controller().setStoredEnergy(200000);room.controller().serverTick();
            if(planarizer){
                check(h,!machine.startPrepJob(),"Planarization requires accepted polish calibration");
                calibratePolish(h,machine,input);
                var snapshot=save(arena.level,machine);
                arena.level.removeBlockEntity(c.anchor());
                machine=new T8StageBlockEntity(c.anchor(),arena.level.getBlockState(c.anchor()),true);
                arena.level.setBlockEntity(machine);
                load(arena.level,machine,snapshot);
                machine.revalidate();
                eq(h,1000,machine.publicData(31),"Accepted polish survives cabinet reload");
                var lastSample=FoupItems.trustedContentsOf(machine.polishSample().getItem(0));
                eq(h,1,lastSample.size(),"Remaining sample survives cabinet reload");
                check(h,machine.polishCommand(14),"Last sample wafer can be tested");
                var emptySample=machine.polishSample().getItem(0);
                check(h,FoupItems.isFoup(emptySample)&&lastSample.withoutWaferAt(0).equals(FoupItems.trustedContentsOf(emptySample)),"Last trial returns the original empty carrier");
                int before=machine.buffer().getAmountAsInt();
                check(h,!machine.polishCommand(14),"An empty sample cannot test again");
                eq(h,before,machine.buffer().getAmountAsInt(),"Empty sample costs no energy");
            }else{
                check(h,!machine.startPrepJob(),"Implantation requires accepted dose calibration");
                while(machine.publicData(20)!=0)check(h,machine.tuneDose(-Integer.signum(machine.publicData(20))),"Adjust implant dose");
                check(h,machine.tuneDose(0),"Accept zero-error implant dose");
            }
            check(h,machine.startPrepJob(),"T8 stage admits the correct lot");
            if(planarizer)check(h,!machine.polishCommand(com.euvcraft.lithmod.mc.multiblock.g3.PlanarizerCabinetMenu.PRESSURE_UP),"Admitted polish locks its settings");
            for(int i=0;i<5000&&machine.slot(3).isEmpty();i++){
                room.controller().setStoredEnergy(200000);room.controller().serverTick();replenish(machine);machine.serverTick();
            }
            check(h,!machine.slot(3).isEmpty(),"T8 stage finishes all paid phases");
            eq(h,1,machine.slot(1).getCount(),"Reagent consumed exactly once");
            if(planarizer)eq(h,1,machine.slot(2).getCount(),"Pad consumed exactly once");
            var result=machine.slot(3).copy();
            machine.clearSlot(3);
            if(planarizer){
                var headPos=c.anchor().offset(0,1,1);
                arena.put(headPos,net.minecraft.world.level.block.Blocks.AIR);
                arena.put(headPos,head);
                machine.revalidate();
                eq(h,0,machine.publicData(31),"Replacing even the same head block resets calibration");
            }
            return result;
        }
    }
    private static void calibratePolish(GameTestHelper h,T8StageBlockEntity machine,ItemStack input){
        var original=FoupItems.trustedContentsOf(input);
        var sample=new FoupContents(java.util.UUID.randomUUID(),original.capacity(),original.wafers(),null,original.grade(),
                original.preparationGeneration(),original.roomExposure(),original.roomYieldPermille(),original.g3Preparation());
        machine.polishSample().setItem(0,FoupItems.with(input.getItem(),sample));
        check(h,!machine.polishCommand(15),"Untested settings cannot be accepted");
        machine.setStoredEnergy(0);
        check(h,!machine.polishCommand(14),"Unpowered trial refused");
        check(h,sample.equals(FoupItems.trustedContentsOf(machine.polishSample().getItem(0))),"Refused trial preserves every sample wafer");
        replenish(machine);
        int energy=machine.buffer().getAmountAsInt();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"polish-test"),false);
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var pos=machine.getBlockPos();
        player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var menu=new com.euvcraft.lithmod.mc.multiblock.g3.PlanarizerCabinetMenu(1,player.getInventory(),machine);
        check(h,menu.getSlot(0).mayPlace(machine.polishSample().getItem(0))&&!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),
                "Sample slot accepts a trusted deposited FOUP");
        check(h,!menu.clickMenuButton(player,14),"Closed cabinet cannot spend a sample");
        player.containerMenu=menu;
        check(h,menu.clickMenuButton(player,14),"Paid cabinet test consumes one wafer");
        player.containerMenu=player.inventoryMenu;
        eq(h,1500,energy-machine.buffer().getAmountAsInt(),"Only the configured trial FE is charged");
        var afterTrial=FoupItems.trustedContentsOf(machine.polishSample().getItem(0));
        check(h,afterTrial.equals(sample.withoutWaferAt(0)),"Trial preserves remaining sample identity and G3 factors");
        int removal=machine.publicData(28),edge=machine.publicData(29);
        adjustPolish(h,machine,(2*removal+edge)/5,10,11);
        adjustPolish(h,machine,(removal-2*edge)/5,12,13);
        check(h,machine.polishCommand(14),"Retest corrected settings");
        eq(h,1000,machine.publicData(30),"Zero-error polish scores perfectly");
        check(h,machine.polishCommand(15),"Accept successful trial");
        check(h,afterTrial.withoutWaferAt(0).equals(FoupItems.trustedContentsOf(machine.polishSample().getItem(0))),"Each successful trial spends exactly one wafer");
    }

    private static void adjustPolish(GameTestHelper h,T8StageBlockEntity machine,int error,int decrease,int increase){
        for(int step=0;step<Math.abs(error);step++)
            check(h,machine.polishCommand(error>0?decrease:increase),"Adjust polish control");
    }

    static Item resist(int tier){return switch(tier){case 5->ModItems.RESIST_T5.get();case 6->ModItems.RESIST_T6.get();case 8->ModItems.RESIST_T8.get();default->ModItems.RESIST_T7.get();};}
    private LaterFabFixtures(){}
}
