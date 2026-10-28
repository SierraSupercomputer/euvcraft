package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumConsumer;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.transaction.*;
import org.jspecify.annotations.Nullable;

/** One physical source per scanner; FE, H2 and tin are charged only for committed exposure. */
public final class EuvLightSourceControllerBlockEntity extends FabMachineBlockEntity implements EuvFormationOwner,VacuumConsumer {
    public static final int SLOT_TIN_INPUT=0;
    private static final TagKey<Item> TIN=TagKey.create(Registries.ITEM,Identifier.parse("c:ingots/tin"));
    private final EuvLightSourceFormation formation=new EuvLightSourceFormation(this);
    public final WaterTank hydrogenTank=new WaterTank(32000);
    final SnapshotJournal<Integer> hydrogenJournal=new SnapshotJournal<>(){
        @Override protected Integer createSnapshot(){return hydrogenTank.amount();}
        @Override protected void revertToSnapshot(Integer amount){hydrogenTank.restoreAmount(amount);}
        @Override protected void onRootCommit(Integer old){setChanged();}
    };
    private VacuumChamberState vacuum=VacuumChamberState.at(VacuumChamberState.MAX_PRESSURE_MICROTORR,0);
    private VacuumPumpSpec spec=new VacuumPumpSpec(5000,100,2000,10000,50000,1000);
    private int tinTicks;
    private @Nullable BlockPos pairedScanner;
    private @Nullable UUID pairedInstallation;
    private long lastExposure=Long.MIN_VALUE,grantTick=Long.MIN_VALUE,usedGrantTick=Long.MIN_VALUE;
    private int grant;
    private boolean isolated;
    private record SourceSnapshot(ItemStack tin,int credit,BlockPos scanner,UUID installation,long exposure){}
    private final SnapshotJournal<SourceSnapshot> exposureJournal=new SnapshotJournal<>(){
        @Override protected SourceSnapshot createSnapshot(){return new SourceSnapshot(slot(0).copy(),tinTicks,pairedScanner,pairedInstallation,lastExposure);}
        @Override protected void revertToSnapshot(SourceSnapshot s){setSlot(0,s.tin());tinTicks=s.credit();pairedScanner=s.scanner();pairedInstallation=s.installation();lastExposure=s.exposure();}
        @Override protected void onRootCommit(SourceSnapshot previous){setChanged();}
    };
    public EuvLightSourceControllerBlockEntity(BlockPos pos,BlockState state){super(EuvMachines.LIGHT_SOURCE_CONTROLLER_BE.get(),pos,state,1,0);}
    @Override protected String machineId(){return "euv_light_source";}
    @Override public Component getDisplayName(){return Component.translatable("block.lithmod.euv_light_source_controller");}
    @Override public FabMachineBlockEntity machine(){return this;}
    @Override public Formation formation(){return formation;}
    @Override public boolean isFormed(){return formation.formed();}
    @Override public List<BlockPos> members(){return formation.members();}
    @Override public MatchResult matchStructure(MultiblockPos anchor,MultiblockDirection facing,Function<MultiblockPos,BlockKind> lookup){return LightSourceStructureMatcher.match(anchor,facing,lookup);}
    public String failureReason(){return formation.failureReason();}
    @Override public void onFormed(){}
    @Override public void onUnformed(){}
    private int p(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
    private int fe(int raw){return (int)Math.clamp(Math.round(raw*EUVCRAFTMod.settings().fePowerMultiplier()),1,Integer.MAX_VALUE);}
    public int exposureFe(){return definition().map(d->d.energyPerTick(null,EUVCRAFTMod.settings())).orElse(Integer.MAX_VALUE);}
    public int hydrogenPerTick(){return p("hydrogen_per_tick_mb",10);}
    public int tinStored(){return slot(0).getCount();}
    public int tinCreditTicks(){return tinTicks;}
    public boolean acceptsTin(net.neoforged.neoforge.transfer.item.ItemResource resource){return !resource.isEmpty()&&resource.toStack().is(TIN);}
    public int insertTin(net.neoforged.neoforge.transfer.item.ItemResource resource,int amount,TransactionContext transaction){
        if(amount<=0||!acceptsTin(resource))return 0;var stack=slot(0);if(!stack.isEmpty()&&!resource.matches(stack))return 0;
        int accepted=Math.min(amount,Math.max(0,resource.getMaxStackSize()-stack.getCount()));if(accepted==0)return 0;
        exposureJournal.updateSnapshots(transaction);if(stack.isEmpty())setSlot(0,resource.toStack(accepted));else stack.grow(accepted);return accepted;
    }
    public boolean hasScannerReservation(){return pairedScanner!=null;}
    public VacuumChamberState vacuumState(){return vacuum;}
    public VacuumPumpSpec vacuumSpec(){return spec;}
    public boolean toggleIsolation(){isolated=!isolated;grant=0;setChanged();return true;}
    public boolean canExpose(EuvScannerControllerBlockEntity scanner){
        if(level==null||isRemoved()||scanner.isRemoved()||scanner.getLevel()!=level||level.getBlockEntity(worldPosition)!=this||level.getBlockEntity(scanner.getBlockPos())!=scanner)return false;
        revalidate();
        if(!isFormed()||definition().isEmpty()||!vacuum.canProcess(spec)||hydrogenTank.amount()<hydrogenPerTick()||buffer().getAmountAsLong()<exposureFe())return false;
        if(tinTicks==0&&(slot(0).isEmpty()||!slot(0).is(TIN)))return false;
        if(EuvBeamlineConnection.source(scanner)!=this)return false;
        if(pairedScanner==null)return true;
        if(pairedScanner.equals(scanner.getBlockPos())&&scanner.installationId().equals(pairedInstallation))return true;
        if(!level.hasChunkAt(pairedScanner))return false;
        var other=level.getBlockEntity(pairedScanner);
        return !(other instanceof EuvScannerControllerBlockEntity active)||!active.installationId().equals(pairedInstallation)||EuvBeamlineConnection.source(active)!=this;
    }
    public boolean payExposure(EuvScannerControllerBlockEntity scanner,TransactionContext transaction){
        if(!canExpose(scanner))return false;
        if(buffer().extract(exposureFe(),transaction)!=exposureFe())return false;
        hydrogenJournal.updateSnapshots(transaction);exposureJournal.updateSnapshots(transaction);
        hydrogenTank.drainExact(hydrogenPerTick(),false);
        if(tinTicks==0){slot(0).shrink(1);tinTicks=p("tin_exposure_ticks",1000);}
        tinTicks--;pairedScanner=scanner.getBlockPos().immutable();pairedInstallation=scanner.installationId();lastExposure=level.getGameTime();return true;
    }
    @Override public BlockPos vacuumOwnerPos(){return worldPosition;}
    private VacuumMode mode(){return level!=null&&lastExposure>=level.getGameTime()-1?VacuumMode.PROCESS:VacuumMode.IDLE;}
    @Override public int vacuumDemand(){return isolated||!isFormed()?0:vacuum.requiredServiceMicroTorrPerTick(spec,mode());}
    public int vacuumCentralDemand(){return vacuumDemand();}
    @Override public boolean acceptVacuumGrant(long tick,int amount){if(level==null||isolated||amount<0||tick>level.getGameTime()||tick<level.getGameTime()-1||tick<=usedGrantTick||tick<grantTick)return false;grantTick=tick;grant=amount;return true;}
    @Override public void serverTick(){
        if(level==null||level.isClientSide())return;refreshDefinition();revalidate();
        hydrogenTank.resize(Math.clamp(p("hydrogen_capacity_mb",32000),1000,WaterTank.MAX_CAPACITY));
        spec=new VacuumPumpSpec(p("vacuum_pump_rate",5000),p("vacuum_idle_leak",100),p("vacuum_process_load",2000),p("vacuum_operating_pressure",10000),p("vacuum_reserve_capacity",50000),p("vacuum_reserve_recharge",1000));
        int service=0;if(grantTick>usedGrantTick&&grantTick>=level.getGameTime()-1&&grantTick<=level.getGameTime()&&!isolated){service=grant;usedGrantTick=grantTick;}grant=0;
        int pumpFe=fe(p("local_pump_fe_per_tick",200));boolean local=isFormed()&&definition().isPresent()&&service<vacuumDemand()&&energySource().draw(pumpFe)==pumpFe;
        vacuum=vacuum.tick(spec,mode(),service,local).state();data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());data().set(DATA_PROGRESS,0);data().set(DATA_TICKS,0);
    }
    @Override public void setRemoved(){formation.unload();super.setRemoved();}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new EuvLightSourceDashboardMenu(id,inventory,this,worldPosition);}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("euvHydrogen",hydrogenTank.amount());out.putInt("euvTinTicks",tinTicks);out.putInt("euvPressure",vacuum.pressureMicroTorr());out.putInt("euvReserve",vacuum.reserveMicroTorr());out.putBoolean("euvIsolation",isolated);if(pairedScanner!=null)out.store("euvPairedScanner",BlockPos.CODEC,pairedScanner);if(pairedInstallation!=null)out.putString("euvPairedInstallation",pairedInstallation.toString());}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);hydrogenTank.restoreAmount(Math.clamp(in.getIntOr("euvHydrogen",0),0,WaterTank.MAX_CAPACITY));tinTicks=Math.clamp(in.getIntOr("euvTinTicks",0),0,1000000);vacuum=VacuumChamberState.at(Math.clamp(in.getIntOr("euvPressure",0),0,VacuumChamberState.MAX_PRESSURE_MICROTORR),Math.clamp(in.getIntOr("euvReserve",0),0,1000000));isolated=in.getBooleanOr("euvIsolation",false);pairedScanner=in.read("euvPairedScanner",BlockPos.CODEC).orElse(null);try{pairedInstallation=UUID.fromString(in.getString("euvPairedInstallation").orElse(""));}catch(IllegalArgumentException invalid){pairedInstallation=null;pairedScanner=null;}grant=0;grantTick=usedGrantTick=lastExposure=Long.MIN_VALUE;}
}
