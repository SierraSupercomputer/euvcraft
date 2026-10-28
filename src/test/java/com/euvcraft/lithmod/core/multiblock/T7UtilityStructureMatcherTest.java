package com.euvcraft.lithmod.core.multiblock;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent literal geometry; local-cell keys are never passed to a world-position lookup. */
class T7UtilityStructureMatcherTest {
    private static final MultiblockPos ANCHOR=new MultiblockPos(100,64,200);
    private static final class Fixture {
        final boolean purifier;
        final int width,height,depth,column;
        final MultiblockDirection facing;
        final Map<MultiblockPos,BlockKind> world=new LinkedHashMap<>();
        Fixture(boolean purifier,MultiblockDirection facing,int column){
            this.purifier=purifier;this.facing=facing;this.column=column;
            width=purifier?2:3;height=purifier?2:3;depth=purifier?3:5;
            for(int u=0;u<width;u++)for(int v=0;v<height;v++)for(int d=0;d<depth;d++)world.put(cell(u,v,d),BlockKind.CASING);
            world.put(ANCHOR,BlockKind.CONTROLLER);world.put(cell(column,1,0),BlockKind.OPERATING_PANEL);
            world.put(cell(0,0,1),purifier?BlockKind.GAS_INLET:BlockKind.VACUUM_CONDUIT);
            world.put(cell(1,0,1),purifier?BlockKind.GAS_OUTLET:BlockKind.VACUUM_PORT);
            world.put(cell(0,1,1),BlockKind.POWER_PORT);
        }
        MultiblockPos cell(int u,int v,int d){
            int x=u-column;
            return switch(facing){case NORTH->new MultiblockPos(100+x,64+v,200+d);case SOUTH->new MultiblockPos(100-x,64+v,200-d);case EAST->new MultiblockPos(100-d,64+v,200+x);case WEST->new MultiblockPos(100+d,64+v,200-x);};
        }
        MatchResult match(){return purifier?T7UtilityStructureMatcher.matchPurifier(ANCHOR,facing,p->world.getOrDefault(p,BlockKind.OTHER)):T7UtilityStructureMatcher.matchVacuumPlant(ANCHOR,facing,p->world.getOrDefault(p,BlockKind.OTHER));}
        List<MultiblockPos> free(){return world.entrySet().stream().filter(e->e.getValue()==BlockKind.CASING).map(Map.Entry::getKey).toList();}
    }
    @ParameterizedTest @EnumSource(MultiblockDirection.class)
    void everyFacingAndFrontColumnHasExactLiteralDimensions(MultiblockDirection facing){
        for(boolean purifier:new boolean[]{true,false})for(int col=0;col<(purifier?2:3);col++){
            var f=new Fixture(purifier,facing,col);var result=f.match();assertTrue(result.success(),result.failureReason());
            assertEquals(purifier?12:45,result.memberPositions().size());assertEquals(new ArrayList<>(f.world.keySet()),result.memberPositions());
            assertEquals(purifier?2:3,result.memberPositions().stream().map(MultiblockPos::y).distinct().count());
        }
    }
    @ParameterizedTest @EnumSource(MultiblockDirection.class)
    void oneThroughFourPowerPortsWorkButZeroAndFiveRefuse(MultiblockDirection facing){
        for(boolean purifier:new boolean[]{true,false})for(int count=0;count<=5;count++){
            var f=new Fixture(purifier,facing,0);f.world.replaceAll((p,k)->k==BlockKind.POWER_PORT?BlockKind.CASING:k);var free=f.free();
            for(int i=0;i<count;i++)f.world.put(free.get(i),BlockKind.POWER_PORT);
            assertEquals(count>=1&&count<=4,f.match().success(),"ports="+count+" purifier="+purifier+" facing="+facing);
        }
    }
    @ParameterizedTest @EnumSource(MultiblockDirection.class)
    void everyRequiredRoleAndEveryMissingVolumeCellIsEnforced(MultiblockDirection facing){
        for(boolean purifier:new boolean[]{true,false}){
            var f=new Fixture(purifier,facing,0);var original=new LinkedHashMap<>(f.world);
            for(var entry:original.entrySet()){
                f.world.put(entry.getKey(),BlockKind.OTHER);assertFalse(f.match().success(),"Missing cell "+entry.getKey());f.world.put(entry.getKey(),entry.getValue());
                if(entry.getValue()!=BlockKind.CASING){f.world.put(entry.getKey(),BlockKind.CASING);assertFalse(f.match().success(),"Missing role "+entry.getValue());f.world.put(entry.getKey(),entry.getValue());}
            }
        }
    }
    @ParameterizedTest @EnumSource(MultiblockDirection.class)
    void utilityPortsMoveFreelyAndForeignRolesAreRejected(MultiblockDirection facing){
        for(boolean purifier:new boolean[]{true,false}){
            var original=new Fixture(purifier,facing,0);var role=purifier?BlockKind.GAS_INLET:BlockKind.VACUUM_PORT;
            var old=original.world.entrySet().stream().filter(e->e.getValue()==role).findFirst().orElseThrow().getKey();
            for(var pos:original.free()){var f=new Fixture(purifier,facing,0);f.world.put(old,BlockKind.CASING);f.world.put(pos,role);assertTrue(f.match().success(),"Flexible utility port at "+pos);}
            for(var wrong:List.of(BlockKind.CONTROLLER,BlockKind.OPERATING_PANEL,BlockKind.DEPOSITION_MODULE,BlockKind.DIAGNOSTIC_PANEL)){
                var f=new Fixture(purifier,facing,0);f.world.put(f.free().getFirst(),wrong);assertFalse(f.match().success(),"Foreign/extra role "+wrong);
            }
        }
    }
    @Test void productionShapeConstantsMatchTheIndependentContract(){
        assertEquals(new StructureShape(2,2,3),T7UtilityStructureMatcher.PURIFIER_SHAPE);
        assertEquals(new StructureShape(3,3,5),T7UtilityStructureMatcher.VACUUM_PLANT_SHAPE);
    }
}
