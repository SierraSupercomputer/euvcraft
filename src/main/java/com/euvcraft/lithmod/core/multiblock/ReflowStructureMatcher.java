package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Matches the T6 reflow oven multiblock against the world.
 *
 * <p>The structure is a fixed 3 wide x 3 tall x 2 deep box (18 blocks). The controller sits
 * on the front face ({@code d = 0}) at the lower row ({@code v = 0}), in any column
 * ({@code u = 0, 1, or 2}). There must be exactly one {@link BlockKind#OPERATING_PANEL}
 * directly above the controller at {@code (u0, 1, 0)} and exactly one
 * {@link BlockKind#CALIBRATION_PANEL} in the middle layer ({@code v = 1}) at any other
 * position. There must be 1 or 2 {@link BlockKind#POWER_PORT} blocks anywhere in the
 * remaining cells. All other cells must be {@link BlockKind#CASING}.
 *
 * <p>No module, diagnostic panel, water inlet or other block kind is accepted. No extra
 * controller, operating panel or calibration panel is allowed. Missing or extra parts cause
 * a match failure.
 *
 * <p>The match searches the three valid anchor columns deterministically: (u0=0, v0=0),
 * (u0=1, v0=0), (u0=2, v0=0), in that order. The first full match wins.
 *
 * <p>Member positions are returned in the canonical iteration order u*6 + v*2 + d
 * (u outer, v middle, d inner), matching the {@link StructureShape} volume order (18
 * members).
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.
 */
public final class ReflowStructureMatcher {

    /** Fixed shape: 3 wide, 3 tall, 2 deep. */
    public static final StructureShape REFLOW_SHAPE = new StructureShape(3, 3, 2);

    /** The middle layer index (v = 1), where the calibration panel must sit. */
    private static final int MIDDLE_LAYER = 1;

    private ReflowStructureMatcher() {}

    /**
     * Attempts to match a reflow oven structure at the given controller position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match tries each
     * column {@code u0} from 0 to 2 (with {@code v0 = 0} fixed) in order; the first full match
     * wins.
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 18 member positions in canonical u/v/d order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        Objects.requireNonNull(controllerPos, "controllerPos");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(blockKindAt, "blockKindAt");

        String firstFailure = null;
        // Only the lower row (v0 = 0) is a valid controller row; try all three columns.
        for (int u0 = 0; u0 < REFLOW_SHAPE.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controllerPos, facing, blockKindAt, u0, v0);
            if (result.success()) {
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

        List<MultiblockPos> memberPositions = new ArrayList<>(REFLOW_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int calibrationPanelCount = 0;
        int powerPortCount = 0;

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < REFLOW_SHAPE.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*6 + v*2 + d
        for (int u = 0; u < REFLOW_SHAPE.width(); u++) {
            for (int v = 0; v < REFLOW_SHAPE.height(); v++) {
                for (int d = 0; d < REFLOW_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);
                    boolean isMiddleLayer = (v == MIDDLE_LAYER);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }
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
                            return MatchResult.failure("calibration panel at wrong position " + worldPos
                                    + " (must be in the middle layer, not at the operating panel position)");
                        }
                    } else if (kind == BlockKind.POWER_PORT) {
                        powerPortCount++;
                    } else if (kind != BlockKind.CASING) {
                        // WATER_INLET, FLUID_OUTLET, DIAGNOSTIC_PANEL, every module kind and any
                        // other foreign block is refused: the reflow frame is casing only.
                        return MatchResult.failure("wrong block at " + worldPos + ": " + kind
                                + " is not allowed in a reflow oven");
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
            return MatchResult.failure("expected exactly one operating panel above controller, found "
                    + operatingPanelCount);
        }
        if (calibrationPanelCount != 1) {
            return MatchResult.failure("expected exactly one calibration panel in middle layer, found "
                    + calibrationPanelCount);
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
     * Returns world cell (u, v, d) of the box whose controller sits at front face offset
     * (u0, v0), that is controller + (u - u0)*right + (v - v0)*up + d*back.
     * The anchor is at d=0 (front face).
     */
    private static MultiblockPos cellPos(MultiblockPos controllerPos, MultiblockDirection facing,
            int u0, int v0, int u, int v, int d) {
        return controllerPos
                .add(facing.right().multiply(u - u0))
                .add(MultiblockDirection.up().multiply(v - v0))
                .add(facing.back().multiply(d));
    }
}
