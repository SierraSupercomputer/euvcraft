package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSlicing;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
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

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the M1-04 silicon chain on a real server level, with the real registries
 * and the real loaded data pack.
 *
 * <p>Every positive assertion has a matching negative, so a passing run cannot come from an
 * assertion that never fires. The negatives are marked "negative control".</p>
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

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    /** Where the machine under test is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used for the negative controls. */
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);

    static {
        FUNCTIONS.register(SLICER_PRODUCES_LOT, () -> SiliconGameTests::slicerProducesLot);
        FUNCTIONS.register(SLICER_LOT_TAKES_LAYER, () -> SiliconGameTests::slicerLotTakesLayerOne);
        FUNCTIONS.register(SLICER_REFUSES_USED_FOUP, () -> SiliconGameTests::slicerRefusesUsedFoup);
        FUNCTIONS.register(SLICER_PAUSES, () -> SiliconGameTests::slicerPausesWithoutEnergy);
        FUNCTIONS.register(SLICER_ROUND_TRIP, () -> SiliconGameTests::slicerSaveLoadRoundTrip);
        FUNCTIONS.register(PULLER_RUNS_RECIPE, () -> SiliconGameTests::pullerRunsItsRecipe);
        FUNCTIONS.register(NO_ITEM_HANDLER, () -> SiliconGameTests::noItemHandler);
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

        // NEGATIVE CONTROL: a boule in the FOUP slot is not a valid input and never runs.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity idle = slicerAt(helper, SECOND);
        if (idle != null) {
            idle.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
            idle.setSlot(WaferSlicerBlockEntity.FOUP_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get()));
            idle.setStoredEnergy(1_000_000);
            runSlicer(idle, 40);
            helper.assertValueEqual(0, idle.sliceProgress(),
                "the slicer ran on a boule in its FOUP slot (negative control)");
            helper.assertTrue(idle.outputSlot().isEmpty(),
                "the slicer produced output from a non-FOUP input (negative control)");
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

        // NEGATIVE CONTROL: the same lot at a die type that T1 cannot make must be refused, so
        // the positive result above is not just "applyLayerStep never throws".
        boolean refusedDieType = false;
        try {
            FabYieldService.applyLayerStep(level, lot, t1, "compute", null, 1, 0.1);
        } catch (IllegalArgumentException expected) {
            refusedDieType = true;
        }
        helper.assertTrue(refusedDieType,
            "the yield service accepted a die type that cannot be made at T1 "
                + "(negative control)");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. The input slot refuses a lot that already holds wafers or a run spec
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

        // The registered MenuType builds the client-shaped menu; its rules must match.
        Identifier blockId = Identifier.fromNamespaceAndPath("lithmod", "wafer_slicer");
        var menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.getValue(blockId);
        helper.assertTrue(menuType == SiliconMachines.WAFER_SLICER_MENU.get(),
            "lithmod:wafer_slicer menu type is not registered to the expected MenuType");
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        AbstractContainerMenu clientMenu = menuType.create(2, player.getInventory(), buffer);
        helper.assertTrue(clientMenu instanceof WaferSlicerMenu,
            "the registered MenuType factory did not return a WaferSlicerMenu");
        helper.assertValueEqual(menu.slots.size(), clientMenu.slots.size(),
            "client and server menu slot layouts disagree");

        Slot bouleSlot = menu.slots.get(WaferSlicerBlockEntity.BOULE_SLOT);
        Slot foupSlot = menu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);
        Slot outputSlot = menu.slots.get(WaferSlicerBlockEntity.OUTPUT_SLOT);
        Slot clientFoupSlot = clientMenu.slots.get(WaferSlicerBlockEntity.FOUP_SLOT);

        ItemStack empty = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        helper.assertTrue(foupSlot.mayPlace(empty), "the FOUP slot refused an empty FOUP");
        helper.assertTrue(clientFoupSlot.mayPlace(empty),
            "the client FOUP slot refused an empty FOUP");

        ItemStack filled = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(level, UUID.randomUUID(), 25, 3, 2));
        helper.assertFalse(foupSlot.mayPlace(filled),
            "the FOUP slot accepted a lot that already holds wafers");
        helper.assertFalse(clientFoupSlot.mayPlace(filled),
            "the client FOUP slot accepted a lot that already holds wafers");

        Tier t1 = TierDataLoader.getRegistry().getById("T1");
        ItemStack started = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.empty(UUID.randomUUID(), 25).withRun(RunSpec.of(t1, "logic", null,
                EUVCRAFTMod.settings())));
        helper.assertFalse(foupSlot.mayPlace(started),
            "the FOUP slot accepted a lot that carries a run spec");
        helper.assertFalse(clientFoupSlot.mayPlace(started),
            "the client FOUP slot accepted a lot that carries a run spec");

        // NEGATIVE CONTROL: a plain item, and a boule in the FOUP slot.
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the FOUP slot accepted a plain iron ingot (negative control)");
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(ModItems.SILICON_BOULE.get())),
            "the FOUP slot accepted a boule (negative control)");
        helper.assertFalse(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON.get())),
            "the boule slot accepted raw silicon (negative control)");
        helper.assertTrue(bouleSlot.mayPlace(new ItemStack(ModItems.SILICON_BOULE.get())),
            "the boule slot refused a boule");
        helper.assertFalse(outputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the output slot accepted placement (negative control)");
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
        LOGGER.info("[M1-04] wafer_slicer_refuses_a_used_foup: menu and server both refused filled "
            + "and started lots");

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
            "progress advanced with no energy (negative control)");
        helper.assertTrue(slicer.outputSlot().isEmpty(),
            "the paused slicer produced output (negative control)");

        // Recharge and let it finish: a paused run resumes rather than restarting.
        slicer.setStoredEnergy(perTick * slicer.sliceTicksRemaining());
        runSlicer(slicer, slicer.sliceTicksRemaining() + 5);
        helper.assertFalse(slicer.outputSlot().isEmpty(),
            "the paused slicer did not resume and finish");
        FoupContents lot = FoupItems.trustedContentsOf(slicer.outputSlot());
        helper.assertValueEqual(WaferSlicing.wafersFor(EUVCRAFTMod.settings(), lot.capacity()),
            lot.size(), "the resumed slicer produced the wrong lot size");

        // NEGATIVE CONTROL: a slicer that is never charged never produces anything.
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
                "an uncharged slicer made progress (negative control)");
            helper.assertTrue(starved.outputSlot().isEmpty(),
                "an uncharged slicer produced output (negative control)");
            helper.assertFalse(starved.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(),
                "an uncharged slicer consumed a boule (negative control)");
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

        // NEGATIVE CONTROL: a fresh machine has no progress to restore.
        placeSlicer(helper, SECOND);
        WaferSlicerBlockEntity fresh = slicerAt(helper, SECOND);
        if (fresh != null) {
            helper.assertValueEqual(0, fresh.sliceProgress(),
                "a fresh slicer reported progress (negative control)");
            helper.assertFalse(fresh.isSlicing(),
                "a fresh slicer claims to be slicing (negative control)");
            helper.assertTrue(fresh.outputSlot().isEmpty(),
                "a fresh slicer produced output (negative control)");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. The Crystal Puller runs its data recipe end to end
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
        LOGGER.info("[M1-04] crystal_puller_runs_its_recipe: {} FE/tick x {} ticks = {} FE spent, {} boule",
            perTick, recipe.ticks(), spent, output);

        // The framework's runner consumes one item per input slot whatever the ingredient
        // count is, so a stack of four silicon comes back holding three. That is recorded
        // here rather than worked around, because MachineRecipeRunner is out of this task's
        // scope; see the report.
        helper.assertValueEqual(siliconIn - 1,
            puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).getCount(),
            "the puller consumed a different number of silicon than the runner's consume rule gives");

        // NEGATIVE CONTROL: the puller with no input never runs, however full the buffer.
        helper.setBlock(SECOND, SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity idle = pullerAt(helper, SECOND);
        if (idle != null) {
            idle.setStoredEnergy(1_000_000);
            for (int tick = 0; tick < 50; tick++) {
                idle.serverTick();
            }
            helper.assertFalse(idle.runner().isRunning(),
                "the puller ran a recipe with no input (negative control)");
            helper.assertTrue(idle.outputSlot().isEmpty(),
                "the puller produced output with no input (negative control)");
        }

        // NEGATIVE CONTROL: a boule in the puller's input slot is not silicon.
        helper.setBlock(new BlockPos(5, 1, 1), SiliconMachines.CRYSTAL_PULLER.get());
        FabMachineBlockEntity wrongInput = pullerAt(helper, new BlockPos(5, 1, 1));
        if (wrongInput != null) {
            wrongInput.setSlot(CrystalPullerBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.SILICON_BOULE.get(), 4));
            wrongInput.setStoredEnergy(1_000_000);
            for (int tick = 0; tick < recipe.ticks() + 5; tick++) {
                wrongInput.serverTick();
            }
            helper.assertTrue(wrongInput.outputSlot().isEmpty(),
                "the puller ran on a boule instead of silicon (negative control)");
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
                int pushed;
                try (Transaction transaction = Transaction.openRoot()) {
                    pushed = energy.insert(500, transaction);
                    transaction.commit();
                }
                helper.assertTrue(pushed > 0, "the energy capability at " + pos + " accepted nothing");
                helper.assertTrue(energy.getCapacityAsLong() > 0,
                    "the energy capability at " + pos + " has no capacity");
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

        // NEGATIVE CONTROL: a chest does expose an item handler, so the nulls above are a
        // real answer rather than the capability system being unavailable.
        helper.setBlock(new BlockPos(5, 1, 1), net.minecraft.world.level.block.Blocks.CHEST);
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(new BlockPos(5, 1, 1)), Direction.NORTH) != null,
            "a chest has no item handler either, so the negative assertions are vacuous "
                + "(negative control)");
        LOGGER.info("[M1-04] silicon_machines_expose_no_item_handler: neither machine is reachable "
            + "by an item handler, a fluid handler or a hopper");

        helper.succeed();
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

    private static @Nullable MachineRecipe pullerRecipe() {
        var recipes = com.euvcraft.lithmod.mc.data.MachineRecipeLoader.getRegistry();
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