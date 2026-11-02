package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Creative tabs registered by EUVCRAFT.
 *
 * <p>Three tabs: Resources (0_resources), Fab Blocks (1_fab_blocks), Items (2_items).
 * The MAIN alias is kept pointing to FAB_BLOCKS for source compatibility.</p>
 */
public final class ModCreativeTabs {

    /** Registry of EUVCRAFT creative tabs. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EUVCRAFTMod.MODID);

    /**
     * The Resources tab: raw materials, feedstocks, consumables, scrap.
     */
    public static final Supplier<CreativeModeTab> RESOURCES = CREATIVE_MODE_TABS.register(
        "0_resources",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.resources"))
            .icon(() -> new ItemStack(ModItems.SILICON.get()))
            .displayItems((parameters, output) -> {
                FilteredOutput filtered = new FilteredOutput(output, CreativeTabCategory.RESOURCES);
                populate(parameters, filtered);
            })
            .build()
    );

    /**
     * The Fab Blocks tab: all BlockItems (multiblock controllers, casings, ports, panels, cleanroom blocks).
     */
    public static final Supplier<CreativeModeTab> FAB_BLOCKS = CREATIVE_MODE_TABS.register(
        "1_fab_blocks",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.fab_blocks"))
            .icon(() -> new ItemStack(ModItems.PROCESS_CELL_CONTROLLER.get()))
            .displayItems((parameters, output) -> {
                FilteredOutput filtered = new FilteredOutput(output, CreativeTabCategory.FAB_BLOCKS);
                populate(parameters, filtered);
            })
            .build()
    );

    /**
     * Alias for FAB_BLOCKS retained for source compatibility.
     */
    public static final Supplier<CreativeModeTab> MAIN = FAB_BLOCKS;

    /**
     * The Items tab: FOUPs, masks, finished chips/CPUs/DIMMs/MCUs, line components, gown armor.
     */
    public static final Supplier<CreativeModeTab> ITEMS = CREATIVE_MODE_TABS.register(
        "2_items",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod.items"))
            .icon(() -> new ItemStack(ModItems.FOUP_MK1.get()))
            .displayItems((parameters, output) -> {
                FilteredOutput filtered = new FilteredOutput(output, CreativeTabCategory.ITEMS);
                populate(parameters, filtered);
            })
            .build()
    );

