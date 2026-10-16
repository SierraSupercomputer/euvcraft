package com.euvcraft.lithmod.core;

import java.util.List;

/**
 * Reason a process run could not be started.
 */
public record RefusalReason(Code code, List<String> unmetLines) {

    public enum Code {
        NO_LOT,
        EMPTY_LOT,
        LOT_ALREADY_COMPLETE,
        SPEC_MISMATCH,
        NO_MASK,
        MASK_SPENT,
        DIE_NOT_MAKEABLE,
        LINE_CANNOT_RUN_TIER,
        GENERATION_UNMET
    }

    public RefusalReason {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (unmetLines == null) {
            unmetLines = List.of();
        } else {
            unmetLines = List.copyOf(unmetLines);
        }
    }

    public RefusalReason(Code code) {
        this(code, List.of());
    }

    public static final RefusalReason NO_LOT = new RefusalReason(Code.NO_LOT);
    public static final RefusalReason EMPTY_LOT = new RefusalReason(Code.EMPTY_LOT);
    public static final RefusalReason LOT_ALREADY_COMPLETE = new RefusalReason(Code.LOT_ALREADY_COMPLETE);
    public static final RefusalReason SPEC_MISMATCH = new RefusalReason(Code.SPEC_MISMATCH);
    public static final RefusalReason NO_MASK = new RefusalReason(Code.NO_MASK);
    public static final RefusalReason MASK_SPENT = new RefusalReason(Code.MASK_SPENT);
    public static final RefusalReason DIE_NOT_MAKEABLE = new RefusalReason(Code.DIE_NOT_MAKEABLE);
    public static final RefusalReason LINE_CANNOT_RUN_TIER = new RefusalReason(Code.LINE_CANNOT_RUN_TIER);

    public static RefusalReason generationUnmet(List<String> unmetLineNames) {
        return new RefusalReason(Code.GENERATION_UNMET, unmetLineNames);
    }

    public boolean is(Code code) {
        return this.code == code;
    }
}
