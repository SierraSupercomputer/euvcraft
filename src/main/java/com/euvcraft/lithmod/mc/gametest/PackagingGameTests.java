package com.euvcraft.lithmod.mc.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.PackagingRefusalReason;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.JsonResourceDataLoader;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageMenu;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Game tests for M1-10: the Test and Package machine, judged on a real server level with
 * the real registries, the real loaded data pack and the real
 * {@code data/lithmod/machines/test_package.json} definition.
 *
 * <p>Every case drives the machine through its own {@code serverTick}, so what is measured
 * here is the same code path the game runs. There is no core-only simulation below.</p>
 *
 * <h2>What the numbers come from</h2>
 *
 * <p>Nothing about this machine is hard-coded in a test: {@code ticks_per_wafer} and
 * {@code package_per_wafer} are read out of the loaded definition, the per-tick cost is
 * {@code definition.energyPerTick(tier, settings)} — exactly the call the block entity makes at
 * admission — and the chip count is {@code settings().chipsPerWafer()}. Every expected total is
 * then arithmetic on those numbers plus an independently judged lot, never a reading of the
 * machine's own output.</p>
 *
 * <p>A lot is a fixture: it is built by putting unprocessed wafers through
 * {@link FabYieldService#applyLayerStep} for every layer of its tier, exactly as the process
 * chain would, and it carries a frozen {@link RunSpec}. Cases that need a specific mix of pass
 * and fail wafers build several lots and keep the first that actually has it, and say so in an
 * assertion, so a fixture that quietly turned into an all-pass or all-fail lot cannot quietly
 * weaken the case. The passing count the assertions use is counted from the judged flag array
 * by a plain loop in this file, not read back from the machine.</p>
 *
 * <h2>The fixtures that borrow global loader state</h2>
 *
 * <p>Three fixtures borrow a global: the machine definition registry, the support-line matrix
 * and two config values. Each swap is synchronous, bounded to the test body, and puts the exact
 * previous value back in a {@code finally} ({@link #swapDefinitions}, {@link #withMatrix},
 * {@link #withChipsPerWafer}, {@link #withFoupCapacity}). The definition swap goes through
 * {@link MachineDefinitionLoader#build} and the two loader fields
 * {@code MachineDefinitionLoader.CURRENT} and {@code JsonResourceDataLoader.current}, because a
 * {@code GameTest} cannot drive a data-pack reload and {@code applyDefinition} alone is not a
 * reload: {@code serverTick} re-reads the registry every tick and would undo it. No bundled
 * resource is edited.</p>
 *
 * <p><b>No ClientLevel and no screen.</b> Nothing below constructs a client level and nothing
 * touches {@code TestPackageScreen}; the client half of the menu is built through the
 * registered {@code MenuType} factory with a real {@link RegistryFriendlyByteBuf}, which is
 * server-safe. Actual client-world rendering and the client ticker branch stay a manual gate.</p>
 */
public final class PackagingGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "packaging_chain";

    // ---- the registered test ids -------------------------------------------------

    /** A complete T1 logic lot is judged, packaged and accounted for exactly. */
    private static final String LOGIC_T1_LOT = "test_package_judges_a_complete_logic_t1_lot";
    /** The same for logic at T2, which has its own catalog chip. */
    private static final String LOGIC_T2_LOT = "test_package_judges_a_complete_logic_t2_lot";
    /** And for memory at T2. */
    private static final String MEMORY_T2_LOT = "test_package_judges_a_complete_memory_t2_lot";

    /** The FOUP that comes back is the same item, the stored capacity and a fresh id. */
    private static final String RETURNED_FOUP = "test_package_returns_a_fresh_foup_of_the_same_item";
    /** A lot that is not finished is refused and left exactly where it was. */
    private static final String INCOMPLETE_LOT = "test_package_refuses_an_incomplete_lot";
    /** A complete lot whose die has no chip item is refused and not consumed. */
    private static final String NO_CHIP_FOR_DIE = "test_package_refuses_a_die_with_no_catalog_chip";
    /** A lot with no frozen run spec is refused. */
    private static final String NO_SPEC = "test_package_refuses_a_lot_without_a_run_spec";
    /** A client-view FOUP is never judged and never consumed. */
    private static final String CLIENT_VIEW_LOT = "test_package_never_judges_a_client_view_lot";
    /** The generation 1 gate really is asked, under a matrix that demands generation 2. */
    private static final String GENERATION_GATE = "test_package_enforces_the_packaging_generation_gate";

    /** No energy means no payment and no lost progress. */
    private static final String NO_ENERGY = "test_package_pays_nothing_without_energy";
    /** Packages are touched only on the tick a passing wafer finishes. */
    private static final String PACKAGE_TIMING = "test_package_spends_packages_only_on_completion";
    /** Taking the packages away mid-wafer pauses before any FE is paid. */
    private static final String PACKAGE_REMOVAL = "test_package_pauses_when_its_packages_are_taken";
    /** A full chip bank pauses and resumes. */
    private static final String CHIP_BANK_FULL = "test_package_pauses_on_a_full_chip_output";
    /** A full scrap bank pauses and resumes, and a failing wafer uses no package at all. */
    private static final String SCRAP_BANK_FULL = "test_package_pauses_on_a_full_scrap_output";

    /** A component-modified chip stack in every chip slot blocks plain output until freed. */
    private static final String NAMED_CHIP_BANK = "test_package_refuses_plain_chips_behind_named_chips";
    /** A second loaded FOUP waits for the first queue, and the returned empty one may go. */
    private static final String SECOND_LOT = "test_package_keeps_a_second_lot_until_the_first_finishes";
    /** Breaking mid-run drops what was finished and nothing that was still queued. */
    private static final String BREAK_MID_RUN = "test_package_break_drops_no_queued_outputs";

    /** The queue survives NBT mid-wafer and finishes at exactly the remaining cost. */
    private static final String SAVE_LOAD = "test_package_save_load_round_trip_mid_wafer";
    /** An accepted run keeps its frozen numbers across a config and definition change. */
    private static final String FROZEN_PARAMETERS = "test_package_keeps_frozen_parameters_for_an_accepted_run";
    /** A definition that goes away pauses the queue, and taking it back resumes it. */
    private static final String MISSING_DEFINITION = "test_package_resumes_when_its_definition_returns";

    /** A hand-edited queue snapshot is dropped entirely rather than half believed. */
    private static final String MALFORMED_QUEUE = "test_package_malformed_queue_nbt_fails_closed";
    /** The menu: layout, predicates, shift-click and the seven public wire values. */
    private static final String MENU_VALUES = "test_package_menu_layout_and_public_values";

    /** Energy in, nothing else, and no hopper. */
    private static final String ENERGY_ONLY = "test_package_exposes_energy_only";
    /** The block opens the menu and the registered server ticker drives the machine. */
    private static final String BLOCK_USE_TICKER = "test_package_block_use_and_server_ticker";

    /** Where the machine under test is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second machine, for the cases that need two at once. */
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    /** A third position, for a control block of another type. */
    private static final BlockPos THIRD = new BlockPos(5, 1, 1);

    /** Radius used when counting dropped items; deliberately small, as in the drop tests. */
    private static final double DROP_RADIUS = 2.0;

    /** The vanilla maximum stack, which is also the machine's own per-slot limit. */
    private static final int VANILLA_STACK = 64;

    /**
     * Lot sizes a fixture may use, largest first.
     *
     * <p>A case needs a lot with at least one passing and one failing wafer, and the chance of
     * a random lot being all-pass falls as the lot grows, so the fixture walks down this list
     * and retries each size a bounded number of times before moving on. The limits (bank
     * capacity, buffer capacity and the package slot) are checked per candidate, not once up
     * front, so a small {@code chipsPerWafer} or a small buffer narrows the list without any
     * of it being guessed.</p>
     */
    private static final int[] LOT_SIZES = {25, 16, 12, 10, 8, 6, 5, 4, 3, 2};
    /** How many lots of one size are tried before the fixture moves on. */
    private static final int LOT_ATTEMPTS = 12;

    /**
     * The private snapshot keys, spelled exactly as {@code TestPackageBlockEntity} writes them.
     *
     * <p>They are private in production on purpose; a test that hand-edits a snapshot has to
     * name them, and naming them here rather than reflecting them makes a rename in production
     * a compile error in this file instead of a silently skipped case.</p>
     */
    private static final String STATE_KEY = "packaging";
    private static final String FLAGS_KEY = "flags";
    private static final String CHIPS_KEY = "chips_per_wafer";
    private static final String PACKAGES_KEY = "packages_per_wafer";
    private static final String TICKS_KEY = "ticks_per_wafer";
    private static final String ENERGY_KEY = "energy_per_tick";
    private static final String INDEX_KEY = "index";
    private static final String PAID_KEY = "paid_ticks";
    private static final String DIE_KEY = "die_type";
    private static final String TIER_KEY = "tier";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,
            EUVCRAFTMod.MODID);

    static {
        FUNCTIONS.register(LOGIC_T1_LOT, () -> PackagingGameTests::judgesLogicTierOneLot);
        FUNCTIONS.register(LOGIC_T2_LOT, () -> PackagingGameTests::judgesLogicTierTwoLot);
        FUNCTIONS.register(MEMORY_T2_LOT, () -> PackagingGameTests::judgesMemoryTierTwoLot);
        FUNCTIONS.register(RETURNED_FOUP, () -> PackagingGameTests::returnsAFreshFoup);
        FUNCTIONS.register(INCOMPLETE_LOT, () -> PackagingGameTests::refusesIncompleteLot);
        FUNCTIONS.register(NO_CHIP_FOR_DIE, () -> PackagingGameTests::refusesDieWithoutChip);
        FUNCTIONS.register(NO_SPEC, () -> PackagingGameTests::refusesLotWithoutRunSpec);
        FUNCTIONS.register(CLIENT_VIEW_LOT, () -> PackagingGameTests::neverJudgesClientViewLot);
        FUNCTIONS.register(GENERATION_GATE, () -> PackagingGameTests::enforcesGenerationGate);
        FUNCTIONS.register(NO_ENERGY, () -> PackagingGameTests::paysNothingWithoutEnergy);
        FUNCTIONS.register(PACKAGE_TIMING, () -> PackagingGameTests::spendsPackagesOnlyOnCompletion);
        FUNCTIONS.register(PACKAGE_REMOVAL, () -> PackagingGameTests::pausesWhenPackagesAreTaken);
        FUNCTIONS.register(CHIP_BANK_FULL, () -> PackagingGameTests::pausesOnFullChipBank);
        FUNCTIONS.register(SCRAP_BANK_FULL, () -> PackagingGameTests::pausesOnFullScrapBank);
        FUNCTIONS.register(NAMED_CHIP_BANK, () -> PackagingGameTests::namedChipsBlockTheBank);
        FUNCTIONS.register(SECOND_LOT, () -> PackagingGameTests::secondLotWaitsForTheFirst);
        FUNCTIONS.register(BREAK_MID_RUN, () -> PackagingGameTests::breakDropsNoQueuedOutputs);
        FUNCTIONS.register(SAVE_LOAD, () -> PackagingGameTests::saveLoadRoundTripMidWafer);
        FUNCTIONS.register(FROZEN_PARAMETERS, () -> PackagingGameTests::keepsFrozenParameters);
        FUNCTIONS.register(MISSING_DEFINITION, () -> PackagingGameTests::missingDefinitionPauses);
        FUNCTIONS.register(MALFORMED_QUEUE, () -> PackagingGameTests::malformedQueueFailsClosed);
        FUNCTIONS.register(MENU_VALUES, () -> PackagingGameTests::menuLayoutAndPublicValues);
        FUNCTIONS.register(ENERGY_ONLY, () -> PackagingGameTests::exposesEnergyOnly);
        FUNCTIONS.register(BLOCK_USE_TICKER, () -> PackagingGameTests::blockUseAndServerTicker);
    }

    private PackagingGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) PackagingGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, LOGIC_T1_LOT);
        register(event, data, LOGIC_T2_LOT);
        register(event, data, MEMORY_T2_LOT);
        register(event, data, RETURNED_FOUP);
        register(event, data, INCOMPLETE_LOT);
        register(event, data, NO_CHIP_FOR_DIE);
        register(event, data, NO_SPEC);
        register(event, data, CLIENT_VIEW_LOT);
        register(event, data, GENERATION_GATE);
        register(event, data, NO_ENERGY);
        register(event, data, PACKAGE_TIMING);
        register(event, data, PACKAGE_REMOVAL);
        register(event, data, CHIP_BANK_FULL);
        register(event, data, SCRAP_BANK_FULL);
        register(event, data, NAMED_CHIP_BANK);
        register(event, data, SECOND_LOT);
        register(event, data, BREAK_MID_RUN);
        register(event, data, SAVE_LOAD);
        register(event, data, FROZEN_PARAMETERS);
        register(event, data, MISSING_DEFINITION);
        register(event, data, MALFORMED_QUEUE);
        register(event, data, MENU_VALUES);
        register(event, data, ENERGY_ONLY);
        register(event, data, BLOCK_USE_TICKER);
        LOGGER.info("Registered EUVCRAFT packaging chain game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1-3. A complete lot becomes chips and scrap, and nothing else
    // ------------------------------------------------------------------

    private static void judgesLogicTierOneLot(GameTestHelper helper) {
        judgesCompleteLot(helper, DieTypes.LOGIC, 1, ModItems.LOGIC_CHIP_T1.get());
    }

    private static void judgesLogicTierTwoLot(GameTestHelper helper) {
        judgesCompleteLot(helper, DieTypes.LOGIC, 2, ModItems.LOGIC_CHIP_T2.get());
    }

    private static void judgesMemoryTierTwoLot(GameTestHelper helper) {
        judgesCompleteLot(helper, DieTypes.MEMORY, 2, ModItems.MEMORY_CHIP_T2.get());
    }

    /**
     * The end-to-end contract, for one design.
     *
     * <p>The expected counts are built from three independent things and only then compared:
     * the lot's own judged flag array (counted here with a plain loop), the configured
     * {@code chipsPerWafer} and the definition's {@code package_per_wafer}. The FE total is
     * {@code ticksPerWafer * lotSize * energyPerTick}, again from the definition. Nothing is read
     * back off the machine to build an expectation.</p>
     *
     * <p>The fixture must really be mixed: an all-pass lot would satisfy every chip and
     * conservation assertion with no scrap at all, and an all-fail lot would satisfy the scrap
     * ones with no chips, so both states are asserted on the fixture itself.</p>
     */
    private static void judgesCompleteLot(GameTestHelper helper, String dieType, int tierIndex,
            Item expectedChip) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, tierIndex);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        Lot lot = mixedLot(level, tier, dieType, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(dieType, tierIndex, numbers));
            return;
        }
        int passing = lot.passing();
        int expectedChips = passing * numbers.chipsPerWafer();
        int expectedScrap = lot.failing() * numbers.chipsPerWafer();
        int expectedPackages = passing * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();

        // The oracle is counted here, not read from the machine, and it must agree with the
        // service's own count so a disagreement between the two is a visible failure rather
        // than a quietly different expectation.
        helper.assertValueEqual(passing, FabYieldService.countPassing(level, lot.contents(),
                tier, dieType, null),
            "the fixture's counted passing wafers disagree with FabYieldService.countPassing");
        helper.assertTrue(passing > 0 && lot.failing() > 0,
            "the fixture lot for " + dieType + " at T" + tierIndex + " is not mixed: "
                + passing + " passing and " + lot.failing() + " failing of " + lot.size());

        // The catalog is the machine's own source of truth for the output item, and the
        // expected item is named here, so a change to either side is visible.
        helper.assertTrue(ChipCatalog.chipFor(dieType, tierIndex).orElse(null) == expectedChip,
            "the chip catalog does not answer " + expectedChip + " for " + dieType
                + " at tier " + tierIndex);

        loadPackages(machine, expectedPackages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, totalTicks * numbers.energyPerTick());

        runUntilFinished(helper, machine, totalTicks + 8);

        assertCatalogChips(helper, machine, expectedChip, expectedChips,
            dieType + " at T" + tierIndex);
        assertScrapOnly(helper, machine, ModItems.CHIP_SCRAP.get(), expectedScrap);
        assertConservation(helper, machine, numbers, lot, expectedChips, expectedScrap,
            expectedPackages);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the run did not spend exactly one per-tick cost on each of its " + totalTicks
                + " ticks");
        helper.assertValueEqual(totalTicks, machine.data().get(FabMachineBlockEntity.DATA_PROGRESS),
            "a finished run does not report its full tick count as its progress");
        helper.assertValueEqual(totalTicks, machine.data().get(FabMachineBlockEntity.DATA_TICKS),
            "a finished run does not report the same number as its tick total");

        // The judged lot is gone and an empty one of the same item took its place.
        ItemStack returned = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(returned.is(ModItems.FOUP_MK1.get()),
            "the FOUP slot does not hold a FOUP after the run: " + returned);
        FoupContents returnedLot = FoupItems.contentsOf(returned);
        helper.assertTrue(returnedLot != null && returnedLot.isEmpty(),
            "the FOUP in the slot is not the empty lot the machine returned");
        helper.assertTrue(returnedLot != null
                && !returnedLot.lotId().equals(lot.contents().lotId()),
            "the returned FOUP reused the judged lot's id, so the same lot could be judged twice");

        LOGGER.info("[M1-10] {}: {} wafers of {} at T{} judged as {} passing and {} failing; "
                + "{} chips, {} scrap, {} packages and {} FE ({} ticks at {} FE)",
            dieType + " T" + tierIndex, lot.size(), dieType, tierIndex, passing, lot.failing(),
            expectedChips, expectedScrap, expectedPackages, totalTicks * numbers.energyPerTick(),
            numbers.ticksPerWafer(), numbers.energyPerTick());
    }

    // ------------------------------------------------------------------
    // 4. The FOUP that comes back
    // ------------------------------------------------------------------

    /**
     * The returned carrier is the item that went in, carrying the capacity that lot had, with a
     * fresh lot id and nothing in it — and all of that on the admission tick, before a single
     * wafer has been worked.
     *
     * <p>The configured capacity is deliberately moved to a different value for the duration, so
     * a machine that rebuilt the returned FOUP from the current config would be caught. Both
     * FOUP items are covered.</p>
     */
    private static void returnsAFreshFoup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }

        int configured = EUVCRAFTMod.settings().foupCapacity();
        int moved = configured == 1 ? 2 : 1;

        withFoupCapacity(moved, () -> {
            helper.assertTrue(FoupItems.configuredCapacity() != configured,
                "the config fixture did not move the configured capacity, so the stored-capacity "
                    + "assertions below would be vacuous");
            for (Item item : List.of(ModItems.FOUP_MK1.get(), ModItems.FOUP_MK2.get())) {
                // The lot is built at the capacity the config held before the move, which is
                // now a different number, so "stored capacity" and "configured capacity" are
                // two different things in this case.
                FoupContents full = processLot(level, tier, DieTypes.LOGIC, null,
                    configured, lot.size());
                UUID judgedId = full.lotId();

                machine.clearContent();
                machine.setStoredEnergy(0);
                loadPackages(machine, lot.passing() * numbers.packagesPerWafer());
                loadLot(machine, item, full);
                charge(helper, machine,
                    lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());

                // One tick: the admission itself. Nothing has been worked yet.
                machine.serverTick();
                helper.assertTrue(machine.isQueueActive(),
                    "a " + item + " lot was not accepted on the first tick");
                helper.assertValueEqual(0, machine.data().get(FabMachineBlockEntity.DATA_PROGRESS),
                    "the admission tick worked a wafer instead of only judging the lot");

                ItemStack returned = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
                helper.assertTrue(returned.is(item),
                    "the returned carrier is " + returned.getItem() + " rather than the " + item
                        + " that went in");
                FoupContents returnedLot = FoupItems.contentsOf(returned);
                helper.assertTrue(returnedLot != null,
                    "the returned carrier carries no lot component at all");
                if (returnedLot != null) {
                    helper.assertTrue(returnedLot.isEmpty(),
                        "the returned carrier still holds " + returnedLot.size() + " wafers");
                    helper.assertValueEqual(full.capacity(), returnedLot.capacity(),
                        "the returned carrier rebuilt its capacity from the config ("
                            + moved + ") instead of keeping the " + full.capacity()
                            + " the judged lot had");
                    helper.assertFalse(returnedLot.lotId().equals(judgedId),
                        "the returned carrier reused the judged lot id, so the same lot could be "
                            + "judged a second time");
                    helper.assertTrue(returnedLot.run() == null,
                        "the returned carrier kept the judged lot's run spec");
                }
                LOGGER.info("[M1-10] {}: a {} wafer lot came back as {} with capacity {} (config "
                        + "says {}) holding {} wafers",
                    RETURNED_FOUP, item, item, full.capacity(), moved,
                    returnedLot == null ? -1 : returnedLot.size());
            }
        });
        helper.assertValueEqual(configured, EUVCRAFTMod.settings().foupCapacity(),
            "the foupCapacity config was not restored");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5-7. Refusals cost the player nothing
    // ------------------------------------------------------------------

    private static void refusesIncompleteLot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(tier.layers() >= 2,
            "T" + tier.index() + " has " + tier.layers()
                + " layers, so an incomplete lot cannot be built for it");

        // One layer short of the tier's last: every wafer sits at a real layer and none at the
        // final one, so the lot is genuinely unfinished rather than malformed.
        int wafers = Math.min(4, EUVCRAFTMod.settings().foupCapacity());
        FoupContents partial = FoupContents.empty(UUID.randomUUID(),
            EUVCRAFTMod.settings().foupCapacity());
        for (int index = 0; index < wafers; index++) {
            partial = partial.withWafer(Wafer.unprocessed());
        }
        for (int layer = 1; layer <= tier.layers() - 1; layer++) {
            partial = FabYieldService.applyLayerStep(level, partial, tier, DieTypes.LOGIC, null,
                layer, 1.0);
        }
        helper.assertTrue(partial.run() != null,
            "the incomplete fixture carries no run spec, so it would be refused for NO_SPEC "
                + "rather than LOT_INCOMPLETE");
        for (Wafer wafer : partial.wafers()) {
            helper.assertTrue(wafer.layerIndex() == tier.layers() - 1,
                "the incomplete fixture holds a wafer at layer " + wafer.layerIndex()
                    + " rather than " + (tier.layers() - 1));
        }

        assertRefusedUnchanged(helper, machine, numbers, partial,
            PackagingRefusalReason.LOT_INCOMPLETE, "a lot one layer short of finished");
    }

    private static void refusesDieWithoutChip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        Tier tier = tier(helper, 7);
        if (tier == null) {
            return;
        }
        // Fixture honesty: the catalog really has no chip for this design, and the die really
        // can be made at this tier, so the refusal cannot be blamed on a missing tier.
        helper.assertTrue(ChipCatalog.chipFor(DieTypes.COMPUTE, tier.index()).isEmpty(),
            "the chip catalog now names a chip for compute at tier " + tier.index()
                + ", so this case cannot prove a NO_CHIP_FOR_DIE refusal");

        FoupContents complete;
        try {
            complete = processLot(level, tier, DieTypes.COMPUTE, 8,
                EUVCRAFTMod.settings().foupCapacity(), 4);
        } catch (RuntimeException refusedByTheCore) {
            helper.fail("a compute lot at T" + tier.index() + " could not be built, so the "
                + "NO_CHIP_FOR_DIE case cannot run: " + refusedByTheCore.getMessage());
            return;
        }
        helper.assertTrue(complete.run() != null,
            "the compute fixture carries no run spec, so it would be refused for NO_SPEC");
        for (Wafer wafer : complete.wafers()) {
            helper.assertTrue(wafer.layerIndex() == tier.layers(),
                "the compute fixture is not at the tier's last layer, so it is not a complete lot");
        }

        MachineDefinition definition = definitionOf(helper, machine);
        if (definition == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        assertRefusedUnchanged(helper, machine, numbers, complete,
            PackagingRefusalReason.NO_CHIP_FOR_DIE,
            "a complete compute lot whose die has no catalog chip");

        // Nothing of any kind was written: there is no compute item to write.
        helper.assertValueEqual(0, totalOutput(machine),
            "the refused compute lot produced output");
        LOGGER.info("[M1-10] {}: a complete compute lot at T{} was refused, no compute item "
                + "exists to write and nothing was", NO_CHIP_FOR_DIE, tier.index());
        helper.succeed();
    }

    private static void refusesLotWithoutRunSpec(GameTestHelper helper) {
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        // A lot that was never processed: it holds wafers, it is not empty, and it carries no
        // frozen run spec, which is the case this reason exists for.
        FoupContents unstarted = FoupContents.empty(UUID.randomUUID(),
            EUVCRAFTMod.settings().foupCapacity());
        for (int index = 0; index < Math.min(3, unstarted.capacity()); index++) {
            unstarted = unstarted.withWafer(Wafer.unprocessed());
        }
        helper.assertTrue(unstarted.run() == null,
            "the unstarted fixture carries a run spec, so it cannot prove NO_SPEC");

        assertRefusedUnchanged(helper, machine, numbers, unstarted,
            PackagingRefusalReason.NO_SPEC, "a lot that was never processed");
    }

    /** The whole refusal contract, for one lot that must not be admitted. */
    private static void assertRefusedUnchanged(GameTestHelper helper,
            TestPackageBlockEntity machine, Numbers numbers, FoupContents lot,
            PackagingRefusalReason expected, String what) {
        int packages = VANILLA_STACK;
        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot);
        charge(helper, machine, numbers.energyPerTick() * 20);
        long charged = machine.buffer().getAmountAsLong();
        ItemStack before = machine.slot(TestPackageBlockEntity.FOUP_SLOT).copy();

        runTicks(machine, 40);

        helper.assertFalse(machine.isQueueActive(),
            "the machine started a queue for " + what);
        helper.assertValueEqual(expected, machine.refusalReason(),
            "the machine did not publish the expected refusal for " + what);
        helper.assertValueEqual(TestPackageBlockEntity.STATE_REFUSED_BASE + expected.ordinal(),
            machine.state(), "the machine published the wrong state for " + what);
        helper.assertValueEqual(0, machine.wafersDone(),
            "the machine reported progress for " + what);
        helper.assertValueEqual(0, machine.wafersTotal(),
            "the machine reported a wafer total for " + what);

        helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
            "the machine charged the player for refusing " + what);
        helper.assertValueEqual(0, totalOutput(machine),
            "the machine produced output from " + what);
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the machine consumed packages for " + what);

        ItemStack after = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, after),
            "the refused FOUP was replaced or altered; it must be left exactly where the player "
                + "put it");
        FoupContents still = FoupItems.contentsOf(after);
        helper.assertTrue(still != null && still.lotId().equals(lot.lotId()),
            "the refused lot changed identity in the slot");
        helper.assertTrue(still != null && still.size() == lot.size(),
            "the refused lot lost or gained wafers in the slot");
        helper.assertValueEqual(0, progressOf(machine),
            "the machine published progress for " + what);
        LOGGER.info("[M1-10] {}: refused with {}, {} FE untouched, no progress, no output, the "
                + "packages and the refused lot untouched", what, expected, charged);
    }

    // ------------------------------------------------------------------
    // 8-9. The two gates the trusted read and the matrix provide
    // ------------------------------------------------------------------

    /**
     * A FOUP stack whose lot came off the network is never judged and never consumed.
     *
     * <p>The client view is built with the real {@link FoupContentsCodec#STREAM_CODEC} over a
     * real buffer, not by hand-writing a placeholder lot, so the fixture is exactly what a client
     * would hold: the capacity, a real wafer count and the lot layer, with no run spec and no
     * rolls anywhere.</p>
     */
    private static void neverJudgesClientViewLot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }

        // The real network form, both ways.
        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, lot.contents());
        FoupContents clientView = FoupContentsCodec.STREAM_CODEC.decode(buffer);
        helper.assertTrue(FoupContentsCodec.isClientView(clientView),
            "the stream codec did not produce a client view");
        helper.assertValueEqual(lot.size(), clientView.size(),
            "the client view lost the wafer count the network form carries");
        helper.assertTrue(clientView.run() == null,
            "the client view carries a run spec, so the fixture is not what a client holds");
        for (int index = 0; index < clientView.size(); index++) {
            helper.assertValueEqual(FoupContentsCodec.CLIENT_VIEW_ROLL,
                clientView.wafers().get(index).cumulativeRoll(),
                "the client view holds a real roll rather than the placeholder one");
        }

        // Preserve the real client-view marker while supplying an otherwise eligible
        // spec. This isolates trustedContentsOf from the separate NO_SPEC safeguard.
        FoupContents untrusted = new FoupContents(clientView.lotId(), clientView.capacity(),
            clientView.wafers(), lot.contents().run());
        helper.assertTrue(FoupContentsCodec.isClientView(untrusted),
            "adding a spec must not remove the client-view marker");
        ItemStack stack = FoupItems.with(ModItems.FOUP_MK1.get(), untrusted);
        int packages = VANILLA_STACK;
        loadPackages(machine, packages);
        machine.setSlot(TestPackageBlockEntity.FOUP_SLOT, stack);
        charge(helper, machine, numbers.energyPerTick() * 20);
        long charged = machine.buffer().getAmountAsLong();
        ItemStack before = machine.slot(TestPackageBlockEntity.FOUP_SLOT).copy();

        runTicks(machine, 60);

        helper.assertFalse(machine.isQueueActive(),
            "the machine started a queue on a client-view FOUP");
        helper.assertTrue(machine.refusalReason() == null,
            "the machine published a refusal of " + machine.refusalReason() + "; a client view "
                + "is not an admission failure, it is nothing being offered");
        helper.assertValueEqual(0, machine.wafersTotal(),
            "the machine reported a wafer total for a client-view FOUP");
        helper.assertValueEqual(0, progressOf(machine),
            "the machine published progress for a client-view FOUP");
        helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
            "the machine spent FE on a client-view FOUP");
        helper.assertValueEqual(0, totalOutput(machine),
            "the machine produced output from a client-view FOUP: every placeholder wafer passes "
                + "any yield check, which is the exploit the trusted read exists to stop");
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the machine consumed packages for a client-view FOUP");
        ItemStack after = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, after),
            "the client-view stack in the slot was replaced, so the machine consumed a lot it "
                + "must never judge");
        FoupContents still = FoupItems.contentsOf(after);
        helper.assertTrue(still != null && FoupContentsCodec.isClientView(still),
            "the slot no longer holds the client view it was given");

        // Negative control: the same lot, trusted, is admitted. Without this the assertions
        // above would also pass on a machine that refuses every lot.
        FoupContents trusted = FoupItems.trustedContentsOf(before);
        helper.assertTrue(trusted.isEmpty(),
            "the trusted read of a client view did not come back as an empty lot");
        machine.clearContent();
        machine.setStoredEnergy(0);
        loadPackages(machine, lot.passing() * numbers.packagesPerWafer());
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());
        machine.serverTick();
        helper.assertTrue(machine.isQueueActive(),
            "the same lot was not admitted when it was trusted, so the assertions above prove "
                + "nothing about the client view specifically");

        LOGGER.info("[M1-10] {}: a client-view FOUP holding {} placeholder wafers was neither "
                + "judged nor consumed, and the trusted form of the same lot was accepted",
            CLIENT_VIEW_LOT, lot.size());
        helper.succeed();
    }

    /**
     * The packaging generation gate is real: a valid matrix that requires generation 2 at T1
     * refuses the machine's generation 1.
     *
     * <p>The replacement matrix is built from the loaded one so it is a valid matrix in its own
     * right — every line present, every tier covered and every generation still non-decreasing —
     * and only the {@code test_packaging} requirement is raised. The loaded matrix is put back in
     * a {@code finally}.</p>
     */
    private static void enforcesGenerationGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        TierRegistry registry = TierDataLoader.getRegistry();
        GenerationMatrix loaded = SupportLineDataLoader.getMatrix();
        if (loaded == null) {
            helper.fail("the support-line generation matrix has not loaded, so the gate cannot "
                + "be asked");
            return;
        }
        if (definition == null || tier == null || registry == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        // Non-vacuity: under the loaded matrix this lot really is admissible at generation 1, so
        // a refusal under the replacement matrix can only be the raised requirement.
        helper.assertTrue(loaded.isSatisfied(SupportLines.TEST_PACKAGING,
                TestPackageBlockEntity.INSTALLED_GENERATION, tier.index()),
            "the loaded matrix already requires generation "
                + loaded.requiredGeneration(SupportLines.TEST_PACKAGING, tier.index())
                + " for " + SupportLines.TEST_PACKAGING + " at T1, so this case cannot prove the "
                + "gate");

        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }

        GenerationMatrix stricter = withPackagingGeneration(registry, loaded, tier.index(), 2);
        helper.assertTrue(!stricter.isSatisfied(SupportLines.TEST_PACKAGING,
                TestPackageBlockEntity.INSTALLED_GENERATION, tier.index()),
            "the replacement matrix still satisfies generation "
                + TestPackageBlockEntity.INSTALLED_GENERATION + " at T1, so the fixture is wrong");
        helper.assertTrue(stricter.isSatisfied(SupportLines.TEST_PACKAGING, 2, tier.index()),
            "the replacement matrix refuses even generation 2, so it is not a usable fixture");

        int packages = VANILLA_STACK;
        withMatrix(stricter, () -> {
            loadPackages(machine, packages);
            loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
            charge(helper, machine, numbers.energyPerTick() * 20);
            long charged = machine.buffer().getAmountAsLong();
            ItemStack before = machine.slot(TestPackageBlockEntity.FOUP_SLOT).copy();

            runTicks(machine, 40);

            helper.assertFalse(machine.isQueueActive(),
                "the machine judged a lot while its generation was below the matrix requirement");
            helper.assertValueEqual(PackagingRefusalReason.GENERATION_UNMET,
                machine.refusalReason(),
                "the machine did not refuse for GENERATION_UNMET under the stricter matrix");
            helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
                "the machine charged the player for a generation-gate refusal");
            helper.assertValueEqual(0, totalOutput(machine),
                "the machine produced output from a lot refused by the generation gate");
            helper.assertValueEqual(packages,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "the machine consumed packages for a generation-gate refusal");
            helper.assertTrue(ItemStack.isSameItemSameComponents(before,
                    machine.slot(TestPackageBlockEntity.FOUP_SLOT)),
                "the refused lot was consumed by a generation-gate refusal");
            LOGGER.info("[M1-10] {}: generation {} refused under a matrix requiring generation 2 "
                    + "for {} at T1, with the lot, the packages and {} FE untouched",
                GENERATION_GATE, TestPackageBlockEntity.INSTALLED_GENERATION,
                SupportLines.TEST_PACKAGING, charged);
        });

        // The real matrix is back.
        helper.assertTrue(SupportLineDataLoader.getMatrix() == loaded,
            "the support-line matrix was not restored after the fixture");
        helper.assertTrue(machine.definition().isPresent(),
            "the machine definition registry was not restored after the fixture");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 10-14. A blocked tick moves nothing and pays nothing
    // ------------------------------------------------------------------

    private static void paysNothingWithoutEnergy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a run cannot be caught mid-wafer to starve it");

        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();

        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        int half = Math.max(1, numbers.ticksPerWafer() / 2);
        charge(helper, machine, numbers.energyPerTick() * half);
        runTicks(machine, half);
        helper.assertValueEqual(half, progressOf(machine),
            "the first half did not cost exactly one charge per tick");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the buffer was not drained exactly by the ticks that were paid for");

        // Nothing left to pay with.
        machine.setStoredEnergy(0);
        runTicks(machine, 60);
        helper.assertTrue(machine.isQueueActive(),
            "an empty buffer dropped the accepted run instead of pausing it");
        helper.assertTrue(machine.isPaused(),
            "a machine with no FE for a tick is not marked paused");
        helper.assertValueEqual(half, progressOf(machine), "an empty buffer advanced the run");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "an empty buffer gained FE");
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "an empty buffer consumed packages");
        helper.assertValueEqual(0, totalOutput(machine),
            "an empty buffer produced output");

        // One FE short of a whole tick is still nothing: the payment is all-or-nothing.
        machine.setStoredEnergy(numbers.energyPerTick() - 1);
        long partial = machine.buffer().getAmountAsLong();
        machine.serverTick();
        helper.assertTrue(machine.isPaused(),
            "energy_per_tick - 1 FE did not pause the machine");
        helper.assertValueEqual(half, progressOf(machine),
            "energy_per_tick - 1 FE advanced the run");
        helper.assertValueEqual(partial, machine.buffer().getAmountAsLong(),
            "the machine spent part of a tick it could not pay in full");

        // Paying the rest resumes the same run rather than restarting it.
        int remainingTicks = totalTicks - half;
        machine.setStoredEnergy(numbers.energyPerTick() * remainingTicks);
        runUntilFinished(helper, machine, remainingTicks + 8);
        assertCatalogChips(helper, machine, ModItems.LOGIC_CHIP_T1.get(),
            lot.passing() * numbers.chipsPerWafer(), "starved logic T1");
        assertScrapOnly(helper, machine, ModItems.CHIP_SCRAP.get(),
            lot.failing() * numbers.chipsPerWafer());
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the resumed run did not spend exactly the FE that was left to spend");
        helper.assertValueEqual((long) numbers.energyPerTick() * totalTicks,
            (long) numbers.energyPerTick() * totalTicks,
            "the whole run did not cost exactly energy_per_tick * ticks_per_wafer * wafers");

        LOGGER.info("[M1-10] {}: paused at {} of {} ticks with no FE, cost-1 FE changed nothing, "
                + "resumed and finished on exactly {} FE",
            NO_ENERGY, half, totalTicks, (long) numbers.energyPerTick() * totalTicks);
        helper.succeed();
    }

    private static void spendsPackagesOnlyOnCompletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a wafer cannot be watched before it completes");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        helper.assertTrue(lot.flags()[0],
            "the fixture's first wafer does not pass, so this case is not about a passing wafer");
        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();
        int cost = totalTicks * numbers.energyPerTick();

        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, cost);

        machine.serverTick();
        helper.assertValueEqual(0, progressOf(machine),
            "the admission tick paid for a tick of work");

        // Every tick before the first wafer completes must leave the packages alone, while the
        // FE really is being paid: the run is working, not stalled.
        for (int tick = 1; tick < numbers.ticksPerWafer(); tick++) {
            machine.serverTick();
            helper.assertValueEqual(packages,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "a partial wafer consumed packages on tick " + tick);
            helper.assertValueEqual(tick, progressOf(machine),
                "tick " + tick + " did not advance the run by exactly one tick");
            helper.assertValueEqual((long) cost - (long) numbers.energyPerTick() * tick,
                machine.buffer().getAmountAsLong(),
                "the partial tick did not cost exactly one per-tick charge");
            helper.assertValueEqual(0, totalOutput(machine),
                "output appeared before the first wafer completed");
        }

        // The tick that completes wafer 1 takes exactly one wafer's worth of packages and writes
        // exactly one wafer's worth of chips, in the same tick.
        machine.serverTick();
        helper.assertValueEqual(numbers.ticksPerWafer(), progressOf(machine),
            "the completing tick did not advance the run by one tick");
        helper.assertValueEqual(packages - numbers.packagesPerWafer(),
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the completing tick did not take exactly package_per_wafer packages");
        helper.assertValueEqual(numbers.chipsPerWafer(),
            bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "the completing tick did not write exactly chipsPerWafer catalog chips");

        runUntilFinished(helper, machine, totalTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);
        helper.assertValueEqual(0, machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "packages were left over after the run consumed every passing wafer's worth");

        LOGGER.info("[M1-10] {}: {} packages were untouched for {} partial ticks, then taken "
                + "{} at a time on each of the {} passing wafers",
            PACKAGE_TIMING, packages, numbers.ticksPerWafer() - 1, numbers.packagesPerWafer(),
            lot.passing());
        helper.succeed();
    }

    private static void pausesWhenPackagesAreTaken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a wafer cannot be interrupted part-way through");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        int partial = Math.max(1, numbers.ticksPerWafer() / 2);

        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());

        machine.serverTick();
        runTicks(machine, partial);
        helper.assertValueEqual(partial, progressOf(machine),
            "the test did not get the machine part-way through a wafer");
        long paidBefore = machine.buffer().getAmountAsLong();

        // The player takes the packages out mid-wafer. The tick must stop before the FE
        // payment, so not one FE may move.
        machine.clearSlot(TestPackageBlockEntity.PACKAGE_SLOT);
        runTicks(machine, 30);
        helper.assertTrue(machine.isQueueActive(),
            "taking the packages away dropped the accepted queue instead of pausing it");
        helper.assertTrue(machine.isPaused(),
            "a machine with no packages for its next passing wafer is not marked paused");
        helper.assertValueEqual(partial, progressOf(machine),
            "a machine with no packages advanced the run");
        helper.assertValueEqual(paidBefore, machine.buffer().getAmountAsLong(),
            "a machine with no packages was paid for the blocked ticks");
        helper.assertValueEqual(0, totalOutput(machine),
            "a machine with no packages produced output");

        // Putting them back resumes the same run at the progress it had paid for.
        loadPackages(machine, packages);
        int remaining = lot.size() * numbers.ticksPerWafer() - partial;
        machine.setStoredEnergy(numbers.energyPerTick() * remaining);
        runUntilFinished(helper, machine, remaining + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the resumed run did not spend exactly the FE it was recharged with");

        LOGGER.info("[M1-10] {}: paused mid-wafer at tick {} with the packages removed and {} FE "
                + "kept, resumed at the same progress and finished",
            PACKAGE_REMOVAL, partial, paidBefore);
        helper.succeed();
    }

    private static void pausesOnFullChipBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a wafer cannot be interrupted part-way through");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();

        // Every chip slot holds a full stack of the plain catalog chip: there is no room
        // anywhere in the bank, and nothing else is wrong with the machine.
        fillBank(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get());
        machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
            new ItemStack(ModItems.CHIP_PACKAGE.get(), packages));
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, totalTicks * numbers.energyPerTick());
        long charged = machine.buffer().getAmountAsLong();
        helper.assertValueEqual(0, bankRoomFor(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "the fixture chip bank is not actually full");

        runTicks(machine, 40);
        helper.assertTrue(machine.isQueueActive(),
            "a full chip bank dropped the accepted run instead of pausing it");
        helper.assertTrue(machine.isPaused(),
            "a machine with a full chip bank is not marked paused");
        helper.assertValueEqual(TestPackageBlockEntity.STATE_PAUSED, machine.state(),
            "a full chip bank did not publish the paused state");
        helper.assertValueEqual(0, progressOf(machine), "a full chip bank advanced the run");
        helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
            "a full chip bank cost the player FE");
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a full chip bank consumed packages");
        helper.assertValueEqual(TestPackageBlockEntity.CHIP_SLOT_COUNT * VANILLA_STACK,
            bankCount(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT),
            "a full chip bank overwrote the stacks already in it");

        // Freeing one compatible slot resumes the same run: one tick for one charge, and the
        // output lands in that slot on the tick the wafer completes.
        machine.clearSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST);
        long before = machine.buffer().getAmountAsLong();
        machine.serverTick();
        helper.assertValueEqual(1, progressOf(machine),
            "the resumed run did not advance by exactly one tick");
        helper.assertValueEqual(before - numbers.energyPerTick(),
            machine.buffer().getAmountAsLong(),
            "the first tick after the chip bank was freed did not cost exactly one charge");
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a partial wafer consumed packages after the chip bank was freed");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).isEmpty(),
            "the freed chip slot was written before its wafer completed");

        runTicks(machine, numbers.ticksPerWafer() - 1);
        helper.assertValueEqual(numbers.ticksPerWafer(), progressOf(machine),
            "the run did not complete the first wafer once its bank had room");
        helper.assertValueEqual(numbers.chipsPerWafer(),
            machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).getCount(),
            "the completed wafer did not write exactly chipsPerWafer chips into the freed slot");
        helper.assertValueEqual(packages - numbers.packagesPerWafer(),
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the completed wafer did not take exactly package_per_wafer packages");

        // Empty the bank and recharge for exactly what is left, then let the lot through.
        clearBank(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT);
        machine.setStoredEnergy(
            numbers.energyPerTick() * (totalTicks - progressOf(machine)));
        runUntilFinished(helper, machine, totalTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);

        LOGGER.info("[M1-10] {}: paused with all four chip slots at 64 and {} FE and {} packages "
                + "kept, resumed the moment one slot was freed",
            CHIP_BANK_FULL, charged, packages);
        helper.succeed();
    }

    private static void pausesOnFullScrapBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a wafer cannot be interrupted part-way through");

        // The first wafer must fail, so the scrap bank is the one that fills first and the
        // package question is asked of a wafer that needs none.
        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, false);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer fails could be "
                + "built for this case");
            return;
        }
        helper.assertFalse(lot.flags()[0],
            "the fixture's first wafer passes, so the scrap bank is not the one under test");
        int totalTicks = lot.size() * numbers.ticksPerWafer();

        // No packages at all for now: every one of them is consumed by a later passing wafer,
        // and the one this case watches needs none.
        fillBank(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
            TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get());
        machine.clearSlot(TestPackageBlockEntity.PACKAGE_SLOT);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, totalTicks * numbers.energyPerTick());
        long charged = machine.buffer().getAmountAsLong();
        helper.assertValueEqual(0, bankRoomFor(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get()),
            "the fixture scrap bank is not actually full");

        runTicks(machine, 40);
        helper.assertTrue(machine.isQueueActive(),
            "a full scrap bank dropped the accepted run instead of pausing it");
        helper.assertTrue(machine.isPaused(),
            "a machine with a full scrap bank is not marked paused");
        helper.assertValueEqual(0, progressOf(machine), "a full scrap bank advanced the run");
        helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
            "a full scrap bank cost the player FE");
        helper.assertValueEqual(TestPackageBlockEntity.SCRAP_SLOT_COUNT * VANILLA_STACK,
            bankCount(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT),
            "a full scrap bank overwrote the stacks already in it");

        // Freeing one scrap slot resumes it, and the failing wafer that completes needs zero
        // packages: the package slot stays empty throughout.
        machine.clearSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST);
        runTicks(machine, numbers.ticksPerWafer());
        helper.assertValueEqual(numbers.ticksPerWafer(), progressOf(machine),
            "the resumed run did not complete the failing wafer");
        helper.assertValueEqual(numbers.chipsPerWafer(),
            machine.slot(TestPackageBlockEntity.SCRAP_SLOT_FIRST).getCount(),
            "the failing wafer did not write exactly chipsPerWafer scrap");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).isEmpty(),
            "a failing wafer consumed packages; it must need none");
        helper.assertValueEqual(0,
            bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "a failing wafer wrote catalog chips");

        // The rest of the lot does need packages, so they are fed now and accounted for.
        int packages = lot.passing() * numbers.packagesPerWafer();
        loadPackages(machine, packages);
        clearBank(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
            TestPackageBlockEntity.SCRAP_SLOT_COUNT);
        machine.setStoredEnergy(numbers.energyPerTick() * (totalTicks - progressOf(machine)));
        runUntilFinished(helper, machine, totalTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);

        LOGGER.info("[M1-10] {}: paused with both scrap slots full and {} FE kept, resumed, and "
                + "the failing wafer it finished consumed zero packages", SCRAP_BANK_FULL, charged);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 15-16. The banks, and a second lot
    // ------------------------------------------------------------------

    /**
     * A chip stack the machine did not write blocks the bank.
     *
     * <p>Each chip slot holds a named stack of the catalog chip, so every slot has plenty of
     * numeric room and none of it is room for a <em>plain</em> chip: the preflight compares item
     * and components, so the run must wait. Freeing compatible space resumes it, the chips it
     * writes are plain, and the stacks that were left where they were keep both their counts and
     * their name.</p>
     */
    private static void namedChipsBlockTheBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a wafer cannot be interrupted part-way through");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();

        // Each blocker is a plain catalog chip carrying one extra component, which is all it
        // takes to make it a different stack from the plain chip the machine writes.
        List<ItemStack> blockers = new ArrayList<>();
        for (int offset = 0; offset < TestPackageBlockEntity.CHIP_SLOT_COUNT; offset++) {
            ItemStack blocker = new ItemStack(ModItems.LOGIC_CHIP_T1.get(), 1 + offset);
            blocker.set(ModDataComponents.RESIST_TIER.get(), offset + 1);
            machine.setSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST + offset, blocker);
        }
        for (int offset = 0; offset < TestPackageBlockEntity.CHIP_SLOT_COUNT; offset++) {
            ItemStack placed = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST + offset);
            blockers.add(placed.copy());
            helper.assertTrue(Integer.valueOf(offset + 1)
                    .equals(placed.get(ModDataComponents.RESIST_TIER.get())),
                "the stack in chip slot " + offset + " carries no extra component, so the "
                    + "fixture is not component-modified");
        }

        machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
            new ItemStack(ModItems.CHIP_PACKAGE.get(), packages));
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, totalTicks * numbers.energyPerTick());
        long charged = machine.buffer().getAmountAsLong();

        // Fixture honesty: there is numeric room, and it is the components that refuse it.
        int numericRoom = 0;
        for (int offset = 0; offset < TestPackageBlockEntity.CHIP_SLOT_COUNT; offset++) {
            numericRoom += VANILLA_STACK
                - machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST + offset).getCount();
        }
        helper.assertTrue(numericRoom >= numbers.chipsPerWafer(),
            "the named chip slots hold " + numericRoom + " items of slack, less than "
                + "chipsPerWafer " + numbers.chipsPerWafer()
                + "; this case cannot prove anything about numeric room");
        helper.assertValueEqual(0, bankRoomFor(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "a bank of named chip stacks reports room for plain chips");

        runTicks(machine, 40);
        helper.assertTrue(machine.isQueueActive(),
            "named chip stacks dropped the accepted run instead of pausing it");
        helper.assertTrue(machine.isPaused(),
            "a bank of named chip stacks did not pause the machine");
        helper.assertValueEqual(0, progressOf(machine),
            "a bank of named chip stacks advanced the run");
        helper.assertValueEqual(charged, machine.buffer().getAmountAsLong(),
            "a bank of named chip stacks cost the player FE");
        helper.assertValueEqual(packages,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a bank of named chip stacks consumed packages");

        // Free the first slot for compatible output and let the wafer finish.
        ItemStack taken = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).copy();
        machine.clearSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST);
        runTicks(machine, numbers.ticksPerWafer());
        helper.assertValueEqual(numbers.ticksPerWafer(), progressOf(machine),
            "the run did not finish its first wafer once compatible space was free");
        ItemStack written = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST);
        helper.assertValueEqual(numbers.chipsPerWafer(), written.getCount(),
            "the freed slot did not receive exactly chipsPerWafer chips");
        helper.assertTrue(written.is(ModItems.LOGIC_CHIP_T1.get()),
            "the freed slot received " + written.getItem() + " rather than the catalog chip");
        helper.assertTrue(written.get(ModDataComponents.RESIST_TIER.get()) == null,
            "the chips written into the freed slot carry an extra component, so they are not the "
                + "plain catalog chip the machine is supposed to produce");
        helper.assertValueEqual(packages - numbers.packagesPerWafer(),
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the completed wafer did not take exactly package_per_wafer packages");

        // The stacks that were left where they were kept both their counts and their component.
        for (int offset = 1; offset < TestPackageBlockEntity.CHIP_SLOT_COUNT; offset++) {
            ItemStack still = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST + offset);
            ItemStack expected = blockers.get(offset);
            helper.assertValueEqual(expected.getCount(), still.getCount(),
                "the component-modified chip stack in slot " + offset + " lost or gained items");
            helper.assertTrue(ItemStack.isSameItemSameComponents(expected, still),
                "the component-modified chip stack in slot " + offset
                    + " lost or changed its components");
            helper.assertTrue(Integer.valueOf(offset)
                    .equals(still.get(ModDataComponents.RESIST_TIER.get())),
                "the component-modified chip stack in slot " + offset + " lost its component");
        }
        // And the stack that was taken out of the machine is still exactly what it was.
        helper.assertTrue(taken.getCount() >= 1
                && Integer.valueOf(1).equals(taken.get(ModDataComponents.RESIST_TIER.get())),
            "the stack taken out of the machine was altered");

        // The rest of the lot still runs once the rest of the bank is emptied.
        clearBank(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT);
        machine.setStoredEnergy(numbers.energyPerTick() * (totalTicks - progressOf(machine)));
        runUntilFinished(helper, machine, totalTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);

        LOGGER.info("[M1-10] {}: four component-modified chip stacks with {} items of numeric slack "
                + "and zero usable room, resumed on plain chips when one slot was freed",
            NAMED_CHIP_BANK, numericRoom);
        helper.succeed();
    }

    private static void secondLotWaitsForTheFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 3,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a run cannot be watched part-way through while another lot waits");

        Lot first = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (first == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }
        Lot second = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (second == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }

        loadPackages(machine, first.passing() * numbers.packagesPerWafer());
        loadLot(machine, ModItems.FOUP_MK1.get(), first.contents());
        charge(helper, machine,
            first.size() * numbers.ticksPerWafer() * numbers.energyPerTick());

        machine.serverTick();
        helper.assertTrue(machine.isQueueActive(), "the first lot was not accepted");
        helper.assertValueEqual(first.size(), machine.wafersTotal(),
            "the accepted queue does not cover the first lot");

        // The empty FOUP the machine returned may simply be taken away: the queue does not
        // depend on it and does not restart without it.
        ItemStack returned = machine.slot(TestPackageBlockEntity.FOUP_SLOT);
        FoupContents returnedLot = FoupItems.contentsOf(returned);
        helper.assertTrue(returnedLot != null && returnedLot.isEmpty(),
            "the admission tick did not put an empty FOUP in the slot");
        machine.clearSlot(TestPackageBlockEntity.FOUP_SLOT);
        runTicks(machine, 2);
        helper.assertTrue(machine.isQueueActive(),
            "removing the returned empty FOUP cancelled the accepted queue");
        helper.assertValueEqual(2, progressOf(machine),
            "the queue did not keep working after the returned empty FOUP was removed");

        // A second, fully loaded FOUP goes into the same slot and is not looked at.
        machine.setSlot(TestPackageBlockEntity.FOUP_SLOT,
            FoupItems.with(ModItems.FOUP_MK1.get(), second.contents()));
        UUID waitingId = second.contents().lotId();
        ItemStack waiting = machine.slot(TestPackageBlockEntity.FOUP_SLOT).copy();
        int packages = first.passing() * numbers.packagesPerWafer();

        runTicks(machine, 2);
        helper.assertValueEqual(4, progressOf(machine),
            "the queue stopped advancing once a second FOUP was in the slot");
        helper.assertTrue(ItemStack.isSameItemSameComponents(waiting,
                machine.slot(TestPackageBlockEntity.FOUP_SLOT)),
            "the waiting lot was replaced while the first queue was still working");
        helper.assertValueEqual(first.size(), machine.wafersTotal(),
            "the queue changed its wafer total while a second lot was in the slot, so a second "
                + "lot must have been judged");

        // The first lot runs to its own end, with the second one sitting in the slot.
        runUntilFinished(helper, machine, first.size() * numbers.ticksPerWafer() + 8);
        helper.assertValueEqual(first.size(), machine.wafersDone(),
            "the finished queue reports the wrong number of completed wafers");
        assertConservation(helper, machine, numbers, first,
            first.passing() * numbers.chipsPerWafer(), first.failing() * numbers.chipsPerWafer(),
            packages);
        FoupContents stillWaiting = FoupItems.contentsOf(machine.slot(TestPackageBlockEntity.FOUP_SLOT));
        helper.assertTrue(stillWaiting != null && stillWaiting.lotId().equals(waitingId),
            "the second lot was consumed before the first queue finished");
        helper.assertTrue(stillWaiting != null && stillWaiting.size() == second.size(),
            "the waiting lot lost wafers");

        // Now the second lot is admissible, and it is judged on its own terms.
        int secondPackages = second.passing() * numbers.packagesPerWafer();
        loadPackages(machine, secondPackages);
        charge(helper, machine,
            second.size() * numbers.ticksPerWafer() * numbers.energyPerTick());
        machine.serverTick();
        helper.assertTrue(machine.isQueueActive(),
            "the second lot was not admitted after the first queue finished");
        helper.assertValueEqual(second.size(), machine.wafersTotal(),
            "the second queue does not cover the second lot");
        FoupContents afterAdmission =
            FoupItems.contentsOf(machine.slot(TestPackageBlockEntity.FOUP_SLOT));
        helper.assertTrue(afterAdmission != null && afterAdmission.isEmpty(),
            "the second lot was not consumed by its own admission tick");
        helper.assertTrue(afterAdmission != null && !afterAdmission.lotId().equals(waitingId),
            "the second lot came back under its own id, so it could be judged again");

        runUntilFinished(helper, machine, second.size() * numbers.ticksPerWafer() + 8);
        helper.assertValueEqual(second.size(), machine.wafersDone(),
            "the second queue did not finish its own lot");
        helper.assertValueEqual(0, machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the second run did not consume exactly the packages it was fed");

        LOGGER.info("[M1-10] {}: a {} wafer lot waited untouched through a {} wafer lot and was "
                + "judged only once the first queue had finished",
            SECOND_LOT, second.size(), first.size());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 17. Breaking mid-run
    // ------------------------------------------------------------------

    /**
     * A break after one finished wafer and part-way through the next drops what was finished and
     * nothing else.
     *
     * <p>The point of the case is the thing that cannot be allowed: the judged lot is already
     * gone, so the drops must hold an <em>empty</em> FOUP and never the lot that was judged, and
     * the chips and scrap must equal the finished wafers only. The remaining wafers' outputs are
     * lost, which is the documented behaviour.</p>
     */
    private static void breakDropsNoQueuedOutputs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so the second wafer cannot be interrupted part-way through");

        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }
        helper.assertTrue(lot.size() >= 2,
            "the fixture lot holds " + lot.size() + " wafers, so there is no queued wafer to lose");

        int packages = lot.passing() * numbers.packagesPerWafer();
        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());

        machine.serverTick();
        runTicks(machine, numbers.ticksPerWafer());
        helper.assertValueEqual(1, machine.wafersDone(),
            "the test did not get exactly one wafer finished before the break");
        int partial = Math.max(1, numbers.ticksPerWafer() - 1);
        runTicks(machine, partial);
        helper.assertValueEqual(numbers.ticksPerWafer() + partial, progressOf(machine),
            "the test did not get part-way through the second wafer");

        boolean firstPassed = lot.flags()[0];
        int expectedChips = firstPassed ? numbers.chipsPerWafer() : 0;
        int expectedScrap = firstPassed ? 0 : numbers.chipsPerWafer();
        int expectedPackages = packages - (firstPassed ? numbers.packagesPerWafer() : 0);
        helper.assertValueEqual(expectedChips, totalOutput(machine),
            "the machine did not hold exactly the finished wafers' output before the break");

        helper.killAllEntitiesOfClass(ItemEntity.class);
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.assertValueEqual(0, sumOf(droppedItems(level, pos)),
            "the test area already held item entities before the break");

        // Replacing the block goes through the same removal path a player mining it does, and
        // the block entity is a Container, so its slots drop.
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(machine.isRemoved(), "the machine survived the break");

        Map<Item, Integer> drops = droppedItems(level, pos);
        helper.assertValueEqual(expectedChips, countOf(drops, ModItems.LOGIC_CHIP_T1.get()),
            "the break dropped " + countOf(drops, ModItems.LOGIC_CHIP_T1.get())
                + " chips rather than the " + expectedChips + " the finished wafer produced");
        helper.assertValueEqual(expectedScrap, countOf(drops, ModItems.CHIP_SCRAP.get()),
            "the break dropped the wrong amount of scrap");
        helper.assertValueEqual(expectedPackages, countOf(drops, ModItems.CHIP_PACKAGE.get()),
            "the break dropped the wrong number of packages");
        helper.assertValueEqual(1, countOf(drops, ModItems.FOUP_MK1.get()),
            "the break did not drop exactly one FOUP");
        helper.assertValueEqual(expectedChips + expectedScrap + expectedPackages + 1,
            sumOf(drops),
            "the break dropped something this machine cannot account for: " + drops);

        // The FOUP that came back is the empty carrier, not the lot that was judged.
        ItemEntity droppedFoup = findItemEntity(level, pos, ModItems.FOUP_MK1.get());
        helper.assertTrue(droppedFoup != null, "no FOUP item entity was dropped");
        if (droppedFoup != null) {
            FoupContents dropped = FoupItems.contentsOf(droppedFoup.getItem());
            helper.assertTrue(dropped != null && dropped.isEmpty(),
                "the dropped FOUP still holds a lot: the judged lot must never come back");
            helper.assertTrue(dropped != null && !dropped.lotId().equals(lot.contents().lotId()),
                "the dropped FOUP carries the judged lot's id, so that lot could be judged again");
        }
        LOGGER.info("[M1-10] {}: broke at {}/{} ticks and dropped {} - the {} wafers still queued "
                + "produced nothing and the judged lot did not come back",
            BREAK_MID_RUN, progressOf(machine), lot.size() * numbers.ticksPerWafer(), drops,
            lot.size() - 1);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 18. NBT in the middle of a wafer
    // ------------------------------------------------------------------

    private static void saveLoadRoundTripMidWafer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so the round trip cannot be taken mid-wafer");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        helper.assertTrue(lot.size() >= 3,
            "the fixture lot holds " + lot.size() + " wafers, so there is not enough left after "
                + "the finished one to be caught mid-wafer");

        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();
        int cost = totalTicks * numbers.energyPerTick();
        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, cost);

        machine.serverTick();
        runTicks(machine, numbers.ticksPerWafer());
        helper.assertValueEqual(1, machine.wafersDone(),
            "the test did not get one wafer finished before the round trip");
        int chipsBefore = bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get());
        int scrapBefore = bankCountOf(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
            TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get());
        helper.assertValueEqual(numbers.chipsPerWafer(), chipsBefore,
            "the first wafer did not write exactly chipsPerWafer chips before the round trip");
        int packagesBefore = machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount();
        helper.assertValueEqual(packages - numbers.packagesPerWafer(), packagesBefore,
            "the first wafer did not take exactly package_per_wafer packages");

        int partial = Math.max(1, numbers.ticksPerWafer() / 2);
        runTicks(machine, partial);
        int progressBefore = progressOf(machine);
        long energyBefore = machine.buffer().getAmountAsLong();
        helper.assertValueEqual(numbers.ticksPerWafer() + partial, progressBefore,
            "the test did not get part-way through the second wafer");

        // Three save/load cycles, none of them followed by a tick: a world reload can land
        // between a load and the next tick, and every one of those states has to carry the
        // completed outputs, the progress, the frozen parameters and the remaining FE.
        for (int cycle = 1; cycle <= 3; cycle++) {
            CompoundTag tag = saveTag(level, machine);
            loadTag(level, machine, tag);
            helper.assertValueEqual(progressBefore, progressOf(machine),
                "save/load cycle " + cycle + " lost the paid progress");
            helper.assertValueEqual(1, machine.wafersDone(),
                "save/load cycle " + cycle + " lost the completed wafer count");
            helper.assertValueEqual(lot.size(), machine.wafersTotal(),
                "save/load cycle " + cycle + " lost the wafer total");
            helper.assertValueEqual(chipsBefore,
                bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                    TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
                "save/load cycle " + cycle + " lost the chips the finished wafer produced");
            helper.assertValueEqual(scrapBefore,
                bankCountOf(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                    TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get()),
                "save/load cycle " + cycle + " lost the scrap");
            helper.assertValueEqual(packagesBefore,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "save/load cycle " + cycle + " lost packages");
            helper.assertValueEqual(energyBefore, machine.buffer().getAmountAsLong(),
                "save/load cycle " + cycle + " lost the energy the paid ticks left");
            helper.assertTrue(machine.isQueueActive(),
                "save/load cycle " + cycle + " dropped the accepted queue");
        }

        // One tick on the restored state costs exactly one frozen charge.
        machine.serverTick();
        helper.assertValueEqual(progressBefore + 1, progressOf(machine),
            "the first tick after the restore did not continue from the saved progress");
        helper.assertValueEqual(energyBefore - numbers.energyPerTick(),
            machine.buffer().getAmountAsLong(),
            "the first tick after the restore did not cost exactly one frozen charge");

        // And it finishes at exactly the cost that was left, with exactly the output that was
        // left to produce.
        runUntilFinished(helper, machine, totalTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the restored run did not finish on the FE the save/load cycles brought back");

        // A finished queue is terminal: extra ticks write nothing and spend nothing.
        int finished = totalOutput(machine);
        int left = machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount();
        long stored = machine.buffer().getAmountAsLong();
        runTicks(machine, 40);
        helper.assertValueEqual(finished, totalOutput(machine),
            "extra ticks after the queue finished produced more output");
        helper.assertValueEqual(left, machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "extra ticks after the queue finished consumed more packages");
        helper.assertValueEqual(stored, machine.buffer().getAmountAsLong(),
            "extra ticks after the queue finished spent FE");
        helper.assertFalse(machine.isQueueActive(),
            "a machine with no FOUP in its slot started a new queue by itself");
        helper.assertValueEqual(lot.size(), machine.wafersDone(),
            "the finished queue stopped reporting its completed count");
        helper.assertValueEqual(lot.size(), machine.wafersTotal(),
            "the finished queue stopped reporting its total");

        LOGGER.info("[M1-10] {}: three save/load cycles at {}/{} ticks kept {} chips, {} packages "
                + "and {} FE, and the run finished on exactly that state",
            SAVE_LOAD, progressBefore, totalTicks, chipsBefore, packagesBefore, energyBefore);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 19. Frozen parameters, and a definition that goes away
    // ------------------------------------------------------------------

    /**
     * An accepted run is governed by the numbers it was accepted under, whatever the config and
     * the definition say afterwards — including when what they say is not usable at all — and
     * the next lot is governed by the numbers in force when it is offered.
     *
     * <p>The swaps are the real loader fields, because {@code serverTick} re-reads the registry on
     * every tick: calling {@code applyDefinition} on the machine would be undone immediately and
     * would prove nothing. The unusable replacement uses {@code package_per_wafer = 65}, which is
     * a legal positive param for the generic definition but more than one slot can hold, so the
     * machine refuses to admit anything rather than having to guess.</p>
     */
    private static void keepsFrozenParameters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers original = numbers(helper, definition, tier);
        if (original == null) {
            return;
        }
        int originalChips = original.chipsPerWafer();

        Lot first = mixedLot(level, tier, DieTypes.LOGIC, null, original);
        if (first == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, original));
            return;
        }
        Lot second = mixedLot(level, tier, DieTypes.LOGIC, null, original);
        if (second == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, original));
            return;
        }

        int firstPackages = first.passing() * original.packagesPerWafer();
        loadPackages(machine, firstPackages);
        loadLot(machine, ModItems.FOUP_MK1.get(), first.contents());
        charge(helper, machine,
            first.size() * original.ticksPerWafer() * original.energyPerTick());
        machine.serverTick();
        helper.assertTrue(machine.isQueueActive(), "the first lot was not accepted");
        int frozenCost =
            first.size() * original.ticksPerWafer() * original.energyPerTick();

        // A different chip count in the config, and a definition whose own per-tick cost, wafer
        // length and package amount all differ, with a package amount no slot can hold.
        int movedChips = originalChips == 1 ? 2 : 1;
        MachineDefinition unusable = redefined(definition,
            original.ticksPerWafer() + 1, VANILLA_STACK + 1, original.energyPerTick() + 1);

        withChipsPerWafer(movedChips, () ->
            swapDefinitions(definitions -> replace(definitions, unusable), () -> {
                helper.assertValueEqual(movedChips, EUVCRAFTMod.settings().chipsPerWafer(),
                    "the config change did not take effect");

                // The accepted run keeps every frozen number.
                runUntilFinished(helper, machine,
                    first.size() * original.ticksPerWafer() + 8);
                assertConservation(helper, machine, original, first,
                    first.passing() * originalChips, first.failing() * originalChips,
                    firstPackages);
                helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
                    "the accepted run did not spend its own frozen cost of " + frozenCost
                        + " FE; it appears to have re-read the current definition");
                helper.assertTrue(bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                        TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get())
                        != first.passing() * movedChips,
                    "the accepted run used the chip count the config holds now (" + movedChips
                        + ") rather than the " + originalChips + " it was accepted with");

                // A second lot offered while those parameters are unusable is not admitted.
                int finishedOutputs = totalOutput(machine);
                ItemStack waiting = FoupItems.with(ModItems.FOUP_MK1.get(), second.contents());
                machine.setSlot(TestPackageBlockEntity.FOUP_SLOT, waiting);
                loadPackages(machine, VANILLA_STACK);
                long held = machine.buffer().getAmountAsLong();
                runTicks(machine, 40);
                helper.assertFalse(machine.isQueueActive(),
                    "a lot was admitted while package_per_wafer was " + (VANILLA_STACK + 1)
                        + ", more than a slot holds");
                helper.assertTrue(ItemStack.isSameItemSameComponents(waiting,
                        machine.slot(TestPackageBlockEntity.FOUP_SLOT)),
                    "the lot was consumed by an admission the machine could not make");
                helper.assertValueEqual(finishedOutputs, totalOutput(machine),
                    "the unusable parameters still let the waiting lot produce output");
                helper.assertValueEqual(held, machine.buffer().getAmountAsLong(),
                    "the unusable parameters still cost the player FE");
                helper.assertValueEqual(VANILLA_STACK,
                    machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                    "the unusable parameters still consumed packages");
                helper.assertTrue(machine.refusalReason() == null,
                    "unusable parameters published a refusal reason of " + machine.refusalReason()
                        + "; they are not an admission failure of a lot");
                LOGGER.info("[M1-10] {}: the accepted run finished at its own frozen numbers "
                        + "(chips {}, ticks {}, packages {}, cost {} FE) and the next lot was "
                        + "refused while the parameters were unusable", FROZEN_PARAMETERS,
                    originalChips, original.ticksPerWafer(), original.packagesPerWafer(),
                    frozenCost);
            }));

        // The config and the registry are back, and identical to what they were.
        helper.assertValueEqual(originalChips, EUVCRAFTMod.settings().chipsPerWafer(),
            "the chipsPerWafer config was not restored");
        MachineDefinition restored = machine.definition().orElse(null);
        helper.assertTrue(restored != null
                && restored.energyPerTick() == definition.energyPerTick()
                && restored.params().equals(definition.params()),
            "the machine definition registry was not restored after the fixture");

        // The next lot is governed by the parameters in force when it is offered. Every one of
        // them is resolved inside the fixture, because the fixture is what changes them.
        int newChips = originalChips == 1 ? 2 : 1;
        int newPackages = original.packagesPerWafer() == 1 ? 2 : 1;
        int newTicks = original.ticksPerWafer() <= 1 ? 2 : original.ticksPerWafer() - 1;
        helper.assertTrue(newTicks != original.ticksPerWafer(),
            "the replacement definition kept the same wafer length");
        helper.assertTrue(newPackages != original.packagesPerWafer(),
            "the replacement definition kept the same package amount");
        helper.assertTrue(newChips != originalChips,
            "the replacement config kept the same chip count");
        MachineDefinition usable = redefined(definition, newTicks, newPackages,
            original.energyPerTick());

        withChipsPerWafer(newChips, () ->
            swapDefinitions(definitions -> replace(definitions, usable), () -> {
                Tier again = tier(helper, 1);
                MachineDefinition live = machine.definition().orElse(null);
                if (again == null || live == null) {
                    return;
                }
                Numbers fresh = numbers(helper, live, again);
                if (fresh == null) {
                    return;
                }
                helper.assertValueEqual(newChips, fresh.chipsPerWafer(),
                    "the fixture's chip count is not the one the config now holds");
                helper.assertValueEqual(newTicks, fresh.ticksPerWafer(),
                    "the fixture's wafer length is not the one the definition now declares");
                helper.assertValueEqual(newPackages, fresh.packagesPerWafer(),
                    "the fixture's package amount is not the one the definition now declares");

                Lot next = mixedLot(level, again, DieTypes.LOGIC, null, fresh);
                if (next == null) {
                    helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, fresh));
                    return;
                }
                int nextPackages = next.passing() * fresh.packagesPerWafer();
                int cost = next.size() * fresh.ticksPerWafer() * fresh.energyPerTick();

                machine.clearContent();
                machine.setStoredEnergy(0);
                loadPackages(machine, nextPackages);
                loadLot(machine, ModItems.FOUP_MK1.get(), next.contents());
                charge(helper, machine, cost);
                machine.serverTick();
                helper.assertTrue(machine.isQueueActive(),
                    "the next lot was not admitted under the new valid parameters");
                runUntilFinished(helper, machine, next.size() * fresh.ticksPerWafer() + 8);
                assertConservation(helper, machine, fresh, next,
                    next.passing() * newChips, next.failing() * newChips, nextPackages);
                helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
                    "the next lot did not cost exactly " + cost + " FE at the new parameters");
                LOGGER.info("[M1-10] {}: the next lot ran at the new parameters (chips {}, ticks "
                        + "{}, packages {}) for {} FE", FROZEN_PARAMETERS, newChips,
                    fresh.ticksPerWafer(), fresh.packagesPerWafer(), cost);
            }));

        helper.assertValueEqual(originalChips, EUVCRAFTMod.settings().chipsPerWafer(),
            "the chipsPerWafer config was not restored after the second fixture");
        helper.succeed();
    }

    private static void missingDefinitionPauses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.ticksPerWafer() >= 2,
            "ticks_per_wafer is " + numbers.ticksPerWafer()
                + ", so a run cannot be caught part-way through a wafer");

        Lot lot = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (lot == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        int totalTicks = lot.size() * numbers.ticksPerWafer();
        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, totalTicks * numbers.energyPerTick());

        machine.serverTick();
        runTicks(machine, Math.max(1, numbers.ticksPerWafer() / 2));
        helper.assertTrue(machine.isQueueActive(), "the lot was not accepted");
        int progressBefore = progressOf(machine);
        long energyBefore = machine.buffer().getAmountAsLong();
        int outputsBefore = totalOutput(machine);
        int capacity = machine.buffer().getCapacityAsInt();
        helper.assertValueEqual(definition.energyCapacity(), capacity,
            "the machine's buffer capacity is not the definition's energy_capacity");
        UUID judgedId = lot.contents().lotId();

        swapDefinitions(definitions -> without(definitions,
                TestPackageBlockEntity.MACHINE_ID), () -> {
            helper.assertTrue(machine.definition().isEmpty(),
                "the definition fixture did not actually remove the machine's definition");

            runTicks(machine, 40);
            helper.assertTrue(machine.isQueueActive(),
                "a missing definition dropped the accepted queue");
            helper.assertTrue(machine.isPaused(),
                "a missing definition did not pause the accepted queue");
            helper.assertValueEqual(TestPackageBlockEntity.STATE_PAUSED, machine.state(),
                "a missing definition did not publish the paused state");
            helper.assertValueEqual(progressBefore, progressOf(machine),
                "a missing definition advanced the queue");
            helper.assertValueEqual(energyBefore, machine.buffer().getAmountAsLong(),
                "a missing definition spent stored FE");
            helper.assertValueEqual(outputsBefore, totalOutput(machine),
                "a missing definition produced output");
            helper.assertValueEqual(packages,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "a missing definition consumed packages");

            // The documented boundary: a definition that is gone closes the buffer, so the
            // machine takes no energy and can be told a capacity of zero. What was already
            // charged is deliberately not asserted either way here, because closing a buffer is
            // allowed to discard it; only the closure itself is pinned.
            helper.assertValueEqual(0, machine.buffer().getCapacityAsInt(),
                "a missing definition left the real buffer its capacity");
            helper.assertValueEqual(0, machine.data().get(FabMachineBlockEntity.DATA_CAPACITY),
                "a missing definition left the menu its capacity");
            helper.assertValueEqual(0, insert(machine.energy(), 2_000),
                "a buffer with no definition accepted FE");

            // The queue survives a save and a load while the definition is still missing.
            CompoundTag tag = saveTag(level, machine);
            machine.clearContent();
            machine.setStoredEnergy(0);
            loadTag(level, machine, tag);
            helper.assertTrue(machine.isQueueActive(),
                "a queue saved while its definition was missing did not come back");
            helper.assertValueEqual(progressBefore, progressOf(machine),
                "a queue loaded while its definition was missing lost its progress");
            helper.assertValueEqual(lot.size(), machine.wafersTotal(),
                "a queue loaded while its definition was missing lost its wafer total");
            helper.assertValueEqual(outputsBefore, totalOutput(machine),
                "a queue loaded while its definition was missing lost its finished outputs");
            helper.assertValueEqual(0, machine.buffer().getCapacityAsInt(),
                "the load left the buffer its capacity while the definition was missing");

            runTicks(machine, 20);
            helper.assertValueEqual(progressBefore, progressOf(machine),
                "the restored queue advanced while its definition was still missing");
        });

        // The definition is back. The buffer is emptied first rather than trusted: closing a
        // buffer is allowed to discard what was in it, and this case does not assert either
        // way about that, so the machine is put into a known state instead. One tick then
        // re-applies the definition to the real buffer; that tick is a legitimate one and,
        // with nothing to pay for, buys no progress.
        helper.assertTrue(machine.definition().isPresent(),
            "the definition registry was not restored after the fixture");
        machine.setStoredEnergy(0);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the buffer could not be emptied before the definition came back");
        machine.serverTick();
        helper.assertValueEqual(capacity, machine.buffer().getCapacityAsInt(),
            "the restored definition did not restore the buffer capacity");
        helper.assertValueEqual(capacity, machine.data().get(FabMachineBlockEntity.DATA_CAPACITY),
            "the restored definition did not restore the capacity the menu shows");
        helper.assertValueEqual(progressBefore, progressOf(machine),
            "the first tick on the restored definition advanced the queue for free");

        // Recharge exactly what the queue still needs and let it finish.
        int remainingTicks = totalTicks - progressBefore;
        machine.setStoredEnergy(numbers.energyPerTick() * remainingTicks);
        helper.assertValueEqual((long) numbers.energyPerTick() * remainingTicks,
            machine.buffer().getAmountAsLong(),
            "the queue could not be recharged with exactly the FE it still needed");
        runUntilFinished(helper, machine, remainingTicks + 8);
        assertConservation(helper, machine, numbers, lot,
            lot.passing() * numbers.chipsPerWafer(), lot.failing() * numbers.chipsPerWafer(),
            packages);
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(),
            "the resumed queue did not spend exactly the FE it was recharged with");

        FoupContents returned = FoupItems.contentsOf(machine.slot(TestPackageBlockEntity.FOUP_SLOT));
        helper.assertTrue(returned != null && !returned.lotId().equals(judgedId),
            "the FOUP in the slot is the judged lot, which would let it be judged again");

        LOGGER.info("[M1-10] {}: paused at {}/{} ticks while the definition was missing, survived "
                + "a save and load with no definition, resumed on {} FE", MISSING_DEFINITION,
            progressBefore, totalTicks, numbers.energyPerTick() * remainingTicks);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 20. The private snapshot
    // ------------------------------------------------------------------

    /** One malformed queue snapshot: the valid one with exactly one field wrong. */
    private record Variant(String what, String flags, int chips, int packages, int ticks,
                           int energy, int index, int paid, String die, int tier) {
    }

    /**
     * A hand-edited queue snapshot is either completely believed or completely dropped.
     *
     * <p>A valid snapshot written by this file, rather than by the machine, is the positive
     * control: it proves the restore path works and that the field names and the value ranges
     * below are the ones the production code reads. Every malformed variant is then the same
     * snapshot with exactly one thing wrong, and each must leave the machine with no queue, no new
     * outputs, no FE spent, no packages used and the inventory it already had — and with the
     * judged lot still gone.</p>
     */
    private static void malformedQueueFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }
        helper.assertTrue(numbers.chipsPerWafer() <= VANILLA_STACK,
            "chipsPerWafer " + numbers.chipsPerWafer() + " exceeds what one slot holds, so the "
                + "supported per-wafer range asserted below could not be probed");

        // The crafted snapshot starts at wafer index 1, so the control below only produces chips if
        // the fixture's SECOND wafer passes as well as its first. The fixture is asked for both
        // explicitly rather than assumed, and the case says so.
        Lot lot = lotWithPassingPrefix(level, tier, DieTypes.LOGIC, null, numbers, 2, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first two wafers both pass "
                + "could be built for this case");
            return;
        }
        helper.assertTrue(lot.size() >= 3, "the fixture lot needs at least three wafers");
        helper.assertTrue(lot.flags()[0] && lot.flags()[1],
            "the fixture's first two wafers do not both pass");
        String flags = flagsText(lot.flags());
        int index = 1;

        // ---- the positive control: a snapshot this file wrote, all fields in range -------
        wipe(machine);
        loadTag(level, machine, queueTag(level, machine, flags, numbers.chipsPerWafer(),
            numbers.packagesPerWafer(), numbers.ticksPerWafer(), numbers.energyPerTick(),
            index, 0, DieTypes.LOGIC, tier.index()));
        helper.assertTrue(machine.isQueueActive(),
            "a valid hand-written queue snapshot was not restored, so the malformed cases below "
                + "would prove nothing");
        helper.assertValueEqual(index, machine.wafersDone(),
            "the valid snapshot restored the wrong completed wafer count");
        helper.assertValueEqual(lot.size(), machine.wafersTotal(),
            "the valid snapshot restored the wrong wafer total");
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());
        runTicks(machine, numbers.ticksPerWafer());
        helper.assertValueEqual(numbers.chipsPerWafer(),
            bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "the restored queue did not work its wafer, so the control did not really run");
        helper.assertValueEqual(0, machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the restored queue did not consume a package for its passing wafer");

        // ---- the malformed variants: one field wrong each --------------------------------
        int chips = numbers.chipsPerWafer();
        int packagesPerWafer = numbers.packagesPerWafer();
        int ticks = numbers.ticksPerWafer();
        int energy = numbers.energyPerTick();
        List<Variant> malformed = List.of(
            new Variant("a flag that is neither 0 nor 1",
                flags.substring(0, flags.length() - 1) + "x", chips, packagesPerWafer, ticks,
                energy, index, 0, DieTypes.LOGIC, tier.index()),
            new Variant("an empty flag list", "", chips, packagesPerWafer, ticks, energy, index, 0,
                DieTypes.LOGIC, tier.index()),
            new Variant("a negative wafer index", flags, chips, packagesPerWafer, ticks, energy,
                -1, 0, DieTypes.LOGIC, tier.index()),
            new Variant("an index past the end of the plan", flags, chips, packagesPerWafer,
                ticks, energy, lot.size() + 1, 0, DieTypes.LOGIC, tier.index()),
            new Variant("paid ticks equal to a whole wafer", flags, chips, packagesPerWafer,
                ticks, energy, index, ticks, DieTypes.LOGIC, tier.index()),
            new Variant("paid ticks past a whole wafer", flags, chips, packagesPerWafer, ticks,
                energy, index, ticks + 5, DieTypes.LOGIC, tier.index()),
            new Variant("a terminal index with partial progress", flags, chips, packagesPerWafer,
                ticks, energy, lot.size(), 1, DieTypes.LOGIC, tier.index()),
            new Variant("a die type the catalog cannot resolve", flags, chips, packagesPerWafer,
                ticks, energy, index, 0, "quantum", tier.index()),
            new Variant("a tier index below one", flags, chips, packagesPerWafer, ticks, energy,
                index, 0, DieTypes.LOGIC, 0),
            new Variant("a tier no die is catalogued at", flags, chips, packagesPerWafer, ticks,
                energy, index, 0, DieTypes.LOGIC, 8),
            new Variant("more chips per wafer than a slot holds", flags, VANILLA_STACK + 1,
                packagesPerWafer, ticks, energy, index, 0, DieTypes.LOGIC, tier.index()),
            new Variant("more packages per wafer than a slot holds", flags, chips,
                VANILLA_STACK + 1, ticks, energy, index, 0, DieTypes.LOGIC, tier.index()));

        int finishedChips = chips;
        int finishedScrap = 3;
        int finishedPackages = VANILLA_STACK;

        for (int position = 0; position < malformed.size(); position++) {
            Variant variant = malformed.get(position);
            // The machine is given a real, finished inventory before the malformed tag lands, and
            // the tag carries its own copy of it, so "the finished outputs were kept" is a claim
            // about the load rather than about leftover state.
            wipe(machine);
            machine.setSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST,
                new ItemStack(ModItems.LOGIC_CHIP_T1.get(), finishedChips));
            machine.setSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                new ItemStack(ModItems.CHIP_SCRAP.get(), finishedScrap));
            machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
                new ItemStack(ModItems.CHIP_PACKAGE.get(), finishedPackages));

            CompoundTag tag = queueTag(level, machine, variant.flags(), variant.chips(),
                variant.packages(), variant.ticks(), variant.energy(), variant.index(),
                variant.paid(), variant.die(), variant.tier());
            loadTag(level, machine, tag);

            helper.assertFalse(machine.isQueueActive(),
                "a snapshot with " + variant.what() + " was adopted as a live queue");
            helper.assertValueEqual(0, machine.wafersDone(),
                "a snapshot with " + variant.what() + " restored a completed wafer count");
            helper.assertValueEqual(0, machine.wafersTotal(),
                "a snapshot with " + variant.what() + " restored a wafer total");
            helper.assertTrue(machine.refusalReason() == null,
                "a snapshot with " + variant.what() + " published a refusal reason of "
                    + machine.refusalReason());
            helper.assertFalse(machine.isPaused(),
                "a snapshot with " + variant.what() + " left the machine marked paused");
            helper.assertValueEqual(TestPackageBlockEntity.STATE_IDLE, machine.state(),
                "a snapshot with " + variant.what() + " did not settle on the idle state");

            helper.assertValueEqual(finishedChips,
                bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                    TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
                "a snapshot with " + variant.what() + " lost the chips already in the bank");
            helper.assertValueEqual(finishedScrap,
                bankCountOf(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                    TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get()),
                "a snapshot with " + variant.what() + " lost the scrap already in the bank");
            helper.assertValueEqual(finishedPackages,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "a snapshot with " + variant.what() + " changed the package slot");

            long held = machine.buffer().getAmountAsLong();
            runTicks(machine, 30);
            helper.assertValueEqual(finishedChips + finishedScrap, totalOutput(machine),
                "a snapshot with " + variant.what() + " produced output on later ticks");
            helper.assertValueEqual(finishedPackages,
                machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
                "a snapshot with " + variant.what() + " consumed packages on later ticks");
            helper.assertValueEqual(held, machine.buffer().getAmountAsLong(),
                "a snapshot with " + variant.what() + " spent FE on later ticks");

            FoupContents inSlot =
                FoupItems.contentsOf(machine.slot(TestPackageBlockEntity.FOUP_SLOT));
            helper.assertTrue(inSlot == null || inSlot.isEmpty(),
                "a snapshot with " + variant.what() + " recovered a lot into the FOUP slot");
            helper.assertFalse(inSlot != null && inSlot.lotId().equals(lot.contents().lotId()),
                "a snapshot with " + variant.what() + " recovered the judged lot");
            LOGGER.info("[M1-10] {}: variant {} of {} ({}): dropped, finished inventory and the "
                    + "absence of the judged lot both held", MALFORMED_QUEUE, position + 1,
                malformed.size(), variant.what());
        }

        // A valid snapshot is still accepted afterwards, so the malformed cases cannot have left
        // the machine in a state where nothing would restore.
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());
        loadTag(level, machine, queueTag(level, machine, flags, numbers.chipsPerWafer(),
            numbers.packagesPerWafer(), numbers.ticksPerWafer(), numbers.energyPerTick(),
            index, 0, DieTypes.LOGIC, tier.index()));
        helper.assertTrue(machine.isQueueActive(),
            "a valid snapshot was no longer accepted after the malformed ones, so those cases may "
                + "have left the machine in a bad state");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 21. The menu
    // ------------------------------------------------------------------

    /**
     * The menu's layout, its predicates and, above all, what it publishes.
     *
     * <p>Two machines are given valid snapshots whose hidden pass flags are exact opposites and
     * whose index, progress and every public number are identical. They must publish the same
     * seven values, and they must report the same coarse paused state when their outputs are
     * blocked, even though one is waiting on a chip and the other on scrap. A state that named
     * the result would have to differ here.</p>
     *
     * <p>The block entity's update tag is checked through reflection, because it is a vanilla
     * API and its exact signature is what has to be pinned: if it is missing, the case fails and
     * says which signature it looked for.</p>
     */
    private static void menuLayoutAndPublicValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        TestPackageBlockEntity other = placedMachine(helper, SECOND);
        if (machine == null || other == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        ServerPlayer player = connect(level);
        TestPackageMenu menu = new TestPackageMenu(1, player.getInventory(), machine);
        var menuType = BuiltInRegistries.MENU.getValue(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, "test_package"));
        helper.assertTrue(menuType == PackagingMachines.TEST_PACKAGE_MENU.get(),
            "lithmod:test_package is not registered to the expected MenuType");
        AbstractContainerMenu rawClientMenu = menuType.create(2, player.getInventory(),
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess()));
        helper.assertTrue(rawClientMenu instanceof TestPackageMenu,
            "the registered MenuType factory did not return a TestPackageMenu");
        TestPackageMenu clientMenu = (TestPackageMenu) rawClientMenu;

        // Eight machine slots and thirty-six player slots, in the same order on both sides.
        int expectedSlots = TestPackageBlockEntity.SLOT_COUNT + 36;
        helper.assertValueEqual(expectedSlots, menu.slots.size(),
            "the server menu has an unexpected slot count");
        helper.assertValueEqual(menu.slots.size(), rawClientMenu.slots.size(),
            "client and server menu slot layouts disagree");
        for (int index = 0; index < menu.slots.size(); index++) {
            helper.assertValueEqual(menu.slots.get(index).index,
                rawClientMenu.slots.get(index).index,
                "client/server slot index differs at " + index);
        }
        for (int index = 0; index < TestPackageBlockEntity.SLOT_COUNT; index++) {
            helper.assertTrue(menu.slots.get(index).container == machine,
                "machine slot " + index + " is not bound to the machine");
            helper.assertTrue(rawClientMenu.slots.get(index).container != null,
                "client machine slot " + index + " has no container");
            helper.assertFalse(rawClientMenu.slots.get(index).container == machine,
                "client machine slot " + index + " is bound to the server block entity");
        }

        // The predicates both sides can know agree on the same stacks.
        ItemStack foup = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        ItemStack filledFoup = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContents.empty(UUID.randomUUID(), 8));
        ItemStack packages = new ItemStack(ModItems.CHIP_PACKAGE.get(), 4);
        ItemStack ingot = new ItemStack(Items.IRON_INGOT, 3);
        Slot serverFoup = menu.slots.get(TestPackageBlockEntity.FOUP_SLOT);
        Slot clientFoupSlot = rawClientMenu.slots.get(TestPackageBlockEntity.FOUP_SLOT);
        Slot serverPackage = menu.slots.get(TestPackageBlockEntity.PACKAGE_SLOT);
        Slot clientPackageSlot = rawClientMenu.slots.get(TestPackageBlockEntity.PACKAGE_SLOT);
        helper.assertTrue(serverFoup.mayPlace(foup) && clientFoupSlot.mayPlace(foup),
            "the FOUP slot refused an empty FOUP on one of the two sides");
        helper.assertTrue(serverFoup.mayPlace(filledFoup) && clientFoupSlot.mayPlace(filledFoup),
            "the FOUP slot refused a FOUP carrying a lot on one of the two sides");
        helper.assertFalse(serverFoup.mayPlace(ingot) || clientFoupSlot.mayPlace(ingot),
            "the FOUP slot accepted an iron ingot on one of the two sides");
        helper.assertFalse(serverFoup.mayPlace(packages) || clientFoupSlot.mayPlace(packages),
            "the FOUP slot accepted a package on one of the two sides");
        helper.assertValueEqual(1, serverFoup.getMaxStackSize(),
            "the FOUP slot does not hold exactly one FOUP");
        helper.assertTrue(serverPackage.mayPlace(packages) && clientPackageSlot.mayPlace(packages),
            "the package slot refused a chip_package on one of the two sides");
        helper.assertFalse(serverPackage.mayPlace(ingot) || clientPackageSlot.mayPlace(ingot),
            "the package slot accepted an iron ingot on one of the two sides");
        helper.assertFalse(serverPackage.mayPlace(new ItemStack(ModItems.CHIP_SCRAP.get()))
                || clientPackageSlot.mayPlace(new ItemStack(ModItems.CHIP_SCRAP.get())),
            "the package slot accepted chip_scrap on one of the two sides");
        helper.assertFalse(serverPackage.mayPlace(foup) || clientPackageSlot.mayPlace(foup),
            "the package slot accepted a FOUP on one of the two sides");
        for (int offset = 0; offset < TestPackageBlockEntity.OUTPUT_SLOTS; offset++) {
            int index = TestPackageBlockEntity.CHIP_SLOT_FIRST + offset;
            helper.assertFalse(menu.slots.get(index).mayPlace(ingot)
                    || rawClientMenu.slots.get(index).mayPlace(ingot),
                "output slot " + index + " accepted placement on one of the two sides");
            helper.assertFalse(menu.slots.get(index).mayPlace(foup)
                    || rawClientMenu.slots.get(index).mayPlace(foup),
                "output slot " + index + " accepted a FOUP on one of the two sides");
            helper.assertTrue(menu.slots.get(index).mayPickup(player),
                "output slot " + index + " refuses pickup on the server");
        }

        // Shift-click routing, on the real slots.
        player.getInventory().clearContent();
        int hotbar = menu.slots.size() - 9;
        player.containerMenu = menu;
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        player.getInventory().setItem(1, packages);
        player.getInventory().setItem(2, foup);

        helper.assertTrue(menu.quickMoveStack(player, hotbar).isEmpty(),
            "shift-clicking an iron ingot was routed into a machine slot");
        helper.assertFalse(menu.quickMoveStack(player, hotbar + 1).isEmpty(),
            "shift-clicking a chip_package moved nothing");
        helper.assertValueEqual(4, machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "the chip_package did not land in the package slot");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.FOUP_SLOT).isEmpty(),
            "the chip_package landed in the FOUP slot");
        helper.assertFalse(menu.quickMoveStack(player, hotbar + 2).isEmpty(),
            "shift-clicking a FOUP moved nothing");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.FOUP_SLOT).is(ModItems.FOUP_MK1.get()),
            "the FOUP did not land in the FOUP slot");

        machine.setSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST,
            new ItemStack(ModItems.LOGIC_CHIP_T1.get(), 6));
        helper.assertFalse(
            menu.quickMoveStack(player, TestPackageBlockEntity.CHIP_SLOT_FIRST).isEmpty(),
            "shift-clicking a chip out of the machine moved nothing");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).isEmpty(),
            "the chip stayed in the machine after a shift-click out");
        machine.setSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST,
            new ItemStack(ModItems.CHIP_SCRAP.get(), 2));
        helper.assertFalse(
            menu.quickMoveStack(player, TestPackageBlockEntity.SCRAP_SLOT_FIRST).isEmpty(),
            "shift-clicking scrap out of the machine moved nothing");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.SCRAP_SLOT_FIRST).isEmpty(),
            "the scrap stayed in the machine after a shift-click out");
        // A fresh carrier: the one above was the player's own stack and the shift-click above
        // mutated that very instance down to EMPTY, so reusing it would put nothing in the slot.
        machine.setSlot(TestPackageBlockEntity.FOUP_SLOT,
            FoupItems.emptyFoup(ModItems.FOUP_MK1.get()));
        helper.assertFalse(menu.quickMoveStack(player, TestPackageBlockEntity.FOUP_SLOT).isEmpty(),
            "shift-clicking the FOUP out of the machine moved nothing");
        helper.assertTrue(machine.slot(TestPackageBlockEntity.FOUP_SLOT).isEmpty(),
            "the FOUP stayed in the machine after a shift-click out");

        // Exactly seven public wire values, on both sides, with the machine's own three last.
        helper.assertValueEqual(FabMachineBlockEntity.DATA_COUNT
                + TestPackageBlockEntity.QUEUE_DATA_COUNT, 7,
            "the test package machine is not expected to sync exactly seven values");
        helper.assertValueEqual(FabMachineBlockEntity.DATA_COUNT, TestPackageMenu.STATE_WIRE_INDEX,
            "the state value is not the fifth value on the wire");
        helper.assertValueEqual(FabMachineBlockEntity.DATA_COUNT + 1,
            TestPackageMenu.DONE_WIRE_INDEX, "the done value is not the sixth value on the wire");
        helper.assertValueEqual(FabMachineBlockEntity.DATA_COUNT + 2,
            TestPackageMenu.TOTAL_WIRE_INDEX, "the total value is not the seventh value on the wire");
        helper.assertValueEqual(7, dataSlotCount(menu),
            "the server menu did not register seven synced values");
        helper.assertValueEqual(7, dataSlotCount(rawClientMenu),
            "the client menu did not register seven synced values");
        helper.assertValueEqual(TestPackageBlockEntity.STATE_IDLE, clientMenu.state(),
            "a fresh client menu already shows a state before anything was sent to it");
        helper.assertValueEqual(TestPackageBlockEntity.STATE_IDLE, machine.state(),
            "an idle machine with no lot published a state other than idle");

        // Two machines, opposite hidden flags, identical public numbers.
        Lot fixture = mixedLot(level, tier, DieTypes.LOGIC, null, numbers);
        if (fixture == null) {
            helper.fail(noMixedLotMessage(DieTypes.LOGIC, 1, numbers));
            return;
        }
        helper.assertTrue(fixture.size() >= 2, "the fixture lot needs at least two wafers");
        String forwards = flagsText(fixture.flags());
        String backwards = flagsText(reverse(fixture.flags()));
        helper.assertFalse(forwards.equals(backwards),
            "the fixture's flags are their own reverse, so the two machines would hold identical "
                + "hidden data and this case would prove nothing");
        // Both machines start at wafer zero with the same paid progress, so every published
        // number is identical while the hidden per-wafer results are not.
        int index = 0;
        int paid = Math.max(0, Math.min(numbers.ticksPerWafer() - 1, 1));
        int whole = fixture.size() * numbers.ticksPerWafer() * numbers.energyPerTick();

        for (TestPackageBlockEntity target : List.of(machine, other)) {
            target.clearContent();
            target.setStoredEnergy(0);
            loadPackages(target, VANILLA_STACK);
        }
        loadTag(level, machine, queueTag(level, machine, forwards, numbers.chipsPerWafer(),
            numbers.packagesPerWafer(), numbers.ticksPerWafer(), numbers.energyPerTick(),
            index, paid, DieTypes.LOGIC, tier.index()));
        loadTag(level, other, queueTag(level, other, backwards, numbers.chipsPerWafer(),
            numbers.packagesPerWafer(), numbers.ticksPerWafer(), numbers.energyPerTick(),
            index, paid, DieTypes.LOGIC, tier.index()));
        helper.assertTrue(machine.isQueueActive() && other.isQueueActive(),
            "the two opposite-flag machines did not both accept their snapshots");
        charge(helper, machine, whole);
        charge(helper, other, whole);

        TestPackageMenu otherMenu = new TestPackageMenu(3, player.getInventory(), other);
        helper.assertValueEqual(machine.state(), other.state(),
            "the two machines published different states for the same public state of work");
        helper.assertValueEqual(machine.wafersDone(), other.wafersDone(),
            "the two machines published different completed counts");
        helper.assertValueEqual(machine.wafersTotal(), other.wafersTotal(),
            "the two machines published different totals");
        helper.assertValueEqual(progressOf(machine), progressOf(other),
            "the two machines published different progress");
        helper.assertValueEqual(machine.data().get(FabMachineBlockEntity.DATA_TICKS),
            other.data().get(FabMachineBlockEntity.DATA_TICKS),
            "the two machines published different tick totals");
        helper.assertValueEqual(machine.buffer().getAmountAsLong(),
            other.buffer().getAmountAsLong(),
            "the two machines published different energy");
        helper.assertValueEqual(machine.state(), menu.state(),
            "the server menu does not show the machine's own state");
        helper.assertValueEqual(machine.wafersDone(), menu.wafersDone(),
            "the server menu does not show the machine's own completed count");
        helper.assertValueEqual(machine.wafersTotal(), menu.wafersTotal(),
            "the server menu does not show the machine's own total");
        helper.assertValueEqual(machine.data().get(FabMachineBlockEntity.DATA_ENERGY),
            menu.energy(), "the server menu does not show the machine's own stored energy");

        // What the client would receive, through the real set-data path.
        clientMenu.setData(FabMachineBlockEntity.DATA_ENERGY,
            machine.data().get(FabMachineBlockEntity.DATA_ENERGY));
        clientMenu.setData(FabMachineBlockEntity.DATA_CAPACITY,
            machine.data().get(FabMachineBlockEntity.DATA_CAPACITY));
        clientMenu.setData(FabMachineBlockEntity.DATA_PROGRESS, progressOf(machine));
        clientMenu.setData(FabMachineBlockEntity.DATA_TICKS,
            machine.data().get(FabMachineBlockEntity.DATA_TICKS));
        clientMenu.setData(TestPackageMenu.STATE_WIRE_INDEX, machine.state());
        clientMenu.setData(TestPackageMenu.DONE_WIRE_INDEX, machine.wafersDone());
        clientMenu.setData(TestPackageMenu.TOTAL_WIRE_INDEX, machine.wafersTotal());
        otherMenu.setData(TestPackageMenu.STATE_WIRE_INDEX, other.state());
        otherMenu.setData(TestPackageMenu.DONE_WIRE_INDEX, other.wafersDone());
        otherMenu.setData(TestPackageMenu.TOTAL_WIRE_INDEX, other.wafersTotal());
        helper.assertValueEqual(machine.state(), clientMenu.state(),
            "the state did not reach the client menu through the wire index");
        helper.assertValueEqual(machine.wafersDone(), clientMenu.wafersDone(),
            "the completed count did not reach the client menu through the wire index");
        helper.assertValueEqual(machine.wafersTotal(), clientMenu.wafersTotal(),
            "the total did not reach the client menu through the wire index");
        helper.assertValueEqual(machine.data().get(FabMachineBlockEntity.DATA_ENERGY),
            clientMenu.energy(),
            "the energy did not reach the client menu through the wire index");
        helper.assertValueEqual(clientMenu.progress(), otherMenu.progress(),
            "the two client menus disagree about progress for identical public numbers");

        // Block both banks on both machines: one is waiting on a chip and the other on scrap,
        // and the coarse state must be the same either way.
        for (TestPackageBlockEntity target : List.of(machine, other)) {
            fillBank(target, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get());
            fillBank(target, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get());
        }
        long beforeBlocked = machine.buffer().getAmountAsLong();
        runTicks(machine, 20);
        runTicks(other, 20);
        helper.assertValueEqual(TestPackageBlockEntity.STATE_PAUSED, machine.state(),
            "the machine waiting on a chip slot did not publish the paused state");
        helper.assertValueEqual(machine.state(), other.state(),
            "the paused state depends on which wafer is next, so it leaks a per-wafer result");
        helper.assertValueEqual(progressOf(machine), progressOf(other),
            "the two machines made different progress while both were blocked");
        helper.assertValueEqual(beforeBlocked, machine.buffer().getAmountAsLong(),
            "a blocked machine spent FE");
        helper.assertValueEqual(VANILLA_STACK,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a blocked machine consumed packages");

        // The hidden data really is different, which is what the seven values above had to
        // survive: unblocking both writes opposite products.
        for (TestPackageBlockEntity target : List.of(machine, other)) {
            clearBank(target, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT);
            clearBank(target, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT);
            target.setStoredEnergy(whole);
        }
        runUntilFinished(helper, machine, fixture.size() * numbers.ticksPerWafer() + 8);
        runUntilFinished(helper, other, fixture.size() * numbers.ticksPerWafer() + 8);
        helper.assertValueEqual(fixture.passing() * numbers.chipsPerWafer(),
            bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "the forwards-flag machine did not produce the chips its own flags describe");
        helper.assertValueEqual(fixture.failing() * numbers.chipsPerWafer(),
            bankCountOf(other, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT, ModItems.CHIP_SCRAP.get()),
            "the backwards-flag machine did not produce the scrap its own flags describe");
        helper.assertTrue(bankCountOf(other, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                    TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get())
                != bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT, ModItems.LOGIC_CHIP_T1.get()),
            "the two opposite-flag machines produced the same chips, so the fixtures are not "
                + "actually opposite");

        // The default block entity update tag must not carry the private queue.
        CompoundTag updateTag = machine.getUpdateTag(level.registryAccess());
        helper.assertFalse(updateTag.contains(STATE_KEY),
            "the default update tag carries the private '" + STATE_KEY + "' node, so the pass "
                + "flags, the frozen parameters and the per-wafer progress are published to "
                + "every client the block update reaches");
        helper.assertTrue(machine.getUpdatePacket() == null,
            "the machine must not publish its private queue through a block entity update packet");
        LOGGER.info("[M1-10] {}: {} slots, 7 synced values with the machine's own on indices "
                + "{}, {}, {}, two opposite-flag machines publishing identical values, and no "
                + "'{}' node in the update tag", MENU_VALUES, menu.slots.size(),
            TestPackageMenu.STATE_WIRE_INDEX, TestPackageMenu.DONE_WIRE_INDEX,
            TestPackageMenu.TOTAL_WIRE_INDEX, STATE_KEY);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 22-23. From outside the block
    // ------------------------------------------------------------------

    private static void exposesEnergyOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        BlockPos pos = helper.absolutePos(MACHINE);

        for (Direction side : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, side) == null,
                "an item handler capability exists on " + side);
            helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, pos, side) == null,
                "a fluid handler capability exists on " + side);
        }
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, null) == null,
            "an item handler capability exists on a null side");
        helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, pos, null) == null,
            "a fluid handler capability exists on a null side");

        EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.WEST);
        helper.assertTrue(energy != null, "the machine exposes no energy capability at all");
        if (energy == null) {
            return;
        }
        helper.assertTrue(energy.getCapacityAsLong() > 0,
            "the energy capability has no capacity, so the definition did not load");
        int accepted = insert(energy, 500);
        helper.assertTrue(accepted > 0, "the energy capability accepted nothing");
        long stored = energy.getAmountAsLong();
        int pulled;
        try (Transaction transaction = Transaction.openRoot()) {
            pulled = energy.extract((int) stored, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(0, pulled,
            "the energy capability let FE be pulled back out");
        helper.assertValueEqual(stored, energy.getAmountAsLong(),
            "the stored amount changed while extraction was refused");

        // The container half of the same contract: no hopper may push in or pull out, and the
        // Container interface says so itself on every slot.
        loadLot(machine, ModItems.FOUP_MK1.get(),
            FoupContents.empty(UUID.randomUUID(), 3).withWafer(Wafer.unprocessed()));
        machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
            new ItemStack(ModItems.CHIP_PACKAGE.get(), 7));
        machine.setSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST,
            new ItemStack(ModItems.LOGIC_CHIP_T1.get(), 11));
        int foupCount = machine.slot(TestPackageBlockEntity.FOUP_SLOT).getCount();
        int packageCount = machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount();
        int chipCount = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).getCount();

        for (int slot = 0; slot < machine.getContainerSize(); slot++) {
            helper.assertFalse(machine.canPlaceItem(slot, new ItemStack(Items.IRON_INGOT)),
                "slot " + slot + " reports that a hopper may push an item into it");
            helper.assertFalse(machine.canTakeItem(machine, slot, new ItemStack(Items.IRON_INGOT)),
                "slot " + slot + " reports that a hopper may take an item out of it");
        }
        for (Direction side : Direction.values()) {
            ItemStack remainder = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                null, machine, new ItemStack(Items.IRON_INGOT), side);
            helper.assertValueEqual(1, remainder.getCount(),
                "a hopper pushed into the machine on " + side);
        }
        helper.assertValueEqual(foupCount,
            machine.slot(TestPackageBlockEntity.FOUP_SLOT).getCount(),
            "a hopper changed the FOUP slot");
        helper.assertValueEqual(packageCount,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a hopper changed the package slot");
        helper.assertValueEqual(chipCount,
            machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST).getCount(),
            "a hopper changed the first chip slot");

        BlockPos hopperPos = helper.absolutePos(MACHINE.below());
        level.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)
            level.getBlockEntity(hopperPos);
        helper.assertFalse(
            net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(level, hopper),
            "a hopper extracted from the machine");
        helper.assertTrue(hopper.isEmpty(), "a hopper received an item from the machine");

        // A chest does expose an item handler, so the nulls above are a real answer rather than
        // the capability system being unavailable.
        helper.setBlock(SECOND, Blocks.CHEST);
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(SECOND), Direction.NORTH) != null,
            "a chest has no item handler either, so the null assertions are vacuous");

        LOGGER.info("[M1-10] {}: no item or fluid capability on any side or on null, energy "
                + "accepted {} FE and never extracted, and no hopper reaches any slot",
            ENERGY_ONLY, accepted);
        helper.succeed();
    }

    /**
     * The two paths a player actually takes: opening the block opens this machine's menu, and
     * the registered server ticker drives this machine's own tick.
     *
     * <p>The client half of the ticker rule cannot be asked for without a client world, which a
     * dedicated server test must not build: what is asserted instead is the guard the tick
     * carries, on a level-less block entity. Actual client-world rendering and the client ticker
     * branch stay a manual gate.</p>
     */
    private static void blockUseAndServerTicker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPackageBlockEntity machine = placedMachine(helper, MACHINE);
        if (machine == null) {
            return;
        }
        MachineDefinition definition = definitionOf(helper, machine);
        Tier tier = tier(helper, 1);
        if (definition == null || tier == null) {
            return;
        }
        Numbers numbers = numbers(helper, definition, tier);
        if (numbers == null) {
            return;
        }

        // The registered ticker, obtained the way the level obtains it.
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = PackagingMachines.TEST_PACKAGE.get().defaultBlockState();
        helper.assertTrue(state.hasBlockEntity(),
            "the test_package block reports no block entity, so it has nothing to tick");
        BlockEntityTicker<TestPackageBlockEntity> ticker =
            state.getTicker(level, PackagingMachines.TEST_PACKAGE_BE.get());
        helper.assertTrue(ticker != null, "the test_package block has no ticker on a server level");

        Lot lot = lotWithFirstOutcome(level, tier, DieTypes.LOGIC, null, numbers, true);
        if (lot == null) {
            helper.fail("no " + DieTypes.LOGIC + " lot at T1 whose first wafer passes could be "
                + "built for this case");
            return;
        }
        int packages = lot.passing() * numbers.packagesPerWafer();
        loadPackages(machine, packages);
        loadLot(machine, ModItems.FOUP_MK1.get(), lot.contents());
        charge(helper, machine, lot.size() * numbers.ticksPerWafer() * numbers.energyPerTick());

        // The first call is the admission; the one after it is a single tick of work.
        ticker.tick(level, pos, state, machine);
        helper.assertTrue(machine.isQueueActive(),
            "the registered ticker did not accept a complete lot on its first call");
        long before = machine.buffer().getAmountAsLong();
        ticker.tick(level, pos, state, machine);
        helper.assertValueEqual(1, progressOf(machine),
            "one tick through the registered ticker did not advance the run by exactly one tick");
        helper.assertValueEqual(before - numbers.energyPerTick(),
            machine.buffer().getAmountAsLong(),
            "one tick through the registered ticker did not cost exactly one per-tick charge");

        // A ticker handed a block entity of the wrong type must be a safe no-op rather than a
        // crash or a stray tick of somebody else's contents.
        BlockPos otherPos = helper.absolutePos(THIRD);
        helper.setBlock(THIRD, Blocks.CHEST);
        BlockEntity other = level.getBlockEntity(otherPos);
        helper.assertTrue(other != null, "the control chest has no block entity");
        helper.assertFalse(other instanceof TestPackageBlockEntity,
            "the control block is the machine itself, so this proves nothing");
        if (other != null) {
            @SuppressWarnings("unchecked")
            BlockEntityTicker<BlockEntity> raw =
                (BlockEntityTicker<BlockEntity>) (BlockEntityTicker<?>) ticker;
            raw.tick(level, otherPos, Blocks.CHEST.defaultBlockState(), other);
            helper.assertTrue(other instanceof net.minecraft.world.Container
                    && ((net.minecraft.world.Container) other).isEmpty(),
                "the machine's ticker put something into a block entity of another type");
            helper.assertValueEqual(1, progressOf(machine),
                "a tick for another block entity changed this machine's progress");
        }

        // The client half, as far as it can be asked without a client level: the very same block
        // entity, loaded and charged, with no level at all, treats a tick as a no-op.
        TestPackageBlockEntity detached =
            new TestPackageBlockEntity(new BlockPos(0, 0, 0), state);
        detached.clearContent();
        detached.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
            new ItemStack(ModItems.CHIP_PACKAGE.get(), packages));
        loadLot(detached, ModItems.FOUP_MK1.get(), lot.contents());
        detached.setStoredEnergy(detached.buffer().getCapacityAsInt());
        helper.assertTrue(detached.buffer().getAmountAsLong() > 0,
            "the detached machine could not be charged, so the guard assertions below would be "
                + "vacuous");
        for (int tick = 0; tick < lot.size() * numbers.ticksPerWafer() + 10; tick++) {
            detached.serverTick();
        }
        helper.assertFalse(detached.isQueueActive(),
            "a machine with no level started a queue");
        helper.assertValueEqual(0, detached.wafersTotal(),
            "a machine with no level reported a wafer total");
        helper.assertValueEqual(0, progressOf(detached),
            "a machine with no level made progress");
        helper.assertValueEqual(0, totalOutput(detached),
            "a machine with no level produced output");
        helper.assertValueEqual(packages,
            detached.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "a machine with no level consumed packages");
        helper.assertValueEqual((long) detached.buffer().getCapacityAsInt(),
            detached.buffer().getAmountAsLong(),
            "a machine with no level charged the player");

        // The block use a player's click drives.
        ServerPlayer player = connect(level);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult emptyHand = player.gameMode.useItemOn(player, level,
            player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(emptyHand.consumesAction(),
            "using the machine with an empty hand returned " + emptyHand);
        helper.assertTrue(player.containerMenu instanceof TestPackageMenu,
            "using the block with an empty hand left the player on "
                + player.containerMenu.getClass().getSimpleName());
        TestPackageMenu opened = (TestPackageMenu) player.containerMenu;
        helper.assertTrue(opened.slots.get(TestPackageBlockEntity.FOUP_SLOT).container == machine,
            "the menu opened by the block interaction is not bound to the machine");
        helper.assertTrue(opened.stillValid(player),
            "the menu the block opened does not consider the player close enough to use it");
        player.closeContainer();
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
            "closing the menu did not return the player to their inventory menu");

        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(ModItems.LOGIC_CHIP_T1.get()));
        InteractionResult heldItem = player.gameMode.useItemOn(player, level,
            player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(heldItem.consumesAction(),
            "using the machine with a held item returned " + heldItem);
        helper.assertTrue(player.containerMenu instanceof TestPackageMenu,
            "using the block with a held item left the player on "
                + player.containerMenu.getClass().getSimpleName());

        LOGGER.info("[M1-10] {}: empty hand -> {}, held item -> {}; the registered server ticker "
                + "advanced the run one tick for one charge, refused another block entity type, "
                + "and a level-less machine stayed inert", BLOCK_USE_TICKER, emptyHand, heldItem);
        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * The machine's own numbers, resolved exactly the way the block entity resolves them.
     *
     * <p>Nothing here is invented: if the definition is missing or declares something the machine
     * cannot use, the case fails with the values it found rather than carrying on.</p>
     */
    private record Numbers(int ticksPerWafer, int packagesPerWafer, int chipsPerWafer,
                           int energyPerTick, int capacity) {
    }

    /** One judged fixture lot, with its own flag array and the passing count counted here. */
    private record Lot(FoupContents contents, boolean[] flags, int passing) {
        int failing() {
            return this.flags.length - this.passing;
        }

        int size() {
            return this.flags.length;
        }
    }

    /** The machine's published progress, read through the base class's own index. */
    private static int progressOf(TestPackageBlockEntity machine) {
        return machine.data().get(FabMachineBlockEntity.DATA_PROGRESS);
    }

    private static @Nullable TestPackageBlockEntity placedMachine(GameTestHelper helper,
            BlockPos relative) {
        helper.setBlock(relative, PackagingMachines.TEST_PACKAGE.get());
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative))
            instanceof TestPackageBlockEntity machine ? machine : null;
    }

    private static @Nullable MachineDefinition definitionOf(GameTestHelper helper,
            TestPackageBlockEntity machine) {
        MachineDefinition definition = machine.definition().orElse(null);
        helper.assertTrue(definition != null,
            "no " + TestPackageBlockEntity.MACHINE_ID
                + " definition loaded from data/lithmod/machines");
        return definition;
    }

    private static @Nullable Tier tier(GameTestHelper helper, int index) {
        TierRegistry registry = TierDataLoader.getRegistry();
        if (registry == null) {
            helper.fail("the tier data has not been loaded; these tests need data/lithmod/tiers");
            return null;
        }
        Tier tier = registry.byIndex(index).orElse(null);
        helper.assertTrue(tier != null, "the loaded tier data has no T" + index);
        return tier;
    }

    private static @Nullable Numbers numbers(GameTestHelper helper, MachineDefinition definition,
            Tier tier) {
        int ticks = definition.paramOrDefault(TestPackageBlockEntity.TICKS_PER_WAFER_PARAM, 0);
        int packages = definition.paramOrDefault(TestPackageBlockEntity.PACKAGE_PER_WAFER_PARAM, 0);
        int chips = EUVCRAFTMod.settings().chipsPerWafer();
        int perTick = definition.energyPerTick(tier, EUVCRAFTMod.settings());
        helper.assertTrue(ticks >= 1, "the definition's ticks_per_wafer is " + ticks);
        helper.assertTrue(packages >= 1 && packages <= VANILLA_STACK,
            "the definition's package_per_wafer is " + packages
                + ", which a single slot cannot hold");
        helper.assertTrue(chips >= 1
                && chips <= TestPackageBlockEntity.CHIP_SLOT_COUNT * VANILLA_STACK
                && chips <= TestPackageBlockEntity.SCRAP_SLOT_COUNT * VANILLA_STACK,
            "the configured chipsPerWafer is " + chips);
        helper.assertTrue(perTick >= 1,
            "the definition charges " + perTick + " FE per tick for this tier, so the exact-FE "
                + "assertions in these tests would be vacuous");
        helper.assertTrue(definition.energyCapacity() >= perTick,
            "the definition's energy_capacity " + definition.energyCapacity()
                + " cannot pay one tick of " + perTick + " FE");
        return new Numbers(ticks, packages, chips, perTick, definition.energyCapacity());
    }

    /** Builds a lot by putting unprocessed wafers through every layer of its tier. */
    private static FoupContents processLot(ServerLevel level, Tier tier, String dieType,
            @Nullable Integer cores, int capacity, int wafers) {
        FoupContents lot = FoupContents.empty(UUID.randomUUID(), capacity);
        for (int index = 0; index < wafers; index++) {
            lot = lot.withWafer(Wafer.unprocessed());
        }
        for (int layer = 1; layer <= tier.layers(); layer++) {
            lot = FabYieldService.applyLayerStep(level, lot, tier, dieType, cores, layer, 1.0);
        }
        return lot;
    }

    /**
     * A lot with at least one passing and one failing wafer that also fits the machine's real
     * limits: both banks, the package slot and the buffer.
     */
    private static @Nullable Lot mixedLot(ServerLevel level, Tier tier,
            String dieType, @Nullable Integer cores, Numbers numbers) {
        for (int wafers : LOT_SIZES) {
            if (wafers > EUVCRAFTMod.settings().foupCapacity()) {
                continue;
            }
            for (int attempt = 0; attempt < LOT_ATTEMPTS; attempt++) {
                Lot candidate = buildCandidate(level, tier, dieType, cores, numbers, wafers);
                if (candidate != null && candidate.passing() > 0 && candidate.failing() > 0) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** A mixed lot whose first {@code prefix} wafers have the given outcome. */
    private static @Nullable Lot lotWithPassingPrefix(ServerLevel level, Tier tier, String dieType,
            @Nullable Integer cores, Numbers numbers, int prefix, boolean prefixPasses) {
        for (int wafers : LOT_SIZES) {
            if (wafers < 2 || wafers > EUVCRAFTMod.settings().foupCapacity()) {
                continue;
            }
            for (int attempt = 0; attempt < LOT_ATTEMPTS; attempt++) {
                Lot candidate = buildCandidate(level, tier, dieType, cores, numbers, wafers);
                if (candidate == null || candidate.flags().length <= prefix) {
                    continue;
                }
                boolean prefixMatches = true;
                for (int wafer = 0; wafer < prefix; wafer++) {
                    if (candidate.flags()[wafer] != prefixPasses) {
                        prefixMatches = false;
                        break;
                    }
                }
                if (!prefixMatches) {
                    continue;
                }
                if (prefixPasses || candidate.passing() > 0) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** A mixed lot whose first wafer has the given outcome, so a specific bank is first. */
    private static @Nullable Lot lotWithFirstOutcome(ServerLevel level, Tier tier,
            String dieType, @Nullable Integer cores, Numbers numbers, boolean firstPasses) {
        return lotWithPassingPrefix(level, tier, dieType, cores, numbers, 1, firstPasses);
    }

    /** One lot, judged and counted here, or {@code null} when it does not fit the machine. */
    private static @Nullable Lot buildCandidate(ServerLevel level, Tier tier, String dieType,
            @Nullable Integer cores, Numbers numbers, int wafers) {
        if ((long) wafers * numbers.ticksPerWafer() * numbers.energyPerTick() > numbers.capacity()) {
            return null;
        }
        FoupContents lot;
        try {
            lot = processLot(level, tier, dieType, cores,
                Math.max(wafers, EUVCRAFTMod.settings().foupCapacity()), wafers);
        } catch (RuntimeException refusedByTheCore) {
            return null;
        }
        boolean[] flags = FabYieldService.evaluateLot(lot, tier, dieType, cores);
        int passing = countTrue(flags);
        int failing = flags.length - passing;
        if ((long) passing * numbers.packagesPerWafer() > VANILLA_STACK) {
            return null;
        }
        if ((long) passing * numbers.chipsPerWafer()
            > TestPackageBlockEntity.CHIP_SLOT_COUNT * (long) VANILLA_STACK) {
            return null;
        }
        if ((long) failing * numbers.chipsPerWafer()
            > TestPackageBlockEntity.SCRAP_SLOT_COUNT * (long) VANILLA_STACK) {
            return null;
        }
        return new Lot(lot, flags, passing);
    }

    private static String noMixedLotMessage(String dieType, int tierIndex, Numbers numbers) {
        return "no " + dieType + " lot at T" + tierIndex + " could be built with at least one "
            + "passing and one failing wafer that also fits the machine (ticks_per_wafer "
            + numbers.ticksPerWafer() + ", package_per_wafer " + numbers.packagesPerWafer()
            + ", chipsPerWafer " + numbers.chipsPerWafer() + ", energy_per_tick "
            + numbers.energyPerTick() + ", energy_capacity " + numbers.capacity() + ")";
    }

    private static int countTrue(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) {
            if (flag) {
                count++;
            }
        }
        return count;
    }

    private static String flagsText(boolean[] flags) {
        StringBuilder text = new StringBuilder(flags.length);
        for (boolean flag : flags) {
            text.append(flag ? '1' : '0');
        }
        return text.toString();
    }

    private static boolean[] reverse(boolean[] flags) {
        boolean[] flipped = new boolean[flags.length];
        for (int index = 0; index < flags.length; index++) {
            flipped[index] = flags[flags.length - 1 - index];
        }
        return flipped;
    }

    private static void loadLot(TestPackageBlockEntity machine, Item item, FoupContents lot) {
        machine.setSlot(TestPackageBlockEntity.FOUP_SLOT, FoupItems.with(item, lot));
    }

    private static void loadPackages(TestPackageBlockEntity machine, int count) {
        machine.setSlot(TestPackageBlockEntity.PACKAGE_SLOT,
            count <= 0 ? ItemStack.EMPTY : new ItemStack(ModItems.CHIP_PACKAGE.get(), count));
    }

    private static void fillBank(TestPackageBlockEntity machine, int first, int count, Item item) {
        for (int offset = 0; offset < count; offset++) {
            machine.setSlot(first + offset, new ItemStack(item, VANILLA_STACK));
        }
    }

    private static void clearBank(TestPackageBlockEntity machine, int first, int count) {
        for (int offset = 0; offset < count; offset++) {
            machine.clearSlot(first + offset);
        }
    }

    private static int bankCount(TestPackageBlockEntity machine, int first, int count) {
        int total = 0;
        for (int offset = 0; offset < count; offset++) {
            total += machine.slot(first + offset).getCount();
        }
        return total;
    }

    private static int bankCountOf(TestPackageBlockEntity machine, int first, int count, Item item) {
        int total = 0;
        for (int offset = 0; offset < count; offset++) {
            ItemStack stack = machine.slot(first + offset);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The machine's own bank preflight, re-derived from its public slots for the fixtures. */
    private static int bankRoomFor(TestPackageBlockEntity machine, int first, int count,
            Item item) {
        int room = 0;
        ItemStack product = new ItemStack(item);
        for (int offset = 0; offset < count; offset++) {
            ItemStack stack = machine.slot(first + offset);
            if (stack.isEmpty()) {
                room += product.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, product)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    private static int totalOutput(TestPackageBlockEntity machine) {
        return bankCount(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT)
            + bankCount(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
            TestPackageBlockEntity.SCRAP_SLOT_COUNT);
    }

    /**
     * Charges the buffer to exactly {@code target} FE, in whole legal steps through the real
     * capability, so the amount lands on the target instead of overshooting it.
     */
    private static void charge(GameTestHelper helper, TestPackageBlockEntity machine, int target) {
        MachineDefinition definition = machine.definition().orElse(null);
        helper.assertTrue(definition != null,
            "the machine has no definition loaded from data/lithmod/machines");
        if (definition == null) {
            return;
        }
        helper.assertTrue(definition.maxReceive() > 0,
            "the definition declares max_receive " + definition.maxReceive());
        helper.assertTrue(target <= definition.energyCapacity(),
            "this fixture asks for " + target + " FE but energy_capacity is only "
                + definition.energyCapacity());
        for (int attempt = 0; attempt < 8192; attempt++) {
            long stored = machine.buffer().getAmountAsLong();
            if (stored >= target) {
                break;
            }
            int wanted = (int) Math.min(definition.maxReceive(), target - stored);
            if (insert(machine.energy(), wanted) <= 0) {
                break;
            }
        }
        helper.assertValueEqual((long) target, machine.buffer().getAmountAsLong(),
            "the machine could not be charged to " + target + " FE");
    }

    private static void runTicks(TestPackageBlockEntity machine, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            machine.serverTick();
        }
    }

    /** Ticks until the queue is finished, and fails when the bound is reached first. */
    private static void runUntilFinished(GameTestHelper helper, TestPackageBlockEntity machine,
            int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (!machine.isQueueActive()) {
                return;
            }
            machine.serverTick();
        }
        helper.assertFalse(machine.isQueueActive(),
            "the queue did not finish within " + maxTicks + " ticks: "
                + machine.wafersDone() + " of " + machine.wafersTotal()
                + " wafers, progress " + progressOf(machine) + " of "
                + machine.data().get(FabMachineBlockEntity.DATA_TICKS));
    }

    private static void assertCatalogChips(GameTestHelper helper, TestPackageBlockEntity machine,
            Item expected, int expectedCount, String what) {
        int total = bankCountOf(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
            TestPackageBlockEntity.CHIP_SLOT_COUNT, expected);
        helper.assertValueEqual(expectedCount, total,
            "the " + what + " chip bank holds " + total + " catalog chips rather than "
                + expectedCount);
        helper.assertValueEqual(expectedCount,
            bankCount(machine, TestPackageBlockEntity.CHIP_SLOT_FIRST,
                TestPackageBlockEntity.CHIP_SLOT_COUNT),
            "the " + what + " chip bank holds items that are not the catalog chip");
        for (int offset = 0; offset < TestPackageBlockEntity.CHIP_SLOT_COUNT; offset++) {
            ItemStack stack = machine.slot(TestPackageBlockEntity.CHIP_SLOT_FIRST + offset);
            if (stack.isEmpty()) {
                continue;
            }
            helper.assertTrue(stack.getItem() == expected,
                "the " + what + " chip bank holds " + stack.getItem()
                    + " rather than the catalog chip");
            helper.assertTrue(stack.get(ModDataComponents.RESIST_TIER.get()) == null,
                "a chip in the " + what + " bank carries an extra component, so it is not the "
                    + "plain catalog chip");
        }
    }

    private static void assertScrapOnly(GameTestHelper helper, TestPackageBlockEntity machine,
            Item scrap, int expectedCount) {
        helper.assertValueEqual(expectedCount,
            bankCountOf(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT, scrap),
            "the scrap bank does not hold the expected amount of chip_scrap");
        helper.assertValueEqual(expectedCount,
            bankCount(machine, TestPackageBlockEntity.SCRAP_SLOT_FIRST,
                TestPackageBlockEntity.SCRAP_SLOT_COUNT),
            "the scrap bank holds items that are not chip_scrap");
    }

    /** The conservation arithmetic, all of it derived from the fixture and the definition. */
    private static void assertConservation(GameTestHelper helper, TestPackageBlockEntity machine,
            Numbers numbers, Lot lot, int expectedChips, int expectedScrap, int expectedPackages) {
        helper.assertValueEqual(lot.passing() * numbers.chipsPerWafer(), expectedChips,
            "the expected chip count does not follow the fixture's passing wafers");
        helper.assertValueEqual(lot.failing() * numbers.chipsPerWafer(), expectedScrap,
            "the expected scrap count does not follow the fixture's failing wafers");
        helper.assertValueEqual(lot.passing() * numbers.packagesPerWafer(), expectedPackages,
            "the expected package count does not follow the fixture's passing wafers");
        helper.assertValueEqual(expectedChips + expectedScrap,
            numbers.chipsPerWafer() * lot.size(),
            "chips plus scrap do not add up to chipsPerWafer * wafers");
        helper.assertTrue(expectedChips % numbers.chipsPerWafer() == 0
                && expectedScrap % numbers.chipsPerWafer() == 0,
            "the expected counts are not whole numbers of wafers");

        helper.assertValueEqual(lot.size(), machine.wafersDone(),
            "the machine did not report every wafer of the lot as finished");
        helper.assertValueEqual(lot.size(), machine.wafersTotal(),
            "the machine did not report the lot's wafer total");
        helper.assertValueEqual(0,
            machine.slot(TestPackageBlockEntity.PACKAGE_SLOT).getCount(),
            "packages were left in the slot after a run that consumed every passing wafer's "
                + "worth");
    }

    // ------------------------------------------------------------------
    // NBT helpers
    // ------------------------------------------------------------------

    private static CompoundTag saveTag(ServerLevel level, BlockEntity blockEntity) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveWithId(output);
        return output.buildResult();
    }

    private static void loadTag(ServerLevel level, BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(TagValueInput.create(
            ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    /** Clears the slots and the buffer, leaving the machine with no state of its own. */
    private static void wipe(TestPackageBlockEntity machine) {
        machine.clearContent();
        machine.setStoredEnergy(0);
    }

    /**
     * A save whose private {@code packaging} node is written by this file.
     *
     * <p>The root is a real save, so the inventory, the energy and everything else in it are the
     * machine's own; only the private node is replaced. That is what makes the malformed cases
     * load an ordinary machine that happens to carry a hand-written queue.</p>
     */
    private static CompoundTag queueTag(ServerLevel level, TestPackageBlockEntity machine,
            String flags, int chipsPerWafer, int packagesPerWafer, int ticksPerWafer,
            int energyPerTick, int index, int paidTicks, String dieType, int tierIndex) {
        CompoundTag root = saveTag(level, machine);
        CompoundTag queue = new CompoundTag();
        queue.putString(FLAGS_KEY, flags);
        queue.putInt(CHIPS_KEY, chipsPerWafer);
        queue.putInt(PACKAGES_KEY, packagesPerWafer);
        queue.putInt(TICKS_KEY, ticksPerWafer);
        queue.putInt(ENERGY_KEY, energyPerTick);
        queue.putInt(INDEX_KEY, index);
        queue.putInt(PAID_KEY, paidTicks);
        queue.putString(DIE_KEY, dieType);
        queue.putInt(TIER_KEY, tierIndex);
        root.put(STATE_KEY, queue);
        return root;
    }

    // ------------------------------------------------------------------
    // world helpers
    // ------------------------------------------------------------------

    private static Map<Item, Integer> droppedItems(ServerLevel level, BlockPos centre) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemEntity entity : level.getEntities(net.minecraft.world.entity.EntityTypes.ITEM,
                new AABB(centre).inflate(DROP_RADIUS), Entity::isAlive)) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static int countOf(Map<Item, Integer> counts, Item item) {
        return counts.getOrDefault(item, 0);
    }

    private static int sumOf(Map<Item, Integer> counts) {
        int total = 0;
        for (int value : counts.values()) {
            total += value;
        }
        return total;
    }

    private static @Nullable ItemEntity findItemEntity(ServerLevel level, BlockPos centre,
            Item item) {
        for (ItemEntity entity : level.getEntities(net.minecraft.world.entity.EntityTypes.ITEM,
                new AABB(centre).inflate(DROP_RADIUS), Entity::isAlive)) {
            if (entity.getItem().getItem() == item) {
                return entity;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // fixture helpers that borrow global loader state
    // ------------------------------------------------------------------

    /**
     * Runs {@code body} with the loaded machine definitions passed through {@code swap}.
     *
     * <p>{@link MachineDefinitionLoader} keeps the loaded registry on the instance the reload
     * listener created and offers no setter, so this writes that field and puts the exact previous
     * registry back in a {@code finally}. A {@code GameTest} cannot drive a data-pack reload, and
     * {@code applyDefinition} alone is not one: the machine re-reads the registry on every tick,
     * so a change made only on the block entity would be undone on the next tick and would prove
     * nothing.</p>
     */
    private static void swapDefinitions(UnaryOperator<List<MachineDefinition>> swap,
            Runnable body) {
        try {
            Field live = MachineDefinitionLoader.class.getDeclaredField("CURRENT");
            live.setAccessible(true);
            Field loaded = JsonResourceDataLoader.class.getDeclaredField("current");
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
                loaded.set(loader, MachineDefinitionLoader.build(next, "M1-10 game test"));
                body.run();
            } finally {
                loaded.set(loader, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the machine definition registry could not be swapped", failure);
        }
    }

    private static List<MachineDefinition> replace(List<MachineDefinition> definitions,
            MachineDefinition replacement) {
        List<MachineDefinition> swapped = new ArrayList<>(definitions.size());
        for (MachineDefinition definition : definitions) {
            swapped.add(definition.id().equals(replacement.id()) ? replacement : definition);
        }
        return swapped;
    }

    private static List<MachineDefinition> without(List<MachineDefinition> definitions,
            String machineId) {
        return definitions.stream()
            .filter(definition -> !definition.id().equals(machineId))
            .toList();
    }

    /**
     * A copy of the loaded definition with the wafer length, the package amount and the per-tick
     * cost replaced, keeping every other param and all of its energy numbers.
     */
    private static MachineDefinition redefined(MachineDefinition original, int ticksPerWafer,
            int packagesPerWafer, int energyPerTick) {
        Map<String, Integer> params = new LinkedHashMap<>(original.params());
        params.put(TestPackageBlockEntity.TICKS_PER_WAFER_PARAM, ticksPerWafer);
        params.put(TestPackageBlockEntity.PACKAGE_PER_WAFER_PARAM, packagesPerWafer);
        return new MachineDefinition(original.id(), original.energyCapacity(),
            original.maxReceive(), energyPerTick, original.perTierEnergyPerTick(), params);
    }

    /**
     * Runs {@code body} with the support-line matrix replaced, putting the exact previous matrix
     * back in a {@code finally}.
     */
    private static void withMatrix(GenerationMatrix replacement, Runnable body) {
        try {
            Field field = SupportLineDataLoader.class.getDeclaredField("currentMatrix");
            field.setAccessible(true);
            GenerationMatrix previous = (GenerationMatrix) field.get(null);
            try {
                field.set(null, replacement);
                body.run();
            } finally {
                field.set(null, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the support-line generation matrix could not be swapped",
                failure);
        }
    }

    /**
     * A valid matrix that raises the {@code test_packaging} requirement.
     *
     * <p>Every tier from {@code tierIndex} up is raised to at least {@code generation}, because
     * the matrix must stay non-decreasing along the tiers or it is not a matrix at all. The result
     * is therefore a real matrix a data pack could ship, with only the packaging line's
     * requirement moved.</p>
     */
    private static GenerationMatrix withPackagingGeneration(TierRegistry tiers,
            GenerationMatrix loaded, int tierIndex, int generation) {
        Map<String, List<Integer>> byLine = new LinkedHashMap<>();
        for (String line : GenerationMatrix.LINE_IDS) {
            List<Integer> generations = new ArrayList<>();
            for (Tier tier : tiers.all()) {
                int current = loaded.requiredGeneration(line, tier.index());
                generations.add(tier.index() >= tierIndex
                    ? Math.max(generation, current) : current);
            }
            byLine.put(line, generations);
        }
        return GenerationMatrix.build(tiers, byLine, "M1-10 game test support lines");
    }

    private static void withChipsPerWafer(int value, Runnable body) {
        FabConfig config = EUVCRAFTMod.config();
        if (config == null) {
            throw new AssertionError("the EUVCRAFT config binding does not exist yet");
        }
        int previous = FabConfig.CHIPS_PER_WAFER.get();
        try {
            FabConfig.CHIPS_PER_WAFER.set(value);
            config.onConfigReload();
            body.run();
        } finally {
            FabConfig.CHIPS_PER_WAFER.set(previous);
            config.onConfigReload();
        }
    }

    private static void withFoupCapacity(int value, Runnable body) {
        FabConfig config = EUVCRAFTMod.config();
        if (config == null) {
            throw new AssertionError("the EUVCRAFT config binding does not exist yet");
        }
        int previous = FabConfig.FOUP_CAPACITY.get();
        try {
            FabConfig.FOUP_CAPACITY.set(value);
            config.onConfigReload();
            body.run();
        } finally {
            FabConfig.FOUP_CAPACITY.set(previous);
            config.onConfigReload();
        }
    }

    private static int insert(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** How many synced values a menu registered, read off the field the sync walks. */
    private static int dataSlotCount(AbstractContainerMenu menu) {
        try {
            Field slots = AbstractContainerMenu.class.getDeclaredField("dataSlots");
            slots.setAccessible(true);
            return ((List<?>) slots.get(menu)).size();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("the menu's synced value list could not be read", failure);
        }
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "packaging-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
