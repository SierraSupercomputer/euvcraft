package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ServerLevel;
import com.euvcraft.lithmod.core.MaskRules;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeSpecCodec;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Consumer;

/** Data-derived checks against the live server registries and normal recipe lookup. */
public final class PresentationGameTests {
    private static final List<String> CASES = List.of("presentation_recipe_registry",
        "presentation_crafting_lookup", "presentation_pattern_production", "presentation_obtainability",
        "presentation_foup_crafting", "presentation_foup_initialization", "presentation_creative_order");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");
    static {
        FUNCTIONS.register(CASES.get(0), () -> PresentationGameTests::recipeRegistry);
        FUNCTIONS.register(CASES.get(1), () -> PresentationGameTests::craftingLookup);
        FUNCTIONS.register(CASES.get(2), () -> PresentationGameTests::patternProduction);
        FUNCTIONS.register(CASES.get(3), () -> PresentationGameTests::obtainability);
        FUNCTIONS.register(CASES.get(4), () -> PresentationGameTests::foupCrafting);
        FUNCTIONS.register(CASES.get(5), () -> PresentationGameTests::foupInitialization);
        FUNCTIONS.register(CASES.get(6), () -> PresentationGameTests::creativeOrder);
    }
    private PresentationGameTests() {}
    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((Consumer<RegisterGameTestsEvent>) PresentationGameTests::registerTests);
    }
    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", "presentation"), new TestEnvironmentDefinition[0]);
        var data = new TestData<>(env, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);
        for (String name : CASES) {
            Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }
    private static Map<Identifier, Resource> resources(GameTestHelper h, String folder) {
        return h.getLevel().getServer().getResourceManager().listResources(folder,
            id -> id.getNamespace().equals("lithmod") && id.getPath().endsWith(".json"));
    }
    private static JsonObject json(Resource resource) {
        try (var reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static Identifier recipeId(Identifier source) {
        String path = source.getPath();
        return Identifier.fromNamespaceAndPath(source.getNamespace(), path.substring("recipe/".length(), path.length()-5));
    }
    private static Item item(String raw) {
        Identifier id = Identifier.parse(raw);
        if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id) == Items.AIR)
            throw new IllegalArgumentException("Missing/non-item ingredient or result: " + raw);
        return BuiltInRegistries.ITEM.getValue(id);
    }
    private static ItemStack ingredient(String raw) {
        if (!raw.startsWith("#")) return new ItemStack(item(raw));
        var key = TagKey.create(Registries.ITEM, Identifier.parse(raw.substring(1)));
        var values = BuiltInRegistries.ITEM.getTagOrEmpty(key).iterator();
        if (!values.hasNext()) throw new IllegalArgumentException("Empty ingredient tag: " + raw);
        return new ItemStack(values.next().value());
    }
    private static CraftingInput craftingInput(JsonObject source) {
        var pattern = source.getAsJsonArray("pattern");
        var keys = source.getAsJsonObject("key");
        int width = pattern.get(0).getAsString().length();
        List<ItemStack> slots = new ArrayList<>();
        for (var row : pattern) for (char symbol : row.getAsString().toCharArray())
            slots.add(symbol == ' ' ? ItemStack.EMPTY : ingredient(keys.get(String.valueOf(symbol)).getAsString()));
        return CraftingInput.of(width, pattern.size(), slots);
    }
    private static void expectedOutput(GameTestHelper h, JsonObject source, ItemStack output, String context) {
        JsonObject result = source.getAsJsonObject("result");
        h.assertTrue(output.is(item(result.get("id").getAsString())), context + " wrong result item");
        h.assertValueEqual(result.has("count") ? result.get("count").getAsInt() : 1,
            output.getCount(), context + " wrong result count");
    }
    private static ItemStack assemble(GameTestHelper h, Identifier sourceId, JsonObject source) {
        var manager = h.getLevel().getServer().getRecipeManager();
        var holder = manager.byKey(ResourceKey.create(Registries.RECIPE, recipeId(sourceId))).orElseThrow();
        if (holder.value() instanceof CraftingRecipe crafting) {
            var input = craftingInput(source);
            h.assertTrue(crafting.matches(input, h.getLevel()), sourceId + " does not match its actual JSON inputs");
            return crafting.assemble(input);
        }
        if (holder.value() instanceof AbstractCookingRecipe cooking) {
            var input = new SingleRecipeInput(ingredient(source.get("ingredient").getAsString()));
            h.assertTrue(cooking.matches(input, h.getLevel()), sourceId + " cooking inputs do not match");
            return cooking.assemble(input);
        }
        throw new IllegalStateException("Unexpected M1 recipe kind: " + sourceId);
    }
    private static void recipeRegistry(GameTestHelper h) {
        var vanilla = resources(h, "recipe");
        h.assertValueEqual(24, vanilla.size(), "M1 vanilla recipe inventory");
        for (var entry : vanilla.entrySet()) {
            JsonObject source = json(entry.getValue());
            expectedOutput(h, source, assemble(h, entry.getKey(), source), entry.getKey().toString());
        }
        var custom = resources(h, "machine_recipes");
        var resolved = MachineRecipeLoader.getRegistry().all();
        h.assertValueEqual(6, custom.size(), "Custom source inventory");
        h.assertValueEqual(6, resolved.size(), "Resolved custom inventory");
        Set<com.euvcraft.lithmod.core.MachineRecipeSpec> expected = new HashSet<>();
        for (var entry : custom.entrySet()) {
            var spec = MachineRecipeSpecCodec.CODEC.parse(JsonOps.INSTANCE, json(entry.getValue())).getOrThrow();
            expected.add(spec);
            item(spec.result().itemId());
            for (var in : spec.ingredients()) ingredient(in.isTag() ? "#" + in.id() : in.id());
        }
        h.assertTrue(expected.equals(resolved.stream().map(r -> r.spec()).collect(java.util.stream.Collectors.toSet())),
            "Resolved machine recipes differ from actual source resources");
        for (var recipe : resolved) {
            h.assertTrue(!recipe.assemble().isEmpty(), "Resolved custom recipe assembles empty");
            h.assertValueEqual(recipe.spec().result().count(), recipe.assemble().getCount(), "Custom result count");
        }
        h.succeed();
    }
    private static void craftingLookup(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        int crafting = 0, cooking = 0;
        for (var entry : resources(h, "recipe").entrySet()) {
            JsonObject source = json(entry.getValue());
            Identifier expected = recipeId(entry.getKey());
            if (source.get("type").getAsString().equals("minecraft:crafting_shaped")) {
                var input = craftingInput(source);
                var picked = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
                h.assertTrue(picked.id().identifier().equals(expected), "Crafting lookup collision: " + expected + " selected " + picked.id());
                expectedOutput(h, source, picked.value().assemble(input), expected.toString());
                crafting++;
            } else {
                var input = new SingleRecipeInput(ingredient(source.get("ingredient").getAsString()));
                var type = source.get("type").getAsString().equals("minecraft:blasting") ? RecipeType.BLASTING : RecipeType.SMELTING;
                var picked = manager.getRecipeFor(type, input, h.getLevel()).orElseThrow();
                h.assertTrue(picked.id().identifier().equals(expected), "Cooking lookup collision: " + expected);
                expectedOutput(h, source, picked.value().assemble(input), expected.toString());
                cooking++;
            }
        }
        h.assertValueEqual(22, crafting, "Crafting lookup inventory");
        h.assertValueEqual(2, cooking, "Cooking lookup inventory");
        var sand = manager.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(Items.SAND)), h.getLevel()).orElseThrow();
        h.assertTrue(sand.value().assemble(new SingleRecipeInput(new ItemStack(Items.SAND))).is(Items.GLASS), "Sand must still smelt to glass");
        h.succeed();
    }
    private static void patternProduction(GameTestHelper h) {
        var recipes = MachineRecipeLoader.getRegistry().forMachine("pattern_generator");
        h.assertValueEqual(3, recipes.size(), "Three production mask recipes");
        Set<String> designs = new HashSet<>();
        int index = 0;
        for (var recipe : recipes) {
            MaskData template = recipe.assemble().get(ModDataComponents.MASK_DATA.get());
            h.assertTrue(template != null, "Missing public mask design");
            designs.add(template.dieType() + ":" + template.tierIndex());
            h.assertTrue(MaskRules.canPattern(template.dieType(), template.tierIndex(), 1,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix()), "Production mask rejected by G1 rules");
            BlockPos pos = new BlockPos(1 + index++ * 2, 1, 1);
            h.setBlock(pos, MaskMachines.PATTERN_GENERATOR.get());
            var gen = (PatternGeneratorBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
            for (int slot = 0; slot < 2; slot++) {
                var in = recipe.spec().ingredients().get(slot);
                ItemStack stack = ingredient(in.isTag() ? "#" + in.id() : in.id());
                stack.setCount(in.count()); gen.setSlot(slot, stack);
            }
            int ticks = recipe.spec().ticks();
            h.assertTrue(ticks > 0 && ticks <= 1000, "Bounded mask recipe duration");
            for (int tick = 0; tick < ticks; tick++) {
                gen.setStoredEnergy(gen.buffer().getCapacityAsInt());
                gen.serverTick();
                if (tick < ticks-1) h.assertTrue(gen.outputSlot().isEmpty(), "Mask completed early");
            }
            ItemStack output = gen.outputSlot();
            h.assertTrue(output.is(ModItems.MASK.get()) && output.getCount() == 1, "Exactly one mask produced");
            MaskData actual = output.get(ModDataComponents.MASK_DATA.get());
            h.assertTrue(actual != null && actual.dieType().equals(template.dieType()) && actual.tierIndex() == template.tierIndex(), "Mask design changed");
            h.assertValueEqual(EUVCRAFTMod.settings().maskUses(), actual.usesRemaining(), "Configured uses stamped");
            h.assertTrue(gen.slot(0).isEmpty() && gen.slot(1).isEmpty(), "Pattern inputs not consumed exactly");
        }
        h.assertTrue(designs.equals(Set.of("logic:1", "logic:2", "memory:2")), "Distinct M1 designs");
        h.succeed();
    }
    private static void obtainability(GameTestHelper h) {
        Set<String> produced = new HashSet<>();
        for (var entry : resources(h, "recipe").entrySet())
            produced.add(BuiltInRegistries.ITEM.getKey(assemble(h, entry.getKey(), json(entry.getValue())).getItem()).getPath());
        for (var recipe : MachineRecipeLoader.getRegistry().all())
            produced.add(BuiltInRegistries.ITEM.getKey(recipe.assemble().getItem()).getPath());
        Set<String> missing = new HashSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet())
            if (id.getNamespace().equals("lithmod") && !id.getPath().equals("test_machine") && !produced.contains(id.getPath()))
                missing.add(id.getPath());
        h.assertTrue(missing.equals(Set.of("foup_mk2", "logic_chip_t1", "logic_chip_t2", "memory_chip_t2", "chip_scrap")),
            "Unexpected recipe-less production items: " + missing);
        h.succeed();
    }

    private static void assertFreshFoup(GameTestHelper h, ItemStack stack) {
        var lot = FoupItems.contentsOf(stack);
        h.assertTrue(lot != null && lot.isEmpty() && !FoupContentsCodec.isClientView(lot), "Fresh FOUP must hold an empty server lot");
        h.assertValueEqual(FoupItems.configuredCapacity(), lot.capacity(), "Fresh FOUP capacity");
    }
    private static void foupCrafting(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var sourceId = Identifier.parse("lithmod:recipe/crafting/foup_mk1.json");
        var input = craftingInput(json(resources(h, "recipe").get(sourceId)));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        h.assertTrue(recipe.id().identifier().equals(Identifier.parse("lithmod:crafting/foup_mk1")), "Actual FOUP crafting lookup");
        Set<UUID> identities = new HashSet<>();
        for (int i=0;i<2;i++) {
            ItemStack output = recipe.value().assemble(input);
            h.assertTrue(output.is(ModItems.FOUP_MK1.get()), "Crafting produced wrong FOUP");
            h.assertTrue(!output.has(ModDataComponents.FOUP_CONTENTS.get()), "Recipe must not embed a shared lot identity");
            output.getItem().onCraftedBy(output, player);
            assertFreshFoup(h, output);
            var lot = FoupItems.contentsOf(output);
            h.assertTrue(identities.add(lot.lotId()), "Separately crafted FOUPs reused a lot identity");
            output.getItem().onCraftedPostProcess(output, h.getLevel());
            output.getItem().inventoryTick(output, h.getLevel(), player, null);
            h.assertTrue(FoupItems.contentsOf(output) == lot, "Repeated craft/inventory hooks replaced a sealed lot");
        }
        h.succeed();
    }
    private static void foupInitialization(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        Set<UUID> identities = new HashSet<>();
        Component name = Component.literal("FOUP initialization probe");
        for (Item type : List.of(ModItems.FOUP_MK1.get(), ModItems.FOUP_MK2.get())) {
            for (int i=0;i<2;i++) {
                ItemStack stack = new ItemStack(type);
                h.assertTrue(!stack.has(ModDataComponents.FOUP_CONTENTS.get()), "Default properties embedded a lot");
                stack.set(DataComponents.CUSTOM_NAME, name);
                type.inventoryTick(stack, h.getLevel(), player, null);
                assertFreshFoup(h, stack);
                var lot = FoupItems.contentsOf(stack);
                h.assertTrue(identities.add(lot.lotId()), "Fresh inventory FOUPs share an identity");
                h.assertTrue(stack.getCount() == 1 && name.equals(stack.get(DataComponents.CUSTOM_NAME)), "Initialization changed other stack data");
                type.inventoryTick(stack, h.getLevel(), player, null);
                type.onCraftedPostProcess(stack, h.getLevel());
                h.assertTrue(FoupItems.contentsOf(stack) == lot, "Repeated inventory hooks replaced lot");
            }
            var filled = FoupContents.empty(UUID.randomUUID(), FoupItems.configuredCapacity()).withWafer(new Wafer(1, 0.42f, 0));
            ItemStack existing = FoupItems.with(type, filled);
            type.inventoryTick(existing, h.getLevel(), player, null);
            type.onCraftedPostProcess(existing, h.getLevel());
            h.assertTrue(FoupItems.contentsOf(existing) == filled, "Initialization replaced existing trusted wafer data");
            var view = FoupContents.empty(FoupContentsCodec.CLIENT_VIEW_LOT_ID, FoupItems.configuredCapacity()).withWafer(Wafer.unprocessed());
            ItemStack untrusted = FoupItems.with(type, view);
            h.assertTrue(FoupContentsCodec.isClientView(view), "Client-view precondition");
            type.inventoryTick(untrusted, h.getLevel(), player, null);
            type.onCraftedPostProcess(untrusted, h.getLevel());
            h.assertTrue(FoupItems.contentsOf(untrusted) == view, "Initialization rewrote an existing client-view component");
            h.assertTrue(FoupItems.trustedContentsOf(untrusted).isEmpty(), "Client-view wafers became trusted");
        }
        ItemStack other = new ItemStack(Items.IRON_INGOT);
        FoupItems.initializeIfMissing(other);
        h.assertTrue(!other.has(ModDataComponents.FOUP_CONTENTS.get()), "Initialization modified a non-FOUP item");
        h.succeed();
    }

    /**
     * Validates the entry order of all EUVCRAFT creative tabs.
     *
     * <p>This test ensures that the creative tab entry order is stable and matches the
     * intended presentation order. Each tab's entries are validated against their
     * expected item sequence.</p>
     */
    private static void creativeOrder(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var params = new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess());

        // Tab 0: MAIN - FOUP variants
        CreativeModeTab mainTab = ModCreativeTabs.MAIN.get();
        mainTab.buildContents(params);
        List<ItemStack> mainEntries = new ArrayList<>(mainTab.getDisplayItems());
        h.assertValueEqual(2, mainEntries.size(), "MAIN tab entry count changed");
        h.assertTrue(mainEntries.get(0).is(ModItems.FOUP_MK1.get()),
            "MAIN tab entry 0 is not FOUP Mk1: " + mainEntries.get(0).getItem());
        h.assertTrue(mainEntries.get(1).is(ModItems.FOUP_MK2.get()),
            "MAIN tab entry 1 is not FOUP Mk2: " + mainEntries.get(1).getItem());

        // Tab 1: CHIPS - chips and consumables
        CreativeModeTab chipsTab = ModCreativeTabs.CHIPS.get();
        chipsTab.buildContents(params);
        List<ItemStack> chipsEntries = new ArrayList<>(chipsTab.getDisplayItems());
        h.assertValueEqual(7, chipsEntries.size(), "CHIPS tab entry count changed");
        h.assertTrue(chipsEntries.get(0).is(ModItems.LOGIC_CHIP_T1.get()), "CHIPS entry 0");
        h.assertTrue(chipsEntries.get(1).is(ModItems.LOGIC_CHIP_T2.get()), "CHIPS entry 1");
        h.assertTrue(chipsEntries.get(2).is(ModItems.MEMORY_CHIP_T2.get()), "CHIPS entry 2");
        h.assertTrue(chipsEntries.get(3).is(ModItems.CHIP_SCRAP.get()), "CHIPS entry 3");
        h.assertTrue(chipsEntries.get(4).is(ModItems.RESIST_T1.get()), "CHIPS entry 4");
        h.assertTrue(chipsEntries.get(5).is(ModItems.RESIST_T2.get()), "CHIPS entry 5");
        h.assertTrue(chipsEntries.get(6).is(ModItems.CHIP_PACKAGE.get()), "CHIPS entry 6");

        // Tab 2: PROCESS_CELL - process cell blocks
        CreativeModeTab processCellTab = ModCreativeTabs.PROCESS_CELL.get();
        processCellTab.buildContents(params);
        List<ItemStack> pcEntries = new ArrayList<>(processCellTab.getDisplayItems());
        h.assertValueEqual(3, pcEntries.size(), "PROCESS_CELL tab entry count changed");
        h.assertTrue(pcEntries.get(0).is(ModItems.PROCESS_CELL_CONTROLLER.get()), "PROCESS_CELL entry 0");
        h.assertTrue(pcEntries.get(1).is(ModItems.PROCESS_CELL_CASING.get()), "PROCESS_CELL entry 1");
        h.assertTrue(pcEntries.get(2).is(ModItems.PROCESS_CELL_POWER_PORT.get()), "PROCESS_CELL entry 2");

        // Tab 3: COMPONENTS - line components
        CreativeModeTab componentsTab = ModCreativeTabs.COMPONENTS.get();
        componentsTab.buildContents(params);
        List<ItemStack> compEntries = new ArrayList<>(componentsTab.getDisplayItems());
        h.assertValueEqual(10, compEntries.size(), "COMPONENTS tab entry count changed");
        h.assertTrue(compEntries.get(0).is(ModItems.MERCURY_LAMP.get()), "COMPONENTS entry 0");
        h.assertTrue(compEntries.get(1).is(ModItems.I_LINE_LAMP.get()), "COMPONENTS entry 1");
        h.assertTrue(compEntries.get(2).is(ModItems.CONTACT_PLATE.get()), "COMPONENTS entry 2");
        h.assertTrue(compEntries.get(3).is(ModItems.REDUCTION_LENS.get()), "COMPONENTS entry 3");
        h.assertTrue(compEntries.get(4).is(ModItems.MANUAL_ALIGNER.get()), "COMPONENTS entry 4");
        h.assertTrue(compEntries.get(5).is(ModItems.STEPPER_STAGE.get()), "COMPONENTS entry 5");
        h.assertTrue(compEntries.get(6).is(ModItems.BASIC_AIR_FILTER.get()), "COMPONENTS entry 6");
        h.assertTrue(compEntries.get(7).is(ModItems.FINE_AIR_FILTER.get()), "COMPONENTS entry 7");
        h.assertTrue(compEntries.get(8).is(ModItems.OPTICAL_MICROSCOPE.get()), "COMPONENTS entry 8");
        h.assertTrue(compEntries.get(9).is(ModItems.OVERLAY_SCOPE.get()), "COMPONENTS entry 9");

        // Tab 4: SILICON_CHAIN - silicon chain items
        CreativeModeTab siliconTab = ModCreativeTabs.SILICON_CHAIN.get();
        siliconTab.buildContents(params);
        List<ItemStack> siEntries = new ArrayList<>(siliconTab.getDisplayItems());
        h.assertValueEqual(4, siEntries.size(), "SILICON_CHAIN tab entry count changed");
        h.assertTrue(siEntries.get(0).is(ModItems.SILICON.get()), "SILICON_CHAIN entry 0");
        h.assertTrue(siEntries.get(1).is(ModItems.SILICON_BOULE.get()), "SILICON_CHAIN entry 1");
        h.assertTrue(siEntries.get(2).is(ModItems.CRYSTAL_PULLER.get()), "SILICON_CHAIN entry 2");
        h.assertTrue(siEntries.get(3).is(ModItems.WAFER_SLICER.get()), "SILICON_CHAIN entry 3");

        // Tab 5: MASKS - photomask group
        CreativeModeTab masksTab = ModCreativeTabs.MASKS.get();
        masksTab.buildContents(params);
        List<ItemStack> maskEntries = new ArrayList<>(masksTab.getDisplayItems());
        h.assertValueEqual(5, maskEntries.size(), "MASKS tab entry count changed");
        h.assertTrue(maskEntries.get(0).is(ModItems.BLANK_MASK.get()), "MASKS entry 0 not blank mask");
        h.assertTrue(maskEntries.get(0).get(ModDataComponents.MASK_DATA.get()) == null,
            "MASKS entry 0 blank mask carries a design");
        // Entry 1: logic T1 mask
        h.assertTrue(maskEntries.get(1).is(ModItems.MASK.get()), "MASKS entry 1 not a mask");
        MaskData d1 = maskEntries.get(1).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d1 != null && DieTypes.LOGIC.equals(d1.dieType()) && d1.tierIndex() == 1,
            "MASKS entry 1 not logic T1: " + d1);
        // Entry 2: logic T2 mask
        h.assertTrue(maskEntries.get(2).is(ModItems.MASK.get()), "MASKS entry 2 not a mask");
        MaskData d2 = maskEntries.get(2).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d2 != null && DieTypes.LOGIC.equals(d2.dieType()) && d2.tierIndex() == 2,
            "MASKS entry 2 not logic T2: " + d2);
        // Entry 3: memory T2 mask
        h.assertTrue(maskEntries.get(3).is(ModItems.MASK.get()), "MASKS entry 3 not a mask");
        MaskData d3 = maskEntries.get(3).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d3 != null && DieTypes.MEMORY.equals(d3.dieType()) && d3.tierIndex() == 2,
            "MASKS entry 3 not memory T2: " + d3);
        // Entry 4: pattern generator
        h.assertTrue(maskEntries.get(4).is(ModItems.PATTERN_GENERATOR.get()),
            "MASKS entry 4 not pattern generator: " + maskEntries.get(4).getItem());

        // Tab 6: TEST_PACKAGE - test package machine
        CreativeModeTab testPkgTab = ModCreativeTabs.TEST_PACKAGE.get();
        testPkgTab.buildContents(params);
        List<ItemStack> tpEntries = new ArrayList<>(testPkgTab.getDisplayItems());
        h.assertValueEqual(1, tpEntries.size(), "TEST_PACKAGE tab entry count changed");
        h.assertTrue(tpEntries.get(0).is(ModItems.TEST_PACKAGE.get()), "TEST_PACKAGE entry 0");

        h.succeed();
    }
}
