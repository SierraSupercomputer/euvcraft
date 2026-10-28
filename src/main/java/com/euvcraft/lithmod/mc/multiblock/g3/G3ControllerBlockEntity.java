package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.core.t7.VacuumChamberState;
import com.euvcraft.lithmod.core.t7.VacuumMode;
import com.euvcraft.lithmod.core.t7.VacuumPumpSpec;
import com.euvcraft.lithmod.core.t7.CentralVacuumAllocator;
import com.euvcraft.lithmod.core.t7.MachineContaminationState;
import com.euvcraft.lithmod.core.t7.G3Preparation;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupDisposal;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for G3 controller block entities.
 * Handles formation lifecycle, visual state, energy buffer, and common job infrastructure.
 */
public abstract class G3ControllerBlockEntity extends FabMachineBlockEntity implements G3ControllerOwner, EnergyBufferProvider {

    /** Revalidation interval in ticks. */
    public static final int REVALIDATE_INTERVAL = 20;

    /** Maximum part index for visual states (175 cells for 5x5x7). */
    public static final int MAX_PART_INDEX = 174;

    /** The current formation state. */
    private final G3Formation formation = new G3Formation(this);

    /** Current machine kind (writer or deposition etcher). */
    private final G3MachineKind kind;

    /** Water tank for deposition etcher (UPW for rinse phase). Null for writer. */
    private @Nullable WaterTank waterTank;

    /** Water journal for transactional fluid handling. */
    final G3WaterInletHandler.TankJournal waterJournal = new G3WaterInletHandler.TankJournal(this);

    /** Vacuum chamber state for deposition etcher. Null for writer. */
    private @Nullable VacuumChamberState vacuumState;

    /** Local vacuum pump spec (from definition). */
    private @Nullable VacuumPumpSpec vacuumSpec;

    /** Machine contamination state for T7 processing. */
    private MachineContaminationState contaminationState = MachineContaminationState.clean();

    /** Central vacuum allocation cursor (persisted, advanced each tick). */
    private int vacuumCursor = 0;

    /** Pending job state — common fields. */
    private boolean jobRunning;
    private boolean jobPaused;
    private int jobProgress;
    private int jobDuration;
    private int jobEnergyPerTick;

    /** Job input/output tracking for conservation. */
    private @Nullable ItemStack pendingInput0;
    private @Nullable ItemStack pendingInput1;
    private @Nullable ItemStack pendingOutput;
    private int pendingOutputCount;
    private int pendingInput0Count;
    private int pendingInput1Count;
    private int pendingWaterMb;

    /** Menu data container. */
    private final ContainerData data = new SimpleContainerData(16);

