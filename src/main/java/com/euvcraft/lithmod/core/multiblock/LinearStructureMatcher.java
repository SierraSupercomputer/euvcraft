package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a linear multiblock structure against the world.
 *
 * <p>The structure is a fully filled rectangular solid volume with exactly one lower-front
 * controller in any front column and exactly one {@link BlockKind#OPERATING_PANEL}
 * immediately above it. There must be 1 or 2 {@link BlockKind#POWER_PORT} blocks anywhere
 * else in the structure. If {@code waterRequired} is true, exactly one
 * {@link BlockKind#WATER_INLET} is required; otherwise zero are allowed. All remaining cells
 * must be {@link BlockKind#CASING}. No extra controller, extra operating panel, or other
 * block kinds are allowed.</p>
 *
 * <p>Supported shapes are exactly {@code 3x3x5} (Etch), {@code 3x2x5} (Coater), and
 * {@code 2x2x3} (Wet Cleaner). The match tries each valid front-face column {@code u0}
 * deterministically (0..width-1) and the first full match wins.</p>
 *
 * <p>Member positions are returned in stable canonical iteration order:
 * {@code u * (height * length) + v * length + d} (u outer, v middle, d inner).</p>
 *
 * <p>No Minecraft types are involved.</p>
 */
public final class LinearStructureMatcher {

    /** Supported shape: 3 wide, 3 tall, 5 deep (Etch). */
    public static final StructureShape ETCH_3x3x5 = new StructureShape(3, 3, 5);

    /** Supported shape: 3 wide, 2 tall, 5 deep (Coater). */
    public static final StructureShape COATER_3x2x5 = new StructureShape(3, 2, 5);

    /** Supported shape: 2 wide, 2 tall, 3 deep (Wet Cleaner). */
    public static final StructureShape WET_CLEANER_2x2x3 = new StructureShape(2, 2, 3);

    private LinearStructureMatcher() {}

    /**
     * Attempts to match a linear multiblock structure at the given controller position and facing.
     *
     * <p>The shape must be one of {@link #ETCH_3x3x5}, {@link #COATER_3x2x5}, or
     * {@link #WET_CLEANER_2x2x3}. The controller position IS the anchor (front face, lower row).
     * The match tries each column {@code u0} from 0 to {@code width-1} (with {@code v0 = 0}
     * fixed) in order; the first full match wins.</p>
     *
     * @param controller the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param shape the shape of the structure; must be one of the three supported linear shapes
     * @param waterRequired whether a water inlet block is required
     * @param lookup reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the member positions in canonical u/v/d order on success
     * @throws IllegalArgumentException if the shape is not one of the supported linear shapes
     */
    public static MatchResult match(MultiblockPos controller, MultiblockDirection facing,
            StructureShape shape, boolean waterRequired, Function<MultiblockPos, BlockKind> lookup) {

        java.util.Objects.requireNonNull(controller);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(shape);
        java.util.Objects.requireNonNull(lookup);
        if (!shape.equals(ETCH_3x3x5) && !shape.equals(COATER_3x2x5) && !shape.equals(WET_CLEANER_2x2x3)) {
            throw new IllegalArgumentException("unsupported linear shape: " + shape.width() + "x"
                    + shape.height() + "x" + shape.length() + "; only 3x3x5, 3x2x5, and 2x2x3 are allowed");
        }

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all front-face columns (u0)
        for (int u0 = 0; u0 < shape.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controller, facing, shape, waterRequired, lookup, u0, v0);
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
    private static MatchResult matchAtAnchor(MultiblockPos controller, MultiblockDirection facing,
            StructureShape shape, boolean waterRequired, Function<MultiblockPos, BlockKind> lookup, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(shape.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int powerPortCount = 0;
        int waterInletCount = 0;

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < shape.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d
        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    MultiblockPos worldPos = cellPos(controller, facing, shape, u0, v0, u, v, d);
                    BlockKind kind = lookup.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
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
                    } else if (kind == BlockKind.WATER_INLET) {
                        waterInletCount++;
                    } else if (kind != BlockKind.CASING) {
                        return MatchResult.failure("unexpected block " + kind + " at " + worldPos);
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
        if (waterRequired) {
            if (waterInletCount != 1) {
                return MatchResult.failure("expected exactly one water inlet, found " + waterInletCount);
            }
        } else {
            if (waterInletCount != 0) {
                return MatchResult.failure("water inlet not allowed, found " + waterInletCount);
            }
        }

        return MatchResult.success(memberPositions);
    }

    /**
     * Returns world cell (u, v, d) of the box whose controller sits at front face offset (u0, v0),
     * that is controller + (u - u0)*right + (v - v0)*up + d*back.
     * The anchor is at d=0 (front face).
     */
    private static MultiblockPos cellPos(MultiblockPos controller, MultiblockDirection facing,
            StructureShape shape, int u0, int v0, int u, int v, int d) {
        return controller
                .add(facing.right().multiply(u - u0))
                .add(MultiblockDirection.up().multiply(v - v0))
                .add(facing.back().multiply(d));
    }
}