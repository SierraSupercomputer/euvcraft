package com.euvcraft.lithmod.core.t7;

/**
 * The operating mode of one vacuum chamber for one tick.
 *
 * <p>The mode only says what the chamber is doing; it never decides whether a lot
 * is scrapped. A shortage is reported as a pause by
 * {@link VacuumChamberState#tick(VacuumPumpSpec, VacuumMode, int)}.</p>
 */
public enum VacuumMode {

    /** Chamber loaded and at rest; only the static leak has to be pumped. */
    IDLE,

    /** A T7 process is running; {@link VacuumPumpSpec#processLoadMicroTorrPerTick()} is added. */
    PROCESS,

    /** Chamber is being pumped down after loading or venting; the pump runs at full rate. */
    PUMP_DOWN
}
