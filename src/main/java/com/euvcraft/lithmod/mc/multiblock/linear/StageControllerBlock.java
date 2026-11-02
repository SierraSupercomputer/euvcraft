package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class StageControllerBlock extends AdvancedControllerBlock {
    private final boolean coating;
    public StageControllerBlock(Properties properties,boolean coating){super(properties);this.coating=coating;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return coating?new CoatingTrackBlockEntity(pos,state):new WetCleanerBlockEntity(pos,state);}
}
