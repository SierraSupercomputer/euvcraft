package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
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
 * The self-contained HydrogenMachines group: the hydrogen purifier multiblock.
 *
 * <p>Five blocks: the {@code hydrogen_purifier} controller, the {@code hydrogen_casing} frame,
 * the {@code hydrogen_power_port} FE port, the {@code hydrogen_gas_inlet} raw H2 inlet,
 * the {@code hydrogen_gas_outlet} purified H2 outlet, and the {@code hydrogen_operating_panel} upper panel.
 * Two block entity types: one for the controller and one shared member type over all four member blocks.
 * One menu: the lower controller GUI with batch progress and tank status.
 * Registration is deliberately own-registers-only, mirroring {@code UtilityMachines}:
 * the mod entry point calls {@link #register(IEventBus)} and nothing outside this package is
 * touched. Block items, creative tab entries, assets, recipes, lang and mining tags are the
 * coordinator's integration scope.</p>
 */
public final class HydrogenMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredBlock<HydrogenControllerBlock> CONTROLLER = BLOCKS.registerBlock("hydrogen_purifier", HydrogenControllerBlock::new, p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<HydrogenMemberBlock> CASING = BLOCKS.registerBlock("hydrogen_casing", p -> new HydrogenMemberBlock(p, BlockKind.CASING), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<HydrogenMemberBlock> POWER_PORT = BLOCKS.registerBlock("hydrogen_power_port", p -> new HydrogenMemberBlock(p, BlockKind.POWER_PORT), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<HydrogenMemberBlock> GAS_INLET = BLOCKS.registerBlock("hydrogen_gas_inlet", p -> new HydrogenMemberBlock(p, BlockKind.GAS_INLET), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<HydrogenMemberBlock> GAS_OUTLET = BLOCKS.registerBlock("hydrogen_gas_outlet", p -> new HydrogenMemberBlock(p, BlockKind.GAS_OUTLET), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<HydrogenMemberBlock> OPERATING_PANEL = BLOCKS.registerBlock("hydrogen_operating_panel", p -> new HydrogenMemberBlock(p, BlockKind.OPERATING_PANEL), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrogenControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("hydrogen_purifier", () -> new BlockEntityType<>(HydrogenControllerBlockEntity::new, CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrogenMemberBlockEntity>> MEMBER_BE = BLOCK_ENTITIES.register("hydrogen_member", () -> new BlockEntityType<>(HydrogenMemberBlockEntity::new, CASING.get(), POWER_PORT.get(), GAS_INLET.get(), GAS_OUTLET.get(), OPERATING_PANEL.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<HydrogenMenu>> MENU = MENU_TYPES.register("hydrogen_purifier", () -> IMenuTypeExtension.create(HydrogenMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        bus.addListener(HydrogenMachines::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Member FE power through the shared member type; the port lens refuses every
        // owner but the hydrogen controller, so another machine cannot leech through it.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MEMBER_BE.get(), (be, side) -> be.energy());
        // Gas inlet/outlet fluid lenses through the shared member type.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MEMBER_BE.get(), (be, side) -> be.handler());
    }

    private HydrogenMachines() {}
}