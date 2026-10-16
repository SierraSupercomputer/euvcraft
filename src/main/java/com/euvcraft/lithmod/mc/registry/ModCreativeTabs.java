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

    /**
     * The EUVCRAFT chips and consumables tab.
     *
     * <p>A second tab rather than more entries in {@link #MAIN}, because the M0-08 game
     * test {@code foup_creative_tab_entries} pins the main tab to exactly the two FOUP
     * variants. Every chip and every consumable this milestone registers appears here,
     * so later milestones only add entries to this tab.</p>
     */
    public static final Supplier<CreativeModeTab> CHIPS = CREATIVE_MODE_TABS.register(
        "1_chips_and_consumables",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.chips"))
            .icon(() -> new ItemStack(ModItems.LOGIC_CHIP_T1.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.LOGIC_CHIP_T1.get());
                output.accept(ModItems.LOGIC_CHIP_T2.get());
                output.accept(ModItems.MEMORY_CHIP_T2.get());
                output.accept(ModItems.CHIP_SCRAP.get());
                output.accept(ModItems.RESIST_T1.get());
                output.accept(ModItems.RESIST_T2.get());
                output.accept(ModItems.CHIP_PACKAGE.get());
            })
            .build()
    );

    /**
     * The Process Cell multiblock blocks.
     *
     * <p>A third tab rather than entries in {@link #MAIN} or {@link #CHIPS}: the M0-08 game test
     * pins the main tab to exactly two entries and the M1-05 test pins the chips tab to exactly
     * seven, so adding to either would break a passing test rather than extend a feature.</p>
     */
    public static final Supplier<CreativeModeTab> PROCESS_CELL = CREATIVE_MODE_TABS.register(
        "2_process_cell",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.process_cell"))
            .icon(() -> new ItemStack(ModItems.PROCESS_CELL_CONTROLLER.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.PROCESS_CELL_CONTROLLER.get());
                output.accept(ModItems.PROCESS_CELL_CASING.get());
                output.accept(ModItems.PROCESS_CELL_POWER_PORT.get());
            })
            .build()
    );

    private ModCreativeTabs() {
    }
}