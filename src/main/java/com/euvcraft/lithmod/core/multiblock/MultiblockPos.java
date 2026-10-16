package com.euvcraft.lithmod.core.multiblock;

/**
 * A lightweight block position for core logic that cannot depend on Minecraft classes.
 */
public record MultiblockPos(int x, int y, int z) {
    /**
     * Adds another position to this one.
     */
    public MultiblockPos add(MultiblockPos other) {
        return new MultiblockPos(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Subtracts another position from this one.
     */
    public MultiblockPos subtract(MultiblockPos other) {
        return new MultiblockPos(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiplies this position by a scalar.
     */
    public MultiblockPos multiply(int scalar) {
        return new MultiblockPos(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Returns the offset between two positions.
     */
    public static MultiblockPos offset(MultiblockPos from, MultiblockPos to) {
        return to.subtract(from);
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}