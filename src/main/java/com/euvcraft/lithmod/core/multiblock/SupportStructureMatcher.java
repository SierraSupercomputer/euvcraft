package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a support structure multiblock against the world.
 *
 * <p>The structure is a 2 wide x 2 tall x 3 deep box. The controller sits on the front face
 * (d = 0) at the lower row (v = 0), in either column (u = 0 or u = 1). There is no operating
 * panel. There must be exactly one controller at the anchor, 1 or 2 power ports anywhere in the
 * structure, and all other cells must be casing. Any other block kind (including OPERATING_PANEL
 * and extra CONTROLLER) causes a match failure.
 *
 * <p>The match searches the two valid anchor offsets deterministically: (u0=0, v0=0) then
 * (u0=1, v0=0). The first full match wins.
 *
 * <p>Member positions are returned in the canonical iteration order u*6 + v*3 + d
 * (u outer, v middle, d inner), matching the StructureShape volume order.
 *
 * <p>No Minecraft types are involved.
 */
public final class SupportStructureMatcher {

    /** Fixed shape: 2 wide, 2 tall, 3 deep. */
    public static final StructureShape SUPPORT_SHAPE = new StructureShape(2, 2, 3);

    private SupportStructureMatcher() {}

    /**
     * Attempts to match a support structure at the given controller position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). Only two offsets are tried:
     * (u0=0, v0=0) and (u0=1, v0=0), in that order. The first full match wins.
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 12 member positions in u*6+v*3+d order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try both columns (u0=0, u0=1)
        for (int u0 = 0; u0 < SUPPORT_SHAPE.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controllerPos, facing, blockKindAt, u0, v0);
            if (result.success()) {
                return result;
            }
            if (firstFailure == null) {
                firstFailure = result.failureReason();
            }
        }
        return MatchResult.failure(firstFailure != null ? firstFailure : "no valid anchor found");
    }

    /**
     * Tries to match the structure with the controller at front-face offset (u0, v0=0).
     */
    private static MatchResult matchAtAnchor(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(SUPPORT_SHAPE.volume());
        int controllerCount = 0;
        int powerPortCount = 0;

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*6 + v*3 + d
        for (int u = 0; u < SUPPORT_SHAPE.width(); u++) {
            for (int v = 0; v < SUPPORT_SHAPE.height(); v++) {
                for (int d = 0; d < SUPPORT_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }

                    boolean isAnchor = (u == u0 && v == v0 && d == 0);

                    if (kind == BlockKind.CONTROLLER) {
                        if (isAnchor) {
                            controllerCount++;
                        } else {
                            return MatchResult.failure("extra controller at " + worldPos);
                        }
                    } else if (kind == BlockKind.OPERATING_PANEL) {
                        return MatchResult.failure("operating panel not allowed in support structure at " + worldPos);
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