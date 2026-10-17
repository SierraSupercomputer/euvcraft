package com.euvcraft.lithmod.mc.gametest;

import java.util.List;

import com.euvcraft.lithmod.core.LineLoadout;
import com.euvcraft.lithmod.mc.ComponentCatalog;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.machine.ComponentSlot;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;

public final class ComponentGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "component_default";

    private static final DeferredRegister<java.util.function.Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register("component_catalog_lookups", () -> ComponentGameTests::catalogLookups);
        FUNCTIONS.register("component_reader_t1", () -> ComponentGameTests::readerT1);
        FUNCTIONS.register("component_reader_t2", () -> ComponentGameTests::readerT2);
        FUNCTIONS.register("component_slot_validation", () -> ComponentGameTests::slotValidation);
    }

    private ComponentGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((java.util.function.Consumer<RegisterGameTestsEvent>) ComponentGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        net.minecraft.core.Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        net.minecraft.resources.Identifier empty = net.minecraft.resources.Identifier.withDefaultNamespace("empty");
        TestData<net.minecraft.core.Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            env, net.minecraft.world.level.Level.OVERWORLD, empty,
            200, 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "component_catalog_lookups");
        register(event, data, "component_reader_t1");
        register(event, data, "component_reader_t2");
        register(event, data, "component_slot_validation");
        LOGGER.info("Registered EUVCRAFT component game tests");
    }

    private static void register(RegisterGameTestsEvent event, TestData<net.minecraft.core.Holder<TestEnvironmentDefinition<?>>> data, String name) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("lithmod", name);
        net.minecraft.resources.ResourceKey<java.util.function.Consumer<GameTestHelper>> function =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    private static void catalogLookups(GameTestHelper helper) {
        Object[][] cases = new Object[][] {
            { ModItems.MERCURY_LAMP.get(), "light_source", 1 },
            { ModItems.I_LINE_LAMP.get(), "light_source", 2 },
            { ModItems.CONTACT_PLATE.get(), "optics", 1 },
            { ModItems.REDUCTION_LENS.get(), "optics", 2 },
            { ModItems.MANUAL_ALIGNER.get(), "stage", 1 },
            { ModItems.STEPPER_STAGE.get(), "stage", 2 },
            { ModItems.BASIC_AIR_FILTER.get(), "cleanroom", 1 },
            { ModItems.FINE_AIR_FILTER.get(), "cleanroom", 2 },
            { ModItems.OPTICAL_MICROSCOPE.get(), "metrology", 1 },
            { ModItems.OVERLAY_SCOPE.get(), "metrology", 2 }
        };
        for (Object[] c : cases) {
            net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) c[0];
            String kind = (String) c[1];
            int tier = (int) c[2];
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(ComponentCatalog.kindOf(stack).orElse("").equals(kind),
                item + " reports kind " + ComponentCatalog.kindOf(stack) + ", expected " + kind);
            helper.assertTrue(ComponentCatalog.tierOf(stack) == tier,
                item + " reports tier " + ComponentCatalog.tierOf(stack) + ", expected " + tier);
        }
        helper.assertTrue(ComponentCatalog.kindOf(new ItemStack(net.minecraft.world.item.Items.STONE)).isEmpty(),
            "stone reports a component kind");
        helper.assertTrue(ComponentCatalog.tierOf(new ItemStack(net.minecraft.world.item.Items.STONE)) == 0,
            "stone reports a component tier");
        helper.assertTrue(ComponentCatalog.kindOf(ItemStack.EMPTY).isEmpty(), "empty reports a component kind");
        helper.succeed();
    }

    private static void readerT1(GameTestHelper helper) {
        ItemStack light = new ItemStack(ModItems.MERCURY_LAMP.get());
        ItemStack optics = new ItemStack(ModItems.CONTACT_PLATE.get());
        ItemStack stage = new ItemStack(ModItems.MANUAL_ALIGNER.get());
        ItemStack cleanroom = new ItemStack(ModItems.BASIC_AIR_FILTER.get());
        ItemStack metrology = new ItemStack(ModItems.OPTICAL_MICROSCOPE.get());
        ItemStack resist = new ItemStack(com.euvcraft.lithmod.mc.registry.ModItems.RESIST_T1.get());

        LineLoadout loadout = LineLoadoutReader.read(light, optics, stage, cleanroom, metrology, resist, 1);
        LineLoadout expected = new LineLoadout(1, 1, 1, 1, 1, 1, 1, 1.0);
        helper.assertTrue(loadout.equals(expected), "T1 reader mismatch: " + loadout + " != " + expected);
        LineLoadout wrongKind = LineLoadoutReader.read(light, light, stage, cleanroom, metrology, resist, 1);
        helper.assertValueEqual(0, wrongKind.opticsTier(), "a lamp in the optics slot must not count as optics");
        helper.assertValueEqual(1, wrongKind.lightSourceTier(), "valid lamp slot must still count");
        helper.succeed();
    }

    private static void readerT2(GameTestHelper helper) {
        ItemStack light = new ItemStack(ModItems.I_LINE_LAMP.get());
        ItemStack optics = new ItemStack(ModItems.REDUCTION_LENS.get());
        ItemStack stage = new ItemStack(ModItems.STEPPER_STAGE.get());
        ItemStack cleanroom = new ItemStack(ModItems.FINE_AIR_FILTER.get());
        ItemStack metrology = new ItemStack(ModItems.OVERLAY_SCOPE.get());
        ItemStack resist = new ItemStack(com.euvcraft.lithmod.mc.registry.ModItems.RESIST_T2.get());

        LineLoadout loadout = LineLoadoutReader.read(light, optics, stage, cleanroom, metrology, resist, 2);
        LineLoadout expected = new LineLoadout(2, 2, 2, 2, 2, 2, 2, 1.0);
        helper.assertTrue(loadout.equals(expected), "T2 reader mismatch: " + loadout + " != " + expected);
        helper.succeed();
    }

    private static void slotValidation(GameTestHelper helper) {
        Container container = new net.minecraft.world.SimpleContainer(1);
        ComponentSlot slot = new ComponentSlot(container, 0, 0, 0, "light_source");
        helper.assertTrue(slot.mayPlace(new ItemStack(ModItems.MERCURY_LAMP.get())),
            "matching kind rejected");
        helper.assertTrue(!slot.mayPlace(new ItemStack(ModItems.CONTACT_PLATE.get())),
            "non-matching kind accepted");
        helper.assertTrue(!slot.mayPlace(new ItemStack(net.minecraft.world.item.Items.STONE)),
            "non-component accepted");
        helper.assertTrue(slot.getMaxStackSize() == 1, "slot does not hold one");
        helper.succeed();
    }
}
