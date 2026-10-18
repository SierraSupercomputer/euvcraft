package com.euvcraft.lithmod.mc.machine.packaging;

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
 * Registers the Test and Package machine: its block, block entity type, menu type and the
 * energy capability.
 *
 * <p>The packaging chain's own registry class rather than an addition to the silicon chain's
 * or the mask chain's, for the same reason those exist: the block, the block entity and the
 * menu belong to this machine, and the screen is registered from
 * {@link PackagingMachineClientEvents} on the client alone. The block item lives in
 * {@code ModItems}, because an item cannot be built before its block is registered.</p>
 *
 * <p>Same rules as every other fab machine:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)}
 *       hands the factory properties that already carry the id, which is the only way the
 *       block can be constructed at all;</li>
 *   <li>only {@code Capabilities.Energy.BLOCK} is registered, so item and fluid lookups
 *       return {@code null} on every side and no hopper can reach the slots or the FOUP;</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so the listener
 *       is attached here and never on {@code NeoForge.EVENT_BUS}.</li>
 * </ul>
 */
public final class PackagingMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredBlock<TestPackageBlock> TEST_PACKAGE =
        BLOCKS.registerBlock("test_package", TestPackageBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestPackageBlockEntity>>
        TEST_PACKAGE_BE = BLOCK_ENTITIES.register("test_package",
            () -> new BlockEntityType<>(TestPackageBlockEntity::new, TEST_PACKAGE.get()));

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TestPackageMenu>> TEST_PACKAGE_MENU =
        MENU_TYPES.register("test_package", () -> IMenuTypeExtension.create(TestPackageMenu::new));

    private PackagingMachines() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(PackagingMachines::registerCapabilities);
    }

    /**
     * The energy capability hook: the machine's own handler, on every side.
     *
     * <p>Energy in only — the handler is the base class's {@code WithdrawNothing} view, which
     * accepts a charge and refuses every extraction, and nothing else is registered, so there
     * is no item or fluid route into the slots.</p>
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, TEST_PACKAGE_BE.get(),
            (blockEntity, side) -> blockEntity instanceof FabMachineBlockEntity machine
                ? machine.energy()
                : null);
    }
}
