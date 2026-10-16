package com.euvcraft.lithmod.core.multiblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StructureMatcher}.
 */
class StructureMatcherTest {

    private static final StructureShape SHAPE = StructureShape.PROCESS_CELL;

    /**
     * Creates a block kind function for a perfect structure with controller at given offset.
     * Power ports are placed at fixed local positions that work for all facings.
     */
    private Function<MultiblockPos, BlockKind> perfectStructure(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY) {
        MultiblockPos origin = calculateOrigin(controllerPos, facing, offsetX, offsetY);
        ControllerLocalPos controllerLocal = getControllerLocalPos(facing, offsetX, offsetY);
        
        // Fixed power port positions (local coordinates) - choose positions that are never the controller
        MultiblockPos powerPort1Local = getPowerPort1Local(facing);
        MultiblockPos powerPort2Local = getPowerPort2Local(facing);

        return pos -> {
            MultiblockPos local = pos.subtract(origin);
            if (local.x() < 0 || local.x() >= SHAPE.width() ||
                local.y() < 0 || local.y() >= SHAPE.height() ||
                local.z() < 0 || local.z() >= SHAPE.length()) {
                return BlockKind.OTHER;
            }
            // Check if this is the controller position
            if (local.x() == controllerLocal.x() && local.y() == controllerLocal.y() && local.z() == controllerLocal.z()) {
                return BlockKind.CONTROLLER;
            }
            // Check if this is a power port position
            if (local.equals(powerPort1Local) || local.equals(powerPort2Local)) {
                return BlockKind.POWER_PORT;
            }
            return BlockKind.CASING;
        };
    }

    /**
     * Creates a block kind function for a structure with one missing block (replaced with OTHER).
     */
    private Function<MultiblockPos, BlockKind> missingBlock(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY, MultiblockPos missingLocal) {
        Function<MultiblockPos, BlockKind> perfect = perfectStructure(controllerPos, facing, offsetX, offsetY);
        MultiblockPos origin = calculateOrigin(controllerPos, facing, offsetX, offsetY);
        MultiblockPos missingWorld = origin.add(missingLocal);
        return pos -> pos.equals(missingWorld) ? BlockKind.OTHER : perfect.apply(pos);
    }

    /**
     * Creates a block kind function for a structure with an extra controller.
     */
    private Function<MultiblockPos, BlockKind> extraController(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY, MultiblockPos extraLocal) {
        Function<MultiblockPos, BlockKind> perfect = perfectStructure(controllerPos, facing, offsetX, offsetY);
        MultiblockPos origin = calculateOrigin(controllerPos, facing, offsetX, offsetY);
        MultiblockPos extraWorld = origin.add(extraLocal);
        return pos -> pos.equals(extraWorld) ? BlockKind.CONTROLLER : perfect.apply(pos);
    }

    /**
     * Creates a block kind function for a structure with no power ports.
     */
    private Function<MultiblockPos, BlockKind> noPowerPorts(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY) {
        Function<MultiblockPos, BlockKind> perfect = perfectStructure(controllerPos, facing, offsetX, offsetY);
        return pos -> {
            BlockKind kind = perfect.apply(pos);
            return kind == BlockKind.POWER_PORT ? BlockKind.CASING : kind;
        };
    }

    /**
     * Creates a block kind function for a structure with three power ports.
     */
    private Function<MultiblockPos, BlockKind> threePowerPorts(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY) {
        Function<MultiblockPos, BlockKind> perfect = perfectStructure(controllerPos, facing, offsetX, offsetY);
        MultiblockPos origin = calculateOrigin(controllerPos, facing, offsetX, offsetY);
        // Add a third power port at a casing position
        MultiblockPos thirdPortLocal = getThirdPowerPortLocal(facing);
        MultiblockPos thirdPortWorld = origin.add(thirdPortLocal);
        return pos -> pos.equals(thirdPortWorld) ? BlockKind.POWER_PORT : perfect.apply(pos);
    }

