package com.euvcraft.lithmod.core.t7;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VacuumServiceRegressionTest {
    @Test void repeatedScarceAllocationServicesEveryChamber() {
        int[] totals = new int[3];
        for (int tick = 0; tick < 30; tick++) {
            int[] grants = CentralVacuumAllocator.allocate(1, new int[]{1, 100, 1000}, tick).allocatedMicroTorrPerTick();
            assertEquals(1, java.util.Arrays.stream(grants).sum());
            for (int i = 0; i < 3; i++) totals[i] += grants[i];
        }
        assertArrayEquals(new int[]{10, 10, 10}, totals);
    }
    @Test void cursorAllocationConservesCapacityAndNeverOverfillsARequest() {
        var rng = new java.util.Random(741L);
        for (int trial = 0; trial < 1000; trial++) {
            int[] requests = rng.ints(8, 0, 1000).toArray();
            int capacity = rng.nextInt(10000);
            int[] actual = CentralVacuumAllocator.allocate(capacity, requests, trial).allocatedMicroTorrPerTick();
            assertEquals(Math.min(capacity, java.util.Arrays.stream(requests).sum()), java.util.Arrays.stream(actual).sum());
            for (int i = 0; i < requests.length; i++) assertTrue(actual[i] >= 0 && actual[i] <= requests[i]);
        }
    }
    @Test void disabledLocalPumpCannotProvideFreeService() {
        var spec = new VacuumPumpSpec(30, 10, 0, 10, 0, 0);
        var chamber = VacuumChamberState.at(10, 0);
        var off = chamber.tick(spec, VacuumMode.IDLE, 0, false);
        assertEquals(20, off.state().pressureMicroTorr());
        assertEquals(0, off.suppliedMicroTorrPerTick());
        assertFalse(off.processingAllowed());
        assertEquals(10, chamber.tick(spec, VacuumMode.IDLE, 10, false).state().pressureMicroTorr());
    }
}
