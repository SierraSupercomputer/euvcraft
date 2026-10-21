package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
/** Shared deposition controller base; packaging composes the same formation separately. */
public abstract class AdvancedControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner,EnergyBufferProvider {
    private final AdvancedFormation formation=new AdvancedFormation(this);
    protected AdvancedControllerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,int inputs,int outputs){
        super(type,pos,state,inputs,outputs);
    }
    public final FabMachineBlockEntity machine(){return this;}
    public final AdvancedFormation formation(){return formation;}
    public boolean large(){return true;}
    @Override public void serverTick(){
        if(level==null||level.isClientSide())return;
        refreshDefinition();formation.validate();
        data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());
    }
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity ignored){
        return new DepositionEtcherMenu(id,inventory,this);
    }
    @Override public void setRemoved(){formation.detach();super.setRemoved();}
}
