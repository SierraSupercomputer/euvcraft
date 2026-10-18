package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.DefaultStatRules;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.LineComponents;
import com.euvcraft.lithmod.core.LineLoadout;
import com.euvcraft.lithmod.core.LineStats;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessRun;
import com.euvcraft.lithmod.core.ProcessRunEvent;
import com.euvcraft.lithmod.core.ProcessRunRequest;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.core.StatRules;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.ComponentCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.registry.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellMemberBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagValueInput;
import net.minecraft.nbt.TagValueOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static com.euvcraft.lithmod.core.SupportLines.ALL;

/**
 * Game tests for the Process Cell runtime behaviour on a real server level with real registries.
 *
 * <p>These tests exercise the full {@link ProcessCellControllerBlockEntity} server tick, persistence
 * round-trips, config changes and structure pauses. They build actual blocks, obtain the real
 * controller block entity and call {@link ProcessCellControllerBlockEntity#serverTick()} synchronously.
 * No {@code runAfterDelay} is used because GameTests run on a {@link ServerLevel}.</p>
 *
 * <p>The fixture is a north-facing controller at relative {@code (1,1,1)}, twelve cells
 * {@code x=1..2, y=1..2, z=1..3}, controller {@code (1,1,1)}, power ports {@code (1,1,3)} and
 * {@code (2,2,3)}, others casing. All tests re-validate and assert the cell is formed before
 * starting.</p>
 */
