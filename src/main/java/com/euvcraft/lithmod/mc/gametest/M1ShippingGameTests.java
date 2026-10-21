package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.GameTestFixtures;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/**
 * The shipping-off registry probe: one game test with the required literal id
 * {@code m1_gate_shipping_no_fixtures}, registered ONLY for the
 * {@code -PlithmodFixtures=false -PlithmodShippingProbe=true} gameTestServer run.
 *
 * <p>It asserts that the test-machine fixture is genuinely absent from the live
 * server: no BLOCK, ITEM, BLOCK_ENTITY_TYPE or MENU registration under
 * {@code lithmod:test_machine}, no loaded machine definition or machine recipe for
 * {@code test_machine}, and no fixture item reachable from any creative tab. It also
 * asserts real production content IS present (registries are loaded and production
 * blocks, items, definitions and recipes resolve), so an empty registry can never
 * make the probe pass.</p>
 *
 * <p>Production never registers this class, and ordinary fixture-on runs never
 * register the probe.</p>
 */
public final class M1ShippingGameTests {

    /** The required literal game-test id for the off-mode probe. */
    private static final String CASE = "m1_gate_shipping_no_fixtures";

    /** The fixture-only registry id the probe proves absent. */
    private static final Identifier FIXTURE_ID = Identifier.parse("lithmod:test_machine");

    /** Real production content that must be present for the probe to be meaningful. */
    private static final List<Identifier> PRODUCTION_BLOCKS = List.of(
        Identifier.parse("lithmod:crystal_puller"),
        Identifier.parse("lithmod:wafer_slicer"),
        Identifier.parse("lithmod:pattern_generator"),
        Identifier.parse("lithmod:test_package"),
        Identifier.parse("lithmod:boule_purifier"),
        Identifier.parse("lithmod:ebeam_mask_writer"),
        Identifier.parse("lithmod:support_casing"),
        Identifier.parse("lithmod:support_power_port"), Identifier.parse("lithmod:support_water_inlet"));
    private static final List<Identifier> PRODUCTION_ITEMS = List.of(
        Identifier.parse("lithmod:foup_mk1"),
        Identifier.parse("lithmod:silicon"),
        Identifier.parse("lithmod:test_package"));
    /** Machine definitions loaded when fixtures are off (test_machine excluded). */
    private static final List<String> PRODUCTION_DEFINITIONS = List.of(
        "crystal_puller", "wafer_slicer", "pattern_generator", "test_package", "process_cell",
        "boule_purifier", "ebeam_mask_writer");
    /** Machine recipe machines loaded when fixtures are off (test_machine excluded). */
    private static final List<String> PRODUCTION_RECIPE_MACHINES = List.of(
        "crystal_puller", "pattern_generator");

    private static final String ENVIRONMENT_ID = "m1_shipping";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    static {
        FUNCTIONS.register(CASE, () -> M1ShippingGameTests::noFixtures);
    }

