package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.core.cleanroom.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.*;
public final class CleanroomControllerBlockEntity extends FabMachineBlockEntity {
 public static final int UNFORMED=0,ENCLOSED=1,BREACHED=2,UNKNOWN=3;
 private RoomBounds bounds;private int state=UNFORMED,score,doors,activeFilters,trend;private boolean powered;private String fault="Build an enclosed cuboid";private List<BlockPos> filters=List.of();private int ticks;private long validatedAt=Long.MIN_VALUE;private boolean topologyDirty=true;
 public void markTopologyDirty(){topologyDirty=true;}
 public CleanroomControllerBlockEntity(BlockPos p,BlockState s){super(CleanroomBlocks.CONTROLLER_BE.get(),p,s,0,0);}
 @Override protected String machineId(){return "cleanroom_controller";}
 public RoomBounds bounds(){return bounds;}public int state(){return state;}public int cleanliness(){return score;}public int ppm(){return CleanroomAtmosphere.ppm(score,CleanroomRulesLoader.rules());}public int roomYield(){return CleanroomAtmosphere.yieldPermille(score,CleanroomRulesLoader.rules());}public int doors(){return doors;}public int activeFilters(){return activeFilters;}public int filterCount(){return filters.size();}public int trend(){return trend;}public boolean powered(){return powered;}public String fault(){return fault;}
 public void revalidate(){
  if(!(level instanceof ServerLevel server)||isRemoved())return;
  CleanroomRooms.register(this);var result=CleanroomTopology.discover(server,worldPosition);
  if(result.state()==ENCLOSED){if(!Objects.equals(bounds,result.bounds()))score=0;bounds=result.bounds();apply(result);if(CleanroomRooms.overlaps(server,this,bounds)){state=BREACHED;fault="Room overlaps another controller";}}
  else if(bounds!=null)validateKnown();else apply(result);
  setChanged();
 }
 private void apply(CleanroomTopology.Result result){state=result.state();fault=result.fault();doors=result.doors();filters=result.filters();}
 public void validateKnown(){if(!(level instanceof ServerLevel server)||bounds==null||isRemoved())return;
  for(int cx=bounds.minX()>>4;cx<=bounds.maxX()>>4;cx++)for(int cz=bounds.minZ()>>4;cz<=bounds.maxZ()>>4;cz++)if(!server.hasChunk(cx,cz)){state=UNKNOWN;fault="Room chunk unavailable";topologyDirty=true;return;}
  if(!topologyDirty&&server.getGameTime()-validatedAt<20)return;topologyDirty=false;validatedAt=server.getGameTime();apply(CleanroomTopology.validate(server,worldPosition,bounds));if(state==ENCLOSED&&CleanroomRooms.overlaps(server,this,bounds)){state=BREACHED;fault="Room overlaps another controller";}}
 @Override public void serverTick(){
  if(!(level instanceof ServerLevel server))return;refreshDefinition();CleanroomRooms.register(this);
  if(ticks++%100==0)revalidate();if(ticks%20!=0)return;validateKnown();
  data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());
  if(state==UNKNOWN){activeFilters=0;trend=0;powered=false;return;}
  var rules=CleanroomRulesLoader.rules();int required=rules.controllerFePerTick()*20;
  powered=definition().isPresent()&&buffer().getAmountAsLong()>=required&&energySource().draw(required)==required;
  activeFilters=0;if(state==ENCLOSED&&powered)for(var p:filters)if(server.hasChunkAt(p)&&server.getBlockEntity(p) instanceof CleanroomFilterBlockEntity filter&&filter.workSecond(this))activeFilters++;
  int ungowned=0,gowned=0;
  if(bounds!=null)for(var player:server.getEntitiesOfClass(Player.class,bounds.interior())){if(player.isSpectator())continue;if(gowned(player))gowned++;else ungowned++;}
  int previous=score;score=CleanroomAtmosphere.step(score,bounds==null?rules.referenceVolume():bounds.volume(),activeFilters,ungowned,gowned,doors,powered,state==BREACHED,rules);trend=score-previous;setChanged();
 }
 public static boolean gowned(Player p){return p.getItemBySlot(EquipmentSlot.HEAD).is(CleanroomItems.HOOD.get())&&p.getItemBySlot(EquipmentSlot.CHEST).is(CleanroomItems.SUIT.get())&&p.getItemBySlot(EquipmentSlot.LEGS).is(CleanroomItems.TROUSERS.get())&&p.getItemBySlot(EquipmentSlot.FEET).is(CleanroomItems.BOOTS.get());}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inv,FabMachineBlockEntity machine){return new CleanroomMenu(id,inv,this,false);}
 @Override public void setRemoved(){CleanroomRooms.forget(this);super.setRemoved();}
 @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("cleanliness",score);if(bounds!=null){out.putInt("roomMinX",bounds.minX());out.putInt("roomMinY",bounds.minY());out.putInt("roomMinZ",bounds.minZ());out.putInt("roomMaxX",bounds.maxX());out.putInt("roomMaxY",bounds.maxY());out.putInt("roomMaxZ",bounds.maxZ());out.putBoolean("roomKnown",true);}}
 @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);score=Math.clamp(in.getIntOr("cleanliness",0),0,CleanroomAtmosphere.SCALE);bounds=null;if(in.getBooleanOr("roomKnown",false))try{bounds=new RoomBounds(in.getIntOr("roomMinX",0),in.getIntOr("roomMinY",0),in.getIntOr("roomMinZ",0),in.getIntOr("roomMaxX",0),in.getIntOr("roomMaxY",0),in.getIntOr("roomMaxZ",0));}catch(IllegalArgumentException invalid){score=0;}state=bounds==null?UNFORMED:UNKNOWN;powered=false;filters=List.of();ticks=0;}
}
