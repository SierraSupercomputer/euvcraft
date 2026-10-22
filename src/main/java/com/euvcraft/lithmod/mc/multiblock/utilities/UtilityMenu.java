package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.machine.*;import net.minecraft.world.entity.player.Inventory;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.world.inventory.*;import org.jspecify.annotations.Nullable;
public final class UtilityMenu extends FabMachineMenu{
 private final ContainerData tanks;
 public UtilityMenu(int id,Inventory inv,@Nullable UtilityControllerBlockEntity machine){super(UtilityMachines.MENU.get(),id,inv,machine,2,3);tanks=machine==null?new SimpleContainerData(5):new ContainerData(){public int get(int i){return switch(i){case 0->machine.isFormed()?1:0;case 1->machine.raw.amount();case 2->machine.raw.capacity();case 3->machine.pure.amount();case 4->machine.pure.capacity();default->0;};}public void set(int i,int v){}public int getCount(){return 5;}};addDataSlots(tanks);}
 public UtilityMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,(UtilityControllerBlockEntity)null);}
 @Override protected void addMachineSlots(){addCustomSlot(new Slot(machineSlots(),0,35,35){@Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack){return stack.is(com.euvcraft.lithmod.mc.registry.ModItems.UTILITY_FILTER.get());}});addCustomSlot(new Slot(machineSlots(),1,80,35){@Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack){return stack.is(net.minecraft.world.item.Items.WATER_BUCKET);}});addMachineSlot(2,125,35,false);}
 public boolean formed(){return tanks.get(0)==1;}public int raw(){return tanks.get(1);}public int rawCapacity(){return tanks.get(2);}public int pure(){return tanks.get(3);}public int pureCapacity(){return tanks.get(4);}
}

