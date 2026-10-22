package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
/** G2 owns one existing packaging queue; no parallel inventory or yield system. */
public class AdvancedTestPackageControllerBlockEntity extends TestPackageBlockEntity implements AdvancedOwner,EnergyBufferProvider {
    private boolean highBinning;
    private final net.minecraft.world.inventory.ContainerData binningData=new net.minecraft.world.inventory.SimpleContainerData(1);
    public net.minecraft.world.inventory.ContainerData binningData(){return binningData;}
    public boolean highBinningEnabled(){return highBinning;}
    public boolean toggleHighBinning(){
        if(isQueueActive())return false;
        highBinning=!highBinning;binningData.set(0,highBinning?1:0);setChanged();return true;
    }
    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output){super.saveAdditional(output);output.putBoolean("t4_high_binning",highBinning);}
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input){super.loadAdditional(input);highBinning=input.getBooleanOr("t4_high_binning",false);binningData.set(0,highBinning?1:0);}
    private final AdvancedFormation formation=new AdvancedFormation(this);
    public AdvancedTestPackageControllerBlockEntity(BlockPos pos,BlockState state){
        super(AdvancedMachines.ADVANCED_TEST_PACKAGE_CONTROLLER_BE.get(),pos,state,INPUT_SLOTS,OUTPUT_SLOTS);
    }
    @Override protected String machineId(){return "advanced_test_package";}
    @Override public int installedGeneration(){return 2;}
    @Override protected boolean processingAllowed(){return isFormed();}
    public FabMachineBlockEntity machine(){return this;}
    public AdvancedFormation formation(){return formation;}
    public boolean large(){return false;}
    @Override public void serverTick(){
        if(level==null||level.isClientSide())return;
        formation.validate();super.serverTick();
    }
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity ignored){
        return new AdvancedTestPackageMenu(id,inventory,this);
    }
    @Override public void setRemoved(){formation.unload();super.setRemoved();}
}
