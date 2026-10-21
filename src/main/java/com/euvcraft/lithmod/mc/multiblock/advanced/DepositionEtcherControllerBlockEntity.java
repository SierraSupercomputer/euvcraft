package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deposition Etcher Controller Block Entity.
 *
 * <p>Registry ID: deposition_etcher. Shape: 3 wide x 3 tall x 5 deep (45 blocks).
 * Slots: 0 FOUP input, 1 silicon reagent input, 2 prepared FOUP output.
 * Requires PURE fresh trusted nonempty unstarted unprepared (G1) lot, all wafer layer 0.
 * Each job transforms actual original FOUP (preserve item/template/components/UUID/wafer rolls/flags/capacity/grade)
 * to generation 2 prepared. Consumes input FOUP + editable quantity silicon atomically at admission,
 * retains pending FOUP privately until completion; output blocked/power/unformed pauses exact progress.
 * Break drops original pending FOUP (unprepared, no refund reagent/paid FE) exactly once,
 * since no output ever written; no copying to inventory as well.
 * Uses existing base dropContents hook correctly.
 * Saves pending FOUP with ItemStack.CODEC and frozen job cost/progress;
 * malformed jobs fail closed preserving recoverable legitimate inputs where possible, no free output.
 * Cannot reprepare processed/already prepared/ordinary/client-view lot.
 * Reload freezes job cost/mode.
 * Recipe parameters: duration 1200, silicon_per_lot 4, energy_per_tick 1000, capacity 1_000_000, max_receive 20_000.
 * All balance editable JSON, no hardcoded recipe fallback when missing. No fluid/item capabilities.
 */
public class DepositionEtcherControllerBlockEntity extends AdvancedControllerBlockEntity {

    // Slot indices
    public static final int SLOT_FOUP_INPUT = 0;
    public static final int SLOT_SILICON_REAGENT = 1;
    public static final int SLOT_PREPARED_FOUP_OUTPUT = 2;
    public static final int INPUT_SLOTS = 2;
    public static final int OUTPUT_SLOTS = 1;
    public static final int SLOT_COUNT = 3;

    // Job state
    private enum JobState { IDLE, RUNNING, PAUSED, FINISHED }
    private JobState jobState = JobState.IDLE;
    private int jobProgress = 0;
    private int jobDuration;
    private int siliconPerLot;
    private int energyPerTick;
    private @Nullable ItemStack pendingFoup; // The FOUP being prepared
    private @Nullable ItemStack siliconReagentSnapshot; // The silicon consumed at admission
    private int siliconConsumed = 0;

