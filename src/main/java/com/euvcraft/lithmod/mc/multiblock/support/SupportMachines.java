package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;

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
 * Registers the shared support-machine foundation: five blocks (two controllers, one casing, one
 * power port, one optional water inlet), TWO block entity types (one controller BE type valid for both controller blocks,
 * one shared member BE type valid for casing, power port and water inlet), two menu types (one per shell), and the
 * FE capability.
 *
 * <p>Every line follows the pattern the Process Cell registry ({@code ModMultiblocks}) and the M1
 * machine registries proved against the real 26.3 classpath:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)} hands the
 *       factory properties that already carry the registry id.</li>
 *   <li>{@code BlockEntityType} is keyed on all of its valid blocks, so one controller BE type
 *       answers for both controller block instances and one member BE type for casing and port.
 *       The kind is derived from the actual block state, never from the BE type.</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so its listener is
 *       attached to the mod bus handed to the {@code @Mod} constructor, never to
 *       {@code NeoForge.EVENT_BUS}.</li>
 * </ul>
 *
 * <p><b>The FE rule.</b> The energy capability is registered on the shared member BE type, and the
 * provider filters on the actual block state: only a {@link SupportPowerPortBlock} position gets
 * the handler, and only while it belongs to a formed machine whose live controller is the
 * registered owner. So: FE reaches the world from a formed, owned power port — never from a
 * controller, never from a casing, never while unformed. No item capability is registered. Fluid insertion is exposed only by a formed purifier water inlet,
 * on all faces, through the same controller tank used by bucket handling.</p>
 */
public final class SupportMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    // ---------------------------------------------------------------- blocks

    /** The Boule Purifier controller: one of the two kinds of the shared controller block. */
    public static final DeferredBlock<SupportMachineControllerBlock> BOULE_PURIFIER =
            BLOCKS.registerBlock("boule_purifier", SupportMachineControllerBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** The E-beam Mask Writer controller: the other kind of the shared controller block. */
    public static final DeferredBlock<SupportMachineControllerBlock> E_BEAM_MASK_WRITER =
            BLOCKS.registerBlock("ebeam_mask_writer", SupportMachineControllerBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    /** Support casing: the filler of the 2x2x3 structure, distinct from the Process Cell casing. */
    public static final DeferredBlock<SupportCasingBlock> SUPPORT_CASING =
            BLOCKS.registerBlock("support_casing", SupportCasingBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    /** Support power port: the only support member that can expose FE while formed and owned. */
    public static final DeferredBlock<SupportPowerPortBlock> SUPPORT_POWER_PORT =
            BLOCKS.registerBlock("support_power_port", SupportPowerPortBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    /** Support water inlet: optional member that replaces casing in the purifier only; exposes fluid capability. */
    public static final DeferredBlock<SupportWaterInletBlock> SUPPORT_WATER_INLET =
            BLOCKS.registerBlock("support_water_inlet", SupportWaterInletBlock::new,
                    properties -> properties.strength(2.5f, 5.0f).requiresCorrectToolForDrops());

    // ---------------------------------------------------------------- block entity types

    /**
     * ONE controller BE type valid for BOTH controller blocks; the kind is read from the block
     * state in the block entity itself.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupportMachineBlockEntity>>
            SUPPORT_CONTROLLER_BE = BLOCK_ENTITIES.register("support_controller",
            () -> new BlockEntityType<SupportMachineBlockEntity>(
                    SupportMachineBlockEntity::new,
                    SupportMachines.BOULE_PURIFIER.get(), SupportMachines.E_BEAM_MASK_WRITER.get()));

    /** ONE shared member BE type, valid for the casing block, the power port block, and the water inlet block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupportMemberBlockEntity>>
            SUPPORT_MEMBER_BE = BLOCK_ENTITIES.register("support_member",
            () -> new BlockEntityType<SupportMemberBlockEntity>(
                    SupportMemberBlockEntity::new,
                    SupportMachines.SUPPORT_CASING.get(), SupportMachines.SUPPORT_POWER_PORT.get(), SupportMachines.SUPPORT_WATER_INLET.get()));

    // ---------------------------------------------------------------- menu types

    /** The Boule Purifier shell menu. */
    public static final DeferredHolder<MenuType<?>, MenuType<SupportMachineMenu>> SUPPORT_PURIFIER_MENU =
            MENU_TYPES.register("support_purifier", () -> IMenuTypeExtension.create(SupportMachineMenu::clientPurifier));

    /** The E-beam Mask Writer shell menu. */
    public static final DeferredHolder<MenuType<?>, MenuType<SupportMachineMenu>> SUPPORT_EBEAM_MENU =
            MENU_TYPES.register("support_ebeam", () -> IMenuTypeExtension.create(SupportMachineMenu::clientEbeam));

    private SupportMachines() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(SupportMachines::registerCapabilities);
    }

    /**
     * The FE capability, filtered on the actual block state.
     *
     * <p>The shared member BE type holds one registration, and because casing, port, and inlet share it,
     * the provider must be the filter: a casing position answers null on every side, an unformed
     * or unowned port answers null, and a formed owned port hands over the controller's buffer
     * lens ({@link SupportMemberBlockEntity#energy()}). The water inlet exposes fluid capability
     * instead of energy. No controller capability and no item capability exists anywhere in this package.</p>
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SUPPORT_MEMBER_BE.get(),
            (blockEntity, side) -> blockEntity instanceof SupportMemberBlockEntity member
                    ? member.energy()   // null unless a formed, owned power port
                    : null);

        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SUPPORT_MEMBER_BE.get(),
            (blockEntity, side) -> blockEntity instanceof SupportMemberBlockEntity member ? member.waterInput() : null);
    }

    /**
     * Which core {@link BlockKind} a block state reports, for the matcher's {@code blockKindAt}
     * callback and for tests that need to double-check the mapping.
     */
    public static BlockKind kindOf(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof SupportMachineControllerBlock) {
            return BlockKind.CONTROLLER;
        }
        if (state.getBlock() instanceof SupportPowerPortBlock) {
            return BlockKind.POWER_PORT;
        }
        if (state.getBlock() instanceof SupportWaterInletBlock) {
            return BlockKind.WATER_INLET;
        }
        if (state.getBlock() instanceof SupportCasingBlock) {
            return BlockKind.CASING;
        }
        return BlockKind.OTHER;
    }
}
