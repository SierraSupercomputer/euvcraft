package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.CalibrationExposure;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.ThermalProfile;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.ReflowStructureMatcher;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedFormation;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Controller of the T6 reflow oven.
 *
 * <p>It owns its own job runner instead of the base {@link MachineRecipeRunner}: a job is
 * admitted once from the {@code reflow_oven} recipe registry, then frozen (original inputs,
 * result, ticks, FE per tick, accepted calibration multiplier, room yield sum and one
 * server-private roll). The base runner is never ticked and therefore stays idle.</p>
 *
 * <p>An admitted job is paid one tick at a time. Each fully paid tick samples
 * {@link CleanroomRooms}; an unavailable room pauses the job <em>before</em> any FE is drawn.
 * A clean paid tick accumulates the room yield, and the final success test is
 * {@code frozenRoll < calibrationMultiplier * averageRoomYield / 1000}, evaluated exactly once
 * from persisted numbers so a save/reload cannot reroll it.</p>
 *
 * <p>Formation and thermal calibration are separate gates. Formation loss pauses the job and
 * never spends FE. Contamination latches through {@link CalibrationExposure}: it never changes
 * an already admitted job's frozen multiplier, but it blocks the next admission until a new
 * thermal calibration is accepted.</p>
 */
public final class ReflowControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner {

    public static final int SLOT_CHIP = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_PRODUCT = 2;
    public static final int SLOT_SCRAP = 3;
    public static final int SLOT_COUPON = 4;
    public static final int SLOT_COUNT = 5;

    private static final int NOMINAL_CONTROL = 50;
    private static final int DEFAULT_CONTAMINATION_PPM = 2500;
    private static final int DEFAULT_CONTAMINATION_TICKS = 1200;
    private static final int GRAPH_SAMPLES = 49;

    private final AdvancedFormation formation = new AdvancedFormation(this);

    private @Nullable Job pending;
    private boolean paused;
    private PauseReason pauseReason = PauseReason.NONE;

    private ThermalProfile.Controls controls =
            new ThermalProfile.Controls(NOMINAL_CONTROL, NOMINAL_CONTROL, NOMINAL_CONTROL);
    private int controlRevision = 1;
    private double[] previewCache;
    private int previewCacheRevision = -1;

    private CalibrationExposure.State exposure = CalibrationExposure.recalibrated();

    /** UUID string of the physical calibration panel this oven is tuned against. */
    private String hardwareFingerprint = "";

    private boolean measured;
    private int measuredHeater = NOMINAL_CONTROL;
    private int measuredConveyor = NOMINAL_CONTROL;
    private int measuredAirflow = NOMINAL_CONTROL;
    private double measuredRamp;
    private double measuredPeak;
    private double measuredDwell;
    private double measuredCooling;
    private int measuredQuality = -1;
    private boolean measuredAcceptable;
    private int measuredProblems;
    private String measuredFingerprint = "";
    private int measuredRevision = 1;
    private double[] measuredCache;
    private int measuredCacheRevision = -1;

    private boolean accepted;
    private int acceptedQuality = -1;
    private int acceptedPermille;
    private String acceptedFingerprint = "";

    /** Inputs recovered from a malformed save that did not fit back into their slots. */
    private final NonNullList<ItemStack> recoveredOverflow = NonNullList.withSize(4, ItemStack.EMPTY);

    private @Nullable MachineDefinition settingsDefinition;
    private @Nullable ThermalSettings settingsCache;

