package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

/** Actual server controller tests; all configuration changes are restored synchronously. */
public final class ProcessCellGameTests {
    private static final String LOGIC_T1_CONSERVATION = "process_cell_logic_t1_conservation";
    private static final String MEMORY_T2_CONSERVATION = "process_cell_memory_t2_conservation";
    private static final String WEAK_LINE_REFUSED = "process_cell_weak_line_refused";
    private static final String ABORT_LATCH_ROUND_TRIP = "process_cell_abort_latch_round_trip";
    private static final String EMPTY_RESIST_RESTORE_AND_TOP_UP = "process_cell_empty_resist_restore_and_top_up";
    private static final String STRUCTURE_PAUSE_RESTORE = "process_cell_structure_pause_restore";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");
    static {
        FUNCTIONS.register(LOGIC_T1_CONSERVATION, () -> ProcessCellGameTests::logicT1);
        FUNCTIONS.register(MEMORY_T2_CONSERVATION, () -> ProcessCellGameTests::memoryT2);
        FUNCTIONS.register(WEAK_LINE_REFUSED, () -> ProcessCellGameTests::weakLine);
        FUNCTIONS.register(ABORT_LATCH_ROUND_TRIP, () -> ProcessCellGameTests::abortLatch);
        FUNCTIONS.register(EMPTY_RESIST_RESTORE_AND_TOP_UP, () -> ProcessCellGameTests::emptyResist);
        FUNCTIONS.register(STRUCTURE_PAUSE_RESTORE, () -> ProcessCellGameTests::structurePause);
    }
    private ProcessCellGameTests() {}
    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((Consumer<RegisterGameTestsEvent>) ProcessCellGameTests::registerTests);
    }
    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", "process_cell_runtime"), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(env, Level.OVERWORLD,
            Identifier.withDefaultNamespace("empty"), 200, 0, true, Rotation.NONE, false, 1, 1, false, 0);
        register(event, data, LOGIC_T1_CONSERVATION);
        register(event, data, MEMORY_T2_CONSERVATION);
        register(event, data, WEAK_LINE_REFUSED);
        register(event, data, ABORT_LATCH_ROUND_TRIP);
        register(event, data, EMPTY_RESIST_RESTORE_AND_TOP_UP);
        register(event, data, STRUCTURE_PAUSE_RESTORE);
    }
    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    private record Fixture(ProcessCellControllerBlockEntity cell, UUID lotId, int cost) {}
    private static Fixture fixture(GameTestHelper h, int tier, int resistCount, int uses, boolean weak) {
        BlockPos controller = new BlockPos(1, 1, 1);
        // Independent explicit 2x2x3 north-facing box, with two back-face ports.
        for (int x = 1; x <= 2; x++) for (int y = 1; y <= 2; y++) for (int z = 1; z <= 3; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (p.equals(controller)) h.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
            else if (z == 3 && (x == 1 && y == 1 || x == 2 && y == 2))
                h.setBlock(p, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
            else h.setBlock(p, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        }
        var cell = (ProcessCellControllerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(controller));
        cell.revalidate();
        h.assertTrue(cell.isFormed() && cell.members().size() == 12, "real twelve-member cell forms");
        UUID id = UUID.randomUUID();
        FoupContents lot = new FoupContents(id, 2, List.of(new Wafer(0, 1.0f, 0), new Wafer(0, 1.0f, 0)));
        cell.setSlot(0, FoupItems.with(ModItems.FOUP_MK1.get(), lot));
        ItemStack mask = new ItemStack(ModItems.MASK.get());
        mask.set(ModDataComponents.MASK_DATA.get(), new MaskData(tier == 1 ? DieTypes.LOGIC : DieTypes.MEMORY, tier, uses));
        cell.setSlot(1, mask);
        cell.setSlot(2, new ItemStack(tier == 1 ? ModItems.RESIST_T1.get() : ModItems.RESIST_T2.get(), resistCount));
        boolean t1 = tier == 1 || weak;
        Item[] parts = t1 ? new Item[]{ModItems.MERCURY_LAMP.get(), ModItems.CONTACT_PLATE.get(),
            ModItems.MANUAL_ALIGNER.get(), ModItems.BASIC_AIR_FILTER.get(), ModItems.OPTICAL_MICROSCOPE.get()}
            : new Item[]{ModItems.I_LINE_LAMP.get(), ModItems.REDUCTION_LENS.get(),
                ModItems.STEPPER_STAGE.get(), ModItems.FINE_AIR_FILTER.get(), ModItems.OVERLAY_SCOPE.get()};
        for (int i = 0; i < 5; i++) cell.setSlot(3 + i, new ItemStack(parts[i]));
        cell.setStoredEnergy(10000);
        int cost = cell.definition().orElseThrow().energyPerTick(
            TierDataLoader.getRegistry().byIndex(tier).orElseThrow(), EUVCRAFTMod.settings());
        return new Fixture(cell, id, cost);
    }
    private static void settings(Runnable body) {
        List<String> oldTicks = new ArrayList<>(FabConfig.LAYER_TICKS_OVERRIDES.get());
        int oldResist = FabConfig.RESIST_PER_LAYER.get();
        try {
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=3", "T2=3"));
            FabConfig.RESIST_PER_LAYER.set(2);
            EUVCRAFTMod.config().onConfigReload();
            body.run();
        } finally {
            FabConfig.LAYER_TICKS_OVERRIDES.set(oldTicks);
            FabConfig.RESIST_PER_LAYER.set(oldResist);
            EUVCRAFTMod.config().onConfigReload();
        }
    }
    private static void tick(ProcessCellControllerBlockEntity cell, int count) {
        for (int i = 0; i < count; i++) cell.serverTick();
    }
    private static FoupContents lot(ProcessCellControllerBlockEntity cell) {
        return FoupItems.trustedContentsOf(cell.slot(0));
    }
    private static int resist(ProcessCellControllerBlockEntity cell) { return cell.slot(2).getCount(); }
    private static int uses(ProcessCellControllerBlockEntity cell) {
        MaskData mask = cell.slot(1).get(ModDataComponents.MASK_DATA.get());
        return mask == null ? 0 : mask.usesRemaining();
    }
    private static CompoundTag save(ServerLevel level, ProcessCellControllerBlockEntity cell) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        cell.saveWithId(out);
        return out.buildResult();
    }
    private static void load(ServerLevel level, ProcessCellControllerBlockEntity cell, CompoundTag tag) {
        cell.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }
    private static void completed(GameTestHelper h, Fixture f, int layer, String die) {
        FoupContents result = lot(f.cell());
        h.assertValueEqual(layer, result.lotLayer(), "physical applied layers");
        h.assertValueEqual(f.lotId(), result.lotId(), "same lot identity");
        h.assertValueEqual(2, result.size(), "two wafers retained");
        h.assertValueEqual(2, result.capacity(), "same capacity");
        h.assertTrue(result.run() != null, "frozen specification exists");
        h.assertValueEqual(die, result.run().dieType(), "frozen die type");
        h.assertValueEqual(layer == 1 ? 1 : 2, result.run().tierIndex(), "frozen tier");
        h.assertTrue(f.cell().processState() == ProcessState.FINISHED, "terminal state");
        h.assertFalse(f.cell().inputsLocked(), "finished inputs unlocked");
    }

    private static void logicT1(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 1, 64, 4, false);
            tick(f.cell(), 3);
            completed(h, f, 1, DieTypes.LOGIC);
            h.assertValueEqual(62, resist(f.cell()), "one layer costs two resist");
            h.assertValueEqual(10000L - 3L * f.cost(), f.cell().buffer().getAmountAsLong(), "three paid ticks");
            h.assertValueEqual(3, uses(f.cell()), "one mask use for whole run");
            tick(f.cell(), 10);
            h.assertValueEqual(10000L - 3L * f.cost(), f.cell().buffer().getAmountAsLong(), "no terminal payment");
            h.assertValueEqual(3, uses(f.cell()), "no repeated mask use");
        });
        h.succeed();
    }
    private static void memoryT2(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 1, false);
            tick(f.cell(), 36);
            completed(h, f, 12, DieTypes.MEMORY);
            h.assertValueEqual(40, resist(f.cell()), "twelve layers cost twenty-four resist");
            h.assertValueEqual(10000L - 36L * f.cost(), f.cell().buffer().getAmountAsLong(), "thirty-six paid ticks");
            h.assertTrue(f.cell().slot(1).isEmpty(), "exhausted mask removed");
            load(h.getLevel(), f.cell(), save(h.getLevel(), f.cell()));
            tick(f.cell(), 10);
            completed(h, f, 12, DieTypes.MEMORY);
            h.assertValueEqual(10000L - 36L * f.cost(), f.cell().buffer().getAmountAsLong(), "finished reload cannot replay payment");
            h.assertTrue(f.cell().slot(1).isEmpty(), "finished reload cannot restore spent mask");
        });
        h.succeed();
    }
    private static void weakLine(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, true);
            tick(f.cell(), 5);
            h.assertTrue(f.cell().lastRefusal() != null && f.cell().lastRefusal().is(RefusalReason.Code.LINE_CANNOT_RUN_TIER), "weak line refused");
            h.assertValueEqual(0, lot(f.cell()).lotLayer(), "no applied layer");
            h.assertValueEqual(10000L, f.cell().buffer().getAmountAsLong(), "no refused energy payment");
            h.assertValueEqual(64, resist(f.cell()), "no refused resist payment");
            h.assertValueEqual(4, uses(f.cell()), "no refused mask use");
        });
        h.succeed();
    }
    private static void abortLatch(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before abort");
            h.assertValueEqual(1, cell.progressTicks(), "one partial tick before abort");
            cell.abortRun();
            h.assertTrue(cell.aborted(), "Abort latches stopped");
            h.assertFalse(cell.inputsLocked(), "Abort permits FOUP removal");
            tick(cell, 10);
            load(h.getLevel(), cell, save(h.getLevel(), cell));
            tick(cell, 2);
            h.assertTrue(cell.aborted(), "Abort survives reload");
            h.assertValueEqual(1, lot(cell).lotLayer(), "Abort retains applied layer");
            h.assertValueEqual(60, resist(cell), "no resist refund or stopped payment");
            h.assertValueEqual(10000L - 4L * f.cost(), cell.buffer().getAmountAsLong(), "no energy refund or stopped payment");
            h.assertValueEqual(4, uses(cell), "Abort does not spend mask use");
            ItemStack same = cell.removeItemNoUpdate(0);
            cell.setItem(0, same); // removal and reinsertion between server ticks
            tick(cell, 1);
            h.assertFalse(cell.aborted(), "actual removal releases latch");
            h.assertValueEqual(1, cell.progressTicks(), "discarded partial layer starts from zero");
            h.assertValueEqual(58, resist(cell), "restart repays discarded layer resist");
            tick(cell, 32);
            completed(h, f, 12, DieTypes.MEMORY);
            h.assertValueEqual(38, resist(cell), "twenty-six resist including discarded partial layer");
            h.assertValueEqual(10000L - 37L * f.cost(), cell.buffer().getAmountAsLong(), "thirty-seven paid ticks including discarded tick");
            h.assertValueEqual(3, uses(cell), "only completed run spends mask use");
        });
        h.succeed();
    }
    private static void emptyResist(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 2, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.slot(2).isEmpty(), "initial layer empties resist slot");
            h.assertTrue(cell.inputsLocked(), "partial run locks inputs");
            h.assertTrue(cell.canTopUpResist(new ItemStack(ModItems.RESIST_T2.get())), "same-type top-up survives empty slot");
            h.assertFalse(cell.canTopUpResist(new ItemStack(ModItems.RESIST_T1.get())), "wrong resist rejected");
            CompoundTag tag = save(h.getLevel(), cell);
            FabConfig.LAYER_TICKS_OVERRIDES.set(List.of("T1=7", "T2=7"));
            FabConfig.RESIST_PER_LAYER.set(5);
            EUVCRAFTMod.config().onConfigReload();
            load(h.getLevel(), cell, tag);
            tick(cell, 2);
            h.assertValueEqual(1, lot(cell).lotLayer(), "saved three-tick schedule survives seven-tick reload");
            h.assertValueEqual(10000L - 3L * f.cost(), cell.buffer().getAmountAsLong(), "paid progress restored exactly");
            tick(cell, 1);
            h.assertTrue(cell.processState() == ProcessState.PAUSED_NO_RESIST, "next layer pauses for resist");
            h.assertTrue(cell.canTopUpResist(new ItemStack(ModItems.RESIST_T2.get())), "saved template retains same-type top-up");
            h.assertFalse(cell.canTopUpResist(new ItemStack(ModItems.RESIST_T1.get())), "saved template rejects other type");
            cell.setItem(2, new ItemStack(ModItems.RESIST_T2.get(), 64));
            tick(cell, 3);
            h.assertValueEqual(2, lot(cell).lotLayer(), "top-up completes next three-tick layer");
            h.assertValueEqual(62, resist(cell), "saved two-resist cost survives five-resist reload");
            h.assertValueEqual(10000L - 6L * f.cost(), cell.buffer().getAmountAsLong(), "no free or duplicate paid ticks on resume");
            h.assertValueEqual(f.lotId(), lot(cell).lotId(), "same lot after resume");
        });
        h.succeed();
    }
    private static void structurePause(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            BlockPos casing = new BlockPos(2, 1, 1);
            h.setBlock(casing, Blocks.AIR.defaultBlockState());
            cell.revalidate();
            tick(cell, 5);
            h.assertFalse(cell.isFormed(), "broken structure unforms");
            h.assertTrue(cell.processState() == ProcessState.PAUSED_STRUCTURE, "run pauses for broken structure");
            h.assertValueEqual(1, cell.progressTicks(), "pause preserves partial tick");
            h.assertValueEqual(62, resist(cell), "pause preserves paid layer resist");
            h.assertValueEqual(10000L - f.cost(), cell.buffer().getAmountAsLong(), "broken structure spends no energy");
            load(h.getLevel(), cell, save(h.getLevel(), cell));
            tick(cell, 1);
            h.assertTrue(cell.processState() == ProcessState.PAUSED_STRUCTURE, "reload revalidates broken world before processing");
            h.assertValueEqual(1, cell.progressTicks(), "broken reload preserves progress");
            h.setBlock(casing, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
            cell.revalidate();
            tick(cell, 35);
            completed(h, f, 12, DieTypes.MEMORY);
            h.assertValueEqual(40, resist(cell), "exact resist after reforming");
            h.assertValueEqual(10000L - 36L * f.cost(), cell.buffer().getAmountAsLong(), "exact energy after reforming");
        });
        h.succeed();
    }
}
