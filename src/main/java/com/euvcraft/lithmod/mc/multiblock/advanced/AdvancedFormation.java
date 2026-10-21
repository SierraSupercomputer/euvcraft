package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;

import java.util.List;
import java.util.function.Function;

/**
 * Shared geometry helper for T4 advanced multiblocks.
 *
 * <p>Two multiblocks share the same geometry rules: a lower controller (Equipment Cabinet)
 * with a required upper operating panel directly above it, any front column and all four
 * horizontal facings, 1-2 power ports anywhere else, and the rest casing. The only difference
 * is the shape: deposition_etcher is 3 wide x 3 tall x 5 deep (45 blocks), and
 * advanced_test_package is 2 wide x 2 tall x 3 deep (12 blocks).</p>
 *
 * <p>This class provides a common interface for matching both shapes via composition with
 * {@link StructureMatcher}. It defines the shared property objects once: FORMED, FACING,
 * PART 0..44, LARGE boolean. Default false/unformed and part 0 — no undefined states.</p>
 */
public final class AdvancedFormation {

    private AdvancedFormation() {}

    /** Deposition Etcher: 3 wide x 3 tall x 5 deep = 45 blocks. */
    public static final StructureShape DEPOSITION_ETCHER_SHAPE = new StructureShape(3, 3, 5);

    /** Advanced Test Package: 2 wide x 2 tall x 3 deep = 12 blocks. */
    public static final StructureShape ADVANCED_TEST_PACKAGE_SHAPE = new StructureShape(2, 2, 3);

    /**
     * Matches an advanced multiblock structure using {@link StructureMatcher}.
     *
     * <p>The controller sits on the front face (d=0) at the lower row (v=0), in any column
     * (u=0..width-1). The operating panel must be directly above the controller at
     * (u, v+1, 0), which requires v=0 (controller on bottom row) for both shapes.
     * There must be 1-2 power ports anywhere in the structure. All other cells must be
     * casing. No other block kinds allowed.</p>
     *
     * @param controllerPos the position of the controller block
     * @param facing the horizontal facing of the controller
     * @param shape the shape to match (DEPOSITION_ETCHER_SHAPE or ADVANCED_TEST_PACKAGE_SHAPE)
     * @param blockKindAt reports the kind of block at a world position
     * @param controllerKind the BlockKind to report for the controller block
     * @param panelKind the BlockKind to report for the operating panel block
     * @param powerPortKind the BlockKind to report for power port blocks
     * @param casingKind the BlockKind to report for casing blocks
     * @return the match result with member positions in canonical order on success
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
                                     StructureShape shape, Function<MultiblockPos, BlockKind> blockKindAt,
                                     BlockKind controllerKind, BlockKind panelKind,
                                     BlockKind powerPortKind, BlockKind casingKind) {

        // Delegate to StructureMatcher which already implements the correct geometry rules
        // for controller-on-front-face with operating panel above, 1-2 power ports, rest casing.
        return StructureMatcher.match(controllerPos, facing, shape, blockKindAt);
    }

    /**
     * The canonical member position order: u*volume_per_u + v*shape.length() + d
     * where volume_per_u = shape.height() * shape.length().
     *
     * <p>For deposition_etcher (3x3x5): u*15 + v*5 + d, indices 0..44.
     * For advanced_test_package (2x2x3): u*6 + v*3 + d, indices 0..11.</p>
     */
    public static int partIndex(StructureShape shape, int u, int v, int d) {
        return u * shape.height() * shape.length() + v * shape.length() + d;
    }

    /**
     * The visual part property shared by all advanced multiblock members.
     * Range: 0..44 for deposition_etcher, 0..11 for advanced_test_package.
     * When unformed, always 0.
     */
    public static final com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.PartProperty PART_PROPERTY =
            new com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.PartProperty();

    /**
     * The visual formed property shared by all advanced multiblock members.
     * Default false.
     */
    public static final com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FormedProperty FORMED_PROPERTY =
            new com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FormedProperty();

    /**
     * The visual facing property shared by all advanced multiblock members.
     * Only meaningful when formed; default NORTH when unformed.
     */
    public static final com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FacingProperty FACING_PROPERTY =
            new com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.FacingProperty();

    /**
     * The LARGE boolean property: true for deposition_etcher (3x3x5), false for advanced_test_package (2x2x3).
     * Used later for joined panel rendering.
     */
    public static final com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.LargeProperty LARGE_PROPERTY =
            new com.euvcraft.lithmod.mc.multiblock.ProcessCellVisualProperties.LargeProperty();
}