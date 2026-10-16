package com.euvcraft.lithmod.mc.spike;

import java.util.UUID;
import java.util.function.Consumer;

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

/**
 * Game tests for the M1-02s energy and registration spike.
 *
 * <p>Each test runs on a real server level with the real registries:</p>
 * <ul>
 *   <li>{@code spike_energy_capability} - a machine at one position accepts FE pushed from
 *       another position through a {@code Level.getCapability} lookup, the stored amount rises
 *       by exactly the amount accepted, extraction is refused, and no item or fluid capability
 *       exists on any of the six sides.</li>
 *   <li>{@code spike_save_load_round_trip} - stored FE and the slot contents survive a
 *       {@code saveAdditional} / {@code loadAdditional} round trip through the 26.3 value
 *       input and output API.</li>
 *   <li>{@code spike_menu_opens_server_side} - the block registers and stays registered, and
 *       its menu type opens server-side.</li>
 * </ul>
 */
public final class SpikeGameTests {

    private static final Logger LOGGER = com.euvcraft.lithmod.mc.EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "spike";

    private static final String ENERGY_CAPABILITY = "spike_energy_capability";
    private static final String SAVE_LOAD_ROUND_TRIP = "spike_save_load_round_trip";
    private static final String MENU_OPENS = "spike_menu_opens_server_side";

