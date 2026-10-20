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
 * Unit tests for {@link StructureMatcher}.
 *
 * <p>The expectations below are derived from the geometric definition and deliberately do not call
 * {@link StructureMatcher} to build them:
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
 */
class StructureMatcherTest {

    private static final StructureShape SHAPE = StructureShape.PROCESS_CELL;
    private static final MultiblockPos CONTROLLER = new MultiblockPos(10, 5, 10);
    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

    /** Two ports on the back face, never colliding with the controller cell at d = 0. */
    private static final Cell PORT_A = new Cell(0, 0, SHAPE.length() - 1);
    private static final Cell PORT_B = new Cell(1, 1, SHAPE.length() - 1);
    /** A third distinct back-face cell, used to build an invalid three-port box. */
    private static final Cell PORT_C = new Cell(1, 0, SHAPE.length() - 1);
    /** A back-face cell that is plain casing in a perfect box, used to inject defects. */
    private static final Cell CASING_CELL = new Cell(0, 1, SHAPE.length() - 1);
    /** Operating panel position: directly above controller at front face (u0, v0+1, 0). */
    private static final Cell PANEL_CELL = new Cell(0, 1, 0); // u=0,v=1,d=0 when u0=0,v0=0

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

    /** All 12 cells of the box, in (u, v, d) iteration order. */
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

    /** A complete valid box: all casing, then the controller at the front face offset, operating panel above it, plus the given ports. */
    private static Map<Cell, BlockKind> layoutWithPorts(int u0, int v0, Cell... ports) {
        Map<Cell, BlockKind> layout = new HashMap<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        layout.put(new Cell(u0, v0, 0), BlockKind.CONTROLLER);
        // Operating panel directly above controller at (u0, v0+1, 0)
        if (v0 + 1 < SHAPE.height()) {
            layout.put(new Cell(u0, v0 + 1, 0), BlockKind.OPERATING_PANEL);
        }
        for (Cell port : ports) {
            layout.put(port, BlockKind.POWER_PORT);
        }
        return layout;
    }

