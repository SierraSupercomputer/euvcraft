package com.euvcraft.lithmod.mc.cleanroom;
import java.util.Objects;
import net.minecraft.world.inventory.ContainerData;
/** Two unsigned 16-bit menu slots per signed int; vanilla data packets truncate to shorts. */
public final class WideContainerData implements ContainerData {
 private final ContainerData values;
 public WideContainerData(ContainerData values){this.values=Objects.requireNonNull(values);}
 @Override public int getCount(){return values.getCount()*2;}
 @Override public int get(int i){Objects.checkIndex(i,getCount());return values.get(i/2) >>> (i%2*16) & 65535;}
 @Override public void set(int i,int value){Objects.checkIndex(i,getCount());int old=values.get(i/2);values.set(i/2,i%2==0?(old&0xffff0000)|(value&65535):(old&65535)|((value&65535)<<16));}
}
