package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.core.ProcessRun;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.ComponentCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellOperatingPanelBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * T3 content and admission game tests.
 * Validates T3 items, recipes, admission logic, frozen contract save/load, and end-to-end chain.
 */
public final class T3ContentGameTests {

    private static final String ENVIRONMENT_ID = "t3_content";

    private static final String T3_ITEMS_AND_CATALOG = "t3_items_and_catalog";
    private static final String T3_RECIPES_VALIDATION = "t3_recipes_validation";
    private static final String T3_ADMISSION_ORDINARY_REFUSAL = "t3_admission_ordinary_refusal";
    private static final String T3_ADMISSION_G1_MASK_REFUSAL = "t3_admission_g1_mask_refusal";
    private static final String T3_ADMISSION_PURE_G2_POSITIVE = "t3_admission_pure_g2_positive";
    private static final String T3_FROZEN_CONTRACT_SAVE_LOAD = "t3_frozen_contract_save_load";
    private static final String T3_MATRIX_RELOAD_PINS = "t3_matrix_reload_pins";
    private static final String T3_FORGED_GRADE_LOT_FAILS = "t3_forged_grade_lot_fails";
    private static final String T3_MALFORMED_CONTRACT_FAILS = "t3_malformed_contract_fails";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    static {
        FUNCTIONS.register(T3_ITEMS_AND_CATALOG, () -> T3ContentGameTests::t3ItemsAndCatalog);
        FUNCTIONS.register(T3_RECIPES_VALIDATION, () -> T3ContentGameTests::t3RecipesValidation);
        FUNCTIONS.register(T3_ADMISSION_ORDINARY_REFUSAL, () -> T3ContentGameTests::t3AdmissionOrdinaryRefusal);
        FUNCTIONS.register(T3_ADMISSION_G1_MASK_REFUSAL, () -> T3ContentGameTests::t3AdmissionG1MaskRefusal);
        FUNCTIONS.register(T3_ADMISSION_PURE_G2_POSITIVE, () -> T3ContentGameTests::t3AdmissionPureG2Positive);
        FUNCTIONS.register(T3_FROZEN_CONTRACT_SAVE_LOAD, () -> T3ContentGameTests::t3FrozenContractSaveLoad);
        FUNCTIONS.register(T3_MATRIX_RELOAD_PINS, () -> T3ContentGameTests::t3MatrixReloadPins);
        FUNCTIONS.register(T3_FORGED_GRADE_LOT_FAILS, () -> T3ContentGameTests::t3ForgedGradeLotFails);
        FUNCTIONS.register(T3_MALFORMED_CONTRACT_FAILS, () -> T3ContentGameTests::t3MalformedContractFails);
    }

