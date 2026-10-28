package com.euvcraft.lithmod.core.t7;

/**
 * Outcome of a structural-breach notification.
 *
 * <p>{@link #ALREADY_CONTAMINATED} and the ignored outcomes are intentionally
 * side-effect free: a repeated notification must not consume repair materials,
 * UPW or FE, and must not restart a repair or recalibration.</p>
 */
public enum BreachNotification {

    /** A powered, loaded, actively processing T7 machine became contaminated. */
    NEWLY_CONTAMINATED,

    /** The machine was already contaminated or awaiting recalibration; nothing changed. */
    ALREADY_CONTAMINATED,

    /** The machine could not have been processing because it had no power. */
    IGNORED_UNPOWERED,

    /** The machine had no carrier loaded, so there was no active wafer lot to breach. */
    IGNORED_EMPTY,

    /** The machine was powered and loaded but not running a T7 wafer process. */
    IGNORED_NOT_PROCESSING
}
