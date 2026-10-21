package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.*;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
/** Upper panel owns no extra inventory. Slot counts are identical on both sides. */
public class AdvancedOperatingPanelMenu extends FabMachineMenu {
 private final BlockPos corePos;
 private final @Nullable AdvancedMemberBlockEntity panel;
 private final ContainerData formation,queue,mode;
 private final boolean deposition;
 private record Description(BlockPos core,boolean deposition){}
 public AdvancedOperatingPanelMenu(int id,Inventory inventory,@Nullable FabMachineBlockEntity machine,BlockPos core){
  this(id,inventory,machine,new Description(core,machine instanceof DepositionEtcherControllerBlockEntity));
 }
 private AdvancedOperatingPanelMenu(int id,Inventory inventory,@Nullable FabMachineBlockEntity machine,Description description){
  super(AdvancedMachines.ADVANCED_OPERATING_PANEL_MENU.get(),id,inventory,machine,2,description.deposition()?3:8);
  corePos=description.core().immutable();deposition=description.deposition();
  panel=machine!=null&&machine.getLevel().getBlockEntity(corePos.above()) instanceof AdvancedMemberBlockEntity p?p:null;
  formation=machine instanceof AdvancedOwner owner?owner.formation().data():new SimpleContainerData(1);
  queue=machine instanceof TestPackageBlockEntity pack?pack.queueData():new SimpleContainerData(3);
  mode=machine instanceof AdvancedTestPackageControllerBlockEntity pack?pack.binningData():new SimpleContainerData(1);
  addDataSlots(formation);addDataSlots(queue);addDataSlots(mode);
 }
 public AdvancedOperatingPanelMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){
  this(id,inventory,null,new Description(buffer.readBlockPos().below(),buffer.readBoolean()));
 }
 @Override protected void addMachineSlots(){
  addFoupSlot(0,38,35);
  addCustomSlot(new Slot(machineSlots(),1,38,53){
   @Override public boolean mayPlace(ItemStack stack){
    return machineSlots().getContainerSize()==3?stack.is(ModItems.SILICON.get()):TestPackageBlockEntity.isPackage(stack);
   }
  });
  if(machineSlots().getContainerSize()==3)addOutputSlot(2,98,44);
  else {
   for(int n=0;n<4;n++)addOutputSlot(2+n,70+n*18,35);
   for(int n=0;n<2;n++)addOutputSlot(6+n,70+n*18,53);
  }
 }
 public boolean deposition(){return deposition;}
 public boolean formed(){return formation.get(0)==1;}
 public boolean highBinning(){return mode.get(0)==1;}
 public boolean active(){return !deposition&&wafersTotal()>wafersDone();}
 public int state(){return queue.get(0);}
 public int wafersDone(){return queue.get(1);}
 public int wafersTotal(){return queue.get(2);}
 @Override public boolean clickMenuButton(Player player,int button){
  if(button!=1||!(player instanceof net.minecraft.server.level.ServerPlayer)||player.containerMenu!=this||!formed()||!stillValid(player)||!(blockEntity instanceof AdvancedTestPackageControllerBlockEntity pack))return false;
  return pack.toggleHighBinning();
 }
 @Override public boolean stillValid(Player player){
  return super.stillValid(player)&&panel!=null&&blockEntity.getBlockPos().equals(corePos)
   &&blockEntity.getLevel().getBlockEntity(corePos.above())==panel
   &&panel.getBlockState().getBlock()==AdvancedMachines.ADVANCED_OPERATING_PANEL.get()
   &&player.distanceToSqr(corePos.getX()+.5,corePos.getY()+1.5,corePos.getZ()+.5)<=64;
 }
}
