package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.test.TestMachineMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types registered by EUVCRAFT.
 *
 * <p>{@code IMenuTypeExtension.create} is what supplies the client-side constructor that
 * takes a {@code RegistryFriendlyByteBuf}; the plain
 * {@code new MenuType<>(MenuSupplier, FeatureFlagSet)} constructor cannot.</p>
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, EUVCRAFTMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TestMachineMenu>> TEST_MACHINE =
        MENU_TYPES.register("test_machine", () -> IMenuTypeExtension.create(TestMachineMenu::new));

    private ModMenus() {
    }
}
