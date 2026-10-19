package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * End-to-end M1 gate tests: each case drives a complete real-machine chain on a real
 * ServerLevel from raw inputs to final packaged output.
 *
 * <p>No simulation, no handcrafted intermediate products, no FabYieldService.applyLayerStep
 * calls by the test. Every machine is ticked through its actual serverTick with real FE.
 * All configuration borrowed only inside synchronous try/finally with exact restoration.
 * h.succeed() called AFTER cleanup.
 */
public final class M1GateGameTests {

    private static final String ENVIRONMENT_ID = "m1_gate";

    /** Logic T1 complete chain test. */
    private static final String LOGIC_T1_CHAIN = "m1_gate_logic_t1_chain";
    /** Memory T2 complete chain test. */
    private static final String MEMORY_T2_CHAIN = "m1_gate_memory_t2_chain";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    /** Base position for the first machine in the chain. */
    private static final BlockPos BASE = new BlockPos(1, 1, 1);
    /** Offset for each subsequent machine to avoid overlap. */
    private static final int MACHINE_SPACING = 10;

    static {
        FUNCTIONS.register(LOGIC_T1_CHAIN, () -> M1GateGameTests::logicT1Chain);
        FUNCTIONS.register(MEMORY_T2_CHAIN, () -> M1GateGameTests::memoryT2Chain);
    }

