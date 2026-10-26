package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
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
 * Registry objects for the T6 reflow oven.
 *
 * <p>Owns its own registry group so the coordinator only has to call {@link #register(IEventBus)}:
 * blocks, block entities, menus, the two items this machine adds, and the energy capability of
 * its power port. Datapack JSON, textures, creative tabs and world content stay with the
 * coordinator.</p>
 *
 * <p>Block/item ids: {@code reflow_oven} (controller), {@code reflow_casing},
 * {@code reflow_power_port}, {@code reflow_operating_panel},
 * {@code reflow_calibration_panel}, {@code thermal_test_coupon}, {@code assembly_scrap}.</p>
 */
public final class ReflowMachines {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EUVCRAFTMod.MODID);

    public static final DeferredBlock<ReflowControllerBlock> CONTROLLER = BLOCKS.registerBlock(
            "reflow_oven", ReflowControllerBlock::new, p -> p.strength(4, 8).requiresCorrectToolForDrops());

    public static final DeferredBlock<ReflowMemberBlock> CASING = member("reflow_casing", BlockKind.CASING);
    public static final DeferredBlock<ReflowMemberBlock> POWER_PORT =
            member("reflow_power_port", BlockKind.POWER_PORT);
    public static final DeferredBlock<ReflowMemberBlock> OPERATING_PANEL =
            member("reflow_operating_panel", BlockKind.OPERATING_PANEL);
    public static final DeferredBlock<ReflowMemberBlock> CALIBRATION_PANEL =
            member("reflow_calibration_panel", BlockKind.CALIBRATION_PANEL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReflowControllerBlockEntity>>
            CONTROLLER_BE = BLOCK_ENTITIES.register("reflow_oven",
                    () -> new BlockEntityType<>(ReflowControllerBlockEntity::new, CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReflowMemberBlockEntity>>
            MEMBER_BE = BLOCK_ENTITIES.register("reflow_member",
                    () -> new BlockEntityType<>(ReflowMemberBlockEntity::new,
                            CASING.get(), POWER_PORT.get(), OPERATING_PANEL.get(), CALIBRATION_PANEL.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ReflowControllerMenu>> CONTROLLER_MENU =
            MENU_TYPES.register("reflow_oven", () -> IMenuTypeExtension.create(ReflowControllerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ReflowOperatingMenu>> OPERATING_MENU =
            MENU_TYPES.register("reflow_operation", () -> IMenuTypeExtension.create(ReflowOperatingMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ReflowCalibrationMenu>> CALIBRATION_MENU =
            MENU_TYPES.register("reflow_calibration", () -> IMenuTypeExtension.create(ReflowCalibrationMenu::new));


    /** A cheap consumable for one paid thermal test. */
    public static final DeferredItem<Item> THERMAL_TEST_COUPON =
            ITEMS.registerSimpleItem("thermal_test_coupon");
    /** Failure output of an admitted reflow assembly. */
    public static final DeferredItem<Item> ASSEMBLY_SCRAP = ITEMS.registerSimpleItem("assembly_scrap");

    private static DeferredBlock<ReflowMemberBlock> member(String id, BlockKind kind) {
        return BLOCKS.registerBlock(id, p -> new ReflowMemberBlock(p, kind),
                p -> p.strength(4, 8).requiresCorrectToolForDrops());
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        ITEMS.register(bus);
        bus.addListener((java.util.function.Consumer<RegisterCapabilitiesEvent>) ReflowMachines::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Only the power-port member answers with a handler; every other member returns null.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MEMBER_BE.get(),
                (member, side) -> member.energy());
    }

    private ReflowMachines() {}
}
