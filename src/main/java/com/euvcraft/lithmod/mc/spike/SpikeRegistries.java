package com.euvcraft.lithmod.mc.spike;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration of the M1-02s spike machine: block, block entity type, menu type and the
 * block energy capability.
 *
 * <p>Every declaration below is the pattern taken from Arcforge; the source file each one
 * came from is listed in {@code docs/reports/M1-02s_energy-spike.md}.</p>
 */
public final class SpikeRegistries {

    /** MOD id, the same string {@code EUVCRAFTMod} uses. */
    public static final String MODID = "lithmod";

    /**
     * Blocks.
     *
     * <p>The 26.3 form is
     * {@code registerBlock(String, Function<Properties, B>, UnaryOperator<Properties>)}: the
     * second argument receives the {@code Properties} the helper has already stamped with the
     * registry id and the third mutates them. Registering through
     * {@code register(String, Supplier<Block>)} instead leaves the id unset and the block
     * constructor throws {@code NullPointerException: Block id not set}.</p>
     */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    public static final DeferredBlock<SpikeMachineBlock> SPIKE_MACHINE =
        BLOCKS.registerBlock("spike_machine",
            SpikeMachineBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** Block entity types. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpikeMachineBlockEntity>> BLOCK_ENTITY =
        BLOCK_ENTITIES.register("spike_machine",
            () -> new BlockEntityType<>(SpikeMachineBlockEntity::new, SPIKE_MACHINE.get()));

    /** Menu types. {@code IMenuTypeExtension.create} is what supplies the client constructor. */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SpikeMachineMenu>> MENU =
        MENU_TYPES.register("spike_machine", () -> IMenuTypeExtension.create(SpikeMachineMenu::new));

    private SpikeRegistries() {
    }

    /**
     * Attaches every deferred register and the capability listener to the mod event bus.
     *
     * <p>{@code RegisterCapabilitiesEvent implements IModBusEvent}, so it belongs on the mod
     * bus handed to the {@code @Mod} constructor, never on {@code NeoForge.EVENT_BUS}.</p>
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(SpikeRegistries::registerCapabilities);
    }

    /**
     * The energy capability hook, exactly the shape Arcforge uses:
     * {@code event.registerBlockEntity(capability, blockEntityType, (blockEntity, side) -> handler)}.
     *
     * <p>Nothing is registered for {@code Capabilities.Item.BLOCK} or
     * {@code Capabilities.Fluid.BLOCK}, so a lookup for either returns {@code null}.</p>
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(),
            (blockEntity, side) -> blockEntity instanceof SpikeMachineBlockEntity machine
                ? machine.energy()
                : null);
    }
}
