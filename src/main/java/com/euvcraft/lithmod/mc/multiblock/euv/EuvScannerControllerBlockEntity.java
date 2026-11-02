package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.*;
import java.util.function.Function;
import com.euvcraft.lithmod.core.cleanroom.CleanroomExposure;
import com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms;
import com.euvcraft.lithmod.mc.cleanroom.CleanroomControllerBlockEntity;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.*;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.SimpleContainer;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EUV Scanner controller block entity.
 * Handles 5x7x5 multiblock, single exposure pass/layer, physical modules, vacuum, UPW,
 * calibration, diagnostics, cleanroom breach contamination, and light source reservation.
 */
public class EuvScannerControllerBlockEntity extends ProcessCellControllerBlockEntity implements EuvFormationOwner, com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumConsumer {

    // Formation
    private final EuvFormation formation = new EuvFormation(this);
    private boolean formed;

    // Module tracking
    private final Map<BlockKind, BlockPos> modulePositions = new EnumMap<>(BlockKind.class);
    private String hardwareFingerprint = "";
    private String calibratedHardware = "";
    private String acceptedHardware = "";
    private BeamAlignment alignment = BeamAlignment.create(0);
    private int acceptedQuality = -1;
    private double acceptedMultiplier = 0;
    private boolean hardwareValidated;

    // Diagnostics
    private DiagnosticFit diagnostic = DiagnosticFit.create(0);
    private String diagnosticHardware = "";
    private int diagnosticQuality = -1;
    private double diagnosticMultiplier = 0;
    private boolean diagnosticPlotted;

    // Vacuum
    private VacuumChamberState vacuumState = VacuumChamberState.at(VacuumChamberState.MAX_PRESSURE_MICROTORR,0);
    private VacuumPumpSpec vacuumSpec;

    // UPW
    public final WaterTank upwTank = new WaterTank(16000);

    // Cleanroom / contamination
    private MachineContaminationState contaminationState = MachineContaminationState.clean();
    private long lastBreachCheckTick = -1;

    // Light source reservation

    // Diagnostic paper
    public final SimpleContainer diagnosticPaper = new SimpleContainer(1);

    // Yield/calibration freezing
    private boolean yieldFrozen;
    private double frozenYieldMultiplier;
    private int frozenRoomYieldPermille;

    // Per-layer progress
    private int currentLayerProgress;
    private int currentLayerTargetTicks;


    private UUID frozenSourceId;
    private int frozenSourceMultiplier;
    private UUID installationId=UUID.randomUUID();
    private java.util.List<BlockPos> activeFootprint=java.util.List.of();
    public final SimpleContainer repairItems=new SimpleContainer(2);
    final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer> upwJournal=new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<>(){
        @Override protected Integer createSnapshot(){return upwTank.amount();}
        @Override protected void revertToSnapshot(Integer amount){upwTank.restoreAmount(amount);}
        @Override protected void onRootCommit(Integer previous){setChanged();}
    };
    private boolean vacuumIsolated;
    private long centralTick=Long.MIN_VALUE,usedCentralTick=Long.MIN_VALUE;
    private int centralGrant;
    public UUID installationId(){return installationId;}
    public boolean vacuumIsolated(){return vacuumIsolated;}
    private EuvLightSourceControllerBlockEntity source(){var source=EuvBeamlineConnection.source(this);return source!=null&&(!inputsLocked()||frozenSourceId!=null&&frozenSourceId.equals(source.pulses.installation()))?source:null;}
    public int sourceIssue(){var physical=EuvBeamlineConnection.source(this);if(physical==null)return 1;if(inputsLocked()&&(frozenSourceId==null||!frozenSourceId.equals(physical.pulses.installation())))return 2;if(!physical.pulses.calibrated())return 3;return physical.readiness()==8||physical.readiness()==9?0:4;}
    public int sourceMultiplier(){var source=source();return inputsLocked()?frozenSourceMultiplier:source==null?0:source.pulses.multiplier();}
    public boolean hasReservedLightSource(){var source=source();return source!=null&&source.canExpose(this);}
    private int p(String key,int fallback){return definition().map(d->d.paramOrDefault(key,fallback)).orElse(fallback);}
    private int scaledFe(int raw){return (int)Math.clamp(Math.round(raw*com.euvcraft.lithmod.mc.EUVCRAFTMod.settings().fePowerMultiplier()),1,Integer.MAX_VALUE);}

