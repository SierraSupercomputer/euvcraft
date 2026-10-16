package com.euvcraft.lithmod.core.multiblock;

import java.util.List;

/**
 * Result of a multiblock structure match attempt.
 */
public record MatchResult(
    /** Whether the match succeeded. */
    boolean success,

    /** The positions of all member blocks relative to the controller, if successful. */
    List<MultiblockPos> memberPositions,

    /** A short failure reason for the first mismatch, if unsuccessful. */
    String failureReason
) {
    /**
     * Creates a successful match result.
     */
    public static MatchResult success(List<MultiblockPos> memberPositions) {
        return new MatchResult(true, List.copyOf(memberPositions), null);
    }

    /**
     * Creates a failed match result.
     */
    public static MatchResult failure(String reason) {
        return new MatchResult(false, List.of(), reason);
    }
}