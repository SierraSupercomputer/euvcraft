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
 private DiagnosticFit diagnostic=DiagnosticFit.create(0);
 private String diagnosticHardware="";
 private int diagnosticQuality=-1;private double diagnosticMultiplier;
 public final com.euvcraft.lithmod.core.WaterTank pureWater=new com.euvcraft.lithmod.core.WaterTank(16000);
 public final net.minecraft.world.SimpleContainer diagnosticPaper=new net.minecraft.world.SimpleContainer(1);
 final PureJournal pureJournal=new PureJournal(this);
 private boolean immersion,diagnosticPlotted;
 public boolean immersion(){return immersion;}
 public DiagnosticFit diagnostic(){return diagnostic;}
 public int diagnosticQuality(){return diagnosticQuality;}
 public int diagnosticMinimum(){return definition().map(d->d.paramOrDefault("diagnostic_min_quality",50)).orElse(101);}
 private int diagnosticMin(){return definition().map(d->d.paramOrDefault("diagnostic_min_multiplier_permille",500)).orElse(0);}
 private int diagnosticMax(){return definition().map(d->d.paramOrDefault("diagnostic_max_multiplier_permille",1000)).orElse(0);}
 public double diagnosticPreview(){return diagnosticParams()?diagnostic.multiplier(diagnosticMin()/1000.0,diagnosticMax()/1000.0):0;}
 private boolean diagnosticParams(){return diagnosticMinimum()>=1&&diagnosticMinimum()<=100&&diagnosticMin()>0&&diagnosticMax()>=diagnosticMin()&&diagnosticMax()<=1000;}
 public boolean diagnosed(){return immersion&&hardwareValidated&&diagnosticHardware.equals(hardware)&&diagnosticQuality>=diagnosticMinimum()&&diagnosticQuality<=100&&diagnosticMultiplier>0&&diagnosticMultiplier<=1;}
 public boolean plotDiagnostic(int a,int b,int c){
  if(level==null||level.isClientSide())return false;revalidate();if(!isFormed()||!immersion||inputsLocked()||!diagnosticParams()||!diagnosticPaper.getItem(0).is(ModItems.BURN_PAPER.get())||a< -200||a>200||b< -200||b>200||c< -200||c>200)return false;
  var next=diagnostic.plot(a,b,c);diagnosticPaper.removeItem(0,1);diagnostic=next;diagnosticPlotted=true;diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;setChanged();return true;
 }
 public boolean acceptDiagnostic(){if(level==null||level.isClientSide())return false;revalidate();if(!isFormed()||!immersion||inputsLocked()||!diagnosticParams()||!diagnosticPlotted||diagnostic.quality()<diagnosticMinimum())return false;diagnosticHardware=hardware;diagnosticQuality=diagnostic.quality();diagnosticMultiplier=diagnosticPreview();setChanged();return true;}
 private int maskTier(){var mask=slot(SLOT_MASK).get(com.euvcraft.lithmod.mc.registry.ModDataComponents.MASK_DATA.get());return mask==null?0:mask.tierIndex();}
 public boolean operatingCalibrated(){return calibrated()&&(maskTier()!=5||diagnosed());}
 private int waterPerTick(){return definition().map(d->d.paramOrDefault("pure_water_per_tick_mb",2)).orElse(0);}
 @Override public int extractEnergy(int amount,boolean simulate){
  if(tierIndex()!=5)return super.extractEnergy(amount,simulate);
  int water=waterPerTick();if(water<1||water>pureWater.capacity()||pureWater.amount()<water)return 0;
  int drawn=super.extractEnergy(amount,simulate);if(!simulate&&drawn==amount&&drawn>0){pureWater.drainExact(water,false);setChanged();}return drawn;
 }
 static final class PureJournal extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer>{final ScannerControllerBlockEntity owner;PureJournal(ScannerControllerBlockEntity owner){this.owner=owner;}@Override protected Integer createSnapshot(){return owner.pureWater.amount();}@Override protected void revertToSnapshot(Integer n){owner.pureWater.restoreAmount(n);}@Override protected void onRootCommit(Integer n){owner.setChanged();}}

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
  boolean hasDiagnostic=false,hasInlet=false,hasImmersion=false;
  for(var pos:members())if(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m){
   if(m.structureKind()==BlockKind.DIAGNOSTIC_PANEL){hasDiagnostic=true;fingerprint.append("diagnostic:").append(m.installation());}
   if(m.structureKind()==BlockKind.WATER_INLET)hasInlet=true;
   if(m.getBlockState().getBlock()==ScannerMachines.IMMERSION_MODULE.get())hasImmersion=true;
  }
  immersion=hasDiagnostic&&hasInlet&&hasImmersion;
  String next=fingerprint.toString();hardwareValidated=positions.size()==5;
  modulePositions.clear();modulePositions.putAll(positions);
  if(!next.equals(hardware)){
   hardware=next;calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;
   alignment=BeamAlignment.create(next.hashCode());diagnostic=DiagnosticFit.create(next.hashCode());diagnosticPlotted=false;diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;setChanged();
  }
 }
 public boolean calibrated(){return hardwareValidated&&!hardware.isEmpty()&&hardware.equals(calibratedHardware)&&acceptedQuality>=minimumQuality()&&acceptedQuality<=100&&acceptedMultiplier>0&&acceptedMultiplier<=1;}
 public int calibrationQuality(){return acceptedQuality;}
 public double calibratedMultiplier(){return operatingCalibrated()?acceptedMultiplier*(maskTier()==5?diagnosticMultiplier:1.0):0;}
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
 @Override protected int utilityGeneration(){return immersion?2:1;}
 @Override protected boolean supportsTier(Tier tier){return tier.index()==4||tier.index()==5;}
 @Override protected boolean readyForAdmission(){return operatingCalibrated()&&validParameters()&&(maskTier()!=5||immersion&&diagnosticParams()&&waterPerTick()>0&&pureWater.amount()>=waterPerTick());}
 @Override protected double admissionYieldMultiplier(){acceptedHardware=hardware;setChanged();return acceptedMultiplier*(maskTier()==5?diagnosticMultiplier:1.0);}

 @Override public boolean structureFormed(){return isFormed()&&definition().isPresent()&&hardwareValidated&&(!inputsLocked()||hardware.equals(acceptedHardware));}
 @Override protected boolean inputsTemporarilyUnavailable(){return !isFormed();}
 @Override protected ItemStack equipment(int logicalSlot){
  BlockKind kind=switch(logicalSlot){case 3->BlockKind.EXPOSURE_MODULE;case 4->BlockKind.OPTICS_MODULE;case 5->BlockKind.STAGE_MODULE;case 6->BlockKind.FILTRATION_MODULE;case 7->BlockKind.METROLOGY_MODULE;default->BlockKind.OTHER;};
  var pos=modulePositions.get(kind);
  if(level==null||pos==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m)||m.structureKind()!=kind)return ItemStack.EMPTY;
  if(immersion)return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.IMMERSION_LIGHT_SOURCE.get());case OPTICS_MODULE->new ItemStack(ModItems.IMMERSION_OPTICS.get());case STAGE_MODULE->new ItemStack(ModItems.IMMERSION_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.IMMERSION_FILTRATION.get());case METROLOGY_MODULE->new ItemStack(ModItems.IMMERSION_METROLOGY.get());default->ItemStack.EMPTY;};
  return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.ARF_EXCIMER_LASER.get());case OPTICS_MODULE->new ItemStack(ModItems.ARF_DRY_LENS.get());case STAGE_MODULE->new ItemStack(ModItems.NANOPOSITIONING_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.ULPA_FILTER.get());case METROLOGY_MODULE->new ItemStack(ModItems.PRECISION_OVERLAY_SCOPE.get());default->ItemStack.EMPTY;};
 }
 @Override public void serverTick(){revalidate();pureWater.resize(Math.clamp(definition().map(d->d.paramOrDefault("pure_capacity_mb",16000)).orElse(16000),1,1000000));super.serverTick();}
 @Override protected void dropExtraContents(net.minecraft.world.level.Level level,BlockPos pos){super.dropExtraContents(level,pos);dropContainer(level,pos,diagnosticPaper);}
 @Override public void setRemoved(){formation.detach();super.setRemoved();}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new ScannerDashboardMenu(id,inventory,this,worldPosition,false);}
 @Override protected void loadAdditional(ValueInput input){
  super.loadAdditional(input);hardware=input.getString("scannerHardware").orElse("");calibratedHardware=input.getString("scannerCalibratedHardware").orElse("");acceptedHardware=input.getString("scannerAcceptedHardware").orElse("");
  acceptedQuality=input.getInt("scannerQuality").orElse(-1);acceptedMultiplier=input.getDoubleOr("scannerMultiplier",0);
  try{alignment=new BeamAlignment(input.getIntOr("scannerX",0),input.getIntOr("scannerY",0),input.getIntOr("scannerFocus",0),input.getIntOr("scannerTargetX",0),input.getIntOr("scannerTargetY",0),input.getIntOr("scannerTargetFocus",0));}catch(IllegalArgumentException e){alignment=BeamAlignment.create(0);calibratedHardware="";}
  boolean diagnosticValid=true;try{diagnostic=new DiagnosticFit(input.getIntOr("diagnosticA",0),input.getIntOr("diagnosticB",0),input.getIntOr("diagnosticC",0),input.getIntOr("diagnosticTargetA",0),input.getIntOr("diagnosticTargetB",0),input.getIntOr("diagnosticTargetC",0));}catch(IllegalArgumentException invalid){diagnostic=DiagnosticFit.create(0);diagnosticValid=false;}
  diagnosticPlotted=input.getBooleanOr("diagnosticPlotted",false);
  diagnosticHardware=input.getString("diagnosticHardware").orElse("");diagnosticQuality=input.getIntOr("diagnosticQuality",-1);diagnosticMultiplier=input.getDoubleOr("diagnosticMultiplier",0);
  pureWater.restoreAmount(Math.clamp(input.getIntOr("scannerPureWater",0),0,1000000));diagnosticPaper.setItem(0,input.read("diagnosticPaper",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
  if(!diagnosticValid||!diagnosticPlotted){diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;}
  hardwareValidated=false;modulePositions.clear();
 }
 @Override protected void saveAdditional(ValueOutput out){
  super.saveAdditional(out);
  out.putBoolean("diagnosticPlotted",diagnosticPlotted);out.putString("diagnosticHardware",diagnosticHardware);out.putInt("diagnosticQuality",diagnosticQuality);out.putDouble("diagnosticMultiplier",diagnosticMultiplier);out.putInt("diagnosticA",diagnostic.a());out.putInt("diagnosticB",diagnostic.b());out.putInt("diagnosticC",diagnostic.c());out.putInt("diagnosticTargetA",diagnostic.targetA());out.putInt("diagnosticTargetB",diagnostic.targetB());out.putInt("diagnosticTargetC",diagnostic.targetC());out.putInt("scannerPureWater",pureWater.amount());out.store("diagnosticPaper",ItemStack.OPTIONAL_CODEC,diagnosticPaper.getItem(0));out.putString("scannerHardware",hardware);out.putString("scannerCalibratedHardware",calibratedHardware);out.putString("scannerAcceptedHardware",acceptedHardware);out.putInt("scannerQuality",acceptedQuality);out.putDouble("scannerMultiplier",acceptedMultiplier);
  out.putInt("scannerX",alignment.x());out.putInt("scannerY",alignment.y());out.putInt("scannerFocus",alignment.focus());out.putInt("scannerTargetX",alignment.targetX());out.putInt("scannerTargetY",alignment.targetY());out.putInt("scannerTargetFocus",alignment.targetFocus());
 }
}




