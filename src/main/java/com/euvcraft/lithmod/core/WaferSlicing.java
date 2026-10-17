package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * The one number the Wafer Slicer needs: how many wafers come out of one boule.
 *
 * <p>A boule yields {@link FabSettings#wafersPerBoule()} wafers, but a FOUP only has
 * {@link FoupContents#capacity()} slots, so the lot that lands in the FOUP is the smaller
 * of the two. The two directions are not symmetric and are both worth stating plainly: a
 * FOUP <em>smaller</em> than the yield comes back filled to its own capacity, because the
 * surplus wafers are never created; a FOUP <em>larger</em> than the yield comes back
 * partially filled, because one boule only ever produced that many.</p>
 *
 * <p>This is pure core logic on purpose: the rule is the only part of the slicer worth unit
 * testing without a world, and the machine asks this class rather than repeating the
 * comparison.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class WaferSlicing {

    private WaferSlicing() {
    }

    /**
     * How many wafers one boule puts into a FOUP of the given capacity.
     *
     * @param settings the fab settings holding {@code wafersPerBoule}, not null
     * @param capacity the capacity stored in the target FOUP, {@code >= 1}
     * @return {@code min(wafersPerBoule, capacity)}, never below one
     * @throws NullPointerException     if {@code settings} is null
     * @throws IllegalArgumentException if {@code capacity} is below one
     */
    public static int wafersFor(FabSettings settings, int capacity) {
        Objects.requireNonNull(settings, "settings must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        return Math.min(settings.wafersPerBoule(), capacity);
    }
}
