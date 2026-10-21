package com.euvcraft.lithmod.core.multiblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SupportStructureMatcher}.
 *
 * <p>The expectations below are derived from the geometric definition and deliberately do not call
 * {@link SupportStructureMatcher} to build them:
 * <ul>
 *   <li>for facing {@code f}, {@code back = -f};</li>
 *   <li>{@code right = f} rotated 90 degrees clockwise seen from above;</li>
 *   <li>{@code up = +Y};</li>
 *   <li>the box extends from the controller's face along {@code back} for length, along
 *       {@code right} for width and along {@code up} for height, so the origin is
 *       {@code controller - u0*right - v0*up} and cell {@code (u, v, d)} is
 *       {@code origin + u*right + v*up + d*back}.</li>
 * </ul>
 *
 * <p>The basis vectors are written out as literal tables rather than computed from
 * {@link MultiblockDirection}, so a sign error in the production basis cannot be mirrored here.
 *
 * <p>The anchor is at the front face (d=0), lower row (v=0). Two valid columns: u0=0 and u0=1.
 * No operating panel allowed. 1 or 2 power ports required.
 */
class SupportStructureMatcherTest {

    private static final StructureShape SHAPE = SupportStructureMatcher.SUPPORT_SHAPE;
    private static final MultiblockPos CONTROLLER = new MultiblockPos(10, 5, 10);
    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

    // Two ports on the back face, never colliding with the controller cell at d = 0.
    private static final Cell PORT_A = new Cell(0, 0, SHAPE.length() - 1);
    private static final Cell PORT_B = new Cell(1, 1, SHAPE.length() - 1);
    // A third distinct back-face cell, used to build an invalid three-port box.
    private static final Cell PORT_C = new Cell(1, 0, SHAPE.length() - 1);
    // A back-face cell that is plain casing in a perfect box, used to inject defects.
    private static final Cell CASING_CELL = new Cell(0, 1, SHAPE.length() - 1);

    // ---------------------------------------------------------------- geometry oracle

