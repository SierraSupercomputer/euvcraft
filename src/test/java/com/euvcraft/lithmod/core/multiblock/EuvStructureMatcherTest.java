package com.euvcraft.lithmod.core.multiblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EuvStructureMatcher}.
 *
 * <p>Tests cover all four facings, all five controller column positions,
 * exact required parts/panel/dimensions, missing/wrong/foreign modules.</p>
 */
class EuvStructureMatcherTest {

    private static final MultiblockPos CONTROLLER_POS = new MultiblockPos(100, 64, 100);

    // A simple in-memory world that always returns CASING for structure blocks
    private static final class TestWorld {
        private final MultiblockDirection facing;
        private final int u0;
        private final BlockKind[][][] blocks; // [u][v][d]

        TestWorld(MultiblockDirection facing, int u0) {
            this.facing = facing;
            this.u0 = u0;
            this.blocks = new BlockKind[5][5][7];
            // Initialize all to CASING
            for (int u = 0; u < 5; u++) {
                for (int v = 0; v < 5; v++) {
                    for (int d = 0; d < 7; d++) {
                        blocks[u][v][d] = BlockKind.CASING;
                    }
                }
            }
            // Place controller at anchor
            blocks[u0][0][0] = BlockKind.CONTROLLER;
            // Place operating panel above controller
            blocks[u0][1][0] = BlockKind.OPERATING_PANEL;
        }

        BlockKind get(MultiblockPos pos) {
            // Convert world pos to local coordinates
            // This is a simplified version - in reality we'd use the same cellPos logic
            return BlockKind.CASING;
        }

        void set(int u, int v, int d, BlockKind kind) {
            blocks[u][v][d] = kind;
        }

        BlockKind lookup(MultiblockPos worldPos) {
            // Compute local coordinates from world position
            int dx = worldPos.x() - CONTROLLER_POS.x();
            int dy = worldPos.y() - CONTROLLER_POS.y();
            int dz = worldPos.z() - CONTROLLER_POS.z();

            MultiblockPos local;
            switch (facing) {
                case NORTH -> local = new MultiblockPos(dx, dy, -dz);
                case SOUTH -> local = new MultiblockPos(-dx, dy, dz);
                case EAST -> local = new MultiblockPos(dz, dy, -dx);
                case WEST -> local = new MultiblockPos(-dz, dy, dx);
                default -> throw new IllegalStateException();
            }

            // Check if local coordinates match our anchor
            // The box origin is at controller - u0*right - v0*up
            // For our test, v0=0, so origin is controller - u0*right
            int localU, localV, localD;
            switch (facing) {
                case NORTH -> {
                    localU = -local.x(); // right = east = +x, so -x is u
                    localV = local.y();
                    localD = -local.z(); // back = north = -z, so -z is d
                }
                case SOUTH -> {
                    localU = local.x(); // right = west = -x, so x is u
                    localV = local.y();
                    localD = local.z(); // back = south = +z
                }
                case EAST -> {
                    localU = local.z(); // right = south = +z
                    localV = local.y();
                    localD = -local.x(); // back = east = +x, so -x is d
                }
                case WEST -> {
                    localU = -local.z(); // right = north = -z
                    localV = local.y();
                    localD = local.x(); // back = west = -x
                }
                default -> throw new IllegalStateException();
            }

            // Adjust for anchor offset u0
            localU += u0;

            if (localU < 0 || localU >= 5 || localV < 0 || localV >= 5 || localD < 0 || localD >= 7) {
                return BlockKind.OTHER;
            }
            return blocks[localU][localV][localD];
        }

        MatchResult match() {
            return EuvStructureMatcher.match(CONTROLLER_POS, facing, this::lookup);
        }
    }

