package com.euvcraft.lithmod.mc.cleanroom;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.*;
/** Bounded shell discovery; never asks the level to load a chunk. */
public final class CleanroomTopology {
 public record Result(RoomBounds bounds,int state,String fault,int doors,List<BlockPos> filters){}
 public static Result discover(ServerLevel level,BlockPos anchor){
  var rules=CleanroomRulesLoader.rules();var visited=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(anchor);visited.add(anchor);boolean unknown=false;
  int minX=anchor.getX(),minY=anchor.getY(),minZ=anchor.getZ(),maxX=minX,maxY=minY,maxZ=minZ;
  int budget=6*rules.maxSpan()*rules.maxSpan();
  while(!queue.isEmpty()){
   var p=queue.remove();minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
   if(maxX-minX+1>rules.maxSpan()||maxY-minY+1>rules.maxSpan()||maxZ-minZ+1>rules.maxSpan()||visited.size()>budget)return new Result(null,CleanroomControllerBlockEntity.UNFORMED,"Room exceeds size limit",0,List.of());
   for(var direction:Direction.values()){
    var n=p.relative(direction);if(visited.contains(n))continue;
    if(!level.hasChunkAt(n)){unknown=true;continue;}
    if(CleanroomBlocks.isShell(level.getBlockState(n).getBlock())){visited.add(n);queue.add(n.immutable());}
   }
  }
  RoomBounds bounds;
  try{bounds=new RoomBounds(minX,minY,minZ,maxX,maxY,maxZ);}catch(IllegalArgumentException invalid){return new Result(null,unknown?CleanroomControllerBlockEntity.UNKNOWN:CleanroomControllerBlockEntity.UNFORMED,"Incomplete cuboid",0,List.of());}
  return validate(level,anchor,bounds);
 }
 public static Result validate(ServerLevel level,BlockPos anchor,RoomBounds b){
  var rules=CleanroomRulesLoader.rules();if(b.volume()>rules.maxVolume()||b.maxX()-b.minX()+1>rules.maxSpan()||b.maxY()-b.minY()+1>rules.maxSpan()||b.maxZ()-b.minZ()+1>rules.maxSpan())return new Result(b,CleanroomControllerBlockEntity.BREACHED,"Room exceeds configured limit",0,List.of());
  int controllers=0,doors=0;var filters=new ArrayList<BlockPos>();BlockPos firstMissing=null;boolean unknown=false;
  for(BlockPos p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())){
   if(!level.hasChunkAt(p)){unknown=true;continue;}
   var state=level.getBlockState(p);var block=state.getBlock();
   if(block==CleanroomBlocks.CONTROLLER.get())controllers++;
   if(!b.shell(p))continue;
   if(!CleanroomBlocks.isShell(block)){if(firstMissing==null)firstMissing=p.immutable();continue;}
   if(block==CleanroomBlocks.FILTER.get())filters.add(p.immutable());
   if(block==CleanroomBlocks.DOOR.get()){
    var other=p.relative(state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN);
    if(!level.hasChunkAt(other)){unknown=true;continue;}
    var pair=level.getBlockState(other);
    if(pair.getBlock()!=block||pair.getValue(DoorBlock.HALF)==state.getValue(DoorBlock.HALF)||pair.getValue(DoorBlock.FACING)!=state.getValue(DoorBlock.FACING)||pair.getValue(DoorBlock.OPEN)!=state.getValue(DoorBlock.OPEN)||!b.shell(other)){if(firstMissing==null)firstMissing=p.immutable();}
    if(state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&state.getValue(DoorBlock.OPEN))doors++;
   }
  }
  if(unknown)return new Result(b,CleanroomControllerBlockEntity.UNKNOWN,"Room chunk unavailable",doors,List.copyOf(filters));
  if(controllers!=1||level.getBlockState(anchor).getBlock()!=CleanroomBlocks.CONTROLLER.get())return new Result(b,CleanroomControllerBlockEntity.BREACHED,"Exactly one shell controller required",doors,List.copyOf(filters));
  if(firstMissing!=null)return new Result(b,CleanroomControllerBlockEntity.BREACHED,"Breach at "+firstMissing.toShortString(),doors,List.copyOf(filters));
  return new Result(b,CleanroomControllerBlockEntity.ENCLOSED,"Enclosed",doors,List.copyOf(filters));
 }
 private CleanroomTopology(){}
}
