package com.euvcraft.lithmod.core.t7;

import java.util.Arrays;
import java.util.Objects;

/**
 * Fair integer allocation of optional central-vacuum capacity over independent
 * chambers.
 *
 * <p>The allocator is a pure function. It never touches a chamber, never creates
 * service and never invents a shared pressure: it only splits the plant's
 * capacity into integer service units that the caller passes to each chamber's own
 * {@link VacuumChamberState#tick(VacuumPumpSpec, VacuumMode, int)}. A chamber that
 * is not in the request array, for example because its isolation valve is closed
 * or its service conduit is disconnected, receives nothing and cannot affect the
 * others.</p>
 *
 * <p><strong>Fairness.</strong> When capacity is scarce, each chamber first gets
 * the floor of its proportional share. The remaining whole units go to the largest
 * fractional remainders, with ties broken by the lower array index. This is the
 * largest-remainder method, so no chamber is starved by index order while another
 * gets a windfall.</p>
 *
 * <p><strong>Conservation.</strong> The allocation always satisfies
 * {@code 0 <= allocated[i] <= demand[i]} and
 * {@code sum(allocated) == min(capacity, sum(demand))}. It never hands out more
 * than the plant has and never generates vacuum for an absent request.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class CentralVacuumAllocator {

    private CentralVacuumAllocator() {
    }

    /**
     * One allocation result. The arrays are immutable copies.
     *
     * @param allocatedMicroTorrPerTick service granted per request, in request order
     */
    public record Allocation(int[] allocatedMicroTorrPerTick) {

        /**
         * Copies and validates the allocation.
         *
         * @throws NullPointerException     if the array is null
         * @throws IllegalArgumentException if any value is negative
         */
        public Allocation {
            Objects.requireNonNull(allocatedMicroTorrPerTick, "allocatedMicroTorrPerTick must not be null");
            for (int value : allocatedMicroTorrPerTick) {
                if (value < 0) {
                    throw new IllegalArgumentException("an allocation cannot be negative, got " + value);
                }
            }
            allocatedMicroTorrPerTick = allocatedMicroTorrPerTick.clone();
        }

        /** Returns a copy of the per-request allocation. */
        @Override
        public int[] allocatedMicroTorrPerTick() {
            return this.allocatedMicroTorrPerTick.clone();
        }

        /** Returns the number of requests. */
        public int size() {
            return this.allocatedMicroTorrPerTick.length;
        }

        /** Returns the conserved total allocated. */
        public int totalAllocatedMicroTorrPerTick() {
            long total = 0;
            for (int value : this.allocatedMicroTorrPerTick) {
                total += value;
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }

        /** Returns the allocation at one request slot. */
        public int at(int index) {
            return this.allocatedMicroTorrPerTick[index];
        }

        /**
         * Compares allocations by contents.
         *
         * @param other object to compare
         * @return whether every slot is equal
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Allocation allocation
                && Arrays.equals(this.allocatedMicroTorrPerTick, allocation.allocatedMicroTorrPerTick);
        }

        /** Returns a content-based hash. */
        @Override
        public int hashCode() {
            return Arrays.hashCode(this.allocatedMicroTorrPerTick);
        }

        /** Prints only the conserved total and request count, never private state. */
        @Override
        public String toString() {
            return "Allocation[requests=" + this.allocatedMicroTorrPerTick.length
                + ", total=" + totalAllocatedMicroTorrPerTick() + "]";
        }
    }

    /**
     * Allocates central capacity fairly over the requested service.
     *
     * @param capacityMicroTorrPerTick plant capacity available this tick, {@code >= 0}
     * @param demandMicroTorrPerTick   per-chamber requests, all {@code >= 0}, not null
     * @return the conserved per-request allocation
     * @throws NullPointerException     if the demand array is null
     * @throws IllegalArgumentException if the capacity or any demand is negative
     */
    public static Allocation allocate(int capacityMicroTorrPerTick, int[] demandMicroTorrPerTick) {
        Objects.requireNonNull(demandMicroTorrPerTick, "demandMicroTorrPerTick must not be null");
        if (capacityMicroTorrPerTick < 0) {
            throw new IllegalArgumentException(
                "capacityMicroTorrPerTick must be >= 0, got " + capacityMicroTorrPerTick);
        }
        long totalDemand = 0;
        for (int demand : demandMicroTorrPerTick) {
            if (demand < 0) {
                throw new IllegalArgumentException("a demand cannot be negative, got " + demand);
            }
            totalDemand += demand;
        }

        int[] allocated = new int[demandMicroTorrPerTick.length];
        if (totalDemand == 0 || capacityMicroTorrPerTick == 0) {
            return new Allocation(allocated);
        }
        if (capacityMicroTorrPerTick >= totalDemand) {
            System.arraycopy(demandMicroTorrPerTick, 0, allocated, 0, allocated.length);
            return new Allocation(allocated);
        }

        long[] remainders = new long[demandMicroTorrPerTick.length];
        long floorSum = 0;
        for (int i = 0; i < demandMicroTorrPerTick.length; i++) {
            long scaled = (long) capacityMicroTorrPerTick * demandMicroTorrPerTick[i];
            allocated[i] = (int) (scaled / totalDemand);
            remainders[i] = scaled % totalDemand;
            floorSum += allocated[i];
        }

        int leftover = (int) (capacityMicroTorrPerTick - floorSum);
        if (leftover > 0) {
            Integer[] order = new Integer[demandMicroTorrPerTick.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (left, right) -> {
                int byRemainder = Long.compare(remainders[right], remainders[left]);
                if (byRemainder != 0) {
                    return byRemainder;
                }
                return Integer.compare(left, right);
            });
            for (int i = 0; i < leftover; i++) {
                int slot = order[i];
                if (allocated[slot] < demandMicroTorrPerTick[slot]) {
                    allocated[slot]++;
                }
            }
        }
        return new Allocation(allocated);
    }
}
