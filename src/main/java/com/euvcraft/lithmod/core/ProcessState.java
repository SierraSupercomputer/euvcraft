package com.euvcraft.lithmod.core;

/**
 * The state of one Process Cell run.
 */
public enum ProcessState {
    IDLE,
    RUNNING,
    PAUSED_NO_POWER,
    PAUSED_NO_RESIST,
    PAUSED_STRUCTURE,
    FINISHED
}
