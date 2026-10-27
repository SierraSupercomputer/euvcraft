package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.network.chat.Component;import net.minecraft.world.entity.player.Inventory;import java.util.Locale;
public final class UtilityScreen extends AdvancedMachineScreen<UtilityMenu>{public UtilityScreen(UtilityMenu m,Inventory i,Component t){super(m,i,t,238);}@Override protected boolean formed(){return menu.formed();}@Override protected boolean cabinet(){return false;}
 // Lower band (local y, opaque 176x238 window, hotbar well ends at 159): 170 formed status;
 // formed: 182 batch status, 194 batch costs, 206 water, 218 ultrapure; unformed: 184/198 tank
 // rows; the outlet note sits at 228 on its own line. Every row fits under the 237 border.
 @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){line(g,title,8,6,160);line(g,Component.literal("Filter"),30,23,42);line(g,Component.literal("Water"),77,23,42);line(g,Component.literal("Bucket"),121,23,47);line(g,Component.literal(menu.energy()+" / "+menu.energyCapacity()+" FE"),30,57,140);line(g,playerInventoryTitle,8,72,160);line(g,Component.literal(formed()?"Formed":"Unformed: check structure"),8,170,160);
  if(formed()){line(g,batchStatus(),8,182,160);line(g,batchCosts(),8,194,160);line(g,Component.literal("Water: "+menu.raw()+" / "+menu.rawCapacity()+" mB"),8,206,160);line(g,Component.literal("Ultrapure: "+menu.pure()+" / "+menu.pureCapacity()+" mB"),8,218,160);}
  else{line(g,Component.literal("Water: "+menu.raw()+" / "+menu.rawCapacity()+" mB"),8,184,160);line(g,Component.literal("Ultrapure: "+menu.pure()+" / "+menu.pureCapacity()+" mB"),8,198,160);}
  line(g,Component.literal("Pure outlet to scanner inlet"),8,228,160);}
 /** Batch progress banner. Pauses mirror the server's own per-tick conditions exactly:
  *  the plant stalls when the pure tank lacks the admitted output space or the buffer
  *  holds less than one tick's draw; while those hold it reports the synced base
  *  progress/total. Idle reasons mirror the admission checks. */
 private Component batchStatus(){
  int water=menu.batchWaterMb(),output=menu.batchOutputMb(),filters=menu.batchFilters(),fe=menu.batchFePerTick();
  if(menu.active()){
   if(output<=0||fe<=0)return Component.literal("Paused: settings not loaded");
   int free=menu.pureCapacity()-menu.pure();
   if(free<output)return Component.literal("Paused: pure tank full");
   if(menu.energy()<fe)return Component.literal("Paused: waiting for "+fe+" FE/t");
   return Component.literal("Batch: "+menu.progress()+"/"+menu.ticks()+" ticks");
  }
  if(water<=0||output<=0||filters<=0||fe<=0)return Component.literal("Idle: settings not loaded");
  if(menu.raw()<water)return Component.literal("Idle: waiting for "+(water-menu.raw())+" mB raw");
  if(menu.filterCount()<filters)return Component.literal("Idle: insert "+(filters-menu.filterCount())+" filter(s)");
  int free=menu.pureCapacity()-menu.pure();
  if(free<output)return Component.literal("Idle: pure tank needs "+output+" mB free");
  if(menu.energy()<fe)return Component.literal("Idle: needs "+fe+" FE stored");
  return Component.literal("Ready: next batch queued");
 }
 /** The current or next batch's costs, straight from the synced menu state. */
 private Component batchCosts(){return Component.literal("Output: "+menu.batchOutputMb()+" mB UPW");}

 private static String compact(long value){
  if(value>=1_000_000_000_000L)return String.format(Locale.ROOT,"%.1fT",value/1e12);
  if(value>=1_000_000_000L)return String.format(Locale.ROOT,"%.1fB",value/1e9);
  if(value>=1_000_000L)return String.format(Locale.ROOT,"%.1fM",value/1e6);
  if(value>=1_000L)return String.format(Locale.ROOT,"%.1fk",value/1e3);
  return Long.toString(value);
 }
}
