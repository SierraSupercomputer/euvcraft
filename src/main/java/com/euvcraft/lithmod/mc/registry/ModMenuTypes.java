package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Menu types registered by EUVCRAFT.
 * Currently empty - menus will be added in later tasks.
 */
public final class ModMenuTypes {

    /** Registry of EUVCRAFT menu types. */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    private ModMenuTypes() {}

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}