    /** Unit vector of each facing, written out from the definition. */
    private static MultiblockPos facingVector(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(0, 0, -1);
            case SOUTH -> new MultiblockPos(0, 0, 1);
            case EAST -> new MultiblockPos(1, 0, 0);
            case WEST -> new MultiblockPos(-1, 0, 0);
        };
    }

    /** back = -f. */
    private static MultiblockPos backVector(MultiblockDirection facing) {
        return facingVector(facing).multiply(-1);
    }

    /**
     * right = f rotated 90 degrees clockwise seen from above. Seen from above with north up and east
     * right, clockwise runs NORTH -> EAST -> SOUTH -> WEST -> NORTH, which gives the table below.
     */
    private static MultiblockPos rightVector(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(1, 0, 0);
            case SOUTH -> new MultiblockPos(-1, 0, 0);
            case EAST -> new MultiblockPos(0, 0, 1);
            case WEST -> new MultiblockPos(0, 0, -1);
        };
    }

    /** The box origin, controller - u0*right - v0*up. */
    private static MultiblockPos boxOrigin(MultiblockPos controller, MultiblockDirection facing, int u0, int v0) {
        return controller
                .subtract(rightVector(facing).multiply(u0))
                .subtract(UP.multiply(v0));
    }

    /** World cell (u, v, d) = origin + u*right + v*up + d*back. */
    private static MultiblockPos expectedCell(MultiblockPos controller, MultiblockDirection facing, int u0, int v0,
            int u, int v, int d) {
        return boxOrigin(controller, facing, u0, v0)
                .add(rightVector(facing).multiply(u))
                .add(UP.multiply(v))
                .add(backVector(facing).multiply(d));
    }

    /** All 12 cells of the box, in canonical (u*6 + v*3 + d) iteration order. */
    private static List<MultiblockPos> expectedCells(MultiblockPos controller, MultiblockDirection facing, int u0,
            int v0) {
        List<MultiblockPos> cells = new ArrayList<>(SHAPE.volume());
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    cells.add(expectedCell(controller, facing, u0, v0, u, v, d));
                }
            }
        }
        return cells;
    }

    // ---------------------------------------------------------------- world builders

    /** Turns a local-cell layout into a world lookup; unlisted positions are {@link BlockKind#OTHER}. */
    private static Function<MultiblockPos, BlockKind> worldAt(MultiblockPos controller, MultiblockDirection facing,
            int u0, int v0, Map<Cell, BlockKind> layout) {
        Map<MultiblockPos, BlockKind> blocks = new HashMap<>();
        for (Map.Entry<Cell, BlockKind> entry : layout.entrySet()) {
            Cell cell = entry.getKey();
            blocks.put(expectedCell(controller, facing, u0, v0, cell.u(), cell.v(), cell.d()), entry.getValue());
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    /** A complete valid box: all casing, controller at anchor (u0, 0, 0), 1 or 2 power ports, NO operating panel. */
    private static Map<Cell, BlockKind> validLayout(int u0, Cell... ports) {
        int v0 = 0; // Anchor must be on lower row
        Map<Cell, BlockKind> layout = new HashMap<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        // Controller at anchor (front face, lower row)
        layout.put(new Cell(u0, v0, 0), BlockKind.CONTROLLER);
        // No operating panel!
        for (Cell port : ports) {
            layout.put(port, BlockKind.POWER_PORT);
        }
        return layout;
    }

    /**
     * A complete valid box built on the wrong side of the controller: the whole box is one full
     * length in front of the controller's face instead of behind it (mirrored along depth).
     * This tests the "no mirror d+1 bug" - the true anchor is at front d=0.
     */
    private static Function<MultiblockPos, BlockKind> worldBuiltInFront(MultiblockPos controller,
            MultiblockDirection facing, int u0) {
        Map<MultiblockPos, BlockKind> blocks = new HashMap<>();
        // The box extends along facing (opposite of back) for length cells
        // Keep the real anchor at (u0,0,0); mirror only the depth direction.
        MultiblockPos origin = controller.add(rightVector(facing).multiply(-u0));
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    MultiblockPos pos = origin
                            .add(rightVector(facing).multiply(u))
                            .add(UP.multiply(v))
                            .add(facingVector(facing).multiply(d));
                    // Controller at anchor (u0, 0, 0)
                    if (u == u0 && v == 0 && d == 0) {
                        blocks.put(pos, BlockKind.CONTROLLER);
                    }
                    // Power ports at back face (d = length-1) in MIRRORED box
                    else if ((u == PORT_A.u() && v == PORT_A.v() && d == PORT_A.d())
                            || (u == PORT_B.u() && v == PORT_B.v() && d == PORT_B.d())) {
                        blocks.put(pos, BlockKind.POWER_PORT);
                    }
                    // Everything else is casing
                    else {
                        blocks.put(pos, BlockKind.CASING);
                    }
                }
            }
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    // ---------------------------------------------------------------- success cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_formsForEveryFacingAndBothLowerColumns(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B));
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing, world);

            assertTrue(result.success(),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
            assertEquals(SHAPE.volume(), result.memberPositions().size(),
                    "facing=" + facing + " u0=" + u0);
            assertEquals(new HashSet<>(expectedCells(CONTROLLER, facing, u0, 0)),
                    new HashSet<>(result.memberPositions()),
                    "member cells for facing=" + facing + " u0=" + u0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsExactlyOnePowerPort(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A)));
            assertTrue(result.success(),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsExactlyTwoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B)));
            assertTrue(result.success(),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_returnsMemberPositionsInCanonicalOrder(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B));
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing, world);

            assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());

            List<MultiblockPos> expected = expectedCells(CONTROLLER, facing, u0, 0);
            List<MultiblockPos> actual = result.memberPositions();

            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i), actual.get(i),
                        "facing=" + facing + " u0=" + u0 + " index=" + i + " order mismatch");
            }
        }
    }

    // ---------------------------------------------------------------- failure: foreign block

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsForeignBlock(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            layout.put(CASING_CELL, BlockKind.OTHER);
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertRejects(result, "wrong block", facing, u0);
        }
    }

    // ---------------------------------------------------------------- failure: extra controller

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsExtraController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            layout.put(CASING_CELL, BlockKind.CONTROLLER);
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertRejects(result, "extra controller", facing, u0);
        }
    }

    // ---------------------------------------------------------------- failure: absent ports

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsNoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, validLayout(u0)));

            assertRejects(result, "expected at least one power port", facing, u0);
        }
    }

    // ---------------------------------------------------------------- failure: excess ports

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsThreePowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B, PORT_C)));

            assertRejects(result, "expected at most two power ports", facing, u0);
        }
    }

    // ---------------------------------------------------------------- failure: operating panel (disallowed)

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsOperatingPanelAnywhere(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Add operating panel at various positions - all should be rejected
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    for (int d = 0; d < SHAPE.length(); d++) {
                        if (u == u0 && v == 0 && d == 0) continue; // anchor is controller
                        Map<Cell, BlockKind> testLayout = new HashMap<>(layout);
                        testLayout.put(new Cell(u, v, d), BlockKind.OPERATING_PANEL);
                        MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                                worldAt(CONTROLLER, facing, u0, 0, testLayout));
                        assertFalse(result.success(),
                                "facing=" + facing + " u0=" + u0 + " should reject panel at (" + u + "," + v + "," + d + ")");
                        assertNotNull(result.failureReason());
                        if (u0 == 0) assertTrue(result.failureReason().contains("operating panel not allowed"),
                                "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- failure: upper controller (anchor on top row)

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsControllerOnUpperRow(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Build a box with controller at v0=1 (upper row) - should fail because anchor must be lower
            Map<Cell, BlockKind> layout = new HashMap<>();
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    for (int d = 0; d < SHAPE.length(); d++) {
                        layout.put(new Cell(u, v, d), BlockKind.CASING);
                    }
                }
            }
            layout.put(new Cell(u0, 1, 0), BlockKind.CONTROLLER); // controller on upper row
            layout.put(PORT_A, BlockKind.POWER_PORT);
            layout.put(PORT_B, BlockKind.POWER_PORT);

            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 1, layout));

            assertFalse(result.success(),
                    "facing=" + facing + " u0=" + u0 + " should fail with controller on upper row");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty());
        }
    }

    // ---------------------------------------------------------------- failure: wrong anchor (controller not at anchor)

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsWhenControllerNotAtAnchor(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Build a valid box but with controller at a different front-face cell
            int wrongU0 = (u0 + 1) % SHAPE.width();
            Map<Cell, BlockKind> layout = new HashMap<>();
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    for (int d = 0; d < SHAPE.length(); d++) {
                        layout.put(new Cell(u, v, d), BlockKind.CASING);
                    }
                }
            }
            layout.put(new Cell(wrongU0, 0, 0), BlockKind.CONTROLLER); // controller at wrong column
            layout.put(PORT_A, BlockKind.POWER_PORT);
            layout.put(PORT_B, BlockKind.POWER_PORT);

            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(),
                    "facing=" + facing + " u0=" + u0 + " should fail when controller not at tested anchor");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty());
        }
    }

    // ---------------------------------------------------------------- failure: mirrored layout (box in front)

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsCompleteBoxBuiltInFrontOfController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldBuiltInFront(CONTROLLER, facing, u0);
            // Verify the mirrored fixture keeps the actual anchor block at the controller position
            assertEquals(BlockKind.CONTROLLER, world.apply(CONTROLLER), "mirrored fixture keeps actual anchor");

            MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing, world);

            assertFalse(result.success(),
                    "facing=" + facing + " u0=" + u0 + " should fail for mirrored box");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty());
            // Should fail because the box behind the controller is empty (OTHER blocks)
            assertTrue(result.failureReason().contains("wrong block"),
                    "facing=" + facing + " u0=" + u0 + ": expected 'wrong block', got " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- all 12 single-cell removals

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsAnySingleCellRemoval(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Test removing each of the 12 cells one at a time
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    for (int d = 0; d < SHAPE.length(); d++) {
                        // Skip the anchor cell (controller) - that's tested separately
                        if (u == u0 && v == 0 && d == 0) continue;
                        // Skip port cells - those are tested in port count tests
                        if ((u == PORT_A.u() && v == PORT_A.v() && d == PORT_A.d())
                                || (u == PORT_B.u() && v == PORT_B.v() && d == PORT_B.d())) {
                            continue;
                        }

                        Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
                        layout.put(new Cell(u, v, d), BlockKind.OTHER);

                        MatchResult result = SupportStructureMatcher.match(CONTROLLER, facing,
                                worldAt(CONTROLLER, facing, u0, 0, layout));

                        assertFalse(result.success(),
                                "facing=" + facing + " u0=" + u0 + " should fail when cell (" + u + "," + v + "," + d + ") removed");
                        assertNotNull(result.failureReason());
                        assertTrue(result.memberPositions().isEmpty());
                    }
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(MultiblockDirection.class)
    void optionalInletsReplaceEveryCasingWithoutReplacingRequiredBlocks(MultiblockDirection facing) {
        for (int column=0; column<2; column++) {
            var layout=validLayout(column, PORT_A);
            for (var cell : new ArrayList<>(layout.keySet())) {
                if (layout.get(cell)!=BlockKind.CASING) continue;
                layout.put(cell,BlockKind.WATER_INLET);
                assertTrue(SupportStructureMatcher.match(CONTROLLER,facing,worldAt(CONTROLLER,facing,column,0,layout)).success());
                // Retain previous inlets, so the same test proves multiple inlet admission.
            }
            layout.put(PORT_A,BlockKind.WATER_INLET);
            assertFalse(SupportStructureMatcher.match(CONTROLLER,facing,worldAt(CONTROLLER,facing,column,0,layout)).success(),"water inlet cannot replace the required power port");
        }
    }

    // ---------------------------------------------------------------- invariants

    @Test
    void match_isDeterministic() {
        Function<MultiblockPos, BlockKind> world =
                worldAt(CONTROLLER, MultiblockDirection.WEST, 0, 0, validLayout(0, PORT_A, PORT_B));

        MatchResult first = SupportStructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, world);
        MatchResult second = SupportStructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, world);

        assertEquals(first.success(), second.success());
        assertEquals(first.failureReason(), second.failureReason());
        assertEquals(first.memberPositions(), second.memberPositions());
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void expectedBox_containsTheControllerCell(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            assertTrue(expectedCells(CONTROLLER, facing, u0, 0).contains(CONTROLLER),
                    "controller cell missing for facing=" + facing + " u0=" + u0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void expectedBox_hasNoDuplicateCells(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            List<MultiblockPos> cells = expectedCells(CONTROLLER, facing, u0, 0);
            assertEquals(SHAPE.volume(), cells.size(),
                    "facing=" + facing + " u0=" + u0);
            assertEquals(SHAPE.volume(), new HashSet<>(cells).size(),
                    "duplicate cells for facing=" + facing + " u0=" + u0);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Asserts the structure does not form at this anchor offset. */
    private static void assertRejects(MatchResult result, String firstTriedReason, MultiblockDirection facing,
            int u0) {
        String where = "facing=" + facing + " u0=" + u0;
        assertFalse(result.success(), where + " should not have formed");
        assertNotNull(result.failureReason(), where + " needs a reason");
        assertTrue(result.memberPositions().isEmpty(), where + " must report no members on failure");

        if (u0 == 0) {
            assertTrue(result.failureReason().contains(firstTriedReason),
                    where + ": expected a reason containing '" + firstTriedReason + "' but got '"
                            + result.failureReason() + "'");
        }
    }

    /** A cell inside the box, addressed in local (u, v, d) coordinates. */
    private record Cell(int u, int v, int d) {}
}