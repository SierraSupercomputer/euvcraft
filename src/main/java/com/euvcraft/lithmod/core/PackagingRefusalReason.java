package com.euvcraft.lithmod.core;

/**
 * Why the test and package machine refused a lot before judging it.
 *
 * <p>The machine refuses a lot for exactly five reasons, in the order the {@link
 * com.euvcraft.lithmod.core.PackagingRules#validate} check runs from the lot itself
 * outwards: no lot at all, no frozen run spec, an incomplete lot, a die with no chip
 * item and an unmet generation gate. A lot is only judged and consumed when every
 * one of these can be answered "no".</p>
 *
 * <p>The reasons are deliberately coarse: they say the lot may not enter the machine,
 * never what was wrong with it in detail. They are safe to log and safe to surface to
 * the operator, unlike the hidden rolls and flags of the wafers inside.</p>
 */
public enum PackagingRefusalReason {

    /** No lot was presented, or the lot holds no wafers. */
    NO_LOT,

    /** Some wafer is not at the frozen tier's final layer, so the lot is unfinished. */
    LOT_INCOMPLETE,

    /** The lot carries no frozen {@link RunSpec}, so it was never processed. */
    NO_SPEC,

    /** The chip catalog has no chip item for the lot's die type at its tier. */
    NO_CHIP_FOR_DIE,

    /** The installed generation does not meet the support matrix gate for the line. */
    GENERATION_UNMET
}
