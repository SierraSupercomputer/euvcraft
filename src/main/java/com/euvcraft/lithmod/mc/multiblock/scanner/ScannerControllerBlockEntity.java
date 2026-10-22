package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Physical equipment has no phantom inventory. Calibration belongs to this installation. */
public final class ScannerControllerBlockEntity extends ProcessCellControllerBlockEntity implements AdvancedOwner {
 private final AdvancedFormation formation=new AdvancedFormation(this);
 private final Map<BlockKind,BlockPos> modulePositions=new EnumMap<>(BlockKind.class);
 private String hardware="",calibratedHardware="",acceptedHardware="";
 private BeamAlignment alignment=BeamAlignment.create(0);
 private int acceptedQuality=-1;
 private double acceptedMultiplier=0;
 private boolean hardwareValidated;
 public ScannerControllerBlockEntity(BlockPos pos,BlockState state){super(ScannerMachines.CONTROLLER_BE.get(),pos,state,3);}
 @Override protected String machineId(){return "lithography_scanner";}
 @Override public Component getDisplayName(){return Component.translatable("block.lithmod.scanner_controller");}
 @Override public FabMachineBlockEntity machine(){return this;}
 @Override public AdvancedFormation formation(){return formation;}
 @Override public boolean large(){return true;}
 @Override public boolean isFormed(){return formation.formed();}
 @Override public List<BlockPos> members(){return formation.members();}
 @Override public String failureReason(){return formation.failureReason();}
 @Override public MatchResult matchStructure(MultiblockPos anchor,MultiblockDirection facing,java.util.function.Function<MultiblockPos,BlockKind> lookup){return ScannerStructureMatcher.match(anchor,facing,lookup);}
 @Override public void revalidate(){formation.validate();refreshHardware();}
 private static boolean module(BlockKind kind){return switch(kind){case EXPOSURE_MODULE,OPTICS_MODULE,STAGE_MODULE,FILTRATION_MODULE,METROLOGY_MODULE->true;default->false;};}
 private void refreshHardware(){
  if(level==null||level.isClientSide()||!isFormed())return;
  var positions=new EnumMap<BlockKind,BlockPos>(BlockKind.class);var fingerprint=new StringBuilder();
  for(var pos:members())if(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m&&module(m.structureKind())){
   positions.put(m.structureKind(),pos);fingerprint.append(m.structureKind().name()).append('@').append(pos.asLong()).append(':').append(m.installation()).append(';');
  }
  String next=fingerprint.toString();hardwareValidated=positions.size()==5;
  modulePositions.clear();modulePositions.putAll(positions);
  if(!next.equals(hardware)){
   hardware=next;calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;
   alignment=BeamAlignment.create(next.hashCode());setChanged();
  }
 }
 public boolean calibrated(){return hardwareValidated&&!hardware.isEmpty()&&hardware.equals(calibratedHardware)&&acceptedQuality>=minimumQuality()&&acceptedMultiplier>0&&acceptedMultiplier<=1;}
 public int calibrationQuality(){return acceptedQuality;}
 public double calibratedMultiplier(){return calibrated()?acceptedMultiplier:0;}
 public BeamAlignment alignment(){return alignment;}
 public int minimumQuality(){return definition().map(d->d.paramOrDefault("calibration_min_quality",50)).orElse(101);}
 private boolean validParameters(){return minimumQuality()>=1&&minimumQuality()<=100&&minPermille()>0&&maxPermille()>=minPermille()&&maxPermille()<=1000;}
 private int minPermille(){return definition().map(d->d.paramOrDefault("calibration_min_multiplier_permille",500)).orElse(0);}
 private int maxPermille(){return definition().map(d->d.paramOrDefault("calibration_max_multiplier_permille",1000)).orElse(0);}
 public double previewMultiplier(){return validParameters()?alignment.multiplier(minPermille()/1000.0,maxPermille()/1000.0):0;}
 /** Called only after the menu has checked the player's identity, distance and actual panel. */
 boolean calibrationControl(int control){
  if(level==null||level.isClientSide()||control<0||control>6)return false;
  revalidate();if(!isFormed()||inputsLocked()||!validParameters())return false;
  if(control==6){if(alignment.quality()<minimumQuality())return false;calibratedHardware=hardware;acceptedQuality=alignment.quality();acceptedMultiplier=previewMultiplier();}
  else {alignment=alignment.adjust(control);calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;}
  setChanged();return true;
 }
 @Override protected boolean supportsTier(Tier tier){return tier.index()==4;}
 @Override protected boolean readyForAdmission(){return calibrated()&&validParameters();}
 @Override protected double admissionYieldMultiplier(){acceptedHardware=hardware;setChanged();return acceptedMultiplier;}
 @Override protected String admissionRefusal(){return "Calibration required—use the Calibration Panel";}
 @Override public boolean structureFormed(){return isFormed()&&definition().isPresent()&&hardwareValidated&&(!inputsLocked()||hardware.equals(acceptedHardware));}
 @Override protected ItemStack equipment(int logicalSlot){
  BlockKind kind=switch(logicalSlot){case 3->BlockKind.EXPOSURE_MODULE;case 4->BlockKind.OPTICS_MODULE;case 5->BlockKind.STAGE_MODULE;case 6->BlockKind.FILTRATION_MODULE;case 7->BlockKind.METROLOGY_MODULE;default->BlockKind.OTHER;};
  var pos=modulePositions.get(kind);
  if(level==null||pos==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m)||m.structureKind()!=kind)return ItemStack.EMPTY;
  return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.ARF_EXCIMER_LASER.get());case OPTICS_MODULE->new ItemStack(ModItems.ARF_DRY_LENS.get());case STAGE_MODULE->new ItemStack(ModItems.NANOPOSITIONING_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.ULPA_FILTER.get());case METROLOGY_MODULE->new ItemStack(ModItems.PRECISION_OVERLAY_SCOPE.get());default->ItemStack.EMPTY;};
 }
 @Override public void serverTick(){revalidate();super.serverTick();}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new ScannerDashboardMenu(id,inventory,this,worldPosition,false);}
 @Override protected void loadAdditional(ValueInput input){
  super.loadAdditional(input);hardware=input.getString("scannerHardware").orElse("");calibratedHardware=input.getString("scannerCalibratedHardware").orElse("");acceptedHardware=input.getString("scannerAcceptedHardware").orElse("");
  acceptedQuality=input.getInt("scannerQuality").orElse(-1);acceptedMultiplier=input.getDoubleOr("scannerMultiplier",0);
  try{alignment=new BeamAlignment(input.getIntOr("scannerX",0),input.getIntOr("scannerY",0),input.getIntOr("scannerFocus",0),input.getIntOr("scannerTargetX",0),input.getIntOr("scannerTargetY",0),input.getIntOr("scannerTargetFocus",0));}catch(IllegalArgumentException e){alignment=BeamAlignment.create(0);calibratedHardware="";}
  hardwareValidated=false;modulePositions.clear();
 }
 @Override protected void saveAdditional(ValueOutput out){
  super.saveAdditional(out);out.putString("scannerHardware",hardware);out.putString("scannerCalibratedHardware",calibratedHardware);out.putString("scannerAcceptedHardware",acceptedHardware);out.putInt("scannerQuality",acceptedQuality);out.putDouble("scannerMultiplier",acceptedMultiplier);
  out.putInt("scannerX",alignment.x());out.putInt("scannerY",alignment.y());out.putInt("scannerFocus",alignment.focus());out.putInt("scannerTargetX",alignment.targetX());out.putInt("scannerTargetY",alignment.targetY());out.putInt("scannerTargetFocus",alignment.targetFocus());
 }
}
