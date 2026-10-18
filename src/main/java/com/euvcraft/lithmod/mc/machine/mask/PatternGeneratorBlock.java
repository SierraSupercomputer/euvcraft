package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Pattern Generator block: a blank plate and a design in, one photomask out.
 *
 * <p>All the work is {@link PatternGeneratorBlockEntity#serverTick()} running
 * {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} against the recipes in
 * {@code data/lithmod/machine_recipes}, so this block exists only to place the block
 * entity. The server ticker and the use-on behaviour that opens the menu are not restated
 * here: both come from the shared shell this class extends, so this block adds nothing of
 * its own and stays as small as the Crystal Puller's is.</p>
 *
 * <p>Hand-fed, like every other fab machine: no item or fluid capability is registered for
 * its block entity type, so nothing but a player with the menu open can put a plate in or
 * take a mask out. Energy is the only thing that enters from outside.</p>
 */
public class PatternGeneratorBlock extends SiliconMachineBlock {

    public PatternGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PatternGeneratorBlockEntity(pos, state);
    }
}