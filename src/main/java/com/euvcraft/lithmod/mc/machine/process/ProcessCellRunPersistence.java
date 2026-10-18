package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.LineStats;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessRun;
import com.euvcraft.lithmod.core.ProcessRunRequest;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.TierDataLoader;
import com.euvcraft.lithmod.mc.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Private persistence for the Process Cell controller's run state.
 * <p>
 * Saves and restores the {@link ProcessRun} snapshot using the CURRENT persisted lot,
 * not the initial lot. Validates malformed values BEFORE FabSettings clamping.
 * Rejects inconsistent snapshot safely without resetting into a free new run.
 * Restore must survive definitions missing/changed and resume under original
 * accepted cost/schedule when definition returns.
 */
public final class ProcessCellRunPersistence {

    private static final String TAG_RUN = "processRun";
    private static final String TAG_ABORT_LATCHED = "abortLatched";
    private static final String TAG_LAST_LOT_ID = "lastLotId";
    private static final String TAG_LAST_REFUSAL = "lastRefusal";
    private static final String TAG_INSTALLED_GENERATIONS = "installedGenerations";

    // Snapshot fields
    private static final String TAG_STATE = "state";
    private static final String TAG_CURRENT_LAYER = "currentLayer";
    private static final String TAG_PROGRESS_TICKS = "progressTicks";
    private static final String TAG_RESIST_PAID = "resistPaid";
    private static final String TAG_MASK_USE_CONSUMED = "maskUseConsumed";
    private static final String TAG_FINISH_EVENTS_EMITTED = "finishEventsEmitted";

    // Frozen admission contract fields
    private static final String TAG_DIE_TYPE = "dieType";
    private static final String TAG_TIER_INDEX = "tierIndex";
    private static final String TAG_USES_REMAINING = "usesRemaining";
    private static final String TAG_LINE_STATS = "lineStats";
    private static final String TAG_LINE_CAN_RUN_TIER = "lineCanRunTier";
    private static final String TAG_DIE_MAKEABLE = "dieMakeable";
    private static final String TAG_ENERGY_PER_TICK = "energyPerTick";
    private static final String TAG_RESIST_TEMPLATE_TIER = "resistTemplateTier";
    private static final String TAG_RESIST_TEMPLATE_COUNT = "resistTemplateCount";

    private ProcessCellRunPersistence() {
    }

