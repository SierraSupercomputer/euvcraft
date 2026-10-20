package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for the Creative FOUP client-polish repair.
 *
 * <p>Every case drives the real path: an actual {@link ServerboundSetCreativeModeSlotPacket}
 * encoded and decoded through its {@code STREAM_CODEC} on a
 * {@link RegistryFriendlyByteBuf}, handled by the real Connection packet listener, then
 * the repaired stack ticked through the real {@code FoupItem#inventoryTick} and, for the
 * positive case, fed into a real {@link WaferSlicerBlockEntity} run.</p>
 *
 * <p>Three cases, registered only inside the existing normal-fixture branch in
 * {@link EUVCRAFTMod}: the creative empty round trip (Mk1/Mk2 plus a real slicer run),
 * the untrusted guards, and direct sealed-data preservation.</p>
 */
public final class ClientPolishGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final String ENVIRONMENT_ID = "client_polish_default";

    private static final String EMPTY_ROUND_TRIP = "client_polish_creative_empty_roundtrip";
    private static final String UNTRUSTED_GUARDS = "client_polish_creative_untrusted_guards";
    private static final String SEALED_PRESERVATION = "client_polish_creative_sealed_preservation";

    /** Where the sliced machine is placed. */
    private static final BlockPos SLICER = new BlockPos(1, 1, 1);

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register(EMPTY_ROUND_TRIP, () -> ClientPolishGameTests::creativeEmptyRoundTrip);
        FUNCTIONS.register(UNTRUSTED_GUARDS, () -> ClientPolishGameTests::untrustedGuards);
        FUNCTIONS.register(SEALED_PRESERVATION, () -> ClientPolishGameTests::sealedPreservation);
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
                400, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, EMPTY_ROUND_TRIP);
        register(event, data, UNTRUSTED_GUARDS);
        register(event, data, SEALED_PRESERVATION);
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

    private static ServerPlayer connect(ServerLevel level, GameType mode) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "client-polish-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {};
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        player.gameMode.setLevel(level);
        return player;
    }

    /**
     * Runs the real creative-slot packet: encodes it, decodes it again off the wire and
     * hands the decoded packet to the real Connection packet listener (slot 36 is the
     * valid server slot of inventory container slot 0).
     */
    private static void handleCreativeSlot(ServerPlayer player, ItemStack sent) {
        ServerLevel level = player.level();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.encode(buf,
                    new ServerboundSetCreativeModeSlotPacket(36, sent));
            ServerboundSetCreativeModeSlotPacket decoded = ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.decode(buf);
            player.connection.handleSetCreativeModeSlot(decoded);
        } finally {
            buf.release();
        }
    }

    /**
     * What a client reconstructs from the network form: encodes and decodes the packet
     * without invoking the handler, and returns the decoded stack, whose lot is now the
     * untrusted client view ({@code fromPublicView} is the codec's own reconstruction).
     */
    private static ItemStack deconstructedClientView(ServerLevel level, ItemStack sent) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.encode(buf,
                    new ServerboundSetCreativeModeSlotPacket(36, sent));
            ServerboundSetCreativeModeSlotPacket decoded = ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.decode(buf);
            return decoded.itemStack();
        } finally {
            buf.release();
        }
    }

    private static void runInventoryTick(ItemStack stack, ServerLevel level, Entity owner) {
        stack.getItem().inventoryTick(stack, level, owner, null);
    }

    private static void cleanupPlayer(ServerLevel level, ServerPlayer player) {
        if (player != null) {
            player.getInventory().clearContent();
            player.closeContainer();
            level.getServer().getPlayerList().remove(player);
        }
    }

    /** Replaces the fixture position with a freshly placed slicer. */
    private static void placeSlicer(GameTestHelper h, BlockPos relative) {
        clearFixture(h, relative);
        h.setBlock(relative, SiliconMachines.WAFER_SLICER.get());
    }

    /** Clears a fixture position: empty its container first, then set air. */
    private static void clearFixture(GameTestHelper h, BlockPos relative) {
        var be = h.getLevel().getBlockEntity(h.absolutePos(relative));
        if (be instanceof net.minecraft.world.Container items) {
            items.clearContent();
        }
        h.setBlock(relative, Blocks.AIR);
    }

    // ------------------------------------------------------------------------
    // Positive case: the creative empty round trip, Mk1 and Mk2, then a real slicer
    // ------------------------------------------------------------------------

    private static void creativeEmptyRoundTrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connect(level, GameType.CREATIVE);
            int configured = FoupItems.configuredCapacity();
            Item[] foupTypes = {ModItems.FOUP_MK1.get(), ModItems.FOUP_MK2.get()};
            int[] forged = {999, 888};

            for (int i = 0; i < foupTypes.length; i++) {
                Item mk = foupTypes[i];
                String label = i == 0 ? "Mk1" : "Mk2";

                // The client sends a real sealed empty lot (forged capacity 999/888 and a custom name);
                // the packet round trip is what turns it into the untrusted client view on the server.
                ItemStack sent = FoupItems.with(mk, FoupContents.empty(UUID.randomUUID(), forged[i]));
                sent.set(DataComponents.CUSTOM_NAME, Component.literal("creative-polish-foup"));
                player.getInventory().setItem(0, ItemStack.EMPTY);
                handleCreativeSlot(player, sent);

                ItemStack serverStack = player.getInventory().getItem(0);
                h.assertTrue(serverStack.is(mk), label + ": the packet placed a real FOUP on the server");
                h.assertValueEqual(1, serverStack.getCount(), label + ": count arrives as 1");
                FoupContents clientView = FoupItems.contentsOf(serverStack);
                h.assertTrue(clientView != null, label + ": the packet view carries a lot component");
                h.assertTrue(FoupContentsCodec.isClientView(clientView), label + ": the server stack is initially the client view");
                h.assertValueEqual(forged[i], clientView.capacity(), label + ": forged client capacity survives the wire");
                h.assertTrue(clientView.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), label + ": nil placeholder lot id");

                // The repair hook tick.
                runInventoryTick(serverStack, level, player);
                FoupContents repaired = FoupItems.contentsOf(serverStack);
                h.assertTrue(repaired != null && !FoupContentsCodec.isClientView(repaired), label + ": repaired to a sealed lot");
                UUID repairedId = repaired.lotId();
                h.assertFalse(repairedId.equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID), label + ": fresh non-placeholder lot id");
                h.assertValueEqual(configured, repaired.capacity(), label + ": server capacity, not the forged one");
                h.assertTrue(repaired.isEmpty() && repaired.run() == null, label + ": still empty with no run spec");
                h.assertTrue(serverStack.has(DataComponents.CUSTOM_NAME), label + ": custom name preserved");
                h.assertTrue("creative-polish-foup".equals(serverStack.get(DataComponents.CUSTOM_NAME).getString()), label + ": custom name content preserved");

                // A repeated hook call must not repair (and re-roll) the lot again.
                runInventoryTick(serverStack, level, player);
                h.assertTrue(FoupItems.contentsOf(serverStack).lotId().equals(repairedId), label + ": repeated hook is stable");
                h.assertTrue(WaferSlicerBlockEntity.acceptedFoup(serverStack) != null, label + ": the repaired FOUP is accepted as a machine input");

                // Real slicer end to end on the loaded data definition.
                placeSlicer(h, SLICER);
                WaferSlicerBlockEntity slicer = h.getLevel().getBlockEntity(h.absolutePos(SLICER)) instanceof WaferSlicerBlockEntity s ? s : null;
                h.assertTrue(slicer != null && slicer.definition().isPresent(), label + ": a real slicer with a loaded definition");
                h.assertTrue(slicer.sliceTicks() > 0 && slicer.energyPerTick() > 0, label + ": data-driven run parameters");
                int perTick = slicer.energyPerTick();
                int ticks = slicer.sliceTicks();
                slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
                slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, serverStack.copy());
                slicer.setStoredEnergy(perTick * (ticks + 2));
                long energyBefore = slicer.buffer().getAmountAsLong();
                for (int tick = 0; tick < ticks + 5; tick++) {
                    slicer.serverTick();
                }
                long spent = energyBefore - slicer.buffer().getAmountAsLong();
                h.assertValueEqual(perTick * (long) ticks, spent, label + ": spends exactly per_tick x slice_ticks");
                h.assertValueEqual(6000L, spent, label + ": the loaded 30 FE x 200 tick definition debits exactly 6000 FE");

                ItemStack output = slicer.outputSlot();
                h.assertFalse(output.isEmpty(), label + ": the slicer produced an output FOUP");
                h.assertTrue(output.is(mk), label + ": the output is the same FOUP type");
                FoupContents lot = FoupItems.trustedContentsOf(output);
                h.assertTrue(!FoupContentsCodec.isClientView(lot), label + ": the output is a trusted sealed lot");
                h.assertFalse(lot.lotId().equals(FoupContentsCodec.CLIENT_VIEW_LOT_ID) || lot.lotId().equals(repairedId), label + ": a fresh new lot id");
                h.assertValueEqual(configured, lot.capacity(), label + ": the forged capacity never reached the output");
                h.assertValueEqual(EUVCRAFTMod.settings().wafersPerBoule(), lot.size(), label + ": one boule's worth of wafers");
                h.assertValueEqual(25, lot.size(), label + ": exactly 25 wafers per the loaded data");
                h.assertTrue(lot.run() == null, label + ": a fresh slice carries no run spec");
                for (Wafer wafer : lot.wafers()) {
                    h.assertTrue(wafer.isUnprocessed(), label + ": every freshly sliced wafer is unprocessed");
                }
                h.assertTrue(slicer.slot(WaferSlicerBlockEntity.FOUP_SLOT).isEmpty(), label + ": the input FOUP was consumed");
                h.assertTrue(slicer.slot(WaferSlicerBlockEntity.BOULE_SLOT).isEmpty(), label + ": exactly the one boule was consumed");
            }
            h.succeed();
        } finally {
            try {
                clearFixture(h, SLICER);
            } finally {
                cleanupPlayer(level, player);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Negative case: every untrusted shape the hook must refuse
    // ------------------------------------------------------------------------

    private static void untrustedGuards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer creative = null;
        ServerPlayer survival = null;
        try {
            creative = connect(level, GameType.CREATIVE);
            survival = connect(level, GameType.SURVIVAL);

            // The shared empty client view, built through the real packet decode.
            ItemStack emptyView = deconstructedClientView(level,
                    FoupItems.with(ModItems.FOUP_MK1.get(), FoupContents.empty(UUID.randomUUID(), 999)));
            h.assertTrue(FoupContentsCodec.isClientView(FoupItems.contentsOf(emptyView)), "the packet decode produced a client view");
            h.assertFalse(FoupItems.hasSealedContents(emptyView), "the client view is not sealed");

            // Survival: the creative-slot packet itself is refused, and a held empty client
            // view stays a client view (its capacity stays the client's forged one).
            handleCreativeSlot(survival, FoupItems.with(ModItems.FOUP_MK1.get(), FoupContents.empty(UUID.randomUUID(), 999)));
            h.assertTrue(survival.getInventory().getItem(0).isEmpty(), "the packet handler refused the Survival player");
            survival.getInventory().setItem(0, emptyView.copy());
            ItemStack held = survival.getInventory().getItem(0);
            runInventoryTick(held, level, survival);
            FoupContents inSurvival = FoupItems.contentsOf(held);
            h.assertTrue(inSurvival != null, "survival keeps a component (this is not a stale absence)");
            h.assertTrue(FoupContentsCodec.isClientView(inSurvival), "survival: the empty client view stays a client view");
            h.assertFalse(FoupItems.hasSealedContents(held), "survival: the client view never becomes sealed");
            h.assertValueEqual(999, inSurvival.capacity(), "survival: the forged capacity is untouched by the hook");

            // Non-player owner: an ItemEntity holding an empty client view.
            ItemStack dropped = emptyView.copy();
            BlockPos dropPos = h.absolutePos(new BlockPos(1, 1, 1));
            ItemEntity drop = new ItemEntity(level,
                    dropPos.getX() + 0.5, dropPos.getY() + 1.0, dropPos.getZ() + 0.5, dropped);
            runInventoryTick(dropped, level, drop);
            FoupContents inDrop = FoupItems.contentsOf(dropped);
            h.assertTrue(inDrop != null && FoupContentsCodec.isClientView(inDrop), "an ItemEntity owner never gets the repair");
            h.assertFalse(FoupItems.hasSealedContents(dropped), "an ItemEntity owner's client view stays unsealed");

            // Non-empty packet view: a real sealed lot with wafers round-trips into an
            // untrusted view that the creative hook never repairs.
            ItemStack sealedWafers = FoupItems.with(ModItems.FOUP_MK1.get(),
                    new FoupContents(UUID.randomUUID(), 25, List.of(new Wafer(1, 0.37f, 0), new Wafer(2, 0.71f, 0))));
            handleCreativeSlot(creative, sealedWafers);
            ItemStack packed = creative.getInventory().getItem(0);
            h.assertTrue(FoupItems.isClientView(packed), "a sealed lot with wafers arrives on the server as a client view");
            FoupContents packedLot = FoupItems.contentsOf(packed);
            h.assertTrue(packedLot != null && packedLot.size() == 2, "the packet view keeps the public wafer count");
            h.assertFalse(packedLot.lotId().equals(FoupItems.contentsOf(sealedWafers).lotId()), "the private lot id is never preserved through the packet");
            runInventoryTick(packed, level, creative);
            h.assertTrue(FoupItems.isClientView(packed), "the creative hook does not repair a non-empty packet view");
            h.assertTrue(FoupItems.contentsOf(packed).size() == 2, "the packet view keeps its wafer count through the hook");
            h.assertTrue(WaferSlicerBlockEntity.acceptedFoup(packed) == null, "the untrusted packet view is never accepted by the slicer");

            // Count 2 on an empty client view: a malformed lifecycle guard, built directly
            // (the packet codec itself refuses an oversized stack, so it never went through it).
            ItemStack two = emptyView.copyWithCount(2);
            creative.getInventory().setItem(0, two);
            ItemStack counted = creative.getInventory().getItem(0);
            h.assertValueEqual(2, counted.getCount(), "precondition: a count-2 empty client view");
            runInventoryTick(counted, level, creative);
            h.assertValueEqual(2, counted.getCount(), "a count-2 client view is unchanged");
            h.assertTrue(FoupItems.isClientView(counted), "a count-2 empty client view is not repaired");
            h.assertFalse(FoupItems.hasSealedContents(counted), "a count-2 empty client view never becomes sealed");
            h.succeed();
        } finally {
            cleanupPlayer(level, creative);
            cleanupPlayer(level, survival);
        }
    }

    // ------------------------------------------------------------------------
    // Negative control: real sealed data survives the lifecycle untouched
    // ------------------------------------------------------------------------

    private static void sealedPreservation(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = null;
        try {
            player = connect(level, GameType.CREATIVE);

            // Sealed empty and filled lots, held DIRECTLY in the inventory: no packet round
            // trip, which would destroy exactly the private values this controls on.
            UUID emptyId = UUID.randomUUID();
            UUID filledId = UUID.randomUUID();
            ItemStack sealedEmpty = FoupItems.with(ModItems.FOUP_MK1.get(), FoupContents.empty(emptyId, 25));
            ItemStack sealedFilled = FoupItems.with(ModItems.FOUP_MK2.get(),
                    new FoupContents(filledId, 25, List.of(
                            new Wafer(1, 0.37f, 0), new Wafer(2, 0.71f, 0), new Wafer(3, 0.91f, 0))));
            player.getInventory().setItem(0, sealedEmpty);
            player.getInventory().setItem(1, sealedFilled);
            FoupContents storedEmpty = FoupItems.contentsOf(sealedEmpty);
            FoupContents storedFilled = FoupItems.contentsOf(sealedFilled);
            h.assertTrue(!FoupContentsCodec.isClientView(storedEmpty) && !FoupContentsCodec.isClientView(storedFilled), "both lots start sealed");

            runInventoryTick(player.getInventory().getItem(0), level, player);
            runInventoryTick(player.getInventory().getItem(1), level, player);

            FoupContents nowEmpty = FoupItems.contentsOf(sealedEmpty);
            FoupContents nowFilled = FoupItems.contentsOf(sealedFilled);
            h.assertTrue(nowEmpty == storedEmpty, "the sealed empty lot keeps its component identity");
            h.assertTrue(nowFilled == storedFilled, "the sealed filled lot keeps its component identity");
            h.assertTrue(nowEmpty.isEmpty() && nowEmpty.lotLayer() == 0, "the sealed empty lot stays empty at layer 0");
            h.assertTrue(nowEmpty.lotId().equals(emptyId), "the sealed empty lot keeps its server uuid");
            h.assertTrue(nowFilled.lotId().equals(filledId), "the sealed filled lot keeps its server uuid");
            h.assertValueEqual(3, nowFilled.size(), "the sealed filled lot keeps every wafer");
            h.assertValueEqual(1, nowFilled.lotLayer(), "the sealed filled lot keeps its lowest layer");
            h.assertTrue(nowFilled.wafers().get(0).cumulativeRoll() == 0.37f, "hidden roll 0 survives the tick");
            h.assertTrue(nowFilled.wafers().get(1).cumulativeRoll() == 0.71f, "hidden roll 1 survives the tick");
            h.assertTrue(nowFilled.wafers().get(2).cumulativeRoll() == 0.91f, "hidden roll 2 survives the tick");
            h.assertFalse(FoupContentsCodec.isClientView(nowEmpty) || FoupContentsCodec.isClientView(nowFilled), "both sealed lots stay server-trusted");
            h.succeed();
        } finally {
            cleanupPlayer(level, player);
        }
    }
}
