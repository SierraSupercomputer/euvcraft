package com.euvcraft.lithmod.mc.machine.recycler;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
public final class ScrapRecyclerMenu extends FabMachineMenu {
    @Override protected void addDataSlots(net.minecraft.world.inventory.ContainerData data){super.addDataSlots(new com.euvcraft.lithmod.mc.cleanroom.WideContainerData(data));}
    public ScrapRecyclerMenu(int id,Inventory inventory,FabMachineBlockEntity machine){super(RecyclerMachines.MENU.get(),id,inventory,machine,1,2);}
    public ScrapRecyclerMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,(FabMachineBlockEntity)null);}
    @Override protected void addMachineSlots(){
        addCustomSlot(new Slot(machineSlots(),0,MACHINE_SLOT_X,MACHINE_SLOT_Y){
            @Override public boolean mayPlace(ItemStack stack){return blockEntity==null||((ScrapRecyclerBlockEntity)blockEntity).acceptsInput(stack);}
            @Override public boolean mayPickup(Player player){return blockEntity==null||!blockEntity.runner().isRunning();}
        });
        addOutputSlot(1,MACHINE_SLOT_X+24,MACHINE_SLOT_Y);
    }
}
