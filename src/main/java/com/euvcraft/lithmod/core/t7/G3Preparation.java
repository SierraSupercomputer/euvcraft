package com.euvcraft.lithmod.core.t7;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, hidden evidence of a completed G3 wafer-preparation stage.
 *
 * <p>A G3 preparation result belongs to exactly one sealed lot and is committed
 * once, when the preparation stage completes. It is deliberately separate from
 * {@link com.euvcraft.lithmod.core.Wafer#cumulativeRoll()} and from the
 * lithography roll/exposure accounting: the packaging integration later applies
 * each wafer's preparation factor exactly once.</p>
 *
 * <p><strong>Units.</strong> Every factor and the cleanroom mean are integer
 * permille values in {@code [1, 1000]}, where 1000 means no preparation loss and
 * 1 means the maximum modelled loss. The factor is a multiplier applied to the
 * wafer's yield, not a subtraction.</p>
 *
 * <p><strong>States.</strong> A preparation value is one of:</p>
 * <ul>
 *   <li>{@link #none()} — legacy/unprepared or G2; no G3 evidence;</li>
 *   <li>an exact result — a non-empty factor list, one factor per wafer in slot
 *       order, committed once;</li>
 *   <li>{@link #redacted()} — a display-only marker used by the public network
 *       projection. It says "a G3 prepared lot exists" but contains no factor
 *       values and therefore cannot recover the private result.</li>
 * </ul>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public record G3Preparation(int cleanroomMeanPermille, List<Integer> waferFactorsPermille, boolean redacted) {

    /** Largest number of wafers a single lot may carry (mirrors the configured FOUP bound). */
    public static final int MAX_WAFERS = 64;

    /** Factor and mean permille bound. */
    public static final int MIN_PERMILLE = 1;
    public static final int MAX_PERMILLE = 1000;

    /**
     * Validates the representation.
     *
     * @throws NullPointerException     if the factor list or an entry is null
     * @throws IllegalArgumentException if the mean is outside {@code [0, 1000]},
     *                                  the list holds more than {@link #MAX_WAFERS}
     *                                  entries, a factor is outside
     *                                  {@code [1, 1000]}, a redacted value carries
     *                                  factors or a mean, or an empty non-redacted
     *                                  value carries a mean
     */
    public G3Preparation {
        Objects.requireNonNull(waferFactorsPermille, "waferFactorsPermille must not be null");
        waferFactorsPermille = List.copyOf(waferFactorsPermille);
        if (cleanroomMeanPermille < 0 || cleanroomMeanPermille > MAX_PERMILLE) {
            throw new IllegalArgumentException(
                "cleanroomMeanPermille must be in [0, " + MAX_PERMILLE + "], got " + cleanroomMeanPermille);
        }
        if (waferFactorsPermille.size() > MAX_WAFERS) {
            throw new IllegalArgumentException(
                "a G3 preparation cannot hold more than " + MAX_WAFERS + " factors, got " + waferFactorsPermille.size());
        }
        for (int factor : waferFactorsPermille) {
            if (factor < MIN_PERMILLE || factor > MAX_PERMILLE) {
                throw new IllegalArgumentException(
                    "a G3 wafer factor must be in [" + MIN_PERMILLE + ", " + MAX_PERMILLE + "], got " + factor);
            }
        }
        if (redacted) {
            if (!waferFactorsPermille.isEmpty() || cleanroomMeanPermille != 0) {
                throw new IllegalArgumentException("a redacted G3 preparation cannot carry factors or a mean");
            }
        } else if (waferFactorsPermille.isEmpty() && cleanroomMeanPermille != 0) {
            throw new IllegalArgumentException("an unprepared G3 value cannot carry a mean");
        } else if (!waferFactorsPermille.isEmpty() && cleanroomMeanPermille < MIN_PERMILLE) {
            throw new IllegalArgumentException("a prepared G3 value requires a mean in [1, 1000]");
        }
    }

    /** Returns the empty/legacy value. */
    public static G3Preparation none() {
        return new G3Preparation(0, List.of(), false);
    }

    /**
     * Returns the display-only value carried by a public network projection.
     * It never contains factor values.
     */
    public static G3Preparation redacted() {
        return new G3Preparation(0, List.of(), true);
    }

    /**
     * Creates an exact preparation result from already-computed factors.
     *
     * <p>The caller supplies final factors; this factory does not roll, reorder
     * or normalise them. It is intended for deterministic data loading and for
     * tests, not as a way to reprepare an existing lot.</p>
     *
     * @param cleanroomMeanPermille paid cleanroom exposure mean in permille
     * @param factorsPermille       one factor per wafer, in slot order
     * @return the exact preparation value
     * @throws IllegalArgumentException if the mean is outside {@code [1, 1000]},
     *                                  the list is empty or holds more than
     *                                  {@link #MAX_WAFERS} entries, or a factor is
     *                                  outside {@code [1, 1000]}
     */
    public static G3Preparation of(int cleanroomMeanPermille, List<Integer> factorsPermille) {
        Objects.requireNonNull(factorsPermille, "factorsPermille must not be null");
        if (cleanroomMeanPermille < MIN_PERMILLE || cleanroomMeanPermille > MAX_PERMILLE) {
            throw new IllegalArgumentException(
                "cleanroomMeanPermille must be in [" + MIN_PERMILLE + ", " + MAX_PERMILLE + "], got "
                    + cleanroomMeanPermille);
        }
        if (factorsPermille.isEmpty()) {
            throw new IllegalArgumentException("an exact G3 preparation requires at least one factor");
        }
        return new G3Preparation(cleanroomMeanPermille, factorsPermille, false);
    }

    /**
     * Commits the deterministic per-wafer factors from the paid cleanroom mean and
     * caller-supplied per-wafer draws.
     *
     * <p>Each supplied draw is a permille fraction in {@code [0, 1000]}: 1000
     * carries the cleanroom mean through unchanged and 0 represents a fully lost
     * preparation result. The integer formula is</p>
     *
     * <pre>{@code factor = clamp(round(mean * draw / 1000), 1, 1000)}</pre>
     *
     * <p>where {@code round} is integer half-up rounding. The result is a pure
     * function of the arguments, so the same paid mean and draws always commit the
     * same factors; there is no hidden random source. The caller is responsible
     * for drawing {@code drawsPermille} once, before the first call.</p>
     *
     * @param cleanroomMeanPermille paid cleanroom exposure mean in permille
     * @param drawsPermille         per-wafer draw in {@code [0, 1000]}, in slot order
     * @return the exact preparation value
     * @throws NullPointerException     if {@code drawsPermille} is null
     * @throws IllegalArgumentException if the mean is outside {@code [1, 1000]}, the
     *                                  number of draws is outside {@code [1, MAX_WAFERS]},
     *                                  or a draw is outside {@code [0, 1000]}
     */
    public static G3Preparation commit(int cleanroomMeanPermille, int[] drawsPermille) {
        Objects.requireNonNull(drawsPermille, "drawsPermille must not be null");
        if (cleanroomMeanPermille < MIN_PERMILLE || cleanroomMeanPermille > MAX_PERMILLE) {
            throw new IllegalArgumentException(
                "cleanroomMeanPermille must be in [" + MIN_PERMILLE + ", " + MAX_PERMILLE + "], got "
                    + cleanroomMeanPermille);
        }
        if (drawsPermille.length < 1 || drawsPermille.length > MAX_WAFERS) {
            throw new IllegalArgumentException(
                "drawsPermille must hold 1.." + MAX_WAFERS + " entries, got " + drawsPermille.length);
        }
        List<Integer> factors = new ArrayList<>(drawsPermille.length);
        for (int draw : drawsPermille) {
            if (draw < 0 || draw > MAX_PERMILLE) {
                throw new IllegalArgumentException("a G3 draw must be in [0, " + MAX_PERMILLE + "], got " + draw);
            }
            long rounded = ((long) cleanroomMeanPermille * draw + MAX_PERMILLE / 2) / MAX_PERMILLE;
            int factor = (int) Math.max(MIN_PERMILLE, Math.min(MAX_PERMILLE, rounded));
            factors.add(factor);
        }
        return of(cleanroomMeanPermille, factors);
    }

    /**
     * Rebuilds a value from stored fields. The stored form is validated like every
     * other construction, so malformed saved data fails closed.
     *
     * @param cleanroomMeanPermille stored mean in permille
     * @param factorsPermille       stored factors, in slot order
     * @param redacted              whether this is a display-only value
     * @return the validated value
     */
    public static G3Preparation fromStored(int cleanroomMeanPermille, List<Integer> factorsPermille, boolean redacted) {
        return new G3Preparation(cleanroomMeanPermille, factorsPermille, redacted);
    }

    /** Returns whether this value represents a G3 preparation result (exact or redacted). */
    public boolean isPrepared() {
        return this.redacted || !this.waferFactorsPermille.isEmpty();
    }

    /** Returns whether this value carries the hidden per-wafer factor values. */
    public boolean hasFactors() {
        return !this.redacted && !this.waferFactorsPermille.isEmpty();
    }

    /** Returns the number of stored factors; zero for none and redacted values. */
    public int factorCount() {
        return this.waferFactorsPermille.size();
    }

    /**
     * Returns one hidden factor by slot.
     *
     * @param index wafer slot
     * @return the factor in permille, {@code 1..1000}
     * @throws IllegalStateException    if this value carries no factors
     * @throws IndexOutOfBoundsException if the slot is not present
     */
    public int factorPermilleAt(int index) {
        if (this.redacted) {
            throw new IllegalStateException("a redacted G3 preparation carries no factors");
        }
        return this.waferFactorsPermille.get(index);
    }

    /**
     * Returns a copy without the factor at the given slot. Used when a wafer is
     * physically removed; it never recomputes or upgrades the remaining factors.
     *
     * @param index slot to drop
     * @return the remaining exact evidence, or {@link #none()} when none remains
     */
    public G3Preparation withoutFactorAt(int index) {
        if (this.redacted) {
            return this;
        }
        if (this.waferFactorsPermille.isEmpty()) {
            return this;
        }
        List<Integer> remaining = new ArrayList<>(this.waferFactorsPermille);
        remaining.remove(index);
        if (remaining.isEmpty()) {
            return none();
        }
        return of(this.cleanroomMeanPermille, remaining);
    }

    /** Returns a defensive copy of the factor array for transport codecs. */
    public int[] factorArray() {
        int[] factors = new int[this.waferFactorsPermille.size()];
        for (int i = 0; i < factors.length; i++) {
            factors[i] = this.waferFactorsPermille.get(i);
        }
        return factors;
    }

    /**
     * Prints the evidence without its hidden factors.
     *
     * @return a description with only state and count
     */
    @Override
    public String toString() {
        if (this.redacted) {
            return "G3Preparation[redacted]";
        }
        if (this.waferFactorsPermille.isEmpty()) {
            return "G3Preparation[none]";
        }
        return "G3Preparation[factors=" + this.waferFactorsPermille.size()
            + ", mean=" + this.cleanroomMeanPermille + "]";
    }
}
