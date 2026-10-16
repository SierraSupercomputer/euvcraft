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
    OTHER
}