package com.euvcraft.lithmod.core;

import java.util.List;

/**
 * Canonical die type string constants, mirroring the SupportLines pattern.
 */
public final class DieTypes {
    public static final String COMPUTE = "compute";
    public static final String LOGIC = "logic";
    public static final String MEMORY = "memory";
    public static final String NAND = "nand";

    public static final List<String> ALL = List.of(COMPUTE, LOGIC, MEMORY, NAND);

    private DieTypes() {
    }
}
