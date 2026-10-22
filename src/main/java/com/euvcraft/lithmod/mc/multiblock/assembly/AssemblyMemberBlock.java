package com.euvcraft.lithmod.mc.multiblock.assembly;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;import net.minecraft.world.level.block.state.*;
public final class AssemblyMemberBlock extends AdvancedCasingBlock{public AssemblyMemberBlock(BlockBehaviour.Properties p){super(p);}@Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new AssemblyMemberBlockEntity(p,s);}}

