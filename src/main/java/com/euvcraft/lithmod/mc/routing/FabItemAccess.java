package com.euvcraft.lithmod.mc.routing;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.FabProgress;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** One shared transactional wrapper per controller; ordinary blocks still have no item capability. */
public final class FabItemAccess {
    public static ResourceHandler<ItemResource> create(FabMachineBlockEntity machine){
        var proxy=new Container(){
            public int getContainerSize(){return machine instanceof ProcessCellControllerBlockEntity?3:machine.getContainerSize();}
            public boolean isEmpty(){for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;}
            public ItemStack getItem(int i){return machine.getItem(i);}
            public ItemStack removeItem(int i,int n){return machine.removeItem(i,n);}
            public ItemStack removeItemNoUpdate(int i){return machine.removeItemNoUpdate(i);}
            public void setItem(int i,ItemStack stack){machine.setItem(i,stack);}
            public void setChanged(){machine.setChanged();}
            public boolean stillValid(Player player){return !machine.isRemoved();}
            public void clearContent(){throw new UnsupportedOperationException("Port cannot clear controller inventory");}
            public boolean canPlaceItem(int i,ItemStack stack){return canInsert(machine,i,stack);}
            public boolean canTakeItem(Container into,int i,ItemStack stack){return canExtract(machine,i);}
        };
        var delegate=VanillaContainerWrapper.ofUncached(proxy);
        return new ResourceHandler<>(){
            private boolean slot(int i){return !machine.isRemoved()&&i>=0&&i<delegate.size();}
            public int size(){return machine.isRemoved()?0:delegate.size();}
            public ItemResource getResource(int i){return slot(i)?delegate.getResource(i):ItemResource.EMPTY;}
            public long getAmountAsLong(int i){return slot(i)?delegate.getAmountAsLong(i):0;}
            public long getCapacityAsLong(int i,ItemResource r){return slot(i)?delegate.getCapacityAsLong(i,r):0;}
            public boolean isValid(int i,ItemResource r){return slot(i)&&delegate.isValid(i,r);}
            public int insert(int i,ItemResource r,int n,TransactionContext tx){return slot(i)&&delegate.isValid(i,r)?delegate.insert(i,r,n,tx):0;}
            public int extract(int i,ItemResource r,int n,TransactionContext tx){return slot(i)&&canExtract(machine,i)?delegate.extract(i,r,n,tx):0;}
        };
    }

    private static boolean canInsert(FabMachineBlockEntity machine,int slot,ItemStack stack){
        if(machine.isRemoved()||stack.isEmpty())return false;
        if(machine instanceof com.euvcraft.lithmod.mc.machine.recycler.ScrapRecyclerBlockEntity recycler)return slot==0&&recycler.acceptsInput(stack);
        if(machine instanceof com.euvcraft.lithmod.mc.multiblock.linear.LinearStageBlockEntity stage){
            return slot==0?stage.canReceiveCarrier(stack):slot==1?stage.acceptsReagent(stack):slot==2&&stage.requiresRepair()&&stack.is(net.minecraft.world.item.Items.REDSTONE);
        }
        if(machine instanceof com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlockEntity g3){
            return slot==0?g3.canReceiveCarrier(stack):g3.acceptsPrepReagent(slot,stack)
                ||g3.contaminationState().requiresRepair()&&(slot==1&&stack.is(ModItems.UTILITY_FILTER.get())||slot==2&&stack.is(net.minecraft.world.item.Items.REDSTONE));
        }
        if(machine instanceof DepositionEtcherControllerBlockEntity process){
            return slot==0?process.canReceiveCarrier(stack):slot==1&&process.acceptsReagent(stack);
        }
        if(machine instanceof ProcessCellControllerBlockEntity scanner){
            if(slot==0){
                var lot=FoupItems.trustedContentsOf(stack);
                return !scanner.inputsLocked()&&lot!=null&&!lot.isEmpty()&&!lot.isStarted()&&!lot.isContaminated()&&lot.isPrepared();
            }
            if(slot==1)return !scanner.inputsLocked()&&stack.is(ModItems.MASK.get())&&stack.has(ModDataComponents.MASK_DATA.get());
            return slot==2&&!machine.slot(0).has(ModDataComponents.COATING_RECEIPT.get())&&ResistCatalog.tierOf(stack)>0&&scanner.canTopUpResist(stack);
        }
        if(machine instanceof TestPackageBlockEntity){
            if(slot==1)return TestPackageBlockEntity.isPackage(stack);
            var lot=FoupItems.trustedContentsOf(stack);
            var route=stack.get(ModDataComponents.FOUP_ROUTING.get());
            return slot==0&&lot!=null&&!lot.isEmpty()&&!lot.isContaminated()&&lot.run()!=null&&route!=null&&route.progress().stage().ordinal()>=FabProgress.Stage.EXPOSED.ordinal()&&FoupRouting.packagingReady(stack)
                &&(!(machine instanceof com.euvcraft.lithmod.mc.multiblock.advanced.HighBinTestPackageBlockEntity)
                    ||com.euvcraft.lithmod.mc.data.HighBinningDataLoader.supportsSource(lot.run()));
        }
        return false;
    }

    private static boolean canExtract(FabMachineBlockEntity machine,int slot){
        if(machine.isRemoved())return false;
        if(machine instanceof ProcessCellControllerBlockEntity scanner)return slot==0&&scanner.processState()==ProcessState.FINISHED;
        if(machine instanceof TestPackageBlockEntity&&slot==0){
            var returned=FoupItems.trustedContentsOf(machine.slot(0));
            return returned!=null&&returned.isEmpty();
        }
        return slot>=machine.inputSlotCount()&&slot<machine.getContainerSize();
    }
    private FabItemAccess(){}
}
