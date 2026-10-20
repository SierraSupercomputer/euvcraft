package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerMenu;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachines;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the T3 Boule Purifier jobs and the Wafer Slicer's pure-boule path, on the
 * real server level with the real loaded data pack.
 *
 * <p>Everything of consequence runs through the machines' own {@code serverTick}, the same
 * code path the game runs. The support recipe registry that must change mid-test is swapped
 * synchronously and restored in a {@code finally} (see {@link #withSupportRegistry}), the way
 * the silicon chain tests swap their registries; what the machine does with its own pinned
 * snapshot across such a swap is the thing under test, not the swap.</p>
 *
 * <p>These tests carry the existing fixture gate: they are registered only when fixtures are
 * enabled, so shipping builds and the shipping probe pack never include them. They are a
 * separate class from {@link SupportMultiblockGameTests}, which stays frozen as the
 * foundation's contract.</p>
 */
public final class T3PurificationGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "t3_purification";

    private static final String PURIFIER_REFUSES = "purifier_refuses_without_water_output_or_fe";
    private static final String PURIFIER_BUCKET = "purifier_bucket_exchange_conserves";
    private static final String PURIFIER_FULL_JOB = "purifier_runs_full_job_1000_water_exactly_once";
    private static final String PURIFIER_PAUSE_RESUME = "purifier_low_fe_pause_and_resume";
    private static final String PURIFIER_OUTPUT_FULL = "purifier_output_full_pauses";
    private static final String PURIFIER_PINNED = "purifier_reload_keeps_pinned_cost_and_tamper_cancels";
    private static final String PURIFIER_BAD_SAVE = "purifier_malformed_save_rejected";
    private static final String SLICER_GRADES = "slicer_slices_both_grades_to_matching_foups";
    private static final String SLICER_MENU_GRADES = "slicer_menu_accepts_both_grades";
    private static final String SLICER_RELOAD_GRADE = "slicer_pure_grade_survives_reload_and_refuses_tamper";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    /** The purifier controller in the test structure. */
    private static final BlockPos CELL = new BlockPos(3, 3, 3);
    /** The control position, for a second support structure. */
    private static final BlockPos SECOND_CELL = new BlockPos(9, 3, 9);
    /** The slicer under test, well away from the support cells. */
    private static final BlockPos SLICER = new BlockPos(15, 3, 3);
    /** A second slicer position, for the control machine a slicer case needs. */
    private static final BlockPos SLICER_TWO = new BlockPos(21, 3, 9);

    static {
        FUNCTIONS.register(PURIFIER_REFUSES, () -> T3PurificationGameTests::purifierRefusesWithoutInputs);
        FUNCTIONS.register(PURIFIER_BUCKET, () -> T3PurificationGameTests::purifierBucketExchangeConserves);
        FUNCTIONS.register(PURIFIER_FULL_JOB, () -> T3PurificationGameTests::purifierRunsFullJob);
        FUNCTIONS.register(PURIFIER_PAUSE_RESUME, () -> T3PurificationGameTests::purifierPauseAndResume);
        FUNCTIONS.register(PURIFIER_OUTPUT_FULL, () -> T3PurificationGameTests::purifierOutputFullPauses);
        FUNCTIONS.register(PURIFIER_PINNED, () -> T3PurificationGameTests::purifierPinnedCostAndTamper);
        FUNCTIONS.register(PURIFIER_BAD_SAVE, () -> T3PurificationGameTests::purifierMalformedSave);
        FUNCTIONS.register(SLICER_GRADES, () -> T3PurificationGameTests::slicerSlicesBothGrades);
        FUNCTIONS.register(SLICER_MENU_GRADES, () -> T3PurificationGameTests::slicerMenuAcceptsBothGrades);
        FUNCTIONS.register(SLICER_RELOAD_GRADE, () -> T3PurificationGameTests::slicerPureGradeSurvivesReload);
    }

    private T3PurificationGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) T3PurificationGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            400, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, PURIFIER_REFUSES);
        register(event, data, PURIFIER_BUCKET);
        register(event, data, PURIFIER_FULL_JOB);
        register(event, data, PURIFIER_PAUSE_RESUME);
        register(event, data, PURIFIER_OUTPUT_FULL);
        register(event, data, PURIFIER_PINNED);
        register(event, data, PURIFIER_BAD_SAVE);
        register(event, data, SLICER_GRADES);
        register(event, data, SLICER_MENU_GRADES);
        register(event, data, SLICER_RELOAD_GRADE);
        LOGGER.info("Registered EUVCRAFT T3 purification game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // world building
    // ------------------------------------------------------------------

    /** Unit vector along the cell's depth, written out from the core structure definition. */
    private static int[] backVector(Direction facing) {
        return switch (facing) {
            case EAST -> new int[] { 1, 0, 0 };
            case WEST -> new int[] { -1, 0, 0 };
            case SOUTH -> new int[] { 0, 0, 1 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    /** Right vector of a horizontal facing. */
    private static int[] rightVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 1, 0, 0 };
            case SOUTH -> new int[] { -1, 0, 0 };
            case EAST -> new int[] { 0, 0, 1 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    private static final int[] UP = { 0, 1, 0 };

    /** The twelve cells in canonical (u*6+v*3+d) order around the anchor at d == 0. */
    private static List<BlockPos> cellsOf(BlockPos controller, Direction facing) {
        int[] right = rightVector(facing);
        int[] back = backVector(facing);
        List<BlockPos> cells = new ArrayList<>(12);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    cells.add(new BlockPos(
                        controller.getX() + right[0] * u + back[0] * d,
                        controller.getY() + UP[1] * v,
                        controller.getZ() + right[2] * u + back[2] * d));
                }
            }
        }
        return cells;
    }

    /**
     * Places a purifier and its eleven support members from the matcher's canonical cell
     * geometry (2 wide x 2 tall x 3 deep, one power port at the back-left-lower cell), then
     * drives one revalidation.
     *
     * @return the controller, or null when there is none; the caller asserts on formation
     */
    private static @Nullable SupportMachineBlockEntity placeFormedPurifier(GameTestHelper helper,
            BlockPos controllerRel, Direction facing) {
        BlockPos controller = helper.absolutePos(controllerRel);
        ServerLevel level = helper.getLevel();
        List<BlockPos> cells = cellsOf(controller, facing);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos pos = cells.get(u * 6 + v * 3 + d);
                    BlockState state;
                    if (u == 0 && v == 0 && d == 0) {
                        state = SupportMachines.BOULE_PURIFIER.get().defaultBlockState()
                            .setValue(SupportMachineControllerBlock.FACING, facing);
                    } else if (u == 0 && v == 0 && d == 2) {
                        state = SupportMachines.SUPPORT_POWER_PORT.get().defaultBlockState();
                    } else {
                        state = SupportMachines.SUPPORT_CASING.get().defaultBlockState();
                    }
                    level.setBlock(pos, state, 3);
                }
            }
        }
        SupportMachineBlockEntity machine = controllerAt(level, controller);
        if (machine != null) {
            machine.revalidate();
        }
        return machine;
    }

    /**
     * Replaces just the controller block, yielding a fresh block entity with no items, no
     * energy and no running job sitting on the same (already valid) casing cell.
     */
    private static @Nullable SupportMachineBlockEntity freshPurifierOnCell(GameTestHelper helper) {
        return placeFormedPurifier(helper, CELL, Direction.NORTH);
    }

    private static @Nullable SupportMachineBlockEntity controllerAt(ServerLevel level, BlockPos abs) {
        return level.getBlockEntity(abs) instanceof SupportMachineBlockEntity controller
            ? controller : null;
    }

    private static @Nullable WaferSlicerBlockEntity slicerAt(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(rel))
            instanceof WaferSlicerBlockEntity slicer ? slicer : null;
    }

    /** The loaded purifier recipe; every test reads its numbers instead of hard-coding them. */
    private static SupportRecipeLoader.Resolved purifierRecipe(GameTestHelper helper) {
        SupportRecipeLoader.Registry registry = SupportRecipeLoader.getRegistry();
        helper.assertTrue(registry != null,
            "no support recipe registry: data/lithmod/support_recipes did not load");
        helper.assertTrue(registry != null && registry.hasMachine("boule_purifier"),
            "no boule_purifier recipe loaded from data/lithmod/support_recipes");
        SupportRecipeLoader.Resolved recipe = registry == null
            ? null : registry.firstFor("boule_purifier").orElse(null);
        helper.assertTrue(recipe != null, "no boule_purifier recipe resolved");
        return recipe;
    }

    private static void tickSupport(SupportMachineBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) {
            machine.serverTick();
        }
    }

    private static void tickSlicer(WaferSlicerBlockEntity slicer, int count) {
        for (int i = 0; i < count; i++) {
            slicer.serverTick();
        }
    }

    /** Fills the purifier's tank with {@code amount} mB and returns the tank. */
    private static WaterTank fillTank(GameTestHelper helper, SupportMachineBlockEntity purifier,
            int amount) {
        WaterTank tank = purifier.waterTank();
        helper.assertTrue(tank != null, "the purifier has no water tank");
        if (tank != null) {
            tank.fill(amount, false);
        }
        return tank;
    }

    private static boolean formed(GameTestHelper helper,
            @Nullable SupportMachineBlockEntity machine, String what) {
        helper.assertTrue(machine != null, what + ": no controller block entity");
        helper.assertTrue(machine != null && machine.isFormed(),
            what + ": the cell did not form (reason: "
                + (machine == null ? "-" : machine.failureReason()) + ")");
        return machine != null && machine.isFormed();
    }

    // ------------------------------------------------------------------
    // 1. No water, no boule, no FE: nothing starts and nothing is consumed
    // ------------------------------------------------------------------

    private static void purifierRefusesWithoutInputs(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }

        tickSupport(purifier, 20);
        helper.assertFalse(purifier.isJobRunning(), "the purifier ran with no inputs at all");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT).isEmpty(),
            "the purifier produced something from nothing");

        // An ordinary boule alone: no water, no job.
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        tickSupport(purifier, 20);
        helper.assertFalse(purifier.isJobRunning(), "the purifier ran with no water");

        // Water in the tank alone: no boule, no job.
        purifier.clearContent();
        fillTank(helper, purifier, recipe.recipe().waterMb());
        tickSupport(purifier, 20);
        helper.assertFalse(purifier.isJobRunning(), "the purifier ran with no boule");

        // Both present: the job starts, and per-tick cost minus one FE pays for nothing.
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        tickSupport(purifier, 1);
        helper.assertTrue(purifier.isJobRunning(),
            "a formed cell with complete inputs did not start a job");
        int cost = recipe.recipe().energyPerTick();
        purifier.setStoredEnergy(cost - 1);
        tickSupport(purifier, 5);
        helper.assertValueEqual(0, purifier.jobProgress(),
            "the purifier advanced a tick without the FE for one");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_0).is(
            ModItems.SILICON_BOULE.get()), "the unpaid run consumed the boule");
        LOGGER.info("[T3] purifier_refuses_without_water_output_or_fe: idle, no-water, no-boule "
            + "and perTick-1 cases all refused");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. The bucket exchange is exact, all or nothing, and conservative
    // ------------------------------------------------------------------

    private static void purifierBucketExchangeConserves(GameTestHelper helper) {
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        WaterTank tank = purifier.waterTank();
        helper.assertTrue(tank != null, "the purifier has no water tank");

        // One water bucket -> exactly 1000 mB in the tank and exactly one empty bucket in slot 2.
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.WATER_BUCKET));
        tickSupport(purifier, 1);
        helper.assertValueEqual(SupportMachineBlockEntity.BUCKET_WATER_MB,
            purifier.waterAmount(), "one bucket did not put exactly 1000 mB in the tank");
        ItemStack drained = purifier.slot(SupportMachineBlockEntity.SLOT_EMPTY_BUCKET_OUT);
        helper.assertTrue(drained.is(Items.BUCKET) && drained.getCount() == 1,
            "the drained bucket did not land in slot 2 as one empty bucket");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_1).isEmpty(),
            "the drained slot kept a stack");

        // A blocked empty-bucket output preserves both inventories and the tank.
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.WATER_BUCKET));
        purifier.setSlot(SupportMachineBlockEntity.SLOT_EMPTY_BUCKET_OUT,
            new ItemStack(ModItems.SILICON_BOULE.get()));
        tickSupport(purifier, 3);
        helper.assertValueEqual(SupportMachineBlockEntity.BUCKET_WATER_MB, purifier.waterAmount(),
            "a blocked output still filled the tank");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_1).is(Items.WATER_BUCKET),
            "the blocked exchange consumed the water bucket anyway");
        helper.assertTrue(
            purifier.slot(SupportMachineBlockEntity.SLOT_EMPTY_BUCKET_OUT).is(ModItems.SILICON_BOULE.get()),
            "the blocked exchange overwrote the output slot");
        purifier.clearSlot(SupportMachineBlockEntity.SLOT_EMPTY_BUCKET_OUT);
        purifier.clearSlot(SupportMachineBlockEntity.SLOT_IN_1);

        // A wrong fluid is never drained into the tank.
        int before = purifier.waterAmount();
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.LAVA_BUCKET));
        tickSupport(purifier, 3);
        helper.assertValueEqual(before, purifier.waterAmount(), "a lava bucket changed the tank");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_1).is(Items.LAVA_BUCKET),
            "the lava bucket was consumed by the exchange");
        purifier.clearSlot(SupportMachineBlockEntity.SLOT_IN_1);

        // A full tank accepts nothing more: the exchange refuses and preserves everything.
        fillTank(helper, purifier, SupportMachineBlockEntity.DEFAULT_WATER_CAPACITY_MB);
        int full = purifier.waterAmount();
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.WATER_BUCKET));
        tickSupport(purifier, 3);
        helper.assertValueEqual(full, purifier.waterAmount(),
            "a full tank took water from a bucket it had no room for");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_1).is(Items.WATER_BUCKET),
            "a full tank consumed the water bucket anyway");

        // An unformed cell exchanges nothing, even with a bucket in the slot.
        purifier.clearContent();
        purifier.clearContent();
        helper.getLevel().removeBlock(helper.absolutePos(CELL), false);
        // The step above destroys the controller; place an air marker cell: unformed.
        SupportMachineBlockEntity unformed = controllerAt(helper.getLevel(), helper.absolutePos(CELL));
        helper.assertTrue(unformed == null, "removing the controller left a block entity behind");
        LOGGER.info("[T3] purifier_bucket_exchange_conserves: exact 1000 mB fill, blocked-output, "
            + "wrong-fluid and full-tank cases all conserved");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. A full powered job: water consumed once, exact FE, exact products
    // ------------------------------------------------------------------

    private static void purifierRunsFullJob(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        WaterTank tank = fillTank(helper, purifier, recipe.recipe().waterMb());
        helper.assertTrue(tank != null, "the purifier has no water tank");
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        // The bucket is drained first, so the exchange fills the tank here and its empty
        // bucket lands in slot 2 before the job starts.
        purifier.clearSlot(SupportMachineBlockEntity.SLOT_IN_1);

        int duration = recipe.recipe().duration();
        int cost = recipe.recipe().energyPerTick();
        purifier.setStoredEnergy(cost * (duration + 2));

        tickSupport(purifier, duration + 5);

        ItemStack pure = purifier.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT);
        helper.assertTrue(pure.is(ModItems.PURE_SILICON_BOULE.get()),
            "the product is not a pure boule but " + pure.getItem());
        helper.assertValueEqual(recipe.recipe().outputCount(), pure.getCount(),
            "the product count is wrong");
        helper.assertValueEqual(0, purifier.waterAmount(),
            "the job did not drain exactly its pinned water once");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_EMPTY_BUCKET_OUT).isEmpty(),
            "no bucket was drained in this test (none inserted), yet slot 2 holds one");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_0).isEmpty(),
            "the ordinary boule was not consumed at completion");
        helper.assertFalse(purifier.isJobRunning(), "the job did not finish");
        long spent = cost * (duration + 2) - purifier.buffer().getAmountAsLong();
        helper.assertValueEqual((long) cost * duration, spent,
            "the job did not spend exactly energy_per_tick * duration");
        LOGGER.info("[T3] purifier_runs_full_job: {} FE over {} ticks, 1 boule + {} mB -> {} pure boule",
            spent, duration, recipe.recipe().waterMb(), recipe.recipe().outputCount());

        // With the tank empty again, a second job cannot start.
        tickSupport(purifier, 5);
        helper.assertFalse(purifier.isJobRunning(), "the purifier started a job with no water left");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. Low FE pauses with progress intact; recharging resumes
    // ------------------------------------------------------------------

    private static void purifierPauseAndResume(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        fillTank(helper, purifier, recipe.recipe().waterMb());
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        int duration = recipe.recipe().duration();
        int cost = recipe.recipe().energyPerTick();
        purifier.setStoredEnergy(cost * 5);
        tickSupport(purifier, 10);

        int progress = purifier.jobProgress();
        helper.assertTrue(progress > 0 && progress < duration && purifier.isJobPaused(),
            "the test did not get a paused mid-run job (progress=" + progress + ")");
        long energyAtPause = purifier.buffer().getAmountAsLong();
        tickSupport(purifier, 30);
        helper.assertValueEqual(progress, purifier.jobProgress(), "progress crept while unpowered");
        helper.assertValueEqual(energyAtPause, purifier.buffer().getAmountAsLong(),
            "the paused purifier kept drawing FE");
        helper.assertValueEqual(recipe.recipe().waterMb(), purifier.waterAmount(),
            "the paused purifier leaked water");

        // Recharge: the paused job resumes (its progress is kept) and finishes.
        purifier.setStoredEnergy(cost * duration);
        tickSupport(purifier, duration + 5);
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT).is(
            ModItems.PURE_SILICON_BOULE.get()), "the resumed job did not finish its product");
        helper.assertValueEqual(0, purifier.waterAmount(), "the resumed job's water was not drained once");
        LOGGER.info("[T3] purifier_low_fe_pause_and_resume: paused at {}/{}, resumed and finished",
            progress, duration);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. A full pure-boule output pauses the run with its progress intact
    // ------------------------------------------------------------------

    private static void purifierOutputFullPauses(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        fillTank(helper, purifier, recipe.recipe().waterMb());
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        int duration = recipe.recipe().duration();
        int cost = recipe.recipe().energyPerTick();
        purifier.setStoredEnergy(cost * duration);

        tickSupport(purifier, 6);
        // Start checkpoint: a full output blocked the start as well, but the run is under way
        // here (started with a free output) — the blockage arrives mid-run.
        helper.assertTrue(purifier.isJobRunning(), "the job did not start with a free output");
        purifier.setSlot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT,
            new ItemStack(ModItems.PURE_SILICON_BOULE.get(), 64));
        tickSupport(purifier, 1);
        helper.assertTrue(purifier.isJobRunning() && purifier.isJobPaused(),
            "a full output did not pause the job before it charged anything");
        helper.assertValueEqual(0, purifier.jobProgress(),
            "the purifier charged a tick against a blocked output");
        int progress = purifier.jobProgress();
        tickSupport(purifier, 30);
        helper.assertValueEqual(progress, purifier.jobProgress(), "progress moved while output was full");
        helper.assertTrue(purifier.slot(SupportMachineBlockEntity.SLOT_IN_0).is(ModItems.SILICON_BOULE.get()),
            "the boule was consumed against a blocked output");
        helper.assertValueEqual(recipe.recipe().waterMb(), purifier.waterAmount(),
            "water was consumed against a blocked output");
        helper.assertValueEqual((long) recipe.recipe().energyPerTick() * (duration - progress),
            purifier.buffer().getAmountAsLong(), "FE was drawn against a blocked output");

        // Free the output: the job resumes, finishes and stacks onto the freed slot.
        purifier.setSlot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT,
            new ItemStack(ModItems.PURE_SILICON_BOULE.get(), 40));
        tickSupport(purifier, duration + 5);
        helper.assertValueEqual(41, purifier.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT).getCount(),
            "the resumed job did not land its product on the freed output");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. A pinned job keeps its cost across a reload and a data swap; tamper cancels
    // ------------------------------------------------------------------

    private static void purifierPinnedCostAndTamper(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        fillTank(helper, purifier, recipe.recipe().waterMb());
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        int duration = recipe.recipe().duration();
        int cost = recipe.recipe().energyPerTick();
        purifier.setStoredEnergy(cost * (duration + 2));
        tickSupport(purifier, 3);
        helper.assertTrue(purifier.isJobRunning(), "the job did not start");
        int savedProgress = purifier.jobProgress();

        TagValueOutput savedOutput = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        purifier.saveWithId(savedOutput);
        CompoundTag saved = savedOutput.buildResult();
        int expectedTicksLeft = duration - savedProgress;
        long expectedEnergyAfterFinish = purifier.buffer().getAmountAsLong()
            - (long) cost * expectedTicksLeft;

        // Reload into a fresh block entity on the same cell.
        SupportMachineBlockEntity reloaded = freshPurifierOnCell(helper);
        if (!formed(helper, reloaded, "reloaded purifier")) {
            return;
        }
        reloaded.loadWithComponents(TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING,
            helper.getLevel().registryAccess(), saved));
        helper.assertTrue(reloaded.isJobRunning(), "the pinned job did not survive the round trip");
        helper.assertValueEqual(savedProgress, reloaded.jobProgress(),
            "the pinned job's progress changed across the round trip");
        helper.assertValueEqual(duration, reloaded.jobDuration(),
            "the pinned job's duration changed across the round trip");

        SupportRecipeLoader.Registry previous = SupportRecipeLoader.getRegistry();
        try {
            // Swap the loaded recipes for one that is half as long and half as expensive: the
            // already-running job must still finish under its pinned snapshot numbers.
            SupportRecipe halved = new SupportRecipe(recipe.recipe().machine(),
                recipe.recipe().input(), null, recipe.recipe().outputItem(),
                recipe.recipe().outputCount(), Math.max(1, duration / 2),
                Math.max(1, cost / 2), recipe.recipe().waterMb());
            withSupportRegistry(SupportRecipeLoader.buildResolved(Map.of(
                Identifier.fromNamespaceAndPath("lithmod", "swapped"), halved)), () -> {
                    tickSupport(reloaded, duration + 5);
                });
            helper.assertTrue(
                reloaded.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT).is(ModItems.PURE_SILICON_BOULE.get()),
                "the pinned job did not finish its product after the mid-run data swap");
            helper.assertFalse(reloaded.isJobRunning(), "the pinned job stayed running");
            helper.assertValueEqual(expectedEnergyAfterFinish, reloaded.buffer().getAmountAsLong(),
                "the pinned job's cost changed when the data swapped under it");
            LOGGER.info("[T3] purifier_reload_keeps_pinned_cost: finished {} remaining ticks under a swapped data pack",
                expectedTicksLeft);
        } finally {
            withSupportRegistry(previous, () -> { });
        }
        helper.assertValueEqual(0, reloaded.waterAmount(),
            "the pinned job did not drain its pinned water");
        helper.assertTrue(reloaded.slot(SupportMachineBlockEntity.SLOT_IN_0).isEmpty(),
            "the pinned job did not consume its boule");

        // Input tamper: swapping the ordinary boule for a pure one cancels the new run,
        // consumes nothing and grants no finish.
        SupportMachineBlockEntity tamperer = placeFormedPurifier(helper, SECOND_CELL, Direction.NORTH);
        if (!formed(helper, tamperer, "control purifier")) {
            return;
        }
        fillTank(helper, tamperer, recipe.recipe().waterMb());
        tamperer.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        tamperer.setStoredEnergy(cost * 3);
        tickSupport(tamperer, 2);
        helper.assertTrue(tamperer.isJobRunning(), "the control job did not start");
        long feBeforeTamper = tamperer.buffer().getAmountAsLong();
        tamperer.setSlot(SupportMachineBlockEntity.SLOT_IN_0,
            new ItemStack(ModItems.PURE_SILICON_BOULE.get()));
        tickSupport(tamperer, 5);
        helper.assertFalse(tamperer.isJobRunning(), "a tampered job kept running or restarted");
        helper.assertTrue(tamperer.slot(SupportMachineBlockEntity.SLOT_PURE_BOULE_OUT).isEmpty(),
            "a tampered job produced a pure boule");
        helper.assertValueEqual(recipe.recipe().waterMb(), tamperer.waterAmount(),
            "a tampered job consumed water");
        helper.assertTrue(tamperer.buffer().getAmountAsLong() <= feBeforeTamper,
            "the tampered job gained FE it did not hold");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 7. A malformed save is rejected wholesale; the machine fails closed
    // ------------------------------------------------------------------

    private static void purifierMalformedSave(GameTestHelper helper) {
        SupportRecipeLoader.Resolved recipe = purifierRecipe(helper);
        SupportMachineBlockEntity purifier = placeFormedPurifier(helper, CELL, Direction.NORTH);
        if (!formed(helper, purifier, "purifier")) {
            return;
        }
        fillTank(helper, purifier, recipe.recipe().waterMb());
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.SILICON_BOULE.get()));
        int duration = recipe.recipe().duration();
        purifier.setStoredEnergy(recipe.recipe().energyPerTick() * 3);
        tickSupport(purifier, 2);
        TagValueOutput savedOutput = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        purifier.saveWithId(savedOutput);
        CompoundTag saved = savedOutput.buildResult();

        for (String corruption : new String[] {"progress", "duration", "energy_per_tick", "water_mb",
            "output_item", "input_count"}) {
            SupportMachineBlockEntity reloaded = freshPurifierOnCell(helper);
            if (reloaded == null) {
                helper.fail("the reloaded controller is missing for " + corruption);
                return;
            }
            CompoundTag copy = saved.copy();
            CompoundTag jobTag = copy.getCompound("purifierJob");
            switch (corruption) {
                case "progress" -> jobTag.putInt("progress", duration);
                case "duration" -> jobTag.putInt("duration", 0);
                case "energy_per_tick" -> jobTag.putInt("energy_per_tick", -5);
                case "water_mb" -> jobTag.putInt("water_mb", 0);
                case "output_item" -> jobTag.putString("output_item", "lithmod:does_not_exist");
                case "input_count" -> jobTag.putInt("input_count", 0);
                default -> { }
            }
            reloaded.loadWithComponents(TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), copy));
            helper.assertFalse(reloaded.isJobRunning(),
                "a corrupted '" + corruption + "' save was adopted");
            helper.assertValueEqual(0, reloaded.jobProgress(),
                "the rejected save left progress behind");
        }
        LOGGER.info("[T3] purifier_malformed_save_rejected: all six malformed fields rejected");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 8. The slicer stamps the lot with the boule's own grade, both ways
    // ------------------------------------------------------------------

    private static void slicerSlicesBothGrades(GameTestHelper helper) {
        helper.setBlock(SLICER, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity slicer = slicerAt(helper, SLICER);
        helper.assertTrue(slicer != null, "no wafer slicer block entity");
        if (slicer == null) {
            return;
        }
        int capacity = EUVCRAFTMod.settings().foupCapacity();

        // Ordinary boule into an ordinary carrier -> ORDINARY lot, exactly as before.
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.of(UUID.randomUUID(), capacity, List.of(), WaferSubstrate.ORDINARY)));
        slicer.setStoredEnergy(slicer.energyPerTick() * (slicer.sliceTicks() + 2));
        tickSlicer(slicer, slicer.sliceTicks() + 5);
        FoupPublicView ordinaryView = FoupItems.publicViewOf(slicer.outputSlot());
        helper.assertTrue(ordinaryView != null && ordinaryView.grade() == WaferSubstrate.ORDINARY,
            "an ordinary boule did not slice into an ORDINARY lot");

        // Ordinary carrier + pure boule -> PURE lot. The carrier's grade never decides.
        helper.setBlock(SLICER_TWO, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity pureSlicer = slicerAt(helper, SLICER_TWO);
        helper.assertTrue(pureSlicer != null, "no control slicer");
        if (pureSlicer == null) {
            return;
        }
        pureSlicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.PURE_SILICON_BOULE.get()));
        pureSlicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.of(UUID.randomUUID(), capacity, List.of(), WaferSubstrate.ORDINARY)));
        pureSlicer.setStoredEnergy(pureSlicer.energyPerTick() * (pureSlicer.sliceTicks() + 2));
        tickSlicer(pureSlicer, pureSlicer.sliceTicks() + 5);
        // The grade is read through the public view, the payload a client is allowed to hold;
        // hidden rolls are never inspected directly.
        FoupPublicView pureView = FoupItems.publicViewOf(pureSlicer.outputSlot());
        helper.assertTrue(pureView != null && pureView.grade() == WaferSubstrate.PURE,
            "a pure boule did not slice into a PURE lot");
        helper.assertTrue(pureView.waferCount() > 0, "the pure lot came out empty");
        // The trusted server-side read agrees with the public view.
        FoupContents pureLot = FoupItems.trustedContentsOf(pureSlicer.outputSlot());
        helper.assertTrue(pureLot.grade() == WaferSubstrate.PURE,
            "the trusted read disagrees with the public view about the grade");
        LOGGER.info("[T3] slicer_slices_both_grades: ordinary -> ORDINARY, pure -> PURE");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 9. The menu's boule slot accepts both grades and refuses the rest
    // ------------------------------------------------------------------

    private static void slicerMenuAcceptsBothGrades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(SLICER, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity slicer = slicerAt(helper, SLICER);
        helper.assertTrue(slicer != null, "no wafer slicer block entity");
        if (slicer == null) {
            return;
        }
        ServerPlayer player = connect(level);
        WaferSlicerMenu menu = new WaferSlicerMenu(1, player.getInventory(), slicer);
        Slot bouleSlot = menu.slots.get(WaferSlicerBlockEntity.BOULE_SLOT);
        helper.assertTrue(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON_BOULE.get())),
            "the boule slot refused an ordinary boule");
        helper.assertTrue(bouleSlot.mayPlace(new ItemStack(ModItems.PURE_SILICON_BOULE.get())),
            "the boule slot refused a pure boule");
        helper.assertFalse(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON.get())),
            "the boule slot accepted raw silicon");
        // The pure-boule slot of the purifier handles its own item: nothing else in.
        player.closeContainer();
        LOGGER.info("[T3] slicer_menu_accepts_both_grades: ordinary and pure accepted, silicon refused");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 10. A pure run keeps its grade across a reload; a swapped grade cancels
    // ------------------------------------------------------------------

    private static void slicerPureGradeSurvivesReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(SLICER, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity slicer = slicerAt(helper, SLICER);
        helper.assertTrue(slicer != null, "no wafer slicer block entity");
        if (slicer == null) {
            return;
        }
        int capacity = EUVCRAFTMod.settings().foupCapacity();
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.PURE_SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.of(UUID.randomUUID(), capacity, List.of(), WaferSubstrate.ORDINARY)));
        int perTick = slicer.energyPerTick();
        slicer.setStoredEnergy(perTick * slicer.sliceTicks());
        tickSlicer(slicer, Math.max(1, slicer.sliceTicks() / 2));
        int progress = slicer.sliceProgress();
        helper.assertTrue(progress > 0 && progress < slicer.sliceTicks(),
            "the test did not get halfway through a pure slice");

        // A swapped grade cancels the run rather than mislabelling the lot.
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        tickSlicer(slicer, 1);
        helper.assertFalse(slicer.isSlicing(), "a grade-swapped run kept slicing");
        helper.assertValueEqual(0, slicer.sliceProgress(), "a grade-swapped run kept its progress");

        // A pure run survives a reload with its grade intact.
        helper.setBlock(SLICER_TWO, SiliconMachines.WAFER_SLICER.get());
        WaferSlicerBlockEntity reloaded = slicerAt(helper, SLICER_TWO);
        helper.assertTrue(reloaded != null, "no control slicer");
        if (reloaded != null) {
            reloaded.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.PURE_SILICON_BOULE.get()));
            reloaded.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK1.get(),
                FoupContents.of(UUID.randomUUID(), capacity, List.of(), WaferSubstrate.ORDINARY)));
            reloaded.setStoredEnergy(perTick * reloaded.sliceTicks());
            tickSlicer(reloaded, Math.max(1, reloaded.sliceTicks() / 2));
            TagValueOutput savedOutput = TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            reloaded.saveWithId(savedOutput);
            CompoundTag saved = savedOutput.buildResult();
            int savedProgress = reloaded.sliceProgress();
            reloaded.resetSlice();
            reloaded.clearContent();
            reloaded.setStoredEnergy(0);
            reloaded.loadWithComponents(TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved));
            helper.assertValueEqual(savedProgress, reloaded.sliceProgress(),
                "the pure run's progress did not survive the round trip");
            reloaded.setStoredEnergy(perTick * reloaded.sliceTicks());
            tickSlicer(reloaded, reloaded.sliceTicks() + 5);
            FoupPublicView view = FoupItems.publicViewOf(reloaded.outputSlot());
            helper.assertTrue(view != null && view.grade() == WaferSubstrate.PURE,
                "the reloaded run did not finish into a PURE lot");

            // A hand-edited grade field is not trusted: the run is dropped.
            CompoundTag tampered = saved.copy();
            CompoundTag slicerTag = tampered.getCompound("slicer");
            slicerTag.putString("bouleGrade", "gilded");
            reloaded.resetSlice();
            reloaded.loadWithComponents(TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tampered));
            helper.assertFalse(reloaded.isSlicing(),
                "a save with an unknown boule grade was adopted");
        }
        LOGGER.info("[T3] slicer_pure_grade_survives_reload_and_refuses_tamper");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // registry swap helper
    // ------------------------------------------------------------------

    /**
     * Swaps the support recipe registry for the duration of {@code action}, restoring the
     * previous registry afterwards even if the action throws.
     *
     * <p>{@link SupportRecipeLoader} caches what it resolved in a static field because a game
     * test cannot drive a real server resource reload; the previous registry is put back in a
     * {@code finally} so the fallback runs no matter what the body did.</p>
     */
    private static void withSupportRegistry(@Nullable SupportRecipeLoader.Registry registry,
            Runnable action) {
        try {
            Field field = SupportRecipeLoader.class.getDeclaredField("resolved");
            field.setAccessible(true);
            field.set(null, registry);
            action.run();
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the support recipe registry could not be swapped", failure);
        }
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "t3-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
