package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches an EUV light source multiblock structure against the world.
 *
 * <p>The light source structure is a fixed 3 wide x 3 tall x 5 deep box (45 blocks).
 * The controller sits on the front face (d = 0) at the bottom row (v = 0), in any column
 * (u = 0, 1, or 2). There must be exactly one {@link BlockKind#OPERATING_PANEL}
 * directly above the controller at (u0, 1, 0).</p>
 *
 * <p>The top layer (v = 2) and bottom layer (v = 0) are frame layers: all positions
 * except the controller and operating panel must be {@link BlockKind#CASING}.
 * The middle layer (v = 1) is flexible: it must contain exactly one of each of
 * the three module kinds ({@link BlockKind#VACUUM_MODULE}, {@link BlockKind#FLUID_OUTLET}
 * for tin inlet, {@link BlockKind#WATER_INLET} for hydrogen inlet) in any positions.
 * There must be 1 to 2 {@link BlockKind#POWER_PORT} blocks anywhere in the remaining cells.
 * All other cells must be {@link BlockKind#CASING}.</p>
 *
 * <p>The match searches the three valid anchor columns deterministically: (u0=0, v0=0) through
 * (u0=2, v0=0), in that order. The first full match wins.</p>
 *
 * <p>Member positions are returned in the canonical iteration order u*15 + v*5 + d
 * (u outer, v middle, d inner), matching the StructureShape volume order (45 members).</p>
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.
 * The matcher is pure and stateless; the caller provides a {@code blockKindAt} function
 * that maps world positions to {@link BlockKind}.</p>
 */
public final class LightSourceStructureMatcher {

    /** Fixed light source shape: 3 wide, 3 tall, 5 deep. */
    public static final StructureShape LIGHT_SOURCE_SHAPE = new StructureShape(3, 3, 5);

    /** The bottom layer index (v = 0). */
    private static final int BOTTOM_LAYER = 0;

    /** The top layer index (v = 2). */
    private static final int TOP_LAYER = 2;

    /** The three module kinds that must each appear exactly once in the middle layer (v=1). */
    private static final BlockKind[] MODULE_KINDS = {
        BlockKind.VACUUM_MODULE,
        BlockKind.FLUID_OUTLET,  // tin inlet
        BlockKind.WATER_INLET    // hydrogen inlet
    };

    private LightSourceStructureMatcher() {}

    /**
     * Attempts to match an EUV light source multiblock structure at the given controller position and facing.
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

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all three front-face columns (u0=0..2)
        for (int u0 = 0; u0 < LIGHT_SOURCE_SHAPE.width(); u0++) {
            int v0 = BOTTOM_LAYER;
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

        List<MultiblockPos> memberPositions = new ArrayList<>(LIGHT_SOURCE_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int powerPortCount = 0;
        int[] moduleCounts = new int[MODULE_KINDS.length];

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        // For light source shape (height=3), v0=0, so panel is at v=1.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < LIGHT_SOURCE_SHAPE.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d = u*15 + v*5 + d
        for (int u = 0; u < LIGHT_SOURCE_SHAPE.width(); u++) {
            for (int v = 0; v < LIGHT_SOURCE_SHAPE.height(); v++) {
                for (int d = 0; d < LIGHT_SOURCE_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);
                    boolean isBottomLayer = (v == BOTTOM_LAYER);
                    boolean isTopLayer = (v == TOP_LAYER);
                    boolean isMiddleLayer = (v > BOTTOM_LAYER && v < TOP_LAYER); // v=1

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
                    } else {
                        // Check if it's one of the three module kinds
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
                                return MatchResult.failure(kind + " at wrong position " + worldPos + " (modules must be in middle layer v=1, not at panel position)");
                            }
                        } else if (isBottomLayer || isTopLayer) {
                            // Frame layers: only casing allowed (besides controller/panel at their positions)
                            if (kind != BlockKind.CASING) {
                                return MatchResult.failure("frame layer at " + worldPos + " must be casing, got " + kind);
                            }
                        } else if (kind != BlockKind.CASING) {
                            // Middle layer non-module positions: only casing allowed
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