    private T3ContentGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) T3ContentGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            500, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, T3_ITEMS_AND_CATALOG);
        register(event, data, T3_RECIPES_VALIDATION);
        register(event, data, T3_ADMISSION_ORDINARY_REFUSAL);
        register(event, data, T3_ADMISSION_G1_MASK_REFUSAL);
        register(event, data, T3_ADMISSION_PURE_G2_POSITIVE);
        register(event, data, T3_FROZEN_CONTRACT_SAVE_LOAD);
        register(event, data, T3_MATRIX_RELOAD_PINS);
        register(event, data, T3_FORGED_GRADE_LOT_FAILS);
        register(event, data, T3_MALFORMED_CONTRACT_FAILS);
        EUVCRAFTMod.LOGGER.info("Registered EUVCRAFT T3 content game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. T3 items exist and ChipCatalog has all four entries
    // ------------------------------------------------------------------
    private static void t3ItemsAndCatalog(GameTestHelper helper) {
        // Four T3 chip items registered
        helper.assertTrue(ModItems.LOGIC_CHIP_T3.get() != null, "LOGIC_CHIP_T3 must be registered");
        helper.assertTrue(ModItems.MEMORY_CHIP_T3.get() != null, "MEMORY_CHIP_T3 must be registered");
        helper.assertTrue(ModItems.COMPUTE_CHIP_T3.get() != null, "COMPUTE_CHIP_T3 must be registered");
        helper.assertTrue(ModItems.NAND_CHIP_T3.get() != null, "NAND_CHIP_T3 must be registered");

        // RESIST_T3 registered with tier 3 component
        ItemStack resistT3 = new ItemStack(ModItems.RESIST_T3.get());
        helper.assertTrue(ResistCatalog.tierOf(resistT3) == 3, "RESIST_T3 must report tier 3");
        helper.assertTrue(resistT3.getMaxStackSize() == 64, "RESIST_T3 must stack to 64");

        // Five T3 line components
        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(ModItems.KRF_EXCIMER_LASER.get())).orElse("").equals("light_source"),
            "KRF_EXCIMER_LASER must be light_source");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(ModItems.KRF_EXCIMER_LASER.get())) == 3,
            "KRF_EXCIMER_LASER must be tier 3");

        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(ModItems.DUV_PROJECTION_LENS.get())).orElse("").equals("optics"),
            "DUV_PROJECTION_LENS must be optics");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(ModItems.DUV_PROJECTION_LENS.get())) == 3,
            "DUV_PROJECTION_LENS must be tier 3");

        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(ModItems.PRECISION_STEPPER_STAGE.get())).orElse("").equals("stage"),
            "PRECISION_STEPPER_STAGE must be stage");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(ModItems.PRECISION_STEPPER_STAGE.get())) == 3,
            "PRECISION_STEPPER_STAGE must be tier 3");

        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(ModItems.HEPA_FILTER.get())).orElse("").equals("cleanroom"),
            "HEPA_FILTER must be cleanroom");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(ModItems.HEPA_FILTER.get())) == 3,
            "HEPA_FILTER must be tier 3");

        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(ModItems.LASER_INTERFEROMETER.get())).orElse("").equals("metrology"),
            "LASER_INTERFEROMETER must be metrology");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(ModItems.LASER_INTERFEROMETER.get())) == 3,
            "LASER_INTERFEROMETER must be tier 3");

        // ChipCatalog has all four T3 entries
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.LOGIC, 3).isPresent(), "ChipCatalog must have LOGIC at T3");
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.LOGIC, 3).get() == ModItems.LOGIC_CHIP_T3.get(),
            "ChipCatalog LOGIC T3 must be LOGIC_CHIP_T3");

        helper.assertTrue(ChipCatalog.chipFor(DieTypes.MEMORY, 3).isPresent(), "ChipCatalog must have MEMORY at T3");
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.MEMORY, 3).get() == ModItems.MEMORY_CHIP_T3.get(),
            "ChipCatalog MEMORY T3 must be MEMORY_CHIP_T3");

        helper.assertTrue(ChipCatalog.chipFor(DieTypes.COMPUTE, 3).isPresent(), "ChipCatalog must have COMPUTE at T3");
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.COMPUTE, 3).get() == ModItems.COMPUTE_CHIP_T3.get(),
            "ChipCatalog COMPUTE T3 must be COMPUTE_CHIP_T3");

        helper.assertTrue(ChipCatalog.chipFor(DieTypes.NAND, 3).isPresent(), "ChipCatalog must have NAND at T3");
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.NAND, 3).get() == ModItems.NAND_CHIP_T3.get(),
            "ChipCatalog NAND T3 must be NAND_CHIP_T3");

        // No T4 entries
        for (Tier tier : TierDataLoader.getRegistry().all()) {
            if (tier.index() >= 4) {
                for (String dieType : List.of(DieTypes.LOGIC, DieTypes.MEMORY, DieTypes.COMPUTE, DieTypes.NAND)) {
                    helper.assertTrue(ChipCatalog.chipFor(dieType, tier.index()).isEmpty(),
                        "ChipCatalog must not have " + dieType + " at " + tier.id());
                }
            }
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. T3 recipes validate against actual JSON (no manufacturing)
    // ------------------------------------------------------------------
    private static void t3RecipesValidation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var recipeManager = level.getServer().getRecipeManager();

        // Resist T3 recipe: T2 logic + resist T2 + redstone + glowstone + bottle -> 8 resist T3
        var resistT3Recipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.REDSTONE), new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.GLASS_BOTTLE), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(ModItems.RESIST_T2.get()), new ItemStack(Items.GLOWSTONE_DUST)
            )), level);
        helper.assertTrue(resistT3Recipe.isPresent(), "Resist T3 recipe must exist");
        ItemStack resistT3Output = resistT3Recipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.REDSTONE), new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.GLASS_BOTTLE), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(ModItems.RESIST_T2.get()), new ItemStack(Items.GLOWSTONE_DUST)
            )));
        helper.assertTrue(resistT3Output.is(ModItems.RESIST_T3.get()), "Resist T3 recipe must produce RESIST_T3");
        helper.assertValueEqual(8, resistT3Output.getCount(), "Resist T3 recipe output count must be 8");

        // KRF_EXCIMER_LASER: i_line_lamp + logic_chip_t2 + gold + diamond
        var laserRecipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.I_LINE_LAMP.get()), ItemStack.EMPTY,
                new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GOLD_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.DIAMOND), ItemStack.EMPTY
            )), level);
        helper.assertTrue(laserRecipe.isPresent(), "KRF_EXCIMER_LASER recipe must exist");
        ItemStack laserOutput = laserRecipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.I_LINE_LAMP.get()), ItemStack.EMPTY,
                new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GOLD_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.DIAMOND), ItemStack.EMPTY
            )));
        helper.assertTrue(laserOutput.is(ModItems.KRF_EXCIMER_LASER.get()), "KRF_EXCIMER_LASER recipe must produce KRF_EXCIMER_LASER");
        helper.assertValueEqual(1, laserOutput.getCount(), "KRF_EXCIMER_LASER recipe output count must be 1");

        // DUV_PROJECTION_LENS: reduction_lens + logic_chip_t2 + glass + diamond
        var lensRecipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.REDUCTION_LENS.get()), ItemStack.EMPTY,
                new ItemStack(Items.GLASS), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GLASS),
                ItemStack.EMPTY, new ItemStack(Items.DIAMOND), ItemStack.EMPTY
            )), level);
        helper.assertTrue(lensRecipe.isPresent(), "DUV_PROJECTION_LENS recipe must exist");
        ItemStack lensOutput = lensRecipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.REDUCTION_LENS.get()), ItemStack.EMPTY,
                new ItemStack(Items.GLASS), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GLASS),
                ItemStack.EMPTY, new ItemStack(Items.DIAMOND), ItemStack.EMPTY
            )));
        helper.assertTrue(lensOutput.is(ModItems.DUV_PROJECTION_LENS.get()), "DUV_PROJECTION_LENS recipe must produce DUV_PROJECTION_LENS");
        helper.assertValueEqual(1, lensOutput.getCount(), "DUV_PROJECTION_LENS recipe output count must be 1");

        // PRECISION_STEPPER_STAGE: stepper_stage + logic_chip_t2 + iron + redstone
        var stageRecipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.STEPPER_STAGE.get()), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY
            )), level);
        helper.assertTrue(stageRecipe.isPresent(), "PRECISION_STEPPER_STAGE recipe must exist");
        ItemStack stageOutput = stageRecipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.STEPPER_STAGE.get()), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY
            )));
        helper.assertTrue(stageOutput.is(ModItems.PRECISION_STEPPER_STAGE.get()), "PRECISION_STEPPER_STAGE recipe must produce PRECISION_STEPPER_STAGE");
        helper.assertValueEqual(1, stageOutput.getCount(), "PRECISION_STEPPER_STAGE recipe output count must be 1");

        // HEPA_FILTER: fine_air_filter + logic_chip_t2 + paper + redstone
        var hepaRecipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.FINE_AIR_FILTER.get()), ItemStack.EMPTY,
                new ItemStack(Items.PAPER), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.PAPER),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY
            )), level);
        helper.assertTrue(hepaRecipe.isPresent(), "HEPA_FILTER recipe must exist");
        ItemStack hepaOutput = hepaRecipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.FINE_AIR_FILTER.get()), ItemStack.EMPTY,
                new ItemStack(Items.PAPER), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.PAPER),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY
            )));
        helper.assertTrue(hepaOutput.is(ModItems.HEPA_FILTER.get()), "HEPA_FILTER recipe must produce HEPA_FILTER");
        helper.assertValueEqual(1, hepaOutput.getCount(), "HEPA_FILTER recipe output count must be 1");

        // LASER_INTERFEROMETER: overlay_scope + logic_chip_t2 + gold + quartz
        var interferometerRecipe = recipeManager.getRecipeFor(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.OVERLAY_SCOPE.get()), ItemStack.EMPTY,
                new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GOLD_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.QUARTZ), ItemStack.EMPTY
            )), level);
        helper.assertTrue(interferometerRecipe.isPresent(), "LASER_INTERFEROMETER recipe must exist");
        ItemStack interferometerOutput = interferometerRecipe.get().value().assemble(
            net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(ModItems.OVERLAY_SCOPE.get()), ItemStack.EMPTY,
                new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.LOGIC_CHIP_T2.get()), new ItemStack(Items.GOLD_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.QUARTZ), ItemStack.EMPTY
            )));
        helper.assertTrue(interferometerOutput.is(ModItems.LASER_INTERFEROMETER.get()), "LASER_INTERFEROMETER recipe must produce LASER_INTERFEROMETER");
        helper.assertValueEqual(1, interferometerOutput.getCount(), "LASER_INTERFEROMETER recipe output count must be 1");

        // No chip crafting bypass - T3 chips should not be craftable
        for (String dieType : List.of(DieTypes.LOGIC, DieTypes.MEMORY, DieTypes.COMPUTE, DieTypes.NAND)) {
            Item chip = ChipCatalog.chipFor(dieType, 3).orElse(null);
            helper.assertTrue(chip != null, "Chip must exist for " + dieType + " T3");
            // Verify no shaped recipe produces T3 chips directly
            // This is validated by the obtainability test in PresentationGameTests
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. T3 admission: ordinary substrate refuses T3
    // ------------------------------------------------------------------
    private static void t3AdmissionOrdinaryRefusal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cellPos = new BlockPos(1, 1, 1);

        // Build formed process cell
        buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(cellPos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");

        // Load ordinary lot (grade ORDINARY)
        ItemStack foupStack = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        FoupContents ordinaryLot = FoupItems.trustedContentsOf(foupStack);
        helper.assertTrue(ordinaryLot.grade() == WaferSubstrate.ORDINARY, "lot must be ORDINARY");

        // Add wafers
        for (int i = 0; i < 4; i++) {
            ordinaryLot = ordinaryLot.withWafer(Wafer.unprocessed());
        }
        ItemStack foupWithLot = FoupItems.with(ModItems.FOUP_MK1.get(), ordinaryLot);

        // Load T3 mask (G2 shopGeneration)
        ItemStack maskStack = new ItemStack(ModItems.MASK.get());
        maskStack.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.LOGIC, 3, EUVCRAFTMod.settings().maskUses(), 2)); // G2 mask
        helper.assertTrue(maskStack.get(ModDataComponents.MASK_DATA.get()).shopGeneration() == 2, "mask must be G2");

        // Load T3 components
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupWithLot);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
        Item[] t3Components = {
            ModItems.KRF_EXCIMER_LASER.get(),
            ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(),
            ModItems.HEPA_FILTER.get(),
            ModItems.LASER_INTERFEROMETER.get()
        };
        for (int i = 0; i < 5; i++) {
            cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(t3Components[i]));
        }

        // Energy
        cell.setStoredEnergy(cell.buffer().getCapacityAsInt());

        // Try to start - should refuse due to ORDINARY grade
        cell.serverTick();
        helper.assertTrue(cell.processState() == ProcessState.IDLE, "T3 must refuse ORDINARY substrate");
        helper.assertTrue(cell.lastRefusal() != null, "must have refusal reason");
        // T3 requires PURE substrate (generation 2), ORDINARY is generation 1
        // The refusal should be GENERATION_UNMET for substrate line
        helper.assertTrue(cell.lastRefusal().code() == RefusalReason.Code.GENERATION_UNMET,
            "refusal must be GENERATION_UNMET for substrate");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. T3 admission: pure substrate but G1 mask refuses
    // ------------------------------------------------------------------
    private static void t3AdmissionG1MaskRefusal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cellPos = new BlockPos(1, 1, 1);

        buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(cellPos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");

        // Load PURE lot (grade PURE)
        ItemStack foupStack = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        FoupContents pureLot = FoupItems.trustedContentsOf(foupStack);
        FoupContents pureLotWithWafers = FoupContents.of(UUID.randomUUID(), FoupItems.configuredCapacity(),
            java.util.List.of(Wafer.unprocessed(), Wafer.unprocessed(), Wafer.unprocessed(), Wafer.unprocessed()),
            WaferSubstrate.PURE);
        ItemStack foupWithLot = FoupItems.with(ModItems.FOUP_MK1.get(), pureLotWithWafers);

        // Load T3 mask BUT with shopGeneration = 1 (G1)
        ItemStack maskStack = new ItemStack(ModItems.MASK.get());
        maskStack.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.LOGIC, 3, EUVCRAFTMod.settings().maskUses(), 1)); // G1 mask
        helper.assertTrue(maskStack.get(ModDataComponents.MASK_DATA.get()).shopGeneration() == 1, "mask must be G1");

        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupWithLot);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
        Item[] t3Components = {
            ModItems.KRF_EXCIMER_LASER.get(),
            ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(),
            ModItems.HEPA_FILTER.get(),
            ModItems.LASER_INTERFEROMETER.get()
        };
        for (int i = 0; i < 5; i++) {
            cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(t3Components[i]));
        }
        cell.setStoredEnergy(cell.buffer().getCapacityAsInt());

        cell.serverTick();
        helper.assertTrue(cell.processState() == ProcessState.IDLE, "T3 must refuse G1 mask even with PURE substrate");
        helper.assertTrue(cell.lastRefusal() != null, "must have refusal reason");
        // T3 requires mask shop generation 2
        helper.assertTrue(cell.lastRefusal().code() == RefusalReason.Code.GENERATION_UNMET,
            "refusal must be GENERATION_UNMET for mask_shop");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. T3 admission positive: PURE substrate + G2 mask runs all 4 designs
    // ------------------------------------------------------------------
    private static void t3AdmissionPureG2Positive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cellPos = new BlockPos(1, 1, 1);
        BlockPos packagePos = new BlockPos(15, 1, 1);

        buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(cellPos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");

        // Snapshot config for override
        int originalWafersPerBoule = FabConfig.WAFERS_PER_BOULE.get();
        int originalResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        List<String> originalLayerTicksOverrides = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());

        try {
            FabConfig.WAFERS_PER_BOULE.set(4);
            FabConfig.RESIST_PER_LAYER.set(2);
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T3=3"));
            EUVCRAFTMod.config().onConfigReload();

            // Test all four T3 designs
            for (String dieType : List.of(DieTypes.LOGIC, DieTypes.MEMORY, DieTypes.COMPUTE, DieTypes.NAND)) {
                // Fresh cell for each design
                clearCell(helper, level, cellPos);
                buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
                cell = (ProcessCellControllerBlockEntity)
                    level.getBlockEntity(helper.absolutePos(cellPos));
                cell.revalidate();
                helper.assertTrue(cell.isFormed(), "cell must be formed");

                // PURE lot with wafers
                FoupContents pureLot = FoupContents.of(UUID.randomUUID(), FoupItems.configuredCapacity(),
                    java.util.List.of(Wafer.unprocessed(), Wafer.unprocessed(), Wafer.unprocessed(), Wafer.unprocessed()),
                    WaferSubstrate.PURE);
                ItemStack foupWithLot = FoupItems.with(ModItems.FOUP_MK1.get(), pureLot);

                // T3 mask with G2 shopGeneration
                ItemStack maskStack = new ItemStack(ModItems.MASK.get());
                maskStack.set(ModDataComponents.MASK_DATA.get(),
                    new MaskData(dieType, 3, EUVCRAFTMod.settings().maskUses(), 2));

                // T3 components
                cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupWithLot);
                cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
                cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
                Item[] t3Components = {
                    ModItems.KRF_EXCIMER_LASER.get(),
                    ModItems.DUV_PROJECTION_LENS.get(),
                    ModItems.PRECISION_STEPPER_STAGE.get(),
                    ModItems.HEPA_FILTER.get(),
                    ModItems.LASER_INTERFEROMETER.get()
                };
                for (int i = 0; i < 5; i++) {
                    cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(t3Components[i]));
                }
                cell.setStoredEnergy(cell.buffer().getCapacityAsInt());

                // Run process cell to completion
                int maxTicks = 200;
                for (int tick = 0; tick < maxTicks; tick++) {
                    if (cell.processState() == ProcessState.FINISHED) break;
                    cell.serverTick();
                }
                helper.assertValueEqual(ProcessState.FINISHED, cell.processState(),
                    "T3 " + dieType + " must reach FINISHED state");

                // Verify lot layer = 24 (T3 layers)
                ItemStack processedFoup = cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP);
                FoupContents processedLot = FoupItems.trustedContentsOf(processedFoup);
                helper.assertValueEqual(24, processedLot.lotLayer(), "T3 " + dieType + " must complete 24 layers");
                helper.assertTrue(processedLot.grade() == WaferSubstrate.PURE, "grade must remain PURE");
                helper.assertTrue(processedLot.run() != null, "run spec must be frozen");
                helper.assertValueEqual(3, processedLot.run().tierIndex(), "run spec tier must be 3");
                helper.assertTrue(processedLot.run().dieType().equals(dieType), "run spec die type must match");

                // Verify compute T3 uses core count 1
                if (DieTypes.COMPUTE.equals(dieType)) {
                    helper.assertTrue(processedLot.run().hasCores(), "COMPUTE T3 must have core count");
                    helper.assertValueEqual(1, processedLot.run().cores(), "COMPUTE T3 core count must be 1");
                } else {
                    helper.assertTrue(!processedLot.run().hasCores(), dieType + " T3 must not have core count");
                }

                // Test & Package
                helper.setBlock(packagePos, PackagingMachines.TEST_PACKAGE.get());
                TestPackageBlockEntity packaging = (TestPackageBlockEntity)
                    level.getBlockEntity(helper.absolutePos(packagePos));
                packaging.serverTick();

                MachineDefinition def = packaging.definition().orElseThrow();
                int ticksPerWafer = def.param(TestPackageBlockEntity.TICKS_PER_WAFER_PARAM).orElse(0);
                int chipsPerWafer = EUVCRAFTMod.settings().chipsPerWafer();
                int packagesPerWafer = def.param(TestPackageBlockEntity.PACKAGE_PER_WAFER_PARAM).orElse(1);

                int waferCount = processedLot.size();
                int passing = FabYieldService.countPassing(level, processedLot,
                    TierDataLoader.getRegistry().byIndex(3).orElseThrow(), dieType,
                    DieTypes.COMPUTE.equals(dieType) ? 1 : null);
                int expectedChips = passing * chipsPerWafer;
                int expectedScrap = (waferCount - passing) * chipsPerWafer;
                int expectedPackages = passing * packagesPerWafer;

                Item expectedChip = ChipCatalog.chipFor(dieType, 3).orElseThrow();
                packaging.setSlot(TestPackageBlockEntity.PACKAGE_SLOT, new ItemStack(ModItems.CHIP_PACKAGE.get(), 64));
                packaging.setSlot(TestPackageBlockEntity.FOUP_SLOT, processedFoup.copy());

                packaging.setStoredEnergy(packaging.buffer().getCapacityAsInt());

                // Run packaging
                int totalTicks = waferCount * ticksPerWafer;
                for (int tick = 0; tick < totalTicks + 20; tick++) {
                    if (!packaging.isQueueActive() && packaging.wafersDone() == waferCount) break;
                    packaging.serverTick();
                }
                helper.assertValueEqual(waferCount, packaging.wafersDone(), "all wafers must be processed");

                // Verify outputs
                int actualChips = 0, actualScrap = 0;
                for (int i = 0; i < TestPackageBlockEntity.CHIP_SLOT_COUNT; i++) {
                    ItemStack stack = packaging.removeItemNoUpdate(TestPackageBlockEntity.CHIP_SLOT_FIRST + i);
                    if (stack.getItem() == expectedChip) actualChips += stack.getCount();
                }
                for (int i = 0; i < TestPackageBlockEntity.SCRAP_SLOT_COUNT; i++) {
                    ItemStack stack = packaging.removeItemNoUpdate(TestPackageBlockEntity.SCRAP_SLOT_FIRST + i);
                    if (stack.getItem() == ModItems.CHIP_SCRAP.get()) actualScrap += stack.getCount();
                }
                helper.assertValueEqual(expectedChips, actualChips,
                    dieType + " T3 exact chip count mismatch");
                helper.assertValueEqual(expectedScrap, actualScrap,
                    dieType + " T3 exact scrap count mismatch");

                clearFixtureBlock(helper, level, packagePos);
            }

        } finally {
            FabConfig.WAFERS_PER_BOULE.set(originalWafersPerBoule);
            FabConfig.RESIST_PER_LAYER.set(originalResistPerLayer);
            FabConfig.LAYER_TICKS_OVERRIDES.set(originalLayerTicksOverrides);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. T3 frozen contract save/load with matrix reload
    // ------------------------------------------------------------------
    private static void t3FrozenContractSaveLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cellPos = new BlockPos(1, 1, 1);

        buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(cellPos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");

        // PURE lot with G2 mask
        FoupContents pureLot = FoupContents.of(UUID.randomUUID(), FoupItems.configuredCapacity(),
            java.util.List.of(Wafer.unprocessed(), Wafer.unprocessed()), WaferSubstrate.PURE);
        ItemStack foupWithLot = FoupItems.with(ModItems.FOUP_MK1.get(), pureLot);

        ItemStack maskStack = new ItemStack(ModItems.MASK.get());
        maskStack.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.LOGIC, 3, EUVCRAFTMod.settings().maskUses(), 2));

        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupWithLot);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
        Item[] t3Components = {
            ModItems.KRF_EXCIMER_LASER.get(),
            ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(),
            ModItems.HEPA_FILTER.get(),
            ModItems.LASER_INTERFEROMETER.get()
        };
        for (int i = 0; i < 5; i++) {
            cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(t3Components[i]));
        }
        cell.setStoredEnergy(cell.buffer().getCapacityAsInt());

        // Start run
        int originalLayerTicks = FabConfig.LAYER_TICKS_OVERRIDES.get().stream()
            .filter(s -> s.startsWith("T3=")).findFirst().map(s -> Integer.parseInt(s.split("=")[1])).orElse(100);
        List<String> originalOverrides = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T3=3"));
        EUVCRAFTMod.config().onConfigReload();

        try {
            // Run a few layers
            for (int tick = 0; tick < 20; tick++) {
                if (cell.processState() == ProcessState.RUNNING && cell.currentLayer() > 1) break;
                cell.serverTick();
            }
            helper.assertTrue(cell.processState() == ProcessState.RUNNING, "must be running");
            int layerAtSave = cell.currentLayer();
            int progressAtSave = cell.progressTicks();

            // Save contract and snapshot (simulate chunk save)
            ProcessCellRunPersistence.Contract contract = privateField(cell, "contract", ProcessCellRunPersistence.Contract.class);
            ProcessRun.Snapshot snapshot = privateField(cell, "run", ProcessRun.class).snapshot();
            helper.assertTrue(contract != null && snapshot != null, "contract and snapshot must exist");
            helper.assertTrue(contract.grade() == WaferSubstrate.PURE, "contract must carry PURE grade");

            // Simulate config reload with different layerTicks
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T3=5"));
            EUVCRAFTMod.config().onConfigReload();

            // Restore from frozen contract
            ProcessCellRunPersistence.Saved saved = new ProcessCellRunPersistence.Saved(contract, snapshot);
            ProcessRun restoredRun = saved.restore(FoupItems.trustedContentsOf(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP)));

            // Verify restored run uses FROZEN layerTicks (3), not live config (5)
            helper.assertValueEqual(3, restoredRun.request().tier().layerTicks(restoredRun.request().settings()),
                "restored run must use frozen layerTicks (3), not live config (5)");

            // Verify grade is preserved
            helper.assertTrue(restoredRun.lot().grade() == WaferSubstrate.PURE, "restored lot must have PURE grade");

            // Verify installed generations are pinned from frozen contract
            Map<String, Integer> installed = restoredRun.request().installedGenerations();
            helper.assertValueEqual(2, installed.get(SupportLines.SUBSTRATE), "restored must pin substrate=2");
            helper.assertValueEqual(2, installed.get(SupportLines.MASK_SHOP), "restored must pin mask_shop=2");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(originalOverrides);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 7. Matrix reload pins admission (active config/data reload)
    // ------------------------------------------------------------------
    private static void t3MatrixReloadPins(GameTestHelper helper) {
        // This test validates that changing the support line matrix at runtime
        // does NOT affect already-admitted T3 runs (they use frozen requiredGenerations)
        ServerLevel level = helper.getLevel();
        BlockPos cellPos = new BlockPos(1, 1, 1);

        buildFormedProcessCell(helper, level, cellPos, Direction.NORTH);
        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(cellPos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");

        // PURE lot with G2 mask - start T3 run
        FoupContents pureLot = FoupContents.of(UUID.randomUUID(), FoupItems.configuredCapacity(),
            java.util.List.of(Wafer.unprocessed(), Wafer.unprocessed()), WaferSubstrate.PURE);
        ItemStack foupWithLot = FoupItems.with(ModItems.FOUP_MK1.get(), pureLot);

        ItemStack maskStack = new ItemStack(ModItems.MASK.get());
        maskStack.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.LOGIC, 3, EUVCRAFTMod.settings().maskUses(), 2));

        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupWithLot);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
        Item[] t3Components = {
            ModItems.KRF_EXCIMER_LASER.get(),
            ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(),
            ModItems.HEPA_FILTER.get(),
            ModItems.LASER_INTERFEROMETER.get()
        };
        for (int i = 0; i < 5; i++) {
            cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(t3Components[i]));
        }
        cell.setStoredEnergy(cell.buffer().getCapacityAsInt());


        // Start and run a few layers
        for (int tick = 0; tick < 20; tick++) {
            if (cell.processState() == ProcessState.RUNNING && cell.currentLayer() > 1) break;
            cell.serverTick();
        }
        helper.assertTrue(cell.processState() == ProcessState.RUNNING, "must be running");

        // Save the frozen requiredGenerations from contract
        Map<String, List<Integer>> frozenRequired = new HashMap<>(privateField(cell, "contract", ProcessCellRunPersistence.Contract.class).requiredGenerations());

        var contract = privateField(cell, "contract", ProcessCellRunPersistence.Contract.class);
        List<Integer> stronger = new ArrayList<>(frozenRequired.get(SupportLines.SUBSTRATE));
        for (int index = 2; index < stronger.size(); index++) stronger.set(index, Math.max(3, stronger.get(index)));
        Map<String, List<Integer>> changed = new HashMap<>(frozenRequired);
        changed.put(SupportLines.SUBSTRATE, List.copyOf(stronger));
        GenerationMatrix changedMatrix = GenerationMatrix.build(TierDataLoader.getRegistry(), changed, "T3 reload fixture");
        try {
            var field = SupportLineDataLoader.class.getDeclaredField("currentMatrix");
            field.setAccessible(true);
            Object previous = field.get(null);
            try {
                field.set(null, changedMatrix);
                var lot = FoupItems.trustedContentsOf(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP));
                var request = contract.request(lot);
                helper.assertFalse(SupportLineDataLoader.getMatrix().unmetLines(request.installedGenerations(), request.tier()).isEmpty(),
                    "new live matrix would refuse this lot");
                helper.assertTrue(request.generationMatrix().unmetLines(request.installedGenerations(), request.tier()).isEmpty(),
                    "frozen contract retains accepted requirements");
            } finally {
                field.set(null, previous);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("T3 matrix reload fixture", failure);
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 8. Forged grade lot fails closed
    // ------------------------------------------------------------------
    private static void t3ForgedGradeLotFails(GameTestHelper helper) {
        var cell = admittedCell(helper, 3, WaferSubstrate.PURE, 2);
        var original = FoupItems.trustedContentsOf(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP));
        long energy = cell.buffer().getAmountAsLong();
        int resist = cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount();
        ItemStack mask = cell.slot(ProcessCellControllerBlockEntity.SLOT_MASK).copy();
        var forged = new FoupContents(original.lotId(), original.capacity(), original.wafers(), original.run(), WaferSubstrate.ORDINARY);
        cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).set(ModDataComponents.FOUP_CONTENTS.get(), forged);
        cell.serverTick();
        helper.assertTrue(cell.aborted(), "active lot grade tamper latches abort");
        helper.assertValueEqual(energy, cell.buffer().getAmountAsLong(), "tamper tick consumes no additional FE");
        helper.assertValueEqual(resist, cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount(), "tamper tick consumes no resist");
        helper.assertTrue(ItemStack.isSameItemSameComponents(mask, cell.slot(ProcessCellControllerBlockEntity.SLOT_MASK)), "tamper tick consumes no mask use");
        helper.succeed();
    }

    private static Map<String, List<Integer>> requiredGenerationsMap() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put(SupportLines.SUBSTRATE, List.of(1, 1, 2, 2, 2, 3, 3, 3));
        map.put(SupportLines.MASK_SHOP, List.of(1, 1, 2, 2, 2, 2, 3, 3));
        map.put(SupportLines.DEPOSITION_ETCH, List.of(1, 1, 1, 2, 2, 2, 3, 3));
        map.put(SupportLines.UTILITIES, List.of(1, 1, 1, 1, 2, 2, 3, 3));
        map.put(SupportLines.TEST_PACKAGING, List.of(1, 1, 1, 2, 2, 2, 2, 3));
        return map;
    }

    // ------------------------------------------------------------------
    // 9. Malformed T3 contract fails closed
    // ------------------------------------------------------------------
    private static void t3MalformedContractFails(GameTestHelper helper) {
        var cell = admittedCell(helper, 3, WaferSubstrate.PURE, 2);
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, helper.getLevel().registryAccess());
        var saved = new ProcessCellRunPersistence.Saved(privateField(cell, "contract", ProcessCellRunPersistence.Contract.class),
            privateField(cell, "run", ProcessRun.class).snapshot());
        var encoded = (net.minecraft.nbt.CompoundTag) ProcessCellRunPersistence.CODEC.encodeStart(ops, saved).result().orElseThrow();
        var decoded = ProcessCellRunPersistence.CODEC.parse(ops, encoded).result().orElseThrow();
        helper.assertTrue(decoded.contract().grade() == WaferSubstrate.PURE, "actual saved T3 codec preserves pure substrate");
        var bad = encoded.copy();
        bad.getCompound("contract").orElseThrow().putString("grade", "unknown");
        helper.assertTrue(ProcessCellRunPersistence.CODEC.parse(ops, bad).result().isEmpty(), "explicit unknown grade fails closed");
        bad = encoded.copy();
        bad.getCompound("contract").orElseThrow().remove("grade");
        helper.assertTrue(ProcessCellRunPersistence.CODEC.parse(ops, bad).result().isEmpty(), "missing T3 grade cannot downgrade to ordinary");
        bad = encoded.copy();
        bad.getCompound("contract").orElseThrow().getCompound("mask").orElseThrow()
            .getCompound("components").orElseThrow().getCompound("lithmod:mask_data").orElseThrow().putInt("shop_generation", 1);
        helper.assertTrue(ProcessCellRunPersistence.CODEC.parse(ops, bad).result().isEmpty(), "T3 saved G1 mask fails closed");
        clearCell(helper, helper.getLevel(), new BlockPos(1, 1, 1));
        var legacy = admittedCell(helper, 1, WaferSubstrate.ORDINARY, 1);
        var legacySaved = new ProcessCellRunPersistence.Saved(privateField(legacy, "contract", ProcessCellRunPersistence.Contract.class),
            privateField(legacy, "run", ProcessRun.class).snapshot());
        var legacyNbt = (net.minecraft.nbt.CompoundTag) ProcessCellRunPersistence.CODEC.encodeStart(ops, legacySaved).result().orElseThrow();
        legacyNbt.getCompound("contract").orElseThrow().remove("grade");
        helper.assertTrue(ProcessCellRunPersistence.CODEC.parse(ops, legacyNbt).result().orElseThrow().contract().grade() == WaferSubstrate.ORDINARY,
            "legacy T1 save without grade retains ordinary substrate");
        helper.succeed();
    }

    private static ProcessCellControllerBlockEntity admittedCell(GameTestHelper helper, int tier, WaferSubstrate grade, int maskGeneration) {
        BlockPos pos = new BlockPos(1, 1, 1);
        buildFormedProcessCell(helper, helper.getLevel(), pos, Direction.NORTH);
        var cell = (ProcessCellControllerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "fixture cell forms");
        var lot = FoupContents.of(UUID.randomUUID(), FoupItems.configuredCapacity(), List.of(Wafer.unprocessed()), grade);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, FoupItems.with(ModItems.FOUP_MK1.get(), lot));
        ItemStack mask = new ItemStack(ModItems.MASK.get());
        mask.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, tier, 4, maskGeneration));
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, mask);
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(ModItems.RESIST_T3.get(), 64));
        Item[] components = {ModItems.KRF_EXCIMER_LASER.get(), ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(), ModItems.HEPA_FILTER.get(), ModItems.LASER_INTERFEROMETER.get()};
        for (int index = 0; index < components.length; index++) cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + index, new ItemStack(components[index]));
        cell.setStoredEnergy(cell.buffer().getCapacityAsInt());
        cell.serverTick();
        helper.assertTrue(cell.processState() == ProcessState.RUNNING, "fixture starts a real admitted run");
        return cell;
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private static void buildFormedProcessCell(GameTestHelper helper, ServerLevel level,
            BlockPos controllerPos, Direction facing) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos p = controllerPos.offset(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                            .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                    } else if (x == 0 && y == 1 && z == 0) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
                    } else if (z == 2 && (x == 0 && y == 0 || x == 1 && y == 1)) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                }
            }
        }
    }

    private static void clearCell(GameTestHelper helper, ServerLevel level, BlockPos controllerPos) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos p = controllerPos.offset(x, y, z);
                    clearFixtureBlock(helper, level, p);
                }
            }
        }
    }

    private static void clearFixtureBlock(GameTestHelper helper, ServerLevel level, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        var blockEntity = level.getBlockEntity(absolute);
        if (blockEntity instanceof net.minecraft.world.Container inventory) {
            inventory.clearContent();
        }
        helper.setBlock(relative, net.minecraft.world.level.block.Blocks.AIR);
    }
    private static <T> T privateField(Object owner, String name, Class<T> type) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Frozen process fixture field " + name, failure);
        }
    }

}