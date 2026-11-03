package com.euvcraft.lithmod.mc.machine.recycler;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ScrapRecyclerBlock extends SiliconMachineBlock {
    public ScrapRecyclerBlock(Properties properties){super(properties);}
    @Override protected BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ScrapRecyclerBlockEntity(pos,state);}
}
