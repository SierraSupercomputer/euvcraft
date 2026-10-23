package com.euvcraft.lithmod.mc.machine.cleaner;
import net.minecraft.core.BlockPos;import net.minecraft.world.*;import net.minecraft.world.entity.player.Player;import net.minecraft.world.item.ItemStack;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.BaseEntityBlock;import net.minecraft.world.level.block.entity.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.phys.BlockHitResult;
public final class FoupCleanerBlock extends BaseEntityBlock {
 public FoupCleanerBlock(BlockBehaviour.Properties p){super(p);}
 @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new FoupCleanerBlockEntity(p,s);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide()?null:(level,pos,state,be)->{if(be instanceof FoupCleanerBlockEntity c)c.serverTick();};}
 @Override protected InteractionResult useItemOn(ItemStack held,BlockState s,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(!l.isClientSide()&&l.getBlockEntity(pos) instanceof FoupCleanerBlockEntity c){player.openMenu(c,b->b.writeBlockPos(pos));return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
}
