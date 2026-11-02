package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class FabUpwInletBlock extends AdvancedCasingBlock {
    public FabUpwInletBlock(Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FabUpwInletBlockEntity(pos,state);}
}
