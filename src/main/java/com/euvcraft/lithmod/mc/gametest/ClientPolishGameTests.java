package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the Creative FOUP client-polish fix (M1 client polish).
 *
 * <p>These tests exercise the actual creative inventory packet path:
 * {@link ServerboundSetCreativeModeSlotPacket} encode/decode via
 * {@link net.minecraft.network.RegistryFriendlyByteBuf} and
 * {@link ServerboundSetCreativeModeSlotPacket#STREAM_CODEC}, real
 * {@link Connection#handleSetCreativeModeSlot()}, then actual
 * {@link FoupItem#inventoryTick} and real {@link com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity}
 * inputs/FE/serverTick completion.</p>
 *
 * <p>Test IDs begin with {@code client_polish_} and are registered only inside the existing
 * normal-fixture branch in {@link EUVCRAFTMod}.</p>
 */
public final class ClientPolishGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final String ENVIRONMENT_ID = "client_polish_default";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        // Positive cases: Creative empty client-view FOUP becomes server lot
        FUNCTIONS.register("client_polish_creative_empty_mk1_stream_roundtrip", () -> ClientPolishGameTests::creativeEmptyMk1StreamRoundTrip);
        FUNCTIONS.register("client_polish_creative_empty_mk2_stream_roundtrip", () -> ClientPolishGameTests::creativeEmptyMk2StreamRoundTrip);
        FUNCTIONS.register("client_polish_creative_empty_repeat_hook_stable", () -> ClientPolishGameTests::creativeEmptyRepeatHookStable);
        FUNCTIONS.register("client_polish_creative_empty_custom_name_preserved", () -> ClientPolishGameTests::creativeEmptyCustomNamePreserved);
        FUNCTIONS.register("client_polish_creative_empty_slicer_accepts", () -> ClientPolishGameTests::creativeEmptySlicerAccepts);

        // Negative cases: Survival/nonplayer unchanged, filled client-view cannot start slicer,
        // sealed lot identity/wafer roll exact preserved, overstack refused
        FUNCTIONS.register("client_polish_survival_empty_unchanged", () -> ClientPolishGameTests::survivalEmptyUnchanged);
        FUNCTIONS.register("client_polish_nonplayer_empty_unchanged", () -> ClientPolishGameTests::nonplayerEmptyUnchanged);
        FUNCTIONS.register("client_polish_filled_client_view_rejected_by_slicer", () -> ClientPolishGameTests::filledClientViewRejectedBySlicer);
        FUNCTIONS.register("client_polish_filled_client_view_cannot_fabricate_wafers", () -> ClientPolishGameTests::filledClientViewCannotFabricateWafers);
        FUNCTIONS.register("client_polish_sealed_lot_identity_preserved", () -> ClientPolishGameTests::sealedLotIdentityPreserved);
        FUNCTIONS.register("client_polish_sealed_lot_wafer_roll_preserved", () -> ClientPolishGameTests::sealedLotWaferRollPreserved);
        FUNCTIONS.register("client_polish_overstack_client_view_untouched", () -> ClientPolishGameTests::overstackClientViewUntouched);
    }

    private ClientPolishGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) ClientPolishGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                400, 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "client_polish_creative_empty_mk1_stream_roundtrip");
        register(event, data, "client_polish_creative_empty_mk2_stream_roundtrip");
        register(event, data, "client_polish_creative_empty_repeat_hook_stable");
        register(event, data, "client_polish_creative_empty_custom_name_preserved");
        register(event, data, "client_polish_creative_empty_slicer_accepts");
        register(event, data, "client_polish_survival_empty_unchanged");
        register(event, data, "client_polish_nonplayer_empty_unchanged");
        register(event, data, "client_polish_filled_client_view_rejected_by_slicer");
        register(event, data, "client_polish_filled_client_view_cannot_fabricate_wafers");
        register(event, data, "client_polish_sealed_lot_identity_preserved");
        register(event, data, "client_polish_sealed_lot_wafer_roll_preserved");
        register(event, data, "client_polish_overstack_client_view_untouched");
        LOGGER.info("Registered EUVCRAFT client polish game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
                ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static ServerPlayer connectCreative(ServerLevel level, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static ServerPlayer connectSurvival(ServerLevel level, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void cleanupPlayer(ServerLevel level, ServerPlayer player) {
        if (player != null) {
            player.closeContainer();
            level.getServer().getPlayerList().remove(player);
        }
    }

    private static ItemStack buildClientViewEmptyFOUP(Item item, int capacity, int waferCount, int lotLayer) {
        // Build a client-view lot via the network codec (what the client sends)
        FoupContents clientView = FoupContentsCodec.fromPublicView(capacity, waferCount, lotLayer);
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), clientView);
        return stack;
    }

    private static ItemStack buildClientViewFilledFOUP(Item item, int capacity, int waferCount, int lotLayer) {
        // Client view with wafers (still client view because rolls are NO_ROLLS)
        FoupContents clientView = FoupContentsCodec.fromPublicView(capacity, waferCount, lotLayer);
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), clientView);
        return stack;
    }

    private static ItemStack buildOverstackClientView(Item item, int capacity, int waferCount, int lotLayer) {
        // Client view where waferCount > capacity (malformed)
        FoupContents clientView = FoupContentsCodec.fromPublicView(capacity, waferCount, lotLayer);
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), clientView);
        return stack;
    }

    private static ItemStack buildSealedEmptyFOUP(Item item, UUID lotId, int capacity) {
        FoupContents sealed = FoupContents.empty(lotId, capacity);
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), sealed);
        return stack;
    }

    private static ItemStack buildSealedFilledFOUP(Item item, UUID lotId, int capacity, int waferCount) {
        List<Wafer> wafers = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers.add(Wafer.unprocessed());
        }
        FoupContents sealed = new FoupContents(lotId, capacity, wafers);
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.FOUP_CONTENTS.get(), sealed);
        return stack;
    }

    private static void sendCreativeSlotPacket(Connection connection, ServerLevel level, int slot, ItemStack stack) {
        // Use the actual STREAM_CODEC to encode the packet
        // ServerboundSetCreativeModeSlotPacket carries: slot (short), stack (ItemStack), heldItem (boolean)
        // We need to encode it properly via RegistryFriendlyByteBuf
        ByteBuf buf = Unpooled.buffer();
        try {
            var registries = level.registryAccess();
            var friendlyBuf = net.minecraft.network.RegistryFriendlyByteBuf.create(buf, registries, PacketFlow.SERVERBOUND);
            ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.encode(friendlyBuf, new ServerboundSetCreativeModeSlotPacket(slot, stack, false));
            connection.handleSetCreativeModeSlot(friendlyBuf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode/send creative slot packet", e);
        } finally {
            buf.release();
        }
    }

    private static void runInventoryTick(ItemStack stack, ServerLevel level, ServerPlayer player) {
        stack.getItem().inventoryTick(stack, level, player, null);
    }

    // ------------------------------------------------------------------------
    // Positive cases
    // ------------------------------------------------------------------------

    /**
     * Creative player receives empty client-view FOUP Mk1 via creative inventory packet.
     * After inventoryTick, the FOUP must hold a fresh server-generated empty lot with
     * non-placeholder identity and server-configured capacity.
     */
    private static void creativeEmptyMk1StreamRoundTrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-mk1-test");
            int configured = FoupItems.configuredCapacity();

            // Build what the client sends: empty client-view FOUP Mk1 with forged capacity (999)
            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 999, 0, FoupPublicView.EMPTY_LOT_LAYER);
            h.assertTrue(FoupContentsCodec.isClientView(FoupItems.contentsOf(clientStack)), "Precondition: must be client view");
            h.assertValueEqual(999, FoupItems.contentsOf(clientStack).capacity(), "Precondition: client forged capacity");

            // Send via actual packet path
            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            // The packet handler puts the stack in the player's inventory slot 0
            ItemStack serverStack = player.getInventory().getItem(0);
            h.assertTrue(serverStack.is(ModItems.FOUP_MK1.get()), "Server received FOUP Mk1");

            // Run inventoryTick (this is where the fix applies)
            runInventoryTick(serverStack, level, player);

            // Verify: fresh non-placeholder identity, server-configured capacity, empty
            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after != null, "FOUP must have contents after inventoryTick");
            h.assertTrue(!FoupContentsCodec.isClientView(after), "Must not be client view after fix");
            h.assertTrue(!after.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), "Must have fresh server UUID, not client view nil UUID");
            h.assertValueEqual(configured, after.capacity(), "Capacity must be server configured, not client forged");
            h.assertTrue(after.isEmpty(), "Lot must remain empty");
            h.assertTrue(after.run() == null, "Run spec must be null");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Same test for FOUP Mk2.
     */
    private static void creativeEmptyMk2StreamRoundTrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-mk2-test");
            int configured = FoupItems.configuredCapacity();

            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK2.get(), 888, 0, FoupPublicView.EMPTY_LOT_LAYER);
            h.assertTrue(FoupContentsCodec.isClientView(FoupItems.contentsOf(clientStack)), "Precondition: must be client view");

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            h.assertTrue(serverStack.is(ModItems.FOUP_MK2.get()), "Server received FOUP Mk2");

            runInventoryTick(serverStack, level, player);

            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after != null, "FOUP must have contents after inventoryTick");
            h.assertTrue(!FoupContentsCodec.isClientView(after), "Must not be client view after fix");
            h.assertTrue(!after.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), "Must have fresh server UUID");
            h.assertValueEqual(configured, after.capacity(), "Capacity must be server configured");
            h.assertTrue(after.isEmpty(), "Lot must remain empty");
            h.assertTrue(after.run() == null, "Run spec must be null");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Repeated inventoryTick calls on the same stack must be stable (not replace the lot again).
     */
    private static void creativeEmptyRepeatHookStable(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-repeat-test");
            int configured = FoupItems.configuredCapacity();

            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 777, 0, FoupPublicView.EMPTY_LOT_LAYER);
            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);

            // First inventoryTick
            runInventoryTick(serverStack, level, player);
            FoupContents afterFirst = FoupItems.contentsOf(serverStack);
            h.assertTrue(afterFirst != null && !FoupContentsCodec.isClientView(afterFirst), "First tick must produce server lot");
            UUID firstLotId = afterFirst.lotId();
            h.assertValueEqual(configured, afterFirst.capacity(), "First tick capacity");

            // Second inventoryTick
            runInventoryTick(serverStack, level, player);
            FoupContents afterSecond = FoupItems.contentsOf(serverStack);
            h.assertTrue(afterSecond != null && !FoupContentsCodec.isClientView(afterSecond), "Second tick must keep server lot");
            h.assertTrue(afterSecond.lotId().equals(firstLotId), "Lot identity must be stable across repeated ticks");
            h.assertValueEqual(configured, afterSecond.capacity(), "Capacity must remain stable");

            // Third inventoryTick (also via onCraftedPostProcess path)
            serverStack.getItem().onCraftedPostProcess(serverStack, level);
            FoupContents afterThird = FoupItems.contentsOf(serverStack);
            h.assertTrue(afterThird.lotId().equals(firstLotId), "Lot identity stable after onCraftedPostProcess");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Custom name and other components must be preserved when the fix replaces the lot.
     */
    private static void creativeEmptyCustomNamePreserved(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-name-test");

            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 999, 0, FoupPublicView.EMPTY_LOT_LAYER);
            // Add a custom name
            clientStack.set(DataComponents.CUSTOM_NAME, Component.literal("My Special FOUP"));

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            h.assertTrue(serverStack.has(DataComponents.CUSTOM_NAME), "Custom name must survive packet");

            runInventoryTick(serverStack, level, player);

            // Verify custom name preserved
            h.assertTrue(serverStack.has(DataComponents.CUSTOM_NAME), "Custom name must be preserved after fix");
            h.assertTrue(serverStack.get(DataComponents.CUSTOM_NAME).getString().equals("My Special FOUP"), "Custom name content preserved");

            // Verify stack count preserved (should be 1)
            h.assertValueEqual(1, serverStack.getCount(), "Stack count preserved");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * The fixed FOUP must be accepted by the Wafer Slicer and complete a slice run.
     */
    private static void creativeEmptySlicerAccepts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-slicer-test");
            int configured = FoupItems.configuredCapacity();

            // Place a Wafer Slicer
            net.minecraft.core.BlockPos slicerPos = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            h.setBlock(slicerPos, com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines.WAFER_SLICER.get());
            var slicerBE = (com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity) level.getBlockEntity(slicerPos);
            h.assertTrue(slicerBE != null, "Slicer block entity exists");
            slicerBE.serverTick(); // Initialize definition

            // Give player a boule and the fixed FOUP
            player.getInventory().setItem(1, new ItemStack(com.euvcraft.lithmod.mc.registry.ModItems.SILICON_BOULE.get()));

            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 999, 0, FoupPublicView.EMPTY_LOT_LAYER);
            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            // Verify the fix applied
            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after != null && !FoupContentsCodec.isClientView(after), "Fix must apply before slicer test");

            // Manually insert into slicer slots (simulating player action)
            slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.BOULE_SLOT).set(player.getInventory().getItem(1).copy());
            player.getInventory().setItem(1, ItemStack.EMPTY);
            slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.FOUP_SLOT).set(serverStack.copy());
            player.getInventory().setItem(0, ItemStack.EMPTY);

            // Give the slicer plenty of FE
            slicerBE.buffer().insert(1000000, false);

            // Run slicer ticks until complete
            int maxTicks = 2000;
            boolean completed = false;
            for (int i = 0; i < maxTicks; i++) {
                slicerBE.serverTick();
                if (!slicerBE.isSlicing() && slicerBE.sliceProgress() == 0 && !slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.OUTPUT_SLOT).isEmpty()) {
                    completed = true;
                    break;
                }
            }

            h.assertTrue(completed, "Slicer must complete a run with the fixed FOUP");

            // Verify output FOUP has wafers and is a sealed lot
            ItemStack output = slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.OUTPUT_SLOT);
            h.assertTrue(output.is(ModItems.FOUP_MK1.get()), "Output is FOUP Mk1");
            FoupContents outputContents = FoupItems.trustedContentsOf(output);
            h.assertTrue(!FoupContentsCodec.isClientView(outputContents), "Output must be sealed lot");
            h.assertTrue(outputContents.size() > 0, "Output must contain wafers");
            h.assertValueEqual(configured, outputContents.capacity(), "Output capacity matches configured");
            h.assertTrue(outputContents.run() == null, "Slicer output has no run spec");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    // ------------------------------------------------------------------------
    // Negative cases
    // ------------------------------------------------------------------------

    /**
     * Survival player with empty client-view FOUP: must NOT be replaced (initializeIfMissing handles absent, but client view stays).
     */
    private static void survivalEmptyUnchanged(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectSurvival(level, "survival-test");

            ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 999, 0, FoupPublicView.EMPTY_LOT_LAYER);
            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            // In Survival, the client-view lot should remain (initializeIfMissing only handles ABSENT component)
            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after != null, "FOUP must have contents");
            h.assertTrue(FoupContentsCodec.isClientView(after), "Survival: client view must remain untouched");
            h.assertValueEqual(999, after.capacity(), "Survival: client forged capacity stays (but trustedContentsOf will discard)");

            // But trustedContentsOf must still return fresh empty lot
            FoupContents trusted = FoupItems.trustedContentsOf(serverStack);
            h.assertTrue(trusted.isEmpty(), "Trusted read returns empty");
            h.assertValueEqual(FoupItems.configuredCapacity(), trusted.capacity(), "Trusted read uses configured capacity");
            h.assertTrue(!trusted.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), "Trusted read has fresh UUID");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Non-player entity (e.g., ItemEntity, hopper, etc.) with empty client-view FOUP: must NOT be replaced.
     */
    private static void nonplayerEmptyUnchanged(GameTestHelper h) {
        ServerLevel level = h.getLevel();

        ItemStack clientStack = buildClientViewEmptyFOUP(ModItems.FOUP_MK1.get(), 999, 0, FoupPublicView.EMPTY_LOT_LAYER);
        ItemEntity entity = new ItemEntity(level, 0.5, 0.5, 0.5, clientStack);

        // Call inventoryTick with non-player owner
        clientStack.getItem().inventoryTick(clientStack, level, entity, null);

        FoupContents after = FoupItems.contentsOf(clientStack);
        h.assertTrue(after != null, "FOUP must have contents");
        h.assertTrue(FoupContentsCodec.isClientView(after), "Non-player: client view must remain untouched");
        h.assertValueEqual(999, after.capacity(), "Non-player: client forged capacity stays");

        h.succeed();
    }

    /**
     * Creative player with filled client-view FOUP: must NOT be replaced, and must be rejected by slicer.
     */
    private static void filledClientViewRejectedBySlicer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-filled-test");
            int configured = FoupItems.configuredCapacity();

            // Place a Wafer Slicer
            net.minecraft.core.BlockPos slicerPos = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
            h.setBlock(slicerPos, com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines.WAFER_SLICER.get());
            var slicerBE = (com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity) level.getBlockEntity(slicerPos);
            h.assertTrue(slicerBE != null, "Slicer block entity exists");
            slicerBE.serverTick();
            slicerBE.buffer().insert(1000000, false);

            // Filled client-view FOUP (5 wafers)
            ItemStack clientStack = buildClientViewFilledFOUP(ModItems.FOUP_MK1.get(), configured, 5, 1);
            h.assertTrue(FoupContentsCodec.isClientView(FoupItems.contentsOf(clientStack)), "Precondition: client view");
            h.assertValueEqual(5, FoupItems.contentsOf(clientStack).size(), "Precondition: 5 wafers");

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            // Fix must NOT apply (filled client view)
            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(FoupContentsCodec.isClientView(after), "Filled client view must remain untouched by fix");
            h.assertValueEqual(5, after.size(), "Wafer count unchanged");

            // Insert into slicer - must be rejected
            slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.BOULE_SLOT).set(new ItemStack(com.euvcraft.lithmod.mc.registry.ModItems.SILICON_BOULE.get()));
            slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.FOUP_SLOT).set(serverStack.copy());
            player.getInventory().setItem(0, ItemStack.EMPTY);

            slicerBE.serverTick();
            h.assertTrue(!slicerBE.isSlicing(), "Slicer must not start with filled client view");
            h.assertTrue(slicerBE.slot(com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity.FOUP_SLOT).is(ModItems.FOUP_MK1.get()), "FOUP must remain in input slot");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Filled client-view FOUP cannot fabricate wafers via trustedContentsOf.
     */
    private static void filledClientViewCannotFabricateWafers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-filled-trusted-test");

            ItemStack clientStack = buildClientViewFilledFOUP(ModItems.FOUP_MK1.get(), 25, 10, 1);
            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            // trustedContentsOf must discard the client view and return fresh empty lot
            FoupContents trusted = FoupItems.trustedContentsOf(serverStack);
            h.assertTrue(trusted.isEmpty(), "Trusted read of filled client view returns empty lot");
            h.assertValueEqual(FoupItems.configuredCapacity(), trusted.capacity(), "Trusted read uses configured capacity");
            h.assertTrue(!trusted.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), "Trusted read has fresh UUID");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Sealed lot (server-generated) identity and wafer rolls must be exactly preserved.
     */
    private static void sealedLotIdentityPreserved(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-sealed-test");
            int configured = FoupItems.configuredCapacity();
            UUID originalLotId = UUID.randomUUID();

            // Sealed empty lot
            ItemStack sealedStack = buildSealedEmptyFOUP(ModItems.FOUP_MK1.get(), originalLotId, configured);
            h.assertTrue(!FoupContentsCodec.isClientView(FoupItems.contentsOf(sealedStack)), "Precondition: sealed lot");

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, sealedStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after.lotId().equals(originalLotId), "Sealed lot identity exactly preserved");
            h.assertValueEqual(configured, after.capacity(), "Sealed lot capacity preserved");
            h.assertTrue(after.isEmpty(), "Sealed empty lot remains empty");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Sealed lot with wafers: wafer rolls exactly preserved.
     */
    private static void sealedLotWaferRollPreserved(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-sealed-filled-test");
            int configured = FoupItems.configuredCapacity();
            UUID originalLotId = UUID.randomUUID();

            // Sealed lot with 3 wafers with specific rolls
            List<Wafer> wafers = List.of(
                    new Wafer(0, 0.123f, 0),
                    new Wafer(0, 0.456f, 0),
                    new Wafer(0, 0.789f, 0)
            );
            FoupContents sealed = new FoupContents(originalLotId, configured, wafers);
            ItemStack sealedStack = new ItemStack(ModItems.FOUP_MK1.get());
            sealedStack.set(ModDataComponents.FOUP_CONTENTS.get(), sealed);

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, sealedStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(after.lotId().equals(originalLotId), "Lot identity preserved");
            h.assertValueEqual(3, after.size(), "Wafer count preserved");
            h.assertTrue(after.wafers().get(0).cumulativeRoll() == 0.123f, "Wafer 0 roll preserved");
            h.assertTrue(after.wafers().get(1).cumulativeRoll() == 0.456f, "Wafer 1 roll preserved");
            h.assertTrue(after.wafers().get(2).cumulativeRoll() == 0.789f, "Wafer 2 roll preserved");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }

    /**
     * Overstack client view (waferCount > capacity): must remain untouched.
     */
    private static void overstackClientViewUntouched(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connectCreative(level, "creative-overstack-test");

            // Client view with waferCount > capacity (malformed)
            ItemStack clientStack = buildOverstackClientView(ModItems.FOUP_MK1.get(), 10, 25, 0);
            h.assertTrue(FoupContentsCodec.isClientView(FoupItems.contentsOf(clientStack)), "Precondition: client view");
            FoupContents before = FoupItems.contentsOf(clientStack);
            h.assertValueEqual(10, before.capacity(), "Precondition: capacity 10");
            h.assertValueEqual(10, before.size(), "Precondition: clamped to 10 wafers by fromPublicView");

            var connection = player.connection;
            sendCreativeSlotPacket(connection, level, 0, clientStack);

            ItemStack serverStack = player.getInventory().getItem(0);
            runInventoryTick(serverStack, level, player);

            // Must remain untouched (not replaced by fix because it's not empty)
            FoupContents after = FoupItems.contentsOf(serverStack);
            h.assertTrue(FoupContentsCodec.isClientView(after), "Overstack client view must remain untouched");
            h.assertValueEqual(10, after.capacity(), "Capacity unchanged");
            h.assertValueEqual(10, after.size(), "Wafer count unchanged (clamped)");

            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }
}