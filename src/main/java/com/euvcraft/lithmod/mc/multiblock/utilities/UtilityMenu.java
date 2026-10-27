package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.machine.*;import net.minecraft.world.entity.player.Inventory;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.world.inventory.*;import org.jspecify.annotations.Nullable;
public final class UtilityMenu extends FabMachineMenu{
 private final ContainerData tanks;
 // Tank indices 0..4 are the original tank wire; 5..10 carry the batch snapshot.
 // ContainerData syncs ints as-is, so mB and FE values survive: no 16-bit clamp on this path.
 public static final int TANK_WIRE_FORMED=0,TANK_WIRE_RAW=1,TANK_WIRE_RAW_CAPACITY=2,TANK_WIRE_PURE=3,TANK_WIRE_PURE_CAPACITY=4;
 public static final int TANK_WIRE_ACTIVE=5,TANK_WIRE_BATCH_WATER=6,TANK_WIRE_BATCH_OUTPUT=7,TANK_WIRE_BATCH_FILTERS=8,TANK_WIRE_BATCH_FE=9,TANK_WIRE_BATCH_TICKS=10;
 public static final int TANK_WIRE_COUNT=11;
 public UtilityMenu(int id,Inventory inv,@Nullable UtilityControllerBlockEntity machine){super(UtilityMachines.MENU.get(),id,inv,machine,2,3);tanks=machine==null?new SimpleContainerData(TANK_WIRE_COUNT):new ContainerData(){public int get(int i){return switch(i){case 0->machine.isFormed()?1:0;case 1->machine.raw.amount();case 2->machine.raw.capacity();case 3->machine.pure.amount();case 4->machine.pure.capacity();case 5->machine.hasJob()?1:0;case 6->machine.batchWaterMb();case 7->machine.batchOutputMb();case 8->machine.batchFilters();case 9->machine.batchFePerTick();case 10->machine.batchTicks();default->0;};}public void set(int i,int v){}public int getCount(){return TANK_WIRE_COUNT;}};addDataSlots(tanks);}
 public UtilityMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,(UtilityControllerBlockEntity)null);}
 @Override protected void addMachineSlots(){addCustomSlot(new Slot(machineSlots(),0,35,35){@Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack){return stack.is(com.euvcraft.lithmod.mc.registry.ModItems.UTILITY_FILTER.get());}});addCustomSlot(new Slot(machineSlots(),1,80,35){@Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack){return stack.is(net.minecraft.world.item.Items.WATER_BUCKET);}});addMachineSlot(2,125,35,false);}
 public boolean formed(){return tanks.get(0)==1;}public int raw(){return tanks.get(1);}public int rawCapacity(){return tanks.get(2);}public int pure(){return tanks.get(3);}public int pureCapacity(){return tanks.get(4);}
 /** Viewer-facing batch state, all read back from the same synced ints the server wrote. */
 public boolean active(){return tanks.get(TANK_WIRE_ACTIVE)==1;}
 /** Current or next batch inputs/costs: admitted job values while it runs, settings otherwise. */
 public int batchWaterMb(){return tanks.get(TANK_WIRE_BATCH_WATER);}
 public int batchOutputMb(){return tanks.get(TANK_WIRE_BATCH_OUTPUT);}
 public int batchFilters(){return tanks.get(TANK_WIRE_BATCH_FILTERS);}
 public int batchFePerTick(){return tanks.get(TANK_WIRE_BATCH_FE);}
 public int batchTicks(){return tanks.get(TANK_WIRE_BATCH_TICKS);}
 /** Stacks in machine slot 0 on this side (the client mirrors the server stack), for the ready check. */
 public int filterCount(){return slots.isEmpty()?0:slots.get(0).getItem().getCount();}
}

