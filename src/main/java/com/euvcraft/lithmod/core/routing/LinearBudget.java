package com.euvcraft.lithmod.core.routing;

/**
 * Immutable linear fabrication budget for a multi-layer EUV exposure run.
 * Calculates scanner, coat, clean, water and resist budgets from input parameters.
 * <p>
 * This record has no Minecraft or NeoForge dependencies.
 * </p>
 *
 * @param scannerTicksPerLayer effective scanner ticks per layer after coat/clean deduction (1..72000)
 * @param coatTicks            total coat ticks across all layers (0..72000)
 * @param cleanDryTicks        total clean/dry ticks across all layers (0..72000)
 * @param cleanWaterMb         total clean water millibuckets (>=0, no overflow)
 * @param resistTotal          total resist units across all layers (0..65536)
 */
public record LinearBudget(
        int scannerTicksPerLayer,
        int coatTicks,
        int cleanDryTicks,
        int cleanWaterMb,
        int resistTotal
) {

    /**
     * Calculates a linear fabrication budget with full validation.
     *
     * @param layers               number of layers, must be 1..1024
     * @param originalLayerTicks   original layer ticks, must be 1..72000
     * @param passes               exposure passes per layer, must be 1..8
     * @param resistPerLayer       resist units per layer, must be 0..64
     * @param oldScanWaterPerTick  legacy scanner water rate (Mb/tick), must be 0..1000
     * @param newScanWaterPerTick  new scanner water rate (Mb/tick), must be 0..1000
     * @param coatTicksPerLayer    coat ticks per layer, must be 0..1000
     * @param cleanTicksPerLayer   clean/dry ticks per layer, must be 0..1000
     * @param extraRinseWaterMb    extra rinse water millibuckets, must be 0..1_000_000
     * @return a validated {@link LinearBudget}
     * @throws IllegalArgumentException if any input is out of range
     * @throws ArithmeticException      if any intermediate calculation overflows or produces invalid results
     */
    public static LinearBudget calculate(
            int layers,
            int originalLayerTicks,
            int passes,
            int resistPerLayer,
            int oldScanWaterPerTick,
            int newScanWaterPerTick,
            int coatTicksPerLayer,
            int cleanTicksPerLayer,
            int extraRinseWaterMb
    ) {
        // Validate inputs
        if (layers < 1 || layers > 1024) {
            throw new IllegalArgumentException("layers must be 1..1024, got " + layers);
        }
        if (originalLayerTicks < 1 || originalLayerTicks > 72000) {
            throw new IllegalArgumentException("originalLayerTicks must be 1..72000, got " + originalLayerTicks);
        }
        if (passes < 1 || passes > 8) {
            throw new IllegalArgumentException("passes must be 1..8, got " + passes);
        }
        if (resistPerLayer < 0 || resistPerLayer > 64) {
            throw new IllegalArgumentException("resistPerLayer must be 0..64, got " + resistPerLayer);
        }
        if (oldScanWaterPerTick < 0 || oldScanWaterPerTick > 1000) {
            throw new IllegalArgumentException("oldScanWaterPerTick must be 0..1000, got " + oldScanWaterPerTick);
        }
        if (newScanWaterPerTick < 0 || newScanWaterPerTick > 1000) {
            throw new IllegalArgumentException("newScanWaterPerTick must be 0..1000, got " + newScanWaterPerTick);
        }
        if (coatTicksPerLayer < 0 || coatTicksPerLayer > 1000) {
            throw new IllegalArgumentException("coatTicksPerLayer must be 0..1000, got " + coatTicksPerLayer);
        }
        if (cleanTicksPerLayer < 0 || cleanTicksPerLayer > 1000) {
            throw new IllegalArgumentException("cleanTicksPerLayer must be 0..1000, got " + cleanTicksPerLayer);
        }
        if (extraRinseWaterMb < 0 || extraRinseWaterMb > 1_000_000) {
            throw new IllegalArgumentException("extraRinseWaterMb must be 0..1000000, got " + extraRinseWaterMb);
        }

        // Calculate original effective per-layer ticks: originalLayerTicks * passes
        long originalEffectivePerLayer = (long) originalLayerTicks * passes;
        if (originalEffectivePerLayer > 72000) {
            throw new ArithmeticException("original effective per-layer ticks exceed 72000: " + originalEffectivePerLayer);
        }

        // Scanner ticks per layer = effective - coat - clean
        long coatCleanPerLayer = (long) coatTicksPerLayer + cleanTicksPerLayer;
        long scannerTicksPerLayerLong = originalEffectivePerLayer - coatCleanPerLayer;
        if (scannerTicksPerLayerLong < 1 || scannerTicksPerLayerLong > 72000) {
            throw new ArithmeticException("scannerTicksPerLayer must be 1..72000, got " + scannerTicksPerLayerLong);
        }
        int scannerTicksPerLayer = (int) scannerTicksPerLayerLong;

        // Total coat ticks = layers * coatTicksPerLayer
        long coatTicksLong = (long) layers * coatTicksPerLayer;
        if (coatTicksLong > 72000) {
            throw new ArithmeticException("coatTicks exceeds 72000: " + coatTicksLong);
        }
        int coatTicks = (int) coatTicksLong;

        // Total clean/dry ticks = layers * cleanTicksPerLayer
        long cleanDryTicksLong = (long) layers * cleanTicksPerLayer;
        if (cleanDryTicksLong > 72000) {
            throw new ArithmeticException("cleanDryTicks exceeds 72000: " + cleanDryTicksLong);
        }
        int cleanDryTicks = (int) cleanDryTicksLong;

        // Clean water Mb = layers * (effectiveLayerTicks * oldScanWaterPerTick - scannerTicksPerLayer * newScanWaterPerTick) + extraRinseWaterMb
        long originalWaterPerLayer = originalEffectivePerLayer * oldScanWaterPerTick;
        long scannerWaterPerLayer = scannerTicksPerLayerLong * newScanWaterPerTick;
        long cleanWaterPerLayer = originalWaterPerLayer - scannerWaterPerLayer;
        long cleanWaterMbLong = (long) layers * cleanWaterPerLayer + extraRinseWaterMb;
        if (cleanWaterMbLong < 0 || cleanWaterMbLong > Integer.MAX_VALUE) {
            throw new ArithmeticException("cleanWaterMb out of range [0, Integer.MAX_VALUE]: " + cleanWaterMbLong);
        }
        int cleanWaterMb = (int) cleanWaterMbLong;

        // Resist total = layers * resistPerLayer
        long resistTotalLong = (long) layers * resistPerLayer;
        if (resistTotalLong > 65536) {
            throw new ArithmeticException("resistTotal exceeds 65536: " + resistTotalLong);
        }
        int resistTotal = (int) resistTotalLong;

        // Construct and validate via canonical constructor
        return new LinearBudget(scannerTicksPerLayer, coatTicks, cleanDryTicks, cleanWaterMb, resistTotal);
    }

    /**
     * Canonical constructor validates result fields.
     */
    public LinearBudget {
        if (scannerTicksPerLayer < 1 || scannerTicksPerLayer > 72000) {
            throw new IllegalArgumentException("scannerTicksPerLayer must be 1..72000, got " + scannerTicksPerLayer);
        }
        if (coatTicks < 0 || coatTicks > 72000) {
            throw new IllegalArgumentException("coatTicks must be 0..72000, got " + coatTicks);
        }
        if (cleanDryTicks < 0 || cleanDryTicks > 72000) {
            throw new IllegalArgumentException("cleanDryTicks must be 0..72000, got " + cleanDryTicks);
        }
        if (cleanWaterMb < 0) {
            throw new IllegalArgumentException("cleanWaterMb must be >= 0, got " + cleanWaterMb);
        }
        if (resistTotal < 0 || resistTotal > 65536) {
            throw new IllegalArgumentException("resistTotal must be 0..65536, got " + resistTotal);
        }
    }

    /**
     * Returns the total scanner ticks across all layers.
     *
     * @return layers * scannerTicksPerLayer (computed on demand)
     */
    public long totalScannerTicks(int layers) {
        return (long) layers * scannerTicksPerLayer;
    }

    /**
     * Returns the original total ticks across all layers (scanner + coat + clean).
     *
     * @param layers        number of layers
     * @param originalLayerTicks original layer ticks
     * @param passes        exposure passes
     * @return layers * originalLayerTicks * passes
     */
    public static long originalTotalTicks(int layers, int originalLayerTicks, int passes) {
        return (long) layers * originalLayerTicks * passes;
    }
}