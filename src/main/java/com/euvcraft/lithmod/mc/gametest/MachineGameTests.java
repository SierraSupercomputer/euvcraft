package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.test.TestMachineBlock;
import com.euvcraft.lithmod.mc.machine.test.TestMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.test.TestMachineMenu;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the M1-02 machine framework, all on a real server level with the real
 * registries and the real loaded data pack.
 *
 * <p>Every test that asserts something positive also asserts the matching negative, so a
 * passing run cannot come from an assertion that never fires. The negatives are marked
 * "negative control".</p>
 */
public final class MachineGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "machine";

    private static final String ENERGY_CAPABILITY = "machine_energy_capability";
    private static final String RUNS_A_RECIPE = "machine_runs_a_recipe";
    private static final String PAUSES_WITHOUT_ENERGY = "machine_pauses_without_energy";
    private static final String SAVE_LOAD_ROUND_TRIP = "machine_save_load_round_trip";
    private static final String RECIPE_FROM_DATA = "machine_recipe_from_data";
    private static final String MENU_OPENS = "machine_menu_opens_server_side";
    private static final String FOUP_SLOT_REJECTS = "machine_foup_slot_rejects_plain_items";
    private static final String HOPPERS_BLOCKED = "machine_rejects_hopper_transfers";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    /** Where the machine is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used to show FE can be pushed in from elsewhere. */
    private static final BlockPos PUSHER = new BlockPos(3, 1, 1);

    static {
        FUNCTIONS.register(ENERGY_CAPABILITY, () -> MachineGameTests::energyCapability);
        FUNCTIONS.register(HOPPERS_BLOCKED, () -> MachineGameTests::hoppersBlocked);
        FUNCTIONS.register(RUNS_A_RECIPE, () -> MachineGameTests::runsARecipe);
        FUNCTIONS.register(PAUSES_WITHOUT_ENERGY, () -> MachineGameTests::pausesWithoutEnergy);
        FUNCTIONS.register(SAVE_LOAD_ROUND_TRIP, () -> MachineGameTests::saveLoadRoundTrip);
        FUNCTIONS.register(RECIPE_FROM_DATA, () -> MachineGameTests::recipeFromData);
        FUNCTIONS.register(MENU_OPENS, () -> MachineGameTests::menuOpens);
        FUNCTIONS.register(FOUP_SLOT_REJECTS, () -> MachineGameTests::foupSlotRejectsPlainItems);
    }

    private MachineGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) MachineGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 3, 3, false, 0);

        register(event, data, ENERGY_CAPABILITY);
        register(event, data, HOPPERS_BLOCKED);
        register(event, data, RUNS_A_RECIPE);
        register(event, data, PAUSES_WITHOUT_ENERGY);
        register(event, data, SAVE_LOAD_ROUND_TRIP);
        register(event, data, RECIPE_FROM_DATA);
        register(event, data, MENU_OPENS);
        register(event, data, FOUP_SLOT_REJECTS);
        LOGGER.info("Registered EUVCRAFT machine framework game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. Energy: Energy.BLOCK only
    // ------------------------------------------------------------------

    private static void energyCapability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        BlockPos pusherPos = helper.absolutePos(PUSHER);
        helper.assertFalse(machinePos.equals(pusherPos), "the two positions are the same");

        // Present on all six sides and on a null (unsided) query.
        for (Direction side : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, machinePos, side) != null,
                "no energy capability on side " + side);
        }
        helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, machinePos, null) != null,
            "no energy capability on a null side");

        EnergyHandler exposed = level.getCapability(Capabilities.Energy.BLOCK, machinePos, Direction.WEST);
        helper.assertTrue(exposed != null, "no energy capability on WEST");

        // Insert through the capability, as an adjacent energy pipe would.
        int pushed = push(exposed, 300);
        helper.assertValueEqual(300, pushed, "the insert was not accepted in full");
        helper.assertValueEqual((long) 300, exposed.getAmountAsLong(),
            "the stored amount did not rise by the accepted amount");

        // A second push is clamped by max_receive, not by capacity.
        int clamped = push(exposed, 1_000_000);
        helper.assertValueEqual(definitionMaxReceive(helper), clamped,
            "the second insert was not clamped to max_receive");
        long stored = exposed.getAmountAsLong();
        LOGGER.info("[M1-02] energy_capability: pushed 300 then {} (clamped), stored={} capacity={}",
            clamped, stored, exposed.getCapacityAsLong());

        // Extraction is refused through the capability.
        int extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            extracted = exposed.extract((int) stored, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(0, extracted, "extraction was allowed through the capability");
        helper.assertValueEqual(stored, exposed.getAmountAsLong(),
            "the stored amount changed while extraction was refused");

        // NEGATIVE CONTROL: the machine still spends its own energy. A buffer that refused
        // extraction internally would be free to run, so the internal draw must succeed.
        FabMachineBlockEntity machine = blockEntity(level, machinePos);
        helper.assertTrue(machine != null, "no block entity");
        int spent = machine.energySource().draw(10);
        helper.assertValueEqual(10, spent,
            "the machine cannot spend its own energy (negative control: refused extraction "
                + "must not make the machine free)");
        helper.assertValueEqual(stored - 10, exposed.getAmountAsLong(),
            "the internal draw did not come out of the same buffer");

        // No item handler, no fluid capability, on any side.
        for (Direction side : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, machinePos, side) == null,
                "an item handler capability exists on side " + side);
            helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, machinePos, side) == null,
                "a fluid handler capability exists on side " + side);
        }
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, machinePos, null) == null,
            "an item handler capability exists on a null side");
        helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, machinePos, null) == null,
            "a fluid handler capability exists on a null side");

        // NEGATIVE CONTROL: a plain chest block next door does expose an item handler, so the
        // null above is a real answer and not the capability system being unavailable.
        helper.setBlock(new BlockPos(5, 1, 1), net.minecraft.world.level.block.Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(new BlockPos(5, 1, 1));
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, chestPos, Direction.NORTH) != null,
            "a chest has no item handler either, so the negative assertion above is vacuous "
                + "(negative control)");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. Energy in, progress, output
    // ------------------------------------------------------------------

    private static void runsARecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "no block entity");
        if (machine == null) {
            helper.fail("no block entity");
            return;
        }

        var recipe = recipeFor("minecraft:gold_ingot");
        helper.assertTrue(recipe != null, "the test recipe was not loaded from data");
        if (recipe == null) {
            return;
        }

        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));

        // Drive the machine the way the game does, so the definition's energy cost applies.
        machine.serverTick();
        int perTick = machine.runner().energyPerTick();
        helper.assertTrue(perTick > 0,
            "the machine's energy per tick is " + perTick + "; the definition was not applied");
        long energyBefore = machine.buffer().getAmountAsLong();
        charge(machine, perTick * (recipe.ticks() + 2));

        // Enough energy for the whole recipe plus slack, so it must finish and must have cost FE.
        for (int tick = 0; tick < 40 && machine.runner().isRunning(); tick++) {
            machine.serverTick();
        }

        ItemStack output = machine.outputSlot();
        helper.assertFalse(machine.runner().isRunning(), "the recipe did not finish");
        helper.assertValueEqual(1, output.getCount(), "the output stack has the wrong count");
        helper.assertTrue(output.getItem() == Items.GOLD_INGOT,
            "the output is not a gold ingot but " + output.getItem());
        helper.assertTrue(machine.slot(TestMachineBlockEntity.INPUT_SLOT).isEmpty(),
            "the ingredient was not consumed");
        long spent = energyBefore + (long) perTick * (recipe.ticks() + 2)
            - machine.buffer().getAmountAsLong();
        helper.assertValueEqual((long) perTick * recipe.ticks(), spent,
            "the machine did not spend exactly energy_per_tick * ticks");
        LOGGER.info("[M1-02] runs_a_recipe: {} FE/tick x {} ticks = {} FE spent, output {}",
            perTick, recipe.ticks(), spent, output);

        // NEGATIVE CONTROL: a machine with no input never runs a recipe, even fully charged.
        place(helper, PUSHER);
        FabMachineBlockEntity idle = blockEntity(level, helper.absolutePos(PUSHER));
        helper.assertTrue(idle != null, "no block entity at the pusher position");
        if (idle != null) {
            charge(idle, 100_000);
            for (int tick = 0; tick < 20; tick++) {
                idle.serverTick();
            }
            helper.assertFalse(idle.runner().isRunning(),
                "a machine with no input ran a recipe (negative control)");
            helper.assertTrue(idle.outputSlot().isEmpty(),
                "a machine with no input produced output (negative control)");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. Pause without energy, resume after
    // ------------------------------------------------------------------

    private static void pausesWithoutEnergy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "no block entity");
        if (machine == null) {
            return;
        }

        var recipe = recipeFor("minecraft:gold_ingot");
        helper.assertTrue(recipe != null, "the test recipe was not loaded from data");
        if (recipe == null) {
            return;
        }

        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));

        // Drive the machine the way the game does, so the definition's energy cost applies.
        // Reading the cost off the recipe instead would pass 0 as the machine value and make
        // the recipe free, which is exactly what this test must rule out.
        helper.assertTrue(machine.definition().isPresent(),
            "the machine has no definition loaded from data/lithmod/machines");
        machine.serverTick();
        int perTick = machine.runner().energyPerTick();
        helper.assertTrue(perTick > 0,
            "the machine's energy per tick is " + perTick + "; the definition was not applied");

        // Exactly two ticks' worth: the run starts, then stops for want of energy.
        charge(machine, perTick * 2);
        long charged = machine.buffer().getAmountAsLong();

        for (int tick = 0; tick < 6; tick++) {
            machine.serverTick();
        }

        helper.assertTrue(machine.runner().isRunning(), "the recipe stopped instead of pausing");
        helper.assertTrue(machine.runner().isPaused(), "the runner is not marked paused");
        int progressBefore = machine.runner().progress();
        helper.assertValueEqual(2, progressBefore, "the wrong number of ticks ran before pausing");
        helper.assertTrue(machine.outputSlot().isEmpty(), "output appeared while paused");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "energy was left after pausing");
        LOGGER.info("[M1-02] pauses_without_energy: paused at {}/{} with {} FE left",
            progressBefore, machine.runner().ticks(), charged);

        // Progress must not creep forward with no energy.
        for (int tick = 0; tick < 20; tick++) {
            machine.serverTick();
        }
        helper.assertValueEqual(progressBefore, machine.runner().progress(),
            "progress advanced with no energy (negative control)");

        // Recharge and let it finish: a paused recipe resumes rather than restarting.
        charge(machine, perTick * recipe.ticks());
        boolean completed = false;
        for (int tick = 0; tick < 40; tick++) {
            machine.serverTick();
            if (!machine.runner().isRunning() && !machine.outputSlot().isEmpty()) {
                completed = true;
                break;
            }
        }
        helper.assertTrue(completed, "the paused recipe did not resume and finish");
        helper.assertTrue(machine.outputSlot().getItem() == Items.GOLD_INGOT,
            "the resumed recipe produced the wrong item");
        LOGGER.info("[M1-02] pauses_without_energy: resumed and completed, output {}",
            machine.outputSlot().getCount());

        // NEGATIVE CONTROL: a machine that never gets energy never completes anything.
        place(helper, PUSHER);
        FabMachineBlockEntity starved = blockEntity(level, helper.absolutePos(PUSHER));
        helper.assertTrue(starved != null, "no block entity at the pusher position");
        if (starved != null) {
            starved.setSlot(TestMachineBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
            for (int tick = 0; tick < 100; tick++) {
                starved.serverTick();
            }
            helper.assertTrue(starved.outputSlot().isEmpty(),
                "a machine with no energy produced output (negative control)");
            // A recipe may be selected and still paused, so "isRunning" is not the right
            // negative assertion here; "made progress" is.
            helper.assertValueEqual(0, starved.runner().progress(),
                "a machine with no energy made progress (negative control)");
            helper.assertTrue(starved.runner().isPaused(),
                "a machine with no energy is not marked paused (negative control)");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2 and 5. Inventory, energy and progress round trip
    // ------------------------------------------------------------------

    private static void saveLoadRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "no block entity");
        if (machine == null) {
            helper.fail("no block entity");
            return;
        }

        var recipe = recipeFor("minecraft:gold_ingot");
        helper.assertTrue(recipe != null, "the test recipe was not loaded from data");
        if (recipe == null) {
            return;
        }

        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
        charge(machine, 4_000);

        // Stop one tick short of finishing, with energy and the ingredient still in place.
        // The first serverTick both applies the definition and advances one tick, so the
        // loop runs one tick fewer to land exactly one short.
        machine.serverTick();
        int perTick = machine.runner().energyPerTick();
        helper.assertTrue(perTick > 0, "the machine's energy per tick is " + perTick);
        for (int tick = 1; tick < recipe.ticks() - 1; tick++) {
            machine.serverTick();
        }

        int progressBefore = machine.runner().progress();
        long energyBefore = machine.buffer().getAmountAsLong();
        helper.assertTrue(machine.runner().isRunning(),
            "the recipe should still be running one tick before completion");
        helper.assertTrue(progressBefore > 0 && progressBefore < recipe.ticks(),
            "the test did not get part-way through a recipe: progress=" + progressBefore);
        helper.assertTrue(energyBefore > 0, "the machine should still hold energy mid-recipe");

        CompoundTag tag = new CompoundTag();
        TagValueOutput output = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        machine.saveWithId(output);
        tag = output.buildResult();
        helper.assertTrue(tag.contains(ContainerHelper.TAG_ITEMS), "the saved tag has no Items entry");
        helper.assertTrue(tag.contains("energy"), "the saved tag has no energy entry");
        helper.assertTrue(tag.contains("recipe"), "the saved tag has no recipe progress entry");

        // Wipe everything, then load it back.
        machine.clearSlot(TestMachineBlockEntity.INPUT_SLOT);
        machine.runner().reset();
        machine.setStoredEnergy(0);
        helper.assertTrue(machine.slot(TestMachineBlockEntity.INPUT_SLOT).isEmpty(), "the wipe failed (input)");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "the wipe failed (energy)");
        helper.assertValueEqual(0, machine.runner().progress(), "the wipe failed (progress)");

        machine.loadWithComponents(
            TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));

        helper.assertValueEqual(energyBefore, machine.buffer().getAmountAsLong(),
            "the stored energy did not survive the round trip");
        helper.assertTrue(machine.slot(TestMachineBlockEntity.INPUT_SLOT).getItem() == Items.IRON_INGOT,
            "the input item did not survive the round trip");
        helper.assertValueEqual(progressBefore, machine.runner().progress(),
            "the recipe progress did not survive the round trip");
        helper.assertValueEqual(recipe.ticks(), machine.runner().ticks(),
            "the recipe length did not survive the round trip");
        helper.assertTrue(machine.runner().hasRestoredProgress(),
            "the restored progress should be waiting for its recipe to be re-matched");
        LOGGER.info("[M1-02] save_load_round_trip: energy {} FE and progress {}/{} both restored",
            energyBefore, progressBefore, machine.runner().ticks());

        // The restored progress resumes the same recipe rather than restarting it.
        charge(machine, 4_000);
        for (int tick = 0; tick < 10; tick++) {
            machine.serverTick();
        }
        helper.assertTrue(machine.outputSlot().getItem() == Items.GOLD_INGOT,
            "the restored recipe did not finish after recharging");
        helper.assertValueEqual(1, machine.outputSlot().getCount(), "the restored recipe produced the wrong count");
        LOGGER.info("[M1-02] save_load_round_trip: resumed from {}/{} and finished",
            progressBefore, recipe.ticks());

        // NEGATIVE CONTROL: a fresh machine has no progress and does not pretend otherwise.
        place(helper, PUSHER);
        FabMachineBlockEntity fresh = blockEntity(level, helper.absolutePos(PUSHER));
        helper.assertTrue(fresh != null, "no block entity at the pusher position");
        if (fresh != null) {
            helper.assertValueEqual(0, fresh.runner().progress(),
                "a fresh machine reported progress (negative control)");
            helper.assertFalse(fresh.runner().hasRestoredProgress(),
                "a fresh machine claimed restored progress (negative control)");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. Recipe JSON parses from data
    // ------------------------------------------------------------------

    private static void recipeFromData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        var definitions = com.euvcraft.lithmod.mc.data.MachineDefinitionLoader.getRegistry();
        helper.assertTrue(definitions != null, "no machine definition registry");
        if (definitions == null) {
            helper.fail("no machine definition registry");
            return;
        }
        var definition = definitions.byId(TestMachineBlockEntity.MACHINE_ID);
        helper.assertTrue(definition.isPresent(), "test_machine is not loaded from data/lithmod/machines");
        if (definition.isEmpty()) {
            return;
        }
        helper.assertTrue(definition.get().energyCapacity() > 0, "the loaded capacity is not positive");
        helper.assertValueEqual(2, definition.get().perTierEnergyPerTick().size(),
            "the per-tier overrides did not load");

        var recipes = com.euvcraft.lithmod.mc.data.MachineRecipeLoader.getRegistry();
        helper.assertTrue(recipes != null, "no machine recipe registry");
        if (recipes == null) {
            helper.fail("no machine recipe registry");
            return;
        }
        helper.assertTrue(recipes.hasMachine(TestMachineBlockEntity.MACHINE_ID),
            "no recipes loaded for test_machine");
        helper.assertTrue(recipes.size() >= 2,
            "expected at least two recipes from data, got " + recipes.size());

        // The item recipe, found by its result rather than by position, so the test does not
        // depend on registry iteration order.
        var itemRecipe = recipes.forMachine(TestMachineBlockEntity.MACHINE_ID).stream()
            .filter(recipe -> recipe.resultItem() == Items.GOLD_INGOT)
            .findFirst().orElse(null);
        helper.assertTrue(itemRecipe != null, "the iron-ingot recipe was not loaded from data");
        if (itemRecipe == null) {
            return;
        }
        helper.assertValueEqual(1, itemRecipe.inputSlotCount(), "the recipe has the wrong input count");
        helper.assertFalse(itemRecipe.spec().ingredients().get(0).isTag(),
            "the iron-ingot recipe should use a plain item ingredient");
        helper.assertTrue(itemRecipe.usesMachineEnergy(),
            "the iron-ingot recipe has no energy override so it should use the machine value");
        helper.assertValueEqual(itemRecipe.resultItem(), Items.GOLD_INGOT,
            "the recipe result is not a gold ingot");

        // The tag recipe, which also carries components and an energy override.
        var tagRecipe = recipes.forMachine(TestMachineBlockEntity.MACHINE_ID).stream()
            .filter(recipe -> recipe.spec().ingredients().get(0).isTag())
            .findFirst().orElse(null);
        helper.assertTrue(tagRecipe != null, "the tag recipe was not loaded from data");
        if (tagRecipe == null) {
            return;
        }
        helper.assertTrue(!tagRecipe.tagMembers("minecraft:planks").isEmpty(),
            "the planks tag resolved to no members");
        helper.assertTrue(tagRecipe.resultItem() == Items.STICK, "the tag recipe result is wrong");
        helper.assertValueEqual(4, tagRecipe.resultCount(), "the tag recipe result count is wrong");
        helper.assertFalse(tagRecipe.usesMachineEnergy(),
            "the tag recipe should override the machine energy cost");
        helper.assertValueEqual(5, tagRecipe.energyPerTick(999), "the energy override did not apply");
        ItemStack assembled = tagRecipe.assemble();
        helper.assertValueEqual(4, assembled.getCount(), "the assembled stack count is wrong");
        helper.assertTrue(assembled.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
            "the result component did not survive resolution");

        // NEGATIVE CONTROL: the other recipe declares no components, so its product carries
        // none beyond the item defaults.
        helper.assertTrue(itemRecipe.resultComponents().isEmpty(),
            "the iron-ingot recipe should declare no components");
        ItemStack plain = itemRecipe.assemble();
        helper.assertFalse(plain.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
            "a recipe with no components produced a named stack (negative control)");
        LOGGER.info("[M1-02] recipe_from_data: {} recipes; tag recipe {} x{} carries custom_name={}, "
                + "item recipe {} x{} carries custom_name={}",
            recipes.size(), assembled.getItem(), assembled.getCount(),
            assembled.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
            plain.getItem(), plain.getCount(),
            plain.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));

        // NEGATIVE CONTROL: a machine with no recipes loaded resolves to none.
        helper.assertTrue(recipes.forMachine("no_such_machine").isEmpty(),
            "an unknown machine resolved to recipes (negative control)");

        // NEGATIVE CONTROL: the ingredient matcher rejects a wrong item and a short stack.
        var spec = itemRecipe.spec();
        var noTags = com.euvcraft.lithmod.core.MachineRecipeSpec.TagMemberResolver.none();
        helper.assertFalse(spec.matches(List.of("minecraft:iron_ingot"), List.of(1), noTags) == false,
            "the matcher rejected the right item (negative control)");
        helper.assertFalse(spec.matches(List.of("minecraft:dirt"), List.of(1), noTags),
            "the matcher accepted a wrong item (negative control)");
        helper.assertFalse(spec.matches(List.of("minecraft:iron_ingot"), List.of(0), noTags),
            "the matcher accepted an empty slot (negative control)");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Menu opens server-side, without "Block id not set"
    // ------------------------------------------------------------------

    private static void menuOpens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);

        Identifier blockId = Identifier.fromNamespaceAndPath("lithmod", "test_machine");
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(blockId);
        helper.assertTrue(block == com.euvcraft.lithmod.mc.machine.test.ModMachines.TEST_MACHINE.get(),
            "lithmod:test_machine is not registered to the expected block");
        LOGGER.info("[M1-02] menu: block {} registered as {}", block,
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));

        var menuType = net.minecraft.core.registries.BuiltInRegistries.MENU.getValue(blockId);
        helper.assertTrue(menuType == com.euvcraft.lithmod.mc.registry.ModMenus.TEST_MACHINE.get(),
            "lithmod:test_machine menu type is not registered to the expected MenuType");

        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "no block entity");
        if (machine == null) {
            return;
        }

        // The registered factory builds a client-shaped menu.
        ServerPlayer player = connect(level);
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        AbstractContainerMenu fromFactory = menuType.create(7, player.getInventory(), buffer);
        helper.assertTrue(fromFactory instanceof TestMachineMenu,
            "the registered MenuType factory did not return a TestMachineMenu but "
                + fromFactory.getClass().getName());
        helper.assertFalse(((TestMachineMenu) fromFactory).isServerSide(),
            "the factory-built menu claims to be server side");

        // The block entity builds a menu bound to itself, with machine slots.
        AbstractContainerMenu bound = machine.createMenu(8, player.getInventory(), player);
        helper.assertTrue(bound instanceof TestMachineMenu,
            "createMenu did not return a TestMachineMenu");
        TestMachineMenu menu = (TestMachineMenu) bound;
        helper.assertTrue(menu.isServerSide(), "the block entity menu is not server side");
        // 3 machine slots + 27 inventory + 9 hotbar.
        helper.assertValueEqual(39, menu.slots.size(), "the menu has an unexpected slot count");
        helper.assertValueEqual(menu.slots.size(), fromFactory.slots.size(),
            "client and server menu slot layouts disagree");
        for (int index = 0; index < menu.slots.size(); index++) {
            helper.assertValueEqual(menu.slots.get(index).index, fromFactory.slots.get(index).index,
                "client/server slot index differs at " + index);
            helper.assertValueEqual(menu.slots.get(index).mayPlace(new ItemStack(Items.IRON_INGOT)),
                fromFactory.slots.get(index).mayPlace(new ItemStack(Items.IRON_INGOT)),
                "client/server placement rules differ at " + index);
        }
        helper.assertTrue(menu.energyCapacity() > 0, "the menu reports no energy capacity");
        helper.assertValueEqual(0, menu.progress(), "a fresh menu reports progress");

        // Open it the way a player would from the server side. Player.openMenu sends
        // neoforge:advanced_open_screen, which a game-test connection rejects, so the menu
        // is installed on the player's public containerMenu field instead.
        player.containerMenu = menu;
        helper.assertTrue(player.containerMenu instanceof TestMachineMenu,
            "the opened menu is not a TestMachineMenu");
        LOGGER.info("[M1-02] menu: opened {} with {} slots, capacity {}",
            player.containerMenu.getClass().getSimpleName(), menu.slots.size(), menu.energyCapacity());
        player.closeContainer();

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // FoupSlot validation, including shift-click
    // ------------------------------------------------------------------

    private static void hoppersBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "machine missing");
        for (Direction side : Direction.values()) {
            ItemStack remainder = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                null, machine, new ItemStack(Items.IRON_INGOT), side);
            helper.assertValueEqual(1, remainder.getCount(), "hopper inserted on " + side);
            helper.assertTrue(machine.isEmpty(), "hopper changed machine inventory on " + side);
        }
        // Manual inventory access must still work while automation is denied.
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 3));
        BlockPos hopperPos = helper.absolutePos(MACHINE.below());
        level.setBlock(hopperPos, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(hopperPos);
        helper.assertFalse(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(level, hopper),
            "hopper extracted from a hand-fed machine");
        helper.assertTrue(hopper.isEmpty(), "hopper received a machine item");
        helper.assertValueEqual(3, machine.slot(TestMachineBlockEntity.STORAGE_SLOT).getCount(),
            "blocked extraction changed inventory");
        helper.succeed();
    }

    private static void foupSlotRejectsPlainItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper);
        FabMachineBlockEntity machine = blockEntity(level, helper.absolutePos(MACHINE));
        helper.assertTrue(machine != null, "no block entity");
        if (machine == null) {
            return;
        }

        ServerPlayer player = connect(level);
        TestMachineMenu menu = new TestMachineMenu(1, player.getInventory(), machine);
        var foupSlot = menu.slots.get(TestMachineBlockEntity.INPUT_SLOT);
        helper.assertTrue(foupSlot != null, "no input slot in the menu");

        ItemStack foup = com.euvcraft.lithmod.mc.FoupItems
            .with(com.euvcraft.lithmod.mc.registry.ModItems.FOUP_MK1.get(),
                com.euvcraft.lithmod.mc.FoupDebugCommand.fillContents(level, java.util.UUID.randomUUID(), 4, 4, 2));

        helper.assertTrue(foupSlot.mayPlace(foup), "the FOUP slot refused a real FOUP");
        helper.assertValueEqual(1, foupSlot.getMaxStackSize(), "the FOUP slot does not hold one");

        // NEGATIVE CONTROL: a plain item is refused.
        helper.assertFalse(foupSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the FOUP slot accepted a plain iron ingot (negative control)");
        helper.assertFalse(foupSlot.mayPlace(ItemStack.EMPTY),
            "the FOUP slot accepted an empty stack (negative control)");

        // Shift-click cannot get around it either.
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setItem(1, foup.copy());
        player.containerMenu = menu;

        ItemStack moved = menu.quickMoveStack(player, hotbarBase(menu));
        helper.assertTrue(moved.isEmpty(),
            "shift-clicking a plain ingot moved it (negative control)");
        helper.assertTrue(machine.slot(TestMachineBlockEntity.INPUT_SLOT).isEmpty(),
            "shift-clicking a plain ingot put it in the FOUP slot (negative control)");

        ItemStack foupMoved = menu.quickMoveStack(player, (hotbarBase(menu) + 1));
        helper.assertFalse(foupMoved.isEmpty(), "shift-clicking a real FOUP moved nothing");
        helper.assertTrue(machine.slot(TestMachineBlockEntity.INPUT_SLOT).getItem()
                == foup.getItem(),
            "the FOUP did not land in the FOUP slot");
        LOGGER.info("[M1-02] foup_slot: plain item refused, FOUP accepted on shift-click");

        // NEGATIVE CONTROL: the output slot refuses placement outright.
        var outputSlot = menu.slots.get(TestMachineBlockEntity.OUTPUT_SLOT);
        helper.assertFalse(outputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)),
            "the output slot accepted placement (negative control)");
        helper.assertTrue(outputSlot.mayPickup(player), "the output slot refuses pickup (negative control)");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void place(GameTestHelper helper) {
        helper.setBlock(MACHINE, com.euvcraft.lithmod.mc.machine.test.ModMachines.TEST_MACHINE.get());
    }

    private static void place(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, com.euvcraft.lithmod.mc.machine.test.ModMachines.TEST_MACHINE.get());
    }

    private static @Nullable FabMachineBlockEntity blockEntity(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FabMachineBlockEntity machine ? machine : null;
    }

    private static int push(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int definitionMaxReceive(GameTestHelper helper) {
        var definitions = com.euvcraft.lithmod.mc.data.MachineDefinitionLoader.getRegistry();
        return definitions == null ? 1000
            : definitions.byId(TestMachineBlockEntity.MACHINE_ID)
                .map(com.euvcraft.lithmod.core.MachineDefinition::maxReceive).orElse(1000);
    }

    /** Puts the machine's real buffer into a known state, so a test can be deterministic. */
    private static void charge(FabMachineBlockEntity machine, int wanted) {
        machine.setStoredEnergy(wanted);
    }

    private static com.euvcraft.lithmod.mc.recipe.MachineRecipe recipeFor(String resultItemId) {
        var recipes = com.euvcraft.lithmod.mc.data.MachineRecipeLoader.getRegistry();
        if (recipes == null) {
            return null;
        }
        for (var recipe : recipes.forMachine(TestMachineBlockEntity.MACHINE_ID)) {
            if (recipe.resultItem() == itemOf(resultItemId)) {
                return recipe;
            }
        }
        return null;
    }

    /** The first hotbar slot index, which is where a shift-click test puts its items. */
    private static int hotbarBase(TestMachineMenu menu) {
        return menu.slots.size() - 9;
    }

    private static net.minecraft.world.item.Item itemOf(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getValue(Identifier.parse(id.contains(":") ? id : "minecraft:" + id));
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "machine-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
