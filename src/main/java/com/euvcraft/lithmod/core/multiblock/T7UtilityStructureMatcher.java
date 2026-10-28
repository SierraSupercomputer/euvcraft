package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure structure matcher for T7 utility multiblocks: hydrogen purifier (2x2x3) and
 * central vacuum plant (3x3x5).
 *
 * <p>Both share the same geometric rules: controller on the lower front face at any
 * column, operating panel directly above it, 1-4 power ports anywhere else in the
 * volume, and the remaining cells are casing. The only difference is the required
 * special blocks: the purifier needs exactly one {@link BlockKind#GAS_INLET} and
 * exactly one {@link BlockKind#GAS_OUTLET}; the vacuum plant needs exactly one
 * {@link BlockKind#VACUUM_CONDUIT} and exactly one {@link BlockKind#VACUUM_PORT}.
 *
 * <p>The matcher searches all valid controller columns deterministically (u0 = 0 to
 * width-1, v0 = 0 fixed) and returns the first full match. Member positions are
 * returned in canonical u/v/d order (u outer, v middle, d inner), matching the
 * shape's volume iteration.
 *
 * <p>All four horizontal facings are supported. No Minecraft types are involved.
 */
public final class T7UtilityStructureMatcher {

    private T7UtilityStructureMatcher() {}

    /**
     * Shape for the hydrogen purifier: 2 wide, 2 tall, 3 deep (12 cells).
     */
    public static final StructureShape PURIFIER_SHAPE = new StructureShape(2, 2, 3);

    /**
     * Shape for the central vacuum plant: 3 wide, 3 tall, 5 deep (45 cells).
     */
    public static final StructureShape VACUUM_PLANT_SHAPE = new StructureShape(3, 3, 5);

    /**
     * Attempts to match a hydrogen purifier structure at the given controller
     * position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match
     * tries each column {@code u0} from 0 to 1 (with {@code v0 = 0} fixed) in order;
     * the first full match wins.</p>
     *
     * <p>Requirements:
     * <ul>
     *   <li>Exactly one {@link BlockKind#CONTROLLER} at the anchor</li>
     *   <li>Exactly one {@link BlockKind#OPERATING_PANEL} directly above the controller at (u0, 1, 0)</li>
     *   <li>Exactly one {@link BlockKind#GAS_INLET} anywhere except controller/panel</li>
     *   <li>Exactly one {@link BlockKind#GAS_OUTLET} anywhere except controller/panel</li>
     *   <li>1 to 4 {@link BlockKind#POWER_PORT} anywhere except controller/panel</li>
     *   <li>All remaining cells {@link BlockKind#CASING}</li>
     * </ul>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything that is not a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 18 member positions in canonical u/v/d order on success
     */
    public static MatchResult matchPurifier(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(blockKindAt);

        String firstFailure = null;
        for (int u0 = 0; u0 < PURIFIER_SHAPE.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchPurifierAtAnchor(controllerPos, facing, blockKindAt, u0, v0);
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
     * Attempts to match a central vacuum plant structure at the given controller
     * position and facing.
     *
     * <p>The controller position IS the anchor (front face, lower row). The match
     * tries each column {@code u0} from 0 to 2 (with {@code v0 = 0} fixed) in order;
     * the first full match wins.</p>
     *
     * <p>Requirements:
     * <ul>
     *   <li>Exactly one {@link BlockKind#CONTROLLER} at the anchor</li>
     *   <li>Exactly one {@link BlockKind#OPERATING_PANEL} directly above the controller at (u0, 1, 0)</li>
     *   <li>Exactly one {@link BlockKind#VACUUM_CONDUIT} anywhere except controller/panel</li>
     *   <li>Exactly one {@link BlockKind#VACUUM_PORT} anywhere except controller/panel</li>
     *   <li>1 to 4 {@link BlockKind#POWER_PORT} anywhere except controller/panel</li>
     *   <li>All remaining cells {@link BlockKind#CASING}</li>
     * </ul>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything that is not a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 45 member positions in canonical u/v/d order on success
     */
    public static MatchResult matchVacuumPlant(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(blockKindAt);

        String firstFailure = null;
        for (int u0 = 0; u0 < VACUUM_PLANT_SHAPE.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchVacuumPlantAtAnchor(controllerPos, facing, blockKindAt, u0, v0);
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
     * Tries to match the purifier with the controller at front-face offset (u0, v0=0).
     */
    private static MatchResult matchPurifierAtAnchor(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(PURIFIER_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int gasInletCount = 0;
        int gasOutletCount = 0;
        int powerPortCount = 0;

        // The operating panel must be at local (u0, 1, 0) - directly above the controller.
        // The anchor is always on the lower row (v0=0) and the shape is 3 tall, so the panel
        // cell (v=1) always exists.
        int panelV = v0 + 1;

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d
        for (int u = 0; u < PURIFIER_SHAPE.width(); u++) {
            for (int v = 0; v < PURIFIER_SHAPE.height(); v++) {
                for (int d = 0; d < PURIFIER_SHAPE.length(); d++) {
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
                            case GAS_INLET -> gasInletCount++;
                            case GAS_OUTLET -> gasOutletCount++;
                            case CONTROLLER -> {
                                return MatchResult.failure("extra controller at " + worldPos);
                            }
                            case OPERATING_PANEL -> {
                                return MatchResult.failure("operating panel at wrong position " + worldPos);
                            }
                            default -> {
                                return MatchResult.failure(kind + " not allowed in purifier structure at " + worldPos);
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
        if (gasInletCount != 1) {
            return MatchResult.failure("expected exactly one gas inlet, found " + gasInletCount);
        }
        if (gasOutletCount != 1) {
            return MatchResult.failure("expected exactly one gas outlet, found " + gasOutletCount);
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
     * Tries to match the vacuum plant with the controller at front-face offset (u0, v0=0).
     */
    private static MatchResult matchVacuumPlantAtAnchor(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0) {

        List<MultiblockPos> memberPositions = new ArrayList<>(VACUUM_PLANT_SHAPE.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int vacuumConduitCount = 0;
        int vacuumPortCount = 0;
        int powerPortCount = 0;

        // The operating panel must be at local (u0, 1, 0) - directly above the controller.
        // The anchor is always on the lower row (v0=0) and the shape is 3 tall, so the panel
        // cell (v=1) always exists.
        int panelV = v0 + 1;

        // Iterate in canonical order: u outer, v middle, d inner -> index = u*(h*l) + v*l + d
        for (int u = 0; u < VACUUM_PLANT_SHAPE.width(); u++) {
            for (int v = 0; v < VACUUM_PLANT_SHAPE.height(); v++) {
                for (int d = 0; d < VACUUM_PLANT_SHAPE.length(); d++) {
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
                            case VACUUM_CONDUIT -> vacuumConduitCount++;
                            case VACUUM_PORT -> vacuumPortCount++;
                            case CONTROLLER -> {
                                return MatchResult.failure("extra controller at " + worldPos);
                            }
                            case OPERATING_PANEL -> {
                                return MatchResult.failure("operating panel at wrong position " + worldPos);
                            }
                            default -> {
                                return MatchResult.failure(kind + " not allowed in vacuum plant structure at " + worldPos);
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
        if (vacuumConduitCount != 1) {
            return MatchResult.failure("expected exactly one vacuum conduit, found " + vacuumConduitCount);
        }
        if (vacuumPortCount != 1) {
            return MatchResult.failure("expected exactly one vacuum port, found " + vacuumPortCount);
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
     * Returns world cell (u, v, d) of the box whose controller sits at front face offset
     * (u0, v0), that is controller + (u - u0)*right + (v - v0)*up + d*back.
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