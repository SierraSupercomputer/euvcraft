package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Matches a G3 multiblock structure against the world.
 *
 * <p>The G3 machines share a common geometry: a rectangular solid with the controller
 * on the lower-front face (d = 0, v = 0) in any column (u = 0..width-1), an operating
 * panel directly above at (u0, 1, 0), and the remaining cells filled according to the
 * machine kind's module/frame rules.</p>
 *
 * <p>Two shapes are supported:</p>
 * <ul>
 *   <li>Writer: 3 wide x 3 tall x 5 deep (45 cells) — top/bottom frames, middle layer
 *       accepts writing/metrology modules and power ports</li>
 *   <li>Deposition/Etcher: 5 wide x 5 tall x 7 deep (175 cells) — top/bottom frames,
 *       middle three layers accept deposition/rinse/vacuum modules and power ports</li>
 * </ul>
 *
 * <p>Rules common to both:</p>
 * <ul>
 *   <li>Exactly one controller at the anchor (u0, 0, 0)</li>
 *   <li>Exactly one operating panel at (u0, 1, 0)</li>
 *   <li>1 to 4 power ports anywhere except controller/panel positions</li>
 *   <li>Optional water inlet anywhere except controller/panel positions</li>
 *   <li>All other cells must match the kind's allowed block kinds</li>
 *   <li>No foreign blocks, missing parts, or extra parts</li>
 * </ul>
 *
 * <p>The match searches all valid anchor columns (u0 = 0..width-1, v0 = 0 fixed)
 * deterministically; the first full match wins. Member positions are returned in
 * canonical iteration order: u * (height * length) + v * length + d (u outer, v middle, d inner).</p>
 *
 * <p>No Minecraft types are involved.</p>
 */
public final class G3StructureMatcher {

    /** Writer shape: 3 wide, 3 tall, 5 deep (45 cells). */
    public static final StructureShape WRITER_SHAPE = new StructureShape(3, 3, 5);

    /** Deposition/Etcher shape: 5 wide, 5 tall, 7 deep (175 cells). */
    public static final StructureShape DEPOSITION_ETCHER_SHAPE = new StructureShape(5, 5, 7);

    /** Writer middle layer index (v = 1). */
    private static final int WRITER_MIDDLE = 1;

    /** Deposition/Etcher middle layer range (v = 1, 2, 3). */
    private static final int DEP_ETCH_MIDDLE_START = 1;
    private static final int DEP_ETCH_MIDDLE_END = 3; // inclusive

    private G3StructureMatcher() {}

