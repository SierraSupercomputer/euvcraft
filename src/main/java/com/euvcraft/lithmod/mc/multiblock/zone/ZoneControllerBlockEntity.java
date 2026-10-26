package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.ZoneStructureMatcher;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedFormation;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;

import org.jspecify.annotations.Nullable;

/**
 * The zone refiner: one admitted job at a time, frozen from admission to completion.
 *
 * <p>The machine takes its first matching resolved recipe for {@code machine=zone_refiner}
 * through {@code FabMachineBlockEntity.recipes()} (the loader registry) and
 * {@link MachineRecipeRunner#matchesSlot}; the recipe's ingredient count, assembled product,
 * tick length and per-tick energy (falling back to the machine definition) are authoritative
 * and nothing about them is hard-coded. Admission reserves the input count and the exact
 * ultrapure-water batch only when the machine is formed, the data is valid, the frozen output
 * has room and the buffer holds the first tick's FE. From then on the job never re-matches:
 * it pauses without spending anything while unformed, out of FE or blocked on output, and on
 * completion it writes the frozen product exactly once and consumes the frozen input.</p>
 *
 * <p>Breaking the machine returns only the admitted original input; the frozen unearned
 * output never leaves with it. A save whose pending job is malformed recovers the original
 * input instead of deleting or duplicating it. The base recipe runner is deliberately never
 * ticked — this machine owns its job logic — and the shutdown path calls
 * {@code formation.unload()} before {@code super.setRemoved()} with no world queries.</p>
 */
public final class ZoneControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner {

    private final AdvancedFormation formation = new AdvancedFormation(this);

    /** Ultrapure water store; filled only through the inlet's UPW lens. Package-private for the lens. */
    final WaterTank upw = new WaterTank(16000);
    /** The one journal every view of the UPW tank goes through. */
    final ZoneTankJournal waterJournal = new ZoneTankJournal(this);

    /** The admitted original input, held until completion, break or recovery. */
    private ItemStack pendingInput = ItemStack.EMPTY;
    /** The frozen product of the admitted recipe; written once, on completion. */
    private ItemStack pendingResult = ItemStack.EMPTY;
    /** The batch's reserved UPW, frozen at admission. */
    private int waterCost;
    /** The admitted recipe's tick length, frozen at admission. */
    private int durationTicks;
    /** The admitted recipe's per-tick FE, frozen at admission. */
    private int fePerTick;
    private int progress;
    /** True while a malformed pending is waiting to hand the original input back. */
    private boolean recovery;

