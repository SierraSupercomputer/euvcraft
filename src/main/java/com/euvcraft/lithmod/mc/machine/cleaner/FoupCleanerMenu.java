package com.euvcraft.lithmod.mc.machine.cleaner;
import com.euvcraft.lithmod.mc.machine.*;import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.world.entity.player.*;import net.minecraft.world.inventory.*;import net.minecraft.world.item.*;
public final class FoupCleanerMenu extends FabMachineMenu {
 private final ContainerData extra;
 @Override protected void addDataSlots(ContainerData data){super.addDataSlots(new WideContainerData(data));}
 public FoupCleanerMenu(int id,Inventory inv,FoupCleanerBlockEntity machine){super(FoupCleanerMachines.MENU.get(),id,inv,machine,2,5);extra=machine==null?new SimpleContainerData(6):new ContainerData(){public int getCount(){return 6;}public void set(int i,int n){}public int get(int i){return switch(i){case 0->machine.hasJob()?1:0;case 1->machine.status();case 2->machine.water.amount();case 3->machine.water.capacity();case 4->machine.pendingScrap();case 5->machine.waterRequired();default->0;};}};addDataSlots(extra);}
 public FoupCleanerMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,(FoupCleanerBlockEntity)null);buf.readBlockPos();}
 public int value(int i){return extra.get(i);}public boolean locked(){return blockEntity instanceof FoupCleanerBlockEntity c?c.hasJob():value(0)==1;}
 @Override protected void addMachineSlots(){addCustomSlot(new Slot(machineSlots(),0,62,35){public boolean mayPlace(ItemStack s){return !locked()&&FoupCleanerBlockEntity.isFoup(s);}public boolean mayPickup(Player p){return !locked();}public int getMaxStackSize(){return 1;}});addCustomSlot(new Slot(machineSlots(),1,104,35){public boolean mayPlace(ItemStack s){return s.is(Items.WATER_BUCKET);}public int getMaxStackSize(){return 1;}});addOutputSlot(2,146,35);addOutputSlot(3,188,35);addOutputSlot(4,230,35);}
 @Override public ItemStack quickMoveStack(Player p,int index){return index==0&&locked()?ItemStack.EMPTY:super.quickMoveStack(p,index);}
 @Override public boolean clickMenuButton(Player p,int id){return id==0&&p.containerMenu==this&&stillValid(p)&&blockEntity instanceof FoupCleanerBlockEntity c&&c.start();}
}
