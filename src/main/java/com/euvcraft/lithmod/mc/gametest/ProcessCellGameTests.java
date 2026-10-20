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
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;
import com.euvcraft.lithmod.mc.machine.process.OperatingPanelMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.UnaryOperator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
    // M1-09 new tests
    private static final String MENU_ACTIVE_LOCKS = "process_cell_menu_active_locks";
    private static final String MENU_EMPTY_RESIST_TOP_UP = "process_cell_menu_empty_resist_top_up";
    private static final String ABORT_BUTTON_VALIDATION = "process_cell_abort_button_validation";
    private static final String MENU_PUBLIC_WIRE = "process_cell_menu_public_wire";
    private static final String BLOCK_USE_OPENS_MENU = "process_cell_block_use_opens_menu";
    private static final String DEFINITION_PAUSE_RESTORE = "process_cell_definition_pause_restore";
    private static final String MALFORMED_STATE_STAYS_STOPPED = "process_cell_malformed_state_stays_stopped";
    private static final String ACTIVE_INPUT_TAMPER_STOPS = "process_cell_active_input_tamper_stops";
    private static final String PARTIAL_BREAK_CONSERVES_INVENTORY = "process_cell_partial_break_conserves_inventory";
    private static final String HAND_FED_CAPABILITIES = "process_cell_hand_fed_capabilities";
    // M1 Operator Panels new tests
    private static final String OPERATOR_PANEL_BLOCK_USE = "operator_panel_block_use";
    private static final String OPERATOR_PANEL_MENU_OPEN = "operator_panel_menu_open";
    private static final String OPERATOR_PANEL_SLOT_MAPPING = "operator_panel_slot_mapping";
    private static final String OPERATOR_PANEL_EQUIPMENT_RETAINS = "operator_panel_equipment_retains";
    private static final String OPERATOR_PANEL_NO_HIDDEN_ACCESS = "operator_panel_no_hidden_access";
    private static final String OPERATOR_PANEL_STAGE_NO_BYPASS = "operator_panel_stage_no_bypass";
    private static final String OPERATOR_PANEL_PLAYER_SLOTS_USABLE = "operator_panel_player_slots_usable";
    private static final String OPERATOR_PANEL_NO_LOWER_ABORT = "operator_panel_no_lower_abort";
    private static final String OPERATOR_PANEL_UPPER_ABORT_VALID = "operator_panel_upper_abort_valid";
    private static final String OPERATOR_PANEL_INVALID_AFTER_REMOVAL = "operator_panel_invalid_after_removal";
    private static final String OPERATOR_PANEL_ADJACENT_CONTROLLER = "operator_panel_adjacent_controller";
    private static final String OPERATOR_PANEL_MISSING_REFORMS = "operator_panel_missing_reforms";
    private static final String OPERATOR_PANEL_SAVE_RELOAD = "operator_panel_save_reload";
    private static final String OPERATOR_PANEL_UPPER_NO_INVENTORY = "operator_panel_upper_no_inventory";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");
    static {
        FUNCTIONS.register(LOGIC_T1_CONSERVATION, () -> ProcessCellGameTests::logicT1);
        FUNCTIONS.register(MEMORY_T2_CONSERVATION, () -> ProcessCellGameTests::memoryT2);
        FUNCTIONS.register(WEAK_LINE_REFUSED, () -> ProcessCellGameTests::weakLine);
        FUNCTIONS.register(ABORT_LATCH_ROUND_TRIP, () -> ProcessCellGameTests::abortLatch);
        FUNCTIONS.register(EMPTY_RESIST_RESTORE_AND_TOP_UP, () -> ProcessCellGameTests::emptyResist);
        FUNCTIONS.register(STRUCTURE_PAUSE_RESTORE, () -> ProcessCellGameTests::structurePause);
        FUNCTIONS.register(MENU_ACTIVE_LOCKS, () -> ProcessCellGameTests::menuActiveLocks);
        FUNCTIONS.register(MENU_EMPTY_RESIST_TOP_UP, () -> ProcessCellGameTests::menuEmptyResistTopUp);
        FUNCTIONS.register(ABORT_BUTTON_VALIDATION, () -> ProcessCellGameTests::abortButtonValidation);
        FUNCTIONS.register(MENU_PUBLIC_WIRE, () -> ProcessCellGameTests::menuPublicWire);
        FUNCTIONS.register(DEFINITION_PAUSE_RESTORE, () -> ProcessCellGameTests::definitionPauseRestore);
        FUNCTIONS.register(MALFORMED_STATE_STAYS_STOPPED, () -> ProcessCellGameTests::malformedStateStaysStopped);
        FUNCTIONS.register(ACTIVE_INPUT_TAMPER_STOPS, () -> ProcessCellGameTests::activeInputTamperStops);
        FUNCTIONS.register(PARTIAL_BREAK_CONSERVES_INVENTORY, () -> ProcessCellGameTests::partialBreakConservesInventory);
        FUNCTIONS.register(HAND_FED_CAPABILITIES, () -> ProcessCellGameTests::handFedCapabilities);
        FUNCTIONS.register(BLOCK_USE_OPENS_MENU, () -> ProcessCellGameTests::blockUseOpensMenu);
        // M1 Operator Panels
        FUNCTIONS.register(OPERATOR_PANEL_BLOCK_USE, () -> ProcessCellGameTests::operatorPanelBlockUse);
        FUNCTIONS.register(OPERATOR_PANEL_MENU_OPEN, () -> ProcessCellGameTests::operatorPanelMenuOpen);
        FUNCTIONS.register(OPERATOR_PANEL_SLOT_MAPPING, () -> ProcessCellGameTests::operatorPanelSlotMapping);
        FUNCTIONS.register(OPERATOR_PANEL_EQUIPMENT_RETAINS, () -> ProcessCellGameTests::operatorPanelEquipmentRetains);
        FUNCTIONS.register(OPERATOR_PANEL_NO_HIDDEN_ACCESS, () -> ProcessCellGameTests::operatorPanelNoHiddenAccess);
        FUNCTIONS.register(OPERATOR_PANEL_STAGE_NO_BYPASS, () -> ProcessCellGameTests::operatorPanelStageNoBypass);
        FUNCTIONS.register(OPERATOR_PANEL_PLAYER_SLOTS_USABLE, () -> ProcessCellGameTests::operatorPanelPlayerSlotsUsable);
        FUNCTIONS.register(OPERATOR_PANEL_NO_LOWER_ABORT, () -> ProcessCellGameTests::operatorPanelNoLowerAbort);
        FUNCTIONS.register(OPERATOR_PANEL_UPPER_ABORT_VALID, () -> ProcessCellGameTests::operatorPanelUpperAbortValid);
        FUNCTIONS.register(OPERATOR_PANEL_INVALID_AFTER_REMOVAL, () -> ProcessCellGameTests::operatorPanelInvalidAfterRemoval);
        FUNCTIONS.register(OPERATOR_PANEL_ADJACENT_CONTROLLER, () -> ProcessCellGameTests::operatorPanelAdjacentController);
        FUNCTIONS.register(OPERATOR_PANEL_MISSING_REFORMS, () -> ProcessCellGameTests::operatorPanelMissingReforms);
        FUNCTIONS.register(OPERATOR_PANEL_SAVE_RELOAD, () -> ProcessCellGameTests::operatorPanelSaveReload);
        FUNCTIONS.register(OPERATOR_PANEL_UPPER_NO_INVENTORY, () -> ProcessCellGameTests::operatorPanelUpperNoInventory);
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
        register(event, data, MENU_ACTIVE_LOCKS);
        register(event, data, MENU_EMPTY_RESIST_TOP_UP);
        register(event, data, ABORT_BUTTON_VALIDATION);
        register(event, data, MENU_PUBLIC_WIRE);
        register(event, data, DEFINITION_PAUSE_RESTORE);
        register(event, data, MALFORMED_STATE_STAYS_STOPPED);
        register(event, data, ACTIVE_INPUT_TAMPER_STOPS);
        register(event, data, PARTIAL_BREAK_CONSERVES_INVENTORY);
        register(event, data, HAND_FED_CAPABILITIES);
        register(event, data, BLOCK_USE_OPENS_MENU);
        // M1 Operator Panels
        register(event, data, OPERATOR_PANEL_BLOCK_USE);
        register(event, data, OPERATOR_PANEL_MENU_OPEN);
        register(event, data, OPERATOR_PANEL_SLOT_MAPPING);
        register(event, data, OPERATOR_PANEL_EQUIPMENT_RETAINS);
        register(event, data, OPERATOR_PANEL_NO_HIDDEN_ACCESS);
        register(event, data, OPERATOR_PANEL_STAGE_NO_BYPASS);
        register(event, data, OPERATOR_PANEL_PLAYER_SLOTS_USABLE);
        register(event, data, OPERATOR_PANEL_NO_LOWER_ABORT);
        register(event, data, OPERATOR_PANEL_UPPER_ABORT_VALID);
        register(event, data, OPERATOR_PANEL_INVALID_AFTER_REMOVAL);
        register(event, data, OPERATOR_PANEL_ADJACENT_CONTROLLER);
        register(event, data, OPERATOR_PANEL_MISSING_REFORMS);
        register(event, data, OPERATOR_PANEL_SAVE_RELOAD);
        register(event, data, OPERATOR_PANEL_UPPER_NO_INVENTORY);
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
        // Independent explicit 2x2x3 north-facing box, with two back-face ports and operating panel above controller.
        // Controller at bottom (y=1), operating panel at y=2 directly above, ports at z=3 back face.
        for (int x = 1; x <= 2; x++) for (int y = 1; y <= 2; y++) for (int z = 1; z <= 3; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (p.equals(controller)) h.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
            else if (x == 1 && y == 2 && z == 1)
                h.setBlock(p, ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
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

    // ------------------------------------------------------------------------
    // M1-09 new test cases
    // ------------------------------------------------------------------------

    /**
     * Connects a mock ServerPlayer to the level for menu interaction tests.
     */
    @SuppressWarnings("unchecked")
    private static List<DataSlot> syncedData(AbstractContainerMenu menu) {
        try {
            java.lang.reflect.Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
            field.setAccessible(true);
            return (List<DataSlot>) field.get(menu);
        } catch (ReflectiveOperationException invalid) {
            throw new AssertionError("Cannot inspect registered menu wire values", invalid);
        }
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "process-cell-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getAbilities().instabuild = true;
        return player;
    }

    /**
     * Case process_cell_menu_active_locks:
     * fixture T2 resist 8 => tick 1 leaves 6, active.
     * Assert all 8 machineSlot.mayPickup(player) false; FOUP/mask/components mayPlace false.
     * Real clicked FOUP PICKUP with empty cursor must keep FOUP and cursor empty.
     * quickMoveStack(player, 0) empty and FOUP retained.
     * Set player hotbar via menu.slots.get(hotbarIndex).set(new ItemStack(Items.STONE)), clicked(0, 0, SWAP),
     * assert FOUP retained/hotbar stone; clicked(0, 0, THROW), clicked(0, 2, CLONE) must not remove/duplicate FOUP.
     * Real normal resist top-up: menu.setCarried(new ItemStack(RESIST_T2, 3)); menu.clicked(2, 0, PICKUP, player);
     * assert resist 9, cursor empty. Right-click 2 more adds one, cursor 1.
     * Wrong T1 cursor 3 rejected, resist unchanged. Empty cursor PICKUP resist cannot extract.
     * Player-to-machine shift: set slot 8 to RESIST_T2 stack 2; quickMoveStack(player, 8) adds 2 to existing resist, player stack empties.
     * Direct quickMoveStack(player, 2) cannot extract.
     * PICKUP_ALL with carried T2 count 1 cannot collect from locked resist slot; assert machine resist unchanged
     * (clear other player resist stack first).
     * Use physical FOUP UUID/item/count and cursor count assertions, not only slot predicates.
     * Test BOTH lower (5 visible) and upper (3 visible) menus with correct hotbar indices:
     * lower hotbar starts at 5+27=32, upper hotbar starts at 3+27=30.
     */
    private static void menuActiveLocks(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 8, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertValueEqual(6, resist(cell), "one layer costs two resist, leaves six");
            h.assertTrue(cell.inputsLocked(), "cell is active with inputs locked");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            BlockPos panelPos = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // ===== TEST LOWER MENU (ProcessCellMenu - 5 visible equipment slots) =====
            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            player.containerMenu = lowerMenu;

            // Prove the creative fixture can clone an ordinary player-inventory item.
            h.assertTrue(player.getAbilities().instabuild, "creative clone precondition");
            // Lower menu: player slots start at 5, hotbar at 5+27=32
            int lowerFirstHotbar = 5 + 27;
            lowerMenu.slots.get(lowerFirstHotbar).set(new ItemStack(Items.STONE));
            lowerMenu.setCarried(ItemStack.EMPTY);
            lowerMenu.clicked(lowerFirstHotbar, 2, ContainerInput.CLONE, player);
            h.assertTrue(lowerMenu.getCarried().is(Items.STONE) && lowerMenu.getCarried().getCount() == 64,
                "creative clone works for the player's own inventory (lower)");
            lowerMenu.setCarried(ItemStack.EMPTY);
            lowerMenu.slots.get(lowerFirstHotbar).set(ItemStack.EMPTY);

            // All 5 lower visible machine slots mayPickup must be false
            for (int i = 0; i < 5; i++) {
                h.assertFalse(lowerMenu.slots.get(i).mayPickup(player), "lower machine slot " + i + " mayPickup must be false when locked");
            }
            // Lower visible slots are equipment (underlying 3..7), all mayPlace false when locked
            for (int i = 0; i < 5; i++) {
                h.assertFalse(lowerMenu.slots.get(i).mayPlace(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i).copy()),
                    "locked lower machine slot " + i + " rejects its normally valid input");
            }

            // Real clicked FOUP PICKUP with empty cursor must keep FOUP and cursor empty
            // FOUP is NOT visible in lower menu! It's in upper menu only.
            // But we can test that the lower menu's machine slots are locked by trying to interact with them
            ItemStack foupBefore = cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).copy();
            UUID foupUuid = FoupItems.trustedContentsOf(foupBefore).lotId();
            
            // Test that lower menu equipment slots are locked
            ItemStack lightBefore = cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).copy();
            lowerMenu.setCarried(ItemStack.EMPTY);
            lowerMenu.clicked(0, 0, ContainerInput.PICKUP, player); // Lower visible 0 = LIGHT_SOURCE
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), lightBefore),
                "LIGHT_SOURCE must be retained after PICKUP with empty cursor");
            h.assertTrue(lowerMenu.getCarried().isEmpty(), "cursor must remain empty after PICKUP on locked equipment slot");

            // quickMoveStack(player, 0) empty and LIGHT_SOURCE retained
            ItemStack qmResult = lowerMenu.quickMoveStack(player, 0);
            h.assertTrue(qmResult.isEmpty(), "quickMoveStack on locked lower slot returns empty");
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), lightBefore),
                "LIGHT_SOURCE retained after quickMoveStack");

            // Set player hotbar via lower menu hotbar slot
            ItemStack replacementLight = new ItemStack(ModItems.I_LINE_LAMP.get());
            lowerMenu.slots.get(lowerFirstHotbar).set(replacementLight);
            // clicked(0, 0, SWAP) assert LIGHT_SOURCE retained/hotbar stone
            lowerMenu.clicked(0, 0, ContainerInput.SWAP, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), lightBefore),
                "LIGHT_SOURCE retained after SWAP");
            h.assertTrue(ItemStack.isSameItemSameComponents(lowerMenu.slots.get(lowerFirstHotbar).getItem(), replacementLight),
                "valid replacement LIGHT_SOURCE stays in hotbar after rejected SWAP");

            // clicked(0, 0, THROW) must not remove LIGHT_SOURCE
            lowerMenu.clicked(0, 0, ContainerInput.THROW, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), lightBefore),
                "LIGHT_SOURCE retained after THROW");
            h.assertTrue(lowerMenu.getCarried().isEmpty(), "cursor empty after THROW on locked slot");

            // clicked(0, 2, CLONE) must not duplicate LIGHT_SOURCE (creative middle-click)
            lowerMenu.clicked(0, 2, ContainerInput.CLONE, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), lightBefore),
                "LIGHT_SOURCE retained after CLONE");
            h.assertTrue(lowerMenu.getCarried().isEmpty(), "CLONE must leave the cursor empty in locked state");

            // ===== TEST UPPER MENU (OperatingPanelMenu - 3 visible consumable slots) =====
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;

            // Upper menu: player slots start at 3, hotbar at 3+27=30
            int upperFirstHotbar = 3 + 27;
            
            // All 3 upper visible machine slots mayPickup must be false
            for (int i = 0; i < 3; i++) {
                h.assertFalse(upperMenu.slots.get(i).mayPickup(player), "upper machine slot " + i + " mayPickup must be false when locked");
            }
            // FOUP (vis 0) and MASK (vis 1) mayPlace false when locked; RESIST (vis 2) mayPlace allowed for top-up
            h.assertFalse(upperMenu.slots.get(0).mayPlace(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).copy()),
                "locked upper FOUP slot rejects placement");
            h.assertFalse(upperMenu.slots.get(1).mayPlace(cell.slot(ProcessCellControllerBlockEntity.SLOT_MASK).copy()),
                "locked upper MASK slot rejects placement");
            // RESIST mayPlace is allowed for top-up when locked - test this separately

            // Real clicked FOUP PICKUP with empty cursor must keep FOUP and cursor empty
            foupBefore = cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).copy();
            foupUuid = FoupItems.trustedContentsOf(foupBefore).lotId();
            upperMenu.setCarried(ItemStack.EMPTY);
            upperMenu.clicked(0, 0, ContainerInput.PICKUP, player); // Upper visible 0 = FOUP
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), foupBefore),
                "FOUP must be retained after PICKUP with empty cursor (upper)");
            h.assertValueEqual(foupUuid, FoupItems.trustedContentsOf(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP)).lotId(),
                "FOUP lot UUID unchanged");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor must remain empty after PICKUP on locked FOUP");

            // quickMoveStack(player, 0) empty and FOUP retained
            qmResult = upperMenu.quickMoveStack(player, 0);
            h.assertTrue(qmResult.isEmpty(), "quickMoveStack on locked FOUP returns empty");
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), foupBefore),
                "FOUP retained after quickMoveStack");

            // Set player hotbar via upper menu hotbar slot
            ItemStack replacementFoup = FoupItems.with(ModItems.FOUP_MK2.get(),
                new FoupContents(UUID.randomUUID(), 2, List.of(new Wafer(0, 1.0f, 0))));
            upperMenu.slots.get(upperFirstHotbar).set(replacementFoup);
            // clicked(0, 0, SWAP) assert FOUP retained/hotbar stone
            upperMenu.clicked(0, 0, ContainerInput.SWAP, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), foupBefore),
                "FOUP retained after SWAP (upper)");
            h.assertTrue(ItemStack.isSameItemSameComponents(upperMenu.slots.get(upperFirstHotbar).getItem(), replacementFoup),
                "valid replacement FOUP stays in hotbar after rejected SWAP (upper)");

            // clicked(0, 0, THROW) must not remove FOUP
            upperMenu.clicked(0, 0, ContainerInput.THROW, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), foupBefore),
                "FOUP retained after THROW (upper)");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor empty after THROW on locked FOUP (upper)");

            // clicked(0, 2, CLONE) must not duplicate FOUP (creative middle-click)
            upperMenu.clicked(0, 2, ContainerInput.CLONE, player);
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), foupBefore),
                "FOUP retained after CLONE (upper)");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "CLONE must leave the cursor empty in locked state (upper)");

            // Real normal resist top-up: upperMenu.setCarried(new ItemStack(RESIST_T2, 3)); upperMenu.clicked(2, 0, PICKUP, player)
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 3));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player); // Upper visible 2 = RESIST
            h.assertValueEqual(9, resist(cell), "resist top-up adds 3 to existing 6 (upper)");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor empty after successful resist top-up (upper)");

            // Right-click 2 more adds one, cursor 1 (right-click places one from cursor)
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 2));
            upperMenu.clicked(2, 1, ContainerInput.PICKUP, player); // button=1 for right-click
            h.assertValueEqual(10, resist(cell), "right-click adds one more resist (upper)");
            h.assertValueEqual(1, upperMenu.getCarried().getCount(), "cursor has 1 left after right-click top-up (upper)");

            // Wrong T1 cursor 3 rejected, resist unchanged
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T1.get(), 3));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(10, resist(cell), "wrong T1 resist rejected, resist unchanged (upper)");
            h.assertValueEqual(3, upperMenu.getCarried().getCount(), "T1 cursor unchanged after rejection (upper)");

            // Empty cursor PICKUP resist cannot extract
            upperMenu.setCarried(ItemStack.EMPTY);
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(10, resist(cell), "empty cursor PICKUP cannot extract resist (upper)");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor remains empty (upper)");

            // Player-to-machine shift: set upper hotbar slot to RESIST_T2 stack 2; quickMoveStack adds 2 to existing resist
            upperMenu.slots.get(upperFirstHotbar).set(new ItemStack(ModItems.RESIST_T2.get(), 2));
            ItemStack shifted = upperMenu.quickMoveStack(player, upperFirstHotbar);
            h.assertTrue(!shifted.isEmpty() && shifted.is(ModItems.RESIST_T2.get()), "shift-click returns the moved stack copy (upper)");
            h.assertTrue(upperMenu.slots.get(upperFirstHotbar).getItem().isEmpty(), "shift-click empties the player source slot (upper)");
            h.assertValueEqual(12, resist(cell), "shift-click adds 2 resist to machine (upper)");

            // Direct quickMoveStack(player, 2) cannot extract (resist slot is visible index 2 in upper)
            upperMenu.setCarried(ItemStack.EMPTY);
            ItemStack directShift = upperMenu.quickMoveStack(player, 2);
            h.assertTrue(directShift.isEmpty(), "quickMoveStack on locked resist slot cannot extract (upper)");
            h.assertValueEqual(12, resist(cell), "resist unchanged after failed extraction (upper)");

            // PICKUP_ALL with carried T2 count 1 cannot collect from locked resist slot
            // First clear other player resist stacks
            for (int i = upperFirstHotbar; i < upperFirstHotbar + 9; i++) { // hotbar only
                if (upperMenu.slots.get(i).getItem().is(ModItems.RESIST_T2.get())) {
                    upperMenu.slots.get(i).set(ItemStack.EMPTY);
                }
            }
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 1));
            h.assertFalse(upperMenu.canTakeItemForPickAll(upperMenu.getCarried(), upperMenu.slots.get(2)),
                "PICKUP_ALL predicate protects the active resist slot (upper)");
            upperMenu.clicked(2, 0, ContainerInput.PICKUP_ALL, player);
            h.assertValueEqual(12, resist(cell), "real PICKUP_ALL cannot extract active resist (upper)");
            h.assertValueEqual(1, upperMenu.getCarried().getCount(), "real PICKUP_ALL leaves the cursor count unchanged (upper)");

            // Verify physical FOUP UUID/item/count and cursor count assertions
            ItemStack finalFoup = cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP);
            h.assertValueEqual(foupUuid, FoupItems.trustedContentsOf(finalFoup).lotId(), "final FOUP UUID matches");
            h.assertTrue(finalFoup.is(ModItems.FOUP_MK1.get()), "final FOUP is correct item");
            h.assertValueEqual(1, finalFoup.getCount(), "final FOUP count is 1");
        });
        h.succeed();
    }

    /**
     * Case process_cell_menu_empty_resist_top_up:
     * fixture T2 resist 2 => tick 1 empty slot. Actual server menu remains locked,
     * resistSlot.mayPlace T2 true / T1 false, mayPickup false.
     * Resist is ONLY visible in upper menu (OperatingPanelMenu) at visible index 2.
     * Wrong T1 cursor PICKUP rejected empty; same T2 cursor count 3 normal PICKUP fills 3 and cursor empty.
     * Tick 2 completes first layer without paying resist again, tick 1 next layer consumes 2 leaves 1;
     * verify layer 1, active, original UUID. Normal pickup from empty or filled active slot cannot extract.
     * Preserve exact resist counts.
     * Test using UPPER menu since resist is only visible there.
     */
    private static void menuEmptyResistTopUp(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 2, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).isEmpty(), "initial layer empties resist slot");
            h.assertTrue(cell.inputsLocked(), "partial run locks inputs");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            // Use UPPER menu since resist is only visible there (visible index 2)
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;

            Slot resistSlot = upperMenu.slots.get(2); // Upper visible 2 = RESIST (underlying 2)
            // resistSlot.mayPlace T2 true / T1 false
            h.assertTrue(resistSlot.mayPlace(new ItemStack(ModItems.RESIST_T2.get())), "resist slot mayPlace T2 when locked and empty");
            h.assertFalse(resistSlot.mayPlace(new ItemStack(ModItems.RESIST_T1.get())), "resist slot rejects T1 when locked and empty");
            // mayPickup false
            h.assertFalse(resistSlot.mayPickup(player), "resist slot mayPickup false when locked");

            // Wrong T1 cursor PICKUP rejected empty
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T1.get(), 3));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertTrue(cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).isEmpty(), "T1 resist rejected, slot remains empty");
            h.assertValueEqual(3, upperMenu.getCarried().getCount(), "T1 cursor unchanged after rejection");

            // Same T2 cursor count 3 normal PICKUP fills 3 and cursor empty
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 3));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(3, resist(cell), "T2 resist top-up fills 3");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor empty after successful top-up");

            // Tick 2 completes first layer without paying resist again
            UUID originalLotId = f.lotId();
            tick(cell, 2);
            h.assertValueEqual(1, lot(cell).lotLayer(), "first layer completed");
            h.assertTrue(cell.inputsLocked(), "still active");
            h.assertValueEqual(originalLotId, lot(cell).lotId(), "original lot UUID preserved");
            h.assertValueEqual(3, resist(cell), "resist unchanged after completing layer (no double payment)");

            // Tick 1 next layer consumes 2 leaves 1
            tick(cell, 1);
            h.assertValueEqual(1, resist(cell), "second layer consumes 2 resist, leaves 1");

            // Normal pickup from empty or filled active slot cannot extract
            upperMenu.setCarried(ItemStack.EMPTY);
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(1, resist(cell), "empty cursor PICKUP cannot extract from filled resist slot");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor remains empty");

            // Try to extract with PICKUP when resist is present
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 1));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(2, resist(cell), "same-type cursor adds one to the occupied active slot");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "same-type top-up consumes exactly the cursor item");
        });
        h.succeed();
    }

    /**
     * Case process_cell_abort_button_validation:
     * fixture T2 resist 64 tick 1, real menu + connected player.
     * Wrong id 1 false/no abort; player.containerMenu = player.inventoryMenu false/no abort;
     * restore pointer, move 20 blocks away false/no abort; return near.
     * Construct client-style Menu(id, inventory, (FabMachineBlockEntity)null), set pointer clientMenu,
     * clickMenuButton must false / client handler cannot abort real controller.
     * Restore pointer serverMenu and correct near id 0 true, cell.aborted true, inputs unlocked;
     * second click false / no active. Verify applied layers / FE / resist no premature refund.
     * Same FOUP remove/reinsert + tick reactivates; destroy actual controller and assert staleMenu
     * stillValid false / clickMenuButton false. Include wrong world check only if actual API readily available;
     * no fabricated ClientLevel.
     * ABORT_BUTTON only works on OperatingPanelMenu (upper), ProcessCellMenu (lower) returns false.
     */
    private static void abortButtonValidation(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before abort test");
            h.assertTrue(cell.inputsLocked(), "inputs locked before abort");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // ===== Test LOWER menu (ProcessCellMenu) - should NOT have abort =====
            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            player.containerMenu = lowerMenu;

            // Lower menu is not operating panel, so clickMenuButton should return false for abort button
            h.assertFalse(lowerMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "lower menu clickMenuButton returns false (not operating panel)");
            h.assertFalse(cell.aborted(), "lower menu cannot abort the cell");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after lower menu abort attempt");

            // ===== Test UPPER menu (OperatingPanelMenu) - should have valid abort =====
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;

            // Wrong id 1 false/no abort
            h.assertFalse(upperMenu.clickMenuButton(player, 1), "wrong button id returns false");
            h.assertFalse(cell.aborted(), "wrong id does not abort");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after wrong id");

            // player.containerMenu = player.inventoryMenu false/no abort
            player.containerMenu = player.inventoryMenu;
            h.assertFalse(upperMenu.clickMenuButton(player, 0), "wrong containerMenu returns false");
            h.assertFalse(cell.aborted(), "wrong containerMenu does not abort");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after wrong containerMenu");

            // Restore pointer, move 20 blocks away false/no abort
            player.containerMenu = upperMenu;
            player.setPos(controllerPos.getX() + 20.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            h.assertFalse(upperMenu.clickMenuButton(player, 0), "too far returns false");
            h.assertFalse(cell.aborted(), "too far does not abort");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after distance check");

            // Return near
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // Matching coordinates in another dimension must still be rejected.
            ServerLevel otherLevel = h.getLevel().getServer().getLevel(Level.NETHER);
            h.assertTrue(otherLevel != null, "other dimension exists for wrong-world validation");
            CommonListenerCookie otherCookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "wrong-world-process"), false);
            ServerPlayer otherPlayer = new ServerPlayer(h.getLevel().getServer(), otherLevel,
                otherCookie.gameProfile(), otherCookie.clientInformation());
            otherPlayer.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            otherPlayer.containerMenu = upperMenu;
            h.assertFalse(upperMenu.clickMenuButton(otherPlayer, 0), "wrong-world player cannot abort");
            h.assertFalse(cell.aborted(), "wrong-world request leaves the run active");

            // Construct client-style Menu(id, inventory, (FabMachineBlockEntity)null), set pointer clientMenu
            ProcessCellMenu clientMenu = new ProcessCellMenu(8, player.getInventory(), (ProcessCellControllerBlockEntity) null);
            player.containerMenu = clientMenu;
            // clickMenuButton must false / client handler cannot abort real controller
            h.assertFalse(clientMenu.clickMenuButton(player, 0), "client-side menu clickMenuButton returns false");
            h.assertFalse(cell.aborted(), "client menu cannot abort real controller");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after client menu click");

            // Restore pointer upperMenu and correct near id 0 true, cell.aborted true, inputs unlocked
            player.containerMenu = upperMenu;
            h.assertTrue(upperMenu.clickMenuButton(player, 0), "correct upper menu near player aborts");
            h.assertTrue(cell.aborted(), "abort latches stopped");
            h.assertFalse(cell.inputsLocked(), "abort permits FOUP removal");

            // Second click false / no active
            h.assertFalse(upperMenu.clickMenuButton(player, 0), "second click on aborted cell returns false");
            h.assertTrue(cell.aborted(), "abort remains latched");
            h.assertFalse(cell.inputsLocked(), "inputs remain unlocked");

            // Verify applied layers / FE / resist no premature refund
            h.assertValueEqual(1, lot(cell).lotLayer(), "applied layer preserved");
            long expectedFE = 10000L - 4L * f.cost();
            h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE not refunded");
            h.assertValueEqual(60, resist(cell), "resist not refunded");

            // Same FOUP remove/reinsert + tick reactivates
            ItemStack foupStack = cell.removeItemNoUpdate(ProcessCellControllerBlockEntity.SLOT_FOUP);
            cell.setItem(ProcessCellControllerBlockEntity.SLOT_FOUP, foupStack);
            tick(cell, 1);
            h.assertFalse(cell.aborted(), "FOUP removal releases abort latch");
            h.assertValueEqual(1, cell.progressTicks(), "discarded partial layer starts from zero");
            h.assertValueEqual(58, resist(cell), "restart repays discarded layer resist");
            h.assertTrue(cell.inputsLocked(), "restarted run is active before stale-menu check");
            h.assertValueEqual(10000L - 5L * f.cost(), cell.buffer().getAmountAsLong(),
                "only the restarted paid tick changes energy");

            // Destroy actual controller and assert staleMenu stillValid false / clickMenuButton false
            h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
            cell.revalidate();
            h.assertFalse(upperMenu.stillValid(player), "stale menu stillValid false after controller destroyed");
            h.assertFalse(upperMenu.clickMenuButton(player, 0), "clickMenuButton false on stale menu");
            h.assertFalse(cell.aborted(), "stale menu cannot abort its still-active removed controller");
        });
        h.succeed();
    }

    /**
     * Case process_cell_menu_public_wire:
     * fixture T2 tick 1 active, actual server menu and client-style Menu(7, inventory, (FabMachineBlockEntity)null);
     * reflection AbstractContainerMenu.dataSlots list exactly 12 (base 4 + own 8) on both.
     * Server data values in actual registration order must equal known public tuple:
     * stored FE 10000-cost, capacity from definition, progress 1, ticks 3, status RUNNING ordinal 1,
     * refusal 0, layer 1, total 12, formed 1, tier 2, die DieTypes.ALL.indexOf(MEMORY)+1, locked 1.
     * Copy each serverDataSlot.get() into client.setData(index, value); assert client.inputsLocked true,
     * status RUNNING, tier 2, die Memory, layer 1/12.
     * Abort actual controller then copy again: client STATUS_ABORTED, unlocked.
     * No private wafer fields/data slots added. Do not instantiate screens/clientworld or claim visual verification.
     * Can assert language key coverage via reading actual en_us resource if helpful, but no extra asset edits.
     */
    private static void menuPublicWire(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.inputsLocked(), "cell active after one tick");
            h.assertValueEqual(1, cell.currentLayer(), "layer 1");
            h.assertValueEqual(12, cell.totalLayers(), "total 12 layers for T2 memory");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // Actual server menu
            ProcessCellMenu serverMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            // Client-style menu (null block entity)
            ProcessCellMenu clientMenu = new ProcessCellMenu(7, player.getInventory(), (ProcessCellControllerBlockEntity) null);

            // Reflection: AbstractContainerMenu.dataSlots list exactly 12 (base 4 + own 8) on both
            List<DataSlot> serverDataSlots = syncedData(serverMenu);
            List<DataSlot> clientDataSlots = syncedData(clientMenu);
            h.assertValueEqual(12, serverDataSlots.size(), "server menu has exactly 12 data slots (4 base + 8 own)");
            h.assertValueEqual(12, clientDataSlots.size(), "client menu has exactly 12 data slots (4 base + 8 own)");

            // Server data values in actual registration order must equal known public tuple
            // Base 4: DATA_ENERGY, DATA_CAPACITY, DATA_PROGRESS, DATA_TICKS
            // Own 8: DATA_STATUS, DATA_REFUSAL, DATA_LAYER, DATA_TOTAL, DATA_FORMED, DATA_TIER, DATA_DIE, DATA_LOCKED
            int storedFE = cell.buffer().getAmountAsInt();
            int capacity = cell.definition().orElseThrow().energyCapacity();
            int progress = cell.progressTicks();
            int ticks = cell.data().get(FabMachineBlockEntity.DATA_TICKS);
            // Expected values
            h.assertValueEqual(10000 - f.cost(), storedFE, "stored FE = 10000 - cost");
            h.assertValueEqual(1, progress, "progress = 1 tick");
            h.assertValueEqual(3, ticks, "ticks per layer = 3");

            // Read server data slots in order
            // Index 0: DATA_ENERGY
            h.assertValueEqual(storedFE, serverDataSlots.get(0).get(), "dataSlot[0] = stored FE");
            // Index 1: DATA_CAPACITY
            h.assertValueEqual(capacity, serverDataSlots.get(1).get(), "dataSlot[1] = capacity");
            // Index 2: DATA_PROGRESS
            h.assertValueEqual(progress, serverDataSlots.get(2).get(), "dataSlot[2] = progress");
            // Index 3: DATA_TICKS
            h.assertValueEqual(ticks, serverDataSlots.get(3).get(), "dataSlot[3] = ticks");
            // Index 4: DATA_STATUS = RUNNING ordinal 1
            h.assertValueEqual(ProcessState.RUNNING.ordinal(), serverDataSlots.get(4).get(), "dataSlot[4] = status RUNNING ordinal 1");
            // Index 5: DATA_REFUSAL = 0
            h.assertValueEqual(0, serverDataSlots.get(5).get(), "dataSlot[5] = refusal 0");
            // Index 6: DATA_LAYER = 1
            h.assertValueEqual(1, serverDataSlots.get(6).get(), "dataSlot[6] = layer 1");
            // Index 7: DATA_TOTAL = 12
            h.assertValueEqual(12, serverDataSlots.get(7).get(), "dataSlot[7] = total 12");
            // Index 8: DATA_FORMED = 1
            h.assertValueEqual(1, serverDataSlots.get(8).get(), "dataSlot[8] = formed 1");
            // Index 9: DATA_TIER = 2
            h.assertValueEqual(2, serverDataSlots.get(9).get(), "dataSlot[9] = tier 2");
            // Index 10: DATA_DIE = DieTypes.ALL.indexOf(MEMORY) + 1
            int expectedDieIndex = DieTypes.ALL.indexOf(DieTypes.MEMORY) + 1;
            h.assertValueEqual(expectedDieIndex, serverDataSlots.get(10).get(), "dataSlot[10] = die index for MEMORY");
            // Index 11: DATA_LOCKED = 1
            h.assertValueEqual(1, serverDataSlots.get(11).get(), "dataSlot[11] = locked 1");

            // Copy each serverDataSlot.get() into client.setData(index, value)
            for (int i = 0; i < 12; i++) {
                int value = serverDataSlots.get(i).get();
                clientMenu.setData(i, value);
            }

            // Assert client state matches
            h.assertTrue(clientMenu.inputsLocked(), "client inputsLocked true after data copy");
            h.assertValueEqual(ProcessState.RUNNING.ordinal(), clientMenu.status(), "client status RUNNING");
            h.assertValueEqual(2, clientMenu.tierIndex(), "client tier 2");
            h.assertValueEqual(expectedDieIndex, clientMenu.dieIndex(), "client die MEMORY");
            h.assertValueEqual(1, clientMenu.currentLayer(), "client layer 1");
            h.assertValueEqual(12, clientMenu.totalLayers(), "client total 12");

            // Abort actual controller then copy again
            cell.abortRun();
            for (int i = 0; i < 12; i++) {
                int value = serverDataSlots.get(i).get();
                clientMenu.setData(i, value);
            }
            h.assertValueEqual(ProcessCellMenu.STATUS_ABORTED, clientMenu.status(), "client status ABORTED after abort");
            h.assertFalse(clientMenu.inputsLocked(), "client unlocked after abort");
            h.assertValueEqual(0, clientMenu.locked(), "client locked = 0 after abort");

            // Release the Abort latch, then reject a T2 mask on a real T1 line.
            ItemStack sameLot = cell.removeItemNoUpdate(0);
            cell.setItem(0, sameLot);
            Item[] weakParts = {ModItems.MERCURY_LAMP.get(), ModItems.CONTACT_PLATE.get(),
                ModItems.MANUAL_ALIGNER.get(), ModItems.BASIC_AIR_FILTER.get(), ModItems.OPTICAL_MICROSCOPE.get()};
            for (int i = 0; i < 5; i++) cell.setSlot(3 + i, new ItemStack(weakParts[i]));
            tick(cell, 1);
            h.assertTrue(cell.lastRefusal() != null && cell.lastRefusal().is(RefusalReason.Code.LINE_CANNOT_RUN_TIER),
                "real weak line produces the expected refusal");
            for (int i = 0; i < 12; i++) clientMenu.setData(i, serverDataSlots.get(i).get());
            h.assertValueEqual(RefusalReason.Code.LINE_CANNOT_RUN_TIER.ordinal() + 1, clientMenu.refusal(),
                "client receives the actual weak-line refusal");
            h.assertFalse(clientMenu.inputsLocked(), "refused run stays unlocked on client");
        });
        h.succeed();
    }

    /**
     * Case process_cell_block_use_opens_menu:
     * fixture T1 (before tick okay), actual connected player near controller,
     * hit BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false).
     * player.gameMode.useItemOn real server path with empty hand then held ordinary item;
     * both consume action and open ProcessCellMenu bound slots[0].container == cell, stillValid true.
     * player.closeContainer between uses; after closing returns inventoryMenu.
     * Lower menu has 41 total slots (5 machine + 36 player).
     * Follow actual source EXAMPLES signature. No direct constructor-only substitute for block interaction.
     * No logs containing private wafer data. Each case helper.succeed only after assertions.
     */
    private static void blockUseOpensMenu(GameTestHelper h) {
        settings(() -> {
            // T1 fixture (before tick okay)
            Fixture f = fixture(h, 1, 64, 4, false);
            var cell = f.cell();
            // Don't tick - test block interaction before any processing

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // BlockHitResult with Vec3.atCenterOf(absPos), Direction.UP, absPos, false
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.UP, controllerPos, false);

            // Empty hand
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            InteractionResult emptyHand = player.gameMode.useItemOn(player, h.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(emptyHand.consumesAction(), "using the controller with empty hand consumes action");
            h.assertTrue(player.containerMenu instanceof ProcessCellMenu,
                "empty hand opens ProcessCellMenu, got " + player.containerMenu.getClass().getSimpleName());
            ProcessCellMenu openedMenu = (ProcessCellMenu) player.containerMenu;
            h.assertTrue(openedMenu.slots.get(0).container == cell,
                "menu opened by block interaction is bound to the cell (slot 0 container)");
            h.assertTrue(openedMenu.slots.size() == 41, "lower menu has 41 total slots (5 machine + 36 player)");
            h.assertTrue(openedMenu.stillValid(player), "menu considers player close enough");
            player.closeContainer();
            h.assertTrue(player.containerMenu == player.inventoryMenu,
                "closing menu returns player to inventory menu");

            // Held ordinary item
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            InteractionResult heldItem = player.gameMode.useItemOn(player, h.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(heldItem.consumesAction(), "using the controller with held item consumes action");
            h.assertTrue(player.containerMenu instanceof ProcessCellMenu,
                "held item opens ProcessCellMenu, got " + player.containerMenu.getClass().getSimpleName());
            ProcessCellMenu openedMenu2 = (ProcessCellMenu) player.containerMenu;
            h.assertTrue(openedMenu2.slots.get(0).container == cell,
                "menu opened with held item is bound to the cell");
            h.assertTrue(openedMenu2.slots.size() == 41, "lower menu has 41 total slots (5 machine + 36 player)");
            h.assertTrue(openedMenu2.stillValid(player), "menu considers player close enough");
            player.closeContainer();
            h.assertTrue(player.containerMenu == player.inventoryMenu,
                "closing menu after held item returns player to inventory menu");
        });
        h.succeed();
    }

    // ------------------------------------------------------------------------
    // M1 Operator Panels new test cases
    // ------------------------------------------------------------------------

    /**
     * Case operator_panel_block_use:
     * Fixture T1, actual connected player near operating panel (above controller),
     * hit BlockHitResult(Vec3.atCenterOf(absPanelPos), Direction.UP, absPanelPos, false).
     * player.gameMode.useItemOn real server path with empty hand then held ordinary item;
     * both consume action and open OperatingPanelMenu bound slots[0].container == cell, stillValid true.
     * player.closeContainer between uses; after closing returns inventoryMenu.
     * Upper menu has 39 total slots (3 machine + 36 player).
     */
    private static void operatorPanelBlockUse(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 1, 64, 4, false);
            var cell = f.cell();

            ServerPlayer player = connect(h.getLevel());
            BlockPos panelPos = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(panelPos.getX() + 0.5, panelPos.getY() + 0.5, panelPos.getZ() + 0.5);

            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(panelPos), Direction.UP, panelPos, false);

            // Empty hand
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            InteractionResult emptyHand = player.gameMode.useItemOn(player, h.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(emptyHand.consumesAction(), "using the operating panel with empty hand consumes action");
            h.assertTrue(player.containerMenu instanceof OperatingPanelMenu,
                "empty hand opens OperatingPanelMenu, got " + player.containerMenu.getClass().getSimpleName());
            OperatingPanelMenu openedMenu = (OperatingPanelMenu) player.containerMenu;
            h.assertTrue(openedMenu.slots.get(0).container == cell,
                "menu opened by panel interaction is bound to the cell (slot 0 container)");
            h.assertTrue(openedMenu.slots.size() == 39, "upper menu has 39 total slots (3 machine + 36 player)");
            h.assertTrue(openedMenu.stillValid(player), "menu considers player close enough");
            player.closeContainer();
            h.assertTrue(player.containerMenu == player.inventoryMenu,
                "closing menu returns player to inventory menu");

            // Held ordinary item
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            InteractionResult heldItem = player.gameMode.useItemOn(player, h.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(heldItem.consumesAction(), "using the panel with held item consumes action");
            h.assertTrue(player.containerMenu instanceof OperatingPanelMenu,
                "held item opens OperatingPanelMenu, got " + player.containerMenu.getClass().getSimpleName());
            OperatingPanelMenu openedMenu2 = (OperatingPanelMenu) player.containerMenu;
            h.assertTrue(openedMenu2.slots.get(0).container == cell,
                "menu opened with held item is bound to the cell");
            h.assertTrue(openedMenu2.slots.size() == 39, "upper menu has 39 total slots (3 machine + 36 player)");
            h.assertTrue(openedMenu2.stillValid(player), "menu considers player close enough");
            player.closeContainer();
            h.assertTrue(player.containerMenu == player.inventoryMenu,
                "closing menu after held item returns player to inventory menu");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_menu_open:
     * Direct menu construction for both lower (ProcessCellMenu) and upper (OperatingPanelMenu)
     * with real connected player. Verify both open correctly and reference the same cell.
     * Upper menu: 39 total slots (3 machine + 36 player). Lower menu: 41 total slots (5 machine + 36 player).
     */
    private static void operatorPanelMenuOpen(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 1, 64, 4, false);
            var cell = f.cell();

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            BlockPos panelPos = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // Lower menu (ProcessCellMenu) - 5 visible equipment slots
            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            player.containerMenu = lowerMenu;
            h.assertTrue(lowerMenu.visibleMachineSlotCount() == 5, "lower menu shows 5 visible machine slots");
            h.assertTrue(lowerMenu.slots.size() == 41, "lower menu has 41 total slots (5 machine + 36 player)");
            h.assertTrue(lowerMenu.slots.get(0).container == cell, "lower menu slot 0 bound to cell");
            h.assertTrue(lowerMenu.stillValid(player), "lower menu valid near controller");

            // Upper menu (OperatingPanelMenu) - 3 visible consumable slots
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;
            h.assertTrue(upperMenu.visibleMachineSlotCount() == 3, "upper menu shows 3 visible machine slots");
            h.assertTrue(upperMenu.slots.size() == 39, "upper menu has 39 total slots (3 machine + 36 player)");
            h.assertTrue(upperMenu.slots.get(0).container == cell, "upper menu slot 0 bound to cell");
            h.assertTrue(upperMenu.corePos().equals(controllerPos), "upper menu tracks core position");
            h.assertTrue(upperMenu.stillValid(player), "upper menu valid near panel");

            player.closeContainer();
            h.assertTrue(player.containerMenu == player.inventoryMenu,
                "closing upper menu returns player to inventory menu");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_slot_mapping:
     * Verify exact 3/5 slot underlying mapping shared one owner.
     * Lower: visible 5 slots map to underlying indices 3..7 (LIGHT_SOURCE..METROLOGY).
     * Upper: visible 3 slots map to underlying indices 0..2 (FOUP, MASK, RESIST).
     * Both menus share the same 8-slot inventory in the cell.
     * Use Slot.getContainerSlot() for backing slot index; getSlotIndex() is menu index.
     */
    private static void operatorPanelSlotMapping(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 1, 64, 4, false);
            var cell = f.cell();

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);

            // Lower menu: visible indices 0..4 map to underlying 3..7
            for (int vis = 0; vis < 5; vis++) {
                int underlying = ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + vis;
                h.assertTrue(lowerMenu.slots.get(vis).container == cell,
                    "lower visible " + vis + " container is cell");
                h.assertTrue(lowerMenu.slots.get(vis).getContainerSlot() == underlying,
                    "lower visible " + vis + " maps to underlying slot " + underlying);
            }

            // Upper menu: visible indices 0..2 map to underlying 0..2
            for (int vis = 0; vis < 3; vis++) {
                int underlying = vis; // FOUP, MASK, RESIST
                h.assertTrue(upperMenu.slots.get(vis).container == cell,
                    "upper visible " + vis + " container is cell");
                h.assertTrue(upperMenu.slots.get(vis).getContainerSlot() == underlying,
                    "upper visible " + vis + " maps to underlying slot " + underlying);
            }

            // Upper menu corePos tracking
            h.assertTrue(upperMenu.corePos().equals(controllerPos), "upper menu tracks core position");

            // Verify shared inventory: change equipment via lower visible 0 (underlying 3 = LIGHT_SOURCE),
            // assert cell.slot(3) changes and upper FOUP (underlying 0) unchanged
            ItemStack newLight = new ItemStack(ModItems.I_LINE_LAMP.get());
            lowerMenu.slots.get(0).set(newLight.copy());
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem(), newLight),
                "cell underlying slot 3 (LIGHT_SOURCE) changed via lower visible 0");
            h.assertTrue(ItemStack.isSameItemSameComponents(upperMenu.slots.get(0).getItem(), cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem()),
                "upper FOUP (underlying 0) unchanged after lower equipment change");

            // Change FOUP/resist via upper, assert cell.slot(0/2) changes while lower equipment retained
            ItemStack newFoup = FoupItems.with(ModItems.FOUP_MK2.get(),
                new FoupContents(UUID.randomUUID(), 2, List.of(new Wafer(0, 1.0f, 0))));
            upperMenu.slots.get(0).set(newFoup.copy());
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), newFoup),
                "cell underlying slot 0 (FOUP) changed via upper visible 0");
            // Lower equipment (visible 0 = underlying 3) should be unchanged
            h.assertTrue(ItemStack.isSameItemSameComponents(lowerMenu.slots.get(0).getItem(), cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem()),
                "lower equipment retained after upper FOUP change");

            ItemStack newResist = new ItemStack(ModItems.RESIST_T2.get(), 10);
            upperMenu.slots.get(2).set(newResist.copy());
            h.assertTrue(ItemStack.isSameItemSameComponents(cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).getItem(), newResist),
                "cell underlying slot 2 (RESIST) changed via upper visible 2");
            // Lower equipment (visible 0 = underlying 3) should still be unchanged
            h.assertTrue(ItemStack.isSameItemSameComponents(lowerMenu.slots.get(0).getItem(), cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE).getItem()),
                "lower equipment retained after upper resist change");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_equipment_retains:
     * Installed equipment (slots 3..7) remains when operating FOUP changes.
     * Swap FOUP in upper menu (visible index 0), verify lower equipment slots unchanged.
     */
    private static void operatorPanelEquipmentRetains(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);

            // Capture equipment slots (underlying 3..7) from lower menu (visible 0..4)
            ItemStack[] originalEquipment = new ItemStack[5];
            for (int i = 0; i < 5; i++) {
                originalEquipment[i] = lowerMenu.slots.get(i).getItem().copy();
            }

            // Swap FOUP via upper menu (visible index 0 = underlying SLOT_FOUP)
            ItemStack newFoup = FoupItems.with(ModItems.FOUP_MK2.get(),
                new FoupContents(UUID.randomUUID(), 2, List.of(new Wafer(0, 1.0f, 0))));
            upperMenu.slots.get(0).set(newFoup.copy());

            // Verify equipment unchanged in lower menu (visible 0..4 = underlying 3..7)
            for (int i = 0; i < 5; i++) {
                h.assertTrue(ItemStack.matches(originalEquipment[i], lowerMenu.slots.get(i).getItem()),
                    "equipment slot " + i + " unchanged after FOUP swap");
            }

            // Verify FOUP changed in both menus
            h.assertTrue(ItemStack.isSameItemSameComponents(upperMenu.slots.get(0).getItem(), newFoup),
                "upper menu shows new FOUP (visible 0)");
            h.assertTrue(ItemStack.isSameItemSameComponents(lowerMenu.slots.get(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem(), newFoup),
                "lower menu shows new FOUP (shared inventory, underlying " + ProcessCellControllerBlockEntity.SLOT_FOUP + ")");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_no_hidden_access:
     * No hidden-slot accesses via click/shift/drag on either menu when inputs locked.
     * Tick once to lock inputs, then test all lock actions on both menus.
     * Lower visible slots are equipment (indices 3..7), upper visible slots are consumables (indices 0..2).
     */
    private static void operatorPanelNoHiddenAccess(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 8, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.inputsLocked(), "cell is active with inputs locked");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);

            // Test lower menu (5 visible slots = equipment, all locked)
            player.containerMenu = lowerMenu;
            for (int vis = 0; vis < 5; vis++) {
                Slot slot = lowerMenu.slots.get(vis);
                h.assertFalse(slot.mayPickup(player), "lower visible " + vis + " mayPickup false when locked");
                h.assertFalse(slot.mayPlace(ItemStack.EMPTY), "lower visible " + vis + " mayPlace empty when locked");
            }
            // Test quickMoveStack on lower locked slots returns empty
            for (int vis = 0; vis < 5; vis++) {
                ItemStack result = lowerMenu.quickMoveStack(player, vis);
                h.assertTrue(result.isEmpty(), "lower quickMoveStack on locked slot " + vis + " returns empty");
            }
            // Test PICKUP_ALL cannot extract from lower locked slots
            for (int vis = 0; vis < 5; vis++) {
                h.assertFalse(lowerMenu.canTakeItemForPickAll(ItemStack.EMPTY, lowerMenu.slots.get(vis)),
                    "lower PICKUP_ALL predicate protects locked slot " + vis);
            }

            // Test upper menu (3 visible slots = FOUP, MASK, RESIST, all locked)
            player.containerMenu = upperMenu;
            for (int vis = 0; vis < 3; vis++) {
                Slot slot = upperMenu.slots.get(vis);
                h.assertFalse(slot.mayPickup(player), "upper visible " + vis + " mayPickup false when locked");
                // RESIST (vis=2) mayPlace allowed for top-up, but FOUP/MASK (vis=0,1) not
                if (vis < 2) {
                    h.assertFalse(slot.mayPlace(ItemStack.EMPTY), "upper visible " + vis + " mayPlace empty when locked");
                }
            }
            // Test quickMoveStack on upper locked slots (except resist top-up)
            for (int vis = 0; vis < 2; vis++) { // FOUP, MASK
                ItemStack result = upperMenu.quickMoveStack(player, vis);
                h.assertTrue(result.isEmpty(), "upper quickMoveStack on locked slot " + vis + " returns empty");
            }
            // Test PICKUP_ALL cannot extract from upper locked slots (except resist)
            for (int vis = 0; vis < 3; vis++) {
                if (vis != ProcessCellControllerBlockEntity.SLOT_RESIST) {
                    h.assertFalse(upperMenu.canTakeItemForPickAll(ItemStack.EMPTY, upperMenu.slots.get(vis)),
                        "upper PICKUP_ALL predicate protects locked slot " + vis);
                }
            }
        });
        h.succeed();
    }

    /**
     * Case operator_panel_stage_no_bypass:
     * Lower visible slot 2 (STAGE, underlying index 5) never gets resist bypass.
     * The resist bypass only applies to the RESIST slot (underlying index 2, upper visible 2).
     * Lower menu only shows equipment slots (underlying 3..7), never consumables (0..2).
     */
    private static void operatorPanelStageNoBypass(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 8, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.inputsLocked(), "cell is active with inputs locked");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);
            player.containerMenu = lowerMenu;

            // Lower visible slot 2 = STAGE (underlying index 5 = SLOT_LIGHT_SOURCE + 2)
            Slot stageSlot = lowerMenu.slots.get(2);
            int stageUnderlying = stageSlot.getContainerSlot();
            h.assertTrue(stageUnderlying == ProcessCellControllerBlockEntity.SLOT_STAGE,
                "lower visible 2 maps to STAGE (underlying " + ProcessCellControllerBlockEntity.SLOT_STAGE + ")");
            // STAGE is a LockedComponentSlot, mayPickup should be false when locked
            h.assertFalse(stageSlot.mayPickup(player), "STAGE slot mayPickup false when locked");
            h.assertFalse(stageSlot.mayPlace(new ItemStack(ModItems.RESIST_T2.get())),
                "STAGE slot rejects resist (wrong item type)");
            h.assertFalse(stageSlot.mayPlace(new ItemStack(ModItems.STEPPER_STAGE.get())),
                "STAGE slot rejects placement when locked");

            // RESIST slot (underlying index 2) is NOT visible in lower menu.
            // Lower visible slots are equipment only (underlying 3..7).
            h.assertTrue(lowerMenu.visibleMachineSlotCount() == 5, "lower menu has 5 visible slots (equipment only)");

            // Upper menu visible slot 2 = RESIST (underlying index 2) - this is the only slot with resist bypass
            player.containerMenu = upperMenu;
            Slot upperResistSlot = upperMenu.slots.get(2);
            h.assertTrue(upperResistSlot.getContainerSlot() == ProcessCellControllerBlockEntity.SLOT_RESIST,
                "upper visible 2 maps to RESIST (underlying " + ProcessCellControllerBlockEntity.SLOT_RESIST + ")");
            // The resist bypass (mayPickup during insertion) is only in OperatingPanelMenu's resist slot.
            // Verify resist top-up still works on upper menu resist slot when locked
            upperMenu.setCarried(new ItemStack(ModItems.RESIST_T2.get(), 3));
            upperMenu.clicked(2, 0, ContainerInput.PICKUP, player);
            h.assertValueEqual(11, resist(cell), "upper resist top-up works when locked (bypass)");
            h.assertTrue(upperMenu.getCarried().isEmpty(), "cursor consumed on resist top-up");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_player_slots_usable:
     * Own player slots remain usable while running.
     * Lower menu: 5 visible machine slots, player inventory starts at index 5, hotbar at 5+27=32.
     * Upper menu: 3 visible machine slots, player inventory starts at index 3, hotbar at 3+27=30.
     * Test that player hotbar slots work normally on both menus.
     */
    private static void operatorPanelPlayerSlotsUsable(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 8, 4, false);
            var cell = f.cell();
            tick(cell, 1);
            h.assertTrue(cell.inputsLocked(), "cell is active with inputs locked");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            // Test lower menu player slots
            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            player.containerMenu = lowerMenu;
            int lowerPlayerStart = 5; // 5 visible machine slots
            int lowerFirstHotbar = lowerPlayerStart + 27; // 27 main inventory slots = index 32
            lowerMenu.slots.get(lowerFirstHotbar).set(new ItemStack(Items.DIAMOND, 3));
            h.assertTrue(lowerMenu.slots.get(lowerFirstHotbar).getItem().is(Items.DIAMOND),
                "lower menu player hotbar slot accepts items");
            lowerMenu.clicked(lowerFirstHotbar, 0, ContainerInput.PICKUP, player);
            h.assertTrue(lowerMenu.getCarried().is(Items.DIAMOND) && lowerMenu.getCarried().getCount() == 3,
                "lower menu can pick up from player hotbar");

            // Test upper menu player slots
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(8, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;
            int upperPlayerStart = 3; // 3 visible machine slots
            int upperFirstHotbar = upperPlayerStart + 27; // 27 main inventory slots = index 30
            upperMenu.slots.get(upperFirstHotbar).set(new ItemStack(Items.EMERALD, 2));
            h.assertTrue(upperMenu.slots.get(upperFirstHotbar).getItem().is(Items.EMERALD),
                "upper menu player hotbar slot accepts items");
            upperMenu.clicked(upperFirstHotbar, 0, ContainerInput.PICKUP, player);
            h.assertTrue(upperMenu.getCarried().is(Items.EMERALD) && upperMenu.getCarried().getCount() == 2,
                "upper menu can pick up from player hotbar");

            // Verify shift-click from player inventory to machine is blocked on locked slots
            // Lower: put resist in player slot, shift-click should not move to locked equipment slots
            lowerMenu.slots.get(lowerFirstHotbar).set(new ItemStack(ModItems.RESIST_T2.get(), 5));
            ItemStack lowerShiftResult = lowerMenu.quickMoveStack(player, lowerFirstHotbar);
            h.assertTrue(lowerShiftResult.is(ModItems.RESIST_T2.get()) && lowerShiftResult.getCount() == 5,
                "lower shift-click from player to locked machine slots returns original stack");
            h.assertValueEqual(8, resist(cell), "lower resist unchanged (no shift into locked equipment)");

            // Upper: put resist in player slot, shift-click should move to resist slot (top-up allowed)
            upperMenu.slots.get(upperFirstHotbar).set(new ItemStack(ModItems.RESIST_T2.get(), 5));
            ItemStack upperShiftResult = upperMenu.quickMoveStack(player, upperFirstHotbar);
            h.assertTrue(upperShiftResult.isEmpty() || upperShiftResult.getCount() < 5,
                "upper shift-click from player to resist slot consumes some/all");
            h.assertValueEqual(13, resist(cell), "upper resist increased via shift-click top-up");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_no_lower_abort:
     * Lower menu has no Abort button (ABORT_BUTTON only works on operating panel menu).
     * Clicking button 0 on lower menu returns false, does not abort.
     */
    private static void operatorPanelNoLowerAbort(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertTrue(cell.inputsLocked(), "cell is active with inputs locked");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            ProcessCellMenu lowerMenu = new ProcessCellMenu(7, player.getInventory(), cell);
            player.containerMenu = lowerMenu;

            // Lower menu is not operating panel, so clickMenuButton should return false
            h.assertFalse(lowerMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "lower menu clickMenuButton returns false (not operating panel)");
            h.assertFalse(cell.aborted(), "lower menu cannot abort the cell");
            h.assertTrue(cell.inputsLocked(), "inputs still locked after lower menu abort attempt");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_upper_abort_valid:
     * Upper menu (OperatingPanelMenu) has valid Abort button.
     * Clicking button 0 on upper menu returns true, aborts cell, unlocks inputs.
     * Second click returns false (no active run).
     */
    private static void operatorPanelUpperAbortValid(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before abort test");
            h.assertTrue(cell.inputsLocked(), "inputs locked before abort");

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;

            // Valid abort via upper menu
            h.assertTrue(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "upper menu clickMenuButton returns true");
            h.assertTrue(cell.aborted(), "abort latches stopped");
            h.assertFalse(cell.inputsLocked(), "abort permits FOUP removal");

            // Second click returns false
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "second click on aborted cell returns false");
            h.assertTrue(cell.aborted(), "abort remains latched");

            // Verify applied layers / FE / resist no premature refund
            h.assertValueEqual(1, lot(cell).lotLayer(), "applied layer preserved");
            long expectedFE = 10000L - 4L * f.cost();
            h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE not refunded");
            h.assertValueEqual(60, resist(cell), "resist not refunded");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_invalid_after_removal:
     * Upper menu becomes invalid after panel/controller removal/replacement or wrong world/distance.
     * Test: remove panel -> stillValid false, clickMenuButton false.
     * Test: destroy controller -> stillValid false.
     * Test: wrong world -> stillValid false.
     * Test: distance > 8 -> stillValid false.
     */
    private static void operatorPanelInvalidAfterRemoval(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 1);

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            BlockPos panelPos = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(panelPos.getX() + 0.5, panelPos.getY() + 0.5, panelPos.getZ() + 0.5);

            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell, controllerPos);
            player.containerMenu = upperMenu;
            h.assertTrue(upperMenu.stillValid(player), "upper menu initially valid");

            // Remove operating panel block
            h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            cell.revalidate();
            h.assertFalse(upperMenu.stillValid(player), "upper menu invalid after panel removed");
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false after panel removed");

            // Restore panel, then destroy controller
            h.setBlock(new BlockPos(1, 2, 1), ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
            cell.revalidate();
            h.assertTrue(upperMenu.stillValid(player), "upper menu valid after panel restored");

            h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
            cell.revalidate();
            h.assertFalse(upperMenu.stillValid(player), "upper menu invalid after controller destroyed");
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false after controller destroyed");

            // Restore controller and panel for wrong world test
            h.setBlock(new BlockPos(1, 1, 1), ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
            h.setBlock(new BlockPos(1, 2, 1), ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
            cell.revalidate();
            h.assertTrue(upperMenu.stillValid(player), "upper menu valid after full restore");

            // Wrong world test - player in different dimension
            ServerLevel otherLevel = h.getLevel().getServer().getLevel(Level.NETHER);
            h.assertTrue(otherLevel != null, "other dimension exists for wrong-world validation");
            CommonListenerCookie otherCookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "wrong-world-panel"), false);
            ServerPlayer otherPlayer = new ServerPlayer(h.getLevel().getServer(), otherLevel,
                otherCookie.gameProfile(), otherCookie.clientInformation());
            otherPlayer.setPos(panelPos.getX() + 0.5, panelPos.getY() + 0.5, panelPos.getZ() + 0.5);
            otherPlayer.containerMenu = upperMenu;
            h.assertFalse(upperMenu.stillValid(otherPlayer), "upper menu invalid for wrong-world player");
            h.assertFalse(upperMenu.clickMenuButton(otherPlayer, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false for wrong-world player");

            // Distance > 8 test
            player.setPos(controllerPos.getX() + 10.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5); // 10 blocks away
            h.assertFalse(upperMenu.stillValid(player), "upper menu invalid when player >8 blocks from core");
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false when player >8 blocks away");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_adjacent_controller:
     * Upper menu cannot resolve adjacent different controller.
     * Build two cells side by side, open upper menu on first, verify it rejects second controller.
     * Proximity to second cell 3 blocks away does NOT invalidate first menu within 8 blocks!
     * Assert owner remains first and second contents unchanged, then remove/replace first panel/core
     * to assert no retarget to second; explicit >8 distance check.
     */
    private static void operatorPanelAdjacentController(GameTestHelper h) {
        settings(() -> {
            // Build first cell at (1,1,1)
            Fixture f1 = fixture(h, 2, 64, 4, false);
            var cell1 = f1.cell();

            // Build second cell at (4,1,1) - adjacent but separate (3 blocks away in X)
            // Second cell has NO operating panel initially
            BlockPos controller2 = new BlockPos(4, 1, 1);
            for (int x = 4; x <= 5; x++) for (int y = 1; y <= 2; y++) for (int z = 1; z <= 3; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (p.equals(controller2)) h.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                    .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
                else if (z == 3 && (x == 4 && y == 1 || x == 5 && y == 2))
                    h.setBlock(p, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                else h.setBlock(p, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
            }
            // Note: NO operating panel at (4,2,1) for second cell
            var cell2 = (ProcessCellControllerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(controller2));
            cell2.revalidate();
            h.assertTrue(cell2.isFormed(), "second cell forms without panel");

            ServerPlayer player = connect(h.getLevel());
            BlockPos panelPos1 = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(panelPos1.getX() + 0.5, panelPos1.getY() + 0.5, panelPos1.getZ() + 0.5);

            // Open upper menu for first cell
            BlockPos corePos1 = h.absolutePos(new BlockPos(1, 1, 1));
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell1, corePos1);
            player.containerMenu = upperMenu;
            h.assertTrue(upperMenu.stillValid(player), "upper menu valid for first cell");

            // Player is at first panel (within 8 blocks of first core)
            // Distance from first core (1,1,1) to player at first panel (1,2,1) is 1 block - valid
            // Distance from second core (4,1,1) to player at first panel (1,2,1) is ~3.7 blocks - also within 8
            // But upperMenu is bound to cell1 via corePos and panel, so it should NOT retarget to cell2

            // Verify upper menu still bound to cell1
            h.assertTrue(upperMenu.corePos().equals(corePos1), "upper menu corePos unchanged");
            h.assertTrue(upperMenu.slots.get(0).container == cell1, "upper menu still bound to cell1");

            // Move player to second cell's panel position (4,2,1) - but there's no panel there!
            // Player is now ~3.7 blocks from first core, still within 8 blocks
            BlockPos panelPos2 = h.absolutePos(new BlockPos(4, 2, 1));
            player.setPos(panelPos2.getX() + 0.5, panelPos2.getY() + 0.5, panelPos2.getZ() + 0.5);

            // Upper menu should still be valid (player within 8 blocks of first core at 1,1,1)
            // Distance from (1,1,1) to (4,2,1) = sqrt(3^2 + 1^2 + 0^2) = sqrt(10) ~ 3.16 < 8
            h.assertTrue(upperMenu.stillValid(player), "upper menu still valid - player within 8 blocks of first core");

            // Menu still bound to first cell, cannot interact with second
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false when player at different location but menu bound to first cell");
            h.assertFalse(cell1.aborted(), "first cell not aborted by stray click");

            // Now add panel to second cell - upper menu should STILL be bound to first cell
            h.setBlock(new BlockPos(4, 2, 1), ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
            cell2.revalidate();
            h.assertTrue(cell2.isFormed(), "second cell formed with panel");

            // Upper menu still bound to cell1
            h.assertTrue(upperMenu.slots.get(0).container == cell1, "upper menu still bound to cell1 after second cell forms");
            h.assertTrue(upperMenu.corePos().equals(corePos1), "upper menu corePos still first cell");

            // Now remove FIRST cell's panel - upper menu should become invalid
            h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            cell1.revalidate();
            h.assertFalse(upperMenu.stillValid(player), "upper menu invalid after first panel removed");

            // Restore first panel
            h.setBlock(new BlockPos(1, 2, 1), ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
            cell1.revalidate();
            h.assertTrue(upperMenu.stillValid(player), "upper menu valid after first panel restored");

            // Move player >8 blocks from first core - should invalidate
            player.setPos(corePos1.getX() + 10.5, corePos1.getY() + 0.5, corePos1.getZ() + 0.5); // 10 blocks away
            h.assertFalse(upperMenu.stillValid(player), "upper menu invalid when player >8 blocks from core");
            h.assertFalse(upperMenu.clickMenuButton(player, ProcessCellMenu.ABORT_BUTTON),
                "clickMenuButton false when player >8 blocks away");
        });
        h.succeed();
    }

    /**
     * Case operator_panel_missing_reforms:
     * Required panel missing/misplaced/extra pauses/reform resumes preserving exact
     * FOUP UUID/rolls/mask/resist/equipment/energy and no duplicate drops.
     * Test: remove panel -> PAUSED_STRUCTURE (after one tick), restore panel -> resumes preserving all state.
     * Initial tick 4 = first layer done + 1 tick next layer, restore 32 remaining ticks => 36 total paid ticks correct.
     * Total resist 40 of 64 unchanged (12 layers * 2 each).
     */
    private static void operatorPanelMissingReforms(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before panel removal");
            UUID originalLotId = f.lotId();
            int originalResist = resist(cell); // 60 after first layer
            long originalFE = cell.buffer().getAmountAsLong(); // 10000 - 4*cost
            int originalUses = uses(cell);
            ItemStack[] originalEquipment = new ItemStack[5];
            for (int i = 0; i < 5; i++) {
                originalEquipment[i] = cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i).copy();
            }

            // Remove operating panel -> cell unforms, run pauses ON NEXT TICK
            h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            cell.revalidate();
            h.assertFalse(cell.isFormed(), "cell unformed after panel removal");
            // Core run.tick observes structure on next serverTick - tick once before asserting pause
            tick(cell, 1);
            h.assertTrue(cell.processState() == ProcessState.PAUSED_STRUCTURE, "run pauses for missing panel after tick");

            // State preserved during pause (no FE/resist advance)
            tick(cell, 5);
            h.assertValueEqual(1, lot(cell).lotLayer(), "layer preserved during pause");
            h.assertValueEqual(originalLotId, lot(cell).lotId(), "FOUP UUID preserved");
            h.assertValueEqual(originalResist, resist(cell), "resist preserved during pause");
            h.assertValueEqual(originalFE, cell.buffer().getAmountAsLong(), "FE preserved during pause");
            h.assertValueEqual(originalUses, uses(cell), "mask uses preserved");
            for (int i = 0; i < 5; i++) {
                h.assertTrue(ItemStack.matches(originalEquipment[i], cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i)),
                    "equipment " + i + " preserved");
            }

            // Restore panel -> cell reforms, run resumes
            h.setBlock(new BlockPos(1, 2, 1), ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().defaultBlockState());
            cell.revalidate();
            h.assertTrue(cell.isFormed(), "cell reformed after panel restored");
            // Initial 4 ticks + 1 pause tick = 5 paid. Need 31 more to reach 36 total (12 layers * 3 ticks)
            tick(cell, 31); // Complete remaining 11 layers (32 ticks total after the pause tick = 36 total paid)
            completed(h, f, 12, DieTypes.MEMORY);
            h.assertValueEqual(40, resist(cell), "exact resist after completion (12 layers * 2 = 24 used, 64-24=40)");
            h.assertValueEqual(10000L - 36L * f.cost(), cell.buffer().getAmountAsLong(), "exact energy after completion (36 paid ticks)");

            // Verify all state preserved through pause/reform
            h.assertValueEqual(originalLotId, lot(cell).lotId(), "FOUP UUID preserved through reform");
            for (int i = 0; i < 5; i++) {
                h.assertTrue(ItemStack.matches(originalEquipment[i], cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i)),
                    "equipment " + i + " preserved through reform");
            }

            // No duplicate drops - meaningful conservation check
            ServerLevel level = h.getLevel();
            BlockPos absController = h.absolutePos(new BlockPos(1, 1, 1));
            AABB area = new AABB(absController).inflate(3.0);
            List<net.minecraft.world.entity.item.ItemEntity> entities = level.getEntities(EntityTypes.ITEM, area, Entity::isAlive);
            // During pause/reform, no items should have been dropped
            for (Entity e : entities) {
                if (e.getType() == EntityTypes.ITEM) {
                    net.minecraft.world.entity.item.ItemEntity itemEnt = (net.minecraft.world.entity.item.ItemEntity) e;
                    ItemStack stack = itemEnt.getItem();
                    h.assertTrue(!stack.is(ModItems.FOUP_MK1.get()) && !stack.is(ModItems.MASK.get()) &&
                            !stack.is(ModItems.RESIST_T2.get()) && !stack.is(ModItems.I_LINE_LAMP.get()) &&
                            !stack.is(ModItems.REDUCTION_LENS.get()) && !stack.is(ModItems.STEPPER_STAGE.get()) &&
                            !stack.is(ModItems.FINE_AIR_FILTER.get()) && !stack.is(ModItems.OVERLAY_SCOPE.get()),
                        "no machine inventory items dropped during pause/reform: " + stack.getItem());
                }
            }
        });
        h.succeed();
    }

    /**
     * Case operator_panel_save_reload:
     * Save/reload owner persists all 8 slots and run state; upper owns no inventory/run/FE.
     * Upper menu has 39 slots (3 machine + 36 player), lower has 41 slots (5 machine + 36 player).
     * Save the cell with active run, reload, verify all state preserved.
     */
    private static void operatorPanelSaveReload(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before save");

            // Save
            CompoundTag saved = save(h.getLevel(), cell);

            // Create fresh cell at same position (simulate reload)
            h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            // Rebuild fixture
            Fixture f2 = fixture(h, 2, 64, 4, false);
            var cell2 = f2.cell();

            // Load saved state
            load(h.getLevel(), cell2, saved);

            // Verify all 8 slots preserved
            h.assertValueEqual(f.lotId(), lot(cell2).lotId(), "FOUP UUID preserved after reload");
            h.assertValueEqual(1, lot(cell2).lotLayer(), "applied layer preserved");
            h.assertValueEqual(resist(f.cell()), resist(cell2), "resist count preserved");
            h.assertValueEqual(f.cell().buffer().getAmountAsLong(), cell2.buffer().getAmountAsLong(), "FE preserved");
            h.assertValueEqual(uses(f.cell()), uses(cell2), "mask uses preserved");
            for (int i = 0; i < 5; i++) {
                h.assertTrue(ItemStack.matches(f.cell().slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i),
                        cell2.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i)),
                    "equipment " + i + " preserved");
            }

            // Run state preserved
            h.assertTrue(cell2.inputsLocked(), "run still active after reload");
            h.assertFalse(cell2.aborted(), "abort not latched after reload");

            // Resume and complete
            tick(cell2, 32);
            completed(h, f2, 12, DieTypes.MEMORY);

            // Upper menu owns no inventory/run/FE - verify by constructing upper menu
            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell2, controllerPos);
            // Upper menu references same cell, so data slots show same values
            h.assertValueEqual(12, upperMenu.currentLayer(), "upper menu shows completed layer");
            h.assertValueEqual(12, upperMenu.totalLayers(), "upper menu shows total layers");
            h.assertValueEqual(ProcessState.FINISHED.ordinal(), upperMenu.status(), "upper menu shows finished status");
            // But upper menu itself has no inventory - it's just a view
            h.assertTrue(upperMenu.slots.size() == 39, "upper menu has 39 slots (3 machine + 36 player)");
            h.assertTrue(upperMenu.visibleMachineSlotCount() == 3, "upper menu shows 3 visible machine slots");

            // Lower menu has 41 slots (5 machine + 36 player)
            ProcessCellMenu lowerMenu = new ProcessCellMenu(8, player.getInventory(), cell2);
            h.assertTrue(lowerMenu.slots.size() == 41, "lower menu has 41 slots (5 machine + 36 player)");
            h.assertTrue(lowerMenu.visibleMachineSlotCount() == 5, "lower menu shows 5 visible machine slots");

            // Verify upper menu slots 0..2 backed by cell, 3..38 backed by player
            for (int i = 0; i < 3; i++) {
                h.assertTrue(upperMenu.slots.get(i).container == cell2, "upper slot " + i + " backed by cell");
            }
            for (int i = 3; i < 39; i++) {
                h.assertTrue(upperMenu.slots.get(i).container == player.getInventory(), "upper slot " + i + " backed by player");
            }
            // Verify lower menu slots 0..4 backed by cell, 5..40 backed by player
            for (int i = 0; i < 5; i++) {
                h.assertTrue(lowerMenu.slots.get(i).container == cell2, "lower slot " + i + " backed by cell");
            }
            for (int i = 5; i < 41; i++) {
                h.assertTrue(lowerMenu.slots.get(i).container == player.getInventory(), "lower slot " + i + " backed by player");
            }
        });
        h.succeed();
    }

    /**
     * Case operator_panel_upper_no_inventory:
     * Upper menu (OperatingPanelMenu) owns no inventory/run/FE - it's a view only.
     * The client SimpleContainer backing still has 8 slots, but only 3 visible.
     * Upper menu slot indices: 0..2 = cell-backed (FOUP, MASK, RESIST), 3..38 = player inventory backed.
     * Total slots = 3 (visible machine) + 36 (player) = 39 slots.
     */
    private static void operatorPanelUpperNoInventory(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();

            ServerPlayer player = connect(h.getLevel());
            BlockPos controllerPos = h.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);

            OperatingPanelMenu upperMenu = new OperatingPanelMenu(7, player.getInventory(), cell, controllerPos);

            // Upper menu has 3 visible machine slots
            h.assertTrue(upperMenu.visibleMachineSlotCount() == 3, "upper menu shows 3 visible slots");
            h.assertTrue(upperMenu.slots.size() == 39, "upper menu has 39 total slots (3 machine + 36 player)");

            // Machine slots 0..2 backed by cell
            for (int vis = 0; vis < 3; vis++) {
                h.assertTrue(upperMenu.slots.get(vis).container == cell,
                    "upper visible " + vis + " container is cell");
                h.assertTrue(upperMenu.slots.get(vis).getContainerSlot() == vis,
                    "upper visible " + vis + " maps to underlying slot " + vis);
            }

            // Player inventory slots 3..38 backed by player inventory
            for (int i = 3; i < 39; i++) {
                h.assertTrue(upperMenu.slots.get(i).container == player.getInventory(),
                    "upper slot " + i + " container is player inventory");
            }

            // Upper menu is operating panel
            h.assertTrue(upperMenu.isOperatingPanel(), "upper menu is operating panel");
            h.assertTrue(upperMenu.blockEntity == cell, "upper menu blockEntity is the controller");

            // Modify via upper visible slot, see via cell directly
            ProcessCellMenu lowerMenu = new ProcessCellMenu(8, player.getInventory(), cell);
            upperMenu.slots.get(0).set(new ItemStack(Items.DIRT)); // FOUP slot (upper visible 0 = underlying 0)
            h.assertTrue(lowerMenu.slots.get(ProcessCellControllerBlockEntity.SLOT_FOUP).getItem().is(Items.DIRT),
                "lower menu sees change made via upper menu (same inventory, underlying FOUP slot)");

            // Shared FE observation - both menus read from same core
            h.assertValueEqual(upperMenu.energy(), cell.buffer().getAmountAsInt(), "upper menu energy matches cell");
            h.assertValueEqual(lowerMenu.energy(), cell.buffer().getAmountAsInt(), "lower menu energy matches cell");
            h.assertValueEqual(upperMenu.energy(), lowerMenu.energy(), "both menus show same energy");
        });
        h.succeed();
    }

    private static void definitionPauseRestore(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before definition removal");
            h.assertValueEqual(60, resist(cell), "resist 60 after first layer");
            long expectedFE = 10000L - 4L * f.cost();
            h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE after four paid ticks");
            h.assertValueEqual(4, uses(cell), "mask remains unspent before the run finishes");

            // Save the original registry and swap to one without process_cell
            swapDefinitions(defs -> without(defs, "process_cell"), () -> {
                tick(cell, 1);
                h.assertTrue(cell.definition().isEmpty(), "definition missing after swap");
                h.assertTrue(cell.processState() == ProcessState.PAUSED_STRUCTURE, "PAUSED_STRUCTURE from missing definition");
                h.assertValueEqual(1, cell.progressTicks(), "partial tick preserved");
                h.assertValueEqual(1, lot(cell).lotLayer(), "physical layer unchanged");
                h.assertValueEqual(60, resist(cell), "resist unchanged during pause");
                h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE unchanged during pause");

                // Save and load while definition is missing
                CompoundTag saved = save(h.getLevel(), cell);
                load(h.getLevel(), cell, saved);
                tick(cell, 1);
                h.assertTrue(cell.processState() == ProcessState.PAUSED_STRUCTURE, "still paused after reload with missing definition");
                h.assertValueEqual(1, cell.progressTicks(), "progress still preserved after reload");
                h.assertValueEqual(f.lotId(), lot(cell).lotId(), "FOUP UUID preserved");
            });

            // Restore original registry (finally block in swapDefinitions)

            // Now swap to a modified process_cell with doubled T2 raw cost
            swapDefinitions(defs -> {
                MachineDefinition original = defs.stream()
                    .filter(d -> d.id().equals("process_cell"))
                    .findFirst()
                    .orElseThrow();
                Map<String, Integer> newPerTier = new LinkedHashMap<>(original.perTierEnergyPerTick());
                Integer originalT2 = newPerTier.get("T2");
                h.assertTrue(originalT2 != null && originalT2 > 0, "original T2 override exists");
                newPerTier.put("T2", originalT2 * 2);
                MachineDefinition modified = new MachineDefinition(
                    original.id(),
                    original.energyCapacity(),
                    original.maxReceive(),
                    original.energyPerTick(),
                    newPerTier,
                    original.params()
                );
                return replace(defs, modified);
            }, () -> {
                // Run remaining 11 layers (32 ticks total = 4 already paid + 32 more = 36 total original paid ticks)
                // With settings 3 ticks/layer, 12 layers = 36 ticks. We paid 4, need 32 more.
                tick(cell, 32);
                Tier tier = TierDataLoader.getRegistry().byIndex(2).orElseThrow();
                int newCost = cell.definition().orElseThrow().energyPerTick(tier, EUVCRAFTMod.settings());
                h.assertTrue(newCost != f.cost(), "new definition loaded with a different cost");
                completed(h, f, 12, DieTypes.MEMORY);
                h.assertValueEqual(40, resist(cell), "resist 40 after 12 layers at 2 per layer");
                h.assertValueEqual(10000L - 36L * f.cost(), cell.buffer().getAmountAsLong(),
                    "energy paid at original cost for 36 total ticks, not new doubled cost");
                h.assertValueEqual(3, uses(cell), "mask uses 3 for full T2 run");
            });
            // swapDefinitions finally block restores original registry
        });
        h.succeed();
    }

    private static void malformedStateStaysStopped(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            CompoundTag legacyIdle = save(h.getLevel(), f.cell());
            legacyIdle.remove("processRun");
            legacyIdle.remove("processHasRun");
            legacyIdle.remove("processAbortLatched");
            var cell = f.cell();
            tick(cell, 4);
            h.assertValueEqual(1, lot(cell).lotLayer(), "one layer before corruption test");
            h.assertValueEqual(1, cell.progressTicks(), "one partial tick");
            h.assertValueEqual(60, resist(cell), "resist 60");
            long expectedFE = 10000L - 4L * f.cost();
            h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE after four ticks");
            h.assertValueEqual(4, uses(cell), "mask uses 4");

            CompoundTag originalTag = save(h.getLevel(), cell);
            UUID originalLotId = f.lotId();
            int originalResist = resist(cell);
            long originalFE = cell.buffer().getAmountAsLong();
            int originalUses = uses(cell);
            int originalLayer = lot(cell).lotLayer();

            // Corruption 1: processRun string bad
            CompoundTag tag1 = originalTag.copy();
            CompoundTag runTag1 = tag1.getCompound("processRun").orElseThrow();
            runTag1.putString("snapshot", "not-a-compound");
            tag1.put("processRun", runTag1);
            loadAndAssertStopped(h, cell, tag1, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 2: processRun missing while processHasRun true
            CompoundTag tag2 = originalTag.copy();
            tag2.remove("processRun");
            tag2.putBoolean("processHasRun", true);
            loadAndAssertStopped(h, cell, tag2, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 3: processHasRun string bad with valid payload
            CompoundTag tag3 = originalTag.copy();
            tag3.putString("processHasRun", "not-a-boolean");
            loadAndAssertStopped(h, cell, tag3, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 4: processHasRun false with valid payload
            CompoundTag tag4 = originalTag.copy();
            tag4.putBoolean("processHasRun", false);
            loadAndAssertStopped(h, cell, tag4, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 5: processAbortLatched string bad with valid payload
            CompoundTag tag5 = originalTag.copy();
            tag5.putString("processAbortLatched", "not-a-boolean");
            loadAndAssertStopped(h, cell, tag5, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 6: processRun.snapshot.progress = -1
            CompoundTag tag6 = originalTag.copy();
            CompoundTag runTag6 = tag6.getCompound("processRun").orElseThrow();
            CompoundTag snapshotTag6 = runTag6.getCompound("snapshot").orElseThrow();
            snapshotTag6.putInt("progress", -1);
            runTag6.put("snapshot", snapshotTag6);
            tag6.put("processRun", runTag6);
            loadAndAssertStopped(h, cell, tag6, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Corruption 7: processRun.contract.lotId = random different UUID
            CompoundTag tag7 = originalTag.copy();
            CompoundTag runTag7 = tag7.getCompound("processRun").orElseThrow();
            CompoundTag contractTag7 = runTag7.getCompound("contract").orElseThrow();
            contractTag7.putString("lotId", UUID.randomUUID().toString());
            runTag7.put("contract", contractTag7);
            tag7.put("processRun", runTag7);
            loadAndAssertStopped(h, cell, tag7, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            CompoundTag orphan = originalTag.copy();
            orphan.remove("processHasRun");
            loadAndAssertStopped(h, cell, orphan, originalLotId, originalLayer, originalResist, originalFE, originalUses);
            CompoundTag wrongPayloadType = originalTag.copy();
            wrongPayloadType.putString("processRun", "invalid-run-payload");
            loadAndAssertStopped(h, cell, wrongPayloadType, originalLotId, originalLayer, originalResist, originalFE, originalUses);

            // Positive control: load valid original tag + tick 1 resumes progress 2 without additional resist charge
            load(h.getLevel(), cell, originalTag);
            tick(cell, 1);
            h.assertValueEqual(2, cell.progressTicks(), "positive control resumes to progress 2");
            h.assertValueEqual(60, resist(cell), "positive control no additional resist charge");
            h.assertValueEqual(originalLotId, lot(cell).lotId(), "positive control lot UUID preserved");
            h.assertTrue(cell.inputsLocked(), "positive control still active");
            h.assertFalse(cell.aborted(), "positive control not aborted");
            load(h.getLevel(), cell, legacyIdle);
            tick(cell, 1);
            h.assertTrue(cell.inputsLocked() && !cell.aborted(), "legacy idle save admits normally");
            h.assertValueEqual(1, cell.progressTicks(), "legacy idle admission makes one tick");
            h.assertValueEqual(62, resist(cell), "legacy idle admission pays one layer");
            h.assertValueEqual(10000L - f.cost(), cell.buffer().getAmountAsLong(), "legacy idle pays one energy tick");
        });
        h.succeed();
    }

    private static void loadAndAssertStopped(GameTestHelper h, ProcessCellControllerBlockEntity cell,
            CompoundTag tag, UUID expectedLotId, int expectedLayer, int expectedResist,
            long expectedFE, int expectedUses) {
        load(h.getLevel(), cell, tag);
        tick(cell, 1);
        h.assertTrue(cell.processState() != ProcessState.RUNNING, "corrupted load stops running");
        h.assertFalse(cell.inputsLocked(), "corrupted load unlocks inputs");
        h.assertTrue(cell.aborted(), "corrupted load latches stopped");
        tick(cell, 3);
        h.assertValueEqual(expectedLotId, lot(cell).lotId(), "FOUP UUID preserved after corruption");
        h.assertValueEqual(expectedLayer, lot(cell).lotLayer(), "applied layer preserved after corruption");
        h.assertValueEqual(expectedResist, resist(cell), "resist preserved after corruption");
        h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "FE preserved after corruption");
        h.assertValueEqual(expectedUses, uses(cell), "mask uses preserved after corruption");
    }

    private static void activeInputTamperStops(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4);
            CompoundTag saved = save(h.getLevel(), cell);
            ItemStack originalFoup = cell.slot(0).copy();
            ItemStack originalMask = cell.slot(1).copy();
            ItemStack originalPart = cell.slot(3).copy();
            long expectedFE = 10000L - 4L * f.cost();
            for (int target : new int[]{0, 1, 2, 3, 4, 5, 6, 7}) {
                load(h.getLevel(), cell, saved);
                List<ItemStack> accepted = new ArrayList<>();
                for (int slot = 0; slot < 8; slot++) accepted.add(cell.slot(slot).copy());
                // Restore the accepted run without paying another tick by loading its real snapshot.
                ItemStack replacement;
                if (target == 0) {
                    replacement = FoupItems.with(ModItems.FOUP_MK1.get(), new FoupContents(
                        UUID.randomUUID(), 2, List.of(new Wafer(0, 1.0f, 0), new Wafer(0, 1.0f, 0))));
                } else if (target == 1) {
                    replacement = new ItemStack(ModItems.MASK.get());
                    replacement.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, 1, 2));
                } else if (target == 2) replacement = new ItemStack(ModItems.RESIST_T1.get(), 60);
                else replacement = new ItemStack(ModItems.MERCURY_LAMP.get());
                cell.setSlot(target, replacement.copy());
                tick(cell, 1);
                h.assertTrue(cell.aborted() && !cell.inputsLocked(), "tampering stops and unlocks: " + target);
                h.assertTrue(ItemStack.matches(replacement, cell.slot(target)), "replacement untouched: " + target);
                for (int slot = 0; slot < 8; slot++) if (slot != target) h.assertTrue(
                    ItemStack.matches(accepted.get(slot), cell.slot(slot)), "other input unchanged: " + slot);
                h.assertValueEqual(60, resist(cell), "tampering spends no resist");
                h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "tampering spends no energy");
                if (target != 0) h.assertTrue(ItemStack.matches(originalFoup, cell.slot(0)), "accepted FOUP unchanged");
                if (target != 1) h.assertTrue(ItemStack.matches(originalMask, cell.slot(1)), "accepted mask unchanged");
                if (target != 3) h.assertTrue(ItemStack.matches(originalPart, cell.slot(3)), "accepted component unchanged");
                tick(cell, 3);
                h.assertValueEqual(60, resist(cell), "tampered run stays stopped");
                h.assertValueEqual(expectedFE, cell.buffer().getAmountAsLong(), "no automatic restart after tampering");
            }
        });
        h.succeed();
    }

    private static void partialBreakConservesInventory(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            tick(cell, 4); // layer 1 complete, partial layer 2 paid (1 tick of 3)
            h.assertValueEqual(1, lot(cell).lotLayer(), "layer 1 applied");
            h.assertValueEqual(1, cell.progressTicks(), "partial layer 2 progress 1");
            h.assertValueEqual(60, resist(cell), "resist 60 after first layer payment");
            UUID originalLotId = f.lotId();
            ItemStack originalFoup = cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).copy();
            ItemStack originalMask = cell.slot(ProcessCellControllerBlockEntity.SLOT_MASK).copy();
            ItemStack originalResist = cell.slot(ProcessCellControllerBlockEntity.SLOT_RESIST).copy();
            ItemStack[] originalComponents = new ItemStack[5];
            for (int i = 0; i < 5; i++) {
                originalComponents[i] = cell.slot(ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE + i).copy();
            }

            // Collect drops by destroying the controller block
            ServerLevel level = h.getLevel();
            BlockPos absController = h.absolutePos(new BlockPos(1, 1, 1));
            AABB area = new AABB(absController).inflate(2.0);
            List<net.minecraft.world.entity.item.ItemEntity> entitiesBefore = level.getEntities(EntityTypes.ITEM, area, Entity::isAlive);

            // Destroy the controller block (drops items)
            level.destroyBlock(absController, true, null);

            // Collect all item entities in the area after destruction
            List<net.minecraft.world.entity.item.ItemEntity> entitiesAfter = level.getEntities(EntityTypes.ITEM, area, Entity::isAlive);
            Map<Item, Integer> dropCounts = new LinkedHashMap<>();
            for (Entity e : entitiesAfter) {
                if (e.getType() == EntityTypes.ITEM) {
                    net.minecraft.world.entity.item.ItemEntity itemEnt = (net.minecraft.world.entity.item.ItemEntity) e;
                    boolean isNew = true;
                    for (Entity before : entitiesBefore) {
                        if (before.getUUID().equals(e.getUUID())) {
                            isNew = false;
                            break;
                        }
                    }
                    if (isNew) {
                        ItemStack stack = itemEnt.getItem();
                        dropCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                        if (stack.is(ModItems.FOUP_MK1.get())) h.assertTrue(ItemStack.matches(originalFoup, stack), "FOUP metadata survives break");
                        if (stack.is(ModItems.MASK.get())) h.assertTrue(ItemStack.matches(originalMask, stack), "mask metadata survives break");
                    }
                }
            }

            // Assert expected inventory drops
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.FOUP_MK1.get(), 0), "one FOUP MK1 dropped");
            h.assertTrue(cell.slot(ProcessCellControllerBlockEntity.SLOT_FOUP).isEmpty(), "FOUP slot emptied after break");

            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.MASK.get(), 0), "one MASK dropped");
            h.assertValueEqual(60, dropCounts.getOrDefault(ModItems.RESIST_T2.get(), 0), "sixty RESIST_T2 dropped (may be multiple entities)");

            // Each of the five T2 components count 1
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.I_LINE_LAMP.get(), 0), "I_LINE_LAMP count 1");
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.REDUCTION_LENS.get(), 0), "REDUCTION_LENS count 1");
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.STEPPER_STAGE.get(), 0), "STEPPER_STAGE count 1");
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.FINE_AIR_FILTER.get(), 0), "FINE_AIR_FILTER count 1");
            h.assertValueEqual(1, dropCounts.getOrDefault(ModItems.OVERLAY_SCOPE.get(), 0), "OVERLAY_SCOPE count 1");

            // Controller block loot may also drop one block item
            int controllerBlockDrops = dropCounts.getOrDefault(ModMultiblocks.PROCESS_CELL_CONTROLLER.get().asItem(), 0);
            h.assertTrue(controllerBlockDrops <= 1, "controller block drops at most one block item");

            // Assert no other FOUP/chip/scrap outputs
            for (Map.Entry<Item, Integer> entry : dropCounts.entrySet()) {
                Item item = entry.getKey();
                if (item != ModItems.FOUP_MK1.get() && item != ModItems.MASK.get()
                        && item != ModItems.RESIST_T2.get() && item != ModItems.I_LINE_LAMP.get()
                        && item != ModItems.REDUCTION_LENS.get() && item != ModItems.STEPPER_STAGE.get()
                        && item != ModItems.FINE_AIR_FILTER.get() && item != ModItems.OVERLAY_SCOPE.get()
                        && item != ModMultiblocks.PROCESS_CELL_CONTROLLER.get().asItem()) {
                    h.assertTrue(false, "unexpected drop: " + item + " count " + entry.getValue());
                }
            }
        });
        h.succeed();
    }

    private static void handFedCapabilities(GameTestHelper h) {
        settings(() -> {
            Fixture f = fixture(h, 2, 64, 4, false);
            var cell = f.cell();
            ServerLevel level = h.getLevel();
            List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
            sides.add(null);
            for (int x = 1; x <= 2; x++) for (int y = 1; y <= 2; y++) for (int z = 1; z <= 3; z++) {
                BlockPos pos = h.absolutePos(new BlockPos(x, y, z));
                boolean port = z == 3 && (x == 1 && y == 1 || x == 2 && y == 2);
                for (Direction side : sides) {
                    h.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, side) == null, "no item automation");
                    h.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, pos, side) == null, "no fluid automation");
                    var energy = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
                    h.assertTrue((energy != null) == port, "only formed ports expose energy");
                    if (energy != null) {
                        long before = cell.buffer().getAmountAsLong();
                        try (Transaction tx = Transaction.openRoot()) {
                            h.assertValueEqual(100, energy.insert(100, tx), "port insertion works");
                            tx.commit();
                        }
                        try (Transaction tx = Transaction.openRoot()) {
                            h.assertValueEqual(0, energy.extract(100, tx), "external extraction refused");
                            tx.commit();
                        }
                        h.assertValueEqual(before + 100, cell.buffer().getAmountAsLong(), "all ports use inherited buffer");
                    }
                }
            }
            h.setBlock(new BlockPos(2, 1, 1), Blocks.AIR.defaultBlockState());
            cell.revalidate();
            h.assertFalse(cell.isFormed(), "broken cell unformed");
            for (BlockPos port : List.of(new BlockPos(1, 1, 3), new BlockPos(2, 2, 3))) {
                for (Direction side : sides) h.assertTrue(
                    level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(port), side) == null,
                    "unformed port no longer exposes energy");
            }
        });
        h.succeed();
    }

    // Helper: swap definitions using the actual helper from PackagingGameTests
    private static void swapDefinitions(UnaryOperator<List<MachineDefinition>> swap, Runnable body) {
        try {
            Field live = MachineDefinitionLoader.class.getDeclaredField("CURRENT");
            live.setAccessible(true);
            Field loaded = com.euvcraft.lithmod.mc.data.JsonResourceDataLoader.class.getDeclaredField("current");
            loaded.setAccessible(true);
            Object loader = live.get(null);
            if (loader == null) {
                throw new AssertionError("no MachineDefinitionLoader instance exists, so the "
                    + "definition fixtures cannot be installed");
            }
            MachineDefinitionLoader.Registry previous =
                (MachineDefinitionLoader.Registry) loaded.get(loader);
            try {
                List<MachineDefinition> next = swap.apply(previous.all());
                loaded.set(loader, MachineDefinitionLoader.build(next, "M1-09 game test"));
                body.run();
            } finally {
                loaded.set(loader, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the machine definition registry could not be swapped", failure);
        }
    }

    private static List<MachineDefinition> without(List<MachineDefinition> definitions, String machineId) {
        return definitions.stream()
            .filter(definition -> !definition.id().equals(machineId))
            .toList();
    }

    private static List<MachineDefinition> replace(List<MachineDefinition> definitions, MachineDefinition replacement) {
        List<MachineDefinition> swapped = new ArrayList<>(definitions.size());
        for (MachineDefinition definition : definitions) {
            swapped.add(definition.id().equals(replacement.id()) ? replacement : definition);
        }
        return swapped;
    }
}
