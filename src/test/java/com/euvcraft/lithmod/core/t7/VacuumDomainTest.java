package com.euvcraft.lithmod.core.t7;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the T7 chamber vacuum model and the optional central allocator.
 * All rates are microtorr per tick; no world, item or lot is touched.
 */
class VacuumDomainTest {

    /** Local pump 30, leak 10, process load 40, operating at 10 µTorr, reserve 30 recharge 5. */
    private static VacuumPumpSpec spec() {
        return new VacuumPumpSpec(30, 10, 40, 10, 30, 5);
    }

    private static VacuumPumpSpec noReserveSpec() {
        return new VacuumPumpSpec(30, 10, 40, 10, 0, 0);
    }

    @Nested
    @DisplayName("configuration and bounds")
    class Bounds {

        @Test
        @DisplayName("rejects non-positive pumping and negative loads")
        void rejectsBadRates() {
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(0, 10, 40, 10, 30, 5));
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(30, -1, 40, 10, 30, 5));
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(30, 10, -1, 10, 30, 5));
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(30, 10, 40, 0, 30, 5));
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(30, 10, 40, 10, -1, 0));
            assertThrows(IllegalArgumentException.class, () -> new VacuumPumpSpec(30, 10, 40, 10, 30, 31));
        }

        @Test
        @DisplayName("chamber pressure is bounded and the reserve cannot be negative")
        void rejectsOutOfBoundsState() {
            assertThrows(IllegalArgumentException.class, () -> new VacuumChamberState(-1, 0));
            assertThrows(IllegalArgumentException.class,
                () -> new VacuumChamberState(VacuumChamberState.MAX_PRESSURE_MICROTORR + 1, 0));
            assertThrows(IllegalArgumentException.class, () -> new VacuumChamberState(0, -1));
            assertEquals(VacuumChamberState.MAX_PRESSURE_MICROTORR,
                new VacuumChamberState(VacuumChamberState.MAX_PRESSURE_MICROTORR, 0).pressureMicroTorr());
        }

        @Test
        @DisplayName("a hostile central grant is rejected instead of creating vacuum")
        void rejectsNegativeCentralService() {
            assertThrows(IllegalArgumentException.class,
                () -> VacuumChamberState.evacuated().tick(spec(), VacuumMode.IDLE, -1));
        }
    }

    @Nested
    @DisplayName("independent chamber tick")
    class Tick {

        @Test
        @DisplayName("idle leak is held at the operating pressure and recharges the reserve")
        void idleHoldsAndRecharges() {
            VacuumChamberState state = VacuumChamberState.at(10, 0);
            VacuumTickResult result = state.tick(spec(), VacuumMode.IDLE, 0);

            assertEquals(10, result.state().pressureMicroTorr());
            assertEquals(5, result.state().reserveMicroTorr());
            assertEquals(10, result.demandMicroTorrPerTick());
            assertEquals(10, result.suppliedMicroTorrPerTick());
            assertTrue(result.processingAllowed());
            assertFalse(result.paused());
            assertEquals(0, result.shortfallMicroTorrPerTick());
        }

        @Test
        @DisplayName("a processing load with no extra service pauses and raises pressure instead of scrapping")
        void processLossPauses() {
            VacuumChamberState state = VacuumChamberState.at(10, 0);
            VacuumTickResult result = state.tick(spec(), VacuumMode.PROCESS, 0);

            assertEquals(30, result.state().pressureMicroTorr());
            assertEquals(50, result.demandMicroTorrPerTick());
            assertEquals(30, result.suppliedMicroTorrPerTick());
            assertEquals(20, result.shortfallMicroTorrPerTick());
            assertFalse(result.processingAllowed());
            assertTrue(result.paused());

            // Repeated loss only climbs, and the pure model has no scrap side effect.
            VacuumTickResult again = result.state().tick(spec(), VacuumMode.PROCESS, 0);
            assertTrue(again.state().pressureMicroTorr() > result.state().pressureMicroTorr());
            assertTrue(again.paused());
        }

        @Test
        @DisplayName("the local reserve covers a shortfall exactly once")
        void reserveCoversShortfall() {
            VacuumChamberState state = VacuumChamberState.at(10, 20);
            VacuumTickResult covered = state.tick(spec(), VacuumMode.PROCESS, 0);

            assertEquals(10, covered.state().pressureMicroTorr());
            assertEquals(0, covered.state().reserveMicroTorr());
            assertEquals(50, covered.suppliedMicroTorrPerTick());
            assertTrue(covered.processingAllowed());

            VacuumTickResult next = covered.state().tick(spec(), VacuumMode.PROCESS, 0);
            assertEquals(30, next.state().pressureMicroTorr());
            assertTrue(next.paused());
        }

        @Test
        @DisplayName("central service is added on top of the local pump, not instead of it")
        void centralServiceAddsCapacity() {
            VacuumChamberState state = VacuumChamberState.at(10, 0);

            VacuumTickResult half = state.tick(spec(), VacuumMode.PROCESS, 20);
            assertEquals(10, half.state().pressureMicroTorr());
            assertTrue(half.processingAllowed());
            assertFalse(half.paused());

            VacuumTickResult surplus = state.tick(spec(), VacuumMode.PROCESS, 30);
            assertEquals(5, surplus.state().reserveMicroTorr());
        }

        @Test
        @DisplayName("pump-down uses the full local pump rate and recovers from a vented chamber")
        void pumpDownRecovers() {
            VacuumChamberState state = VacuumChamberState.at(100, 0);
            VacuumTickResult first = state.tick(spec(), VacuumMode.PUMP_DOWN, 0);

            assertEquals(80, first.state().pressureMicroTorr());
            assertFalse(first.processingAllowed());
            assertFalse(first.paused());

            VacuumChamberState recovered = VacuumChamberState.at(20, 0);
            assertEquals(0, recovered.tick(spec(), VacuumMode.PUMP_DOWN, 0).state().pressureMicroTorr());
        }

        @Test
        @DisplayName("pressure never leaves its bound even under maximum leak")
        void pressureIsBounded() {
            VacuumPumpSpec hugeLeak = new VacuumPumpSpec(1, 1_000_000, 1_000_000, 10, 0, 0);
            VacuumChamberState state = VacuumChamberState.evacuated();
            for (int i = 0; i < 100; i++) {
                state = state.tick(hugeLeak, VacuumMode.PROCESS, 0).state();
                assertTrue(state.pressureMicroTorr() >= 0);
                assertTrue(state.pressureMicroTorr() <= VacuumChamberState.MAX_PRESSURE_MICROTORR);
            }
            assertEquals(VacuumChamberState.MAX_PRESSURE_MICROTORR, state.pressureMicroTorr());
        }

        @Test
        @DisplayName("the reserve never exceeds its configured capacity")
        void reserveIsBounded() {
            VacuumChamberState state = VacuumChamberState.evacuated(29);
            VacuumTickResult result = state.tick(spec(), VacuumMode.IDLE, 0);
            assertEquals(30, result.state().reserveMicroTorr());
        }
    }

    @Nested
    @DisplayName("central allocation")
    class Allocation {

        @Test
        @DisplayName("scarce capacity uses largest remainders and conserves every unit")
        void fairScarceAllocation() {
            CentralVacuumAllocator.Allocation allocation =
                CentralVacuumAllocator.allocate(5, new int[] {3, 3, 4});

            assertArrayEquals(new int[] {2, 1, 2}, allocation.allocatedMicroTorrPerTick());
            assertEquals(5, allocation.totalAllocatedMicroTorrPerTick());
        }

        @Test
        @DisplayName("abundant capacity serves demand but never generates extra service")
        void abundantCapacityDoesNotGenerate() {
            CentralVacuumAllocator.Allocation allocation =
                CentralVacuumAllocator.allocate(100, new int[] {10, 20});

            assertArrayEquals(new int[] {10, 20}, allocation.allocatedMicroTorrPerTick());
            assertEquals(30, allocation.totalAllocatedMicroTorrPerTick());
        }

        @Test
        @DisplayName("zero capacity, zero demand and an empty request list allocate nothing")
        void zeroCases() {
            assertArrayEquals(new int[] {0, 0}, CentralVacuumAllocator.allocate(0, new int[] {5, 7})
                .allocatedMicroTorrPerTick());
            assertArrayEquals(new int[] {0, 0}, CentralVacuumAllocator.allocate(50, new int[] {0, 0})
                .allocatedMicroTorrPerTick());
            assertArrayEquals(new int[] {}, CentralVacuumAllocator.allocate(50, new int[] {})
                .allocatedMicroTorrPerTick());
        }

        @Test
        @DisplayName("negative demands and capacity are rejected")
        void rejectsNegativeInputs() {
            assertThrows(IllegalArgumentException.class, () -> CentralVacuumAllocator.allocate(-1, new int[] {1}));
            assertThrows(IllegalArgumentException.class, () -> CentralVacuumAllocator.allocate(1, new int[] {-1}));
            assertThrows(NullPointerException.class, () -> CentralVacuumAllocator.allocate(1, null));
        }

        @Test
        @DisplayName("allocation arrays are defensive copies and compare by contents")
        void allocationIsImmutable() {
            CentralVacuumAllocator.Allocation first =
                CentralVacuumAllocator.allocate(5, new int[] {3, 3, 4});
            CentralVacuumAllocator.Allocation second =
                CentralVacuumAllocator.allocate(5, new int[] {3, 3, 4});

            assertNotSame(first.allocatedMicroTorrPerTick(), second.allocatedMicroTorrPerTick());
            assertEquals(first, second);
            int[] exposed = first.allocatedMicroTorrPerTick();
            exposed[0] = 99;
            assertEquals(2, first.at(0));
            assertEquals("Allocation[requests=3, total=5]", first.toString());
        }

        @Test
        @DisplayName("conservation holds across many small capacities and demand shapes")
        void conservationProperty() {
            int[][] shapes = {
                {0, 0, 0}, {1, 0, 0}, {1, 1, 1}, {3, 3, 4}, {10, 1, 0}, {7, 7, 7, 7}
            };
            for (int[] demands : shapes) {
                int totalDemand = 0;
                for (int demand : demands) {
                    totalDemand += demand;
                }
                for (int capacity = 0; capacity <= 20; capacity++) {
                    CentralVacuumAllocator.Allocation allocation =
                        CentralVacuumAllocator.allocate(capacity, demands);
                    int total = allocation.totalAllocatedMicroTorrPerTick();
                    assertEquals(Math.min(capacity, totalDemand), total,
                        "capacity " + capacity + " demands " + java.util.Arrays.toString(demands));
                    for (int i = 0; i < demands.length; i++) {
                        assertTrue(allocation.at(i) >= 0 && allocation.at(i) <= demands[i]);
                    }
                }
            }
        }

        @Test
        @DisplayName("a chamber absent from the request receives nothing and cannot vent another")
        void disconnectedChamberIsIsolated() {
            VacuumPumpSpec isolated = noReserveSpec();
            VacuumChamberState vented = VacuumChamberState.at(100, 0);
            VacuumChamberState isolatedChamber = VacuumChamberState.evacuated();

            int[] demands = {vented.centralDemandMicroTorrPerTick(isolated, VacuumMode.PUMP_DOWN), 0};
            CentralVacuumAllocator.Allocation allocation = CentralVacuumAllocator.allocate(50, demands);

            VacuumChamberState afterVented = vented.tick(isolated, VacuumMode.PUMP_DOWN, allocation.at(0)).state();
            VacuumChamberState afterIsolated = isolatedChamber.tick(isolated, VacuumMode.IDLE, allocation.at(1)).state();

            assertTrue(afterVented.pressureMicroTorr() < vented.pressureMicroTorr());
            assertEquals(0, afterIsolated.pressureMicroTorr(),
                "the disconnected chamber gained pressure from another chamber's service");
            assertEquals(0, allocation.at(1));
        }
    }
}
