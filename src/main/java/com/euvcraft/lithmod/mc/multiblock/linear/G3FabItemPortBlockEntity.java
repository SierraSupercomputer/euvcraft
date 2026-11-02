package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import com.euvcraft.lithmod.mc.routing.LinkedFabItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
public final class G3FabItemPortBlockEntity extends G3MemberBlockEntity {
    public G3FabItemPortBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.G3_ITEM_PORT_BE.get(),pos,state);}
    @Override public BlockKind structureKind(){return BlockKind.CASING;}
    @Override public boolean supportsOwner(G3ControllerBlockEntity owner){return owner.large();}
    private boolean live(G3ControllerBlockEntity owner){return level!=null&&!level.isClientSide()&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&controller()==owner&&isMemberOfFormedMachine()&&!owner.isRemoved()&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&owner.isFormed()&&owner.members().contains(worldPosition);}
    public @Nullable ResourceHandler<ItemResource> items(){var owner=controller();return owner!=null&&live(owner)?LinkedFabItems.view(owner.fabPortItems(),()->live(owner)):null;}
}