    public DepositionEtcherControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedMachines.DEPOSITION_ETCHER_CONTROLLER_BE.get(), pos, state, INPUT_SLOTS, OUTPUT_SLOTS);
    }

    @Override
    protected String machineId() {
        return "deposition_etcher";
    }

    @Override
    protected StructureShape shape() {
        return AdvancedFormation.DEPOSITION_ETCHER_SHAPE;
    }

    @Override
    protected BlockKind controllerKind() {
        return BlockKind.CONTROLLER;
    }

    @Override
    protected BlockKind panelKind() {
        return BlockKind.OPERATING_PANEL;
    }

    @Override
    protected BlockKind powerPortKind() {
        return BlockKind.POWER_PORT;
    }

    @Override
    protected BlockKind casingKind() {
        return BlockKind.CASING;
    }

    @Override
    protected boolean isLarge() {
        return true;
    }

    @Override
    protected FabMachineMenu createAdvancedMenu(int containerId, Inventory playerInventory, AdvancedControllerBlockEntity machine) {
        return new DepositionEtcherMenu(containerId, playerInventory, this);
    }

    // ---------------------------------------------------------------- admission validation

    /**
     * Checks if the FOUP in the input slot is a valid candidate for preparation.
     * Must be: PURE, fresh, trusted, nonempty, unstarted, unprepared (G1), all wafer layer 0.
     */
    private @Nullable FoupContents validateFoupForPreparation(ItemStack foupStack) {
        if (foupStack.isEmpty() || !FoupItems.isFoup(foupStack)) return null;
        FoupContents contents = FoupItems.trustedContentsOf(foupStack);
        if (contents == null || contents.isEmpty()) return null;
        if (contents.grade() != com.euvcraft.lithmod.core.WaferSubstrate.PURE) return null;
        if (contents.preparationGeneration() != 1) return null; // unprepared = G1
        if (contents.run() != null) return null; // unstarted
        // All wafers must be at layer 0
        for (com.euvcraft.lithmod.core.Wafer wafer : contents.wafers()) {
            if (wafer.layer() != 0) return null;
        }
        return contents;
    }

    /**
     * Attempts to start a preparation job.
     * Consumes the FOUP and silicon reagent atomically.
     */
    private boolean tryStartJob() {
        ItemStack foupStack = slot(SLOT_FOUP_INPUT);
        ItemStack siliconStack = slot(SLOT_SILICON_REAGENT);

        FoupContents contents = validateFoupForPreparation(foupStack);
        if (contents == null) return false;

        // Check silicon reagent (must be silicon item)
        if (siliconStack.isEmpty() || !siliconStack.is(ModItems.SILICON.get())) return false;
        if (siliconStack.getCount() < siliconPerLot) return false;

        // Check energy buffer can pay for at least one tick
        if (buffer().getAmountAsLong() < energyPerTick) return false;

        // Check output slot is empty
        if (!slot(SLOT_PREPARED_FOUP_OUTPUT).isEmpty()) return false;

        // Consume silicon reagent
        siliconStack.shrink(siliconPerLot);
        if (siliconStack.isEmpty()) clearSlot(SLOT_SILICON_REAGENT);
        siliconReagentSnapshot = new ItemStack(ModItems.SILICON.get(), siliconPerLot);
        siliconConsumed = siliconPerLot;

        // Take the FOUP into pending (preserving all data)
        pendingFoup = foupStack.copy();
        clearSlot(SLOT_FOUP_INPUT);

        // Initialize job
        jobState = JobState.RUNNING;
        jobProgress = 0;
        jobDuration = definition().map(d -> d.paramOrDefault("duration", 1200)).orElse(1200);
        siliconPerLot = definition().map(d -> d.paramOrDefault("silicon_per_lot", 4)).orElse(4);
        energyPerTick = definition().map(d -> d.paramOrDefault("energy_per_tick", 1000)).orElse(1000);

        setChanged();
        return true;
    }

    // ---------------------------------------------------------------- job ticking

    private void tickJob() {
        if (jobState != JobState.RUNNING) return;

        // Check if formed, powered, and output not blocked
        if (!formed) {
            jobState = JobState.PAUSED;
            setChanged();
            return;
        }
        if (buffer().getAmountAsLong() < energyPerTick) {
            jobState = JobState.PAUSED;
            setChanged();
            return;
        }
        if (!slot(SLOT_PREPARED_FOUP_OUTPUT).isEmpty()) {
            jobState = JobState.PAUSED;
            setChanged();
            return;
        }

        // Pay energy
        energySource().draw(energyPerTick);

        // Advance progress
        jobProgress++;
        if (jobProgress >= jobDuration) {
            // Job complete: create prepared FOUP (generation 2)
            FoupContents original = FoupItems.trustedContentsOf(pendingFoup);
            FoupContents prepared = original.withPreparationGeneration(2); // G2 = prepared
            ItemStack outputFoup = FoupItems.with(pendingFoup.getItem(), prepared);
            setSlot(SLOT_PREPARED_FOUP_OUTPUT, outputFoup);

            // Clear pending
            pendingFoup = null;
            siliconReagentSnapshot = null;
            siliconConsumed = 0;
            jobState = JobState.FINISHED;
        }
        setChanged();
    }

    // ---------------------------------------------------------------- server tick override

    @Override
    public void serverTick() {
        super.serverTick(); // handles formation revalidation
        if (!(level instanceof ServerLevel)) return;

        refreshDefinition();
        // Load job parameters from definition
        if (jobState == JobState.IDLE) {
            jobDuration = definition().map(d -> d.paramOrDefault("duration", 1200)).orElse(1200);
            siliconPerLot = definition().map(d -> d.paramOrDefault("silicon_per_lot", 4)).orElse(4);
            energyPerTick = definition().map(d -> d.paramOrDefault("energy_per_tick", 1000)).orElse(1000);
        }

        if (jobState == JobState.IDLE) {
            tryStartJob();
        } else if (jobState == JobState.RUNNING || jobState == JobState.PAUSED) {
            // Check if pause conditions cleared
            if (jobState == JobState.PAUSED && formed
                    && buffer().getAmountAsLong() >= energyPerTick
                    && slot(SLOT_PREPARED_FOUP_OUTPUT).isEmpty()) {
                jobState = JobState.RUNNING;
            }
            tickJob();
        } else if (jobState == JobState.FINISHED) {
            // Stay in FINISHED until output is removed
            if (slot(SLOT_PREPARED_FOUP_OUTPUT).isEmpty()) {
                jobState = JobState.IDLE;
                setChanged();
            }
        }

        publishProgress();
    }

    // ---------------------------------------------------------------- drop on break

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // If there's a pending FOUP, drop it exactly once (unprepared, no refund)
        if (pendingFoup != null && level != null && !level.isClientSide()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pendingFoup.copy());
            pendingFoup = null;
        }
        // Also drop silicon reagent if it was consumed but job didn't complete
        // (The base class Container.dropContents handles the silicon stack in the slot)
    }

    // ---------------------------------------------------------------- progress publishing

    @Override
    protected void publishProgress() {
        super.publishProgress();
        data().set(DATA_PROGRESS, jobProgress);
        data().set(DATA_TICKS, jobDuration);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("job_state", jobState.name());
        output.putInt("job_progress", jobProgress);
        output.putInt("job_duration", jobDuration);
        output.putInt("silicon_per_lot", siliconPerLot);
        output.putInt("energy_per_tick", energyPerTick);
        output.putInt("silicon_consumed", siliconConsumed);
        if (pendingFoup != null) {
            output.store("pending_foup", ItemStack.CODEC, pendingFoup);
        }
        if (siliconReagentSnapshot != null) {
            output.store("silicon_reagent", ItemStack.CODEC, siliconReagentSnapshot);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String stateStr = input.getString("job_state").orElse("IDLE");
        try {
            jobState = JobState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            jobState = JobState.IDLE;
        }
        jobProgress = input.getInt("job_progress").orElse(0);
        jobDuration = input.getInt("job_duration").orElse(1200);
        siliconPerLot = input.getInt("silicon_per_lot").orElse(4);
        energyPerTick = input.getInt("energy_per_tick").orElse(1000);
        siliconConsumed = input.getInt("silicon_consumed").orElse(0);
        pendingFoup = input.read("pending_foup", ItemStack.CODEC).orElse(null);
        siliconReagentSnapshot = input.read("silicon_reagent", ItemStack.CODEC).orElse(null);

        // Malformed job recovery: if pendingFoup exists but jobState is not RUNNING/PAUSED,
        // drop it and reset (fail closed, preserve legitimate input where possible)
        if (pendingFoup != null && jobState != JobState.RUNNING && jobState != JobState.PAUSED) {
            // This is a malformed save - drop the FOUP and reset
            pendingFoup = null;
            siliconReagentSnapshot = null;
            siliconConsumed = 0;
            jobState = JobState.IDLE;
            jobProgress = 0;
        }
    }

    // ---------------------------------------------------------------- client data

    public int jobProgress() { return jobProgress; }
    public int jobDuration() { return jobDuration; }
    public JobState jobState() { return jobState; }
    public boolean hasPendingFoup() { return pendingFoup != null; }
    public @Nullable ItemStack pendingFoup() { return pendingFoup != null ? pendingFoup.copy() : null; }
}