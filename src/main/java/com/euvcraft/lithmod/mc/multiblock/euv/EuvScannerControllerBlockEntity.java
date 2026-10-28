package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.*;
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
public class EuvScannerControllerBlockEntity extends ProcessCellControllerBlockEntity implements EuvFormationOwner {

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
    private VacuumChamberState vacuumState = VacuumChamberState.evacuated();
    private final VacuumPumpSpec vacuumSpec;

    // UPW
    public final WaterTank upwTank = new WaterTank(16000);

    // Cleanroom / contamination
    private MachineContaminationState contaminationState = MachineContaminationState.clean();
    private long lastBreachCheckTick = -1;

    // Light source reservation
    private @Nullable UUID reservedSourceId;
    private int reservedSourceTicksRemaining;

    // Diagnostic paper
    public final SimpleContainer diagnosticPaper = new SimpleContainer(1);

    // Yield/calibration freezing
    private boolean yieldFrozen;
    private double frozenYieldMultiplier;
    private int frozenRoomYieldPermille;

    // Per-layer progress
    private int currentLayerProgress;
    private int currentLayerTargetTicks;

    public EuvScannerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(EuvMachines.SCANNER_CONTROLLER_BE.get(), pos, state, 3); // 3 slots: FOUP, MASK, RESIST
        this.vacuumSpec = createVacuumSpec();
    }

    private VacuumPumpSpec createVacuumSpec() {
        return new VacuumPumpSpec(
                5000,  // pumpRateMicroTorrPerTick
                100,   // idleLeakMicroTorrPerTick
                200,   // processLoadMicroTorrPerTick
                1000,  // operatingPressureMicroTorr
                50000, // reserveCapacityMicroTorr
                1000   // reserveRechargeMicroTorrPerTick
        );
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
        return formed;
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

    @Override
    public void setRemoved() {
        formation.unload();
        // Release light source reservation
        if (reservedSourceId != null) {
            releaseLightSource();
        }
        super.setRemoved();
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        // Check UPW for T7
        int upwCost = upwPerTick();
        if (upwCost > 0 && upwCost <= upwTank.capacity() && upwTank.amount() < upwCost) {
            return 0;
        }
        int drawn = super.extractEnergy(amount, simulate);
        if (!simulate && drawn == amount && upwCost > 0) {
            upwTank.drainExact(upwCost, false);
            setChanged();
        }
        return drawn;
    }

    private int upwPerTick() {
        return definition().map(d -> d.paramOrDefault("upw_per_tick_mb", 5)).orElse(5);
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
        int upwCost = upwPerTick();
        if (upwCost > 0 && (upwCost > upwTank.capacity() || upwTank.amount() < upwCost)) return false;
        if (!vacuumState.canProcess(vacuumSpec)) return false;
        CleanroomRooms.Sample room = roomSample();
        if (!room.available()) return false;
        if (room.room() == null || room.room().state() != CleanroomControllerBlockEntity.ENCLOSED) return false;
        if (reservedSourceId == null) return false;
        return true;
    }

    @Override
    protected double admissionYieldMultiplier() {
        // Freeze calibration multiplier at admission
        acceptedHardware = hardwareFingerprint;
        frozenYieldMultiplier = acceptedMultiplier * diagnosticMultiplier;
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
        // Add cleanroom exposure sample for this paid layer
        CleanroomRooms.Sample room = roomSample();
        if (room.available() && room.room() != null) {
            lot = lot.withRoomExposure(lot.roomExposure().sample(room.yieldPermille()), frozenRoomYieldPermille);
        }
        FoupContents next = super.applyLayer(lot, layer, defectFactor);
        // Check if this is the final layer using runner's tier info
        var currentContract = runner().getContract();
        if (currentContract != null && layer == currentContract.tier().layers()) {
            // Final layer - finish with cleanroom yield
            next = CleanroomYield.finish(next, currentContract.tier());
        }
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
        sampleCalibrationExposure(room);
        return lot;
    }

    /** Returns the public operating issue code for GUI display. */
    public int operatingIssue() {
        if (!isFormed()) return RefusalReason.Code.STRUCTURE_INCOMPLETE.ordinal() + 1;
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
            return RefusalReason.Code.GENERATION_UNMET.ordinal() + 1; // VACUUM_INSUFFICIENT -> GENERATION_UNMET
        }
        if (reservedSourceId == null) {
            return RefusalReason.Code.GENERATION_UNMET.ordinal() + 1; // LIGHT_SOURCE_UNAVAILABLE -> GENERATION_UNMET
        }
        if (contaminationState.requiresRepair()) {
            return RefusalReason.Code.CONTAMINATED_FOUP.ordinal() + 1; // CONTAMINATED -> CONTAMINATED_FOUP
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
        if (reservedSourceId == null) return false;
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
        return operatingCalibrated() ? frozenYieldMultiplier : 0;
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
        if (!isFormed() || inputsLocked() || !validCalibrationParams()) return false;
        if (control == 6) {
            if (alignment.quality() < minimumQuality()) return false;
            calibratedHardware = hardwareFingerprint;
            acceptedQuality = alignment.quality();
            acceptedMultiplier = previewMultiplier();
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
        if (!isFormed() || inputsLocked() || !diagnosticParamsValid()
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
        return vacuumState.tick(vacuumSpec, VacuumMode.PROCESS, centralService, localPumpPowered);
    }

    public int vacuumCentralDemand() {
        return vacuumState.centralDemandMicroTorrPerTick(vacuumSpec, VacuumMode.PROCESS);
    }

    // Contamination
    public MachineContaminationState contaminationState() {
        return contaminationState;
    }

    public void notifyStructuralBreach(BreachConditions conditions, long tick) {
        MachineContaminationState.BreachResult result = contaminationState.notifyStructuralBreach(conditions, tick);
        if (result.notification() == BreachNotification.NEWLY_CONTAMINATED) {
            // Scrap active FOUP to contaminated state
            scrapActiveFoup();
            setChanged();
        }
    }

    private void scrapActiveFoup() {
        ItemStack foup = slot(SLOT_FOUP);
        if (!foup.isEmpty()) {
            // Mark as contaminated - this would be done via FoupContents mutation
            // For now, just clear the slot to simulate scrap
            clearSlot(SLOT_FOUP);
        }
    }

    public MachineContaminationState.RepairResult repairContamination(ContaminationRepairCost cost) {
        MachineContaminationState.RepairResult result = contaminationState.repair(cost);
        if (result.charged()) {
            setChanged();
        }
        return result;
    }

    public MachineContaminationState.RecalibrationResult recalibrateAfterRepair() {
        MachineContaminationState.RecalibrationResult result = contaminationState.recalibrate();
        if (result.recalibrated()) {
            // Reset calibration state
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
        return result;
    }

    // Light source reservation
    public boolean reserveLightSource(UUID sourceId, int maxTicks) {
        if (reservedSourceId != null) return false;
        reservedSourceId = sourceId;
        reservedSourceTicksRemaining = maxTicks;
        setChanged();
        return true;
    }

    public void releaseLightSource() {
        reservedSourceId = null;
        reservedSourceTicksRemaining = 0;
        setChanged();
    }

    public boolean hasReservedLightSource() {
        return reservedSourceId != null;
    }

    public @Nullable UUID reservedSourceId() {
        return reservedSourceId;
    }

    public int reservedSourceTicksRemaining() {
        return reservedSourceTicksRemaining;
    }

    public void decrementSourceReservation() {
        if (reservedSourceTicksRemaining > 0) {
            reservedSourceTicksRemaining--;
            if (reservedSourceTicksRemaining == 0) {
                reservedSourceId = null;
            }
            setChanged();
        }
    }

    // Energy handler for power ports
    public EnergyHandler energyHandler() {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return buffer().getAmountAsLong();
            }
            @Override
            public long getCapacityAsLong() {
                return buffer().getCapacityAsLong();
            }
            @Override
            public int insert(int amount, TransactionContext transaction) {
                return buffer().insert(amount, transaction);
            }
            @Override
            public int extract(int amount, TransactionContext transaction) {
                return buffer().extract(amount, transaction);
            }
        };
    }

    // Persistence
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
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
        }
        try {
            diagnostic = new DiagnosticFit(
                    input.getIntOr("euvDiagA", 0), input.getIntOr("euvDiagB", 0), input.getIntOr("euvDiagC", 0),
                    input.getIntOr("euvDiagTargetA", 0), input.getIntOr("euvDiagTargetB", 0), input.getIntOr("euvDiagTargetC", 0));
        } catch (IllegalArgumentException e) {
            diagnostic = DiagnosticFit.create(0);
        }
        diagnosticPlotted = input.getBooleanOr("euvDiagPlotted", false);
        diagnosticHardware = input.getString("euvDiagHardware").orElse("");
        diagnosticQuality = input.getIntOr("euvDiagQuality", -1);
        diagnosticMultiplier = input.getDoubleOr("euvDiagMultiplier", 0);

        vacuumState = new VacuumChamberState(
                input.getIntOr("euvVacuumPressure", 0),
                input.getIntOr("euvVacuumReserve", 0));

        contaminationState = new MachineContaminationState(
                MachineContaminationState.Phase.valueOf(input.getString("euvContamPhase").orElse("CLEAN")),
                input.getLongOr("euvContamBreachTick", -1));

        reservedSourceId = input.getString("euvReservedSource").orElse("").isEmpty()
                ? null : UUID.fromString(input.getString("euvReservedSource").orElse(""));
        reservedSourceTicksRemaining = input.getIntOr("euvReservedTicks", 0);

        upwTank.restoreAmount(Math.clamp(input.getIntOr("euvUpw", 0), 0, upwTank.capacity()));
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
        if (reservedSourceId != null) output.putString("euvReservedSource", reservedSourceId.toString());
        output.putInt("euvReservedTicks", reservedSourceTicksRemaining);
        output.putInt("euvUpw", upwTank.amount());
        output.store("euvDiagPaper", ItemStack.OPTIONAL_CODEC, diagnosticPaper.getItem(0));
        output.putDouble("euvFrozenYield", frozenYieldMultiplier);
        output.putInt("euvFrozenRoomYield", frozenRoomYieldPermille);
        output.putBoolean("euvYieldFrozen", yieldFrozen);
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (!(level instanceof ServerLevel)) return;

        // Tick vacuum
        int centralService = 0; // Would come from central allocator
        boolean localPumpPowered = buffer().getAmountAsLong() > 0;
        VacuumTickResult vacResult = tickVacuum(centralService, localPumpPowered);

        // Check cleanroom breach
        if (isFormed() && runner().isRunning()) {
            long tick = level.getGameTime();
            if (tick != lastBreachCheckTick) {
                lastBreachCheckTick = tick;
                CleanroomRooms.Sample room = roomSample();
                if (room.available() && room.room() != null) {
                    if (room.room().state() != CleanroomControllerBlockEntity.ENCLOSED) {
                        BreachConditions conditions = new BreachConditions(
                                buffer().getAmountAsLong() > 0,
                                !slot(SLOT_FOUP).isEmpty(),
                                true
                        );
                        notifyStructuralBreach(conditions, tick);
                    }
                }
            }
        }

        // Decrement light source reservation
        if (reservedSourceTicksRemaining > 0) {
            decrementSourceReservation();
        }

        // Resize UPW tank from definition
        upwTank.resize(Math.clamp(
                definition().map(d -> d.paramOrDefault("upw_capacity_mb", 16000)).orElse(16000),
                1, 1000000));

        publishProgress();
    }

    private void publishProgress() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, progressTicks());
        data().set(DATA_TICKS, runner().ticks());
        // Additional EUV data
        data().set(100, vacuumState.pressureMicroTorr() / 1000); // mTorr for display
        data().set(101, upwTank.amount());
        data().set(102, contaminationState.phase().ordinal());
        data().set(103, reservedSourceId != null ? 1 : 0);
    }

    private CleanroomRooms.Sample roomSample() {
        return level instanceof ServerLevel server
                ? CleanroomRooms.sample(server, members())
                : new CleanroomRooms.Sample(false, 500, null);
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new EuvScannerOperatingMenu(containerId, playerInventory, this, worldPosition, false);
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
        // Release light source on unform
        if (reservedSourceId != null) {
            releaseLightSource();
        }
    }
}