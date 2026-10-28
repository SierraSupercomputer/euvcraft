package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.euvcraft.lithmod.core.t7.G3Preparation;

/**
 * The immutable contents of one sealed lot: a {@link UUID} lot id, a capacity, the
 * ordered list of {@link Wafer}s inside it and the {@link RunSpec} it was processed
 * under.
 *
 * <p>The wafer order is the slot order inside the FOUP and is stable across saving,
 * loading and inventory moves, because the wafer index is part of the roll context
 * the yield engine draws from (see {@link ContextualRng}). A wafer is never a loose
 * item; this record is the only way one exists.</p>
 *
 * <p>The record is immutable: the wafer list is copied on construction and every
 * {@code with...} method returns a new instance. The hidden {@link Wafer#cumulativeRoll()},
 * {@link Wafer#flags()} and {@link RunSpec} values are reachable from the accessors, so
 * this type must only ever be used on the server. What may leave the server is
 * {@link #publicView()}.</p>
 *
 * <p>The run spec is {@code null} until the lot takes its first layer, which is what
 * "not started" means: a fresh lot has none, and a lot that has been through at least
 * one layer always has one.</p>
 *
 * <p>Every lot carries a {@link WaferSubstrate} grade (ORDINARY, PURE or
 * ELECTRONIC_GRADE) that is set when the lot is created and preserved through every
 * mutation. Existing saved lots without a grade are treated as ORDINARY.</p>
 *
 * <p>Every lot carries a preparation generation (1 = legacy/unprepared/G1, 2 = G2
 * prepared, 3 = G3 prepared). This is a separate property from the substrate grade.
 * Existing saved lots without a preparation generation are treated as generation 1.
 * A G2 lot must be non-empty and PURE-or-better. A G3 lot must be non-empty,
 * ELECTRONIC_GRADE-or-better and carry exactly one {@link G3Preparation} evidence
 * object: either the exact per-wafer factors for a server lot or the redacted
 * display marker for a client view. Applying preparation requires unstarted wafers
 * at layer 0; subsequent lithography retains that preparation. Preparation is
 * preserved through mutations, except that adding ANY fresh wafer(s) to a prepared
 * lot refuses mixing, and removing all wafers resets generation to 1 and clears the
 * evidence so an empty carrier cannot transfer preparation.</p>
 *
 * <p>G3 evidence is frozen once by {@link #preparedG3(int, int[])}. A second
 * preparation attempt, a fresh-wafer reinsertion and a reload cannot reroll,
 * upgrade or erase the saved factors. The factors are separate from
 * {@link Wafer#cumulativeRoll()}; packaging integration later applies them once.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param lotId                server-assigned identifier of the sealed lot
 * @param capacity             maximum number of wafers this lot holds, {@code >= 1}
 * @param wafers               wafers in the lot, in slot order, at most {@code capacity} entries
 * @param run                  the parameters the lot was processed under, or {@code null} when it
 *                             has not been started
 * @param grade                the wafer substrate grade of this lot, never null
 * @param preparationGeneration the preparation generation: 1 = legacy/unprepared, 2 = G2, 3 = G3
 * @param roomExposure         accumulated paid cleanroom exposure samples
 * @param roomYieldPermille    frozen room yield factor, {@code 0} until frozen
 * @param g3Preparation        hidden G3 preparation evidence, never null
 */
