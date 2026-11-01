package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Optional casing replacement; capabilities reject stale links and unloaded/replaced owners. */
public final class FabItemPortBlockEntity extends AdvancedMemberBlockEntity {
    public FabItemPortBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.ITEM_PORT_BE.get(),pos,state);}
    @Override public BlockKind structureKind(){return BlockKind.CASING;}
    private boolean live(AdvancedOwner expected){
        return level!=null&&!level.isClientSide()&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this
            &&controller()==expected&&isMemberOfFormedCell()&&!expected.machine().isRemoved()&&level.hasChunkAt(expected.machine().getBlockPos())
            &&level.getBlockEntity(expected.machine().getBlockPos())==expected.machine()&&expected.isFormed()&&expected.members().contains(worldPosition);
    }
    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side){
        var expected=controller();if(expected==null||!live(expected))return null;
        var delegate=expected.machine().fabPortItems();
        return new ResourceHandler<>(){
            public int size(){return live(expected)?delegate.size():0;}
            public ItemResource getResource(int i){return live(expected)?delegate.getResource(i):ItemResource.EMPTY;}
            public long getAmountAsLong(int i){return live(expected)?delegate.getAmountAsLong(i):0;}
            public long getCapacityAsLong(int i,ItemResource r){return live(expected)?delegate.getCapacityAsLong(i,r):0;}
            public boolean isValid(int i,ItemResource r){return live(expected)&&delegate.isValid(i,r);}
            public int insert(int i,ItemResource r,int n,TransactionContext tx){return live(expected)?delegate.insert(i,r,n,tx):0;}
            public int extract(int i,ItemResource r,int n,TransactionContext tx){return live(expected)?delegate.extract(i,r,n,tx):0;}
        };
    }
}
