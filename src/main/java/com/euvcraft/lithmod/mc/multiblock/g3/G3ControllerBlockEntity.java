package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumConsumer;
import com.euvcraft.lithmod.mc.registry.*;
import com.mojang.serialization.Codec;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import org.jspecify.annotations.Nullable;

/** Frozen G3 jobs own real reserved stacks; no readiness check or simulation mutates inputs. */
public abstract class G3ControllerBlockEntity extends FabMachineBlockEntity implements G3ControllerOwner,VacuumConsumer {
    public enum PrepPhase { IDLE,VACUUM_DEP_ETCH,UPW_RINSE,POWERED_DRY,COMPLETE }
    public enum Status { IDLE,UNFORMED,NO_ROOM,NO_INPUT,OUTPUT_BLOCKED,NO_POWER,NO_UPW,VACUUM,REPAIR,WORKING,RECOVERY }
    private final G3Formation formation=new G3Formation(this);
    private final G3MachineKind kind;
    protected final WaterTank waterTank=new WaterTank(16000);
    final G3WaterInletHandler.TankJournal waterJournal=new G3WaterInletHandler.TankJournal(this);
    protected VacuumChamberState vacuumState=VacuumChamberState.evacuated();
    protected VacuumPumpSpec vacuumSpec=new VacuumPumpSpec(5000,100,2000,10000,50000,1000);
    protected MachineContaminationState contaminationState=MachineContaminationState.clean();
    protected PrepPhase prepPhase=PrepPhase.IDLE;
    protected int phaseProgress,phaseDuration,phaseEnergyPerTick,phaseWaterMb;
    protected boolean jobRunning,jobPaused;
    private int jobProgress,jobDuration,jobEnergyPerTick,selection=-1,cores=1,frozenUses=1;
    private ItemStack carrier=ItemStack.EMPTY,input0=ItemStack.EMPTY,input1=ItemStack.EMPTY,product=ItemStack.EMPTY;
    private List<BlockPos> footprint=List.of();
    private long paidYieldSum,paidSamples;
    private int rinseDuration,rinseFe,rinseWater,dryDuration,dryFe,pumpFe=200;
    private boolean recovery,isolated;
    private long grantTick=Long.MIN_VALUE,consumedGrantTick=Long.MIN_VALUE;
    private int grant;
    private Status status=Status.IDLE;
    protected G3ControllerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,G3MachineKind kind,int inputs,int outputs){super(type,pos,state,inputs,outputs);this.kind=kind;}
    @Override public final G3ControllerBlockEntity machine(){return this;}
    @Override public final G3Formation formation(){return formation;}
    @Override public final G3MachineKind getKind(){return kind;}
    @Override public final boolean large(){return kind==G3MachineKind.DEPOSITION_ETCHER;}
    public final WaterTank waterTank(){return waterTank;}
    public VacuumChamberState vacuumState(){return vacuumState;}
    public VacuumPumpSpec vacuumSpec(){return vacuumSpec;}
    public MachineContaminationState contaminationState(){return contaminationState;}
    public boolean hasJob(){return jobRunning||!carrier.isEmpty()||!input0.isEmpty()||!input1.isEmpty();}
    public int corePreference(){return cores;}
    public int selectedDesign(){return selection;}
    public boolean coresSelectable(){return !hasJob()&&selection==0&&isFormed();}
    public boolean cycleCores(){if(!coresSelectable())return false;var options=EUVCRAFTMod.settings().coreCountOptions();if(options.isEmpty())return false;cores=options.get((options.indexOf(cores)+1)%options.size());setChanged();return true;}
    public boolean selectDesign(int index){if(kind!=G3MachineKind.RETICLE_WRITER||hasJob()||index<0||index>2)return false;selection=index;if(index!=0)cores=1;setChanged();return true;}
    public boolean toggleIsolation(){isolated=!isolated;grant=0;setChanged();return true;}
    private int p(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
    private int fe(int raw){return (int)Math.clamp(Math.round(raw*EUVCRAFTMod.settings().fePowerMultiplier()),1,Integer.MAX_VALUE);}
    private CleanroomRooms.Sample room(){return CleanroomRooms.sample((ServerLevel)level,footprint.isEmpty()?members():footprint);}
    private boolean enclosed(CleanroomRooms.Sample sample){return sample.available()&&sample.room()!=null&&sample.room().state()==CleanroomControllerBlockEntity.ENCLOSED;}
    @Override public void serverTick(){
        if(!(level instanceof ServerLevel)||isRemoved()||level.getBlockEntity(worldPosition)!=this)return;
        refreshDefinition();revalidate();waterTank.resize(Math.clamp(p("water_capacity",32000),1000,WaterTank.MAX_CAPACITY));
        status=Status.IDLE;jobPaused=false;
        if(large()&&!carrier.isEmpty()){
            var sample=room();
            if(!sample.available()){status=Status.NO_ROOM;sync();return;}
            if(!enclosed(sample)&&!FoupItems.trustedContentsOf(carrier).isContaminated())notifyStructuralBreach(level.getGameTime());
        }
        if(large())tickVacuum();
        if(!isFormed()){status=Status.UNFORMED;jobPaused=hasJob();sync();return;}
        if(definition().isEmpty()){sync();return;}
        if(recovery&&!jobRunning&&carrier.isEmpty()){recoverInputs();status=Status.RECOVERY;sync();return;}
        if(large())tickPrep();else tickWriter();sync();
    }
    private void sync(){data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());data().set(DATA_PROGRESS,large()?phaseProgress:jobProgress);data().set(DATA_TICKS,large()?phaseDuration:jobDuration);}
    public int publicData(int i){return switch(i){case 0->isFormed()?1:0;case 1->prepPhase.ordinal();case 2->jobPaused?1:0;case 3->waterTank.amount();case 4->waterTank.capacity();case 5->vacuumState.pressureMicroTorr();case 6->vacuumSpec.operatingPressureMicroTorr();case 7->contaminationState.phase().ordinal();case 8->selection;case 9->cores;case 10->hasJob()?1:0;case 11->isolated?1:0;case 12->phaseProgress;case 13->phaseDuration;case 14->status.ordinal();default->0;};}
    public boolean startWriterJob(String recipeId,int coreCount){
        if(kind!=G3MachineKind.RETICLE_WRITER||hasJob()||!isFormed()||definition().isEmpty()||!(level instanceof ServerLevel))return false;
        var families=List.of("compute","memory","nand");int index=-1;for(int i=0;i<3;i++)if(recipeId.equals("lithmod:g3_"+families.get(i)+"_t7"))index=i;if(index<0)return false;
        var recipes=SupportRecipeLoader.getRegistry();if(recipes==null)return false;var resolved=recipes.byId(Identifier.parse(recipeId)).orElse(null);if(resolved==null||!resolved.recipe().machine().equals(machineId()))return false;
        var recipe=resolved.recipe();int blank=recipe.input().count(),red=recipe.secondary()==null?0:recipe.secondary().count(),cost=fe(recipe.energyPerTick());
        if(blank<1||red<1||slot(0).getCount()<blank||!slot(0).is(resolved.inputItem())||!slot(1).is(Items.REDSTONE)||slot(1).getCount()<red||!slot(2).isEmpty()||buffer().getAmountAsLong()<cost)return false;
        int count=index==0?coreCount:1;if(count<1||!EUVCRAFTMod.settings().coreCountOptions().contains(count))return false;
        input0=slot(0).split(blank);if(slot(0).isEmpty())clearSlot(0);input1=slot(1).split(red);if(slot(1).isEmpty())clearSlot(1);
        product=new ItemStack(resolved.outputItem(),recipe.outputCount());product.set(ModDataComponents.MASK_DATA.get(),new MaskData(families.get(index),7,1,3));if(index==0)product.set(ModDataComponents.CORE_COUNT.get(),count);
        selection=index;cores=count;frozenUses=Math.max(1,EUVCRAFTMod.settings().maskUses());jobDuration=recipe.duration();jobEnergyPerTick=cost;jobProgress=0;paidSamples=paidYieldSum=0;jobRunning=true;setChanged();return true;
    }
    private void tickWriter(){
        if(!jobRunning)return;
        if(!slot(2).isEmpty()){status=Status.OUTPUT_BLOCKED;jobPaused=true;return;}
        var sample=room();if(!sample.available()){status=Status.NO_ROOM;jobPaused=true;return;}
        if(jobProgress<jobDuration){if(energySource().draw(jobEnergyPerTick)!=jobEnergyPerTick){status=Status.NO_POWER;jobPaused=true;return;}jobProgress++;paidYieldSum+=sample.yieldPermille();paidSamples++;setChanged();}
        if(jobProgress>=jobDuration){int quality=(int)(paidYieldSum/Math.max(1,paidSamples));quality=Math.clamp(quality,p("mask_min_uses_permille",250),p("mask_max_uses_permille",1000));var mask=product.get(ModDataComponents.MASK_DATA.get());product.set(ModDataComponents.MASK_DATA.get(),new MaskData(mask.dieType(),7,Math.max(1,(int)((long)frozenUses*quality/1000)),3));setSlot(2,product);product=input0=input1=ItemStack.EMPTY;jobRunning=false;setChanged();}else status=Status.WORKING;
    }
    public boolean startPrepJob(){
        if(!large()||hasJob()||!isFormed()||definition().isEmpty()||!contaminationState.isOperational()||!(level instanceof ServerLevel))return false;
        var sample=room();if(!enclosed(sample)||!slot(3).isEmpty())return false;var stack=slot(0);var lot=FoupItems.trustedContentsOf(stack);
        if(stack.getCount()!=1||lot==null||lot.isEmpty()||lot.isStarted()||lot.isContaminated()||lot.grade()!=WaferSubstrate.ELECTRONIC_GRADE||lot.preparationGeneration()!=1||lot.wafers().stream().anyMatch(w->w.layerIndex()!=0||w.cumulativeRoll()!=Wafer.NO_ROLLS||w.flags()!=0))return false;
        int duration=p("vacuum_duration",400),cost=fe(p("vacuum_energy_per_tick",960)),water=p("rinse_water_mb",4000);
        if(waterTank.amount()<water||buffer().getAmountAsLong()<cost||lot.size()>64)return false;
        rinseDuration=p("rinse_duration",200);rinseFe=fe(p("rinse_energy_per_tick",480));rinseWater=water;dryDuration=p("dry_duration",200);dryFe=fe(p("dry_energy_per_tick",960));pumpFe=fe(p("local_pump_fe_per_tick",200));
        vacuumSpec=new VacuumPumpSpec(p("vacuum_pump_rate",5000),p("vacuum_idle_leak",100),p("vacuum_process_load",2000),p("vacuum_operating_pressure",10000),p("vacuum_reserve_capacity",50000),p("vacuum_reserve_recharge",1000));
        carrier=stack.copy();clearSlot(0);footprint=List.copyOf(members());paidSamples=paidYieldSum=0;prepPhase=PrepPhase.VACUUM_DEP_ETCH;phaseProgress=0;phaseDuration=duration;phaseEnergyPerTick=cost;phaseWaterMb=0;jobRunning=true;setChanged();return true;
    }
    private void tickPrep(){
        if(prepPhase==PrepPhase.COMPLETE){finishCarrier();return;}
        if(!contaminationState.isOperational()){status=Status.REPAIR;return;}
        if(carrier.isEmpty()){if(!startPrepJob()){status=slot(0).isEmpty()?Status.NO_INPUT:Status.NO_ROOM;}return;}
        var sample=room();if(!enclosed(sample)){status=Status.NO_ROOM;jobPaused=true;return;}
        if(prepPhase==PrepPhase.VACUUM_DEP_ETCH&&!vacuumState.canProcess(vacuumSpec)){status=Status.VACUUM;jobPaused=true;return;}
        if(prepPhase==PrepPhase.UPW_RINSE&&phaseProgress==0&&waterTank.amount()<rinseWater){status=Status.NO_UPW;jobPaused=true;return;}
        if(energySource().draw(phaseEnergyPerTick)!=phaseEnergyPerTick){status=Status.NO_POWER;jobPaused=true;return;}
        if(prepPhase==PrepPhase.UPW_RINSE&&phaseProgress==0)waterTank.drainExact(rinseWater,false);
        phaseProgress++;paidSamples++;paidYieldSum+=sample.yieldPermille();status=Status.WORKING;
        if(phaseProgress>=phaseDuration){
            if(prepPhase==PrepPhase.VACUUM_DEP_ETCH){prepPhase=PrepPhase.UPW_RINSE;phaseDuration=rinseDuration;phaseEnergyPerTick=rinseFe;phaseWaterMb=rinseWater;phaseProgress=0;}
            else if(prepPhase==PrepPhase.UPW_RINSE){prepPhase=PrepPhase.POWERED_DRY;phaseDuration=dryDuration;phaseEnergyPerTick=dryFe;phaseWaterMb=0;phaseProgress=0;}
            else {var lot=FoupItems.trustedContentsOf(carrier);int[] draws=new int[lot.size()];for(int i=0;i<draws.length;i++)draws[i]=ThreadLocalRandom.current().nextInt(1,1001);carrier.set(ModDataComponents.FOUP_CONTENTS.get(),lot.preparedG3((int)(paidYieldSum/Math.max(1,paidSamples)),draws));prepPhase=PrepPhase.COMPLETE;jobRunning=false;finishCarrier();}
        }
        setChanged();
    }
    private void finishCarrier(){if(carrier.isEmpty()){prepPhase=PrepPhase.IDLE;return;}if(!slot(3).isEmpty()){status=Status.OUTPUT_BLOCKED;return;}setSlot(3,carrier);carrier=ItemStack.EMPTY;prepPhase=PrepPhase.IDLE;jobRunning=false;footprint=List.of();setChanged();}
    public boolean notifyStructuralBreach(long tick){if(!large()||carrier.isEmpty())return false;var lot=FoupItems.trustedContentsOf(carrier);if(lot==null||lot.isContaminated())return false;contaminationState=MachineContaminationState.contaminated(tick);carrier.set(ModDataComponents.FOUP_CONTENTS.get(),lot.contaminated());prepPhase=PrepPhase.COMPLETE;jobRunning=false;setChanged();return true;}
    public boolean repair(){
        if(!large()||!isFormed()||!contaminationState.requiresRepair()||!enclosed(room()))return false;
        int filters=p("repair_filter_medium",2),red=p("repair_redstone",16),water=p("repair_upw_mb",8000),cost=fe(p("repair_fe",200000));
        if(!slot(1).is(ModItems.UTILITY_FILTER.get())||slot(1).getCount()<filters||!slot(2).is(Items.REDSTONE)||slot(2).getCount()<red||waterTank.amount()<water||buffer().getAmountAsLong()<cost)return false;
        if(energySource().draw(cost)!=cost)return false;waterTank.drainExact(water,false);slot(1).shrink(filters);slot(2).shrink(red);contaminationState=contaminationState.repair(new ContaminationRepairCost(filters+red,water,cost)).state().recalibrate().state();setChanged();return true;
    }
    private VacuumMode mode(){return prepPhase==PrepPhase.VACUUM_DEP_ETCH?VacuumMode.PROCESS:VacuumMode.IDLE;}
    @Override public BlockPos vacuumOwnerPos(){return worldPosition;}
    @Override public int vacuumDemand(){return isolated||!isFormed()||!large()?0:vacuumState.requiredServiceMicroTorrPerTick(vacuumSpec,mode());}
    @Override public boolean acceptVacuumGrant(long tick,int amount){if(level==null||isolated||amount<0||tick>level.getGameTime()||tick<level.getGameTime()-1||tick<=consumedGrantTick||tick<grantTick)return false;grantTick=tick;grant=amount;return true;}
    private void tickVacuum(){
        int service=0;if(level!=null&&grantTick>consumedGrantTick&&grantTick>=level.getGameTime()-1&&grantTick<=level.getGameTime()&&!isolated){service=grant;consumedGrantTick=grantTick;}grant=0;
        boolean local=isFormed()&&definition().isPresent()&&service<vacuumDemand()&&energySource().draw(pumpFe)==pumpFe;
        vacuumState=vacuumState.tick(vacuumSpec,mode(),service,local).state();
    }
    private void recoverInputs(){if(!input0.isEmpty()&&slot(0).isEmpty()){setSlot(0,input0);input0=ItemStack.EMPTY;}if(!input1.isEmpty()&&slot(1).isEmpty()){setSlot(1,input1);input1=ItemStack.EMPTY;}recovery=!input0.isEmpty()||!input1.isEmpty();}
    @Override public void setRemoved(){formation.unload();super.setRemoved();}
    @Override protected void dropExtraContents(@Nullable Level world,BlockPos pos){dropContainer(world,pos,new SimpleContainer(carrier,input0,input1));carrier=input0=input1=product=ItemStack.EMPTY;jobRunning=false;}
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);out.putInt("g3Water",waterTank.amount());out.putInt("g3Pressure",vacuumState.pressureMicroTorr());out.putInt("g3Reserve",vacuumState.reserveMicroTorr());out.putInt("g3Contamination",contaminationState.phase().ordinal());out.putLong("g3Breach",contaminationState.lastBreachTick());out.putInt("g3Selection",selection);out.putInt("g3Cores",cores);out.putBoolean("g3Isolated",isolated);
        out.store("g3Carrier",ItemStack.OPTIONAL_CODEC,carrier);out.store("g3Input0",ItemStack.OPTIONAL_CODEC,input0);out.store("g3Input1",ItemStack.OPTIONAL_CODEC,input1);out.store("g3Product",ItemStack.OPTIONAL_CODEC,product);out.store("g3Footprint",BlockPos.CODEC.listOf(),footprint);out.putBoolean("g3Running",jobRunning);out.putBoolean("g3Recovery",recovery);out.putLong("g3Samples",paidSamples);out.putLong("g3YieldSum",paidYieldSum);
        out.store("g3Frozen",Codec.INT.listOf(),List.of(prepPhase.ordinal(),phaseProgress,phaseDuration,phaseEnergyPerTick,phaseWaterMb,rinseDuration,rinseFe,rinseWater,dryDuration,dryFe,pumpFe,jobProgress,jobDuration,jobEnergyPerTick,frozenUses,vacuumSpec.pumpRateMicroTorrPerTick(),vacuumSpec.idleLeakMicroTorrPerTick(),vacuumSpec.processLoadMicroTorrPerTick(),vacuumSpec.operatingPressureMicroTorr(),vacuumSpec.reserveCapacityMicroTorr(),vacuumSpec.reserveRechargeMicroTorrPerTick()));
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);waterTank.restoreAmount(Math.clamp(in.getIntOr("g3Water",0),0,WaterTank.MAX_CAPACITY));vacuumState=VacuumChamberState.at(Math.clamp(in.getIntOr("g3Pressure",0),0,VacuumChamberState.MAX_PRESSURE_MICROTORR),Math.clamp(in.getIntOr("g3Reserve",0),0,1000000));int condition=Math.clamp(in.getIntOr("g3Contamination",0),0,2);contaminationState=new MachineContaminationState(MachineContaminationState.Phase.values()[condition],condition==0?-1:Math.max(0,in.getLongOr("g3Breach",0)));selection=Math.clamp(in.getIntOr("g3Selection",-1),-1,2);cores=Math.clamp(in.getIntOr("g3Cores",1),1,256);isolated=in.getBooleanOr("g3Isolated",false);
        carrier=in.read("g3Carrier",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);input0=in.read("g3Input0",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);input1=in.read("g3Input1",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);product=in.read("g3Product",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);footprint=in.read("g3Footprint",BlockPos.CODEC.listOf()).orElse(List.of());jobRunning=in.getBooleanOr("g3Running",false);recovery=in.getBooleanOr("g3Recovery",false);paidSamples=Math.max(0,in.getLongOr("g3Samples",0));paidYieldSum=Math.clamp(in.getLongOr("g3YieldSum",0),0,1000L*paidSamples);
        var f=in.read("g3Frozen",Codec.INT.listOf()).orElse(List.of());
        try{if(f.size()!=21)throw new IllegalArgumentException();prepPhase=PrepPhase.values()[f.get(0)];phaseProgress=f.get(1);phaseDuration=f.get(2);phaseEnergyPerTick=f.get(3);phaseWaterMb=f.get(4);rinseDuration=f.get(5);rinseFe=f.get(6);rinseWater=f.get(7);dryDuration=f.get(8);dryFe=f.get(9);pumpFe=f.get(10);jobProgress=f.get(11);jobDuration=f.get(12);jobEnergyPerTick=f.get(13);frozenUses=f.get(14);vacuumSpec=new VacuumPumpSpec(f.get(15),f.get(16),f.get(17),f.get(18),f.get(19),f.get(20));if(jobRunning&&(large()?(carrier.isEmpty()||footprint.size()!=175||phaseDuration<1||phaseEnergyPerTick<1||rinseDuration<1||rinseFe<1||rinseWater<1||dryDuration<1||dryFe<1||phaseProgress<0||phaseProgress>=phaseDuration):(input0.isEmpty()||input1.isEmpty()||product.isEmpty()||jobDuration<1||jobEnergyPerTick<1||jobProgress<0||jobProgress>jobDuration)))throw new IllegalArgumentException();}
        catch(RuntimeException invalid){jobRunning=false;product=ItemStack.EMPTY;recovery=true;prepPhase=carrier.isEmpty()?PrepPhase.IDLE:PrepPhase.COMPLETE;}
        grant=0;grantTick=consumedGrantTick=Long.MIN_VALUE;sync();
    }
}
