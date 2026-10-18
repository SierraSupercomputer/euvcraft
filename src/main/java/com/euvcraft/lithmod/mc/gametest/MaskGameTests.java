package com.euvcraft.lithmod.mc.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.EnergySource;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.core.MachineResult;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.MaskRules;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeSpecCodec;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorMenu;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
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
 * Game tests for the M1-06 mask chain: the Pattern Generator machine, the photomask item and
 * the {@code mask_data} component, on a real server level with the real registries and the
 * real loaded data pack.
 *
 * <p>Every machine is driven through its own {@code serverTick}, so what is measured here is
 * the same code path the game runs. There is no core-only simulation anywhere below.</p>
 *
 * <p><b>Why the recipes are a fixture.</b> The mask chain ships no
 * {@code data/lithmod/machine_recipes} file in this milestone, so a test that wanted a real
 * loaded recipe would have nothing to run. Every case that needs a recipe therefore builds
 * one as a {@link MachineRecipeSpec}, resolves it through the real
 * {@link MachineRecipeLoader#build(List, String)} and installs it as the whole live recipe
 * set for the synchronous length of the test body with {@link #withRecipes}, which puts the
 * previous registry back in a {@code finally}. Each fixture has exactly the two ingredient
 * inputs and one output the machine declares: an ordinary {@code lithmod:blank_mask} in
 * slot 0 and a distinguishing vanilla item in slot 1, so a case can name which design it is
 * exercising and the other recipes in the same list cannot match it.</p>
 *
 * <p><b>Why the configured exposure count is read at completion.</b> Every fixture recipe
 * writes a template exposure count that is deliberately different from the configured
 * {@code maskUses}, so the assertions can tell "the generator stamped the configured
 * number" apart from "the template number happened to be the same".</p>
 *
 * <p><b>No ClientLevel.</b> Nothing below constructs or loads a client level. The client
 * half of the ticker guard is asserted through the block entity's own
 * {@code serverTick} guard, and the client menu is built through the real
 * {@code MenuType} factory with a real {@link RegistryFriendlyByteBuf} rather than by
 * loading a client world. Actual client rendering stays a manual gate.</p>
 */
public final class MaskGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "mask_chain";

    /** The three designs this milestone is expected to be able to cut. */
    private static final String LOGIC_T1_MASK = "pattern_generator_cuts_a_logic_t1_mask";
    private static final String LOGIC_T2_MASK = "pattern_generator_cuts_a_logic_t2_mask";
    private static final String MEMORY_T2_MASK = "pattern_generator_cuts_a_memory_t2_mask";

    /** The exposure count is stamped when the recipe completes, not when it starts. */
    private static final String CONFIG_USES_MID_RUN =
        "pattern_generator_uses_the_config_at_completion";

    /** A die type the loaded rules do not allow at the tier the template asks for. */
    private static final String MEMORY_T1_REFUSED = "pattern_generator_refuses_a_memory_t1_design";
    private static final String COMPUTE_T3_REFUSED = "pattern_generator_refuses_a_compute_t3_design";

    /** A product that is not a usable patterned photomask: a bare mask, or not a mask at all. */
    private static final String MISSING_DESIGN_REFUSED =
        "pattern_generator_refuses_a_product_without_a_design";
    private static final String NON_MASK_REFUSED =
        "pattern_generator_refuses_a_product_that_is_not_a_mask";

    /** A paused run keeps its progress, its inputs and its energy, and resumes. */
    private static final String FULL_OUTPUT_PAUSE = "pattern_generator_pauses_on_a_full_output_slot";
    private static final String NO_ENERGY_PAUSE = "pattern_generator_pauses_without_energy";

    /** Inputs that go away mid-run drop the run before it costs or produces anything. */
    private static final String WITHDRAWN_INPUT_CANCELS =
        "pattern_generator_cancels_when_an_input_is_withdrawn";

    /** Paid progress survives NBT, at the frozen cost, and finishes in the remaining ticks. */
    private static final String SAVE_LOAD_ROUND_TRIP = "pattern_generator_save_load_round_trip";

    /** Capabilities, the block interaction, the ticker and the menu. */
    private static final String ENERGY_ONLY_CAPABILITY = "pattern_generator_exposes_energy_only";
    private static final String BLOCK_USE_OPENS_MENU = "pattern_generator_block_use_opens_the_menu";
    private static final String TICKER = "pattern_generator_ticker_is_server_side";
    private static final String MENU_LAYOUT = "pattern_generator_menu_layout_and_shift_click";

    /** The item, the component and the tooltip. */
    private static final String CATALOG_AND_TAB = "mask_catalog_and_creative_tab";
    private static final String DATA_ROUND_TRIP = "mask_data_component_round_trip";
    private static final String TOOLTIP = "mask_tooltip_lines";

    /** The registry ids the fixtures are spelled in. */
    private static final String BLANK_MASK_ID = EUVCRAFTMod.MODID + ":blank_mask";
    private static final String MASK_ID = EUVCRAFTMod.MODID + ":mask";
    private static final String MASK_DATA_ID = EUVCRAFTMod.MODID + ":mask_data";

    /** Distinguishing vanilla ingredients, one per fixture so the cases cannot collide. */
    private static final String DESIGN_REDSTONE = "minecraft:redstone";
    private static final String DESIGN_QUARTZ = "minecraft:quartz";
    private static final String DESIGN_AMETHYST = "minecraft:amethyst_shard";
    private static final String DESIGN_EMERALD = "minecraft:emerald";
    private static final String DESIGN_DIAMOND = "minecraft:diamond";
    private static final String DESIGN_GOLD = "minecraft:gold_ingot";
    private static final String DESIGN_IRON = "minecraft:iron_ingot";

    /** How long every fixture recipe takes, in ticks. Small so a test can drive it by hand. */
    private static final int RECIPE_TICKS = 20;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,
            EUVCRAFTMod.MODID);

    /** Where the machine under test is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used for the controls that make an assertion non-vacuous. */
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);

    static {
        FUNCTIONS.register(LOGIC_T1_MASK, () -> MaskGameTests::cutsLogicTierOneMask);
        FUNCTIONS.register(LOGIC_T2_MASK, () -> MaskGameTests::cutsLogicTierTwoMask);
        FUNCTIONS.register(MEMORY_T2_MASK, () -> MaskGameTests::cutsMemoryTierTwoMask);
        FUNCTIONS.register(CONFIG_USES_MID_RUN, () -> MaskGameTests::usesTheConfigAtCompletion);
        FUNCTIONS.register(MEMORY_T1_REFUSED, () -> MaskGameTests::refusesMemoryTierOne);
        FUNCTIONS.register(COMPUTE_T3_REFUSED, () -> MaskGameTests::refusesComputeTierThree);
        FUNCTIONS.register(MISSING_DESIGN_REFUSED, () -> MaskGameTests::refusesAProductWithoutADesign);
        FUNCTIONS.register(NON_MASK_REFUSED, () -> MaskGameTests::refusesAProductThatIsNotAMask);
        FUNCTIONS.register(FULL_OUTPUT_PAUSE, () -> MaskGameTests::pausesOnAFullOutputSlot);
        FUNCTIONS.register(NO_ENERGY_PAUSE, () -> MaskGameTests::pausesWithoutEnergy);
        FUNCTIONS.register(WITHDRAWN_INPUT_CANCELS, () -> MaskGameTests::cancelsWhenAnInputIsWithdrawn);
        FUNCTIONS.register(SAVE_LOAD_ROUND_TRIP, () -> MaskGameTests::saveLoadRoundTrip);
        FUNCTIONS.register(ENERGY_ONLY_CAPABILITY, () -> MaskGameTests::exposesEnergyOnly);
        FUNCTIONS.register(BLOCK_USE_OPENS_MENU, () -> MaskGameTests::blockUseOpensTheMenu);
        FUNCTIONS.register(TICKER, () -> MaskGameTests::tickerIsServerSide);
        FUNCTIONS.register(MENU_LAYOUT, () -> MaskGameTests::menuLayoutAndShiftClick);
        FUNCTIONS.register(CATALOG_AND_TAB, () -> MaskGameTests::catalogAndCreativeTab);
        FUNCTIONS.register(DATA_ROUND_TRIP, () -> MaskGameTests::dataComponentRoundTrip);
        FUNCTIONS.register(TOOLTIP, () -> MaskGameTests::tooltipLines);
    }

    private MaskGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) MaskGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, LOGIC_T1_MASK);
        register(event, data, LOGIC_T2_MASK);
        register(event, data, MEMORY_T2_MASK);
        register(event, data, CONFIG_USES_MID_RUN);
        register(event, data, MEMORY_T1_REFUSED);
        register(event, data, COMPUTE_T3_REFUSED);
        register(event, data, MISSING_DESIGN_REFUSED);
        register(event, data, NON_MASK_REFUSED);
        register(event, data, FULL_OUTPUT_PAUSE);
        register(event, data, NO_ENERGY_PAUSE);
        register(event, data, WITHDRAWN_INPUT_CANCELS);
        register(event, data, SAVE_LOAD_ROUND_TRIP);
        register(event, data, ENERGY_ONLY_CAPABILITY);
        register(event, data, BLOCK_USE_OPENS_MENU);
        register(event, data, TICKER);
        register(event, data, MENU_LAYOUT);
        register(event, data, CATALOG_AND_TAB);
        register(event, data, DATA_ROUND_TRIP);
        register(event, data, TOOLTIP);
        LOGGER.info("Registered EUVCRAFT mask chain game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1-3. A blank plate and a design become one patterned photomask
    // ------------------------------------------------------------------

    /**
     * Runs the {@code logic} at node tier 1 design end to end and reads the finished mask's
     * own public component.
     *
     * <p>The expected value is built here, out of the canonical die constant and the
     * configured exposure count, and never out of the machine: a test that read the
     * expectation back off the product would pass whatever the machine did.</p>
     */
    private static void cutsLogicTierOneMask(GameTestHelper helper) {
        cutsMask(helper, DieTypes.LOGIC, 1, DESIGN_REDSTONE);
    }

    private static void cutsLogicTierTwoMask(GameTestHelper helper) {
        cutsMask(helper, DieTypes.LOGIC, 2, DESIGN_QUARTZ);
    }

    private static void cutsMemoryTierTwoMask(GameTestHelper helper) {
        cutsMask(helper, DieTypes.MEMORY, 2, DESIGN_AMETHYST);
    }

    /** One machine, one fixture recipe, one finished mask, asserted value by value. */
    private static void cutsMask(GameTestHelper helper, String dieType, int tierIndex,
            String designIngredient) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }

        // The gate itself is checked first, so a refusal later cannot be blamed on the recipe.
        helper.assertTrue(MaskRules.canPattern(dieType, tierIndex,
                PatternGeneratorBlockEntity.MASK_SHOP_GENERATION,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix()),
            "the loaded rules refuse " + dieType + " at tier " + tierIndex
                + ", so this milestone cannot cut that mask");

        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int templateUses = configuredUses + 1;
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(designIngredient, dieType, tierIndex, templateUses,
            RECIPE_TICKS, cost);

        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, designIngredient);
            charge(helper, generator, cost * RECIPE_TICKS);

            runUntilOutput(generator, RECIPE_TICKS + 5);

            ItemStack product = generator.outputSlot();
            helper.assertFalse(product.isEmpty(),
                "the pattern generator produced nothing for " + dieType + " at tier " + tierIndex);
            helper.assertTrue(product.getItem() == ModItems.MASK.get(),
                "the product is " + product.getItem() + " rather than a lithmod mask");
            helper.assertValueEqual(1, product.getCount(),
                "the recipe produced the wrong number of masks");

            MaskData design = product.get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(design != null,
                "the finished mask carries no mask_data component, so it names no design");
            if (design != null) {
                helper.assertValueEqual(tierIndex, design.tierIndex(),
                    "the finished mask names another node tier");
                helper.assertTrue(dieType.equals(design.dieType()),
                    "the finished mask names the die type " + design.dieType()
                        + " rather than " + dieType);
                helper.assertValueEqual(configuredUses, design.usesRemaining(),
                    "the finished mask does not carry the configured exposure count");
                helper.assertTrue(design.usesRemaining() != templateUses,
                    "the finished mask kept the recipe template's exposure count of "
                        + templateUses + " instead of the configured " + configuredUses);
                helper.assertFalse(design.isSpent(),
                    "a freshly cut mask handed out an already spent one");
            }

            // The recipe's own two ingredient counts are what left the slots.
            helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).isEmpty(),
                "the blank plate was not consumed");
            helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).isEmpty(),
                "the design ingredient was not consumed");
            helper.assertFalse(generator.runner().isRunning(),
                "the recipe did not finish");
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "the machine was charged more than the recipe cost");

            LOGGER.info("[M1-06] {}: {} mask for die {} at tier {} carrying {} uses "
                    + "(the recipe template said {}), charged {} FE",
                designIngredient, product.getItem(), dieType, tierIndex, configuredUses,
                templateUses, cost * RECIPE_TICKS);
        });

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. The exposure count is stamped at completion, not at start
    // ------------------------------------------------------------------

    /**
     * A config edit made while the recipe is running is the one that applies to the mask
     * that finishes after it.
     *
     * <p>The template is written with a third number again, so the three are told apart:
     * the template says one thing, the config said another while the run started, and the
     * config says a third thing now.</p>
     */
    private static void usesTheConfigAtCompletion(GameTestHelper helper) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        FabConfig config = EUVCRAFTMod.config();
        if (config == null) {
            helper.fail("the EUVCRAFT config binding does not exist yet");
            return;
        }

        int originalUses = FabConfig.MASK_USES.get();
        int usesAtStart = 3;
        int usesAtCompletion = 11;
        try {
            FabConfig.MASK_USES.set(usesAtStart);
            config.onConfigReload();
            helper.assertValueEqual(usesAtStart, EUVCRAFTMod.settings().maskUses(),
                "the config change did not take effect before the run started");

            int cost = fixtureCost(generator);
            int templateUses = usesAtStart + 1;
            MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
                templateUses, RECIPE_TICKS, cost);

            withRecipes(List.of(fixture), () -> {
                loadFixture(helper, generator, DESIGN_REDSTONE);
                charge(helper, generator, cost * RECIPE_TICKS);

                int beforeConfigChange = RECIPE_TICKS / 2;
                runGenerator(generator, beforeConfigChange);
                helper.assertTrue(generator.runner().isRunning()
                        && generator.runner().progress() == beforeConfigChange,
                    "the test did not get the generator part-way through a recipe: "
                        + generator.runner());

                // The player edits the config while the recipe is running.
                FabConfig.MASK_USES.set(usesAtCompletion);
                config.onConfigReload();
                helper.assertValueEqual(usesAtCompletion, EUVCRAFTMod.settings().maskUses(),
                    "the mid-run config change did not take effect");
                helper.assertTrue(generator.outputSlot().isEmpty(),
                    "the mask appeared before the recipe finished");

                runUntilOutput(generator, RECIPE_TICKS + 5);

                ItemStack product = generator.outputSlot();
                helper.assertFalse(product.isEmpty(),
                    "the recipe did not finish after the config change");
                MaskData design = product.get(ModDataComponents.MASK_DATA.get());
                helper.assertTrue(design != null, "the finished mask carries no mask_data component");
                if (design != null) {
                    helper.assertValueEqual(usesAtCompletion, design.usesRemaining(),
                        "the finished mask carries " + design.usesRemaining() + " uses, not the "
                            + usesAtCompletion + " the config holds now: the exposure count was "
                            + "frozen at start rather than stamped at completion");
                    helper.assertTrue(design.usesRemaining() != usesAtStart,
                        "the finished mask kept the exposure count the config held when the run "
                            + "started");
                    helper.assertTrue(design.usesRemaining() != templateUses,
                        "the finished mask kept the recipe template's exposure count of "
                            + templateUses);
                }
                LOGGER.info("[M1-06] {}: template {} uses, config {} at start and {} at "
                        + "completion; the mask carries {}",
                    CONFIG_USES_MID_RUN, templateUses, usesAtStart, usesAtCompletion,
                    design == null ? -1 : design.usesRemaining());
            });
        } finally {
            FabConfig.MASK_USES.set(originalUses);
            config.onConfigReload();
        }
        helper.assertValueEqual(originalUses, EUVCRAFTMod.settings().maskUses(),
            "the exposure count config was not restored");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5-8. A refused design costs the player nothing
    // ------------------------------------------------------------------

    /**
     * A die type that does not exist at the tier the template asks for.
     *
     * <p>Refusal is a filter on the candidate list rather than a check inside the run, so a
     * refused recipe is never selected, never draws FE and never touches an ingredient or
     * the output slot. Every one of those is asserted here, together with the one value the
     * menu is allowed to show.</p>
     */
    private static void refusesMemoryTierOne(GameTestHelper helper) {
        helper.assertTrue(DieRulesDataLoader.getRules() != null,
            "the die rules registry has not loaded, so every design would be refused");
        helper.assertTrue(SupportLineDataLoader.getMatrix() != null,
            "the support line matrix has not loaded, so every design would be refused");
        helper.assertFalse(MaskRules.canPattern(DieTypes.MEMORY, 1,
                PatternGeneratorBlockEntity.MASK_SHOP_GENERATION,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix()),
            "the loaded rules allow a memory design at tier 1, so this case cannot prove a refusal");

        refuses(helper, DieTypes.MEMORY, 1, DESIGN_EMERALD,
            PatternGeneratorBlockEntity.STATUS_DESIGN_REFUSED);
    }

    private static void refusesComputeTierThree(GameTestHelper helper) {
        helper.assertTrue(DieRulesDataLoader.getRules() != null,
            "the die rules registry has not loaded, so every design would be refused");
        helper.assertTrue(SupportLineDataLoader.getMatrix() != null,
            "the support line matrix has not loaded, so every design would be refused");
        helper.assertFalse(MaskRules.canPattern(DieTypes.COMPUTE, 3,
                PatternGeneratorBlockEntity.MASK_SHOP_GENERATION,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix()),
            "the loaded rules allow a compute design at tier 3, so this case cannot prove a refusal");

        refuses(helper, DieTypes.COMPUTE, 3, DESIGN_DIAMOND,
            PatternGeneratorBlockEntity.STATUS_DESIGN_REFUSED);
    }

    /** A recipe whose product is a mask that carries no design at all. */
    private static void refusesAProductWithoutADesign(GameTestHelper helper) {
        PatternGeneratorBlockEntity generator = refusedMachine(helper);
        if (generator == null) {
            return;
        }
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = new MachineRecipeSpec(
            PatternGeneratorBlockEntity.MACHINE_ID,
            fixtureIngredients(DESIGN_GOLD),
            new MachineResult(MASK_ID, 1, Map.of()),
            RECIPE_TICKS, cost);

        assertRefused(helper, generator, fixture, DESIGN_GOLD, cost,
            PatternGeneratorBlockEntity.STATUS_MISSING_DESIGN,
            "a recipe whose product is a mask with no mask_data component");
    }

    /** A recipe whose product is not a photomask at all. */
    private static void refusesAProductThatIsNotAMask(GameTestHelper helper) {
        PatternGeneratorBlockEntity generator = refusedMachine(helper);
        if (generator == null) {
            return;
        }
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = new MachineRecipeSpec(
            PatternGeneratorBlockEntity.MACHINE_ID,
            fixtureIngredients(DESIGN_IRON),
            new MachineResult("minecraft:iron_ingot", 1, Map.of()),
            RECIPE_TICKS, cost);

        assertRefused(helper, generator, fixture, DESIGN_IRON, cost,
            PatternGeneratorBlockEntity.STATUS_NOT_A_MASK,
            "a recipe whose product is not a photomask");
    }

    /** Places and returns the machine the refusal cases drive; fails the test if it is absent. */
    private static @Nullable PatternGeneratorBlockEntity refusedMachine(GameTestHelper helper) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
        }
        return generator;
    }

    /** Builds and runs a die/tier refusal case. */
    private static void refuses(GameTestHelper helper, String dieType, int tierIndex,
            String designIngredient, int expectedStatus) {
        PatternGeneratorBlockEntity generator = refusedMachine(helper);
        if (generator == null) {
            return;
        }
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(designIngredient, dieType, tierIndex,
            EUVCRAFTMod.settings().maskUses() + 1, RECIPE_TICKS, cost);

        assertRefused(helper, generator, fixture, designIngredient, cost, expectedStatus,
            "a " + dieType + " design at tier " + tierIndex);
    }

    /**
     * The whole refusal contract in one place.
     *
     * <p>The machine is fully charged and loaded before anything is refused, so a mutant
     * that simply never starts would still be caught by the status assertion, and one that
     * started and then bailed would be caught by the progress, energy and output
     * assertions.</p>
     */
    private static void assertRefused(GameTestHelper helper, PatternGeneratorBlockEntity generator,
            MachineRecipeSpec fixture, String designIngredient, int cost, int expectedStatus,
            String what) {
        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, designIngredient);
            charge(helper, generator, cost * RECIPE_TICKS);
            long energyBefore = generator.buffer().getAmountAsLong();
            helper.assertTrue(energyBefore > 0,
                "the machine was not charged, so a refusal could not cost anything");

            runGenerator(generator, RECIPE_TICKS + 5);

            helper.assertFalse(generator.runner().isRunning(),
                "the machine started " + what + ", which it must refuse");
            helper.assertValueEqual(0, generator.runner().progress(),
                "the machine made progress on " + what);
            helper.assertFalse(generator.runner().isPaused(),
                "the machine reports a paused run it never started");
            helper.assertValueEqual(energyBefore, generator.buffer().getAmountAsLong(),
                "the machine charged the player for refusing " + what);
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the machine produced output from " + what);
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                "the machine consumed the blank plate for " + what);
            helper.assertValueEqual(1,
                generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                "the machine consumed the design ingredient for " + what);

            helper.assertTrue(expectedStatus != PatternGeneratorBlockEntity.STATUS_NONE,
                "the test asks for a refusal status but passes STATUS_NONE");
            helper.assertValueEqual(expectedStatus, generator.status(),
                "the machine did not publish the refusal reason for " + what
                    + "; the menu would show nothing at all");

            // The same value, read the way the menu reads it.
            ServerPlayer player = connect(helper.getLevel());
            PatternGeneratorMenu menu =
                new PatternGeneratorMenu(1, player.getInventory(), generator);
            helper.assertValueEqual(expectedStatus, menu.status(),
                "the menu does not show the machine's own refusal reason for " + what);
            player.closeContainer();

            LOGGER.info("[M1-06] {}: refused with status {}, {} FE untouched, no progress, "
                    + "no output, both inputs intact",
                what, expectedStatus, energyBefore);
        });

        // Every refusal case ends here, so the shared contract is also where the test is
        // finished. It is after withRecipes rather than before the assertions: the fixture
        // registry is only restored once the body returns, so a succeed() raised from inside
        // it could pass before the state it borrowed had been put back.
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 9. A blocked output pauses a run instead of dropping it
    // ------------------------------------------------------------------

    private static void pausesOnAFullOutputSlot(GameTestHelper helper) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(generator);
        // The template carries the configured count here, so the blocking stack is byte for
        // byte the stack the recipe would write and the block is exactly "the output is full".
        MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
            configuredUses, RECIPE_TICKS, cost);

        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, DESIGN_REDSTONE);
            charge(helper, generator, cost * RECIPE_TICKS);

            int before = RECIPE_TICKS / 2;
            runGenerator(generator, before);
            helper.assertTrue(generator.runner().isRunning() && generator.runner().progress() == before,
                "the test did not get the generator part-way through a recipe: " + generator.runner());
            long energyBefore = generator.buffer().getAmountAsLong();

            List<MachineRecipe> loaded = generator.recipes();
            helper.assertValueEqual(1, loaded.size(),
                "the fixture recipe registry did not hold exactly the one recipe under test");
            if (loaded.isEmpty()) {
                helper.fail("the fixture recipe registry is empty, so no product can be built");
                return;
            }
            ItemStack blocker = loaded.get(0).assemble();
            helper.assertTrue(blocker.getItem() == ModItems.MASK.get(),
                "the recipe under test writes " + blocker.getItem() + " rather than a photomask");
            helper.assertTrue(loaded.get(0).resultCount() == 1,
                "the recipe under test writes " + loaded.get(0).resultCount() + " products");
            generator.setSlot(PatternGeneratorBlockEntity.OUTPUT_SLOT, blocker);

            runGenerator(generator, RECIPE_TICKS + 5);

            helper.assertTrue(generator.runner().isRunning(),
                "a full output slot dropped the run instead of pausing it");
            helper.assertTrue(generator.runner().isPaused(),
                "a machine with a full output slot is not marked paused");
            helper.assertValueEqual(before, generator.runner().progress(),
                "a full output slot advanced the run");
            helper.assertValueEqual(energyBefore, generator.buffer().getAmountAsLong(),
                "a full output slot cost the player FE");
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                "a full output slot consumed the blank plate");
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                "a full output slot consumed the design ingredient");
            helper.assertValueEqual(1, generator.outputSlot().getCount(),
                "the machine overwrote the mask already in its output slot");

            // Clearing the output resumes the same run, not a new one: it still needs exactly
            // the ticks it had left, so it must not finish a tick early.
            generator.clearSlot(PatternGeneratorBlockEntity.OUTPUT_SLOT);
            int remaining = RECIPE_TICKS - before;
            runGenerator(generator, remaining - 1);
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the resumed run finished before the ticks it had left were up, so it restarted");
            helper.assertValueEqual(RECIPE_TICKS - 1, generator.runner().progress(),
                "the resumed run is not one tick away from finishing");
            generator.serverTick();
            helper.assertFalse(generator.outputSlot().isEmpty(),
                "the resumed run did not finish on the tick it was due");
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "the resumed run did not cost exactly one charge per remaining tick");

            LOGGER.info("[M1-06] {}: paused at {}/{} with the output slot full, {} FE kept, "
                    + "inputs intact, resumed and finished in exactly {} more ticks",
                FULL_OUTPUT_PAUSE, before, RECIPE_TICKS, energyBefore, remaining);
        });

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 10. No energy pauses a run; paying it again resumes it
    // ------------------------------------------------------------------

    private static void pausesWithoutEnergy(GameTestHelper helper) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
            configuredUses, RECIPE_TICKS, cost);

        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, DESIGN_REDSTONE);
            charge(helper, generator, cost * RECIPE_TICKS);

            int before = RECIPE_TICKS / 2;
            runGenerator(generator, before);
            helper.assertTrue(generator.runner().isRunning() && generator.runner().progress() == before,
                "the test did not get the generator part-way through a recipe: " + generator.runner());

            generator.setStoredEnergy(0);
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "the machine could not be emptied");

            runGenerator(generator, RECIPE_TICKS + 5);
            helper.assertTrue(generator.runner().isRunning(),
                "an empty buffer dropped the run instead of pausing it");
            helper.assertTrue(generator.runner().isPaused(),
                "a machine with an empty buffer is not marked paused");
            helper.assertValueEqual(before, generator.runner().progress(),
                "an empty buffer advanced the run");
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "an empty buffer somehow gained FE");
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                "a paused run consumed the blank plate");
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                "a paused run consumed the design ingredient");
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "a paused run produced a mask");

            // One tick's worth of FE short of a full tick changes nothing at all.
            generator.setStoredEnergy(cost - 1);
            generator.serverTick();
            helper.assertTrue(generator.runner().isPaused(),
                "cost-1 FE did not pause the machine");
            helper.assertValueEqual(before, generator.runner().progress(),
                "cost-1 FE advanced the run");
            helper.assertValueEqual((long) cost - 1, generator.buffer().getAmountAsLong(),
                "the machine spent FE it could not pay a full tick with");

            // Paying for the rest resumes the same run.
            int remaining = RECIPE_TICKS - before;
            generator.setStoredEnergy(cost * remaining);
            runGenerator(generator, remaining - 1);
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the resumed run finished before the ticks it had left were up");
            generator.serverTick();
            helper.assertFalse(generator.outputSlot().isEmpty(),
                "the resumed run did not finish on the tick it was due");
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "the resumed run did not cost exactly one charge per remaining tick");
            helper.assertTrue(generator.outputSlot().getItem() == ModItems.MASK.get(),
                "the resumed run produced " + generator.outputSlot().getItem());

            LOGGER.info("[M1-06] {}: paused at {}/{} with an empty buffer, cost-1 FE changed "
                    + "nothing, resumed and finished in exactly {} more ticks",
                NO_ENERGY_PAUSE, before, RECIPE_TICKS, remaining);
        });

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 11. Taking an ingredient away drops the run and costs nothing
    // ------------------------------------------------------------------

    private static void cancelsWhenAnInputIsWithdrawn(GameTestHelper helper) {
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
            configuredUses, RECIPE_TICKS, cost);

        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, DESIGN_REDSTONE);
            charge(helper, generator, cost * RECIPE_TICKS);

            int before = RECIPE_TICKS / 2;
            runGenerator(generator, before);
            helper.assertTrue(generator.runner().isRunning() && generator.runner().progress() == before,
                "the test did not get the generator part-way through a recipe: " + generator.runner());
            long energyBefore = generator.buffer().getAmountAsLong();

            // The player takes the design ingredient back out mid-run.
            generator.clearSlot(PatternGeneratorBlockEntity.DESIGN_SLOT);
            generator.serverTick();

            helper.assertFalse(generator.runner().isRunning(),
                "the machine kept running after its design ingredient was taken away");
            helper.assertValueEqual(0, generator.runner().progress(),
                "the machine kept its progress after its design ingredient was taken away");
            helper.assertFalse(generator.runner().isPaused(),
                "a dropped run still reports itself paused");
            helper.assertValueEqual(energyBefore, generator.buffer().getAmountAsLong(),
                "the machine charged the player for the tick it dropped the run on");
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the machine produced a mask after its design ingredient was taken away");
            helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                "the machine consumed the blank plate of the run it dropped");

            // Swapping the ingredient for another item is the same case.
            charge(helper, generator, cost * RECIPE_TICKS);
            loadFixture(helper, generator, DESIGN_REDSTONE);
            runGenerator(generator, before);
            helper.assertTrue(generator.runner().isRunning(),
                "the machine did not start a run after being reloaded");
            long energyBeforeSwap = generator.buffer().getAmountAsLong();
            generator.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT,
                new ItemStack(Items.QUARTZ, 3));
            generator.serverTick();
            helper.assertFalse(generator.runner().isRunning(),
                "the machine kept running after its design ingredient was swapped for another item");
            helper.assertValueEqual(energyBeforeSwap, generator.buffer().getAmountAsLong(),
                "the machine charged the player for the tick it dropped the swapped run on");
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the machine produced a mask from a swapped design ingredient");
            helper.assertValueEqual(3, generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                "the machine consumed the item that was swapped in");

            LOGGER.info("[M1-06] {}: run at {}/{} dropped on withdrawal ({} FE kept) and on a "
                    + "swap ({} FE kept), no output either time",
                WITHDRAWN_INPUT_CANCELS, before, RECIPE_TICKS, energyBefore, energyBeforeSwap);
        });

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 12. A component-bearing run survives NBT at the frozen cost
    // ------------------------------------------------------------------

    /**
     * Paid progress on a recipe whose product carries a component survives a real save and
     * load, and finishes at the cost it was already paying.
     *
     * <p>The recipe's product here is a mask with a {@code mask_data} component, so the
     * whole thing is a genuine {@code NbtOps} round trip: the running spec is written
     * through the recipe spec codec, not by id, and the component payload inside it has to
     * come back the same shape it went in.</p>
     *
     * <p>The machine is charged once, for the whole recipe, and never recharged: the only FE
     * the resumed run gets is the balance the save/load cycles brought back with the
     * progress. A reload that restarted the run, or that adopted a different recipe, or that
     * restored a different per-tick cost, or that dropped the stored energy, would all show
     * up as the wrong progress, a buffer that is not the one the payment left, or a buffer
     * that does not land on zero.</p>
     */
    private static void saveLoadRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
            configuredUses, RECIPE_TICKS, cost);

        withRecipes(List.of(fixture), () -> {
            loadFixture(helper, generator, DESIGN_REDSTONE);
            charge(helper, generator, cost * RECIPE_TICKS);

            int paid = RECIPE_TICKS / 2;
            runGenerator(generator, paid);
            helper.assertTrue(generator.runner().isRunning() && generator.runner().progress() == paid,
                "the test did not get the generator part-way through a recipe: " + generator.runner());
            long energyAfterPaidTicks = generator.buffer().getAmountAsLong();
            helper.assertValueEqual((long) cost * (RECIPE_TICKS - paid), energyAfterPaidTicks,
                "the first half did not cost exactly one charge per tick");

            CompoundTag tag = saveTag(level, generator);
            helper.assertTrue(tag.contains("recipe"),
                "the saved tag has no recipe node, so no progress could survive");
            helper.assertTrue(tag.contains(ContainerHelper.TAG_ITEMS),
                "the saved tag has no Items entry, so the inputs could not survive");

            // Three save/load cycles, none of them followed by a tick: a world reload can land
            // between a load and the next tick, and every one of those states has to carry the
            // progress, the frozen cost and the FE the ticks have already paid for.
            for (int cycle = 1; cycle <= 3; cycle++) {
                CompoundTag live = saveTag(level, generator);
                wipe(generator);
                loadTag(level, generator, live);
                helper.assertValueEqual(paid, generator.runner().progress(),
                    "save/load cycle " + cycle + " lost the paid progress");
                helper.assertValueEqual(RECIPE_TICKS, generator.runner().ticks(),
                    "save/load cycle " + cycle + " lost the recipe length");
                helper.assertValueEqual(cost, generator.runner().energyPerTick(),
                    "save/load cycle " + cycle + " lost the frozen per-tick cost");
                helper.assertTrue(generator.runner().hasRestoredProgress(),
                    "save/load cycle " + cycle + " left nothing to adopt");
                helper.assertFalse(generator.runner().isRunning(),
                    "save/load cycle " + cycle + " attached a recipe with no tick to do it");
                helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                    "save/load cycle " + cycle + " lost the blank plate");
                helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                    "save/load cycle " + cycle + " lost the design ingredient");
                // Nothing is ever recharged below, so the FE the paid ticks left has to come
                // back with the progress: the buffer is part of the state under test here.
                helper.assertValueEqual(energyAfterPaidTicks, generator.buffer().getAmountAsLong(),
                    "save/load cycle " + cycle + " did not restore the energy the paid ticks left");
            }

            // The buffer the three cycles restored is the only payment this run ever gets:
            // nothing is injected between the initial charge and the finish below. The first
            // tick re-attaches the run to the exact recipe it was running and continues it.
            // Adoption compares the whole decoded spec against the live one, so this also
            // pins the spec's own round trip through NBT.
            generator.serverTick();
            helper.assertTrue(generator.runner().isRunning(),
                "the restored run did not re-attach to a recipe at all");
            helper.assertTrue(generator.runner().current() != null
                    && fixture.equals(generator.runner().current().spec()),
                "the restored run re-attached to a different recipe than the one it paid for");
            helper.assertValueEqual(paid + 1, generator.runner().progress(),
                "the restored run restarted instead of continuing from its own progress");
            helper.assertValueEqual(RECIPE_TICKS, generator.runner().ticks(),
                "the restored run adopted a different recipe length");
            helper.assertValueEqual(cost, generator.runner().energyPerTick(),
                "the restored run was charged at a new rate");
            helper.assertValueEqual(energyAfterPaidTicks - cost, generator.buffer().getAmountAsLong(),
                "the first tick after the restore did not cost exactly one frozen charge");
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the restored run wrote its product on the first tick after the restore");

            // It finishes in exactly the ticks it had left, out of the balance the one
            // up-front payment left behind.
            int remaining = RECIPE_TICKS - (paid + 1);
            runGenerator(generator, remaining - 1);
            helper.assertTrue(generator.outputSlot().isEmpty(),
                "the restored run finished before the ticks it had left were up");
            generator.serverTick();
            helper.assertFalse(generator.outputSlot().isEmpty(),
                "the restored run did not finish on the tick it was due");
            helper.assertValueEqual(0L, generator.buffer().getAmountAsLong(),
                "the whole recipe did not cost exactly one charge per tick from a single payment");
            helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).isEmpty(),
                "the restored run did not consume its blank plate");
            helper.assertTrue(generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).isEmpty(),
                "the restored run did not consume its design ingredient");

            ItemStack product = generator.outputSlot();
            MaskData design = product.get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(design != null, "the restored run produced a mask with no design");
            if (design != null) {
                helper.assertTrue(DieTypes.LOGIC.equals(design.dieType()),
                    "the restored run produced a mask for " + design.dieType());
                helper.assertValueEqual(1, design.tierIndex(),
                    "the restored run produced a mask for another node tier");
                helper.assertValueEqual(configuredUses, design.usesRemaining(),
                    "the restored run did not stamp the configured exposure count");
            }

            LOGGER.info("[M1-06] {}: {} FE for {} ticks at {} FE/tick paid up front; progress {}, "
                    + "the frozen cost and the remaining FE survived three save/load cycles taken "
                    + "before any tick, and the run finished the remaining {} ticks on that same "
                    + "payment",
                SAVE_LOAD_ROUND_TRIP, cost * RECIPE_TICKS, RECIPE_TICKS, cost, paid,
                RECIPE_TICKS - paid);

            // ---- M1-06 regression: legacy reader & corrupted spec_text guard ----
            // 1) Legacy compound spec (old writer) restores progress at frozen cost, and
            //    setMachineEnergyPerTick(99) before adoption must NOT overwrite runningEnergyPerTick.
            MachineRecipeSpec plainSpec = new MachineRecipeSpec(
                    PatternGeneratorBlockEntity.MACHINE_ID,
                    List.of(MachineIngredient.item(BLANK_MASK_ID, 1),
                            MachineIngredient.item(DESIGN_REDSTONE, 1)),
                    new MachineResult("minecraft:iron_ingot", 1, Map.of()),
                    20, 21);
            MachineRecipe resolvedLegacy = MachineRecipeLoader.build(List.of(plainSpec),
                    "legacy regression").all().get(0);

            // Build runner state tag using TagValueOutput (what loadState expects at root level)
            TagValueOutput legacyOut = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, level.registryAccess());
            legacyOut.putInt("progress", 3);
            legacyOut.putInt("ticks", 20);
            legacyOut.putInt("energy_per_tick", 21);
            legacyOut.putBoolean("running", true);
            legacyOut.store("spec", MachineRecipeSpecCodec.CODEC, plainSpec);
            CompoundTag legacyRunnerTag = legacyOut.buildResult();

            MachineRecipeRunner legacyRunner = new MachineRecipeRunner(
                    PatternGeneratorBlockEntity.MACHINE_ID, 99);
            legacyRunner.loadState(TagValueInput.create(
                    ProblemReporter.DISCARDING, level.registryAccess(), legacyRunnerTag));

            helper.assertValueEqual(3, legacyRunner.progress(), "legacy load lost progress");
            helper.assertValueEqual(20, legacyRunner.ticks(), "legacy load lost ticks");
            helper.assertValueEqual(21, legacyRunner.energyPerTick(), "legacy load lost frozen cost");
            helper.assertTrue(legacyRunner.hasRestoredProgress(), "legacy load has no restored progress");

            // Setter guard: before adoption, fallback changes but running cost stays frozen
            legacyRunner.setMachineEnergyPerTick(99);
            helper.assertValueEqual(99, legacyRunner.machineEnergyPerTick(), "fallback not updated");
            helper.assertValueEqual(21, legacyRunner.energyPerTick(), "frozen cost overwritten before adoption");

            // Adoption succeeds, current.spec equals the plain spec, no restart
            helper.assertTrue(legacyRunner.adoptRestored(resolvedLegacy), "legacy adoption failed");
            helper.assertTrue(legacyRunner.current() != null
                    && plainSpec.equals(legacyRunner.current().spec()), "adopted spec mismatch");
            helper.assertValueEqual(3, legacyRunner.progress(), "adoption reset progress");
            helper.assertFalse(legacyRunner.hasRestoredProgress(), "restored flag not cleared");

            // 2) Corrupt NEW key (spec_text) must be authoritative even alongside VALID legacy compound.
            //    Each mutation: load must not throw, adoptRestored(validLegacyRecipe) FALSE,
            //    one tick with matching inputs must fail closed (no energy spent, no output, no start, no progress retained).
            List<ItemStack> matchingInputs = List.of(
                    new ItemStack(ModItems.BLANK_MASK.get(), 1),
                    new ItemStack(Items.REDSTONE, 1));
            ItemStack emptyOutput = ItemStack.EMPTY;
            int[] drawCount = {0};
            int[] writeCount = {0};
            EnergySource countingEnergy = amount -> { drawCount[0]++; return amount; };
            Consumer<ItemStack> countingWriter = s -> { writeCount[0]++; };

            String[] corruptTexts = {"{", "null", "{}", "42"};
            for (String badText : corruptTexts) {
                CompoundTag corrupt = legacyRunnerTag.copy();
                corrupt.putString("spec_text", badText);

                MachineRecipeRunner r = new MachineRecipeRunner(
                        PatternGeneratorBlockEntity.MACHINE_ID, 99);
                r.loadState(TagValueInput.create(
                        ProblemReporter.DISCARDING, level.registryAccess(), corrupt));

                helper.assertFalse(r.adoptRestored(resolvedLegacy),
                        "corrupt spec_text '" + badText + "' must not adopt");

                drawCount[0] = 0;
                writeCount[0] = 0;
                boolean completed = r.tick(List.of(resolvedLegacy), matchingInputs, emptyOutput,
                        countingWriter, countingEnergy);

                helper.assertFalse(completed, "corrupt spec_text '" + badText + "' produced output");
                helper.assertValueEqual(0, drawCount[0], "corrupt spec_text '" + badText + "' drew energy");
                helper.assertValueEqual(0, writeCount[0], "corrupt spec_text '" + badText + "' wrote output");
                helper.assertFalse(r.isRunning(), "corrupt spec_text '" + badText + "' started a run");
                helper.assertValueEqual(0, r.progress(), "corrupt spec_text '" + badText + "' retained progress");
            }

            // Both a numeric NBT tag and a compound tag are present values of the wrong type.
            for (boolean numericTag : List.of(true, false)) {
                CompoundTag corrupt = legacyRunnerTag.copy();
                if (numericTag) {
                    corrupt.putInt("spec_text", 42);
                } else {
                    corrupt.put("spec_text", new CompoundTag());
                }
                String wrongType = numericTag ? "numeric" : "compound";

                MachineRecipeRunner r = new MachineRecipeRunner(
                        PatternGeneratorBlockEntity.MACHINE_ID, 99);
                r.loadState(TagValueInput.create(
                        ProblemReporter.DISCARDING, level.registryAccess(), corrupt));

                helper.assertFalse(r.adoptRestored(resolvedLegacy),
                        wrongType + " spec_text must not adopt");

                drawCount[0] = 0;
                writeCount[0] = 0;
                boolean completed = r.tick(List.of(resolvedLegacy), matchingInputs, emptyOutput,
                        countingWriter, countingEnergy);

                helper.assertFalse(completed, wrongType + " spec_text produced output");
                helper.assertValueEqual(0, drawCount[0], wrongType + " spec_text drew energy");
                helper.assertValueEqual(0, writeCount[0], wrongType + " spec_text wrote output");
                helper.assertFalse(r.isRunning(), wrongType + " spec_text started a run");
                helper.assertValueEqual(0, r.progress(), wrongType + " spec_text retained progress");
            }

            LOGGER.info("[M1-06] {}: legacy compound spec restores at frozen cost; "
                    + "setter guard holds before adoption; corrupt spec_text fails closed",
                    SAVE_LOAD_ROUND_TRIP);
        });

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 13. Nothing but energy is reachable from outside the block
    // ------------------------------------------------------------------

    private static void exposesEnergyOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
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

        // Energy is exposed and accepts a charge from an adjacent machine, but refuses to
        // give any of it back: extracting is how a neighbouring machine would steal the
        // player's charge.
        EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.WEST);
        helper.assertTrue(energy != null, "no energy capability on the pattern generator");
        if (energy == null) {
            helper.fail("the pattern generator exposes no energy capability at all");
            return;
        }
        helper.assertTrue(energy.getCapacityAsLong() > 0,
            "the energy capability has no capacity, so the definition did not load");
        int accepted = insert(energy, 500);
        helper.assertTrue(accepted > 0, "the energy capability accepted nothing");
        helper.assertTrue(energy.getAmountAsLong() >= accepted,
            "the energy capability did not report what it took");
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

        // A hopper may neither push into the slots nor pull anything out of them.
        generator.setSlot(PatternGeneratorBlockEntity.BLANK_SLOT,
            new ItemStack(ModItems.BLANK_MASK.get()));
        generator.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT,
            new ItemStack(Items.REDSTONE));
        for (Direction side : Direction.values()) {
            ItemStack remainder = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                null, generator, new ItemStack(Items.IRON_INGOT), side);
            helper.assertValueEqual(1, remainder.getCount(),
                "a hopper pushed into the pattern generator on " + side);
        }
        helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
            "a hopper changed the generator's blank plate slot");
        helper.assertValueEqual(1, generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
            "a hopper changed the generator's design slot");

        BlockPos hopperPos = helper.absolutePos(MACHINE.below());
        level.setBlock(hopperPos, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)
            level.getBlockEntity(hopperPos);
        helper.assertFalse(
            net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(level, hopper),
            "a hopper extracted from the pattern generator");
        helper.assertTrue(hopper.isEmpty(), "a hopper received a pattern generator item");

        // A chest does expose an item handler, so the nulls above are a real answer rather
        // than the capability system being unavailable.
        helper.setBlock(SECOND, net.minecraft.world.level.block.Blocks.CHEST);
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(SECOND), Direction.NORTH) != null,
            "a chest has no item handler either, so the null assertions are vacuous");

        LOGGER.info("[M1-06] {}: no item or fluid capability on any side or on null, energy "
                + "accepts and never extracts, no hopper in or out",
            ENERGY_ONLY_CAPABILITY);

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 14. Using the block opens the menu, held item or not
    // ------------------------------------------------------------------

    /**
     * The block's use-on behaviour, driven the way a player's click drives it.
     *
     * <p>This is the path a player actually takes: {@code ServerPlayerGameMode.useItemOn}
     * with a connected {@link ServerPlayer}, not a direct menu constructor. The player is
     * put on the block first, because the vanilla reach check refuses an interaction from
     * further away than the block interaction range.</p>
     */
    private static void blockUseOpensTheMenu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        ServerPlayer player = connect(level);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // Empty hand.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult emptyHand = player.gameMode.useItemOn(player, level,
            player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(emptyHand.consumesAction(),
            "using the pattern generator with an empty hand returned " + emptyHand);
        helper.assertTrue(player.containerMenu instanceof PatternGeneratorMenu,
            "using the block with an empty hand left the player on "
                + player.containerMenu.getClass().getSimpleName());
        PatternGeneratorMenu bound = (PatternGeneratorMenu) player.containerMenu;
        helper.assertTrue(
            bound.slots.get(PatternGeneratorBlockEntity.BLANK_SLOT).container == generator,
            "the menu opened by the block interaction is not bound to the machine");
        player.closeContainer();
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
            "closing the menu did not return the player to their inventory menu");

        // The same block, with a real item in the player's hand.
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(ModItems.LOGIC_CHIP_T1.get()));
        InteractionResult heldItem = player.gameMode.useItemOn(player, level,
            player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(heldItem.consumesAction(),
            "using the pattern generator with a held item returned " + heldItem);
        helper.assertTrue(player.containerMenu instanceof PatternGeneratorMenu,
            "using the block with a held item left the player on "
                + player.containerMenu.getClass().getSimpleName());

        // The opened menu really is this machine's: its slots are bound to the block entity.
        PatternGeneratorMenu opened = (PatternGeneratorMenu) player.containerMenu;
        helper.assertTrue(opened.slots.get(PatternGeneratorBlockEntity.BLANK_SLOT).container == generator,
            "the opened menu's blank plate slot is not bound to the machine");
        helper.assertTrue(opened.stillValid(player),
            "the menu the block opened does not consider the player close enough to use it");

        LOGGER.info("[M1-06] {}: empty hand -> {}, held item -> {}; both opened the "
                + "PatternGeneratorMenu on the machine itself",
            BLOCK_USE_OPENS_MENU, emptyHand, heldItem);

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 15. The ticker, and the client-side guard it relies on
    // ------------------------------------------------------------------

    /**
     * The block hands out a ticker on a server level and that ticker drives the real machine
     * tick.
     *
     * <p>The other half of the server-only rule cannot be asked for here without a client
     * world, which a dedicated server test must not build. What can be asked for is the
     * guard the tick itself carries: {@code FabMachineBlockEntity.serverTick} returns at
     * once on a block entity with no level, so even a ticker that somehow existed on the
     * client could not spend FE, take an ingredient or write a mask. That is asserted on a
     * level-less block entity below. Actual client rendering stays a manual gate.</p>
     */
    private static void tickerIsServerSide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = MaskMachines.PATTERN_GENERATOR.get().defaultBlockState();
        helper.assertTrue(state.hasBlockEntity(),
            "the pattern generator block reports no block entity, so it has nothing to tick");

        BlockEntityTicker<PatternGeneratorBlockEntity> ticker =
            state.getTicker(level, MaskMachines.PATTERN_GENERATOR_BE.get());
        helper.assertTrue(ticker != null,
            "the pattern generator has no ticker on a server level");

        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(generator);
        MachineRecipeSpec fixture = maskRecipe(DESIGN_REDSTONE, DieTypes.LOGIC, 1,
            configuredUses, RECIPE_TICKS, cost);

        if (ticker != null) {
            withRecipes(List.of(fixture), () -> {
                loadFixture(helper, generator, DESIGN_REDSTONE);
                charge(helper, generator, cost * RECIPE_TICKS);
                long energyBefore = generator.buffer().getAmountAsLong();

                // One ticker call, exactly as the level would deliver it.
                ticker.tick(level, pos, state, generator);

                helper.assertTrue(generator.runner().isRunning(),
                    "one tick through the block's ticker did not start the recipe");
                helper.assertValueEqual(1, generator.runner().progress(),
                    "one tick through the block's ticker did not advance the recipe by one tick");
                helper.assertValueEqual(energyBefore - cost, generator.buffer().getAmountAsLong(),
                    "one tick through the block's ticker did not cost exactly one charge");
            });
        }

        // The client-side half of the rule, as far as it can be asked without a ClientLevel:
        // the very same block entity, loaded with the very same inputs and charged, must
        // treat a tick as a no-op when it is not on a level. The recipe is loaded here too,
        // so the only reason the run cannot start is the guard itself.
        BlockPos detachedPos = new BlockPos(0, 0, 0);
        PatternGeneratorBlockEntity detached = new PatternGeneratorBlockEntity(detachedPos, state);
        helper.assertTrue(detached.buffer().getCapacityAsInt() > 0,
            "the detached machine has no capacity, so the guard assertions below would be vacuous");

        withRecipes(List.of(fixture), () -> {
            detached.clearContent();
            detached.setSlot(PatternGeneratorBlockEntity.BLANK_SLOT,
                new ItemStack(ModItems.BLANK_MASK.get()));
            detached.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT,
                new ItemStack(Items.REDSTONE));
            detached.setStoredEnergy(detached.buffer().getCapacityAsInt());
            helper.assertTrue(detached.buffer().getAmountAsLong() > 0,
                "the detached machine could not be charged, so the guard assertions below "
                    + "would be vacuous");

            for (int tick = 0; tick < RECIPE_TICKS + 5; tick++) {
                detached.serverTick();
            }
            helper.assertFalse(detached.runner().isRunning(),
                "a machine with no level started a recipe");
            helper.assertValueEqual(0, detached.runner().progress(),
                "a machine with no level made progress");
            helper.assertTrue(detached.outputSlot().isEmpty(),
                "a machine with no level produced a mask");
            helper.assertValueEqual((long) detached.buffer().getCapacityAsInt(),
                detached.buffer().getAmountAsLong(),
                "a machine with no level charged the player");
            helper.assertValueEqual(1, detached.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount(),
                "a machine with no level consumed its blank plate");
            helper.assertValueEqual(1, detached.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount(),
                "a machine with no level consumed its design ingredient");
        });

        LOGGER.info("[M1-06] {}: the server ticker exists and advanced the recipe by one tick "
                + "for one charge; a level-less machine treats the same tick as a no-op",
            TICKER);

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 16. The menu: slot order, the output slot and shift-click routing
    // ------------------------------------------------------------------

    private static void menuLayoutAndShiftClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }

        ServerPlayer player = connect(level);
        PatternGeneratorMenu menu = new PatternGeneratorMenu(1, player.getInventory(), generator);

        // The registered MenuType builds the client-shaped menu; both must have the machine
        // slots first and the 36 player slots after, in the same order.
        var menuType = BuiltInRegistries.MENU.getValue(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, "pattern_generator"));
        helper.assertTrue(menuType == MaskMachines.PATTERN_GENERATOR_MENU.get(),
            "lithmod:pattern_generator menu type is not registered to the expected MenuType");
        AbstractContainerMenu clientMenu = menuType.create(2, player.getInventory(),
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess()));
        helper.assertTrue(clientMenu instanceof PatternGeneratorMenu,
            "the registered MenuType factory did not return a PatternGeneratorMenu");

        // 3 machine slots + 27 inventory + 9 hotbar.
        helper.assertValueEqual(39, menu.slots.size(), "the server menu has an unexpected slot count");
        helper.assertValueEqual(menu.slots.size(), clientMenu.slots.size(),
            "client and server menu slot layouts disagree");
        for (int index = 0; index < menu.slots.size(); index++) {
            helper.assertValueEqual(menu.slots.get(index).index,
                clientMenu.slots.get(index).index,
                "client/server slot index differs at " + index);
        }

        // The machine slots are the first three and they are bound to the machine itself.
        for (int index : new int[] {PatternGeneratorBlockEntity.BLANK_SLOT,
                PatternGeneratorBlockEntity.DESIGN_SLOT, PatternGeneratorBlockEntity.OUTPUT_SLOT}) {
            helper.assertTrue(menu.slots.get(index).container == generator,
                "machine slot " + index + " is not bound to the pattern generator");
            helper.assertTrue(clientMenu.slots.get(index).container != null,
                "client machine slot " + index + " has no container");
            helper.assertFalse(clientMenu.slots.get(index).container == generator,
                "client machine slot " + index + " is bound to the server block entity");
        }

        // Five synced values: the base class's four plus this machine's own status.
        helper.assertValueEqual(PatternGeneratorBlockEntity.DATA_COUNT
                + PatternGeneratorBlockEntity.STATUS_COUNT, 5,
            "the pattern generator is not expected to sync exactly five values");
        helper.assertValueEqual(4, PatternGeneratorMenu.STATUS_WIRE_INDEX,
            "the status value is not the fifth value on the wire");
        helper.assertValueEqual(5, dataSlotCount(menu),
            "the server menu did not register five synced values");
        helper.assertValueEqual(5, dataSlotCount(clientMenu),
            "the client menu did not register five synced values");

        // The status arrives on the client the way it does in game: the container set-data
        // packet handler calls AbstractContainerMenu.setData on the wire index, and nothing
        // else. The client menu's own holder is a plain local ContainerData, so this cannot
        // pass by reading the server's value.
        helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_NONE,
            ((PatternGeneratorMenu) clientMenu).status(),
            "a fresh client menu already shows a status before anything was sent to it");
        clientMenu.setData(PatternGeneratorMenu.STATUS_WIRE_INDEX,
            PatternGeneratorBlockEntity.STATUS_DESIGN_REFUSED);
        helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_DESIGN_REFUSED,
            ((PatternGeneratorMenu) clientMenu).status(),
            "the client menu did not take the status off the wire index");

        // With no recipe loaded and no inputs, the machine has nothing to refuse, so both
        // sides read STATUS_NONE. That is the quiet state, and it is checked before the
        // busy one so a machine that always published something would be caught.
        generator.serverTick();
        helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_NONE, generator.status(),
            "an idle machine with empty slots published a status");
        helper.assertValueEqual(generator.status(), menu.status(),
            "the server menu does not show the machine's own status");
        clientMenu.setData(PatternGeneratorMenu.STATUS_WIRE_INDEX, generator.status());
        helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_NONE,
            ((PatternGeneratorMenu) clientMenu).status(),
            "the client menu took a status off the wire index it should not have had");

        // Now a real refusal: a product that is a mask with no design on it. This is a code
        // rule, not a data rule, so it does not depend on which tiers the pack allows.
        int cost = fixtureCost(generator);
        MachineRecipeSpec refused = new MachineRecipeSpec(
            PatternGeneratorBlockEntity.MACHINE_ID,
            fixtureIngredients(DESIGN_GOLD),
            new MachineResult(MASK_ID, 1, Map.of()),
            RECIPE_TICKS, cost);
        withRecipes(List.of(refused), () -> {
            loadFixture(helper, generator, DESIGN_GOLD);
            charge(helper, generator, cost * RECIPE_TICKS);
            generator.serverTick();
            helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_MISSING_DESIGN,
                generator.status(),
                "a product with no design did not publish its refusal reason");
            helper.assertValueEqual(generator.status(), menu.status(),
                "the server menu does not show the machine's own refusal reason");
            clientMenu.setData(PatternGeneratorMenu.STATUS_WIRE_INDEX, generator.status());
            helper.assertValueEqual(PatternGeneratorBlockEntity.STATUS_MISSING_DESIGN,
                ((PatternGeneratorMenu) clientMenu).status(),
                "the machine's own refusal reason did not reach the client menu");
        });

        // The output slot takes nothing and gives everything.
        Slot serverOutput = menu.slots.get(PatternGeneratorBlockEntity.OUTPUT_SLOT);
        Slot clientOutput = clientMenu.slots.get(PatternGeneratorBlockEntity.OUTPUT_SLOT);
        ItemStack ingot = new ItemStack(Items.IRON_INGOT, 3);
        ItemStack blank = new ItemStack(ModItems.BLANK_MASK.get());
        helper.assertFalse(serverOutput.mayPlace(ingot) || clientOutput.mayPlace(ingot),
            "the output slot accepted placement on one of the two sides");
        helper.assertFalse(serverOutput.mayPlace(blank) || clientOutput.mayPlace(blank),
            "the output slot accepted a blank plate on one of the two sides");
        helper.assertTrue(serverOutput.mayPickup(player),
            "the output slot refuses pickup on the server");
        helper.assertTrue(menu.slots.get(PatternGeneratorBlockEntity.BLANK_SLOT).mayPlace(blank),
            "the blank plate slot refused a blank plate");
        helper.assertTrue(menu.slots.get(PatternGeneratorBlockEntity.DESIGN_SLOT).mayPlace(blank),
            "the design slot refused a blank plate");

        // Shift-click routing. Both menus share the inherited implementation; if the machine
        // slots were counted wrongly these moves would land in player slots or nowhere.
        generator.clearContent();
        player.getInventory().clearContent();
        int hotbar = menu.slots.size() - 9;
        player.containerMenu = menu;

        player.getInventory().setItem(0, new ItemStack(ModItems.BLANK_MASK.get(), 2));
        ItemStack moved = menu.quickMoveStack(player, hotbar);
        helper.assertFalse(moved.isEmpty(), "shift-clicking a blank plate moved nothing");
        helper.assertTrue(generator.outputSlot().isEmpty(),
            "a blank plate was shift-clicked into the output slot");
        int landedInInputs =
            generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount()
            + generator.slot(PatternGeneratorBlockEntity.DESIGN_SLOT).getCount();
        helper.assertValueEqual(2, landedInInputs,
            "the blank plates did not land in the machine's input slots");

        // An iron ingot may go into an input slot, but never into the output slot, and the
        // machine is not obliged to take it at all.
        player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        menu.quickMoveStack(player, hotbar + 1);
        helper.assertTrue(generator.outputSlot().isEmpty(),
            "an iron ingot was shift-clicked into the output slot");

        // Machine slot to player: an input and the product both move back out.
        int sourceSlot = generator.slot(PatternGeneratorBlockEntity.BLANK_SLOT).getCount() > 0
            ? PatternGeneratorBlockEntity.BLANK_SLOT
            : PatternGeneratorBlockEntity.DESIGN_SLOT;
        ItemStack back = menu.quickMoveStack(player, sourceSlot);
        helper.assertFalse(back.isEmpty(),
            "shift-clicking an ingredient out of the machine moved nothing");
        helper.assertTrue(generator.slot(sourceSlot).isEmpty(),
            "the ingredient stayed in the machine after a shift-click out");

        // The product goes out through the output slot.
        ItemStack product = new ItemStack(ModItems.MASK.get());
        product.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.LOGIC, 1, EUVCRAFTMod.settings().maskUses()));
        generator.setSlot(PatternGeneratorBlockEntity.OUTPUT_SLOT, product);
        ItemStack productOut = menu.quickMoveStack(player, PatternGeneratorBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(productOut.isEmpty(),
            "shift-clicking the product out of the machine moved nothing");
        helper.assertTrue(generator.outputSlot().isEmpty(),
            "the product stayed in the machine after a shift-click out");
        MaskData movedDesign = productOut.get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(movedDesign != null,
            "the mask that came out of the output slot lost its design component");

        LOGGER.info("[M1-06] {}: {} slots, {} synced values with the status on index {}, "
                + "output-only slot, ingredients routed in and out and the product routed out",
            MENU_LAYOUT, menu.slots.size(), dataSlotCount(menu),
            PatternGeneratorMenu.STATUS_WIRE_INDEX);

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 17. The item, its stack sizes and the photomask creative tab
    // ------------------------------------------------------------------

    private static void catalogAndCreativeTab(GameTestHelper helper) {
        int configuredUses = EUVCRAFTMod.settings().maskUses();

        // A blank plate is a bulk consumable and a photomask is not.
        ItemStack blankStack = new ItemStack(ModItems.BLANK_MASK.get(), 64);
        helper.assertValueEqual(64, blankStack.getMaxStackSize(),
            "the blank plate does not stack to 64");
        helper.assertValueEqual(64, ModItems.BLANK_MASK.get().getMaxStackSize(blankStack),
            "the blank plate item does not report a stack size of 64");
        ItemStack maskStack = new ItemStack(ModItems.MASK.get());
        helper.assertValueEqual(1, maskStack.getMaxStackSize(),
            "a photomask does not have a stack size of 1");

        // A bare mask item carries no design: there is no default component to guess from.
        helper.assertTrue(maskStack.get(ModDataComponents.MASK_DATA.get()) == null,
            "a bare mask stack already carries a mask_data component");
        helper.assertFalse(maskStack.hasNonDefault(ModDataComponents.MASK_DATA.get()),
            "the mask item declares a default mask_data component");
        helper.assertTrue(maskStack.copy().get(ModDataComponents.MASK_DATA.get()) == null,
            "copying a bare mask stack invented a design");
        ItemStack patterned = new ItemStack(ModItems.MASK.get());
        patterned.set(ModDataComponents.MASK_DATA.get(),
            new MaskData(DieTypes.MEMORY, 2, configuredUses));
        helper.assertTrue(patterned.hasNonDefault(ModDataComponents.MASK_DATA.get()),
            "a mask carrying a design reports no non-default component");
        MaskData written = patterned.get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(written != null, "the design written to a mask stack did not read back");
        if (written != null) {
            helper.assertTrue(DieTypes.MEMORY.equals(written.dieType()),
                "the design read back names " + written.dieType());
            helper.assertValueEqual(2, written.tierIndex(), "the design read back names another tier");
            helper.assertValueEqual(configuredUses, written.usesRemaining(),
                "the design read back carries another exposure count");
        }

        // The photomask tab: the blank plate, one usable sample per design this milestone
        // cuts, and the machine.
        CreativeModeTab tab = ModCreativeTabs.MASKS.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
            helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        Collection<ItemStack> displayed = tab.getDisplayItems();
        List<ItemStack> entries = new ArrayList<>(displayed);
        helper.assertValueEqual(5, entries.size(),
            "the photomask tab holds " + entries.size() + " entries rather than the five it registers");

        helper.assertTrue(entries.get(0).getItem() == ModItems.BLANK_MASK.get(),
            "the first photomask tab entry is " + entries.get(0).getItem() + " not the blank plate");
        helper.assertTrue(entries.get(0).get(ModDataComponents.MASK_DATA.get()) == null,
            "the blank plate in the tab carries a design");

        String[] sampleDieTypes = {DieTypes.LOGIC, DieTypes.LOGIC, DieTypes.MEMORY};
        int[] sampleTiers = {1, 2, 2};
        for (int sample = 0; sample < sampleDieTypes.length; sample++) {
            ItemStack entry = entries.get(1 + sample);
            helper.assertTrue(entry.getItem() == ModItems.MASK.get(),
                "tab entry " + (1 + sample) + " is " + entry.getItem() + " not a photomask");
            MaskData design = entry.get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(design != null,
                "the photomask tab hands out a mask with no design at entry " + (1 + sample));
            if (design == null) {
                continue;
            }
            helper.assertTrue(sampleDieTypes[sample].equals(design.dieType()),
                "the sample at entry " + (1 + sample) + " names the die type " + design.dieType());
            helper.assertValueEqual(sampleTiers[sample], design.tierIndex(),
                "the sample at entry " + (1 + sample) + " names another node tier");
            helper.assertValueEqual(configuredUses, design.usesRemaining(),
                "the sample at entry " + (1 + sample) + " does not follow the configured "
                    + "maskUses");
            helper.assertFalse(design.isSpent(),
                "the photomask tab hands out an already spent mask at entry " + (1 + sample));
        }
        helper.assertTrue(entries.get(4).getItem() == ModItems.PATTERN_GENERATOR.get(),
            "the last photomask tab entry is " + entries.get(4).getItem()
                + " rather than the pattern generator");

        LOGGER.info("[M1-06] {}: blank plate stacks to 64, a photomask to 1, a bare mask has no "
                + "design, and the tab hands out the plate, logic T1/T2, memory T2 at {} uses "
                + "and the generator",
            CATALOG_AND_TAB, configuredUses);

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 18. The component's persistent form and its network form
    // ------------------------------------------------------------------

    /**
     * The registered {@code mask_data} component survives both routes it has.
     *
     * <p>Storage goes through the real container helpers and a real
     * {@link TagValueOutput}/{@link TagValueInput} pair, which is what a block entity's own
     * save and load use. The network goes through a real {@link RegistryFriendlyByteBuf}
     * and {@link ItemStack#STREAM_CODEC}, which is what an inventory packet carries. If the
     * component were registered with only one of its two codecs, exactly one of these
     * would fail.</p>
     */
    private static void dataComponentRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MaskData design = new MaskData(DieTypes.MEMORY, 2, 37);
        ItemStack original = new ItemStack(ModItems.MASK.get());
        original.set(ModDataComponents.MASK_DATA.get(), design);

        // Container storage.
        NonNullList<ItemStack> chest = NonNullList.withSize(3, ItemStack.EMPTY);
        chest.set(1, original.copy());
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, chest);
        CompoundTag saved = output.buildResult();
        NonNullList<ItemStack> reloaded = NonNullList.withSize(3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValueInput.create(
            ProblemReporter.DISCARDING, level.registryAccess(), saved), reloaded);

        helper.assertFalse(reloaded.get(1).isEmpty(),
            "the container round trip lost the mask entirely");
        helper.assertTrue(ItemStack.isSameItemSameComponents(original, reloaded.get(1)),
            "the container round trip changed the stack or its components");
        assertSameDesign(helper, design, reloaded.get(1), "after the container round trip");

        // The same thing through the machine's own save and load, which is the path a world
        // reload actually takes for a stack sitting in one of its slots.
        placeGenerator(helper, MACHINE);
        PatternGeneratorBlockEntity generator = generatorAt(helper, MACHINE);
        if (generator == null) {
            helper.fail("no pattern generator block entity at " + MACHINE);
            return;
        }
        generator.setSlot(PatternGeneratorBlockEntity.OUTPUT_SLOT, original.copy());
        CompoundTag machineTag = saveTag(level, generator);
        generator.clearContent();
        loadTag(level, generator, machineTag);
        helper.assertFalse(generator.outputSlot().isEmpty(),
            "the machine's save and load lost the mask from its output slot");
        assertSameDesign(helper, design, generator.outputSlot(), "after the machine round trip");

        // The network form.
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ItemStack.STREAM_CODEC.encode(buffer, original);
        ItemStack sent = ItemStack.STREAM_CODEC.decode(buffer);
        helper.assertTrue(ItemStack.isSameItemSameComponents(original, sent),
            "the network round trip changed the stack or its components");
        assertSameDesign(helper, design, sent, "after the network round trip");
        helper.assertValueEqual(original.getCount(), sent.getCount(),
            "the network round trip changed the stack count");

        LOGGER.info("[M1-06] {}: a memory mask at tier 2 with 37 uses survived the container "
                + "helpers, the machine's own save and load and the ItemStack stream codec",
            DATA_ROUND_TRIP);

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 19. The tooltip
    // ------------------------------------------------------------------

    /**
     * A photomask tooltip is a name line and three public lines, and nothing else.
     *
     * <p>The language file is not loaded on a server, so each line is checked by its
     * translatable key and its arguments rather than by its rendered text. That is the
     * stronger check: a lot line would have to carry a second argument, and a hidden wafer
     * count would have to be a number somewhere.</p>
     *
     * <p>The tier 99 case is deliberate. {@link MaskData} does not cap the tier and the
     * tooltip must not pretend the tier was checked against a registry, so the line still
     * names the tier it was given and stays translatable all the same.</p>
     */
    private static void tooltipLines(GameTestHelper helper) {
        assertMaskTooltip(helper, DieTypes.LOGIC, 1, 12);
        assertMaskTooltip(helper, DieTypes.LOGIC, 2, 5);
        assertMaskTooltip(helper, DieTypes.MEMORY, 2, EUVCRAFTMod.settings().maskUses());
        assertMaskTooltip(helper, DieTypes.NAND, 1, 1);
        // An arbitrary positive tier the registries have never heard of.
        assertMaskTooltip(helper, DieTypes.COMPUTE, 99, 7);

        // A bare mask and a blank plate have no design to describe, so they carry the name
        // line only.
        for (ItemStack plain : List.of(new ItemStack(ModItems.MASK.get()),
                new ItemStack(ModItems.BLANK_MASK.get()))) {
            List<Component> lines = tooltipOf(plain);
            helper.assertValueEqual(1, lines.size(),
                plain.getItem() + " has " + lines.size() + " tooltip lines but no design to describe");
        }

        helper.succeed();
    }

    private static void assertMaskTooltip(GameTestHelper helper, String dieType, int tierIndex,
            int uses) {
        MaskData design = new MaskData(dieType, tierIndex, uses);
        ItemStack stack = new ItemStack(ModItems.MASK.get());
        stack.set(ModDataComponents.MASK_DATA.get(), design);

        List<Component> lines = tooltipOf(stack);
        helper.assertValueEqual(4, lines.size(),
            "a patterned mask must show a name line and exactly three public lines, got: " + lines);

        // The first line is the stack's own name and nothing else. getTooltipLines adds
        // getStyledHoverName rather than the raw hover name, so the line is compared against
        // that exact rendered component instead of a re-spelled copy of it. The styled name
        // is an empty root carrying the hover name as its sibling, so translatability is
        // checked on the hover name's own contents. Neither value is a hard-coded
        // translation key, so the check does not depend on how this version spells an item
        // name.
        Component name = lines.get(0);
        helper.assertTrue(name.equals(stack.getStyledHoverName()),
            "the first tooltip line of a mask is not its own styled name: " + name);
        helper.assertTrue(stack.getHoverName().getContents() instanceof TranslatableContents,
            "the mask name line is not translatable: " + stack.getHoverName());

        TranslatableContents dieLine = translatable(helper, lines.get(1),
            "tooltip.lithmod.mask.die", dieType + " at tier " + tierIndex);
        TranslatableContents tierLine = translatable(helper, lines.get(2),
            "tooltip.lithmod.mask.tier", dieType + " at tier " + tierIndex);
        TranslatableContents usesLine = translatable(helper, lines.get(3),
            "tooltip.lithmod.mask.uses", dieType + " at tier " + tierIndex);

        // One argument per line, and nothing on the line but that argument: a lot, a wafer or
        // a run spec would have to arrive as a second argument or an extra line.
        helper.assertValueEqual(1, dieLine.getArgs().length,
            "the die line of a " + dieType + " mask carries " + dieLine.getArgs().length
                + " arguments; a mask has no lot data to show");
        helper.assertValueEqual(1, tierLine.getArgs().length,
            "the tier line of a " + dieType + " mask carries " + tierLine.getArgs().length
                + " arguments");
        helper.assertValueEqual(1, usesLine.getArgs().length,
            "the uses line of a " + dieType + " mask carries " + usesLine.getArgs().length
                + " arguments");

        // The die label stays translatable rather than being baked into a string here.
        assertEqual(helper, "dieType.lithmod." + dieType, argumentKey(dieLine.getArgs()[0]),
            "the die line of a " + dieType + " mask at tier " + tierIndex + " names another die type");

        assertEqual(helper, tierIndex, integerArgument(helper, tierLine.getArgs()[0],
            "tooltip.lithmod.mask.tier"),
            "the tier line of a " + dieType + " mask at tier " + tierIndex + " names another tier");
        assertEqual(helper, uses, integerArgument(helper, usesLine.getArgs()[0],
            "tooltip.lithmod.mask.uses"),
            "the uses line of a " + dieType + " mask at tier " + tierIndex + " names another count");
    }

    private static TranslatableContents translatable(GameTestHelper helper, Component line,
            String expectedKey, String what) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents,
            "the " + expectedKey + " line of a " + what + " is not translatable: " + line);
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEqual(helper, expectedKey, contents.getKey(),
            "the " + what + " mask uses another tooltip key");
        return contents;
    }

    private static int integerArgument(GameTestHelper helper, Object argument, String key) {
        helper.assertTrue(argument instanceof Integer,
            "the " + key + " line carries " + argument + " rather than a whole number");
        return argument instanceof Integer value ? value : -1;
    }

    private static List<Component> tooltipOf(ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void placeGenerator(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, MaskMachines.PATTERN_GENERATOR.get());
    }

    private static @Nullable PatternGeneratorBlockEntity generatorAt(GameTestHelper helper,
            BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative))
            instanceof PatternGeneratorBlockEntity generator ? generator : null;
    }

    /**
     * The per-tick cost every fixture recipe runs at.
     *
     * <p>Deliberately not the machine's own cost and not equal to it: a recipe override that
     * differs from the definition's fallback is what makes "the frozen cost survived the
     * save" and "the mask was stamped with the configured count" falsifiable, because a
     * mutant that substituted the current machine number for either would be caught.</p>
     */
    private static int fixtureCost(PatternGeneratorBlockEntity generator) {
        return Math.max(2, generator.runner().energyPerTick() + 1);
    }

    /**
     * One fixture recipe: an ordinary blank plate in slot 0 and a distinguishing item in
     * slot 1, producing one mask carrying one design.
     *
     * <p>The template exposure count is what the recipe says the mask has; the machine is
     * what decides it actually has. Callers pass a template value that differs from the
     * configured one so the two cannot be confused.</p>
     */
    private static MachineRecipeSpec maskRecipe(String designIngredient, String dieType,
            int tierIndex, int templateUses, int ticks, int costPerTick) {
        return new MachineRecipeSpec(
            PatternGeneratorBlockEntity.MACHINE_ID,
            fixtureIngredients(designIngredient),
            new MachineResult(MASK_ID, 1,
                Map.of(MASK_DATA_ID,
                    "{\"die_type\":\"" + dieType + "\",\"tier_index\":" + tierIndex
                        + ",\"uses_remaining\":" + templateUses + "}")),
            ticks, costPerTick);
    }

    /** Exactly the two input slots the machine declares, in slot order. */
    private static List<MachineIngredient> fixtureIngredients(String designIngredient) {
        return List.of(MachineIngredient.item(BLANK_MASK_ID, 1),
            MachineIngredient.item(designIngredient, 1));
    }

    /** Puts a blank plate and one design ingredient into the machine's two input slots. */
    private static void loadFixture(GameTestHelper helper, PatternGeneratorBlockEntity generator,
            String designIngredient) {
        ItemStack ingredient = registeredItem(helper, designIngredient);
        generator.clearContent();
        generator.setSlot(PatternGeneratorBlockEntity.BLANK_SLOT,
            new ItemStack(ModItems.BLANK_MASK.get(), 1));
        generator.setSlot(PatternGeneratorBlockEntity.DESIGN_SLOT, ingredient);
    }

    private static ItemStack registeredItem(GameTestHelper helper, String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        helper.assertTrue(item != null,
            "the fixture ingredient '" + itemId + "' is not a registered item");
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1);
    }

    /**
     * Charges the machine's buffer to exactly {@code target} FE.
     *
     * <p>Insertion is done through the real capability in whole, legal steps, so the amount
     * lands on the target exactly instead of overshooting it, and the per-operation limit the
     * definition sets is honoured. A capacity smaller than the target shows up as a failed
     * assertion rather than a silently shorter run.</p>
     */
    private static void charge(GameTestHelper helper, PatternGeneratorBlockEntity generator,
            int target) {
        MachineDefinition definition = generator.definition().orElse(null);
        helper.assertTrue(definition != null,
            "the pattern generator has no machine definition loaded from data/lithmod/machines");
        if (definition == null) {
            // The assertion above has already failed the test; nothing below can run.
            return;
        }
        helper.assertTrue(definition.maxReceive() > 0,
            "the pattern generator's definition declares max_receive " + definition.maxReceive());
        for (int attempt = 0; attempt < 4096; attempt++) {
            long stored = generator.buffer().getAmountAsLong();
            if (stored >= target) {
                break;
            }
            int wanted = (int) Math.min(definition.maxReceive(), target - stored);
            if (insert(generator.energy(), wanted) <= 0) {
                break;
            }
        }
        helper.assertValueEqual((long) target, generator.buffer().getAmountAsLong(),
            "the machine could not be charged to " + target + " FE; its capacity is "
                + definition.energyCapacity());
    }

    /** Ticks the machine a fixed number of times. */
    private static void runGenerator(PatternGeneratorBlockEntity generator, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            generator.serverTick();
        }
    }

    /** Ticks the machine until its output slot has something in it, with a hard bound. */
    private static void runUntilOutput(PatternGeneratorBlockEntity generator, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (!generator.outputSlot().isEmpty()) {
                return;
            }
            generator.serverTick();
        }
    }

    /** Clears every slot, the run and the buffer, leaving the machine as a fresh one. */
    private static void wipe(PatternGeneratorBlockEntity generator) {
        generator.runner().reset();
        generator.clearContent();
        generator.setStoredEnergy(0);
    }

    /** Saves a block entity the way the server does and hands back its NBT. */
    private static CompoundTag saveTag(ServerLevel level,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveWithId(output);
        return output.buildResult();
    }

    /** Loads a block entity from an NBT the way the server does. */
    private static void loadTag(ServerLevel level,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(TagValueInput.create(
            ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    /**
     * Runs {@code body} with {@code specs} as the whole loaded machine recipe set.
     *
     * <p>{@link MachineRecipeLoader} caches what it resolved in a static field, and there
     * is no reload a game test can drive, so this writes that field and puts the previous
     * registry back in a {@code finally}. The swap is synchronous and lives only for the
     * body, so nothing after this test sees it.</p>
     */
    private static void withRecipes(List<MachineRecipeSpec> specs, Runnable body) {
        try {
            Field resolved = MachineRecipeLoader.class.getDeclaredField("resolved");
            resolved.setAccessible(true);
            MachineRecipeLoader.Registry previous =
                (MachineRecipeLoader.Registry) resolved.get(null);
            try {
                resolved.set(null, MachineRecipeLoader.build(specs, "M1-06 game test recipes"));
                body.run();
            } finally {
                resolved.set(null, previous);
            }
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new AssertionError("the machine recipe registry could not be swapped", failure);
        }
    }

    /** Inserts through a real transaction and reports how much was taken. */
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

    private static void assertSameDesign(GameTestHelper helper, MaskData expected, ItemStack stack,
            String where) {
        MaskData actual = stack.get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(actual != null, "the mask lost its mask_data component " + where);
        if (actual == null) {
            return;
        }
        helper.assertTrue(expected.dieType().equals(actual.dieType()),
            "the die type changed " + where + ": " + actual.dieType());
        helper.assertValueEqual(expected.tierIndex(), actual.tierIndex(),
            "the node tier changed " + where);
        helper.assertValueEqual(expected.usesRemaining(), actual.usesRemaining(),
            "the exposure count changed " + where);
    }

    private static String argumentKey(Object argument) {
        if (argument instanceof Component component
                && component.getContents() instanceof TranslatableContents nested) {
            return nested.getKey();
        }
        return String.valueOf(argument);
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "mask-test"), false);
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

    private static void assertEqual(GameTestHelper helper, Object expected, Object actual,
            String message) {
        helper.assertTrue(expected.equals(actual), message + ": " + expected + " -> " + actual);
    }
}
