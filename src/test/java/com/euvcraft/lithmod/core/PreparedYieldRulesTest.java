package com.euvcraft.lithmod.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparedYieldRulesTest {
    private static RunSpec spec() { return new RunSpec(DieTypes.COMPUTE, 7, 1, .85, 80); }

    @Test void fullPreparationPreservesOriginalThresholdExactly() {
        assertEquals(1.0 - .85, PreparedYieldRules.threshold(spec(), 1.0));
    }
    @Test void penaltiesOnlyTightenTheSameSavedRollThreshold() {
        double previous = PreparedYieldRules.threshold(spec(), 1.0);
        for (double penalty : new double[]{.9,.75,.5,.25,.1,0}) {
            double next = PreparedYieldRules.threshold(spec(), penalty);
            assertTrue(next >= previous && next <= 1.0);
            previous = next;
        }
    }
    @Test void preparationEvidenceStaysSeparateFromExposureRolls() {
        var fresh = FoupContents.of(UUID.randomUUID(),25,List.of(Wafer.unprocessed(),Wafer.unprocessed()),WaferSubstrate.ELECTRONIC_GRADE);
        var prepared = fresh.preparedG3(800,new int[]{1000,500});
        assertEquals(fresh.wafers(),prepared.wafers());
        assertEquals(.8,PreparedYieldRules.multiplier(prepared,0));
        assertEquals(.4,PreparedYieldRules.multiplier(prepared,1));
        assertThrows(IllegalStateException.class,()->prepared.preparedG3(1000,new int[]{1000,1000}));
        assertEquals(prepared.g3Preparation(),prepared.withRoomExposure(prepared.roomExposure(),900).g3Preparation());
    }
}
