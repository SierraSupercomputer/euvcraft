package com.euvcraft.lithmod.core;

import java.util.Random;

/**
 * Immutable pure record representing the beam alignment state.
 *
 * <p>State consists of three knob positions (x, y, focus) and three target positions
 * (targetX, targetY, targetFocus). Knobs are bounded to [-10, 10]; targets are bounded to [-5, 5].
 *
 * <p>All arithmetic is exact integer math. The quality metric and multiplier are derived
 * deterministically from the state. No hidden yields, timers, or side effects.
 *
 * <p>No Minecraft types are involved.
 */
public record BeamAlignment(
        /** X knob position, bounded to [-10, 10]. */
        int x,
        /** Y knob position, bounded to [-10, 10]. */
        int y,
        /** Focus knob position, bounded to [-10, 10]. */
        int focus,
        /** Target X position, bounded to [-5, 5]. */
        int targetX,
        /** Target Y position, bounded to [-5, 5]. */
        int targetY,
        /** Target focus position, bounded to [-5, 5]. */
        int targetFocus
) {

    /** Minimum allowed knob value. */
    public static final int KNOB_MIN = -10;
    /** Maximum allowed knob value. */
    public static final int KNOB_MAX = 10;
    /** Minimum allowed target value. */
    public static final int TARGET_MIN = -5;
    /** Maximum allowed target value. */
    public static final int TARGET_MAX = 5;

    /**
     * Creates a deterministic BeamAlignment from a seed.
     *
     * <p>The seed initializes a {@link Random} instance which generates the six state values.
     * Knobs are generated in range [-10, 10]; targets in range [-5, 5].
     *
     * @param seed the seed for deterministic generation
     * @return a new BeamAlignment with generated state
     */
    public static BeamAlignment create(long seed) {
        Random rng = new Random(seed);
        int x = rng.nextInt(KNOB_MAX - KNOB_MIN + 1) + KNOB_MIN;
        int y = rng.nextInt(KNOB_MAX - KNOB_MIN + 1) + KNOB_MIN;
        int focus = rng.nextInt(KNOB_MAX - KNOB_MIN + 1) + KNOB_MIN;
        int targetX = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        int targetY = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        int targetFocus = rng.nextInt(TARGET_MAX - TARGET_MIN + 1) + TARGET_MIN;
        return new BeamAlignment(x, y, focus, targetX, targetY, targetFocus);
    }

    /**
     * Returns a new BeamAlignment with the specified control applied.
     *
     * <p>Controls are encoded as:
     * <ul>
     *   <li>0: x-- (decrement x)</li>
     *   <li>1: x++ (increment x)</li>
     *   <li>2: y-- (decrement y)</li>
     *   <li>3: y++ (increment y)</li>
     *   <li>4: focus-- (decrement focus)</li>
     *   <li>5: focus++ (increment focus)</li>
     * </ul>
     *
     * <p>Knobs remain bounded to [-10, 10]. Invalid control values (outside 0..5) leave the state unchanged.
     *
     * @param control the control to apply (0-5)
     * @return a new BeamAlignment with the adjustment applied, or this if control is invalid
     */
    public BeamAlignment adjust(int control) {
        int nx = x;
        int ny = y;
        int nfocus = focus;

        switch (control) {
            case 0 -> nx = clamp(x - 1, KNOB_MIN, KNOB_MAX);
            case 1 -> nx = clamp(x + 1, KNOB_MIN, KNOB_MAX);
            case 2 -> ny = clamp(y - 1, KNOB_MIN, KNOB_MAX);
            case 3 -> ny = clamp(y + 1, KNOB_MIN, KNOB_MAX);
            case 4 -> nfocus = clamp(focus - 1, KNOB_MIN, KNOB_MAX);
            case 5 -> nfocus = clamp(focus + 1, KNOB_MIN, KNOB_MAX);
            default -> { return this; }
        }

        return new BeamAlignment(nx, ny, nfocus, targetX, targetY, targetFocus);
    }

    /**
     * Computes the X spot offset: 2*(x - targetX) + (y - targetY).
     *
     * @return the X spot offset
     */
    public int spotX() {
        return 2 * (x - targetX) + (y - targetY);
    }

    /**
     * Computes the Y spot offset: (x - targetX) - 2*(y - targetY).
     *
     * @return the Y spot offset
     */
    public int spotY() {
        return (x - targetX) - 2 * (y - targetY);
    }

    /**
     * Computes the focus error: absolute difference between focus and targetFocus.
     *
     * @return the focus error (non-negative)
     */
    public int focusError() {
        return Math.abs(focus - targetFocus);
    }

    /**
     * Computes the alignment quality score in [0, 100].
     *
     * <p>Formula: clamp(100 - 4*(|spotX| + |spotY|) - 10*focusError, 0, 100)
     *
     * @return the quality score, 0 to 100 inclusive
     */
    public int quality() {
        int spotSum = Math.abs(spotX()) + Math.abs(spotY());
        int raw = 100 - 4 * spotSum - 10 * focusError();
        return clamp(raw, 0, 100);
    }

    /**
     * Computes a multiplier in the range [min, max] based on quality.
     *
     * <p>Formula: min + (max - min) * quality / 100
     *
     * @param min the minimum multiplier (inclusive), must be finite
     * @param max the maximum multiplier (inclusive), must be finite and >= min
     * @return the interpolated multiplier
     * @throws IllegalArgumentException if min or max is not finite, or if max < min
     */
    public double multiplier(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("min and max must be finite");
        }
        if (max < min) {
            throw new IllegalArgumentException("max must be >= min");
        }
        return min + (max - min) * quality() / 100.0;
    }

    /** Clamps a value to the inclusive range [min, max]. */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}