package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The eight reference tiers from the design reference table, in one place for the yield
 * tests to share.
 *
 * <p>These are the numbers a test asserts against, so they are written out here rather
 * than read from the data files: a test that read the data files would only prove that
 * the engine agrees with itself.</p>
 */
final class ReferenceTiers {

    private ReferenceTiers() {
    }

    /**
     * Returns T1 to T8 exactly as the reference table has them.
     *
     * @return the eight tiers in index order
     */
    static List<Tier> all() {
        List<Tier> tiers = new ArrayList<>(8);
        tiers.add(new Tier("T1", 1, "Contact", "mercury arc lamp",
            5000, 1000, 75, 6, 60.0, 1, null, false));
        tiers.add(new Tier("T2", 2, "Stepper", "g-line then i-line lamp",
            500, 150, 45, 12, 80.0, 15, null, false));
        tiers.add(new Tier("T3", 3, "KrF DUV", "248 nm excimer laser",
            180, 40, 80, 24, 90.0, 600, null, false));
        tiers.add(new Tier("T4", 4, "ArF dry", "193 nm excimer laser",
            65, 15, 110, 32, 92.0, 2600, null, false));
        tiers.add(new Tier("T5", 5, "ArF immersion", "193 nm, water immersion",
            28, 4, 250, 42, 93.0, 3400, null, false));
        tiers.add(new Tier("T6", 6, "Multi-patterned", "193 nm, several passes",
            7, 3, 125, 70, 85.0, 3900, 4, true));
        tiers.add(new Tier("T7", 7, "EUV", "13.5 nm tin plasma",
            13, 2, 140, 80, 85.0, 5000, 8, false));
        tiers.add(new Tier("T8", 8, "High-NA EUV", "13.5 nm tin plasma, NA 0.55",
            8, 1, 170, 90, 80.0, 5500, 12, false));
        return tiers;
    }

    /**
     * Returns the reference tiers as a registry.
     *
     * @return a registry holding T1 to T8
     */
    static TierRegistry registry() {
        return TierRegistry.build(all(), "reference tiers");
    }

    /**
     * Returns one reference tier.
     *
     * @param id the tier id, {@code T1} to {@code T8}
     * @return the tier
     */
    static Tier byId(String id) {
        for (Tier tier : all()) {
            if (tier.id().equals(id)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("no reference tier " + id);
    }
}