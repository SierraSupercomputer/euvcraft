package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
public final class G3EtchCellBlockEntity extends G3ControllerBlockEntity {
    public G3EtchCellBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.G3_ETCH_BE.get(),pos,state,G3MachineKind.DEPOSITION_ETCHER,3,1);}
    @Override protected String machineId(){return "g3_etch_cell";}
    @Override protected boolean etching(){return true;}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity ignored){return new G3DepositionEtcherMenu(id,inventory,this);}
}
