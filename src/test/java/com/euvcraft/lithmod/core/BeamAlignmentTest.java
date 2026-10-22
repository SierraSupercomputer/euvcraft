package com.euvcraft.lithmod.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BeamAlignmentTest {
 @Test void perfectAlignment(){var a=new BeamAlignment(2,-3,4,2,-3,4);assertEquals(100,a.quality());assertEquals(0,a.spotX());assertEquals(0,a.spotY());assertEquals(0,a.focusError());}
 @Test void badAlignmentClampsAtZero(){var a=new BeamAlignment(10,10,10,-5,-5,-5);assertEquals(0,a.quality());assertEquals(.5,a.multiplier(.5,1));}
 @Test void horizontalControlCouplesBothAxes(){var a=new BeamAlignment(0,0,0,0,0,0).adjust(1);assertEquals(2,a.spotX());assertEquals(1,a.spotY());assertEquals(88,a.quality());assertEquals(new BeamAlignment(0,0,0,0,0,0),a.adjust(0));}
 @Test void verticalControlCouplesBothAxes(){var a=new BeamAlignment(0,0,0,0,0,0).adjust(3);assertEquals(1,a.spotX());assertEquals(-2,a.spotY());assertEquals(88,a.quality());}
 @Test void focusPenaltyIsIndependent(){var a=new BeamAlignment(0,0,1,0,0,0);assertEquals(90,a.quality());assertEquals(1,a.focusError());assertEquals(0,a.spotX());assertEquals(0,a.spotY());}
 @Test void qualityFiftyGivesPointSevenFive(){var a=new BeamAlignment(0,0,5,0,0,0);assertEquals(50,a.quality());assertEquals(.75,a.multiplier(.5,1));}
 @Test void perfectGivesMaximumMultiplier(){assertEquals(1,new BeamAlignment(0,0,0,0,0,0).multiplier(.5,1));}
 @Test void constantMultiplier(){assertEquals(.8,new BeamAlignment(0,0,5,0,0,0).multiplier(.8,.8));}
 @Test void knobsClampAtBothEnds(){var a=new BeamAlignment(10,10,10,0,0,0);for(int c:new int[]{1,3,5})assertEquals(a,a.adjust(c));a=new BeamAlignment(-10,-10,-10,0,0,0);for(int c:new int[]{0,2,4})assertEquals(a,a.adjust(c));}
 @Test void controlsAreImmutable(){var a=new BeamAlignment(0,0,0,0,0,0);a.adjust(1);assertEquals(0,a.x());}
 @Test void invalidControlCannotMutate(){var a=BeamAlignment.create(7);assertEquals(a,a.adjust(-1));assertEquals(a,a.adjust(6));assertEquals(a,a.adjust(Integer.MAX_VALUE));}
 @Test void creationIsDeterministicAndAlwaysSolvable(){for(long seed=0;seed<100;seed++){var a=BeamAlignment.create(seed);assertEquals(a,BeamAlignment.create(seed));assertEquals(100,new BeamAlignment(a.targetX(),a.targetY(),a.targetFocus(),a.targetX(),a.targetY(),a.targetFocus()).quality());}}
 @Test void constructorRejectsInvalidKnobs(){for(int invalid:new int[]{-11,11,Integer.MIN_VALUE,Integer.MAX_VALUE}){assertThrows(IllegalArgumentException.class,()->new BeamAlignment(invalid,0,0,0,0,0));assertThrows(IllegalArgumentException.class,()->new BeamAlignment(0,invalid,0,0,0,0));assertThrows(IllegalArgumentException.class,()->new BeamAlignment(0,0,invalid,0,0,0));}}
 @Test void constructorRejectsInvalidTargets(){for(int invalid:new int[]{-6,6,Integer.MIN_VALUE,Integer.MAX_VALUE}){assertThrows(IllegalArgumentException.class,()->new BeamAlignment(0,0,0,invalid,0,0));assertThrows(IllegalArgumentException.class,()->new BeamAlignment(0,0,0,0,invalid,0));assertThrows(IllegalArgumentException.class,()->new BeamAlignment(0,0,0,0,0,invalid));}}
 @Test void invalidMultiplierRangeRejected(){var a=BeamAlignment.create(4);for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0,-.1,1.1})assertThrows(IllegalArgumentException.class,()->a.multiplier(bad,1));assertThrows(IllegalArgumentException.class,()->a.multiplier(.8,.7));assertThrows(IllegalArgumentException.class,()->a.multiplier(.5,Double.NaN));assertThrows(IllegalArgumentException.class,()->a.multiplier(.5,1.1));}
}
