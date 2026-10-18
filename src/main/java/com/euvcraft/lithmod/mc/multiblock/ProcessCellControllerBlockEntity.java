package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.core.DefaultStatRules;
import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.LineLoadout;
import com.euvcraft.lithmod.core.LineStats;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessEnvironment;
import com.euvcraft.lithmod.core.ProcessRun;
import com.euvcraft.lithmod.core.ProcessRunRequest;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.ProcessTickResult;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.StatRules;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Process Cell controller block entity: it owns the cell's energy buffer and is the only place
 * that decides whether the cell is formed.
 *
 * <p>Geometry comes from {@link StructureMatcher} in core, which is the M1-03c implementation: for a
 * facing {@code f} the box starts at the controller's own cell and extends {@code length} cells
 * along {@code back = -f}, {@code width} cells along {@code right} and {@code height} cells up. The
 * controller may sit at any of the four front-face positions, so all four are tried and the first
 * full match wins. Nothing here re-derives that geometry.</p>
 *
 * <p><b>Why a re-validation is not free.</b> When a match fails, {@link MatchResult} carries no
 * member list, so the positions from the last good match have to be kept in order to clear their
 * formed flags. {@link #members()} is that list; it is cleared on failure only after the previous
 * members have been updated.</p>
 */
public class ProcessCellControllerBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider {

    // Slot indices matching the 8-slot layout: 0 FOUP, 1 MASK, 2 RESIST, 3 LIGHT_SOURCE, 4 OPTICS, 5 STAGE, 6 CLEANROOM, 7 METROLOGY
    public static final int SLOT_FOUP = 0;
    public static final int SLOT_MASK = 1;
    public static final int SLOT_RESIST = 2;
    public static final int SLOT_LIGHT_SOURCE = 3;
    public static final int SLOT_OPTICS = 4;
    public static final int SLOT_STAGE = 5;
    public static final int SLOT_CLEANROOM = 6;
    public static final int SLOT_METROLOGY = 7;
    public static final int INPUT_SLOTS = 8;
    public static final int OUTPUT_SLOTS = 0;
    public static final int SLOT_COUNT = 8;

    /** The twelve positions of the current, or last, formation. */
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private String failureReason = "";
    private int ticksSinceValidation;

    // --- M1-09 run wiring ---
    @Nullable
    private ProcessRun run;
    private boolean abortLatched;
    @Nullable
    private RefusalReason lastRefusal;
    @Nullable
    private UUID lastLotId; // tracks FOUP identity for abort latch
    @Nullable
    private Map<String, Integer> installedGenerationsSnapshot; // frozen generation map

    public static final int REVALIDATE_INTERVAL = 20;

    /** M1 stub: all five support lines at generation 1. */
    private static final Map<String, Integer> M1_STUB_GENERATIONS = Map.of(
            SupportLines.SUBSTRATE, 1,
            SupportLines.MASK_SHOP, 1,
            SupportLines.DEPOSITION_ETCH, 1,
            SupportLines.UTILITIES, 1,
            SupportLines.TEST_PACKAGING, 1
    );

    /** Deferred run tag for restoration on first server tick after load. */
    @Nullable
    private CompoundTag deferredRunTag;

    public ProcessCellControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_CONTROLLER_BE.get(), pos, state, INPUT_SLOTS, OUTPUT_SLOTS);
        // machineId() is "process_cell", base constructor calls refreshDefinition() which loads energy numbers from data
    }

    @Override
    protected String machineId() {
        return "process_cell";
    }

    // ---------------------------------------------------------------- formation state

    /** Whether all twelve cells are currently in place. */
    public boolean isFormed() {
        return this.formed;
    }

    /** Why the last match failed, or the empty string when it succeeded. */
    public String failureReason() {
        return this.failureReason;
    }

    /** The twelve positions of the current or last formation, in matcher order. */
    public List<BlockPos> members() {
        return List.copyOf(this.members);
    }

    /** How many times formation has been reported since this block entity was created. */
    private int formationChanges;

    /** Returns the number of formation transitions, for tests. */
    public int formationChanges() {
        return this.formationChanges;
    }

    // ---------------------------------------------------------------- lifecycle hooks for M1-09

    /**
     * Called when the cell becomes formed, after the twelve members exist and have been flagged.
     *
     * <p>Protected on purpose: M1-09 subclasses this block entity to give the cell a behaviour, and
     * an overridable-but-not-internal hook is the whole point of the method. It is not on
     * {@link EnergyBufferProvider}, because Java forbids narrowing an interface method to protected.</p>
     */
    protected void onFormed() {
    }

    /**
     * Called when the cell stops being formed, after the members have been unflagged.
     *
     * @see #onFormed()
     */
    protected void onUnformed() {
    }

    // ---------------------------------------------------------------- ticking

    /** One server tick: re-validate now and then, so a missed edit still heals. */
    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Formation backstop: periodic re-validation regardless of base tick
        if (++this.ticksSinceValidation >= REVALIDATE_INTERVAL) {
            this.ticksSinceValidation = 0;
            revalidate();
        }
        // Refresh definition so data sizes/closures update (capacity, per-tick cost)
        refreshDefinition();

        // Check for FOUP removal to clear abort latch
        checkFoupRemoval();

        // Try to restore a deferred run from persistence
        if (run == null && !abortLatched && deferredRunTag != null) {
            ProcessCellRunPersistence.tryRestoreRun(this);
        }

        // Try to start a run when idle, formed, FOUP present, and not aborted
        if (run == null && !abortLatched && formed && definition().isPresent()) {
            tryStartRun();
        }

        // Tick the run if active
        if (run != null) {
            tickRun();
        }

        // Publish base DATA_ENERGY/CAPACITY/PROGRESS/TICKS accurately
        data().set(DATA_ENERGY, (int) buffer().getAmountAsLong());
        // Only mark dirty on real run state/resource changes
        if (run != null || abortLatched) {
            setChanged();
        }
    }

    /**
     * Re-runs the match and pushes the answer to every member.
     *
     * <p>Public because the game tests drive it directly: waiting out
     * {@link #REVALIDATE_INTERVAL} ticks would make every test a timing question, and the point of
     * these tests is the geometry, not the scheduler.</p>
     */
    public void revalidate() {
        if (level == null || level.isClientSide() || this.revalidating) {
            return;
        }
        this.revalidating = true;
        try {
            runValidation();
        } finally {
            this.revalidating = false;
        }
    }

    // ---------------------------------------------------------------- M1-09 run wiring

    /**
     * Tries to start a run when the cell is formed and idle.
     * Called from serverTick when conditions are met.
     */
    private void tryStartRun() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (run != null || abortLatched) {
            return;
        }
        if (!formed) {
            return;
        }

        Optional<MachineDefinition> defOpt = definition();
        if (defOpt.isEmpty()) {
            return;
        }

        ItemStack foupStack = slot(SLOT_FOUP);
        if (foupStack.isEmpty()) {
            return;
        }

        ItemStack maskStack = slot(SLOT_MASK);
        if (maskStack.isEmpty()) {
            lastRefusal = RefusalReason.NO_MASK;
            return;
        }

        MaskData maskData = maskStack.get(ModDataComponents.MASK_DATA.get());
        if (maskData == null) {
            lastRefusal = RefusalReason.NO_MASK;
            return;
        }

        // Only T1 and T2 are playable in M1
        TierRegistry tierRegistry = TierDataLoader.getRegistry();
        if (tierRegistry == null) {
            lastRefusal = RefusalReason.NO_MASK;
            return;
        }
        Optional<Tier> tierOpt = tierRegistry.byIndex(maskData.tierIndex());
        if (tierOpt.isEmpty()) {
            lastRefusal = RefusalReason.NO_MASK;
            return;
        }
        Tier tier = tierOpt.get();
        if (tier.index() < 1 || tier.index() > 2) {
            lastRefusal = RefusalReason.NO_MASK;
            return;
        }
        if (maskData.isSpent()) {
            lastRefusal = RefusalReason.MASK_SPENT;
            return;
        }

        // Read FOUP lot
        FoupContents lot = FoupItems.trustedContentsOf(foupStack);
        if (lot == null || lot.isEmpty()) {
            lastRefusal = RefusalReason.NO_LOT;
            return;
        }
        if (lot.lotLayer() >= tier.layers()) {
            lastRefusal = RefusalReason.LOT_ALREADY_COMPLETE;
            return;
        }
        if (lot.run() != null && !lot.run().matches(tier, maskData.dieType(), null)) {
            lastRefusal = RefusalReason.SPEC_MISMATCH;
            return;
        }

        // Build line loadout from component slots
        ItemStack resistStack = slot(SLOT_RESIST);
        LineLoadout loadout = LineLoadoutReader.read(
                slot(SLOT_LIGHT_SOURCE),
                slot(SLOT_OPTICS),
                slot(SLOT_STAGE),
                slot(SLOT_CLEANROOM),
                slot(SLOT_METROLOGY),
                resistStack,
                maskData.tierIndex()
        );

        // Only accept playable T1/T2 resist tiers
        if (loadout.resistTier() < 1 || loadout.resistTier() > 2) {
            lastRefusal = RefusalReason.NO_MASK; // closest refusal for invalid resist
            return;
        }

        // Compute stats
        StatRules statRules = new DefaultStatRules(tierRegistry);
        LineComponents components = loadout.toLineComponents();
        LineStats lineStats = statRules.stats(components, tier.index());
        boolean lineCanRunTier = statRules.canRunTier(components, tier.index());

        // Generation matrix
        GenerationMatrix generationMatrix = SupportLineDataLoader.getMatrix();
        if (generationMatrix == null) {
            lastRefusal = RefusalReason.generationUnmet(SupportLines.ALL);
            return;
        }

        // Die rules
        boolean dieMakeable = DieRulesDataLoader.getRules().canMake(maskData.dieType(), tier);

        // Energy per tick
        int energyPerTick = defOpt.get().energyPerTick(tier, EUVCRAFTMod.settings());

        // Build request
        FabSettings settings = EUVCRAFTMod.settings();
        ProcessRunRequest request = new ProcessRunRequest(
                lot,
                maskData.dieType(),
                tier,
                null, // cores: null for M1 (no core count selection)
                maskData.usesRemaining(),
                lineStats,
                lineCanRunTier,
                dieMakeable,
                M1_STUB_GENERATIONS,
                generationMatrix,
                settings,
                energyPerTick
        );

        // Try to start
        Object result = ProcessRun.start(request);
        if (result instanceof RefusalReason refusal) {
            lastRefusal = refusal;
            return;
        }

        // Success - initialize run
        this.run = (ProcessRun) result;
        this.lastRefusal = null;
        this.lastLotId = lot.lotId();
        this.installedGenerationsSnapshot = Map.copyOf(M1_STUB_GENERATIONS);
        setChanged();
    }

    /**
     * Drives one tick of the current run.
     */
    private void tickRun() {
        if (run == null || level == null || level.isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ProcessTickResult tickResult = run.tick(this);

        // Handle events
        for (var event : tickResult.events()) {
            if (event.type() == ProcessRunEvent.Type.MASK_USE_CONSUMED) {
                // Consume one mask use
                ItemStack maskStack = slot(SLOT_MASK);
                MaskData maskData = maskStack.get(ModDataComponents.MASK_DATA.get());
                if (maskData != null && !maskData.isSpent()) {
                    setSlot(SLOT_MASK, FoupItems.with(ModItems.MASK.get(), maskData.consumeUse()));
                }
            }
        }

        // Update state
        this.run = tickResult.state() == ProcessState.FINISHED ? null : run; // keep run for FINISHED state to preserve maskUseConsumed
        if (tickResult.state() == ProcessState.FINISHED) {
            // Run finished - keep run reference but it will return FINISHED on subsequent ticks
            // Mask use already consumed via event above
        }

        // Sync progress data
        data().set(DATA_PROGRESS, run.currentLayer());
        data().set(DATA_TICKS, tierForProgress().map(t -> t.layerTicks(settings())).orElse(0));
        syncData();
        setChanged();
    }

    @Nullable
    private Tier tierForProgress() {
        if (run == null) return null;
        return run.request().tier();
    }

    private FabSettings settings() {
        return EUVCRAFTMod.settings();
    }

    /**
     * Server-only abort method. Latches the abort until actual FOUP removal and reinsertion.
     * Preserves applied layers, discards partial layer progress and paid resist/FE without refund.
     */
    public void abortRun() {
        if (run == null) {
            return;
        }
        // Latch abort - persists until FOUP removed and reinserted
        this.abortLatched = true;
        // Discard partial progress (progressTicks, resistPaid) but keep applied layers (lot)
        this.run = null;
        this.lastRefusal = null;
        this.installedGenerationsSnapshot = null;
        setChanged();
    }

    /**
     * Returns whether the input slots are locked (run in progress or abort latched).
     */
    public boolean inputsLocked() {
        return run != null || abortLatched;
    }

    /**
     * Returns whether the resist stack can be topped up (same tier, run running/paused).
     */
    public boolean canTopUpResist(ItemStack stack) {
        if (run == null) {
            return false;
        }
        int resistTier = ResistCatalog.tierOf(stack);
        if (resistTier <= 0) {
            return false;
        }
        // Must match the frozen resist tier
        ItemStack currentResist = slot(SLOT_RESIST);
        int currentResistTier = ResistCatalog.tierOf(currentResist);
        return resistTier == currentResistTier;
    }

    /**
     * Returns the current refusal reason, if any.
     */
    @Nullable
    public RefusalReason lastRefusal() {
        return lastRefusal;
    }

    /**
     * Returns the current process state for menu display.
     */
    public ProcessState processState() {
        if (run != null) {
            return run.state();
        }
        if (abortLatched) {
            return ProcessState.FINISHED; // Aborted shows as finished for menu
        }
        return ProcessState.IDLE;
    }

    /**
     * Returns the current layer number (1-based) or 0 if no run.
     */
    public int currentLayer() {
        if (run != null) {
            return run.currentLayer();
        }
        return 0;
    }

    /**
     * Returns the progress ticks within the current layer.
     */
    public int progressTicks() {
        if (run != null) {
            return run.progressTicks();
        }
        return 0;
    }

    /**
     * Returns the total layers for the current tier, or 0 if no run.
     */
    public int totalLayers() {
        if (run != null) {
            return run.request().tier().layers();
        }
        return 0;
    }

    /**
     * Returns the die type of the current run, or null.
     */
    @Nullable
    public String dieType() {
        if (run != null) {
            return run.request().dieType();
        }
        return null;
    }

    /**
     * Returns the tier index of the current run, or 0.
     */
    public int tierIndex() {
        if (run != null) {
            return run.request().tier().index();
        }
        return 0;
    }

    /**
     * Checks if the FOUP slot has been emptied (FOUP removed).
     * Called from slot removal hooks to clear abort latch.
     */
    private void checkFoupRemoval() {
        if (abortLatched && lastLotId != null) {
            ItemStack foupStack = slot(SLOT_FOUP);
            FoupContents lot = FoupItems.trustedContentsOf(foupStack);
            if (lot == null || !lot.lotId().equals(lastLotId)) {
                // FOUP was removed or replaced - clear abort latch
                this.abortLatched = false;
                this.lastLotId = null;
                this.lastRefusal = null;
            }
        }
    }

    // ---------------------------------------------------------------- ProcessEnvironment implementation

    @Override
    public boolean structureFormed() {
        // Must require REAL currently validated geometry plus definition available
        if (!formed) {
            return false;
        }
        return definition().isPresent();
    }

    @Override
    public int extractEnergy(int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int drawn = buffer().extract(maxAmount, transaction);
            if (drawn != maxAmount) {
                return 0;
            }
            if (!simulate) {
                transaction.commit();
            }
            return drawn;
        }
    }

    @Override
    public int resistAvailable() {
        ItemStack resistStack = slot(SLOT_RESIST);
        if (resistStack.isEmpty()) {
            return 0;
        }
        return resistStack.getCount();
    }

    @Override
    public void consumeResist(int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack resistStack = slot(SLOT_RESIST);
        if (!resistStack.isEmpty()) {
            resistStack.shrink(amount);
            if (resistStack.isEmpty()) {
                clearSlot(SLOT_RESIST);
            }
        }
    }

    @Override
    public FoupContents applyLayer(FoupContents lot, int layerNumber, double defectFactor) {
        if (level == null || run == null) {
            return null;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Tier tier = run.request().tier();
        String dieType = run.request().dieType();
        Integer cores = run.request().cores(); // null in M1

        try {
            FoupContents next = FabYieldService.applyLayerStep(serverLevel, lot, tier, dieType, cores, layerNumber, defectFactor);
            // Write back to FOUP slot
            if (next != null) {
                ItemStack foupStack = slot(SLOT_FOUP);
                ItemStack newFoupStack = FoupItems.with(foupStack.getItem(), next);
                setSlot(SLOT_FOUP, newFoupStack);
            }
            return next;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Yield service refused - this should not happen if admission was correct
            return null;
        }
    }

    private void runValidation() {
        this.ticksSinceValidation = 0;

        Direction facing = getBlockState().getValue(ProcessCellControllerBlock.FACING);
        MatchResult result = StructureMatcher.match(
                toCore(worldPosition),
                toCore(facing),
                StructureShape.PROCESS_CELL,
                this::blockKindAt);

        boolean nowFormed = result.success();
        List<BlockPos> nextMembers = nowFormed ? toMinecraft(result.memberPositions()) : List.of();
        boolean membershipChanged = !this.members.equals(new LinkedHashSet<>(nextMembers));

        // The reason is refreshed on every pass, not only on a transition: a controller that has
        // never formed still has to be able to say why, which is the whole point of showing the
        // player a message when they right-click it.
        this.failureReason = nowFormed ? "" : result.failureReason();

        if (nowFormed != this.formed || membershipChanged) {
            // The last good members have to be unflagged before the new ones are flagged, because a
            // failed match reports no members at all and would otherwise leave them lit forever.
            List<BlockPos> previous = members();
            this.formed = nowFormed;
            this.members.clear();
            this.members.addAll(nextMembers);

            updateMembers(previous);
            updateMembers(members());
            setChanged();
            this.formationChanges++;

            if (nowFormed) {
                onFormed();
            } else {
                onUnformed();
            }
        } else if (this.formed) {
            // The membership is unchanged, but a member's own block state can still have been reset
            // by an edit that replaced the block, which clears FORMED. updateMembers only writes a
            // state that differs, so re-running it here repairs the flags without counting as a
            // transition and without firing the hooks again.
            updateMembers(members());
        }
    }

    /** Flags or unflags every listed member and rewires its controller link. */
    private void updateMembers(List<BlockPos> positions) {
        if (level == null) {
            return;
        }
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ProcessCellMemberBlockEntity member) {
                member.attach(this, this.formed);
            }
            BlockState state = level.getBlockState(pos);
            // Only write a state that actually differs. Writing unconditionally would notify the
            // neighbours, whose neighborChanged handler calls straight back into revalidate().
            if (state.getBlock() instanceof ProcessCellCasingBlock) {
                if (state.getValue(ProcessCellCasingBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellCasingBlock.FORMED, this.formed), 3);
                }
            } else if (state.getBlock() instanceof ProcessCellPowerPortBlock) {
                if (state.getValue(ProcessCellPowerPortBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellPowerPortBlock.FORMED, this.formed), 3);
                }
            } else if (state.getBlock() instanceof ProcessCellControllerBlock) {
                if (state.getValue(ProcessCellControllerBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellControllerBlock.FORMED, this.formed), 3);
                }
            }
        }
    }

    /**
     * Reports what the cell sees at a position.
     *
     * <p>A block that already belongs to a <em>different</em> formed cell reads as
     * {@link BlockKind#OTHER}. That is what stops two adjacent cells from claiming the same twelve
     * blocks: the second controller finds the first one's members already spoken for.</p>
     */
    private BlockKind blockKindAt(MultiblockPos corePos) {
        if (level == null) {
            return BlockKind.OTHER;
        }
        BlockPos pos = new BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof ProcessCellControllerBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER;
            }
            return BlockKind.CONTROLLER;
        }
        if (blockEntity instanceof ProcessCellMemberBlockEntity member) {
            if (member.isMemberOfFormedCell() && member.controller() != this) {
                return BlockKind.OTHER;
            }
            return state.getBlock() instanceof ProcessCellPowerPortBlock
                    ? BlockKind.POWER_PORT
                    : BlockKind.CASING;
        }
        if (state.getBlock() instanceof ProcessCellPowerPortBlock) {
            return BlockKind.POWER_PORT;
        }
        if (state.getBlock() instanceof ProcessCellCasingBlock) {
            return BlockKind.CASING;
        }
        return BlockKind.OTHER;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack stack = this.items.get(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.split(Math.min(count, stack.getCount()));
        if (stack.isEmpty()) {
            clearSlot(index);
        }
        // Observe FOUP removal for abort latch
        if (index == SLOT_FOUP) {
            checkFoupRemoval();
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = this.items.get(index);
        // Observe FOUP removal for abort latch
        if (index == SLOT_FOUP) {
            checkFoupRemoval();
        }
        clearSlot(index);
        return stack;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        setSlot(index, stack);
        // Observe FOUP reinsertion for abort latch
        if (index == SLOT_FOUP) {
            checkFoupRemoval();
        }
    }

    /** Empties every slot; the {@code Clearable} half of {@link Container}. */
    @Override
    public void clearContent() {
        for (int index = 0; index < this.items.size(); index++) {
            if (index == SLOT_FOUP) {
                checkFoupRemoval();
            }
            clearSlot(index);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); // loads inventory and energy via base
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
        // Formation member list remains world-derived (not persisted)

        // Load run persistence
        ProcessCellRunPersistence.load(this, input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); // saves inventory and energy via base
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
        // Formation member list not persisted on purpose

        // Save run persistence
        ProcessCellRunPersistence.save(this, output);
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new ProcessCellMenu(containerId, playerInventory, this);
    }

    // ---------------------------------------------------------------- conversions

    private static MultiblockPos toCore(BlockPos pos) {
        return new MultiblockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static MultiblockDirection toCore(Direction facing) {
        return switch (facing) {
            case NORTH -> MultiblockDirection.NORTH;
            case SOUTH -> MultiblockDirection.SOUTH;
            case EAST -> MultiblockDirection.EAST;
            case WEST -> MultiblockDirection.WEST;
            default -> MultiblockDirection.NORTH;
        };
    }

    private static List<BlockPos> toMinecraft(List<MultiblockPos> positions) {
        List<BlockPos> converted = new ArrayList<>(positions.size());
        for (MultiblockPos pos : positions) {
            converted.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
        return converted;
    }

}
