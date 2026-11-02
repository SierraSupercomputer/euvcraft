package com.euvcraft.lithmod.mc.multiblock.linear;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.routing.*;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import org.jspecify.annotations.Nullable;

/** One reserved lot, one frozen paid budget, one completion. No new yield roll. */
public abstract class LinearStageBlockEntity extends AdvancedControllerBlockEntity {
    public enum Status { IDLE,UNFORMED,NO_FOUP,WRONG_STAGE,NO_RESIST,NO_WATER,NO_POWER,WORKING,OUTPUT_BLOCKED,CONFIG,NO_ROOM,REPAIR,RECOVERY,COATING,BAKING,RINSING,DRYING }
    private ItemStack carrier=ItemStack.EMPTY;
    private @Nullable CoatingReceipt budget;
    private int progress,resistPaid,waterPaid;
    private boolean recovery;
    private List<BlockPos> footprint=List.of();
    private MachineContaminationState condition=MachineContaminationState.clean();
    public final WaterTank upw=new WaterTank(32000);
    final StageWaterJournal waterJournal=new StageWaterJournal(this);
    private final ContainerData stageData=new SimpleContainerData(4);
    protected LinearStageBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state,3,1);}
    public abstract boolean coating();
    public ContainerData stageData(){return stageData;}
    public boolean hasJob(){return !carrier.isEmpty();}
    public boolean requiresRepair(){return condition.requiresRepair();}
    public int stageProgress(){return progress;}
    public int stageDuration(){return budget==null?0:coating()?Math.max(1,budget.coatTicks()):Math.max(1,budget.totalCleanTicks());}
    public boolean acceptsReagent(ItemStack stack){return coating()&&ResistCatalog.tierOf(stack)>=5&&ResistCatalog.tierOf(stack)<=7||requiresRepair()&&stack.is(ModItems.UTILITY_FILTER.get());}
    private int param(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
    private int scaled(int fe){return (int)Math.clamp(Math.round(fe*EUVCRAFTMod.settings().fePowerMultiplier()),1,Integer.MAX_VALUE);}
    @Override public boolean large(){return coating();}
    @Override public com.euvcraft.lithmod.core.multiblock.MatchResult matchStructure(com.euvcraft.lithmod.core.multiblock.MultiblockPos anchor,com.euvcraft.lithmod.core.multiblock.MultiblockDirection facing,java.util.function.Function<com.euvcraft.lithmod.core.multiblock.MultiblockPos,com.euvcraft.lithmod.core.multiblock.BlockKind> lookup){
        var shape=coating()?com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.COATER_3x2x5:com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.WET_CLEANER_2x2x3;
        var result=com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.match(anchor,facing,shape,!coating(),lookup);
        return result.success()||!coating()?result:com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.match(anchor,facing,shape,true,lookup);
    }
    public boolean canReceiveCarrier(ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||!FoupItems.isFoup(stack)||!FoupRouting.matchesPrivateLot(stack))return false;
        var lot=FoupItems.trustedContentsOf(stack);var route=stack.get(ModDataComponents.FOUP_ROUTING.get());
        if(lot.isEmpty()||lot.isContaminated())return false;
        return coating()?lot.run()==null&&route.progress().stage()==FabProgress.Stage.DEPOSITED:
            lot.run()!=null&&route.progress().stage()==FabProgress.Stage.ETCHED&&route.progress().tier()>=5&&lot.roomYieldPermille()==0&&stack.has(ModDataComponents.COATING_RECEIPT.get());
    }
    private @Nullable CoatingReceipt recipe(){
        if(!canReceiveCarrier(slot(0)))return null;
        if(!coating()){
            var receipt=slot(0).get(ModDataComponents.COATING_RECEIPT.get());var route=slot(0).get(ModDataComponents.FOUP_ROUTING.get());var lot=FoupItems.trustedContentsOf(slot(0));
            return receipt!=null&&receipt.tier()==route.progress().tier()&&lot.lotLayer()==receipt.layers()?receipt:null;
        }
        int index=ResistCatalog.tierOf(slot(1));if(index<5||index>7)return null;
        var tier=TierDataLoader.getRegistry().byIndex(index).orElse(null);var matrix=SupportLineDataLoader.getMatrix();var lot=FoupItems.trustedContentsOf(slot(0));
        if(tier==null||matrix==null||lot.grade().generation()<matrix.requiredGeneration(SupportLines.SUBSTRATE,tier)||lot.preparationGeneration()<matrix.requiredGeneration(SupportLines.DEPOSITION_ETCH,tier))return null;
        var scanner=MachineDefinitionLoader.getRegistry().byId(index==7?"euv_scanner":"lithography_scanner").orElseThrow();
        int passes=index==6?scanner.paramOrDefault("multipattern_passes",2):1;
        int oldWater=index==7?scanner.paramOrDefault("linear_original_upw_per_tick",5):scanner.paramOrDefault("linear_original_upw_per_tick",2);
        int newWater=index==7?scanner.paramOrDefault("linear_upw_per_tick_mb",scanner.paramOrDefault("upw_per_tick_mb",5)):scanner.paramOrDefault("linear_upw_per_tick_mb",scanner.paramOrDefault("pure_water_per_tick_mb",2));
        var cleaner=MachineDefinitionLoader.getRegistry().byId("wet_cleaner").orElseThrow();
        var plan=LinearBudget.calculate(tier.layers(),tier.layerTicks(EUVCRAFTMod.settings()),passes,EUVCRAFTMod.settings().resistPerLayer(),oldWater,newWater,param("coat_ticks_per_layer",5),cleaner.paramOrDefault("clean_ticks_per_layer",5),index==7?cleaner.paramOrDefault("t7_rinse_water_mb",4000):0);
        return new CoatingReceipt(index,tier.layers(),passes,EUVCRAFTMod.settings().resistPerLayer(),plan,scanner.energyPerTick(tier,EUVCRAFTMod.settings()),index==7?cleaner.paramOrDefault("t7_rinse_duration",200):0,index==7?scaled(cleaner.paramOrDefault("t7_rinse_energy_per_tick",480)):1,newWater);
    }
    private CleanroomRooms.Sample room(){return CleanroomRooms.sample((ServerLevel)level,footprint.isEmpty()?members():footprint);}
    private boolean enclosed(CleanroomRooms.Sample sample){return sample.available()&&sample.room()!=null&&sample.room().state()==CleanroomControllerBlockEntity.ENCLOSED;}
    private void status(Status status){stageData.set(0,status.ordinal());stageData.set(1,upw.amount());stageData.set(2,upw.capacity());stageData.set(3,requiresRepair()?1:0);data().set(DATA_PROGRESS,progress);data().set(DATA_TICKS,stageDuration());}
    @Override public void serverTick(){
        super.serverTick();if(!(level instanceof ServerLevel)||isRemoved()||level.getBlockEntity(worldPosition)!=this)return;
        upw.resize(Math.clamp(param("water_capacity",32000),1000,WaterTank.MAX_CAPACITY));
        if(!carrier.isEmpty()&&budget!=null&&budget.tier()==7&&!recovery){var sample=room();if(!sample.available()){status(Status.NO_ROOM);return;}if(!enclosed(sample)){condition=MachineContaminationState.contaminated(level.getGameTime());carrier.set(ModDataComponents.FOUP_CONTENTS.get(),FoupItems.trustedContentsOf(carrier).contaminated());FoupRouting.refresh(carrier);recovery=true;setChanged();}}
        if(recovery){if(slot(3).isEmpty()){setSlot(3,carrier);carrier=ItemStack.EMPTY;budget=null;footprint=List.of();recovery=false;setChanged();}status(requiresRepair()?Status.REPAIR:Status.RECOVERY);return;}
        if(!isFormed()){status(Status.UNFORMED);return;}if(requiresRepair()){status(Status.REPAIR);return;}if(definition().isEmpty()){status(Status.CONFIG);return;}
        if(carrier.isEmpty()){
            if(!slot(3).isEmpty()){status(Status.OUTPUT_BLOCKED);return;}if(slot(0).isEmpty()){status(Status.NO_FOUP);return;}if(!canReceiveCarrier(slot(0))){status(Status.WRONG_STAGE);return;}
            if(coating()&&(ResistCatalog.tierOf(slot(1))<5||ResistCatalog.tierOf(slot(1))>7)){status(Status.NO_RESIST);return;}
            CoatingReceipt next;try{next=recipe();}catch(RuntimeException invalid){status(Status.CONFIG);return;}
            if(next==null){status(Status.WRONG_STAGE);return;}
            if(next.energyPerTick()>buffer().getCapacityAsInt()||next.rinseEnergyPerTick()>buffer().getCapacityAsInt()){status(Status.CONFIG);return;}
            int ticks=coating()?Math.max(1,next.coatTicks()):Math.max(1,next.totalCleanTicks());
            if(coating()&&((long)next.totalResist()+ticks-1)/ticks>64||!coating()&&((long)next.cleanWaterMb()+ticks-1)/ticks>upw.capacity()){status(Status.CONFIG);return;}
            var sample=CleanroomRooms.sample((ServerLevel)level,members());if(!sample.available()||next.tier()==7&&!enclosed(sample)){status(Status.NO_ROOM);return;}
            budget=next;carrier=slot(0).copy();clearSlot(0);footprint=List.copyOf(members());progress=resistPaid=waterPaid=0;setChanged();
        }
        if(!slot(3).isEmpty()){status(Status.OUTPUT_BLOCKED);return;}
        var sample=room();if(!sample.available()){status(Status.NO_ROOM);return;}
        int ticks=stageDuration(),nextProgress=progress+1;
        int needResist=coating()?(int)((long)nextProgress*budget.totalResist()/ticks)-resistPaid:0;
        int needWater=coating()?0:(int)((long)nextProgress*budget.cleanWaterMb()/ticks)-waterPaid;
        if(needResist>0&&(ResistCatalog.tierOf(slot(1))!=budget.tier()||slot(1).getCount()<needResist)){status(Status.NO_RESIST);return;}
        if(upw.amount()<needWater){status(Status.NO_WATER);return;}
        int cost=!coating()&&progress<budget.rinseTicks()?budget.rinseEnergyPerTick():budget.energyPerTick();
        if(energySource().draw(cost)!=cost){status(Status.NO_POWER);return;}
        if(needResist>0){slot(1).shrink(needResist);if(slot(1).isEmpty())clearSlot(1);resistPaid+=needResist;}
        if(needWater>0){upw.drainExact(needWater,false);waterPaid+=needWater;}
        var lot=FoupItems.trustedContentsOf(carrier);carrier.set(ModDataComponents.FOUP_CONTENTS.get(),lot.withRoomExposure(lot.roomExposure().sample(sample.yieldPermille()),0));progress=nextProgress;setChanged();status(coating()?(progress<=ticks/2?Status.COATING:Status.BAKING):(progress<=budget.rinseTicks()?Status.RINSING:Status.DRYING));
        if(progress==ticks){
            var route=carrier.get(ModDataComponents.FOUP_ROUTING.get());
            if(coating()){carrier.set(ModDataComponents.COATING_RECEIPT.get(),budget);carrier.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(route.progress().coated(budget.tier()),"",0,false));}
            else {var current=TierDataLoader.getRegistry().byIndex(budget.tier()).orElseThrow();var tier=new Tier(current.id(),current.index(),current.displayName(),current.lightSource(),current.resolutionNm(),current.overlayNm(),current.throughputWph(),budget.layers(),current.layerTicks(),current.defaultYieldPercent(),current.targetSpeedMhz(),current.comfortableCores(),current.optional());carrier.set(ModDataComponents.FOUP_CONTENTS.get(),CleanroomYield.finish(FoupItems.trustedContentsOf(carrier),tier));carrier.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(route.progress().cleaned(),route.dieType(),route.cores(),false));}
            FoupRouting.refresh(carrier);setSlot(3,carrier);carrier=ItemStack.EMPTY;budget=null;footprint=List.of();progress=0;setChanged();
        }
    }
    public boolean repair(){
        if(!(level instanceof ServerLevel)||!requiresRepair()||hasJob()||!isFormed()||!enclosed(room()))return false;
        int filters=param("repair_filter_medium",2),redstone=param("repair_redstone",16),water=param("repair_upw_mb",8000),energy=scaled(param("repair_fe",200000));
        if(filters<1||redstone<1||water<1||!slot(1).is(ModItems.UTILITY_FILTER.get())||slot(1).getCount()<filters||!slot(2).is(Items.REDSTONE)||slot(2).getCount()<redstone||upw.amount()<water||buffer().getAmountAsLong()<energy)return false;
        if(energySource().draw(energy)!=energy)return false;upw.drainExact(water,false);slot(1).shrink(filters);slot(2).shrink(redstone);condition=condition.repair(new ContaminationRepairCost(filters+redstone,water,energy)).state().recalibrate().state();setChanged();return true;
    }
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity ignored){return new DepositionEtcherMenu(id,inventory,this);}
    @Override protected void dropExtraContents(@Nullable Level world,BlockPos pos){dropContainer(world,pos,new SimpleContainer(carrier));carrier=ItemStack.EMPTY;budget=null;}
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder){super.collectImplicitComponents(builder);if(requiresRepair())builder.set(ModDataComponents.CONTAMINATED_EQUIPMENT.get(),true);}
    @Override protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components){super.applyImplicitComponents(components);if(Boolean.TRUE.equals(components.get(ModDataComponents.CONTAMINATED_EQUIPMENT.get())))condition=MachineContaminationState.contaminated(0);}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.store("linearCarrier",ItemStack.OPTIONAL_CODEC,carrier);if(budget!=null)out.store("linearBudget",CoatingReceipt.CODEC,budget);out.putInt("linearProgress",progress);out.putInt("linearResistPaid",resistPaid);out.putInt("linearWaterPaid",waterPaid);out.putInt("linearWater",upw.amount());out.putBoolean("linearRecovery",recovery);out.putBoolean("linearContaminated",requiresRepair());out.store("linearFootprint",BlockPos.CODEC.listOf(),footprint);}
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);carrier=in.read("linearCarrier",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);budget=in.read("linearBudget",CoatingReceipt.CODEC).orElse(null);progress=in.getIntOr("linearProgress",0);resistPaid=in.getIntOr("linearResistPaid",0);waterPaid=in.getIntOr("linearWaterPaid",0);upw.restoreAmount(Math.clamp(in.getIntOr("linearWater",0),0,upw.capacity()));recovery=in.getBooleanOr("linearRecovery",false);condition=in.getBooleanOr("linearContaminated",false)?MachineContaminationState.contaminated(0):MachineContaminationState.clean();footprint=in.read("linearFootprint",BlockPos.CODEC.listOf()).orElse(List.of());
        if(!carrier.isEmpty()&&(budget==null||progress<0||progress>=stageDuration()||resistPaid<0||waterPaid<0||budget!=null&&(resistPaid>budget.totalResist()||waterPaid>budget.cleanWaterMb())||footprint.size()!=(coating()?30:12)))recovery=true;
        if(!carrier.isEmpty()&&budget!=null&&(resistPaid!=(coating()?(int)((long)progress*budget.totalResist()/stageDuration()):0)||waterPaid!=(coating()?0:(int)((long)progress*budget.cleanWaterMb()/stageDuration()))))recovery=true;
    }
}
