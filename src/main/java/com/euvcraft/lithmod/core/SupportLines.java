package com.euvcraft.lithmod.core;

import java.util.List;

/**
 * Canonical line ids for the five EUVCRAFT support lines.
 */
public final class SupportLines {
    public static final String SUBSTRATE = "substrate";
    public static final String MASK_SHOP = "mask_shop";
    public static final String DEPOSITION_ETCH = "deposition_etch";
    public static final String UTILITIES = "utilities";
    public static final String TEST_PACKAGING = "test_packaging";

    public static final List<String> ALL = List.of(
        SUBSTRATE, MASK_SHOP, DEPOSITION_ETCH, UTILITIES, TEST_PACKAGING
    );

    private SupportLines() {
    }
}
