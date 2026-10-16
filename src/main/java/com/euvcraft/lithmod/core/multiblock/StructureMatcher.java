package com.euvcraft.lithmod.core.multiblock;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches a multiblock structure against the world.
 * The structure is a 2x2x3 box with the controller on the front face.
 */
public final class StructureMatcher {

    private StructureMatcher() {}

    /**
     * Attempts to match a multiblock structure at the given controller position and facing.
     *
     * @param controllerPos the position of the controller block
     * @param facing the horizontal facing of the controller (NORTH, SOUTH, EAST, WEST)
     * @param shape the shape of the structure
     * @param blockKindAt a function that returns the BlockKind at a given position
     * @return the match result
     */
    public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing, StructureShape shape,
        java.util.function.Function<MultiblockPos, BlockKind> blockKindAt) {

        // The controller may occupy any of the 4 positions on the front 2x2 face
        // Try each in a fixed order and return the first valid one
        String firstFailure = null;
        for (int offsetX = 0; offsetX < shape.width(); offsetX++) {
            for (int offsetY = 0; offsetY < shape.height(); offsetY++) {
                MatchResult result = tryMatchAtOffset(controllerPos, facing, shape, blockKindAt, offsetX, offsetY);
                if (result.success()) {
                    return result;
                }
                // Record the first failure reason
                if (firstFailure == null) {
                    firstFailure = result.failureReason();
                }
            }
        }

        // No valid position found - return the first specific failure, or generic if none
        return MatchResult.failure(firstFailure != null ? firstFailure : "no valid controller position found");
    }

    /**
     * Tries to match the structure with the controller at a specific offset on the front face.
     */
    private static MatchResult tryMatchAtOffset(MultiblockPos controllerPos, MultiblockDirection facing, StructureShape shape,
        java.util.function.Function<MultiblockPos, BlockKind> blockKindAt, int offsetX, int offsetY) {

        // Calculate the corner of the structure (minimum coordinates)
        MultiblockPos structureOrigin = calculateStructureOrigin(controllerPos, facing, shape, offsetX, offsetY);

        // Determine the controller's local position on the front face
        ControllerLocalPos controllerLocal = getControllerLocalPos(facing, shape, offsetX, offsetY);

        // Track what we've found
        int controllerCount = 0;
        int powerPortCount = 0;
        List<MultiblockPos> memberPositions = new ArrayList<>(shape.volume());

        // Check all positions in the structure
        for (int x = 0; x < shape.width(); x++) {
            for (int y = 0; y < shape.height(); y++) {
                for (int z = 0; z < shape.length(); z++) {
                    MultiblockPos localPos = new MultiblockPos(x, y, z);
                    MultiblockPos worldPos = structureOrigin.add(localPos);

                    // Determine if this is the controller position
                    boolean isControllerPosition = (x == controllerLocal.x() && y == controllerLocal.y() && z == controllerLocal.z());

                    BlockKind actualKind = blockKindAt.apply(worldPos);

                    // Check for conflicts
                    if (actualKind == BlockKind.OTHER) {
                        return MatchResult.failure("wrong block at " + worldPos + ": expected structure block");
                    }

                    if (actualKind == BlockKind.CONTROLLER) {
                        if (isControllerPosition) {
                            controllerCount++;
                        } else {
                            // Controller at wrong position
                            return MatchResult.failure("extra controller at " + worldPos);
                        }
                    } else if (actualKind == BlockKind.POWER_PORT) {
                        powerPortCount++;
                    } else if (actualKind == BlockKind.CASING) {
                        // OK
                    } else {
                        // Empty or other
                        return MatchResult.failure("missing block at " + worldPos);
                    }

                    memberPositions.add(worldPos);
                }
            }
        }

        // Validate counts
        if (controllerCount != 1) {
            return MatchResult.failure("expected exactly one controller, found " + controllerCount);
        }

        if (powerPortCount < 1) {
            return MatchResult.failure("expected at least one power port, found " + powerPortCount);
        }

        if (powerPortCount > 2) {
            return MatchResult.failure("expected at most two power ports, found " + powerPortCount);
        }

        // Check for blocks already part of another structure
        // This is handled by the caller checking the formed state

        return MatchResult.success(memberPositions);
    }

    /**
     * Returns the controller's local position within the structure for a given facing and offset.
     * Local coordinate system: x in [0, width), y in [0, height), z in [0, length)
     * where x = horizontal on front face, y = vertical on front face, z = depth (0 = front for SOUTH/EAST, length-1 = front for NORTH/WEST)
     */
    private static ControllerLocalPos getControllerLocalPos(MultiblockDirection facing, StructureShape shape, int offsetX, int offsetY) {
        return switch (facing) {
            case NORTH -> new ControllerLocalPos(offsetX, offsetY, shape.length() - 1);
            case SOUTH -> new ControllerLocalPos(offsetX, offsetY, 0);
            case EAST -> new ControllerLocalPos(0, offsetY, offsetX);
            case WEST -> new ControllerLocalPos(shape.length() - 1, offsetY, offsetX);
        };
    }

    /**
     * Calculates the world position of the structure origin (minimum corner)
     * given the controller position, facing, and controller offset on the front face.
     */
    private static MultiblockPos calculateStructureOrigin(MultiblockPos controllerPos, MultiblockDirection facing,
        StructureShape shape, int offsetX, int offsetY) {

        ControllerLocalPos controllerLocal = getControllerLocalPos(facing, shape, offsetX, offsetY);

        // The structure origin is the controller position minus the controller's local position
        // But we need to account for the coordinate system mapping for each facing
        return switch (facing) {
            case NORTH -> new MultiblockPos(
                controllerPos.x() - controllerLocal.x(),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.z()
            );
            case SOUTH -> new MultiblockPos(
                controllerPos.x() - controllerLocal.x(),
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.z()
            );
            case EAST -> new MultiblockPos(
                controllerPos.x(),  // originX = controllerX (min X)
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.x()  // originZ = controllerZ - offsetX
            );
            case WEST -> new MultiblockPos(
                controllerPos.x() - (shape.length() - 1),  // originX = controllerX - (length-1) (min X)
                controllerPos.y() - controllerLocal.y(),
                controllerPos.z() - controllerLocal.x()  // originZ = controllerZ - offsetX
            );
        };
    }

    /**
     * Helper record for controller local position.
     */
    private record ControllerLocalPos(int x, int y, int z) {}
}