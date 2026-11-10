package com.euvcraft.lithmod.mc.machine.screen;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOperatingPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class AdvancedOperatingPanelScreen extends AdvancedMachineScreen<AdvancedOperatingPanelMenu>{
 public AdvancedOperatingPanelScreen(AdvancedOperatingPanelMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
 @Override protected boolean formed(){return menu.formed();}
 @Override protected boolean cabinet(){return false;}
 @Override protected Component formationLabel(){
  if(menu.formed()&&!menu.deposition()){
   int offset=menu.state()-com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity.STATE_REFUSED_BASE;
   if(offset>=0&&offset<com.euvcraft.lithmod.core.PackagingRefusalReason.values().length)return Component.translatable(com.euvcraft.lithmod.mc.machine.packaging.TestPackageScreen.refusalKey(com.euvcraft.lithmod.core.PackagingRefusalReason.values()[offset]));
  }
  return super.formationLabel();
 }
 @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY){
  super.extractLabels(graphics,mouseX,mouseY);
  if(menu.highBinningStation())line(graphics,Component.translatable("label.lithmod.t4.binning_hint"),8,206,160);
 }
}
