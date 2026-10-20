package com.euvcraft.lithmod.core;

/**
 * The part of a {@link FoupContents} that is safe to hand to anybody.
 *
 * <p>It carries the four numbers a client is allowed to know: how many wafers the
 * lot holds at most, how many it currently holds, the lowest layer any of those
 * wafers has reached, and the wafer substrate grade. The hidden per-wafer
 * {@link Wafer#cumulativeRoll()} and {@link Wafer#flags()} are not in this record and
 * cannot be derived from it.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param capacity    maximum number of wafers in the lot, {@code >= 1}
 * @param waferCount  number of wafers in the lot, {@code 0..capacity}
 * @param lotLayer    lowest layer across the wafers, {@code 0} when the lot is empty
 * @param grade       the wafer substrate grade, never null
 */
public record FoupPublicView(int capacity, int waferCount, int lotLayer, WaferSubstrate grade) {

    /** Layer reported for an empty lot. */
    public static final int EMPTY_LOT_LAYER = 0;

    /**
     * Creates a view with ordinary substrate grade (legacy constructor for backward compatibility).
     *
     * @param capacity   maximum number of wafers in the lot
     * @param waferCount number of wafers in the lot
     * @param lotLayer   lowest layer across the wafers
     */
    public FoupPublicView(int capacity, int waferCount, int lotLayer) {
        this(capacity, waferCount, lotLayer, WaferSubstrate.ORDINARY);
    }

    /**
     * Validates the four components.
     *
     * @throws IllegalArgumentException if the capacity is below one, the count is
     *                                  negative or above the capacity, the lot
     *                                  layer is negative, or the grade is null
     */
    public FoupPublicView {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        if (waferCount < 0 || waferCount > capacity) {
            throw new IllegalArgumentException(
                "waferCount must be in [0, " + capacity + "], got " + waferCount);
        }
        if (lotLayer < Wafer.UNPROCESSED_LAYER) {
            throw new IllegalArgumentException("lotLayer must be >= 0, got " + lotLayer);
        }
        Objects.requireNonNull(grade, "grade must not be null");
    }

    /**
     * Creates the view of a lot with no wafers in it.
     *
     * @param capacity maximum number of wafers in the lot
     * @return a view reporting zero wafers at layer {@link #EMPTY_LOT_LAYER} with ordinary grade
     */
    public static FoupPublicView empty(int capacity) {
        return new FoupPublicView(capacity, 0, EMPTY_LOT_LAYER);
    }

    /**
     * Creates the view of a lot with no wafers in it and the given grade.
     *
     * @param capacity maximum number of wafers in the lot
     * @param grade    the wafer substrate grade, not null
     * @return a view reporting zero wafers at layer {@link #EMPTY_LOT_LAYER} with the given grade
     */
    public static FoupPublicView empty(int capacity, WaferSubstrate grade) {
        Objects.requireNonNull(grade, "grade must not be null");
        return new FoupPublicView(capacity, 0, EMPTY_LOT_LAYER, grade);
    }

    /**
     * Returns whether the lot holds no wafers.
     *
     * @return {@code true} if the wafer count is zero
     */
    public boolean isEmpty() {
        return this.waferCount == 0;
    }

    /**
     * Returns whether the lot is at capacity.
     *
     * @return {@code true} if no further wafer fits
     */
    public boolean isFull() {
        return this.waferCount >= this.capacity;
    }
}