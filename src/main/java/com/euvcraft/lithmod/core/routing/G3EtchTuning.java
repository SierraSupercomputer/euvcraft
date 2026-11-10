package com.euvcraft.lithmod.core.routing;

/** The operator tunes depth for compute and center-to-edge balance for memory. */
public final class G3EtchTuning {
    public static final int TRIM_LIMIT = 16;

    private G3EtchTuning() {}

    public static int quality(int error, int tolerance) {
        return 1000 - Math.min(1000, Math.abs(error) * 1000 / Math.max(1, tolerance));
    }

    public static int computeTolerance(int base, int cores, int coresPerStep) {
        return Math.max(1, base - (Math.max(1, cores) - 1) / Math.max(1, coresPerStep));
    }
}
