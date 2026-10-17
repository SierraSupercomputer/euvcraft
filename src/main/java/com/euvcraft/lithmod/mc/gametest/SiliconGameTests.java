package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.core.MachineResult;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSlicing;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.JsonResourceDataLoader;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerMenu;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the M1-04 silicon chain on a real server level, with the real registries
 * and the real loaded data pack.
 *
 * <p>Every machine is driven through its own {@code serverTick}, so what is measured here is
 * the same code path the game runs. Where a test needs an input the data cannot produce (a
 * FOUP that is full, a client-view FOUP, a definition with different numbers) it builds that
 * input explicitly and says so. Two tests need the <em>loaded</em> data itself to change, and
 * swap it synchronously for the length of the test and put the live value back in a
 * {@code finally}: see {@link #withoutDefinitions} and {@link #withRecipes}.</p>
 */
public final class SiliconGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "silicon_chain";

    private static final String SLICER_PRODUCES_LOT = "wafer_slicer_produces_a_lot";
    private static final String SLICER_LOT_TAKES_LAYER = "wafer_slicer_lot_takes_layer_one";
    private static final String SLICER_REFUSES_USED_FOUP = "wafer_slicer_refuses_a_used_foup";
    private static final String SLICER_PAUSES = "wafer_slicer_pauses_without_energy";
    private static final String SLICER_ROUND_TRIP = "wafer_slicer_save_load_round_trip";
    private static final String PULLER_RUNS_RECIPE = "crystal_puller_runs_its_recipe";
    private static final String NO_ITEM_HANDLER = "silicon_machines_expose_no_item_handler";
    private static final String PULLER_RECHECKS_INPUTS = "crystal_puller_rechecks_its_inputs";
    private static final String SLICER_RECHECKS_INPUTS = "wafer_slicer_rechecks_its_inputs";
    private static final String SLICER_LOT_SIZE = "wafer_slicer_lot_size_follows_the_foup_capacity";
    private static final String SLICER_MENU = "wafer_slicer_menu_layout_and_shift_click";
    private static final String SLICER_FOUP_BOUNDARY = "wafer_slicer_foup_slot_client_boundary";
    private static final String ENERGY_FOLLOWS_DATA = "silicon_energy_follows_machine_definitions";
    private static final String FAIL_CLOSED = "silicon_machines_fail_closed_without_a_definition";
    private static final String RESUMES_SAME_RECIPE = "crystal_puller_resumes_only_the_same_recipe";
    private static final String COOKING_RECIPES = "silicon_cooking_recipes_load_and_match";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    /** Where the machine under test is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used for the controls. */
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    /** A third position, for the cases that need two machines at once. */
    private static final BlockPos THIRD = new BlockPos(5, 1, 1);

    static {
        FUNCTIONS.register(SLICER_PRODUCES_LOT, () -> SiliconGameTests::slicerProducesLot);
        FUNCTIONS.register(SLICER_LOT_TAKES_LAYER, () -> SiliconGameTests::slicerLotTakesLayerOne);
        FUNCTIONS.register(SLICER_REFUSES_USED_FOUP, () -> SiliconGameTests::slicerRefusesUsedFoup);
        FUNCTIONS.register(SLICER_PAUSES, () -> SiliconGameTests::slicerPausesWithoutEnergy);
        FUNCTIONS.register(SLICER_ROUND_TRIP, () -> SiliconGameTests::slicerSaveLoadRoundTrip);
        FUNCTIONS.register(PULLER_RUNS_RECIPE, () -> SiliconGameTests::pullerRunsItsRecipe);
        FUNCTIONS.register(NO_ITEM_HANDLER, () -> SiliconGameTests::noItemHandler);
        FUNCTIONS.register(PULLER_RECHECKS_INPUTS, () -> SiliconGameTests::pullerRechecksItsInputs);
        FUNCTIONS.register(SLICER_RECHECKS_INPUTS, () -> SiliconGameTests::slicerRechecksItsInputs);
        FUNCTIONS.register(SLICER_LOT_SIZE, () -> SiliconGameTests::slicerLotSizeFollowsCapacity);
        FUNCTIONS.register(SLICER_MENU, () -> SiliconGameTests::slicerMenuLayoutAndShiftClick);
        FUNCTIONS.register(SLICER_FOUP_BOUNDARY, () -> SiliconGameTests::slicerFoupSlotBoundary);
        FUNCTIONS.register(ENERGY_FOLLOWS_DATA, () -> SiliconGameTests::energyFollowsDefinitions);
        FUNCTIONS.register(FAIL_CLOSED, () -> SiliconGameTests::machinesFailClosedWithoutDefinition);
        FUNCTIONS.register(RESUMES_SAME_RECIPE, () -> SiliconGameTests::pullerResumesOnlyTheSameRecipe);
        FUNCTIONS.register(COOKING_RECIPES, () -> SiliconGameTests::cookingRecipesLoadAndMatch);
    }

    private SiliconGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) SiliconGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 3, 3, false, 0);

        register(event, data, SLICER_PRODUCES_LOT);
        register(event, data, SLICER_LOT_TAKES_LAYER);
        register(event, data, SLICER_REFUSES_USED_FOUP);
        register(event, data, SLICER_PAUSES);
        register(event, data, SLICER_ROUND_TRIP);
        register(event, data, PULLER_RUNS_RECIPE);
        register(event, data, NO_ITEM_HANDLER);
        register(event, data, PULLER_RECHECKS_INPUTS);
        register(event, data, SLICER_RECHECKS_INPUTS);
        register(event, data, SLICER_LOT_SIZE);
        register(event, data, SLICER_MENU);
        register(event, data, SLICER_FOUP_BOUNDARY);
        register(event, data, ENERGY_FOLLOWS_DATA);
        register(event, data, FAIL_CLOSED);
        register(event, data, RESUMES_SAME_RECIPE);
        register(event, data, COOKING_RECIPES);
        LOGGER.info("Registered EUVCRAFT silicon chain game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. A boule becomes a lot of the size the settings say
    // ------------------------------------------------------------------

    private static void slicerProducesLot(GameTestHelper helper) {
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        helper.assertTrue(slicer != null, "no wafer slicer block entity");
        if (slicer == null) {
            return;
        }

        helper.assertTrue(slicer.definition().isPresent(),
            "the slicer has no definition loaded from data/lithmod/machines");
        helper.assertTrue(slicer.sliceTicks() > 0,
            "the slicer's slice_ticks did not load: " + slicer.sliceTicks());

        // An FOUP Mk2 with an explicit capacity, so the stored capacity can be told apart
        // from the configured one.
        int capacity = EUVCRAFTMod.settings().foupCapacity();
        UUID inputLotId = UUID.randomUUID();
        ItemStack emptyFoup = FoupItems.with(ModItems.FOUP_MK2.get(),
            FoupContents.empty(inputLotId, capacity));
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get(), 2));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, emptyFoup);

        int perTick = slicer.energyPerTick();
        helper.assertTrue(perTick > 0, "the slicer draws " + perTick + " FE per tick");
        slicer.setStoredEnergy(perTick * (slicer.sliceTicks() + 2));

        runSlicer(slicer, slicer.sliceTicks() + 5);

        ItemStack output = slicer.outputSlot();
        helper.assertFalse(output.isEmpty(), "the slicer produced nothing");
        helper.assertTrue(output.getItem() == ModItems.FOUP_MK2.get(),
            "the output is not a FOUP of the same item type but " + output.getItem());

        FoupContents lot = FoupItems.trustedContentsOf(output);
        int expected = WaferSlicing.wafersFor(EUVCRAFTMod.settings(), capacity);
        helper.assertValueEqual(expected, lot.size(),
            "the lot holds the wrong number of wafers");
        helper.assertValueEqual(capacity, lot.capacity(),
            "the lot did not keep the input FOUP's capacity");
        helper.assertTrue(lot.run() == null,
            "a freshly sliced lot must carry no run spec, but carried one");
        helper.assertFalse(lot.lotId().equals(inputLotId),
            "the produced lot reused the input lot id instead of drawing a fresh one");
        for (Wafer wafer : lot.wafers()) {
            helper.assertTrue(wafer.isUnprocessed(),
                "a freshly sliced wafer must be unprocessed, but one was at layer "
                    + wafer.layerIndex());
        }

        // The inputs are consumed exactly when the product is written.
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the input FOUP was not consumed");
        helper.assertValueEqual(1, slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).getCount(),
            "exactly one boule should have been consumed");
        LOGGER.info("[M1-04] wafer_slicer_produces_a_lot: {} -> {} wafers, capacity {}, no run spec",
            output.getItem(), lot.size(), lot.capacity());

        // Refused input: a boule in the FOUP slot is not a valid input and never runs.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity idle = slicerAt(helper, SECOND);
        if (idle != null) {
            idle.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
            idle.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get()));
            idle.setStoredEnergy(1_000_000);
            runSlicer(idle, 40);
            helper.assertValueEqual(0, idle.sliceProgress(),
                "the slicer ran on a boule in its FOUP slot");
            helper.assertTrue(idle.outputSlot().isEmpty(),
                "the slicer produced output from a non-FOUP input");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. The lot is a real lot: layer 1 can be applied to it
    // ------------------------------------------------------------------

    private static void slicerLotTakesLayerOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
            FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        slicer.setStoredEnergy(slicer.energyPerTick() * (slicer.sliceTicks() + 2));
        runSlicer(slicer, slicer.sliceTicks() + 5);

        FoupContents lot = FoupItems.trustedContentsOf(slicer.outputSlot());
        helper.assertFalse(lot.isEmpty(), "the slicer produced an empty lot");

        var tiers = TierDataLoader.getRegistry();
        helper.assertTrue(tiers != null, "no tier registry");
        if (tiers == null) {
            return;
        }
        Tier t1 = tiers.getById("T1");
        helper.assertValueEqual(1, t1.layers(),
            "T1 is a single-layer tier, so layer 1 finishes the lot");

        FoupContents processed = FabYieldService.applyLayerStep(level, lot, t1, "logic", null, 1, 0.1);
        helper.assertValueEqual(lot.size(), processed.size(),
            "the layer step changed the wafer count");
        helper.assertTrue(processed.run() != null,
            "layer 1 must freeze a run spec into the lot");
        helper.assertTrue(processed.isStarted(),
            "a lot with one layer must count as started");
        for (Wafer wafer : processed.wafers()) {
            helper.assertValueEqual(1, wafer.layerIndex(),
                "every wafer should sit at layer 1 after one layer step");
        }
        LOGGER.info("[M1-04] wafer_slicer_lot_takes_layer_one: {} wafers processed at T1 for logic, "
                + "run spec frozen", processed.size());

        // Refused input: the same lot at a die type that T1 cannot make is turned away, so the
        // positive result above is not just "applyLayerStep never throws".
        boolean refusedDieType = false;
        try {
            FabYieldService.applyLayerStep(level, lot, t1, "compute", null, 1, 0.1);
        } catch (IllegalArgumentException expected) {
            refusedDieType = true;
        }
        helper.assertTrue(refusedDieType,
            "the yield service accepted a die type that cannot be made at T1");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. The server refuses a lot that already holds wafers or a run spec
    // ------------------------------------------------------------------

    private static void slicerRefusesUsedFoup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        ServerPlayer player = connect(level);
        WaferSlicerMenu menu = new WaferSlicerMenu(1, player.getInventory(), slicer);

        Slot bouleSlot = menu.slots.get(WaferSlicerBlockEntity.BOULE_SLOT);
        Slot foupSlot = menu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);
        Slot outputSlot = menu.slots.get(WaferSlicerBlockEntity.OUTPUT_SLOT);

        ItemStack empty = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        helper.assertTrue(foupSlot.mayPlace(empty), "the FOUP slot refused an empty FOUP");

        ItemStack filled = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(level, UUID.randomUUID(), 25, 3, 2));
        helper.assertFalse(foupSlot.mayPlace(filled),
            "the FOUP slot accepted a lot that already holds wafers");

        Tier t1 = TierDataLoader.getRegistry().getById("T1");
        ItemStack started = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.empty(UUID.randomUUID(), 25).withRun(RunSpec.of(t1, "logic", null,
                EUVCRAFTMod.settings())));
        helper.assertFalse(foupSlot.mayPlace(started),
            "the FOUP slot accepted a lot that carries a run spec");

        // Refused input: a plain item, a boule, and a FOUP item with no lot in it.
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the FOUP slot accepted a plain iron ingot");
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(ModItems.SILICON_BOULE.get())),
            "the FOUP slot accepted a boule");
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(ModItems.FOUP_MK1.get())),
            "the FOUP slot accepted a FOUP item that carries no lot");
        helper.assertFalse(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON.get())),
            "the boule slot accepted raw silicon");
        helper.assertTrue(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON_BOULE.get())),
            "the boule slot refused a boule");
        helper.assertFalse(outputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the output slot accepted placement");
        helper.assertTrue(outputSlot.mayPickup(player), "the output slot refuses pickup");

        // The server refuses the same stack even when it is put in the slot directly, so
        // the check is not only a menu rule.
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, filled);
        slicer.setStoredEnergy(1_000_000);
        runSlicer(slicer, 40);
        helper.assertValueEqual(0, slicer.sliceProgress(),
            "the server ticked on a FOUP that already holds wafers");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the server produced a lot from a FOUP that already holds wafers");
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).is(filled.getItem()),
            "the refused FOUP was consumed anyway");

        // And a started lot is refused the same way.
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, started);
        runSlicer(slicer, 40);
        helper.assertValueEqual(0, slicer.sliceProgress(),
            "the server ticked on a FOUP that carries a run spec");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the server produced a lot from a FOUP that carries a run spec");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
            "the server consumed a boule for a lot it refused");
        LOGGER.info("[M1-04] wafer_slicer_refuses_a_used_foup: the server refused filled, started "
            + "and component-less stacks, in the menu and in the tick");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. Progress pauses with no energy and resumes
    // ------------------------------------------------------------------

    private static void slicerPausesWithoutEnergy(GameTestHelper helper) {
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
            FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));

        // Three ticks of energy: the run starts and then stops.
        int perTick = slicer.energyPerTick();
        helper.assertTrue(perTick > 0, "the slicer draws " + perTick + " FE per tick");
        slicer.setStoredEnergy(perTick * 3);

        for (int tick = 0; tick < 6; tick++) {
            slicer.serverTick();
        }

        helper.assertTrue(slicer.isPaused(), "the slicer is not marked paused");
        int progressBefore = slicer.sliceProgress();
        helper.assertTrue(progressBefore > 0,
            "the slicer made no progress at all with three ticks of energy");
        helper.assertTrue(progressBefore < slicer.sliceTicksRemaining(),
            "the slicer finished with only three ticks of energy");
        helper.assertTrue(slicer.outputSlot().isEmpty(), "output appeared while paused");
        helper.assertValueEqual(0L, slicer.buffer().getAmountAsLong(), "energy was left after pausing");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the input FOUP was consumed before the lot was written");
        LOGGER.info("[M1-04] wafer_slicer_pauses_without_energy: paused at {}/{}",
            progressBefore, slicer.sliceTicksRemaining());

        // Progress must not creep forward with no energy.
        for (int tick = 0; tick < 50; tick++) {
            slicer.serverTick();
        }
        helper.assertValueEqual(progressBefore, slicer.sliceProgress(),
            "progress advanced with no energy");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the paused slicer produced output");

        // Recharge and let it finish: a paused run resumes rather than restarting.
        slicer.setStoredEnergy(perTick * slicer.sliceTicksRemaining());
        runSlicer(slicer, slicer.sliceTicksRemaining() + 5);
        helper.assertFalse(slicer.outputSlot().isEmpty(),
            "the paused slicer did not resume and finish");
        FoupContents lot = FoupItems.trustedContentsOf(slicer.outputSlot());
        helper.assertValueEqual(WaferSlicing.wafersFor(EUVCRAFTMod.settings(), lot.capacity()),
            lot.size(), "the resumed slicer produced the wrong lot size");

        // A slicer that is never charged never produces anything.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity starved = slicerAt(helper, SECOND);
        if (starved != null) {
            starved.setSlot(WaferSlicerBlockEntity.BOULE_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get()));
            starved.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
                FoupItems.emptyFoup(ModItems.FOUP_MK2.get()));
            starved.setStoredEnergy(0);
            runSlicer(starved, 100);
            helper.assertValueEqual(0, starved.sliceProgress(),
                "an uncharged slicer made progress");
            helper.assertTrue(starved.outputSlot().isEmpty(),
                "an uncharged slicer produced output");
            helper.assertFalse(starved.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
                "an uncharged slicer consumed a boule");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. Progress survives a save and load in the middle of a slice
    // ------------------------------------------------------------------

    private static void slicerSaveLoadRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
            FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        int perTick = slicer.energyPerTick();
        slicer.setStoredEnergy(perTick * slicer.sliceTicks());

        // Run halfway, then stop.
        int halfway = Math.max(1, slicer.sliceTicks() / 2);
        for (int tick = 0; tick < halfway; tick++) {
            slicer.serverTick();
        }
        int progressBefore = slicer.sliceProgress();
        long energyBefore = slicer.buffer().getAmountAsLong();
        helper.assertTrue(progressBefore > 0 && progressBefore < slicer.sliceTicks(),
            "the test did not get part-way through a slice: progress=" + progressBefore);
        helper.assertTrue(slicer.outputSlot().isEmpty(), "the slice finished too early");

        CompoundTag tag;
        TagValueOutput output = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        slicer.saveWithId(output);
        tag = output.buildResult();
        helper.assertTrue(tag.contains(ContainerHelper.TAG_ITEMS), "the saved tag has no Items entry");
        helper.assertTrue(tag.contains("energy"), "the saved tag has no energy entry");
        helper.assertTrue(tag.contains("slicer"), "the saved tag has no slice progress entry");

        // Wipe everything, then load it back.
        slicer.resetSlice();
        slicer.clearContent();
        slicer.setStoredEnergy(0);
        helper.assertTrue(slicer.isEmpty(), "the wipe failed (items)");
        helper.assertValueEqual(0L, slicer.buffer().getAmountAsLong(), "the wipe failed (energy)");
        helper.assertValueEqual(0, slicer.sliceProgress(), "the wipe failed (progress)");

        slicer.loadWithComponents(
            TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));

        helper.assertValueEqual(progressBefore, slicer.sliceProgress(),
            "the slice progress did not survive the round trip");
        helper.assertValueEqual(slicer.sliceTicks(), slicer.sliceTicksRemaining(),
            "the slice length did not survive the round trip");
        helper.assertValueEqual(energyBefore, slicer.buffer().getAmountAsLong(),
            "the stored energy did not survive the round trip");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
            "the boule did not survive the round trip");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the input FOUP did not survive the round trip");
        LOGGER.info("[M1-04] wafer_slicer_save_load_round_trip: {} FE and progress {}/{} restored",
            energyBefore, progressBefore, slicer.sliceTicksRemaining());

        // The restored run resumes instead of starting again.
        slicer.setStoredEnergy(perTick * slicer.sliceTicks());
        runSlicer(slicer, slicer.sliceTicks() + 5);
        helper.assertFalse(slicer.outputSlot().isEmpty(),
            "the restored slice did not finish after recharging");
        FoupContents lot = FoupItems.trustedContentsOf(slicer.outputSlot());
        helper.assertValueEqual(WaferSlicing.wafersFor(EUVCRAFTMod.settings(), lot.capacity()),
            lot.size(), "the restored slice produced the wrong lot size");
        helper.assertTrue(lot.run() == null, "the restored slice produced a started lot");

        // A fresh machine has no progress to restore.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity fresh = slicerAt(helper, SECOND);
        if (fresh != null) {
            helper.assertValueEqual(0, fresh.sliceProgress(),
                "a fresh slicer reported progress");
            helper.assertFalse(fresh.isSlicing(),
                "a fresh slicer claims to be slicing");
            helper.assertTrue(fresh.outputSlot().isEmpty(),
                "a fresh slicer produced output");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. The Crystal Puller runs its data recipe end to end, four silicon per boule
    // ------------------------------------------------------------------

    private static void pullerRunsItsRecipe(GameTestHelper helper) {
        helper.setBlock(MACHINE, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity puller = pullerAt(helper, MACHINE);
        helper.assertTrue(puller != null, "no crystal puller block entity");
        if (puller == null) {
            return;
        }
        helper.assertTrue(puller instanceof CrystalPullerBlockEntity,
            "the crystal puller block produced the wrong block entity type");

        MachineRecipe recipe = pullerRecipe();
        helper.assertTrue(recipe != null,
            "no crystal_puller recipe loaded from data/lithmod/machine_recipes");
        if (recipe == null) {
            return;
        }
        helper.assertValueEqual(recipe.ticks(), 400,
            "the recipe length is not the placeholder 400 ticks");
        helper.assertValueEqual(4, recipe.ingredientCounts().get(0),
            "the recipe does not ask for four silicon");

        int siliconIn = 4;
        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
            new ItemStack(ModItems.SILICON.get(), siliconIn));

        // Drive the machine the way the game does so the definition's energy cost applies.
        puller.serverTick();
        int perTick = puller.runner().energyPerTick();
        helper.assertTrue(perTick > 0,
            "the puller's energy per tick is " + perTick + "; the definition was not applied");
        long energyBefore = puller.buffer().getAmountAsLong();
        puller.setStoredEnergy(perTick * (recipe.ticks() + 2));

        for (int tick = 0; tick < recipe.ticks() + 5; tick++) {
            if (!puller.outputSlot().isEmpty()) {
                break;
            }
            puller.serverTick();
        }

        ItemStack output = puller.outputSlot();
        helper.assertFalse(output.isEmpty(), "the crystal puller produced nothing");
        helper.assertTrue(output.getItem() == ModItems.SILICON_BOULE.get(),
            "the puller's product is not a boule but " + output.getItem());
        helper.assertValueEqual(1, output.getCount(), "the puller produced the wrong count");
        helper.assertFalse(puller.runner().isRunning(), "the recipe did not finish");

        long spent = energyBefore + (long) perTick * (recipe.ticks() + 2)
            - puller.buffer().getAmountAsLong();
        helper.assertValueEqual((long) perTick * recipe.ticks(), spent,
            "the puller did not spend exactly energy_per_tick * ticks");

        // The recipe's own counts are what is consumed: four silicon for one boule, all
        // four of them, not one.
        ItemStack leftover = puller.slot(CrystalPullerBlockEntity.INPUT_SLOT);
        helper.assertTrue(leftover.isEmpty(),
            "one boule left " + leftover.getCount() + " silicon behind; the recipe asks for four");
        LOGGER.info("[M1-04] crystal_puller_runs_its_recipe: {} FE/tick x {} ticks = {} FE spent, "
                + "{} boule, {} silicon left", perTick, recipe.ticks(), spent, output,
            leftover.getCount());

        // The puller with no input never runs, however full the buffer.
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity idle = pullerAt(helper, SECOND);
        if (idle != null) {
            idle.setStoredEnergy(1_000_000);
            for (int tick = 0; tick < 50; tick++) {
                idle.serverTick();
            }
            helper.assertFalse(idle.runner().isRunning(),
                "the puller ran a recipe with no input");
            helper.assertTrue(idle.outputSlot().isEmpty(),
                "the puller produced output with no input");
        }

        // Refused input: a boule in the puller's input slot is not silicon.
        helper.setBlock(THIRD, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity wrongInput = pullerAt(helper, THIRD);
        if (wrongInput != null) {
            wrongInput.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get(), 4));
            wrongInput.setStoredEnergy(1_000_000);
            for (int tick = 0; tick < recipe.ticks() + 5; tick++) {
                wrongInput.serverTick();
            }
            helper.assertTrue(wrongInput.outputSlot().isEmpty(),
                "the puller ran on a boule instead of silicon");
            helper.assertValueEqual(4, wrongInput.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
                "the puller consumed a boule it refused");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 7. Neither machine is reachable by an item handler or a hopper
    // ------------------------------------------------------------------

    private static void noItemHandler(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());

        BlockPos slicerPos = helper.absolutePos(MACHINE);
        BlockPos pullerPos = helper.absolutePos(SECOND);
        helper.assertFalse(slicerPos.equals(pullerPos), "the two positions are the same");

        for (BlockPos pos : new BlockPos[] {slicerPos, pullerPos}) {
            for (Direction side : Direction.values()) {
                helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, side) == null,
                    "an item handler capability exists on " + side + " at " + pos);
                helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, pos, side) == null,
                    "a fluid handler capability exists on " + side + " at " + pos);
            }
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, null) == null,
                "an item handler capability exists on a null side at " + pos);
            helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, pos, null) == null,
                "a fluid handler capability exists on a null side at " + pos);

            // Energy is exposed, and accepts FE from an adjacent machine.
            EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.WEST);
            helper.assertTrue(energy != null, "no energy capability at " + pos);
            if (energy != null) {
                int pushed = insert(energy, 500);
                helper.assertTrue(pushed > 0, "the energy capability at " + pos + " accepted nothing");
                helper.assertTrue(energy.getCapacityAsLong() > 0,
                    "the energy capability at " + pos + " has no capacity");
                long stored = energy.getAmountAsLong();
                int pulled;
                try (Transaction transaction = Transaction.openRoot()) {
                    pulled = energy.extract((int) stored, transaction);
                    transaction.commit();
                }
                helper.assertValueEqual(0, pulled,
                    "the energy capability at " + pos + " let FE be pulled back out");
                helper.assertValueEqual(stored, energy.getAmountAsLong(),
                    "the stored amount at " + pos + " changed while extraction was refused");
            }
        }

        // A hopper above each machine may neither push nor pull.
        FabMachineBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer != null) {
            slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get()));
            slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
                FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
            for (Direction side : Direction.values()) {
                ItemStack remainder =
                    net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                        null, slicer, new ItemStack(Items.IRON_INGOT), side);
                helper.assertValueEqual(1, remainder.getCount(),
                    "a hopper pushed into the slicer on " + side);
            }
            helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
                "a hopper changed the slicer's inventory");

            BlockPos hopperPos = helper.absolutePos(MACHINE.below());
            level.setBlock(hopperPos, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)
                level.getBlockEntity(hopperPos);
            helper.assertFalse(
                net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(level, hopper),
                "a hopper extracted from the wafer slicer");
            helper.assertTrue(hopper.isEmpty(), "a hopper received a wafer slicer item");
        }

        FabMachineBlockEntity puller = pullerAt(helper, SECOND);
        if (puller != null) {
            puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON.get(), 8));
            for (Direction side : Direction.values()) {
                ItemStack remainder =
                    net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                        null, puller, new ItemStack(Items.IRON_INGOT), side);
                helper.assertValueEqual(1, remainder.getCount(),
                    "a hopper pushed into the puller on " + side);
            }
            helper.assertValueEqual(8, puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
                "a hopper changed the puller's inventory");
        }

        // A chest does expose an item handler, so the nulls above are a real answer rather
        // than the capability system being unavailable.
        helper.setBlock(THIRD, net.minecraft.world.level.block.Blocks.CHEST);
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(THIRD), Direction.NORTH) != null,
            "a chest has no item handler either, so the null assertions are vacuous");
        LOGGER.info("[M1-04] silicon_machines_expose_no_item_handler: neither machine is reachable "
            + "by an item handler, a fluid handler or a hopper");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 8. The runner consumes the recipe's counts, and re-checks its inputs
    // ------------------------------------------------------------------

    private static void pullerRechecksItsInputs(GameTestHelper helper) {
        helper.setBlock(MACHINE, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity puller = pullerAt(helper, MACHINE);
        helper.assertTrue(puller != null, "no crystal puller block entity");
        if (puller == null) {
            return;
        }
        MachineRecipe recipe = pullerRecipe();
        helper.assertTrue(recipe != null, "no crystal_puller recipe loaded");
        if (recipe == null) {
            return;
        }

        // One tick so the runner picks the recipe up and the definition's cost applies.
        puller.serverTick();
        int cost = puller.runner().energyPerTick();
        helper.assertTrue(cost > 0, "the puller has no energy cost, so nothing below can run");
        if (cost <= 0) {
            return;
        }

        // Five silicon in, one boule out, one silicon left: the recipe's own count of four
        // is what leaves the slot.
        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 5));
        puller.setStoredEnergy(cost * (recipe.ticks() + 5));
        runPuller(puller, recipe.ticks() + 5);
        helper.assertFalse(puller.outputSlot().isEmpty(), "five silicon produced no boule");
        helper.assertValueEqual(1, puller.outputSlot().getCount(), "the boule count is wrong");
        helper.assertValueEqual(5 - recipe.ingredientCounts().get(0),
            puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
            "the puller consumed a different number of silicon than the recipe declares");

        // One silicon short of the recipe's count: no run at all, however much energy.
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity shortOf = pullerAt(helper, SECOND);
        if (shortOf != null) {
            int wanted = recipe.ingredientCounts().get(0);
            shortOf.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON.get(), wanted - 1));
            shortOf.setStoredEnergy(1_000_000);
            runPuller(shortOf, recipe.ticks() + 5);
            helper.assertValueEqual(0, shortOf.runner().progress(),
                "the puller ran a recipe it did not have the ingredients for");
            helper.assertTrue(shortOf.outputSlot().isEmpty(),
                "the puller produced a boule from " + (wanted - 1) + " silicon");
            helper.assertValueEqual(wanted - 1,
                shortOf.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
                "the puller consumed silicon from a run it never made");
        }

        // Taking the ingredient back out mid-run drops the run, writes nothing and costs
        // nothing.
        helper.setBlock(THIRD, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity removal = pullerAt(helper, THIRD);
        if (removal != null) {
            removal.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON.get(), 8));
            removal.setStoredEnergy(cost * (recipe.ticks() + 5));
            runPuller(removal, 10);
            helper.assertTrue(removal.runner().isRunning() && removal.runner().progress() > 0,
                "the test did not get the puller part-way through a recipe");
            long energyBefore = removal.buffer().getAmountAsLong();
            removal.clearSlot(CrystalPullerBlockEntity.INPUT_SLOT);
            removal.serverTick();
            helper.assertFalse(removal.runner().isRunning(),
                "the puller kept running after its ingredient was taken away");
            helper.assertValueEqual(0, removal.runner().progress(),
                "the puller kept its progress after its ingredient was taken away");
            helper.assertTrue(removal.outputSlot().isEmpty(),
                "the puller produced a boule after its ingredient was taken away");
            helper.assertValueEqual(energyBefore, removal.buffer().getAmountAsLong(),
                "the puller charged the player for the tick it dropped the run on");

            // Swapping the ingredient for something else mid-run is the same case.
            removal.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 8));
            removal.setStoredEnergy(cost * (recipe.ticks() + 5));
            runPuller(removal, 10);
            helper.assertTrue(removal.runner().isRunning(), "the puller did not start again");
            removal.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get(), 3));
            removal.serverTick();
            helper.assertFalse(removal.runner().isRunning(),
                "the puller kept running after its ingredient was swapped for a boule");
            helper.assertTrue(removal.outputSlot().isEmpty(),
                "the puller produced a boule from a swapped input");
            helper.assertValueEqual(3, removal.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
                "the puller consumed the boule that was swapped in");
        }
        LOGGER.info("[M1-04] crystal_puller_rechecks_its_inputs: 5 silicon -> 1 boule with 1 left, "
            + "3 silicon never start, removal and swap mid-run drop the run");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 9. The slicer re-checks the boule and the FOUP on every tick
    // ------------------------------------------------------------------

    private static void slicerRechecksItsInputs(GameTestHelper helper) {
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }
        int perTick = slicer.energyPerTick();
        int ticks = slicer.sliceTicks();
        helper.assertTrue(perTick > 0 && ticks > 0,
            "the slicer has no data to run on: " + perTick + " FE/tick over " + ticks + " ticks");
        if (perTick <= 0 || ticks <= 0) {
            return;
        }
        int half = Math.max(1, ticks / 2);

        // Taking the boule away mid-run drops the run and charges nothing for the tick.
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get(), 2));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        slicer.setStoredEnergy(perTick * (ticks + 2));
        runSlicer(slicer, half);
        helper.assertTrue(slicer.isSlicing() && slicer.sliceProgress() > 0,
            "the slicer did not get part-way through a run");
        long energyBefore = slicer.buffer().getAmountAsLong();
        slicer.clearSlot(WaferSlicerBlockEntity.BOULE_SLOT);
        slicer.serverTick();
        helper.assertFalse(slicer.isSlicing(),
            "the slicer kept running after its boule was taken away");
        helper.assertValueEqual(0, slicer.sliceProgress(),
            "the slicer kept its progress after its boule was taken away");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the slicer produced a lot after its boule was taken away");
        helper.assertValueEqual(energyBefore, slicer.buffer().getAmountAsLong(),
            "the slicer charged the player for the tick it dropped the run on");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the slicer consumed the FOUP of the run it dropped");

        // Swapping the boule for another item mid-run is refused, and the replacement stays.
        startRun(slicer, perTick, half, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON.get(), 5));
        slicer.serverTick();
        helper.assertFalse(slicer.isSlicing(),
            "the slicer kept running after its boule was swapped for raw silicon");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the slicer produced a lot from a swapped boule");
        helper.assertValueEqual(5, slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).getCount(),
            "the slicer consumed the silicon that was swapped in");

        // Swapping the FOUP for one that already holds wafers is refused too.
        startRun(slicer, perTick, half, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(helper.getLevel(), UUID.randomUUID(), 25, 4, 1)));
        slicer.serverTick();
        helper.assertFalse(slicer.isSlicing(),
            "the slicer kept running after its FOUP was swapped for a full one");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the slicer sliced into a FOUP that already held wafers");
        helper.assertValueEqual(4,
            FoupItems.trustedContentsOf(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT)).size(),
            "the slicer wrote into the full FOUP that was swapped in");

        // A blocked output pauses the run instead of dropping it, and costs no FE.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity blocked = slicerAt(helper, SECOND);
        if (blocked != null) {
            startRun(blocked, blocked.energyPerTick(), half, new ItemStack(ModItems.SILICON_BOULE.get()));
            int progressBefore = blocked.sliceProgress();
            long energyBeforeBlock = blocked.buffer().getAmountAsLong();
            blocked.setSlot(WaferSlicerBlockEntity.OUTPUT_SLOT,
                FoupItems.emptyFoup(ModItems.FOUP_MK2.get()));
            runSlicer(blocked, 30);
            helper.assertTrue(blocked.isPaused(),
                "a slicer with a full output slot is not marked paused");
            helper.assertTrue(blocked.isSlicing(),
                "a full output slot dropped the run instead of pausing it");
            helper.assertValueEqual(progressBefore, blocked.sliceProgress(),
                "a full output slot advanced the run");
            helper.assertValueEqual(energyBeforeBlock, blocked.buffer().getAmountAsLong(),
                "a full output slot cost the player FE");
            helper.assertTrue(blocked.slot(WaferSlicerBlockEntity.OUTPUT_SLOT)
                    .is(ModItems.FOUP_MK2.get()),
                "the slicer overwrote the FOUP in its output slot");

            // Clearing the output resumes the same run rather than starting a new one.
            blocked.clearSlot(WaferSlicerBlockEntity.OUTPUT_SLOT);
            blocked.setStoredEnergy(blocked.energyPerTick() * (ticks + 2));
            runSlicer(blocked, ticks + 5);
            helper.assertFalse(blocked.outputSlot().isEmpty(),
                "the paused slicer did not finish after the output was cleared");
            helper.assertTrue(blocked.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
                "the finished run did not consume exactly its one boule");
        }
        LOGGER.info("[M1-04] wafer_slicer_rechecks_its_inputs: boule removal, boule swap and FOUP "
            + "swap drop the run; a full output pauses it without spending FE");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 10. The lot size follows the stored FOUP capacity
    // ------------------------------------------------------------------

    private static void slicerLotSizeFollowsCapacity(GameTestHelper helper) {
        int perBoule = EUVCRAFTMod.settings().wafersPerBoule();
        int smallCapacity = Math.max(1, perBoule / 4);
        int largeCapacity = perBoule * 4;

        // A FOUP smaller than one boule's yield comes back filled to its own capacity.
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity small = slicerAt(helper, MACHINE);
        if (small != null) {
            sliceOnce(small, smallCapacity);
            FoupContents lot = FoupItems.trustedContentsOf(small.outputSlot());
            helper.assertFalse(lot.isEmpty(), "the small-FOUP slicer produced nothing");
            helper.assertValueEqual(smallCapacity, lot.size(),
                "a capacity " + smallCapacity + " FOUP must come back full, not at "
                    + perBoule + " wafers");
            helper.assertTrue(lot.isFull(),
                "a FOUP smaller than the yield must end up full");
        }

        // A FOUP larger than one boule's yield comes back partially filled.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity large = slicerAt(helper, SECOND);
        if (large != null) {
            sliceOnce(large, largeCapacity);
            FoupContents lot = FoupItems.trustedContentsOf(large.outputSlot());
            helper.assertFalse(lot.isEmpty(), "the large-FOUP slicer produced nothing");
            helper.assertValueEqual(perBoule, lot.size(),
                "a capacity " + largeCapacity + " FOUP must come back holding one boule's "
                    + perBoule + " wafers");
            helper.assertTrue(!lot.isFull(),
                "a FOUP larger than the yield must stay partially filled");
        }
        LOGGER.info("[M1-04] wafer_slicer_lot_size_follows_the_foup_capacity: capacity {} filled "
            + "to {}, capacity {} filled to {} of {}", smallCapacity, smallCapacity,
            largeCapacity, perBoule, largeCapacity);

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 11. The slicer menu: slot order, predicates and shift-click routing
    // ------------------------------------------------------------------

    private static void slicerMenuLayoutAndShiftClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        ServerPlayer player = connect(level);
        WaferSlicerMenu menu = new WaferSlicerMenu(1, player.getInventory(), slicer);

        // The registered MenuType builds the client-shaped menu; both must have the machine
        // slots first and the 36 player slots after, in the same order.
        Identifier blockId = Identifier.fromNamespaceAndPath("lithmod", "wafer_slicer");
        var menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.getValue(blockId);
        helper.assertTrue(menuType == SiliconMachines.WAFER_SLICER_MENU.get(),
            "lithmod:wafer_slicer menu type is not registered to the expected MenuType");
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        AbstractContainerMenu clientMenu = menuType.create(2, player.getInventory(), buffer);
        helper.assertTrue(clientMenu instanceof WaferSlicerMenu,
            "the registered MenuType factory did not return a WaferSlicerMenu");

        // 3 machine slots + 27 inventory + 9 hotbar.
        helper.assertValueEqual(39, menu.slots.size(), "the server menu has an unexpected slot count");
        helper.assertValueEqual(menu.slots.size(), clientMenu.slots.size(),
            "client and server menu slot layouts disagree");
        for (int index = 0; index < menu.slots.size(); index++) {
            helper.assertValueEqual(menu.slots.get(index).index, clientMenu.slots.get(index).index,
                "client/server slot index differs at " + index);
        }

        // The machine slots are the first three and they are bound to the machine itself.
        for (int index : new int[] {WaferSlicerBlockEntity.BOULE_SLOT,
                WaferSlicerBlockEntity.FOUP_SLOT, WaferSlicerBlockEntity.OUTPUT_SLOT}) {
            helper.assertTrue(menu.slots.get(index).container == slicer,
                "machine slot " + index + " is not bound to the wafer slicer");
            helper.assertTrue(clientMenu.slots.get(index).container != null,
                "client machine slot " + index + " has no container");
            helper.assertFalse(clientMenu.slots.get(index).container == slicer,
                "client machine slot " + index + " is bound to the server block entity");
        }

        // The predicates both sides can know agree, on the same stacks.
        Slot bouleSlot = menu.slots.get(WaferSlicerBlockEntity.BOULE_SLOT);
        Slot outputSlot = menu.slots.get(WaferSlicerBlockEntity.OUTPUT_SLOT);
        Slot foupSlot = menu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);
        Slot clientBoule = clientMenu.slots.get(WaferSlicerBlockEntity.BOULE_SLOT);
        Slot clientOutput = clientMenu.slots.get(WaferSlicerBlockEntity.OUTPUT_SLOT);
        Slot clientFoup = clientMenu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);
        ItemStack boule = new ItemStack(ModItems.SILICON_BOULE.get());
        ItemStack ingot = new ItemStack(Items.IRON_INGOT, 3);
        helper.assertTrue(bouleSlot.mayPlace(boule) && clientBoule.mayPlace(boule),
            "the boule slot refused a boule on one of the two sides");
        helper.assertFalse(bouleSlot.mayPlace(ingot) || clientBoule.mayPlace(ingot),
            "the boule slot accepted an iron ingot on one of the two sides");
        helper.assertFalse(outputSlot.mayPlace(ingot) || clientOutput.mayPlace(ingot),
            "the output slot accepted placement on one of the two sides");
        ItemStack emptyFoup = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        helper.assertTrue(foupSlot.mayPlace(emptyFoup) && clientFoup.mayPlace(emptyFoup),
            "the FOUP slot refused an empty FOUP on one of the two sides");
        helper.assertFalse(foupSlot.mayPlace(ingot) || clientFoup.mayPlace(ingot),
            "the FOUP slot accepted an iron ingot on one of the two sides");

        // Shift-click routing. Both menus share the inherited implementation; if the
        // machine slots were counted wrongly, these moves would land in player slots or
        // nowhere at all.
        player.getInventory().clearContent();
        int hotbar = menu.slots.size() - 9;
        player.getInventory().setItem(0, new ItemStack(ModItems.SILICON_BOULE.get(), 2));
        player.getInventory().setItem(1, emptyFoup);
        player.getInventory().setItem(2, ingot);
        player.containerMenu = menu;

        ItemStack moved = menu.quickMoveStack(player, hotbar);
        helper.assertFalse(moved.isEmpty(), "shift-clicking a boule moved nothing");
        helper.assertValueEqual(2, slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).getCount(),
            "the boule did not land in the boule slot");
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the boule landed in the FOUP slot instead");

        ItemStack foupMoved = menu.quickMoveStack(player, hotbar + 1);
        helper.assertFalse(foupMoved.isEmpty(), "shift-clicking a FOUP moved nothing");
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).is(ModItems.FOUP_MK1.get()),
            "the FOUP did not land in the FOUP slot");

        ItemStack ingotMoved = menu.quickMoveStack(player, hotbar + 2);
        helper.assertTrue(ingotMoved.isEmpty(),
            "shift-clicking an iron ingot was routed into a machine slot");
        helper.assertValueEqual(3, player.getInventory().getItem(2).getCount(),
            "the refused iron ingot left the player inventory");

        // With the FOUP slot empty, a FOUP that already holds wafers is still refused.
        slicer.clearSlot(WaferSlicerBlockEntity.FOUP_SLOT);
        player.getInventory().setItem(3, FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(level, UUID.randomUUID(), 25, 2, 1)));
        ItemStack filledMoved = menu.quickMoveStack(player, hotbar + 3);
        helper.assertTrue(filledMoved.isEmpty(),
            "shift-clicking a FOUP that holds wafers was routed into the machine");
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "a FOUP that holds wafers landed in the FOUP slot");

        // Machine slot to player: both an input and the output slot move back out.
        ItemStack back = menu.quickMoveStack(player, WaferSlicerBlockEntity.BOULE_SLOT);
        helper.assertFalse(back.isEmpty(), "shift-clicking the boule out of the machine moved nothing");
        helper.assertTrue(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
            "the boule stayed in the machine after a shift-click out");

        slicer.setSlot(WaferSlicerBlockEntity.OUTPUT_SLOT, emptyFoup.copy());
        ItemStack productOut = menu.quickMoveStack(player, WaferSlicerBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(productOut.isEmpty(),
            "shift-clicking the product out of the machine moved nothing");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the product stayed in the machine after a shift-click out");
        LOGGER.info("[M1-04] wafer_slicer_menu_layout_and_shift_click: {} slots, machine slots first, "
            + "boule and FOUP routed in, ingot and used FOUP refused, output routed out",
            menu.slots.size());

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 12. What the client can predict about a FOUP, and what it cannot
    // ------------------------------------------------------------------

    private static void slicerFoupSlotBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSlicer(helper, MACHINE);
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        if (slicer == null) {
            helper.fail("no wafer slicer block entity");
            return;
        }

        ServerPlayer player = connect(level);
        WaferSlicerMenu menu = new WaferSlicerMenu(1, player.getInventory(), slicer);
        var menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.getValue(
            Identifier.fromNamespaceAndPath("lithmod", "wafer_slicer"));
        helper.assertTrue(menuType == SiliconMachines.WAFER_SLICER_MENU.get(),
            "lithmod:wafer_slicer menu type is not registered to the expected MenuType");
        AbstractContainerMenu clientMenu = menuType.create(3, player.getInventory(),
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess()));
        helper.assertTrue(clientMenu instanceof WaferSlicerMenu,
            "the registered MenuType factory did not return a WaferSlicerMenu");
        Slot serverFoup = menu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);
        Slot clientFoup = clientMenu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);

        // What a client actually holds: the public view, so a real wafer count and no run
        // spec and no rolls. A full one is refused from the public count alone, on both
        // menus, because that is the one thing both sides can see.
        ItemStack clientViewFull = clientView(25, 6);
        helper.assertFalse(WaferSlicerBlockEntity.acceptsFoupStack(clientViewFull),
            "a client-view FOUP that visibly holds wafers was predicted acceptable");
        helper.assertFalse(clientFoup.mayPlace(clientViewFull),
            "the client slot accepted a client-view FOUP the player can see is full");
        helper.assertFalse(serverFoup.mayPlace(clientViewFull),
            "the server slot accepted a client-view FOUP that holds wafers");

        // An empty client view must be predicted acceptable: the client cannot know about a
        // hidden run spec, because the network form does not carry one.
        ItemStack clientViewEmpty = clientView(25, 0);
        helper.assertTrue(WaferSlicerBlockEntity.acceptsFoupStack(clientViewEmpty),
            "an empty client-view FOUP was refused on a count it can see is zero");
        helper.assertTrue(clientFoup.mayPlace(clientViewEmpty),
            "the client slot refused an empty client-view FOUP");

        // The server refuses every client view outright, so a placeholder never becomes a
        // machine input and never produces a lot.
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, clientViewEmpty);
        slicer.setStoredEnergy(1_000_000);
        runSlicer(slicer, 40);
        helper.assertValueEqual(0, slicer.sliceProgress(),
            "the slicer ran on a client-view FOUP");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the slicer produced a lot from a client-view FOUP");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
            "the slicer consumed a boule for a client-view FOUP");
        helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(),
            "the slicer consumed the client-view FOUP it refused");

        // A real lot holding wafers is refused by the trusted read, on both the slot and
        // the tick.
        ItemStack realFull = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(level, UUID.randomUUID(), 25, 6, 1));
        helper.assertFalse(WaferSlicerBlockEntity.acceptsFoupStack(realFull),
            "a real FOUP holding wafers was predicted acceptable");
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(realFull) == null,
            "the trusted read accepted a real FOUP holding wafers");
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(clientViewEmpty) == null,
            "the trusted read accepted a client view as if it were a lot");
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(
                FoupItems.emptyFoup(ModItems.FOUP_MK1.get())) != null,
            "the trusted read refused a real empty FOUP");
        LOGGER.info("[M1-04] wafer_slicer_foup_slot_client_boundary: the public wafer count is "
            + "refused on both sides, the hidden run spec is invisible to the client and "
            + "every client view is refused by the server");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 13. The energy buffer and the insert limit follow the loaded definition
    // ------------------------------------------------------------------

    private static void energyFollowsDefinitions(GameTestHelper helper) {
        placeSlicer(helper, MACHINE);
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());
        WaferSlicerBlockEntity slicer = slicerAt(helper, MACHINE);
        FabMachineBlockEntity puller = pullerAt(helper, SECOND);
        helper.assertTrue(slicer != null && puller != null, "a machine block entity is missing");
        if (slicer == null || puller == null) {
            return;
        }

        MachineDefinition slicerDefinition = slicer.definition().orElse(null);
        MachineDefinition pullerDefinition = puller.definition().orElse(null);
        helper.assertTrue(slicerDefinition != null && pullerDefinition != null,
            "a machine definition did not load from data/lithmod/machines");
        if (slicerDefinition == null || pullerDefinition == null) {
            return;
        }

        // The buffer is sized from the definition, not from a number in Java.
        helper.assertValueEqual(slicerDefinition.energyCapacity(),
            slicer.buffer().getCapacityAsInt(),
            "the slicer's buffer capacity is not the definition's energy_capacity");
        helper.assertValueEqual(pullerDefinition.energyCapacity(),
            puller.buffer().getCapacityAsInt(),
            "the puller's buffer capacity is not the definition's energy_capacity");
        helper.assertValueEqual(slicer.buffer().getCapacityAsInt(),
            slicer.data().get(FabMachineBlockEntity.DATA_CAPACITY),
            "the slicer's synced capacity does not match its real buffer");
        helper.assertValueEqual(slicerDefinition.paramOrDefault("slice_ticks", 0),
            slicer.sliceTicks(), "the slice length is not the definition's slice_ticks");
        helper.assertValueEqual(pullerDefinition.energyPerTick(null, EUVCRAFTMod.settings()),
            puller.runner().energyPerTick(),
            "the puller's per-tick cost is not the definition's energy_per_tick");
        helper.assertValueEqual(slicerDefinition.energyPerTick(null, EUVCRAFTMod.settings()),
            slicer.energyPerTick(),
            "the slicer's per-tick cost is not the definition's energy_per_tick");

        // The per-operation insert limit follows max_receive, and repeated inserts fill the
        // buffer to exactly energy_capacity.
        int maxReceive = pullerDefinition.maxReceive();
        helper.assertValueEqual(maxReceive, insert(puller.energy(), maxReceive * 4),
            "one insert took more than max_receive");
        helper.assertValueEqual(maxReceive, (int) puller.buffer().getAmountAsLong(),
            "the first insert did not take exactly max_receive");
        for (int call = 0; call < 40; call++) {
            insert(puller.energy(), maxReceive);
        }
        helper.assertValueEqual(pullerDefinition.energyCapacity(),
            (int) puller.buffer().getAmountAsLong(),
            "repeated inserts did not fill the buffer to energy_capacity");
        helper.assertValueEqual(0, insert(puller.energy(), maxReceive),
            "a full buffer accepted more energy");

        // A different definition resizes the real buffer, keeps the energy already stored
        // and moves the insert limit with it. This is the data-change path the tick and
        // the load use; the definitions here are built by hand because a GameTest cannot
        // reload the data pack.
        puller.setStoredEnergy(pullerDefinition.energyCapacity());
        MachineDefinition smaller = new MachineDefinition(CrystalPullerBlockEntity.MACHINE_ID,
            pullerDefinition.energyCapacity() / 5, maxReceive / 5,
            pullerDefinition.energyPerTick(), Map.of(), Map.of());
        puller.applyDefinition(smaller);

        helper.assertValueEqual(smaller.energyCapacity(), puller.buffer().getCapacityAsInt(),
            "the real buffer did not take the new capacity");
        helper.assertValueEqual(smaller.energyCapacity(),
            puller.data().get(FabMachineBlockEntity.DATA_CAPACITY),
            "the synced capacity did not take the new capacity");
        helper.assertValueEqual((long) pullerDefinition.energyCapacity(),
            puller.buffer().getAmountAsLong(),
            "lowering the capacity destroyed the energy already stored");
        helper.assertValueEqual(0, insert(puller.energy(), smaller.maxReceive()),
            "a buffer holding more than its new capacity accepted energy");

        // Spending the surplus still works, and only then does the buffer take energy
        // again: NeoForge's contract is that insertion returns zero until there is room.
        long surplus = puller.buffer().getAmountAsLong() - (long) smaller.energyCapacity() + 1;
        puller.energySource().draw((int) surplus);
        helper.assertValueEqual((long) smaller.energyCapacity() - 1,
            puller.buffer().getAmountAsLong(),
            "the machine could not spend the surplus above its new capacity");
        helper.assertValueEqual(1, insert(puller.energy(), smaller.energyCapacity()),
            "a buffer one FE below its new capacity did not take that FE");

        // And from empty, one insert takes the new limit and repeated inserts stop at the
        // new capacity.
        puller.energySource().draw(Integer.MAX_VALUE);
        helper.assertValueEqual(0L, puller.buffer().getAmountAsLong(),
            "the buffer could not be emptied through its own draw");
        helper.assertValueEqual(smaller.maxReceive(), insert(puller.energy(), Integer.MAX_VALUE),
            "the per-operation insert limit did not follow the new definition");
        for (int call = 0; call < 40; call++) {
            insert(puller.energy(), smaller.maxReceive());
        }
        helper.assertValueEqual(smaller.energyCapacity(), (int) puller.buffer().getAmountAsLong(),
            "repeated inserts did not stop at the new capacity");

        // Putting the loaded definition back restores the real numbers.
        puller.applyDefinition(pullerDefinition);
        helper.assertValueEqual(pullerDefinition.energyCapacity(), puller.buffer().getCapacityAsInt(),
            "restoring the loaded definition did not restore the capacity");
        helper.assertValueEqual(pullerDefinition.energyCapacity(),
            puller.data().get(FabMachineBlockEntity.DATA_CAPACITY),
            "restoring the loaded definition did not restore the synced capacity");
        LOGGER.info("[M1-04] silicon_energy_follows_machine_definitions: puller {} FE capacity, "
            + "max_receive {}, per tick {}; slicer {} FE capacity, slice_ticks {}",
            pullerDefinition.energyCapacity(), maxReceive, puller.runner().energyPerTick(),
            slicerDefinition.energyCapacity(), slicer.sliceTicks());

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 14. Both machines fail closed while their definition is not loaded
    // ------------------------------------------------------------------

    /**
     * A machine whose definition is gone must not work, however it is set up.
     *
     * <p>The puller is part-way through a real recipe with a matching ingredient and a full
     * buffer when its definition is taken away, which is the case the old refresh could not
     * see: the capacity, the per-tick cost and the run all survived. The swap lasts only for
     * the synchronous body below and is put back in a {@code finally}, so the rest of the
     * suite sees the real registry.</p>
     */
    private static void machinesFailClosedWithoutDefinition(GameTestHelper helper) {
        helper.setBlock(MACHINE, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity puller = pullerAt(helper, MACHINE);
        MachineRecipe recipe = pullerRecipe();
        helper.assertTrue(puller != null && recipe != null,
            "the crystal puller or its recipe is missing");
        if (puller == null || recipe == null) {
            return;
        }

        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 8));
        puller.serverTick();
        int perTick = puller.runner().energyPerTick();
        helper.assertTrue(perTick > 0, "the puller has no per-tick cost, so nothing below can run");
        if (perTick <= 0) {
            return;
        }
        puller.setStoredEnergy(perTick * (recipe.ticks() + 2));
        runPuller(puller, 10);
        helper.assertTrue(puller.runner().isRunning() && puller.runner().progress() > 0,
            "the test did not get the puller part-way through a recipe");
        long energyBefore = puller.buffer().getAmountAsLong();
        int capacityBefore = puller.buffer().getCapacityAsInt();

        // The slicer is charged and loaded first, because a machine with no definition cannot
        // be charged through setStoredEnergy either: its capacity is then zero.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity slicer = slicerAt(helper, SECOND);
        helper.assertTrue(slicer != null, "no wafer slicer block entity");
        if (slicer == null) {
            return;
        }
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get(), 2));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        slicer.setStoredEnergy(slicer.energyPerTick() * (slicer.sliceTicks() + 2));
        long slicerEnergyBefore = slicer.buffer().getAmountAsLong();
        helper.assertTrue(slicerEnergyBefore > 0,
            "the slicer was not charged, so the removed definition has nothing to refuse");

        withoutDefinitions(List.of(CrystalPullerBlockEntity.MACHINE_ID,
                WaferSlicerBlockEntity.MACHINE_ID), () -> {
            runPuller(puller, 30);
            helper.assertFalse(puller.runner().isRunning(),
                "the puller kept running after its definition was removed");
            helper.assertValueEqual(0, puller.runner().progress(),
                "the puller kept its progress after its definition was removed");
            helper.assertTrue(puller.outputSlot().isEmpty(),
                "the puller produced a boule with no definition loaded");
            helper.assertValueEqual(energyBefore, puller.buffer().getAmountAsLong(),
                "the puller spent stored FE with no definition loaded");
            helper.assertValueEqual(8, puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
                "the puller consumed silicon with no definition loaded");
            helper.assertValueEqual(0, puller.buffer().getCapacityAsInt(),
                "the removed definition left the buffer its capacity");
            helper.assertValueEqual(0, puller.data().get(FabMachineBlockEntity.DATA_CAPACITY),
                "the removed definition left the menu its capacity");
            helper.assertValueEqual(0, insert(puller.energy(), 2_000),
                "the buffer accepted FE with no definition loaded");

            runSlicer(slicer, 40);
            helper.assertFalse(slicer.isSlicing(),
                "the slicer kept slicing after its definition was removed");
            helper.assertValueEqual(0, slicer.sliceProgress(),
                "the slicer kept its progress after its definition was removed");
            helper.assertTrue(slicer.outputSlot().isEmpty(),
                "the slicer produced a lot with no definition loaded");
            helper.assertValueEqual(slicerEnergyBefore, slicer.buffer().getAmountAsLong(),
                "the slicer spent stored FE with no definition loaded");
            helper.assertValueEqual(0, slicer.buffer().getCapacityAsInt(),
                "the removed definition left the slicer's buffer its capacity");
            helper.assertValueEqual(0, insert(slicer.energy(), 2_000),
                "the slicer's buffer accepted FE with no definition loaded");
            helper.assertFalse(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
                "the slicer consumed a boule with no definition loaded");
        });
        LOGGER.info("[M1-04] silicon_machines_fail_closed_without_a_definition: with no definition "
            + "both machines kept their stored FE, drew none, wrote nothing, accepted no FE and "
            + "published a capacity of 0");

        // The definition is back, so the machine works again on the FE it kept. One tick
        // re-applies it to the buffer; that tick has no energy to draw and so does nothing.
        puller.serverTick();
        helper.assertTrue(puller.definition().isPresent(),
            "the definition registry was not restored after the swap");
        helper.assertValueEqual(capacityBefore, puller.buffer().getCapacityAsInt(),
            "the restored definition did not restore the buffer capacity");
        helper.assertValueEqual(capacityBefore, puller.data().get(FabMachineBlockEntity.DATA_CAPACITY),
            "the restored definition did not restore the capacity the menu shows");
        helper.assertValueEqual(energyBefore, puller.buffer().getAmountAsLong(),
            "the machine could not even spend one tick of the FE it kept");
        puller.setStoredEnergy(perTick * (recipe.ticks() + 2));
        runPuller(puller, recipe.ticks() + 5);
        helper.assertFalse(puller.outputSlot().isEmpty(),
            "the puller did not run again once its definition came back");
        helper.assertTrue(puller.outputSlot().getItem() == ModItems.SILICON_BOULE.get(),
            "the resumed puller produced " + puller.outputSlot().getItem());
        helper.assertTrue(slicer.definition().isPresent(),
            "the wafer slicer definition was not restored after the swap");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 15. A restored run is resumed only by the recipe it belongs to
    // ------------------------------------------------------------------

    /**
     * Progress restored from a save is handed back to the recipe that paid for it and to
     * nothing else.
     *
     * <p>Both machines here run the same recipe from the same data; the second one is
     * restored against a loaded recipe that takes the same silicon and runs twice as long
     * for twice the product, which is the conservation case: old progress must not be
     * handed to it.</p>
     */
    private static void pullerResumesOnlyTheSameRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(MACHINE, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity puller = pullerAt(helper, MACHINE);
        MachineRecipe recipe = pullerRecipe();
        helper.assertTrue(puller != null && recipe != null,
            "the crystal puller or its recipe is missing");
        if (puller == null || recipe == null) {
            return;
        }

        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 8));
        puller.serverTick();
        int perTick = puller.runner().energyPerTick();
        helper.assertTrue(perTick > 0, "the puller has no per-tick cost, so nothing below can run");
        if (perTick <= 0) {
            return;
        }
        int half = Math.max(1, recipe.ticks() / 2);
        puller.setStoredEnergy(perTick * (recipe.ticks() + 2));
        runPuller(puller, half);
        int progressBefore = puller.runner().progress();
        helper.assertTrue(puller.runner().isRunning() && progressBefore > 0,
            "the test did not get the puller part-way through a recipe");

        CompoundTag tag = saveTag(level, puller);
        helper.assertTrue(tag.contains("recipe"), "the saved tag has no recipe node");

        // Wipe and load the unchanged recipe back: the same run resumes at the same cost.
        puller.runner().reset();
        puller.setStoredEnergy(0);
        loadTag(level, puller, tag);
        helper.assertValueEqual(progressBefore, puller.runner().progress(),
            "the recipe progress did not survive the round trip");
        helper.assertValueEqual(perTick, puller.runner().energyPerTick(),
            "the frozen per-tick cost did not survive the round trip");

        puller.setStoredEnergy(perTick * (recipe.ticks() + 2));
        puller.serverTick();
        helper.assertTrue(puller.runner().isRunning(),
            "the restored run did not re-attach to the recipe it was running");
        helper.assertValueEqual(progressBefore + 1, puller.runner().progress(),
            "the restored run did not continue from its own progress");
        helper.assertValueEqual(recipe.ticks(), puller.runner().ticks(),
            "the restored run adopted a different recipe length");
        helper.assertValueEqual(perTick, puller.runner().energyPerTick(),
            "the restored run was charged at a new rate");
        runPuller(puller, recipe.ticks() + 5);
        helper.assertFalse(puller.outputSlot().isEmpty(), "the restored run did not finish");
        helper.assertTrue(puller.outputSlot().getItem() == ModItems.SILICON_BOULE.get(),
            "the restored run produced " + puller.outputSlot().getItem());
        LOGGER.info("[M1-04] crystal_puller_resumes_only_the_same_recipe: resumed at {}/{} ticks "
            + "for {} FE/tick and finished; a changed recipe inherited nothing",
            progressBefore, recipe.ticks(), perTick);

        // The second machine is saved the same way, then restored against a loaded recipe
        // that takes the same silicon, runs twice as long and gives twice the product.
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity swapped = pullerAt(helper, SECOND);
        if (swapped == null) {
            helper.fail("no crystal puller block entity at the second position");
            return;
        }
        swapped.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 8));
        swapped.setStoredEnergy(perTick * (recipe.ticks() * 2 + 2));
        runPuller(swapped, half);
        helper.assertTrue(swapped.runner().isRunning(), "the second puller did not start a run");
        int swappedProgress = swapped.runner().progress();
        CompoundTag savedTag = saveTag(level, swapped);

        MachineRecipeSpec changedSpec = new MachineRecipeSpec(recipe.spec().machineId(),
            recipe.spec().ingredients(), new MachineResult("lithmod:silicon_boule", 2, Map.of()),
            recipe.spec().ticks() * 2, recipe.spec().energyPerTickOverride());
        withRecipes(List.of(changedSpec), () -> {
            swapped.runner().reset();
            swapped.setStoredEnergy(0);
            loadTag(level, swapped, savedTag);
            helper.assertValueEqual(swappedProgress, swapped.runner().progress(),
                "the changed-recipe case did not start from the saved numbers");

            swapped.setStoredEnergy(perTick * (changedSpec.ticks() + 2));
            swapped.serverTick();
            helper.assertValueEqual(1, swapped.runner().progress(),
                "the changed recipe inherited the progress the old one had paid for");
            helper.assertValueEqual(changedSpec.ticks(), swapped.runner().ticks(),
                "the changed recipe was not picked up as its own run");
            helper.assertTrue(swapped.outputSlot().isEmpty(),
                "the changed recipe wrote a product on its first tick");
        });
        helper.assertTrue(swapped.definition().isPresent(),
            "the definition registry was not restored after the swap");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 16. The two bundled cooking recipes are loaded and match real input
    // ------------------------------------------------------------------

    private static void cookingRecipesLoadAndMatch(GameTestHelper helper) {
        // The real lookup, against the live RecipeManager: the question is not whether the
        // files are shaped like the 26.3 examples but whether the datapack loaded them and
        // what they produce. Optional vanilla fields such as group are therefore allowed,
        // and every time and experience number still lives in the JSON.
        ServerLevel level = helper.getLevel();
        RecipeManager manager = level.getServer().getRecipeManager();
        SingleRecipeInput sand = new SingleRecipeInput(new ItemStack(Items.SAND));
        ItemStack smelted = ItemStack.EMPTY;
        ItemStack blasted = ItemStack.EMPTY;
        int smeltingTicks = -1;
        int blastingTicks = -1;

        Optional<RecipeHolder<SmeltingRecipe>> smelting =
            manager.getRecipeFor(RecipeType.SMELTING, sand, level);
        helper.assertTrue(smelting.isPresent(), "no smelting recipe matched a stack of sand");
        if (smelting.isPresent()) {
            helper.assertValueEqual(recipeKey("silicon_from_smelting"), smelting.get().id(),
                "the smelting recipe that matched sand is not the bundled one");
            helper.assertTrue(smelting.get().value().matches(sand, level),
                "the returned smelting recipe does not match the input it was found by");
            smelted = smelting.get().value().assemble(sand);
            helper.assertTrue(smelted.getItem() == ModItems.SILICON.get(),
                "smelting sand produced " + smelted.getItem() + " rather than silicon");
            if (smelting.get().value() instanceof AbstractCookingRecipe cooking) {
                smeltingTicks = cooking.cookingTime();
                helper.assertValueEqual(200, smeltingTicks,
                    "the smelting recipe did not keep cookingtime 200 from the JSON");
            } else {
                helper.fail("the smelting recipe that matched sand is not a cooking recipe");
            }
        }

        Optional<RecipeHolder<BlastingRecipe>> blasting =
            manager.getRecipeFor(RecipeType.BLASTING, sand, level);
        helper.assertTrue(blasting.isPresent(), "no blasting recipe matched a stack of sand");
        if (blasting.isPresent()) {
            helper.assertValueEqual(recipeKey("silicon_from_blasting"), blasting.get().id(),
                "the blasting recipe that matched sand is not the bundled one");
            blasted = blasting.get().value().assemble(sand);
            helper.assertTrue(blasted.getItem() == ModItems.SILICON.get(),
                "blasting sand produced " + blasted.getItem() + " rather than silicon");
            if (blasting.get().value() instanceof AbstractCookingRecipe cooking) {
                blastingTicks = cooking.cookingTime();
                helper.assertValueEqual(100, blastingTicks,
                    "the blasting recipe did not keep cookingtime 100 from the JSON");
            } else {
                helper.fail("the blasting recipe that matched sand is not a cooking recipe");
            }
        }

        // A boule is not sand, so the bundled recipes must not answer for it.
        SingleRecipeInput boule = new SingleRecipeInput(new ItemStack(ModItems.SILICON_BOULE.get()));
        helper.assertTrue(manager.getRecipeFor(RecipeType.SMELTING, boule, level)
                .map(holder -> recipeKey("silicon_from_smelting").equals(holder.id())).orElse(true),
            "a boule matched the bundled smelting recipe, which cooks sand");
        LOGGER.info("[M1-04] silicon_cooking_recipes_load_and_match: {} smelts to {} in {} ticks and "
            + "{} blasts to {} in {} ticks", Items.SAND, smelted, smeltingTicks, Items.SAND,
            blasted, blastingTicks);

        helper.succeed();
    }

    /** The recipe id one bundled cooking file is loaded under. */
    private static ResourceKey<Recipe<?>> recipeKey(String name) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("lithmod", name));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void placeSlicer(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, SiliconMachines.WAFER_SLICER.get());
    }

    private static @Nullable WaferSlicerBlockEntity slicerAt(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof WaferSlicerBlockEntity slicer
            ? slicer : null;
    }

    private static @Nullable CrystalPullerBlockEntity pullerAt(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof CrystalPullerBlockEntity puller
            ? puller : null;
    }

    /** Ticks the slicer until it stops making progress, with a hard bound. */
    private static void runSlicer(WaferSlicerBlockEntity slicer, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            slicer.serverTick();
        }
    }

    /** Ticks the puller until its output appears, with a hard bound. */
    private static void runPuller(FabMachineBlockEntity puller, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (!puller.outputSlot().isEmpty()) {
                return;
            }
            puller.serverTick();
        }
    }

    /** Fills a slicer with one boule and one empty FOUP of the given capacity, charged and run. */
    private static void sliceOnce(WaferSlicerBlockEntity slicer, int capacity) {
        int perTick = slicer.energyPerTick();
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.with(ModItems.FOUP_MK2.get(),
            FoupContents.empty(UUID.randomUUID(), capacity)));
        slicer.setStoredEnergy(perTick * (slicer.sliceTicks() + 2));
        runSlicer(slicer, slicer.sliceTicks() + 5);
    }

    /** Puts a fresh boule and empty FOUP into a slicer and runs it {@code ticks} ticks. */
    private static void startRun(WaferSlicerBlockEntity slicer, int perTick, int ticks,
            ItemStack boule) {
        slicer.clearContent();
        slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, boule);
        slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        slicer.setStoredEnergy(perTick * (slicer.sliceTicks() + 2));
        runSlicer(slicer, ticks);
    }

    /** A FOUP stack as a client holds it: the public view, with a real wafer count. */
    private static ItemStack clientView(int capacity, int wafers) {
        List<Wafer> placeholders = new ArrayList<>(wafers);
        for (int wafer = 0; wafer < wafers; wafer++) {
            placeholders.add(new Wafer(Wafer.UNPROCESSED_LAYER, FoupContentsCodec.CLIENT_VIEW_ROLL,
                Wafer.NO_FLAGS));
        }
        return FoupItems.with(ModItems.FOUP_MK1.get(),
            new FoupContents(FoupContentsCodec.CLIENT_VIEW_LOT_ID, capacity, placeholders));
    }

    /** Saves a block entity the way the server does and hands back its NBT. */
    private static CompoundTag saveTag(ServerLevel level, BlockEntity blockEntity) {
        TagValueOutput output = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveWithId(output);
        return output.buildResult();
    }

    /** Loads a block entity from an NBT the way the server does. */
    private static void loadTag(ServerLevel level, BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    /**
     * Runs {@code body} with {@code removedIds} absent from the loaded machine definitions.
     *
     * <p>The loader keeps the loaded registry on the instance the reload listener created and
     * offers no setter, so this writes that field and puts the previous registry back in a
     * {@code finally}. The swap is synchronous and lives only for the body, so nothing after
     * this test sees it, and no other machine's definition is touched.</p>
     */
    private static void withoutDefinitions(List<String> removedIds, Runnable body) {
        try {
            Field live = MachineDefinitionLoader.class.getDeclaredField("CURRENT");
            live.setAccessible(true);
            Field loaded = JsonResourceDataLoader.class.getDeclaredField("current");
            loaded.setAccessible(true);
            Object loader = live.get(null);
            MachineDefinitionLoader.Registry previous =
                (MachineDefinitionLoader.Registry) loaded.get(loader);
            try {
                List<MachineDefinition> kept = previous.all().stream()
                    .filter(definition -> !removedIds.contains(definition.id())).toList();
                loaded.set(loader, MachineDefinitionLoader.build(kept, "game test, without "
                    + removedIds));
                body.run();
            } finally {
                loaded.set(loader, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the machine definition registry could not be swapped", failure);
        }
    }

    /**
     * Runs {@code body} with {@code specs} as the whole loaded machine recipe set.
     *
     * <p>{@link MachineRecipeLoader} caches what it resolved in a static field for the same
     * reason {@link #withoutDefinitions} has to reach into one: it is the value every machine
     * reads, and there is no reload a game test can drive. The previous set is put back in a
     * {@code finally}.</p>
     */
    private static void withRecipes(List<MachineRecipeSpec> specs, Runnable body) {
        try {
            Field resolved = MachineRecipeLoader.class.getDeclaredField("resolved");
            resolved.setAccessible(true);
            MachineRecipeLoader.Registry previous = (MachineRecipeLoader.Registry) resolved.get(null);
            try {
                resolved.set(null, MachineRecipeLoader.build(specs, "game test recipes"));
                body.run();
            } finally {
                resolved.set(null, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the machine recipe registry could not be swapped", failure);
        }
    }

    /** Inserts through a real transaction and reports how much was taken. */
    private static int insert(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static @Nullable MachineRecipe pullerRecipe() {
        var recipes = MachineRecipeLoader.getRegistry();
        if (recipes == null) {
            return null;
        }
        for (MachineRecipe recipe
                : recipes.forMachine(CrystalPullerBlockEntity.MACHINE_ID)) {
            if (recipe.resultItem() == ModItems.SILICON_BOULE.get()) {
                return recipe;
            }
        }
        return null;
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "silicon-test"), false);
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
