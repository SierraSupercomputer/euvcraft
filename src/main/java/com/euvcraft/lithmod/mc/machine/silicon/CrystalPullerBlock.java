package com.euvcraft.lithmod.mc.machine.silicon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Crystal Puller block: one input slot, one output slot, and nothing else of its own.
 *
 * <p>All the work is {@link FabMachineBlockEntity#serverTick()} running
 * {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} against the recipes in
 * {@code data/lithmod/machine_recipes}, so this block exists only to place the block
 * entity.</p>
 */
public class CrystalPullerBlock extends SiliconMachineBlock {

    public CrystalPullerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalPullerBlockEntity(pos, state);
    }
}