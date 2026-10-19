package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.YieldMath;
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
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Aggregate result of one complete chain run.
 */
record ChainResult(
        UUID lotId,
        int waferCount,
        int passing,
        int chips,
        int scrap,
        int packages
) {
}

/**
 * Result of test and package stage.
 */
record TestPackageResult(
        int passing,
        int chips,
        int scrap,
        int packages
) {
}

/**
 * M1 gate tests: chain cases drive real machines on a ServerLevel from raw inputs
 * to final packaged output; the statistical case exercises the server yield service.
 *
 * <p>Chain cases use no handcrafted intermediate products or direct
 * FabYieldService.applyLayerStep calls. Machines use their actual serverTick with real FE.
 * All configuration borrowed only inside synchronous try/finally with exact restoration.
 * h.succeed() called AFTER cleanup.
 */
public final class M1GateGameTests {

    private static final String ENVIRONMENT_ID = "m1_gate";

    /** Logic T1 complete chain test. */
    private static final String LOGIC_T1_CHAIN = "m1_gate_logic_t1_chain";
    /** Memory T2 complete chain test. */
    private static final String MEMORY_T2_CHAIN = "m1_gate_memory_t2_chain";
    /** Statistics: 200 lots each of T1 logic and T2 memory through FabYieldService.applyLayerStep. */
    private static final String YIELD_STATISTICS = "m1_gate_yield_statistics";
    /** Twenty machine lots: 10 T1 logic and 10 T2 memory through the full real chain. */
    private static final String TWENTY_MACHINE_LOTS = "m1_gate_twenty_machine_lots";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    /** Base position for the first machine in the chain. */
    private static final BlockPos BASE = new BlockPos(1, 1, 1);
    /** Offset for each subsequent machine to avoid overlap. */
    private static final int MACHINE_SPACING = 10;

