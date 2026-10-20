package com.euvcraft.lithmod.mc.gametest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
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
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
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
        FUNCTIONS.register("foup_trusted_contents", () -> FoupGameTests::trustedContents);
        FUNCTIONS.register("foup_stored_capacity_wins", () -> FoupGameTests::storedCapacityWins);
        FUNCTIONS.register("foup_shulker_box_round_trip", () -> FoupGameTests::shulkerBoxRoundTrip);
        FUNCTIONS.register("foup_ender_chest_round_trip", () -> FoupGameTests::enderChestRoundTrip);
        FUNCTIONS.register("foup_hopper_transfer", () -> FoupGameTests::hopperTransfer);
        FUNCTIONS.register("foup_shift_click_move", () -> FoupGameTests::shiftClickMove);
        FUNCTIONS.register("foup_substrate_persistence", () -> FoupGameTests::substratePersistence);
        FUNCTIONS.register("foup_substrate_public_trust", () -> FoupGameTests::substratePublicTrust);
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
        register(event, data, "foup_trusted_contents");
        register(event, data, "foup_stored_capacity_wins");
        register(event, data, "foup_shulker_box_round_trip");
        register(event, data, "foup_ender_chest_round_trip");
        register(event, data, "foup_hopper_transfer");
        register(event, data, "foup_shift_click_move");
        register(event, data, "foup_substrate_persistence");
        register(event, data, "foup_substrate_public_trust");
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

        ItemStack fromChest = chestRoundTrip(helper, level, original);
        if (fromChest == null) {
            return;
        }
        assertSameContents(helper, "a stack that went through a chest", contents, FoupItems.contentsOf(fromChest));

        ItemEntity fromGround = groundRoundTrip(helper, level, original);
        if (fromGround == null) {
            return;
        }
        assertSameContents(helper, "a stack that was dropped and saved", contents,
            FoupItems.contentsOf(fromGround.getItem()));

        ServerPlayer player = connect(level);
        player.getInventory().setItem(0, original.copy());
        assertSameContents(helper, "a stack placed with Inventory.setItem", contents,
            FoupItems.contentsOf(player.getInventory().getItem(0)));

        fromGround.playerTouch(player);
        assertSameContents(helper, "a stack picked up off the ground", contents,
            FoupItems.contentsOf(player.getInventory().getItem(1)));

        helper.succeed();
    }

    /**
     * Runs a stack through the chest container path: {@link ContainerHelper#saveAllItems}
     * into a tag and {@link ContainerHelper#loadAllItems} back into a fresh chest.
     *
     * @return the reloaded stack, or null if the round trip could not be done
     */
    private static ItemStack chestRoundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        NonNullList<ItemStack> chest = NonNullList.withSize(27, ItemStack.EMPTY);
        chest.set(4, stack.copy());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, chest);
        CompoundTag saved = output.buildResult();

        NonNullList<ItemStack> reloaded = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved), reloaded);

        ItemStack result = reloaded.get(4);
        if (result.isEmpty()) {
            helper.fail("the chest round trip lost the FOUP; the saved tag was " + saved);
            return null;
        }
        return result;
    }

    /**
     * Runs a stack through the dropped-item path: an {@link ItemEntity}, saved and read
     * back the way a chunk is written.
     *
     * @return the reloaded entity, or null if the round trip could not be done
     */
    private static ItemEntity groundRoundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, 0.5, 0.5, 0.5, stack.copy());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.save(output);
        CompoundTag saved = output.buildResult();

        ItemEntity reloaded = new ItemEntity(level, 0.5, 0.5, 0.5, ItemStack.EMPTY);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));

        if (reloaded.getItem().isEmpty()) {
            helper.fail("the dropped item round trip lost the FOUP; the saved tag was " + saved);
            return null;
        }
        return reloaded;
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

        // Filter to only the two FOUP entries before counting and iterating
        java.util.List<ItemStack> foupEntries = new java.util.ArrayList<>();
        for (ItemStack stack : displayed) {
            if (stack.is(ModItems.FOUP_MK1.get()) || stack.is(ModItems.FOUP_MK2.get())) {
                foupEntries.add(stack);
            }
        }
        helper.assertTrue(foupEntries.size() == 2,
            "the EUVCRAFT tab holds " + foupEntries.size() + " FOUP entries, expected 2");

        ItemStack mk1 = new ItemStack(ModItems.FOUP_MK1.get());
        ItemStack mk2 = new ItemStack(ModItems.FOUP_MK2.get());
        assertEqual(helper, 1, mk1.getMaxStackSize(), "FOUP Mk1 does not have a stack size of 1");
        assertEqual(helper, 1, mk2.getMaxStackSize(), "FOUP Mk2 does not have a stack size of 1");

        FoupContents first = null;
        FoupContents second = null;
        for (ItemStack stack : foupEntries) {
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

        int filled = run(helper, level, source, FoupDebugCommand.ROOT + " foup fill 3 5");
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
        run(helper, level, source, FoupDebugCommand.ROOT + " foup fill 3 5");
        FoupContents again = firstFoup(player);
        if (again == null) {
            helper.fail("the second debug foup fill gave nothing");
            return;
        }
        helper.assertTrue(!given.lotId().equals(again.lotId()),
            "two fills were given the same lot id");

        int inspected = run(helper, level, source, FoupDebugCommand.ROOT + " foup inspect");
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

    private static int run(GameTestHelper helper, ServerLevel level, CommandSourceStack source, String command) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            helper.fail("the command '" + command + "' did not parse: " + e.getMessage());
            return 0;
        }
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

    /**
     * Proves that {@link FoupItems#trustedContentsOf} refuses the three things it is
     * there to refuse.
     */
    private static void trustedContents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int configured = FoupItems.configuredCapacity();

        // 1. A stack that is not a FOUP at all.
        FoupContents fromStone = FoupItems.trustedContentsOf(new ItemStack(Items.STONE));
        helper.assertTrue(fromStone.isEmpty(), "a stone produced wafers: " + fromStone);
        assertEqual(helper, configured, fromStone.capacity(),
            "a stone produced a lot that is not at the configured capacity");

        // 2. A client view that still holds wafers: the rolls were never filled in.
        FoupContents fake = FoupDebugCommand.fillContents(level, LOT, configured, configured, 4);
        ByteBuf carryingWafers = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(carryingWafers, fake);
        ItemStack tampered = FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupContentsCodec.STREAM_CODEC.decode(carryingWafers));

        FoupContents fromTampered = FoupItems.trustedContentsOf(tampered);
        helper.assertTrue(fromTampered.isEmpty(),
            "a client view holding " + configured + " wafers was accepted: " + fromTampered);
        assertEqual(helper, configured, fromTampered.capacity(),
            "a tampered stack produced a lot at the wrong capacity");
        helper.assertTrue(!fromTampered.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID),
            "a tampered stack kept the client view lot id");

        // 3. A client view with no wafers: the capacity came from the client.
        ByteBuf emptyView = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(emptyView, FoupContents.empty(LOT, configured));
        ItemStack clientCapacity = FoupItems.with(ModItems.FOUP_MK2.get(),
            FoupContentsCodec.STREAM_CODEC.decode(emptyView));

        FoupContents fromClientCapacity = FoupItems.trustedContentsOf(clientCapacity);
        helper.assertTrue(fromClientCapacity.isEmpty(), "a client view produced wafers");
        assertEqual(helper, configured, fromClientCapacity.capacity(),
            "the client's capacity was trusted instead of the configured one");

        // A real lot is returned untouched.
        FoupContents real = FoupDebugCommand.fillContents(level, LOT, configured, 3, 2);
        FoupContents fromReal = FoupItems.trustedContentsOf(FoupItems.with(ModItems.FOUP_MK1.get(), real));
        helper.assertTrue(!FoupContentsCodec.isClientView(fromReal), "a real lot was replaced");
        assertSameContents(helper, "a real lot", real, fromReal);

        helper.succeed();
    }

    /**
     * Proves that a lot keeps the capacity stored in its own data even when the
     * configured capacity is different, and that a newly created lot uses the
     * configured one.
     */
    private static void storedCapacityWins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int configured = FoupItems.configuredCapacity();
        int otherCapacity = configured == 64 ? 25 : 64;

        FoupContents existing = FoupDebugCommand.fillContents(level, LOT, otherCapacity, 3, 1);
        ItemStack stack = FoupItems.with(ModItems.FOUP_MK1.get(), existing);

        ItemStack reloaded = roundTrip(helper, level, stack);
        if (reloaded == null) {
            return;
        }
        FoupContents afterReload = FoupItems.contentsOf(reloaded);
        assertEqual(helper, otherCapacity, afterReload.capacity(),
            "an existing lot did not keep its stored capacity");

        assertEqual(helper, configured, FoupItems.emptyFoup(ModItems.FOUP_MK1.get())
            .get(ModDataComponents.FOUP_CONTENTS.get()).capacity(),
            "a newly created lot did not use the configured capacity");

        helper.succeed();
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

    /**
     * Proves that a FOUP stack keeps its hidden rolls through a shulker box.
     * Uses the same ContainerHelper NBT path as chestRoundTrip.
     */
    private static void shulkerBoxRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 12);
        ItemStack original = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

        ItemStack fromShulker = shulkerBoxRoundTrip(helper, level, original);
        if (fromShulker == null) {
            return;
        }
        assertSameContents(helper, "a stack that went through a shulker box", contents,
            FoupItems.contentsOf(fromShulker));

        helper.succeed();
    }

    /**
     * Runs a stack through a shulker box NBT round trip.
     *
     * @return the reloaded stack, or null if the round trip could not be done
     */
    private static ItemStack shulkerBoxRoundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        NonNullList<ItemStack> box = NonNullList.withSize(27, ItemStack.EMPTY);
        box.set(4, stack.copy());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, box);
        CompoundTag saved = output.buildResult();

        NonNullList<ItemStack> reloaded = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved), reloaded);

        ItemStack result = reloaded.get(4);
        if (result.isEmpty()) {
            helper.fail("the shulker box round trip lost the FOUP; the saved tag was " + saved);
            return null;
        }
        return result;
    }

    /**
     * Proves that a FOUP stack keeps its hidden rolls through an ender chest NBT round trip.
     * The ender chest is saved as part of the player data.
     */
    private static void enderChestRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 12);
        ItemStack original = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

        ServerPlayer player = connect(level);
        player.getInventory().setItem(0, original.copy());

        // Put the FOUP in the player's ender chest
        player.getEnderChestInventory().setItem(0, original.copy());

        // Save and reload the ender chest contents via NBT
        ItemStack fromEnderChest = enderChestRoundTrip(helper, level, player.getEnderChestInventory().getItem(0));
        if (fromEnderChest == null) {
            return;
        }
        assertSameContents(helper, "a stack that went through an ender chest", contents,
            FoupItems.contentsOf(fromEnderChest));

        helper.succeed();
    }

    /**
     * Runs an ender chest item through NBT round trip.
     */
    private static ItemStack enderChestRoundTrip(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        NonNullList<ItemStack> enderChest = NonNullList.withSize(27, ItemStack.EMPTY);
        enderChest.set(0, stack.copy());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, enderChest);
        CompoundTag saved = output.buildResult();

        NonNullList<ItemStack> reloaded = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved), reloaded);

        ItemStack result = reloaded.get(0);
        if (result.isEmpty()) {
            helper.fail("the ender chest round trip lost the FOUP; the saved tag was " + saved);
            return null;
        }
        return result;
    }

    /**
     * Proves that a FOUP stack keeps its hidden rolls when moved by a hopper between containers.
     * Simulates the hopper transfer by saving from one chest and loading into another.
     */
    private static void hopperTransfer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 12);
        ItemStack original = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

        // Chest 1 (source)
        NonNullList<ItemStack> chest1 = NonNullList.withSize(27, ItemStack.EMPTY);
        chest1.set(0, original.copy());

        // Save chest 1
        TagValueOutput output1 = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output1, chest1);
        CompoundTag saved1 = output1.buildResult();

        // Load into hopper (simplified: just move the item)
        NonNullList<ItemStack> hopperInv = NonNullList.withSize(5, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved1), hopperInv);

        // Find the FOUP in hopper
        ItemStack inHopper = ItemStack.EMPTY;
        for (int i = 0; i < hopperInv.size(); i++) {
            if (FoupItems.isFoup(hopperInv.get(i))) {
                inHopper = hopperInv.get(i);
                break;
            }
        }
        if (inHopper.isEmpty()) {
            helper.fail("hopper did not receive the FOUP from chest 1");
            return;
        }

        // Save hopper and load into chest 2
        TagValueOutput output2 = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output2, hopperInv);
        CompoundTag saved2 = output2.buildResult();

        NonNullList<ItemStack> chest2 = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved2), chest2);

        // Find the FOUP in chest 2
        ItemStack fromChest2 = ItemStack.EMPTY;
        for (int i = 0; i < chest2.size(); i++) {
            if (FoupItems.isFoup(chest2.get(i))) {
                fromChest2 = chest2.get(i);
                break;
            }
        }
        if (fromChest2.isEmpty()) {
            helper.fail("the hopper did not transfer the FOUP to the second chest");
            return;
        }
        assertSameContents(helper, "a stack that went through a hopper", contents,
            FoupItems.contentsOf(fromChest2));

        helper.succeed();
    }

    /**
     * Proves that a FOUP stack keeps its hidden rolls when shift-clicked through a container menu.
     * Simulates shift-click by moving item from container slot to player inventory.
     */
    private static void shiftClickMove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoupContents contents = FoupDebugCommand.fillContents(level, LOT, 25, 25, 12);
        ItemStack original = FoupItems.with(ModItems.FOUP_MK1.get(), contents);

        ServerPlayer player = connect(level);

        // Place FOUP in player inventory slot 0
        player.getInventory().setItem(0, original.copy());

        // Simulate shift-click from player inventory to a container (chest)
        // by saving player inventory and loading into a chest
        NonNullList<ItemStack> playerInv = NonNullList.withSize(36, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) {
            playerInv.set(i, player.getInventory().getItem(i).copy());
        }

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, playerInv);
        CompoundTag saved = output.buildResult();

        // Load into a chest (simulating shift-click into container)
        NonNullList<ItemStack> chest = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved), chest);

        // Find the FOUP in the chest
        ItemStack inChest = ItemStack.EMPTY;
        for (int i = 0; i < chest.size(); i++) {
            if (FoupItems.isFoup(chest.get(i))) {
                inChest = chest.get(i);
                break;
            }
        }
        if (inChest.isEmpty()) {
            helper.fail("shift-click did not move the FOUP to the chest");
            return;
        }
        assertSameContents(helper, "a stack that was shift-clicked from inventory to chest", contents,
            FoupItems.contentsOf(inChest));

        // Also test reverse: shift-click from chest back to inventory
        TagValueOutput output2 = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output2, chest);
        CompoundTag saved2 = output2.buildResult();

        NonNullList<ItemStack> playerInv2 = NonNullList.withSize(36, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved2), playerInv2);

        ItemStack backInInv = ItemStack.EMPTY;
        for (int i = 0; i < playerInv2.size(); i++) {
            if (FoupItems.isFoup(playerInv2.get(i))) {
                backInInv = playerInv2.get(i);
                break;
            }
        }
        if (backInInv.isEmpty()) {
            helper.fail("shift-click did not move the FOUP back to player inventory");
            return;
        }
        assertSameContents(helper, "a stack that was shift-clicked from chest back to inventory", contents,
            FoupItems.contentsOf(backInInv));

        helper.succeed();
    }

    private static FoupContents pureSubstrateLot(UUID id, float roll, int flags, RunSpec run) {
        return new FoupContents(id, 25, List.of(new Wafer(3, roll, flags)), run, WaferSubstrate.PURE);
    }

    private static void substratePersistence(GameTestHelper helper) {
        FoupContents pure = pureSubstrateLot(LOT, 0.42f, Wafer.NO_FLAGS,
            new RunSpec("logic", 3, 0, 0.9, 24));
        ItemStack stack = FoupItems.with(ModItems.FOUP_MK1.get(), pure);
        helper.assertTrue(pure.equals(FoupItems.trustedContentsOf(stack)), "real pure lot was not trusted");
        ItemStack restored = roundTrip(helper, helper.getLevel(), stack);
        if (restored == null) return;
        helper.assertTrue(pure.equals(FoupItems.trustedContentsOf(restored)),
            "actual ItemStack persistent roundtrip lost pure grade or private state");
        CompoundTag legacy = (CompoundTag) FoupContentsCodec.CODEC.encodeStart(NbtOps.INSTANCE, pure).getOrThrow();
        legacy.remove(FoupContentsCodec.KEY_GRADE);
        FoupContents old = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        helper.assertTrue(old.grade() == WaferSubstrate.ORDINARY, "legacy lot was silently upgraded");
        helper.assertTrue(old.wafers().equals(pure.wafers()) && old.run().equals(pure.run()),
            "legacy grade default altered private state");
        legacy.putString(FoupContentsCodec.KEY_GRADE, "invalid_substrate");
        helper.assertTrue(FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, legacy).error().isPresent(),
            "invalid explicit substrate was accepted");
        helper.succeed();
    }

    private static void substratePublicTrust(GameTestHelper helper) {
        FoupContents first = pureSubstrateLot(LOT, 0.42f, Wafer.NO_FLAGS,
            new RunSpec("logic", 3, 0, 0.9, 24));
        FoupContents second = pureSubstrateLot(UUID.fromString("00000000-0000-4000-8000-0000000000f9"),
            0.13f, 1, new RunSpec("memory", 2, 0, 0.8, 12));
        ByteBuf a = Unpooled.buffer();
        ByteBuf b = Unpooled.buffer();
        try {
            FoupContentsCodec.STREAM_CODEC.encode(a, first);
            FoupContentsCodec.STREAM_CODEC.encode(b, second);
            helper.assertTrue(java.util.Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(a),
                io.netty.buffer.ByteBufUtil.getBytes(b)), "private lot/run values affected public packet");
            FoupContents view = FoupContentsCodec.STREAM_CODEC.decode(a);
            helper.assertTrue(view.grade() == WaferSubstrate.PURE && view.publicView().equals(first.publicView()),
                "network projection lost approved pure substrate information");
            helper.assertTrue(FoupContentsCodec.isClientView(view) && view.run() == null,
                "public reconstruction gained private authority or a run contract");
            for (Wafer wafer : view.wafers()) {
                helper.assertTrue(wafer.cumulativeRoll() == FoupContentsCodec.CLIENT_VIEW_ROLL
                    && wafer.flags() == Wafer.NO_FLAGS, "private wafer values reached public reconstruction");
            }
            FoupContents replacement = FoupItems.trustedContentsOf(FoupItems.with(ModItems.FOUP_MK1.get(), view));
            helper.assertTrue(replacement != null && replacement.isEmpty() && replacement.run() == null
                && replacement.grade() == WaferSubstrate.ORDINARY
                && replacement.capacity() == FoupItems.configuredCapacity()
                && !FoupContentsCodec.isClientView(replacement),
                "actual trustedContentsOf failed to replace pure client wafers with a fresh ordinary empty lot");
            helper.assertTrue(first.equals(FoupItems.trustedContentsOf(FoupItems.with(ModItems.FOUP_MK1.get(), first))),
                "trust boundary rejected a real pure server lot");
        } finally {
            a.release();
            b.release();
        }
        helper.succeed();
    }
}