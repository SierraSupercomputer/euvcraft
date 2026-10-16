package com.euvcraft.lithmod.mc.block;

import com.euvcraft.lithmod.mc.block.entity.ProcessCellPowerPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Process Cell Power Port block.
 * Provides FE energy capability to the multiblock.
 * Only exposes energy while the structure is formed.
 */
public class ProcessCellPowerPortBlock extends BaseEntityBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ProcessCellPowerPortBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(2.5f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("lithmod", "process_cell_power_port"))));
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessCellPowerPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof ProcessCellPowerPortBlockEntity port) {
                port.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProcessCellPowerPortBlockEntity port) {
                port.onInteract(player);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}