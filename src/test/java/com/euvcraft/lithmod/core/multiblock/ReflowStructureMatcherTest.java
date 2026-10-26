package com.euvcraft.lithmod.core.multiblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReflowStructureMatcher}.
 *
 * <p>The geometric oracle below is written from the definition of the basis, not by calling the
 * production helper: {@code back = -facing}, {@code right} is the facing rotated 90 degrees
 * clockwise seen from above, and {@code up = +Y}. Cell {@code (u, v, d)} is
 * {@code controller + (u - u0)*right + (v - v0)*up + d*back}.
 */
class ReflowStructureMatcherTest {

    private static final StructureShape SHAPE = ReflowStructureMatcher.REFLOW_SHAPE;
    private static final MultiblockPos CONTROLLER = new MultiblockPos(10, 5, 10);
    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

    /** Middle layer is v = 1 (bottom=0, middle=1, top=2). */
    private static final int MIDDLE = 1;

    // ---------------------------------------------------------------- geometry oracle

    private static MultiblockPos facingVector(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(0, 0, -1);
            case SOUTH -> new MultiblockPos(0, 0, 1);
            case EAST -> new MultiblockPos(1, 0, 0);
            case WEST -> new MultiblockPos(-1, 0, 0);
        };
    }

    private static MultiblockPos backVector(MultiblockDirection facing) {
        return facingVector(facing).multiply(-1);
    }

    private static MultiblockPos rightVector(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(1, 0, 0);
            case SOUTH -> new MultiblockPos(-1, 0, 0);
            case EAST -> new MultiblockPos(0, 0, 1);
            case WEST -> new MultiblockPos(0, 0, -1);
        };
    }

    private static MultiblockPos boxOrigin(MultiblockPos controller, MultiblockDirection facing, int u0, int v0) {
        return controller.subtract(rightVector(facing).multiply(u0)).subtract(UP.multiply(v0));
    }

    private static MultiblockPos expectedCell(MultiblockPos controller, MultiblockDirection facing, int u0,
            int v0, int u, int v, int d) {
        return boxOrigin(controller, facing, u0, v0)
                .add(rightVector(facing).multiply(u))
                .add(UP.multiply(v))
                .add(backVector(facing).multiply(d));
    }

    private static List<MultiblockPos> expectedCells(MultiblockPos controller, MultiblockDirection facing,
            int u0, int v0) {
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

    private record Cell(int u, int v, int d) {}

    // ---------------------------------------------------------------- world builders

    private static Function<MultiblockPos, BlockKind> worldAt(MultiblockPos controller,
            MultiblockDirection facing, int u0, int v0, Map<Cell, BlockKind> layout) {
        Map<MultiblockPos, BlockKind> blocks = new HashMap<>();
        for (Map.Entry<Cell, BlockKind> entry : layout.entrySet()) {
            Cell cell = entry.getKey();
            blocks.put(expectedCell(controller, facing, u0, v0, cell.u(), cell.v(), cell.d()), entry.getValue());
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    /** A complete valid 3x3x2 reflow layout with the controller in column {@code u0}. */
    private static Map<Cell, BlockKind> validLayout(int u0) {
        Map<Cell, BlockKind> layout = new LinkedHashMap<>();
        for (int u = 0; u < SHAPE.width(); u++) {
            for (int v = 0; v < SHAPE.height(); v++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        layout.put(new Cell(u0, 0, 0), BlockKind.CONTROLLER);
        layout.put(new Cell(u0, MIDDLE, 0), BlockKind.OPERATING_PANEL);
        // The first middle-layer cell that is not the operating panel cell.
        Cell calibration = null;
        for (int u = 0; u < SHAPE.width() && calibration == null; u++) {
            for (int d = 0; d < SHAPE.length() && calibration == null; d++) {
                if (u == u0 && d == 0) continue;
                calibration = new Cell(u, MIDDLE, d);
            }
        }
        layout.put(calibration, BlockKind.CALIBRATION_PANEL);
        // Two power ports in opposite back-face corners, never on the middle layer.
        layout.put(new Cell(0, 0, SHAPE.length() - 1), BlockKind.POWER_PORT);
        layout.put(new Cell(SHAPE.width() - 1, SHAPE.height() - 1, SHAPE.length() - 1),
                BlockKind.POWER_PORT);
        return layout;
    }

    private static Cell calibrationCell(Map<Cell, BlockKind> layout) {
        for (Map.Entry<Cell, BlockKind> entry : layout.entrySet()) {
            if (entry.getValue() == BlockKind.CALIBRATION_PANEL) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("layout has no calibration panel");
    }

    // ---------------------------------------------------------------- success cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_formsForEveryFacingAndEveryColumn(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0));
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);

            assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
            assertEquals(SHAPE.volume(), result.memberPositions().size(), "facing=" + facing + " u0=" + u0);
            assertEquals(new HashSet<>(expectedCells(CONTROLLER, facing, u0, 0)),
                    new HashSet<>(result.memberPositions()), "member cells for facing=" + facing + " u0=" + u0);
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_returnsExactly18MembersInCanonicalOrder(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0));
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);

            assertTrue(result.success());
            assertEquals(18, result.memberPositions().size());
            List<MultiblockPos> members = result.memberPositions();
            // Canonical order is u*6 + v*2 + d.
            for (int index = 0; index < 18; index++) {
                int u = index / 6;
                int v = (index % 6) / 2;
                int d = index % 2;
                assertEquals(expectedCell(CONTROLLER, facing, u0, 0, u, v, d), members.get(index),
                        "facing=" + facing + " u0=" + u0 + " index=" + index + " order mismatch");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsOnePowerPort(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell(0, 0, SHAPE.length() - 1), BlockKind.CASING);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsTwoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0));
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsEveryMiddleLayerCalibrationPosition(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int d = 0; d < SHAPE.length(); d++) {
                    if (u == u0 && d == 0) continue; // operating panel cell
                    Map<Cell, BlockKind> layout = validLayout(u0);
                    layout.put(calibrationCell(layout), BlockKind.CASING);
                    layout.put(new Cell(u, MIDDLE, d), BlockKind.CALIBRATION_PANEL);
                    Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
                    MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
                    assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + " cal=(" + u + "," + d
                            + "): " + result.failureReason());
                }
            }
        }
    }

    // ---------------------------------------------------------------- failure: parts and counts

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsNoPowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell(0, 0, SHAPE.length() - 1), BlockKind.CASING);
            layout.put(new Cell(SHAPE.width() - 1, SHAPE.height() - 1, SHAPE.length() - 1), BlockKind.CASING);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("expected at least one power port"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsThreePowerPorts(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell(1, 2, 0), BlockKind.POWER_PORT);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("expected at most two power ports"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsMissingCalibrationPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(calibrationCell(layout), BlockKind.CASING);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("expected exactly one calibration panel"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsExtraCalibrationPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            Cell spare = null;
            for (int u = 0; u < SHAPE.width() && spare == null; u++) {
                for (int d = 0; d < SHAPE.length() && spare == null; d++) {
                    Cell cell = new Cell(u, MIDDLE, d);
                    if (u == u0 && d == 0) continue;
                    if (layout.get(cell) == BlockKind.CASING) {
                        spare = cell;
                    }
                }
            }
            layout.put(spare, BlockKind.CALIBRATION_PANEL);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("expected exactly one calibration panel"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsMissingOperatingPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell(u0, MIDDLE, 0), BlockKind.CASING);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("expected exactly one operating panel"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsExtraOperatingPanel(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell((u0 + 1) % SHAPE.width(), MIDDLE, 0), BlockKind.OPERATING_PANEL);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("operating panel at wrong position"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsExtraController(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell((u0 + 1) % SHAPE.width(), 0, 1), BlockKind.CONTROLLER);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("extra controller"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsOperatingPanelOnWrongLayer(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(new Cell(u0, MIDDLE, 0), BlockKind.CASING);
            layout.put(new Cell(u0, 2, 0), BlockKind.OPERATING_PANEL);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("operating panel at wrong position"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsCalibrationPanelOutsideMiddleLayer(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            layout.put(calibrationCell(layout), BlockKind.CASING);
            layout.put(new Cell((u0 + 1) % SHAPE.width(), 0, 1), BlockKind.CALIBRATION_PANEL);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertFalse(result.success());
            if (u0 == 0) {
                assertTrue(result.failureReason().contains("calibration panel at wrong position"));
            }
        }
    }

    // ---------------------------------------------------------------- failure: foreign blocks

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsEveryForeignBlockKind(MultiblockDirection facing) {
        BlockKind[] foreign = {
            BlockKind.OTHER, BlockKind.WATER_INLET, BlockKind.DIAGNOSTIC_PANEL, BlockKind.FLUID_OUTLET,
            BlockKind.EXPOSURE_MODULE, BlockKind.OPTICS_MODULE, BlockKind.STAGE_MODULE,
            BlockKind.FILTRATION_MODULE, BlockKind.METROLOGY_MODULE
        };
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            for (BlockKind kind : foreign) {
                Map<Cell, BlockKind> layout = validLayout(u0);
                layout.put(new Cell(1, 2, 1), kind);
                Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
                MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
                assertFalse(result.success(), "facing=" + facing + " u0=" + u0 + " kind=" + kind
                        + " should fail");
                assertNotNull(result.failureReason());
            }
        }
    }

    // ---------------------------------------------------------------- dimensions and placement

    @Test
    void shape_isTheFixed3x3x2Box() {
        assertEquals(3, SHAPE.width());
        assertEquals(3, SHAPE.height());
        assertEquals(2, SHAPE.length());
        assertEquals(18, SHAPE.volume());
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_acceptsPowerPortsAnywhereInCasingCells(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Map<Cell, BlockKind> layout = validLayout(u0);
            // Replace the two corner ports with ports at arbitrary non-panel positions.
            layout.put(new Cell(0, 0, SHAPE.length() - 1), BlockKind.CASING);
            layout.put(new Cell(SHAPE.width() - 1, SHAPE.height() - 1, SHAPE.length() - 1), BlockKind.CASING);
            layout.put(new Cell(1, 2, 1), BlockKind.POWER_PORT);
            layout.put(new Cell(2, 2, 1), BlockKind.POWER_PORT);
            Function<MultiblockPos, BlockKind> world = worldAt(CONTROLLER, facing, u0, 0, layout);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, world);
            assertTrue(result.success(), "facing=" + facing + " u0=" + u0 + ": " + result.failureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_rejectsWrongDimensions(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            Function<MultiblockPos, BlockKind> base = worldAt(CONTROLLER, facing, u0, 0, validLayout(u0));
            // A 3x3x1 world: the whole back layer (d = 1) reports OTHER, so the box is too small.
            Set<MultiblockPos> backLayer = new HashSet<>();
            for (int u = 0; u < SHAPE.width(); u++) {
                for (int v = 0; v < SHAPE.height(); v++) {
                    backLayer.add(expectedCell(CONTROLLER, facing, u0, 0, u, v, 1));
                }
            }
            Function<MultiblockPos, BlockKind> wrongDim =
                    pos -> backLayer.contains(pos) ? BlockKind.OTHER : base.apply(pos);
            MatchResult result = ReflowStructureMatcher.match(CONTROLLER, facing, wrongDim);
            assertFalse(result.success(), "facing=" + facing + " u0=" + u0
                    + ": a 3x3x1 shell must not match");
        }
    }

    // ---------------------------------------------------------------- invariants

    @Test
    void match_isDeterministic() {
        Function<MultiblockPos, BlockKind> world =
                worldAt(CONTROLLER, MultiblockDirection.WEST, 0, 0, validLayout(0));
        MatchResult first = ReflowStructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, world);
        MatchResult second = ReflowStructureMatcher.match(CONTROLLER, MultiblockDirection.WEST, world);

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
    void expectedBox_isAn18CellRectangularPrism(MultiblockDirection facing) {
        for (int u0 = 0; u0 < SHAPE.width(); u0++) {
            List<MultiblockPos> cells = expectedCells(CONTROLLER, facing, u0, 0);
            assertEquals(SHAPE.volume(), cells.size());
            assertEquals(18, cells.size());
            assertEquals(SHAPE.volume(), new HashSet<>(cells).size(), "duplicate cells");
        }
    }
}
