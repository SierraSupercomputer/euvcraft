package com.euvcraft.lithmod.mc.multiblock.g3;

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
 * Registers the G3 multiblock group: two controllers, shared member block entity type,
 * shared casing/power/panel/water/module blocks, and their menus.
 *
 * <p>Every line follows the pattern proven against the real 26.3 classpath:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)} hands the
 *       factory properties that already carry the registry id.</li>
 *   <li>block entity types are {@code new BlockEntityType<>(factory, block)} keyed on the block.</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, attached to the mod bus.</li>
 * </ul>
 */
public final class G3Machines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    // ---------------------------------------------------------------- blocks

    /** G3 Reticle Writer controller. */
    public static final DeferredBlock<G3ReticleWriterControllerBlock> RETICLE_WRITER_CONTROLLER =
            BLOCKS.registerBlock("g3_reticle_writer", G3ReticleWriterControllerBlock::new,
                    properties -> properties.strength(4.0f, 8.0f).requiresCorrectToolForDrops());

    /** G3 Deposition Etcher controller. */
    public static final DeferredBlock<G3DepositionEtcherControllerBlock> DEPOSITION_ETCHER_CONTROLLER =
            BLOCKS.registerBlock("g3_deposition_etcher", G3DepositionEtcherControllerBlock::new,
                    properties -> properties.strength(4.0f, 8.0f).requiresCorrectToolForDrops());

    /** G3 shared casing block. */
    public static final DeferredBlock<G3CasingBlock> CASING =
            BLOCKS.registerBlock("g3_casing", G3CasingBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 power port (1-4 per machine). */
    public static final DeferredBlock<G3PowerPortBlock> POWER_PORT =
            BLOCKS.registerBlock("g3_power_port", G3PowerPortBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 operating panel (directly above controller). */
    public static final DeferredBlock<G3OperatingPanelBlock> OPERATING_PANEL =
            BLOCKS.registerBlock("g3_operating_panel", G3OperatingPanelBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 water inlet (optional, for prep machine). */
    public static final DeferredBlock<G3WaterInletBlock> WATER_INLET =
            BLOCKS.registerBlock("g3_water_inlet", G3WaterInletBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 writing module (writer middle layer). */
    public static final DeferredBlock<G3WritingModuleBlock> WRITING_MODULE =
            BLOCKS.registerBlock("g3_writing_module", G3WritingModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 metrology module (writer middle layer). */
    public static final DeferredBlock<G3MetrologyModuleBlock> METROLOGY_MODULE =
            BLOCKS.registerBlock("g3_metrology_module", G3MetrologyModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 deposition module (prep middle layers). */
    public static final DeferredBlock<G3DepositionModuleBlock> DEPOSITION_MODULE =
            BLOCKS.registerBlock("g3_deposition_module", G3DepositionModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 rinse module (prep middle layers). */
    public static final DeferredBlock<G3RinseModuleBlock> RINSE_MODULE =
            BLOCKS.registerBlock("g3_rinse_module", G3RinseModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    /** G3 vacuum module (prep middle layers). */
    public static final DeferredBlock<G3VacuumModuleBlock> VACUUM_MODULE =
            BLOCKS.registerBlock("g3_vacuum_module", G3VacuumModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<G3DepositionModuleBlock> COMPUTE_ETCH_MODULE =
            BLOCKS.registerBlock("g3_compute_etch_module", G3DepositionModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<G3DepositionModuleBlock> MEMORY_ETCH_MODULE =
            BLOCKS.registerBlock("g3_memory_etch_module", G3DepositionModuleBlock::new,
                    properties -> properties.strength(3.5f, 7.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<G3DepositionModuleBlock> MEMORY_UNIFORMITY_HEAD =
            BLOCKS.registerBlock("t8_memory_uniformity_head", G3DepositionModuleBlock::new,
                    properties -> properties.strength(4, 8).requiresCorrectToolForDrops());
    public static final DeferredBlock<G3DepositionModuleBlock> COMPUTE_DOSE_HEAD =
            BLOCKS.registerBlock("t8_compute_dose_head", G3DepositionModuleBlock::new,
                    properties -> properties.strength(4, 8).requiresCorrectToolForDrops());

    // ---------------------------------------------------------------- block entity types

    /** Shared member block entity for all non-controller G3 blocks. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<G3MemberBlockEntity>> MEMBER_BE =
            BLOCK_ENTITIES.register("g3_member",
                    () -> new BlockEntityType<>(G3MemberBlockEntity::new,
                            CASING.get(), POWER_PORT.get(), OPERATING_PANEL.get(),
                            WATER_INLET.get(), WRITING_MODULE.get(), METROLOGY_MODULE.get(),
                            DEPOSITION_MODULE.get(), RINSE_MODULE.get(), VACUUM_MODULE.get(),
                            COMPUTE_ETCH_MODULE.get(), MEMORY_ETCH_MODULE.get(), MEMORY_UNIFORMITY_HEAD.get(), COMPUTE_DOSE_HEAD.get()));

    /** Reticle writer controller block entity. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<G3ReticleWriterControllerBlockEntity>> RETICLE_WRITER_CONTROLLER_BE =
            BLOCK_ENTITIES.register("g3_reticle_writer",
                    () -> new BlockEntityType<>(G3ReticleWriterControllerBlockEntity::new, RETICLE_WRITER_CONTROLLER.get()));

    /** Deposition etcher controller block entity. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<G3DepositionEtcherControllerBlockEntity>> DEPOSITION_ETCHER_CONTROLLER_BE =
            BLOCK_ENTITIES.register("g3_deposition_etcher",
                    () -> new BlockEntityType<>(G3DepositionEtcherControllerBlockEntity::new, DEPOSITION_ETCHER_CONTROLLER.get()));

    // ---------------------------------------------------------------- menu types

    public static final DeferredHolder<MenuType<?>, MenuType<G3ReticleWriterMenu>> RETICLE_WRITER_MENU =
            MENU_TYPES.register("g3_reticle_writer", () -> IMenuTypeExtension.create(G3ReticleWriterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<G3DepositionEtcherMenu>> DEPOSITION_ETCHER_MENU =
            MENU_TYPES.register("g3_deposition_etcher", () -> IMenuTypeExtension.create(G3DepositionEtcherMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PlanarizerCabinetMenu>> PLANARIZER_CABINET_MENU =
            MENU_TYPES.register("planarizer_cabinet", () -> IMenuTypeExtension.create(PlanarizerCabinetMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<G3OperatingPanelMenu>> OPERATING_PANEL_MENU =
            MENU_TYPES.register("g3_operating_panel", () -> IMenuTypeExtension.create(G3OperatingPanelMenu::new));

    private G3Machines() {}

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(G3Machines::registerCapabilities);
    }

    /** Registers live utility lenses. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,MEMBER_BE.get(),(be,side)->be.waterInput());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MEMBER_BE.get(),
                (blockEntity, side) -> blockEntity instanceof G3MemberBlockEntity member
                        ? member.energy()
                        : null);
    }
}