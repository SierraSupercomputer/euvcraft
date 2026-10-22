package com.euvcraft.lithmod.mc.multiblock.scanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
/** No item inventory in either the equipment dashboard or the calibration console. */
public final class ScannerDashboardMenu extends AbstractContainerMenu {
 private final @Nullable ScannerControllerBlockEntity scanner;
 private final @Nullable ScannerMemberBlockEntity panel;
 private final BlockPos consolePos;
 private final boolean calibration;
 private final ContainerData values;

 public ScannerDashboardMenu(int id,Inventory inventory,@Nullable ScannerControllerBlockEntity scanner,BlockPos pos,boolean calibration){
  super(ScannerMachines.DASHBOARD_MENU.get(),id);this.scanner=scanner;this.calibration=calibration;consolePos=pos.immutable();
  panel=scanner!=null&&scanner.getLevel()!=null&&scanner.getLevel().getBlockEntity(pos) instanceof ScannerMemberBlockEntity m?m:null;
  values=scanner==null?new SimpleContainerData(13):new ContainerData(){
   public int get(int i){var a=scanner.alignment();return switch(i){case 0->scanner.isFormed()?1:0;case 1->scanner.calibrated()?1:0;case 2->a.quality();case 3->(int)Math.round(scanner.previewMultiplier()*1000);case 4->a.spotX();case 5->a.spotY();case 6->a.focusError();case 7->a.x();case 8->a.y();case 9->a.focus();case 10->scanner.inputsLocked()?1:0;case 11->scanner.minimumQuality();case 12->scanner.calibrationQuality();default->0;};}
   public void set(int i,int v){}public int getCount(){return 13;}
  };addDataSlots(values);
 }
 public ScannerDashboardMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,null,buffer.readBlockPos(),buffer.readBoolean());}
 public boolean calibration(){return calibration;}
 public int value(int i){return values.get(i);}
 @Override public boolean stillValid(Player player){
  if(scanner==null||scanner.getLevel()!=player.level()||scanner.isRemoved()||scanner.getLevel().getBlockEntity(scanner.getBlockPos())!=scanner||player.distanceToSqr(consolePos.getX()+.5,consolePos.getY()+.5,consolePos.getZ()+.5)>64)return false;
  return calibration?panel!=null&&scanner.getLevel().getBlockEntity(consolePos)==panel&&panel.scanner()==scanner&&panel.structureKind()==com.euvcraft.lithmod.core.multiblock.BlockKind.CALIBRATION_PANEL:consolePos.equals(scanner.getBlockPos());
 }
 @Override public boolean clickMenuButton(Player player,int id){
  if(!calibration||!(player instanceof net.minecraft.server.level.ServerPlayer)||player.containerMenu!=this||!stillValid(player)||id<0||id>6)return false;
  return scanner.calibrationControl(id);
 }
 @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
