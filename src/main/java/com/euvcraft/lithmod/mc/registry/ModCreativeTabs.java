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

    /**
     * The single EUVCRAFT creative tab.
     *
     * <p>Unified ordering: the exact sequence is declared here so the
     * presentation test can compare {@code tab.getDisplayItems()} against it.
     * Stale comments about creating multiple tabs to preserve obsolete count tests
     * have been removed.</p>
     */
    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
        "0_main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lithmod"))
            .icon(() -> new ItemStack(ModItems.FOUP_MK1.get()))
            .displayItems((parameters, output) -> {
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
                output.accept(ModItems.ADVANCED_OPERATING_PANEL.get());
                output.accept(ModItems.ADVANCED_TEST_PACKAGE_CONTROLLER.get());
                output.accept(ModItems.ADVANCED_CASING.get());
                output.accept(ModItems.ADVANCED_POWER_PORT.get());
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