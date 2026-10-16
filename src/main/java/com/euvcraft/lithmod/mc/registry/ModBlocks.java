package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.block.ProcessCellCasingBlock;
import com.euvcraft.lithmod.mc.block.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.block.ProcessCellPowerPortBlock;
import com.euvcraft.lithmod.mc.block.entity.ProcessCellCasingBlockEntity;
import com.euvcraft.lithmod.mc.block.entity.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.block.entity.ProcessCellPowerPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks and block entities registered by EUVCRAFT.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
        Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    // Process Cell multiblock (M1-03)
    public static final DeferredBlock<Block> PROCESS_CELL_CONTROLLER = BLOCKS.register("process_cell_controller",
        ProcessCellControllerBlock::new);

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellControllerBlockEntity>> PROCESS_CELL_CONTROLLER_BE =
        BLOCK_ENTITIES.register("process_cell_controller",
            () -> new BlockEntityType<>(ProcessCellControllerBlockEntity::new, PROCESS_CELL_CONTROLLER.get()));

    public static final DeferredBlock<Block> PROCESS_CELL_CASING = BLOCKS.register("process_cell_casing",
        ProcessCellCasingBlock::new);

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellCasingBlockEntity>> PROCESS_CELL_CASING_BE =
        BLOCK_ENTITIES.register("process_cell_casing",
            () -> new BlockEntityType<>(ProcessCellCasingBlockEntity::new, PROCESS_CELL_CASING.get()));

    public static final DeferredBlock<Block> PROCESS_CELL_POWER_PORT = BLOCKS.register("process_cell_power_port",
        ProcessCellPowerPortBlock::new);

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellPowerPortBlockEntity>> PROCESS_CELL_POWER_PORT_BE =
        BLOCK_ENTITIES.register("process_cell_power_port",
            () -> new BlockEntityType<>(ProcessCellPowerPortBlockEntity::new, PROCESS_CELL_POWER_PORT.get()));

    private ModBlocks() {}

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}