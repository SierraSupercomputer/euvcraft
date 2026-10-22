package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;import net.minecraft.world.level.block.state.*;
public final class UtilityPortBlock extends AdvancedCasingBlock{
 final BlockKind kind;public UtilityPortBlock(BlockBehaviour.Properties p,BlockKind kind){super(p);this.kind=kind;}
 @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new UtilityPortBlockEntity(p,s);}
}
