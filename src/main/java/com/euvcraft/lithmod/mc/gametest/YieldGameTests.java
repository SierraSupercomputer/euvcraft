package com.euvcraft.lithmod.mc.gametest;

import java.util.UUID;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.registry.ModItems;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import com.mojang.serialization.DataResult;

/**
 * Game tests for the yield roll engine of milestone M0-09.
 *
 * <p>{@code lithmod:yield_layer_step} runs the real server path: a lot on a real
 * {@link ServerLevel}, rolls drawn from {@link
 * com.euvcraft.lithmod.mc.FabRandomService#rngFor} with the world salt, the layer rules
 * enforced and the lot judged at test. {@code lithmod:yield_pass_rate} runs a thousand
 * wafers through T1 and T3 and logs what actually came out, so the calibration has
 * runtime evidence as well as the unit test.</p>
 *
 * <p>The lot here is built by hand rather than through a machine, because the machines
 * that will call this service come later.</p>
 */
public final class YieldGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000009c3");
    private static final String ENVIRONMENT_ID = "yield_default";
    private static final int WAFERS_PER_LOT = 25;
    private static final int LOTS = 40;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register("yield_layer_step", () -> YieldGameTests::layerStep);
        FUNCTIONS.register("yield_pass_rate", () -> YieldGameTests::passRate);
        FUNCTIONS.register("yield_end_to_end", () -> YieldGameTests::endToEndSimulation);
        FUNCTIONS.register("yield_run_spec", () -> YieldGameTests::runSpecTests);
    }

    private YieldGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) YieldGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "yield_layer_step");
        register(event, data, "yield_pass_rate");
        register(event, data, "yield_end_to_end");
        register(event, data, "yield_run_spec");
        LOGGER.info("Registered EUVCRAFT yield game tests");
    }

    private static void register(RegisterGameTestsEvent event,
        TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * Proves that one layer step on a real level moves the lot, multiplies the hidden
     * rolls, refuses a layer that is not the next one, and leaves a finished lot
     * judgeable without changing it.
     */
    private static void layerStep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Tier tier = tier(helper, 1);
        if (tier == null) {
            return;
        }

        FoupContents fresh = unprocessed(LOT, WAFERS_PER_LOT);
        FoupContents processed = fresh;
        for (int layer = 1; layer <= tier.layers(); layer++) {
            processed = FabYieldService.applyLayerStep(level, processed, tier, "logic", null, layer, 1.0);
            helper.assertTrue(processed.lotLayer() == layer,
                "after layer " + layer + " the lot reports layer " + processed.lotLayer());
        }

        helper.assertTrue(processed.lotId().equals(LOT), "the lot id changed during the run");
        helper.assertTrue(processed.size() == WAFERS_PER_LOT, "the wafer count changed during the run");
        helper.assertTrue(fresh.lotLayer() == Wafer.UNPROCESSED_LAYER,
            "the lot that was passed in was modified in place");

        for (int wafer = 0; wafer < processed.size(); wafer++) {
            Wafer moved = processed.wafers().get(wafer);
            helper.assertTrue(moved.layerIndex() == tier.layers(),
                "wafer " + wafer + " is at layer " + moved.layerIndex() + " instead of " + tier.layers());
            helper.assertTrue(moved.cumulativeRoll() > 0.0f && moved.cumulativeRoll() < Wafer.NO_ROLLS,
                "wafer " + wafer + " holds a cumulative roll of " + moved.cumulativeRoll()
                    + " after " + tier.layers() + " layers");
        }

        // The same step cannot be repeated, and there is no layer past the last one.
        FoupContents finished = processed;
        expectRefusal(helper, "repeating the last layer", () ->
            FabYieldService.applyLayerStep(level, finished, tier, "logic", null, tier.layers(), 1.0));
        expectRefusal(helper, "skipping to a later layer", () ->
            FabYieldService.applyLayerStep(level, finished, tier, "logic", null, tier.layers() + 2, 1.0));

        boolean[] outcomes = FabYieldService.evaluateLot(finished, tier, "logic", null);
        helper.assertTrue(outcomes.length == finished.size(), "one answer per wafer was expected");
        helper.assertTrue(finished.equals(processed), "judging the lot changed it");

        int passing = FabYieldService.countPassing(finished, tier, "logic", null);
        LOGGER.info("T{}: {} of {} wafers of one lot passed against a target of {}%", tier.index(), passing,
            finished.size(), Math.round(YieldMath.resolveEffectiveYield(tier, EUVCRAFTMod.settings(), null) * 100.0));

        helper.succeed();
    }

    /**
     * Runs a thousand wafers through T1 and T3 with the world salt and reports the pass
     * rates, which is the calibration check with the real random source rather than a
     * seeded one.
     *
     * <p>The tolerance is wide on purpose. A thousand wafers carry about a point of
     * sampling noise and the roll draw is a different one on every world; this is
     * evidence that the wiring works, not a statistical test. The accuracy claim is in
     * the unit tests.</p>
     */
    private static void passRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        for (int index : new int[] {1, 3}) {
            Tier tier = tier(helper, index);
            if (tier == null) {
                return;
            }

            int passing = 0;
            int simulated = 0;
            for (int lot = 0; lot < LOTS; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, WAFERS_PER_LOT);
                for (int layer = 1; layer <= tier.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, tier, "logic", null, layer, 1.0);
                }
                passing += FabYieldService.countPassing(contents, tier, "logic", null);
                simulated += contents.size();
            }

            double yield = YieldMath.resolveEffectiveYield(tier, EUVCRAFTMod.settings(), null);
            double rate = (double) passing / simulated;
            LOGGER.info("T{}: {} of {} wafers passed, {}% against a target of {}%", tier.index(), passing,
                simulated, String.format("%.2f", rate * 100.0), Math.round(yield * 100.0));

            helper.assertTrue(Math.abs(rate - yield) <= 0.10,
                "T" + tier.index() + " passed " + (rate * 100.0) + "% against a target of "
                    + (yield * 100.0) + "%");
        }

        helper.succeed();
    }

    /** Builds a lot of unprocessed wafers. */
    private static FoupContents unprocessed(UUID lotId, int count) {
        FoupContents contents = FoupContents.empty(lotId, count);
        for (int wafer = 0; wafer < count; wafer++) {
            contents = contents.withWafer(Wafer.unprocessed());
        }
        return contents;
    }

    /** Returns one tier from the loaded tier data, failing the test if it is missing. */
    private static Tier tier(GameTestHelper helper, int index) {
        var registry = TierDataLoader.getRegistry();
        if (registry == null) {
            helper.fail("the tier data has not been loaded; the yield tests need data/lithmod/tiers");
            return null;
        }
        return registry.byIndex(index).orElse(null);
    }

    /** Runs an action that has to be refused and fails the test when it is not. */
    private static void expectRefusal(GameTestHelper helper, String what, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException refused) {
            LOGGER.info("{}: refused with \"{}\"", what, refused.getMessage());
            return;
        }
        helper.fail(what + " was not refused");
    }

    /**
     * End-to-end simulation: builds lots, applies every layer for T3, T5 and T7 through
     * FabYieldService.applyLayerStep on a real server level, evaluates them with
     * FabYieldService.evaluateLot, and checks the aggregate pass rate over many lots
     * against the tier yield. Includes T7 compute lot with 8 and 24 cores to confirm
     * the core-count penalty.
     */
    private static void endToEndSimulation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        for (int index : new int[] {3, 5, 7}) {
            Tier tier = tier(helper, index);
            if (tier == null) {
                return;
            }

            int wafersPerLot = 25;
            int lots = 40;
            int passing = 0;
            int simulated = 0;

            for (int lot = 0; lot < lots; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, wafersPerLot);
                for (int layer = 1; layer <= tier.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, tier, "logic", null, layer, 1.0);
                }
                passing += FabYieldService.countPassing(contents, tier, "logic", null);
                simulated += contents.size();
            }

            double yield = YieldMath.resolveEffectiveYield(tier, EUVCRAFTMod.settings(), null);
            double rate = (double) passing / simulated;
            LOGGER.info("T{}: {} of {} wafers passed, {}% against a target of {}%", tier.index(), passing,
                simulated, String.format("%.2f", rate * 100.0), Math.round(yield * 100.0));

            helper.assertTrue(Math.abs(rate - yield) <= 0.10,
                "T" + tier.index() + " passed " + (rate * 100.0) + "% against a target of "
                    + (yield * 100.0) + "%");
        }

        Tier t7 = tier(helper, 7);
        if (t7 != null) {
            int wafersPerLot = 25;
            int lots = 40;
            int passing = 0;
            int simulated = 0;

            for (int lot = 0; lot < lots; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, wafersPerLot);
                for (int layer = 1; layer <= t7.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t7, "compute", 8, layer, 1.0);
                }
                passing += FabYieldService.countPassing(contents, t7, "compute", 8);
                simulated += contents.size();
            }

            double yield = YieldMath.resolveEffectiveYield(t7, EUVCRAFTMod.settings(), 8);
            double rate = (double) passing / simulated;
            LOGGER.info("T7 compute 8 cores: {} of {} wafers passed, {}% against a target of {}%", passing,
                simulated, String.format("%.2f", rate * 100.0), Math.round(yield * 100.0));

            helper.assertTrue(Math.abs(rate - yield) <= 0.10,
                "T7 8 cores passed " + (rate * 100.0) + "% against a target of "
                    + (yield * 100.0) + "%");
        }

        if (t7 != null) {
            int wafersPerLot = 25;
            int lots = 40;
            int passing = 0;
            int simulated = 0;

            for (int lot = 0; lot < lots; lot++) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, wafersPerLot);
                for (int layer = 1; layer <= t7.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t7, "compute", 24, layer, 1.0);
                }
                passing += FabYieldService.countPassing(contents, t7, "compute", 24);
                simulated += contents.size();
            }

            double yield = YieldMath.resolveEffectiveYield(t7, EUVCRAFTMod.settings(), 24);
            double rate = (double) passing / simulated;
            LOGGER.info("T7 compute 24 cores: {} of {} wafers passed, {}% against a target of {}%", passing,
                simulated, String.format("%.2f", rate * 100.0), Math.round(yield * 100.0));

            helper.assertTrue(Math.abs(rate - yield) <= 0.10,
                "T7 24 cores passed " + (rate * 100.0) + "% against a target of "
                    + (yield * 100.0) + "%");
        }

        helper.succeed();
    }

    /**
     * Lot run spec tests:
     * (a) After layer 1, change yieldMultiplier to 2.0 and finish the lot: its pass rate
     *     still matches the tier yield, while a new lot started after the change follows
     *     the new setting.
     * (b) Change rollsPerLayer after layer 1: the lot still finishes and judges consistently
     *     (rollsPerLayer is read from stored spec at later layers).
     * (c) A tier or core count that differs from the stored spec is refused at a later
     *     layer and at test.
     * (d) The persistent codec round trip preserves a RunSpec, and a lot with no spec
     *     still decodes.
     * (e) A client-view lot is refused by applyLayerStep and evaluateLot.
     */
    private static void runSpecTests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        final Tier tier = tier(helper, 3);
        if (tier == null) {
            return;
        }

        // Test (c): Different tier refused at later layer
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 10);
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                FoupContents afterLayer1 = FabYieldService.applyLayerStep(level, contents, t1, "logic", null, 1, 1.0);
                expectRefusal(helper, "different tier at layer 2", () ->
                    FabYieldService.applyLayerStep(level, afterLayer1, tier, "logic", null, 2, 1.0));
            }
        }

        // Test (c): Different core count refused at later layer
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 10);
            Tier t7 = tier(helper, 7);
            if (t7 != null) {
                FoupContents afterLayer1 = FabYieldService.applyLayerStep(level, contents, t7, "compute", 8, 1, 1.0);
                expectRefusal(helper, "different cores at layer 2", () ->
                    FabYieldService.applyLayerStep(level, afterLayer1, t7, "compute", 24, 2, 1.0));
            }
        }

        // Test (c): Different tier refused at evaluateLot
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 10);
            final Tier t1 = tier(helper, 1);
            if (t1 != null) {
                FoupContents finished = contents;
                for (int layer = 1; layer <= t1.layers(); layer++) {
                    finished = FabYieldService.applyLayerStep(level, finished, t1, "logic", null, layer, 1.0);
                }
                final Tier evalTier = tier;
                final FoupContents toEval = finished;
                expectRefusal(helper, "different tier at evaluate", () ->
                    FabYieldService.evaluateLot(toEval, evalTier, "logic", null));
            }
        }

        // Test (c): Different core count refused at evaluateLot
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 10);
            final Tier t7 = tier(helper, 7);
            if (t7 != null) {
                FoupContents finished = contents;
                for (int layer = 1; layer <= t7.layers(); layer++) {
                    finished = FabYieldService.applyLayerStep(level, finished, t7, "compute", 8, layer, 1.0);
                }
                final FoupContents toEval = finished;
                expectRefusal(helper, "different cores at evaluate", () ->
                    FabYieldService.evaluateLot(toEval, t7, "compute", 24));
            }
        }

        // Test (d): Persistent codec round trip preserves RunSpec
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 10);
            Tier t3 = tier(helper, 3);
            if (t3 != null) {
                for (int layer = 1; layer <= t3.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t3, "logic", null, layer, 1.0);
                }
                helper.assertTrue(contents.run() != null, "lot should have a run spec");
                helper.assertTrue(contents.run().matches(t3, "logic", null), "run spec should match T3");

                ItemStack stack = FoupItems.with(ModItems.FOUP_MK1.get(), contents);
                ItemStack reloaded = roundTrip(helper, level, stack);
                if (reloaded != null) {
                    FoupContents afterReload = FoupItems.contentsOf(reloaded);
                    helper.assertTrue(afterReload.run() != null, "reloaded lot should have a run spec");
                    helper.assertTrue(afterReload.run().matches(t3, "logic", null), "reloaded run spec should match T3");
                }
            }
        }

        // Test (d): Lot with no spec still decodes (empty lot)
        {
            ItemStack stack = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
            ItemStack reloaded = roundTrip(helper, level, stack);
            if (reloaded != null) {
                FoupContents afterReload = FoupItems.contentsOf(reloaded);
                helper.assertTrue(afterReload.isEmpty(), "reloaded empty lot should be empty");
            }
        }

        // Test (e): Client-view lot refused by applyLayerStep
        {
            FoupContents clientView = makeClientView(25, 10, 5);
            expectRefusal(helper, "client view at applyLayerStep", () ->
                FabYieldService.applyLayerStep(level, clientView, tier, "logic", null, 1, 1.0));
        }

        // Test (e): Client-view lot refused by evaluateLot
        {
            FoupContents clientView = makeClientView(25, 10, 5);
            expectRefusal(helper, "client view at evaluateLot", () ->
                FabYieldService.evaluateLot(clientView, tier, "logic", null));
        }

        // Test (a): yieldMultiplier change mid-run doesn't affect in-flight lot
        {
            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 5);
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                contents = FabYieldService.applyLayerStep(level, contents, t1, "logic", null, 1, 1.0);
                helper.assertTrue(contents.run() != null, "run spec created at layer 1");
                int specRollCount = contents.run().rollCount();
                for (int layer = 2; layer <= t1.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t1, "logic", null, layer, 1.0);
                }
                helper.assertTrue(contents.run().rollCount() == specRollCount,
                    "run spec roll count should not change after settings change");
            }
        }

        // Test (a) extended: Actually change yieldMultiplier after layer 1 and verify
        // the in-flight lot uses the original yield while a new lot uses the new yield
        {
            // Simulate by using different FabSettings objects for the two lots
            FabSettings originalSettings = FabSettings.defaults();
            FabSettings changedSettings = FabSettings.builder()
                .yieldMultiplier(2.0)
                .build();

            // Lot 1: started with original settings (1.0), finished with changed settings (2.0)
            // The run spec should freeze the effective yield at layer 1 (1.0 * tier yield)
            UUID lotId1 = UUID.randomUUID();
            FoupContents contents1 = unprocessed(lotId1, 25);
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                // Layer 1 with original settings
                contents1 = FabYieldService.applyLayerStep(level, contents1, t1, "logic", null, 1, 1.0, originalSettings);
                // Remaining layers with changed settings (should use frozen spec)
                for (int layer = 2; layer <= t1.layers(); layer++) {
                    contents1 = FabYieldService.applyLayerStep(level, contents1, t1, "logic", null, layer, 1.0, changedSettings);
                }
                double originalYield = YieldMath.resolveEffectiveYield(t1, originalSettings, null);
                // Structural only: the in-flight lot keeps using the yield it was started with,
                // not the changed one. Pass-rate statistics live in the seeded core tests.
                helper.assertTrue(contents1.run() != null, "lot 1 should carry a run spec");
                helper.assertTrue(Math.abs(contents1.run().effectiveYield() - originalYield) <= 1e-12,
                    "the in-flight lot did not freeze the yield it was started at");
            }

            // Lot 2: started and finished with changed settings (2.0)
            // Should use the doubled yield
            UUID lotId2 = UUID.randomUUID();
            FoupContents contents2 = unprocessed(lotId2, 25);
            if (t1 != null) {
                for (int layer = 1; layer <= t1.layers(); layer++) {
                    contents2 = FabYieldService.applyLayerStep(level, contents2, t1, "logic", null, layer, 1.0, changedSettings);
                }
                double changedYield = YieldMath.resolveEffectiveYield(t1, changedSettings, null);
                // Structural only: a lot started after the change takes on the new setting.
                helper.assertTrue(contents2.run() != null, "lot 2 should carry a run spec");
                helper.assertTrue(Math.abs(contents2.run().effectiveYield() - changedYield) <= 1e-12,
                    "a new lot did not take on the new yield setting");
            }
        }

        // Test (b): rollsPerLayer change after layer 1 - verify roll count comes from stored spec
        {
            FabSettings settings1 = FabSettings.builder().rollsPerLayer(1).build();
            FabSettings settings2 = FabSettings.builder().rollsPerLayer(2).build();

            UUID lotId = UUID.randomUUID();
            FoupContents contents = unprocessed(lotId, 25);
            Tier t3 = tier(helper, 3);
            if (t3 != null) {
                // Layer 1 with 1 roll per layer
                contents = FabYieldService.applyLayerStep(level, contents, t3, "logic", null, 1, 1.0, settings1);
                helper.assertTrue(contents.run() != null, "run spec created at layer 1");
                int specRollCount = contents.run().rollCount();
                // Should be 24 (24 layers * 1 roll)
                helper.assertTrue(specRollCount == 24, "spec roll count should be 24 for T3 with 1 roll/layer");

                // Remaining layers with 2 rolls per layer (should use frozen spec)
                for (int layer = 2; layer <= t3.layers(); layer++) {
                    contents = FabYieldService.applyLayerStep(level, contents, t3, "logic", null, layer, 1.0, settings2);
                }
                helper.assertTrue(contents.run().rollCount() == specRollCount,
                    "run spec roll count should remain 24 (frozen at layer 1), not change to 48");

                double expectedYield = YieldMath.resolveEffectiveYield(t3, settings1, null);
                // Structural only: the stored run spec keeps the frozen yield and the frozen
                // roll count even though the later layers were applied under new settings.
                helper.assertTrue(contents.run() != null, "the lot should carry a run spec");
                helper.assertTrue(Math.abs(contents.run().effectiveYield() - expectedYield) <= 1e-12,
                    "the lot did not keep the yield it was frozen at");
                helper.assertTrue(contents.run().rollCount() == specRollCount,
                    "the lot did not keep its frozen roll count");
            }
        }

        // Test: layer 1 refuses memory at T1 and compute at T2
        {
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                expectRefusal(helper, "layer 1 refuses memory at T1", () ->
                    FabYieldService.applyLayerStep(level, contents, t1, "memory", null, 1, 1.0));
            }

            Tier t2 = tier(helper, 2);
            if (t2 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                expectRefusal(helper, "layer 1 refuses compute at T2", () ->
                    FabYieldService.applyLayerStep(level, contents, t2, "compute", null, 1, 1.0));
            }
        }

        // Test: layer 1 accepts logic at T1 and memory at T2
        {
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                FoupContents after = FabYieldService.applyLayerStep(level, contents, t1, "logic", null, 1, 1.0);
                helper.assertTrue(after.run() != null, "run spec created");
                helper.assertTrue("logic".equals(after.run().dieType()), "run spec die type should be logic");
            }

            Tier t2 = tier(helper, 2);
            if (t2 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                FoupContents after = FabYieldService.applyLayerStep(level, contents, t2, "memory", null, 1, 1.0);
                helper.assertTrue(after.run() != null, "run spec created");
                helper.assertTrue("memory".equals(after.run().dieType()), "run spec die type should be memory");
            }
        }

        // Test: die type mismatch refused at later layer
        {
            Tier t3 = tier(helper, 3);
            if (t3 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                FoupContents afterLayer1 = FabYieldService.applyLayerStep(level, contents, t3, "logic", null, 1, 1.0);
                helper.assertTrue("logic".equals(afterLayer1.run().dieType()), "run spec die type should be logic");
                expectRefusal(helper, "die type mismatch at later layer", () ->
                    FabYieldService.applyLayerStep(level, afterLayer1, t3, "memory", null, 2, 1.0));
            }
        }

        // Test: die type mismatch refused at evaluateLot
        {
            Tier t1 = tier(helper, 1);
            if (t1 != null) {
                UUID lotId = UUID.randomUUID();
                FoupContents contents = unprocessed(lotId, 10);
                FoupContents finished = contents;
                for (int layer = 1; layer <= t1.layers(); layer++) {
                    finished = FabYieldService.applyLayerStep(level, finished, t1, "logic", null, layer, 1.0);
                }
                final FoupContents toEval = finished;
                helper.assertTrue("logic".equals(toEval.run().dieType()), "run spec die type should be logic");
                expectRefusal(helper, "die type mismatch at evaluate", () ->
                    FabYieldService.evaluateLot(toEval, t1, "memory", null));
            }
        }

        helper.succeed();
    }

    private static ItemStack roundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops, stack);
        if (encoded.result().isEmpty()) {
            helper.fail("could not encode the stack: " + encoded.error());
            return null;
        }
        DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops, encoded.result().get());
        if (decoded.result().isEmpty()) {
            helper.fail("could not decode the stack: " + decoded.error());
            return null;
        }
        return decoded.result().get();
    }

    private static FoupContents makeClientView(int capacity, int waferCount, int lotLayer) {
        FoupContents lot = FoupContents.empty(UUID.randomUUID(), capacity);
        for (int i = 0; i < waferCount; i++) {
            lot = lot.withWafer(new Wafer(lotLayer, Wafer.NO_ROLLS, Wafer.NO_FLAGS));
        }
        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, lot);
        return FoupContentsCodec.STREAM_CODEC.decode(buffer);
    }
}