    /**
     * Matches a G3 writer structure (3x3x5).
     *
     * <p>The middle layer (v = 1) must contain at least one {@link BlockKind#WRITING_MODULE}
     * and at least one {@link BlockKind#METROLOGY_MODULE}, in any positions. Power ports
     * (1-4) and an optional water inlet may appear anywhere except the controller and
     * operating panel positions. Top (v = 2) and bottom (v = 0) layers are frames and must
     * be casing or power ports.</p>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 45 member positions in canonical u/v/d order on success
     */
    public static MatchResult matchWriter(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {
        return match(controllerPos, facing, WRITER_SHAPE, blockKindAt,
                new WriterRequirements());
    }

    /**
     * Matches a G3 deposition/etcher structure (5x5x7).
     *
     * <p>The middle three layers (v = 1, 2, 3) must contain at least one
     * {@link BlockKind#DEPOSITION_MODULE}, at least one {@link BlockKind#RINSE_MODULE},
     * and at least one {@link BlockKind#VACUUM_MODULE}, in any positions across those layers.
     * Power ports (1-4) and an optional water inlet may appear anywhere except the controller
     * and operating panel positions. Top (v = 4) and bottom (v = 0) layers are frames and must
     * be casing or power ports.</p>
     *
     * @param controllerPos the position of the controller block (the anchor)
     * @param facing the horizontal facing of the controller
     * @param blockKindAt reports the kind of block at a world position; anything other than a
     *     structure block must be reported as {@link BlockKind#OTHER}
     * @return the match result, with the 175 member positions in canonical u/v/d order on success
     */
    public static MatchResult matchDepositionEtcher(MultiblockPos controllerPos, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> blockKindAt) {
        return match(controllerPos, facing, DEPOSITION_ETCHER_SHAPE, blockKindAt,
                new DepositionEtcherRequirements(false));
    }

    public static MatchResult matchT8Stage(MultiblockPos controller, MultiblockDirection facing,
            boolean planarizer, Function<MultiblockPos, BlockKind> lookup) {
        var shape = planarizer ? WRITER_SHAPE : new StructureShape(5, 3, 7);
        return match(controller, facing, shape, lookup, new DepositionEtcherRequirements(true));
    }

    /** Internal matcher with kind-specific requirements. */
    private static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
            StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt,
            G3Requirements requirements) {

        java.util.Objects.requireNonNull(controllerPos);
        java.util.Objects.requireNonNull(facing);
        java.util.Objects.requireNonNull(shape);
        java.util.Objects.requireNonNull(blockKindAt);
        java.util.Objects.requireNonNull(requirements);

        String firstFailure = null;
        // Only lower row (v0=0) is valid for the anchor; try all front-face columns (u0)
        for (int u0 = 0; u0 < shape.width(); u0++) {
            int v0 = 0;
            MatchResult result = matchAtAnchor(controllerPos, facing, shape, blockKindAt, u0, v0, requirements);
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
            StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt, int u0, int v0,
            G3Requirements requirements) {

        List<MultiblockPos> memberPositions = new ArrayList<>(shape.volume());
        int controllerCount = 0;
        int operatingPanelCount = 0;
        int powerPortCount = 0;
        int waterInletCount = 0;
        int[] moduleCounts = new int[requirements.requiredModuleKinds().length];

        // The operating panel must be at local (u0, v0+1, 0) - directly above the controller.
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

                    boolean isControllerPos = (u == u0 && v == v0 && d == 0);
                    boolean isPanelPos = (u == u0 && v == panelV && d == 0 && panelPositionValid);
                    boolean isMiddleLayer = requirements.isMiddleLayer(v);

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
                        if (isControllerPos || isPanelPos) {
                            return MatchResult.failure("water inlet not allowed at controller or panel position " + worldPos);
                        }
                    } else {
                        // Check if it's one of the required module kinds
                        int moduleIndex = -1;
                        for (int i = 0; i < requirements.requiredModuleKinds().length; i++) {
                            if (kind == requirements.requiredModuleKinds()[i]) {
                                moduleIndex = i;
                                break;
                            }
                        }
                        if (moduleIndex >= 0) {
                            if (isMiddleLayer && !isPanelPos) {
                                moduleCounts[moduleIndex]++;
                            } else {
                                return MatchResult.failure(kind + " at wrong position " + worldPos + " (must be in middle layer" + (requirements.hasFrames() ? " and not at panel position" : "") + ")");
                            }
                        } else if (kind == BlockKind.CASING) {
                            // Casing is allowed everywhere
                        } else {
                            return MatchResult.failure("unexpected block kind " + kind + " at " + worldPos);
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
        if (powerPortCount < 1) {
            return MatchResult.failure("expected at least one power port, found " + powerPortCount);
        }
        if (powerPortCount > 4) {
            return MatchResult.failure("expected at most four power ports, found " + powerPortCount);
        }
        if (requirements.requiresWater() && waterInletCount != 1) {
            return MatchResult.failure("expected one G3 water inlet");
        }
        if (waterInletCount > 1) {
            return MatchResult.failure("expected at most one water inlet, found " + waterInletCount);
        }

        // Validate required modules
        for (int i = 0; i < requirements.requiredModuleKinds().length; i++) {
            if (moduleCounts[i] < 1) {
                return MatchResult.failure("expected at least one " + requirements.requiredModuleKinds()[i] + " in middle layer(s), found " + moduleCounts[i]);
            }
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

    /** Interface for kind-specific structure requirements. */
    private interface G3Requirements {
        default boolean requiresWater() { return false; }
        /** Returns the module kinds that must each appear at least once. */
        BlockKind[] requiredModuleKinds();

        /** Returns whether the given layer index is a "middle" layer where modules are required/allowed. */
        boolean isMiddleLayer(int v);

        /** Returns whether this shape has distinct frame layers (top/bottom) that restrict modules. */
        boolean hasFrames();

        /** Returns whether a module is allowed on a specific frame layer. */
        boolean allowModuleOnFrame(int v);
    }

    /** Requirements for the 3x3x5 writer. */
    private static final class WriterRequirements implements G3Requirements {
        @Override
        public BlockKind[] requiredModuleKinds() {
            return new BlockKind[]{BlockKind.WRITING_MODULE, BlockKind.METROLOGY_MODULE};
        }

        @Override
        public boolean isMiddleLayer(int v) {
            return v == WRITER_MIDDLE;
        }

        @Override
        public boolean hasFrames() {
            return true;
        }

        @Override
        public boolean allowModuleOnFrame(int v) {
            return false; // Frames (v=0, v=2) only accept casing and power ports
        }
    }

    /** Requirements for the 5x5x7 deposition/etcher. */
    private record DepositionEtcherRequirements(boolean t8) implements G3Requirements {
        @Override public boolean requiresWater() { return t8; }
        @Override
        public BlockKind[] requiredModuleKinds() {
            return new BlockKind[]{
                BlockKind.DEPOSITION_MODULE,
                BlockKind.RINSE_MODULE,
                BlockKind.VACUUM_MODULE
            };
        }

        @Override
        public boolean isMiddleLayer(int v) {
            return t8 ? v == 1 : v >= DEP_ETCH_MIDDLE_START && v <= DEP_ETCH_MIDDLE_END;
        }

        @Override
        public boolean hasFrames() {
            return true;
        }

        @Override
        public boolean allowModuleOnFrame(int v) {
            return false; // Frames (v=0, v=4) only accept casing and power ports
        }
    }
}