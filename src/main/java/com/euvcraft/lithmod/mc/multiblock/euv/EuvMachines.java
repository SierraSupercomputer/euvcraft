package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.registry.ModItems;
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
 * Registers the EUV multiblock group: scanner (5x7x5) and light source (3x3x5).
 * Blocks, block entities, menus, and capabilities for both machines.
 */
public final class EuvMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    // ==================== EUV SCANNER (5x7x5) ====================

    public static final DeferredBlock<EuvScannerControllerBlock> SCANNER_CONTROLLER =
            BLOCKS.registerBlock("euv_scanner_controller", EuvScannerControllerBlock::new,
                    p -> p.strength(5.0f, 10.0f).requiresCorrectToolForDrops());

    private static DeferredBlock<EuvScannerMemberBlock> scannerMember(String id, BlockKind kind) {
        return BLOCKS.registerBlock(id, p -> new EuvScannerMemberBlock(p, kind),
                p -> p.strength(5.0f, 10.0f).requiresCorrectToolForDrops());
    }

    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_OPERATING_PANEL =
            scannerMember("euv_scanner_operating_panel", BlockKind.OPERATING_PANEL);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_CALIBRATION_PANEL =
            scannerMember("euv_scanner_calibration_panel", BlockKind.CALIBRATION_PANEL);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_EXPOSURE_MODULE =
            scannerMember("euv_scanner_exposure_module", BlockKind.EXPOSURE_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_OPTICS_MODULE =
            scannerMember("euv_scanner_optics_module", BlockKind.OPTICS_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_STAGE_MODULE =
            scannerMember("euv_scanner_stage_module", BlockKind.STAGE_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_FILTRATION_MODULE =
            scannerMember("euv_scanner_filtration_module", BlockKind.FILTRATION_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_METROLOGY_MODULE =
            scannerMember("euv_scanner_metrology_module", BlockKind.METROLOGY_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_VACUUM_MODULE =
            scannerMember("euv_scanner_vacuum_module", BlockKind.VACUUM_MODULE);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_DIAGNOSTIC_PANEL =
            scannerMember("euv_scanner_diagnostic_panel", BlockKind.DIAGNOSTIC_PANEL);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_WATER_INLET =
            scannerMember("euv_scanner_water_inlet", BlockKind.WATER_INLET);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_POWER_PORT =
            scannerMember("euv_scanner_power_port", BlockKind.POWER_PORT);
    public static final DeferredBlock<EuvScannerMemberBlock> SCANNER_CASING =
            scannerMember("euv_scanner_casing", BlockKind.CASING);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuvScannerControllerBlockEntity>> SCANNER_CONTROLLER_BE =
            BLOCK_ENTITIES.register("euv_scanner_controller",
                    () -> new BlockEntityType<>(EuvScannerControllerBlockEntity::new, SCANNER_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuvScannerMemberBlockEntity>> SCANNER_MEMBER_BE =
            BLOCK_ENTITIES.register("euv_scanner_member",
                    () -> new BlockEntityType<>(EuvScannerMemberBlockEntity::new,
                            SCANNER_OPERATING_PANEL.get(), SCANNER_CALIBRATION_PANEL.get(),
                            SCANNER_EXPOSURE_MODULE.get(), SCANNER_OPTICS_MODULE.get(),
                            SCANNER_STAGE_MODULE.get(), SCANNER_FILTRATION_MODULE.get(),
                            SCANNER_METROLOGY_MODULE.get(), SCANNER_VACUUM_MODULE.get(),
                            SCANNER_DIAGNOSTIC_PANEL.get(), SCANNER_WATER_INLET.get(),
                            SCANNER_POWER_PORT.get(), SCANNER_CASING.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<EuvScannerOperatingMenu>> SCANNER_OPERATING_MENU =
            MENUS.register("euv_scanner_operation", () -> IMenuTypeExtension.create(EuvScannerOperatingMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EuvScannerDashboardMenu>> SCANNER_DASHBOARD_MENU =
            MENUS.register("euv_scanner_dashboard", () -> IMenuTypeExtension.create(EuvScannerDashboardMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EuvScannerCalibrationMenu>> SCANNER_CALIBRATION_MENU =
            MENUS.register("euv_scanner_calibration", () -> IMenuTypeExtension.create(EuvScannerCalibrationMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EuvScannerDiagnosticMenu>> SCANNER_DIAGNOSTIC_MENU =
            MENUS.register("euv_scanner_diagnostic", () -> IMenuTypeExtension.create(EuvScannerDiagnosticMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EuvScannerEquipmentMenu>> SCANNER_EQUIPMENT_MENU =
            MENUS.register("euv_scanner_equipment", () -> IMenuTypeExtension.create(EuvScannerEquipmentMenu::new));

    // ==================== EUV LIGHT SOURCE (3x3x5) ====================

    public static final DeferredBlock<EuvLightSourceControllerBlock> LIGHT_SOURCE_CONTROLLER =
            BLOCKS.registerBlock("euv_light_source_controller", EuvLightSourceControllerBlock::new,
                    p -> p.strength(5.0f, 10.0f).requiresCorrectToolForDrops());

    private static DeferredBlock<EuvLightSourceMemberBlock> lightSourceMember(String id, BlockKind kind) {
        return BLOCKS.registerBlock(id, p -> new EuvLightSourceMemberBlock(p, kind),
                p -> p.strength(5.0f, 10.0f).requiresCorrectToolForDrops());
    }

    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_OPERATING_PANEL =
            lightSourceMember("euv_light_source_operating_panel", BlockKind.OPERATING_PANEL);
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_PULSE_MODULE =
            lightSourceMember("euv_light_source_pulse_module", BlockKind.PULSE_MODULE);
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_CALIBRATION_PANEL =
            lightSourceMember("euv_light_source_calibration_panel", BlockKind.CALIBRATION_PANEL);
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_TIN_INLET =
            lightSourceMember("euv_light_source_tin_inlet", BlockKind.FLUID_OUTLET); // Reusing for tin input
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_HYDROGEN_INLET =
            lightSourceMember("euv_light_source_hydrogen_inlet", BlockKind.WATER_INLET); // Reusing for H2
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_VACUUM_MODULE =
            lightSourceMember("euv_light_source_vacuum_module", BlockKind.VACUUM_MODULE);
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_POWER_PORT =
            lightSourceMember("euv_light_source_power_port", BlockKind.POWER_PORT);
    public static final DeferredBlock<EuvLightSourceMemberBlock> LIGHT_SOURCE_CASING =
            lightSourceMember("euv_light_source_casing", BlockKind.CASING);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuvLightSourceControllerBlockEntity>> LIGHT_SOURCE_CONTROLLER_BE =
            BLOCK_ENTITIES.register("euv_light_source_controller",
                    () -> new BlockEntityType<>(EuvLightSourceControllerBlockEntity::new, LIGHT_SOURCE_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuvLightSourceMemberBlockEntity>> LIGHT_SOURCE_MEMBER_BE =
            BLOCK_ENTITIES.register("euv_light_source_member",
                    () -> new BlockEntityType<>(EuvLightSourceMemberBlockEntity::new,
                            LIGHT_SOURCE_OPERATING_PANEL.get(), LIGHT_SOURCE_PULSE_MODULE.get(), LIGHT_SOURCE_CALIBRATION_PANEL.get(), LIGHT_SOURCE_TIN_INLET.get(),
                            LIGHT_SOURCE_HYDROGEN_INLET.get(), LIGHT_SOURCE_VACUUM_MODULE.get(),
                            LIGHT_SOURCE_POWER_PORT.get(), LIGHT_SOURCE_CASING.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<EuvLightSourceOperatingMenu>> LIGHT_SOURCE_OPERATING_MENU =
            MENUS.register("euv_light_source_operation", () -> IMenuTypeExtension.create(EuvLightSourceOperatingMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EuvLightSourceDashboardMenu>> LIGHT_SOURCE_DASHBOARD_MENU =
            MENUS.register("euv_light_source_dashboard", () -> IMenuTypeExtension.create(EuvLightSourceDashboardMenu::new));

    // ==================== BEAM LINE ====================

    public static final DeferredBlock<EuvBeamlineBlock> BEAM_LINE =
            BLOCKS.registerBlock("euv_beam_line", EuvBeamlineBlock::new,
                    p -> p.strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuvBeamlineBlockEntity>> BEAM_LINE_BE =
            BLOCK_ENTITIES.register("euv_beam_line",
                    () -> new BlockEntityType<>(EuvBeamlineBlockEntity::new, BEAM_LINE.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<EuvSourceCalibrationMenu>> SOURCE_CALIBRATION_MENU = MENUS.register("euv_source_calibration", () -> IMenuTypeExtension.create(EuvSourceCalibrationMenu::new));

    private EuvMachines() {}

    /** Attaches every deferred register and capability listeners to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(EuvMachines::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Scanner power port energy capability
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SCANNER_MEMBER_BE.get(),
                (be, side) -> be.structureKind() == BlockKind.POWER_PORT && be.scanner() != null
                        ? be.power()
                        : null);

        // Scanner water inlet fluid capability
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SCANNER_MEMBER_BE.get(),
                (be, side) -> be.structureKind() == BlockKind.WATER_INLET && be.scanner() != null
                        ? new EuvScannerWaterHandler(be, be.scanner())
                        : null);

        // Light source power port energy capability
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LIGHT_SOURCE_MEMBER_BE.get(),
                (be, side) -> be.structureKind() == BlockKind.POWER_PORT && be.lightSource() != null
                        ? be.power()
                        : null);

        // Light source tin inlet fluid capability (for tin)
        event.registerBlockEntity(Capabilities.Item.BLOCK, LIGHT_SOURCE_MEMBER_BE.get(),
                (be, side) -> be.structureKind() == BlockKind.FLUID_OUTLET && be.lightSource() != null
                        ? new EuvLightSourceTinHandler(be, be.lightSource())
                        : null);

        // Light source hydrogen inlet fluid capability
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, LIGHT_SOURCE_MEMBER_BE.get(),
                (be, side) -> be.structureKind() == BlockKind.WATER_INLET && be.lightSource() != null
                        ? new EuvLightSourceHydrogenHandler(be, be.lightSource())
                        : null);
    }
}