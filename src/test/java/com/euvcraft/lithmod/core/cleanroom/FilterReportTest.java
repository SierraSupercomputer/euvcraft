package com.euvcraft.lithmod.core.cleanroom;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.euvcraft.lithmod.core.cleanroom.FilterReport.*;
class FilterReportTest {
 private Row row(int x,int y,int z,State s){boolean u=s==State.UNAVAILABLE||s==State.MISSING;return new Row(x,y,z,s,u?-1:80000,u?-1:100000,u?-1:4,u?-1:12000);}
 @Test void attentionAndAbsoluteCoordinatesSortWithoutOverflow(){
  var running=row(-30,0,0,State.FILTERING);var plus=row(Integer.MAX_VALUE,0,0,State.NO_POWER);var minus=row(Integer.MIN_VALUE,0,0,State.NO_POWER);var lowY=row(0,-30,0,State.NO_POWER);var lowZ=row(0,0,-30,State.NO_POWER);var highZ=row(0,0,30,State.NO_POWER);
  var source=new ArrayList<>(List.of(running,highZ,plus,lowZ,minus,lowY));var original=List.copyOf(source);assertEquals(List.of(minus,lowY,lowZ,highZ,plus),page(source,0).rows());assertEquals(List.of(running),page(source,1).rows());assertEquals(original,source);
 }
 @Test void totalsIncludeOffscreenFiltersAndPagesClamp(){var rows=new ArrayList<Row>();for(int i=0;i<9;i++)rows.add(row(i,2,-i,State.FILTERING));rows.add(row(-20,0,0,State.NO_MEDIUM));rows.add(row(-30,0,0,State.UNAVAILABLE));var last=page(rows,Integer.MAX_VALUE);assertEquals(11,last.total());assertEquals(3,last.pages());assertEquals(2,last.page());assertEquals(1,last.rows().size());assertEquals(9,last.working());assertEquals(2,last.attention());assertEquals(1,last.unavailable());assertEquals(0,page(rows,Integer.MIN_VALUE).page());}
 @Test void emptyAndExactPageBoundaries(){var empty=page(List.of(),100);assertEquals(0,empty.page());assertEquals(1,empty.pages());assertEquals(List.of(),empty.rows());var rows=new ArrayList<Row>();for(int i=0;i<PAGE_SIZE;i++)rows.add(row(i,0,0,State.READY));assertEquals(1,page(rows,10).pages());assertEquals(PAGE_SIZE,page(rows,10).rows().size());}
 @Test void pagesAreImmutableSnapshots(){var source=new ArrayList<>(List.of(row(1,2,3,State.READY)));var snapshot=page(source,0);source.clear();assertEquals(1,snapshot.rows().size());assertThrows(UnsupportedOperationException.class,()->snapshot.rows().clear());var raw=new ArrayList<>(snapshot.rows());var direct=new Page(1,0,1,0,0,0,raw);raw.clear();assertEquals(1,direct.rows().size());}
 @Test void unavailableValuesAreExplicit(){assertEquals(-1,row(-20000000,-64,20000000,State.UNAVAILABLE).energy());assertThrows(IllegalArgumentException.class,()->new Row(0,0,0,State.UNAVAILABLE,0,-1,-1,-1));assertThrows(IllegalArgumentException.class,()->new Row(0,0,0,State.READY,0,0,-1,0));assertDoesNotThrow(()->new Row(0,0,0,State.READY,200000,100000,0,0));assertThrows(NullPointerException.class,()->page(null,0));assertThrows(NullPointerException.class,()->page(Arrays.asList((Row)null),0));}
}
