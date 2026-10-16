package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellMemberBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.core.multiblock.StructureShape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.slf4j.Logger;

/**
 * Game tests for the Process Cell multiblock, on a real server level with the real registries.
 *
 * <p><b>The geometry here is not the matcher's.</b> The twelve positions of a cell are recomputed
 * from the M1-03c definition with a literal basis-vector table, the same way
 * {@code StructureMatcherTest} does it, so a regression in {@code StructureMatcher} fails these
 * tests too instead of being agreed with by both sides.</p>
 *
 * <p>Every test that asserts something positive also asserts the matching negative. The negatives
 * are marked "negative control".</p>
 */
public final class MultiblockGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "multiblock";

    private static final String FORMS_IN_ALL_FACINGS = "multiblock_forms_in_all_four_facings";
    private static final String REMOVING_ANY_MEMBER_UNFORMS = "multiblock_removing_any_of_twelve_unforms_the_rest";
    private static final String SURVIVES_CASING_PORT_SWAP = "multiblock_survives_a_casing_and_port_swap";
    private static final String CELLS_DO_NOT_SHARE = "multiblock_cells_do_not_share_blocks";
    private static final String ENERGY_ONLY_WHEN_FORMED = "multiblock_energy_reaches_controller_only_while_formed";
    private static final String FORMATION_HOOKS = "multiblock_formation_hooks_fire_per_transition";
    private static final String MODEL_TEXTURES_RESOLVE = "multiblock_model_textures_all_resolve";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    private static final StructureShape SHAPE = StructureShape.PROCESS_CELL;

    /** Two ports on the back face, so they never collide with the controller cell at d = 0. */
    private static final int[] PORT_A = { 0, 0, 2 };
    private static final int[] PORT_B = { 1, 1, 2 };

    static {
        FUNCTIONS.register(FORMS_IN_ALL_FACINGS, () -> MultiblockGameTests::formsInAllFourFacings);
        FUNCTIONS.register(REMOVING_ANY_MEMBER_UNFORMS, () -> MultiblockGameTests::removingAnyMemberUnformsTheRest);
        FUNCTIONS.register(SURVIVES_CASING_PORT_SWAP, () -> MultiblockGameTests::survivesCasingPortSwap);
        FUNCTIONS.register(CELLS_DO_NOT_SHARE, () -> MultiblockGameTests::cellsDoNotShareBlocks);
        FUNCTIONS.register(ENERGY_ONLY_WHEN_FORMED, () -> MultiblockGameTests::energyOnlyWhileFormed);
        FUNCTIONS.register(FORMATION_HOOKS, () -> MultiblockGameTests::formationHooksFirePerTransition);
        FUNCTIONS.register(MODEL_TEXTURES_RESOLVE, () -> MultiblockGameTests::modelTexturesAllResolve);
    }

    private MultiblockGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) MultiblockGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 5, 5, false, 0);

        register(event, data, FORMS_IN_ALL_FACINGS);
        register(event, data, REMOVING_ANY_MEMBER_UNFORMS);
        register(event, data, SURVIVES_CASING_PORT_SWAP);
        register(event, data, CELLS_DO_NOT_SHARE);
        register(event, data, ENERGY_ONLY_WHEN_FORMED);
        register(event, data, FORMATION_HOOKS);
        register(event, data, MODEL_TEXTURES_RESOLVE);
        LOGGER.info("Registered EUVCRAFT multiblock game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // geometry, recomputed independently of StructureMatcher
    // ------------------------------------------------------------------

    /** Unit vector of each facing, written out from the M1-03c definition. */
    private static int[] facingVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 0, 0, -1 };
            case SOUTH -> new int[] { 0, 0, 1 };
            case EAST -> new int[] { 1, 0, 0 };
            case WEST -> new int[] { -1, 0, 0 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    /** back = -f. */
    private static int[] backVector(Direction facing) {
        int[] v = facingVector(facing);
        return new int[] { -v[0], -v[1], -v[2] };
    }

    /** right = f rotated 90 degrees clockwise seen from above, as a literal table. */
    private static int[] rightVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 1, 0, 0 };
            case SOUTH -> new int[] { -1, 0, 0 };
            case EAST -> new int[] { 0, 0, 1 };
            case WEST -> new int[] { 0, 0, -1 };
            default -> new int[] { 1, 0, 0 };
        };
    }

    private static final int[] UP = { 0, 1, 0 };

    /**
     * World cell {@code (u, v, d)} of the box whose controller sits at front-face offset
     * {@code (u0, v0)}, relative to the test origin: {@code controller + (u-u0)*right + (v-v0)*up + d*back}.
     */
    private static BlockPos cell(BlockPos controller, Direction facing, int u0, int v0, int u, int v, int d) {
        int[] right = rightVector(facing);
        int[] back = backVector(facing);
        return new BlockPos(
            controller.getX() + right[0] * (u - u0) + UP[0] * (v - v0) + back[0] * d,
            controller.getY() + right[1] * (u - u0) + UP[1] * (v - v0) + back[1] * d,
            controller.getZ() + right[2] * (u - u0) + UP[2] * (v - v0) + back[2] * d);
    }

    /** The twelve positions of a cell, in {@code (u, v, d)} order. */
    private static List<BlockPos> cellsOf(BlockPos controller, Direction facing, int u0, int v0) {
        List<BlockPos> cells = new ArrayList<>(SHAPE.volume());
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    cells.add(cell(controller, facing, u0, v0, u, v, d));
                }
            }
        }
        return cells;
    }

    private static boolean isPort(int u, int v, int d) {
        return (u == PORT_A[0] && v == PORT_A[1] && d == PORT_A[2])
            || (u == PORT_B[0] && v == PORT_B[1] && d == PORT_B[2]);
    }

    // ------------------------------------------------------------------
    // world building
    // ------------------------------------------------------------------

    /**
     * Places a complete, valid cell and returns its twelve relative positions.
     *
     * <p>The controller carries the facing under test, which is the only way a multiblock can know
     * where its other eleven blocks are.</p>
     */
    private static List<BlockPos> buildCell(GameTestHelper helper, BlockPos controller, Direction facing,
            int u0, int v0) {
        List<BlockPos> placed = new ArrayList<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    BlockPos pos = cell(controller, facing, u0, v0, u, v, d);
                    if (u == u0 && v == v0 && d == 0) {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                            .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                    } else if (isPort(u, v, d)) {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(pos, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                    placed.add(pos);
                }
            }
        }
        return placed;
    }

    /** Re-runs the controller's match. The tests drive this directly instead of waiting out ticks. */
    private static ProcessCellControllerBlockEntity revalidate(GameTestHelper helper, BlockPos controller) {
        ProcessCellControllerBlockEntity be =
            controller(helper.getLevel(), helper.absolutePos(controller));
        helper.assertTrue(be != null, "no controller block entity at " + controller);
        if (be == null) {
            throw new IllegalStateException("no controller at " + controller);
        }
        be.revalidate();
        return be;
    }

    private static ProcessCellControllerBlockEntity controller(ServerLevel level, BlockPos abs) {
        return level.getBlockEntity(abs) instanceof ProcessCellControllerBlockEntity be ? be : null;
    }

    private static void assertFormed(GameTestHelper helper, ProcessCellControllerBlockEntity controller,
            BlockPos controllerRel, Direction facing, int u0, int v0, String what) {
        helper.assertTrue(controller.isFormed(),
            what + ": the cell is not formed (" + controller.failureReason() + ")");
        if (!controller.isFormed()) {
            return;
        }
        Set<BlockPos> expected = new LinkedHashSet<>();
        for (BlockPos rel : cellsOf(controllerRel, facing, u0, v0)) {
            expected.add(helper.absolutePos(rel));
        }
        helper.assertTrue(controller.members().size() == SHAPE.volume(),
            what + ": expected " + SHAPE.volume() + " members, got " + controller.members().size());
        for (BlockPos rel : cellsOf(controllerRel, facing, u0, v0)) {
            BlockPos abs = helper.absolutePos(rel);
            helper.assertTrue(controller.members().contains(abs),
                what + ": " + rel + " is not in the member list");
            helper.assertTrue(isFormedFlag(helper.getLevel(), abs),
                what + ": " + rel + " does not carry the formed flag");
        }
    }

    private static boolean isFormedFlag(ServerLevel level, BlockPos abs) {
        BlockState state = level.getBlockState(abs);
        if (state.getBlock() instanceof ProcessCellControllerBlock) {
            return state.getValue(ProcessCellControllerBlock.FORMED);
        }
        if (state.getBlock() instanceof ProcessCellCasingBlock) {
            return state.getValue(ProcessCellCasingBlock.FORMED);
        }
        if (state.getBlock() instanceof ProcessCellPowerPortBlock) {
            return state.getValue(ProcessCellPowerPortBlock.FORMED);
        }
        return false;
    }

    private static void assertNoFlagLeftSet(GameTestHelper helper, List<BlockPos> cells, BlockPos skipRel,
            String what) {
        for (BlockPos rel : cells) {
            if (rel.equals(skipRel)) {
                continue;
            }
            helper.assertFalse(isFormedFlag(helper.getLevel(), helper.absolutePos(rel)),
                what + ": " + rel + " is still flagged formed");
        }
    }

    // ------------------------------------------------------------------
    // 1. all four facings
    // ------------------------------------------------------------------

    private static void formsInAllFourFacings(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);

        for (Direction facing : new Direction[] { Direction.NORTH, Direction.SOUTH,
                                                  Direction.EAST, Direction.WEST }) {
            for (int u0 = 0; u0 < SHAPE.width(); u0++) {
                for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                    List<BlockPos> cells = buildCell(helper, controller, facing, u0, v0);
                    ProcessCellControllerBlockEntity be = revalidate(helper, controller);
                    assertFormed(helper, be, controller, facing, u0, v0,
                        "facing=" + facing + " offset=(" + u0 + "," + v0 + ")");

                    // NEGATIVE CONTROL: the same twelve blocks mirrored onto the controller's front
                    // side must not form, so a green assertion above cannot come from a matcher that
                    // accepts any twelve structure blocks.
                    for (BlockPos rel : cells) {
                        helper.setBlock(rel, Blocks.AIR.defaultBlockState());
                    }
                    helper.setBlock(controller, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                        .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                    for (BlockPos rel : mirroredCells(controller, facing, u0, v0)) {
                        boolean port = rel.equals(cell(controller, facing, u0, v0, PORT_A[0], PORT_A[1], PORT_A[2]))
                            || rel.equals(cell(controller, facing, u0, v0, PORT_B[0], PORT_B[1], PORT_B[2]));
                        helper.setBlock(rel, port
                            ? ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState()
                            : ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                    ProcessCellControllerBlockEntity front = revalidate(helper, controller);
                    helper.assertFalse(front.isFormed(),
                        "facing=" + facing + " offset=(" + u0 + "," + v0
                            + "): a complete cell in front of the controller formed (negative control)");
                    for (BlockPos rel : cells) {
                        helper.setBlock(rel, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        LOGGER.info("[M1-03d] forms_in_all_four_facings: 16 controller placements formed, "
            + "16 mirrored placements refused");
        helper.succeed();
    }

    /** The twelve positions a cell would occupy if it were built on the controller's front side. */
    private static List<BlockPos> mirroredCells(BlockPos controller, Direction facing, int u0, int v0) {
        List<BlockPos> cells = new ArrayList<>(SHAPE.volume());
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 1; d <= SHAPE.length(); d++) {
                    BlockPos front = cell(controller, facing, u0, v0, u, v, 0);
                    cells.add(front.offset(-facingVector(facing)[0] * d,
                        -facingVector(facing)[1] * d, -facingVector(facing)[2] * d));
                }
            }
        }
        return cells;
    }

    // ------------------------------------------------------------------
    // 2. removing any of the twelve unforms the other eleven
    // ------------------------------------------------------------------

    private static void removingAnyMemberUnformsTheRest(GameTestHelper helper) {
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0;
        int v0 = 0;
        List<BlockPos> cells = cellsOf(controllerRel, facing, u0, v0);
        int removed = 0;

        for (BlockPos victim : cells) {
            buildCell(helper, controllerRel, facing, u0, v0);
            ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
            helper.assertTrue(be.isFormed(), "the cell did not form before removing " + victim);

            helper.setBlock(victim, Blocks.AIR.defaultBlockState());
            if (victim.equals(controllerRel)) {
                // The controller is gone, so there is nobody left to push an update: each surviving
                // member has to drop its own flag on its own tick.
                for (BlockPos rel : cells) {
                    if (rel.equals(victim)) {
                        continue;
                    }
                    if (helper.getLevel().getBlockEntity(helper.absolutePos(rel))
                            instanceof ProcessCellMemberBlockEntity member) {
                        member.serverTick();
                    }
                }
            } else {
                revalidate(helper, controllerRel);
            }
            assertNoFlagLeftSet(helper, cells, victim, "after removing " + victim);
            removed++;

            for (BlockPos rel : cells) {
                helper.setBlock(rel, Blocks.AIR.defaultBlockState());
            }
        }
        helper.assertValueEqual(12, removed, "not every one of the twelve positions was exercised");
        LOGGER.info("[M1-03d] removing_any_of_twelve: {} removals, each left the other 11 unflagged", removed);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. a casing and a power port can be swapped
    // ------------------------------------------------------------------

    private static void survivesCasingPortSwap(GameTestHelper helper) {
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0;
        int v0 = 0;
        List<BlockPos> cells = cellsOf(controllerRel, facing, u0, v0);

        BlockPos portCell = cell(controllerRel, facing, u0, v0, PORT_A[0], PORT_A[1], PORT_A[2]);
        BlockPos casingCell = cell(controllerRel, facing, u0, v0, 1, 0, 1);

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity be = revalidate(helper, controllerRel);
        assertFormed(helper, be, controllerRel, facing, u0, v0, "before the swap");

        // Swap the two blocks. The multiset of one controller, two ports and nine casings is
        // unchanged, so the cell stays valid; only the two positions change role.
        helper.setBlock(portCell, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        helper.setBlock(casingCell, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());

        ProcessCellControllerBlockEntity after = revalidate(helper, controllerRel);
        assertFormed(helper, after, controllerRel, facing, u0, v0, "after the swap");
        helper.assertValueEqual(be.members().size(), after.members().size(),
            "the swap changed how many blocks the cell claims");

        // The port that moved must now be the one that lends energy, and the old port position must
        // not, so the swap actually moved the capability rather than the matcher ignoring it.
        BlockPos oldPort = helper.absolutePos(portCell);
        BlockPos newPort = helper.absolutePos(casingCell);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, newPort, Direction.NORTH) != null,
            "the block that became a port has no energy capability");
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, oldPort, Direction.NORTH) == null,
            "the block that stopped being a port still exposes energy");

        // NEGATIVE CONTROL: swapping is only about which cell holds a port. Taking both ports away
        // is not a swap, and must break the cell.
        helper.setBlock(portCell, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        helper.setBlock(casingCell, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        BlockPos secondPort = cell(controllerRel, facing, u0, v0, PORT_B[0], PORT_B[1], PORT_B[2]);
        helper.setBlock(secondPort, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        ProcessCellControllerBlockEntity noPorts = revalidate(helper, controllerRel);
        helper.assertFalse(noPorts.isFormed(),
            "a cell with no power port at all still formed (negative control)");
        helper.assertTrue(noPorts.failureReason().contains("power port"),
            "the failure reason does not mention the missing port: " + noPorts.failureReason());
        helper.assertFalse(isFormedFlag(helper.getLevel(), helper.absolutePos(controllerRel)),
            "the controller is still flagged formed with no ports (negative control)");

        LOGGER.info("[M1-03d] casing_port_swap: one port moved from {} to {}, capability followed it",
            portCell, casingCell);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. two cells never share blocks
    // ------------------------------------------------------------------

    private static void cellsDoNotShareBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Direction facing = Direction.NORTH;
        BlockPos firstRel = new BlockPos(1, 1, 1);
        int u0 = 0;
        int v0 = 0;

        // The first cell occupies x 1..2, y 1..2, z 1..3 and is formed.
        buildCell(helper, firstRel, facing, u0, v0);
        ProcessCellControllerBlockEntity first = revalidate(helper, firstRel);
        assertFormed(helper, first, firstRel, facing, u0, v0, "the first cell");

        // The second controller goes at (3, 2, 1) with front-face offset (1, 1), which puts its
        // box at x 2..3, y 1..2, z 1..3. Six of those twelve positions are already members of the
        // first, formed cell and stay that way; the second cell wants them anyway.
        BlockPos secondRel = new BlockPos(3, 2, 1);
        int su0 = 1;
        int sv0 = 1;
        List<BlockPos> secondCells = cellsOf(secondRel, facing, su0, sv0);
        List<BlockPos> firstAbs = new ArrayList<>();
        for (BlockPos rel : cellsOf(firstRel, facing, u0, v0)) {
            firstAbs.add(helper.absolutePos(rel));
        }

        Set<BlockPos> shared = new LinkedHashSet<>();
        for (BlockPos rel : secondCells) {
            if (firstAbs.contains(helper.absolutePos(rel))) {
                shared.add(rel);
            }
        }
        // The controller itself must sit outside the first cell, otherwise this would be replacing
        // one of its members rather than contending for blocks.
        helper.assertFalse(shared.contains(secondRel),
            "the second controller sits inside the first cell, which is not a sharing test");
        helper.assertTrue(shared.size() > 0, "the two cells share no blocks, so this proves nothing");

        // Build the second cell around the six shared positions without touching them.
        for (BlockPos rel : secondCells) {
            if (shared.contains(rel)) {
                continue;
            }
            if (rel.equals(secondRel)) {
                helper.setBlock(rel, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                    .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, facing));
                continue;
            }
            // Two ports, both in positions the first cell does not own.
            boolean port = rel.equals(cell(secondRel, facing, su0, sv0, PORT_A[0], PORT_A[1], PORT_A[2]))
                || rel.equals(cell(secondRel, facing, su0, sv0, PORT_B[0], PORT_B[1], PORT_B[2]));
            helper.setBlock(rel, port
                ? ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState()
                : ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
        }

        // The second cell must be refused, and must say why.
        ProcessCellControllerBlockEntity second = revalidate(helper, secondRel);
        helper.assertFalse(second.isFormed(),
            "a cell whose box overlaps a formed cell formed anyway: "
                + shared.size() + " shared blocks were taken");
        helper.assertTrue(second.failureReason().contains("wrong block"),
            "the refusal does not name the contested block: " + second.failureReason());

        // And the first cell must be untouched: no member lost, no flag dropped.
        ProcessCellControllerBlockEntity firstAfter = revalidate(helper, firstRel);
        assertFormed(helper, firstAfter, firstRel, facing, u0, v0, "the first cell after the second was refused");
        for (BlockPos rel : cellsOf(firstRel, facing, u0, v0)) {
            BlockEntity still = level.getBlockEntity(helper.absolutePos(rel));
            if (still instanceof ProcessCellMemberBlockEntity member) {
                helper.assertTrue(member.controller() == firstAfter,
                    "the second cell stole the member at " + rel);
            }
        }
        LOGGER.info("[M1-03d] cells_do_not_share: second cell wanted {} blocks the first already holds, "
            + "was refused, and the first kept all {}", shared.size(), SHAPE.volume());

        // NEGATIVE CONTROL: a third cell that shares no blocks with either still forms, so the
        // refusal above is about sharing and not about only the first cell ever working.
        BlockPos thirdRel = new BlockPos(6, 1, 1);
        buildCell(helper, thirdRel, Direction.EAST, 0, 0);
        ProcessCellControllerBlockEntity third = revalidate(helper, thirdRel);
        assertFormed(helper, third, thirdRel, Direction.EAST, 0, 0, "the third, separate cell");

        // NEGATIVE CONTROL: break the first cell so it no longer holds the contested blocks, and the
        // second cell must then form. This is what shows the refusal came from the sharing and not
        // from a second cell that was never buildable.
        // A member only the first cell owns, not its controller, so the controller survives to re-validate.
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.AIR.defaultBlockState());
        ProcessCellControllerBlockEntity broken = revalidate(helper, firstRel);
        helper.assertFalse(broken.isFormed(), "the first cell is still formed after losing a block");
        for (BlockPos rel : secondCells) {
            helper.setBlock(rel, Blocks.AIR.defaultBlockState());
        }
        buildCell(helper, secondRel, facing, su0, sv0);
        ProcessCellControllerBlockEntity secondAlone = revalidate(helper, secondRel);
        helper.assertTrue(secondAlone.isFormed(),
            "the second cell did not form once the first stopped holding its blocks (negative control): "
                + secondAlone.failureReason());
        LOGGER.info("[M1-03d] cells_do_not_share: second cell forms once the contention is removed");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. energy through a port, only while formed
    // ------------------------------------------------------------------

    private static void energyOnlyWhileFormed(GameTestHelper helper) {
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0;
        int v0 = 0;
        List<BlockPos> cells = cellsOf(controllerRel, facing, u0, v0);
        BlockPos portRel = cell(controllerRel, facing, u0, v0, PORT_A[0], PORT_A[1], PORT_A[2]);
        BlockPos otherPortRel = cell(controllerRel, facing, u0, v0, PORT_B[0], PORT_B[1], PORT_B[2]);

        // NEGATIVE CONTROL: a port that belongs to no formed cell exposes nothing at all.
        helper.setBlock(portRel, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
        ProcessCellPowerPortBlockEntity lonely = port(helper.getLevel(), helper.absolutePos(portRel));
        helper.assertTrue(lonely != null, "the lone port has no block entity");
        helper.assertTrue(lonely.energy() == null,
            "a port in no formed cell handed out an energy handler (negative control)");
        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(portRel), side) == null,
                "an unformed port exposes energy on side " + side + " (negative control)");
        }

        // Form the cell.
        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity controller = revalidate(helper, controllerRel);
        assertFormed(helper, controller, controllerRel, facing, u0, v0, "the formed cell");

        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(portRel), side) != null,
                "a formed port exposes no energy on side " + side);
        }

        EnergyHandler handler = helper.getLevel()
            .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(portRel), Direction.NORTH);
        helper.assertTrue(handler != null, "no energy handler through the port");
        if (handler == null) {
            return;
        }

        int accepted = push(handler, 500);
        helper.assertValueEqual(500, accepted, "the port did not accept the full insert");
        helper.assertValueEqual(500L, controller.buffer().getAmountAsLong(),
            "the energy did not reach the controller's buffer through the port");
        helper.assertTrue(handler.getAmountAsLong() == 500L,
            "the port's handler does not report the controller's amount");
        helper.assertValueEqual((long) ProcessCellControllerBlockEntity.ENERGY_CAPACITY, handler.getCapacityAsLong(),
            "the port reports a different capacity from the controller");

        // Extraction is refused: the controller's buffer is built with maxExtract 0.
        int extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            extracted = handler.extract(500, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(0, extracted, "the port gave energy back");
        helper.assertValueEqual(500L, controller.buffer().getAmountAsLong(),
            "the refused extraction still changed the buffer");

        // The second port is the same buffer, not a second one.
        EnergyHandler second = helper.getLevel()
            .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(otherPortRel), Direction.NORTH);
        helper.assertTrue(second != null, "the second port has no handler");
        if (second != null) {
            helper.assertValueEqual(500L, second.getAmountAsLong(),
                "the two ports disagree about the stored amount");
        }

        // A casing has no energy capability, even inside a formed cell.
        BlockPos casingRel = cell(controllerRel, facing, u0, v0, 1, 0, 1);
        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(casingRel), side) == null,
                "a casing exposes energy on side " + side + " (negative control)");
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(portRel), side) == null,
                "a port exposes an item handler on side " + side + " (negative control)");
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(portRel), side) == null,
                "a port exposes a fluid handler on side " + side + " (negative control)");
        }

        // Break the cell: the port must stop lending, and the stored energy must survive.
        helper.setBlock(cells.get(cells.size() - 1), Blocks.AIR.defaultBlockState());
        revalidate(helper, controllerRel);
        helper.assertFalse(controller.isFormed(), "the cell is still formed after a block was removed");
        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(portRel), side) == null,
                "a port of a broken cell still exposes energy on side " + side);
        }
        helper.assertValueEqual(500L, controller.buffer().getAmountAsLong(),
            "breaking the cell lost the stored energy");

        // Put the block back: the port must lend again, and the buffer must still hold the energy.
        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity reformed = revalidate(helper, controllerRel);
        assertFormed(helper, reformed, controllerRel, facing, u0, v0, "the reformed cell");
        helper.assertValueEqual(500L, reformed.buffer().getAmountAsLong(),
            "re-forming the cell lost the stored energy");
        EnergyHandler again = helper.getLevel()
            .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(portRel), Direction.NORTH);
        helper.assertTrue(again != null, "the port does not lend after the cell re-formed");
        helper.assertValueEqual(250, push(again, 250), "the port refuses energy after re-forming");

        LOGGER.info("[M1-03d] energy_only_while_formed: 500 FE through the port reached the controller, "
            + "extraction refused, capability gone while broken, 250 more accepted after re-forming");
        helper.succeed();
    }

    private static ProcessCellPowerPortBlockEntity port(ServerLevel level, BlockPos abs) {
        return level.getBlockEntity(abs) instanceof ProcessCellPowerPortBlockEntity be ? be : null;
    }

    private static int push(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    // ------------------------------------------------------------------
    // 6. the M1-09 formation hooks
    // ------------------------------------------------------------------

    private static void formationHooksFirePerTransition(GameTestHelper helper) {
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        int u0 = 0;
        int v0 = 0;
        List<BlockPos> cells = cellsOf(controllerRel, facing, u0, v0);

        // The hooks must be protected, no-argument and void, or M1-09 cannot override them.
        for (String hook : new String[] { "onFormed", "onUnformed" }) {
            Method method = findHook(hook);
            helper.assertTrue(method != null, "ProcessCellControllerBlockEntity has no " + hook + "()");
            if (method == null) {
                continue;
            }
            helper.assertTrue(Modifier.isProtected(method.getModifiers()),
                hook + "() is not protected, so a subclass cannot override it for M1-09");
            helper.assertTrue(method.getReturnType() == void.class, hook + "() does not return void");
            helper.assertValueEqual(0, method.getParameterCount(), hook + "() takes arguments");
        }

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity controller = revalidate(helper, controllerRel);
        helper.assertTrue(controller.isFormed(), "the cell did not form");
        helper.assertValueEqual(1, controller.formationChanges(), "forming is not one transition");

        // Re-validating a formed, unchanged cell must not be reported again.
        controller.revalidate();
        helper.assertValueEqual(1, controller.formationChanges(),
            "an unchanged re-validation counted as another transition");

        // Break it, then put it back: two more transitions, one down and one up.
        helper.setBlock(cells.get(cells.size() - 1), Blocks.AIR.defaultBlockState());
        revalidate(helper, controllerRel);
        helper.assertFalse(controller.isFormed(), "the cell did not unform");
        helper.assertValueEqual(2, controller.formationChanges(), "unforming is not the second transition");

        buildCell(helper, controllerRel, facing, u0, v0);
        ProcessCellControllerBlockEntity back = revalidate(helper, controllerRel);
        helper.assertTrue(back.isFormed(), "the cell did not re-form");
        helper.assertValueEqual(3, back.formationChanges(), "re-forming is not the third transition");

        // NEGATIVE CONTROL: a controller that never forms never reports a transition.
        BlockPos lonelyRel = new BlockPos(5, 1, 5);
        helper.setBlock(lonelyRel, ModMultiblocks.PROCESS_CELL_CONTROLLER.get().defaultBlockState());
        ProcessCellControllerBlockEntity lonely = revalidate(helper, lonelyRel);
        helper.assertFalse(lonely.isFormed(), "a lone controller reported itself as formed (negative control)");
        helper.assertValueEqual(0, lonely.formationChanges(),
            "a controller that never formed reported transitions (negative control)");

        LOGGER.info("[M1-03d] formation_hooks: protected onFormed/onUnformed present, "
            + "3 transitions recorded for form/unform/re-form");
        helper.succeed();
    }

    private static Method findHook(String name) {
        try {
            return ProcessCellControllerBlockEntity.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 7. every model texture resolves
    // ------------------------------------------------------------------

    private static void modelTexturesAllResolve(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> blockModels = List.of(
            "process_cell_controller_unformed", "process_cell_controller_formed",
            "process_cell_casing_unformed", "process_cell_casing_formed",
            "process_cell_power_port_unformed", "process_cell_power_port_formed");
        List<String> itemModels = List.of(
            "process_cell_controller", "process_cell_casing", "process_cell_power_port");

        for (String model : blockModels) {
            JsonObject json = readModel("/assets/lithmod/models/block/" + model + ".json");
            helper.assertTrue(json != null, "no block model " + model);
            if (json == null) {
                continue;
            }
            assertParentResolves(helper, json, model);
            assertTexturesResolve(helper, level, json, model);
        }
        for (String model : itemModels) {
            JsonObject json = readModel("/assets/lithmod/models/item/" + model + ".json");
            helper.assertTrue(json != null, "no item model " + model);
            if (json == null) {
                continue;
            }
            assertParentResolves(helper, json, model);
            assertTexturesResolve(helper, level, json, model);
        }

        // Formed and unformed must look different, or the formed flag is invisible to a player.
        assertDistinctTextures(helper, "process_cell_controller");
        assertDistinctTextures(helper, "process_cell_casing");
        assertDistinctTextures(helper, "process_cell_power_port");

        // The blockstate files must exist and name the six models.
        for (String block : List.of("process_cell_controller", "process_cell_casing", "process_cell_power_port")) {
            helper.assertTrue(MultiblockGameTests.class.getResource(
                    "/assets/lithmod/blockstates/" + block + ".json") != null,
                "no blockstate for " + block);
        }

        // NEGATIVE CONTROL: the three names the M1-03 report used do not resolve. If any of them
        // did, the positive assertions above would be proving nothing.
        helper.assertFalse(resolves(level, "minecraft:block/block_of_iron"),
            "block_of_iron resolves, so the positive assertions are vacuous (negative control)");
        helper.assertFalse(resolves(level, "minecraft:block/smooth_quartz"),
            "smooth_quartz resolves, so the positive assertions are vacuous (negative control)");
        helper.assertFalse(resolves(level, "minecraft:block/quartz_block"),
            "quartz_block resolves, so the positive assertions are vacuous (negative control)");
        helper.assertTrue(resolves(level, "minecraft:block/iron_block"),
            "iron_block does not resolve, so the rule itself is broken (negative control)");
        helper.assertTrue(resolves(level, "minecraft:block/quartz_block_bottom"),
            "quartz_block_bottom does not resolve, so the rule rejects real textures (negative control)");

        LOGGER.info("[M1-03d] model_textures_all_resolve: 6 block models and 3 item models, "
            + "every parent and texture resolved against the vanilla assets");
        helper.succeed();
    }

    private static void assertParentResolves(GameTestHelper helper, JsonObject model, String name) {
        JsonElement parent = model.get("parent");
        helper.assertTrue(parent != null && parent.isJsonPrimitive(),
            name + " has no parent model");
        if (parent == null || !parent.isJsonPrimitive()) {
            return;
        }
        String parentId = parent.getAsString();
        if (parentId.startsWith("minecraft:")) {
            helper.assertTrue(parentId.equals("minecraft:block/cube_all"),
                name + " parents to an unexpected vanilla model " + parentId);
        } else {
            String path = parentId.substring(parentId.indexOf(':') + 1);
            helper.assertTrue(MultiblockGameTests.class.getResource("/assets/lithmod/models/" + path + ".json") != null,
                name + " parents to " + parentId + ", which does not exist");
        }
    }

    private static void assertTexturesResolve(GameTestHelper helper, ServerLevel level, JsonObject model, String name) {
        JsonElement textures = model.get("textures");
        if (textures == null || !textures.isJsonObject()) {
            return;
        }
        JsonObject entries = textures.getAsJsonObject();
        helper.assertTrue(entries.size() > 0, name + " declares no textures");
        for (String key : entries.keySet()) {
            String texture = entries.get(key).getAsString();
            helper.assertTrue(resolves(level, texture),
                name + " texture '" + key + "' = " + texture + " does not resolve");
        }
    }

    private static void assertDistinctTextures(GameTestHelper helper, String block) {
        JsonObject formed = readModel("/assets/lithmod/models/block/" + block + "_formed.json");
        JsonObject unformed = readModel("/assets/lithmod/models/block/" + block + "_unformed.json");
        if (formed == null || unformed == null) {
            return;
        }
        helper.assertTrue(!textureOf(formed).equals(textureOf(unformed)),
            block + " uses the same texture for formed and unformed, so the flag cannot be seen");
    }

    private static String textureOf(JsonObject model) {
        JsonElement textures = model.get("textures");
        if (textures == null || !textures.isJsonObject()) {
            return "";
        }
        JsonObject entries = textures.getAsJsonObject();
        return entries.size() == 0 ? "" : entries.get(entries.keySet().iterator().next()).getAsString();
    }

    /**
 * Whether a texture reference resolves to a real vanilla texture file.
 *
 * *<p>Resolved by opening the Minecraft distribution the server is actually running and looking for
 * the file, because that is the only thing that answers the question the M1-03 report got wrong
 * twice. The server's {@code ResourceManager} is not usable here: it is built from server-side
 * resources and vanilla client assets are masked out of it, so asking it for
 * {@code minecraft:textures/block/iron_block.png} returns nothing even though the file is present.
 * This was confirmed by the first run of this test failing on a texture that does exist.</p>
 *
 * <p>Checking "is this a registered block id" instead would be wrong in both directions:
 * {@code smooth_quartz} is a real block whose textures are {@code smooth_quartz_top} and
 * {@code smooth_quartz_side}, and the legitimate {@code quartz_block_bottom} is not a block at all.</p>
 */
    private static boolean resolves(ServerLevel level, String textureId) {
        int colon = textureId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : textureId.substring(0, colon);
        String path = colon < 0 ? textureId : textureId.substring(colon + 1);
        // A texture reference is written minecraft:block/foo but lives at assets/minecraft/textures/block/foo.png.
        return namespace.equals("minecraft")
            && vanillaResources().contains("assets/minecraft/textures/" + path + ".png");
    }

    /** Every resource path inside the Minecraft jar, read once. */
    private static Set<String> vanillaResources() {
        if (vanillaResources != null) {
            return vanillaResources;
        }
        Set<String> names = new LinkedHashSet<>();
        try {
            URL location = net.minecraft.world.level.block.Blocks.class
                .getProtectionDomain().getCodeSource().getLocation();
            java.nio.file.Path jar = java.nio.file.Paths.get(location.toURI());
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                zip.stream().forEach(entry -> names.add(entry.getName()));
            }
            vanillaResources = names;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not read the Minecraft jar to resolve textures: " + e, e);
        }
        return names;
    }

    private static Set<String> vanillaResources;

    private static JsonObject readModel(String resource) {
        try (InputStream input = MultiblockGameTests.class.getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read model " + resource, e);
        }
    }
}