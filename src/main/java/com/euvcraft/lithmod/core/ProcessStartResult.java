package com.euvcraft.lithmod.core;

/** A start attempt produces exactly one run or one named refusal. */
public sealed interface ProcessStartResult permits ProcessRun, RefusalReason {
}
