package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
public final class G3DepositionEtcherMenu extends G3Menu {
    public G3DepositionEtcherMenu(int id,Inventory inventory,G3ControllerBlockEntity controller){super(G3Machines.DEPOSITION_ETCHER_MENU.get(),id,inventory,controller,controller.getBlockPos(),false,G3MachineKind.DEPOSITION_ETCHER);}
    public G3DepositionEtcherMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){super(G3Machines.DEPOSITION_ETCHER_MENU.get(),id,inventory,null,buffer.readBlockPos(),false,G3MachineKind.DEPOSITION_ETCHER);}
    @Override protected void addMachineSlots(){addMachineSlot(1,62,35);addMachineSlot(2,98,35);}
}

