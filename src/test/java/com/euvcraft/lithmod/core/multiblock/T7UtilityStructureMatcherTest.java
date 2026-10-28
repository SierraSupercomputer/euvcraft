package com.euvcraft.lithmod.core.multiblock;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class T7UtilityStructureMatcherTest {

    private static final MultiblockPos ANCHOR = new MultiblockPos(100, 64, 200);

    // ---------------------------------------------------------------- geometry oracle (independent of production)

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

    /** right = f rotated 90 degrees clockwise seen from above. */
    private static MultiblockPos rightVector(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(1, 0, 0);
            case SOUTH -> new MultiblockPos(-1, 0, 0);
            case EAST -> new MultiblockPos(0, 0, 1);
            case WEST -> new MultiblockPos(0, 0, -1);
        };
    }

    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

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

    /** All cells of the box, in (u, v, d) iteration order. */
    private static List<MultiblockPos> expectedCells(MultiblockPos controller, MultiblockDirection facing, int u0,
            int v0, StructureShape shape) {
        List<MultiblockPos> cells = new ArrayList<>(shape.volume());
        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    cells.add(expectedCell(controller, facing, u0, v0, u, v, d));
                }
            }
        }
        return cells;
    }

    /** Turns a local-cell layout into a world lookup; unlisted positions are {@link BlockKind#OTHER}. */
    private static Function<MultiblockPos, BlockKind> worldAt(MultiblockPos controller, MultiblockDirection facing,
            int u0, int v0, Map<Cell, BlockKind> layout, StructureShape shape) {
        Map<MultiblockPos, BlockKind> blocks = new HashMap<>();
        for (Map.Entry<Cell, BlockKind> entry : layout.entrySet()) {
            Cell cell = entry.getKey();
            blocks.put(expectedCell(controller, facing, u0, v0, cell.u(), cell.v(), cell.d()), entry.getValue());
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    /** A local cell inside the box, addressed in local (u, v, d) coordinates. */
    private record Cell(int u, int v, int d) {}

    // ---------------------------------------------------------------- Purifier (2x2x3) fixtures

    private static Map<Cell, BlockKind> purifierFixture(int u0, Cell gasInlet, Cell gasOutlet, Cell... powerPorts) {
        Map<Cell, BlockKind> layout = new HashMap<>();
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        layout.put(new Cell(u0, 0, 0), BlockKind.CONTROLLER);
        layout.put(new Cell(u0, 1, 0), BlockKind.OPERATING_PANEL);
        layout.put(gasInlet, BlockKind.GAS_INLET);
        layout.put(gasOutlet, BlockKind.GAS_OUTLET);
        for (Cell port : powerPorts) {
            layout.put(port, BlockKind.POWER_PORT);
        }
        return layout;
    }

    // ---------------------------------------------------------------- Vacuum Plant (3x3x5) fixtures

    private static Map<Cell, BlockKind> vacuumFixture(int u0, Cell vacuumConduit, Cell vacuumPort, Cell... powerPorts) {
        Map<Cell, BlockKind> layout = new HashMap<>();
        StructureShape shape = T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE;
        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    layout.put(new Cell(u, v, d), BlockKind.CASING);
                }
            }
        }
        layout.put(new Cell(u0, 0, 0), BlockKind.CONTROLLER);
        layout.put(new Cell(u0, 1, 0), BlockKind.OPERATING_PANEL);
        layout.put(vacuumConduit, BlockKind.VACUUM_CONDUIT);
        layout.put(vacuumPort, BlockKind.VACUUM_PORT);
        for (Cell port : powerPorts) {
            layout.put(port, BlockKind.POWER_PORT);
        }
        return layout;
    }

    // ---------------------------------------------------------------- Purifier success cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_everyFacingAndFrontColumnAccepts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        for (int column = 0; column < shape.width(); column++) {
            // Place inlet and outlet at distinct non-reserved cells
            Cell inlet = new Cell((column + 1) % shape.width(), 2, 1);
            Cell outlet = new Cell((column + 1) % shape.width(), 0, 2);
            Cell port = new Cell((column + 1) % shape.width(), 1, 2);
            var cells = purifierFixture(column, inlet, outlet, port);
            var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertTrue(result.success(), facing + " column " + column + ": " + result.failureReason());
            assertEquals(shape.volume(), result.memberPositions().size());
            assertEquals(new HashSet<>(expectedCells(ANCHOR, facing, column, 0, shape)),
                    new HashSet<>(result.memberPositions()));
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_acceptsOneToFourPowerPorts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        int column = 0;
        Cell inlet = new Cell(1, 2, 1);
        Cell outlet = new Cell(1, 0, 2);
        for (int ports = 1; ports <= 4; ports++) {
            Cell[] portCells = new Cell[ports];
            int idx = 0;
            for (int u = 0; u < shape.width() && idx < ports; u++) {
                for (int v = 0; v < shape.height() && idx < ports; v++) {
                    for (int d = 0; d < shape.length() && idx < ports; d++) {
                        Cell c = new Cell(u, v, d);
                        // Skip controller, panel, inlet, outlet
                        if (c.equals(new Cell(column, 0, 0)) || c.equals(new Cell(column, 1, 0))
                                || c.equals(inlet) || c.equals(outlet)) continue;
                        portCells[idx++] = c;
                    }
                }
            }
            var cells = purifierFixture(column, inlet, outlet, portCells);
            var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertTrue(result.success(), facing + " " + ports + " ports: " + result.failureReason());
        }
    }

    @Test
    void purifier_inletAndOutletMaySitAnywhereExceptReserved() {
        var f = MultiblockDirection.SOUTH;
        var cells = purifierFixture(0, new Cell(0, 0, 1), new Cell(1, 1, 2), new Cell(1, 0, 2));
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, f, p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertTrue(result.success());
        assertEquals(T7UtilityStructureMatcher.PURIFIER_SHAPE.volume(),
                new HashSet<>(result.memberPositions()).size());
    }

    // ---------------------------------------------------------------- Purifier failure cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsMissingInlet(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(1, 2, 1), BlockKind.CASING); // remove inlet
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("gas inlet"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsMissingOutlet(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(1, 0, 2), BlockKind.CASING); // remove outlet
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("gas outlet"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsExtraInlet(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(0, 2, 2), BlockKind.GAS_INLET); // extra inlet
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("gas inlet"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsExtraOutlet(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(0, 2, 2), BlockKind.GAS_OUTLET); // extra outlet
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("gas outlet"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsZeroPowerPorts(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2));
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("power port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsFivePowerPorts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        Cell inlet = new Cell(1, 2, 1);
        Cell outlet = new Cell(1, 0, 2);
        Cell[] ports = new Cell[5];
        int idx = 0;
        for (int u = 0; u < shape.width() && idx < 5; u++) {
            for (int v = 0; v < shape.height() && idx < 5; v++) {
                for (int d = 0; d < shape.length() && idx < 5; d++) {
                    Cell c = new Cell(u, v, d);
                    if (c.equals(new Cell(0, 0, 0)) || c.equals(new Cell(0, 1, 0))
                            || c.equals(inlet) || c.equals(outlet)) continue;
                    ports[idx++] = c;
                }
            }
        }
        var cells = purifierFixture(0, inlet, outlet, ports);
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("power port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsForeignKinds(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(1, 1, 1), BlockKind.WATER_INLET);
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("not allowed in purifier structure"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsExtraController(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(1, 1, 1), BlockKind.CONTROLLER);
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("extra controller"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsWrongPanelPosition(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(0, 1, 0), BlockKind.CASING); // remove correct panel
        cells.put(new Cell(1, 1, 0), BlockKind.OPERATING_PANEL); // panel at wrong column
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel at wrong position"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_rejectsMissingPanel(MultiblockDirection facing) {
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        cells.put(new Cell(0, 1, 0), BlockKind.CASING); // remove panel
        var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void purifier_structureBuiltForOneFacingIsRefusedUnderAnyOtherFacing(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.PURIFIER_SHAPE;
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        for (var other : MultiblockDirection.values()) {
            if (other == facing) continue;
            var result = T7UtilityStructureMatcher.matchPurifier(ANCHOR, other,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertFalse(result.success(), facing + " volume must not match under facing " + other);
            assertNotNull(result.failureReason());
            assertFalse(result.failureReason().isBlank());
        }
    }

    @Test
    void purifier_nullArgumentsAreRejected() {
        var f = MultiblockDirection.WEST;
        var cells = purifierFixture(0, new Cell(1, 2, 1), new Cell(1, 0, 2), new Cell(1, 0, 2));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchPurifier(null, f, p -> cells.getOrDefault(p, BlockKind.OTHER)));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchPurifier(ANCHOR, null, p -> cells.getOrDefault(p, BlockKind.OTHER)));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchPurifier(ANCHOR, f, null));
    }

    @Test
    void purifier_matcherStaysPureCoreJava() {
        assertEquals(Object.class, T7UtilityStructureMatcher.class.getSuperclass(), "no base class beyond Object");
        for (var field : T7UtilityStructureMatcher.class.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            assertTrue(isCoreJvmType(field.getType()), "field " + field.getName() + " must be a core JVM type");
        }
        for (var method : T7UtilityStructureMatcher.class.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            assertTrue(isCoreJvmType(method.getReturnType()), "return of " + method.getName() + " must be a core JVM type");
            for (var parameter : method.getParameterTypes()) {
                assertTrue(isCoreJvmType(parameter), "parameter of " + method.getName() + " must be a core JVM type");
            }
        }
    }

    // ---------------------------------------------------------------- Vacuum Plant success cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_everyFacingAndFrontColumnAccepts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE;
        for (int column = 0; column < shape.width(); column++) {
            Cell conduit = new Cell((column + 1) % shape.width(), 2, 2);
            Cell port = new Cell((column + 2) % shape.width(), 0, 3);
            Cell power = new Cell((column + 1) % shape.width(), 1, 4);
            var cells = vacuumFixture(column, conduit, port, power);
            var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertTrue(result.success(), facing + " column " + column + ": " + result.failureReason());
            assertEquals(shape.volume(), result.memberPositions().size());
            assertEquals(new HashSet<>(expectedCells(ANCHOR, facing, column, 0, shape)),
                    new HashSet<>(result.memberPositions()));
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_acceptsOneToFourPowerPorts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE;
        int column = 0;
        Cell conduit = new Cell(1, 2, 2);
        Cell port = new Cell(2, 0, 3);
        for (int ports = 1; ports <= 4; ports++) {
            Cell[] portCells = new Cell[ports];
            int idx = 0;
            for (int u = 0; u < shape.width() && idx < ports; u++) {
                for (int v = 0; v < shape.height() && idx < ports; v++) {
                    for (int d = 0; d < shape.length() && idx < ports; d++) {
                        Cell c = new Cell(u, v, d);
                        if (c.equals(new Cell(column, 0, 0)) || c.equals(new Cell(column, 1, 0))
                                || c.equals(conduit) || c.equals(port)) continue;
                        portCells[idx++] = c;
                    }
                }
            }
            var cells = vacuumFixture(column, conduit, port, portCells);
            var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertTrue(result.success(), facing + " " + ports + " ports: " + result.failureReason());
        }
    }

    @Test
    void vacuum_conduitAndPortMaySitAnywhereExceptReserved() {
        var f = MultiblockDirection.SOUTH;
        var cells = vacuumFixture(0, new Cell(0, 0, 1), new Cell(1, 1, 2), new Cell(2, 0, 4));
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, f, p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertTrue(result.success());
        assertEquals(T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE.volume(),
                new HashSet<>(result.memberPositions()).size());
    }

    // ---------------------------------------------------------------- Vacuum Plant failure cases

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsMissingConduit(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(1, 2, 2), BlockKind.CASING); // remove conduit
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("vacuum conduit"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsMissingPort(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(2, 0, 3), BlockKind.CASING); // remove port
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("vacuum port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsExtraConduit(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(0, 2, 4), BlockKind.VACUUM_CONDUIT); // extra conduit
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("vacuum conduit"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsExtraPort(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(0, 2, 4), BlockKind.VACUUM_PORT); // extra port
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("vacuum port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsZeroPowerPorts(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3));
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("power port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsFivePowerPorts(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE;
        Cell conduit = new Cell(1, 2, 2);
        Cell port = new Cell(2, 0, 3);
        Cell[] ports = new Cell[5];
        int idx = 0;
        for (int u = 0; u < shape.width() && idx < 5; u++) {
            for (int v = 0; v < shape.height() && idx < 5; v++) {
                for (int d = 0; d < shape.length() && idx < 5; d++) {
                    Cell c = new Cell(u, v, d);
                    if (c.equals(new Cell(0, 0, 0)) || c.equals(new Cell(0, 1, 0))
                            || c.equals(conduit) || c.equals(port)) continue;
                    ports[idx++] = c;
                }
            }
        }
        var cells = vacuumFixture(0, conduit, port, ports);
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("power port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsForeignKinds(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(1, 1, 1), BlockKind.GAS_INLET);
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("not allowed in vacuum plant structure"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsExtraController(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(1, 1, 1), BlockKind.CONTROLLER);
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("extra controller"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsWrongPanelPosition(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(0, 1, 0), BlockKind.CASING); // remove correct panel
        cells.put(new Cell(1, 1, 0), BlockKind.OPERATING_PANEL); // panel at wrong column
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel at wrong position"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_rejectsMissingPanel(MultiblockDirection facing) {
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        cells.put(new Cell(0, 1, 0), BlockKind.CASING); // remove panel
        var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, facing,
                p -> cells.getOrDefault(p, BlockKind.OTHER));
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void vacuum_structureBuiltForOneFacingIsRefusedUnderAnyOtherFacing(MultiblockDirection facing) {
        StructureShape shape = T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE;
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        for (var other : MultiblockDirection.values()) {
            if (other == facing) continue;
            var result = T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, other,
                    p -> cells.getOrDefault(p, BlockKind.OTHER));
            assertFalse(result.success(), facing + " volume must not match under facing " + other);
            assertNotNull(result.failureReason());
            assertFalse(result.failureReason().isBlank());
        }
    }

    @Test
    void vacuum_nullArgumentsAreRejected() {
        var f = MultiblockDirection.WEST;
        var cells = vacuumFixture(0, new Cell(1, 2, 2), new Cell(2, 0, 3), new Cell(2, 0, 4));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchVacuumPlant(null, f, p -> cells.getOrDefault(p, BlockKind.OTHER)));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, null, p -> cells.getOrDefault(p, BlockKind.OTHER)));
        assertThrows(NullPointerException.class,
                () -> T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR, f, null));
    }

    // ---------------------------------------------------------------- helpers

    /** True when the type is a JDK type or declared in the pure core multiblock package. */
    private static boolean isCoreJvmType(Class<?> type) {
        while (type.isArray()) type = type.getComponentType();
        String name = type.getName();
        return type.isPrimitive() || name.startsWith("java.") || name.startsWith(T7UtilityStructureMatcher.class.getPackageName() + ".");
    }
}