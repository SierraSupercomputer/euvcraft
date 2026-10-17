package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The block shell every machine of the silicon chain shares.
 *
 * <p>Two of the three parts of a machine block are identical for the Crystal Puller and
 * the Wafer Slicer: the server ticker that calls
 * {@link FabMachineBlockEntity#serverTick()} and the use-on behaviour that opens the menu.
 * Only the block entity differs, so only that is left to a subclass.</p>
 *
 * <p>The constructor takes the {@code BlockBehaviour.Properties} that
 * {@code DeferredRegister.Blocks.registerBlock} hands over; those already carry the
 * registry id, which is why nothing here builds its own properties.</p>
 */
public abstract class SiliconMachineBlock extends BaseEntityBlock {

    protected SiliconMachineBlock(Properties properties) {
        super(properties);
    }

    /** Builds this block's block entity. */
    protected abstract @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state);

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return createBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof FabMachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof FabMachineBlockEntity machine) {
            player.openMenu(machine, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}