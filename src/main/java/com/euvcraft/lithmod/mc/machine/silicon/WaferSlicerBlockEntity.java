package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSlicing;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
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
 * both inputs are re-checked every tick, nothing is consumed until the product is
 * written, a full output slot pauses instead of eating the boule without spending FE, and
 * a partial energy draw counts as no energy.</p>
 *
 * <p>Every number comes from data or config, with no Java copy of any of them. The length
 * of a run is the {@code slice_ticks} param of
 * {@code data/lithmod/machines/wafer_slicer.json}; the energy is the definition's
 * {@code energy_per_tick} through the configured FE multiplier; the buffer capacity and
 * the insert limit are applied to the real buffer by the base class; the wafer count is
 * {@link WaferSlicing#wafersFor} over the configured {@code wafersPerBoule} and the stored
 * FOUP capacity. A machine whose definition is missing or has been removed, or whose
 * definition declares no {@code slice_ticks}, does not slice at all and refuses FE: it
 * fails closed rather than running on a made-up number, and the stored FE it already holds
 * is kept for the day the definition comes back.</p>
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

    private static final String STATE_KEY = "slicer";

    private boolean slicing;
    private boolean paused;
    private int progress;
    private int ticks;

    public WaferSlicerBlockEntity(BlockPos pos, BlockState state) {
        super(SiliconMachines.WAFER_SLICER_BE.get(), pos, state, 2, 1);
    }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    // ------------------------------------------------------------------
    // Input validation: the trusted read for gameplay, the public one for the slot
    // ------------------------------------------------------------------

    /**
     * Reads a stack as a lot the slicer is willing to fill, through the trusted read.
     *
     * <p><strong>This is the authoritative check</strong>, and it is the one the server tick
     * and {@link #complete()} use: the only acceptable input is a FOUP that carries a lot,
     * and that lot holds no wafers and no run spec. Wafers mean the lot is already sliced
     * and a run spec means it has been processed. The lot itself is only ever read through
     * {@link FoupItems#trustedContentsOf}.</p>
     *
     * <p>A client view is refused outright rather than sanitised. The trusted read turns a
     * client view into a fresh empty lot, so without this check the machine would accept a
     * placeholder as if it were a real empty FOUP and slice a boule into a lot the player
     * never owned. A client view is a display value, not a lot.</p>
     *
     * <p>A FOUP stack with no lot component at all is refused for the same reason: there is
     * no stored capacity to fill it to.</p>
     *
     * @param stack the stack to check, not null
     * @return the trusted lot, or {@code null} when this slot must refuse the stack
     */
    public static @Nullable FoupContents acceptedFoup(ItemStack stack) {
        FoupContents raw = FoupItems.contentsOf(stack);
        if (raw == null || FoupContentsCodec.isClientView(raw)) {
            return null;
        }
        FoupContents contents = FoupItems.trustedContentsOf(stack);
        return contents.isEmpty() && contents.run() == null ? contents : null;
    }

    /**
     * The slot's own rule, which runs on the client menu as well as the server one.
     *
     * <p>A client never holds a sealed lot. The network codec sends
     * {@link FoupContents#publicView()} and nothing else, so the FOUP stack in a client slot
     * is a client-view placeholder: its wafer count is the real one, and its rolls and its
     * run spec were never filled in by the server. Reading that stack through
     * {@link FoupItems#trustedContentsOf} is therefore the wrong tool for predicting what
     * the slot will accept, because the trusted read deliberately throws the wafer count
     * away and answers "empty" — it would report a FOUP the player can plainly see is full
     * as a valid input, and it would log a warning for a perfectly normal client placeholder
     * every time the slot is asked.</p>
     *
     * <p>So this method uses the public wafer count when the stack is a client view and the
     * real lot otherwise, and that is exactly the boundary between the two sides:</p>
     * <ul>
     *   <li>a client view holding wafers is refused here and on the server, so a FOUP the
     *       player can plainly see is full is turned away in the client's own menu before a
     *       click is even sent;</li>
     *   <li>the client cannot know about a hidden run spec, because the network codec sends
     *       none, so a client view with no wafers is predicted acceptable even when the
     *       server's lot is in fact started. Nothing runs either way: the server refuses
     *       every client view outright through {@link #acceptedFoup}, so a placeholder never
     *       becomes a machine input;</li>
     *   <li>a stack with no lot component at all is refused on both sides.</li>
     * </ul>
     */
    public static boolean acceptsFoupStack(ItemStack stack) {
        FoupContents contents = FoupItems.contentsOf(stack);
        if (contents == null) {
            return false;
        }
        if (FoupContentsCodec.isClientView(contents)) {
            FoupPublicView view = contents.publicView();
            return view.waferCount() == 0;
        }
        return contents.isEmpty() && contents.run() == null;
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
     * exist for this machine id. It does call
     * {@link #refreshDefinition() the definition refresh} and nothing else of the base
     * tick, so a data change resizes the real buffer without the recipe runner ever being
     * involved, and a definition that has gone closes the buffer and the run rather than
     * leaving the machine half-chargable. Progress is still published through the same
     * {@code DATA_PROGRESS} and {@code DATA_TICKS} values the menu reads, so the screen
     * needs to know nothing about this class.</p>
     */
    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        refreshDefinition();

        MachineDefinition definition = definition().orElse(null);
        int sliceTicks = definition == null ? 0 : definition.param(SLICE_TICKS_PARAM).orElse(0);
        if (definition == null || sliceTicks <= 0) {
            // No usable data: no run at all. A machine must never slice on a guessed length.
            resetSlice();
            syncSlicerData();
            return;
        }
        int perTick = definition.energyPerTick(null, EUVCRAFTMod.settings());

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

    /** Whether both inputs are present and valid and the output has room. */
    private boolean readyToSlice() {
        return inputsValid() && outputSlot().isEmpty();
    }

    /** Whether the boule and the FOUP this machine is slicing are both still there. */
    private boolean inputsValid() {
        return slot(BOULE_SLOT).is(bouleItem())
            && acceptedFoup(slot(FOUP_SLOT)) != null;
    }

    /**
     * One tick of a run in progress; mirrors the runner's pause rules.
     *
     * <p>Order matters and is the whole of the anti-cheese rule: the inputs are checked
     * first, so a boule or a FOUP taken out or swapped mid-run drops the run before any FE
     * is drawn; then the output slot, so a full output pauses with its progress intact and
     * costs nothing; then the energy.</p>
     */
    private void advance(int perTick) {
        if (!inputsValid()) {
            resetSlice();
            return;
        }
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
     * would be gone and nothing would come out. Both inputs and the output slot are
     * re-checked here, because this is the tick the machine pays for and the tick it hands
     * out wafers on; if anything moved between the check at the top of {@link #advance}
     * and now, the run is dropped and nothing is consumed.</p>
     */
    private void complete() {
        ItemStack foupIn = slot(FOUP_SLOT);
        FoupContents input = acceptedFoup(foupIn);
        if (input == null || !slot(BOULE_SLOT).is(bouleItem()) || !outputSlot().isEmpty()) {
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
     * <p>Called when a run finishes, when an input it was using is taken away or swapped
     * mid-run, when the definition stops describing a usable slice, and by tests that need
     * a machine in a known state. The inputs are left alone: nothing is consumed until a
     * product is written.</p>
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

    /**
     * The slice length from the definition's {@code slice_ticks} param, or {@code 0} when
     * there is no definition or the definition declares no such param.
     *
     * <p>Zero is the "this machine may not run" answer, and the tick treats it as such.</p>
     */
    public int sliceTicks() {
        return definition().flatMap(d -> d.param(SLICE_TICKS_PARAM)).orElse(0);
    }

    /**
     * The FE this machine draws per active tick from its definition, or {@code 0} when no
     * definition is loaded.
     */
    public int energyPerTick() {
        return definition()
            .map(definition -> definition.energyPerTick(null, EUVCRAFTMod.settings()))
            .orElse(0);
    }

    /**
     * Publishes this machine's progress values to the menu.
     *
     * <p>The capacity is not written here: the base class keeps
     * {@link #DATA_CAPACITY} equal to the real buffer's capacity whenever a definition is
     * applied, which is the only thing that resizes the buffer.</p>
     */
    private void syncSlicerData() {
        this.data().set(DATA_ENERGY, (int) this.buffer().getAmountAsLong());
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
     * <p>Only a flag whose progress stays below its tick count is trusted, the same rule
     * the runner uses: anything else can only have come from a hand-edited tag.
     * Zero progress is accepted, because a run paused before its first FE payment is a
     * legitimate slice-in-progress, not corruption. A negative progress is still not
     * trusted, and restarting from tick one is the safe answer.</p>
     *
     * <p>Restores only when a definition is present and declares a valid {@code slice_ticks};
     * otherwise the machine must fail closed and the run is dropped. Stale {@code paused}
     * and {@code slicing} fields are cleared before applying any saved data, because this
     * method can run on an existing block entity that was already mid-run. The synced
     * {@code DATA_PROGRESS} and {@code DATA_TICKS} are published at the end so the menu
     * sees the restored numbers immediately.</p>
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Clear any stale state from a previous run on this BE before restoring.
        this.slicing = false;
        this.paused = false;

        ValueInput state = input.childOrEmpty(STATE_KEY);
        boolean savedSlicing = state.getBooleanOr("slicing", false);
        int savedProgress = state.getIntOr("progress", 0);
        int savedTicks = state.getIntOr("ticks", 0);

        MachineDefinition definition = definition().orElse(null);
        int sliceTicks = definition == null ? 0 : definition.param(SLICE_TICKS_PARAM).orElse(0);

        if (savedSlicing && definition != null && sliceTicks > 0
                && savedTicks > 0 && savedProgress >= 0 && savedProgress < savedTicks) {
            this.slicing = true;
            this.progress = savedProgress;
            this.ticks = savedTicks;
        } else {
            resetSlice();
        }
        syncSlicerData();
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new WaferSlicerMenu(containerId, playerInventory, machine);
    }
}
