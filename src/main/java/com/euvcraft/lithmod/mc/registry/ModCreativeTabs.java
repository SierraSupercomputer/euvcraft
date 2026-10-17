package com.euvcraft.lithmod.mc.registry;

import java.util.function.Supplier;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;

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

    /** The EUVCRAFT line components tab. */
    public static final Supplier<CreativeModeTab> COMPONENTS = CREATIVE_MODE_TABS.register(
        "3_line_components",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.line_components"))
            .icon(() -> new ItemStack(ModItems.MERCURY_LAMP.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MERCURY_LAMP.get());
                output.accept(ModItems.I_LINE_LAMP.get());
                output.accept(ModItems.CONTACT_PLATE.get());
                output.accept(ModItems.REDUCTION_LENS.get());
                output.accept(ModItems.MANUAL_ALIGNER.get());
                output.accept(ModItems.STEPPER_STAGE.get());
                output.accept(ModItems.BASIC_AIR_FILTER.get());
                output.accept(ModItems.FINE_AIR_FILTER.get());
                output.accept(ModItems.OPTICAL_MICROSCOPE.get());
                output.accept(ModItems.OVERLAY_SCOPE.get());
            })
            .build()
    );

    /**
     * The silicon chain: the two feedstock items and the two machines that turn them into
     * a lot of wafers.
     *
     * <p>A fourth tab for the same reason the others exist: the game tests pin the main tab
     * to exactly two entries and the chips tab to exactly seven, so adding to either would
     * break a passing test.</p>
     */
    public static final Supplier<CreativeModeTab> SILICON_CHAIN = CREATIVE_MODE_TABS.register(
        "4_silicon_chain",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.silicon_chain"))
            .icon(() -> new ItemStack(ModItems.SILICON.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SILICON.get());
                output.accept(ModItems.SILICON_BOULE.get());
                output.accept(ModItems.CRYSTAL_PULLER.get());
                output.accept(ModItems.WAFER_SLICER.get());
            })
            .build()
    );

    /**
     * The photomask group: the blank plate and one example mask per die type the
     * milestone designs for.
     *
     * <p>A fifth tab for the same reason the others exist: the game tests pin the
     * entry count of the main, chips, process cell, line component and silicon
     * chain tabs, so a new item group gets a new tab rather than a new entry in
     * one of those.</p>
     */
    public static final Supplier<CreativeModeTab> MASKS = CREATIVE_MODE_TABS.register(
        "5_masks",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.masks"))
            .icon(() -> new ItemStack(ModItems.MASK.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.BLANK_MASK.get());
                output.accept(sampleMask(DieTypes.LOGIC, 1));
                output.accept(sampleMask(DieTypes.LOGIC, 2));
                output.accept(sampleMask(DieTypes.MEMORY, 2));
            })
            .build()
    );

    /**
     * Builds one usable photomask for the tab.
     *
     * <p>The tab hands out masks that are actually patterned: a design a writer
     * would accept, not a bare {@code mask} stack with no design at all. The die
     * types have to be members of {@link com.euvcraft.lithmod.core.DieTypes#ALL},
     * because that is what {@link com.euvcraft.lithmod.core.MaskData} accepts;
     * the samples therefore pass the canonical constants rather than literal ids,
     * because those constants are the bare ids and a namespaced literal is
     * rejected.</p>
     *
     * <p>The remaining uses come from the config binding rather than from a number
     * written here, so the samples follow the configured {@code maskUses} instead
     * of pinning a balance value into a tab.</p>
     *
     * @param dieType   canonical bare die type id
     * @param tierIndex 1-based node tier the sample mask is cut for
     * @return a mask stack with the public design component filled in
     */
    private static ItemStack sampleMask(String dieType, int tierIndex) {
        ItemStack mask = new ItemStack(ModItems.MASK.get());
        mask.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(dieType, tierIndex, EUVCRAFTMod.settings().maskUses()));
        return mask;
    }

    private ModCreativeTabs() {
    }
}