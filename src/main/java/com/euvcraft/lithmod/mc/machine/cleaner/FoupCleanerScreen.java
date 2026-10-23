package com.euvcraft.lithmod.mc.machine.cleaner;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.client.gui.components.Button;import net.minecraft.network.chat.Component;import net.minecraft.world.entity.player.Inventory;
public final class FoupCleanerScreen extends FabMachineScreen<FoupCleanerMenu> {
 private Button start;
 public FoupCleanerScreen(FoupCleanerMenu m,Inventory i,Component t){super(m,i,t,284,232);inventoryLabelY=72;}
 @Override protected int energyBarX(){return 12;}@Override protected int energyBarY(){return 18;}@Override protected int progressBarX(){return 215;}@Override protected int progressBarY(){return 55;}
 @Override protected void init(){super.init();start=addRenderableWidget(Button.builder(Component.literal("Discard & Clean"),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);}).bounds(leftPos+38,topPos+55,135,16).build());}
 @Override protected void containerTick(){super.containerTick();start.active=!menu.locked()&&menu.slots.get(0).hasItem();}
 private void line(GuiGraphicsExtractor g,String s,int x,int y){g.text(font,font.plainSubstrByWidth(s,276-x),x,y,0xff404040,false);}
 @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){line(g,title.getString(),8,6);line(g,"FOUP",58,23);line(g,"Water",100,23);line(g,"Clean",142,23);line(g,"Scrap",184,23);line(g,"Bucket",224,23);line(g,playerInventoryTitle.getString(),8,72);
  line(g,"FE: "+menu.energy()+" / "+menu.energyCapacity()+"  Water: "+menu.value(2)+" / "+menu.value(3)+" mB",8,174);
  String status=switch(menu.value(1)){case 1->"Cleaning";case 2->"Paused: supply FE";case 3->"Clear output slots (pending scrap: "+menu.value(4)+")";case 4->"Insert a FOUP containing wafers";case 5->"Requires "+menu.value(5)+" mB ordinary water";case 7->"Input changed: inspect retained contents";case 8->"Machine definition unavailable";default->"Ready: explicit discard required";};line(g,status,8,188);line(g,"Destroys ALL wafers. No chip recovery.",8,202);line(g,"No cleanroom or calibration required.",8,216);
 }
}