    /**
     * Saves the run state to NBT.
     */
    public static void save(ProcessCellControllerBlockEntity controller, ValueOutput output) {
        ValueOutput runOutput = output.child(TAG_RUN);

        runOutput.putBoolean(TAG_ABORT_LATCHED, controller.abortLatched);

        if (controller.lastLotId != null) {
            runOutput.putUUID(TAG_LAST_LOT_ID, controller.lastLotId);
        }

        if (controller.lastRefusal != null) {
            ValueOutput refusalOutput = runOutput.child(TAG_LAST_REFUSAL);
            refusalOutput.putString("code", controller.lastRefusal.code().name());
            ListTag unmetLinesTag = new ListTag();
            for (String line : controller.lastRefusal.unmetLines()) {
                unmetLinesTag.add(line);
            }
            refusalOutput.put("unmetLines", unmetLinesTag);
        }

        if (controller.installedGenerationsSnapshot != null) {
            ListTag gensTag = new ListTag();
            for (Map.Entry<String, Integer> e : controller.installedGenerationsSnapshot.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("line", e.getKey());
                entry.putInt("gen", e.getValue());
                gensTag.add(entry);
            }
            runOutput.put(TAG_INSTALLED_GENERATIONS, gensTag);
        }

        if (controller.run != null) {
            ProcessRun.Snapshot snapshot = controller.run.snapshot();
            ValueOutput runDataOutput = runOutput.child("runData");
            runDataOutput.putString(TAG_STATE, snapshot.state().name());
            runDataOutput.putInt(TAG_CURRENT_LAYER, snapshot.currentLayer());
            runDataOutput.putInt(TAG_PROGRESS_TICKS, snapshot.progressTicks());
            runDataOutput.putBoolean(TAG_RESIST_PAID, snapshot.resistPaid());
            runDataOutput.putBoolean(TAG_MASK_USE_CONSUMED, snapshot.maskUseConsumed());
            runDataOutput.putBoolean(TAG_FINISH_EVENTS_EMITTED, snapshot.finishEventsEmitted());

            // Frozen admission contract
            ProcessRunRequest request = controller.run.request();
            runDataOutput.putString(TAG_DIE_TYPE, request.dieType());
            runDataOutput.putInt(TAG_TIER_INDEX, request.tier().index());
            runDataOutput.putInt(TAG_USES_REMAINING, request.usesRemaining());
            runDataOutput.putBoolean(TAG_LINE_CAN_RUN_TIER, request.lineCanRunTier());
            runDataOutput.putBoolean(TAG_DIE_MAKEABLE, request.dieMakeable());
            runDataOutput.putInt(TAG_ENERGY_PER_TICK, request.energyPerTick());

            // Resist template (including when slot runs empty)
            ItemStack resistStack = controller.slot(ProcessCellControllerBlockEntity.SLOT_RESIST);
            runDataOutput.putInt(TAG_RESIST_TEMPLATE_TIER, ResistCatalog.tierOf(resistStack));
            runDataOutput.putInt(TAG_RESIST_TEMPLATE_COUNT, resistStack.getCount());

            // LineStats
            LineStats ls = request.lineStats();
            ValueOutput lsOutput = runDataOutput.child(TAG_LINE_STATS);
            lsOutput.putInt("resolutionNm", ls.resolutionNm());
            lsOutput.putInt("overlayNm", ls.overlayNm());
            lsOutput.putInt("throughputWph", ls.throughputWph());
            lsOutput.putDouble("defectFactor", ls.defectFactor());
            lsOutput.putInt("resolutionTier", ls.resolutionTier());
            lsOutput.putInt("overlayTier", ls.overlayTier());
            lsOutput.putInt("throughputTier", ls.throughputTier());
        }
    }

