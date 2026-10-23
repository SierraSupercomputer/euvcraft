package com.euvcraft.lithmod.mc.cleanroom;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
/** Loaded-room index only: values contain no world or block-entity references. */
public final class CleanroomRooms {
 private static final Map<ServerLevel,Set<BlockPos>> ROOMS=new WeakHashMap<>();
 public record Sample(boolean available,int yieldPermille,CleanroomControllerBlockEntity room){}
 public static void blockChanged(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent e){if(e.getLevel() instanceof ServerLevel level)for(var pos:List.copyOf(ROOMS.getOrDefault(level,Set.of())))if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof CleanroomControllerBlockEntity room){var b=room.bounds();if(b==null||e.getPos().getX()>=b.minX()-1&&e.getPos().getX()<=b.maxX()+1&&e.getPos().getY()>=b.minY()-1&&e.getPos().getY()<=b.maxY()+1&&e.getPos().getZ()>=b.minZ()-1&&e.getPos().getZ()<=b.maxZ()+1)room.markTopologyDirty();}}
 public static void register(CleanroomControllerBlockEntity room){if(room.getLevel() instanceof ServerLevel l)ROOMS.computeIfAbsent(l,k->new HashSet<>()).add(room.getBlockPos());}
 public static void forget(CleanroomControllerBlockEntity room){if(room.getLevel() instanceof ServerLevel l){var set=ROOMS.get(l);if(set!=null){set.remove(room.getBlockPos());if(set.isEmpty())ROOMS.remove(l);}}}
 public static Sample sample(ServerLevel level,List<BlockPos> members){
  int outside=CleanroomRulesLoader.rules().outsideYieldPermille();CleanroomControllerBlockEntity found=null;
  for(var pos:List.copyOf(ROOMS.getOrDefault(level,Set.of()))){
   if(!level.hasChunkAt(pos))continue;
   if(!(level.getBlockEntity(pos) instanceof CleanroomControllerBlockEntity r)||r.isRemoved()||r.bounds()==null||!r.bounds().containsAll(members))continue;
   if(found!=null)return new Sample(true,outside,null);found=r;
  }
  if(found==null)return new Sample(true,outside,null);
  // Validate the known footprint before granting a bonus; topology changes never
  // leave a stale clean-room benefit for the next scheduled controller scan.
  found.validateKnown();
  if(found.state()==CleanroomControllerBlockEntity.UNKNOWN)return new Sample(false,outside,found);
  return new Sample(true,found.state()==CleanroomControllerBlockEntity.ENCLOSED?found.roomYield():outside,found);
 }
 static boolean overlaps(ServerLevel level,CleanroomControllerBlockEntity self,RoomBounds bounds){
  for(var p:List.copyOf(ROOMS.getOrDefault(level,Set.of())))if(!p.equals(self.getBlockPos())&&level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof CleanroomControllerBlockEntity r&&r.bounds()!=null&&r.state()==CleanroomControllerBlockEntity.ENCLOSED&&bounds.overlaps(r.bounds()))return true;return false;
 }
 private CleanroomRooms(){}
}
