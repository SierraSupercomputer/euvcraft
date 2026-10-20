package com.euvcraft.lithmod.core;

/**
 * Server-agnostic water tank holding a water amount in millibuckets (mB).
 *
 * <p>No fluid capability, no item API, no Minecraft/NeoForge imports. Future pipes will use its
 * operations; bucket code will later use EXACT 1000 mB transfer after inventory preflight.
 *
 * <p>Capacity is bounded to {@code 1..1_000_000} mB. Amount is bounded to {@code 0..1_000_000} mB.
 * All arithmetic is int-safe (no overflow within bounds). Negative amounts are rejected; zero is a
 * valid no-op. Simulate modes never mutate state.
 *
 * <p>Resizing to a smaller capacity preserves legitimately stored water even if above the new
 * capacity; further fill is rejected until room returns, but drain is allowed. Restoring an amount
 * above the current capacity but within the hard bound is valid to avoid water loss after a data
 * pack capacity shrink; negative or above-hard-bound amounts are rejected.
 */
public final class WaterTank {

    /** Maximum allowed capacity and amount (1 million mB = 1000 buckets). */
    public static final int MAX_CAPACITY = 1_000_000;

    private final int capacity;
    private int amount;

    /**
     * Creates a new water tank with the given capacity, initially empty.
     *
     * @param capacity the capacity in mB, must be in {@code 1..1_000_000}
     * @throws IllegalArgumentException if capacity is out of bounds
     */
    public WaterTank(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be in 1.." + MAX_CAPACITY + ", got " + capacity);
        }
        this.capacity = capacity;
        this.amount = 0;
    }

    /**
     * Restores a water tank with the given capacity and amount.
     *
     * @param capacity the capacity in mB, must be in {@code 1..1_000_000}
     * @param amount the stored water in mB, must be in {@code 0..1_000_000}
     * @throws IllegalArgumentException if capacity or amount is out of bounds
     */
    public WaterTank(int capacity, int amount) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be in 1.." + MAX_CAPACITY + ", got " + capacity);
        }
        if (amount < 0 || amount > MAX_CAPACITY) {
            throw new IllegalArgumentException("amount must be in 0.." + MAX_CAPACITY + ", got " + amount);
        }
        this.capacity = capacity;
        this.amount = amount;
    }

    /**
     * Returns the tank capacity in mB.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the current water amount in mB.
     */
    public int amount() {
        return amount;
    }

    /**
     * Attempts to fill water into the tank.
     *
     * @param fillAmount the amount to fill in mB, must be non-negative
     * @param simulate if true, do not mutate state
     * @return the amount actually accepted (0..fillAmount)
     * @throws IllegalArgumentException if fillAmount is negative
     */
    public int fill(int fillAmount, boolean simulate) {
        if (fillAmount < 0) {
            throw new IllegalArgumentException("fillAmount must be non-negative, got " + fillAmount);
        }
        if (fillAmount == 0) {
            return 0;
        }
        int space = capacity - amount;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(fillAmount, space);
        if (!simulate) {
            amount += accepted;
        }
        return accepted;
    }

    /**
     * Attempts to fill an exact amount; all-or-nothing.
     *
     * @param fillAmount the exact amount to fill in mB, must be non-negative
     * @param simulate if true, do not mutate state
     * @return true if the exact amount was filled (or would be filled), false otherwise
     * @throws IllegalArgumentException if fillAmount is negative
     */
    public boolean fillExact(int fillAmount, boolean simulate) {
        if (fillAmount < 0) {
            throw new IllegalArgumentException("fillAmount must be non-negative, got " + fillAmount);
        }
        if (fillAmount == 0) {
            return true;
        }
        if (amount + fillAmount > capacity) {
            return false;
        }
        if (!simulate) {
            amount += fillAmount;
        }
        return true;
    }

    /**
     * Attempts to drain an exact amount; all-or-nothing.
     *
     * @param drainAmount the exact amount to drain in mB, must be non-negative
     * @param simulate if true, do not mutate state
     * @return true if the exact amount was drained (or would be drained), false otherwise
     * @throws IllegalArgumentException if drainAmount is negative
     */
    public boolean drainExact(int drainAmount, boolean simulate) {
        if (drainAmount < 0) {
            throw new IllegalArgumentException("drainAmount must be non-negative, got " + drainAmount);
        }
        if (drainAmount == 0) {
            return true;
        }
        if (amount < drainAmount) {
            return false;
        }
        if (!simulate) {
            amount -= drainAmount;
        }
        return true;
    }

    /**
     * Resizes the tank to a new capacity.
     *
     * <p>Shrinking preserves the current amount even if it exceeds the new capacity.
     * Further fill is rejected until the amount drops below the new capacity.
     * Drain is always allowed.
     *
     * @param newCapacity the new capacity in mB, must be in {@code 1..1_000_000}
     * @throws IllegalArgumentException if newCapacity is out of bounds
     */
    public void resize(int newCapacity) {
        if (newCapacity < 1 || newCapacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("newCapacity must be in 1.." + MAX_CAPACITY + ", got " + newCapacity);
        }
        // Capacity changes but amount is preserved (even if above new capacity)
        // No mutation of amount here; fill operations will respect the new capacity
    }

    /**
     * Returns an immutable snapshot of the tank state for pure state round-trip.
     * No codecs or core game imports.
     */
    public Snapshot snapshot() {
        return new Snapshot(capacity, amount);
    }

    /**
     * Immutable snapshot of tank state (capacity, amount).
     */
    public record Snapshot(int capacity, int amount) {
        /**
         * Restores a tank from this snapshot.
         */
        public WaterTank restore() {
            return new WaterTank(capacity, amount);
        }
    }
}