package com.euvcraft.lithmod.core;
import static org.junit.jupiter.api.Assertions.*;
import com.euvcraft.lithmod.core.cleanroom.CleanroomExposure;
import java.util.*;
import org.junit.jupiter.api.Test;
class CleanroomYieldTest {
 private final UUID id=UUID.fromString("00000000-0000-4000-8000-00000000ab11");
 private FoupContents run(int count,int factor,double calibration){var tier=ReferenceTiers.byId("T5");var lot=new FoupContents(id,count,Collections.nCopies(count,Wafer.unprocessed()),null,WaferSubstrate.PURE,FoupContents.PREPARATION_G2).withRoomExposure(CleanroomExposure.empty().sample(factor),0);for(int i=1;i<=tier.layers();i++)lot=YieldRollEngine.applyLayerStep(lot,tier,DieTypes.LOGIC,null,i,1,FabSettings.defaults(),calibration,(w,s)->new ContextualRng(0x773391,id,w,s));return lot;}
 @Test void finalMultiplierHasCorrectStatisticalYieldAndNoNewDraws(){var tier=ReferenceTiers.byId("T5");var base=run(16000,500,.8);var result=CleanroomYield.finish(base,tier);assertEquals(base.run().effectiveYield()*.5,result.run().effectiveYield(),1e-14);double actual=(double)YieldRollEngine.countPassing(YieldRollEngine.evaluate(result,tier,DieTypes.LOGIC,null))/result.size();assertEquals(result.run().effectiveYield(),actual,.015);assertSame(result,CleanroomYield.finish(result,tier));assertEquals(base.lotId(),result.lotId());assertEquals(base.size(),result.size());assertEquals(500,result.roomYieldPermille());}
 @Test void pristineRoomIsNoAdditionalYieldLoss(){var base=run(20,1000,.5);var result=CleanroomYield.finish(base,ReferenceTiers.byId("T5"));assertEquals(base.run(),result.run());assertEquals(base.wafers(),result.wafers());assertEquals(1000,result.roomYieldPermille());}
 @Test void exposureSurvivesLayerMutationAndEmptyingClearsIt(){var lot=run(2,750,1);assertEquals(750,lot.roomExposure().meanPermille());assertEquals(1,lot.roomExposure().samples());var finalLot=CleanroomYield.finish(lot,ReferenceTiers.byId("T5"));var empty=finalLot.withoutWaferAt(0).withoutWaferAt(0);assertEquals(0,empty.roomExposure().samples());assertEquals(0,empty.roomYieldPermille());}
 @Test void unfinishedLotsCannotFinalize(){var lot=new FoupContents(id,1,List.of(Wafer.unprocessed()));assertThrows(IllegalStateException.class,()->CleanroomYield.finish(lot,ReferenceTiers.byId("T5")));}
}
