package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;
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
 * The self-contained VacuumMachines group: the central vacuum plant multiblock.
 *
 * <p>Seven blocks: the {@code central_vacuum_plant} controller, the {@code vacuum_casing} frame,
 * the {@code vacuum_power_port} FE port, the {@code vacuum_operating_panel} upper panel,
 * the {@code vacuum_service_conduit} conduit, the {@code cleanroom_vacuum_port} passthrough
 * (sealed wall block; coordinator tags cleanroom shell), and the {@code vacuum_service_outlet}
 * service outlet for consumers.
 * Two block entity types: one for the controller and one shared member type over all six member blocks.
 * One menu: the lower controller GUI with network status.
 * Registration is deliberately own-registers-only, mirroring {@code UtilityMachines}:
 * the mod entry point calls {@link #register(IEventBus)} and nothing outside this package is
 * touched. Block items, creative tab entries, assets, recipes, lang and mining tags are the
 * coordinator's integration scope.</p>
 */
public final class VacuumMachines {

    /** Conduit block id for the vacuum network. */
    public static final String CONDUIT_ID = "vacuum_service_conduit";

    /** Passthrough block id for cleanroom vacuum ports (sealed wall blocks). */
    public static final String PASSTHROUGH_ID = "cleanroom_vacuum_port";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredBlock<VacuumControllerBlock> CONTROLLER = BLOCKS.registerBlock("central_vacuum_plant", VacuumControllerBlock::new, p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> CASING = BLOCKS.registerBlock("vacuum_casing", p -> new VacuumMemberBlock(p, BlockKind.CASING), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> POWER_PORT = BLOCKS.registerBlock("vacuum_power_port", p -> new VacuumMemberBlock(p, BlockKind.POWER_PORT), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> OPERATING_PANEL = BLOCKS.registerBlock("vacuum_operating_panel", p -> new VacuumMemberBlock(p, BlockKind.OPERATING_PANEL), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> CONDUIT = BLOCKS.registerBlock(CONDUIT_ID, p -> new VacuumMemberBlock(p, BlockKind.VACUUM_CONDUIT), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> PASSTHROUGH = BLOCKS.registerBlock(PASSTHROUGH_ID, p -> new VacuumMemberBlock(p, BlockKind.VACUUM_PORT), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<VacuumMemberBlock> SERVICE_OUTLET = BLOCKS.registerBlock("vacuum_service_outlet", p -> new VacuumMemberBlock(p, BlockKind.VACUUM_PORT), p -> p.strength(4.0f, 8.0f).requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("central_vacuum_plant", () -> new BlockEntityType<>(VacuumControllerBlockEntity::new, CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumMemberBlockEntity>> MEMBER_BE = BLOCK_ENTITIES.register("vacuum_member", () -> new BlockEntityType<>(VacuumMemberBlockEntity::new, CASING.get(), POWER_PORT.get(), OPERATING_PANEL.get(), CONDUIT.get(), PASSTHROUGH.get(), SERVICE_OUTLET.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<VacuumMenu>> MENU = MENU_TYPES.register("central_vacuum_plant", () -> IMenuTypeExtension.create(VacuumMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        bus.addListener(VacuumMachines::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Member FE power through the shared member type; the port lens refuses every
        // owner but the vacuum controller, so another machine cannot leech through it.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MEMBER_BE.get(), (be, side) -> be.energy());
    }

    private VacuumMachines() {}
}