    static {
        FUNCTIONS.register(LOGIC_T1_CHAIN, () -> M1GateGameTests::logicT1Chain);
        FUNCTIONS.register(MEMORY_T2_CHAIN, () -> M1GateGameTests::memoryT2Chain);
        FUNCTIONS.register(YIELD_STATISTICS, () -> M1GateGameTests::yieldStatistics);
        FUNCTIONS.register(TWENTY_MACHINE_LOTS, () -> M1GateGameTests::twentyMachineLots);
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
        register(event, data, YIELD_STATISTICS);
        register(event, data, TWENTY_MACHINE_LOTS);
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
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. Memory T2 complete chain
    // ------------------------------------------------------------------

    private static void memoryT2Chain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        runCompleteChain(helper, level, DieTypes.MEMORY, 2, Items.REDSTONE);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. Yield Statistics: 200 lots each of T1 logic and T2 memory
    // ------------------------------------------------------------------

    private static void yieldStatistics(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        runYieldStatistics(helper, level);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. Twenty Machine Lots: 10 T1 logic and 10 T2 memory full chain
    // ------------------------------------------------------------------

    private static void twentyMachineLots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        runTwentyMachineLots(helper, level);
        helper.succeed();
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
     * @return aggregate result of the chain run
     */
    private static ChainResult runCompleteChain(GameTestHelper helper, ServerLevel level,
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
            helper.assertValueEqual(Math.min(4, FoupItems.configuredCapacity()), waferCount, "configured slice yield bounded by carrier capacity");
            helper.assertValueEqual(FoupItems.configuredCapacity(), foupCapacity, "fresh carrier stores configured capacity");

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

            // ============================================================
            // STAGE 6: Test & Package - judge, package, verify exact outputs
            // ============================================================
            TestPackageResult pkgResult = runTestAndPackage(helper, level, packagePos, processedFoupStack, dieType, tierIndex,
                waferCount, maskUsesBefore);

            return new ChainResult(slicedLotId, waferCount, pkgResult.passing(), pkgResult.chips(), pkgResult.scrap(), pkgResult.packages());

        } finally {
            // Exact restoration
            FabConfig.WAFERS_PER_BOULE.set(originalWafersPerBoule);
            FabConfig.RESIST_PER_LAYER.set(originalResistPerLayer);
            FabConfig.LAYER_TICKS_OVERRIDES.set(originalLayerTicksOverrides);
            EUVCRAFTMod.config().onConfigReload();
        }

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
        helper.assertTrue(smeltingHolder.id().identifier().toString().equals("lithmod:silicon_from_smelting"),
            "silicon smelting recipe must be from lithmod");
        helper.assertTrue(blastingHolder.id().identifier().getNamespace().equals(EUVCRAFTMod.MODID),
            "silicon blasting recipe must be from lithmod");

        helper.assertValueEqual(4, count, "four raw quartz feed the complete chain");
        helper.assertTrue(blastingHolder.id().identifier().toString().equals("lithmod:silicon_from_blasting"),
            "actual quartz blasting recipe selected");
        silicon.addAll(cookSilicon(helper, level, BASE.offset(0, 0, 6), Blocks.FURNACE, 2));
        silicon.addAll(cookSilicon(helper, level, BASE.offset(3, 0, 6), Blocks.BLAST_FURNACE, 2));
        EUVCRAFTMod.LOGGER.info("[M1-GATE] Took {} silicon from actual furnace and blast-furnace outputs", silicon.size());
        return silicon;
    }

    /** Raw quartz and fuel are inserted directly; the real vanilla ticker produces every silicon. */
    private static List<ItemStack> cookSilicon(GameTestHelper h, ServerLevel level, BlockPos relative,
            Block block, int count) {
        h.setBlock(relative, Blocks.AIR);
        h.setBlock(relative, block);
        BlockPos pos = h.absolutePos(relative);
        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(furnace != null, "real cooking block entity exists");
        furnace.setItem(0, new ItemStack(Items.QUARTZ, count));
        furnace.setItem(1, new ItemStack(Items.COAL));
        for (int tick = 0; tick < 2000 && furnace.getItem(2).getCount() < count; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        }
        h.assertTrue(furnace.getItem(0).isEmpty(), "all raw quartz consumed by actual furnace");
        h.assertTrue(furnace.getItem(1).isEmpty(), "actual furnace consumes its fuel");
        ItemStack output = furnace.removeItemNoUpdate(2);
        h.assertTrue(output.is(ModItems.SILICON.get()), "actual cooking output is silicon");
        h.assertValueEqual(count, output.getCount(), "one actual silicon per quartz");
        h.assertTrue(furnace.getItem(2).isEmpty(), "cooking output ownership transferred");
        List<ItemStack> result = new ArrayList<>();
        while (!output.isEmpty()) result.add(output.split(1));
        return result;
    }

    // ------------------------------------------------------------------
    // STAGE 2: Crystal Puller - 4 silicon -> 1 boule
    // ------------------------------------------------------------------

    /** Supply test FE up to the real capacity and measure the actual debit. */
    private static long paidTick(FabMachineBlockEntity machine) {
        machine.setStoredEnergy(Math.toIntExact(machine.buffer().getCapacityAsLong()));
        long before = machine.buffer().getAmountAsLong();
        machine.serverTick();
        long paid = before - machine.buffer().getAmountAsLong();
        if (paid < 0) throw new IllegalStateException("machine gained unprovided FE");
        return paid;
    }

    private static long untilProduct(GameTestHelper h, FabMachineBlockEntity machine, int limit) {
        h.assertTrue(limit > 0 && limit <= 10000, "bounded machine duration");
        long paid = 0;
        for (int tick = 0; tick < limit && machine.outputSlot().isEmpty(); tick++) paid += paidTick(machine);
        h.assertFalse(machine.outputSlot().isEmpty(), "machine produced a real output within its bound");
        return paid;
    }

    private static ItemStack runCrystalPuller(GameTestHelper h, ServerLevel level, BlockPos pos,
            List<ItemStack> siliconInput) {
        h.setBlock(pos, SiliconMachines.CRYSTAL_PULLER.get());
        var puller = (CrystalPullerBlockEntity) level.getBlockEntity(h.absolutePos(pos));
        h.assertTrue(puller != null, "real Crystal Puller exists");
        puller.serverTick(); // Empty machine refreshes its actual loaded definition.
        var recipe = puller.recipes().stream().filter(r -> r.spec().result().itemId()
            .equals("lithmod:silicon_boule")).findFirst().orElseThrow();
        ItemStack feed = ItemStack.EMPTY;
        for (ItemStack actual : siliconInput) {
            h.assertTrue(actual.is(ModItems.SILICON.get()) && actual.getCount() == 1, "actual assembled silicon input");
            ItemStack taken = actual.split(actual.getCount());
            if (feed.isEmpty()) feed = taken; else feed.grow(taken.getCount());
            h.assertTrue(actual.isEmpty(), "recipe output ownership transferred");
        }
        h.assertValueEqual(4, feed.getCount(), "four assembled silicon in the feed");
        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, feed);
        int cost = recipe.energyPerTick(puller.definition().orElseThrow().energyPerTick(null, EUVCRAFTMod.settings()));
        long paid = untilProduct(h, puller, recipe.ticks() + 5);
        h.assertValueEqual((long) cost * recipe.ticks(), paid, "puller paid actual recipe cost");
        ItemStack result = puller.removeItemNoUpdate(CrystalPullerBlockEntity.OUTPUT_SLOT);
        h.assertTrue(result.is(ModItems.SILICON_BOULE.get()) && result.getCount() == 1, "one actual boule");
        h.assertTrue(puller.outputSlot().isEmpty() && puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).isEmpty(),
            "puller feed consumed and product removed");
        return result;
    }

    private static ItemStack runWaferSlicer(GameTestHelper h, ServerLevel level, BlockPos pos, ItemStack boule) {
        h.setBlock(pos, SiliconMachines.WAFER_SLICER.get());
        var slicer = (WaferSlicerBlockEntity) level.getBlockEntity(h.absolutePos(pos));
        h.assertTrue(slicer != null, "real Wafer Slicer exists");
        slicer.serverTick();
        int ticks = slicer.sliceTicks(), cost = slicer.energyPerTick();
        ItemStack carrier = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        var empty = FoupItems.trustedContentsOf(carrier);
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, boule);
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, carrier);
        long paid = untilProduct(h, slicer, ticks + 5);
        ItemStack result = slicer.removeItemNoUpdate(WaferSlicerBlockEntity.OUTPUT_SLOT);
        var lot = FoupItems.trustedContentsOf(result);
        h.assertTrue(result.is(ModItems.FOUP_MK1.get()), "actual carrier item retained");
        h.assertValueEqual(empty.capacity(), lot.capacity(), "stored carrier capacity retained");
        h.assertValueEqual(Math.min(4, empty.capacity()), lot.size(), "real sliced wafer yield");
        h.assertFalse(empty.lotId().equals(lot.lotId()), "slicer creates a fresh lot identity");
        h.assertValueEqual((long) ticks * cost, paid, "slicer pays each work tick");
        h.assertTrue(slicer.outputSlot().isEmpty() && slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty()
            && slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(), "slicer consumes both inputs and transfers output");
        return result;
    }

    private static ItemStack runPatternGenerator(GameTestHelper h, ServerLevel level, BlockPos pos,
            String dieType, int tierIndex, Item designIngredient) {
        h.setBlock(pos, MaskMachines.PATTERN_GENERATOR.get());
        var generator = (PatternGeneratorBlockEntity) level.getBlockEntity(h.absolutePos(pos));
        h.assertTrue(generator != null, "real Pattern Generator exists");
        generator.serverTick();
        var recipe = generator.recipes().stream().filter(r -> {
            ItemStack template = r.assemble();
            MaskData design = template.get(ModDataComponents.MASK_DATA.get());
            return template.is(ModItems.MASK.get()) && design != null
                && design.dieType().equals(dieType) && design.tierIndex() == tierIndex;
        }).findFirst().orElseThrow();
        generator.setSlot(PatternGeneratorBlockEntity.BLANK_SLOT, new ItemStack(ModItems.BLANK_MASK.get()));
        generator.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT, new ItemStack(designIngredient));
        int cost = recipe.energyPerTick(generator.definition().orElseThrow().energyPerTick(null, EUVCRAFTMod.settings()));
        long paid = untilProduct(h, generator, recipe.ticks() + 5);
        ItemStack result = generator.removeItemNoUpdate(PatternGeneratorBlockEntity.OUTPUT_SLOT);
        MaskData actual = result.get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(result.is(ModItems.MASK.get()) && result.getCount() == 1 && actual != null, "one actual patterned mask");
        h.assertValueEqual(dieType, actual.dieType(), "actual mask die");
        h.assertValueEqual(tierIndex, actual.tierIndex(), "actual mask tier");
        h.assertValueEqual(EUVCRAFTMod.settings().maskUses(), actual.usesRemaining(), "configured uses stamped");
        h.assertValueEqual((long) cost * recipe.ticks(), paid, "generator pays actual recipe cost");
        h.assertTrue(generator.outputSlot().isEmpty() && generator.slot(0).isEmpty() && generator.slot(1).isEmpty(),
            "generator consumes inputs and transfers output");
        return result;
    }

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
        long cellPaid = 0;

        // Run until FINISHED with bounded iteration
        int maxTicks = 500;
        for (int tick = 0; tick < maxTicks; tick++) {
            if (cell.processState() == ProcessState.FINISHED) break;
            cellPaid += paidTick(cell);
        }

        helper.assertValueEqual(ProcessState.FINISHED, cell.processState(), "cell must reach FINISHED state");
        helper.assertFalse(cell.inputsLocked(), "finished cell must unlock inputs");
        int cellCost = cell.definition().orElseThrow().energyPerTick(TierDataLoader.getRegistry().byIndex(tierIndex).orElseThrow(), EUVCRAFTMod.settings());
        helper.assertValueEqual((long) cellCost * 3 * (tierIndex == 1 ? 1 : 12), cellPaid, "cell exact paid work");

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

    private static TestPackageResult runTestAndPackage(GameTestHelper helper, ServerLevel level, BlockPos pos,
            ItemStack processedFoupStack, String dieType, int tierIndex,
            int waferCount, int maskUsesBefore) {

        helper.setBlock(pos, PackagingMachines.TEST_PACKAGE.get());
        TestPackageBlockEntity machine = (TestPackageBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(machine != null, "must have TestPackageBlockEntity");
        if (machine == null) return new TestPackageResult(0, 0, 0, 0);

        machine.serverTick(); // Refresh while empty, before admission.
        MachineDefinition definition = machine.definition().orElse(null);
        helper.assertTrue(definition != null, "test_package definition must be loaded");
        if (definition == null) return new TestPackageResult(0, 0, 0, 0);

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
        long energySpent = 0;

        // First tick: admission happens
        energySpent += paidTick(machine);
        helper.assertTrue(machine.isQueueActive(), "complete lot must be admitted");

        // Verify returned FOUP immediately on admission tick
        ItemStack returnedFoup = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(returnedFoup.is(foupItem), "returned FOUP must be same item");
        FoupContents returnedLot = FoupItems.trustedContentsOf(returnedFoup);
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
            energySpent += paidTick(machine);

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
        FoupContents finalLot = FoupItems.trustedContentsOf(finalFoup);
        helper.assertTrue(finalLot != null && finalLot.isEmpty(), "final FOUP must be empty");
        helper.assertValueEqual(originalCapacity, finalLot.capacity(), "final FOUP capacity preserved");
        helper.assertFalse(finalLot.lotId().equals(originalLotId), "final FOUP has fresh id");

        // ---- Verify energy spent exactly ----
        helper.assertValueEqual(totalEnergy, energySpent,
            "energy spent must match exactly: expected " + totalEnergy + " got " + energySpent);

        EUVCRAFTMod.LOGGER.info("[M1-GATE] Test & Package: {} wafers ({}, {}) -> {} chips, {} scrap, {} packages, {} FE",
            waferCount, passing, failing, actualChips, actualScrap, packagesUsed, energySpent);

        return new TestPackageResult(passing, actualChips, actualScrap, packagesUsed);
    }

    // ------------------------------------------------------------------
    // 3. Yield Statistics: 200 lots each of T1 logic and T2 memory
    // ------------------------------------------------------------------

    /**
     * Runs exactly 200 lots each of T1 logic and T2 memory through REAL
     * FabYieldService.applyLayerStep on server level, all physical required layers
     * (1 and 12). Uses 64 wafers/lot for 12800 samples/tier.
     * Asserts abs(observedRate - target) <= 0.02 for BOTH tiers.
     */
    private static void runYieldStatistics(GameTestHelper helper, ServerLevel level) {
        // Snapshot and restore yield multiplier
        double originalYieldMultiplier = FabConfig.YIELD_MULTIPLIER.get();
        try {
            // Force yield multiplier = 1.0
            FabConfig.YIELD_MULTIPLIER.set(1.0);
            EUVCRAFTMod.config().onConfigReload();

            TierDataLoader.getRegistry();
            Tier t1 = TierDataLoader.getRegistry().byIndex(1).orElseThrow();
            Tier t2 = TierDataLoader.getRegistry().byIndex(2).orElseThrow();

            // Verify baseline yields are 60% and 80%
            double t1Yield = YieldMath.resolveEffectiveYield(t1, EUVCRAFTMod.settings(), null);
            double t2Yield = YieldMath.resolveEffectiveYield(t2, EUVCRAFTMod.settings(), null);
            helper.assertTrue(Math.abs(t1Yield - 0.60) <= 1e-9, "T1 baseline yield must be 60%, got " + (t1Yield * 100) + "%");
            helper.assertTrue(Math.abs(t2Yield - 0.80) <= 1e-9, "T2 baseline yield must be 80%, got " + (t2Yield * 100) + "%");

            final int LOTS = 200;
            final int WAFERS_PER_LOT = 64;
            final int T1_LAYERS = 1;
            final int T2_LAYERS = 12;

            // T1 Logic statistics
            int t1Passing = 0;
            int t1Total = 0;
            for (int lot = 0; lot < LOTS; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = FoupContents.empty(lotId, WAFERS_PER_LOT);
                for (int wafer = 0; wafer < WAFERS_PER_LOT; wafer++) {
                    contents = contents.withWafer(Wafer.unprocessed());
                }
                for (int layer = 1; layer <= T1_LAYERS; layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t1, DieTypes.LOGIC, null, layer, 1.0);
                }
                t1Passing += FabYieldService.countPassing(level, contents, t1, DieTypes.LOGIC, null);
                t1Total += contents.size();
            }

            // T2 Memory statistics
            int t2Passing = 0;
            int t2Total = 0;
            for (int lot = 0; lot < LOTS; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = FoupContents.empty(lotId, WAFERS_PER_LOT);
                for (int wafer = 0; wafer < WAFERS_PER_LOT; wafer++) {
                    contents = contents.withWafer(Wafer.unprocessed());
                }
                for (int layer = 1; layer <= T2_LAYERS; layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t2, DieTypes.MEMORY, null, layer, 1.0);
                }
                t2Passing += FabYieldService.countPassing(level, contents, t2, DieTypes.MEMORY, null);
                t2Total += contents.size();
            }

            helper.assertTrue(t1Total == LOTS * WAFERS_PER_LOT, "T1 must sample exactly 12800 wafers");
            helper.assertTrue(t2Total == LOTS * WAFERS_PER_LOT, "T2 must sample exactly 12800 wafers");
            double t1Rate = (double) t1Passing / t1Total;
            double t2Rate = (double) t2Passing / t2Total;

            // Assert tolerance <= 0.02
            helper.assertTrue(Math.abs(t1Rate - t1Yield) <= 0.02,
                    "T1 observed rate " + String.format("%.4f", t1Rate) + " vs target " + String.format("%.4f", t1Yield) + " (diff=" + String.format("%.4f", Math.abs(t1Rate - t1Yield)) + ")");
            helper.assertTrue(Math.abs(t2Rate - t2Yield) <= 0.02,
                    "T2 observed rate " + String.format("%.4f", t2Rate) + " vs target " + String.format("%.4f", t2Yield) + " (diff=" + String.format("%.4f", Math.abs(t2Rate - t2Yield)) + ")");

            // Log aggregate evidence
            EUVCRAFTMod.LOGGER.info("[M1-GATE-STATS] T1 logic: {} of {} wafers passed, observed={} target={} diff={}",
                    t1Passing, t1Total, t1Rate, t1Yield, Math.abs(t1Rate - t1Yield));
            EUVCRAFTMod.LOGGER.info("[M1-GATE-STATS] T2 memory: {} of {} wafers passed, observed={} target={} diff={}",
                    t2Passing, t2Total, t2Rate, t2Yield, Math.abs(t2Rate - t2Yield));

        } finally {
            // Restore yield multiplier
            FabConfig.YIELD_MULTIPLIER.set(originalYieldMultiplier);
            EUVCRAFTMod.config().onConfigReload();
        }
    }

    // ------------------------------------------------------------------
    // 4. Twenty Machine Lots: 10 T1 logic and 10 T2 memory full chain
    // ------------------------------------------------------------------

    /**
     * Runs exactly 10 T1 logic and 10 T2 memory lots through the full real chain.
     * Reuses full conservation assertions. Each processed lot has fresh identity.
     * Machines are reset between runs.
     */
    private static void runTwentyMachineLots(GameTestHelper helper, ServerLevel level) {
        // Snapshot and restore config
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

            Set<UUID> seenLotIds = new HashSet<>();
            // Run 10 T1 logic lots
            for (int i = 0; i < 10; i++) {
                resetMachines(helper, level, pullerPos, slicerPos, generatorPos, cellControllerPos, packagePos);
                ChainResult result = runCompleteChain(helper, level, DieTypes.LOGIC, 1, Items.COPPER_INGOT);
                helper.assertTrue(seenLotIds.add(result.lotId()), "Each machine lot must have a fresh identity");
                EUVCRAFTMod.LOGGER.info("[M1-GATE-20LOTS] T1 logic lot {}: {} wafers, {} passing, {} chips, {} scrap, {} packages",
                        i + 1, result.waferCount(), result.passing(), result.chips(), result.scrap(), result.packages());
            }

            // Run 10 T2 memory lots
            for (int i = 0; i < 10; i++) {
                resetMachines(helper, level, pullerPos, slicerPos, generatorPos, cellControllerPos, packagePos);
                ChainResult result = runCompleteChain(helper, level, DieTypes.MEMORY, 2, Items.REDSTONE);
                helper.assertTrue(seenLotIds.add(result.lotId()), "Each machine lot must have a fresh identity");
                EUVCRAFTMod.LOGGER.info("[M1-GATE-20LOTS] T2 memory lot {}: {} wafers, {} passing, {} chips, {} scrap, {} packages",
                        i + 1, result.waferCount(), result.passing(), result.chips(), result.scrap(), result.packages());
            }

            helper.assertTrue(seenLotIds.size() == 20, "Must complete exactly 20 independent machine lots");
        } finally {
            // Exact restoration
            FabConfig.WAFERS_PER_BOULE.set(originalWafersPerBoule);
            FabConfig.RESIST_PER_LAYER.set(originalResistPerLayer);
            FabConfig.LAYER_TICKS_OVERRIDES.set(originalLayerTicksOverrides);
            EUVCRAFTMod.config().onConfigReload();
        }
    }

    /**
     * Resets all machines between lot runs by removing blocks and clearing inventories.
     * Setting the SAME blockstate does not recreate BE, so we must remove/reset.
     */
    private static void resetMachines(GameTestHelper helper, ServerLevel level,
            BlockPos pullerPos, BlockPos slicerPos, BlockPos generatorPos,
            BlockPos cellControllerPos, BlockPos packagePos) {
        // Clear all machine block entities by setting to AIR first
        helper.setBlock(pullerPos, Blocks.AIR);
        helper.setBlock(slicerPos, Blocks.AIR);
        helper.setBlock(generatorPos, Blocks.AIR);
        // Process cell: clear the whole 2x2x3 structure
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    BlockPos p = cellControllerPos.offset(x - 1, y - 1, z - 1);
                    helper.setBlock(p, Blocks.AIR);
                }
            }
        }
        helper.setBlock(packagePos, Blocks.AIR);
    }

}
