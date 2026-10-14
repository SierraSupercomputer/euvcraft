package com.euvcraft.lithmod.mc.registry;

import java.util.function.Supplier;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative tabs registered by EUVCRAFT.
 *
 * <p>The tab hands out one empty FOUP of each variant. "Empty" means a real lot with a
 * freshly drawn lot id and the configured capacity, not a FOUP without a component: a
 * lot without hidden values is a broken lot.</p>
 *
 * <p>The game builds tab contents on the client as well, so the lot id of a tab entry
 * is drawn on whichever side builds the contents first. That is harmless for a lot the
 * player has just picked up and has nothing to do with the creative-mode spike
 * documented in the M0-08 report.</p>
 */
public final class ModCreativeTabs {

    /** Registry of EUVCRAFT creative tabs. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EUVCRAFTMod.MODID);

    /** The EUVCRAFT tab. */
    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
        "0_main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod"))
            .icon(() -> new ItemStack(ModItems.FOUP_MK1.get()))
            .displayItems((parameters, output) -> {
                output.accept(FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
                output.accept(FoupItems.emptyFoup(ModItems.FOUP_MK2.get()));
            })
            .build()
    );

    private ModCreativeTabs() {
    }
}