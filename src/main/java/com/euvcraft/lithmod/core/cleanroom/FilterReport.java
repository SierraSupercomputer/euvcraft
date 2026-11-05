package com.euvcraft.lithmod.core.cleanroom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Filter report data structures for cleanroom monitoring.
 * Pure Java 21+, no Minecraft dependencies.
 */
public final class FilterReport {

    private FilterReport() {}

    /** Number of rows per page. */
    public static final int PAGE_SIZE = 5;

    /**
     * Filter unit state, ordered by priority (errors first).
     * Ordinal determines sort/display priority.
     */
    public enum State {
        /** Unit is completely unavailable. */
        UNAVAILABLE,
        /** Unit is missing from the system. */
        MISSING,
        /** No filter definition configured. */
        NO_DEFINITION,
        /** Insufficient power for operation. */
        NO_POWER,
        /** No filter medium installed. */
        NO_MEDIUM,
        /** Room conditions not ready. */
        ROOM_NOT_READY,
        /** Controller is turned off. */
        CONTROLLER_OFF,
        /** Unit is ready to filter. */
        READY,
        /** Unit is actively filtering. */
        FILTERING;
        public boolean attention(){return this!=READY&&this!=FILTERING;}
    }

    /**
     * A single filter unit row.
     *
     * @param x              X coordinate (unrestricted int, may be negative)
     * @param y              Y coordinate (unrestricted int, may be negative)
     * @param z              Z coordinate (unrestricted int, may be negative)
     * @param state          Unit state (non-null)
     * @param energy         Stored energy; -1 for UNAVAILABLE/MISSING, else non-negative (may exceed capacity after config shrink)
     * @param capacity       Energy capacity; -1 for UNAVAILABLE/MISSING, else non-negative
     * @param spare          Spare medium count; -1 for UNAVAILABLE/MISSING, else non-negative
     * @param remainingTicks Ticks remaining in current operation; -1 for UNAVAILABLE/MISSING, else non-negative
     */
    public record Row(
            int x,
            int y,
            int z,
            State state,
            int energy,
            int capacity,
            int spare,
            int remainingTicks
    ) {
        public Row {
            Objects.requireNonNull(state, "state must not be null");
            boolean isUnavailableOrMissing = state == State.UNAVAILABLE || state == State.MISSING;
            if (isUnavailableOrMissing) {
                if (energy != -1 || capacity != -1 || spare != -1 || remainingTicks != -1) {
                    throw new IllegalArgumentException(
                            "For UNAVAILABLE or MISSING, all resource fields must be -1"
                    );
                }
            } else {
                if (energy < 0 || capacity < 0 || spare < 0 || remainingTicks < 0) {
                    throw new IllegalArgumentException(
                            "For states other than UNAVAILABLE/MISSING, resource fields must be non-negative"
                    );
                }
            }
        }
    }

    /**
     * A page of filter report rows with summary totals.
     *
     * @param total       Total rows across all pages
     * @param page        Current page index (0-based)
     * @param pages       Total number of pages (minimum 1)
     * @param working     Count of FILTERING units across entire dataset
     * @param attention   Count of units requiring attention across entire dataset
     * @param unavailable Count of UNAVAILABLE units across entire dataset
     * @param rows        Rows visible on this page (max PAGE_SIZE, immutable)
     */
    public record Page(
            int total,
            int page,
            int pages,
            int working,
            int attention,
            int unavailable,
            List<Row> rows
    ) {
        public Page {
            Objects.requireNonNull(rows, "rows must not be null");
            rows = List.copyOf(rows);
        }
    }

    /**
     * Creates a paginated, sorted page from the given rows.
     *
     * @param rows      Source rows (non-null, not mutated)
     * @param requested Requested page index (clamped to valid range)
     * @return Page with sorted, paginated rows and computed totals
     */
    public static Page page(List<Row> rows, int requested) {
        Objects.requireNonNull(rows, "rows must not be null");

        // Defensive copy and sort
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparingInt((Row r) -> r.state().ordinal())
                .thenComparingInt(r -> r.x())
                .thenComparingInt(r -> r.y())
                .thenComparingInt(r -> r.z()));

        // Compute totals over entire dataset
        int total = sorted.size();
        int working = 0;
        int attention = 0;
        int unavailable = 0;

        for (Row r : sorted) {
            if (r.state() == State.FILTERING) {
                working++;
            }
            if (r.state().attention()) {
                attention++;
            }
            if (r.state() == State.UNAVAILABLE) {
                unavailable++;
            }
        }

        // Calculate pages (minimum 1)
        int pages = Math.max(1, total==0?1:1+(total-1)/PAGE_SIZE);

        // Clamp requested page
        int page = Math.max(0, Math.min(requested, pages - 1));

        // Extract page slice
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<Row> pageRows = sorted.subList(from, to);

        return new Page(total, page, pages, working, attention, unavailable, pageRows);
    }
}