    /**
     * A complete, valid box built on the wrong side of the controller: the whole box is one full
     * length in front of the controller's face instead of behind it.
     */
    private static Function<MultiblockPos, BlockKind> worldBuiltInFront(MultiblockPos controller,
            MultiblockDirection facing, int u0, int v0) {
        Map<MultiblockPos, BlockKind> blocks = new HashMap<>();
        blocks.put(controller, BlockKind.CONTROLLER);
        MultiblockPos shift = backVector(facing).multiply(-SHAPE.length());
        for (MultiblockPos cell : expectedCells(controller, facing, u0, v0)) {
            blocks.putIfAbsent(cell.add(shift), BlockKind.CASING);
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    // ---------------------------------------------------------------- success cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_formsForEveryFacingAndControllerOffset(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                Function<MultiblockPos, BlockKind> world =
                        worldAt(CONTROLLER, facing, u0, v0, layoutWithPorts(u0, v0, PORT_A, PORT_B));
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE, world);

                if (v0 == 0) {
                    // Only bottom row (v0=0) allows operating panel above controller
                    assertTrue(result.success(),
                            "facing=" + facing + " offset=(" + u0 + "," + v0 + "): " + result.failureReason());
                    assertEquals(SHAPE.volume(), result.memberPositions().size(),
                            "facing=" + facing + " offset=(" + u0 + "," + v0 + ")");
                    assertEquals(new HashSet<>(expectedCells(CONTROLLER, facing, u0, v0)),
                            new HashSet<>(result.memberPositions()),
                            "member cells for facing=" + facing + " offset=(" + u0 + "," + v0 + ")");
                } else {
                    // Top row (v0=1) leaves no room for operating panel above
                    assertFalse(result.success(),
                            "facing=" + facing + " offset=(" + u0 + "," + v0 + ") should fail: no room for panel");
                    assertNotNull(result.failureReason());
                    assertTrue(result.memberPositions().isEmpty());
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_returnsExactlyTheDocumentedBoundingBox(MultiblockDirection facing) {
        // Inclusive min/max world bounds on x, y and z for CONTROLLER at offset (u0, v0) = (1, 0) (bottom row).
        int[][] bounds = switch (facing) {
            //        x min/max,  y min/max, z min/max
            case NORTH -> new int[][] {{9, 10}, {5, 6}, {10, 12}};
            case SOUTH -> new int[][] {{10, 11}, {5, 6}, {8, 10}};
            case EAST -> new int[][] {{8, 10}, {5, 6}, {9, 10}};
            case WEST -> new int[][] {{10, 12}, {5, 6}, {10, 11}};
        };

        MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                worldAt(CONTROLLER, facing, 1, 0, layoutWithPorts(1, 0, PORT_A, PORT_B)));
        assertTrue(result.success(), "facing=" + facing + ": " + result.failureReason());

        int[] min = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
        int[] max = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (MultiblockPos pos : result.memberPositions()) {
            int[] p = { pos.x(), pos.y(), pos.z() };
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], p[axis]);
                max[axis] = Math.max(max[axis], p[axis]);
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(bounds[axis][0], min[axis], "facing=" + facing + " axis " + axis + " min");
            assertEquals(bounds[axis][1], max[axis], "facing=" + facing + " axis " + axis + " max");
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsExactlyOnePowerPort(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Only v0=0 (bottom row) is valid - need room for operating panel above
            int v0 = 0;
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layoutWithPorts(u0, v0, PORT_A)));
            assertTrue(result.success(),
                    "facing=" + facing + " offset=(" + u0 + "," + v0 + "): " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsExactlyTwoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Only v0=0 (bottom row) is valid - need room for operating panel above
            int v0 = 0;
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layoutWithPorts(u0, v0, PORT_A, PORT_B)));
            assertTrue(result.success(),
                    "facing=" + facing + " offset=(" + u0 + "," + v0 + "): " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- failure cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsForeignBlock(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
                layout.put(CASING_CELL, BlockKind.OTHER);
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                        worldAt(CONTROLLER, facing, u0, v0, layout));

                assertRejects(result, "wrong block", facing, u0, v0);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsExtraController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
                layout.put(CASING_CELL, BlockKind.CONTROLLER);
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                        worldAt(CONTROLLER, facing, u0, v0, layout));

                assertRejects(result, "extra controller", facing, u0, v0);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsNoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                        worldAt(CONTROLLER, facing, u0, v0, layoutWithPorts(u0, v0)));

                assertRejects(result, "expected at least one power port", facing, u0, v0);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsThreePowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                        worldAt(CONTROLLER, facing, u0, v0, layoutWithPorts(u0, v0, PORT_A, PORT_B, PORT_C)));

                assertRejects(result, "expected at most two power ports", facing, u0, v0);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsCompleteBoxBuiltInFrontOfController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                        worldBuiltInFront(CONTROLLER, facing, u0, v0));

                assertRejects(result, "wrong block", facing, u0, v0);
            }
        }
    }

    // ---------------------------------------------------------------- operating panel failure cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsMissingOperatingPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // Only v0=0 can have a valid panel position; test missing panel there
            int v0 = 0;
            Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
            // Remove the operating panel (replace with casing)
            layout.put(new Cell(u0, v0 + 1, 0), BlockKind.CASING);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layout));

            assertRejects(result, "expected exactly one operating panel above controller, found 0", facing, u0, v0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsOperatingPanelInWrongPosition(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            int v0 = 0;
            Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
            // Move panel to wrong position (e.g., back face)
            layout.put(new Cell(u0, v0 + 1, 0), BlockKind.CASING);
            layout.put(new Cell(u0, v0, SHAPE.length() - 1), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layout));

            assertRejects(result, "operating panel at wrong position", facing, u0, v0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsSecondOperatingPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            int v0 = 0;
            Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
            // Add a second operating panel at another front-face top cell
            int otherU = (u0 + 1) % SHAPE.width();
            layout.put(new Cell(otherU, v0 + 1, 0), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layout));

            assertRejects(result, "operating panel at wrong position", facing, u0, v0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsOperatingPanelInsteadOfController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            int v0 = 0;
            Map<Cell, BlockKind> layout = layoutWithPorts(u0, v0, PORT_A, PORT_B);
            // Replace controller with operating panel
            layout.put(new Cell(u0, v0, 0), BlockKind.OPERATING_PANEL);
            // Keep the panel above (now two panels, no controller)
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, v0, layout));

            assertRejects(result, "operating panel at wrong position", facing, u0, v0);
        }
    }

    // ---------------------------------------------------------------- invariants

    @Test
    void match_isDeterministic() {
        Function<MultiblockPos, BlockKind> world =
                worldAt(CONTROLLER, MultiblockDirection.WEST, 0, 0, layoutWithPorts(0, 0, PORT_A, PORT_B));

        MatchResult first = StructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, SHAPE, world);
        MatchResult second = StructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, SHAPE, world);

        assertEquals(first.success(), second.success());
        assertEquals(first.failureReason(), second.failureReason());
        assertEquals(first.memberPositions(), second.memberPositions());
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void expectedBox_containsTheControllerCell(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                assertTrue(expectedCells(CONTROLLER, facing, u0, v0).contains(CONTROLLER),
                        "controller cell missing for facing=" + facing + " offset=(" + u0 + "," + v0 + ")");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void expectedBox_hasNoDuplicateCells(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int v0 = 0; v0 < SHAPE.height(); v0++) {
                List<MultiblockPos> cells = expectedCells(CONTROLLER, facing, u0, v0);
                assertEquals(SHAPE.volume(), cells.size(),
                        "facing=" + facing + " offset=(" + u0 + "," + v0 + ")");
                assertEquals(SHAPE.volume(), new HashSet<>(cells).size(),
                        "duplicate cells for facing=" + facing + " offset=(" + u0 + "," + v0 + ")");
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Asserts the structure does not form at this offset.
     *
     * <p>{@link StructureMatcher#match} tries every front face offset and reports the failure of the
     * first one it tried, so only the first offset tried, {@code (0, 0)}, has a predictable reason:
     * for a later offset the reported reason belongs to an earlier, differently placed box. The
     * reason is therefore pinned for {@code (0, 0)} only, and every offset is still required to
     * fail.
     */
    private static void assertRejects(MatchResult result, String firstTriedReason, MultiblockDirection facing,
            int u0, int v0) {
        String where = "facing=" + facing + " offset=(" + u0 + "," + v0 + ")";
        assertFalse(result.success(), where + " should not have formed");
        assertNotNull(result.failureReason(), where + " needs a reason");
        assertTrue(result.memberPositions().isEmpty(), where + " must report no members on failure");

        if (u0 == 0 && v0 == 0) {
            assertTrue(result.failureReason().contains(firstTriedReason),
                    where + ": expected a reason containing '" + firstTriedReason + "' but got '"
                            + result.failureReason() + "'");
        }
    }

    /** A cell inside the box, addressed in local (u, v, d) coordinates. */
    private record Cell(int u, int v, int d) {}
}