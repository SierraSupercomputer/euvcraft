package com.euvcraft.lithmod.core;

/**
 * One wafer inside a sealed lot (FOUP).
 *
 * <p>A wafer is never a loose item: it only exists as an entry of a
 * {@link FoupContents} list, which is stored on the FOUP item. Three values make it
 * up:</p>
 *
 * <ul>
 *   <li>{@code layerIndex} - how many process steps the wafer has been through.
 *       {@link #UNPROCESSED_LAYER} means it has not been through any.</li>
 *   <li>{@code cumulativeRoll} - the hidden yield value, the product of every step
 *       roll drawn for this wafer so far. It is uniform over {@code (0, 1]}, the same
 *       range {@link Rng#nextDouble()} produces, so a wafer with no rolls yet holds
 *       exactly {@code 1}.</li>
 *   <li>{@code flags} - reserved bit field, {@link #NO_FLAGS} for now.</li>
 * </ul>
 *
 * <p>{@code cumulativeRoll} and {@code flags} are hidden data: they are stored in
 * the lot but never sent to a client, never shown in a tooltip and never printed by
 * {@link #toString()}.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 */
public record Wafer(int layerIndex, float cumulativeRoll, int flags) {

    /** Layer index of a wafer that has not been through any process step. */
    public static final int UNPROCESSED_LAYER = 0;

    /** Reserved flags value; no flag is defined yet. */
    public static final int NO_FLAGS = 0;

    /**
     * Cumulative roll of a wafer no roll has been applied to yet: the product of an
     * empty set of step rolls is {@code 1}.
     */
    public static final float NO_ROLLS = 1.0f;

    /**
     * Validates the three components.
     *
     * @throws IllegalArgumentException if {@code layerIndex} is negative or
     *                                  {@code cumulativeRoll} is not a finite value
     *                                  in {@code (0, 1]}
     */
    public Wafer {
        if (layerIndex < UNPROCESSED_LAYER) {
            throw new IllegalArgumentException("layerIndex must be >= 0, got " + layerIndex);
        }
        if (!Float.isFinite(cumulativeRoll) || cumulativeRoll <= 0.0f || cumulativeRoll > 1.0f) {
            throw new IllegalArgumentException(
                "cumulativeRoll must be in (0, 1], got " + cumulativeRoll);
        }
    }

    /**
     * Creates a wafer that has not been through any process step.
     *
     * @return a wafer at {@link #UNPROCESSED_LAYER} with {@link #NO_ROLLS} and no flags
     */
    public static Wafer unprocessed() {
        return new Wafer(UNPROCESSED_LAYER, NO_ROLLS, NO_FLAGS);
    }

    /**
     * Returns whether any process step has been applied to this wafer.
     *
     * @return {@code true} if the wafer is still at {@link #UNPROCESSED_LAYER}
     */
    public boolean isUnprocessed() {
        return this.layerIndex == UNPROCESSED_LAYER;
    }

    /**
     * Returns a copy of this wafer at another layer, keeping the cumulative roll and
     * the flags.
     *
     * @param newLayerIndex layer the wafer has reached, {@code >= 0}
     * @return the updated wafer
     */
    public Wafer atLayer(int newLayerIndex) {
        return new Wafer(newLayerIndex, this.cumulativeRoll, this.flags);
    }

    /**
     * Prints the wafer without its hidden values. Rolls and flags never appear here
     * because a wafer can end up in a log line or an error message.
     *
     * @return a description with the lot layer only
     */
    @Override
    public String toString() {
        return "Wafer[layer=" + this.layerIndex + "]";
    }
}