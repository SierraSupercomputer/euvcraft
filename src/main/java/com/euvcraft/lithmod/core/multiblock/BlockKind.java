package com.euvcraft.lithmod.core.multiblock;

/**
 * The kind of block at a position in a multiblock structure.
 */
public enum BlockKind {
    /** The controller block - exactly one required. */
    CONTROLLER,

    /** Casing block - fills remaining positions. */
    CASING,

    /** Power port block - at least 1, at most 2 required. */
    POWER_PORT,

    /** Any other block - causes match failure. */
    OTHER,

    /** Operating panel block - exactly one required, directly above the controller. */
    OPERATING_PANEL,

    /** Water inlet block - optional, replaces casing in purifier only. Appended to retain old ordinals. */
    WATER_INLET,

    /** Calibration panel block - exactly one required in scanner middle layer. */
    CALIBRATION_PANEL,

    /** Exposure module block - exactly one required in scanner middle layer. */
    EXPOSURE_MODULE,

    /** Optics module block - exactly one required in scanner middle layer. */
    OPTICS_MODULE,

    /** Stage module block - exactly one required in scanner middle layer. */
    STAGE_MODULE,

    /** Filtration module block - exactly one required in scanner middle layer. */
    FILTRATION_MODULE,

    /** Metrology module block - exactly one required in scanner middle layer. */
    METROLOGY_MODULE
}