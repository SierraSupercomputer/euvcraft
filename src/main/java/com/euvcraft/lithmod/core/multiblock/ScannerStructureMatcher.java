package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a scanner multiblock structure against the world.
 *
 * <p>The structure is a fixed 3 wide x 3 tall x 5 deep box (45 blocks). The controller sits on the
 * front face (d = 0) at the bottom row (v = 0), in any column (u = 0, 1, or 2). There must be
 * exactly one {@link BlockKind#OPERATING_PANEL} directly above the controller at (u0, 1, 0).
 * The middle layer (v = 1) must contain exactly one {@link BlockKind#CALIBRATION_PANEL} and
 * exactly one of each of the five module kinds ({@link BlockKind#EXPOSURE_MODULE},
 * {@link BlockKind#OPTICS_MODULE}, {@link BlockKind#STAGE_MODULE},
 * {@link BlockKind#FILTRATION_MODULE}, {@link BlockKind#METROLOGY_MODULE}), in any positions.
 * There must be 1 or 2 {@link BlockKind#POWER_PORT} blocks anywhere in the remaining cells.
 * All other cells must be {@link BlockKind#CASING}.
 *
 * <p>No module blocks are allowed on the top layer (v = 2) or bottom layer (v = 0) except the
 * controller and operating panel at their required positions. No extra operating panel or
 * calibration panel is allowed. Water inlets and other block kinds cause match failure.
 *
 * <p>The match searches the three valid anchor columns deterministically: (u0=0, v0=0),
 * (u0=1, v0=0), (u0=2, v0=0), in that order. The first full match wins.
 *
 * <p>Member positions are returned in the canonical iteration order u*15 + v*5 + d
 * (u outer, v middle, d inner), matching the StructureShape volume order (45 members).
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.
 */
public final class ScannerStructureMatcher {

    /** Fixed shape: 3 wide, 3 tall, 5 deep. */
    public static final StructureShape SCANNER_SHAPE = new StructureShape(3, 3, 5);

    /** The middle layer index (v = 1). */
    private static final int MIDDLE_LAYER = 1;

    /** The five module kinds that must each appear exactly once in the middle layer. */
    private static final BlockKind[] MODULE_KINDS = {
        BlockKind.EXPOSURE_MODULE,
        BlockKind.OPTICS_MODULE,
        BlockKind.STAGE_MODULE,
        BlockKind.FILTRATION_MODULE,
        BlockKind.METROLOGY_MODULE
    };

    private ScannerStructureMatcher() {}

    /**
     * Attempts to match a scanner multiblock structure at the given controller position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match tries each column
     * {@code u0} from 0 to 2 (with {@code v0 = 0} fixed) in order; the first full match wins.
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 45 member positions in canonical u/v/d order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(blockKindAt);

        final var original = blockKindAt;
        blockKindAt = pos -> {var kind=original.apply(pos);return kind==BlockKind.DIAGNOSTIC_PANEL||kind==BlockKind.WATER_INLET?BlockKind.CASING:kind;};
        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all three front-face columns (u0=0,1,2)
        for (int u0 = 0; u0 < SCANNER_SHAPE.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controllerPos, facing, blockKindAt, u0, v0);
            if (result.success()) {
                int diagnostic=0,inlet=0;
                for(var pos:result.memberPositions()){
                    var kind=original.apply(pos);
                    if(kind==BlockKind.DIAGNOSTIC_PANEL){diagnostic++;if(pos.y()!=controllerPos.y()+1)return MatchResult.failure("Diagnostic panel must be in middle layer");}
                    if(kind==BlockKind.WATER_INLET)inlet++;
                }
                if(diagnostic>1||inlet>1||diagnostic!=inlet)return MatchResult.failure("water inlet not allowed without exactly one diagnostic panel, and vice versa");
                return result;
            }
            if (firstFailure == null) {
                firstFailure = result.failureReason();
            }
        }
        return MatchResult.failure(firstFailure != null ? firstFailure : "no valid anchor column found");
    }

    /**
     * Tries to match the structure with the controller at front-face offset (u0, v0=0).
     */
    private static MatchResult matchAtAnchor(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(SCANNER_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int calibrationPanelCount = 0;
        int powerPortCount = 0;
        int[] moduleCounts = new int[MODULE_KINDS.length]; // one per module kind

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        // For scanner shape (height=3), v0=0, so panel is at v=1, which is the middle layer.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < SCANNER_SHAPE.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d = u*15 + v*5 + d
        for (int u = 0; u < SCANNER_SHAPE.width(); u++) {
            for (int v = 0; v < SCANNER_SHAPE.height(); v++) {
                for (int d = 0; d < SCANNER_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }
                    if (kind == BlockKind.WATER_INLET) {
                        return MatchResult.failure("water inlet not allowed in scanner structure at " + worldPos);
                    }

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);
                    boolean isMiddleLayer = (v == MIDDLE_LAYER);

                    if (kind == BlockKind.CONTROLLER) {
                        if (isControllerPos) {
                            controllerCount++;
                        } else {
                            return MatchResult.failure("extra controller at " + worldPos);
                        }
                    } else if (kind == BlockKind.OPERATING_PANEL) {
                        if (isPanelPos) {
                            operatingPanelCount++;
                        } else {
                            return MatchResult.failure("operating panel at wrong position " + worldPos);
                        }
                    } else if (kind == BlockKind.CALIBRATION_PANEL) {
                        if (isMiddleLayer && !isPanelPos) {
                            calibrationPanelCount++;
                        } else {
                            return MatchResult.failure("calibration panel at wrong position " + worldPos + " (must be in middle layer, not at panel position)");
                        }
                    } else if (kind == BlockKind.POWER_PORT) {
                        powerPortCount++;
                    } else {
                        // Check if it's one of the five module kinds
                        int moduleIndex = -1;
                        for (int i = 0; i < MODULE_KINDS.length; i++) {
                            if (kind == MODULE_KINDS[i]) {
                                moduleIndex = i;
                                break;
                            }
                        }
                        if (moduleIndex >= 0) {
                            if (isMiddleLayer && !isPanelPos) {
                                moduleCounts[moduleIndex]++;
                            } else {
                                return MatchResult.failure(kind + " at wrong position " + worldPos + " (modules must be in middle layer, not at panel position)");
                            }
                        } else if (kind != BlockKind.CASING) {
                            return MatchResult.failure("missing block at " + worldPos);
                        }
                    }
                    memberPositions.add(worldPos);
                }
            }
        }

        if (controllerCount != 1) {
            return MatchResult.failure("expected exactly one controller at anchor, found " + controllerCount);
        }
        if (!panelPositionValid) {
            return MatchResult.failure("controller on top row leaves no room for operating panel above");
        }
        if (operatingPanelCount != 1) {
            return MatchResult.failure("expected exactly one operating panel above controller, found " + operatingPanelCount);
        }
        if (calibrationPanelCount != 1) {
            return MatchResult.failure("expected exactly one calibration panel in middle layer, found " + calibrationPanelCount);
        }
        for (int i = 0; i < MODULE_KINDS.length; i++) {
            if (moduleCounts[i] != 1) {
                return MatchResult.failure("expected exactly one " + MODULE_KINDS[i] + " in middle layer, found " + moduleCounts[i]);
            }
        }
        if (powerPortCount < 1) {
            return MatchResult.failure("expected at least one power port, found " + powerPortCount);
        }
        if (powerPortCount > 2) {
            return MatchResult.failure("expected at most two power ports, found " + powerPortCount);
        }

        return MatchResult.success(memberPositions);
    }

    /**
     * Returns world cell (u, v, d) of the box whose controller sits at front face offset (u0, v0),
     * that is controller + (u - u0)*right + (v - v0)*up + d*back.
     * The anchor is at d=0 (front face), not d=1 (no mirror bug).
     */
    private static MultiblockPos cellPos(MultiblockPos controllerPos, MultiblockDirection facing,
            int u0, int v0, int u, int v, int d) {
        return controllerPos
                .add(facing.right().multiply(u - u0))
                .add(MultiblockDirection.up().multiply(v - v0))
                .add(facing.back().multiply(d));
    }
}

