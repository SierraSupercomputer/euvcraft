package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSlicing;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Wafer Slicer: one boule plus one empty FOUP in, one FOUP of unprocessed wafers out.
 *
 * <p>This machine does not use {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner}.
 * A runner product is an {@code ItemStack} it assembles from a JSON recipe, and the
 * product here is a FOUP whose sealed lot has a fresh id, the capacity of the FOUP that
 * went in and {@link Wafer#unprocessed()} wafers in it — none of which a recipe can
 * express. So the slicer owns its own progress, with the same pause rules as the runner:
 * nothing is consumed until the product is written, a full output slot pauses instead of
 * eating the boule, and a partial energy draw counts as no energy.</p>
 *
 * <p>Every number comes from data or config. The length of a run is the {@code slice_ticks}
 * param of {@code data/lithmod/machines/wafer_slicer.json}; the energy is the definition's
 * {@code energy_per_tick} through the configured FE multiplier; the wafer count is
 * {@link WaferSlicing#wafersFor} over the configured {@code wafersPerBoule} and the stored
 * FOUP capacity.</p>
 *
 * <p>Lots produced here carry no substrate generation and no run spec: the lot has not been
 * through a process step, and M1 has nowhere to record where a boule was grown.</p>
 */
public class WaferSlicerBlockEntity extends FabMachineBlockEntity {

    /** The machine id, matching {@code data/lithmod/machines/wafer_slicer.json}. */
    public static final String MACHINE_ID = "wafer_slicer";

    /** Slot holding the boule to slice. */
    public static final int BOULE_SLOT = 0;
    /** Input slot holding the empty FOUP the wafers will be sliced into. */
    public static final int FOUP_SLOT = 1;
    /** Extract-only output slot holding the FOUP that came out. */
    public static final int OUTPUT_SLOT = 2;

    /** Name of the definition param that gives the length of one slice. */
    public static final String SLICE_TICKS_PARAM = "slice_ticks";

    /**
     * Slice length used before {@code wafer_slicer.json} has loaded.
     *
     * <p>The same role as {@code FALLBACK_CAPACITY} in the framework: a block entity can be
     * constructed before any data exists, so the machine needs a value that is only ever
     * used in that window.</p>
     */
    public static final int FALLBACK_SLICE_TICKS = 200;

    /** Buffer capacity; see the note on the Crystal Puller's fallback constants. */
    public static final int FALLBACK_CAPACITY = 20_000;
    /** Fallback per-operation insert limit, equal to {@code max_receive} in the data file. */
    public static final int FALLBACK_MAX_RECEIVE = 2_000;

    private static final String STATE_KEY = "slicer";

    private boolean slicing;
    private boolean paused;
    private int progress;
    private int ticks;

    public WaferSlicerBlockEntity(BlockPos pos, BlockState state) {
        super(SiliconMachines.WAFER_SLICER_BE.get(), pos, state, 2, 1,
            FALLBACK_CAPACITY, FALLBACK_MAX_RECEIVE);
    }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    // ------------------------------------------------------------------
    // Input validation, shared by the slot and the tick
    // ------------------------------------------------------------------

    /**
     * Reads a stack as a lot the slicer is willing to fill.
     *
     * <p>The only acceptable input is a FOUP that carries a lot, and that lot holds no
     * wafers and no run spec: wafers mean the lot is already sliced and a run spec means it
     * has been processed. The read goes through
     * {@link FoupItems#trustedContentsOf}, which refuses a client view, so a stack a client
     * invented reads as an empty lot rather than as wafers.</p>
     *
     * <p>A FOUP stack with no lot component at all is refused rather than treated as empty,
     * because there is no stored capacity to fill it to.</p>
     *
     * @param stack the stack to check, not null
     * @return the trusted lot, or {@code null} when this slot must refuse the stack
     */
    public static @Nullable FoupContents acceptedFoup(ItemStack stack) {
        if (!FoupItems.isFoup(stack)) {
            return null;
        }
        FoupContents contents = FoupItems.trustedContentsOf(stack);
        return contents.isEmpty() && contents.run() == null ? contents : null;
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    /**
     * One server tick.
     *
     * <p>Replaces the base implementation because this machine's product is a sealed lot
     * rather than a recipe result; the base implementation would run
     * {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} over recipes that do not
     * exist for this machine id. Progress is still published through the same
     * {@code DATA_PROGRESS} and {@code DATA_TICKS} values the menu reads, so the screen
     * needs to know nothing about this class.</p>
     */
    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        int sliceTicks = sliceTicks();
        int perTick = energyPerTick();

        if (!slicing) {
            if (!readyToSlice()) {
                paused = false;
                syncSlicerData();
                return;
            }
            slicing = true;
            progress = 0;
            ticks = sliceTicks;
        }

        advance(perTick);
        syncSlicerData();
        if (slicing || progress > 0) {
            setChanged();
        }
    }

    /** Whether both inputs are present and the output has room. */
    private boolean readyToSlice() {
        return slot(BOULE_SLOT).is(bouleItem())
            && acceptedFoup(slot(FOUP_SLOT)) != null
            && outputSlot().isEmpty();
    }

    /** One tick of a run in progress; mirrors the runner's pause rules. */
    private void advance(int perTick) {
        if (!outputSlot().isEmpty()) {
            paused = true;
            return;
        }
        if (energySource().draw(perTick) < perTick) {
            paused = true;
            return;
        }

        paused = false;
        progress++;
        if (progress < ticks) {
            return;
        }
        complete();
    }

    /**
     * Writes the product and only then consumes the inputs.
     *
     * <p>The order matters: if the product could not be written, the boule and the FOUP
     * would be gone and nothing would come out. The room check already ran this tick, and
     * the output slot is extract-only, so the write cannot fail here.</p>
     */
    private void complete() {
        ItemStack foupIn = slot(FOUP_SLOT);
        FoupContents input = acceptedFoup(foupIn);
        if (input == null || !slot(BOULE_SLOT).is(bouleItem())) {
            // An input was taken out or swapped mid-run; start over rather than slicing
            // into a lot with nothing behind it. Nothing was consumed.
            resetSlice();
            return;
        }

        int waferCount = WaferSlicing.wafersFor(EUVCRAFTMod.settings(), input.capacity());
        List<Wafer> wafers = new ArrayList<>(waferCount);
        for (int wafer = 0; wafer < waferCount; wafer++) {
            wafers.add(Wafer.unprocessed());
        }
        FoupContents produced = new FoupContents(UUID.randomUUID(), input.capacity(), wafers);

        giveOutput(FoupItems.with(foupIn.getItem(), produced));

        clearSlot(FOUP_SLOT);
        ItemStack boule = slot(BOULE_SLOT);
        boule.shrink(1);
        if (boule.isEmpty()) {
            clearSlot(BOULE_SLOT);
        }
        setChanged();
        resetSlice();
    }

    /**
     * Drops the run in progress and its progress.
     *
     * <p>Called when a run finishes, when the FOUP it was filling is taken away mid-run,
     * and by tests that need a machine in a known state. The inputs are left alone: nothing
     * is consumed until a product is written.</p>
     */
    public void resetSlice() {
        this.slicing = false;
        this.paused = false;
        this.progress = 0;
        this.ticks = 0;
    }

    // ------------------------------------------------------------------
    // Numbers, all of them from data
    // ------------------------------------------------------------------

    /** The slice length from the definition, or the fallback before it has loaded. */
    public int sliceTicks() {
        MachineDefinition definition = definition().orElse(null);
        return definition == null
            ? FALLBACK_SLICE_TICKS
            : definition.paramOrDefault(SLICE_TICKS_PARAM, FALLBACK_SLICE_TICKS);
    }

    /**
     * The FE this machine draws per active tick.
     *
     * <p>Zero when no definition is loaded, which is what the framework's runner does in
     * the same window: a block entity can exist for a moment before the data pack is read,
     * and running free in that moment is the lesser problem against a machine that can
     * never run at all.</p>
     */
    public int energyPerTick() {
        return definition()
            .map(definition -> definition.energyPerTick(null, EUVCRAFTMod.settings()))
            .orElse(0);
    }

    /**
     * Publishes this machine's values to the menu.
     *
     * <p>The capacity reported is the buffer's own capacity rather than the definition's,
     * because the buffer is built once with the fallback capacity and never resized, so the
     * buffer is what decides what the player can actually fill.</p>
     */
    private void syncSlicerData() {
        this.data().set(DATA_ENERGY, (int) this.buffer().getAmountAsLong());
        this.data().set(DATA_CAPACITY, this.buffer().getCapacityAsInt());
        this.data().set(DATA_PROGRESS, this.progress);
        this.data().set(DATA_TICKS, this.ticks);
    }

    // ------------------------------------------------------------------
    // State, for tests and for the menu
    // ------------------------------------------------------------------

    /** Whether a boule is being sliced right now. */
    public boolean isSlicing() {
        return this.slicing;
    }

    /** Started but stopped for want of energy or for a full output slot. */
    public boolean isPaused() {
        return this.paused;
    }

    /** Ticks completed on the run in progress. */
    public int sliceProgress() {
        return this.progress;
    }

    /** Ticks the run in progress needs; zero when idle. */
    public int sliceTicksRemaining() {
        return this.ticks;
    }

    /** The boule this machine accepts in {@link #BOULE_SLOT}. */
    public static Item bouleItem() {
        return ModItems.SILICON_BOULE.get();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput state = output.child(STATE_KEY);
        state.putBoolean("slicing", this.slicing);
        state.putInt("progress", this.progress);
        state.putInt("ticks", this.ticks);
    }

    /**
     * Restores a run in progress.
     *
     * <p>Only a flag with progress below its tick count is trusted, the same rule the
     * runner uses: anything else can only have come from a hand-edited tag, and restarting
     * from tick one is the safe answer.</p>
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput state = input.childOrEmpty(STATE_KEY);
        boolean savedSlicing = state.getBooleanOr("slicing", false);
        int savedProgress = state.getIntOr("progress", 0);
        int savedTicks = state.getIntOr("ticks", 0);

        if (savedSlicing && savedTicks > 0 && savedProgress > 0 && savedProgress < savedTicks) {
            this.slicing = true;
            this.progress = savedProgress;
            this.ticks = savedTicks;
        } else {
            resetSlice();
        }
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new WaferSlicerMenu(containerId, playerInventory, machine);
    }
}