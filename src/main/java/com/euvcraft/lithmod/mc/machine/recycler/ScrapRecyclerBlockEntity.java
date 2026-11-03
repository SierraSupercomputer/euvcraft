package com.euvcraft.lithmod.mc.machine.recycler;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.routing.LinkedFabItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Uses the existing saved, paid recipe runner; recipes and recovery ratios live in JSON. */
public final class ScrapRecyclerBlockEntity extends FabMachineBlockEntity {
    private final ResourceHandler<ItemResource> items=LinkedFabItems.view(fabPortItems(),this::live);
    public ScrapRecyclerBlockEntity(BlockPos pos,BlockState state){super(RecyclerMachines.BLOCK_ENTITY.get(),pos,state,1,1);}
    @Override protected String machineId(){return "scrap_recycler";}
    public boolean acceptsInput(ItemStack stack){
        if(stack.isEmpty()||runner().isRunning())return false;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return recipes().stream().filter(r->r.inputSlotCount()==1).anyMatch(r->{var ingredient=r.spec().ingredients().getFirst();return ingredient.matches(id,ingredient.count(),r.tagMembers(ingredient.id()));});
    }
    private boolean live(){return !isRemoved()&&level!=null&&level.getBlockEntity(worldPosition)==this&&level.getBlockState(worldPosition).is(RecyclerMachines.BLOCK.get());}
    public ResourceHandler<ItemResource> items(){return items;}
    @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity ignored){return new ScrapRecyclerMenu(id,inventory,this);}
}
