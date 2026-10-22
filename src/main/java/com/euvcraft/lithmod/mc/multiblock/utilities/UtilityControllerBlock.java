package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;import net.minecraft.world.level.block.state.*;
public final class UtilityControllerBlock extends AdvancedControllerBlock{
 public UtilityControllerBlock(BlockBehaviour.Properties p){super(p);}
 @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new UtilityControllerBlockEntity(p,s);}
}
