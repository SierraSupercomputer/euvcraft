package com.euvcraft.lithmod.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Helper methods for FOUP disposal operations.
 * <p>
 * These are pure functions with no Minecraft or NeoForge dependencies.
 */
public final class FoupDisposal {

    private FoupDisposal() {
    }

    /**
     * Creates an empty carrier from a source lot.
     * <p>
     * The returned lot preserves the source capacity, has the given fresh UUID,
     * no wafers, ordinary substrate, legacy preparation generation (1), no run spec,
     * no cleanroom exposure, and zero room yield permille.
     *
     * @param source  the source lot to derive capacity from, must not be null or empty
     * @param freshId the new lot ID, must not be null and must differ from source.lotId()
     * @return an empty lot with the fresh ID and source capacity
     * @throws NullPointerException     if {@code source} or {@code freshId} is null
     * @throws IllegalArgumentException if {@code source} is empty, or if {@code freshId}
     *                                  equals {@code source.lotId()}
     */
    public static FoupContents emptyCarrier(FoupContents source, UUID freshId) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(freshId, "freshId must not be null");

        if (source.isEmpty()) {
            throw new IllegalArgumentException("source lot must not be empty");
        }
        if (freshId.equals(source.lotId())) {
            throw new IllegalArgumentException("freshId must differ from source lotId");
        }

        return FoupContents.empty(freshId, source.capacity());
    }

    /**
     * Computes the total scrap count from a lot.
     * <p>
     * The scrap count is {@code source.size() * scrapPerWafer}.
     *
     * @param source        the lot to scrap, must not be null or empty
     * @param scrapPerWafer scrap per wafer, must be in {@code [1, 64]} and the
     *                      multiplication must not overflow {@code int}
     * @return the total scrap count
     * @throws NullPointerException     if {@code source} is null
     * @throws IllegalArgumentException if {@code source} is empty, {@code scrapPerWafer}
     *                                  is outside {@code [1, 64]}, or the multiplication
     *                                  overflows
     */
    public static int scrapCount(FoupContents source, int scrapPerWafer) {
        Objects.requireNonNull(source, "source must not be null");

        if (source.isEmpty()) {
            throw new IllegalArgumentException("source lot must not be empty");
        }
        if (scrapPerWafer < 1 || scrapPerWafer > 64) {
            throw new IllegalArgumentException("scrapPerWafer must be in [1, 64], got " + scrapPerWafer);
        }

        int waferCount = source.size();
        long product = (long) waferCount * scrapPerWafer;
        if (product > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scrap count overflow: " + waferCount + " * " + scrapPerWafer);
        }
        return (int) product;
    }
}