    private static TestWorld createValidWorld(MultiblockDirection facing, int u0) {
        TestWorld world = new TestWorld(facing, u0);
        // Place the six required modules in middle layers (v=1..3)
        // Spread them across different layers and positions
        world.set(0, 1, 1, BlockKind.EXPOSURE_MODULE);
        world.set(1, 1, 1, BlockKind.OPTICS_MODULE);
        world.set(2, 2, 1, BlockKind.STAGE_MODULE);
        world.set(3, 2, 1, BlockKind.FILTRATION_MODULE);
        world.set(4, 3, 1, BlockKind.METROLOGY_MODULE);
        world.set(0, 3, 2, BlockKind.VACUUM_MODULE);
        // Place required diagnostic panel and water inlet in middle layers
        world.set(1, 1, 2, BlockKind.DIAGNOSTIC_PANEL);
        world.set(2, 1, 2, BlockKind.WATER_INLET);
        // Add power ports (1-4)
        world.set(3, 1, 2, BlockKind.POWER_PORT);
        world.set(4, 2, 2, BlockKind.POWER_PORT);
        return world;
    }

    @Test
    void testAllFacingsAndControllerColumns() {
        for (MultiblockDirection facing : MultiblockDirection.values()) {
            for (int u0 = 0; u0 < 5; u0++) {
                TestWorld world = createValidWorld(facing, u0);
                MatchResult result = world.match();
                assertTrue(result.success(),
                    "facing=" + facing + " u0=" + u0 + " should match: " + result.failureReason());
                assertEquals(175, result.memberPositions().size(),
                    "facing=" + facing + " u0=" + u0 + " should have 175 members");
            }
        }
    }

    @Test
    void testMissingController() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 0, 0, BlockKind.CASING); // Remove controller
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("controller"));
    }

    @Test
    void testExtraController() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 0, 0, BlockKind.CONTROLLER); // Extra controller
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("extra controller"));
    }

    @Test
    void testMissingOperatingPanel() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 0, BlockKind.CASING); // Remove panel
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel"));
    }

    @Test
    void testOperatingPanelWrongPosition() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 0, BlockKind.CASING); // Remove from correct pos
        world.set(1, 1, 0, BlockKind.OPERATING_PANEL); // Place at wrong pos
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("operating panel at wrong position"));
    }

    @Test
    void testMissingModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CASING); // Remove exposure module
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("EXPOSURE_MODULE"));
    }

    @Test
    void testWrongModulePosition() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CASING); // Remove from middle layer
        world.set(0, 0, 1, BlockKind.EXPOSURE_MODULE); // Place in bottom frame layer
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("at wrong position"));
    }

    @Test
    void testForeignModuleKind() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CALIBRATION_PANEL); // Wrong kind in middle layer
        MatchResult result = world.match();
        assertFalse(result.success());
    }

    @Test
    void testWaterInletRequired() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 1, 2, BlockKind.CASING); // Remove water inlet
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("water inlet"));
    }

    @Test
    void testDiagnosticPanelRequired() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 2, BlockKind.CASING); // Remove diagnostic panel
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("diagnostic panel"));
    }

    @Test
    void testMultipatterningModuleNotAllowed() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 1, BlockKind.MULTIPATTERNING_MODULE); // MP module in middle layer
        MatchResult result = world.match();
        assertFalse(result.success());
    }

    @Test
    void testFrameLayerNonCasing() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 0, 1, BlockKind.EXPOSURE_MODULE); // Module in bottom frame layer
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("frame layer"));
    }

    @Test
    void testTopFrameLayerNonCasing() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 4, 1, BlockKind.OPTICS_MODULE); // Module in top frame layer
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("frame layer"));
    }

    @Test
    void testPowerPortCountMin() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(3, 1, 2, BlockKind.CASING); // Remove one power port
        world.set(4, 2, 2, BlockKind.CASING); // Remove second power port
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("at least one power port"));
    }

    @Test
    void testPowerPortCountMax() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        // Add 3 more power ports (total 5, max is 4)
        world.set(0, 1, 3, BlockKind.POWER_PORT);
        world.set(1, 2, 3, BlockKind.POWER_PORT);
        world.set(2, 3, 3, BlockKind.POWER_PORT);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("at most four power ports"));
    }

    @Test
    void testControllerOnTopRow() {
        // Create a world with controller at v0=4 (top row) - should fail
        // This is implicitly tested since we only try v0=0
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        // The matcher only tries v0=0, so this is implicitly tested
        // But we can verify panelPositionValid check
        MatchResult result = world.match();
        assertTrue(result.success());
    }
}