    /**
     * Collects all items that should appear in the creative tabs, partitioned by category.
     *
     * <p>This is the single source of truth for creative tab population. The three tab builders
     * call this through a {@link FilteredOutput} adapter that accepts only stacks whose item
     * matches the tab's category.</p>
     *
     * @param parameters the display parameters provided by the game
     * @param output the output consumer (possibly filtered)
     */
    private static void populate(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        // Silicon chain machines
        output.accept(ModItems.CRYSTAL_PULLER.get());
        output.accept(ModItems.WAFER_SLICER.get());
        // Pattern Generator
        output.accept(ModItems.PATTERN_GENERATOR.get());
        // Test and Package machine
        output.accept(ModItems.TEST_PACKAGE.get());
        // Process Cell multiblock blocks
        output.accept(ModItems.PROCESS_CELL_CONTROLLER.get());
        output.accept(ModItems.PROCESS_CELL_CASING.get());
        output.accept(ModItems.PROCESS_CELL_POWER_PORT.get());
        output.accept(ModItems.PROCESS_CELL_OPERATING_PANEL.get());
        // T3 support multiblocks (appended after the Process Cell blocks)
        output.accept(ModItems.BOULE_PURIFIER.get());
        output.accept(ModItems.E_BEAM_MASK_WRITER.get());
        output.accept(ModItems.SUPPORT_CASING.get());
        output.accept(ModItems.SUPPORT_POWER_PORT.get());
        output.accept(ModItems.SUPPORT_WATER_INLET.get());
        // T4 advanced multiblocks (appended after T3 support multiblocks)
        output.accept(ModItems.DEPOSITION_ETCHER_CONTROLLER.get());
        output.accept(ModItems.ETCH_CELL.get());
        output.accept(ModItems.G3_ETCH_CELL.get());
        output.accept(ModItems.G3_FAB_ITEM_PORT.get());
        output.accept(ModItems.EUV_FAB_ITEM_PORT.get());
        output.accept(ModItems.COATING_TRACK.get());
        output.accept(ModItems.WET_CLEANER.get());
        output.accept(ModItems.FAB_UPW_INLET.get());
        output.accept(ModItems.ADVANCED_OPERATING_PANEL.get());
        output.accept(ModItems.ADVANCED_TEST_PACKAGE_CONTROLLER.get());
        output.accept(ModItems.ADVANCED_CASING.get());
        output.accept(ModItems.ADVANCED_POWER_PORT.get());
        output.accept(ModItems.FAB_ITEM_PORT.get());
        output.accept(ModItems.SCANNER_CONTROLLER.get());
        output.accept(ModItems.SCANNER_OPERATING_PANEL.get());
        output.accept(ModItems.SCANNER_CALIBRATION_PANEL.get());
        output.accept(ModItems.SCANNER_EXPOSURE_MODULE.get());
        output.accept(ModItems.SCANNER_OPTICS_MODULE.get());
        output.accept(ModItems.SCANNER_STAGE_MODULE.get());
        output.accept(ModItems.SCANNER_FILTRATION_MODULE.get());
        output.accept(ModItems.SCANNER_METROLOGY_MODULE.get());
        // FOUP variants via FoupItems.emptyFoup (real sealed lots)
        output.accept(FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        output.accept(FoupItems.emptyFoup(ModItems.FOUP_MK2.get()));
        // Silicon feedstock and boule
        output.accept(ModItems.SILICON.get());
        output.accept(ModItems.ETCHANT_CHARGE.get());
        output.accept(ModItems.SILICON_BOULE.get());
        // Pure boule (appended after the ordinary boule; production is the purifier)
        output.accept(ModItems.PURE_SILICON_BOULE.get());
        // Blank mask + three sample patterned masks
        output.accept(ModItems.BLANK_MASK.get());
        output.accept(sampleMask(DieTypes.LOGIC, 1));
        output.accept(sampleMask(DieTypes.LOGIC, 2));
        output.accept(sampleMask(DieTypes.MEMORY, 2));
        // Line components (10)
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
        // Resists and chip package
        output.accept(ModItems.RESIST_T1.get());
        output.accept(ModItems.RESIST_T2.get());
        output.accept(ModItems.CHIP_PACKAGE.get());
        // Chips and scrap (4)
        output.accept(ModItems.LOGIC_CHIP_T1.get());
        output.accept(ModItems.LOGIC_CHIP_T2.get());
        output.accept(ModItems.MEMORY_CHIP_T2.get());
        output.accept(ModItems.CHIP_SCRAP.get());
        // T3 additions retain the existing order above.
        output.accept(ModItems.KRF_EXCIMER_LASER.get());
        output.accept(ModItems.DUV_PROJECTION_LENS.get());
        output.accept(ModItems.PRECISION_STEPPER_STAGE.get());
        output.accept(ModItems.HEPA_FILTER.get());
        output.accept(ModItems.LASER_INTERFEROMETER.get());
        output.accept(ModItems.RESIST_T3.get());
        output.accept(ModItems.LOGIC_CHIP_T3.get());
        output.accept(ModItems.MEMORY_CHIP_T3.get());
        output.accept(ModItems.COMPUTE_CHIP_T3.get());
        output.accept(ModItems.NAND_CHIP_T3.get());
        // T4 additions retain the existing order above.
        output.accept(ModItems.ARF_EXCIMER_LASER.get());
        output.accept(ModItems.ARF_DRY_LENS.get());
        output.accept(ModItems.NANOPOSITIONING_STAGE.get());
        output.accept(ModItems.ULPA_FILTER.get());
        output.accept(ModItems.PRECISION_OVERLAY_SCOPE.get());
        output.accept(ModItems.RESIST_T4.get());
        output.accept(ModItems.LOGIC_CHIP_T4.get());
        output.accept(ModItems.MEMORY_CHIP_T4.get());
        output.accept(ModItems.COMPUTE_CHIP_T4.get());
        output.accept(ModItems.NAND_CHIP_T4.get());
        output.accept(ModItems.UTILITY_PLANT.get());
        output.accept(ModItems.UTILITY_WATER_INLET.get());
        output.accept(ModItems.UTILITY_PURE_OUTLET.get());
        output.accept(ModItems.PICK_AND_PLACE.get());
        output.accept(ModItems.ASSEMBLY_CASING.get());
        output.accept(ModItems.ASSEMBLY_POWER_PORT.get());
        output.accept(ModItems.SCANNER_DIAGNOSTIC_PANEL.get());
        output.accept(ModItems.SCANNER_WATER_INLET.get());
        output.accept(ModItems.SCANNER_IMMERSION_MODULE.get());
        output.accept(ModItems.LOGIC_CHIP_T5.get());
        output.accept(ModItems.MEMORY_CHIP_T5.get());
        output.accept(ModItems.COMPUTE_CHIP_T5.get());
        output.accept(ModItems.NAND_CHIP_T5.get());
        output.accept(ModItems.UTILITY_FILTER.get());
        output.accept(ModItems.BURN_PAPER.get());
        output.accept(ModItems.CPU_BLANK.get());
        output.accept(ModItems.DIMM_BLANK.get());
        output.accept(ModItems.MICROCONTROLLER_BLANK.get());
        output.accept(ModItems.CPU_T3.get());
        output.accept(ModItems.CPU_T4.get());
        output.accept(ModItems.CPU_T5.get());
        output.accept(ModItems.DIMM_T2.get());
        output.accept(ModItems.DIMM_T3.get());
        output.accept(ModItems.DIMM_T4.get());
        output.accept(ModItems.DIMM_T5.get());
        output.accept(ModItems.MICROCONTROLLER_T1.get());
        output.accept(ModItems.MICROCONTROLLER_T2.get());
        output.accept(ModItems.MICROCONTROLLER_T3.get());
        output.accept(ModItems.MICROCONTROLLER_T4.get());
        output.accept(ModItems.MICROCONTROLLER_T5.get());
        output.accept(ModItems.RESIST_T5.get());
        output.accept(ModItems.IMMERSION_LIGHT_SOURCE.get());
        output.accept(ModItems.IMMERSION_OPTICS.get());
        output.accept(ModItems.IMMERSION_STAGE.get());
        output.accept(ModItems.IMMERSION_FILTRATION.get());
        output.accept(ModItems.IMMERSION_METROLOGY.get());
        output.accept(sampleMask(DieTypes.LOGIC, 5));
        output.accept(sampleMask(DieTypes.MEMORY, 5));
        output.accept(sampleMask(DieTypes.COMPUTE, 5));
        output.accept(sampleMask(DieTypes.NAND, 5));
        com.euvcraft.lithmod.mc.cleanroom.CleanroomItems.all().forEach(output::accept);
        output.accept(com.euvcraft.lithmod.mc.machine.cleaner.FoupCleanerMachines.ITEM.get());
        output.accept(com.euvcraft.lithmod.mc.machine.cleaner.FoupCleanerMachines.WAFER_SCRAP.get());
        // T6 fabrication and assembly
        output.accept(ModItems.ZONE_REFINER.get());
        output.accept(ModItems.ZONE_CASING.get());
        output.accept(ModItems.ZONE_POWER_PORT.get());
        output.accept(ModItems.ZONE_OPERATING_PANEL.get());
        output.accept(ModItems.ZONE_WATER_INLET.get());
        output.accept(ModItems.REFLOW_OVEN.get());
        output.accept(ModItems.REFLOW_CASING.get());
        output.accept(ModItems.REFLOW_POWER_PORT.get());
        output.accept(ModItems.REFLOW_OPERATING_PANEL.get());
        output.accept(ModItems.REFLOW_CALIBRATION_PANEL.get());
        output.accept(ModItems.SCANNER_MULTIPATTERNING_MODULE.get());
        output.accept(ModItems.MEMORY_CHIP_T6.get());
        output.accept(ModItems.COMPUTE_CHIP_T6.get());
        output.accept(ModItems.NAND_CHIP_T6.get());
        output.accept(ModItems.CPU_T6.get());
        output.accept(ModItems.DIMM_T6.get());
        output.accept(ModItems.RESIST_T6.get());
        output.accept(ModItems.MULTIPATTERN_LIGHT_SOURCE.get());
        output.accept(ModItems.MULTIPATTERN_OPTICS.get());
        output.accept(ModItems.MULTIPATTERN_STAGE.get());
        output.accept(ModItems.MULTIPATTERN_FILTRATION.get());
        output.accept(ModItems.MULTIPATTERN_METROLOGY.get());
        output.accept(com.euvcraft.lithmod.mc.multiblock.zone.ZoneMachines.ELECTRONIC_GRADE_BOULE.get());
        output.accept(com.euvcraft.lithmod.mc.multiblock.reflow.ReflowMachines.THERMAL_TEST_COUPON.get());
        output.accept(com.euvcraft.lithmod.mc.multiblock.reflow.ReflowMachines.ASSEMBLY_SCRAP.get());
        output.accept(sampleMask(DieTypes.MEMORY, 6));
        output.accept(sampleMask(DieTypes.COMPUTE, 6));
        output.accept(sampleMask(DieTypes.NAND, 6));
        // T7 follows the entire existing creative sequence.
        output.accept(ModItems.COMPUTE_CHIP_T7.get());
        output.accept(ModItems.MEMORY_CHIP_T7.get());
        output.accept(ModItems.NAND_CHIP_T7.get());
        output.accept(ModItems.CPU_T7.get());
        output.accept(ModItems.EUV_LIGHT_SOURCE.get());
        output.accept(ModItems.EUV_OPTICS.get());
        output.accept(ModItems.EUV_STAGE.get());
        output.accept(ModItems.EUV_FILTRATION.get());
        output.accept(ModItems.EUV_METROLOGY.get());
        com.euvcraft.lithmod.mc.T7Content.blockItems().stream()
            .filter(item -> item != com.euvcraft.lithmod.mc.multiblock.euv.EuvMachines.LIGHT_SOURCE_PULSE_MODULE.get().asItem()
                         && item != com.euvcraft.lithmod.mc.multiblock.euv.EuvMachines.LIGHT_SOURCE_CALIBRATION_PANEL.get().asItem())
            .forEach(output::accept);
        output.accept(ModItems.DIMM_T7.get());
        output.accept(ModItems.RESIST_T7.get());
        output.accept(ModItems.TIN_FEEDSTOCK.get());
        output.accept(com.euvcraft.lithmod.mc.multiblock.euv.EuvMachines.LIGHT_SOURCE_PULSE_MODULE.get());
        output.accept(com.euvcraft.lithmod.mc.multiblock.euv.EuvMachines.LIGHT_SOURCE_CALIBRATION_PANEL.get());
    }

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
            new MaskData(dieType, tierIndex, EUVCRAFTMod.settings().maskUses(), tierIndex >= 3 ? 2 : 1));
        return mask;
    }

    /**
     * Returns all entries that would appear in the creative tabs, in the exact order
     * declared by {@link #populate}.
     *
     * <p>This is exposed so the coordinator can verify that each entry partitions
     * into exactly one of the three categories.</p>
     *
     * @param parameters the display parameters provided by the game
     * @return an immutable list of all item stacks in population order
     */
    public static List<ItemStack> entries(CreativeModeTab.ItemDisplayParameters parameters) {
        List<ItemStack> result = new ArrayList<>();
        populate(parameters, (stack, visibility) -> result.add(stack));
        return List.copyOf(result);
    }

    /** Acceptance/debug helper that reads the actual built category tabs. */
    public static List<ItemStack> displayEntries(CreativeModeTab.ItemDisplayParameters parameters) {
        var result=new ArrayList<ItemStack>();
        for(var tab:List.of(RESOURCES.get(),FAB_BLOCKS.get(),ITEMS.get())){
            tab.buildContents(parameters);result.addAll(tab.getDisplayItems());
        }
        return List.copyOf(result);
    }

    /**
     * Output adapter that filters items by their creative tab category.
     */
    private static final class FilteredOutput implements CreativeModeTab.Output {
        private final CreativeModeTab.Output delegate;
        private final CreativeTabCategory category;

        FilteredOutput(CreativeModeTab.Output delegate, CreativeTabCategory category) {
            this.delegate = delegate;
            this.category = category;
        }

        @Override
        public void accept(ItemStack stack) {
            Item item = stack.getItem();
            if (CreativeTabCategory.of(item) == category) {
                delegate.accept(stack);
            }
        }

        @Override
        public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
            Item item = stack.getItem();
            if (CreativeTabCategory.of(item) == category) {
                delegate.accept(stack, visibility);
            }
        }
    }

    private ModCreativeTabs() {
    }
}
