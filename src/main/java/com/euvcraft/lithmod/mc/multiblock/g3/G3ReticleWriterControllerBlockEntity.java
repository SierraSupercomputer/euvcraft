package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
public final class G3ReticleWriterControllerBlockEntity extends G3ControllerBlockEntity {
    public static final int SLOT_BLANK_MASK=0,SLOT_REDSTONE=1,SLOT_RETICLE_OUTPUT=2;
    public G3ReticleWriterControllerBlockEntity(BlockPos pos,BlockState state){super(G3Machines.RETICLE_WRITER_CONTROLLER_BE.get(),pos,state,G3MachineKind.RETICLE_WRITER,2,1);}
    @Override protected String machineId(){return "g3_reticle_writer";}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new G3ReticleWriterMenu(id,inventory,this);}
}

