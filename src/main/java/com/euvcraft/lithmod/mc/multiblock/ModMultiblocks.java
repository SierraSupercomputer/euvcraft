package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.process.OperatingPanelMenu;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;

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
 * Registers the Process Cell multiblock: four blocks, four block entity types, two menu types,
 * and the power port's energy capability.
 *
 * <p>Every line follows a pattern the M1-02s spike proved against the real 26.3 classpath and
 * M1-02b then used in production:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)} hands the
 *       factory properties that already carry the registry id. That is what avoids
 *       {@code NullPointerException: Block id not set}; building properties inside the block
 *       constructor does not.</li>
 *   <li>block entity types are {@code new BlockEntityType<>(factory, block)} keyed on the block.</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so its listener is
 *       attached to the bus handed to the {@code @Mod} constructor and never to
 *       {@code NeoForge.EVENT_BUS}. A {@code getCapability} override on a block entity is not
 *       consulted for capabilities in 26.3, which is why the port has no such override.</li>
 * </ul>
 */
public final class ModMultiblocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    // Menu type registration on the mod bus (not a second registry class)
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ProcessCellMenu>> PROCESS_CELL_MENU_TYPE =
            MENU_TYPES.register("process_cell", () -> IMenuTypeExtension.create(ProcessCellMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<OperatingPanelMenu>> OPERATING_PANEL_MENU_TYPE =
            MENU_TYPES.register("operating_panel", () -> IMenuTypeExtension.create(OperatingPanelMenu::new));

    // ---------------------------------------------------------------- blocks

    /** The controller: owns the cell and its energy buffer. */
    public static final DeferredBlock<ProcessCellControllerBlock> PROCESS_CELL_CONTROLLER =
            BLOCKS.registerBlock("process_cell_controller", ProcessCellControllerBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** Casing: the filler blocks. */
    public static final DeferredBlock<ProcessCellCasingBlock> PROCESS_CELL_CASING =
            BLOCKS.registerBlock("process_cell_casing", ProcessCellCasingBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    /** Power port: the only member that exposes anything. */
    public static final DeferredBlock<ProcessCellPowerPortBlock> PROCESS_CELL_POWER_PORT =
            BLOCKS.registerBlock("process_cell_power_port", ProcessCellPowerPortBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    /** Operating panel: the upper panel of the two-panel machine, placed directly above the controller. */
    public static final DeferredBlock<ProcessCellOperatingPanelBlock> PROCESS_CELL_OPERATING_PANEL =
            BLOCKS.registerBlock("process_cell_operating_panel", ProcessCellOperatingPanelBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    // ---------------------------------------------------------------- block entity types

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellControllerBlockEntity>>
            PROCESS_CELL_CONTROLLER_BE = BLOCK_ENTITIES.register("process_cell_controller",
            () -> new BlockEntityType<ProcessCellControllerBlockEntity>(
                    ProcessCellControllerBlockEntity::new, PROCESS_CELL_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellCasingBlockEntity>>
            PROCESS_CELL_CASING_BE = BLOCK_ENTITIES.register("process_cell_casing",
            () -> new BlockEntityType<ProcessCellCasingBlockEntity>(
                    ProcessCellCasingBlockEntity::new, PROCESS_CELL_CASING.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellPowerPortBlockEntity>>
            PROCESS_CELL_POWER_PORT_BE = BLOCK_ENTITIES.register("process_cell_power_port",
            () -> new BlockEntityType<ProcessCellPowerPortBlockEntity>(
                    ProcessCellPowerPortBlockEntity::new, PROCESS_CELL_POWER_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessCellOperatingPanelBlockEntity>>
            PROCESS_CELL_OPERATING_PANEL_BE = BLOCK_ENTITIES.register("process_cell_operating_panel",
            () -> new BlockEntityType<ProcessCellOperatingPanelBlockEntity>(
                    ProcessCellOperatingPanelBlockEntity::new, PROCESS_CELL_OPERATING_PANEL.get()));

    // ---------------------------------------------------------------- block items
    //
    // The four block items are declared in ModItems, because a block item cannot be built before
    // its block is registered and the item registry is the natural home for it:
    //   ModItems.PROCESS_CELL_CONTROLLER / _CASING / _POWER_PORT / _OPERATING_PANEL

    private ModMultiblocks() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus); // register menu types on mod bus
        modEventBus.addListener(ModMultiblocks::registerCapabilities);
    }

    /**
     * The power port's energy capability.
     *
     * <p>Only the port gets one, and only {@code Capabilities.Energy.BLOCK}: an item or fluid lookup
     * on a port returns null on every side, which the game test asserts. The handler is
     * {@link ProcessCellPowerPortBlockEntity#energy()}, so it is the controller's buffer while the
     * cell is formed and null otherwise.</p>
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, PROCESS_CELL_POWER_PORT_BE.get(),
                (blockEntity, side) -> blockEntity instanceof ProcessCellPowerPortBlockEntity port
                        ? port.energy()
                        : null);
    }
}