package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
public final class CleanroomMachineBlock extends BaseEntityBlock {
 public CleanroomMachineBlock(BlockBehaviour.Properties p){super(p);}
 @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return this==CleanroomBlocks.CONTROLLER.get()?new CleanroomControllerBlockEntity(p,s):new CleanroomFilterBlockEntity(p,s);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide()?null:(level,pos,state,be)->{if(be instanceof FabMachineBlockEntity machine)machine.serverTick();};}
 @Override protected InteractionResult useItemOn(ItemStack stack,BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof FabMachineBlockEntity be){if(be instanceof CleanroomControllerBlockEntity r)r.revalidate();player.openMenu(be,buf->{buf.writeBlockPos(pos);buf.writeBoolean(be instanceof CleanroomFilterBlockEntity);});return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
}
