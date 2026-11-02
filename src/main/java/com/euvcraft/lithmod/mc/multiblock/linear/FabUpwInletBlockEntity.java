package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
public final class FabUpwInletBlockEntity extends AdvancedMemberBlockEntity {
    public FabUpwInletBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.UPW_INLET_BE.get(),pos,state);}
    @Override public BlockKind structureKind(){return BlockKind.WATER_INLET;}
    @Override public boolean supportsOwner(AdvancedOwner candidate){return candidate instanceof LinearStageBlockEntity;}
    private boolean live(LinearStageBlockEntity owner){return level!=null&&!level.isClientSide()&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&controller()==owner&&isMemberOfFormedCell()&&!owner.isRemoved()&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&owner.isFormed()&&owner.members().contains(worldPosition);}
    public @Nullable ResourceHandler<FluidResource> fluid(){
        if(!(controller() instanceof LinearStageBlockEntity owner)||!live(owner))return null;
        var upw=FluidResource.of(UtilityFluids.ULTRAPURE_WATER.get());
        return new ResourceHandler<>(){
            private boolean slot(int i){return i==0&&live(owner);}
            public int size(){return live(owner)?1:0;}
            public FluidResource getResource(int i){return slot(i)&&owner.upw.amount()>0?upw:FluidResource.EMPTY;}
            public long getAmountAsLong(int i){return slot(i)?owner.upw.amount():0;}
            public long getCapacityAsLong(int i,FluidResource r){return slot(i)&&(r.isEmpty()||r.equals(upw))?owner.upw.capacity():0;}
            public boolean isValid(int i,FluidResource r){return slot(i)&&r.equals(upw);}
            public int insert(int i,FluidResource r,int n,TransactionContext tx){if(n<=0||!slot(i)||!r.equals(upw))return 0;int amount=owner.upw.fill(n,true);if(amount>0){owner.waterJournal.updateSnapshots(tx);owner.upw.fill(amount,false);}return amount;}
            public int extract(int i,FluidResource r,int n,TransactionContext tx){return 0;}
        };
    }
}
