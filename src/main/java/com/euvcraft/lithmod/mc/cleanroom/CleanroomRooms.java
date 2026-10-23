package com.euvcraft.lithmod.mc.cleanroom;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
/** Weak world keys and immutable footprints; never retain entities or force chunk loads. */
public final class CleanroomRooms {
 private static final Map<ServerLevel,Map<BlockPos,RoomBounds>> ROOMS=new WeakHashMap<>();
 public record Sample(boolean available,int yieldPermille,CleanroomControllerBlockEntity room){}
 private static Map<BlockPos,RoomBounds> rooms(ServerLevel level){return ROOMS.computeIfAbsent(level,k->new HashMap<>());}
 public static void blockChanged(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent e){if(e.getLevel() instanceof ServerLevel level)for(var pos:List.copyOf(rooms(level).keySet()))if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof CleanroomControllerBlockEntity room){var b=room.bounds();if(b==null||e.getPos().getX()>=b.minX()-1&&e.getPos().getX()<=b.maxX()+1&&e.getPos().getY()>=b.minY()-1&&e.getPos().getY()<=b.maxY()+1&&e.getPos().getZ()>=b.minZ()-1&&e.getPos().getZ()<=b.maxZ()+1)room.markTopologyDirty();}}
 public static void register(CleanroomControllerBlockEntity room){if(room.getLevel() instanceof ServerLevel l)rooms(l).put(room.getBlockPos().immutable(),room.bounds());}
 /** setRemoved also means chunk unload. Retain known geometry until a loaded query proves removal. */
 public static void forget(CleanroomControllerBlockEntity room){if(room.getLevel() instanceof ServerLevel l){if(room.bounds()!=null)rooms(l).put(room.getBlockPos().immutable(),room.bounds());else rooms(l).remove(room.getBlockPos());}}
 public static Sample sample(ServerLevel level,List<BlockPos> members){
  int outside=CleanroomRulesLoader.rules().outsideYieldPermille();CleanroomControllerBlockEntity found=null;boolean unavailable=false;
  for(var pos:List.copyOf(rooms(level).keySet())){
   var known=rooms(level).get(pos);
   if(!level.hasChunkAt(pos)){if(known!=null&&known.containsAll(members))unavailable=true;continue;}
   if(!(level.getBlockEntity(pos) instanceof CleanroomControllerBlockEntity r)||r.isRemoved()){rooms(level).remove(pos);continue;}
   register(r);if(r.bounds()==null||!r.bounds().containsAll(members))continue;
   if(found!=null)return new Sample(true,outside,null);found=r;
  }
  if(unavailable)return new Sample(false,outside,null); // An unavailable overlapping claim makes ownership ambiguous even if another claimant is loaded.
  if(found==null)return new Sample(true,outside,null);
  found.validateKnown();
  if(found.state()==CleanroomControllerBlockEntity.UNKNOWN)return new Sample(false,outside,found);
  return new Sample(true,found.state()==CleanroomControllerBlockEntity.ENCLOSED?found.roomYield():outside,found);
 }
 static boolean overlaps(ServerLevel level,CleanroomControllerBlockEntity self,RoomBounds bounds){
  for(var p:List.copyOf(rooms(level).keySet()))if(!p.equals(self.getBlockPos())){
   RoomBounds other=rooms(level).get(p);
   if(level.hasChunkAt(p)){if(level.getBlockEntity(p) instanceof CleanroomControllerBlockEntity r&&!r.isRemoved())other=r.bounds();else{rooms(level).remove(p);continue;}}
   if(other!=null&&bounds.overlaps(other))return true;
  }return false;
 }
 private CleanroomRooms(){}
}
