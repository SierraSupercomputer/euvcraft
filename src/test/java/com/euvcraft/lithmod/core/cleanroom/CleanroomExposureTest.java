package com.euvcraft.lithmod.core.cleanroom;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CleanroomExposureTest {
 @Test void emptyAndRoundedMean(){var e=CleanroomExposure.empty();assertEquals(500,e.meanPermille());e=e.sample(250).sample(750).sample(1000);assertEquals(3,e.samples());assertEquals(2000,e.permilleSum());assertEquals(667,e.meanPermille());assertEquals(e,new CleanroomExposure(e.samples(),e.permilleSum()));}
 @Test void factorsAreBounded(){for(int n:new int[]{Integer.MIN_VALUE,-1,0,1001,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->CleanroomExposure.empty().sample(n));assertEquals(1,CleanroomExposure.empty().sample(1).meanPermille());assertEquals(1000,CleanroomExposure.empty().sample(1000).meanPermille());}
 @Test void malformedPersistedStatesRejected(){for(long[] p:new long[][]{{-1,0},{1,-1},{0,1},{2,1},{1,1001},{2,2001}})assertThrows(IllegalArgumentException.class,()->new CleanroomExposure(p[0],p[1]));}
 @Test void sampleCounterOverflowPreservesOriginal(){var e=new CleanroomExposure(Long.MAX_VALUE,Long.MAX_VALUE);assertThrows(ArithmeticException.class,()->e.sample(1));assertEquals(1,e.meanPermille());}
 @Test void sumOverflowPreservesOriginal(){long sum=Long.MAX_VALUE-500;var e=new CleanroomExposure(sum/1000+1,sum);assertThrows(ArithmeticException.class,()->e.sample(1000));assertTrue(e.meanPermille()<=1000);}
 @Test void roundingDoesNotOverflow(){var e=new CleanroomExposure(Long.MAX_VALUE/2,Long.MAX_VALUE-1);assertEquals(2,e.meanPermille());}
}