public record FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade,
                           int preparationGeneration,
                           com.euvcraft.lithmod.core.cleanroom.CleanroomExposure roomExposure,
                           int roomYieldPermille, G3Preparation g3Preparation) {

    /** Source compatibility for the original grade/preparation constructor. */
    public FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run,
                        WaferSubstrate grade, int preparationGeneration) {
        this(lotId, capacity, wafers, run, grade, preparationGeneration,
            com.euvcraft.lithmod.core.cleanroom.CleanroomExposure.empty(), 0, G3Preparation.none());
    }

    /**
     * Backward-compatible eight-argument constructor for pre-G3 call sites.
     * The lot carries no G3 evidence.
     */
    public FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade,
                        int preparationGeneration,
                        com.euvcraft.lithmod.core.cleanroom.CleanroomExposure roomExposure,
                        int roomYieldPermille) {
        this(lotId, capacity, wafers, run, grade, preparationGeneration, roomExposure, roomYieldPermille,
            G3Preparation.none());
    }

    public FoupContents withRoomExposure(com.euvcraft.lithmod.core.cleanroom.CleanroomExposure exposure, int frozenPermille) {
        return new FoupContents(lotId,capacity,wafers,run,grade,preparationGeneration,exposure,frozenPermille,g3Preparation);
    }

    /** Saved/source compatibility with pre-T4 lots. */
    public FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade) {
        this(lotId, capacity, wafers, run, grade, PREPARATION_LEGACY);
    }

    /** The legacy preparation generation value (unprepared/G1). */
    public static final int PREPARATION_LEGACY = 1;

    /** The G2 prepared generation value. */
    public static final int PREPARATION_G2 = 2;

    /** The G3 prepared generation value. */
    public static final int PREPARATION_G3 = 3;

    /**
     * Creates a lot that has not been started, which is the common case for a fresh FOUP.
     * Uses legacy preparation generation (1).
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers this lot holds, {@code >= 1}
     * @param wafers   wafers in the lot, in slot order, at most {@code capacity} entries
     * @throws NullPointerException     if {@code lotId} or {@code wafers} is null, or
     *                                  the wafer list holds a null entry
     * @throws IllegalArgumentException if the capacity is below one or the lot holds
     *                                  more wafers than its capacity
     */
    public FoupContents(UUID lotId, int capacity, List<Wafer> wafers) {
        this(lotId, capacity, wafers, null, WaferSubstrate.ORDINARY, PREPARATION_LEGACY);
    }

    /**
     * Legacy constructor that creates a lot with a run spec but default ORDINARY grade
     * and legacy preparation generation (1). Kept for source compatibility.
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers this lot holds, {@code >= 1}
     * @param wafers   wafers in the lot, in slot order, at most {@code capacity} entries
     * @param run      the run spec, or {@code null} when not started
     * @throws NullPointerException     if {@code lotId} or {@code wafers} is null, or
     *                                  the wafer list holds a null entry
     * @throws IllegalArgumentException if the capacity is below one or the lot holds
     *                                  more wafers than its capacity
     */
    public FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run) {
        this(lotId, capacity, wafers, run, WaferSubstrate.ORDINARY, PREPARATION_LEGACY);
    }

    /**
     * Creates a lot that has not been started, with an explicit substrate grade.
     * Uses legacy preparation generation (1).
     * Static factory to avoid ambiguity with the legacy 4-arg (RunSpec) constructor.
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers this lot holds, {@code >= 1}
     * @param wafers   wafers in the lot, in slot order, at most {@code capacity} entries
     * @param grade    the wafer substrate grade, not null
     * @return a new lot with the given grade, no run spec
     * @throws NullPointerException     if {@code lotId}, {@code wafers} or {@code grade} is null, or
     *                                  the wafer list holds a null entry
     * @throws IllegalArgumentException if the capacity is below one or the lot holds
     *                                  more wafers than its capacity
     */
    public static FoupContents of(UUID lotId, int capacity, List<Wafer> wafers, WaferSubstrate grade) {
        return new FoupContents(lotId, capacity, wafers, null, grade, PREPARATION_LEGACY);
    }

    /**
     * Validates the lot and makes the wafer list immutable.
     *
     * @throws NullPointerException     if {@code lotId}, {@code wafers}, {@code grade},
     *                                  {@code roomExposure} or {@code g3Preparation}
     *                                  is null, or the wafer list holds a null entry
     * @throws IllegalArgumentException if the capacity is below one, the lot holds
     *                                  more wafers than its capacity, the preparation
     *                                  generation is not 1, 2 or 3, or the preparation
     *                                  evidence does not match the generation/grade
     */
    public FoupContents {
        Objects.requireNonNull(roomExposure, "roomExposure");
        Objects.requireNonNull(g3Preparation, "g3Preparation must not be null");
        if(roomYieldPermille<0||roomYieldPermille>1000)throw new IllegalArgumentException("Invalid room yield");
        if(wafers.isEmpty()&&(roomExposure.samples()!=0||roomYieldPermille!=0))throw new IllegalArgumentException("Empty carrier cannot retain room exposure");
        Objects.requireNonNull(lotId, "lotId must not be null");
        Objects.requireNonNull(wafers, "wafers must not be null");
        Objects.requireNonNull(grade, "grade must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        if (preparationGeneration < PREPARATION_LEGACY || preparationGeneration > PREPARATION_G3) {
            throw new IllegalArgumentException("preparationGeneration must be " + PREPARATION_LEGACY + ", "
                + PREPARATION_G2 + " or " + PREPARATION_G3 + ", got " + preparationGeneration);
        }
        wafers = List.copyOf(wafers);
        if (preparationGeneration == PREPARATION_G2
            && (wafers.isEmpty() || grade.generation() < WaferSubstrate.PURE.generation())) {
            throw new IllegalArgumentException("G2 preparation requires nonempty pure-or-better wafers");
        }
        if (preparationGeneration == PREPARATION_G3) {
            if (wafers.isEmpty()) {
                throw new IllegalArgumentException("G3 preparation requires a nonempty lot");
            }
            if (grade.generation() < WaferSubstrate.ELECTRONIC_GRADE.generation()) {
                throw new IllegalArgumentException("G3 preparation requires electronic-grade-or-better wafers");
            }
            if (!g3Preparation.isPrepared()) {
                throw new IllegalArgumentException("G3 preparation requires committed preparation evidence");
            }
            if (g3Preparation.hasFactors() && g3Preparation.factorCount() != wafers.size()) {
                throw new IllegalArgumentException("G3 preparation evidence holds " + g3Preparation.factorCount()
                    + " factors for " + wafers.size() + " wafers");
            }
        } else if (g3Preparation.isPrepared()) {
            throw new IllegalArgumentException("only a G3 lot can carry G3 preparation evidence");
        }
        if (wafers.size() > capacity) {
            throw new IllegalArgumentException(
                "a lot of capacity " + capacity + " cannot hold " + wafers.size() + " wafers");
        }
    }

    /**
     * Creates an empty lot with ordinary substrate and legacy preparation generation (1).
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers the lot holds
     * @return a lot with no wafers in it
     */
    public static FoupContents empty(UUID lotId, int capacity) {
        return new FoupContents(lotId, capacity, List.of(), null, WaferSubstrate.ORDINARY, PREPARATION_LEGACY);
    }

    /**
     * Creates an empty lot with the given substrate grade and legacy preparation generation (1).
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers the lot holds
     * @param grade    the wafer substrate grade, not null
     * @return a lot with no wafers in it
     */
    public static FoupContents empty(UUID lotId, int capacity, WaferSubstrate grade) {
        Objects.requireNonNull(grade, "grade must not be null");
        return new FoupContents(lotId, capacity, List.of(), null, grade, PREPARATION_LEGACY);
    }

    /**
     * Returns the number of wafers in the lot.
     *
     * @return the wafer count
     */
    public int size() {
        return this.wafers.size();
    }

    /**
     * Returns whether the lot holds no wafers.
     *
     * @return {@code true} if the wafer list is empty
     */
    public boolean isEmpty() {
        return this.wafers.isEmpty();
    }

    /**
     * Returns whether no further wafer fits.
     *
     * @return {@code true} if the lot is at capacity
     */
    public boolean isFull() {
        return this.wafers.size() >= this.capacity;
    }

    /** Returns whether this lot has any preparation (G2 or G3). */
    public boolean isPrepared() {
        return this.preparationGeneration != PREPARATION_LEGACY;
    }

    /** Returns whether this lot carries a G3 preparation generation. */
    public boolean isG3Prepared() {
        return this.preparationGeneration == PREPARATION_G3;
    }

    /**
     * Returns a copy of this lot with one more wafer appended.
     * Preserves preparation generation, except refuses to add ANY wafer to a prepared lot (G2 or G3).
     *
     * @param wafer the wafer to add, not null
     * @return a new lot with the wafer appended, carrying the same run spec, grade and preparation generation
     * @throws IllegalStateException if the lot is already full, or if this is a prepared lot
     */
    public FoupContents withWafer(Wafer wafer) {
        Objects.requireNonNull(wafer, "wafer must not be null");
        if (isFull()) {
            throw new IllegalStateException("the lot is already full (" + this.capacity + " wafers)");
        }
        if (isPrepared()) {
            throw new IllegalStateException("cannot add wafers to a prepared lot (generation " + this.preparationGeneration + ")");
        }
        List<Wafer> grown = new ArrayList<>(this.wafers);
        grown.add(wafer);
        return new FoupContents(this.lotId, this.capacity, grown, this.run, this.grade, this.preparationGeneration, this.roomExposure, this.roomYieldPermille, this.g3Preparation);
    }

    /**
     * Returns a copy of this lot with the given wafers appended.
     * Preserves preparation generation, except refuses to add ANY wafers to a prepared lot (G2 or G3).
     *
     * @param newWafers the wafers to add, no null entries
     * @return a new lot with the wafers appended, carrying the same run spec, grade and preparation generation
     * @throws IllegalStateException if the result would exceed the capacity, or if this is a prepared lot
     */
    public FoupContents withWafers(List<Wafer> newWafers) {
        Objects.requireNonNull(newWafers, "newWafers must not be null");
        if (isPrepared()) {
            throw new IllegalStateException("cannot add wafers to a prepared lot (generation " + this.preparationGeneration + ")");
        }
        if (newWafers.size() > this.capacity - this.wafers.size()) {
            throw new IllegalStateException("cannot add " + newWafers.size() + " wafers to a lot holding "
                + this.wafers.size() + " of " + this.capacity);
        }
        List<Wafer> grown = new ArrayList<>(this.wafers);
        grown.addAll(newWafers);
        return new FoupContents(this.lotId, this.capacity, grown, this.run, this.grade, this.preparationGeneration, this.roomExposure, this.roomYieldPermille, this.g3Preparation);
    }

    /**
     * Returns a copy of this lot without the wafer at the given slot.
     * Preserves preparation generation and the corresponding G3 factor, except if removing
     * the last wafer resets generation to 1 and clears the evidence so an empty carrier
     * cannot transfer preparation.
     *
     * @param index slot to clear, {@code 0 <= index < size()}
     * @return a new lot with that wafer removed, carrying the same run spec and grade
     * @throws IndexOutOfBoundsException if the index is not a slot of this lot
     */
    public FoupContents withoutWaferAt(int index) {
        if (index < 0 || index >= this.wafers.size()) {
            throw new IndexOutOfBoundsException(
                "slot " + index + " is not in a lot holding " + this.wafers.size() + " wafers");
        }
        List<Wafer> shrunk = new ArrayList<>(this.wafers);
        shrunk.remove(index);
        int newPreparation = this.preparationGeneration;
        G3Preparation newEvidence = this.g3Preparation;
        if (shrunk.isEmpty()) {
            newPreparation = PREPARATION_LEGACY;
            newEvidence = G3Preparation.none();
        } else if (this.g3Preparation.hasFactors()) {
            // Drop the factor belonging to the removed wafer only; never recompute the rest.
            newEvidence = this.g3Preparation.withoutFactorAt(index);
        }
        return new FoupContents(this.lotId, this.capacity, shrunk, this.run, this.grade, newPreparation, shrunk.isEmpty()?com.euvcraft.lithmod.core.cleanroom.CleanroomExposure.empty():roomExposure, shrunk.isEmpty()?0:roomYieldPermille, newEvidence);
    }

    /**
     * Returns whether the lot has been through at least one layer, which is the same
     * thing as carrying a run spec.
     *
     * @return {@code true} if the lot carries a {@link RunSpec}
     */
    public boolean isStarted() {
        return this.run != null;
    }

    /**
     * Returns a copy of this lot carrying the given run spec.
     *
     * <p>Only the yield engine calls this, and only on the lot it returns from a first
     * layer. The wafers are not touched: a spec says how the lot is being processed,
     * not what has happened to it. G3 preparation evidence is preserved.</p>
     *
     * @param run the run spec to store, or {@code null} to clear it
     * @return a new lot with the spec attached, preserving the grade and preparation generation
     */
    public FoupContents withRun(RunSpec run) {
        return new FoupContents(this.lotId, this.capacity, this.wafers, run, this.grade, this.preparationGeneration, roomExposure, roomYieldPermille, g3Preparation);
    }

    /**
     * Returns a copy with new wafer values at the same slots, preserving the lot id,
     * capacity, run spec, grade, preparation generation, G3 evidence, room exposure
     * and frozen room yield.
     *
     * <p>This is the copy operation the yield engine should use after applying a
     * layer. Rebuilding a lot through the legacy eight-argument constructor instead
     * would silently drop G3 evidence.</p>
     *
     * @param processedWafers the new wafers, one per existing slot
     * @return a new lot with the processed wafers
     * @throws NullPointerException     if the list is null or holds a null entry
     * @throws IllegalArgumentException if the list length differs from this lot's size
     */
    public FoupContents withProcessedWafers(List<Wafer> processedWafers) {
        Objects.requireNonNull(processedWafers, "processedWafers must not be null");
        if (processedWafers.size() != this.wafers.size()) {
            throw new IllegalArgumentException("expected " + this.wafers.size()
                + " processed wafers, got " + processedWafers.size());
        }
        return new FoupContents(this.lotId, this.capacity, processedWafers, this.run, this.grade,
            this.preparationGeneration, this.roomExposure, this.roomYieldPermille, this.g3Preparation);
    }

    /**
     * Returns a copy of this lot with the given preparation generation.
     * Only allows setting generation 2 on trusted fresh nonempty pure unstarted lots
     * where all wafers are at layer 0. Refuses processed or already-prepared lots,
     * including G3 lots, so this legacy method cannot downgrade G3.
     *
     * @param generation the preparation generation to set (must be 2)
     * @return a new lot with the preparation generation set
     * @throws IllegalArgumentException if generation is not 2, or the lot is empty, not PURE grade,
     *                                  already started, already prepared, or any wafer is not at layer 0
     */
    public FoupContents withPreparationGeneration(int generation) {
        if (generation != PREPARATION_G2) {
            throw new IllegalArgumentException("only generation " + PREPARATION_G2 + " can be set via this method");
        }
        if (this.preparationGeneration != PREPARATION_LEGACY) {
            throw new IllegalArgumentException("lot is already prepared (generation " + this.preparationGeneration + ")");
        }
        if (isEmpty()) {
            throw new IllegalArgumentException("cannot prepare an empty lot");
        }
        if (this.grade.generation() < WaferSubstrate.PURE.generation()) {
            throw new IllegalArgumentException("only PURE-or-better grade lots can be prepared");
        }
        if (isStarted()) {
            throw new IllegalArgumentException("cannot prepare a lot that has already been started");
        }
        for (Wafer wafer : this.wafers) {
            if (wafer.layerIndex() != Wafer.UNPROCESSED_LAYER) {
                throw new IllegalArgumentException("all wafers must be at layer 0 (unprocessed) to prepare");
            }
        }
        return new FoupContents(this.lotId, this.capacity, this.wafers, this.run, this.grade, PREPARATION_G2, roomExposure, roomYieldPermille, G3Preparation.none());
    }

    /**
     * The explicit, single-use G3 preparation commit.
     *
     * <p>Accepts only a fresh, unstarted, non-empty ELECTRONIC_GRADE-or-better lot
     * whose wafers are all untouched (layer 0, no rolls, no flags). The supplied
     * paid cleanroom mean and per-wafer draws are frozen into one
     * {@link G3Preparation} at this moment. There is no hidden random source: the
     * same arguments always produce the same factors. A second call throws, and
     * subsequent mutation cannot add fresh wafers, so a caller cannot reroll,
     * upgrade or mix the saved result.</p>
     *
     * @param cleanroomMeanPermille paid cleanroom exposure mean, {@code 1..1000}
     * @param drawsPermille         one per-wafer draw in {@code [0, 1000]}, in slot order
     * @return a new G3-prepared lot carrying the frozen evidence
     * @throws IllegalStateException    if the lot is already prepared
     * @throws IllegalArgumentException if the lot is empty, not electronic-grade,
     *                                  started, holds processed wafers, or the draws
     *                                  do not match the wafer count/range
     */
    public FoupContents preparedG3(int cleanroomMeanPermille, int[] drawsPermille) {
        if (this.preparationGeneration != PREPARATION_LEGACY) {
            throw new IllegalStateException("lot is already prepared (generation " + this.preparationGeneration + ")");
        }
        if (isEmpty()) {
            throw new IllegalArgumentException("cannot prepare an empty lot");
        }
        if (this.grade.generation() < WaferSubstrate.ELECTRONIC_GRADE.generation()) {
            throw new IllegalArgumentException("G3 preparation requires electronic-grade-or-better wafers");
        }
        if (isStarted()) {
            throw new IllegalArgumentException("cannot prepare a lot that has already been started");
        }
        for (Wafer wafer : this.wafers) {
            if (wafer.layerIndex() != Wafer.UNPROCESSED_LAYER
                || wafer.cumulativeRoll() != Wafer.NO_ROLLS
                || wafer.flags() != Wafer.NO_FLAGS) {
                throw new IllegalArgumentException("G3 preparation requires fresh unprocessed wafers");
            }
        }
        G3Preparation evidence = G3Preparation.commit(cleanroomMeanPermille, drawsPermille);
        if (evidence.factorCount() != this.wafers.size()) {
            throw new IllegalArgumentException("G3 preparation produced " + evidence.factorCount()
                + " factors for " + this.wafers.size() + " wafers");
        }
        return new FoupContents(this.lotId, this.capacity, this.wafers, this.run, this.grade,
            PREPARATION_G3, this.roomExposure, this.roomYieldPermille, evidence);
    }

    /**
     * Returns the lowest layer across the wafers in this lot.
     *
     * @return the minimum layer index, or {@link FoupPublicView#EMPTY_LOT_LAYER} when
     *         the lot is empty
     */
    public int lotLayer() {
        int lowest = Integer.MAX_VALUE;
        for (Wafer wafer : this.wafers) {
            lowest = Math.min(lowest, wafer.layerIndex());
        }
        return lowest == Integer.MAX_VALUE ? FoupPublicView.EMPTY_LOT_LAYER : lowest;
    }

    /**
     * Returns everything about this lot that may leave the server.
     *
     * <p>The result carries the capacity, the wafer count, the lot layer, the wafer
     * substrate grade, and the preparation generation. It never carries a
     * {@link Wafer#cumulativeRoll()}, a {@link Wafer#flags()} value, or a single G3
     * per-wafer factor, and none of those can be recovered from it.</p>
     *
     * @return the client-safe view of this lot
     */
    public FoupPublicView publicView() {
        return new FoupPublicView(this.capacity, this.wafers.size(), lotLayer(), this.grade, this.preparationGeneration);
    }

    /**
     * Prints the lot without its hidden values. Rolls, flags, the run spec and G3
     * factors never appear here because a lot can end up in a log line or an error
     * message.
     *
     * @return a description with the public view only
     */
    @Override
    public String toString() {
        FoupPublicView view = publicView();
        return "FoupContents[lot=" + this.lotId + ", capacity=" + view.capacity()
            + ", wafers=" + view.waferCount() + ", layer=" + view.lotLayer()
            + ", grade=" + view.grade().id() + ", preparation=" + view.preparationGeneration() + "]";
    }
}
