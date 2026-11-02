package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.multiblock.euv.*;
import com.euvcraft.lithmod.mc.routing.LinkedFabItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
public final class EuvFabItemPortBlockEntity extends EuvScannerMemberBlockEntity {
    public EuvFabItemPortBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.EUV_ITEM_PORT_BE.get(),pos,state);}
    private boolean live(EuvScannerControllerBlockEntity owner){return level!=null&&!level.isClientSide()&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&controller()==owner&&isMemberOfFormedCell()&&!owner.isRemoved()&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&owner.isFormed()&&owner.members().contains(worldPosition);}
    public @Nullable ResourceHandler<ItemResource> items(){var owner=scanner();return owner!=null&&live(owner)?LinkedFabItems.view(owner.fabPortItems(),()->live(owner)):null;}
}
