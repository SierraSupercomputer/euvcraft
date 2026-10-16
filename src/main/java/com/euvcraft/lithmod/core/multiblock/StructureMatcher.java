package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a multiblock structure against the world.
 *
 * <p>The structure is an axis-aligned box built from the controller's own cell. For facing
 * {@code f} it starts at the controller's face and extends
 * {@link StructureShape#length()} cells along {@link MultiblockDirection#back()},
 * {@link StructureShape#width()} cells along {@link MultiblockDirection#right()} and
 * {@link StructureShape#height()} cells along {@link MultiblockDirection#up()}. The controller
 * occupies the front face cell {@code (u0, v0, 0)}, so the box origin is
 * {@code controller - u0*right - v0*up} and cell {@code (u, v, d)} is
 * {@code origin + u*right + v*up + d*back}.
 *
 * <p>Every cell must be a structure block, exactly one cell must be the controller, and there must
 * be at least one and at most two power ports. No Minecraft types are involved.
 */
public final class StructureMatcher {

    private StructureMatcher() {}

    /**
     * Attempts to match a multiblock structure at the given controller position and facing.
     *
     * <p>All front face offsets {@code (u0, v0)} are tried in a fixed order and the first full
     * match wins, so the result is deterministic.
     *
     * @param controllerPos the position of the controller block
     * @param facing the horizontal facing of the controller
     * @param shape the shape of the structure
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 12-style member positions on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing, StructureShape shape,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        String firstFailure = null;
        for (int u0 = 0; u0 < shape.width(); u0++) {
            for (int v0 = 0; v0 < shape.height(); v0++) {
                MatchResult result = matchAtOffset(controllerPos, facing, shape, blockKindAt, u0, v0);
                if (result.success()) {
                    return result;
                }
                if (firstFailure == null) {
                    firstFailure = result.failureReason();
                }
            }
        }
        return MatchResult.failure(firstFailure != null ? firstFailure : "no valid controller position found");
    }

    /**
     * Tries to match the structure with the controller sitting on the front face cell
     * {@code (u0, v0)}.
     */
    private static MatchResult matchAtOffset(MultiblockPos controllerPos, MultiblockDirection facing,
            StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(shape.volume());
        int controllerCount = 0;
        int powerPortCount = 0;

        for (int u = 0; u < shape.width(); u++) {
            for (int v = 0; v < shape.height(); v++) {
                for (int d = 0; d < shape.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, shape, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);
                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }
                    if (kind == BlockKind.CONTROLLER) {
                        if (u == u0 && v == v0 && d == 0) {
                            controllerCount++;
                        } else {
                            return MatchResult.failure("extra controller at " + worldPos);
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
            return MatchResult.failure("expected exactly one controller, found " + controllerCount);
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
     * Returns world cell {@code (u, v, d)} of the box whose controller sits at front face offset
     * {@code (u0, v0)}, that is
     * {@code controller + (u - u0)*right + (v - v0)*up + d*back}.
     */
    private static MultiblockPos cellPos(MultiblockPos controllerPos, MultiblockDirection facing, StructureShape shape,
            int u0, int v0, int u, int v, int d) {
        return controllerPos
                .add(facing.right().multiply(u - u0))
                .add(MultiblockDirection.up().multiply(v - v0))
                .add(facing.back().multiply(d));
    }
}