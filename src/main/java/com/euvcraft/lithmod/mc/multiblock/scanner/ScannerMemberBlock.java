package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
public final class ScannerMemberBlock extends AdvancedCasingBlock {
 private final BlockKind kind;
 public ScannerMemberBlock(BlockBehaviour.Properties properties,BlockKind kind){super(properties);this.kind=kind;}
 public BlockKind kind(){return kind;}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ScannerMemberBlockEntity(pos,state);}
 @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
  if(kind!=BlockKind.OPERATING_PANEL&&kind!=BlockKind.CALIBRATION_PANEL&&kind!=BlockKind.DIAGNOSTIC_PANEL)return InteractionResult.PASS;
  if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity member){
   var core=member.scanner();
   if(core==null)player.sendSystemMessage(Component.translatable("message.lithmod.scanner.form_first"));
   else if(kind==BlockKind.DIAGNOSTIC_PANEL)player.openMenu(member,pos);
   else if(kind==BlockKind.CALIBRATION_PANEL)player.openMenu(member,buffer->{buffer.writeBlockPos(pos);buffer.writeBoolean(true);});
   else player.openMenu(member,pos);
  }
  return InteractionResult.SUCCESS;
 }
}

