package com.euvcraft.lithmod.mc.multiblock.assembly;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AssemblyMachines {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EUVCRAFTMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredBlock<AssemblyControllerBlock> CONTROLLER = BLOCKS.registerBlock(
        "pick_and_place_controller",
        AssemblyControllerBlock::new,
        p -> p.strength(3, 6).requiresCorrectToolForDrops()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyControllerBlockEntity>> CONTROLLER_BE =
        BLOCK_ENTITIES.register("pick_and_place_controller", () ->
            new BlockEntityType<>(AssemblyControllerBlockEntity::new, CONTROLLER.get())
        );

    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyMenu>> MENU =
        MENU_TYPES.register("pick_and_place", () -> IMenuTypeExtension.create(AssemblyMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        bus.addListener(AssemblyMachines::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            AdvancedMachines.ADVANCED_MEMBER_BE.get(),
            (member, side) -> member.energy()
        );
    }

    private AssemblyMachines() {}
}