    /**
     * Creates a block kind function for a structure with a wrong block inside (replaced with OTHER).
     */
    private Function<MultiblockPos, BlockKind> wrongBlock(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY, MultiblockPos wrongLocal) {
        Function<MultiblockPos, BlockKind> perfect = perfectStructure(controllerPos, facing, offsetX, offsetY);
        MultiblockPos origin = calculateOrigin(controllerPos, facing, offsetX, offsetY);
        MultiblockPos wrongWorld = origin.add(wrongLocal);
        return pos -> pos.equals(wrongWorld) ? BlockKind.OTHER : perfect.apply(pos);
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_perfectStructure_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        // Try all 4 controller positions on the front face
        for (int offsetX = 0; offsetX < SHAPE.width(); offsetX++) {
            for (int offsetY = 0; offsetY < SHAPE.height(); offsetY++) {
                Function<MultiblockPos, BlockKind> blockKindAt = perfectStructure(controllerPos, facing, offsetX, offsetY);
                MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
                assertTrue(result.success(), "facing=" + facing + " offset=(" + offsetX + "," + offsetY + "): " + result.failureReason());
                assertEquals(SHAPE.volume(), result.memberPositions().size());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_missingBlock_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        // Test with controller at (0,0) - missing a casing block (replaced with OTHER)
        MultiblockPos missingLocal = getTestCasingPos(facing);
        Function<MultiblockPos, BlockKind> blockKindAt = missingBlock(controllerPos, facing, 0, 0, missingLocal);
        MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("wrong block"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_extraController_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        // Extra controller at a casing position
        MultiblockPos extraLocal = getTestCasingPos(facing);
        Function<MultiblockPos, BlockKind> blockKindAt = extraController(controllerPos, facing, 0, 0, extraLocal);
        MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("extra controller"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_noPowerPorts_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        Function<MultiblockPos, BlockKind> blockKindAt = noPowerPorts(controllerPos, facing, 0, 0);
        MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("at least one power port"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_threePowerPorts_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        Function<MultiblockPos, BlockKind> blockKindAt = threePowerPorts(controllerPos, facing, 0, 0);
        MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("at most two power ports"));
    }

    @ParameterizedTest
    @EnumSource(MultiblockDirection.class)
    void match_wrongBlockInside_allFacings(MultiblockDirection facing) {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        MultiblockPos wrongLocal = getTestCasingPos(facing);
        Function<MultiblockPos, BlockKind> blockKindAt = wrongBlock(controllerPos, facing, 0, 0, wrongLocal);
        MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("wrong block"));
    }

    @Test
    void match_controllerAtAllFourPositions() {
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        MultiblockDirection facing = MultiblockDirection.NORTH;

        // Test all 4 positions on the front 2x2 face
        for (int offsetX = 0; offsetX < 2; offsetX++) {
            for (int offsetY = 0; offsetY < 2; offsetY++) {
                Function<MultiblockPos, BlockKind> blockKindAt = perfectStructure(controllerPos, facing, offsetX, offsetY);
                MatchResult result = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
                assertTrue(result.success(), "offset=(" + offsetX + "," + offsetY + "): " + result.failureReason());
            }
        }
    }

    @Test
    void match_deterministic() {
        // The same inputs should always produce the same result
        MultiblockPos controllerPos = new MultiblockPos(10, 5, 10);
        MultiblockDirection facing = MultiblockDirection.NORTH;
        Function<MultiblockPos, BlockKind> blockKindAt = perfectStructure(controllerPos, facing, 0, 0);

        MatchResult result1 = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);
        MatchResult result2 = StructureMatcher.match(controllerPos, facing, SHAPE, blockKindAt);

        assertEquals(result1.success(), result2.success());
        assertEquals(result1.failureReason(), result2.failureReason());
        assertEquals(result1.memberPositions(), result2.memberPositions());
    }

    // Helper to get controller local position (mirrors StructureMatcher logic)
    private ControllerLocalPos getControllerLocalPos(MultiblockDirection facing, int offsetX, int offsetY) {
        return switch (facing) {
            case NORTH -> new ControllerLocalPos(offsetX, offsetY, SHAPE.length() - 1);
            case SOUTH -> new ControllerLocalPos(offsetX, offsetY, 0);
            case EAST -> new ControllerLocalPos(offsetX, offsetY, 0);
            case WEST -> new ControllerLocalPos(offsetX, offsetY, SHAPE.length() - 1);
        };
    }

    // Helper to calculate origin (mirrors StructureMatcher logic)
    private MultiblockPos calculateOrigin(MultiblockPos controllerPos, MultiblockDirection facing, int offsetX, int offsetY) {
        ControllerLocalPos controllerLocal = getControllerLocalPos(facing, offsetX, offsetY);
        return switch (facing) {
            case NORTH -> new MultiblockPos(
                controllerPos.x() - controllerLocal.x(),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.z()
            );
            case SOUTH -> new MultiblockPos(
                controllerPos.x() - controllerLocal.x(),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.z()
            );
            case EAST -> new MultiblockPos(
                controllerPos.x(),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.x()
            );
            case WEST -> new MultiblockPos(
                controllerPos.x() - (SHAPE.length() - 1),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.x()
            );
        };
    }

    // Get power port 1 local position for each facing
    private MultiblockPos getPowerPort1Local(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(0, 0, 0);
            case SOUTH -> new MultiblockPos(0, 0, SHAPE.length() - 1);
            case EAST -> new MultiblockPos(0, 0, 1);
            case WEST -> new MultiblockPos(0, 0, 1);
        };
    }

    // Get power port 2 local position for each facing
    private MultiblockPos getPowerPort2Local(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(1, 1, 0);
            case SOUTH -> new MultiblockPos(1, 1, SHAPE.length() - 1);
            case EAST -> new MultiblockPos(1, 1, 1);
            case WEST -> new MultiblockPos(1, 1, 1);
        };
    }

    // Get third power port local position for testing (must be a casing position in perfect structure)
    private MultiblockPos getThirdPowerPortLocal(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(0, 1, 0);
            case SOUTH -> new MultiblockPos(0, 1, SHAPE.length() - 1);
            case EAST -> new MultiblockPos(0, 1, 1);
            case WEST -> new MultiblockPos(0, 1, 1);
        };
    }

    // Get a casing position for testing (not controller, not power port)
    private MultiblockPos getTestCasingPos(MultiblockDirection facing) {
        return switch (facing) {
            case NORTH -> new MultiblockPos(0, 0, 1);
            case SOUTH -> new MultiblockPos(0, 0, 1);
            case EAST -> new MultiblockPos(1, 0, 0);
            case WEST -> new MultiblockPos(1, 0, 1);
        };
    }

    // Helper record for test
    private record ControllerLocalPos(int x, int y, int z) {}
}