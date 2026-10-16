package com.euvcraft.lithmod.mc.machine.test;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModMenus;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the test-only machine: its block, its block entity type, and its energy
 * capability. The menu type lives in {@link ModMenus}.
 *
 * <p>Every line follows a pattern proven by the M1-02s spike:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)} hands
 *       the factory properties that already carry the id, which is what avoids
 *       "Block id not set";</li>
 *   <li>only {@code Capabilities.Energy.BLOCK} is registered, so item and fluid lookups
 *       return {@code null} on every side;</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so the listener is
 *       attached here, never on {@code NeoForge.EVENT_BUS}.</li>
 * </ul>
 */
public final class ModMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredBlock<TestMachineBlock> TEST_MACHINE =
        BLOCKS.registerBlock("test_machine", TestMachineBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestMachineBlockEntity>> TEST_MACHINE_BE =
        BLOCK_ENTITIES.register("test_machine",
            () -> new BlockEntityType<TestMachineBlockEntity>(
                TestMachineBlockEntity::new, TEST_MACHINE.get()));

    private ModMachines() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(ModMachines::registerCapabilities);
    }

    /**
     * The energy capability hook, the shape Arcforge uses:
     * {@code event.registerBlockEntity(capability, blockEntityType, (be, side) -> handler)}.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, TEST_MACHINE_BE.get(),
            (blockEntity, side) -> blockEntity instanceof FabMachineBlockEntity machine
                ? machine.energy()
                : null);
    }
}
