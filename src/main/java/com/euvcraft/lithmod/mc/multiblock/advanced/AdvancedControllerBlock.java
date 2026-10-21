package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
public final class AdvancedControllerBlock extends AdvancedCasingBlock {
    public AdvancedControllerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return this==AdvancedMachines.DEPOSITION_ETCHER_CONTROLLER.get()?new DepositionEtcherControllerBlockEntity(pos,state):new AdvancedTestPackageControllerBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:(l,p,s,b)->{if(b instanceof FabMachineBlockEntity machine)machine.serverTick();};
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof FabMachineBlockEntity machine){
            player.openMenu(machine,pos);return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,net.minecraft.world.level.block.Block neighbor,@Nullable Orientation orientation,boolean moved){
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof AdvancedOwner owner)owner.revalidate();
    }
}
