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
 private boolean immersion,diagnosticPlotted,multipatterning;
 private CalibrationExposure.State exposure=CalibrationExposure.recalibrated();
 public boolean multipatterning(){return multipatterning;}
 public boolean contaminationInvalidated(){return exposure.invalidated();}
 private void acceptedRecalibration(){if(calibrated()&&diagnosed())exposure=CalibrationExposure.recalibrated();}
 private void sampleCalibrationExposure(com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.Sample sample){
  if(tierIndex()!=6)return;boolean dirty=sample.room()==null||sample.room().ppm()>definition().map(d->d.paramOrDefault("contamination_ppm",2500)).orElse(2500);
  var next=CalibrationExposure.advance(exposure,dirty,true,definition().map(d->d.paramOrDefault("contamination_ticks",1200)).orElse(1200));
  if(!exposure.invalidated()&&next.invalidated())resetContaminatedCalibration();
  exposure=next;setChanged();
 }
 /** Runs exactly once at dirty-exposure invalidation; existing lot keeps its frozen contract. */
 private void resetContaminatedCalibration(){
  var rng=java.util.concurrent.ThreadLocalRandom.current();
  var previousBeam=alignment;var previousFit=diagnostic;
  var beam=BeamAlignment.create(rng.nextLong());
  for(int attempt=0;attempt<128;attempt++){
   var oldSettings=new BeamAlignment(previousBeam.x(),previousBeam.y(),previousBeam.focus(),beam.targetX(),beam.targetY(),beam.targetFocus());
   if(oldSettings.quality()<minimumQuality()&&(beam.targetX()!=previousBeam.targetX()||beam.targetY()!=previousBeam.targetY()||beam.targetFocus()!=previousBeam.targetFocus()))break;
   beam=BeamAlignment.create(rng.nextLong());
  }
  // Deterministic far-corner fallback keeps permissive configured quality floors solvable.
  int worst=101;BeamAlignment far=beam;
  for(int x:new int[]{-5,5})for(int y:new int[]{-5,5})for(int f:new int[]{-5,5}){
   var candidate=new BeamAlignment(previousBeam.x(),previousBeam.y(),previousBeam.focus(),x,y,f);
   if(candidate.quality()<worst){worst=candidate.quality();far=candidate;}
  }
  if(new BeamAlignment(previousBeam.x(),previousBeam.y(),previousBeam.focus(),beam.targetX(),beam.targetY(),beam.targetFocus()).quality()>=minimumQuality())beam=far;
  if(beam.quality()>=minimumQuality())beam=new BeamAlignment(-10,-10,10,beam.targetX(),beam.targetY(),beam.targetFocus());
  alignment=beam;
  var fit=DiagnosticFit.create(rng.nextLong());
  for(int attempt=0;attempt<128;attempt++){
   if(fit.plot(previousFit.a(),previousFit.b(),previousFit.c()).quality()<diagnosticMinimum()&&(fit.targetA()!=previousFit.targetA()||fit.targetB()!=previousFit.targetB()||fit.targetC()!=previousFit.targetC()))break;
   fit=DiagnosticFit.create(rng.nextLong());
  }
  worst=101;DiagnosticFit farFit=fit;
  for(int x:new int[]{-100,100})for(int y:new int[]{-100,100})for(int c:new int[]{-100,100}){
   var candidate=new DiagnosticFit(0,0,0,x,y,c);int quality=candidate.plot(previousFit.a(),previousFit.b(),previousFit.c()).quality();
   if(quality<worst){worst=quality;farFit=candidate;}
  }
  if(fit.plot(previousFit.a(),previousFit.b(),previousFit.c()).quality()>=diagnosticMinimum())fit=farFit;
  if(fit.targetA()==previousFit.targetA()&&fit.targetB()==previousFit.targetB()&&fit.targetC()==previousFit.targetC())fit=new DiagnosticFit(0,0,0,Math.floorMod(previousFit.targetA()+101,201)-100,fit.targetB(),fit.targetC());
  diagnostic=fit;diagnosticPlotted=false;
  calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;
  diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;
 }

 public boolean immersion(){return immersion;}
 public DiagnosticFit diagnostic(){return diagnostic;}public boolean diagnosticPlotted(){return diagnosticPlotted;}
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
 public boolean acceptDiagnostic(){if(level==null||level.isClientSide())return false;revalidate();if(!isFormed()||!immersion||inputsLocked()||!diagnosticParams()||!diagnosticPlotted||diagnostic.quality()<diagnosticMinimum())return false;diagnosticHardware=hardware;diagnosticQuality=diagnostic.quality();diagnosticMultiplier=diagnosticPreview();acceptedRecalibration();setChanged();return true;}
 private int maskTier(){var mask=slot(SLOT_MASK).get(com.euvcraft.lithmod.mc.registry.ModDataComponents.MASK_DATA.get());return mask==null?0:mask.tierIndex();}
 public boolean operatingCalibrated(){return calibrated()&&(maskTier()<5||diagnosed())&&(maskTier()!=6||multipatterning&&!exposure.invalidated());}
 public int waterPerTick(){return definition().map(d->d.paramOrDefault("pure_water_per_tick_mb",2)).orElse(0);}
 @Override public int extractEnergy(int amount,boolean simulate){
  if(amount<0)return 0;if(tierIndex()<5)return super.extractEnergy(amount,simulate);
  int water=waterPerTick();if(water<1||water>pureWater.capacity()||pureWater.amount()<water)return 0;
  int drawn=super.extractEnergy(amount,simulate);if(!simulate&&drawn==amount){pureWater.drainExact(water,false);setChanged();}return drawn;
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
  boolean hasDiagnostic=false,hasInlet=false,hasImmersion=false; multipatterning=false;
  for(var pos:members())if(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m){
   if(m.structureKind()==BlockKind.DIAGNOSTIC_PANEL){hasDiagnostic=true;fingerprint.append("diagnostic:").append(m.installation());}
   if(m.structureKind()==BlockKind.WATER_INLET)hasInlet=true;
   if(m.structureKind()==BlockKind.MULTIPATTERNING_MODULE){multipatterning=true;fingerprint.append("multipattern:").append(m.installation());}
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
 public double calibratedMultiplier(){return operatingCalibrated()?acceptedMultiplier*(maskTier()>=5?diagnosticMultiplier:1.0):0;}
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
  if(control==6){if(alignment.quality()<minimumQuality())return false;calibratedHardware=hardware;acceptedQuality=alignment.quality();acceptedMultiplier=previewMultiplier();acceptedRecalibration();}
  else {alignment=alignment.adjust(control);calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;}
  setChanged();return true;
 }
 @Override protected int utilityGeneration(){return immersion?2:1;}
 @Override protected int admissionPasses(Tier tier){return tier.index()==6?definition().map(d->d.paramOrDefault("multipattern_passes",2)).orElse(0):1;}
 @Override protected boolean supportsTier(Tier tier){return tier.index()==4||tier.index()==5||tier.index()==6;}
 /** Public reason for admission/pause; independent of hidden rolls or private fit targets. */
 public int operatingIssue(){
  if(!isFormed())return RefusalReason.Code.STRUCTURE_INCOMPLETE.ordinal()+1;
  if(definition().isEmpty()||!hardwareValidated)return 0;
  int tier=maskTier();
  if(!inputsLocked()&&tier>=4&&tier<=6&&!operatingCalibrated())return RefusalReason.Code.CALIBRATION_REQUIRED.ordinal()+1;
  if(tier>=5&&tier<=6){
   if(!roomSample().available())return RefusalReason.Code.ROOM_UNAVAILABLE.ordinal()+1;
   int cost=waterPerTick();if(cost>0&&cost<=pureWater.capacity()&&pureWater.amount()<cost)return RefusalReason.Code.ULTRAPURE_WATER_REQUIRED.ordinal()+1;
  }
  return 0;
 }
 @Override protected RefusalReason admissionBlocker(Tier tier){int issue=operatingIssue();return issue==0?null:new RefusalReason(RefusalReason.Code.values()[issue-1]);}
 @Override protected boolean readyForAdmission(){return operatingCalibrated()&&validParameters()&&(maskTier()<5||immersion&&diagnosticParams()&&waterPerTick()>0&&waterPerTick()<=pureWater.capacity()&&pureWater.amount()>=waterPerTick());}
 @Override protected double admissionYieldMultiplier(){acceptedHardware=hardware;setChanged();return acceptedMultiplier*(maskTier()>=5?diagnosticMultiplier:1.0);}

 @Override public boolean structureFormed(){return (tierIndex()<5||roomSample().available())&&isFormed()&&definition().isPresent()&&hardwareValidated&&(!inputsLocked()||hardware.equals(acceptedHardware))&&(tierIndex()<5||waterPerTick()>=1&&waterPerTick()<=pureWater.capacity()&&pureWater.amount()>=waterPerTick());}
 @Override protected boolean inputsTemporarilyUnavailable(){return !isFormed();}
 @Override protected ItemStack equipment(int logicalSlot){
  BlockKind kind=switch(logicalSlot){case 3->BlockKind.EXPOSURE_MODULE;case 4->BlockKind.OPTICS_MODULE;case 5->BlockKind.STAGE_MODULE;case 6->BlockKind.FILTRATION_MODULE;case 7->BlockKind.METROLOGY_MODULE;default->BlockKind.OTHER;};
  var pos=modulePositions.get(kind);
  if(level==null||pos==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ScannerMemberBlockEntity m)||m.structureKind()!=kind)return ItemStack.EMPTY;
  if(multipatterning&&maskTier()==6)return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.MULTIPATTERN_LIGHT_SOURCE.get());case OPTICS_MODULE->new ItemStack(ModItems.MULTIPATTERN_OPTICS.get());case STAGE_MODULE->new ItemStack(ModItems.MULTIPATTERN_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.MULTIPATTERN_FILTRATION.get());case METROLOGY_MODULE->new ItemStack(ModItems.MULTIPATTERN_METROLOGY.get());default->ItemStack.EMPTY;};
  if(immersion)return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.IMMERSION_LIGHT_SOURCE.get());case OPTICS_MODULE->new ItemStack(ModItems.IMMERSION_OPTICS.get());case STAGE_MODULE->new ItemStack(ModItems.IMMERSION_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.IMMERSION_FILTRATION.get());case METROLOGY_MODULE->new ItemStack(ModItems.IMMERSION_METROLOGY.get());default->ItemStack.EMPTY;};
  return switch(kind){case EXPOSURE_MODULE->new ItemStack(ModItems.ARF_EXCIMER_LASER.get());case OPTICS_MODULE->new ItemStack(ModItems.ARF_DRY_LENS.get());case STAGE_MODULE->new ItemStack(ModItems.NANOPOSITIONING_STAGE.get());case FILTRATION_MODULE->new ItemStack(ModItems.ULPA_FILTER.get());case METROLOGY_MODULE->new ItemStack(ModItems.PRECISION_OVERLAY_SCOPE.get());default->ItemStack.EMPTY;};
 }
 private com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.Sample roomSample(){return level instanceof net.minecraft.server.level.ServerLevel server?com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.sample(server,members()):new com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.Sample(false,500,null);}
 @Override public FoupContents processingTick(FoupContents lot){if(tierIndex()<5)return lot;var sample=roomSample();if(!sample.available())throw new IllegalStateException("Room chunk unavailable");sampleCalibrationExposure(sample);var next=lot.withRoomExposure(lot.roomExposure().sample(sample.yieldPermille()),0);storeRoomLot(next);return next;}
 private void storeRoomLot(FoupContents lot){var carrier=slot(SLOT_FOUP).copy();carrier.set(com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS.get(),lot);setSlot(SLOT_FOUP,carrier);}
 @Override public FoupContents applyLayer(FoupContents lot,int layer,double defectFactor){var next=super.applyLayer(lot,layer,defectFactor);if(tierIndex()>=5){var tier=com.euvcraft.lithmod.mc.data.TierDataLoader.getRegistry().byIndex(tierIndex()).orElseThrow();if(layer==tier.layers()){next=CleanroomYield.finish(next,tier);storeRoomLot(next);}}return next;}
 @Override public void serverTick(){revalidate();pureWater.resize(Math.clamp(definition().map(d->d.paramOrDefault("pure_capacity_mb",16000)).orElse(16000),1,1000000));super.serverTick();}
 @Override protected void dropExtraContents(net.minecraft.world.level.Level level,BlockPos pos){super.dropExtraContents(level,pos);dropContainer(level,pos,diagnosticPaper);}
 @Override public void setRemoved(){formation.unload();super.setRemoved();}
 @Override protected AbstractContainerMenu createMachineMenu(int id,Inventory inventory,FabMachineBlockEntity machine){return new ScannerDashboardMenu(id,inventory,this,worldPosition,false);}
 @Override protected void loadAdditional(ValueInput input){
  super.loadAdditional(input);hardware=input.getString("scannerHardware").orElse("");calibratedHardware=input.getString("scannerCalibratedHardware").orElse("");acceptedHardware=input.getString("scannerAcceptedHardware").orElse("");
  acceptedQuality=input.getInt("scannerQuality").orElse(-1);acceptedMultiplier=input.getDoubleOr("scannerMultiplier",0);
  try{alignment=new BeamAlignment(input.getIntOr("scannerX",0),input.getIntOr("scannerY",0),input.getIntOr("scannerFocus",0),input.getIntOr("scannerTargetX",0),input.getIntOr("scannerTargetY",0),input.getIntOr("scannerTargetFocus",0));}catch(IllegalArgumentException e){alignment=BeamAlignment.create(0);calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;/* acceptedHardware belongs to the frozen in-flight contract, not new calibration. */}
  boolean diagnosticValid=true;try{diagnostic=new DiagnosticFit(input.getIntOr("diagnosticA",0),input.getIntOr("diagnosticB",0),input.getIntOr("diagnosticC",0),input.getIntOr("diagnosticTargetA",0),input.getIntOr("diagnosticTargetB",0),input.getIntOr("diagnosticTargetC",0));}catch(IllegalArgumentException invalid){diagnostic=DiagnosticFit.create(0);diagnosticValid=false;}
  diagnosticPlotted=input.getBooleanOr("diagnosticPlotted",false);
  diagnosticHardware=input.getString("diagnosticHardware").orElse("");diagnosticQuality=input.getIntOr("diagnosticQuality",-1);diagnosticMultiplier=input.getDoubleOr("diagnosticMultiplier",0);
  pureWater.restoreAmount(Math.clamp(input.getIntOr("scannerPureWater",0),0,1000000));diagnosticPaper.setItem(0,input.read("diagnosticPaper",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
  if(!diagnosticValid||!diagnosticPlotted){diagnosticPlotted=false;diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;}
  exposure=new CalibrationExposure.State(Math.max(0,input.getIntOr("calibrationDirtyTicks",0)),input.getBooleanOr("calibrationInvalidated",false));
  hardwareValidated=false;modulePositions.clear();
 }
 @Override protected void saveAdditional(ValueOutput out){
  super.saveAdditional(out);
  out.putInt("calibrationDirtyTicks",exposure.dirtyTicks());out.putBoolean("calibrationInvalidated",exposure.invalidated());
  out.putBoolean("diagnosticPlotted",diagnosticPlotted);out.putString("diagnosticHardware",diagnosticHardware);out.putInt("diagnosticQuality",diagnosticQuality);out.putDouble("diagnosticMultiplier",diagnosticMultiplier);out.putInt("diagnosticA",diagnostic.a());out.putInt("diagnosticB",diagnostic.b());out.putInt("diagnosticC",diagnostic.c());out.putInt("diagnosticTargetA",diagnostic.targetA());out.putInt("diagnosticTargetB",diagnostic.targetB());out.putInt("diagnosticTargetC",diagnostic.targetC());out.putInt("scannerPureWater",pureWater.amount());out.store("diagnosticPaper",ItemStack.OPTIONAL_CODEC,diagnosticPaper.getItem(0));out.putString("scannerHardware",hardware);out.putString("scannerCalibratedHardware",calibratedHardware);out.putString("scannerAcceptedHardware",acceptedHardware);out.putInt("scannerQuality",acceptedQuality);out.putDouble("scannerMultiplier",acceptedMultiplier);
  out.putInt("scannerX",alignment.x());out.putInt("scannerY",alignment.y());out.putInt("scannerFocus",alignment.focus());out.putInt("scannerTargetX",alignment.targetX());out.putInt("scannerTargetY",alignment.targetY());out.putInt("scannerTargetFocus",alignment.targetFocus());
 }
}






