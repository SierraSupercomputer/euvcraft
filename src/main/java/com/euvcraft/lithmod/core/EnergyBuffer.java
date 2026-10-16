package com.euvcraft.lithmod.core;

/**
 * Simple energy buffer interface for machine block entities.
 * Implements the standard FE (Forge Energy) capability pattern:
 * - Accept energy from every side
 * - Extract none (machines don't output energy)
 */
public interface EnergyBuffer {
    /**
     * Returns the current stored energy.
     */
    long stored();

    /**
     * Returns the maximum energy capacity.
     */
    long capacity();

    /**
     * Returns the maximum energy that can be received per tick.
     */
    long maxReceive();

    /**
     * Inserts energy into the buffer, returns the amount actually inserted.
     */
    long insert(long amount, boolean simulate);

    /**
     * Extracts energy from the buffer, returns the amount actually extracted.
     * Machines don't normally extract, but this exists for completeness.
     */
    long extract(long amount, boolean simulate);

    /**
     * Simple implementation for single-block machines.
     */
    final class Impl implements EnergyBuffer {
        private long stored;
        private final long capacity;
        private final long maxReceive;

        public Impl(long capacity, long maxReceive) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
            this.stored = 0;
        }

        @Override
        public long stored() {
            return stored;
        }

        @Override
        public long capacity() {
            return capacity;
        }

        @Override
        public long maxReceive() {
            return maxReceive;
        }

        @Override
        public long insert(long amount, boolean simulate) {
            if (amount <= 0) return 0;
            long canInsert = Math.min(maxReceive, capacity - stored);
            long inserted = Math.min(amount, canInsert);
            if (!simulate) {
                stored += inserted;
            }
            return inserted;
        }

        @Override
        public long extract(long amount, boolean simulate) {
            if (amount <= 0) return 0;
            long extracted = Math.min(amount, stored);
            if (!simulate) {
                stored -= extracted;
            }
            return extracted;
        }

        public void setStored(long stored) {
            this.stored = Math.max(0, Math.min(stored, capacity));
        }
    }
}