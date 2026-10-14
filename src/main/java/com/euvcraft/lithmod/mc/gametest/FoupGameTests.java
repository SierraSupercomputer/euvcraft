package com.euvcraft.lithmod.mc.gametest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Game tests for the FOUP item of milestone M0-08.
 *
 * <p>{@code lithmod:foup_stack_round_trip} runs on a real server with the real
 * registries and shows that the hidden rolls survive copying an {@link ItemStack} and a
 * full NBT round trip, which is the path a chest, a shulker box or a world save takes.
 * {@code lithmod:foup_network_view_is_public_only} shows that the network codec
 * carries the public view and nothing else. {@code lithmod:foup_encoded_size} measures
 * what a lot costs.</p>
 */
public final class FoupGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");
    private static final String ENVIRONMENT_ID = "foup_default";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register("foup_stack_round_trip", () -> FoupGameTests::stackRoundTrip);
        FUNCTIONS.register("foup_network_view_is_public_only", () -> FoupGameTests::networkViewIsPublicOnly);
        FUNCTIONS.register("foup_encoded_size", () -> FoupGameTests::encodedSize);
        FUNCTIONS.register("foup_creative_tab_entries", () -> FoupGameTests::creativeTabEntries);
        FUNCTIONS.register("foup_debug_commands", () -> FoupGameTests::debugCommands);
    }

    private FoupGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) FoupGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "foup_stack_round_trip");
        register(event, data, "foup_network_view_is_public_only");
        register(event, data, "foup_encoded_size");
        register(event, data, "foup_creative_tab_entries");
        register(event, data, "foup_debug_commands");
        LOGGER.info("Registered EUVCRAFT foup game tests");
    }

    private static void register(RegisterGameTestsEvent event,
        TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * Proves that a FOUP stack keeps its hidden rolls when it is copied and when it is
     * written to NBT and read back.
     */
    private static void stackRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 12);
        ItemStack original = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

        helper.assertTrue(FoupItems.isFoup(original), "the stack has no FOUP contents component");
        assertSameContents(helper, "the freshly built stack", contents, FoupItems.contentsOf(original));

        ItemStack copied = original.copy();
        assertSameContents(helper, "a copied stack", contents, FoupItems.contentsOf(copied));

        ItemStack reloaded = roundTrip(helper, level, original);
        if (reloaded == null) {
            return;
        }
        assertSameContents(helper, "a stack that went through NBT", contents, FoupItems.contentsOf(reloaded));

        helper.succeed();
    }

    /**
     * Proves that what the network codec sends is the public view and that a client
     * cannot recover a roll from it.
     */
    private static void networkViewIsPublicOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 7);
        FoupPublicView sent = contents.publicView();

        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, contents);
        int bytes = buffer.readableBytes();
        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

        helper.assertTrue(FoupContentsCodec.isClientView(received),
            "the decoded lot is not recognisable as a client view: " + received);
        assertEqual(helper, sent.capacity(), received.capacity(), "capacity changed on the wire");
        assertEqual(helper, sent.waferCount(), received.size(), "wafer count changed on the wire");
        assertEqual(helper, sent.lotLayer(), received.lotLayer(), "lot layer changed on the wire");

        for (Wafer wafer : received.wafers()) {
            helper.assertTrue(wafer.cumulativeRoll() == FoupContentsCodec.CLIENT_VIEW_ROLL,
                "a roll reached the client: " + wafer.cumulativeRoll());
            helper.assertTrue(wafer.flags() == Wafer.NO_FLAGS,
                "a flag reached the client: " + wafer.flags());
        }

        helper.assertTrue(bytes < sent.waferCount(),
            "the network form takes " + bytes + " bytes for " + sent.waferCount()
                + " wafers, which leaves room for per-wafer values");

        LOGGER.info("FOUP network form: {} wafers in {} bytes", sent.waferCount(), bytes);
        helper.succeed();
    }

    /**
     * Measures what a lot costs on disk and on the wire and logs the numbers.
     */
    private static void encodedSize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        for (int wafers : new int[] {25, 64}) {
            FoupContents contents = FoupDebugCommand.fillContents(level, LOT, wafers, wafers, 3);
            ItemStack stack = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

            DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops(level), stack);
            if (encoded.result().isEmpty()) {
                helper.fail("could not encode a " + wafers + " wafer stack: " + encoded.error());
                return;
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                NbtIo.writeUnnamedTag(encoded.result().get(), out);
            } catch (Exception e) {
                helper.fail("could not measure the NBT of a " + wafers + " wafer stack: " + e);
                return;
            }

            ByteBuf buffer = Unpooled.buffer();
            FoupContentsCodec.STREAM_CODEC.encode(buffer, contents);

            LOGGER.info("FOUP of {} wafers: {} bytes NBT, {} bytes on the wire",
                wafers, bytes.size(), buffer.readableBytes());
        }

        helper.succeed();
    }

    /**
     * Proves the creative tab hands out one real, empty lot per variant.
     *
     * <p>The tab contents are built by the game on the client as well, so this also
     * exercises the path where a FOUP stack is created without a server level.</p>
     */
    private static void creativeTabEntries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTab tab = ModCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess()));
        Collection<ItemStack> displayed = tab.getDisplayItems();
        helper.assertTrue(displayed.size() == 2,
            "the EUVCRAFT tab holds " + displayed.size() + " entries, expected 2");

        ItemStack mk1 = new ItemStack(ModItems.FOUP_MK1.get());
        ItemStack mk2 = new ItemStack(ModItems.FOUP_MK2.get());
        assertEqual(helper, 1, mk1.getMaxStackSize(), "FOUP Mk1 does not have a stack size of 1");
        assertEqual(helper, 1, mk2.getMaxStackSize(), "FOUP Mk2 does not have a stack size of 1");

        FoupContents first = null;
        FoupContents second = null;
        for (ItemStack stack : displayed) {
            FoupContents contents = FoupItems.contentsOf(stack);
            if (contents == null) {
                helper.fail("a tab entry is a " + stack.getItem() + " without a sealed lot in it");
                return;
            }
            helper.assertTrue(contents.isEmpty(), "a tab entry already holds wafers");
            assertEqual(helper, FoupItems.configuredCapacity(), contents.capacity(),
                "a tab entry does not use the configured capacity");
            if (stack.is(ModItems.FOUP_MK1.get())) {
                first = contents;
            } else if (stack.is(ModItems.FOUP_MK2.get())) {
                second = contents;
            }
        }

        helper.assertTrue(first != null, "FOUP Mk1 is missing from the tab");
        helper.assertTrue(second != null, "FOUP Mk2 is missing from the tab");
        helper.assertTrue(!first.lotId().equals(second.lotId()),
            "both tab entries were given the same lot id");
        helper.assertTrue(!first.lotId().equals(LOT),
            "a tab entry reused the lot id of a test lot");

        helper.succeed();
    }

    /**
 * Proves the debug command tree follows the {@code debugCommands} config key and,
 * when it is on, that {@code fill} and {@code inspect} work end to end.
 *
 * <p>With the key off (the default) this only proves the {@code foup} branch is absent,
 * so the test never fails on a normal run. Turn the key on in
 * {@code run/config/lithmod-local.toml} to exercise the commands themselves.</p>
 *
 * <p>Vanilla already owns {@code /debug}, so this looks for the {@code foup} child of
 * that node and not for the root itself.</p>
 */
    private static void debugCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean enabled = EUVCRAFTMod.settings().debugCommands();
        CommandNode<CommandSourceStack> debug = level.getServer().getCommands().getDispatcher()
            .getRoot().getChild(FoupDebugCommand.ROOT);
        CommandNode<CommandSourceStack> foup = debug == null ? null : debug.getChild("foup");

        if (!enabled) {
            helper.assertTrue(foup == null,
                "the /" + FoupDebugCommand.ROOT + " foup branch is registered while debugCommands is off");
            LOGGER.info("FOUP debug commands are disabled; set debugCommands=true in the config to test them");
            helper.succeed();
            return;
        }

        if (foup == null) {
            helper.fail("debugCommands is on but /" + FoupDebugCommand.ROOT + " foup is not registered");
            return;
        }

        ServerPlayer player = connect(level);
        helper.assertTrue(!foup.canUse(sourceFor(level, player, PermissionSet.NO_PERMISSIONS)),
            "a player without permissions can use the FOUP debug branch");
        helper.assertTrue(foup.canUse(sourceFor(level, player, LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS))),
            "a gamemaster cannot use the FOUP debug branch");

        // Vanilla already owns /debug at permission level 3, and brigadier keeps the
        // existing node's requirement when two literals merge, so the gate that
        // actually applies to /debug foup ... is the stricter one. Recorded here so
        // the behaviour cannot change unnoticed.
        helper.assertTrue(!debug.canUse(sourceFor(level, player, LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS))),
            "a gamemaster can already reach /debug, so the merge assumption no longer holds");
        helper.assertTrue(debug.canUse(sourceFor(level, player, LevelBasedPermissionSet.forLevel(PermissionLevel.ADMINS))),
            "an admin cannot reach /debug at all");

        CommandSourceStack source = sourceFor(level, player, LevelBasedPermissionSet.forLevel(PermissionLevel.ADMINS));

        int filled = run(level, source, FoupDebugCommand.ROOT + " foup fill 3 5");
        helper.assertTrue(filled > 0, "debug foup fill returned " + filled);

        FoupContents given = firstFoup(player);
        if (given == null) {
            helper.fail("debug foup fill did not give the player a FOUP");
            return;
        }
        assertEqual(helper, 3, given.size(), "the given FOUP does not hold the requested wafers");
        assertEqual(helper, 5, given.lotLayer(), "the given FOUP is not at the requested layer");
        assertEqual(helper, FoupItems.configuredCapacity(), given.capacity(),
            "the given FOUP does not use the configured capacity");

        // A second fill must not repeat the lot id of the first one.
        player.getInventory().clearContent();
        run(level, source, FoupDebugCommand.ROOT + " foup fill 3 5");
        FoupContents again = firstFoup(player);
        if (again == null) {
            helper.fail("the second debug foup fill gave nothing");
            return;
        }
        helper.assertTrue(!given.lotId().equals(again.lotId()),
            "two fills were given the same lot id");

        int inspected = run(level, source, FoupDebugCommand.ROOT + " foup inspect");
        helper.assertTrue(inspected == 1, "debug foup inspect returned " + inspected);

        helper.succeed();
    }

    /**
 * Builds a connected creative-mode server player.
 *
 * <p>A connection is needed because the debug commands answer through
 * {@code CommandSourceStack.sendSuccess}, which sends a packet to the player. The
 * player is not ticked by the test; it exists only to receive the answer.</p>
 */
    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "foup-debug-test"), false);
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

    private static int run(ServerLevel level, CommandSourceStack source, String command) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            helper_fail(command, e);
            return 0;
        }
    }

    private static void helper_fail(String command, Exception e) {
        LOGGER.error("Command '{}' failed: {}", command, e);
    }

    private static FoupContents firstFoup(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            FoupContents contents = FoupItems.contentsOf(stack);
            if (contents != null) {
                return contents;
            }
        }
        return null;
    }

    private static CommandSourceStack sourceFor(ServerLevel level, ServerPlayer player, PermissionSet permissions) {
        return new CommandSourceStack(player.createCommandSourceStack().source, player.position(),
            player.getRotationVector(), level, permissions, level.getServer(), player);
    }

    private static RegistryOps<Tag> ops(ServerLevel level) {
        return RegistryOps.create(NbtOps.INSTANCE, lookup(level));
    }

    private static RegistryAccess lookup(ServerLevel level) {
        return level.registryAccess();
    }

    private static ItemStack roundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops(level), stack);
        if (encoded.result().isEmpty()) {
            helper.fail("could not encode the stack: " + encoded.error());
            return null;
        }
        DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops(level), encoded.result().get());
        if (decoded.result().isEmpty()) {
            helper.fail("could not decode the stack: " + decoded.error());
            return null;
        }
        return decoded.result().get();
    }

    private static void assertEqual(GameTestHelper helper, int expected, int actual, String message) {
        helper.assertTrue(expected == actual, message + ": " + expected + " -> " + actual);
    }

    private static void assertSameContents(GameTestHelper helper, String what, FoupContents expected,
        FoupContents actual) {
        if (actual == null) {
            helper.fail(what + " has no FOUP contents component");
            return;
        }
        helper.assertTrue(expected.lotId().equals(actual.lotId()),
            what + " changed the lot id: " + actual.lotId());
        assertEqual(helper, expected.capacity(), actual.capacity(), what + " changed the capacity");
        assertEqual(helper, expected.size(), actual.size(), what + " changed the wafer count");
        for (int i = 0; i < expected.size(); i++) {
            Wafer want = expected.wafers().get(i);
            Wafer got = actual.wafers().get(i);
            assertEqual(helper, want.layerIndex(), got.layerIndex(), what + " changed the layer of wafer " + i);
            helper.assertTrue(want.cumulativeRoll() == got.cumulativeRoll(),
                what + " changed the hidden roll of wafer " + i + ": "
                    + want.cumulativeRoll() + " -> " + got.cumulativeRoll());
            assertEqual(helper, want.flags(), got.flags(), what + " changed the flags of wafer " + i);
        }
    }
}