    protected G3ControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                      G3MachineKind kind, int inputSlots, int outputSlots) {
        super(type, pos, state, inputSlots, outputSlots);
        this.kind = kind;
        if (kind == G3MachineKind.DEPOSITION_ETCHER) {
            this.waterTank = new WaterTank(16000); // default, resized from definition
            this.vacuumState = VacuumChamberState.evacuated();
        }
    }

    @Override
    public final FabMachineBlockEntity machine() {
        return this;
    }

    @Override
    public final G3Formation formation() {
        return formation;
    }

    @Override
    public final boolean large() {
        return kind == G3MachineKind.DEPOSITION_ETCHER;
    }

    @Override
    public final G3MachineKind getKind() {
        return kind;
    }

    @Override
    public final boolean isFormed() {
        return formation.formed();
    }

    @Override
    public final void revalidate() {
        formation.validate();
    }

    @Override
    public final List<BlockPos> members() {
        return formation.members();
    }

    @Override
    public final ContainerData data() {
        return data;
    }

    /** Returns the water tank (deposition etcher only). */
    public @Nullable WaterTank waterTank() {
        return waterTank;
    }

    /** Returns the vacuum chamber state (deposition etcher only). */
    public @Nullable VacuumChamberState vacuumState() {
        return vacuumState;
    }

    /** Returns the machine contamination state. */
    public MachineContaminationState contaminationState() {
        return contaminationState;
    }

    /** Returns the vacuum pump spec (from definition). */
    public @Nullable VacuumPumpSpec vacuumSpec() {
        return vacuumSpec;
    }

    @Override
    protected void serverTick() {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel) || this.isRemoved()
                || this.level.getBlockEntity(this.worldPosition) != this) return;

        refreshDefinition();
        syncWaterCapacity();
        syncVacuumSpec();

        if (this.formation.needsValidation || ++this.formation.ticksSinceValidation >= REVALIDATE_INTERVAL) {
            this.formation.needsValidation = false;
            this.formation.validate();
        }

        if (this.kind == G3MachineKind.RETICLE_WRITER) {
            tickWriterJob();
        } else {
            tickDepositionEtcherJob();
        }

        publishData();
    }

    private void syncWaterCapacity() {
        if (this.waterTank == null) return;
        Optional<Integer> param = definition().flatMap(def -> def.param("water_capacity"));
        if (param.isPresent()) {
            int requested = param.get();
            if (requested >= 1 && requested <= WaterTank.MAX_CAPACITY) {
                this.waterTank.resize(requested);
            }
        }
    }

    private void syncVacuumSpec() {
        if (this.vacuumState == null) return;
        Optional<VacuumPumpSpec> spec = VacuumPumpSpec.fromDefinition(definition());
        if (spec.isPresent()) {
            this.vacuumSpec = spec.get();
        }
    }

    private void publishData() {
        data.set(FabMachineBlockEntity.DATA_ENERGY, this.buffer().getAmountAsInt());
        data.set(FabMachineBlockEntity.DATA_CAPACITY, this.buffer().getCapacityAsInt());
        data.set(FabMachineBlockEntity.DATA_PROGRESS, this.jobRunning ? this.jobProgress : 0);
        data.set(FabMachineBlockEntity.DATA_TICKS, this.jobRunning ? this.jobDuration : 0);
        // Additional data slots for G3-specific UI
        data.set(4, this.formed ? 1 : 0);
        data.set(5, this.jobPaused ? 1 : 0);
        if (this.waterTank != null) {
            data.set(6, this.waterTank.amount());
            data.set(7, this.waterTank.capacity());
        }
        if (this.vacuumState != null && this.vacuumSpec != null) {
            data.set(8, this.vacuumState.pressureMicroTorr());
            data.set(9, this.vacuumSpec.operatingPressureMicroTorr());
            data.set(10, this.vacuumState.reserveMicroTorr());
            data.set(11, this.vacuumSpec.reserveCapacityMicroTorr());
        }
        data.set(12, this.contaminationState.phase().ordinal());
        data.set(13, (int) Math.max(0, this.contaminationState.lastBreachTick()));
    }

    // ==================== Writer job logic ====================

    /** Writer: selectable tier7 compute/memory/NAND recipes from SupportRecipeLoader/MaskData APIs. */
    private void tickWriterJob() {
        if (this.jobRunning) {
            advanceWriterJob();
        } else if (this.formed) {
            maybeStartWriterJob();
        }
    }

    private void maybeStartWriterJob() {
        // Job start is triggered by server-side menu button (clickMenuButton)
        // This method is a placeholder for auto-start if needed later
    }

    /**
     * Server-side handler for writer job start button.
     * Validates selection and pins the job snapshot.
     * @param recipeId the support recipe id to use
     * @param coreCount core count for compute designs (1 for non-compute)
     * @return true if the job was started
     */
    public boolean startWriterJob(String recipeId, int coreCount) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel) || this.isRemoved()
                || this.level.getBlockEntity(this.worldPosition) != this
                || this.kind != G3MachineKind.RETICLE_WRITER
                || this.jobRunning || !this.formed) return false;

        SupportRecipeLoader.Registry registry = SupportRecipeLoader.getRegistry();
        if (registry == null) return false;

        SupportRecipeLoader.Resolved resolved = registry.byId(
                net.minecraft.resources.ResourceLocation.tryParse(recipeId))
                .filter(r -> r.recipe().machine().equals("g3_reticle_writer")).orElse(null);
        if (resolved == null) return false;

        SupportRecipe recipe = resolved.recipe();

        // Validate inputs: blank mask + redstone
        ItemStack blankIn = this.slot(0);
        ItemStack redstoneIn = this.slot(1);
        if (!blankIn.is(resolved.inputItem()) || blankIn.getCount() < recipe.input().count()
                || !redstoneIn.is(Items.REDSTONE) || redstoneIn.getCount() < (recipe.secondary() != null ? recipe.secondary().count() : 0)
                || !canAcceptInto(2, resolved.outputItem(), recipe.outputCount())) {
            return false;
        }

        // Validate core count for compute designs
        // The recipe's output item carries the die type via MaskData component
        // For now, we trust the recipe configuration; core count is validated against settings
        if (coreCount < 1 || coreCount > 256) return false;
        if (!EUVCRAFTMod.settings().coreCountOptions().contains(coreCount)) return false;

        // Pin the job snapshot
        this.jobDuration = recipe.duration();
        this.jobEnergyPerTick = recipe.energyPerTick();
        this.jobProgress = 0;
        this.pendingInput0 = blankIn.copy();
        this.pendingInput0Count = recipe.input().count();
        this.pendingInput1 = redstoneIn.copy();
        this.pendingInput1Count = recipe.secondary() != null ? recipe.secondary().count() : 0;
        this.pendingOutput = new ItemStack(resolved.outputItem());
        this.pendingOutputCount = recipe.outputCount();
        this.pendingWaterMb = 0; // writer uses no water

        this.jobRunning = true;
        this.jobPaused = false;
        setChanged();
        return true;
    }

    private void advanceWriterJob() {
        if (!this.formed) {
            this.jobPaused = true;
            return;
        }
        if (writerInputTampered()) {
            resetJob();
            return;
        }
        if (!canAcceptInto(2, this.pendingOutput.getItem(), this.pendingOutputCount)) {
            this.jobPaused = true;
            return;
        }
        if (this.energySource().draw(this.jobEnergyPerTick) < this.jobEnergyPerTick) {
            this.jobPaused = true;
            return;
        }

        this.jobPaused = false;
        this.jobProgress++;
        if (this.jobProgress < this.jobDuration) {
            return;
        }
        completeWriterJob();
    }

    private boolean writerInputTampered() {
        ItemStack blankIn = this.slot(0);
        ItemStack redstoneIn = this.slot(1);
        return !blankIn.is(this.pendingInput0.getItem()) || blankIn.getCount() < this.pendingInput0Count
                || !redstoneIn.is(this.pendingInput1.getItem()) || redstoneIn.getCount() < this.pendingInput1Count;
    }

    private void completeWriterJob() {
        if (!this.formed || writerInputTampered()
                || !canAcceptInto(2, this.pendingOutput.getItem(), this.pendingOutputCount)) {
            resetJob();
            return;
        }

        // Consume exact inputs
        ItemStack blankIn = this.slot(0);
        ItemStack redstoneIn = this.slot(1);
        blankIn.shrink(this.pendingInput0Count);
        if (blankIn.isEmpty()) this.clearSlot(0);
        redstoneIn.shrink(this.pendingInput1Count);
        if (redstoneIn.isEmpty()) this.clearSlot(1);

        // Write core count to output mask if compute design
        // The recipe's output item is the mask item; we set CORE_COUNT component
        ItemStack product = this.pendingOutput.copy();
        if (coreCount > 1) {
            product.set(com.euvcraft.lithmod.mc.registry.ModDataComponents.CORE_COUNT.get(), coreCount);
        }

        ItemStack existing = this.slot(2);
        if (existing.isEmpty()) {
            this.setSlot(2, product);
        } else {
            existing.setCount(existing.getCount() + product.getCount());
        }
        setChanged();
        resetJob();
    }

    // Core count for the active writer job (pinned at start)
    private int coreCount = 1;

    // ==================== Deposition Etcher job logic ====================

    /** Deposition etcher job phases. */
    private enum PrepPhase {
        IDLE,
        VACUUM_DEP_ETCH,
        UPW_RINSE,
        POWERED_DRY,
        COMPLETE
    }

    private PrepPhase prepPhase = PrepPhase.IDLE;
    private int phaseProgress;
    private int phaseDuration;
    private int phaseEnergyPerTick;
    private int phaseWaterMb;
    private int phaseVacuumDemand;
    private boolean phaseLocalPumpPowered;
    private @Nullable FoupContents admittedLot;
    private @Nullable UUID admittedLotId;
    private int admittedSlot = 0; // FOUP input slot

    private void tickDepositionEtcherJob() {
        // Handle vacuum tick if we have a spec
        if (this.vacuumSpec != null && this.vacuumState != null) {
            tickVacuum();
        }

        switch (this.prepPhase) {
            case IDLE -> {
                if (this.formed) maybeStartPrepJob();
            }
            case VACUUM_DEP_ETCH -> advanceVacuumDepEtchPhase();
            case UPW_RINSE -> advanceRinsePhase();
            case POWERED_DRY -> advanceDryPhase();
            case COMPLETE -> completePrepJob();
        }
    }

    private void maybeStartPrepJob() {
        // Job start triggered by server-side menu button with fresh electronic-grade FOUP
    }

    /**
     * Server-side handler for prep job start button.
     * Admits a fresh unstarted electronic-grade FOUP from the input slot.
     * @return true if the job was started
     */
    public boolean startPrepJob() {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel) || this.isRemoved()
                || this.level.getBlockEntity(this.worldPosition) != this
                || this.kind != G3MachineKind.DEPOSITION_ETCHER
                || this.prepPhase != PrepPhase.IDLE || !this.formed) return false;

        ItemStack foupStack = this.slot(0);
        if (foupStack.isEmpty() || foupStack.getCount() != 1) return false;

        // Validate FOUP contents: fresh, unstarted, electronic-grade
        com.euvcraft.lithmod.core.FoupItems.FoupContentsResult result =
                com.euvcraft.lithmod.core.FoupItems.trustedContentsOrError(foupStack);
        if (result == null || result.contents().isEmpty()) return false;
        FoupContents lot = result.contents();
        if (lot.isStarted() || lot.grade().generation() < com.euvcraft.lithmod.core.WaferSubstrate.ELECTRONIC_GRADE.generation()
                || lot.preparationGeneration() != FoupContents.PREPARATION_LEGACY) return false;
        for (com.euvcraft.lithmod.core.Wafer wafer : lot.wafers()) {
            if (wafer.layerIndex() != com.euvcraft.lithmod.core.Wafer.UNPROCESSED_LAYER) return false;
        }

        // Admit the lot (remove from input slot, store reference)
        this.admittedLot = lot;
        this.admittedLotId = lot.lotId();
        this.admittedSlot = 0;
        this.clearSlot(0);

        // Load definition params for phases
        var def = definition().orElseThrow();
        int vacuumDuration = def.param("vacuum_duration").orElse(200);
        int vacuumEnergy = def.param("vacuum_energy_per_tick").orElse(100);
        int vacuumDemand = def.param("vacuum_demand_microtorr_per_tick").orElse(5000);
        int rinseDuration = def.param("rinse_duration").orElse(200);
        int rinseEnergy = def.param("rinse_energy_per_tick").orElse(50);
        int rinseWater = def.param("rinse_water_mb").orElse(1000);
        int dryDuration = def.param("dry_duration").orElse(200);
        int dryEnergy = def.param("dry_energy_per_tick").orElse(200);

        if (vacuumDuration < 1 || vacuumEnergy < 1 || vacuumDemand < 0
                || rinseDuration < 1 || rinseEnergy < 1 || rinseWater < 0
                || dryDuration < 1 || dryEnergy < 1) return false;

        // Check water availability for rinse phase
        if (this.waterTank == null || this.waterTank.amount() < rinseWater) return false;

        // Start vacuum dep/etch phase
        this.prepPhase = PrepPhase.VACUUM_DEP_ETCH;
        this.phaseProgress = 0;
        this.phaseDuration = vacuumDuration;
        this.phaseEnergyPerTick = vacuumEnergy;
        this.phaseVacuumDemand = vacuumDemand;
        this.phaseLocalPumpPowered = true;
        this.phaseWaterMb = 0;
        setChanged();
        return true;
    }

    private void tickVacuum() {
        if (this.vacuumSpec == null || this.vacuumState == null) return;

        // Calculate central demand
        int centralDemand = 0;
        if (this.prepPhase == PrepPhase.VACUUM_DEP_ETCH) {
            centralDemand = this.vacuumState.centralDemandMicroTorrPerTick(this.vacuumSpec, VacuumMode.PROCESS);
        }

        // Allocate central vacuum (cursor advances each tick)
        int[] demands = new int[]{centralDemand};
        CentralVacuumAllocator.Allocation alloc = CentralVacuumAllocator.allocate(
                EUVCRAFTMod.settings().centralVacuumCapacity(), demands, this.vacuumCursor);
        this.vacuumCursor = (this.vacuumCursor + 1) % Math.max(1, demands.length);
        int centralGrant = alloc.at(0);

        // Tick the chamber
        VacuumChamberState.VacuumTickResult result = this.vacuumState.tick(
                this.vacuumSpec, currentVacuumMode(), centralGrant, this.phaseLocalPumpPowered);
        this.vacuumState = result.state();
    }

    private VacuumMode currentVacuumMode() {
        return switch (this.prepPhase) {
            case VACUUM_DEP_ETCH -> VacuumMode.PROCESS;
            case UPW_RINSE, POWERED_DRY -> VacuumMode.IDLE;
            default -> VacuumMode.IDLE;
        };
    }

    private void advanceVacuumDepEtchPhase() {
        if (!this.formed || this.vacuumState == null || this.vacuumSpec == null) {
            this.prepPhase = PrepPhase.IDLE;
            return;
        }
        if (!this.vacuumState.canProcess(this.vacuumSpec)) {
            // Vacuum not ready, pause but keep phase
            return;
        }
        if (this.energySource().draw(this.phaseEnergyPerTick) < this.phaseEnergyPerTick) {
            return; // paused
        }
        this.phaseProgress++;
        if (this.phaseProgress >= this.phaseDuration) {
            // Move to rinse phase
            this.prepPhase = PrepPhase.UPW_RINSE;
            this.phaseProgress = 0;
            var def = definition().orElseThrow();
            this.phaseDuration = def.param("rinse_duration").orElse(200);
            this.phaseEnergyPerTick = def.param("rinse_energy_per_tick").orElse(50);
            this.phaseWaterMb = def.param("rinse_water_mb").orElse(1000);
            setChanged();
        }
    }

    private void advanceRinsePhase() {
        if (!this.formed) {
            this.prepPhase = PrepPhase.IDLE;
            return;
        }
        if (this.energySource().draw(this.phaseEnergyPerTick) < this.phaseEnergyPerTick) {
            return; // paused
        }
        // Consume water once at the start of rinse phase
        if (this.phaseProgress == 0 && this.waterTank != null) {
            if (!this.waterTank.drainExact(this.phaseWaterMb, false)) {
                // Water shortage - pause phase
                return;
            }
        }
        this.phaseProgress++;
        if (this.phaseProgress >= this.phaseDuration) {
            // Move to dry phase
            this.prepPhase = PrepPhase.POWERED_DRY;
            this.phaseProgress = 0;
            var def = definition().orElseThrow();
            this.phaseDuration = def.param("dry_duration").orElse(200);
            this.phaseEnergyPerTick = def.param("dry_energy_per_tick").orElse(200);
            setChanged();
        }
    }

    private void advanceDryPhase() {
        if (!this.formed) {
            this.prepPhase = PrepPhase.IDLE;
            return;
        }
        if (this.energySource().draw(this.phaseEnergyPerTick) < this.phaseEnergyPerTick) {
            return; // paused
        }
        this.phaseProgress++;
        if (this.phaseProgress >= this.phaseDuration) {
            this.prepPhase = PrepPhase.COMPLETE;
            setChanged();
        }
    }

    private void completePrepJob() {
        // Finalization: apply G3 preparation to the admitted lot
        // This requires FoupContents.preparedG3 API from domain worker
        // For now, isolate the finalization method and explain the dependency

        // TODO: Integration dependency - FoupContents.preparedG3 not yet available from domain worker
        // The preparation factors should be computed from paid processing contamination average
        // and committed once via preparedG3(cleanroomMeanPermille, drawsPermille).
        // Cleanroom structural breach handling via FoupDisposal is also pending.

        // Placeholder: for now, return the FOUP to output slot unmodified to avoid silent downgrade
        if (this.admittedLot != null && this.admittedLotId != null) {
            // Reconstruct the FOUP item with the admitted lot
            ItemStack outputFoup = com.euvcraft.lithmod.core.FoupItems.createFoup(this.admittedLot);
            if (this.canAcceptInto(1, outputFoup.getItem(), 1)) {
                this.setSlot(1, outputFoup);
            } else {
                // Output blocked - should not happen if validated at start
                // Drop as entity to avoid loss
                if (this.level != null) {
                    net.minecraft.world.item.ItemStack drop = outputFoup.copy();
                    net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                            this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1, this.worldPosition.getZ() + 0.5, drop);
                    this.level.addFreshEntity(entity);
                }
            }
        }

        this.prepPhase = PrepPhase.IDLE;
        this.admittedLot = null;
        this.admittedLotId = null;
        setChanged();
    }

    // ==================== Common job utilities ====================

    private boolean canAcceptInto(int index, Item item, int count) {
        ItemStack existing = this.slot(index);
        if (count < 1 || count > Math.min(new ItemStack(item).getMaxStackSize(), this.getMaxStackSize())) return false;
        if (existing.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(existing, new ItemStack(item)) && existing.getCount() + count <= Math.min(
                existing.getMaxStackSize(), this.getMaxStackSize());
    }

    private void resetJob() {
        this.jobRunning = false;
        this.jobPaused = false;
        this.jobProgress = 0;
        this.jobDuration = 0;
        this.jobEnergyPerTick = 0;
        this.pendingInput0 = null;
        this.pendingInput1 = null;
        this.pendingOutput = null;
        this.pendingOutputCount = 0;
        this.pendingInput0Count = 0;
        this.pendingInput1Count = 0;
        this.pendingWaterMb = 0;
        this.coreCount = 1;
        setChanged();
    }

    // ==================== Cleanroom breach handling ====================

    /**
     * Notifies the machine of a structural cleanroom breach.
     * Called by the cleanroom system when the enclosure is breached.
     * @param tick current game tick
     * @return true if the machine was newly contaminated
     */
    public boolean notifyStructuralBreach(long tick) {
        if (this.kind != G3MachineKind.DEPOSITION_ETCHER) return false;
        if (this.admittedLot == null) return false; // no active lot to contaminate

        var breachConditions = new MachineContaminationState.BreachConditions(
                true, // loaded (has admitted lot)
                true, // active T7 processing (in prep phase)
                false // not empty/unpowered
        );
        var breachResult = this.contaminationState.notifyStructuralBreach(breachConditions, tick);
        this.contaminationState = breachResult.state();

        if (breachResult.notification() == MachineContaminationState.BreachNotification.NEWLY_CONTAMINATED) {
            // Scrap the pending wafers via FoupDisposal
            // The admitted lot is lost; create empty carrier
            if (this.admittedLot != null) {
                UUID freshId = UUID.randomUUID();
                FoupContents emptyCarrier = FoupDisposal.emptyCarrier(this.admittedLot, freshId);
                // TODO: Return empty carrier to player or output slot
                this.admittedLot = null;
                this.admittedLotId = null;
            }
            this.prepPhase = PrepPhase.IDLE;
            setChanged();
            return true;
        }
        return false;
    }

    /**
     * Applies a repair bill for contamination recovery.
     * @param materialCost material items required
     * @param upwMb UPW required in mB
     * @param feCost FE required
     * @return true if repair was applied (transitioned out of CONTAMINATED)
     */
    public boolean applyRepair(int materialCost, int upwMb, long feCost) {
        if (this.contaminationState.requiresRepair()) {
            // Validate we have the resources (caller should check before calling)
            var result = this.contaminationState.repair(
                    new MachineContaminationState.ContaminationRepairCost(materialCost, upwMb, feCost));
            this.contaminationState = result.state();
            setChanged();
            return result.charged();
        }
        return false;
    }

    /**
     * Completes recalibration after repair.
     * @return true if recalibration completed (transitioned to CLEAN)
     */
    public boolean recalibrate() {
        if (this.contaminationState.requiresRecalibration()) {
            var result = this.contaminationState.recalibrate();
            this.contaminationState = result.state();
            setChanged();
            return result.recalibrated();
        }
        return false;
    }

    // ==================== Persistence ====================

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("g3_formed", this.formed ? 1 : 0);
        output.putString("g3_failure", this.formation.failureReason());
        output.putInt("g3_kind", this.kind.ordinal());

        // Save formation members (world-derived, but useful for debug)
        // Not strictly needed as formation rebuilds on load

        // Save water tank
        if (this.waterTank != null) {
            output.putInt("g3_water_amount", this.waterTank.amount());
        }

        // Save vacuum state
        if (this.vacuumState != null) {
            output.putInt("g3_vacuum_pressure", this.vacuumState.pressureMicroTorr());
            output.putInt("g3_vacuum_reserve", this.vacuumState.reserveMicroTorr());
        }
        output.putInt("g3_vacuum_cursor", this.vacuumCursor);

        // Save contamination state
        output.putInt("g3_contamination_phase", this.contaminationState.phase().ordinal());
        output.putLong("g3_contamination_breach_tick", this.contaminationState.lastBreachTick());

        // Save writer job
        if (this.jobRunning) {
            output.putInt("g3_job_running", 1);
            output.putInt("g3_job_duration", this.jobDuration);
            output.putInt("g3_job_energy", this.jobEnergyPerTick);
            output.putInt("g3_job_progress", this.jobProgress);
            output.putInt("g3_job_core_count", this.coreCount);
            if (this.pendingInput0 != null) output.store("g3_job_in0", ItemStack.OPTIONAL_CODEC, this.pendingInput0);
            if (this.pendingInput1 != null) output.store("g3_job_in1", ItemStack.OPTIONAL_CODEC, this.pendingInput1);
            if (this.pendingOutput != null) output.store("g3_job_out", ItemStack.OPTIONAL_CODEC, this.pendingOutput);
            output.putInt("g3_job_in0_count", this.pendingInput0Count);
            output.putInt("g3_job_in1_count", this.pendingInput1Count);
            output.putInt("g3_job_out_count", this.pendingOutputCount);
        } else {
            output.putInt("g3_job_running", 0);
        }

        // Save prep job
        output.putInt("g3_prep_phase", this.prepPhase.ordinal());
        output.putInt("g3_prep_progress", this.phaseProgress);
        output.putInt("g3_prep_duration", this.phaseDuration);
        output.putInt("g3_prep_energy", this.phaseEnergyPerTick);
        output.putInt("g3_prep_water", this.phaseWaterMb);
        output.putInt("g3_prep_vacuum_demand", this.phaseVacuumDemand);
        output.putBoolean("g3_prep_local_pump", this.phaseLocalPumpPowered);
        if (this.admittedLot != null) {
            output.store("g3_prep_lot", FoupContents.CODEC, this.admittedLot);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.formed = input.getInt("g3_formed").orElse(0) != 0;
        this.formation.failureReason = input.getString("g3_failure").orElse("");
        this.kind = G3MachineKind.values()[input.getInt("g3_kind").orElse(0)];
        this.formation.needsValidation = true;

        // Water tank
        if (this.waterTank != null) {
            Integer amount = input.getInt("g3_water_amount").orElse(null);
            if (amount != null && amount >= 0 && amount <= WaterTank.MAX_CAPACITY) {
                this.waterTank = new WaterTank(this.waterTank.capacity(), amount);
            }
        }

        // Vacuum state
        if (this.vacuumState != null) {
            Integer pressure = input.getInt("g3_vacuum_pressure").orElse(null);
            Integer reserve = input.getInt("g3_vacuum_reserve").orElse(null);
            if (pressure != null && reserve != null) {
                this.vacuumState = VacuumChamberState.at(pressure, reserve);
            }
        }
        this.vacuumCursor = input.getInt("g3_vacuum_cursor").orElse(0);

        // Contamination state
        int phaseOrd = input.getInt("g3_contamination_phase").orElse(0);
        long breachTick = input.getLong("g3_contamination_breach_tick").orElse(-1L);
        MachineContaminationState.Phase phase = MachineContaminationState.Phase.values()[phaseOrd];
        this.contaminationState = new MachineContaminationState(phase, breachTick);

        // Writer job
        if (input.getInt("g3_job_running").orElse(0) != 0) {
            this.jobRunning = true;
            this.jobDuration = input.getInt("g3_job_duration").orElse(0);
            this.jobEnergyPerTick = input.getInt("g3_job_energy").orElse(0);
            this.jobProgress = input.getInt("g3_job_progress").orElse(0);
            this.coreCount = input.getInt("g3_job_core_count").orElse(1);
            this.pendingInput0 = input.read("g3_job_in0", ItemStack.OPTIONAL_CODEC).orElse(null);
            this.pendingInput1 = input.read("g3_job_in1", ItemStack.OPTIONAL_CODEC).orElse(null);
            this.pendingOutput = input.read("g3_job_out", ItemStack.OPTIONAL_CODEC).orElse(null);
            this.pendingInput0Count = input.getInt("g3_job_in0_count").orElse(0);
            this.pendingInput1Count = input.getInt("g3_job_in1_count").orElse(0);
            this.pendingOutputCount = input.getInt("g3_job_out_count").orElse(0);
        }

        // Prep job
        this.prepPhase = PrepPhase.values()[input.getInt("g3_prep_phase").orElse(0)];
        this.phaseProgress = input.getInt("g3_prep_progress").orElse(0);
        this.phaseDuration = input.getInt("g3_prep_duration").orElse(0);
        this.phaseEnergyPerTick = input.getInt("g3_prep_energy").orElse(0);
        this.phaseWaterMb = input.getInt("g3_prep_water").orElse(0);
        this.phaseVacuumDemand = input.getInt("g3_prep_vacuum_demand").orElse(0);
        this.phaseLocalPumpPowered = input.getBooleanOr("g3_prep_local_pump", true);
        this.admittedLot = input.read("g3_prep_lot", FoupContents.CODEC).orElse(null);
        if (this.admittedLot != null) this.admittedLotId = this.admittedLot.lotId();

        publishData();
    }

    @Override
    protected void dropExtraContents(@Nullable Level level, BlockPos pos) {
        super.dropExtraContents(level, pos);
        // Drop pending job inputs/outputs if any
        if (this.pendingInput0 != null && !this.pendingInput0.isEmpty()) {
            dropContainer(level, pos, new net.minecraft.world.SimpleContainer(this.pendingInput0));
        }
        if (this.pendingInput1 != null && !this.pendingInput1.isEmpty()) {
            dropContainer(level, pos, new net.minecraft.world.SimpleContainer(this.pendingInput1));
        }
        if (this.pendingOutput != null && !this.pendingOutput.isEmpty()) {
            dropContainer(level, pos, new net.minecraft.world.SimpleContainer(this.pendingOutput));
        }
        // Drop admitted prep lot if any
        if (this.admittedLot != null && level != null) {
            ItemStack foup = com.euvcraft.lithmod.core.FoupItems.createFoup(this.admittedLot);
            dropContainer(level, pos, new net.minecraft.world.SimpleContainer(foup));
        }
    }

    @Override
    public void setRemoved() {
        formation.unload();
        super.setRemoved();
    }
}