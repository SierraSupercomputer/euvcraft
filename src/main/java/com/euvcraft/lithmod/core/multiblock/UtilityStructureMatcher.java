package com.euvcraft.lithmod.core.multiblock;
import java.util.*;import java.util.function.Function;
/** 2x2x3 plant; controller any lower-front column, 1-2 FE ports, exactly one water inlet and output. */
public final class UtilityStructureMatcher {
 public static MatchResult match(MultiblockPos anchor,MultiblockDirection facing,Function<MultiblockPos,BlockKind> lookup){
  for(int column=0;column<2;column++){var positions=new ArrayList<MultiblockPos>();int ports=0,inlets=0,outlets=0;boolean valid=true;
   for(int u=0;u<2;u++)for(int v=0;v<2;v++)for(int d=0;d<3;d++){
    var p=anchor.add(facing.right().multiply(u-column)).add(MultiblockDirection.up().multiply(v)).add(facing.back().multiply(d));positions.add(p);var k=lookup.apply(p);
    if(p.equals(anchor)){if(k!=BlockKind.CONTROLLER)valid=false;}else switch(k){case CASING->{}case POWER_PORT->ports++;case WATER_INLET->inlets++;case FLUID_OUTLET->outlets++;default->valid=false;}
   }if(valid&&ports>=1&&ports<=2&&inlets==1&&outlets==1)return MatchResult.success(positions);
  }return MatchResult.failure("Utility plant needs 2x2x3, 1-2 power ports, one water inlet and one pure-water outlet");
 }private UtilityStructureMatcher(){}
}


