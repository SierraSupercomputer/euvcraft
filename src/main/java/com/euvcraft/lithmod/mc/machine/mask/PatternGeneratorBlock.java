package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Pattern Generator block: a blank plate and a design in, one photomask out.
 *
 * <p>There is nothing of its own here beyond placing the block entity and ticking it. All the
 * work is {@link FabMachineBlockEntity#serverTick()} running
 * {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} against the recipes in
 * {@code data/lithmod/machine_recipes}, and the whole of the mask rule is
 * {@link PatternGeneratorBlockEntity}'s. This class therefore stays as small as the Crystal
 * Puller's is, and deliberately does not extend the silicon chain's own block class: that
 * class is not part of this machine's surface, and one generic ticker is cheaper to read than
 * a cross-chain base for a block that carries no silicon-chain behaviour.</p>
 *
 * <p>Hand-fed, like every other fab machine: no item or fluid capability is registered for
 * its block entity type, so nothing but a player with the menu open can put a plate in or
 * take a mask out. Energy is the only thing that enters from outside.</p>
 */
public class PatternGeneratorBlock extends Block {

    /**
     * @param properties the properties {@code DeferredRegister.registerBlock} hands the
     *     factory; the block never adds its own strength or sound, because the silicon
     *     machines take those from the same registration helper.
     */
    public PatternGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PatternGeneratorBlockEntity(pos, state);
    }

    /**
     * One server tick of {@link PatternGeneratorBlockEntity#serverTick()}.
     *
     * <p>Returns a ticker on both sides rather than {@code null} on the client, because the
     * guard that matters belongs to the machine: {@code serverTick()} opens with the same
     * client-side check, so a client-side call returns immediately and a second guard here
     * would only restate it.</p>
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return (tickingLevel, pos, tickingState, blockEntity) -> {
            if (blockEntity instanceof FabMachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }
}
