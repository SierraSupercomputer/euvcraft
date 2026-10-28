package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
public final class G3DepositionEtcherControllerBlockEntity extends G3ControllerBlockEntity {
    public static final int SLOT_FOUP_INPUT=0,SLOT_FOUP_OUTPUT=3;
    public G3DepositionEtcherControllerBlockEntity(BlockPos pos,BlockState state){super(G3Machines.DEPOSITION_ETCHER_CONTROLLER_BE.get(),pos,state,G3MachineKind.DEPOSITION_ETCHER,3,1);}
    @Override protected String machineId(){return getKind().id();}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new G3DepositionEtcherMenu(id,inventory,this);}
}