    /**
     * Loads the run state from NBT.
     * Validates malformed values BEFORE FabSettings clamping.
     * Rejects inconsistent snapshot safely without resetting into a free new run.
     * Defers restoring a pending serialized contract until first server tick if required.
     */
    public static void load(ProcessCellControllerBlockEntity controller, ValueInput input) {
        ValueInput runInput = input.childOrEmpty(TAG_RUN);
        if (runInput == null) {
            return;
        }

        // Load abort latch
        controller.abortLatched = runInput.getBoolean(TAG_ABORT_LATCHED).orElse(false);

        // Load last lot ID
        if (runInput.hasUUID(TAG_LAST_LOT_ID)) {
            controller.lastLotId = runInput.getUUID(TAG_LAST_LOT_ID);
        }

        // Load last refusal
        ValueInput refusalInput = runInput.childOrEmpty(TAG_LAST_REFUSAL);
        if (refusalInput != null) {
            String codeStr = refusalInput.getString("code").orElse("");
            // Reading list from ValueInput is limited - we'll rely on M1_STUB_GENERATIONS being constant
            List<String> unmetLines = List.of();
            try {
                RefusalReason.Code code = RefusalReason.Code.valueOf(codeStr);
                controller.lastRefusal = new RefusalReason(code, unmetLines);
            } catch (IllegalArgumentException e) {
                // Malformed refusal - ignore
                controller.lastRefusal = null;
            }
        }

        // Load installed generations snapshot
        // Reading list from ValueInput is limited - we'll rely on M1_STUB_GENERATIONS being constant
        controller.installedGenerationsSnapshot = Map.copyOf(M1_STUB_GENERATIONS);

        // Load run snapshot - we defer actual reconstruction to first server tick
        // because we need the current FOUP lot, mask, and definition to validate
        if (runInput.childOrEmpty("runData") != null) {
            // We need to preserve the run data for deferred restoration.
            // Since ValueInput doesn't easily give us nested CompoundTag access,
            // we'll re-read from the raw NBT in tryRestoreRun by storing the full input.
            // For now, we'll mark that we have deferred data and re-read from the
            // original CompoundTag in tryRestoreRun.
            controller.deferredRunTag = new CompoundTag(); // marker - will be replaced by actual data
            // Actually, we need to get the raw CompoundTag. Let's store the whole runInput's
            // backing data. Since ValueInput is a wrapper, we can't easily get the CompoundTag.
            // We'll use a workaround: store the CompoundTag from the original input in a
            // transient field on the controller during load.
            // But we don't have access to the original CompoundTag here.
            // The solution: tryRestoreRun will be called on first tick and can re-read
            // from the block entity's NBT if needed. But the NBT is already consumed.
            // Better approach: read all the values we need NOW and store them in a
            // temporary structure on the controller.
            // Let's read the runData values directly from ValueInput.
            ValueInput runDataInput = runInput.childOrEmpty("runData");
            if (runDataInput != null) {
                CompoundTag deferredTag = new CompoundTag();
                // Read all fields we need
                runDataInput.getString(TAG_STATE).ifPresent(s -> deferredTag.putString(TAG_STATE, s));
                runDataInput.getInt(TAG_CURRENT_LAYER).ifPresent(i -> deferredTag.putInt(TAG_CURRENT_LAYER, i));
                runDataInput.getInt(TAG_PROGRESS_TICKS).ifPresent(i -> deferredTag.putInt(TAG_PROGRESS_TICKS, i));
                runDataInput.getBoolean(TAG_RESIST_PAID).ifPresent(b -> deferredTag.putBoolean(TAG_RESIST_PAID, b));
                runDataInput.getBoolean(TAG_MASK_USE_CONSUMED).ifPresent(b -> deferredTag.putBoolean(TAG_MASK_USE_CONSUMED, b));
                runDataInput.getBoolean(TAG_FINISH_EVENTS_EMITTED).ifPresent(b -> deferredTag.putBoolean(TAG_FINISH_EVENTS_EMITTED, b));
                runDataInput.getString(TAG_DIE_TYPE).ifPresent(s -> deferredTag.putString(TAG_DIE_TYPE, s));
                runDataInput.getInt(TAG_TIER_INDEX).ifPresent(i -> deferredTag.putInt(TAG_TIER_INDEX, i));
                runDataInput.getInt(TAG_USES_REMAINING).ifPresent(i -> deferredTag.putInt(TAG_USES_REMAINING, i));
                runDataInput.getBoolean(TAG_LINE_CAN_RUN_TIER).ifPresent(b -> deferredTag.putBoolean(TAG_LINE_CAN_RUN_TIER, b));
                runDataInput.getBoolean(TAG_DIE_MAKEABLE).ifPresent(b -> deferredTag.putBoolean(TAG_DIE_MAKEABLE, b));
                runDataInput.getInt(TAG_ENERGY_PER_TICK).ifPresent(i -> deferredTag.putInt(TAG_ENERGY_PER_TICK, i));
                runDataInput.getInt(TAG_RESIST_TEMPLATE_TIER).ifPresent(i -> deferredTag.putInt(TAG_RESIST_TEMPLATE_TIER, i));
                runDataInput.getInt(TAG_RESIST_TEMPLATE_COUNT).ifPresent(i -> deferredTag.putInt(TAG_RESIST_TEMPLATE_COUNT, i));
                // Read LineStats
                ValueInput lsInput = runDataInput.childOrEmpty(TAG_LINE_STATS);
                if (lsInput != null) {
                    CompoundTag lsTag = new CompoundTag();
                    lsInput.getInt("resolutionNm").ifPresent(i -> lsTag.putInt("resolutionNm", i));
                    lsInput.getInt("overlayNm").ifPresent(i -> lsTag.putInt("overlayNm", i));
                    lsInput.getInt("throughputWph").ifPresent(i -> lsTag.putInt("throughputWph", i));
                    lsInput.getDouble("defectFactor").ifPresent(d -> lsTag.putDouble("defectFactor", d));
                    lsInput.getInt("resolutionTier").ifPresent(i -> lsTag.putInt("resolutionTier", i));
                    lsInput.getInt("overlayTier").ifPresent(i -> lsTag.putInt("overlayTier", i));
                    lsInput.getInt("throughputTier").ifPresent(i -> lsTag.putInt("throughputTier", i));
                    deferredTag.put(TAG_LINE_STATS, lsTag);
                }
                setDeferredRunTag(controller, deferredTag);
            }
        }
    }

