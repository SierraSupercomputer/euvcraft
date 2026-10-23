package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
public final class CleanroomFilterBlockEntity extends FabMachineBlockEntity {
 private int remaining;private boolean working;private long workedAt=Long.MIN_VALUE;
 public CleanroomFilterBlockEntity(BlockPos p,BlockState s){super(CleanroomBlocks.FILTER_BE.get(),p,s,1,0);}
 @Override protected String machineId(){return "cleanroom_filter";}
 public int remaining(){return remaining;}public boolean working(){return working&&level!=null&&level.getGameTime()-workedAt<21;}
 public boolean workSecond(CleanroomControllerBlockEntity owner){
  if(level==null||isRemoved()||owner.isRemoved()||owner.getLevel()!=level||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=this||owner.state()!=CleanroomControllerBlockEntity.ENCLOSED||owner.bounds()==null||!owner.bounds().shell(worldPosition))return false;
  refreshDefinition();var rules=CleanroomRulesLoader.rules();int cost=rules.filterFePerTick()*20;
  if(definition().isEmpty()||buffer().getAmountAsLong()<cost||remaining<20&&!slot(0).is(CleanroomItems.MEDIUM.get())){working=false;return false;}
  if(energySource().draw(cost)!=cost){working=false;return false;}
  if(remaining<20){slot(0).shrink(1);remaining=rules.filterMediaTicks();}remaining=Math.max(0,remaining-20);working=true;workedAt=level.getGameTime();setChanged();return true;
 }
 @Override public void serverTick(){refreshDefinition();data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());}
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&stack.is(CleanroomItems.MEDIUM.get());}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inv,FabMachineBlockEntity machine){return new CleanroomMenu(id,inv,this,true);}
 @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("mediumTicks",remaining);}
 @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);remaining=Math.clamp(in.getIntOr("mediumTicks",0),0,CleanroomRulesLoader.rules().filterMediaTicks());working=false;}
}
