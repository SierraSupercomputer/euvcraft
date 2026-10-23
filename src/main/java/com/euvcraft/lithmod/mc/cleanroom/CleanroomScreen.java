package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
/** Status stays inside an opaque panel, below the fixed player inventory. */
public final class CleanroomScreen extends FabMachineScreen<CleanroomMenu>{
 public CleanroomScreen(CleanroomMenu m,Inventory i,Component t){super(m,i,t,238,288);inventoryLabelY=72;}
 @Override protected int energyBarX(){return 12;}@Override protected int energyBarY(){return 18;}@Override protected boolean hasProgressBar(){return false;}
 private void line(GuiGraphicsExtractor g,String s,int x,int y){g.text(font,font.plainSubstrByWidth(s,222-x+8),x,y,0xff404040,false);}
 @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){line(g,title.getString(),8,6);line(g,menu.energy()+" / "+menu.energyCapacity()+" FE",30,20);line(g,playerInventoryTitle.getString(),8,72);
  if(menu.filter()){line(g,"Filter medium",64,58);line(g,"Medium life: "+menu.value(1)+" s",8,174);line(g,"Requires FE and an enclosed room",8,188);line(g,"Service pauses filtration gradually",8,202);return;}
  String status=switch(menu.value(0)){case 1->"Enclosed";case 2->"Breached: inspect shell/controller";case 3->"Unknown: room chunk unavailable";default->"Unformed: build a sealed cuboid";};
  line(g,status,8,174);line(g,"Interior: "+menu.value(10)+" x "+menu.value(11)+" x "+menu.value(12)+" ("+menu.value(4)+")",8,188);
  line(g,"Contamination: "+menu.value(2)+" PPM",8,202);line(g,"Clean: "+(menu.value(1)/100.0)+"%  Yield: "+(menu.value(3)/10.0)+"%",8,216);
  line(g,"Controller: "+(menu.value(8)==1?"powered":"no power"),8,230);line(g,"Filters: "+menu.value(5)+" / "+menu.value(6)+"   Doors open: "+menu.value(7),8,244);
  line(g,"Trend: "+(menu.value(9)/10000.0)+"% / s",8,258);line(g,menu.value(13)>=0?"To pristine: ~"+menu.value(13)+" s":"Recovery: add powered filtration",8,272);
 }
}
