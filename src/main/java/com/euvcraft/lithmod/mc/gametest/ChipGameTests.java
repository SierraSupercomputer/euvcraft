package com.euvcraft.lithmod.mc.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Game tests for the chips and consumables of milestone M1-05.
 *
 * <p>{@code lithmod:chip_catalog_lookups} asks the catalog for the three chips that
 * exist and for the ones that do not, checks the stack sizes and the resist tiers, and
 * builds the chips tab. {@code lithmod:chip_tooltip_lines} checks what a chip tooltip
 * is made of and that it carries nothing about the lot. {@code lithmod:item_assets}
 * walks the item registry and fails on any EUVCRAFT item without a client item
 * definition, an item model or a language entry.</p>
 *
 * <p>All three need the real registries: the catalog answers in {@link Item} instances
 * and the asset walk asks the item registry what exists.</p>
 */
public final class ChipGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final String ENVIRONMENT_ID = "chip_default";

    private static final String LANG = "/assets/lithmod/lang/en_us.json";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register("chip_catalog_lookups", () -> ChipGameTests::catalogLookups);
        FUNCTIONS.register("chip_tooltip_lines", () -> ChipGameTests::tooltipLines);
        FUNCTIONS.register("item_assets", () -> ChipGameTests::itemAssets);
    }

    private ChipGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) ChipGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "chip_catalog_lookups");
        register(event, data, "chip_tooltip_lines");
        register(event, data, "item_assets");
        LOGGER.info("Registered EUVCRAFT chip game tests");
    }

    private static void register(RegisterGameTestsEvent event,
        TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * Proves what the catalog answers, what the items stack to, what a resist stack
     * reports as its tier and what the chips tab hands out.
     */
    private static void catalogLookups(GameTestHelper helper) {
        assertChip(helper, DieTypes.LOGIC, 1, ModItems.LOGIC_CHIP_T1.get());
        assertChip(helper, DieTypes.LOGIC, 2, ModItems.LOGIC_CHIP_T2.get());
        assertChip(helper, DieTypes.MEMORY, 2, ModItems.MEMORY_CHIP_T2.get());

        helper.assertTrue(ChipCatalog.chipFor(DieTypes.MEMORY, 1).isEmpty(),
            "memory at T1 has no chip and the catalog returned " + ChipCatalog.chipFor(DieTypes.MEMORY, 1));

        for (Tier tier : TierDataLoader.getRegistry().all()) {
            for (String dieType : List.of(DieTypes.COMPUTE, DieTypes.NAND)) {
                Optional<Item> chip = ChipCatalog.chipFor(dieType, tier.index());
                helper.assertTrue(chip.isEmpty(),
                    "the catalog returned " + chip + " for " + dieType + " at " + tier.id() + ", which has no chip yet");
            }
        }
        helper.assertTrue(ChipCatalog.chipFor("no_such_die", 1).isEmpty(),
            "the catalog answered for a die type that does not exist");

        for (Item item : List.of(ModItems.LOGIC_CHIP_T1.get(), ModItems.LOGIC_CHIP_T2.get(),
            ModItems.MEMORY_CHIP_T2.get(), ModItems.CHIP_SCRAP.get(), ModItems.RESIST_T1.get(),
            ModItems.RESIST_T2.get(), ModItems.CHIP_PACKAGE.get())) {
            ItemStack stack = new ItemStack(item);
            assertEqual(helper, 64, stack.getMaxStackSize(), item + " does not stack to 64");
            assertEqual(helper, 64, item.getMaxStackSize(stack), item + " does not report a stack size of 64");
        }

        assertEqual(helper, 1, ResistCatalog.tierOf(new ItemStack(ModItems.RESIST_T1.get())),
            "resist_t1 does not report tier 1");
        assertEqual(helper, 2, ResistCatalog.tierOf(new ItemStack(ModItems.RESIST_T2.get())),
            "resist_t2 does not report tier 2");
        assertEqual(helper, 0, ResistCatalog.tierOf(new ItemStack(Items.STONE)),
            "stone reports a resist tier");
        assertEqual(helper, 0, ResistCatalog.tierOf(new ItemStack(ModItems.LOGIC_CHIP_T1.get())),
            "a logic chip reports a resist tier");
        assertEqual(helper, 0, ResistCatalog.tierOf(ItemStack.EMPTY),
            "an empty stack reports a resist tier");
        ItemStack stripped = new ItemStack(ModItems.RESIST_T1.get());
        stripped.remove(ModDataComponents.RESIST_TIER.get());
        assertEqual(helper, 0, ResistCatalog.tierOf(stripped),
            "a resist with its tier component removed still reports a tier");

        helper.assertTrue(ChipCatalog.designOf(Items.STONE).isEmpty(), "stone reports a chip design");
        assertDesign(helper, ModItems.LOGIC_CHIP_T1.get(), DieTypes.LOGIC, 1);
        assertDesign(helper, ModItems.LOGIC_CHIP_T2.get(), DieTypes.LOGIC, 2);
        assertDesign(helper, ModItems.MEMORY_CHIP_T2.get(), DieTypes.MEMORY, 2);

        CreativeModeTab tab = ModCreativeTabs.CHIPS.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
            helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        Collection<ItemStack> displayed = tab.getDisplayItems();
        List<Item> shown = new ArrayList<>();
        for (ItemStack stack : displayed) {
            shown.add(stack.getItem());
        }
        for (Item expected : List.of(ModItems.LOGIC_CHIP_T1.get(), ModItems.LOGIC_CHIP_T2.get(),
            ModItems.MEMORY_CHIP_T2.get(), ModItems.CHIP_SCRAP.get(), ModItems.RESIST_T1.get(),
            ModItems.RESIST_T2.get(), ModItems.CHIP_PACKAGE.get())) {
            helper.assertTrue(shown.contains(expected), expected + " is missing from the chips tab");
        }
        assertEqual(helper, 7, displayed.size(), "the chips tab holds entries that are not registered in it");

        helper.succeed();
    }

    /**
     * Proves that a chip tooltip is one translatable line naming the die type and the
     * node tier, and that it carries no number at all.
     *
     * <p>The language file is not loaded on a server, so the line is checked by its
     * key and its arguments rather than by its rendered text. That is the stronger
     * check anyway: a yield line would have to put a number in one of the two
     * arguments.</p>
     */
    private static void tooltipLines(GameTestHelper helper) {
        assertChipTooltip(helper, ModItems.LOGIC_CHIP_T1.get(), DieTypes.LOGIC, 1);
        assertChipTooltip(helper, ModItems.LOGIC_CHIP_T2.get(), DieTypes.LOGIC, 2);
        assertChipTooltip(helper, ModItems.MEMORY_CHIP_T2.get(), DieTypes.MEMORY, 2);

        for (Item plain : List.of(ModItems.CHIP_SCRAP.get(), ModItems.RESIST_T1.get(),
            ModItems.RESIST_T2.get(), ModItems.CHIP_PACKAGE.get())) {
            List<Component> lines = tooltipOf(new ItemStack(plain));
            assertEqual(helper, 1, lines.size(), plain + " has more than the name line in its tooltip");
        }

        helper.succeed();
    }

    private static void assertChipTooltip(GameTestHelper helper, Item item, String dieType, int tierIndex) {
        List<Component> lines = tooltipOf(new ItemStack(item));
        assertEqual(helper, 2, lines.size(), item + " does not have exactly a name line and a chip line");

        Component line = lines.get(1);
        helper.assertTrue(line.getContents() instanceof TranslatableContents,
            "the chip line of " + item + " is not translatable: " + line);
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEqual(helper, "tooltip.lithmod.chip", contents.getKey(),
            "the chip line of " + item + " uses another key");

        Object[] args = contents.getArgs();
        assertEqual(helper, 2, args.length, "the chip line of " + item + " does not have two arguments");
        assertEqual(helper, "dieType.lithmod." + dieType, argumentKey(args[0]),
            "the chip line of " + item + " names another die type");
        assertEqual(helper, "tier.lithmod.t" + tierIndex, argumentKey(args[1]),
            "the chip line of " + item + " names another node tier");
        for (Object arg : args) {
            helper.assertTrue(!(arg instanceof Number || arg instanceof Float || arg instanceof Double),
                "the chip line of " + item + " carries the number " + arg + ", which can be a hidden value");
        }
    }

    private static String argumentKey(Object argument) {
        if (argument instanceof Component component && component.getContents() instanceof TranslatableContents nested) {
            return nested.getKey();
        }
        return String.valueOf(argument);
    }

    private static List<Component> tooltipOf(ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
    }

    /**
     * Proves that every registered EUVCRAFT item has a client item definition, an item
     * model and a language entry.
     *
     * <p>The three files are read from the classpath, which is where the mod runs from.
     * An item that renders as the missing-model cube has a definition without a model,
     * and an item that shows its raw registry path in the creative tab has no language
     * entry, so both are checked for every item in the registry, not only for the ones
     * this milestone added.</p>
     */
    private static void itemAssets(GameTestHelper helper) {
        Map<String, String> lang = langEntries();
        List<Identifier> ids = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (EUVCRAFTMod.MODID.equals(id.getNamespace())) {
                ids.add(id);
            }
        }
        helper.assertTrue(!ids.isEmpty(), "no EUVCRAFT item is registered at all");

        for (Identifier id : ids) {
            String path = id.getPath();
            helper.assertTrue(hasResource("/assets/lithmod/items/" + path + ".json"),
                id + " has no client item definition");
            helper.assertTrue(hasResource("/assets/lithmod/models/item/" + path + ".json"),
                id + " has no item model");
            String key = "item." + EUVCRAFTMod.MODID + "." + path;
            helper.assertTrue(lang.containsKey(key), key + " has no language entry");
        }

        LOGGER.info("Checked assets for {} EUVCRAFT items", ids.size());
        helper.succeed();
    }

    private static Map<String, String> langEntries() {
        Map<String, String> entries = new HashMap<>();
        try (InputStream input = ChipGameTests.class.getResourceAsStream(LANG)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled language file: " + LANG);
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject().entrySet()) {
                entries.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the bundled language file " + LANG, e);
        }
        return entries;
    }

    private static boolean hasResource(String path) {
        return ChipGameTests.class.getResource(path) != null;
    }

    private static void assertChip(GameTestHelper helper, String dieType, int tierIndex, Item expected) {
        Optional<Item> chip = ChipCatalog.chipFor(dieType, tierIndex);
        helper.assertTrue(chip.isPresent(), "no chip for " + dieType + " at tier " + tierIndex);
        helper.assertTrue(chip.get() == expected,
            "the catalog answered " + chip + " for " + dieType + " at tier " + tierIndex + ", expected " + expected);
    }

    private static void assertDesign(GameTestHelper helper, Item item, String dieType, int tierIndex) {
        Optional<ChipCatalog.Design> design = ChipCatalog.designOf(item);
        helper.assertTrue(design.isPresent(), item + " has no chip design");
        assertEqual(helper, dieType, design.get().dieType(), item + " reports another die type");
        assertEqual(helper, tierIndex, design.get().tierIndex(), item + " reports another node tier");
    }

    private static void assertEqual(GameTestHelper helper, Object expected, Object actual, String message) {
        helper.assertTrue(expected.equals(actual), message + ": " + expected + " -> " + actual);
    }
}