    private M1GateGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) M1GateGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            1200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, LOGIC_T1_CHAIN);
        register(event, data, MEMORY_T2_CHAIN);
        EUVCRAFTMod.LOGGER.info("Registered EUVCRAFT M1 gate game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. Logic T1 complete chain
    // ------------------------------------------------------------------

    private static void logicT1Chain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        runCompleteChain(helper, level, DieTypes.LOGIC, 1, Items.COPPER_INGOT);
    }

    // ------------------------------------------------------------------
    // 2. Memory T2 complete chain
    // ------------------------------------------------------------------

    private static void memoryT2Chain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        runCompleteChain(helper, level, DieTypes.MEMORY, 2, Items.REDSTONE);
    }

    // ------------------------------------------------------------------
    // Shared chain driver
    // ------------------------------------------------------------------

    /**
     * Drives the complete chain for one die type and tier.
     *
     * @param helper the game test helper
     * @param level the server level
     * @param dieType the die type name (LOGIC or MEMORY)
     * @param tierIndex the node tier index (1 or 2)
     * @param designIngredient the vanilla ingredient that distinguishes the design
     */
    private static void runCompleteChain(GameTestHelper helper, ServerLevel level,
            String dieType, int tierIndex, Item designIngredient) {

        // ------------------------------------------------------------------
        // Snapshot and temporarily override config values
        // ------------------------------------------------------------------
        int originalWafersPerBoule = FabConfig.WAFERS_PER_BOULE.get();
        int originalResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        List<String> originalLayerTicksOverrides = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());

        try {
            // Bound wafer count so output banks don't hang
            FabConfig.WAFERS_PER_BOULE.set(4);
            FabConfig.RESIST_PER_LAYER.set(2);
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            EUVCRAFTMod.config().onConfigReload();

            // Machine positions (spaced apart)
            BlockPos pullerPos = BASE;
            BlockPos slicerPos = BASE.offset(MACHINE_SPACING, 0, 0);
            BlockPos generatorPos = BASE.offset(MACHINE_SPACING * 2, 0, 0);
            BlockPos cellControllerPos = BASE.offset(MACHINE_SPACING * 3, 0, 0);
            BlockPos packagePos = BASE.offset(MACHINE_SPACING * 4, 0, 0);

            // ============================================================
            // STAGE 1: Quartz -> Silicon (4x) via furnace/blast recipes
            // ============================================================
            List<ItemStack> silicon = obtainSiliconFromQuartz(helper, level, 4);
            helper.assertValueEqual(4, silicon.size(), "must obtain exactly 4 silicon from quartz");

            // ============================================================
            // STAGE 2: Crystal Puller - 4 silicon -> 1 boule
            // ============================================================
            ItemStack boule = runCrystalPuller(helper, level, pullerPos, silicon);
            helper.assertTrue(boule.getItem() == ModItems.SILICON_BOULE.get() && boule.getCount() == 1,
                "crystal puller must produce one silicon boule");

            // ============================================================
            // STAGE 3: Wafer Slicer - boule + empty FOUP -> FOUP with wafers
            // ============================================================
            ItemStack foupStack = runWaferSlicer(helper, level, slicerPos, boule);
            FoupContents slicedLot = FoupItems.trustedContentsOf(foupStack);
            helper.assertTrue(slicedLot.size() > 0, "slicer must produce wafers");
            helper.assertTrue(slicedLot.run() == null, "fresh lot must have no run spec");
            UUID slicedLotId = slicedLot.lotId();
            int waferCount = slicedLot.size();
            int foupCapacity = slicedLot.capacity();
            helper.assertValueEqual(4, waferCount, "expecting 4 wafers from bounded config");
            helper.assertValueEqual(foupCapacity, slicedLot.capacity(), "FOUP capacity must match config");

            // ============================================================
            // STAGE 4: Pattern Generator - blank_mask + design -> patterned mask
            // ============================================================
            ItemStack mask = runPatternGenerator(helper, level, generatorPos, dieType, tierIndex, designIngredient);
            helper.assertTrue(mask.getItem() == ModItems.MASK.get(), "generator must produce a mask");
            MaskData maskData = mask.get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(maskData != null, "mask must carry design data");
            helper.assertValueEqual(dieType, maskData.dieType(), "mask die type mismatch");
            helper.assertValueEqual(tierIndex, maskData.tierIndex(), "mask tier mismatch");
            int configuredMaskUses = EUVCRAFTMod.settings().maskUses();
            helper.assertValueEqual(configuredMaskUses, maskData.usesRemaining(),
                "mask must carry configured exposure count");
            int maskUsesBefore = maskData.usesRemaining();

            // ============================================================
            // STAGE 5: Process Cell - build structure, load FOUP/mask/resist/components, run
            // ============================================================
            ItemStack processedFoupStack = runProcessCell(helper, level, cellControllerPos,
                foupStack, mask, dieType, tierIndex);
            FoupContents processedLot = FoupItems.trustedContentsOf(processedFoupStack);
            helper.assertTrue(processedLot.lotId().equals(slicedLotId),
                "lot id must be preserved through process cell");
            helper.assertValueEqual(waferCount, processedLot.size(), "wafer count preserved");
            helper.assertValueEqual(foupCapacity, processedLot.capacity(), "capacity preserved");
            int expectedLayers = tierIndex == 1 ? 1 : 12;
            helper.assertValueEqual(expectedLayers, processedLot.lotLayer(),
                "physical applied layers must match tier");
            helper.assertTrue(processedLot.run() != null, "run spec must be frozen");
            helper.assertValueEqual(dieType, processedLot.run().dieType(), "frozen die type");
            helper.assertValueEqual(tierIndex, processedLot.run().tierIndex(), "frozen tier");

            // Mask must lose exactly ONE use for entire lot
            int maskUsesAfter = 0;
            if (!mask.isEmpty()) {
                MaskData md = mask.get(ModDataComponents.MASK_DATA.get());
                if (md != null) maskUsesAfter = md.usesRemaining();
            }
            int expectedMaskUses = configuredMaskUses - 1;
            if (expectedMaskUses <= 0) {
                helper.assertTrue(mask.isEmpty(), "exhausted mask must be removed from slot");
            } else {
                helper.assertValueEqual(expectedMaskUses, maskUsesAfter, "mask must lose exactly one use for entire lot");
            }

            // ============================================================
            // STAGE 6: Test & Package - judge, package, verify exact outputs
            // ============================================================
            runTestAndPackage(helper, level, packagePos, processedFoupStack, dieType, tierIndex,
                waferCount, maskUsesBefore);

        } finally {
            // Exact restoration
            FabConfig.WAFERS_PER_BOULE.set(originalWafersPerBoule);
            FabConfig.RESIST_PER_LAYER.set(originalResistPerLayer);
            FabConfig.LAYER_TICKS_OVERRIDES.set(originalLayerTicksOverrides);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // STAGE 1: Obtain silicon from quartz via recipe lookup
    // ------------------------------------------------------------------

    private static List<ItemStack> obtainSiliconFromQuartz(GameTestHelper helper, ServerLevel level, int count) {
        RecipeManager recipeManager = level.getServer().getRecipeManager();
        List<ItemStack> silicon = new ArrayList<>();

        // Lookup smelting recipe for quartz -> silicon
        RecipeHolder<SmeltingRecipe> smeltingHolder = recipeManager.getRecipeFor(
            RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(Items.QUARTZ)), level).orElse(null);
        helper.assertTrue(smeltingHolder != null, "quartz smelting recipe must exist");
        if (smeltingHolder == null) return silicon;

        // Lookup blasting recipe for quartz -> silicon
        RecipeHolder<BlastingRecipe> blastingHolder = recipeManager.getRecipeFor(
            RecipeType.BLASTING, new SingleRecipeInput(new ItemStack(Items.QUARTZ)), level).orElse(null);
        helper.assertTrue(blastingHolder != null, "quartz blasting recipe must exist");
        if (blastingHolder == null) return silicon;

        // Verify holder IDs are lithmod recipes
        helper.assertTrue(smeltingHolder.id().identifier().getNamespace().equals(EUVCRAFTMod.MODID),
            "silicon smelting recipe must be from lithmod");
        helper.assertTrue(blastingHolder.id().identifier().getNamespace().equals(EUVCRAFTMod.MODID),
            "silicon blasting recipe must be from lithmod");

        // Use smelting recipe to produce silicon - consume one quartz per result
        SmeltingRecipe smeltingRecipe = smeltingHolder.value();
        for (int i = 0; i < count; i++) {
            ItemStack input = new ItemStack(Items.QUARTZ);
            SingleRecipeInput recipeInput = new SingleRecipeInput(input);
            helper.assertTrue(smeltingRecipe.matches(recipeInput, level), "quartz must match smelting recipe");
            ItemStack result = smeltingRecipe.assemble(recipeInput);
            helper.assertTrue(result.getItem() == ModItems.SILICON.get(), "recipe must produce silicon");
            helper.assertValueEqual(1, result.getCount(), "each quartz yields one silicon");
            silicon.add(result);
        }

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Obtained {} silicon from quartz via recipe lookup", silicon.size());
        return silicon;
    }

    // ------------------------------------------------------------------
    // STAGE 2: Crystal Puller - 4 silicon -> 1 boule
    // ------------------------------------------------------------------

    private static ItemStack runCrystalPuller(GameTestHelper helper, ServerLevel level, BlockPos pos,
            List<ItemStack> siliconInput) {

        helper.setBlock(pos, SiliconMachines.CRYSTAL_PULLER.get());
        CrystalPullerBlockEntity puller = (CrystalPullerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(puller != null, "must be CrystalPullerBlockEntity");
        if (puller == null) return ItemStack.EMPTY;

        puller.refreshDefinition();
        MachineRecipe recipe = pullerRecipe(puller);
        helper.assertTrue(recipe != null, "crystal_puller recipe must be loaded");
        if (recipe == null) return ItemStack.EMPTY;

        // Load 4 silicon into input slot (slot 0)
        ItemStack siliconStack = new ItemStack(ModItems.SILICON.get(), 4);
        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, siliconStack);

        // Energy - refill buffer before run
        int perTick = puller.runner().energyPerTick();
        helper.assertTrue(perTick > 0, "puller energy per tick must be positive");
        int totalEnergy = perTick * (recipe.ticks() + 5);
        puller.setStoredEnergy(totalEnergy);

        // Run to completion
        for (int tick = 0; tick < recipe.ticks() + 5; tick++) {
            if (!puller.outputSlot().isEmpty()) break;
            puller.serverTick();
        }

        // Extract output via removeItemNoUpdate
        ItemStack output = puller.removeItemNoUpdate(CrystalPullerBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(output.isEmpty(), "puller must produce output");
        helper.assertTrue(output.getItem() == ModItems.SILICON_BOULE.get(), "output must be boule");
        helper.assertValueEqual(1, output.getCount(), "one boule produced");

        // Verify all 4 silicon consumed
        ItemStack leftover = puller.slot(CrystalPullerBlockEntity.INPUT_SLOT);
        helper.assertTrue(leftover.isEmpty(), "all 4 silicon must be consumed");

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Crystal puller produced 1 boule from 4 silicon in {} ticks", recipe.ticks());
        return output;
    }

    private static MachineRecipe pullerRecipe(CrystalPullerBlockEntity puller) {
        return puller.recipes().stream()
            .filter(r -> r.spec().result().itemId().contains("silicon_boule"))
            .findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    // STAGE 3: Wafer Slicer - boule + empty FOUP -> FOUP with wafers
    // ------------------------------------------------------------------

    private static ItemStack runWaferSlicer(GameTestHelper helper, ServerLevel level, BlockPos pos,
            ItemStack boule) {

        helper.setBlock(pos, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity slicer = (WaferSlicerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(slicer != null, "must have WaferSlicerBlockEntity");
        if (slicer == null) return ItemStack.EMPTY;

        slicer.refreshDefinition();
        helper.assertTrue(slicer.definition().isPresent(), "slicer must have definition");
        int sliceTicks = slicer.sliceTicks();
        helper.assertTrue(sliceTicks > 0, "slice_ticks must be positive");

        // Load boule (slot 0) and empty FOUP (slot 1)
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, boule.copy());
        ItemStack emptyFoup = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, emptyFoup);

        // Energy - refill buffer before run
        int perTick = slicer.energyPerTick();
        helper.assertTrue(perTick > 0, "slicer energy per tick must be positive");
        int totalEnergy = perTick * (sliceTicks + 5);
        slicer.setStoredEnergy(totalEnergy);

        // Run to completion
        for (int tick = 0; tick < sliceTicks + 5; tick++) {
            if (!slicer.outputSlot().isEmpty()) break;
            slicer.serverTick();
        }

        // Extract output via removeItemNoUpdate (slot 2)
        ItemStack output = slicer.removeItemNoUpdate(WaferSlicerBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(output.isEmpty(), "slicer must produce FOUP");
        helper.assertTrue(output.getItem() == ModItems.FOUP_MK1.get(), "output must be FOUP_MK1");

        FoupContents lot = FoupItems.trustedContentsOf(output);
        helper.assertTrue(lot != null && lot.size() > 0, "lot must have wafers");

        // Inputs consumed exactly when product written
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(), "input FOUP consumed");
        ItemStack bouleRemainder = slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT);
        helper.assertValueEqual(1, bouleRemainder.getCount(), "exactly one boule consumed");

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Wafer slicer produced lot with {} wafers, capacity {}", lot.size(), lot.capacity());
        return output;
    }

    // ------------------------------------------------------------------
    // STAGE 4: Pattern Generator - blank_mask + design -> patterned mask
    // Uses ACTUAL LOADED JSON recipes (as shown in PresentationGameTests.patternProduction)
    // ------------------------------------------------------------------

    private static ItemStack runPatternGenerator(GameTestHelper helper, ServerLevel level, BlockPos pos,
            String dieType, int tierIndex, Item designIngredient) {

        helper.setBlock(pos, MaskMachines.PATTERN_GENERATOR.get());
        PatternGeneratorBlockEntity generator = (PatternGeneratorBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(generator != null, "must have PatternGeneratorBlockEntity");
        if (generator == null) return ItemStack.EMPTY;

        generator.refreshDefinition();

        // Find the matching loaded recipe for this die type and tier
        MachineRecipe match = null;
        for (MachineRecipe recipe : generator.recipes()) {
            ItemStack product = recipe.assemble();
            if (product.getCount() != 1 || product.getItem() != ModItems.MASK.get()) continue;
            MaskData design = product.get(ModDataComponents.MASK_DATA.get());
            if (design != null && design.dieType().equals(dieType) && design.tierIndex() == tierIndex) {
                match = recipe;
                break;
            }
        }
        helper.assertTrue(match != null, "loaded recipe must exist for " + dieType + " tier " + tierIndex);
        if (match == null) return ItemStack.EMPTY;

        int recipeTicks = match.spec().ticks();
        helper.assertTrue(recipeTicks > 0 && recipeTicks <= 1000, "bounded mask recipe duration");

        // Load actual inputs: blank_mask (slot 0) + design ingredient (slot 1)
        generator.setSlot(PatternGeneratorBlockEntity.BLANK_SLOT, new ItemStack(ModItems.BLANK_MASK.get()));
        generator.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT, new ItemStack(designIngredient));

        // Energy - refill buffer before run
        int perTick = generator.runner().energyPerTick();
        helper.assertTrue(perTick > 0, "generator energy per tick must be positive");
        int totalEnergy = perTick * (recipeTicks + 5);
        generator.setStoredEnergy(totalEnergy);

        // Run to completion using actual recipe
        for (int tick = 0; tick < recipeTicks + 5; tick++) {
            if (!generator.outputSlot().isEmpty()) break;
            generator.serverTick();
        }

        // Extract output via removeItemNoUpdate (slot 2)
        ItemStack output = generator.removeItemNoUpdate(PatternGeneratorBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(output.isEmpty(), "generator must produce mask");
        helper.assertTrue(output.getItem() == ModItems.MASK.get(), "product must be mask");
        helper.assertValueEqual(1, output.getCount(), "one mask produced");

        // Verify inputs consumed exactly
        helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).isEmpty(), "blank mask consumed");
        helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).isEmpty(), "design ingredient consumed");
        helper.assertFalse(generator.runner().isRunning(), "recipe finished");
        helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(), "energy fully spent");

        MaskData actual = output.get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(actual != null && actual.dieType().equals(dieType) && actual.tierIndex() == tierIndex, "mask design changed");
        helper.assertValueEqual(EUVCRAFTMod.settings().maskUses(), actual.usesRemaining(), "configured uses stamped");

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Pattern generator produced {} mask for {} at tier {} with {} uses",
            output.getItem(), dieType, tierIndex, EUVCRAFTMod.settings().maskUses());
        return output;
    }

    // ------------------------------------------------------------------
    // STAGE 5: Process Cell - build structure, load, run
    // ------------------------------------------------------------------

    private static ItemStack runProcessCell(GameTestHelper helper, ServerLevel level, BlockPos controllerPos,
            ItemStack inputFoupStack, ItemStack mask, String dieType, int tierIndex) {

        // Build the 2x2x3 north-facing process cell structure
        // Controller at (1,1,1), facing NORTH
        // Ports at back face z=3: (x1,y1) and (x2,y2)
        // Remaining 9 are casings
        Direction facing = Direction.NORTH;
        BlockPos controller = controllerPos;
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    BlockPos p = controllerPos.offset(x - 1, y - 1, z - 1);
                    if (p.equals(controller)) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                            .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                    } else if (z == 3 && (x == 1 && y == 1 || x == 2 && y == 2)) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                }
            }
        }

        ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
            level.getBlockEntity(helper.absolutePos(controller));
        helper.assertTrue(cell != null, "controller block entity must exist");
        if (cell == null) return ItemStack.EMPTY;

        cell.revalidate();
        helper.assertTrue(cell.isFormed(), "cell must be formed");
        helper.assertValueEqual(12, cell.members().size(), "cell must have 12 members");

        // Load slots
        // Slot 0: FOUP with sliced lot (pass actual stack, not reconstructed)
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, inputFoupStack);
        // Slot 1: mask
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, mask);
        // Slot 2: resist (tier-appropriate)
        Item resistItem = tierIndex == 1 ? ModItems.RESIST_T1.get() : ModItems.RESIST_T2.get();
        cell.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, new ItemStack(resistItem, 64));
        // Slots 3-7: components
        Item[] t1Components = {
            ModItems.MERCURY_LAMP.get(),
            ModItems.CONTACT_PLATE.get(),
            ModItems.MANUAL_ALIGNER.get(),
            ModItems.BASIC_AIR_FILTER.get(),
            ModItems.OPTICAL_MICROSCOPE.get()
        };
        Item[] t2Components = {
            ModItems.I_LINE_LAMP.get(),
            ModItems.REDUCTION_LENS.get(),
            ModItems.STEPPER_STAGE.get(),
            ModItems.FINE_AIR_FILTER.get(),
            ModItems.OVERLAY_SCOPE.get()
        };
        Item[] components = tierIndex == 1 ? t1Components : t2Components;
        for (int i = 0; i < 5; i++) {
            cell.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i, new ItemStack(components[i]));
        }

        // Energy - enough for full run
        cell.setStoredEnergy(50000);

        // Run until FINISHED with bounded iteration
        int maxTicks = 500;
        for (int tick = 0; tick < maxTicks; tick++) {
            if (cell.processState() == ProcessState.FINISHED) break;
            cell.serverTick();
        }

        helper.assertValueEqual(ProcessState.FINISHED, cell.processState(), "cell must reach FINISHED state");
        helper.assertFalse(cell.inputsLocked(), "finished cell must unlock inputs");

        // Read the processed lot from slot 0 - extract actual stack
        ItemStack processedFoupStack = cell.removeItemNoUpdate(ProcessCellControllerBlockEntity.SLOT_FOUP);
        helper.assertTrue(!processedFoupStack.isEmpty(), "slot 0 must hold processed FOUP");
        helper.assertTrue(FoupItems.isFoup(processedFoupStack), "slot 0 must be a FOUP");

        // Verify resist consumed: 64 - layers*2 (1 layer for T1, 12 for T2)
        int expectedResistRemaining = 64 - (tierIndex == 1 ? 1 : 12) * 2;
        ItemStack resistRemaining = cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST);
        if (expectedResistRemaining > 0) {
            helper.assertValueEqual(expectedResistRemaining, resistRemaining.getCount(), "resist must be consumed per layer");
        } else {
            helper.assertTrue(resistRemaining.isEmpty(), "resist must be fully consumed");
        }

        // Verify mask used exactly once (for entire lot)
        ItemStack maskAfter = cell.slot(ProcessCellControllerBlockEntity.SLOT_MASK);
        int maskUsesAfter = 0;
        if (!maskAfter.isEmpty()) {
            MaskData md = maskAfter.get(ModDataComponents.MASK_DATA.get());
            if (md != null) maskUsesAfter = md.usesRemaining();
        }
        int expectedMaskUses = EUVCRAFTMod.settings().maskUses() - 1;
        if (expectedMaskUses <= 0) {
            helper.assertTrue(maskAfter.isEmpty(), "exhausted mask must be removed from slot");
        } else {
            helper.assertValueEqual(expectedMaskUses, maskUsesAfter, "mask must lose exactly one use for entire lot");
        }

        FoupContents processedLot = FoupItems.trustedContentsOf(processedFoupStack);
        EUVCRAFTMod.LOGGER.info("[M1-GATE] Process cell finished: lot at layer {}, {} wafers, mask uses {}",
            processedLot.lotLayer(), processedLot.size(), maskUsesAfter);
        return processedFoupStack;
    }

    // ------------------------------------------------------------------
    // STAGE 6: Test & Package - judge, package, verify exact outputs
    // ------------------------------------------------------------------

    private static void runTestAndPackage(GameTestHelper helper, ServerLevel level, BlockPos pos,
            ItemStack processedFoupStack, String dieType, int tierIndex,
            int waferCount, int maskUsesBefore) {

        helper.setBlock(pos, PackagingMachines.TEST_PACKAGE.get());
        TestPackageBlockEntity machine = (TestPackageBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(machine != null, "must have TestPackageBlockEntity");
        if (machine == null) return;

        MachineDefinition definition = machine.definition().orElse(null);
        helper.assertTrue(definition != null, "test_package definition must be loaded");
        if (definition == null) return;

        int ticksPerWafer = definition.param(TestPackageBlockEntity.TICKS_PER_WAFER_PARAM).orElse(0);
        int packagesPerWafer = definition.param(TestPackageBlockEntity.PACKAGE_PER_WAFER_PARAM).orElse(1);
        helper.assertTrue(ticksPerWafer > 0, "ticks_per_wafer must be positive");
        helper.assertTrue(packagesPerWafer > 0, "package_per_wafer must be positive");

        int chipsPerWafer = EUVCRAFTMod.settings().chipsPerWafer();
        int energyPerTick = definition.energyPerTick(
            TierDataLoader.getRegistry().byIndex(tierIndex).orElseThrow(), EUVCRAFTMod.settings());

        // ---- BEFORE admission: compute expected from server-trusted contents ----
        FoupContents processedLot = FoupItems.trustedContentsOf(processedFoupStack);
        int passing = FabYieldService.countPassing(level, processedLot,
            TierDataLoader.getRegistry().byIndex(tierIndex).orElseThrow(), dieType, null);
        int failing = waferCount - passing;

        int expectedChips = passing * chipsPerWafer;
        int expectedScrap = failing * chipsPerWafer;
        int expectedPackages = passing * packagesPerWafer;

        // Passing may be ZERO or ALL - no fixture assert
        Item expectedChip = ChipCatalog.chipFor(dieType, tierIndex).orElse(null);
        helper.assertTrue(expectedChip != null, "chip catalog must have chip for " + dieType + " at tier " + tierIndex);

        // Capture original lot id and FOUP item/capacity
        UUID originalLotId = processedLot.lotId();
        Item foupItem = processedFoupStack.getItem();
        int originalCapacity = processedLot.capacity();

        // Load packages (enough for all passing wafers, capped at 64)
        int packagesToLoad = Math.min(expectedPackages + 10, 64);
        machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT, new ItemStack(ModItems.CHIP_PACKAGE.get(), packagesToLoad));

        // Load the processed FOUP into slot 0 (pass actual stack)
        machine.setSlot(TestPackageBlockEntity.FOUP_SLOT, processedFoupStack);

        // Charge energy for entire run
        int totalTicks = waferCount * ticksPerWafer;
        long totalEnergy = (long) totalTicks * energyPerTick;
        machine.setStoredEnergy(totalEnergy + energyPerTick * 5);

        // First tick: admission happens
        machine.serverTick();
        helper.assertTrue(machine.isQueueActive(), "complete lot must be admitted");

        // Verify returned FOUP immediately on admission tick
        ItemStack returnedFoup = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(returnedFoup.is(foupItem), "returned FOUP must be same item");
        FoupContents returnedLot = FoupItems.contentsOf(returnedFoup);
        helper.assertTrue(returnedLot != null && returnedLot.isEmpty(), "returned FOUP must be empty");
        helper.assertValueEqual(originalCapacity, returnedLot.capacity(), "returned FOUP keeps original capacity");
        helper.assertFalse(returnedLot.lotId().equals(originalLotId), "returned FOUP must have fresh lot id");
        helper.assertTrue(returnedLot.run() == null, "returned FOUP must have no run spec");

        // Drain output banks after every tick to avoid full banks
        int actualChips = 0;
        int actualScrap = 0;

        // Run to completion
        for (int tick = 0; tick < totalTicks + 20; tick++) {
            if (!machine.isQueueActive() && machine.wafersDone() == waferCount) break;
            machine.serverTick();

            // Drain chip bank
            for (int i = 0; i < TestPackageBlockEntity.CHIP_SLOT_COUNT; i++) {
                int slotIdx = TestPackageBlockEntity.CHIP_SLOT_FIRST + i;
                ItemStack stack = machine.removeItemNoUpdate(slotIdx);
                if (stack.getItem() == expectedChip) {
                    actualChips += stack.getCount();
                } else if (!stack.isEmpty()) {
                    helper.fail("chip slot " + i + " holds unexpected item: " + stack.getItem());
                }
            }

            // Drain scrap bank
            for (int i = 0; i < TestPackageBlockEntity.SCRAP_SLOT_COUNT; i++) {
                int slotIdx = TestPackageBlockEntity.SCRAP_SLOT_FIRST + i;
                ItemStack stack = machine.removeItemNoUpdate(slotIdx);
                if (stack.getItem() == ModItems.CHIP_SCRAP.get()) {
                    actualScrap += stack.getCount();
                } else if (!stack.isEmpty()) {
                    helper.fail("scrap slot " + i + " holds unexpected item: " + stack.getItem());
                }
            }
        }

        helper.assertValueEqual(waferCount, machine.wafersDone(), "all wafers must be processed");
        helper.assertValueEqual(waferCount, machine.wafersTotal(), "total must match input");
        helper.assertFalse(machine.isQueueActive(), "queue must be finished");

        // ---- Verify exact chip outputs ----
        helper.assertValueEqual(expectedChips, actualChips,
            "exact chip count mismatch: expected " + expectedChips + " got " + actualChips);

        // ---- Verify exact scrap outputs ----
        helper.assertValueEqual(expectedScrap, actualScrap,
            "exact scrap count mismatch: expected " + expectedScrap + " got " + actualScrap);

        // ---- Verify package consumption ----
        int packagesRemaining = machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount();
        int packagesUsed = packagesToLoad - packagesRemaining;
        helper.assertValueEqual(expectedPackages, packagesUsed,
            "package delta mismatch: expected " + expectedPackages + " used " + packagesUsed);

        // ---- Verify no other outputs ----
        int totalOutputs = actualChips + actualScrap;
        int expectedTotal = expectedChips + expectedScrap;
        helper.assertValueEqual(expectedTotal, totalOutputs,
            "no unexplained outputs: expected " + expectedTotal + " total, got " + totalOutputs);

        // ---- Verify final empty FOUP ----
        ItemStack finalFoup = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(finalFoup.is(foupItem), "final FOUP must be same item");
        FoupContents finalLot = FoupItems.contentsOf(finalFoup);
        helper.assertTrue(finalLot != null && finalLot.isEmpty(), "final FOUP must be empty");
        helper.assertValueEqual(originalCapacity, finalLot.capacity(), "final FOUP capacity preserved");
        helper.assertFalse(finalLot.lotId().equals(originalLotId), "final FOUP has fresh id");

        // ---- Verify energy spent exactly ----
        long energySpent = totalEnergy + energyPerTick * 5 - machine.buffer().getAmountAsLong();
        helper.assertValueEqual(totalEnergy, energySpent,
            "energy spent must match exactly: expected " + totalEnergy + " got " + energySpent);

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Test & Package: {} wafers ({}, {}) -> {} chips, {} scrap, {} packages, {} FE",
            waferCount, passing, failing, actualChips, actualScrap, packagesUsed, energySpent);
    }
}