package com.euvcraft.lithmod.mc.gametest;

import java.util.UUID;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.data.TierDataLoader;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

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
            processed = FabYieldService.applyLayerStep(level, processed, tier, layer, 1.0);
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
            FabYieldService.applyLayerStep(level, finished, tier, tier.layers(), 1.0));
        expectRefusal(helper, "skipping to a later layer", () ->
            FabYieldService.applyLayerStep(level, finished, tier, tier.layers() + 2, 1.0));

        boolean[] outcomes = FabYieldService.evaluateLot(finished, tier, null);
        helper.assertTrue(outcomes.length == finished.size(), "one answer per wafer was expected");
        helper.assertTrue(finished.equals(processed), "judging the lot changed it");

        int passing = FabYieldService.countPassing(finished, tier, null);
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
                    contents = FabYieldService.applyLayerStep(level, contents, tier, layer, 1.0);
                }
                passing += FabYieldService.countPassing(contents, tier, null);
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
        } catch (IllegalArgumentException refused) {
            LOGGER.info("{}: refused with \"{}\"", what, refused.getMessage());
            return;
        }
        helper.fail(what + " was not refused");
    }
}