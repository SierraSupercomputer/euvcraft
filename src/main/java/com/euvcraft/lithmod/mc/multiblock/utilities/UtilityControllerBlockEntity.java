package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.core.*;import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;import com.euvcraft.lithmod.mc.multiblock.advanced.*;import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;import net.minecraft.network.chat.Component;import net.minecraft.world.entity.player.Inventory;import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.transaction.*;
/** Tank transfer journals are shared by every port lens. No power or fluid runs while unformed. */
public final class UtilityControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner{
 private final AdvancedFormation formation=new AdvancedFormation(this);
 public final WaterTank raw=new WaterTank(16000),pure=new WaterTank(16000);
 final TankJournal rawJournal=new TankJournal(this,raw),pureJournal=new TankJournal(this,pure);
 private int progress,duration,cost,pendingOutput;
 public UtilityControllerBlockEntity(BlockPos p,BlockState s){super(UtilityMachines.CONTROLLER_BE.get(),p,s,2,1);}
 @Override protected String machineId(){return "utility_plant";}
 @Override public Component getDisplayName(){return Component.translatable("block.lithmod.utility_plant");}
 @Override public FabMachineBlockEntity machine(){return this;}@Override public AdvancedFormation formation(){return formation;}@Override public boolean large(){return false;}
 @Override public MatchResult matchStructure(MultiblockPos p,MultiblockDirection f,java.util.function.Function<MultiblockPos,BlockKind> lookup){return UtilityStructureMatcher.match(p,f,lookup);}
 public int progress(){return progress;}public int duration(){return duration;}public boolean hasJob(){return pendingOutput>0;}
 private int param(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
 @Override public void serverTick(){if(level==null||level.isClientSide())return;revalidate();refreshDefinition();
  raw.resize(Math.clamp(param("raw_capacity_mb",16000),1000,1000000));pure.resize(Math.clamp(param("pure_capacity_mb",16000),1000,1000000));
  if(!isFormed()||definition().isEmpty()){sync();return;}
  if(slot(1).is(Items.WATER_BUCKET)&&slot(1).getCount()==1&&raw.capacity()-raw.amount()>=1000&&(slot(2).isEmpty()||slot(2).is(Items.BUCKET)&&slot(2).getCount()<slot(2).getMaxStackSize())){
   raw.fill(1000,false);clearSlot(1);giveOutput(new ItemStack(Items.BUCKET));setChanged();
  }
  if(pendingOutput==0){int water=param("water_per_batch_mb",8000),output=param("pure_per_batch_mb",8000),filters=param("filters_per_batch",1),ticks=param("duration",200),fe=definition().orElseThrow().energyPerTick(null,com.euvcraft.lithmod.mc.EUVCRAFTMod.settings());
   if(water>=1&&water<=raw.capacity()&&output>=1&&output<=pure.capacity()&&filters>=1&&filters<=64&&ticks>=1&&ticks<=72000&&fe>0&&raw.amount()>=water&&pure.capacity()-pure.amount()>=output&&slot(0).is(ModItems.UTILITY_FILTER.get())&&slot(0).getCount()>=filters&&buffer().getAmountAsLong()>=fe){
    raw.drainExact(water,false);slot(0).shrink(filters);if(slot(0).isEmpty())clearSlot(0);pendingOutput=output;duration=ticks;cost=fe;progress=0;setChanged();
   }
  }else if(pure.capacity()-pure.amount()>=pendingOutput&&energySource().draw(cost)==cost){progress++;if(progress>=duration){pure.fill(pendingOutput,false);pendingOutput=0;progress=0;}setChanged();}sync();
 }
 private void sync(){data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());data().set(DATA_PROGRESS,progress);data().set(DATA_TICKS,pendingOutput==0?0:duration);}
 @Override public void setRemoved(){formation.unload();super.setRemoved();}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inv,FabMachineBlockEntity machine){return new UtilityMenu(id,inv,this);}
 @Override protected void saveAdditional(ValueOutput o){super.saveAdditional(o);o.putInt("rawMb",raw.amount());o.putInt("pureMb",pure.amount());o.putInt("utilityProgress",progress);o.putInt("utilityDuration",duration);o.putInt("utilityCost",cost);o.putInt("utilityPending",pendingOutput);}
 @Override protected void loadAdditional(ValueInput i){super.loadAdditional(i);raw.restoreAmount(Math.clamp(i.getIntOr("rawMb",0),0,1000000));pure.restoreAmount(Math.clamp(i.getIntOr("pureMb",0),0,1000000));progress=i.getIntOr("utilityProgress",0);duration=i.getIntOr("utilityDuration",0);cost=i.getIntOr("utilityCost",0);pendingOutput=i.getIntOr("utilityPending",0);if(pendingOutput<0||pendingOutput>1000000||progress<0||progress>=duration||duration>72000||cost<=0){progress=0;duration=0;cost=0;pendingOutput=0;}if(pendingOutput==0){progress=0;duration=0;cost=0;}sync();}
 static final class TankJournal extends SnapshotJournal<Integer>{final UtilityControllerBlockEntity owner;final WaterTank tank;TankJournal(UtilityControllerBlockEntity owner,WaterTank tank){this.owner=owner;this.tank=tank;}@Override protected Integer createSnapshot(){return tank.amount();}@Override protected void revertToSnapshot(Integer amount){tank.restoreAmount(amount);}@Override protected void onRootCommit(Integer old){owner.setChanged();}}
}



