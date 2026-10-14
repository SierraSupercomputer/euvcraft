package com.euvcraft.lithmod.core;

/**
 * Minimal random source contract used by the fab yield engine.
 *
 * <p>The mod never uses a shared/global random source for gameplay results. Every
 * roll is drawn from an {@code Rng} that was seeded from an explicit, reproducible
 * context (lot, wafer, step) plus a server-only salt, so a roll can be replayed but
 * cannot be guessed by a client.</p>
 */
public interface Rng {

    /**
     * Returns the next value, uniform over the half-open range {@code (0, 1]}.
     *
     * <p>The range is chosen so that the result can be used directly as a
     * "cumulative roll" without an extra epsilon adjustment: a value of exactly
     * {@code 0} is impossible, which matters because a wafer passes when its
     * cumulative roll is strictly greater than {@code 1 - effectiveYield}.</p>
     *
     * @return a value in {@code (0, 1]}; never {@code 0}, never above {@code 1}
     */
    double nextDouble();
}