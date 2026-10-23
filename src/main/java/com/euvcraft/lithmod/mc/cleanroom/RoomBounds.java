package com.euvcraft.lithmod.mc.cleanroom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.List;
public record RoomBounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
 public RoomBounds{if((long)maxX-minX<2||(long)maxY-minY<2||(long)maxZ-minZ<2||(long)maxX-minX>63||(long)maxY-minY>63||(long)maxZ-minZ>63)throw new IllegalArgumentException("Invalid cuboid");}
 public boolean contains(BlockPos p){return p.getX()>minX&&p.getX()<maxX&&p.getY()>minY&&p.getY()<maxY&&p.getZ()>minZ&&p.getZ()<maxZ;}
 public boolean containsAll(List<BlockPos> p){return !p.isEmpty()&&p.stream().allMatch(this::contains);}
 public boolean shell(BlockPos p){return p.getX()==minX||p.getX()==maxX||p.getY()==minY||p.getY()==maxY||p.getZ()==minZ||p.getZ()==maxZ;}
 public int volume(){return (maxX-minX-1)*(maxY-minY-1)*(maxZ-minZ-1);}
 public AABB interior(){return new AABB(minX+1,minY+1,minZ+1,maxX,maxY,maxZ);}
 public boolean overlaps(RoomBounds b){return interior().intersects(b.interior());}
}
