package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a zone refiner multiblock structure against the world.
 *
 * <p>The structure is a fixed 3 wide x 3 tall x 5 deep box (45 blocks), fully filled. The
 * controller sits on the front face (d = 0) at the bottom row (v = 0), in any of the three
 * columns (u = 0, 1 or 2). There must be exactly one {@link BlockKind#OPERATING_PANEL}
 * immediately above the controller at (u0, 1, 0). Exactly one {@link BlockKind#WATER_INLET}
 * and one or two {@link BlockKind#POWER_PORT} blocks sit anywhere else in the volume. Every
 * remaining cell is {@link BlockKind#CASING}. Only the BlockKind values the framework already
 * declares are used; no kind is appended for the zone.</p>
 *
 * <p>Any {@link BlockKind#OTHER} block, extra controller, extra or misplaced operating panel,
 * extra inlet, or a module/panel kind the zone does not use causes match failure.</p>
 *
 * <p>The match searches the three valid anchor columns deterministically: (u0=0, v0=0),
 * (u0=1, v0=0), (u0=2, v0=0), in that order. The first full match wins. Member positions are
 * returned in the canonical iteration order {@code u*(height*length) + v*length + d}
 * (u outer, v middle, d inner), matching the shape's volume order (45 members).</p>
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.</p>
 */
public final class ZoneStructureMatcher {

    /** Fixed shape: 3 wide, 3 tall, 5 deep. */
    public static final StructureShape ZONE_SHAPE = new StructureShape(3, 3, 5);

    private ZoneStructureMatcher() {}

    /**
     * Attempts to match a zone refiner structure at the given controller position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match tries each
     * column {@code u0} from 0 to 2 (with {@code v0 = 0} fixed) in order; the first full match
     * wins.</p>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything that is not a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 45 member positions in canonical u/v/d order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(blockKindAt);

        String firstFailure = null;
        for (int u0 = 0; u0 < ZONE_SHAPE.width(); u0++) {
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

        List<MultiblockPos> memberPositions = new ArrayList<>(ZONE_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int waterInletCount = 0;
        int powerPortCount = 0;

        // The operating panel must be at local (u0, 1, 0) - directly above the controller.
        // The anchor is always on the lower row (v0=0) and the shape is 3 tall, so the panel
        // cell (v=1) always exists.
        int panelV = v0 + 1;

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d
        for (int u = 0; u < ZONE_SHAPE.width(); u++) {
            for (int v = 0; v < ZONE_SHAPE.height(); v++) {
                for (int d = 0; d < ZONE_SHAPE.length(); d++) {
                    MultiblockPos worldPos = cellPos(controllerPos, facing, u0, v0, u, v, d);
                    BlockKind kind = blockKindAt.apply(worldPos);

                    if (kind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0);

                    if (isControllerPos) {
                        if (kind != BlockKind.CONTROLLER) {
                            return MatchResult.failure("expected controller at anchor, found " + kind + " at " + worldPos);
                        }
                        controllerCount++;
                    } else if (isPanelPos) {
                        if (kind == BlockKind.OPERATING_PANEL) {
                            operatingPanelCount++;
                        } else if (kind == BlockKind.CASING) {
                            // Allowed but means no panel; the count check below refuses it.
                        } else {
                            return MatchResult.failure(kind + " not allowed at operating panel position " + worldPos);
                        }
                    } else {
                        switch (kind) {
                            case CASING -> {}
                            case POWER_PORT -> powerPortCount++;
                            case WATER_INLET -> waterInletCount++;
                            case CONTROLLER -> {
                                return MatchResult.failure("extra controller at " + worldPos);
                            }
                            case OPERATING_PANEL -> {
                                return MatchResult.failure("operating panel at wrong position " + worldPos);
                            }
                            default -> {
                                return MatchResult.failure(kind + " not allowed in zone structure at " + worldPos);
                            }
                        }
                    }
                    memberPositions.add(worldPos);
                }
            }
        }

        if (controllerCount != 1) {
            return MatchResult.failure("expected exactly one controller at anchor, found " + controllerCount);
        }
        if (operatingPanelCount != 1) {
            return MatchResult.failure("expected exactly one operating panel above controller, found " + operatingPanelCount);
        }
        if (waterInletCount != 1) {
            return MatchResult.failure("expected exactly one water inlet, found " + waterInletCount);
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
