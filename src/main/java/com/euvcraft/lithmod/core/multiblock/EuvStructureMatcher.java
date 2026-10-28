package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches an EUV scanner multiblock structure against the world.
 *
 * <p>The scanner structure is a fixed 5 wide x 7 tall x 5 deep box (175 blocks).
 * The controller sits on the front face (d = 0) at the bottom row (v = 0), in any column
 * (u = 0, 1, 2, 3, or 4). There must be exactly one {@link BlockKind#OPERATING_PANEL}
 * directly above the controller at (u0, 1, 0).</p>
 *
 * <p>The top layer (v = 6) and bottom layer (v = 0) are frame layers: all positions
 * except the controller and operating panel must be {@link BlockKind#CASING}.
 * The middle layers (v = 1..5) are flexible: they must contain exactly one of each of
 * the six module kinds ({@link BlockKind#EXPOSURE_MODULE}, {@link BlockKind#OPTICS_MODULE},
 * {@link BlockKind#STAGE_MODULE}, {@link BlockKind#FILTRATION_MODULE}, {@link BlockKind#METROLOGY_MODULE},
 * {@link BlockKind#VACUUM_MODULE}) in any positions across these five layers.
 * There must be 1 to 4 {@link BlockKind#POWER_PORT} blocks anywhere in the remaining cells
 * (excluding controller/operating panel positions and the fixed module positions).
 * All other cells must be {@link BlockKind#CASING}.</p>
 *
 * <p>No extra operating panel is allowed. Water inlets, diagnostic panels, multipatterning
 * modules and other block kinds cause match failure.</p>
 *
 * <p>The match searches the five valid anchor columns deterministically: (u0=0, v0=0) through
 * (u0=4, v0=0), in that order. The first full match wins.</p>
 *
 * <p>Member positions are returned in the canonical iteration order u*35 + v*5 + d
 * (u outer, v middle, d inner), matching the StructureShape volume order (175 members).</p>
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.
 * The matcher is pure and stateless; the caller provides a {@code blockKindAt} function
 * that maps world positions to {@link BlockKind}.</p>
 */
public final class EuvStructureMatcher {

    /** Fixed scanner shape: 5 wide, 7 tall, 5 deep. */
    public static final StructureShape EUV_SCANNER_SHAPE = new StructureShape(5, 7, 5);

    /** The bottom layer index (v = 0). */
    private static final int BOTTOM_LAYER = 0;

    /** The top layer index (v = 6). */
    private static final int TOP_LAYER = 6;

    /** The six module kinds that must each appear exactly once in the middle layers (v=1..5). */
    private static final BlockKind[] MODULE_KINDS = {
        BlockKind.EXPOSURE_MODULE,
        BlockKind.OPTICS_MODULE,
        BlockKind.STAGE_MODULE,
        BlockKind.FILTRATION_MODULE,
        BlockKind.METROLOGY_MODULE,
        BlockKind.VACUUM_MODULE
    };

    private EuvStructureMatcher() {}

    /**
     * Attempts to match an EUV scanner multiblock structure at the given controller position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match tries each column
     * {@code u0} from 0 to 4 (with {@code v0 = 0} fixed) in order; the first full match wins.
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 175 member positions in canonical u/v/d order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(blockKindAt);

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all five front-face columns (u0=0..4)
        for (int u0 = 0; u0 < EUV_SCANNER_SHAPE.width(); u0++) {
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

        List<MultiblockPos> memberPositions = new ArrayList<>(EUV_SCANNER_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int powerPortCount = 0;
        int[] moduleCounts = new int[MODULE_KINDS.length]; // one per module kind

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
        // For EUV scanner shape (height=7), v0=0, so panel is at v=1.
        int panelV = v0 + 1;
        boolean panelPositionValid = panelV < EUV_SCANNER_SHAPE.height();

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d = u*35 + v*5 + d
        for (int u = 0; u < EUV_SCANNER_SHAPE.width(); u++) {
            for (int v = 0; v < EUV_SCANNER_SHAPE.height(); v++) {
                for (int d = 0; d < EUV_SCANNER_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);
                    boolean isBottomLayer = (v == BOTTOM_LAYER);
                    boolean isTopLayer = (v == TOP_LAYER);
                    boolean isMiddleLayer = (v > BOTTOM_LAYER && v < TOP_LAYER); // v=1..5

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
                        // Check if it's one of the six module kinds
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
                                return MatchResult.failure(kind + " at wrong position " + worldPos + " (modules must be in middle layers v=1..5, not at panel position)");
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
                return MatchResult.failure("expected exactly one " + MODULE_KINDS[i] + " in middle layers, found " + moduleCounts[i]);
            }
        }
        if (powerPortCount < 1) {
            return MatchResult.failure("expected at least one power port, found " + powerPortCount);
        }
        if (powerPortCount > 4) {
            return MatchResult.failure("expected at most four power ports, found " + powerPortCount);
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