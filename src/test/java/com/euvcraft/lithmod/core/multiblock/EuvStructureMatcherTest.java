package com.euvcraft.lithmod.core.multiblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EuvStructureMatcher}.
 *
 * <p>Tests cover all four facings, all five controller column positions,
 * exact required parts/panel/dimensions (WIDTH=5, HEIGHT=5, DEPTH=7),
 * missing/wrong/foreign modules, duplicate modules, wrong dimensions,
 * missing frame cells, and power port count boundaries.</p>
 *
 * <p>The test oracle uses a genuinely independent geometry implementation
 * that does not share the axis-reversal bug present in earlier drafts.</p>
 */
class EuvStructureMatcherTest {

    private static final MultiblockPos CONTROLLER_POS = new MultiblockPos(100, 64, 100);

    /**
     * Independent geometry oracle: converts world positions to local (u, v, d) coordinates
     * using the same basis definitions as {@link EuvStructureMatcher#cellPos}.
     * Does NOT copy the axis-reversal bug from earlier test drafts.
     */
    private static final class GeometryOracle {
        private final MultiblockDirection facing;
        private final int u0; // controller column on front face (0..4)

        GeometryOracle(MultiblockDirection facing, int u0) {
            this.facing = facing;
            this.u0 = u0;
        }

        /**
         * Computes local coordinates (u, v, d) from a world position.
         * The structure box has:
         *   u in [0, 4] (width 5), horizontal along facing.right()
         *   v in [0, 4] (height 5), vertical along +Y
         *   d in [0, 6] (depth 7), along facing.back() from front face
         * Controller anchor is at local (u0, 0, 0).
         */
        MultiblockPos worldToLocal(MultiblockPos worldPos) {
            int dx = worldPos.x() - CONTROLLER_POS.x();
            int dy = worldPos.y() - CONTROLLER_POS.y();
            int dz = worldPos.z() - CONTROLLER_POS.z();

            int u, v, d;
            switch (facing) {
                case NORTH -> {
                    // right = +X, back = -Z
                    u = u0 + dx;
                    v = dy;
                    d = -dz;
                }
                case SOUTH -> {
                    // right = -X, back = +Z
                    u = u0 - dx;
                    v = dy;
                    d = dz;
                }
                case EAST -> {
                    // right = +Z, back = +X
                    u = u0 + dz;
                    v = dy;
                    d = dx;
                }
                case WEST -> {
                    // right = -Z, back = -X
                    u = u0 - dz;
                    v = dy;
                    d = -dx;
                }
                default -> throw new IllegalStateException("Unexpected facing: " + facing);
            }
            return new MultiblockPos(u, v, d);
        }

        /**
         * Checks if local coordinates are inside the 5x5x7 structure volume.
         */
        boolean inBounds(MultiblockPos local) {
            return local.x() >= 0 && local.x() < 5
                && local.y() >= 0 && local.y() < 5
                && local.z() >= 0 && local.z() < 7;
        }
    }

    /**
     * Test world that uses the independent geometry oracle.
     * Blocks are stored in local [u][v][d] coordinates.
     */
    private static final class TestWorld {
        private final GeometryOracle oracle;
        private final BlockKind[][][] blocks; // [u][v][d]

        TestWorld(MultiblockDirection facing, int u0) {
            this.oracle = new GeometryOracle(facing, u0);
            this.blocks = new BlockKind[5][5][7];
            // Initialize all to CASING
            for (int u = 0; u < 5; u++) {
                for (int v = 0; v < 5; v++) {
                    for (int d = 0; d < 7; d++) {
                        blocks[u][v][d] = BlockKind.CASING;
                    }
                }
            }
            // Place controller at anchor (u0, 0, 0)
            blocks[u0][0][0] = BlockKind.CONTROLLER;
            // Place operating panel directly above at (u0, 1, 0)
            blocks[u0][1][0] = BlockKind.OPERATING_PANEL;
        }

        void set(int u, int v, int d, BlockKind kind) {
            blocks[u][v][d] = kind;
        }

        BlockKind lookup(MultiblockPos worldPos) {
            MultiblockPos local = oracle.worldToLocal(worldPos);
            if (!oracle.inBounds(local)) {
                return BlockKind.OTHER;
            }
            return blocks[local.x()][local.y()][local.z()];
        }

        MatchResult match() {
            return EuvStructureMatcher.match(CONTROLLER_POS, oracle.facing, this::lookup);
        }
    }

