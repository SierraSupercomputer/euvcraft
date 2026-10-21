package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

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
 * Registers the T4 advanced multiblocks: deposition_etcher and advanced_test_package.
 * Each has its own controller block, controller block entity type, but shares member blocks
 * (casing, power port, operating panel) and member block entity type.
 */
public final class AdvancedMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    // ================================================================
    // DEPOSITION_ETCHER (3 wide x 3 tall x 5 deep = 45 blocks)
    // ================================================================

    /** Deposition Etcher controller: lower Equipment Cabinet. */
    public static final DeferredBlock<AdvancedControllerBlock> DEPOSITION_ETCHER_CONTROLLER =
            BLOCKS.registerBlock("deposition_etcher_controller", AdvancedControllerBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** Deposition Etcher upper operating panel. */
    public static final DeferredBlock<AdvancedOperatingPanelBlock> DEPOSITION_ETCHER_OPERATING_PANEL =
            BLOCKS.registerBlock("deposition_etcher_operating_panel", AdvancedOperatingPanelBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    // ================================================================
    // ADVANCED_TEST_PACKAGE (2 wide x 2 tall x 3 deep = 12 blocks)
    // ================================================================

    /** Advanced Test Package controller: lower Equipment Cabinet. */
    public static final DeferredBlock<AdvancedControllerBlock> ADVANCED_TEST_PACKAGE_CONTROLLER =
            BLOCKS.registerBlock("advanced_test_package_controller", AdvancedControllerBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** Advanced Test Package upper operating panel. */
    public static final DeferredBlock<AdvancedOperatingPanelBlock> ADVANCED_TEST_PACKAGE_OPERATING_PANEL =
            BLOCKS.registerBlock("advanced_test_package_operating_panel", AdvancedOperatingPanelBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    // ================================================================
    // SHARED MEMBER BLOCKS (used by both multiblocks)
    // ================================================================

    /** Advanced casing: filler blocks, distinct from Process Cell casing. */
    public static final DeferredBlock<AdvancedCasingBlock> ADVANCED_CASING =
            BLOCKS.registerBlock("advanced_casing", AdvancedCasingBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    /** Advanced power port: the only member that can expose FE while formed and owned. */
    public static final DeferredBlock<AdvancedPowerPortBlock> ADVANCED_POWER_PORT =
            BLOCKS.registerBlock("advanced_power_port", AdvancedPowerPortBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    // ================================================================
    // BLOCK ENTITY TYPES
    // ================================================================

    /** ONE controller BE type per controller block (different machine IDs); kind from block state. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepositionEtcherControllerBlockEntity>>
            DEPOSITION_ETCHER_CONTROLLER_BE = BLOCK_ENTITIES.register("deposition_etcher_controller",
            () -> new BlockEntityType<>(DepositionEtcherControllerBlockEntity::new,
                    DEPOSITION_ETCHER_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedTestPackageControllerBlockEntity>>
            ADVANCED_TEST_PACKAGE_CONTROLLER_BE = BLOCK_ENTITIES.register("advanced_test_package_controller",
            () -> new BlockEntityType<>(AdvancedTestPackageControllerBlockEntity::new,
                    ADVANCED_TEST_PACKAGE_CONTROLLER.get()));

    /** ONE shared member BE type for casing, power port, and operating panel. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedMemberBlockEntity>>
            ADVANCED_MEMBER_BE = BLOCK_ENTITIES.register("advanced_member",
            () -> new BlockEntityType<>(AdvancedMemberBlockEntity::new,
                    ADVANCED_CASING.get(), ADVANCED_POWER_PORT.get(),
                    DEPOSITION_ETCHER_OPERATING_PANEL.get(), ADVANCED_TEST_PACKAGE_OPERATING_PANEL.get()));

    // ================================================================
    // MENU TYPES
    // ================================================================

    public static final DeferredHolder<MenuType<?>, MenuType<DepositionEtcherMenu>>
            DEPOSITION_ETCHER_MENU = MENU_TYPES.register("deposition_etcher",
            () -> IMenuTypeExtension.create(DepositionEtcherMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedTestPackageMenu>>
            ADVANCED_TEST_PACKAGE_MENU = MENU_TYPES.register("advanced_test_package",
            () -> IMenuTypeExtension.create(AdvancedTestPackageMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedOperatingPanelMenu>>
            ADVANCED_OPERATING_PANEL_MENU = MENU_TYPES.register("advanced_operating_panel",
            () -> IMenuTypeExtension.create(AdvancedOperatingPanelMenu::new));

    private AdvancedMachines() {}

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(AdvancedMachines::registerCapabilities);
    }

    /**
     * The FE capability on the shared member BE type, filtered on actual block state.
     * Only a formed, owned advanced power port exposes FE.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ADVANCED_MEMBER_BE.get(),
                (blockEntity, side) -> blockEntity instanceof AdvancedPowerPortBlockEntity port
                        ? port.energy()
                        : null);
    }
}