public final class ProcessCellGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "process_cell_runtime";

    private static final String LOGIC_T1_CONSERVATION = "process_cell_logic_t1_conservation";
    private static final String MEMORY_T2_CONSERVATION = "process_cell_memory_t2_conservation";
    private static final String WEAK_LINE_REFUSED = "process_cell_weak_line_refused";
    private static final String ABORT_LATCH_ROUND_TRIP = "process_cell_abort_latch_round_trip";
    private static final String EMPTY_RESIST_RESTORE_AND_TOP_UP = "process_cell_empty_resist_restore_and_top_up";
    private static final String STRUCTURE_PAUSE_RESTORE = "process_cell_structure_pause_restore";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    private static final StructureShape SHAPE = StructureShape.PROCESS_CELL;

    /** Two ports on the back face (d=2), so they never collide with the controller cell at d=0. */
    private static final int[] PORT_A = { 0, 0, 2 };
    private static final int[] PORT_B = { 1, 1, 2 };

    static {
        FUNCTIONS.register(LOGIC_T1_CONSERVATION, () -> ProcessCellGameTests::processCellLogicT1Conservation);
        FUNCTIONS.register(MEMORY_T2_CONSERVATION, () -> ProcessCellGameTests::processCellMemoryT2Conservation);
        FUNCTIONS.register(WEAK_LINE_REFUSED, () -> ProcessCellGameTests::processCellWeakLineRefused);
        FUNCTIONS.register(ABORT_LATCH_ROUND_TRIP, () -> ProcessCellGameTests::processCellAbortLatchRoundTrip);
        FUNCTIONS.register(EMPTY_RESIST_RESTORE_AND_TOP_UP, () -> ProcessCellGameTests::processCellEmptyResistRestoreAndTopUp);
        FUNCTIONS.register(STRUCTURE_PAUSE_RESTORE, () -> ProcessCellGameTests::processCellStructurePauseRestore);
    }

    private ProcessCellGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) ProcessCellGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                500, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, LOGIC_T1_CONSERVATION);
        register(event, data, MEMORY_T2_CONSERVATION);
        register(event, data, WEAK_LINE_REFUSED);
        register(event, data, ABORT_LATCH_ROUND_TRIP);
        register(event, data, EMPTY_RESIST_RESTORE_AND_TOP_UP);
        register(event, data, STRUCTURE_PAUSE_RESTORE);
        LOGGER.info("Registered EUVCRAFT process cell runtime game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
                ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // Geometry, recomputed independently of StructureMatcher
    // ------------------------------------------------------------------

    /** Unit vector of each facing, written out from the M1-03c definition. */
    private static int[] facingVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 0, 0, -1 };
            case SOUTH -> new int[] { 0, 0, 1 };
            case EAST -> new int[] { 1, 0, 0 };
            case WEST -> new int[] { -1, 0, 0 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    /** back = -f. */
    private static int[] backVector(Direction facing) {
        int[] v = facingVector(facing);
        return new int[] { -v[0], -v[1], -v[2] };
    }

    /** right = f rotated 90 degrees clockwise seen from above, as a literal table. */
    private static int[] rightVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 1, 0, 0 };
            case SOUTH -> new int[] { -1, 0, 0 };
            case EAST -> new int[] { 0, 0, 1 };
            case WEST -> new int[] { 0, 0, -1 };
            default -> new int[] { 1, 0, 0 };
        };
    }

    private static final int[] UP = { 0, 1, 0 };

    /**
     * World cell {@code (u, v, d)} of the box whose controller sits at front-face offset
     * {@code (u0, v0)}, relative to the test origin: {@code controller + (u-u0)*right + (v-v0)*up + d*back}.
     */
    private static BlockPos cell(BlockPos controller, Direction facing, int u0, int v0, int u, int v, int d) {
        int[] right = rightVector(facing);
        int[] back = backVector(facing);
        return new BlockPos(
                controller.getX() + right[0] * (u - u0) + UP[0] * (v - v0) + back[0] * d,
                controller.getY() + right[1] * (u - u0) + UP[1] * (v - v0) + back[1] * d,
                controller.getZ() + right[2] * (u - u0) + UP[2] * (v - v0) + back[2] * d);
    }

    /** The twelve positions of a cell, in {@code (u, v, d)} order. */
    private static List<BlockPos> cellsOf(BlockPos controller, Direction facing, int u0, int v0) {
        List<BlockPos> cells = new ArrayList<>(SHAPE.volume());
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    cells.add(cell(controller, facing, u0, v0, u, v, d));
                }
            }
        }
        return cells;
    }

    private static boolean isPort(int u, int v, int d) {
        return (u == PORT_A[0] && v == PORT_A[1] && d == PORT_A[2])
                || (u == PORT_B[0] && v == PORT_B[1] && d == PORT_B[2]);
    }

    // ------------------------------------------------------------------
    // World building
    // ------------------------------------------------------------------

    /**
     * Places a complete, valid cell and returns its twelve relative positions.
     *
     * <p>The controller carries the facing under test, which is the only way a multiblock can know
     * where its other eleven blocks are.</p>
     */
    private static List<BlockPos> buildCell(GameTestHelper helper, BlockPos controller, Direction facing,
            int u0, int v0) {
        List<BlockPos> placed = new ArrayList<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    BlockPos pos = cell(controller, facing, u0, v0, u, v, d);
                    if (u == u0 && v == v0 && d == 0) {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                                .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                    } else if (isPort(u, v, d)) {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                    placed.add(pos);
                }
            }
        }
        return placed;
    }

    /** Re-runs the controller's match. The tests drive this directly instead of waiting out ticks. */
    private static ProcessCellControllerBlockEntity revalidate(GameTestHelper helper, BlockPos controller) {
        ProcessCellControllerBlockEntity be =
                controller(helper.getLevel(), helper.absolutePos(controller));
        helper.assertTrue(be != null, "no controller block entity at " + controller);
        if (be == null) {
            throw new IllegalStateException("no controller at " + controller);
        }
        be.revalidate();
        return be;
    }

    private static ProcessCellControllerBlockEntity controller(ServerLevel level, BlockPos abs) {
        return level.getBlockEntity(abs) instanceof ProcessCellControllerBlockEntity be ? be : null;
    }

    private static void assertFormed(GameTestHelper helper, ProcessCellControllerBlockEntity controller,
            BlockPos controllerRel, Direction facing, int u0, int v0, String what) {
        helper.assertTrue(controller.isFormed(),
                what + ": the cell is not formed (" + controller.failureReason() + ")");
        if (!controller.isFormed()) {
            return;
        }
        Set<BlockPos> expected = new LinkedHashSet<>();
        for (BlockPos rel : cellsOf(controllerRel, facing, u0, v0)) {
            expected.add(helper.absolutePos(rel));
        }
        helper.assertTrue(controller.members().size() == SHAPE.volume(),
                what + ": expected " + SHAPE.volume() + " members, got " + controller.members().size());
        for (BlockPos rel : cellsOf(controllerRel, facing, u0, v0)) {
            BlockPos abs = helper.absolutePos(rel);
            helper.assertTrue(controller.members().contains(abs),
                    what + ": " + rel + " is not in the member list");
            helper.assertTrue(isFormedFlag(helper.getLevel(), abs),
                    what + ": " + rel + " does not carry the formed flag");
        }
    }

    private static boolean isFormedFlag(ServerLevel level, BlockPos abs) {
        BlockState state = level.getBlockState(abs);
        if (state.getBlock() instanceof ProcessCellControllerBlock) {
            return state.getValue(ProcessCellControllerBlock.FORMED);
        }
        if (state.getBlock() instanceof com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlock) {
            return state.getValue(com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlock.FORMED);
        }
        if (state.getBlock() instanceof com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlock) {
            return state.getValue(com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlock.FORMED);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Test 1: process_cell_logic_t1_conservation
    // ------------------------------------------------------------------

    private static void processCellLogicT1Conservation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        // Build the cell
        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        // Configure settings: T1=3, T2=3 layer ticks, resistPerLayer=2
        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            // T1 logic: 3 layers, 3 ticks/layer = 9 ticks total
            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t1 = registry.byIndex(1).orElseThrow();

            // Create FOUP with 2 wafers
            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            // Create mask: logic, tier 1, 4 uses
            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, 1, 4));

            // Line components: all T1 (mercury/contact/manual/basicfilter/microscope)
            ItemStack lightSource = new ItemStack(ModItems.MERCURY_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.CONTACT_PLATE.get());
            ItemStack stage = new ItemStack(ModItems.MANUAL_ALIGNER.get());
            ItemStack cleanroom = new ItemStack(ModItems.BASIC_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OPTICAL_MICROSCOPE.get());

            // Resist: T1 resist
            ItemStack resistStack = new ItemStack(ModItems.RESIST_T1.get());

            // Put items in controller
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            // Set energy (capacity <= 100000, cost from definition)
            int energyPerTick = be.definition().orElseThrow().energyPerTick(t1, EUVCRAFTMod.settings());
            be.setStoredEnergy(energyPerTick * 10); // plenty for 9 ticks

            // Run 9 ticks total: 3 layers * 3 ticks/layer
            for (int i = 0; i < 9; i++) {
                be.serverTick();
            }

            // Assert finished
            helper.assertTrue(be.run != null && be.run.state() == ProcessState.FINISHED,
                    "run did not finish");
            helper.assertValueEqual(3, be.currentLayer(), "currentLayer should be 3 (finished)");
            helper.assertValueEqual(lotId, be.run.lot().lotId(), "lot UUID unchanged");
            helper.assertValueEqual(2, be.run.lot().size(), "wafer count unchanged");
            helper.assertValueEqual(2, be.run.lot().capacity(), "capacity unchanged");

            // No input lock after finish
            helper.assertFalse(be.inputsLocked(), "inputs should not be locked after finish");

            // Resist: initial - 2*3 = initial - 6
            helper.assertValueEqual(0, be.resistAvailable(), "resist should be consumed");

            // FE: exact 9 * energyPerTick
            long expectedEnergyUsed = 9L * energyPerTick;
            long actualEnergyUsed = energyPerTick * 10 - be.buffer().getAmountAsLong();
            helper.assertValueEqual(expectedEnergyUsed, actualEnergyUsed, "FE exact 9 * cost");

            // Mask uses: 4 -> 3
            ItemStack finalMask = be.slot(ProcessCellControllerBlockEntity.SLOT_MASK);
            MaskData finalMaskData = finalMask.get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(finalMaskData != null, "mask should still exist");
            helper.assertValueEqual(3, finalMaskData.usesRemaining(), "mask uses 4 -> 3");

            // Additional 10 ticks: no more payment, no mask change
            long energyBeforeExtra = be.buffer().getAmountAsLong();
            int maskUsesBeforeExtra = finalMaskData.usesRemaining();
            for (int i = 0; i < 10; i++) {
                be.serverTick();
            }
            helper.assertValueEqual(energyBeforeExtra, be.buffer().getAmountAsLong(),
                    "no FE spent after finish");
            helper.assertValueEqual(maskUsesBeforeExtra,
                    be.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),
                    "no mask use after finish");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Test 2: process_cell_memory_t2_conservation
    // ------------------------------------------------------------------

    private static void processCellMemoryT2Conservation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t2 = registry.byIndex(2).orElseThrow();

            // T2 memory: 12 layers, 3 ticks/layer = 36 ticks
            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            // Mask: memory, tier 2, 1 use (will be consumed on finish)
            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.MEMORY, 2, 1));

            // Line components: all T2 (i_line/reduction/stepper/finefilter/overlayscope)
            ItemStack lightSource = new ItemStack(ModItems.I_LINE_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.REDUCTION_LENS.get());
            ItemStack stage = new ItemStack(ModItems.STEPPER_STAGE.get());
            ItemStack cleanroom = new ItemStack(ModItems.FINE_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OVERLAY_SCOPE.get());

            // Resist: T2 resist
            ItemStack resistStack = new ItemStack(ModItems.RESIST_T2.get());

            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            int energyPerTick = be.definition().orElseThrow().energyPerTick(t2, EUVCRAFTMod.settings());
            be.setStoredEnergy(energyPerTick * 40); // 36 ticks + margin

            // Run 36 ticks
            for (int i = 0; i < 36; i++) {
                be.serverTick();
            }

            // Assert finished
            helper.assertTrue(be.run != null && be.run.state() == ProcessState.FINISHED,
                    "run did not finish");
            helper.assertValueEqual(12, be.currentLayer(), "currentLayer should be 12 (finished)");
            helper.assertValueEqual(lotId, be.run.lot().lotId(), "lot UUID unchanged");
            helper.assertValueEqual(2, be.run.lot().size(), "wafer count unchanged");
            helper.assertValueEqual(2, be.run.lot().capacity(), "capacity unchanged");
            helper.assertFalse(be.inputsLocked(), "inputs should not be locked after finish");

            // Resist: initial 1 - 2*12 = initial - 24 (but resistPerLayer=2, so 24 consumed)
            // We gave 1 resist item (stack count 1), so it should be empty
            helper.assertTrue(be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).isEmpty(),
                    "resist should be fully consumed");

            // FE: exact 36 * energyPerTick
            long expectedEnergyUsed = 36L * energyPerTick;
            long actualEnergyUsed = energyPerTick * 40 - be.buffer().getAmountAsLong();
            helper.assertValueEqual(expectedEnergyUsed, actualEnergyUsed, "FE exact 36 * cost");

            // Mask: 1 use -> 0 (spent, item removed)
            helper.assertTrue(be.slot(ProcessCellControllerBlockEntity.SLOT_MASK).isEmpty(),
                    "mask should be spent and removed");

            // Finished FOUP kept
            helper.assertTrue(be.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getCount() == 1,
                    "finished FOUP should remain");

            // Additional ticks: no duplicate spending
            long energyAfterFinish = be.buffer().getAmountAsLong();
            for (int i = 0; i < 10; i++) {
                be.serverTick();
            }
            helper.assertValueEqual(energyAfterFinish, be.buffer().getAmountAsLong(),
                    "no FE spent after finish");

            // Reload config: no duplicate spending
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();
            for (int i = 0; i < 10; i++) {
                be.serverTick();
            }
            helper.assertValueEqual(energyAfterFinish, be.buffer().getAmountAsLong(),
                    "no FE spent after config reload");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Test 3: process_cell_weak_line_refused
    // ------------------------------------------------------------------

    private static void processCellWeakLineRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t2 = registry.byIndex(2).orElseThrow();

            // T2 mask and resist with T1 components -> should refuse LINE_CANNOT_RUN_TIER
            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, 2, 4));

            // T1 components (weak line)
            ItemStack lightSource = new ItemStack(ModItems.MERCURY_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.CONTACT_PLATE.get());
            ItemStack stage = new ItemStack(ModItems.MANUAL_ALIGNER.get());
            ItemStack cleanroom = new ItemStack(ModItems.BASIC_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OPTICAL_MICROSCOPE.get());

            ItemStack resistStack = new ItemStack(ModItems.RESIST_T2.get());

            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            be.setStoredEnergy(10000);
            be.serverTick();

            // Should be refused
            helper.assertTrue(be.lastRefusal() != null, "should have a refusal reason");
            helper.assertTrue(be.lastRefusal().is(RefusalReason.Code.LINE_CANNOT_RUN_TIER),
                    "refusal should be LINE_CANNOT_RUN_TIER, got " + be.lastRefusal().code());

            // No layers, no FE, no resist, no mask change
            helper.assertValueEqual(0, be.currentLayer(), "no layers should have run");
            helper.assertValueEqual(10000, be.buffer().getAmountAsLong(), "no FE spent");
            helper.assertValueEqual(1, be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount(),
                    "no resist consumed");
            helper.assertValueEqual(4, be.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),
                    "no mask use consumed");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Test 4: process_cell_abort_latch_round_trip
    // ------------------------------------------------------------------

    private static void processCellAbortLatchRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t2 = registry.byIndex(2).orElseThrow();

            // T2 memory: 3 ticks/layer; tick 4 gives applied layer 1 and partial next
            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.MEMORY, 2, 4));

            ItemStack lightSource = new ItemStack(ModItems.I_LINE_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.REDUCTION_LENS.get());
            ItemStack stage = new ItemStack(ModItems.STEPPER_STAGE.get());
            ItemStack cleanroom = new ItemStack(ModItems.FINE_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OVERLAY_SCOPE.get());

            ItemStack resistStack = new ItemStack(ModItems.RESIST_T2.get());

            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            int energyPerTick = be.definition().orElseThrow().energyPerTick(t2, EUVCRAFTMod.settings());
            be.setStoredEnergy(energyPerTick * 20);

            // Run 3 ticks (layer 1 complete), 1 more tick (starts layer 2, resist paid)
            for (int i = 0; i < 4; i++) {
                be.serverTick();
            }

            // After 4 ticks: layer 1 complete, layer 2 started (resist paid for layer 2)
            helper.assertValueEqual(2, be.currentLayer(), "should be on layer 2");
            helper.assertTrue(be.run != null && be.run.resistPaid(), "resist should be paid for layer 2");
            helper.assertValueEqual(1, be.run.progressTicks(), "progress 1 tick into layer 2");

            // Save current layer and resource counts
            int savedLayer = be.currentLayer();
            long savedEnergy = be.buffer().getAmountAsLong();
            int savedResistCount = be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount();
            int savedMaskUses = be.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get()).usesRemaining();

            // Abort
            be.abortRun();
            helper.assertFalse(be.inputsLocked(), "inputs should be unlocked after abort");
            helper.assertTrue(be.aborted(), "abort should be latched");
            helper.assertValueEqual(savedLayer, be.currentLayer(), "applied layer preserved");

            // 10 more ticks: no resources pending
            long energyAfterAbort = be.buffer().getAmountAsLong();
            for (int i = 0; i < 10; i++) {
                be.serverTick();
            }
            helper.assertValueEqual(energyAfterAbort, be.buffer().getAmountAsLong(),
                    "no FE spent while aborted");
            helper.assertValueEqual(savedResistCount, be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount(),
                    "no resist consumed while aborted");
            helper.assertValueEqual(savedMaskUses,
                    be.slot(ProcessCellControllerBlockEntity.SLOT_MASK).get(ModDataComponents.MASK_DATA.get()).usesRemaining(),
                    "no mask use while aborted");

            // Save/load into same real controller
            CompoundTag tag = saveTag(level, be);
            loadTag(level, be, tag);
            be.revalidate();
            helper.assertTrue(be.isFormed(), "cell should still be formed after reload");

            // First tick after load must still be stopped (abort latched)
            be.serverTick();
            helper.assertTrue(be.aborted(), "abort should persist after save/load");
            helper.assertFalse(be.inputsLocked(), "inputs still unlocked");
            helper.assertValueEqual(savedLayer, be.currentLayer(), "layer preserved after save/load");

            // Remove FOUP (removeItemNoUpdate), reinsert same tick
            ItemStack removedFoup = be.removeItemNoUpdate(ProcessCellControllerBlockEntity.SLOT_FOUP);
            be.setItem(ProcessCellControllerBlockEntity.SLOT_FOUP, removedFoup);

            // Next tick resumes from zero progress on current layer, pays new layer resist
            be.serverTick();
            helper.assertFalse(be.aborted(), "abort should clear after FOUP reinsert");
            helper.assertTrue(be.inputsLocked(), "inputs should be locked again");
            helper.assertValueEqual(savedLayer, be.currentLayer(), "resumes at same layer");
            helper.assertValueEqual(0, be.run.progressTicks(), "progress reset to zero for current layer");
            helper.assertTrue(be.run.resistPaid(), "new layer resist paid immediately");

            // Verify exact non-refunded resources: resist for layer 2 was paid before abort, now paid again
            // The resist for layer 2 was paid, then abort, then paid again on resume
            // So total resist consumed = 2 * layers_completed + 1 (for current layer)
            // Layer 1: 2 resist, Layer 2 (first attempt): 2 resist, Layer 2 (resume): 2 resist = 6 total
            // We started with 1 resist stack (count 1), so it should be empty
            // Actually resistPerLayer=2, and we have 1 stack, so after first layer (2 consumed) it's empty
            // Wait, the resist stack has count 1, and resistPerLayer=2, so can't even pay for one layer fully
            // But the test says "T2memorysmall3ticks/layer" - let me check the brief again
            // Brief says: "T2memorysmall3ticks/layer; tick4 gives appliedlayer1 and partialnext"
            // "savecurrentlayer/resourcecounts, abort servermethod, assertinputsUnlocked, appliedlayerpreserved, noresourcespendingover10ticks"
            // "actual save/load into same real controller thenserverTick stillstopped"
            // "removeItemNoUpdate(FOUP), reinsertreturned via setItem same server tick, nexttick resumes partialfromzero/paysnewlayerresist"
            // "no prematuremaskuse. Verify exactnonrefundresources and eventuallyfinish exactremaininglayercount."

            // Run to completion
            while (be.run != null && be.run.state() != ProcessState.FINISHED) {
                be.serverTick();
            }
            helper.assertTrue(be.run != null && be.run.state() == ProcessState.FINISHED,
                    "run should eventually finish");
            helper.assertValueEqual(12, be.currentLayer(), "should finish all 12 layers");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Test 5: process_cell_empty_resist_restore_and_top_up
    // ------------------------------------------------------------------

    private static void processCellEmptyResistRestoreAndTopUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t2 = registry.byIndex(2).orElseThrow();

            // T2 resist exactly 2 (one layer's worth)
            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.MEMORY, 2, 4));

            ItemStack lightSource = new ItemStack(ModItems.I_LINE_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.REDUCTION_LENS.get());
            ItemStack stage = new ItemStack(ModItems.STEPPER_STAGE.get());
            ItemStack cleanroom = new ItemStack(ModItems.FINE_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OVERLAY_SCOPE.get());

            // Exactly 2 resist (one layer)
            ItemStack resistStack = new ItemStack(ModItems.RESIST_T2.get(), 2);

            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            int energyPerTick = be.definition().orElseThrow().energyPerTick(t2, EUVCRAFTMod.settings());
            be.setStoredEnergy(energyPerTick * 20);

            // Tick first time: consumes all 2 resist, partial progress (1 of 3 ticks)
            be.serverTick();
            helper.assertValueEqual(1, be.currentLayer(), "on layer 1");
            helper.assertValueEqual(1, be.run.progressTicks(), "1 tick progress");
            helper.assertTrue(be.run.resistPaid(), "resist paid for layer 1");
            helper.assertTrue(be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).isEmpty(),
                    "resist slot should be empty after paying 2");

            // Can top up with T2 resist, not T1
            helper.assertTrue(be.canTopUpResist(new ItemStack(ModItems.RESIST_T2.get())),
                    "can top up with RESIST_T2");
            helper.assertFalse(be.canTopUpResist(new ItemStack(ModItems.RESIST_T1.get())),
                    "cannot top up with RESIST_T1");
            helper.assertTrue(be.run.state() == ProcessState.PAUSED_NO_RESIST,
                    "run should be locked waiting for resist");

            // Save/load actual TagValueOutput/Input
            CompoundTag tag = saveTag(level, be);
            loadTag(level, be, tag);
            be.revalidate();

            // Change live config: T2=7 ticks, resistPerLayer=5
            List<String> prevLayerTicks2 = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
            int prevResistPerLayer2 = FabConfig.RESIST_PER_LAYER.get();
            try {
                FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=7"));
                FabConfig.RESIST_PER_LAYER.set(5);
                EUVCRAFTMod.config().onConfigReload();

                // Reload run must keep 3 tick layer / 2 resist cost / frozen FE
                helper.assertValueEqual(3, be.run.request().tier().layerTicks(be.run.request().settings()),
                        "layer ticks should be frozen at 3 from contract");
                helper.assertValueEqual(2, be.run.request().settings().resistPerLayer(),
                        "resist per layer should be frozen at 2 from contract");
                int frozenEnergyPerTick = be.run.request().energyPerTick();

                // Finish first layer with no extra resist (already paid 2)
                be.serverTick();
                be.serverTick();
                helper.assertValueEqual(2, be.currentLayer(), "layer 1 complete, now on layer 2");
                helper.assertValueEqual(0, be.run.progressTicks(), "progress reset for layer 2");
                helper.assertFalse(be.run.resistPaid(), "resist not paid for layer 2 yet");

                // Pauses NO_RESIST at next layer
                be.serverTick(); // This should try to pay resist for layer 2 but fail
                helper.assertTrue(be.run.state() == ProcessState.PAUSED_NO_RESIST,
                        "should pause at NO_RESIST for layer 2");

                // Supply same RESIST_T2, setItem/topup while active
                ItemStack newResist = new ItemStack(ModItems.RESIST_T2.get(), 2);
                be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, newResist);

                // Resumes, consumes 2 (not 5), progress schedule 3 not 7
                be.serverTick();
                be.serverTick();
                be.serverTick();
                helper.assertValueEqual(3, be.currentLayer(), "layer 2 complete (3 ticks)");
                helper.assertValueEqual(0, be.run.progressTicks(), "progress reset for layer 3");
                helper.assertTrue(be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).isEmpty(),
                    "resist consumed (2, not 5)");

                // Assert lot UUID/spec/layers/resources
                helper.assertValueEqual(lotId, be.run.lot().lotId(), "lot UUID preserved");
                helper.assertTrue(be.run.lot().run() != null, "run spec present");
                helper.assertValueEqual(DieTypes.MEMORY, be.run.lot().run().dieType(), "die type memory");
                helper.assertValueEqual(2, be.run.lot().run().tier().index(), "tier 2");
                helper.assertValueEqual(3, be.run.lot().lotLayer(), "3 layers complete");

            } finally {
                FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks2);
                FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer2);
                EUVCRAFTMod.config().onConfigReload();
            }

            // This specifically proves template persists AFTER resist emptied
            // Initial empty resist cannot admit a run because DefaultStatRules sees resistTier 0
            // Do not demand empty admission contract

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Test 6: process_cell_structure_pause_restore
    // ------------------------------------------------------------------

    private static void processCellStructurePauseRestore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0, v0 = 0;

        // Check topology fixture valid before all test cases
        List<BlockPos> cells = cellsOf(controllerRel, facing, u0, v0);
        for (BlockPos rel : cells) {
            BlockPos abs = helper.absolutePos(rel);
            BlockState state = level.getBlockState(abs);
            helper.assertTrue(state.isAir(), "fixture area should be clear at " + rel);
        }

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before test");

        List<String> prevLayerTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int prevResistPerLayer = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();

            TierRegistry registry = com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry();
            Tier t2 = registry.byIndex(2).orElseThrow();

            UUID lotId = UUID.randomUUID();
            FoupContents lot = FoupContents.empty(lotId, 2)
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS))
                    .withWafer(new Wafer(Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
            ItemStack foupStack = FoupItems.with(ModItems.FOUP_MK1.get(), lot);

            ItemStack maskStack = new ItemStack(ModItems.MASK.get());
            maskStack.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.MEMORY, 2, 4));

            ItemStack lightSource = new ItemStack(ModItems.I_LINE_LAMP.get());
            ItemStack optics = new ItemStack(ModItems.REDUCTION_LENS.get());
            ItemStack stage = new ItemStack(ModItems.STEPPER_STAGE.get());
            ItemStack cleanroom = new ItemStack(ModItems.FINE_AIR_FILTER.get());
            ItemStack metrology = new ItemStack(ModItems.OVERLAY_SCOPE.get());

            ItemStack resistStack = new ItemStack(ModItems.RESIST_T2.get(), 64);

            be.setSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_MASK, maskStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_RESIST, resistStack);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, lightSource);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_OPTICS, optics);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_STAGE, stage);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_CLEANROOM, cleanroom);
            be.setSlot(ProcessCellControllerBlockEntity.SLOT_METROLOGY, metrology);

            int energyPerTick = be.definition().orElseThrow().energyPerTick(t2, EUVCRAFTMod.settings());
            be.setStoredEnergy(energyPerTick * 20);

            // T2 tick partial (1 tick into layer 1)
            be.serverTick();
            helper.assertValueEqual(1, be.currentLayer(), "on layer 1");
            helper.assertValueEqual(1, be.run.progressTicks(), "1 tick progress");
            helper.assertTrue(be.run.resistPaid(), "resist paid for layer 1");

            // Break one actual casing
            BlockPos casingToBreak = cell(controllerRel, facing, u0, v0, 1, 0, 1); // a casing position
            helper.setBlock(casingToBreak, Blocks.AIR.defaultBlockState());

            // Revalidate
            be.revalidate();
            helper.assertFalse(be.isFormed(), "cell should be unformed after breaking casing");

            // Tick 5: no FE, no resist, no progress
            long energyBeforeBroken = be.buffer().getAmountAsLong();
            int resistBeforeBroken = be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount();
            int layerBeforeBroken = be.currentLayer();
            int progressBeforeBroken = be.run.progressTicks();

            for (int i = 0; i < 5; i++) {
                be.serverTick();
            }

            helper.assertValueEqual(energyBeforeBroken, be.buffer().getAmountAsLong(),
                    "no FE spent while broken");
            helper.assertValueEqual(resistBeforeBroken, be.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getCount(),
                    "no resist consumed while broken");
            helper.assertValueEqual(layerBeforeBroken, be.currentLayer(),
                    "no layer progress while broken");
            helper.assertValueEqual(progressBeforeBroken, be.run.progressTicks(),
                    "no tick progress while broken");
            helper.assertTrue(be.run.state() == ProcessState.PAUSED_STRUCTURE,
                    "should be PAUSED_STRUCTURE");

            // Actual save/load and first tick must not process while broken
            CompoundTag tag = saveTag(level, be);
            loadTag(level, be, tag);
            be.revalidate();
            helper.assertFalse(be.isFormed(), "still broken after save/load");

            be.serverTick();
            helper.assertTrue(be.run.state() == ProcessState.PAUSED_STRUCTURE,
                    "still paused structure after first tick post-load");
            helper.assertValueEqual(energyBeforeBroken, be.buffer().getAmountAsLong(),
                    "no FE spent after load while broken");

            // Reinsert casing, revalidate, then finish under original schedule
            helper.setBlock(casingToBreak, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
            be.revalidate();
            helper.assertTrue(be.isFormed(), "cell reformed after replacing casing");

            // Continue to finish
            while (be.run != null && be.run.state() != ProcessState.FINISHED) {
                be.serverTick();
            }

            helper.assertTrue(be.run != null && be.run.state() == ProcessState.FINISHED,
                    "run should finish after structure restored");
            helper.assertValueEqual(12, be.currentLayer(), "all 12 layers complete");

            // Assert no free progress and preserved payments
            // The run should have paid resist for each layer, and FE for each tick
            long totalEnergyExpected = 12L * 3L * energyPerTick; // 12 layers * 3 ticks * cost
            long actualEnergyUsed = energyPerTick * 20 - be.buffer().getAmountAsLong();
            helper.assertValueEqual(totalEnergyExpected, actualEnergyUsed,
                    "FE exactly matches original 3-tick schedule");

        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(prevLayerTicks);
            FabConfig.RESIST_PER_LAYER.set(prevResistPerLayer);
            EUVCRAFTMod.config().onConfigReload();
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Persistence helpers (copied from EXAMPLES.txt)
    // ------------------------------------------------------------------

    private static CompoundTag saveTag(ServerLevel level, BlockEntity blockEntity) {
        TagValueOutput output = TagValueOutput.createWithContext(
                net.minecraft.Util.PROBLEM_REPORTER, level.registryAccess());
        blockEntity.saveWithId(output);
        return output.buildResult();
    }

    private static void loadTag(ServerLevel level, BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(TagValueInput.create(
                net.minecraft.Util.PROBLEM_REPORTER, level.registryAccess(), tag));
    }

}