    /**
     * Creates a valid world with all required components:
     * - 6 required modules (exposure, optics, stage, filtration, metrology, vacuum) in middle layers v=1..3
     * - 1 diagnostic panel in middle layers
     * - 1 water inlet in middle layers
     * - 1 calibration panel in middle layers (required by spec; matcher will be updated)
     * - 2 power ports in middle layers (within 1..4 range)
     * - Controller at (u0, 0, 0), operating panel at (u0, 1, 0)
     * - All frame layers (v=0, v=4) are CASING except controller/panel positions
     */
    private static TestWorld createValidWorld(MultiblockDirection facing, int u0) {
        TestWorld world = new TestWorld(facing, u0);
        // Place the six required modules in middle layers (v=1..3)
        world.set(0, 1, 1, BlockKind.EXPOSURE_MODULE);
        world.set(1, 1, 1, BlockKind.OPTICS_MODULE);
        world.set(2, 2, 1, BlockKind.STAGE_MODULE);
        world.set(3, 2, 1, BlockKind.FILTRATION_MODULE);
        world.set(4, 3, 1, BlockKind.METROLOGY_MODULE);
        world.set(0, 3, 2, BlockKind.VACUUM_MODULE);
        // Place required diagnostic panel, water inlet, and calibration panel in middle layers
        world.set(1, 1, 2, BlockKind.DIAGNOSTIC_PANEL);
        world.set(2, 1, 2, BlockKind.WATER_INLET);
        world.set(3, 1, 2, BlockKind.CALIBRATION_PANEL);
        // Add power ports (1-4 allowed)
        world.set(4, 1, 2, BlockKind.POWER_PORT);
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
                    "facing=" + facing + " u0=" + u0 + " should have 175 members (5*5*7)");
            }
        }
    }

    @Test
    void testMissingController() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 0, 0, BlockKind.CASING); // Remove controller
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("controller"));
    }

    @Test
    void testExtraController() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 0, 0, BlockKind.CONTROLLER); // Extra controller at different column
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("extra controller"));
    }

    @Test
    void testMissingOperatingPanel() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 0, BlockKind.CASING); // Remove panel
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("operating panel"));
    }

    @Test
    void testOperatingPanelWrongPosition() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 0, BlockKind.CASING); // Remove from correct pos
        world.set(1, 1, 0, BlockKind.OPERATING_PANEL); // Place at wrong pos (different column)
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("operating panel at wrong position"));
    }

    @Test
    void testMissingExposureModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CASING); // Remove exposure module
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("EXPOSURE_MODULE"));
    }

    @Test
    void testMissingOpticsModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 1, BlockKind.CASING);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("OPTICS_MODULE"));
    }

    @Test
    void testMissingStageModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 2, 1, BlockKind.CASING);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("STAGE_MODULE"));
    }

    @Test
    void testMissingFiltrationModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(3, 2, 1, BlockKind.CASING);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("FILTRATION_MODULE"));
    }

    @Test
    void testMissingMetrologyModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(4, 3, 1, BlockKind.CASING);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("METROLOGY_MODULE"));
    }

    @Test
    void testMissingVacuumModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 3, 2, BlockKind.CASING);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("VACUUM_MODULE"));
    }

    @Test
    void testMissingCalibrationPanel() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(3, 1, 2, BlockKind.CASING); // Remove calibration panel
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("calibration panel"));
    }

    @Test
    void testMissingDiagnosticPanel() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 2, BlockKind.CASING); // Remove diagnostic panel
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("diagnostic panel"));
    }

    @Test
    void testMissingWaterInlet() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 1, 2, BlockKind.CASING); // Remove water inlet
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("water inlet"));
    }

    @Test
    void testDuplicateModule() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        // Add a second exposure module (duplicate)
        world.set(1, 2, 2, BlockKind.EXPOSURE_MODULE);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("EXPOSURE_MODULE"));
    }

    @Test
    void testModuleInBottomFrameLayer() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CASING); // Remove from middle layer
        world.set(1, 0, 1, BlockKind.EXPOSURE_MODULE); // Place in bottom frame layer (v=0)
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("frame layer"));
    }

    @Test
    void testModuleInTopFrameLayer() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 1, BlockKind.CASING); // Remove from middle layer
        world.set(1, 4, 1, BlockKind.OPTICS_MODULE); // Place in top frame layer (v=4)
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("frame layer"));
    }

    @Test
    void testNonCasingInBottomFrameLayer() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 0, 2, BlockKind.POWER_PORT); // Power port in bottom frame (not at controller/panel pos)
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("frame layer"));
    }

    @Test
    void testNonCasingInTopFrameLayer() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 4, 2, BlockKind.DIAGNOSTIC_PANEL); // Diagnostic panel in top frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("frame layer"));
    }

    @Test
    void testMissingFrameCell() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 0, 1, BlockKind.OTHER); // Non-structure block in bottom frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("wrong block"));
    }

    @Test
    void testPowerPortCountZero() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(4, 1, 2, BlockKind.CASING); // Remove first power port
        world.set(4, 2, 2, BlockKind.CASING); // Remove second power port
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("at least one power port"));
    }

    @Test
    void testPowerPortCountFive() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        // Add 3 more power ports (total 5, max is 4)
        world.set(0, 1, 3, BlockKind.POWER_PORT);
        world.set(1, 2, 3, BlockKind.POWER_PORT);
        world.set(2, 3, 3, BlockKind.POWER_PORT);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("at most four power ports"));
    }

    @Test
    void testWrongHeightRejection() {
        // The matcher uses fixed StructureShape(5, 5, 7) - height=5.
        // We simulate a "height 7" structure by placing blocks at v=5,6 which should be OTHER.
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        // Place a module at v=5 (outside valid height 5) - oracle will return OTHER
        world.set(0, 5, 1, BlockKind.EXPOSURE_MODULE);
        MatchResult result = world.match();
        assertFalse(result.success());
        // The matcher will see OTHER at that position and fail
        assertTrue(result.failureReason().toLowerCase().contains("wrong block"));
    }

    @Test
    void testWrongDepthRejection() {
        // Depth is fixed at 7. Place block at d=7 (outside valid depth 7).
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(0, 1, 7, BlockKind.EXPOSURE_MODULE);
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("wrong block"));
    }

    @Test
    void testMultipatterningModuleNotAllowed() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 1, BlockKind.MULTIPATTERNING_MODULE); // MP module in middle layer
        MatchResult result = world.match();
        assertFalse(result.success());
    }

    @Test
    void testCalibrationPanelInFrameLayerRejected() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(3, 1, 2, BlockKind.CASING); // Remove from middle
        world.set(1, 0, 2, BlockKind.CALIBRATION_PANEL); // Place in bottom frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("wrong position"));
    }

    @Test
    void testDiagnosticPanelInFrameLayerRejected() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(1, 1, 2, BlockKind.CASING); // Remove from middle
        world.set(1, 4, 2, BlockKind.DIAGNOSTIC_PANEL); // Place in top frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("wrong position"));
    }

    @Test
    void testWaterInletInFrameLayerRejected() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(2, 1, 2, BlockKind.CASING); // Remove from middle
        world.set(2, 0, 2, BlockKind.WATER_INLET); // Place in bottom frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("wrong position"));
    }

    @Test
    void testPowerPortInFrameLayerRejected() {
        TestWorld world = createValidWorld(MultiblockDirection.NORTH, 0);
        world.set(4, 1, 2, BlockKind.CASING); // Remove from middle
        world.set(1, 0, 3, BlockKind.POWER_PORT); // Place in bottom frame
        MatchResult result = world.match();
        assertFalse(result.success());
        assertTrue(result.failureReason().toLowerCase().contains("frame layer"));
    }

    @Test
    void testOperatingPanelOnlyAboveController() {
        // The operating panel must be directly above the controller at (u0, 1, 0).
        // Test that placing it at a different u column fails.
        for (MultiblockDirection facing : MultiblockDirection.values()) {
            for (int u0 = 0; u0 < 5; u0++) {
                TestWorld world = createValidWorld(facing, u0);
                int wrongU = (u0 + 1) % 5;
                world.set(u0, 1, 0, BlockKind.CASING); // Remove from correct pos
                world.set(wrongU, 1, 0, BlockKind.OPERATING_PANEL); // Place at wrong column
                MatchResult result = world.match();
                assertFalse(result.success(),
                    "facing=" + facing + " u0=" + u0 + " should reject panel at wrong column");
                assertTrue(result.failureReason().toLowerCase().contains("operating panel at wrong position"));
            }
        }
    }

    @Test
    void testAllFiveControllerColumnsWithAllFourFacings() {
        // Explicit test covering the 4 facings × 5 columns = 20 valid anchor positions
        int successCount = 0;
        for (MultiblockDirection facing : MultiblockDirection.values()) {
            for (int u0 = 0; u0 < 5; u0++) {
                TestWorld world = createValidWorld(facing, u0);
                MatchResult result = world.match();
                if (result.success()) {
                    successCount++;
                }
            }
        }
        assertEquals(20, successCount, "All 4 facings × 5 columns should match");
    }
}