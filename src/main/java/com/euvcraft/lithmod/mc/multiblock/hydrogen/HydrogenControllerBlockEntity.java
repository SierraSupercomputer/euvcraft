package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The hydrogen purifier: consumes raw H2 + filter medium + FE to produce fab_hydrogen gas fluid.
 *
 * <p>FLUID-ONLY purification using definition params, NOT MachineRecipe/item-output job.
 * One admitted job at a time, frozen from admission to completion.
 * Admission reserves the exact raw H2 batch + filter cost only when the machine is formed,
 * the data is valid, the frozen output has room and the buffer holds the first tick's FE.
 * From then on the job never re-matches: it pauses without spending anything while unformed,
 * out of FE or blocked on output, and on completion it writes the frozen product exactly once
 * and consumes the frozen input.</p>
 *
 * <p>Breaking the machine returns only the admitted original input; the frozen unearned
 * output never leaves with it. A save whose pending job is malformed recovers the original
 * input instead of deleting or duplicating it. The shutdown path calls
 * {@code formation.unload()} before {@code super.setRemoved()} with no world queries.</p>
 */
public final class HydrogenControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner {

    private final AdvancedFormation formation = new AdvancedFormation(this);

    /** Raw hydrogen tank (arcforge:hydrogen input). */
    public final WaterTank rawHydrogen = new WaterTank(16000);

    /** Purified hydrogen tank (lithmod:fab_hydrogen output). */
    public final WaterTank pureHydrogen = new WaterTank(16000);

    /** The one journal every view of the raw hydrogen tank goes through. */
    final TankJournal rawJournal = new TankJournal(this, rawHydrogen);

    /** The one journal every view of the pure hydrogen tank goes through. */
    final TankJournal pureJournal = new TankJournal(this, pureHydrogen);

    /** The admitted original filter stack, held until completion, break or recovery. */
    private ItemStack pendingFilter = ItemStack.EMPTY;

    /** The batch's reserved raw H2, frozen at admission (mB). */
    private int rawCost;

    /** The batch's reserved filters, frozen at admission. */
    private int filterCost;

    /** The admitted job's tick length, frozen at admission. */
    private int durationTicks;

    /** The admitted job's per-tick FE, frozen at admission. */
    private int fePerTick;

    private int progress;
    /** True while a malformed pending is waiting to hand the original input back. */
    private boolean recovery;

