package com.euvcraft.lithmod.mc.machine.test;

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
 * The test-only machine block.
 *
 * <p>Its constructor takes the {@link BlockBehaviour.Properties} that
 * {@code DeferredRegister.Blocks.registerBlock} hands over. Those already carry the
 * registry id, which is why this must not build its own properties with
 * {@code Properties.of()}: doing so leaves the id unset and the block constructor throws
 * {@code NullPointerException: Block id not set}.</p>
 */
public class TestMachineBlock extends BaseEntityBlock {

    public TestMachineBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestMachineBlockEntity(pos, state);
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
            // The spike found that a game test cannot use Player.openMenu here: the NeoForge
            // override sends neoforge:advanced_open_screen, which a test connection rejects.
            // The real player path is unchanged; the game test installs the menu directly.
            player.openMenu(machine, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

}
