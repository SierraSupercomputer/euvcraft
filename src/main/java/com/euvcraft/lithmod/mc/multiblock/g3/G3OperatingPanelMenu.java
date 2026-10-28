package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
public final class G3OperatingPanelMenu extends G3Menu {
    public G3OperatingPanelMenu(int id,Inventory inventory,G3ControllerBlockEntity controller,BlockPos panel){super(G3Machines.OPERATING_PANEL_MENU.get(),id,inventory,controller,panel.below(),true,controller.getKind());}
    private G3OperatingPanelMenu(int id,Inventory inventory,BlockPos panel,G3MachineKind kind){super(G3Machines.OPERATING_PANEL_MENU.get(),id,inventory,null,panel.below(),true,kind);}
    public G3OperatingPanelMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,buffer.readBlockPos(),buffer.readEnum(G3MachineKind.class));}
    @Override protected void addMachineSlots(){if(machineSlots().getContainerSize()==3){addMachineSlot(0,44,35);addMachineSlot(1,80,35);addOutputSlot(2,116,35);}else{addFoupSlot(0,62,35);addOutputSlot(3,98,35);}}
}

