package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a panel multiblock structure against the world.
 *
 * <p>The structure is a fully filled rectangular solid volume with exactly one lower-front
 * controller in any front column and exactly one {@link BlockKind#OPERATING_PANEL}
 * immediately above it. There must be 1 or 2 {@link BlockKind#POWER_PORT} blocks anywhere
 * else in the structure. All remaining cells must be {@link BlockKind#CASING}. No
 * {@link BlockKind#WATER_INLET}, {@link BlockKind#OTHER}, extra controller or extra
 * operating panel is allowed. The controller is always on the bottom row (v=0) of the
 * front face (d=0); the panel is at (u0, 1, 0).</p>
 *
 * <p>Supported shapes are exactly {@code 2x2x3} (Process Cell) and {@code 3x3x5}
 * (G2 deposition/etch). The match tries each valid front-face column {@code u0}
 * deterministically (0..width-1) and the first full match wins.</p>
 *
 * <p>Member positions are returned in stable canonical iteration order:
 * {@code u * (height * length) + v * length + d} (u outer, v middle, d inner).</p>
 *
 * <p>No Minecraft types are involved.</p>
 */
public final class PanelStructureMatcher {

    /** Supported shape: 2 wide, 2 tall, 3 deep (Process Cell). */
    public static final StructureShape PANEL_2x2x3 = new StructureShape(2, 2, 3);

    /** Supported shape: 3 wide, 3 tall, 5 deep (G2 deposition/etch). */
    public static final StructureShape PANEL_3x3x5 = new StructureShape(3, 3, 5);

    private PanelStructureMatcher() {}

    /**
     * Attempts to match a panel multiblock structure at the given controller position and facing.
     *
     * <p>The shape must be either {@link #PANEL_2x2x3} or {@link #PANEL_3x3x5}. The controller
     * position IS the anchor (front face, lower row). The match tries each column {@code u0}
     * from 0 to {@code width-1} (with {@code v0 = 0} fixed) in order; the first full match wins.</p>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param shape the shape of the structure; must be PANEL_2x2x3 or PANEL_3x3x5
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the member positions in canonical u/v/d order on success
     * @throws IllegalArgumentException if the shape is not one of the supported panel shapes
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(shape);
        java.util.Objects.requireNonNull(blockKindAt);
        if (!shape.equals(PANEL_2x2x3) && !shape.equals(PANEL_3x3x5)) {
            throw new IllegalArgumentException("unsupported panel shape: " + shape.width() + "x"
                    + shape.height() + "x" + shape.length() + "; only 2x2x3 and 3x3x5 are allowed");
        }

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all front-face columns (u0)
        for (int u0 = 0; u0 < shape.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controllerPos, facing, shape, blockKindAt, u0, v0);
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
            StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(shape.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int powerPortCount = 0;

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        // For any valid panel shape, v0=0 and height >= 2, so v0+1 < height is always true.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < shape.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d
        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, shape, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }
                    if (kind == BlockKind.WATER_INLET) {
                        return MatchResult.failure("water inlet not allowed in panel structure at " + worldPos);
                    }

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);

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
                    } else if (kind == BlockKind.POWER_PORT) {
                        powerPortCount++;
                    } else if (kind != BlockKind.CASING) {
                        return MatchResult.failure("missing block at " + worldPos);
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
            StructureShape shape, int u0, int v0, int u, int v, int d) {
        return controllerPos
                .add(facing.right().multiply(u - u0))
                .add(MultiblockDirection.up().multiply(v - v0))
                .add(facing.back().multiply(d));
    }
}