    public HydrogenControllerBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenMachines.CONTROLLER_BE.get(), pos, state, 1, 0);
    }

    @Override protected String machineId() {
        return "hydrogen_purifier";
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.lithmod.hydrogen_purifier");
    }

    @Override public FabMachineBlockEntity machine() {
        return this;
    }

    @Override public AdvancedFormation formation() {
        return formation;
    }

    @Override public boolean large() {
        return false; // 2x2x3 purifier
    }

    @Override public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            java.util.function.Function<MultiblockPos, BlockKind> lookup) {
        return T7UtilityStructureMatcher.matchPurifier(anchor, facing, lookup);
    }

    // ------------------------------------------------------------------
    // Public read access for tests and screens
    // ------------------------------------------------------------------

    /** Raw hydrogen currently stored, in mB. */
    public int rawHydrogenAmount() {
        return rawHydrogen.amount();
    }

    /** Raw hydrogen capacity, in mB. */
    public int rawHydrogenCapacity() {
        return rawHydrogen.capacity();
    }

    /** Pure hydrogen currently stored, in mB. */
    public int pureHydrogenAmount() {
        return pureHydrogen.amount();
    }

    /** Pure hydrogen capacity, in mB. */
    public int pureHydrogenCapacity() {
        return pureHydrogen.capacity();
    }

    /** Ticks completed for the current or recovered job. */
    public int progress() {
        return progress;
    }

    /** Ticks the admitted job needs in total; zero when no job is admitted. */
    public int duration() {
        return durationTicks;
    }

    /** True while an input is reserved — a run in progress or a pending recovery. */
    public boolean hasJob() {
        return !pendingFilter.isEmpty();
    }

    // ------------------------------------------------------------------
    // Menu-facing batch snapshot: the admitted job wins while one runs, settings otherwise.
    // All are 0 without a definition (fail-closed, matching serverTick which cannot admit).
    // ------------------------------------------------------------------

    /** Raw mB one batch draws, from settings; 0 with no definition. */
    public int batchRawMb() {
        return definition().map(d -> d.paramOrDefault("raw_per_batch_mb", 8000)).orElse(0);
    }

    /** mB the batch produces: the admitted amount while running, else the configured amount. */
    public int batchOutputMb() {
        if (hasJob()) return rawCost; // 1:1 for hydrogen purification
        return definition().map(d -> d.paramOrDefault("pure_per_batch_mb", 8000)).orElse(0);
    }

    /** Filters charged per batch, from settings; 0 with no definition. */
    public int batchFilters() {
        return definition().map(d -> d.paramOrDefault("filters_per_batch", 1)).orElse(0);
    }

    /** FE per tick: the admitted cost while running, else the configured cost; 0 with no definition. */
    public int batchFePerTick() {
        return hasJob() ? fePerTick : definition().map(d -> d.energyPerTick(null, com.euvcraft.lithmod.mc.EUVCRAFTMod.settings())).orElse(0);
    }

    /** Ticks of the current or next batch, admitted duration or settings. */
    public int batchTicks() {
        return hasJob() ? durationTicks : definition().map(d -> d.paramOrDefault("duration", 200)).orElse(0);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        revalidate();
        refreshDefinition();
        rawHydrogen.resize(Math.clamp(param("raw_capacity_mb", 16000), 1000, WaterTank.MAX_CAPACITY));
        pureHydrogen.resize(Math.clamp(param("pure_capacity_mb", 16000), 1000, WaterTank.MAX_CAPACITY));
        if (!isFormed() || definition().isEmpty()) {
            // Unformed or dataless: the admitted job simply waits, frozen.
            sync();
            return;
        }
        if (recovery) {
            // A malformed pending must recover the original input, not delete or duplicate it.
            if (slot(0).isEmpty()) {
                setSlot(0, pendingFilter);
                pendingFilter = ItemStack.EMPTY;
                recovery = false;
                setChanged();
            }
            sync();
            return;
        }
        if (pendingFilter.isEmpty()) {
            tryAdmit();
        } else {
            advanceJob();
        }
        sync();
    }

    private int param(String key, int fallback) {
        return definition().map(d -> d.paramOrDefault(key, fallback)).orElse(fallback);
    }

    /**
     * Admission: reserves the exact raw H2 batch + filter cost, and freezes the product,
     * length and cost from definition params. Anything invalid refuses before anything is taken.
     */
    private void tryAdmit() {
        MachineDefinition definition = definition().orElseThrow();
        int rawPerBatch = definition.paramOrDefault("raw_per_batch_mb", 8000);
        int purePerBatch = definition.paramOrDefault("pure_per_batch_mb", 8000);
        int filtersPerBatch = definition.paramOrDefault("filters_per_batch", 1);
        int ticks = definition.paramOrDefault("duration", 200);
        int fe = definition.energyPerTick(null, com.euvcraft.lithmod.mc.EUVCRAFTMod.settings());

        // Validate definition params
        if (rawPerBatch < 1 || rawPerBatch > rawHydrogen.capacity() || rawHydrogen.amount() < rawPerBatch) {
            return;
        }
        if (purePerBatch < 1 || purePerBatch > pureHydrogen.capacity() || pureHydrogen.capacity() - pureHydrogen.amount() < purePerBatch) {
            return;
        }
        if (filtersPerBatch < 1 || filtersPerBatch > 64) {
            return;
        }
        if (ticks < 1 || ticks > 72000) {
            return;
        }
        if (fe < 0 || fe > buffer().getCapacityAsInt()) {
            return;
        }

        // Check filter input slot
        ItemStack filterStack = slot(0);
        if (filterStack.isEmpty() || !filterStack.is(ModItems.UTILITY_FILTER.get())) {
            return;
        }
        if (filterStack.getCount() < filtersPerBatch) {
            return;
        }
        // Check FE buffer for first tick
        if (buffer().getAmountAsLong() < fe) {
            return;
        }

        // Valid admission: reserve input count + exact raw H2 + filters, then freeze the job.
        ItemStack admitted = filterStack.copy();
        admitted.setCount(filtersPerBatch);
        pendingFilter = admitted;
        filterStack.shrink(filtersPerBatch);
        if (filterStack.isEmpty()) {
            clearSlot(0);
        }
        rawHydrogen.drainExact(rawPerBatch, false);
        rawCost = rawPerBatch;
        filterCost = filtersPerBatch;
        durationTicks = ticks;
        fePerTick = fe;
        progress = 0;
        setChanged();
    }

    /**
     * One tick of the admitted job. No rerolls: the frozen numbers are the whole truth.
     * Pauses (without spending FE) while the output is blocked or the buffer is short.
     */
    private void advanceJob() {
        // Check output tank has room
        if (pureHydrogen.capacity() - pureHydrogen.amount() < rawCost) {
            return;
        }
        if (energySource().draw(fePerTick) != fePerTick) {
            return;
        }
        progress++;
        if (progress >= durationTicks) {
            // Completion: produce exact frozen pureH2 once, consume the pending input.
            pureHydrogen.fillExact(rawCost, false);
            pendingFilter = ItemStack.EMPTY;
            rawCost = 0;
            filterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
        }
        setChanged();
    }

    private void sync() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, progress);
        data().set(DATA_TICKS, pendingFilter.isEmpty() ? 0 : durationTicks);
    }

    // ------------------------------------------------------------------
    // Removal and drops
    // ------------------------------------------------------------------

    @Override public void setRemoved() {
        // Removal may run after this chunk left the loaded map: unload() does no world queries.
        formation.unload();
        super.setRemoved();
    }

    /**
     * Breaking returns only the admitted original input. The frozen unearned output is never
     * dropped, and the slots' own contents (normally empty mid-job) drop through the base
     * Container path.
     */
    @Override protected void dropExtraContents(@Nullable net.minecraft.world.level.Level level, BlockPos pos) {
        if (!pendingFilter.isEmpty()) {
            var returned = pendingFilter;
            pendingFilter = ItemStack.EMPTY;
            rawCost = 0;
            filterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
            recovery = false;
            dropContainer(level, pos, new net.minecraft.world.SimpleContainer(returned));
        }
    }

    // ------------------------------------------------------------------
    // Save and load: the frozen job is the save's contract
    // ------------------------------------------------------------------

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("hydrogenRawMb", rawHydrogen.amount());
        output.putInt("hydrogenPureMb", pureHydrogen.amount());
        if (!pendingFilter.isEmpty()) {
            output.store("hydrogenPendingFilter", ItemStack.OPTIONAL_CODEC, pendingFilter);
            output.putInt("hydrogenRawCost", rawCost);
            output.putInt("hydrogenFilterCost", filterCost);
            output.putInt("hydrogenDuration", durationTicks);
            output.putInt("hydrogenFePerTick", fePerTick);
            output.putInt("hydrogenProgress", progress);
            output.putBoolean("hydrogenRecovery", recovery);
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rawHydrogen.restoreAmount(Math.clamp(input.getIntOr("hydrogenRawMb", 0), 0, WaterTank.MAX_CAPACITY));
        pureHydrogen.restoreAmount(Math.clamp(input.getIntOr("hydrogenPureMb", 0), 0, WaterTank.MAX_CAPACITY));
        ItemStack savedFilter = input.read("hydrogenPendingFilter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        int savedRaw = input.getIntOr("hydrogenRawCost", 0);
        int savedFilters = input.getIntOr("hydrogenFilterCost", 0);
        int savedDuration = input.getIntOr("hydrogenDuration", 0);
        int savedFe = input.getIntOr("hydrogenFePerTick", -1);
        int savedProgress = input.getIntOr("hydrogenProgress", 0);
        boolean savedRecovery = input.getBooleanOr("hydrogenRecovery", false);
        if (savedFilter.isEmpty()) {
            // No job may exist without its reserved input; a frozen result is unearned and is
            // dropped rather than paid out or carried into a new job.
            pendingFilter = ItemStack.EMPTY;
            rawCost = 0;
            filterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
            recovery = false;
        } else {
            boolean malformed = savedRaw < 1 || savedRaw > WaterTank.MAX_CAPACITY
                    || savedFilters < 1 || savedFilters > 64
                    || savedDuration < 1 || savedDuration > 72000
                    || savedFe < 0
                    || savedProgress < 0 || savedProgress >= savedDuration;
            pendingFilter = savedFilter;
            recovery = malformed || savedRecovery;
            if (malformed) {
                // The original input is kept for recovery; the frozen numbers are discarded.
                rawCost = 0;
                filterCost = 0;
                durationTicks = 0;
                fePerTick = 0;
                progress = 0;
            } else {
                rawCost = savedRaw;
                filterCost = savedFilters;
                durationTicks = savedDuration;
                fePerTick = savedFe;
                progress = savedProgress;
            }
        }
        sync();
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    @Override protected AbstractContainerMenu createMachineMenu(int id, Inventory inventory, FabMachineBlockEntity machine) {
        return new HydrogenMenu(id, inventory, this);
    }

    /** One journal shared by every view of a hydrogen tank, including the port lenses. */
    static final class TankJournal extends SnapshotJournal<Integer> {
        private final HydrogenControllerBlockEntity owner;
        private final WaterTank tank;

        TankJournal(HydrogenControllerBlockEntity owner, WaterTank tank) {
            this.owner = owner;
            this.tank = tank;
        }

        @Override protected Integer createSnapshot() {
            return tank.amount();
        }

        @Override protected void revertToSnapshot(Integer amount) {
            tank.restoreAmount(amount);
        }

        @Override protected void onRootCommit(Integer previous) {
            owner.setChanged();
        }
    }
}