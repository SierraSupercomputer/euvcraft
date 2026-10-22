package com.euvcraft.lithmod.mc.machine.screen;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
/** All status text remains inside the opaque window, below the player's fixed inventory. */
public abstract class AdvancedMachineScreen<M extends FabMachineMenu> extends FabMachineScreen<M> {
 protected AdvancedMachineScreen(M menu,Inventory inventory,Component title){
  this(menu,inventory,title,238);
 }
 protected AdvancedMachineScreen(M menu,Inventory inventory,Component title,int height){
  super(menu,inventory,title,176,height);inventoryLabelY=72;
 }
 protected abstract boolean formed();
 protected boolean cabinet(){return true;}
 @Override protected int energyBarX(){return 12;}
 @Override protected int energyBarY(){return 18;}
 @Override protected int progressBarX(){return 78;}
 @Override protected int progressBarY(){return 178;}
 @Override protected boolean hasProgressBar(){return false;}
 protected void line(GuiGraphicsExtractor graphics,Component text,int x,int y,int width){
  graphics.text(font,font.plainSubstrByWidth(text.getString(),width),x,y,0xFF404040,false);
 }
 @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY){
  line(graphics,title,8,6,160);
  line(graphics,playerInventoryTitle,8,72,160);
  line(graphics,Component.literal(menu.energy()+" / "+menu.energyCapacity()+" FE"),30,20,138);
  if(cabinet())line(graphics,Component.translatable("label.lithmod.t4.cabinet"),30,42,138);
  line(graphics,Component.translatable(formed()?"state.lithmod.process_cell.formed":"state.lithmod.process_cell.unformed"),8,168,160);
  line(graphics,Component.translatable("screen.lithmod.progress",menu.progress(),menu.ticks()),8,180,160);
 }
}