    private M1ShippingGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) M1ShippingGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        Identifier id = Identifier.fromNamespaceAndPath("lithmod", "m1_gate_shipping_no_fixtures");
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * The probe: the shipping build must have no test-machine fixture registration
     * anywhere, while the production content stays fully loaded.
     */
    private static void noFixtures(GameTestHelper helper) {
        // The probe only runs legitimately in the off-mode run.
        helper.assertTrue(!GameTestFixtures.enabled(),
            "fixtures must be off for m1_gate_shipping_no_fixtures");
        helper.assertTrue(GameTestFixtures.shippingProbeEnabled(),
            "shipping probe must be enabled for m1_gate_shipping_no_fixtures");

        assertRegistryAbsence(helper);

        // Runtime data filtering: the fixture definition and recipes are absent,
        // production definitions/recipes are loaded.
        MachineDefinitionLoader.Registry definitions = MachineDefinitionLoader.getRegistry();
        helper.assertTrue(definitions != null, "machine definitions must be loaded");
        if (definitions != null) {
            helper.assertTrue(definitions.byId("test_machine").isEmpty(),
                "test_machine definition must be absent when fixtures are off");
            helper.assertValueEqual(7, definitions.size(),
                "production machine definition inventory when fixtures are off");
            for (String machineId : PRODUCTION_DEFINITIONS) {
                helper.assertTrue(definitions.byId(machineId).isPresent(),
                    "production definition '" + machineId + "' must be loaded");
            }
        }

        MachineRecipeLoader.Registry recipes = MachineRecipeLoader.getRegistry();
        helper.assertTrue(recipes != null, "machine recipes must be loaded");
        if (recipes != null) {
            helper.assertTrue(!recipes.hasMachine("test_machine"),
                "test_machine recipes must be absent when fixtures are off");
            helper.assertTrue(recipes.forMachine("test_machine").isEmpty(),
                "test_machine recipe list must be empty when fixtures are off");
            for (String machineId : PRODUCTION_RECIPE_MACHINES) {
                helper.assertTrue(recipes.firstFor(machineId).isPresent(),
                    "production recipes for '" + machineId + "' must be loaded");
            }
            // 4 production recipe JSONs remain once the two test_machine fixtures are filtered.
            helper.assertValueEqual(4, recipes.size(),
                "production machine recipe inventory when fixtures are off");
        }

        SupportRecipeLoader.Registry support = SupportRecipeLoader.getRegistry();
        helper.assertValueEqual(8, support.size(), "shipping support recipe inventory");
        helper.assertValueEqual(1, support.forMachine("boule_purifier").size(), "shipping purifier recipe");
        helper.assertValueEqual(7, support.forMachine("ebeam_mask_writer").size(), "shipping mask designs");
        assertCreativeAbsence(helper);
    }

    /** Proves the fixed id is absent from the live block/item/BE-type/menu registries. */
    private static void assertRegistryAbsence(GameTestHelper helper) {
        helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(FIXTURE_ID),
            "no test_machine block may be registered when fixtures are off");
        helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(FIXTURE_ID),
            "no test_machine item may be registered when fixtures are off");
        helper.assertTrue(!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(FIXTURE_ID),
            "no test_machine block entity type may be registered when fixtures are off");
        helper.assertTrue(!BuiltInRegistries.MENU.containsKey(FIXTURE_ID),
            "no test_machine menu type may be registered when fixtures are off");

        // Positive controls: several real production blocks and items ARE registered,
        // so an empty or half-loaded registry cannot pass the absence checks above.
        for (Identifier blockId : PRODUCTION_BLOCKS) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(blockId),
                "production block '" + blockId + "' must be registered");
        }
        for (Identifier itemId : PRODUCTION_ITEMS) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(itemId)
                    && BuiltInRegistries.ITEM.getValue(itemId) != Items.AIR,
                "production item '" + itemId + "' must be registered");
        }
    }

    /** Builds the actual mod tab on the server; empty contents cannot pass. */
    private static void assertCreativeAbsence(GameTestHelper helper) {
        var level = helper.getLevel();
        var params = new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess());
        CreativeModeTab tab = com.euvcraft.lithmod.mc.registry.ModCreativeTabs.MAIN.get();
        tab.buildContents(params);
        var display = tab.getDisplayItems();
        helper.assertTrue(!display.isEmpty(), "production creative tab must have contents");
        helper.assertTrue(display.stream().anyMatch(stack ->
            Identifier.parse("lithmod:crystal_puller").equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))),
            "production machine must appear in actual creative contents");
        helper.assertTrue(display.stream().anyMatch(stack ->
            Identifier.parse("lithmod:foup_mk1").equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))),
            "production FOUP must appear in actual creative contents");
        for (ItemStack stack : display) {
            helper.assertTrue(stack.isEmpty()
                    || !FIXTURE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                "creative tab must not list the test-machine fixture");
        }
        helper.succeed();
    }
}
