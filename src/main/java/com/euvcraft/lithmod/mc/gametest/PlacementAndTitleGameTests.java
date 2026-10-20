package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellOperatingPanelBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellOperatingPanelBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Game tests for the Process Cell controller placement and title repair.
 *
 * <p>Three cases, registered only inside the existing normal-fixture branch in
 * {@link EUVCRAFTMod} (same as {@link ClientPolishGameTests}): vertical
 * placement contexts driven by real player yaw/pitch, real
 * {@link BlockItem#place(BlockPlaceContext)} placements onto the same fixture,
 * and the controller's dynamic localized title against the bundled en_us
 * resource. Every mock player is a real {@link Player} from
 * {@link GameTestHelper#makeMockPlayer(GameType)}; rotation is set with
 * {@code setYRot}/{@code setXRot} only and is never overwritten by
 * {@code lookAt}.</p>
 */
public final class PlacementAndTitleGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;

    private static final String ENVIRONMENT_ID = "placement_title_default";

    private static final String VERTICAL_CONTEXTS = "placement_title_vertical_contexts";
    private static final String ACTUAL_PLACEMENT = "placement_title_actual_placement";
    private static final String LOCALIZED_NAME = "placement_title_localized_name";
    private static final String OPERATOR_PANEL_HORIZONTAL_PLACEMENT = "operator_panel_horizontal_placement";

    /** The language resource bundled with the mod. */
    private static final String LANGUAGE_PATH = "/assets/lithmod/lang/en_us.json";
    /** The translation key the Process Cell controller's display name must use. */
    private static final String CONTROLLER_KEY = "block.lithmod.process_cell";

    /** Fixture: one supporting stone, relative to the test structure. */
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);
    /** Fixture: the air cell the controller is placed in, directly above the stone. */
    private static final BlockPos TARGET = new BlockPos(1, 2, 1);
    /** The controller position for the title case, relative to the test structure. */
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);
    /** The four horizontal yaws under test. */
    private static final Direction[] YAWS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register(VERTICAL_CONTEXTS, () -> PlacementAndTitleGameTests::verticalContexts);
        FUNCTIONS.register(ACTUAL_PLACEMENT, () -> PlacementAndTitleGameTests::actualPlacement);
        FUNCTIONS.register(LOCALIZED_NAME, () -> PlacementAndTitleGameTests::localizedName);
        FUNCTIONS.register(OPERATOR_PANEL_HORIZONTAL_PLACEMENT, () -> PlacementAndTitleGameTests::operatorPanelHorizontalPlacement);
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
        register(event, data, OPERATOR_PANEL_HORIZONTAL_PLACEMENT);
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
    // shared helper
    // ------------------------------------------------------------------------

    /**
     * Builds one real placement context against the fixture: sets only the player's
     * yaw and pitch ({@code lookAt} would overwrite them, so it is never called),
     * positions the player clear of the target cell's hitbox, puts a freshly built
     * controller stack in the main hand and aims a hit on the support stone's top
     * face. Both block arguments are absolute world coordinates; the player's
     * horizontal direction equal to the requested yaw is asserted so a stale or
     * corrected rotation cannot pass unnoticed.
     */
    private static BlockPlaceContext placementContext(GameTestHelper h, Player player,
            Direction yaw, float pitch, BlockPos targetAbs, BlockPos supportAbs) {
        player.setYRot(yaw.toYRot());
        player.setXRot(pitch);
        // Stand beside the fixture along the requested yaw so the player's own box
        // never intersects the cell the controller is placed into.
        double x = supportAbs.getX() + 0.5 + yaw.getStepX() * 2.0;
        double z = supportAbs.getZ() + 0.5 + yaw.getStepZ() * 2.0;
        // +89.9 looks down (player comes from above the air cell); -89.9 looks up
        // (player comes from below the supporting stone).
        double y = pitch > 0 ? targetAbs.getY() + 2.5 : supportAbs.getY() - 2.0;
        player.setPos(x, y, z);
        h.assertValueEqual(yaw, player.getDirection(),
                "the mock player's horizontal direction must equal the requested yaw (yaw " + yaw + ", pitch " + pitch + ")");
        ItemStack stack = new ItemStack(ModItems.PROCESS_CELL_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                new Vec3(supportAbs.getX() + 0.5, supportAbs.getY() + 1.0, supportAbs.getZ() + 0.5),
                Direction.UP, supportAbs, true);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
    }

    /**
     * Empties a placed controller's inventory through the real block entity (read
     * at the absolute position) before the block itself is cleared, so the removal
     * drops nothing.
     */
    private static void clearPlacedController(GameTestHelper h, ServerLevel level, BlockPos relativeTarget) {
        if (level.getBlockEntity(h.absolutePos(relativeTarget)) instanceof FabMachineBlockEntity machine) {
            machine.clearContent();
        }
        h.setBlock(relativeTarget, Blocks.AIR.defaultBlockState());
    }

    // ------------------------------------------------------------------------
    // Test 1: placement title vertical contexts
    // ------------------------------------------------------------------------

    /**
     * Twelve real contexts: four yaws at pitches -89.9, 0 and +89.9. A steep pitch
     * must make the nearest looking direction vertical (seen up and seen down both)
     * while {@code getStateForPlacement} still uses the horizontal opposite of the
     * yaw; level pitch behaves identically to the steep ones.
     */
    private static void verticalContexts(GameTestHelper h) {
        h.setBlock(SUPPORT, Blocks.STONE);
        try {
            for (Direction yaw : YAWS) {
                for (float pitch : new float[] {-89.9f, 0f, 89.9f}) {
                    Player player = h.makeMockPlayer(GameType.CREATIVE);
                    BlockPlaceContext context = placementContext(h, player, yaw, pitch,
                            h.absolutePos(TARGET), h.absolutePos(SUPPORT));
                    BlockState state = ModMultiblocks.PROCESS_CELL_CONTROLLER.get().getStateForPlacement(context);
                    h.assertValueEqual(yaw.getOpposite(), state.getValue(ProcessCellControllerBlock.FACING),
                            "facing must be the horizontal opposite of the player yaw (yaw " + yaw + ", pitch " + pitch + ")");
                    h.assertFalse(state.getValue(ProcessCellControllerBlock.FORMED),
                            "a freshly built controller state must not be formed (yaw " + yaw + ", pitch " + pitch + ")");
                    if (Math.abs(pitch) > 45.0f) {
                        Direction nearest = context.getNearestLookingDirection();
                        h.assertTrue(nearest.getAxis() == Direction.Axis.Y,
                                "a steep pitch must make the nearest looking direction vertical, saw " + nearest
                                        + " (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertValueEqual(pitch > 0 ? Direction.DOWN : Direction.UP, nearest,
                                "the nearest looking direction must follow the pitch sign (yaw " + yaw + ", pitch " + pitch + ")");
                    }
                }
            }
        } finally {
            h.setBlock(SUPPORT, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------
    // Test 2: placement title actual placement
    // ------------------------------------------------------------------------

    /**
     * Real {@code BlockItem.place} with a real support stone and hitpoint, four
     * yaws at both steep pitches, always into the same air target above the stone.
     * Each iteration is cleared in a finally block even when an assertion fails:
     * the placed block entity's inventory is emptied before the block goes away,
     * so nothing drops and no state leaks to the next iteration.
     */
    private static void actualPlacement(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockItem controllerItem = ModItems.PROCESS_CELL_CONTROLLER.get();
        h.setBlock(SUPPORT, Blocks.STONE);
        try {
            for (Direction yaw : YAWS) {
                for (float pitch : new float[] {-89.9f, 89.9f}) {
                    try {
                        Player player = h.makeMockPlayer(GameType.CREATIVE);
                        BlockPlaceContext context = placementContext(h, player, yaw, pitch,
                                h.absolutePos(TARGET), h.absolutePos(SUPPORT));
                        InteractionResult result = controllerItem.place(context);
                        h.assertTrue(result.consumesAction(),
                                "placing the controller item must succeed (yaw " + yaw + ", pitch " + pitch + "), got " + result);
                        BlockState placed = level.getBlockState(h.absolutePos(TARGET));
                        h.assertTrue(placed.is(ModMultiblocks.PROCESS_CELL_CONTROLLER.get()),
                                "the world block at the target must be the controller (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertValueEqual(yaw.getOpposite(), placed.getValue(ProcessCellControllerBlock.FACING),
                                "the placed controller must face the horizontal opposite of the player yaw (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertFalse(placed.getValue(ProcessCellControllerBlock.FORMED),
                                "a placed controller must not be formed (yaw " + yaw + ", pitch " + pitch + ")");
                    } finally {
                        clearPlacedController(h, level, TARGET);
                    }
                }
            }
        } finally {
            h.setBlock(SUPPORT, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------
    // Test 3: placement title localized name
    // ------------------------------------------------------------------------

    /**
     * The placed controller's real {@code getDisplayName} must carry the
     * {@code block.lithmod.process_cell} translation key, and the bundled en_us
     * resource must resolve that exact key to "Lithography Machine". The key is
     * read off the component's translatable contents, never through
     * {@code getString}, which cannot resolve on a dedicated server.
     */
    private static void localizedName(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(CONTROLLER, ModMultiblocks.PROCESS_CELL_CONTROLLER.get().defaultBlockState()
                .setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
        try {
            ProcessCellControllerBlockEntity controller =
                    level.getBlockEntity(h.absolutePos(CONTROLLER)) instanceof ProcessCellControllerBlockEntity blockEntity
                            ? blockEntity : null;
            h.assertTrue(controller != null, "placing the controller must create its block entity");

            Component displayName = controller.getDisplayName();
            h.assertTrue(displayName.getContents() instanceof TranslatableContents,
                    "the controller display name must be a translatable component: " + displayName);
            TranslatableContents contents = (TranslatableContents) displayName.getContents();
            h.assertValueEqual(CONTROLLER_KEY, contents.getKey(),
                    "the dynamic display name must use the " + CONTROLLER_KEY + " key");

            InputStream input = PlacementAndTitleGameTests.class.getResourceAsStream(LANGUAGE_PATH);
            h.assertTrue(input != null, "the bundled en_us.json must be on the classpath");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                h.assertTrue(root.isJsonObject(), "en_us.json must be a JSON object");
                JsonObject lang = root.getAsJsonObject();
                h.assertTrue(lang.has(CONTROLLER_KEY), "en_us.json must contain " + CONTROLLER_KEY);
                h.assertValueEqual("Lithography Machine", lang.get(CONTROLLER_KEY).getAsString(),
                        CONTROLLER_KEY + " must translate to Lithography Machine");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read the bundled machine-title translation", exception);
        } finally {
            clearPlacedController(h, level, CONTROLLER);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------
    // Test 4: operator panel horizontal placement
    // ------------------------------------------------------------------------

    /**
     * Tests the operating panel's horizontal placement behavior:
     * - Steep up/down pitch with all four yaws
     * - Checks that the new FACING property is horizontal (copied from controller below when present,
     *   otherwise opposite player yaw)
     * - Uses real BlockItem.place for operator steep pitch
     * - Reuses existing placement helper patterns
     */
    private static void operatorPanelHorizontalPlacement(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockItem panelItem = ModItems.PROCESS_CELL_OPERATING_PANEL.get();

        // Test 1: Panel placed with controller directly below (copies controller's facing)
        h.setBlock(SUPPORT, Blocks.STONE);
        h.setBlock(CONTROLLER, ModMultiblocks.PROCESS_CELL_CONTROLLER.get().defaultBlockState()
            .setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
        try {
            for (Direction yaw : YAWS) {
                for (float pitch : new float[] {-89.9f, 89.9f}) {
                    try {
                        Player player = h.makeMockPlayer(GameType.CREATIVE);
                        BlockPlaceContext context = placementContext(h, player, yaw, pitch,
                                h.absolutePos(TARGET), h.absolutePos(SUPPORT));
                        InteractionResult result = panelItem.place(context);
                        h.assertTrue(result.consumesAction(),
                                "placing the operator panel must succeed (yaw " + yaw + ", pitch " + pitch + "), got " + result);
                        BlockState placed = level.getBlockState(h.absolutePos(TARGET));
                        h.assertTrue(placed.is(ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get()),
                                "the world block at the target must be the operator panel (yaw " + yaw + ", pitch " + pitch + ")");
                        // FACING should be copied from controller below (NORTH), not from player yaw
                        h.assertValueEqual(Direction.NORTH, placed.getValue(ProcessCellOperatingPanelBlock.FACING),
                                "operator panel FACING must copy controller below (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertFalse(placed.getValue(ProcessCellOperatingPanelBlock.FORMED),
                                "a placed operator panel must not be formed (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertValueEqual(0, placed.getValue(ProcessCellOperatingPanelBlock.PART),
                                "a placed operator panel must have PART=0 (yaw " + yaw + ", pitch " + pitch + ")");
                    } finally {
                        clearPlacedPanel(h, level, TARGET);
                    }
                }
            }
        } finally {
            h.setBlock(CONTROLLER, Blocks.AIR.defaultBlockState());
            h.setBlock(SUPPORT, Blocks.AIR.defaultBlockState());
        }

        // Test 2: Panel placed WITHOUT controller below (uses horizontal opposite of player yaw)
        h.setBlock(SUPPORT, Blocks.STONE);
        try {
            for (Direction yaw : YAWS) {
                for (float pitch : new float[] {-89.9f, 89.9f}) {
                    try {
                        Player player = h.makeMockPlayer(GameType.CREATIVE);
                        BlockPlaceContext context = placementContext(h, player, yaw, pitch,
                                h.absolutePos(TARGET), h.absolutePos(SUPPORT));
                        InteractionResult result = panelItem.place(context);
                        h.assertTrue(result.consumesAction(),
                                "placing the operator panel must succeed without controller (yaw " + yaw + ", pitch " + pitch + "), got " + result);
                        BlockState placed = level.getBlockState(h.absolutePos(TARGET));
                        h.assertTrue(placed.is(ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get()),
                                "the world block at the target must be the operator panel (yaw " + yaw + ", pitch " + pitch + ")");
                        // FACING should be horizontal opposite of player yaw
                        h.assertValueEqual(yaw.getOpposite(), placed.getValue(ProcessCellOperatingPanelBlock.FACING),
                                "operator panel FACING must be horizontal opposite of player yaw without controller (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertFalse(placed.getValue(ProcessCellOperatingPanelBlock.FORMED),
                                "a placed operator panel must not be formed (yaw " + yaw + ", pitch " + pitch + ")");
                        h.assertValueEqual(0, placed.getValue(ProcessCellOperatingPanelBlock.PART),
                                "a placed operator panel must have PART=0 (yaw " + yaw + ", pitch " + pitch + ")");
                    } finally {
                        clearPlacedPanel(h, level, TARGET);
                    }
                }
            }
        } finally {
            h.setBlock(SUPPORT, Blocks.AIR.defaultBlockState());
        }

        // Test 3: Verify FACING is always horizontal (never vertical) even with steep pitch
        h.setBlock(SUPPORT, Blocks.STONE);
        try {
            for (Direction yaw : YAWS) {
                for (float pitch : new float[] {-89.9f, 0f, 89.9f}) {
                    try {
                        Player player = h.makeMockPlayer(GameType.CREATIVE);
                        BlockPlaceContext context = placementContext(h, player, yaw, pitch,
                                h.absolutePos(TARGET), h.absolutePos(SUPPORT));
                        BlockState state = ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get().getStateForPlacement(context);
                        Direction facing = state.getValue(ProcessCellOperatingPanelBlock.FACING);
                        h.assertTrue(facing.getAxis().isHorizontal(),
                                "operator panel FACING must always be horizontal (yaw " + yaw + ", pitch " + pitch + "), got " + facing);
                    } finally {
                        clearPlacedPanel(h, level, TARGET);
                    }
                }
            }
        } finally {
            h.setBlock(SUPPORT, Blocks.AIR.defaultBlockState());
        }

        LOGGER.info("[M1-03d] operator_panel_horizontal_placement: panel FACING copies controller below or uses horizontal opposite player yaw, always horizontal");
        h.succeed();
    }

    /**
     * Empties a placed operator panel's inventory (if any) and clears the block.
     */
    private static void clearPlacedPanel(GameTestHelper h, ServerLevel level, BlockPos relativeTarget) {
        if (level.getBlockEntity(h.absolutePos(relativeTarget)) instanceof ProcessCellOperatingPanelBlockEntity panel) {
            // Panel has no inventory, but clear for consistency
        }
        h.setBlock(relativeTarget, Blocks.AIR.defaultBlockState());
    }
}
