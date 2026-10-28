package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
public final class G3ReticleWriterMenu extends G3Menu {
    public G3ReticleWriterMenu(int id,Inventory inventory,G3ControllerBlockEntity controller){super(G3Machines.RETICLE_WRITER_MENU.get(),id,inventory,controller,controller.getBlockPos(),false,G3MachineKind.RETICLE_WRITER);}
    public G3ReticleWriterMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){super(G3Machines.RETICLE_WRITER_MENU.get(),id,inventory,null,buffer.readBlockPos(),false,G3MachineKind.RETICLE_WRITER);}
    @Override protected void addMachineSlots(){}
}