    public EuvScannerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(EuvMachines.SCANNER_CONTROLLER_BE.get(), pos, state, 3); // 3 slots: FOUP, MASK, RESIST
        this.vacuumSpec = createVacuumSpec();
    }

    private VacuumPumpSpec createVacuumSpec() {
        return new VacuumPumpSpec(p("vacuum_pump_rate",5000),p("vacuum_idle_leak",100),p("vacuum_process_load",2000),p("vacuum_operating_pressure",10000),p("vacuum_reserve_capacity",50000),p("vacuum_reserve_recharge",1000));
    }

    @Override
    protected String machineId() {
        return "euv_scanner";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod.euv_scanner_controller");
    }

    @Override
    public FabMachineBlockEntity machine() {
        return this;
    }

    @Override
    public Formation formation() {
        return formation;
    }

    @Override
    public boolean isFormed() {
        return formation.formed();
    }

    @Override
    public List<BlockPos> members() {
        return formation.members();
    }

    @Override
    public String failureReason() {
        return formation.failureReason();
    }

    @Override
    public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return EuvStructureMatcher.match(anchor, facing, lookup);
    }

    @Override
    public void revalidate() {
        formation.validate();
        refreshHardware();
    }


    @Override public void setRemoved(){formation.unload();super.setRemoved();}
    @Override public int extractEnergy(int amount,boolean simulate){
        int water=upwPerTick();var source=source();
        if(amount<1||source==null||!source.canExpose(this)||water<1||water>upwTank.capacity()||upwTank.amount()<water||buffer().getAmountAsLong()<amount)return 0;
        if(simulate)return amount;
        try(var transaction=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){
            if(buffer().extract(amount,transaction)!=amount||!source.payExposure(this,transaction))return 0;
            upwJournal.updateSnapshots(transaction);if(!upwTank.drainExact(water,false))return 0;transaction.commit();setChanged();return amount;
        }
    }

    public int upwPerTick() {
        var receipt=slot(SLOT_FOUP).get(ModDataComponents.COATING_RECEIPT.get());if(receipt!=null)return receipt.scannerWaterPerTick();
        return definition().map(d -> d.paramOrDefault("linear_upw_per_tick_mb",d.paramOrDefault("upw_per_tick_mb", 5))).orElse(5);
    }

    @Override
    protected int utilityGeneration() {
        return 3; // EUV requires gen3 utilities
    }

    @Override
    protected int admissionPasses(Tier tier) {
        return tier.index() == 7 ? 1 : 0; // Single exposure pass for EUV
    }

    @Override
    protected boolean supportsTier(Tier tier) {
        return tier.index() == 7; // EUV only runs T7
    }

    @Override
    protected @Nullable RefusalReason admissionBlocker(Tier tier) {
        int issue = operatingIssue();
        return issue == 0 ? null : new RefusalReason(RefusalReason.Code.values()[issue - 1]);
    }

    @Override
    protected boolean readyForAdmission() {
        // Must be calibrated, have valid hardware, UPW, vacuum, cleanroom, light source
        if (!calibrated() || !diagnosed()) return false;
        if (!hardwareValidated || hardwareFingerprint.isEmpty()) return false;
        if(inputsLocked()&&!hardwareFingerprint.equals(acceptedHardware))return false;
        int upwCost = upwPerTick();
        if (upwCost > 0 && (upwCost > upwTank.capacity() || upwTank.amount() < upwCost)) return false;
        if (!vacuumState.canProcess(vacuumSpec)) return false;
        CleanroomRooms.Sample room = roomSample();
        if (!room.available()) return false;
        if (room.room() == null || room.room().state() != CleanroomControllerBlockEntity.ENCLOSED) return false;
        if (!hasReservedLightSource()) return false;
        return true;
    }

    @Override
    protected double admissionYieldMultiplier() {
        // Freeze calibration multiplier at admission
        activeFootprint=java.util.List.copyOf(members());
        acceptedHardware = hardwareFingerprint;
        var source=source();
        if(source==null||!source.reserveForLot(this))throw new IllegalStateException("Source lost before lot admission");
        frozenSourceId=source.pulses.installation();frozenSourceMultiplier=source.pulses.multiplier();
        frozenYieldMultiplier = acceptedMultiplier * diagnosticMultiplier * frozenSourceMultiplier / 1000.0;
        yieldFrozen = true;
        // Freeze room yield
        CleanroomRooms.Sample room = roomSample();
        if (room.available() && room.room() != null) {
            frozenRoomYieldPermille = room.yieldPermille();
        }
        setChanged();
        return frozenYieldMultiplier;
    }

    @Override
    public FoupContents applyLayer(FoupContents lot, int layer, double defectFactor) {
        FoupContents next = super.applyLayer(lot,layer,defectFactor);
        var tier = TierDataLoader.getRegistry().byIndex(7).orElseThrow();
        if(layer==tier.layers()&&!slot(0).has(ModDataComponents.COATING_RECEIPT.get()))next=CleanroomYield.finish(next,tier);
        storeRoomLot(next);
        return next;
    }

    private void storeRoomLot(FoupContents lot) {
        ItemStack carrier = slot(SLOT_FOUP).copy();
        carrier.set(ModDataComponents.FOUP_CONTENTS.get(), lot);
        setSlot(SLOT_FOUP, carrier);
    }

    @Override
    public FoupContents processingTick(FoupContents lot) {
        if (tierIndex() != 7) return lot;
        CleanroomRooms.Sample room = roomSample();
        if (!room.available()) {
            throw new IllegalStateException("Cleanroom chunk unavailable during EUV processing");
        }
        // Sample calibration exposure for contamination tracking
        var next=lot.withRoomExposure(lot.roomExposure().sample(room.yieldPermille()),0);
        storeRoomLot(next);
        return next;
    }

    /** Returns the public operating issue code for GUI display. */
    public int operatingIssue() {
        if (!isFormed()) return RefusalReason.Code.STRUCTURE_INCOMPLETE.ordinal() + 1;
        if (contaminationState.requiresRepair())return RefusalReason.Code.MACHINE_REPAIR_REQUIRED.ordinal()+1;
        if (definition().isEmpty() || !hardwareValidated) return 0;
        if (!inputsLocked() && !operatingCalibrated()) return RefusalReason.Code.CALIBRATION_REQUIRED.ordinal() + 1;

        // T7 specific checks - map to existing RefusalReason codes
        CleanroomRooms.Sample room = roomSample();
        if (!room.available()) return RefusalReason.Code.ROOM_UNAVAILABLE.ordinal() + 1;
        if (room.room() == null || room.room().state() != CleanroomControllerBlockEntity.ENCLOSED) {
            return RefusalReason.Code.ROOM_UNAVAILABLE.ordinal() + 1; // ROOM_BREACHED -> ROOM_UNAVAILABLE
        }
        int upwCost = upwPerTick();
        if (upwCost > 0 && upwCost <= upwTank.capacity() && upwTank.amount() < upwCost) {
            return RefusalReason.Code.ULTRAPURE_WATER_REQUIRED.ordinal() + 1;
        }
        if (!vacuumState.canProcess(vacuumSpec)) {
            return RefusalReason.Code.VACUUM_REQUIRED.ordinal()+1;
        }
        if (!hasReservedLightSource()) return RefusalReason.Code.EUV_SOURCE_REQUIRED.ordinal()+1;
        if (contaminationState.requiresRepair()) {
            return RefusalReason.Code.MACHINE_REPAIR_REQUIRED.ordinal()+1;
        }
        if (contaminationState.requiresRecalibration()) {
            return RefusalReason.Code.CALIBRATION_REQUIRED.ordinal() + 1; // AWAITING_RECALIBRATION -> CALIBRATION_REQUIRED
        }
        return 0;
    }

    @Override
    public boolean structureFormed() {
        if (!isFormed() || definition().isEmpty()) return false;
        CleanroomRooms.Sample room = roomSample();
        if (!room.available()) return false;
        if (room.room() == null || room.room().state() != CleanroomControllerBlockEntity.ENCLOSED) return false;
        if (!hardwareValidated || hardwareFingerprint.isEmpty()) return false;
        int upwCost = upwPerTick();
        if (upwCost > 0 && upwCost <= upwTank.capacity() && upwTank.amount() < upwCost) return false;
        if (!vacuumState.canProcess(vacuumSpec)) return false;
        if (!hasReservedLightSource()) return false;
        if (contaminationState.requiresRepair() || contaminationState.requiresRecalibration()) return false;
        return true;
    }

    @Override
    protected boolean inputsTemporarilyUnavailable() {
        return !isFormed();
    }

    @Override
    protected ItemStack equipment(int logicalSlot) {
        BlockKind kind = switch (logicalSlot) {
            case 3 -> BlockKind.EXPOSURE_MODULE;
            case 4 -> BlockKind.OPTICS_MODULE;
            case 5 -> BlockKind.STAGE_MODULE;
            case 6 -> BlockKind.FILTRATION_MODULE;
            case 7 -> BlockKind.METROLOGY_MODULE;
            default -> BlockKind.OTHER;
        };
        BlockPos pos = modulePositions.get(kind);
        if (level == null || pos == null || !level.hasChunkAt(pos)) return ItemStack.EMPTY;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof EuvScannerMemberBlockEntity m) || m.structureKind() != kind) return ItemStack.EMPTY;
        // Return appropriate T7 item for module
        return switch (kind) {
            case EXPOSURE_MODULE -> new ItemStack(ModItems.EUV_LIGHT_SOURCE.get());
            case OPTICS_MODULE -> new ItemStack(ModItems.EUV_OPTICS.get());
            case STAGE_MODULE -> new ItemStack(ModItems.EUV_STAGE.get());
            case FILTRATION_MODULE -> new ItemStack(ModItems.EUV_FILTRATION.get());
            case METROLOGY_MODULE -> new ItemStack(ModItems.EUV_METROLOGY.get());
            case VACUUM_MODULE -> new ItemStack(ModItems.EUV_METROLOGY.get()); // No dedicated vacuum item, use metrology as placeholder
            default -> ItemStack.EMPTY;
        };
    }

    private void refreshHardware() {
        if (level == null || level.isClientSide() || !isFormed()) return;
        Map<BlockKind, BlockPos> positions = new EnumMap<>(BlockKind.class);
        StringBuilder fingerprint = new StringBuilder();

        for (BlockPos pos : members()) {
            if (level.getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity m) {
                BlockKind kind = m.structureKind();
                if (isModuleKind(kind)) {
                    positions.put(kind, pos);
                    fingerprint.append(kind.name()).append('@').append(pos.asLong()).append(':').append(m.installation()).append(';');
                }
            }
        }

        boolean hasDiagnostic = false, hasWaterInlet = false;
        for (BlockPos pos : members()) {
            if (level.getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity m) {
                if (m.structureKind() == BlockKind.DIAGNOSTIC_PANEL) {
                    hasDiagnostic = true;
                    fingerprint.append("diagnostic:").append(m.installation());
                }
                if (m.structureKind() == BlockKind.WATER_INLET) {
                    hasWaterInlet = true;
                }
            }
        }

        hardwareValidated = positions.size() == 6; // 6 module kinds
        modulePositions.clear();
        modulePositions.putAll(positions);

        if (!fingerprint.toString().equals(hardwareFingerprint)) {
            hardwareFingerprint = fingerprint.toString();
            calibratedHardware = "";
            acceptedQuality = -1;
            acceptedMultiplier = 0;
            alignment = BeamAlignment.create(hardwareFingerprint.hashCode());
            diagnostic = DiagnosticFit.create(hardwareFingerprint.hashCode());
            diagnosticPlotted = false;
            diagnosticHardware = "";
            diagnosticQuality = -1;
            diagnosticMultiplier = 0;
            setChanged();
        }
    }

    private boolean isModuleKind(BlockKind kind) {
        return kind == BlockKind.EXPOSURE_MODULE || kind == BlockKind.OPTICS_MODULE
                || kind == BlockKind.STAGE_MODULE || kind == BlockKind.FILTRATION_MODULE
                || kind == BlockKind.METROLOGY_MODULE || kind == BlockKind.VACUUM_MODULE;
    }

    public boolean calibrated() {
        return hardwareValidated && !hardwareFingerprint.isEmpty()
                && hardwareFingerprint.equals(calibratedHardware)
                && acceptedQuality >= minimumQuality() && acceptedQuality <= 100
                && acceptedMultiplier > 0 && acceptedMultiplier <= 1;
    }

    public boolean diagnosed() {
        return hardwareValidated && diagnosticHardware.equals(hardwareFingerprint)
                && diagnosticQuality >= diagnosticMinimum() && diagnosticQuality <= 100
                && diagnosticMultiplier > 0 && diagnosticMultiplier <= 1;
    }

    public boolean operatingCalibrated() {
        return calibrated() && diagnosed();
    }

    public int calibrationQuality() {
        return acceptedQuality;
    }

    public double calibratedMultiplier() {
        return operatingCalibrated() ? acceptedMultiplier*diagnosticMultiplier : 0;
    }

    public BeamAlignment alignment() {
        return alignment;
    }

    public int minimumQuality() {
        return definition().map(d -> d.paramOrDefault("calibration_min_quality", 50)).orElse(101);
    }

    private boolean validCalibrationParams() {
        return minimumQuality() >= 1 && minimumQuality() <= 100
                && minPermille() > 0 && maxPermille() >= minPermille() && maxPermille() <= 1000;
    }

    private int minPermille() {
        return definition().map(d -> d.paramOrDefault("calibration_min_multiplier_permille", 500)).orElse(0);
    }

    private int maxPermille() {
        return definition().map(d -> d.paramOrDefault("calibration_max_multiplier_permille", 1000)).orElse(0);
    }

    public double previewMultiplier() {
        return validCalibrationParams() ? alignment.multiplier(minPermille() / 1000.0, maxPermille() / 1000.0) : 0;
    }

    public boolean calibrationControl(int control) {
        if (level == null || level.isClientSide() || control < 0 || control > 6) return false;
        revalidate();
        if (contaminationState.requiresRepair() || !isFormed() || inputsLocked() || !validCalibrationParams()) return false;
        if (control == 6) {
            if (alignment.quality() < minimumQuality()) return false;
            calibratedHardware = hardwareFingerprint;
            acceptedQuality = alignment.quality();
            acceptedMultiplier = previewMultiplier();
            finishRepairCalibration();
        } else {
            alignment = alignment.adjust(control);
            calibratedHardware = "";
            acceptedQuality = -1;
            acceptedMultiplier = 0;
        }
        setChanged();
        return true;
    }

    // Diagnostics
    public DiagnosticFit diagnostic() {
        return diagnostic;
    }

    public boolean diagnosticPlotted() {
        return diagnosticPlotted;
    }

    public int diagnosticQuality() {
        return diagnosticQuality;
    }

    public int diagnosticMinimum() {
        return definition().map(d -> d.paramOrDefault("diagnostic_min_quality", 50)).orElse(101);
    }

    private int diagnosticMinPermille() {
        return definition().map(d -> d.paramOrDefault("diagnostic_min_multiplier_permille", 500)).orElse(0);
    }

    private int diagnosticMaxPermille() {
        return definition().map(d -> d.paramOrDefault("diagnostic_max_multiplier_permille", 1000)).orElse(0);
    }

    private boolean diagnosticParamsValid() {
        return diagnosticMinimum() >= 1 && diagnosticMinimum() <= 100
                && diagnosticMinPermille() > 0 && diagnosticMaxPermille() >= diagnosticMinPermille()
                && diagnosticMaxPermille() <= 1000;
    }

    public double diagnosticPreview() {
        return diagnosticParamsValid() ? diagnostic.multiplier(diagnosticMinPermille() / 1000.0, diagnosticMaxPermille() / 1000.0) : 0;
    }

    public boolean plotDiagnostic(int a, int b, int c) {
        if (level == null || level.isClientSide()) return false;
        revalidate();
        if (contaminationState.requiresRepair() || !isFormed() || inputsLocked() || !diagnosticParamsValid()
                || !diagnosticPaper.getItem(0).is(ModItems.BURN_PAPER.get())
                || a < -200 || a > 200 || b < -200 || b > 200 || c < -200 || c > 200) return false;
        diagnostic = diagnostic.plot(a, b, c);
        diagnosticPaper.removeItem(0, 1);
        diagnosticPlotted = true;
        diagnosticHardware = "";
        diagnosticQuality = -1;
        diagnosticMultiplier = 0;
        setChanged();
        return true;
    }

    public boolean acceptDiagnostic() {
        if (level == null || level.isClientSide()) return false;
        revalidate();
        if (!isFormed() || inputsLocked() || !diagnosticParamsValid() || !diagnosticPlotted
                || diagnostic.quality() < diagnosticMinimum()) return false;
        diagnosticHardware = hardwareFingerprint;
        diagnosticQuality = diagnostic.quality();
        diagnosticMultiplier = diagnosticPreview();
        finishRepairCalibration();
        setChanged();
        return true;
    }

    // Vacuum
    public VacuumChamberState vacuumState() {
        return vacuumState;
    }

    public VacuumPumpSpec vacuumSpec() {
        return vacuumSpec;
    }

    public VacuumTickResult tickVacuum(int centralService, boolean localPumpPowered) {
        var result=vacuumState.tick(vacuumSpec, VacuumMode.PROCESS, centralService, localPumpPowered);vacuumState=result.state();return result;
    }

    public int vacuumCentralDemand() {
        return vacuumState.centralDemandMicroTorrPerTick(vacuumSpec, VacuumMode.PROCESS);
    }

    // Contamination
    public MachineContaminationState contaminationState() {
        return contaminationState;
    }


    public void notifyStructuralBreach(BreachConditions conditions,long tick){
        var result=contaminationState.notifyStructuralBreach(conditions,tick);contaminationState=result.state();
        if(result.notification()==BreachNotification.NEWLY_CONTAMINATED){
            var carrier=slot(SLOT_FOUP).copy();var lot=FoupItems.trustedContentsOf(carrier);abortRun();
            if(lot!=null&&!lot.isEmpty()){carrier.set(ModDataComponents.FOUP_CONTENTS.get(),lot.contaminated());setSlot(SLOT_FOUP,carrier);}setChanged();
        }
    }
    public boolean repair(){
        if(!isFormed()||!contaminationState.requiresRepair()||!(level instanceof ServerLevel))return false;
        var room=roomSample();if(!room.available()||room.room()==null||room.room().state()!=CleanroomControllerBlockEntity.ENCLOSED)return false;
        int filters=p("repair_filter_medium",2),redstone=p("repair_redstone",16),water=p("repair_upw_mb",8000),energy=scaledFe(p("repair_fe",200000));
        if(!repairItems.getItem(0).is(ModItems.UTILITY_FILTER.get())||repairItems.getItem(0).getCount()<filters||!repairItems.getItem(1).is(net.minecraft.world.item.Items.REDSTONE)||repairItems.getItem(1).getCount()<redstone||upwTank.amount()<water||buffer().getAmountAsLong()<energy)return false;
        if(energySource().draw(energy)!=energy)return false;upwTank.drainExact(water,false);repairItems.removeItem(0,filters);repairItems.removeItem(1,redstone);contaminationState=contaminationState.repair(new ContaminationRepairCost(filters+redstone,water,energy)).state();renewCalibration();setChanged();return true;
    }
    private void finishRepairCalibration(){if(contaminationState.requiresRecalibration()&&calibrated()&&diagnosed()){contaminationState=contaminationState.recalibrate().state();setChanged();}}
 private void renewCalibration(){
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
  if(beam.targetX()==previousBeam.targetX()&&beam.targetY()==previousBeam.targetY()&&beam.targetFocus()==previousBeam.targetFocus())beam=far;
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

    @Override public BlockPos vacuumOwnerPos(){return worldPosition;}
    private VacuumMode vacuumMode(){return hasActiveRun()?VacuumMode.PROCESS:VacuumMode.IDLE;}
    @Override public int vacuumDemand(){return vacuumIsolated||!isFormed()?0:vacuumState.requiredServiceMicroTorrPerTick(vacuumSpec,vacuumMode());}
    @Override public boolean acceptVacuumGrant(long tick,int grant){if(level==null||vacuumIsolated||grant<0||tick>level.getGameTime()||tick<level.getGameTime()-1||tick<=usedCentralTick||tick<centralTick)return false;centralTick=tick;centralGrant=grant;return true;}
    public boolean toggleVacuumIsolation(){vacuumIsolated=!vacuumIsolated;centralGrant=0;setChanged();return true;}
    private void pumpVacuum(){int service=0;if(level!=null&&centralTick>usedCentralTick&&centralTick>=level.getGameTime()-1&&centralTick<=level.getGameTime()&&!vacuumIsolated){service=centralGrant;usedCentralTick=centralTick;}centralGrant=0;int fe=scaledFe(p("local_pump_fe_per_tick",200));boolean local=isFormed()&&definition().isPresent()&&(vacuumIsolated||service<vacuumDemand())&&energySource().draw(fe)==fe;vacuumState=vacuumState.tick(vacuumSpec,vacuumMode(),service,local).state();}

    // Persistence
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try{frozenSourceId=UUID.fromString(input.getString("euvFrozenSource").orElse(""));}catch(IllegalArgumentException error){frozenSourceId=null;}
        frozenSourceMultiplier=Math.clamp(input.getIntOr("euvFrozenSourceMultiplier",0),0,1000);
        formed = input.getIntOr("euvFormed", 0) != 0;
        hardwareFingerprint = input.getString("euvHardware").orElse("");
        calibratedHardware = input.getString("euvCalibratedHardware").orElse("");
        acceptedHardware = input.getString("euvAcceptedHardware").orElse("");
        acceptedQuality = input.getIntOr("euvQuality", -1);
        acceptedMultiplier = input.getDoubleOr("euvMultiplier", 0);
        try {
            alignment = new BeamAlignment(
                    input.getIntOr("euvX", 0), input.getIntOr("euvY", 0), input.getIntOr("euvFocus", 0),
                    input.getIntOr("euvTargetX", 0), input.getIntOr("euvTargetY", 0), input.getIntOr("euvTargetFocus", 0));
        } catch (IllegalArgumentException e) {
            alignment = BeamAlignment.create(0);
            acceptedHardware="";calibratedHardware="";acceptedQuality=-1;acceptedMultiplier=0;
        }
        boolean malformedDiagnostic=false;
        try {
            diagnostic = new DiagnosticFit(
                    input.getIntOr("euvDiagA", 0), input.getIntOr("euvDiagB", 0), input.getIntOr("euvDiagC", 0),
                    input.getIntOr("euvDiagTargetA", 0), input.getIntOr("euvDiagTargetB", 0), input.getIntOr("euvDiagTargetC", 0));
        } catch (IllegalArgumentException e) {
            diagnostic = DiagnosticFit.create(0);malformedDiagnostic=true;
        }
        diagnosticPlotted = input.getBooleanOr("euvDiagPlotted", false);
        diagnosticHardware = input.getString("euvDiagHardware").orElse("");
        diagnosticQuality = input.getIntOr("euvDiagQuality", -1);
        diagnosticMultiplier = input.getDoubleOr("euvDiagMultiplier", 0);
        if(malformedDiagnostic){diagnosticPlotted=false;diagnosticHardware="";diagnosticQuality=-1;diagnosticMultiplier=0;}

        vacuumState = new VacuumChamberState(
                Math.clamp(input.getIntOr("euvVacuumPressure", 0),0,VacuumChamberState.MAX_PRESSURE_MICROTORR),
                Math.clamp(input.getIntOr("euvVacuumReserve", 0),0,1000000));

        try{contaminationState=new MachineContaminationState(MachineContaminationState.Phase.valueOf(input.getString("euvContamPhase").orElse("CLEAN")),input.getLongOr("euvContamBreachTick",-1));}catch(IllegalArgumentException invalid){contaminationState=MachineContaminationState.contaminated(0);}

        try{installationId=UUID.fromString(input.getString("euvInstallation").orElse(""));}catch(IllegalArgumentException invalid){installationId=UUID.randomUUID();}
        activeFootprint=input.read("euvActiveFootprint",BlockPos.CODEC.listOf()).orElse(java.util.List.of());
        vacuumIsolated=input.getBooleanOr("euvVacuumIsolation",false);
        repairItems.setItem(0,input.read("euvRepairFilter",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        repairItems.setItem(1,input.read("euvRepairRedstone",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));

        upwTank.restoreAmount(Math.clamp(input.getIntOr("euvUpw", 0), 0, 1000000));
        diagnosticPaper.setItem(0, input.read("euvDiagPaper", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));

        frozenYieldMultiplier = input.getDoubleOr("euvFrozenYield", 0);
        frozenRoomYieldPermille = input.getIntOr("euvFrozenRoomYield", 0);
        yieldFrozen = input.getBooleanOr("euvYieldFrozen", false);

        hardwareValidated = false;
        modulePositions.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if(frozenSourceId!=null)output.putString("euvFrozenSource",frozenSourceId.toString());
        output.putInt("euvFrozenSourceMultiplier",frozenSourceMultiplier);
        output.putInt("euvFormed", formed ? 1 : 0);
        output.putString("euvHardware", hardwareFingerprint);
        output.putString("euvCalibratedHardware", calibratedHardware);
        output.putString("euvAcceptedHardware", acceptedHardware);
        output.putInt("euvQuality", acceptedQuality);
        output.putDouble("euvMultiplier", acceptedMultiplier);
        output.putInt("euvX", alignment.x());
        output.putInt("euvY", alignment.y());
        output.putInt("euvFocus", alignment.focus());
        output.putInt("euvTargetX", alignment.targetX());
        output.putInt("euvTargetY", alignment.targetY());
        output.putInt("euvTargetFocus", alignment.targetFocus());
        output.putBoolean("euvDiagPlotted", diagnosticPlotted);
        output.putString("euvDiagHardware", diagnosticHardware);
        output.putInt("euvDiagQuality", diagnosticQuality);
        output.putDouble("euvDiagMultiplier", diagnosticMultiplier);
        output.putInt("euvDiagA", diagnostic.a());
        output.putInt("euvDiagB", diagnostic.b());
        output.putInt("euvDiagC", diagnostic.c());
        output.putInt("euvDiagTargetA", diagnostic.targetA());
        output.putInt("euvDiagTargetB", diagnostic.targetB());
        output.putInt("euvDiagTargetC", diagnostic.targetC());
        output.putInt("euvVacuumPressure", vacuumState.pressureMicroTorr());
        output.putInt("euvVacuumReserve", vacuumState.reserveMicroTorr());
        output.putString("euvContamPhase", contaminationState.phase().name());
        output.putLong("euvContamBreachTick", contaminationState.lastBreachTick());
        output.putString("euvInstallation",installationId.toString());
        output.store("euvActiveFootprint",BlockPos.CODEC.listOf(),activeFootprint);
        output.putBoolean("euvVacuumIsolation",vacuumIsolated);
        output.store("euvRepairFilter",ItemStack.OPTIONAL_CODEC,repairItems.getItem(0));
        output.store("euvRepairRedstone",ItemStack.OPTIONAL_CODEC,repairItems.getItem(1));
        output.putInt("euvUpw", upwTank.amount());
        output.store("euvDiagPaper", ItemStack.OPTIONAL_CODEC, diagnosticPaper.getItem(0));
        output.putDouble("euvFrozenYield", frozenYieldMultiplier);
        output.putInt("euvFrozenRoomYield", frozenRoomYieldPermille);
        output.putBoolean("euvYieldFrozen", yieldFrozen);
    }


    @Override public void serverTick(){
        if(!(level instanceof ServerLevel))return;refreshDefinition();revalidate();vacuumSpec=createVacuumSpec();upwTank.resize(Math.clamp(p("upw_capacity_mb",32000),1000,1000000));
        if(inputsLocked()&&!slot(SLOT_FOUP).isEmpty()){
            var room=roomSample();if(room.available()&&(room.room()==null||room.room().state()!=CleanroomControllerBlockEntity.ENCLOSED))notifyStructuralBreach(new BreachConditions(buffer().getAmountAsLong()>0,true,true),level.getGameTime());
        }
        pumpVacuum();super.serverTick();
    }

    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder){
        super.collectImplicitComponents(builder);
        if(contaminationState.requiresRepair())builder.set(ModDataComponents.CONTAMINATED_EQUIPMENT.get(),true);
    }
    @Override protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components){
        super.applyImplicitComponents(components);
        if(Boolean.TRUE.equals(components.get(ModDataComponents.CONTAMINATED_EQUIPMENT.get())))contaminationState=MachineContaminationState.contaminated(0);
    }
    @Override protected void dropExtraContents(net.minecraft.world.level.Level level,BlockPos pos){super.dropExtraContents(level,pos);dropContainer(level,pos,diagnosticPaper);dropContainer(level,pos,repairItems);}

    private CleanroomRooms.Sample roomSample() {
        return level instanceof ServerLevel server
                ? CleanroomRooms.sample(server, inputsLocked()&&!activeFootprint.isEmpty()?activeFootprint:members())
                : new CleanroomRooms.Sample(false, 500, null);
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new EuvScannerEquipmentMenu(containerId, playerInventory, this, worldPosition);
    }

    // Interface for formation owner
    @Override
    public void onFormed() {
        this.formed = true;
        // formationChanges is private in parent, but formation validation will handle sync
    }

    @Override
    public void onUnformed() {
        this.formed = false;
        hardwareValidated = false;
        modulePositions.clear();

    }
}
