package com.euvcraft.lithmod.core.multiblock;

/**
 * The four horizontal facings, together with the geometric basis a multiblock is laid out against.
 *
 * <p>For a facing {@code f} the basis is:
 * <ul>
 *   <li>{@link #back()} = {@code -f}: the direction a structure extends from the controller's face.</li>
 *   <li>{@link #right()} = {@code f} rotated 90 degrees clockwise seen from above.</li>
 *   <li>{@link #up()} = {@code +Y}.</li>
 * </ul>
 *
 * <p>All of these are unit steps; no Minecraft types are involved.
 */
public enum MultiblockDirection {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0);

    /** Up is always positive Y. */
    private static final MultiblockPos UP = new MultiblockPos(0, 1, 0);

    private final MultiblockPos vector;

    MultiblockDirection(int x, int y, int z) {
        this.vector = new MultiblockPos(x, y, z);
    }

    /**
     * Returns the unit vector this facing points along.
     */
    public MultiblockPos vector() {
        return vector;
    }

    /**
     * Returns the unit vector pointing away from the controller's face, i.e. {@code -vector()}.
     */
    public MultiblockPos back() {
        return vector.multiply(-1);
    }

    /**
     * Returns the unit vector 90 degrees clockwise from {@link #vector()} seen from above.
     *
     * <p>Seen from above with north up and east right, clockwise runs
     * {@code NORTH -> EAST -> SOUTH -> WEST -> NORTH}, which maps a step {@code (x, z)} to
     * {@code (-z, x)}.
     */
    public MultiblockPos right() {
        return new MultiblockPos(-vector.z(), 0, vector.x());
    }

    /**
     * Returns the up unit vector, always {@code +Y}.
     */
    public static MultiblockPos up() {
        return UP;
    }
}