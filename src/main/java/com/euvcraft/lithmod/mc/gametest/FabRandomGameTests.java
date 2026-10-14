package com.euvcraft.lithmod.mc.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.euvcraft.lithmod.core.ContextualRng;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabRandomData;
import com.euvcraft.lithmod.mc.FabRandomService;
import com.mojang.serialization.DataResult;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Game tests for the server-side randomness of milestone M0-07.
 *
 * <p>{@code lithmod:fab_random_salt_round_trip} proves that the salt is written to
 * disk with the world and that reloading it reproduces the same rolls, which is what
 * stops a player from re-rolling by reloading a save. {@code
 * lithmod:fab_random_client_side_rejected} proves the server-authority guard
 * rejects a client-side call.</p>
 */
public final class FabRandomGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f0");
    private static final int WAFER_INDEX = 4;
    private static final int STEP_INDEX = 2;
    private static final int ROLLS = 8;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    private static final String ENVIRONMENT_ID = "fab_random_default";
    private static final String SALT_FILE_NAME = "fab_random.dat";

    static {
        FUNCTIONS.register("fab_random_salt_round_trip", () -> FabRandomGameTests::saltRoundTrip);
        FUNCTIONS.register("fab_random_client_side_rejected", () -> FabRandomGameTests::clientSideRejected);
    }

    private FabRandomGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) FabRandomGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, "fab_random_salt_round_trip");
        register(event, data, "fab_random_client_side_rejected");
        LOGGER.info("Registered EUVCRAFT fab random game tests");
    }

    private static void register(RegisterGameTestsEvent event,
        TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * Writes the salt to disk, reads the file back and replays the same context.
     *
     * <p>The salt itself is never read here: it is package private, and the shared
     * salt is proven by comparing rolls instead. Identical rolls across dimensions
     * and across a save/load round trip can only happen if the salt is the same.</p>
     */
    private static void saltRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FabRandomData data = FabRandomService.data(level);

        ContextualRng before = data.rngFor(LOT, WAFER_INDEX, STEP_INDEX);
        double[] expected = new double[ROLLS];
        for (int i = 0; i < ROLLS; i++) {
            expected[i] = before.nextDouble();
        }

        // Every dimension of the save must resolve to the same salt.
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) {
            double fromNether = FabRandomService.rngFor(nether, LOT, WAFER_INDEX, STEP_INDEX).nextDouble();
            helper.assertTrue(fromNether == expected[0],
                "the nether does not share the overworld salt");
        }

        // Force the real write, then read the file back the way the game would.
        data.setDirty();
        FabRandomService.storage(level).saveAndJoin();

        Path file = findSaltFile(level.getServer().getWorldPath(LevelResource.ROOT));
        if (file == null) {
            helper.fail("no " + SALT_FILE_NAME + " was written under the world folder");
            return;
        }
        LOGGER.info("Fab random salt stored at {}", file);

        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            helper.fail("could not read " + file + ": " + e);
            return;
        }

        Tag payload = root.get("data");
        if (payload == null) {
            helper.fail(file + " has no data compound");
            return;
        }

        DataResult<FabRandomData> decoded = FabRandomData.CODEC.parse(NbtOps.INSTANCE, payload);
        Optional<FabRandomData> reloaded = decoded.result();
        if (reloaded.isEmpty()) {
            helper.fail("could not decode " + file + ": " + decoded.error());
            return;
        }

        ContextualRng after = reloaded.get().rngFor(LOT, WAFER_INDEX, STEP_INDEX);
        for (int i = 0; i < ROLLS; i++) {
            double replayed = after.nextDouble();
            helper.assertTrue(replayed == expected[i],
                "roll " + i + " changed after a save/load round trip: " + expected[i] + " -> " + replayed);
        }

        helper.succeed();
    }

    /**
     * Proves that the server-authority guard rejects a client-side request and that a
     * real server level is accepted.
     */
    private static void clientSideRejected(GameTestHelper helper) {
        boolean clientThrew = false;
        try {
            FabRandomService.requireServerSide(true, "minecraft:overworld (client)");
        } catch (IllegalStateException expected) {
            clientThrew = true;
        }
        helper.assertTrue(clientThrew, "a client-side level did not throw");

        boolean nullThrew = false;
        try {
            FabRandomService.requireServerSide((Level) null);
        } catch (IllegalArgumentException expected) {
            nullThrew = true;
        }
        helper.assertTrue(nullThrew, "a null level did not throw");

        ServerLevel level = helper.getLevel();
        helper.assertTrue(FabRandomService.requireServerSide(level) == level,
            "the server level was not accepted");

        double roll = FabRandomService.rngFor(level, LOT, WAFER_INDEX, STEP_INDEX).nextDouble();
        helper.assertTrue(roll > 0.0 && roll <= 1.0, "roll outside (0, 1]: " + roll);

        helper.succeed();
    }

    /** Finds the saved data file anywhere under the world folder. */
    private static Path findSaltFile(Path worldRoot) {
        try (Stream<Path> paths = Files.walk(worldRoot, 12)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(SALT_FILE_NAME))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            LOGGER.error("Could not search {} for {}", worldRoot, SALT_FILE_NAME, e);
            return null;
        }
    }
}