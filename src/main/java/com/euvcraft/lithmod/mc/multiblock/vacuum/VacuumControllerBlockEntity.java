package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.t7.CentralVacuumAllocator;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Bounded, paid service distribution. Chamber pressure stays with each consumer. */
public final class VacuumControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner {
    private final AdvancedFormation formation=new AdvancedFormation(this);
    private boolean enabled=true,conflict;
    private int cursor,capacity,demand,fePaused,clients;
    private long epoch,plantTick;
    private final NavigableMap<BlockPos,VacuumConsumer> consumers=new TreeMap<>();
    public VacuumControllerBlockEntity(BlockPos pos,BlockState state){super(VacuumMachines.CONTROLLER_BE.get(),pos,state,0,0);}
    @Override protected String machineId(){return "central_vacuum_plant";}
    @Override public Component getDisplayName(){return Component.translatable("block.lithmod.central_vacuum_plant");}
    @Override public FabMachineBlockEntity machine(){return this;}
    @Override public AdvancedFormation formation(){return formation;}
    @Override public boolean large(){return true;}
    @Override public MatchResult matchStructure(MultiblockPos anchor,MultiblockDirection facing,Function<MultiblockPos,BlockKind> lookup){return T7UtilityStructureMatcher.matchVacuumPlant(anchor,facing,lookup);}
    public int networkCapacity(){return capacity;}
    public int networkDemand(){return demand;}
    public int networkFePaused(){return fePaused;}
    public boolean networkConflict(){return conflict;}
    public int networkClientCount(){return clients;}
    public boolean enabled(){return enabled;}
    public void setEnabled(boolean next){enabled=next;setChanged();}
    public long plantTick(){return plantTick;}
    public long discoveryEpoch(){return epoch;}
    private int param(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
    private int scaled(int fe){return (int)Math.clamp(Math.round(fe*EUVCRAFTMod.settings().fePowerMultiplier()),1,Integer.MAX_VALUE);}
    @Override public void serverTick(){
        if(level==null||level.isClientSide())return;
        refreshDefinition();revalidate();
        capacity=demand=fePaused=clients=0;conflict=false;consumers.clear();
        if(!enabled||!isFormed()||definition().isEmpty()){sync();return;}
        plantTick=level.getGameTime();discover();clients=consumers.size();
        if(conflict){sync();return;}
        capacity=Math.clamp(param("vacuum_capacity_microtorr_per_tick",20000),1,1000000);
        int[] requests=consumers.values().stream().mapToInt(c->Math.clamp(c.vacuumDemand(),0,1000000)).toArray();
        long total=0;for(int request:requests)total+=request;demand=(int)Math.min(Integer.MAX_VALUE,total);
        var allocation=CentralVacuumAllocator.allocate(capacity,requests,cursor);
        int service=allocation.totalAllocatedMicroTorrPerTick();
        int fe=scaled(service>0?param("vacuum_active_fe_per_tick",350):param("vacuum_standing_fe_per_tick",120));
        if(energySource().draw(fe)!=fe){fePaused=fe;sync();return;}
        int i=0;for(var consumer:consumers.values())consumer.acceptVacuumGrant(plantTick,allocation.at(i++));
        cursor=clients==0?0:(Math.floorMod(cursor,clients)+1)%clients;setChanged();sync();
    }
    private boolean conduit(BlockEntity entity){return entity instanceof VacuumMemberBlockEntity member&&(member.structureKind()==BlockKind.VACUUM_CONDUIT||member.structureKind()==BlockKind.VACUUM_PORT);}
    /** Complete a bounded connected-component scan before selecting one live plant. */
    private void discover(){
        int limit=Math.clamp(param("vacuum_max_nodes",512),16,4096);
        Set<BlockPos> seen=new HashSet<>();Deque<BlockPos> queue=new ArrayDeque<>();
        for(var pos:members())if(level.hasChunkAt(pos)&&conduit(level.getBlockEntity(pos))&&seen.add(pos))queue.add(pos);
        BlockPos leader=getBlockPos();int visited=0;
        while(!queue.isEmpty()&&visited<limit){
            var pos=queue.removeFirst();visited++;if(!level.hasChunkAt(pos))continue;
            var entity=level.getBlockEntity(pos);
            if(entity instanceof VacuumMemberBlockEntity member&&member.controller() instanceof VacuumControllerBlockEntity plant&&plant!=this&&!plant.isRemoved()&&plant.enabled()&&plant.isFormed()&&level.hasChunkAt(plant.getBlockPos())&&level.getBlockEntity(plant.getBlockPos())==plant&&plant.getBlockPos().compareTo(leader)<0)leader=plant.getBlockPos();
            for(var direction:Direction.values()){
                var next=pos.relative(direction);if(!level.hasChunkAt(next))continue;var neighbor=level.getBlockEntity(next);
                if(conduit(neighbor)){if(seen.size()>=limit&&!seen.contains(next)){conflict=true;continue;}if(seen.add(next))queue.addLast(next);}
                else if(neighbor instanceof VacuumAccess access){
                    var consumer=access.vacuumConsumer();
                    if(consumer instanceof BlockEntity owner&&!owner.isRemoved()&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&owner.getBlockPos().equals(consumer.vacuumOwnerPos()))consumers.put(owner.getBlockPos().immutable(),consumer);
                }
            }
        }
        if(!queue.isEmpty()||!leader.equals(getBlockPos()))conflict=true;epoch++;
    }
    private void sync(){data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());data().set(DATA_PROGRESS,0);data().set(DATA_TICKS,0);}
    @Override public void setRemoved(){formation.unload();consumers.clear();super.setRemoved();}
    @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);output.putBoolean("vacuumEnabled",enabled);output.putInt("vacuumCursor",cursor);}
    @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);enabled=input.getBooleanOr("vacuumEnabled",true);cursor=input.getIntOr("vacuumCursor",0);consumers.clear();capacity=demand=fePaused=clients=0;conflict=false;epoch=plantTick=0;sync();}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new VacuumMenu(id,inventory,this);}
}
