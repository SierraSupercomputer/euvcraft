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
        try(var arena=new T7RuntimeFixtures.Arena(h)){
            var c=stage(arena,coating);var m=(LinearStageBlockEntity)c.machine();T7RuntimeFixtures.Room room=null;if(tier==7){room=arena.room(c,coating?3:2,2,coating?5:3);room.controller().setStoredEnergy(200000);room.controller().serverTick();}
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
        try(var arena=new T7RuntimeFixtures.Arena(h)){
            var c=arena.prep();if(etch)arena.oriented(c.anchor(),LinearMachines.G3_ETCH.get());
            var m=(com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlockEntity)arena.level.getBlockEntity(c.anchor());m.revalidate();var room=arena.room(c,5,5,7);
            m.setSlot(0,input.copy());if(etch)m.setSlot(1,new ItemStack(ModItems.ETCHANT_CHARGE.get()));
            for(int i=0;i<5000&&m.slot(3).isEmpty();i++){room.controller().setStoredEnergy(200000);room.controller().serverTick();replenish(m);m.serverTick();}
            check(h,!m.slot(3).isEmpty(),"Real G3 "+(etch?"etch":"deposition")+" finishes");var result=m.slot(3).copy();m.clearSlot(3);return result;
        }
    }
    static Item resist(int tier){return switch(tier){case 5->ModItems.RESIST_T5.get();case 6->ModItems.RESIST_T6.get();default->ModItems.RESIST_T7.get();};}
    private LaterFabFixtures(){}
}
