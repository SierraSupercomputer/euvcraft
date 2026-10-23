package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
public final class CleanroomMenu extends FabMachineMenu {
 private final ContainerData room;
 private final boolean filter;
 public CleanroomMenu(int id,Inventory inv,FabMachineBlockEntity machine,boolean filter){
  super(CleanroomBlocks.MENU.get(),id,inv,machine,filter?1:0,filter?1:0);this.filter=filter;
  room=machine==null?new SimpleContainerData(14):new ContainerData(){public int get(int i){
   if(machine instanceof CleanroomFilterBlockEntity f)return switch(i){case 0->1;case 1->f.remaining()/20;case 2->f.working()?1:0;default->0;};
   var r=(CleanroomControllerBlockEntity)machine;var b=r.bounds();return switch(i){case 0->r.state();case 1->r.cleanliness()/100;case 2->r.ppm();case 3->r.roomYield();case 4->b==null?0:b.volume();case 5->r.activeFilters();case 6->r.filterCount();case 7->r.doors();case 8->r.powered()?1:0;case 9->r.trend();case 10->b==null?0:b.maxX()-b.minX()-1;case 11->b==null?0:b.maxY()-b.minY()-1;case 12->b==null?0:b.maxZ()-b.minZ()-1;case 13->r.trend()>0?(1000000-r.cleanliness()+r.trend()-1)/r.trend():-1;default->0;};}
   public void set(int i,int v){}public int getCount(){return 14;}};addDataSlots(room);
 }
 public CleanroomMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,null,readFilter(buf));}
 private static boolean readFilter(RegistryFriendlyByteBuf buf){buf.readBlockPos();return buf.readBoolean();}
 @Override protected void addMachineSlots(){if(machineSlots().getContainerSize()>0)addCustomSlot(new Slot(machineSlots(),0,80,35){@Override public boolean mayPlace(ItemStack s){return s.is(CleanroomItems.MEDIUM.get());}});}
 public boolean filter(){return filter;}public int value(int i){return room.get(i);}
}
