package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable contents of one sealed lot: a {@link UUID} lot id, a capacity and the
 * ordered list of {@link Wafer}s inside it.
 *
 * <p>The wafer order is the slot order inside the FOUP and is stable across saving,
 * loading and inventory moves, because the wafer index is part of the roll context
 * the yield engine draws from (see {@link ContextualRng}). A wafer is never a loose
 * item; this record is the only way one exists.</p>
 *
 * <p>The record is immutable: the wafer list is copied on construction and every
 * {@code with...} method returns a new instance. The hidden
 * {@link Wafer#cumulativeRoll()} and {@link Wafer#flags()} values are reachable from
 * the accessors, so this type must only ever be used on the server. What may leave
 * the server is {@link #publicView()}.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param lotId    server-assigned identifier of the sealed lot
 * @param capacity maximum number of wafers this lot holds, {@code >= 1}
 * @param wafers   wafers in the lot, in slot order, at most {@code capacity} entries
 */
public record FoupContents(UUID lotId, int capacity, List<Wafer> wafers) {

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
     * Creates an empty lot.
     *
     * @param lotId    server-assigned identifier of the sealed lot
     * @param capacity maximum number of wafers the lot holds
     * @return a lot with no wafers in it
     */
    public static FoupContents empty(UUID lotId, int capacity) {
        return new FoupContents(lotId, capacity, List.of());
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
     * @return a new lot with the wafer appended
     * @throws IllegalStateException if the lot is already full
     */
    public FoupContents withWafer(Wafer wafer) {
        Objects.requireNonNull(wafer, "wafer must not be null");
        if (isFull()) {
            throw new IllegalStateException("the lot is already full (" + this.capacity + " wafers)");
        }
        List<Wafer> grown = new ArrayList<>(this.wafers);
        grown.add(wafer);
        return new FoupContents(this.lotId, this.capacity, grown);
    }

    /**
     * Returns a copy of this lot with the given wafers appended.
     *
     * @param newWafers the wafers to add, no null entries
     * @return a new lot with the wafers appended
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
        return new FoupContents(this.lotId, this.capacity, grown);
    }

    /**
     * Returns a copy of this lot without the wafer at the given slot.
     *
     * @param index slot to clear, {@code 0 <= index < size()}
     * @return a new lot with that wafer removed
     * @throws IndexOutOfBoundsException if the index is not a slot of this lot
     */
    public FoupContents withoutWaferAt(int index) {
        if (index < 0 || index >= this.wafers.size()) {
            throw new IndexOutOfBoundsException(
                "slot " + index + " is not in a lot holding " + this.wafers.size() + " wafers");
        }
        List<Wafer> shrunk = new ArrayList<>(this.wafers);
        shrunk.remove(index);
        return new FoupContents(this.lotId, this.capacity, shrunk);
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
     * <p>The result carries the capacity, the wafer count and the lot layer. It never
     * carries a {@link Wafer#cumulativeRoll()} or a {@link Wafer#flags()} value, and
     * neither can be recovered from it.</p>
     *
     * @return the client-safe view of this lot
     */
    public FoupPublicView publicView() {
        return new FoupPublicView(this.capacity, this.wafers.size(), lotLayer());
    }

    /**
     * Prints the lot without its hidden values. Rolls and flags never appear here
     * because a lot can end up in a log line or an error message.
     *
     * @return a description with the public view only
     */
    @Override
    public String toString() {
        FoupPublicView view = publicView();
        return "FoupContents[lot=" + this.lotId + ", capacity=" + view.capacity()
            + ", wafers=" + view.waferCount() + ", layer=" + view.lotLayer() + "]";
    }
}