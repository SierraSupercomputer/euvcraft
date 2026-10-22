package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;
/** Reuses all established FOUP/mask/resist locks and top-up protections. */
public final class ScannerOperatingMenu extends ProcessCellMenu {
 private final BlockPos panelPos;
 private final @Nullable ScannerMemberBlockEntity panel;
 private final @Nullable ScannerControllerBlockEntity scanner;
 private final ContainerData scannerData;
 public ScannerOperatingMenu(int id,Inventory inventory,@Nullable ScannerControllerBlockEntity scanner,BlockPos pos){
  super(ScannerMachines.OPERATING_MENU.get(),id,inventory,scanner,3);this.scanner=scanner;panelPos=pos.immutable();
  panel=scanner!=null&&scanner.getLevel()!=null&&scanner.getLevel().getBlockEntity(pos) instanceof ScannerMemberBlockEntity m?m:null;
  scannerData=scanner==null?new SimpleContainerData(6):new ContainerData(){public int get(int i){return switch(i){case 0->scanner.operatingCalibrated()?1:0;case 1->scanner.calibrationQuality();case 2->(int)Math.round(scanner.calibratedMultiplier()*1000);case 3->scanner.pureWater.amount();case 4->scanner.diagnosed()?1:0;case 5->scanner.immersion()?1:0;default->0;};}public void set(int i,int v){}public int getCount(){return 6;}};addDataSlots(scannerData);
 }
 public ScannerOperatingMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,null,buffer.readBlockPos());}
 @Override public boolean isOperatingPanel(){return true;}
 @Override public boolean stillValid(Player player){return super.stillValid(player)&&scanner!=null&&panel!=null&&scanner.getLevel().getBlockEntity(panelPos)==panel&&panel.scanner()==scanner&&panelPos.equals(scanner.getBlockPos().above())&&player.distanceToSqr(panelPos.getX()+.5,panelPos.getY()+.5,panelPos.getZ()+.5)<=64;}
 public int water(){return scannerData.get(3);}public boolean diagnosed(){return scannerData.get(4)==1;}public boolean immersion(){return scannerData.get(5)==1;}
 public boolean calibrated(){return scannerData.get(0)!=0;}
 public int quality(){return scannerData.get(1);}
 public int multiplierPermille(){return scannerData.get(2);}
}

