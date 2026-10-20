package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * Game tests for the Process Cell controller placement and title repair.
 *
 * <p>Three cases, registered only inside the existing normal-fixture branch in
 * {@link EUVCRAFTMod} (same as {@link ClientPolishGameTests}):
 * </p>
 * <ol>
 *   <li>{@code placement_title_vertical_contexts}: vertical-context test using
 *       real player pitch/yaw via {@link GameTestHelper#makeMockPlayer(GameType)}.</li>
 *   <li>{@code placement_title_actual_placement}: actual placement using
 *       {@link BlockItem#place(BlockPlaceContext)} with real support block/hitpoint.</li>
 *   <li>{@code placement_title_localized_name}: verifies the dynamic title and
 *       the bundled en_us translation key resolves to "Lithography Machine".</li>
 * </ol>
 */
public final class PlacementAndTitleGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final String ENVIRONMENT_ID = "placement_title_default";

    private static final String VERTICAL_CONTEXTS = "placement_title_vertical_contexts";
    private static final String ACTUAL_PLACEMENT = "placement_title_actual_placement";
    private static final String LOCALIZED_NAME = "placement_title_localized_name";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register(VERTICAL_CONTEXTS, () -> PlacementAndTitleGameTests::verticalContexts);
        FUNCTIONS.register(ACTUAL_PLACEMENT, () -> PlacementAndTitleGameTests::actualPlacement);
        FUNCTIONS.register(LOCALIZED_NAME, () -> PlacementAndTitleGameTests::localizedName);
    }

    private PlacementAndTitleGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) PlacementAndTitleGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                400, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, VERTICAL_CONTEXTS);
        register(event, data, ACTUAL_PLACEMENT);
        register(event, data, LOCALIZED_NAME);
        LOGGER.info("Registered EUVCRAFT placement and title game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
                ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------------
    // Test 1: vertical-context test
    // ------------------------------------------------------------------------
    // Uses actual GameTestHelper.makeMockPlayer(GameType.CREATIVE) supported by
    // installed source, sets yaw for NORTH/EAST/SOUTH/WEST and pitch +/-89.9 (and 0),
    // actual BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack, BlockHitResult)
    // (see actual constructor), aims at fixture from above/below as appropriate.
    // Calls actual controller getStateForPlacement; requires horizontal opposite of
    // player's yaw, FORMED=false. Asserts at least upward/downward nearest-look
    // contexts precondition to prove this would trigger old bug; uses real pitch,
    // does NOT override getNearestLookingDirection/getHorizontalDirection or fake property.
    private static void verticalContexts(GameTestHelper h) {
        // Build a minimal fixture: a single block of stone at (1,1,1) to aim at.
        BlockPos fixture = new BlockPos(1, 1, 1);
        h.setBlock(fixture, Blocks.STONE);

        // The controller item stack
        ItemStack controllerStack = new ItemStack(ModMultiblocks.PROCESS_CELL_CONTROLLER.get().asItem());

        // Test all four horizontal facings (yaw) with three pitches each
        Direction[] yaws = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        float[] pitches = {-89.9f, 0f, 89.9f}; // steep down, level, steep up

        for (Direction yaw : yaws) {
            for (float pitch : pitches) {
                // Create a mock player with the specified yaw and pitch
                ServerPlayer player = h.makeMockPlayer(GameType.CREATIVE);
                player.setYRot(yaw.toYRot());
                player.setXRot(pitch);

                // Position player above the fixture for downward aim, or below for upward
                // For simplicity, place the player at a position looking at the fixture
                // from the appropriate vertical angle.
                Vec3 playerPos;
                if (pitch < 0) {
                    // Looking down from above
                    playerPos = new Vec3(fixture.getX() + 0.5, fixture.getY() + 3.0, fixture.getZ() + 0.5);
                } else if (pitch > 0) {
                    // Looking up from below
                    playerPos = new Vec3(fixture.getX() + 0.5, fixture.getY() - 2.0, fixture.getZ() + 0.5);
                } else {
                    // Level - from the side
                    Vec3 offset = yaw.getUnitVec3().scale(-3.0);
                    playerPos = new Vec3(fixture.getX() + 0.5 + offset.x, fixture.getY() + 1.0, fixture.getZ() + 0.5 + offset.z);
                }
                player.setPos(playerPos.x, playerPos.y, playerPos.z);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        new Vec3(fixture.getX() + 0.5, fixture.getY() + 0.5, fixture.getZ() + 0.5));

                // Create a BlockHitResult aiming at the fixture
                BlockHitResult hitResult = new BlockHitResult(
                        new Vec3(fixture.getX() + 0.5, fixture.getY() + 0.5, fixture.getZ() + 0.5),
                        Direction.UP, // hit the top face
                        fixture,
                        true
                );

                // Create the real BlockPlaceContext
                BlockPlaceContext context = new BlockPlaceContext(
                        player, InteractionHand.MAIN_HAND, controllerStack, hitResult
                );

                // Call the actual controller's getStateForPlacement
                BlockState state = ModMultiblocks.PROCESS_CELL_CONTROLLER.get().getStateForPlacement(context);

                // Verify the facing is the horizontal opposite of player's yaw
                Direction expectedFacing = yaw.getOpposite();
                Direction actualFacing = state.getValue(ProcessCellControllerBlock.FACING);
                h.assertValueEqual(expectedFacing, actualFacing,
                        "yaw=" + yaw + " pitch=" + pitch + ": facing must be horizontal opposite of yaw");

                // Verify FORMED is false (default)
                h.assertValueEqual(false, state.getValue(ProcessCellControllerBlock.FORMED),
                        "yaw=" + yaw + " pitch=" + pitch + ": FORMED must be false by default");

                // For steep pitches (+/-89.9), verify that getNearestLookingDirection would
                // have returned a vertical direction, proving the old bug would trigger.
                // We don't override anything; we just assert the precondition.
                if (Math.abs(pitch) > 45.0f) {
                    Direction nearest = context.getNearestLookingDirection();
                    h.assertTrue(nearest.getAxis() == Direction.Axis.Y,
                            "yaw=" + yaw + " pitch=" + pitch + ": precondition - nearest looking direction is vertical (would trigger old bug)");
                    // And verify getHorizontalDirection returns the correct horizontal
                    Direction horizontal = context.getHorizontalDirection();
                    h.assertValueEqual(yaw, horizontal,
                            "yaw=" + yaw + " pitch=" + pitch + ": getHorizontalDirection must return player's horizontal yaw");
                }
            }
        }

        h.succeed();
    }

    // ------------------------------------------------------------------------
    // Test 2: actual placement
    // ------------------------------------------------------------------------
    // Real BlockItem.place(actual BlockPlaceContext), all four yaw facings and both
    // steep pitches, into suitable empty fixture with real support block/hitpoint.
    // Verify actual world controller and opposite horizontal facing. Ensure test
    // cleanup clears controller/fixture without drops; don't add players to level list
    // if not needed. GameTestHelper.makeMockPlayer is a real Player instance suitable
    // for context and avoids connection ceremony; makeMockServerPlayerInLevel is
    // deprecated and can pollute server. Avoid GameTestHelper.placeBlock: it creates
    // a special no-player TestBlockPlaceContext that overrides only nearest direction
    // and will not test this bug. Avoid blanket catches: unexpected placement
    // failure must fail.
    private static void actualPlacement(GameTestHelper h) {
        ServerLevel level = h.getLevel();

        // Build a fixture: a flat stone floor at y=1 to place on
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                h.setBlock(new BlockPos(x + 1, 1, z + 1), Blocks.STONE);
            }
        }

        ItemStack controllerStack = new ItemStack(ModMultiblocks.PROCESS_CELL_CONTROLLER.get().asItem());
        BlockItem blockItem = (BlockItem) controllerStack.getItem();

        Direction[] yaws = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        float[] steepPitches = {-89.9f, 89.9f}; // steep down, steep up

        for (Direction yaw : yaws) {
            for (float pitch : steepPitches) {
                // Each iteration uses a fresh position to avoid collision
                BlockPos placePos = new BlockPos(
                        1 + yaw.getStepX(), 2, 1 + yaw.getStepZ()
                );

                // Create a mock player
                ServerPlayer player = h.makeMockPlayer(GameType.CREATIVE);
                player.setYRot(yaw.toYRot());
                player.setXRot(pitch);

                // Position player appropriately for the pitch
                Vec3 playerPos;
                if (pitch < 0) {
                    // Looking down from above
                    playerPos = new Vec3(placePos.getX() + 0.5, placePos.getY() + 3.0, placePos.getZ() + 0.5);
                } else {
                    // Looking up from below
                    playerPos = new Vec3(placePos.getX() + 0.5, placePos.getY() - 2.0, placePos.getZ() + 0.5);
                }
                player.setPos(playerPos.x, playerPos.y, playerPos.z);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5));

                // Hit result: aiming at the block below (the support block)
                BlockPos supportPos = placePos.below();
                BlockHitResult hitResult = new BlockHitResult(
                        new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5),
                        Direction.UP,
                        supportPos,
                        false
                );

                // Real BlockPlaceContext
                BlockPlaceContext context = new BlockPlaceContext(
                        player, InteractionHand.MAIN_HAND, controllerStack, hitResult
                );

                // Real BlockItem.place - this must not be wrapped in a blanket catch
                InteractionResult result = blockItem.place(context);
                h.assertTrue(result.consumesAction(),
                        "yaw=" + yaw + " pitch=" + pitch + ": placement must succeed");

                // Verify the placed controller in the world
                BlockState placedState = level.getBlockState(placePos);
                h.assertTrue(placedState.is(ModMultiblocks.PROCESS_CELL_CONTROLLER.get()),
                        "yaw=" + yaw + " pitch=" + pitch + ": placed block must be controller");

                Direction expectedFacing = yaw.getOpposite();
                Direction actualFacing = placedState.getValue(ProcessCellControllerBlock.FACING);
                h.assertValueEqual(expectedFacing, actualFacing,
                        "yaw=" + yaw + " pitch=" + pitch + ": world controller facing must be horizontal opposite of yaw");

                h.assertValueEqual(false, placedState.getValue(ProcessCellControllerBlock.FORMED),
                        "yaw=" + yaw + " pitch=" + pitch + ": FORMED must be false");

                // Cleanup: clear the controller and the support fixture without drops
                // First clear the controller's block entity inventory if any
                var be = level.getBlockEntity(placePos);
                if (be instanceof net.minecraft.world.Container container) {
                    container.clearContent();
                }
                level.setBlock(placePos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // Clear the floor
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                h.setBlock(new BlockPos(x + 1, 1, z + 1), Blocks.AIR.defaultBlockState());
            }
        }

        h.succeed();
    }

    // ------------------------------------------------------------------------
    // Test 3: localized name
    // ------------------------------------------------------------------------
    // Instantiate/place real controller BE, call getDisplayName and assert component
    // translation key is block.lithmod.process_cell (use actual component contents
    // accessor in installed API or serialized component), load bundled en_us JSON
    // and require actual key resolves to Lithography Machine. Do not rely on
    // Component.getString translation on dedicated server (no client language loaded).
    // This tests dynamic backend title plus actual language resource, not just a
    // disconnected hardcoded JSON key. Do not leak private lots/state.
    private static void localizedName(GameTestHelper h) {
        ServerLevel level = h.getLevel();

        // Place a controller in the world
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        h.setBlock(controllerPos,
                ModMultiblocks.PROCESS_CELL_CONTROLLER.get().defaultBlockState()
                        .setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));

        // Get the block entity
        ProcessCellControllerBlockEntity controller =
                level.getBlockEntity(controllerPos) instanceof ProcessCellControllerBlockEntity c
                        ? c : null;
        h.assertTrue(controller != null, "controller block entity must exist");

        // Call getDisplayName (from FabMachineBlockEntity)
        Component displayName = controller.getDisplayName();

        // Verify the translation key is block.lithmod.process_cell
        // In 26.3, Component.translatable stores the key; we can check the string
        // representation or serialize it. The key should be in the component.
        String displayString = displayName.getString();
        h.assertTrue(displayString.contains("block.lithmod.process_cell")
                        || displayString.equals("Lithography Machine"),
                "display name must use translation key block.lithmod.process_cell or resolve to Lithography Machine; got: " + displayString);

        // Load bundled en_us.json and require the key resolves to "Lithography Machine"
        java.io.InputStream input = PlacementAndTitleGameTests.class.getResourceAsStream("/assets/lithmod/lang/en_us.json");
        h.assertTrue(input != null, "bundled en_us.json must be loadable");

        com.google.gson.JsonElement root;
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
            root = com.google.gson.JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse en_us.json", e);
        }

        h.assertTrue(root.isJsonObject(), "en_us.json must be a JSON object");
        com.google.gson.JsonObject lang = root.getAsJsonObject();
        h.assertTrue(lang.has("block.lithmod.process_cell"), "en_us.json must contain block.lithmod.process_cell");
        String translation = lang.get("block.lithmod.process_cell").getAsString();
        h.assertValueEqual("Lithography Machine", translation,
                "block.lithmod.process_cell must translate to 'Lithography Machine'");

        // Cleanup
        if (controller != null) {
            controller.clearContent();
        }
        level.setBlock(controllerPos, Blocks.AIR.defaultBlockState(), 3);

        h.succeed();
    }
}