package com.euvcraft.lithmod.mc.machine.screen;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOperatingPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class AdvancedOperatingPanelScreen extends AdvancedMachineScreen<AdvancedOperatingPanelMenu>{
 private Button binning;
 public AdvancedOperatingPanelScreen(AdvancedOperatingPanelMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
 @Override protected boolean formed(){return menu.formed();}
 @Override protected boolean cabinet(){return false;}
 @Override protected void init(){
  super.init();
  if(!menu.deposition())binning=addRenderableWidget(Button.builder(Component.empty(),button->{
   if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1);
  }).bounds(leftPos+8,topPos+194,160,20).build());
 }
 @Override protected void containerTick(){
  super.containerTick();
  if(binning!=null){
   binning.active=!menu.active();
   binning.setMessage(Component.translatable("button.lithmod.t4.high_binning",Component.translatable(menu.highBinning()?"state.lithmod.t4.on":"state.lithmod.t4.off")));
  }
 }
 @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY){
  super.extractLabels(graphics,mouseX,mouseY);
  if(!menu.deposition())line(graphics,Component.translatable("label.lithmod.t4.binning_hint"),8,222,160);
 }
}
