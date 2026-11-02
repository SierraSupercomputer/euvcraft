package com.euvcraft.lithmod.mc.routing;
import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
public final class LinkedFabItems {
    public static ResourceHandler<ItemResource> view(ResourceHandler<ItemResource> delegate,BooleanSupplier live){return new ResourceHandler<>(){
        public int size(){return live.getAsBoolean()?delegate.size():0;}
        public ItemResource getResource(int i){return live.getAsBoolean()?delegate.getResource(i):ItemResource.EMPTY;}
        public long getAmountAsLong(int i){return live.getAsBoolean()?delegate.getAmountAsLong(i):0;}
        public long getCapacityAsLong(int i,ItemResource r){return live.getAsBoolean()?delegate.getCapacityAsLong(i,r):0;}
        public boolean isValid(int i,ItemResource r){return live.getAsBoolean()&&delegate.isValid(i,r);}
        public int insert(int i,ItemResource r,int n,TransactionContext tx){return live.getAsBoolean()?delegate.insert(i,r,n,tx):0;}
        public int extract(int i,ItemResource r,int n,TransactionContext tx){return live.getAsBoolean()?delegate.extract(i,r,n,tx):0;}
    };}
    private LinkedFabItems(){}
}
