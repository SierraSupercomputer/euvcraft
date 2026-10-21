package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;
public class AdvancedTestPackageMenu extends FabMachineMenu {
 private final ContainerData formation;
 public AdvancedTestPackageMenu(int id,Inventory inventory,@Nullable FabMachineBlockEntity machine){
  super(AdvancedMachines.ADVANCED_TEST_PACKAGE_MENU.get(),id,inventory,machine,2,8);
  formation=machine instanceof AdvancedOwner owner?owner.formation().data():new SimpleContainerData(1);
  addDataSlots(formation);
 }
 public AdvancedTestPackageMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,(FabMachineBlockEntity)null);}
 @Override protected void addMachineSlots(){}
 public boolean formed(){return formation.get(0)==1;}
}
