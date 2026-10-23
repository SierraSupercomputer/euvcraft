package com.euvcraft.lithmod.mc.cleanroom;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.*;
/** A live single-slot transaction journal; media wear is separate persistent state. */
public final class CleanroomFilterItems extends ItemStackResourceHandler {
 private final CleanroomFilterBlockEntity owner;
 public CleanroomFilterItems(CleanroomFilterBlockEntity owner){this.owner=owner;}
 private boolean live(){var l=owner.getLevel();return l!=null&&!l.isClientSide()&&!owner.isRemoved()&&l.hasChunkAt(owner.getBlockPos())&&l.getBlockEntity(owner.getBlockPos())==owner;}
 @Override protected ItemStack getStack(){return live()?owner.slot(0):ItemStack.EMPTY;}
 @Override protected void setStack(ItemStack stack){if(live())owner.setSlot(0,stack);}
 @Override protected boolean isValid(ItemResource resource){return live()&&resource.is(CleanroomItems.MEDIUM.get());}
 @Override protected int getCapacity(ItemResource resource){return live()?64:0;}
 @Override protected void onRootCommit(ItemStack original){if(live())owner.setChanged();}
}
