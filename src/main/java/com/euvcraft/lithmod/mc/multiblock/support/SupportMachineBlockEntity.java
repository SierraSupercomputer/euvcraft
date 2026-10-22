package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.SupportStructureMatcher;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The block entity behind both support controllers: Boule Purifier and E-beam Mask Writer.
 *
 * <p><b>Foundation only, no jobs.</b> {@link #serverTick()} deliberately does NOT run the inherited
 * recipe runner from {@link FabMachineBlockEntity}: a support machine with no jobs must never
 * silently start an owner-less recipe. Each tick only refreshes the definition, re-validates
 * formation, and publishes the public menu data. Bucket exchange, refinement and mask selection
 * come later.</p>
 *
 * <p>Geometry comes from the pure core {@link SupportStructureMatcher}: 2 wide x 2 tall x 3 deep,
 * the controller on the lower front row in either column, one or two power ports, everything else
 * casing, no operating panel, in the canonical ordered part index {@code u*6 + v*3 + d}.</p>
 *
 * <p><b>Ownership rules.</b> Like the fixed Process Cell classes: a replaced core must never be
 * reclaimed by its departed owner; stale, removed or reloaded members reset their visual state; a
 * foreign live owner's membership is never cleared. Never shares members with another live
 * owner.</p>
 *
 * <p><b>No generic capabilities.</b> The controller exposes no item and no fluid capability and no
 * FE; the only FE that reaches the world is a formed, owned power port's view of the buffer.</p>
 */
public class SupportMachineBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider {

    public static final int REVALIDATE_INTERVAL = 20;

    // Slot indices: purifier 0 boule, 1 water bucket, 2 empty-bucket-out, 3 pure-boule-out;
    //              ebeam    0 blank plate, 1 redstone, 2 mask-out.
    public static final int SLOT_IN_0 = 0;
    public static final int SLOT_IN_1 = 1;

    /** Purifier output slot that receives the drained, now-empty bucket. */
    public static final int SLOT_EMPTY_BUCKET_OUT = 2;
    /** Purifier output slot that receives the pure boule. */
    public static final int SLOT_PURE_BOULE_OUT = 3;

    /** Purifier water tank capacity in mB before any definition resize; the tank starts EMPTY. */
    public static final int DEFAULT_WATER_CAPACITY_MB = 16_000;
    /** Purifier definition param: water tank capacity in mB. */
    public static final String PARAM_WATER_CAPACITY = "water_capacity";

    /** One water bucket into the tank is always an exact 1000 mB transaction. */
    public static final int BUCKET_WATER_MB = 1_000;

    /** The twelve positions of the current, or last, formation. */
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private boolean needsValidation = true;
    private String failureReason = "";
    private int ticksSinceValidation;

    /** The purifier's water; null on the e-beam, which owns no functional water. */
    private @Nullable WaterTank water;
    final SupportWaterInletHandler.TankJournal waterJournal = new SupportWaterInletHandler.TankJournal(this);
    private int formationChanges;

    // ---------------------------------------------------------------- purifier job state
    // Purchased only for kind() == PURIFIER. The e-beam leaves all of it untouched until its
    // own job phase exists; nothing here seeks e-beam recipes.

    /** Whether a purifier job is in progress. */
    private boolean jobRunning;
    /** Started but stopped for want of energy, an unformed cell or a full output. */
    private boolean jobPaused;
    /** Ticks completed on the job in progress. */
    private int jobProgress;
    /** The recipe the current job is pinned to, with the exact input counts it started under. */
    private @Nullable JobSnapshot job;

    // ---------------------------------------------------------------- e-beam job state
    // Purchased only for kind() == EBEAM.

    /** The seven supported e-beam designs in selector order. */
    private static final String[] EBEAM_DESIGNS = {
        "logic_t1", "logic_t2", "memory_t2", "logic_t3", "memory_t3", "compute_t3", "nand_t3", "logic_t4", "memory_t4", "compute_t4", "nand_t4", "logic_t5", "memory_t5", "compute_t5", "nand_t5"
    };
    /** Die type for each design index. */
    private static final String[] EBEAM_DIE_TYPES = {DieTypes.LOGIC,DieTypes.LOGIC,DieTypes.MEMORY,DieTypes.LOGIC,DieTypes.MEMORY,DieTypes.COMPUTE,DieTypes.NAND,DieTypes.LOGIC,DieTypes.MEMORY,DieTypes.COMPUTE,DieTypes.NAND,DieTypes.LOGIC,DieTypes.MEMORY,DieTypes.COMPUTE,DieTypes.NAND};
    /** Tier index for each design index. */
    private static final int[] EBEAM_TIER_INDICES = { 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5 };

    /** Currently selected design index (0-6), or -1 for no selection. */
    private int selectedDesign = -1;
    /** Whether an e-beam job is in progress. */
    private boolean ebeamJobRunning;
    /** Started but stopped for want of energy, an unformed cell or a full output. */
    private boolean ebeamJobPaused;
    /** Ticks completed on the e-beam job in progress. */
    private int ebeamJobProgress;
    /** The pinned e-beam job snapshot. */
    private @Nullable EbeamJobSnapshot ebeamJob;

    public SupportMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SupportMachines.SUPPORT_CONTROLLER_BE.get(), pos, state,
            kind(state).inputSlots(), kind(state).outputSlots());
        if (kind() == SupportMachineKind.PURIFIER) {
            // Starts EMPTY with 16000 mB of capacity; refreshDefinition (already run by the base
            // constructor) resizes the capacity from the definition param. The amount is preserved
            // when the capacity changes, and it starts at zero here — no free water.
            this.water = new WaterTank(DEFAULT_WATER_CAPACITY_MB);
        }
    }

    /** Static-side kind lookup so the base constructor can size the inventory from it. */
    private static SupportMachineKind kind(BlockState state) {
        SupportMachineKind kind = SupportMachineKind.of(state);
        if (kind == null) {
            throw new IllegalArgumentException(
                "Support controller block entity requires a support controller block, got " + state);
        }
        return kind;
    }

    /** The kind derived from the actual block state at this position. */
    public final SupportMachineKind kind() {
        return kind(this.getBlockState());
    }

    @Override
    protected String machineId() {
        // machineId() is read by the base constructor; getBlockState() is valid by then because the
        // BlockEntity super constructor has already stored the state.
        return kind().id();
    }

    // ---------------------------------------------------------------- water

    /** The purifier's tank, or null on the e-beam. */
    public @Nullable WaterTank waterTank() {
        return this.water;
    }

    /** Water currently stored in mB; 0 on the e-beam. */
    public int waterAmount() {
        return this.water == null ? 0 : this.water.amount();
    }

    /** Tank capacity in mB from the definition param; 0 on the e-beam. */
    public int waterCapacity() {
        return this.water == null ? 0 : this.water.capacity();
    }

    // ---------------------------------------------------------------- formation state

    /** Whether all twelve blocks are currently in place. */
    public boolean isFormed() {
        return this.formed;
    }

    /** Why the last match failed, or the empty string when it succeeded. */
    public String failureReason() {
        return this.failureReason;
    }

    /** The twelve positions of the current or last formation, in matcher (u*6+v*3+d) order. */
    public List<BlockPos> members() {
        return List.copyOf(this.members);
    }

    /** Number of formation transitions since this block entity was created; for tests. */
    public int formationChanges() {
        return this.formationChanges;
    }

    // ---------------------------------------------------------------- ticking

    /**
     * The job phase: bucket exchange, then the purifier's own run. Never the recipe runner.
     *
     * <p>The energy numbers live in the base {@link FabMachineBlockEntity#serverTick()} path, which
     * is deliberately not called here: it would run the generic {@code MachineRecipeRunner} over
     * the machine_recipes registry, whose recipes cannot pin their own water cost and duration.
     * The purifier owns its own job loop over {@code data/lithmod/support_recipes} instead, and
     * the runner object inherited from the base class stays at rest forever — a support machine
     * must never silently start an owner-less recipe.</p>
     *
     * <p>A job runs only while the cell is formed: an unformed cell with a stored pause keeps
     * its progress and never charges FE, and reforming resumes it.</p>
     */
    @Override
    public void serverTick() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel) || isRemoved()
                || level.getBlockEntity(worldPosition) != this) return;
        refreshDefinition();
        syncWaterCapacity();
        if (needsValidation || ++ticksSinceValidation >= REVALIDATE_INTERVAL) {
            needsValidation = false;
            revalidate();
        }
        if (kind() == SupportMachineKind.PURIFIER) {
            tryBucketRefill();
            tickPurifierJob();
        } else if (kind() == SupportMachineKind.EBEAM) {
            tickEbeamJob();
        }
        publishData();
    }

    /** Publishes the public menu values, including the purifier job's progress and e-beam state. */
    private void publishData() {
        data().set(FabMachineBlockEntity.DATA_ENERGY, buffer().getAmountAsInt());
        data().set(FabMachineBlockEntity.DATA_CAPACITY, buffer().getCapacityAsInt());
        // A pinned job keeps its own duration even if the loaded data changed under it, so
        // DATA_TICKS is what the job actually runs against, never the registry's word.
        if (kind() == SupportMachineKind.PURIFIER) {
            data().set(FabMachineBlockEntity.DATA_PROGRESS, this.jobRunning ? this.jobProgress : 0);
            data().set(FabMachineBlockEntity.DATA_TICKS,
                this.jobRunning && this.job != null ? this.job.duration() : 0);
        } else {
            data().set(FabMachineBlockEntity.DATA_PROGRESS, this.ebeamJobRunning ? this.ebeamJobProgress : 0);
            data().set(FabMachineBlockEntity.DATA_TICKS,
                this.ebeamJobRunning && this.ebeamJob != null ? this.ebeamJob.duration() : 0);
        }
    }

    // ---------------------------------------------------------------- bucket exchange

    /**
     * One full water bucket becomes exactly 1000 mB of tank water plus exactly one empty
     * bucket in the empty-bucket output slot, all or nothing.
     *
     * <p>This is the bucket adapter; the tank itself never knows an item exists. The whole
     * exchange runs as a simulation first and commits only when every part of it fits: the
     * tank must have room for the full 1000 mB and slot 2 must take exactly one empty
     * bucket. Anything else — a partial bucket in the slot (any non-water-bucket stack),
     * a full tank, a blocked output — changes nothing and preserves both the inventory and
     * the tank.</p>
     *
     * <p>Gated on formation as the routine consistent choice: an unformed cell exchanges
     * nothing, so a work bench someone re-equipped mid-repair cannot quietly fill the tank.
     * Filling and draining the tank through {@link WaterTank} are independent of this path,
     * so the water inlet uses the same tank without changing bucket exchange. No item
     * capability is registered.</p>
     */
    private void tryBucketRefill() {
        if (!this.formed || this.water == null) {
            return;
        }
        ItemStack in = slot(SLOT_IN_1);
        if (!in.is(Items.WATER_BUCKET)) {
            return;
        }

        ItemStack out = slot(SLOT_EMPTY_BUCKET_OUT);
        boolean outputCanAccept = out.isEmpty()
            || (ItemStack.isSameItemSameComponents(out, new ItemStack(Items.BUCKET)) && out.getCount() < out.getMaxStackSize());
        if (!this.water.fillExact(BUCKET_WATER_MB, true) || !outputCanAccept) {
            return;
        }

        // Commit once, after the simulation agreed to all of it.
        this.water.fillExact(BUCKET_WATER_MB, false);
        ItemStack newEmpty = out.isEmpty()
            ? new ItemStack(Items.BUCKET)
            : out.copyWithCount(out.getCount() + 1);
        setSlot(SLOT_EMPTY_BUCKET_OUT, newEmpty);
        in.shrink(1);
        if (in.isEmpty()) clearSlot(SLOT_IN_1);
        setChanged();
    }

    // ---------------------------------------------------------------- purifier job loop

    /**
     * One tick of the purifier's job loop: start a run when everything conspires, or advance
     * the pinned run.
     *
     * <p>A job may start only when the cell is formed, at least one valid recipe is loaded
     * for {@code boule_purifier}, the boule slot holds the recipe's resolved input, the tank
     * holds the recipe's water cost, and the pure-boule output slot can take the products.
     * The moment those hold, the recipe's duration, per-tick cost, water cost and output are
     * pinned into a snapshot, so a data reload mid-run can never retroactively change what
     * the running job costs or produces.</p>
     */
    private void tickPurifierJob() {
        if (this.jobRunning) {
            advanceJob();
        } else if (this.formed) {
            maybeStartJob();
        }
    }

    /** Starts a job if every precondition holds; pins the recipe and the exact input state. */
    private void maybeStartJob() {
        SupportRecipeLoader.Registry registry = SupportRecipeLoader.getRegistry();
        SupportRecipeLoader.Resolved recipe = registry == null ? null : registry.firstFor(kind().id()).orElse(null);
        if (recipe == null || !isFormed()) {
            return;
        }

        ItemStack bouleIn = slot(SLOT_IN_0);
        boolean inputPresent = bouleIn.is(recipe.inputItem()) && bouleIn.getCount() >= recipe.recipe().input().count();
        boolean waterAvailable = this.water != null && this.water.amount() >= recipe.recipe().waterMb();
        boolean outputRoom = canAcceptInto(SLOT_PURE_BOULE_OUT, recipe.outputItem(), recipe.recipe().outputCount());
        if (!inputPresent || !waterAvailable || !outputRoom) {
            return;
        }

        // Pin recipe and exact inputs: this job's numbers come from this snapshot and from
        // nothing else until the job commits or is cancelled.
        this.job = new JobSnapshot(recipe.recipe().duration(), recipe.recipe().energyPerTick(),
            recipe.recipe().waterMb(), recipe.outputItem(), recipe.recipe().outputCount(),
            recipe.inputItem(), recipe.recipe().input().count(), this.water.amount());
        this.jobRunning = true;
        this.jobPaused = false;
        this.jobProgress = 0;
        setChanged();
    }

    /**
     * One tick of a run in progress; mirrors the runner's pause rules with the purifier's
     * own cancellation rule.
     *
     * <p>Order matters and is the whole of the conservation rule: formation first, because
     * an unformed cell never charges; then tampering, which drops the run without a refund
     * because progress already paid for is progress spent; then the output slot, so a full
     * output pauses with its progress intact; then the energy. The output room is checked
     * once more immediately before the commit, because that is the tick it gets written.</p>
     */
    private void advanceJob() {
        JobSnapshot pinned = this.job;
        if (pinned == null) {
            // Defensive: a running job always pins a snapshot. Fail closed rather than run free.
            resetJob();
            return;
        }
        if (!this.formed) {
            this.jobPaused = true;
            return;
        }
        if (inputTampered(pinned)) {
            // No FE refund: whatever the tamperer already made the machine draw is spent.
            resetJob();
            return;
        }
        if (!canAcceptInto(SLOT_PURE_BOULE_OUT, pinned.outputItem, pinned.outputCount)) {
            this.jobPaused = true;
            return;
        }
        if (this.energySource().draw(pinned.energyPerTick) < pinned.energyPerTick) {
            this.jobPaused = true;
            return;
        }

        this.jobPaused = false;
        this.jobProgress++;
        if (this.jobProgress < pinned.duration) {
            return;
        }
        completeJob(pinned);
    }

    /**
     * Writes the product and only then consumes the inputs, exactly once.
     *
     * <p>Both the input and the water are re-checked here, because this is the tick the
     * machine pays for and the tick it hands out a pure boule on: a tamper between the check
     * at the top of {@link #advanceJob} and now cancels the run and consumes nothing. The
     * committed water comes from the snapshot, not from the registry, so a reload mid-run is
     * what the player paid for, not today's price.</p>
     */
    private void completeJob(JobSnapshot pinned) {
        ItemStack bouleIn = slot(SLOT_IN_0);
        if (!this.formed || inputTampered(pinned) || this.water == null
                || !canAcceptInto(SLOT_PURE_BOULE_OUT, pinned.outputItem, pinned.outputCount)) {
            resetJob();
            return;
        }

        if (!this.water.drainExact(pinned.waterMb, false)) {
            // Defensive: the tamper check above pins the amount; this cannot normally fail.
            resetJob();
            return;
        }
        bouleIn.shrink(pinned.inputCount);
        if (bouleIn.isEmpty()) {
            clearSlot(SLOT_IN_0);
        }
        ItemStack existing = slot(SLOT_PURE_BOULE_OUT);
        ItemStack product = existing.isEmpty()
            ? new ItemStack(pinned.outputItem, pinned.outputCount)
            : existing.copyWithCount(existing.getCount() + pinned.outputCount);
        setSlot(SLOT_PURE_BOULE_OUT, product);
        setChanged();
        resetJob();
    }

    /** Whether the pinned exact input state is no longer what the job started under. */
    private boolean inputTampered(JobSnapshot pinned) {
        if (this.water == null || this.water.amount() < pinned.waterMb) {
            return true;
        }
        ItemStack bouleIn = slot(SLOT_IN_0);
        return !bouleIn.is(pinned.inputItem) || bouleIn.getCount() < pinned.inputCount;
    }

    /** Drops the job in progress and its progress; nothing is refunded. */
    private void resetJob() {
        this.jobRunning = false;
        this.jobPaused = false;
        this.jobProgress = 0;
        this.job = null;
        setChanged();
    }

    /** Whether {@code count} of {@code item} fits into the output slot at {@code index}. */
    private boolean canAcceptInto(int index, net.minecraft.world.item.Item item, int count) {
        ItemStack existing = slot(index);
        if (count < 1 || count > Math.min(new ItemStack(item).getMaxStackSize(), getMaxStackSize())) return false;
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, new ItemStack(item)) && existing.getCount() + count <= Math.min(
            existing.getMaxStackSize(), getMaxStackSize());
    }

    /** One pinned purifier job: the recipe's numbers and the exact input state at start. */
    private record JobSnapshot(
        int duration,
        int energyPerTick,
        int waterMb,
        net.minecraft.world.item.Item outputItem,
        int outputCount,
        net.minecraft.world.item.Item inputItem,
        int inputCount,
        int waterPinned
    ) { }

    /** Whether a purifier job is in progress right now. */
    public boolean isJobRunning() {
        return this.jobRunning;
    }

    /** Started but stopped for want of energy, an unformed cell or a full output. */
    public boolean isJobPaused() {
        return this.jobPaused;
    }

    /** Ticks completed on the job in progress. */
    public int jobProgress() {
        return this.jobProgress;
    }

    /** Ticks the job in progress needs; zero when idle. */
    public int jobDuration() {
        return this.jobRunning && this.job != null ? this.job.duration() : 0;
    }

    // ---------------------------------------------------------------- e-beam job loop

    /**
     * One tick of the e-beam's job loop: advance the pinned run or do nothing.
     * Jobs are started only via menu button (server-side clickMenuButton), never here.
     */
    private void tickEbeamJob() {
        if (this.ebeamJobRunning) {
            advanceEbeamJob();
        }
        // No auto-start: the server-only clickMenuButton handler starts jobs.
    }

    /** Advances a pinned e-beam job by one tick; mirrors purifier pause rules. */
    private void advanceEbeamJob() {
        EbeamJobSnapshot pinned = this.ebeamJob;
        if (pinned == null) {
            resetEbeamJob();
            return;
        }
        if (!this.formed) {
            this.ebeamJobPaused = true;
            return;
        }
        if (ebeamInputTampered(pinned)) {
            resetEbeamJob();
            return;
        }
        if (!canAcceptInto(SLOT_IN_0 + 2, pinned.outputItem, pinned.outputCount)) {
            this.ebeamJobPaused = true;
            return;
        }
        if (this.energySource().draw(pinned.energyPerTick) < pinned.energyPerTick) {
            this.ebeamJobPaused = true;
            return;
        }

        this.ebeamJobPaused = false;
        this.ebeamJobProgress++;
        if (this.ebeamJobProgress < pinned.duration) {
            return;
        }
        completeEbeamJob(pinned);
    }

    /**
     * Writes the GEN2 mask and only then consumes the inputs, exactly once.
     *
     * <p>Inputs are re-checked here because this is the tick the machine pays for.
     * A tamper between the check at the top of {@link #advanceEbeamJob} and now
     * cancels the run and consumes nothing. The committed recipe numbers come from
     * the snapshot, not from the registry, so a reload mid-run is what the player
     * paid for, not today's price.</p>
     */
    private void completeEbeamJob(EbeamJobSnapshot pinned) {
        ItemStack blankIn = slot(SLOT_IN_0);
        ItemStack redstoneIn = slot(SLOT_IN_1);
        if (!this.formed || ebeamInputTampered(pinned)
                || !canAcceptInto(SLOT_IN_0 + 2, pinned.outputItem, pinned.outputCount)) {
            resetEbeamJob();
            return;
        }

        // Consume exact inputs: 1 blank + JSON redstone count
        blankIn.shrink(pinned.blankCount);
        if (blankIn.isEmpty()) clearSlot(SLOT_IN_0);
        redstoneIn.shrink(pinned.redstoneCount);
        if (redstoneIn.isEmpty()) clearSlot(SLOT_IN_1);

        MaskData maskData = new MaskData(pinned.dieType, pinned.tierIndex, pinned.uses, 2);
        ItemStack product = new ItemStack(pinned.outputItem);
        product.set(com.euvcraft.lithmod.mc.registry.ModDataComponents.MASK_DATA.get(), maskData);

        ItemStack existing = slot(SLOT_IN_0 + 2);
        if (existing.isEmpty()) {
            setSlot(SLOT_IN_0 + 2, product);
        } else {
            existing.setCount(existing.getCount() + product.getCount());
        }
        setChanged();
        resetEbeamJob();
    }

    /** Whether the pinned exact input state is no longer what the e-beam job started under. */
    private boolean ebeamInputTampered(EbeamJobSnapshot pinned) {
        ItemStack blankIn = slot(SLOT_IN_0);
        ItemStack redstoneIn = slot(SLOT_IN_1);
        return !blankIn.is(pinned.blankItem) || blankIn.getCount() < pinned.blankCount
            || !redstoneIn.is(pinned.redstoneItem) || redstoneIn.getCount() < pinned.redstoneCount;
    }

    /** Drops the e-beam job in progress and its progress; nothing is refunded. */
    private void resetEbeamJob() {
        this.ebeamJobRunning = false;
        this.ebeamJobPaused = false;
        this.ebeamJobProgress = 0;
        this.ebeamJob = null;
        setChanged();
    }

    /** One pinned e-beam job: the recipe's numbers, design, and exact input state at start. */
    private record EbeamJobSnapshot(
        int duration,
        int energyPerTick,
        int blankCount,
        int redstoneCount,
        net.minecraft.world.item.Item blankItem,
        net.minecraft.world.item.Item redstoneItem,
        net.minecraft.world.item.Item outputItem,
        int outputCount,
        String dieType,
        int tierIndex,
        int uses
    ) { }

    // ---------------------------------------------------------------- e-beam menu actions

    /**
     * Server-side handler for design selection button.
     * @param designIndex 0-6 for the seven designs, -1 to clear selection
     * @return true if the selection was accepted
     */
    public boolean selectDesign(int designIndex) {
        if (kind() != SupportMachineKind.EBEAM) return false;
        if (designIndex < -1 || designIndex >= EBEAM_DESIGNS.length) return false;
        if (this.ebeamJobRunning) return false; // selection cannot change active job
        this.selectedDesign = designIndex;
        setChanged();
        return true;
    }

    /**
     * Server-side handler for WriteMask Start button.
     * Validates everything and pins the job snapshot.
     * @return true if the job was started
     */
    public boolean startEbeamJob() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel) || isRemoved()
                || level.getBlockEntity(worldPosition) != this || kind() != SupportMachineKind.EBEAM
                || ebeamJobRunning || !formed) return false;
        SupportRecipeLoader.Resolved resolved = selectedEbeamRecipe(selectedDesign);
        if (resolved == null) return false;
        SupportRecipe recipe = resolved.recipe();
        if (!slot(0).is(resolved.inputItem()) || slot(0).getCount() < recipe.input().count()
                || !slot(1).is(Items.REDSTONE) || slot(1).getCount() < recipe.secondary().count()
                || !canAcceptInto(2, resolved.outputItem(), 1)) return false;
        int uses = EUVCRAFTMod.settings().maskUses();
        if (uses <= 0) return false;
        ebeamJob = new EbeamJobSnapshot(recipe.duration(), recipe.energyPerTick(), recipe.input().count(),
            recipe.secondary().count(), resolved.inputItem(), Items.REDSTONE, resolved.outputItem(), 1,
            EBEAM_DIE_TYPES[selectedDesign], EBEAM_TIER_INDICES[selectedDesign], uses);
        ebeamJobRunning = true;
        ebeamJobPaused = false;
        ebeamJobProgress = 0;
        setChanged();
        return true;
    }

    // ---------------------------------------------------------------- e-beam public accessors

    /** Current selected design index (0-6), or -1 if none. */
    public int selectedDesign() {
        return this.selectedDesign;
    }

    /** Design name for the current selection, or null if none. */
    public String selectedDesignName() {
        if (this.selectedDesign < 0 || this.selectedDesign >= EBEAM_DESIGNS.length) return null;
        return EBEAM_DESIGNS[this.selectedDesign];
    }

    /** Die type for the current selection, or null if none. */
    public String selectedDieType() {
        if (this.selectedDesign < 0 || this.selectedDesign >= EBEAM_DIE_TYPES.length) return null;
        return EBEAM_DIE_TYPES[this.selectedDesign];
    }

    /** Tier index for the current selection, or 0 if none. */
    public int selectedTierIndex() {
        if (this.selectedDesign < 0 || this.selectedDesign >= EBEAM_TIER_INDICES.length) return 0;
        return EBEAM_TIER_INDICES[this.selectedDesign];
    }

    /** Whether an e-beam job is in progress right now. */
    public boolean isEbeamJobRunning() {
        return this.ebeamJobRunning;
    }

    /** Started but stopped for want of energy, an unformed cell or a full output. */
    public boolean isEbeamJobPaused() {
        return this.ebeamJobPaused;
    }

    /** Ticks completed on the e-beam job in progress. */
    public int ebeamJobProgress() {
        return this.ebeamJobProgress;
    }

    /** Ticks the e-beam job in progress needs; zero when idle. */
    public int ebeamJobDuration() {
        return this.ebeamJobRunning && this.ebeamJob != null ? this.ebeamJob.duration() : 0;
    }

    /** Number of supported e-beam designs (7). */
    public int ebeamDesignCount() {
        return EBEAM_DESIGNS.length;
    }

    /** Whether the given design index has a valid loaded recipe. */
    public boolean ebeamDesignAvailable(int designIndex) {
        return selectedEbeamRecipe(designIndex) != null;
    }

    private SupportRecipeLoader.@Nullable Resolved selectedEbeamRecipe(int index) {
        if (index < 0 || index >= EBEAM_DESIGNS.length) return null;
        SupportRecipeLoader.Registry registry = SupportRecipeLoader.getRegistry();
        if (registry == null) return null;
        return registry.byId(Identifier.fromNamespaceAndPath("lithmod", "ebeam_" + EBEAM_DESIGNS[index]))
            .filter(r -> r.recipe().machine().equals("ebeam_mask_writer")).orElse(null);
    }

    public int ebeamAvailability() {
        int bits = 0;
        for (int i = 0; i < EBEAM_DESIGNS.length; i++) if (ebeamDesignAvailable(i)) bits |= 1 << i;
        return bits;
    }

    public int ebeamBlankCost() {
        if (ebeamJobRunning && ebeamJob != null) return ebeamJob.blankCount();
        var recipe = selectedEbeamRecipe(selectedDesign);
        return recipe == null ? 0 : recipe.recipe().input().count();
    }

    public int ebeamRedstoneCost() {
        if (ebeamJobRunning && ebeamJob != null) return ebeamJob.redstoneCount();
        var recipe = selectedEbeamRecipe(selectedDesign);
        return recipe == null ? 0 : recipe.recipe().secondary().count();
    }

    public int ebeamRecipeTicks() {
        if (ebeamJobRunning && ebeamJob != null) return ebeamJob.duration();
        var recipe = selectedEbeamRecipe(selectedDesign);
        return recipe == null ? 0 : recipe.recipe().duration();
    }

    public int ebeamEnergyCost() {
        if (ebeamJobRunning && ebeamJob != null) return ebeamJob.energyPerTick();
        var recipe = selectedEbeamRecipe(selectedDesign);
        return recipe == null ? 0 : recipe.recipe().energyPerTick();
    }

    private static int designIndex(String dieType, int tier) {
        for (int i = 0; i < EBEAM_DESIGNS.length; i++)
            if (EBEAM_DIE_TYPES[i].equals(dieType) && EBEAM_TIER_INDICES[i] == tier) return i;
        return -1;
    }

    // ---------------------------------------------------------------- capability lens

    // The controller's buffer lens is the inherited public final
    // FabMachineBlockEntity.energy(): it accepts insertion on every side and refuses every
    // extraction. No capability is registered for the controller block entity type at all, so
    // this lens only reaches the world through a formed, owned power port.

    // ---------------------------------------------------------------- definition (water)

    /**
     * Syncs the tank capacity from the definition's water_capacity param.
     *
     * <p>{@code applyDefinition} is final in the base class, so the water side of the definition
     * rides behind it: every caller of {@code refreshDefinition()} — the tick, the load path, the
     * constructor — follows up with this method. The tank is resized, which preserves the stored
     * water even if the new capacity is smaller ({@link WaterTank#resize} contract). A param
     * outside the tank's strict bounds ({@code 1..1_000_000}) is ignored rather than applied, so a
     * broken definition cannot crash every tick; the previously valid capacity stays in force.</p>
     */
    private void syncWaterCapacity() {
        if (this.water == null) {
            return;
        }
        Optional<Integer> param = definition().flatMap(def -> def.param(PARAM_WATER_CAPACITY));
        if (param.isEmpty()) {
            return;
        }
        int requested = param.get();
        if (requested < 1 || requested > WaterTank.MAX_CAPACITY) {
            return;
        }
        this.water.resize(requested);
    }

    // ---------------------------------------------------------------- revalidation

    /**
     * Re-runs the match and pushes the answer to every member.
     *
     * <p>Public because the game tests drive it directly (see the Process Cell tests) instead of
     * waiting out {@link #REVALIDATE_INTERVAL} ticks.</p>
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

    private void runValidation() {
        this.ticksSinceValidation = 0;

        Direction facing = getBlockState().getValue(SupportMachineControllerBlock.FACING);
        MatchResult result = SupportStructureMatcher.match(
                toCore(worldPosition),
                toCore(facing),
                this::blockKindAt);

        boolean nowFormed = result.success();
        List<BlockPos> nextMembers = nowFormed ? toMinecraft(result.memberPositions()) : List.of();
        boolean membershipChanged = !this.members.equals(new LinkedHashSet<>(nextMembers));

        this.failureReason = nowFormed ? "" : result.failureReason();

        if (nowFormed != this.formed || membershipChanged) {
            List<BlockPos> previous = members();
            this.formed = nowFormed;
            this.members.clear();
            this.members.addAll(nextMembers);

            updateMembers(previous, facing, false);
            updateMembers(members(), facing, nowFormed);
            setChanged();
            this.formationChanges++;

            if (nowFormed) {
                onFormed();
            } else {
                onUnformed();
            }
        } else if (this.formed) {
            // Membership unchanged, but a member's block state can still have been reset by an edit
            // that replaced the block (clearing FORMED/PART/FACING). updateMembers only writes a
            // state that differs, so re-running it repairs the flags without a transition.
            updateMembers(members(), facing, true);
        }
    }

    /** Formation hook for the job phase; fires after all members are flagged. */
    protected void onFormed() {
    }

    /** Unformation hook for the job phase; fires after all members are unflagged. */
    protected void onUnformed() {
    }

    /** Flags or unflags every listed member and rewires its controller link with PART and FACING. */
    private void updateMembers(List<BlockPos> positions, Direction facing, boolean formed) {
        if (level == null) return;
        List<BlockPos> current = members();
        for (BlockPos pos : positions) {
            BlockEntity entity = level.getBlockEntity(pos);
            // A departed controller must never modify the replacement at its own old location.
            if (entity instanceof SupportMachineBlockEntity supportController && supportController != this) continue;
            if (entity instanceof SupportMemberBlockEntity member) {
                SupportMachineBlockEntity owner = member.controller();
                // Never clear a foreign live owner's member.
                if (owner != null && owner != this && level.getBlockEntity(owner.getBlockPos()) == owner
                        && owner.isFormed() && owner.members().contains(pos)) continue;
            }
            int part = formed ? current.indexOf(pos) : 0;
            if (part < 0) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SupportMachineControllerBlock)
                    && !(state.getBlock() instanceof SupportCasingBlock)
                    && !(state.getBlock() instanceof SupportPowerPortBlock)) continue;
            if (entity instanceof SupportMemberBlockEntity member) {
                member.attach(formed ? this : null, formed, part, formed ? facing : null);
            }
            BlockState next = state.setValue(SupportMachineControllerBlock.FORMED, formed)
                .setValue(SupportMachineControllerBlock.PART, part);
            if (formed) next = next.setValue(SupportMachineControllerBlock.FACING, facing);
            if (next != state) level.setBlock(pos, next, 3);
        }
    }

    /**
     * Reports what the machine sees at a position.
     *
     * <p>A block that already belongs to a different formed machine reads as
     * {@link BlockKind#OTHER}, which stops two adjacent machines from claiming the same twelve
     * blocks. The block entity at the controller position must still be this one: another
     * {@link SupportMachineKind} or a foreign controller instance at this position is OTHER.</p>
     */
    private BlockKind blockKindAt(MultiblockPos corePos) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return BlockKind.OTHER;
        }
        BlockPos pos = new BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof SupportMachineBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER;
            }
            return BlockKind.CONTROLLER;
        }
        if (blockEntity instanceof SupportMemberBlockEntity member) {
            if (member.isMemberOfFormedMachine() && member.controller() != this) {
                return BlockKind.OTHER;
            }
            if (state.getBlock() instanceof SupportPowerPortBlock) {
                return BlockKind.POWER_PORT;
            }
            if (state.getBlock() instanceof SupportWaterInletBlock) {
                // Water inlet only valid for purifier; e-beam rejects it
                return kind() == SupportMachineKind.PURIFIER ? BlockKind.WATER_INLET : BlockKind.OTHER;
            }
            return BlockKind.CASING;
        }
        if (state.getBlock() instanceof SupportPowerPortBlock) {
            return BlockKind.POWER_PORT;
        }
        if (state.getBlock() instanceof SupportWaterInletBlock) {
            return kind() == SupportMachineKind.PURIFIER ? BlockKind.WATER_INLET : BlockKind.OTHER;
        }
        if (state.getBlock() instanceof SupportCasingBlock) {
            return BlockKind.CASING;
        }
        return BlockKind.OTHER;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); // loads inventory and energy via base
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
        this.needsValidation = true;
        // Stale job state is cleared before anything is restored, because this can run on an
        // existing block entity that was already mid-job.
        this.jobRunning = false;
        this.jobPaused = false;
        this.jobProgress = 0;
        this.job = null;
        this.ebeamJobRunning = false;
        this.ebeamJobPaused = false;
        this.ebeamJobProgress = 0;
        this.ebeamJob = null;
        this.selectedDesign = -1;
        // The base load path already ran refreshDefinition; apply the definition's water capacity
        // before restoring the persisted amount, so a valid save round-trips capacity and amount.
        syncWaterCapacity();
        // Formation member list remains world-derived (not persisted). The water amount is:
        if (this.water != null) {
            Integer amount = input.getInt("supportWaterAmount").orElse(null);
            // Strict bounds: a save written out of bounds must never be adopted. A negative or
            // over-hard-bound value resets the tank to the post-definition state instead of
            // crashing or inventing water.
            if (amount != null && amount >= 0 && amount <= WaterTank.MAX_CAPACITY) {
                // A valid save can legitimately carry an amount between the current capacity and
                // the hard bound (the definition shrank between sessions); WaterTank accepts that
                // and rejects further fill until room returns.
                this.water = new WaterTank(this.water.capacity(), amount);
            }
        }
        restorePurifierJob(input.childOrEmpty("purifierJob"));
        int savedSelection = input.getInt("selectedDesign").orElse(-1);
        this.selectedDesign = savedSelection >= -1 && savedSelection < EBEAM_DESIGNS.length ? savedSelection : -1;
        restoreEbeamJob(input.childOrEmpty("ebeamJob"));
        publishData();
    }

    /**
     * Restores a pinned e-beam job from its own save child.
     *
     * <p>Every number the job will ever spend is in the save: the recipe's duration, per-tick
     * cost, input counts, output item and count, plus the exact input state the run started
     * under and the design (die type + tier). Nothing is re-read from the current recipes
     * registry, so a reload that changes the recipe definition can never retroactively change
     * the cost, the length, the inputs or the output of a job that is already running — the
     * player finishes what they paid to start.</p>
     *
     * <p>The save is rejected wholesale when anything in it is not a plausible running job:
     * a missing or negative field, a progress count at or past the duration (a running job can
     * never be saved in the tick it committed), an unknown output or input item, or an invalid
     * design. That is the fail-closed answer to a hand-edited tag: the job is dropped, no cost
     * arrives from nowhere, and the machine is simply idle until real inputs start a fresh job.</p>
     */
    private void restorePurifierJob(ValueInput state) {
        if (!state.getBooleanOr("running", false)) {
            return;
        }
        Integer duration = state.getInt("duration").orElse(null);
        Integer energyPerTick = state.getInt("energy_per_tick").orElse(null);
        Integer waterMb = state.getInt("water_mb").orElse(null);
        Integer outputCount = state.getInt("output_count").orElse(null);
        Integer inputCount = state.getInt("input_count").orElse(null);
        Integer progress = state.getInt("progress").orElse(null);
        Integer waterPinned = state.getInt("water_pinned").orElse(null);
        Item outputItem = itemFrom(state.getString("output_item").orElse(null));
        Item inputItem = itemFrom(state.getString("input_item").orElse(null));

        boolean valid = duration != null && energyPerTick != null && waterMb != null
            && outputCount != null && inputCount != null && progress != null && waterPinned != null
            && duration > 0 && energyPerTick > 0 && waterMb > 0 && outputCount > 0 && inputCount > 0
            && progress >= 0 && progress < duration;
        if (valid) {
            valid = kind() == SupportMachineKind.PURIFIER
                && outputItem == com.euvcraft.lithmod.mc.registry.ModItems.PURE_SILICON_BOULE.get()
                && inputItem == com.euvcraft.lithmod.mc.registry.ModItems.SILICON_BOULE.get()
                && duration <= SupportRecipe.MAX_DURATION_TICKS
                && energyPerTick <= SupportRecipe.MAX_ENERGY_PER_TICK
                && waterMb <= WaterTank.MAX_CAPACITY
                && waterPinned >= waterMb && waterPinned <= WaterTank.MAX_CAPACITY
                && inputCount <= new ItemStack(inputItem).getMaxStackSize()
                && outputCount <= new ItemStack(outputItem).getMaxStackSize();
        }
        if (!valid) {
            EUVCRAFTMod.LOGGER.warn(
                "EUVCRAFT dropped a malformed saved purifier job; the machine stays idle");
            return;
        }
        this.job = new JobSnapshot(duration, energyPerTick, waterMb, outputItem, outputCount,
            inputItem, inputCount, waterPinned);
        this.jobRunning = true;
        this.jobProgress = progress;
    }

    private void savePurifierJob(ValueOutput output) {
        if (!this.jobRunning || this.job == null) {
            return;
        }
        ValueOutput state = output.child("purifierJob");
        state.putBoolean("running", true);
        state.putInt("progress", this.jobProgress);
        JobSnapshot pinned = this.job;
        state.putInt("duration", pinned.duration());
        state.putInt("energy_per_tick", pinned.energyPerTick());
        state.putInt("water_mb", pinned.waterMb());
        state.putString("output_item", BuiltInRegistries.ITEM.getKey(pinned.outputItem()).toString());
        state.putInt("output_count", pinned.outputCount());
        state.putString("input_item", BuiltInRegistries.ITEM.getKey(pinned.inputItem()).toString());
        state.putInt("input_count", pinned.inputCount());
        state.putInt("water_pinned", pinned.waterPinned());
    }

    private void restoreEbeamJob(ValueInput state) {
        if (!state.getBooleanOr("running", false)) {
            return;
        }
        Integer duration = state.getInt("duration").orElse(null);
        Integer energyPerTick = state.getInt("energy_per_tick").orElse(null);
        Integer blankCount = state.getInt("blank_count").orElse(null);
        Integer redstoneCount = state.getInt("redstone_count").orElse(null);
        Integer outputCount = state.getInt("output_count").orElse(null);
        Integer progress = state.getInt("progress").orElse(null);
        String dieType = state.getString("die_type").orElse(null);
        Integer tierIndex = state.getInt("tier_index").orElse(null);
        Integer uses = state.getInt("uses").orElse(null);
        Item blankItem = itemFrom(state.getString("blank_item").orElse(null));
        Item redstoneItem = itemFrom(state.getString("redstone_item").orElse(null));
        Item outputItem = itemFrom(state.getString("output_item").orElse(null));

        boolean valid = duration != null && energyPerTick != null && blankCount != null
            && redstoneCount != null && outputCount != null && progress != null
            && dieType != null && tierIndex != null && uses != null && uses > 0 && blankItem != null && redstoneItem != null
            && outputItem != null
            && duration > 0 && energyPerTick > 0 && blankCount > 0 && redstoneCount > 0
            && outputCount > 0 && progress >= 0 && progress < duration;
        if (valid) {
            valid = kind() == SupportMachineKind.EBEAM
                && designIndex(dieType, tierIndex) >= 0
                && blankItem == com.euvcraft.lithmod.mc.registry.ModItems.BLANK_MASK.get()
                && redstoneItem == net.minecraft.world.item.Items.REDSTONE
                && outputItem == com.euvcraft.lithmod.mc.registry.ModItems.MASK.get()
                && duration <= SupportRecipe.MAX_DURATION_TICKS
                && energyPerTick <= SupportRecipe.MAX_ENERGY_PER_TICK
                && blankCount <= new ItemStack(blankItem).getMaxStackSize()
                && redstoneCount <= new ItemStack(redstoneItem).getMaxStackSize()
                && outputCount <= new ItemStack(outputItem).getMaxStackSize();
        }
        if (!valid) {
            EUVCRAFTMod.LOGGER.warn(
                "EUVCRAFT dropped a malformed saved e-beam job; the machine stays idle");
            return;
        }
        this.ebeamJob = new EbeamJobSnapshot(duration, energyPerTick, blankCount, redstoneCount,
            blankItem, redstoneItem, outputItem, outputCount, dieType, tierIndex, uses);
        this.ebeamJobRunning = true;
        this.ebeamJobProgress = progress;
        this.selectedDesign = designIndex(dieType, tierIndex); // active snapshot owns the display
    }

    /** Writes the pinned e-beam job, if one is running, into its own save child. */
    private void saveEbeamJob(ValueOutput output) {
        if (!this.ebeamJobRunning || this.ebeamJob == null) {
            return;
        }
        ValueOutput state = output.child("ebeamJob");
        state.putBoolean("running", true);
        state.putInt("progress", this.ebeamJobProgress);
        EbeamJobSnapshot pinned = this.ebeamJob;
        state.putInt("duration", pinned.duration());
        state.putInt("energy_per_tick", pinned.energyPerTick());
        state.putInt("blank_count", pinned.blankCount());
        state.putInt("redstone_count", pinned.redstoneCount());
        state.putInt("output_count", pinned.outputCount());
        state.putString("die_type", pinned.dieType());
        state.putInt("tier_index", pinned.tierIndex());
        state.putInt("uses", pinned.uses());
        state.putString("blank_item", BuiltInRegistries.ITEM.getKey(pinned.blankItem()).toString());
        state.putString("redstone_item", BuiltInRegistries.ITEM.getKey(pinned.redstoneItem()).toString());
        state.putString("output_item", BuiltInRegistries.ITEM.getKey(pinned.outputItem()).toString());
    }

    /** A registered item from its saved id, or null when the id is absent or unknown. */
    private static @Nullable Item itemFrom(@Nullable String savedId) {
        if (savedId == null || savedId.isBlank()) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(savedId);
        if (parsed == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        return item == null || item == Items.AIR ? null : item;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
        if (this.water != null) {
            output.putInt("supportWaterAmount", this.water.amount());
        }
        savePurifierJob(output);
        output.putInt("selectedDesign", this.selectedDesign);
        saveEbeamJob(output);
        // Capacity is definition data, not saved.
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                      FabMachineBlockEntity machine) {
        return SupportMachineMenu.forMachine(containerId, playerInventory, this);
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

