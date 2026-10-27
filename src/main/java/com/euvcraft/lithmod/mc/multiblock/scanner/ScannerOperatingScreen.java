package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class ScannerOperatingScreen extends AdvancedMachineScreen<ScannerOperatingMenu> {
 private Button abort;
 public ScannerOperatingScreen(ScannerOperatingMenu menu,Inventory inventory,Component title){super(menu,inventory,title,260);}
 @Override protected boolean formed(){return menu.formed()!=0;}
 @Override protected boolean cabinet(){return false;}
 @Override protected void init(){super.init();abort=addRenderableWidget(Button.builder(Component.translatable("button.lithmod.process_cell.abort"),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);}).bounds(leftPos+8,topPos+232,160,20).build());}
 @Override protected void containerTick(){super.containerTick();abort.active=menu.inputsLocked();}
 @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY){
  line(g,Component.translatable("block.lithmod.scanner_operating_panel"),8,6,160);
  line(g,Component.literal("FOUP     Mask    Resist"),24,22,142);
  line(g,Component.literal(menu.energy()+" / "+menu.energyCapacity()+" FE"),26,56,142);
  line(g,playerInventoryTitle,8,72,160);
  line(g,Component.translatable(formed()?"state.lithmod.process_cell.formed":"state.lithmod.process_cell.unformed"),8,168,160);
  if(!menu.calibrated()&&!menu.inputsLocked())
   line(g,Component.literal(menu.contaminationInvalidated()?"Recalibrate: Beam + Diagnostic":menu.immersion()?"Use Beam + Diagnostic Panels":"Use the Calibration Panel"),8,180,160);
  else line(g,Component.literal(menu.inputsLocked()?"Calibration fixed for this lot":"Calibration yield: "+menu.multiplierPermille()/10.0+"%"),8,180,160);
  line(g,statusText(),8,192,160);
  line(g,Component.literal("Layer "+menu.currentLayer()+"/"+menu.totalLayers()+(menu.immersion()?"  UPW "+menu.water():"")),8,204,160);
  int ticks=menu.ticks(),progress=menu.progress();
  line(g,Component.literal("Exposure: "+progress+"/"+ticks+" t"+(ticks>0?" ("+(int)(100L*progress/ticks)+"%)":"")),8,216,160);

