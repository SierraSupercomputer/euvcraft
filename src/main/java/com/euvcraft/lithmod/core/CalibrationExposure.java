package com.euvcraft.lithmod.core;

import java.util.Objects;

/**
 * Accumulator for light-exposure calibration wear on an exposure tool.
 *
 * <p><strong>The model.</strong> Each <em>paid</em> processing tick is one calibration sample:
 * a clean wafer under the column burns accumulated {@linkplain State#dirtyTicks() dirty ticks}
 * back down (it clears them in one paid tick), and a dirty wafer adds one. After
 * {@code limitTicks} dirty paid ticks in a row the tool's calibration is {@linkplain
 * State#invalidated() invalidated} and the caller must refuse the <em>next</em> batch until an
 * operator recalibrates; the currently running batch keeps its frozen calibration, so
 * invalidation never rewrites work already in flight. Unpaid ticks change nothing: the
 * accumulator only ever advances on real, paid processing.</p>
 *
 * <p><strong>Discipline.</strong> {@link #recalibrated()} resets the grace counter (and only
 * the grace counter, never anything else) wherever it is called, inside a room or not, so
 * periodic recalibration keeps outside production possible. There are no level, time, thread
 * or item dependencies, no random rerolls and no identity mutation; every {@code advance}
 * returns a fresh immutable {@link State}.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class CalibrationExposure {

    private CalibrationExposure() {
    }

    /**
     * One calibration state: the accumulated dirty paid ticks and whether the tool's
     * calibration has been invalidated (a sticky latch until recalibration). {@code dirtyTicks}
     * must not be negative.
     */
    public record State(int dirtyTicks, boolean invalidated) {
        public State {
            if (dirtyTicks < 0) {
                throw new IllegalArgumentException("dirtyTicks must be >= 0, got " + dirtyTicks);
            }
        }
    }

    /**
     * Advances one tick of exposure work.
     *
     * <ul>
     *   <li>An unpaid tick ({@code paidTick == false}) changes nothing — the same state is
     *       returned unchanged, whatever {@code dirty} says.</li>
     *   <li>A clean paid tick clears the accumulated dirty ticks in full but preserves the
     *       invalidation latch; an already-invalidated tool stays invalidated until it is
     *       {@linkplain #recalibrated() recalibrated}.</li>
     *   <li>A dirty paid tick increments the dirty count, saturating at {@code limitTicks}, and
     *       latches invalidation the moment the count reaches the limit.</li>
     * </ul>
     *
     * @param current    the state before this tick, not null
     * @param dirty      whether this tick processed a dirty wafer
     * @param paidTick   whether this tick was a real, paid processing tick
     * @param limitTicks the dirty-tick budget before invalidation, at least one
     * @return the state after the tick (the same instance when nothing changed)
     * @throws IllegalArgumentException if {@code limitTicks} is below one
     * @throws NullPointerException     if {@code current} is null and the tick is paid
     */
    public static State advance(State current, boolean dirty, boolean paidTick, int limitTicks) {
        if (limitTicks < 1) {
            throw new IllegalArgumentException("limitTicks must be >= 1, got " + limitTicks);
        }
        if (!paidTick) {
            return current;
        }
        Objects.requireNonNull(current, "current");
        if (!dirty) {
            return new State(0, current.invalidated());
        }
        int next = (int) Math.min((long) current.dirtyTicks() + 1L, limitTicks);
        boolean latched = current.invalidated() || next >= limitTicks;
        return new State(next, latched);
    }

    /**
     * Returns the fresh state after an operator recalibration: zero dirty ticks and the
     * invalidation latch cleared. Recalibration works anywhere — inside a cleanroom or not —
     * and restarts the full dirty-tick grace budget.
     *
     * @return a state of zero dirty ticks and no invalidation
     */
    public static State recalibrated() {
        return new State(0, false);
    }
}
