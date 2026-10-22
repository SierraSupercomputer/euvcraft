package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
public final class ScannerControllerBlock extends AdvancedControllerBlock {
 @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
  if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof ScannerControllerBlockEntity scanner)player.openMenu(scanner,buffer->{buffer.writeBlockPos(pos);buffer.writeBoolean(false);});
  return net.minecraft.world.InteractionResult.SUCCESS;
 }
 public ScannerControllerBlock(BlockBehaviour.Properties properties){super(properties);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ScannerControllerBlockEntity(pos,state);}
}
