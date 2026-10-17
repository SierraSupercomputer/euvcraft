package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

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
 * Registers the Pattern Generator: its block, block entity type, menu type and the energy
 * capability.
 *
 * <p>The mask chain's own registry class rather than an addition to the silicon chain's,
 * for the same reason that one exists: the block, the block entity and the menu belong to
 * this machine, and the screen is registered from
 * {@link MaskMachineClientEvents} on the client alone. The block item lives in
 * {@code ModItems}, because an item cannot be built before its block is registered.</p>
 *
 * <p>Same rules as every other fab machine:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)}
 *       hands the factory properties that already carry the id, which is the only way the
 *       block can be constructed at all;</li>
 *   <li>only {@code Capabilities.Energy.BLOCK} is registered, so item and fluid lookups
 *       return {@code null} on every side and no hopper can reach the slots;</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so the listener
 *       is attached here and never on {@code NeoForge.EVENT_BUS}.</li>
 * </ul>
 */
public final class MaskMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredBlock<PatternGeneratorBlock> PATTERN_GENERATOR =
        BLOCKS.registerBlock("pattern_generator", PatternGeneratorBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatternGeneratorBlockEntity>>
        PATTERN_GENERATOR_BE = BLOCK_ENTITIES.register("pattern_generator",
            () -> new BlockEntityType<>(PatternGeneratorBlockEntity::new, PATTERN_GENERATOR.get()));

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PatternGeneratorMenu>> PATTERN_GENERATOR_MENU =
        MENU_TYPES.register("pattern_generator", () -> IMenuTypeExtension.create(PatternGeneratorMenu::new));

    private MaskMachines() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(MaskMachines::registerCapabilities);
    }

    /**
     * The energy capability hook: the machine's own handler, on every side.
     *
     * <p>Energy in only — the handler is the base class's {@code WithdrawNothing} view, which
     * accepts a charge and refuses every extraction, and nothing else is registered, so there
     * is no item or fluid route into the slots.</p>
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, PATTERN_GENERATOR_BE.get(),
            (blockEntity, side) -> blockEntity instanceof FabMachineBlockEntity machine
                ? machine.energy()
                : null);
    }
}
