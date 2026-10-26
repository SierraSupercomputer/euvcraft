package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The self-contained ZoneMachines group: the zone refiner multiblock and its product item.
 *
 * <p>Five blocks: the {@code zone_refiner} controller, the {@code zone_casing} frame, the
 * {@code zone_power_port} FE port, the {@code zone_operating_panel} upper panel and the
 * {@code zone_water_inlet}. Two block entity types: one for the controller and one shared
 * member type over all four member blocks. Two menus: the lower status-only controller GUI
 * and the upper operating panel GUI with the boule slots. One item: the
 * {@code electronic_grade_boule} product, a plain item at the vanilla stack size of 64.</p>
 *
 * <p>Registration is deliberately own-registers-only, mirroring {@code UtilityMachines}:
 * the mod entry point calls {@link #register(IEventBus)} and nothing outside this package is
 * touched. Block items, creative tab entries, assets, recipes, lang and mining tags are the
 * coordinator's integration scope.</p>
 */
public final class ZoneMachines {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);
    /** The zone's own item register; block items stay with the coordinator's ModItems. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EUVCRAFTMod.MODID);

    public static final DeferredBlock<ZoneControllerBlock> CONTROLLER = BLOCKS.registerBlock("zone_refiner", ZoneControllerBlock::new, p -> p.strength(3, 6).requiresCorrectToolForDrops());
    public static final DeferredBlock<ZoneMemberBlock> CASING = BLOCKS.registerBlock("zone_casing", p -> new ZoneMemberBlock(p, BlockKind.CASING), p -> p.strength(3, 6).requiresCorrectToolForDrops());
    public static final DeferredBlock<ZoneMemberBlock> POWER_PORT = BLOCKS.registerBlock("zone_power_port", p -> new ZoneMemberBlock(p, BlockKind.POWER_PORT), p -> p.strength(3, 6).requiresCorrectToolForDrops());
    public static final DeferredBlock<ZoneMemberBlock> OPERATING_PANEL = BLOCKS.registerBlock("zone_operating_panel", p -> new ZoneMemberBlock(p, BlockKind.OPERATING_PANEL), p -> p.strength(3, 6).requiresCorrectToolForDrops());
    public static final DeferredBlock<ZoneMemberBlock> WATER_INLET = BLOCKS.registerBlock("zone_water_inlet", p -> new ZoneMemberBlock(p, BlockKind.WATER_INLET), p -> p.strength(3, 6).requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZoneControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("zone_refiner", () -> new BlockEntityType<>(ZoneControllerBlockEntity::new, CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZoneMemberBlockEntity>> MEMBER_BE = BLOCK_ENTITIES.register("zone_member", () -> new BlockEntityType<>(ZoneMemberBlockEntity::new, CASING.get(), POWER_PORT.get(), OPERATING_PANEL.get(), WATER_INLET.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ZoneStatusMenu>> STATUS_MENU = MENU_TYPES.register("zone_refiner", () -> IMenuTypeExtension.create(ZoneStatusMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ZoneOperatingPanelMenu>> PANEL_MENU = MENU_TYPES.register("zone_operating_panel", () -> IMenuTypeExtension.create(ZoneOperatingPanelMenu::new));

    /** The refined product boule; a plain item with no hidden data, vanilla stack of 64. */
    public static final DeferredItem<Item> ELECTRONIC_GRADE_BOULE = ITEMS.registerSimpleItem("electronic_grade_boule");

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        ITEMS.register(bus);
        bus.addListener(ZoneMachines::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Member FE power through the shared member type; the port lens refuses every
        // owner but the zone controller, so an advanced machine cannot leech through it.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MEMBER_BE.get(), (be, side) -> be.energy());
        // The water inlet's UPW lens; every other zone member reports null here.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MEMBER_BE.get(), (be, side) -> be.handler());
    }

    private ZoneMachines() {}
}
