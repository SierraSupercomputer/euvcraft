package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * The part of a {@link FoupContents} that is safe to hand to anybody.
 *
 * <p>It carries the five numbers a client is allowed to know: how many wafers the
 * lot holds at most, how many it currently holds, the lowest layer any of those
 * wafers has reached, the wafer substrate grade, and the preparation generation.
 * The hidden per-wafer {@link Wafer#cumulativeRoll()} and {@link Wafer#flags()} are
 * not in this record and cannot be derived from it. Neither are the hidden G3
 * per-wafer preparation factors: generation 3 is public state, the factors are
 * server-only and never part of this record.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param capacity              maximum number of wafers in the lot, {@code >= 1}
 * @param waferCount            number of wafers in the lot, {@code 0..capacity}
 * @param lotLayer              lowest layer across the wafers, {@code 0} when the lot is empty
 * @param grade                 the wafer substrate grade, never null
 * @param preparationGeneration the preparation generation: 1 = legacy/unprepared, 2 = G2, 3 = G3
 */
public record FoupPublicView(int capacity, int waferCount, int lotLayer, WaferSubstrate grade, int preparationGeneration) {

    /** Layer reported for an empty lot. */
    public static final int EMPTY_LOT_LAYER = 0;

    /** The legacy preparation generation value (unprepared/G1). */
    public static final int PREPARATION_LEGACY = 1;

    /** The G2 prepared generation value. */
    public static final int PREPARATION_G2 = 2;

    /** The G3 prepared generation value. */
    public static final int PREPARATION_G3 = 3;

    /**
     * Creates a view with ordinary substrate grade and legacy preparation generation (1)
     * (legacy constructor for backward compatibility).
     *
     * @param capacity   maximum number of wafers in the lot
     * @param waferCount number of wafers in the lot
     * @param lotLayer   lowest layer across the wafers
     */
    public FoupPublicView(int capacity, int waferCount, int lotLayer) {
        this(capacity, waferCount, lotLayer, WaferSubstrate.ORDINARY, PREPARATION_LEGACY);
    }

    /**
     * Creates a view with the given grade and legacy preparation generation (1).
     *
     * @param capacity   maximum number of wafers in the lot
     * @param waferCount number of wafers in the lot
     * @param lotLayer   lowest layer across the wafers
     * @param grade      the wafer substrate grade, not null
     */
    public FoupPublicView(int capacity, int waferCount, int lotLayer, WaferSubstrate grade) {
        this(capacity, waferCount, lotLayer, grade, PREPARATION_LEGACY);
    }

    /**
     * Validates the five components.
     *
     * @throws IllegalArgumentException if the capacity is below one, the count is
     *                                  negative or above the capacity, the lot
     *                                  layer is negative, the grade is null, the
     *                                  preparation generation is not 1, 2 or 3, or
     *                                  the generation does not match the grade
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
        if (preparationGeneration == PREPARATION_G2
            && (waferCount == 0 || grade.generation() < WaferSubstrate.PURE.generation())) {
            throw new IllegalArgumentException("G2 preparation requires nonempty pure-or-better wafers");
        }
        if (preparationGeneration == PREPARATION_G3
            && (waferCount == 0 || grade.generation() < WaferSubstrate.ELECTRONIC_GRADE.generation())) {
            throw new IllegalArgumentException("G3 preparation requires nonempty electronic-grade-or-better wafers");
        }
        if (preparationGeneration < PREPARATION_LEGACY || preparationGeneration > PREPARATION_G3) {
            throw new IllegalArgumentException("preparationGeneration must be " + PREPARATION_LEGACY + ", "
                + PREPARATION_G2 + " or " + PREPARATION_G3 + ", got " + preparationGeneration);
        }
    }

    /**
     * Creates the view of a lot with no wafers in it.
     *
     * @param capacity maximum number of wafers in the lot
     * @return a view reporting zero wafers at layer {@link #EMPTY_LOT_LAYER} with ordinary grade and legacy preparation
     */
    public static FoupPublicView empty(int capacity) {
        return new FoupPublicView(capacity, 0, EMPTY_LOT_LAYER);
    }

    /**
     * Creates the view of a lot with no wafers in it and the given grade.
     *
     * @param capacity maximum number of wafers in the lot
     * @param grade    the wafer substrate grade, not null
     * @return a view reporting zero wafers at layer {@link #EMPTY_LOT_LAYER} with the given grade and legacy preparation
     */
    public static FoupPublicView empty(int capacity, WaferSubstrate grade) {
        Objects.requireNonNull(grade, "grade must not be null");
        return new FoupPublicView(capacity, 0, EMPTY_LOT_LAYER, grade);
    }

    /**
     * Creates the view of a lot with no wafers in it, the given grade and preparation generation.
     * An empty view cannot be prepared, so only generation 1 is accepted here.
     *
     * @param capacity              maximum number of wafers in the lot
     * @param grade                 the wafer substrate grade, not null
     * @param preparationGeneration the preparation generation: must be 1 for an empty lot
     * @return a view reporting zero wafers at layer {@link #EMPTY_LOT_LAYER} with the given grade and preparation
     */
    public static FoupPublicView empty(int capacity, WaferSubstrate grade, int preparationGeneration) {
        Objects.requireNonNull(grade, "grade must not be null");
        return new FoupPublicView(capacity, 0, EMPTY_LOT_LAYER, grade, preparationGeneration);
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

    /** Returns whether the lot is G2 prepared. */
    public boolean isG2Prepared() {
        return this.preparationGeneration == PREPARATION_G2;
    }

    /** Returns whether the lot is G3 prepared. */
    public boolean isG3Prepared() {
        return this.preparationGeneration == PREPARATION_G3;
    }
}