    public ZoneControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ZoneMachines.CONTROLLER_BE.get(), pos, state, 1, 1);
    }

    @Override protected String machineId() {
        return "zone_refiner";
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.lithmod.zone_refiner");
    }

    @Override public FabMachineBlockEntity machine() {
        return this;
    }

    @Override public AdvancedFormation formation() {
        return formation;
    }

    @Override public boolean large() {
        return true;
    }

    @Override public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            java.util.function.Function<MultiblockPos, BlockKind> lookup) {
        return ZoneStructureMatcher.match(anchor, facing, lookup);
    }

    // ------------------------------------------------------------------
    // Public read access for tests and screens
    // ------------------------------------------------------------------

    /** Ultrapure water currently stored, in mB. */
    public int waterAmount() {
        return upw.amount();
    }

    /** Ultrapure water capacity, in mB. */
    public int waterCapacity() {
        return upw.capacity();
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
        return !pendingInput.isEmpty();
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
        upw.resize(Math.clamp(definition().map(d -> d.paramOrDefault("upw_capacity_mb", 16000)).orElse(16000), 1000, WaterTank.MAX_CAPACITY));
        if (!isFormed() || definition().isEmpty()) {
            // Unformed or dataless: the admitted job simply waits, frozen.
            sync();
            return;
        }
        if (recovery) {
            // A malformed pending must recover the original input, not delete or duplicate it.
            if (slot(0).isEmpty()) {
                setSlot(0, pendingInput);
                pendingInput = ItemStack.EMPTY;
                recovery = false;
                setChanged();
            }
            sync();
            return;
        }
        if (pendingInput.isEmpty()) {
            tryAdmit();
        } else {
            advanceJob();
        }
        sync();
    }

    /**
     * Admission: the first matching one-input recipe reserves its input count and the exact
     * UPW batch, and freezes the product, length and cost. Anything invalid refuses before
     * anything is taken.
     */
    private void tryAdmit() {
        MachineDefinition definition = definition().orElseThrow();
        int upwPerBatch = definition.paramOrDefault("water_per_batch_mb", 4000);
        if (upwPerBatch < 1 || upwPerBatch > upw.capacity() || upw.amount() < upwPerBatch) {
            return;
        }
        MachineRecipe recipe = firstMatchingRecipe();
        if (recipe == null) {
            return;
        }
        int inputCount = recipe.ingredientCounts().get(0);
        if (inputCount < 1 || inputCount > 64) {
            return;
        }
        int ticks = recipe.ticks();
        if (ticks < 1 || ticks > 72000) {
            return;
        }
        int fe = recipe.energyPerTick(definition.energyPerTick(null, EUVCRAFTMod.settings()));
        if (fe < 0 || fe > buffer().getCapacityAsInt()) {
            return;
        }
        ItemStack result = recipe.assemble();
        if (!hasRoomFor(result, outputSlot())) {
            return;
        }
        if (buffer().getAmountAsLong() < fe) {
            return;
        }
        // Valid admission: reserve input count + exact UPW, then freeze the job.
        ItemStack admitted = slot(0).copy();
        admitted.setCount(inputCount);
        pendingInput = admitted;
        slot(0).shrink(inputCount);
        if (slot(0).isEmpty()) {
            clearSlot(0);
        }
        upw.drainExact(upwPerBatch, false);
        pendingResult = result;
        waterCost = upwPerBatch;
        durationTicks = ticks;
        fePerTick = fe;
        progress = 0;
        setChanged();
    }

    /** The first resolved recipe for this machine whose single input the slot satisfies. */
    private @Nullable MachineRecipe firstMatchingRecipe() {
        for (MachineRecipe recipe : recipes()) {
            if (recipe.inputSlotCount() != 1) {
                continue;
            }
            if (MachineRecipeRunner.matchesSlot(recipe, 0, slot(0))) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * One tick of the admitted job. No rerolls: the frozen numbers are the whole truth.
     * Pauses (without spending FE) while the output is blocked or the buffer is short.
     */
    private void advanceJob() {
        if (!hasRoomFor(pendingResult, outputSlot())) {
            return;
        }
        if (energySource().draw(fePerTick) != fePerTick) {
            return;
        }
        progress++;
        if (progress >= durationTicks) {
            // Completion: output exactly once, consume the pending input.
            giveOutput(pendingResult);
            pendingResult = ItemStack.EMPTY;
            pendingInput = ItemStack.EMPTY;
            waterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
        }
        setChanged();
    }

    /** Whether {@code output} can take the frozen {@code result} right now. */
    private static boolean hasRoomFor(ItemStack result, ItemStack output) {
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void sync() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, progress);
        data().set(DATA_TICKS, pendingInput.isEmpty() ? 0 : durationTicks);
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
    @Override protected void dropExtraContents(@Nullable Level level, BlockPos pos) {
        if (!pendingInput.isEmpty()) {
            var returned = pendingInput;
            pendingInput = ItemStack.EMPTY;
            pendingResult = ItemStack.EMPTY;
            waterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
            recovery = false;
            dropContainer(level, pos, new SimpleContainer(returned));
        }
    }

    // ------------------------------------------------------------------
    // Save and load: the frozen job is the save's contract
    // ------------------------------------------------------------------

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("zoneUpwMb", upw.amount());
        if (!pendingInput.isEmpty()) {
            output.store("zonePendingInput", ItemStack.OPTIONAL_CODEC, pendingInput);
            output.store("zonePendingResult", ItemStack.OPTIONAL_CODEC, pendingResult);
            output.putInt("zoneWaterCost", waterCost);
            output.putInt("zoneDuration", durationTicks);
            output.putInt("zoneFePerTick", fePerTick);
            output.putInt("zoneProgress", progress);
            output.putBoolean("zoneRecovery", recovery);
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upw.restoreAmount(Math.clamp(input.getIntOr("zoneUpwMb", 0), 0, WaterTank.MAX_CAPACITY));
        ItemStack savedInput = input.read("zonePendingInput", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        ItemStack savedResult = input.read("zonePendingResult", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        int savedWater = input.getIntOr("zoneWaterCost", 0);
        int savedDuration = input.getIntOr("zoneDuration", 0);
        int savedFe = input.getIntOr("zoneFePerTick", -1);
        int savedProgress = input.getIntOr("zoneProgress", 0);
        boolean savedRecovery = input.getBooleanOr("zoneRecovery", false);
        if (savedInput.isEmpty()) {
            // No job may exist without its reserved input; a frozen result is unearned and is
            // dropped rather than paid out or carried into a new job.
            pendingInput = ItemStack.EMPTY;
            pendingResult = ItemStack.EMPTY;
            waterCost = 0;
            durationTicks = 0;
            fePerTick = 0;
            progress = 0;
            recovery = false;
        } else {
            boolean malformed = savedWater < 1 || savedWater > WaterTank.MAX_CAPACITY
                || savedDuration < 1 || savedDuration > 72000
                || savedFe < 0
                || savedProgress < 0 || savedProgress >= savedDuration;
            pendingInput = savedInput;
            recovery = malformed || savedRecovery;
            if (malformed) {
                // The original input is kept for recovery; the frozen numbers are discarded.
                pendingResult = ItemStack.EMPTY;
                waterCost = 0;
                durationTicks = 0;
                fePerTick = 0;
                progress = 0;
            } else {
                pendingResult = savedResult;
                waterCost = savedWater;
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
        return new ZoneStatusMenu(id, inventory, this);
    }

    /** One journal shared by every view of the UPW tank, including the inlet lens. */
    static final class ZoneTankJournal extends SnapshotJournal<Integer> {
        private final ZoneControllerBlockEntity owner;

        ZoneTankJournal(ZoneControllerBlockEntity owner) {
            this.owner = owner;
        }

        @Override protected Integer createSnapshot() {
            return owner.upw.amount();
        }

        @Override protected void revertToSnapshot(Integer amount) {
            owner.upw.restoreAmount(amount);
        }

        @Override protected void onRootCommit(Integer previous) {
            owner.setChanged();
        }
    }
}
