package com.euvcraft.lithmod.core.multiblock;

/**
 * Defines the shape of a multiblock structure.
 * The Process Cell shape is 2 wide x 2 tall x 3 long.
 */
public record StructureShape(int width, int height, int length) {
    /** The fixed shape for the Process Cell multiblock. */
    public static final StructureShape PROCESS_CELL = new StructureShape(2, 2, 3);

    /**
     * Returns the total number of blocks in this shape.
     */
    public int volume() {
        return width * height * length;
    }
}