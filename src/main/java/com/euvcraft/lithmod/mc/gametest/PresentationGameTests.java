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
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.tags.TagKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Data-derived checks against the live server registries and normal recipe lookup. */
public final class PresentationGameTests {
    private static final List<String> CASES = List.of("presentation_recipe_registry",
        "presentation_crafting_lookup", "presentation_pattern_production", "presentation_obtainability",
        "presentation_foup_crafting", "presentation_foup_initialization", "presentation_creative_order",
        "presentation_datapack_override", "presentation_assets_and_harvest", "presentation_survival_drops");
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
        FUNCTIONS.register(CASES.get(7), () -> PresentationGameTests::datapackOverride);
        FUNCTIONS.register(CASES.get(8), () -> PresentationGameTests::assetsAndHarvest);
        FUNCTIONS.register(CASES.get(9), () -> PresentationGameTests::survivalDrops);
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
        h.assertValueEqual(55, vanilla.size(), "M1 vanilla recipe inventory");
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
        h.assertValueEqual(53, crafting, "Crafting lookup inventory");
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
        for (var recipe : com.euvcraft.lithmod.mc.data.SupportRecipeLoader.getRegistry().all())
            produced.add(BuiltInRegistries.ITEM.getKey(recipe.outputItem()).getPath());
        Set<String> missing = new HashSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet())
            if (id.getNamespace().equals("lithmod") && !id.getPath().equals("test_machine") && !produced.contains(id.getPath()))
                missing.add(id.getPath());
        h.assertTrue(missing.equals(Set.of("foup_mk2", "logic_chip_t1", "logic_chip_t2", "memory_chip_t2", "chip_scrap",
                "logic_chip_t3", "memory_chip_t3", "compute_chip_t3", "nand_chip_t3",
                "logic_chip_t4", "memory_chip_t4", "compute_chip_t4", "nand_chip_t4")),
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
     * Validates the entry order of the unified EUVCRAFT creative tab.
     *
     * <p>This test ensures that the MAIN creative tab entry order is stable and matches the
     * intended presentation order. The 49 entries are validated against their
     * expected item sequence. Also verifies that all lithmod items (except test_machine)
     * appear exactly once in the tab, and that only one lithmod CreativeModeTab is registered.
     * </p>
     */
    private static void creativeOrder(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var params = new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess());

        CreativeModeTab mainTab = ModCreativeTabs.MAIN.get();
        mainTab.buildContents(params);
        List<ItemStack> actualEntries = new ArrayList<>(mainTab.getDisplayItems());

        // Expected 49 stacks in exact order
        List<Item> expectedItems = List.of(
            // M1 items
            ModItems.CRYSTAL_PULLER.get(),
            ModItems.WAFER_SLICER.get(),
            ModItems.PATTERN_GENERATOR.get(),
            ModItems.TEST_PACKAGE.get(),
            ModItems.PROCESS_CELL_CONTROLLER.get(),
            ModItems.PROCESS_CELL_CASING.get(),
            ModItems.PROCESS_CELL_POWER_PORT.get(),
            ModItems.PROCESS_CELL_OPERATING_PANEL.get(),
            ModItems.BOULE_PURIFIER.get(),
            ModItems.E_BEAM_MASK_WRITER.get(),
            ModItems.SUPPORT_CASING.get(),
            ModItems.SUPPORT_POWER_PORT.get(),
            ModItems.SUPPORT_WATER_INLET.get(),
            ModItems.DEPOSITION_ETCHER_CONTROLLER.get(),
            ModItems.ADVANCED_OPERATING_PANEL.get(),
            ModItems.ADVANCED_TEST_PACKAGE_CONTROLLER.get(),
            ModItems.ADVANCED_CASING.get(),
            ModItems.ADVANCED_POWER_PORT.get(),
            ModItems.SCANNER_CONTROLLER.get(),
            ModItems.SCANNER_OPERATING_PANEL.get(),
            ModItems.SCANNER_CALIBRATION_PANEL.get(),
            ModItems.SCANNER_EXPOSURE_MODULE.get(),
            ModItems.SCANNER_OPTICS_MODULE.get(),
            ModItems.SCANNER_STAGE_MODULE.get(),
            ModItems.SCANNER_FILTRATION_MODULE.get(),
            ModItems.SCANNER_METROLOGY_MODULE.get(),
            ModItems.FOUP_MK1.get(),
            ModItems.FOUP_MK2.get(),
            ModItems.SILICON.get(),
            ModItems.SILICON_BOULE.get(),
            ModItems.PURE_SILICON_BOULE.get(),
            ModItems.BLANK_MASK.get(),
            ModItems.MASK.get(), // sampleMask(LOGIC, 1)
            ModItems.MASK.get(), // sampleMask(LOGIC, 2)
            ModItems.MASK.get(), // sampleMask(MEMORY, 2)
            ModItems.MERCURY_LAMP.get(),
            ModItems.I_LINE_LAMP.get(),
            ModItems.CONTACT_PLATE.get(),
            ModItems.REDUCTION_LENS.get(),
            ModItems.MANUAL_ALIGNER.get(),
            ModItems.STEPPER_STAGE.get(),
            ModItems.BASIC_AIR_FILTER.get(),
            ModItems.FINE_AIR_FILTER.get(),
            ModItems.OPTICAL_MICROSCOPE.get(),
            ModItems.OVERLAY_SCOPE.get(),
            ModItems.RESIST_T1.get(),
            ModItems.RESIST_T2.get(),
            ModItems.CHIP_PACKAGE.get(),
            ModItems.LOGIC_CHIP_T1.get(),
            ModItems.LOGIC_CHIP_T2.get(),
            ModItems.MEMORY_CHIP_T2.get(),
            ModItems.CHIP_SCRAP.get(),
            // T3 items appended
            ModItems.KRF_EXCIMER_LASER.get(),
            ModItems.DUV_PROJECTION_LENS.get(),
            ModItems.PRECISION_STEPPER_STAGE.get(),
            ModItems.HEPA_FILTER.get(),
            ModItems.LASER_INTERFEROMETER.get(),
            ModItems.RESIST_T3.get(),
            ModItems.LOGIC_CHIP_T3.get(),
            ModItems.MEMORY_CHIP_T3.get(),
            ModItems.COMPUTE_CHIP_T3.get(),
            ModItems.NAND_CHIP_T3.get(),
            ModItems.ARF_EXCIMER_LASER.get(), ModItems.ARF_DRY_LENS.get(),
            ModItems.NANOPOSITIONING_STAGE.get(), ModItems.ULPA_FILTER.get(),
            ModItems.PRECISION_OVERLAY_SCOPE.get(), ModItems.RESIST_T4.get(),
            ModItems.LOGIC_CHIP_T4.get(), ModItems.MEMORY_CHIP_T4.get(),
            ModItems.COMPUTE_CHIP_T4.get(), ModItems.NAND_CHIP_T4.get()
        );

        // Verify exact 49 stacks in order
        h.assertValueEqual(72, actualEntries.size(), "MAIN tab exact entry count");
        for (int i = 0; i < 72; i++) {
            Item expected = expectedItems.get(i);
            Item actual = actualEntries.get(i).getItem();
            h.assertTrue(actual == expected,
                "MAIN tab entry " + i + " is " + actual + " expected " + expected);
        }

        // Verify the three mask entries have correct designs
        MaskData d1 = actualEntries.get(32).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d1 != null && DieTypes.LOGIC.equals(d1.dieType()) && d1.tierIndex() == 1,
            "Entry 18 (mask 0) not logic T1: " + d1);
        MaskData d2 = actualEntries.get(33).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d2 != null && DieTypes.LOGIC.equals(d2.dieType()) && d2.tierIndex() == 2,
            "Entry 19 (mask 1) not logic T2: " + d2);
        MaskData d3 = actualEntries.get(34).get(ModDataComponents.MASK_DATA.get());
        h.assertTrue(d3 != null && DieTypes.MEMORY.equals(d3.dieType()) && d3.tierIndex() == 2,
            "Entry 20 (mask 2) not memory T2: " + d3);

        // Ensure all registered lithmod item IDs except EXACT test_machine fixture present
        Set<String> expectedItemIds = new HashSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (EUVCRAFTMod.MODID.equals(id.getNamespace()) && !id.getPath().equals("test_machine")) {
                expectedItemIds.add(id.getPath());
            }
        }
        Set<String> shownItemIds = new HashSet<>();
        for (ItemStack stack : actualEntries) {
            shownItemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        }
        h.assertTrue(expectedItemIds.equals(shownItemIds),
            "Actual item registry set != unique shown item IDs. Missing: " +
            new HashSet<>(expectedItemIds) {{ removeAll(shownItemIds); }} +
            ", Extra: " + new HashSet<>(shownItemIds) {{ removeAll(expectedItemIds); }});

        // Assert exactly one registered lithmod CreativeModeTab
        long lithmodTabCount = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
            .filter(key -> EUVCRAFTMod.MODID.equals(key.getNamespace())).count();
        h.assertValueEqual(1L, lithmodTabCount, "Exactly one lithmod creative tab");
        h.succeed();
    }

    private static void datapackOverride(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var repo = server.getPackRepository();
        var manager = server.getRecipeManager();

        Identifier baseResourceId = Identifier.fromNamespaceAndPath("lithmod", "recipe/crafting/blank_mask.json");
        Resource baseResource = server.getResourceManager().getResource(baseResourceId).orElseThrow(
                () -> new IllegalStateException("Base recipe resource not found: " + baseResourceId));
        JsonObject baseJson = json(baseResource);
        CraftingInput baseInput = craftingInput(baseJson);

        var baselineHolder = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, baseInput, h.getLevel()).orElseThrow();
            h.assertTrue(baselineHolder.id().identifier().equals(Identifier.parse("lithmod:crafting/blank_mask")), "Actual blank-mask lookup selected another recipe");
        int baselineCount = baselineHolder.value().assemble(baseInput).getCount();
        h.assertTrue(baselineCount >= 1 && baselineCount <= 64, "Baseline count out of range: " + baselineCount);

        int modifiedCount = (baselineCount == 1) ? 2 : 1;

        List<String> originalSelected = new ArrayList<>(repo.getSelectedIds());
        String packName = "euvcraft_m1_probe_" + UUID.randomUUID();
        Path worldDataDir = server.getWorldPath(LevelResource.DATAPACK_DIR).toAbsolutePath().normalize();
        Path packRoot = worldDataDir.resolve(packName).normalize();
        if (!packRoot.startsWith(worldDataDir) || packRoot.equals(worldDataDir)) {
            throw new IllegalStateException("Pack root escapes datapack directory");
        }

        try {
            Files.createDirectories(packRoot.resolve("data/lithmod/recipe/crafting"));

            var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            PackMetadataSection metadata = new PackMetadataSection(
                    Component.literal("EUVCRAFT M1 recipe override test"),
                    new InclusiveRange<>(format));
            JsonObject root = new JsonObject();
            root.add("pack", PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, metadata).getOrThrow());
            Files.writeString(packRoot.resolve("pack.mcmeta"), root.toString(), StandardCharsets.UTF_8);

            JsonObject modifiedJson = baseJson.deepCopy();
            JsonObject result = modifiedJson.getAsJsonObject("result");
            result.addProperty("count", modifiedCount);
            Files.writeString(
                    packRoot.resolve("data/lithmod/recipe/crafting/blank_mask.json"),
                    modifiedJson.toString(),
                    StandardCharsets.UTF_8);

            repo.reload();
            String newPackId = "file/" + packName;
            h.assertTrue(repo.isAvailable(newPackId), "Probe pack not discoverable: " + newPackId);

            List<String> enabled = new ArrayList<>(originalSelected);
            enabled.add(newPackId);
            server.reloadResources(enabled).join();

            Resource overriddenResource = server.getResourceManager().getResource(baseResourceId).orElseThrow();
            h.assertTrue(overriddenResource.sourcePackId().equals(newPackId),
                    "Override pack did not win priority for " + baseResourceId);

            var overriddenHolder = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, baseInput, h.getLevel()).orElseThrow();
            h.assertTrue(overriddenHolder.id().identifier().equals(Identifier.parse("lithmod:crafting/blank_mask")), "Actual blank-mask lookup selected another recipe");
            ItemStack overriddenOutput = overriddenHolder.value().assemble(baseInput);
            h.assertTrue(overriddenOutput.getCount() == modifiedCount,
                    "Assembled count did not reflect override: expected " + modifiedCount + " got " + overriddenOutput.getCount());
            h.assertTrue(overriddenOutput.is(baselineHolder.value().assemble(baseInput).getItem()),
                    "Override changed result item type");

        } catch (java.io.IOException e) {
            throw new IllegalStateException("Datapack probe I/O failed", e);
        } finally {
            try {
                server.reloadResources(originalSelected).join();
            } catch (Exception restoreEx) {
                throw new IllegalStateException("Failed to restore original datapack selection", restoreEx);
            }
            List<String> afterSelected = new ArrayList<>(repo.getSelectedIds());
            h.assertTrue(afterSelected.equals(originalSelected),
                    "Datapack selection not restored. Original: " + originalSelected + " After: " + afterSelected);

            var restoredHolder = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, baseInput, h.getLevel()).orElseThrow();
            h.assertTrue(restoredHolder.id().identifier().equals(Identifier.parse("lithmod:crafting/blank_mask")), "Actual blank-mask lookup selected another recipe");
            ItemStack restoredOutput = restoredHolder.value().assemble(baseInput);
            h.assertTrue(restoredOutput.getCount() == baselineCount,
                    "Restored recipe count mismatch: expected " + baselineCount + " got " + restoredOutput.getCount());
            Resource restoredResource = server.getResourceManager().getResource(baseResourceId).orElseThrow();
            h.assertTrue(!restoredResource.sourcePackId().equals("file/" + packName),
                    "Probe pack still providing recipe after restore");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------
    // 9. Assets and harvesting verification
    // ------------------------------------------------------------------

    /**
     * Enumerates actual BuiltInRegistries.ITEM and BLOCK namespace lithmod.
     * Every registered item needs /assets/lithmod/items/<id>.json and /assets/lithmod/models/item/<id>.json
     * and language entry for its descriptionId.
     * For every registered block require blockstate JSON and all its model references including
     * formed/unformed/facing variants. Recursively inspect JSON objects/arrays for model and parent
     * references; lithmod:<modelpath> must exist at /assets/lithmod/models/<modelpath>.json; vanilla
     * parents allowed. Guard visited refs to prevent cycles.
     * Every block requires its bundled loot table /data/lithmod/loot_table/blocks/<id>.json with
     * self-drop item entry, actual live state.is(BlockTags.MINEABLE_WITH_PICKAXE), correct tool
     * harvestable with new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state).
     * Assert nonempty registered inventory. All blockstate/model/loot JSONs must parse.
     */
    private static void assetsAndHarvest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Gson gson = new Gson();

        // ---- Load language file ----
        Map<String, String> lang = loadLang(gson);

        // ---- Collect registered items and blocks ----
        List<Identifier> itemIds = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (EUVCRAFTMod.MODID.equals(id.getNamespace())) {
                itemIds.add(id);
            }
        }
        h.assertTrue(!itemIds.isEmpty(), "no lithmod item is registered at all");

        List<Identifier> blockIds = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            if (EUVCRAFTMod.MODID.equals(id.getNamespace())) {
                blockIds.add(id);
            }
        }
        h.assertTrue(!blockIds.isEmpty(), "no lithmod block is registered at all");

        Set<String> visitedModels = new HashSet<>();
        // ---- Verify item assets ----
        for (Identifier id : itemIds) {
            String path = id.getPath();
            // client item definition
            h.assertTrue(hasResource("/assets/lithmod/items/" + path + ".json"),
                    id + " has no client item definition");
            // item model
            h.assertTrue(hasResource("/assets/lithmod/models/item/" + path + ".json"),
                    id + " has no item model");
            // language entry (descriptionId format: item.lithmod.<id>)
            String langKey = "item." + EUVCRAFTMod.MODID + "." + path;
            h.assertTrue(lang.containsKey(langKey), langKey + " has no language entry");
            Item registered = BuiltInRegistries.ITEM.getValue(id);
            if (registered instanceof com.euvcraft.lithmod.mc.LineComponentItem component) {
                h.assertTrue(lang.containsKey("componentKind.lithmod." + component.kind()), id + " has no component-kind translation");
                h.assertTrue(lang.containsKey("tier.lithmod.t" + component.tier()), id + " has no tier translation");
            }
            collectModelRefs(readJson(gson, "/assets/lithmod/items/" + path + ".json"), visitedModels);
            collectModelRefs(readJson(gson, "/assets/lithmod/models/item/" + path + ".json"), visitedModels);
        }

        // ---- Verify block assets ----
        for (Identifier blockId : blockIds) {
            String path = blockId.getPath();

            // blockstate JSON
            String blockstatePath = "/assets/lithmod/blockstates/" + path + ".json";
            h.assertTrue(hasResource(blockstatePath), blockId + " has no blockstate JSON");
            JsonObject blockstate = readJson(gson, blockstatePath);
            // Recursively collect model references from blockstate
            Set<String> blockModels = new HashSet<>();
            collectModelRefs(blockstate, blockModels);
            h.assertTrue(!blockModels.isEmpty(), blockId + " blockstate names no model");
            visitedModels.addAll(blockModels);
            h.assertTrue(lang.containsKey("block.lithmod." + path), blockId + " has no block language entry");

            // loot table
            String lootPath = "/data/lithmod/loot_table/blocks/" + path + ".json";
            h.assertTrue(hasResource(lootPath), blockId + " has no loot table");
            JsonObject loot = readJson(gson, lootPath);
            // Verify self-drop entry exists
            boolean hasSelfDrop = false;
            if (loot.has("pools")) {
                for (var pool : loot.getAsJsonArray("pools")) {
                    if (pool.isJsonObject()) {
                        JsonObject poolObj = pool.getAsJsonObject();
                        if (poolObj.has("entries")) {
                            for (var entry : poolObj.getAsJsonArray("entries")) {
                                if (entry.isJsonObject()) {
                                    JsonObject entryObj = entry.getAsJsonObject();
                                    if (entryObj.has("type") && entryObj.get("type").getAsString().equals("minecraft:item")
                                            && entryObj.has("name") && entryObj.get("name").getAsString().equals("lithmod:" + path)) {
                                        hasSelfDrop = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            h.assertTrue(hasSelfDrop, blockId + " loot table missing self-drop entry for lithmod:" + path);

            // block harvestability: mineable/pickaxe tag
            Block block = BuiltInRegistries.BLOCK.getValue(blockId);
            BlockState state = block.defaultBlockState();
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
                    blockId + " is not in mineable/pickaxe tag");

            // correct tool for drops with iron pickaxe
            ItemStack ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
            h.assertTrue(ironPickaxe.isCorrectToolForDrops(state),
                    blockId + " is not harvestable with iron pickaxe (isCorrectToolForDrops failed)");
        }

        // Follow item definitions and every mod model parent, without repeated traversal.
        Deque<String> pendingModels = new ArrayDeque<>(visitedModels);
        Set<String> checkedModels = new HashSet<>();
        while (!pendingModels.isEmpty()) {
            String ref = pendingModels.removeFirst();
            if (!ref.startsWith("lithmod:") || !checkedModels.add(ref)) continue;
            JsonObject model = readJson(gson, "/assets/lithmod/models/" + ref.substring("lithmod:".length()) + ".json");
            Set<String> parents = new HashSet<>();
            collectModelRefs(model, parents);
            pendingModels.addAll(parents);
        }

        // Verify nonempty registered inventory (already checked above with !itemIds.isEmpty())
        h.assertTrue(!itemIds.isEmpty(), "registered item inventory is empty");
        h.assertTrue(!blockIds.isEmpty(), "registered block inventory is empty");

        h.succeed();
    }

    private static Map<String, String> loadLang(Gson gson) {
        Map<String, String> entries = new HashMap<>();
        try (InputStream input = PresentationGameTests.class.getResourceAsStream("/assets/lithmod/lang/en_us.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled language file: /assets/lithmod/lang/en_us.json");
            }
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.entrySet()) {
                entries.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the bundled language file", e);
        }
        return entries;
    }

    private static boolean hasResource(String path) {
        return PresentationGameTests.class.getResource(path) != null;
    }

    private static JsonObject readJson(Gson gson, String path) {
        try (InputStream input = PresentationGameTests.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read/parse JSON: " + path, e);
        }
    }

    private static void collectModelRefs(JsonObject obj, Set<String> visited) {
        if (obj == null) return;
        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            var value = entry.getValue();
            if (key.equals("model") || key.equals("parent")) {
                if (value.isJsonPrimitive()) {
                    String ref = value.getAsString();
                    if (!visited.contains(ref)) {
                        visited.add(ref);
                    }
                }
            }
            if (value.isJsonObject()) {
                collectModelRefs(value.getAsJsonObject(), visited);
            } else if (value.isJsonArray()) {
                for (var elem : value.getAsJsonArray()) {
                    if (elem.isJsonObject()) {
                        collectModelRefs(elem.getAsJsonObject(), visited);
                    } else if (elem.isJsonPrimitive()) {
                        String ref = elem.getAsString();
                        if ((key.equals("model") || key.equals("parent")) && !visited.contains(ref)) {
                            visited.add(ref);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 10. Survival drops verification
    // ------------------------------------------------------------------

    /**
     * Breaks a real production CRYSTAL_PULLER and a PROCESS_CELL_CASING in survival
     * with iron pickaxe, finds exactly one own block item for each at distinct positions.
     * Reuses the exact survival player setup from MachineDropGameTests.
     */
    private static void survivalDrops(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        clearItems(h);

        // Positions for the two blocks (relative positions 1,1,1 and 4,1,1 as specified)
        BlockPos pullerPos = new BlockPos(1, 1, 1);
        BlockPos casingPos = new BlockPos(4, 1, 1);

        // Place CRYSTAL_PULLER
        Block pullerBlock = com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines.CRYSTAL_PULLER.get();
        level.setBlock(h.absolutePos(pullerPos), pullerBlock.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(level.getBlockState(h.absolutePos(pullerPos)).is(pullerBlock),
                "could not place CRYSTAL_PULLER");

        // Place PROCESS_CELL_CASING
        Block casingBlock = com.euvcraft.lithmod.mc.multiblock.ModMultiblocks.PROCESS_CELL_CASING.get();
        level.setBlock(h.absolutePos(casingPos), casingBlock.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(level.getBlockState(h.absolutePos(casingPos)).is(casingBlock),
                "could not place PROCESS_CELL_CASING");

        // Create survival player with iron pickaxe (exact setup from MachineDropGameTests)
        ServerPlayer player = createSurvivalPlayerWithIronPickaxe(level, h.absolutePos(pullerPos));

        h.assertValueEqual(GameType.SURVIVAL, player.gameMode.getGameModeForPlayer(), "Real survival game mode required");
        h.assertTrue(level.getBlockState(h.absolutePos(pullerPos)).canHarvestBlock(level, h.absolutePos(pullerPos), player), "Pickaxe cannot harvest the production machine");
        h.assertTrue(level.getBlockState(h.absolutePos(casingPos)).canHarvestBlock(level, h.absolutePos(casingPos), player), "Pickaxe cannot harvest the structure block");
        // Break CRYSTAL_PULLER
        Map<Item, Integer> beforePuller = countItems(h, h.absolutePos(pullerPos), 2.0);
        h.assertValueEqual(0, sumOf(beforePuller), "test area already held item entities before breaking CRYSTAL_PULLER");
        boolean destroyedPuller = player.gameMode.destroyBlock(h.absolutePos(pullerPos));
        h.assertTrue(destroyedPuller, "survival break did not remove CRYSTAL_PULLER");
        h.assertTrue(level.getBlockState(h.absolutePos(pullerPos)).isAir(),
                "CRYSTAL_PULLER block is still there after survival break");
        Map<Item, Integer> afterPuller = countItems(h, h.absolutePos(pullerPos), 2.0);
        h.assertValueEqual(1, countOf(afterPuller, com.euvcraft.lithmod.mc.registry.ModItems.CRYSTAL_PULLER.get()),
                "CRYSTAL_PULLER did not drop exactly one of its own block item, got " + afterPuller);
        h.assertValueEqual(1, sumOf(afterPuller),
                "CRYSTAL_PULLER dropped something other than exactly itself: " + afterPuller);

        // Move player to casing position
        player.setPos(h.absolutePos(casingPos).getX() + 0.5, h.absolutePos(casingPos).getY(), h.absolutePos(casingPos).getZ() + 0.5);

        // Break PROCESS_CELL_CASING
        Map<Item, Integer> beforeCasing = countItems(h, h.absolutePos(casingPos), 2.0);
        h.assertValueEqual(0, sumOf(beforeCasing), "test area already held item entities before breaking PROCESS_CELL_CASING");
        boolean destroyedCasing = player.gameMode.destroyBlock(h.absolutePos(casingPos));
        h.assertTrue(destroyedCasing, "survival break did not remove PROCESS_CELL_CASING");
        h.assertTrue(level.getBlockState(h.absolutePos(casingPos)).isAir(),
                "PROCESS_CELL_CASING block is still there after survival break");
        Map<Item, Integer> afterCasing = countItems(h, h.absolutePos(casingPos), 2.0);
        h.assertValueEqual(1, countOf(afterCasing, com.euvcraft.lithmod.mc.registry.ModItems.PROCESS_CELL_CASING.get()),
                "PROCESS_CELL_CASING did not drop exactly one of its own block item, got " + afterCasing);
        h.assertValueEqual(1, sumOf(afterCasing),
                "PROCESS_CELL_CASING dropped something other than exactly itself: " + afterCasing);

        // Cleanup player connection
        player.closeContainer();
        // Note: player connection cleanup follows existing lifecycle; the test framework handles the rest

        h.succeed();
    }

    private static ServerPlayer createSurvivalPlayerWithIronPickaxe(ServerLevel level, BlockPos pos) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "survival-drop-tester"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) { };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.gameMode.setLevel(level);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        return player;
    }

    private static Map<Item, Integer> countItems(GameTestHelper h, BlockPos centre, double radius) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemEntity entity : h.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.ITEM,
                new net.minecraft.world.phys.AABB(centre).inflate(radius), Entity::isAlive)) {
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

    private static void clearItems(GameTestHelper h) {
        h.killAllEntitiesOfClass(ItemEntity.class);
    }
}

