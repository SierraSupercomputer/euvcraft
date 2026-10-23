package com.euvcraft.lithmod.mc.machine.cleaner;
import java.util.UUID;
import com.euvcraft.lithmod.core.*;import com.euvcraft.lithmod.mc.*;import com.euvcraft.lithmod.mc.registry.*;import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;import net.minecraft.world.entity.player.Inventory;import net.minecraft.world.inventory.AbstractContainerMenu;import net.minecraft.world.item.*;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.Block;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.level.storage.*;
/** Explicit irreversible disposal; input is locked, cost contract frozen and excess waste retained. */
public final class FoupCleanerBlockEntity extends FabMachineBlockEntity {
 public static final int INPUT=0,WATER_BUCKET=1,CLEAN_OUTPUT=2,SCRAP_OUTPUT=3,BUCKET_OUTPUT=4;
 private static final int MAX_SCRAP=1000000;
 public final WaterTank water=new WaterTank(16000);
 public final CleanerWaterInput waterInput=new CleanerWaterInput(this);
 private int progress,duration,cost,totalScrap,pendingScrap;private boolean committed;
 private ItemStack accepted=ItemStack.EMPTY;
 public FoupCleanerBlockEntity(BlockPos p,BlockState s){super(FoupCleanerMachines.BE.get(),p,s,2,3);}
 @Override protected String machineId(){return "foup_cleaner";}
 public boolean live(){return level instanceof ServerLevel&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this;}
 public boolean hasJob(){return duration>0;}public int progress(){return progress;}public int duration(){return duration;}public int pendingScrap(){return pendingScrap;}
 private int param(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
 public int waterRequired(){return param("water_mb",1000);}
 public static boolean isFoup(ItemStack s){return s.is(ModItems.FOUP_MK1.get())||s.is(ModItems.FOUP_MK2.get());}
 private boolean sameInput(){return slot(INPUT).getCount()==1&&ItemStack.isSameItemSameComponents(slot(INPUT),accepted);}
 private boolean scrapSpace(){var s=slot(SCRAP_OUTPUT);return s.isEmpty()||s.is(FoupCleanerMachines.WAFER_SCRAP.get())&&s.getCount()<s.getMaxStackSize();}
 public int status(){if(hasJob()){if(!sameInput())return 7;if(committed||progress>=duration)return 3;if(buffer().getAmountAsLong()<cost)return 2;return 1;}if(definition().isEmpty())return 8;if(!isFoup(slot(INPUT)))return 4;var lot=FoupItems.trustedContentsOf(slot(INPUT));if(lot==null||lot.isEmpty())return 4;if(!slot(CLEAN_OUTPUT).isEmpty()||!scrapSpace())return 3;if(water.amount()<waterRequired())return 5;return 0;}
 public boolean start(){
  if(!live()||hasJob())return false;refreshDefinition();
  if(definition().isEmpty()||!isFoup(slot(INPUT))||slot(INPUT).getCount()!=1||!slot(CLEAN_OUTPUT).isEmpty()||!scrapSpace())return false;
  var lot=FoupItems.trustedContentsOf(slot(INPUT));if(lot==null||lot.isEmpty())return false;
  int ticks=param("duration",200),mb=waterRequired(),ratio=param("scrap_per_wafer",1),fe=definition().orElseThrow().energyPerTick(null,EUVCRAFTMod.settings()),scrap;
  try{scrap=FoupDisposal.scrapCount(lot,ratio);}catch(IllegalArgumentException|ArithmeticException invalid){return false;}
  if(ticks<1||ticks>72000||mb<1||mb>water.capacity()||water.amount()<mb||fe<0||fe>1000000||buffer().getAmountAsLong()<fe||scrap<1||scrap>MAX_SCRAP)return false;
  accepted=slot(INPUT).copy();duration=ticks;cost=fe;totalScrap=scrap;pendingScrap=0;progress=0;committed=false;water.drainExact(mb,false);setChanged();sync();return true;
 }
 private void resetJob(){duration=progress=cost=totalScrap=pendingScrap=0;committed=false;accepted=ItemStack.EMPTY;setChanged();}
 @Override public void serverTick(){
  if(!live())return;refreshDefinition();water.resize(Math.clamp(param("water_capacity_mb",16000),1000,WaterTank.MAX_CAPACITY));
  if(slot(WATER_BUCKET).is(Items.WATER_BUCKET)&&slot(WATER_BUCKET).getCount()==1&&water.capacity()-water.amount()>=1000&&(slot(BUCKET_OUTPUT).isEmpty()||slot(BUCKET_OUTPUT).is(Items.BUCKET)&&slot(BUCKET_OUTPUT).getCount()<slot(BUCKET_OUTPUT).getMaxStackSize())){
   water.fill(1000,false);clearSlot(WATER_BUCKET);if(slot(BUCKET_OUTPUT).isEmpty())setSlot(BUCKET_OUTPUT,new ItemStack(Items.BUCKET));else slot(BUCKET_OUTPUT).grow(1);setChanged();
  }
  if(hasJob()){
   if(!sameInput()){if(!committed)resetJob();sync();return;}
   if(definition().isEmpty()){sync();return;}
   if(!committed){
    if(progress<duration){if(energySource().draw(cost)!=cost){sync();return;}progress++;setChanged();}
    if(progress==duration&&!slot(CLEAN_OUTPUT).isEmpty()||progress==duration&&!scrapSpace()){sync();return;}
    if(progress==duration){var original=FoupItems.trustedContentsOf(slot(INPUT));if(original==null||original.isEmpty()){resetJob();sync();return;}var clean=slot(INPUT).copy();clean.set(ModDataComponents.FOUP_CONTENTS.get(),FoupDisposal.emptyCarrier(original,UUID.randomUUID()));setSlot(INPUT,clean);accepted=clean.copy();committed=true;pendingScrap=totalScrap;setChanged();}
   }
   if(committed){
    if(pendingScrap>0&&scrapSpace()){int available=slot(SCRAP_OUTPUT).isEmpty()?64:slot(SCRAP_OUTPUT).getMaxStackSize()-slot(SCRAP_OUTPUT).getCount();int n=Math.min(available,pendingScrap);if(slot(SCRAP_OUTPUT).isEmpty())setSlot(SCRAP_OUTPUT,new ItemStack(FoupCleanerMachines.WAFER_SCRAP.get(),n));else slot(SCRAP_OUTPUT).grow(n);pendingScrap-=n;setChanged();}
    if(pendingScrap==0&&slot(CLEAN_OUTPUT).isEmpty()){setSlot(CLEAN_OUTPUT,slot(INPUT));clearSlot(INPUT);resetJob();}
   }
  }sync();
 }
 private void sync(){data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());data().set(DATA_PROGRESS,progress);data().set(DATA_TICKS,duration);}
 @Override public boolean canPlaceItem(int i,ItemStack s){return i==INPUT&&!hasJob()&&isFoup(s)||i==WATER_BUCKET&&s.is(Items.WATER_BUCKET);}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inv,FabMachineBlockEntity be){return new FoupCleanerMenu(id,inv,this);}
 @Override protected void dropExtraContents(Level l,BlockPos p){if(l!=null&&!l.isClientSide()&&committed){while(pendingScrap>0){int n=Math.min(64,pendingScrap);Block.popResource(l,p,new ItemStack(FoupCleanerMachines.WAFER_SCRAP.get(),n));pendingScrap-=n;}}resetJob();}
 @Override protected void saveAdditional(ValueOutput o){super.saveAdditional(o);o.putInt("cleanerWater",water.amount());o.putInt("cleanerProgress",progress);o.putInt("cleanerDuration",duration);o.putInt("cleanerCost",cost);o.putInt("cleanerTotalScrap",totalScrap);o.putInt("cleanerPendingScrap",pendingScrap);o.putBoolean("cleanerCommitted",committed);if(hasJob())o.store("cleanerAccepted",ItemStack.CODEC,accepted);}
 @Override protected void loadAdditional(ValueInput i){super.loadAdditional(i);water.restoreAmount(Math.clamp(i.getIntOr("cleanerWater",0),0,WaterTank.MAX_CAPACITY));progress=i.getIntOr("cleanerProgress",0);duration=i.getIntOr("cleanerDuration",0);cost=i.getIntOr("cleanerCost",0);totalScrap=i.getIntOr("cleanerTotalScrap",0);pendingScrap=i.getIntOr("cleanerPendingScrap",0);committed=i.getBooleanOr("cleanerCommitted",false);accepted=i.read("cleanerAccepted",ItemStack.CODEC).orElse(ItemStack.EMPTY);
  if(duration<1||duration>72000||progress<0||progress>duration||cost<0||cost>1000000||totalScrap<1||totalScrap>MAX_SCRAP||pendingScrap<0||pendingScrap>totalScrap||committed&&progress!=duration||!committed&&pendingScrap!=0||!isFoup(accepted)||!sameInput())resetJob();sync();
 }
}