    public ReflowControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ReflowMachines.CONTROLLER_BE.get(), pos, state, 2, 2, 1, 0, 0);
    }

    @Override
    protected String machineId() {
        return "reflow_oven";
    }

    // ---------------------------------------------------------------- AdvancedOwner

    @Override
    public FabMachineBlockEntity machine() {
        return this;
    }

    @Override
    public AdvancedFormation formation() {
        return formation;
    }

    /**
     * {@code large} only drives the shared formation's visual LARGE flag. The actual 3x3x2
     * positions come from the override below, never from the shared large shape.
     */
    @Override
    public boolean large() {
        return false;
    }

    @Override
    public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return ReflowStructureMatcher.match(anchor, facing, lookup);
    }

    @Override
    public void setRemoved() {
        // Never touch the world here: this also runs on chunk unload.
        formation.unload();
        super.setRemoved();
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        refreshDefinition();
        formation.validate();
        refreshHardware();

        if (definition().isEmpty()) {
            if (pending != null) {
                paused = true;
                pauseReason = PauseReason.NO_DEFINITION;
            } else {
                paused = false;
                pauseReason = PauseReason.NONE;
            }
            syncData();
            return;
        }

        if (pending == null) {
            paused = false;
            pauseReason = PauseReason.NONE;
            tryAdmit();
        }
        if (pending != null) {
            advance(server);
        }
        syncData();
    }

    private void syncData() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, pending == null ? 0 : pending.progress);
        data().set(DATA_TICKS, pending == null ? 0 : pending.ticks);
    }

    // ---------------------------------------------------------------- admission

    private void tryAdmit() {
        if (!formation.formed()) {
            pauseReason = PauseReason.UNFORMED;
            return;
        }
        if (exposure.invalidated()) {
            pauseReason = PauseReason.CALIBRATION_INVALIDATED;
            return;
        }
        if (!calibrationAccepted()) {
            pauseReason = PauseReason.NOT_CALIBRATED;
            return;
        }
        ThermalSettings settings = thermalSettings().orElse(null);
        MachineDefinition definition = definition().orElse(null);
        if (settings == null || definition == null) {
            pauseReason = PauseReason.THERMAL_DATA_MISSING;
            return;
        }

        for (MachineRecipe recipe : recipes()) {
            if (recipe.inputSlotCount() != 2) {
                continue;
            }
            if (!MachineRecipeRunner.matchesSlot(recipe, SLOT_CHIP, slot(SLOT_CHIP))
                    || !MachineRecipeRunner.matchesSlot(recipe, SLOT_BLANK, slot(SLOT_BLANK))) {
                continue;
            }
            int chipCount = recipe.ingredientCounts().get(SLOT_CHIP);
            int blankCount = recipe.ingredientCounts().get(SLOT_BLANK);
            if (chipCount <= 0 || blankCount <= 0) {
                continue;
            }
            ItemStack product = recipe.assemble();
            if (product.isEmpty() || product.getCount() <= 0) {
                continue;
            }
            int ticks = recipe.ticks();
            if (ticks <= 0) {
                continue;
            }
            int energyPerTick = recipe.energyPerTick(
                    definition.energyPerTick(null, EUVCRAFTMod.settings()));
            if (energyPerTick < 0) {
                continue;
            }
            int scrapCount = Math.min(64,
                    Math.max(0, definition.paramOrDefault("scrap_per_failed_assembly", 1)));
            if (!hasRoom(SLOT_PRODUCT, product)
                    || (scrapCount > 0 && !hasRoom(SLOT_SCRAP, scrapStack(scrapCount)))) {
                // Never pay for a job whose deterministic output possibilities have no room.
                pauseReason = PauseReason.NO_OUTPUT_ROOM;
                return;
            }

            ItemStack originalChip = slot(SLOT_CHIP).copy();
            originalChip.setCount(chipCount);
            ItemStack originalBlank = slot(SLOT_BLANK).copy();
            originalBlank.setCount(blankCount);

            consumeSlot(SLOT_CHIP, chipCount);
            consumeSlot(SLOT_BLANK, blankCount);
            pending = new Job(originalChip, originalBlank, product, scrapCount,
                    energyPerTick, ticks, acceptedPermille, ThreadLocalRandom.current().nextDouble());
            paused = false;
            pauseReason = PauseReason.NONE;
            setChanged();
            return;
        }
        pauseReason = PauseReason.NO_RECIPE;
    }

    private void advance(ServerLevel server) {
        Job job = pending;
        if (job == null) {
            return;
        }
        if (!formation.formed()) {
            paused = true;
            pauseReason = PauseReason.UNFORMED;
            return;
        }
        CleanroomRooms.Sample sample = CleanroomRooms.sample(server, members());
        if (!sample.available()) {
            // Unavailable room pauses before payment; no FE, no progress, no dirty tick.
            paused = true;
            pauseReason = PauseReason.ROOM_UNAVAILABLE;
            return;
        }
        if (!hasRoom(SLOT_PRODUCT, job.product)
                || (job.scrapCount > 0 && !hasRoom(SLOT_SCRAP, scrapStack(job.scrapCount)))) {
            paused = true;
            pauseReason = PauseReason.NO_OUTPUT_ROOM;
            return;
        }
        if (job.energyPerTick > 0 && energySource().draw(job.energyPerTick) < job.energyPerTick) {
            paused = true;
            pauseReason = PauseReason.NO_ENERGY;
            return;
        }

        paused = false;
        pauseReason = PauseReason.NONE;
        job.progress++;
        long yield = Math.max(0, Math.min(1_000_000, sample.yieldPermille()));
        job.roomSum = job.roomSum > Long.MAX_VALUE - yield ? Long.MAX_VALUE : job.roomSum + yield;
        job.paidTicks++;

        ThermalSettings settings = thermalSettings().orElse(null);
        int contaminationPpm = settings == null ? DEFAULT_CONTAMINATION_PPM : settings.contaminationPpm;
        int contaminationTicks = settings == null ? DEFAULT_CONTAMINATION_TICKS : settings.contaminationTicks;
        // "No room" (outside any cleanroom) counts as dirty, as does a room above the ppm limit.
        boolean dirty = sample.room() == null || sample.room().ppm() > contaminationPpm;
        exposure = CalibrationExposure.advance(exposure, dirty, true, contaminationTicks);

        setChanged();
        if (job.progress >= job.ticks) {
            finish(job);
        }
    }

    /** The only place a job produces an item. Called once, from persisted numbers. */
    private void finish(Job job) {
        double averageYield = job.paidTicks > 0 ? (double) job.roomSum / job.paidTicks : 0.0;
        double chance = Math.max(0.0, Math.min(1.0,
                (job.multiplierPermille / 1000.0) * (averageYield / 1000.0)));
        boolean success = job.paidTicks > 0 && job.roll < chance;

        if (success) {
            writeSlot(SLOT_PRODUCT, job.product.copy());
        } else if (job.scrapCount > 0) {
            writeSlot(SLOT_SCRAP, scrapStack(job.scrapCount));
        }
        pending = null;
        paused = false;
        pauseReason = PauseReason.NONE;
        setChanged();
    }

    // ---------------------------------------------------------------- slots

    private static boolean hasRoom(ItemStack current, ItemStack candidate) {
        if (candidate.isEmpty() || candidate.getCount() <= 0) {
            return true;
        }
        if (current.isEmpty()) {
            return candidate.getCount() <= candidate.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(current, candidate)
                && current.getCount() + candidate.getCount() <= current.getMaxStackSize();
    }

    private boolean hasRoom(int index, ItemStack candidate) {
        return hasRoom(slot(index), candidate);
    }

    private void writeSlot(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack current = slot(index);
        if (current.isEmpty()) {
            setSlot(index, stack.copy());
            return;
        }
        ItemStack grown = current.copy();
        grown.setCount(current.getCount() + stack.getCount());
        setSlot(index, grown);
    }

    private void consumeSlot(int index, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack current = slot(index);
        if (current.isEmpty()) {
            return;
        }
        if (current.getCount() <= count) {
            clearSlot(index);
            return;
        }
        ItemStack reduced = current.copy();
        reduced.setCount(current.getCount() - count);
        setSlot(index, reduced);
    }

    private static ItemStack scrapStack(int count) {
        return count <= 0 ? ItemStack.EMPTY : new ItemStack(ReflowMachines.ASSEMBLY_SCRAP.get(), count);
    }

    /** Public status for menus. */
    public boolean inputsLocked() {
        return pending != null;
    }

    public boolean paused() {
        return paused;
    }

    public PauseReason pauseReason() {
        return pauseReason;
    }

    public int jobProgress() {
        return pending == null ? 0 : pending.progress;
    }

    public int jobTicks() {
        return pending == null ? 0 : pending.ticks;
    }

    // ---------------------------------------------------------------- thermal calibration

    public ThermalProfile.Controls controls() {
        return controls;
    }

    public boolean measured() {
        return measured && measuredFingerprint.equals(hardwareFingerprint);
    }

    public int measuredQuality() {
        return measured ? measuredQuality : -1;
    }

    public int measuredProblems() {
        return measured ? measuredProblems : 0;
    }

    public boolean calibrationAccepted() {
        return accepted
                && acceptedPermille > 0
                && acceptedQuality >= 0 && acceptedQuality <= 1000
                && !hardwareFingerprint.isEmpty()
                && hardwareFingerprint.equals(acceptedFingerprint);
    }

    /** Accepted calibration plus an unlatched contamination counter. */
    public boolean readyForBatch() {
        return calibrationAccepted() && !exposure.invalidated();
    }

    public boolean exposureInvalidated() {
        return exposure.invalidated();
    }

    public int exposureDirtyTicks() {
        return exposure.dirtyTicks();
    }

    public int acceptedQuality() {
        return acceptedQuality;
    }

    public int acceptedPermille() {
        return acceptedPermille;
    }

    public int minimumQuality() {
        return thermalSettings().map(settings -> settings.minQuality).orElse(0);
    }

    public int minMultiplierPermille() {
        return thermalSettings().map(settings -> settings.minPermille).orElse(0);
    }

    public int maxMultiplierPermille() {
        return thermalSettings().map(settings -> settings.maxPermille).orElse(0);
    }

    public int minPeakHundredths() {
        return (int) Math.round(thermalSettings().map(settings -> settings.minPeak * 100.0).orElse(0.0));
    }

    public int maxPeakHundredths() {
        return (int) Math.round(thermalSettings().map(settings -> settings.maxPeak * 100.0).orElse(0.0));
    }

    public int testFe() {
        return thermalSettings().map(settings -> settings.testFe).orElse(0);
    }

    public int couponCount() {
        return thermalSettings().map(settings -> settings.couponCount).orElse(0);
    }

    public boolean hasThermalParameters() {
        return thermalSettings().isPresent();
    }

    public boolean couponReady() {
        ThermalSettings settings = thermalSettings().orElse(null);
        ItemStack coupon = slot(SLOT_COUPON);
        return settings != null && !coupon.isEmpty()
                && coupon.is(ReflowMachines.THERMAL_TEST_COUPON.get())
                && coupon.getCount() >= settings.couponCount;
    }

    public boolean energyReadyForTest() {
        ThermalSettings settings = thermalSettings().orElse(null);
        return settings != null && settings.testFe > 0
                && buffer().getAmountAsInt() >= settings.testFe;
    }

    /**
     * Server-paid thermal test. Consumes exactly one test cost and coupon batch only after
     * every precondition passed, then stores the measured assessment of the current controls.
     * The response is stable for the calibration panel installation, so a retest is never a
     * reroll.
     */
    public boolean testThermal() {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return false;
        }
        formation.validate();
        refreshHardware();
        if (!formation.formed() || pending != null) {
            return false;
        }
        ThermalSettings settings = thermalSettings().orElse(null);
        ThermalProfile.Response response = equipmentResponse();
        if (settings == null || response == null) {
            return false;
        }
        ItemStack coupon = slot(SLOT_COUPON);
        if (coupon.isEmpty() || !coupon.is(ReflowMachines.THERMAL_TEST_COUPON.get())
                || coupon.getCount() < settings.couponCount) {
            return false;
        }
        if (settings.testFe <= 0 || buffer().getAmountAsInt() < settings.testFe) {
            return false;
        }
        ThermalProfile.Assessment assessment;
        try {
            assessment = ThermalProfile.assess(controls, response, settings.rules);
        } catch (RuntimeException invalid) {
            return false;
        }
        if (energySource().draw(settings.testFe) != settings.testFe) {
            return false;
        }
        consumeSlot(SLOT_COUPON, settings.couponCount);

        ThermalProfile.Metrics metrics = assessment.metrics();
        measured = true;
        measuredHeater = controls.heater();
        measuredConveyor = controls.conveyor();
        measuredAirflow = controls.airflow();
        measuredRamp = metrics.ramp();
        measuredPeak = metrics.peak();
        measuredDwell = metrics.dwell();
        measuredCooling = metrics.cooling();
        measuredQuality = assessment.quality();
        measuredAcceptable = assessment.acceptable();
        measuredProblems = problemMask(assessment.problems());
        measuredFingerprint = hardwareFingerprint;
        measuredRevision++;
        // A new test needs a new explicit acceptance; this is not a reroll of the response.
        accepted = false;
        acceptedQuality = -1;
        acceptedPermille = 0;
        acceptedFingerprint = "";
        setChanged();
        return true;
    }

    /** Accepts the latest measured controls when they meet the current quality floor. */
    public boolean acceptThermal() {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return false;
        }
        formation.validate();
        refreshHardware();
        if (!formation.formed() || pending != null) {
            return false;
        }
        ThermalSettings settings = thermalSettings().orElse(null);
        if (settings == null || !measured || !measuredFingerprint.equals(hardwareFingerprint)) {
            return false;
        }
        if (!measuredAcceptable || measuredQuality < settings.minQuality) {
            return false;
        }
        if (measuredHeater != controls.heater() || measuredConveyor != controls.conveyor()
                || measuredAirflow != controls.airflow()) {
            return false;
        }
        accepted = true;
        acceptedQuality = measuredQuality;
        acceptedPermille = multiplierPermille(settings, measuredQuality);
        acceptedFingerprint = hardwareFingerprint;
        // Acceptance resets the contamination grace, even when the oven is outside a room.
        exposure = CalibrationExposure.recalibrated();
        setChanged();
        return true;
    }

    /** Whether the current measured curve may be accepted right now, for the button state. */
    public boolean acceptReady() {
        ThermalSettings settings = thermalSettings().orElse(null);
        return pending == null && settings != null && measured
                && measuredFingerprint.equals(hardwareFingerprint)
                && measuredAcceptable && measuredQuality >= settings.minQuality
                && measuredHeater == controls.heater()
                && measuredConveyor == controls.conveyor()
                && measuredAirflow == controls.airflow();
    }

    /**
     * Menu click ids 0..5 are the six +/- control buttons. Editing controls invalidates an
     * accepted calibration but never rerolls the measured response.
     */
    public boolean adjustThermalControl(int id) {
        if (level == null || level.isClientSide() || pending != null || id < 0 || id > 5) {
            return false;
        }
        int heater = controls.heater();
        int conveyor = controls.conveyor();
        int airflow = controls.airflow();
        switch (id) {
            case 0 -> heater = Math.max(0, heater - 1);
            case 1 -> heater = Math.min(100, heater + 1);
            case 2 -> conveyor = Math.max(0, conveyor - 1);
            case 3 -> conveyor = Math.min(100, conveyor + 1);
            case 4 -> airflow = Math.max(0, airflow - 1);
            case 5 -> airflow = Math.min(100, airflow + 1);
            default -> {
                return false;
            }
        }
        try {
            controls = new ThermalProfile.Controls(heater, conveyor, airflow);
        } catch (RuntimeException invalid) {
            return false;
        }
        controlRevision++;
        accepted = false;
        acceptedQuality = -1;
        acceptedPermille = 0;
        acceptedFingerprint = "";
        setChanged();
        return true;
    }

    /** Live preview curve, nominal response, no cost and no randomness. */
    public double[] previewCurve() {
        if (previewCache == null || previewCacheRevision != controlRevision) {
            previewCache = safeCurve(controls, ThermalProfile.Response.nominal());
            previewCacheRevision = controlRevision;
        }
        return previewCache;
    }

    /** Measured curve, or null until a paid test exists for the current installation. */
    public @Nullable double[] measuredCurve() {
        if (!measured || !measuredFingerprint.equals(hardwareFingerprint)) {
            return null;
        }
        if (measuredCache == null || measuredCacheRevision != measuredRevision) {
            ThermalProfile.Response response = equipmentResponse();
            if (response == null) {
                return null;
            }
            try {
                ThermalProfile.Controls tested = new ThermalProfile.Controls(
                        measuredHeater, measuredConveyor, measuredAirflow);
                measuredCache = safeCurve(tested, response);
            } catch (RuntimeException invalid) {
                return null;
            }
            measuredCacheRevision = measuredRevision;
        }
        return measuredCache;
    }

    public double measuredRamp() {
        return measuredRamp;
    }

    public double measuredPeak() {
        return measuredPeak;
    }

    public double measuredDwell() {
        return measuredDwell;
    }

    public double measuredCooling() {
        return measuredCooling;
    }

    public int measuredHeater() {
        return measuredHeater;
    }

    public int measuredConveyor() {
        return measuredConveyor;
    }

    public int measuredAirflow() {
        return measuredAirflow;
    }

    public boolean measuredAcceptable() {
        return measuredAcceptable;
    }

    private double[] safeCurve(ThermalProfile.Controls tested, ThermalProfile.Response response) {
        double[] curve = new double[GRAPH_SAMPLES];
        try {
            double[] sampled = ThermalProfile.curve(tested, response, GRAPH_SAMPLES);
            for (int i = 0; i < curve.length; i++) {
                double value = sampled[i];
                curve[i] = Double.isFinite(value) ? value : 0.0;
            }
        } catch (RuntimeException invalid) {
            // Leave a zero curve; the UI shows the missing-data state separately.
        }
        return curve;
    }

    private static int problemMask(Set<ThermalProfile.Problem> problems) {
        int mask = 0;
        for (ThermalProfile.Problem problem : problems) {
            mask |= switch (problem) {
                case HEATING -> 1;
                case PEAK -> 2;
                case DWELL -> 4;
                case COOLING -> 8;
            };
        }
        return mask;
    }

    private static int multiplierPermille(ThermalSettings settings, int quality) {
        int clamped = Math.max(0, Math.min(1000, quality));
        return settings.minPermille
                + (settings.maxPermille - settings.minPermille) * clamped / 1000;
    }

    // ---------------------------------------------------------------- hardware fingerprint

    private void refreshHardware() {
        if (level == null || level.isClientSide() || !formation.formed()) {
            return;
        }
        String next = calibrationFingerprint();
        if (next.equals(hardwareFingerprint)) {
            return;
        }
        boolean hadIdentity = !hardwareFingerprint.isEmpty();
        hardwareFingerprint = next;
        if (hadIdentity) {
            // A different physical calibration panel is different equipment: retune required.
            accepted = false;
            acceptedQuality = -1;
            acceptedPermille = 0;
            acceptedFingerprint = "";
            measured = false;
            measuredFingerprint = "";
            measuredRevision++;
            measuredCache = null;
            setChanged();
        } else if (!acceptedFingerprint.isEmpty() && !acceptedFingerprint.equals(hardwareFingerprint)) {
            // Restored acceptance belongs to another installation.
            accepted = false;
            acceptedQuality = -1;
            acceptedPermille = 0;
            acceptedFingerprint = "";
            setChanged();
        }
    }

    private String calibrationFingerprint() {
        ReflowMemberBlockEntity panel = calibrationPanel();
        if (panel == null) {
            return "";
        }
        try {
            return panel.installation().toString();
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    private @Nullable ReflowMemberBlockEntity calibrationPanel() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        for (BlockPos pos : members()) {
            if (!server.hasChunkAt(pos)) {
                continue;
            }
            if (server.getBlockEntity(pos) instanceof ReflowMemberBlockEntity member
                    && member.structureKind() == BlockKind.CALIBRATION_PANEL) {
                return member;
            }
        }
        return null;
    }

    private ThermalProfile.@Nullable Response equipmentResponse() {
        if (hardwareFingerprint.isEmpty()) {
            return null;
        }
        try {
            UUID installation = UUID.fromString(hardwareFingerprint);
            return ThermalProfile.response(
                    installation.getMostSignificantBits() ^ installation.getLeastSignificantBits());
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    // ---------------------------------------------------------------- thermal data

    /** Parsed thermal_* machine parameters. Empty when any required value is missing or invalid. */
    public Optional<ThermalSettings> thermalSettings() {
        MachineDefinition definition = definition().orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        if (definition != settingsDefinition) {
            settingsDefinition = definition;
            settingsCache = parseThermalSettings(definition);
        }
        return Optional.ofNullable(settingsCache);
    }

    private static @Nullable ThermalSettings parseThermalSettings(MachineDefinition definition) {
        Integer minRamp = definition.param("thermal_min_ramp").orElse(null);
        Integer maxRamp = definition.param("thermal_max_ramp").orElse(null);
        Integer minPeak = definition.param("thermal_min_peak").orElse(null);
        Integer maxPeak = definition.param("thermal_max_peak").orElse(null);
        Integer minDwell = definition.param("thermal_min_dwell").orElse(null);
        Integer maxDwell = definition.param("thermal_max_dwell").orElse(null);
        Integer minCooling = definition.param("thermal_min_cooling").orElse(null);
        Integer maxCooling = definition.param("thermal_max_cooling").orElse(null);
        Integer minQuality = definition.param("thermal_min_quality").orElse(null);
        Integer testFe = definition.param("thermal_test_fe").orElse(null);
        Integer couponCount = definition.param("thermal_test_coupon_count").orElse(null);
        Integer minPermille = definition.param("thermal_min_multiplier_permille").orElse(null);
        Integer maxPermille = definition.param("thermal_max_multiplier_permille").orElse(null);
        if (minRamp == null || maxRamp == null || minPeak == null || maxPeak == null
                || minDwell == null || maxDwell == null || minCooling == null || maxCooling == null
                || minQuality == null || testFe == null || couponCount == null
                || minPermille == null || maxPermille == null) {
            return null;
        }
        if (testFe <= 0 || couponCount <= 0 || minQuality < 0 || minQuality > 1000
                || minPermille <= 0 || maxPermille < minPermille || maxPermille > 1000) {
            return null;
        }

        double rampMin = minRamp / 100.0;
        double rampMax = maxRamp / 100.0;
        double peakMin = minPeak / 100.0;
        double peakMax = maxPeak / 100.0;
        double dwellMin = minDwell / 100.0;
        double dwellMax = maxDwell / 100.0;
        double coolingMin = minCooling / 100.0;
        double coolingMax = maxCooling / 100.0;
        if (!Double.isFinite(rampMin) || !Double.isFinite(rampMax)
                || !Double.isFinite(peakMin) || !Double.isFinite(peakMax)
                || !Double.isFinite(dwellMin) || !Double.isFinite(dwellMax)
                || !Double.isFinite(coolingMin) || !Double.isFinite(coolingMax)) {
            return null;
        }
        if (rampMin <= 0 || rampMax <= rampMin
                || peakMin <= 0 || peakMax <= peakMin
                || dwellMin <= 0 || dwellMax <= dwellMin
                || coolingMin < 0 || coolingMax <= coolingMin) {
            return null;
        }

        ThermalProfile.Rules rules;
        try {
            rules = new ThermalProfile.Rules(rampMin, rampMax, peakMin, peakMax,
                    dwellMin, dwellMax, coolingMin, coolingMax, minQuality);
        } catch (RuntimeException invalid) {
            return null;
        }

        int contaminationPpm = definition.paramOrDefault("contamination_ppm",
                definition.paramOrDefault("contamination_ppm", DEFAULT_CONTAMINATION_PPM));
        int contaminationTicks = definition.paramOrDefault("contamination_ticks",
                definition.paramOrDefault("contamination_ticks", DEFAULT_CONTAMINATION_TICKS));
        if (contaminationPpm < 0) {
            contaminationPpm = DEFAULT_CONTAMINATION_PPM;
        }
        if (contaminationTicks <= 0) {
            contaminationTicks = DEFAULT_CONTAMINATION_TICKS;
        }

        return new ThermalSettings(rules, minQuality, testFe, couponCount,
                minPermille, maxPermille, contaminationPpm, contaminationTicks,
                rampMin, rampMax, peakMin, peakMax, dwellMin, dwellMax, coolingMin, coolingMax);
    }

    /** Parsed data shared by admission, test and the calibration menu. */
    public record ThermalSettings(
            ThermalProfile.Rules rules,
            int minQuality,
            int testFe,
            int couponCount,
            int minPermille,
            int maxPermille,
            int contaminationPpm,
            int contaminationTicks,
            double minRamp,
            double maxRamp,
            double minPeak,
            double maxPeak,
            double minDwell,
            double maxDwell,
            double minCooling,
            double maxCooling
    ) {}

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        int heater = Math.max(0, Math.min(100, input.getIntOr("reflowHeater", NOMINAL_CONTROL)));
        int conveyor = Math.max(0, Math.min(100, input.getIntOr("reflowConveyor", NOMINAL_CONTROL)));
        int airflow = Math.max(0, Math.min(100, input.getIntOr("reflowAirflow", NOMINAL_CONTROL)));
        try {
            controls = new ThermalProfile.Controls(heater, conveyor, airflow);
        } catch (RuntimeException invalid) {
            controls = new ThermalProfile.Controls(NOMINAL_CONTROL, NOMINAL_CONTROL, NOMINAL_CONTROL);
        }
        controlRevision++;
        previewCache = null;
        previewCacheRevision = -1;

        exposure = new CalibrationExposure.State(
                Math.max(0, input.getIntOr("reflowDirtyTicks", 0)),
                input.getBooleanOr("reflowInvalidated", false));

        hardwareFingerprint = input.getString("reflowHardware").orElse("");
        measured = input.getBooleanOr("reflowMeasured", false);
        measuredHeater = Math.max(0, Math.min(100, input.getIntOr("reflowMeasuredHeater", NOMINAL_CONTROL)));
        measuredConveyor = Math.max(0, Math.min(100, input.getIntOr("reflowMeasuredConveyor", NOMINAL_CONTROL)));
        measuredAirflow = Math.max(0, Math.min(100, input.getIntOr("reflowMeasuredAirflow", NOMINAL_CONTROL)));
        measuredRamp = input.getDoubleOr("reflowMeasuredRamp", 0);
        measuredPeak = input.getDoubleOr("reflowMeasuredPeak", 0);
        measuredDwell = input.getDoubleOr("reflowMeasuredDwell", 0);
        measuredCooling = input.getDoubleOr("reflowMeasuredCooling", 0);
        measuredQuality = input.getIntOr("reflowMeasuredQuality", -1);
        measuredAcceptable = input.getBooleanOr("reflowMeasuredAcceptable", false);
        measuredProblems = input.getIntOr("reflowMeasuredProblems", 0);
        measuredFingerprint = input.getString("reflowMeasuredHardware").orElse("");
        measuredRevision++;
        measuredCache = null;
        measuredCacheRevision = -1;

        accepted = input.getBooleanOr("reflowAccepted", false);
        acceptedQuality = input.getIntOr("reflowAcceptedQuality", -1);
        acceptedPermille = input.getIntOr("reflowAcceptedPermille", 0);
        acceptedFingerprint = input.getString("reflowAcceptedHardware").orElse("");

        for (int i = 0; i < recoveredOverflow.size(); i++) {
            recoveredOverflow.set(i, input.read("reflowOverflow" + i, ItemStack.OPTIONAL_CODEC)
                    .orElse(ItemStack.EMPTY));
        }

        readPending(input);
        paused = false;
        pauseReason = PauseReason.NONE;
        settingsDefinition = null;
        settingsCache = null;
        syncData();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("reflowHeater", controls.heater());
        output.putInt("reflowConveyor", controls.conveyor());
        output.putInt("reflowAirflow", controls.airflow());
        output.putInt("reflowDirtyTicks", exposure.dirtyTicks());
        output.putBoolean("reflowInvalidated", exposure.invalidated());
        output.putString("reflowHardware", hardwareFingerprint);

        output.putBoolean("reflowMeasured", measured);
        output.putInt("reflowMeasuredHeater", measuredHeater);
        output.putInt("reflowMeasuredConveyor", measuredConveyor);
        output.putInt("reflowMeasuredAirflow", measuredAirflow);
        output.putDouble("reflowMeasuredRamp", measuredRamp);
        output.putDouble("reflowMeasuredPeak", measuredPeak);
        output.putDouble("reflowMeasuredDwell", measuredDwell);
        output.putDouble("reflowMeasuredCooling", measuredCooling);
        output.putInt("reflowMeasuredQuality", measuredQuality);
        output.putBoolean("reflowMeasuredAcceptable", measuredAcceptable);
        output.putInt("reflowMeasuredProblems", measuredProblems);
        output.putString("reflowMeasuredHardware", measuredFingerprint);

        output.putBoolean("reflowAccepted", accepted);
        output.putInt("reflowAcceptedQuality", acceptedQuality);
        output.putInt("reflowAcceptedPermille", acceptedPermille);
        output.putString("reflowAcceptedHardware", acceptedFingerprint);

        Job job = pending;
        output.putBoolean("reflowPending", job != null);
        if (job != null) {
            // Private admitted payload: ItemStack codecs, never exposed through the menu.
            output.store("reflowOriginal0", ItemStack.OPTIONAL_CODEC, job.original0);
            output.store("reflowOriginal1", ItemStack.OPTIONAL_CODEC, job.original1);
            output.store("reflowProduct", ItemStack.OPTIONAL_CODEC, job.product);
            output.putInt("reflowEnergyPerTick", job.energyPerTick);
            output.putInt("reflowTicks", job.ticks);
            output.putInt("reflowProgress", job.progress);
            output.putInt("reflowPermille", job.multiplierPermille);
            output.putDouble("reflowRoll", job.roll);
            output.putDouble("reflowRoomSum", (double) job.roomSum);
            output.putInt("reflowPaidTicks", (int) Math.min(Integer.MAX_VALUE, job.paidTicks));
            output.putInt("reflowScrap", job.scrapCount);
        }
        for (int i = 0; i < recoveredOverflow.size(); i++) {
            output.store("reflowOverflow" + i, ItemStack.OPTIONAL_CODEC, recoveredOverflow.get(i));
        }
    }

    private void readPending(ValueInput input) {
        boolean declared = input.getBooleanOr("reflowPending", false);
        ItemStack original0 = input.read("reflowOriginal0", ItemStack.OPTIONAL_CODEC)
                .orElse(ItemStack.EMPTY);
        ItemStack original1 = input.read("reflowOriginal1", ItemStack.OPTIONAL_CODEC)
                .orElse(ItemStack.EMPTY);
        ItemStack product = input.read("reflowProduct", ItemStack.OPTIONAL_CODEC)
                .orElse(ItemStack.EMPTY);
        boolean hasPayload = declared || !original0.isEmpty() || !original1.isEmpty()
                || !product.isEmpty();
        if (!hasPayload) {
            pending = null;
            return;
        }

        int energyPerTick = input.getIntOr("reflowEnergyPerTick", -1);
        int ticks = input.getIntOr("reflowTicks", 0);
        int progress = input.getIntOr("reflowProgress", -1);
        int permille = input.getIntOr("reflowPermille", -1);
        double roll = input.getDoubleOr("reflowRoll", -1);
        double roomSum = input.getDoubleOr("reflowRoomSum", -1);
        int paidTicks = input.getIntOr("reflowPaidTicks", -1);
        int scrapCount = input.getIntOr("reflowScrap", -1);

        boolean valid = !original0.isEmpty() && !original1.isEmpty() && !product.isEmpty()
                && energyPerTick >= 0 && ticks > 0 && progress >= 0 && progress < ticks
                && permille > 0 && permille <= 1000
                && Double.isFinite(roll) && roll >= 0 && roll < 1
                && Double.isFinite(roomSum) && roomSum >= 0
                && roomSum <= (double) progress * 1_000_000.0 + 1.0
                && paidTicks == progress
                && scrapCount >= 0 && scrapCount <= 64;
        if (!valid) {
            // A malformed admitted payload must return what it can of the original inputs and
            // must never produce a product or scrap.
            recoverOriginals(original0, original1);
            pending = null;
            setChanged();
            return;
        }
        pending = new Job(original0, original1, product, scrapCount, energyPerTick, ticks,
                permille, roll);
        pending.progress = progress;
        pending.roomSum = Math.round(roomSum);
        pending.paidTicks = paidTicks;
    }

    private void recoverOriginals(ItemStack original0, ItemStack original1) {
        restoreOrOverflow(SLOT_CHIP, original0);
        restoreOrOverflow(SLOT_BLANK, original1);
    }

    private void restoreOrOverflow(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack current = slot(index);
        if (current.isEmpty()) {
            setSlot(index, stack.copy());
            return;
        }
        if (ItemStack.isSameItemSameComponents(current, stack)
                && current.getCount() + stack.getCount() <= current.getMaxStackSize()) {
            ItemStack grown = current.copy();
            grown.setCount(current.getCount() + stack.getCount());
            setSlot(index, grown);
            return;
        }
        for (int i = 0; i < recoveredOverflow.size(); i++) {
            if (recoveredOverflow.get(i).isEmpty()) {
                recoveredOverflow.set(i, stack.copy());
                setChanged();
                return;
            }
        }
        // Every overflow slot is occupied and the input slot cannot take this stack. Keep it
        // in any empty machine slot rather than overwriting an earned output or deleting it.
        for (int i = 0; i < slotCount(); i++) {
            if (slot(i).isEmpty()) {
                setSlot(i, stack.copy());
                return;
            }
        }
        // A hand-edited save with no free slot anywhere: keeping the stack in slot zero is the
        // least destructive remaining option; it is still never turned into product or scrap.
        setSlot(SLOT_CHIP, stack.copy());
    }

    @Override
    protected void dropExtraContents(@Nullable Level level, BlockPos pos) {
        List<ItemStack> drops = new ArrayList<>();
        Job job = pending;
        if (job != null) {
            if (!job.original0.isEmpty()) {
                drops.add(job.original0.copy());
            }
            if (!job.original1.isEmpty()) {
                drops.add(job.original1.copy());
            }
        }
        for (ItemStack stack : recoveredOverflow) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        if (!drops.isEmpty()) {
            SimpleContainer container = new SimpleContainer(drops.size());
            for (int i = 0; i < drops.size(); i++) {
                container.setItem(i, drops.get(i));
            }
            dropContainer(level, pos, container);
        }
        if (level != null && !level.isClientSide()) {
            // The block is being removed, not unloaded: return the admitted originals once.
            pending = null;
            for (int i = 0; i < recoveredOverflow.size(); i++) {
                recoveredOverflow.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod.reflow_oven");
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
            FabMachineBlockEntity machine) {
        return new ReflowControllerMenu(containerId, playerInventory, this, worldPosition);
    }

    // ---------------------------------------------------------------- inner types

    /** Why a pending or idle oven is not progressing. */
    public enum PauseReason {
        NONE,
        UNFORMED,
        NO_DEFINITION,
        NOT_CALIBRATED,
        CALIBRATION_INVALIDATED,
        THERMAL_DATA_MISSING,
        NO_RECIPE,
        ROOM_UNAVAILABLE,
        NO_OUTPUT_ROOM,
        NO_ENERGY
    }

    /** The private, frozen admitted payload. Only the server mutates progress and yield. */
    private static final class Job {
        final ItemStack original0;
        final ItemStack original1;
        final ItemStack product;
        final int scrapCount;
        final int energyPerTick;
        final int ticks;
        final int multiplierPermille;
        final double roll;
        int progress;
        long roomSum;
        long paidTicks;

        Job(ItemStack original0, ItemStack original1, ItemStack product, int scrapCount,
                int energyPerTick, int ticks, int multiplierPermille, double roll) {
            this.original0 = original0;
            this.original1 = original1;
            this.product = product;
            this.scrapCount = scrapCount;
            this.energyPerTick = energyPerTick;
            this.ticks = ticks;
            this.multiplierPermille = multiplierPermille;
            this.roll = roll;
        }
    }
}