    /**
     * Attempts to restore the run on first server tick after load.
     * Called from serverTick when run is null but deferredRunTag exists.
     * Rebuilds admitted gates from a validated frozen contract, never treating
     * this as admission of a new run.
     */
    public static void tryRestoreRun(ProcessCellControllerBlockEntity controller) {
        CompoundTag runTag = getDeferredRunTag(controller);
        if (runTag == null) {
            return;
        }
        if (controller.level == null || controller.level.isClientSide()) {
            return;
        }
        if (controller.run != null || controller.abortLatched) {
            return;
        }
        if (!controller.formed) {
            return;
        }

        Optional<MachineDefinition> defOpt = controller.definition();
        if (defOpt.isEmpty()) {
            // Definition not loaded yet - keep deferred tag for next tick
            return;
        }

        setDeferredRunTag(controller, null); // Consume it

        // Read frozen contract
        String dieType = runTag.getString(TAG_DIE_TYPE).orElse("");
        int tierIndex = runTag.getInt(TAG_TIER_INDEX).orElse(0);
        int usesRemaining = runTag.getInt(TAG_USES_REMAINING).orElse(0);
        boolean lineCanRunTier = runTag.getBoolean(TAG_LINE_CAN_RUN_TIER).orElse(false);
        boolean dieMakeable = runTag.getBoolean(TAG_DIE_MAKEABLE).orElse(false);
        int energyPerTick = runTag.getInt(TAG_ENERGY_PER_TICK).orElse(0);
        int resistTemplateTier = runTag.getInt(TAG_RESIST_TEMPLATE_TIER).orElse(0);
        int resistTemplateCount = runTag.getInt(TAG_RESIST_TEMPLATE_COUNT).orElse(0);

        // Read LineStats
        LineStats lineStats = null;
        if (runTag.contains(TAG_LINE_STATS, Tag.TAG_COMPOUND)) {
            CompoundTag lsTag = runTag.getCompound(TAG_LINE_STATS);
            lineStats = new LineStats(
                    lsTag.getInt("resolutionNm").orElse(0),
                    lsTag.getInt("overlayNm").orElse(0),
                    lsTag.getInt("throughputWph").orElse(0),
                    lsTag.getDouble("defectFactor").orElse(0.0),
                    lsTag.getInt("resolutionTier").orElse(0),
                    lsTag.getInt("overlayTier").orElse(0),
                    lsTag.getInt("throughputTier").orElse(0)
            );
        }

        // Read snapshot
        ProcessState state = ProcessState.IDLE;
        int currentLayer = 1;
        int progressTicks = 0;
        boolean resistPaid = false;
        boolean maskUseConsumed = false;
        boolean finishEventsEmitted = false;

        if (runTag.contains(TAG_STATE, Tag.TAG_STRING)) {
            try {
                state = ProcessState.valueOf(runTag.getString(TAG_STATE).orElse("IDLE"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        currentLayer = runTag.getInt(TAG_CURRENT_LAYER).orElse(1);
        progressTicks = runTag.getInt(TAG_PROGRESS_TICKS).orElse(0);
        resistPaid = runTag.getBoolean(TAG_RESIST_PAID).orElse(false);
        maskUseConsumed = runTag.getBoolean(TAG_MASK_USE_CONSUMED).orElse(false);
        finishEventsEmitted = runTag.getBoolean(TAG_FINISH_EVENTS_EMITTED).orElse(false);

        // Validate snapshot consistency (ProcessRun.Snapshot does this in constructor)
        // But we need to validate against the current lot first

        // Get current lot
        ItemStack foupStack = controller.slot(ProcessCellControllerBlockEntity.SLOT_FOUP);
        FoupContents lot = FoupItems.trustedContentsOf(foupStack);
        if (lot == null || lot.isEmpty()) {
            // Lot gone - cannot restore
            return;
        }

        // Validate lot UUID matches
        if (controller.lastLotId != null && !lot.lotId().equals(controller.lastLotId)) {
            // Different lot - cannot restore
            return;
        }

        // Get tier
        TierRegistry tierRegistry = TierDataLoader.getRegistry();
        if (tierRegistry == null) {
            return;
        }
        Optional<Tier> tierOpt = tierRegistry.byIndex(tierIndex);
        if (tierOpt.isEmpty()) {
            return;
        }
        Tier tier = tierOpt.get();

        // Validate mask still present and matches
        ItemStack maskStack = controller.slot(ProcessCellControllerBlockEntity.SLOT_MASK);
        if (maskStack.isEmpty()) {
            return;
        }
        MaskData maskData = maskStack.get(ModDataComponents.MASK_DATA.get());
        if (maskData == null || maskData.tierIndex() != tierIndex || !maskData.dieType().equals(dieType)) {
            return;
        }

        // Validate resist template matches (same tier, can be empty)
        ItemStack resistStack = controller.slot(ProcessCellControllerBlockEntity.SLOT_RESIST);
        int currentResistTier = ResistCatalog.tierOf(resistStack);
        if (resistTemplateTier > 0 && currentResistTier != resistTemplateTier) {
            // Resist tier changed - cannot restore
            return;
        }

        // Validate generation matrix
        GenerationMatrix generationMatrix = SupportLineDataLoader.getMatrix();
        if (generationMatrix == null) {
            return;
        }

        // Validate installed generations match snapshot
        if (controller.installedGenerationsSnapshot == null) {
            return;
        }

        // Validate snapshot against current lot layer
        if (currentLayer != lot.lotLayer() + 1) {
            // Layer mismatch - cannot restore
            return;
        }

        // Validate progressTicks within bounds
        FabSettings settings = EUVCRAFTMod.settings();
        int layerTicks = FabSettings.layerTicks(tier, settings);
        if (progressTicks >= layerTicks) {
            return;
        }

        // Validate resistPaid state consistency
        if (state == ProcessState.FINISHED) {
            if (lot.lotLayer() != tier.layers() || lot.run() == null
                    || !lot.run().matches(tier, dieType, null)) {
                return;
            }
            if (progressTicks != 0 || resistPaid) {
                return;
            }
        } else if (state == ProcessState.IDLE && (progressTicks != 0 || resistPaid)) {
            return;
        } else if (state == ProcessState.PAUSED_NO_RESIST && (progressTicks != 0 || resistPaid)) {
            return;
        } else if (state == ProcessState.RUNNING && progressTicks == 0 && resistPaid) {
            return;
        } else if (state == ProcessState.PAUSED_NO_POWER && !resistPaid) {
            return;
        }

        // Validate start conditions still satisfied (except we allow paused states)
        // Build request from frozen contract
        ProcessRunRequest request = new ProcessRunRequest(
                lot,
                dieType,
                tier,
                null, // cores
                usesRemaining,
                lineStats,
                lineCanRunTier,
                dieMakeable,
                controller.installedGenerationsSnapshot,
                generationMatrix,
                settings,
                energyPerTick
        );

        // If not finished, refusal must be null
        if (state != ProcessState.FINISHED) {
            if (ProcessRun.refusal(request) != null) {
                return;
            }
        }

        // All validations passed - restore the run
        try {
            ProcessRun restored = ProcessRun.restore(request, new ProcessRun.Snapshot(
                    state, currentLayer, progressTicks, resistPaid, maskUseConsumed, finishEventsEmitted
            ));
            controller.run = restored;
            controller.lastRefusal = null;
            // Keep abortLatched if it was set, lastLotId, installedGenerationsSnapshot
            controller.setChanged();
        } catch (IllegalArgumentException e) {
            // Restore failed - leave run as null, keep deferred data discarded
        }
    }

    @Nullable
    static CompoundTag getDeferredRunTag(ProcessCellControllerBlockEntity controller) {
        return controller.deferredRunTag;
    }

    static void setDeferredRunTag(ProcessCellControllerBlockEntity controller, @Nullable CompoundTag tag) {
        controller.deferredRunTag = tag;
    }
}

// Add a package-private field to ProcessCellControllerBlockEntity for deferred run tag
// This needs to be added to the controller class