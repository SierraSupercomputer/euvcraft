package com.euvcraft.lithmod.mc.machine.packaging;

import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Test and Package block: one finished lot in, chips and scrap out.
 *
 * <p>All the work is {@link TestPackageBlockEntity#serverTick()} judging and working
 * through a lot, so this block exists only to place the block entity. The server ticker
 * and the use-on behaviour that opens the menu are not restated here: both come from the
 * shared shell this class extends, the same way the Pattern Generator and the silicon
 * chain's machines get them.</p>
 *
 * <p>Hand-fed, like every other fab machine: no item or fluid capability is registered for
 * its block entity type, so nothing but a player with the menu open can put a lot or a
 * package in or take chips out. Energy is the only thing that enters from outside.</p>
 */
public class TestPackageBlock extends SiliconMachineBlock {

    public TestPackageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TestPackageBlockEntity(pos, state);
    }
}
