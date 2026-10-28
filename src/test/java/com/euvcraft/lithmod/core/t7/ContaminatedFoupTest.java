package com.euvcraft.lithmod.core.t7;

import com.euvcraft.lithmod.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContaminatedFoupTest {
    private FoupContents original() {
        return FoupContents.of(UUID.randomUUID(),25,List.of(Wafer.unprocessed(),Wafer.unprocessed()),WaferSubstrate.ELECTRONIC_GRADE)
            .preparedG3(800,new int[]{1000,500});
    }
    @Test void contaminationPreservesTheCarrierIdentityAndFrozenEvidence() {
        var lot=original();var bad=FoupDisposal.contaminate(lot);
        assertTrue(bad.isContaminated()); assertEquals(lot.lotId(),bad.lotId());
        assertEquals(lot.g3Preparation(),bad.g3Preparation()); assertEquals(lot.size(),bad.size());
        assertSame(bad,FoupDisposal.contaminate(bad));
        for(int i=0;i<lot.size();i++)assertEquals(lot.wafers().get(i).cumulativeRoll(),bad.wafers().get(i).cumulativeRoll());
    }
    @Test void disposalAccountsEveryWaferAndOnlyTheEmptyCarrierIsClean() {
        var bad=original().contaminated();
        assertEquals(2,FoupDisposal.scrapCount(bad,1));
        var empty=FoupDisposal.emptyCarrier(bad,UUID.randomUUID());
        assertFalse(empty.isContaminated()); assertTrue(empty.isEmpty());
        assertEquals(1,empty.preparationGeneration()); assertFalse(empty.g3Preparation().isPrepared());
        assertEquals(bad.capacity(),empty.capacity());
    }
    @Test void packagingRefusesContaminatedLotsBeforeJudgingOrSpendingPackages() {
        assertEquals(PackagingRefusalReason.CONTAMINATED_FOUP,PackagingRules.validate(original().contaminated(),null,false,null,2));
    }
}
