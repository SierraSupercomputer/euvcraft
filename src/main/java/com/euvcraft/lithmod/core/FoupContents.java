package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
 * <p>Every lot carries a {@link WaferSubstrate} grade (ORDINARY or PURE) that is set
 * when the lot is created and preserved through every mutation. Existing saved lots
 * without a grade are treated as ORDINARY.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param lotId    server-assigned identifier of the sealed lot
 * @param capacity maximum number of wafers this lot holds, {@code >= 1}
 * @param wafers   wafers in the lot, in slot order, at most {@code capacity} entries
 * @param run      the parameters the lot was processed under, or {@code null} when it
 *                 has not been started
 * @param grade    the wafer substrate grade of this lot, never null
 */
public record FoupContents(UUID lotId, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade) {

    /**
     * Creates a lot that has not been started, which is the common case for a fresh FOUP.
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
        this(lotId, capacity, wafers, null, WaferSubstrate.ORDINARY);
    }

    /**
     * Legacy constructor that creates a lot with a run spec but default ORDINARY grade.
     * Kept for source compatibility.
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
        this(lotId, capacity, wafers, run, WaferSubstrate.ORDINARY);
    }

    /**
     * Creates a lot that has not been started, with an explicit substrate grade.
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
        return new FoupContents(lotId, capacity, wafers, null, grade);
    }

    /**
     * Validates the lot and makes the wafer list immutable.
     *
     * @throws NullPointerException     if {@code lotId} or {@code wafers} is null, or
     *                                  the wafer list holds a null entry
     * @throws IllegalArgumentException if the capacity is below one or the lot holds
     *                                  more wafers than its capacity
     */
    public FoupContents {
        Objects.requireNonNull(lotId, "lotId must not be null");
        Objects.requireNonNull(wafers, "wafers must not be null");
        Objects.requireNonNull(grade, "grade must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        wafers = List.copyOf(wafers);
        if (wafers.size() > capacity) {
            throw new IllegalArgumentException(
                "a lot of capacity " + capacity + " cannot hold " + wafers.size() + " wafers");
        }
    }

    /**
     * Creates an empty lot with ordinary substrate.
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers the lot holds
     * @return a lot with no wafers in it
     */
    public static FoupContents empty(UUID lotId, int capacity) {
        return new FoupContents(lotId, capacity, List.of(), null, WaferSubstrate.ORDINARY);
    }

    /**
     * Creates an empty lot with the given substrate grade.
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers the lot holds
     * @param grade    the wafer substrate grade, not null
     * @return a lot with no wafers in it
     */
    public static FoupContents empty(UUID lotId, int capacity, WaferSubstrate grade) {
        Objects.requireNonNull(grade, "grade must not be null");
        return new FoupContents(lotId, capacity, List.of(), null, grade);
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

    /**
     * Returns a copy of this lot with one more wafer appended.
     *
     * @param wafer the wafer to add, not null
     * @return a new lot with the wafer appended, carrying the same run spec and grade
     * @throws IllegalStateException if the lot is already full
     */
    public FoupContents withWafer(Wafer wafer) {
        Objects.requireNonNull(wafer, "wafer must not be null");
        if (isFull()) {
            throw new IllegalStateException("the lot is already full (" + this.capacity + " wafers)");
        }
        List<Wafer> grown = new ArrayList<>(this.wafers);
        grown.add(wafer);
        return new FoupContents(this.lotId, this.capacity, grown, this.run, this.grade);
    }

    /**
     * Returns a copy of this lot with the given wafers appended.
     *
     * @param newWafers the wafers to add, no null entries
     * @return a new lot with the wafers appended, carrying the same run spec and grade
     * @throws IllegalStateException if the result would exceed the capacity
     */
    public FoupContents withWafers(List<Wafer> newWafers) {
        Objects.requireNonNull(newWafers, "newWafers must not be null");
        if (newWafers.size() > this.capacity - this.wafers.size()) {
            throw new IllegalStateException("cannot add " + newWafers.size() + " wafers to a lot holding "
                + this.wafers.size() + " of " + this.capacity);
        }
        List<Wafer> grown = new ArrayList<>(this.wafers);
        grown.addAll(newWafers);
        return new FoupContents(this.lotId, this.capacity, grown, this.run, this.grade);
    }

    /**
     * Returns a copy of this lot without the wafer at the given slot.
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
        return new FoupContents(this.lotId, this.capacity, shrunk, this.run, this.grade);
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
     * not what has happened to it.</p>
     *
     * @param run the run spec to store, or {@code null} to clear it
     * @return a new lot with the spec attached, preserving the grade
     */
    public FoupContents withRun(RunSpec run) {
        return new FoupContents(this.lotId, this.capacity, this.wafers, run, this.grade);
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
     * <p>The result carries the capacity, the wafer count, the lot layer and the
     * wafer substrate grade. It never carries a {@link Wafer#cumulativeRoll()} or a
     * {@link Wafer#flags()} value, and neither can be recovered from it.</p>
     *
     * @return the client-safe view of this lot
     */
    public FoupPublicView publicView() {
        return new FoupPublicView(this.capacity, this.wafers.size(), lotLayer(), this.grade);
    }

    /**
     * Prints the lot without its hidden values. Rolls, flags and the run spec never
     * appear here because a lot can end up in a log line or an error message.
     *
     * @return a description with the public view only
     */
    @Override
    public String toString() {
        FoupPublicView view = publicView();
        return "FoupContents[lot=" + this.lotId + ", capacity=" + view.capacity()
            + ", wafers=" + view.waferCount() + ", layer=" + view.lotLayer() + ", grade=" + view.grade().id() + "]";
    }
}