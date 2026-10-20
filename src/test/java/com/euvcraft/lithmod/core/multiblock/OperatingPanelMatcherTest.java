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
 * Focused tests for the Operating Panel requirement.
 *
 * <p>These tests verify the exact positioning rules for the operating panel:
 * <ul>
 *   <li>Exactly one operating panel required directly above the controller (at local {@code (u0, v0+1, 0)})</li>
 *   <li>Controller must be on bottom row ({@code v0 = 0}) for a height-2 shape</li>
 *   <li>All four horizontal facings and both front-face columns (u0=0, u0=1) work</li>
 * </ul>
 *
 * <p>Expectations are derived from the geometric definition and do not call
 * {@link StructureMatcher} to build them.
 */
class OperatingPanelMatcherTest {

    private static final StructureShape SHAPE = StructureShape.PROCESS_CELL;
    private static final MultiblockPos CONTROLLER = new MultiblockPos(10, 5, 10);
    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

    // Back-face ports for a valid box
    private static final Cell PORT_A = new Cell(0, 0, SHAPE.length() - 1);
    private static final Cell PORT_B = new Cell(1, 1, SHAPE.length() - 1);

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
     * right = f rotated 90 degrees clockwise seen from above.
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

    /** Expected world position of the operating panel for a given facing and u0 (v0 is always 0). */
    private static MultiblockPos expectedPanelPos(MultiblockPos controller, MultiblockDirection facing, int u0) {
        // Panel is at local (u0, 1, 0) when controller is at (u0, 0, 0)
        return expectedCell(controller, facing, u0, 0, u0, 1, 0);
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

    /** A complete valid box with operating panel above controller at (u0, 0), plus given ports. */
    private static Map<Cell, BlockKind> validLayout(int u0, Cell... ports) {
        int v0 = 0; // Controller must be on bottom row
        Map<Cell, BlockKind> layout = new HashMap<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        layout.put(new Cell(u0, v0, 0), BlockKind.CONTROLLER);
        layout.put(new Cell(u0, v0 + 1, 0), BlockKind.OPERATING_PANEL);
        for (Cell port : ports) {
            layout.put(port, BlockKind.POWER_PORT);
        }
        return layout;
    }

    // ---------------------------------------------------------------- success: exact above, all facings, both columns

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_exactlyAboveController_succeedsForAllFacingsAndColumns(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B));
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE, world);

            assertTrue(result.success(),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
            assertEquals(SHAPE.volume(), result.memberPositions().size());

            // Verify the operating panel is at the expected world position
            MultiblockPos expectedPanel = expectedPanelPos(CONTROLLER, facing, u0);
            assertTrue(result.memberPositions().contains(expectedPanel),
                    "facing=" + facing + " u0=" + u0 + ": panel not at expected position " + expectedPanel);
        }
    }

    @Test
    void operatingPanel_memberPositionsIncludeAll12Cells() {
        // Spot-check one facing/column to verify the full 12-cell set
        MultiblockDirection facing = MultiblockDirection.NORTH;
        int u0 = 0;
        Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0, PORT_A, PORT_B));
        MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE, world);

        assertTrue(result.success());
        assertEquals(12, result.memberPositions().size());

        // Build expected set independently
        List<MultiblockPos> expected = new ArrayList<>(12);
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    expected.add(expectedCell(CONTROLLER, facing, u0, 0, u, v, d));
                }
            }
        }
        assertEquals(new HashSet<>(expected), new HashSet<>(result.memberPositions()));
    }

    // ---------------------------------------------------------------- failure: missing panel

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_missingPanel_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Remove the operating panel (replace with casing)
            layout.put(new Cell(u0, 1, 0), BlockKind.CASING);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail without panel");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("expected exactly one operating panel above controller, found 0"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- failure: panel in wrong position

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_panelAtBackFace_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Move panel to back face top cell
            layout.put(new Cell(u0, 1, 0), BlockKind.CASING);
            layout.put(new Cell(u0, 1, SHAPE.length() - 1), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail with panel at back");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("operating panel at wrong position"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_panelAtSideFace_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Move panel to side face (other column, top row, front)
            int otherU = (u0 + 1) % SHAPE.width();
            layout.put(new Cell(u0, 1, 0), BlockKind.CASING);
            layout.put(new Cell(otherU, 1, 0), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail with panel at side");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("operating panel at wrong position"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_panelAtControllerPosition_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Replace controller with panel, keep panel above (now two panels, no controller)
            layout.put(new Cell(u0, 0, 0), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail with panel at controller pos");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("operating panel at wrong position"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- failure: second panel

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_secondPanel_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0, PORT_A, PORT_B);
            // Add a second operating panel at the other front top cell
            int otherU = (u0 + 1) % SHAPE.width();
            layout.put(new Cell(otherU, 1, 0), BlockKind.OPERATING_PANEL);
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 0, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail with second panel");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("operating panel at wrong position"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- failure: controller on top row

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void operatingPanel_controllerOnTopRow_fails(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            // v0 = 1 (top row) - no room for panel above
            Map<Cell, BlockKind> layout = new HashMap<>();
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    for (int d = 0; d < SHAPE.length(); d++) {
                        layout.put(new Cell(u, v, d), BlockKind.CASING);
                    }
                }
            }
            layout.put(new Cell(u0, 1, 0), BlockKind.CONTROLLER);
            layout.put(PORT_A, BlockKind.POWER_PORT);
            layout.put(PORT_B, BlockKind.POWER_PORT);
            // No panel possible above v=1 in a height-2 shape
            MatchResult result = StructureMatcher.match(CONTROLLER, facing, SHAPE,
                    worldAt(CONTROLLER, facing, u0, 1, layout));

            assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " should fail with controller on top row");
            assertNotNull(result.failureReason());
            assertTrue(result.memberPositions().isEmpty(), "failed match must expose no members");
            // Public match reports the first attempted offset (0,0), not this fixture offset.
            if (u0 == 0) assertTrue(result.failureReason().contains("wrong block"),
                    "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** A cell inside the box, addressed in local (u, v, d) coordinates. */
    private record Cell(int u, int v, int d) {}
}