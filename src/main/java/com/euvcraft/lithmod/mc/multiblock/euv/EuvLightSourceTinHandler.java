package com.euvcraft.lithmod.mc.multiblock.euv;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import java.util.Objects;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
/** Transactional item inlet for real tin ingots; accepts no fluids. */
public final class EuvLightSourceTinHandler implements ResourceHandler<ItemResource>{
    private final EuvLightSourceMemberBlockEntity port;private final EuvLightSourceControllerBlockEntity owner;
    public EuvLightSourceTinHandler(EuvLightSourceMemberBlockEntity port,EuvLightSourceControllerBlockEntity owner){this.port=port;this.owner=owner;}
    private boolean live(){var level=owner.getLevel();if(level==null||level.isClientSide()||owner.isRemoved()||port.isRemoved()||port.getLevel()!=level||level.getBlockEntity(owner.getBlockPos())!=owner||level.getBlockEntity(port.getBlockPos())!=port||port.structureKind()!=BlockKind.FLUID_OUTLET||port.controller()!=owner)return false;owner.revalidate();return owner.isFormed()&&port.controller()==owner&&owner.members().contains(port.getBlockPos());}
    @Override public int size(){return 1;}
    @Override public ItemResource getResource(int i){Objects.checkIndex(i,1);return live()?ItemResource.of(owner.slot(0)):ItemResource.EMPTY;}
    @Override public long getAmountAsLong(int i){Objects.checkIndex(i,1);return live()?owner.slot(0).getCount():0;}
    @Override public long getCapacityAsLong(int i,ItemResource resource){Objects.checkIndex(i,1);return live()&&(resource.isEmpty()||owner.acceptsTin(resource))?(resource.isEmpty()?64:resource.getMaxStackSize()):0;}
    @Override public boolean isValid(int i,ItemResource resource){Objects.checkIndex(i,1);return live()&&owner.acceptsTin(resource);}
    @Override public int insert(int i,ItemResource resource,int amount,TransactionContext transaction){Objects.checkIndex(i,1);Objects.requireNonNull(resource);Objects.requireNonNull(transaction);return live()?owner.insertTin(resource,amount,transaction):0;}
    @Override public int extract(int i,ItemResource resource,int amount,TransactionContext transaction){Objects.checkIndex(i,1);return 0;}
}
