package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.MaskData;
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

/** FOUP lifecycle checks against live server registries and pattern generator production. */
public final class PresentationGameTests {
    private static final List<String> CASES = List.of("presentation_pattern_production", "presentation_obtainability");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");
    static {
        FUNCTIONS.register(CASES.get(0), () -> PresentationGameTests::patternProduction);
        FUNCTIONS.register(CASES.get(1), () -> PresentationGameTests::obtainability);
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
    private static ItemStack assemble(GameTestHelper h, Identifier sourceId, JsonObject source) {
        var manager = h.getLevel().getServer().getRecipeManager();
        var holder = manager.byKey(ResourceKey.create(Registries.RECIPE, recipeId(sourceId))).orElseThrow();
        if (holder.value() instanceof CraftingRecipe crafting) {
            var pattern = source.getAsJsonArray("pattern");
            var keys = source.getAsJsonObject("key");
            int width = pattern.get(0).getAsString().length();
            List<ItemStack> slots = new ArrayList<>();
            for (var row : pattern) for (char symbol : row.getAsString().toCharArray())
                slots.add(symbol == ' ' ? ItemStack.EMPTY : ingredient(keys.get(String.valueOf(symbol)).getAsString()));
            var input = CraftingInput.of(width, pattern.size(), slots);
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
}