    /** Test bodies, registered under the ids {@code RegisterGameTestsEvent} points at. */
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register(ENERGY_CAPABILITY, () -> SpikeGameTests::energyCapability);
        FUNCTIONS.register(SAVE_LOAD_ROUND_TRIP, () -> SpikeGameTests::saveLoadRoundTrip);
        FUNCTIONS.register(MENU_OPENS, () -> SpikeGameTests::menuOpensServerSide);
    }

    /** The machine sits at the origin of the test structure. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used to show a push can come from elsewhere. */
    private static final BlockPos PUSHER = new BlockPos(3, 1, 1);

    private static final int PUSH_AMOUNT = 300;

    private SpikeGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) SpikeGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, ENERGY_CAPABILITY);
        register(event, data, SAVE_LOAD_ROUND_TRIP);
        register(event, data, MENU_OPENS);
        LOGGER.info("Registered EUVCRAFT M1-02s spike game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * The energy capability is reachable from every side, accepts energy pushed from another
     * position, refuses to give any back, and there is no item or fluid capability anywhere.
     */
    private static void energyCapability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper, MACHINE);

        // The block is registered under the expected id and did not abort registration.
        helper.assertBlockPresent(SpikeRegistries.SPIKE_MACHINE.get(), MACHINE);

        BlockPos machinePos = helper.absolutePos(MACHINE);
        BlockPos pusherPos = helper.absolutePos(PUSHER);
        helper.assertFalse(machinePos.equals(pusherPos), "the two positions are the same");

        // The capability exists on all six sides and on a null (unsided) query.
        for (Direction side : Direction.values()) {
            EnergyHandler sided = level.getCapability(Capabilities.Energy.BLOCK, machinePos, side);
            helper.assertTrue(sided != null, "no energy capability on side " + side);
        }
        helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, machinePos, null) != null,
            "no energy capability on a null side");

        // Push energy from the other position, the way a cable or another block would.
        EnergyHandler fromPusher = level.getCapability(Capabilities.Energy.BLOCK, machinePos, Direction.WEST);
        long before = fromPusher.getAmountAsLong();
        helper.assertValueEqual(0L, before, "the buffer starts empty");

        int accepted;
        try (Transaction transaction = Transaction.openRoot()) {
            accepted = fromPusher.insert(PUSH_AMOUNT, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(PUSH_AMOUNT, accepted, "the insert was not accepted in full");

        long after = fromPusher.getAmountAsLong();
        helper.assertValueEqual(before + accepted, after,
            "the stored amount did not rise by the accepted amount");
        helper.assertValueEqual((long) PUSH_AMOUNT, after, "the stored amount is not " + PUSH_AMOUNT);
        LOGGER.info("[M1-02s] energy_capability: offered={} accepted={} stored={} capacity={}",
            PUSH_AMOUNT, accepted, after, fromPusher.getCapacityAsLong());

        // A second push is clamped by maxInsert, not by capacity.
        try (Transaction transaction = Transaction.openRoot()) {
            int second = fromPusher.insert(100_000, transaction);
            transaction.commit();
            helper.assertValueEqual(SpikeMachineBlockEntity.MAX_INSERT, second,
                "the second insert was not clamped to maxInsert");
        }

        // Extraction is refused.
        long storedBeforeExtract = fromPusher.getAmountAsLong();
        int extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            extracted = fromPusher.extract((int) storedBeforeExtract, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(0, extracted, "extraction was allowed");
        helper.assertValueEqual(storedBeforeExtract, fromPusher.getAmountAsLong(),
            "the stored amount changed while extraction was refused");
        LOGGER.info("[M1-02s] energy_capability: extract(offered={}) returned {}",
            storedBeforeExtract, extracted);

        // No item or fluid capability on any side, including a null side.
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

        // The pushed energy really is in the block entity, not just in a capability cache.
        SpikeMachineBlockEntity machine = blockEntity(level, machinePos);
        helper.assertTrue(machine != null, "no block entity after placing the block");
        helper.assertValueEqual((long) PUSH_AMOUNT + SpikeMachineBlockEntity.MAX_INSERT,
            machine.energy().getAmountAsLong(), "the block entity buffer disagrees with the capability");

        helper.succeed();
    }

    /**
     * Stored FE and the slot contents survive a save and load round trip through the 26.3
     * value input and output API.
     */
    private static void saveLoadRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        SpikeMachineBlockEntity machine = blockEntity(level, pos);
        helper.assertTrue(machine != null, "no block entity after placing the block");
        if (machine == null) {
            helper.fail("no block entity");
            return;
        }

        // Put known state in: energy plus one item stack. The insert must stay under
        // MAX_INSERT, because insert() is clamped per call rather than per transaction.
        int storedWanted = SpikeMachineBlockEntity.MAX_INSERT - 100;
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = machine.energy().insert(storedWanted, transaction);
            transaction.commit();
            helper.assertValueEqual(storedWanted, inserted, "the energy insert was not accepted in full");
        }
        machine.items().set(0, new ItemStack(Items.IRON_INGOT, 7));

        long storedBefore = machine.energy().getAmountAsLong();
        ItemStack itemBefore = machine.items().get(0).copy();
        helper.assertValueEqual((long) storedWanted, storedBefore, "the energy insert did not land");
        helper.assertValueEqual(Items.IRON_INGOT, itemBefore.getItem(), "the wrong item was placed");
        helper.assertValueEqual(7, itemBefore.getCount(), "the wrong count was placed");

        // Save through ValueOutput and read back through ValueInput, which is what a level
        // save/load does.
        TagValueOutput output = TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,
            level.registryAccess());
        machine.saveWithId(output);
        CompoundTag tag = output.buildResult();

        helper.assertTrue(tag.contains(net.minecraft.world.ContainerHelper.TAG_ITEMS),
            "the saved tag has no Items entry");
        helper.assertTrue(tag.contains("energy"), "the saved tag has no energy entry");

        // Wipe, then load.
        machine.items().clear();
        machine.setStoredEnergy(0);
        helper.assertValueEqual(0L, machine.energy().getAmountAsLong(), "the wipe did not clear the energy");
        helper.assertTrue(machine.items().get(0).isEmpty(), "the wipe did not clear the slot");

        machine.loadWithComponents(
            TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));

        long storedAfter = machine.energy().getAmountAsLong();
        ItemStack itemAfter = machine.items().get(0).copy();
        helper.assertValueEqual(storedBefore, storedAfter, "the stored energy did not survive the round trip");
        helper.assertValueEqual(Items.IRON_INGOT, itemAfter.getItem(), "the item did not survive the round trip");
        helper.assertValueEqual(7, itemAfter.getCount(), "the item count did not survive the round trip");
        LOGGER.info("[M1-02s] save_load_round_trip: energy {} -> {} FE, item {}x{} -> {}x{}",
            storedBefore, storedAfter, itemBefore.getItem(), itemBefore.getCount(),
            itemAfter.getItem(), itemAfter.getCount());

        helper.succeed();
    }

    /**
     * The block registers without the "Block id not set" failure and its menu opens
     * server-side.
     */
    private static void menuOpensServerSide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        // Registration succeeded and the block is in the block registry under our id.
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("lithmod", "spike_machine"));
        helper.assertTrue(block == SpikeRegistries.SPIKE_MACHINE.get(),
            "lithmod:spike_machine is not registered to the expected block");
        LOGGER.info("[M1-02s] menu: block {} registered as {}", block,
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));

        SpikeMachineBlockEntity machine = blockEntity(level, pos);
        helper.assertTrue(machine != null, "no block entity after placing the block");
        if (machine == null) {
            helper.fail("no block entity");
            return;
        }

        // The menu type is registered.
        var menuType = net.minecraft.core.registries.BuiltInRegistries.MENU
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("lithmod", "spike_machine"));
        helper.assertTrue(menuType == SpikeRegistries.MENU.get(),
            "lithmod:spike_machine menu type is not registered to the expected MenuType");
        helper.assertTrue(menuType != null, "lithmod:spike_machine menu type is missing");

        // The menu type can build a server-side instance through the very factory that
        // IMenuTypeExtension.create installed.
        ServerPlayer opener = connect(level);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        AbstractContainerMenu fromFactory = menuType.create(7, opener.getInventory(), buf);
        helper.assertTrue(fromFactory instanceof SpikeMachineMenu,
            "the registered MenuType factory did not return a SpikeMachineMenu but "
                + fromFactory.getClass().getName());

        // And the block entity builds a menu bound to itself.
        AbstractContainerMenu fromProvider = machine.createMenu(8, opener.getInventory(), opener);
        helper.assertTrue(fromProvider instanceof SpikeMachineMenu,
            "createMenu did not return a SpikeMachineMenu but " + fromProvider.getClass().getName());
        SpikeMachineMenu bound = (SpikeMachineMenu) fromProvider;
        helper.assertTrue(bound.machine() == machine, "createMenu's menu points at a different block entity");
        helper.assertTrue(bound.energy() != null, "the server-side menu has no energy handler");
        helper.assertValueEqual(37, bound.slots.size(), "the menu has an unexpected slot count");

        // Open it server-side. The test player has no real client, so the menu is installed
        // directly on the player instead of through Player.openMenu, which would send the
        // neoforge:advanced_open_screen payload to a client that does not exist.
        opener.containerMenu = bound;
        helper.assertTrue(opener.containerMenu instanceof SpikeMachineMenu,
            "the opened menu is not a SpikeMachineMenu but " + opener.containerMenu.getClass().getName());
        LOGGER.info("[M1-02s] menu: registered {} -> {} | opened {} with {} slots, energy handler {}",
            menuType.getClass().getSimpleName(), fromFactory.getClass().getSimpleName(),
            opener.containerMenu.getClass().getSimpleName(), bound.slots.size(), bound.energy() != null);

        opener.closeContainer();
        helper.succeed();
    }

    /** Places the spike machine and returns once the block entity exists. */
    private static void place(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, SpikeRegistries.SPIKE_MACHINE.get());
    }

    private static @Nullable SpikeMachineBlockEntity blockEntity(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpikeMachineBlockEntity machine) {
            return machine;
        }
        return null;
    }

    /** Builds a connected creative-mode server player, as {@code FoupGameTests} does. */
    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "spike-test"), false);
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
