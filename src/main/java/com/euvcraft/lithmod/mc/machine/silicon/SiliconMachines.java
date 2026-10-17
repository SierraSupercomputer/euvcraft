package com.euvcraft.lithmod.mc.machine.silicon;

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
 * Registers the two machines of the silicon chain: blocks, block entity types, menu types
 * and the energy capability.
 *
 * <p>This is the silicon chain's own registry class rather than an addition to the
 * framework's, because both machine blocks, both block entities and both menu types belong
 * to this milestone and nothing else has to find them. The block items live in
 * {@code ModItems}, because an item cannot be built before its block is registered.</p>
 *
 * <p>Every line follows the pattern the M1-02s spike proved:</p>
 * <ul>
 *   <li>{@code registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)}
 *       hands the factory properties that already carry the id;</li>
 *   <li>only {@code Capabilities.Energy.BLOCK} is registered, so item and fluid lookups
 *       return {@code null} on every side and no hopper can reach the slots;</li>
 *   <li>{@code RegisterCapabilitiesEvent} implements {@code IModBusEvent}, so the listener
 *       is attached here, never on {@code NeoForge.EVENT_BUS}.</li>
 * </ul>
 */
public final class SiliconMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);

    public static final DeferredBlock<CrystalPullerBlock> CRYSTAL_PULLER =
        BLOCKS.registerBlock("crystal_puller", CrystalPullerBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<WaferSlicerBlock> WAFER_SLICER =
        BLOCKS.registerBlock("wafer_slicer", WaferSlicerBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalPullerBlockEntity>>
        CRYSTAL_PULLER_BE = BLOCK_ENTITIES.register("crystal_puller",
            () -> new BlockEntityType<>(CrystalPullerBlockEntity::new, CRYSTAL_PULLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaferSlicerBlockEntity>>
        WAFER_SLICER_BE = BLOCK_ENTITIES.register("wafer_slicer",
            () -> new BlockEntityType<>(WaferSlicerBlockEntity::new, WAFER_SLICER.get()));

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CrystalPullerMenu>> CRYSTAL_PULLER_MENU =
        MENU_TYPES.register("crystal_puller", () -> IMenuTypeExtension.create(CrystalPullerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WaferSlicerMenu>> WAFER_SLICER_MENU =
        MENU_TYPES.register("wafer_slicer", () -> IMenuTypeExtension.create(WaferSlicerMenu::new));

    private SiliconMachines() {
    }

    /** Attaches every deferred register and the capability listener to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(SiliconMachines::registerCapabilities);
    }

    /**
     * The energy capability hook, the shape Arcforge uses:
     * {@code event.registerBlockEntity(capability, blockEntityType, (be, side) -> handler)}.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CRYSTAL_PULLER_BE.get(),
            (blockEntity, side) -> blockEntity instanceof FabMachineBlockEntity machine
                ? machine.energy()
                : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, WAFER_SLICER_BE.get(),
            (blockEntity, side) -> blockEntity instanceof FabMachineBlockEntity machine
                ? machine.energy()
                : null);
    }
}