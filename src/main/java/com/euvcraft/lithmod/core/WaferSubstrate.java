package com.euvcraft.lithmod.core;

/**
 * The substrate a wafer is made from.
 *
 * <p>{@code ORDINARY} wafers are the existing silicon wafers produced by the
 * {@code WaferSlicer}. {@code PURE} wafers are the new substrate that will be
 * produced by a later machine from ordinary boules and water.</p>
 *
 * <p>Each substrate has a stable string ID used in saved data and network
 * packets, and a generation number that increments when a new substrate is
 * introduced. The ID and generation are permanent and must never change.</p>
 *
 * <p>This enum has no Minecraft or NeoForge dependencies.</p>
 */
public enum WaferSubstrate {

    /**
     * Ordinary silicon wafers (generation 1).
     * <p>Stable ID: {@code "ordinary"}</p>
     */
    ORDINARY("ordinary", 1),

    /**
     * Pure silicon wafers (generation 2).
     * <p>Stable ID: {@code "pure"}</p>
     */
    PURE("pure", 2);

    private final String id;
    private final int generation;

    WaferSubstrate(String id, int generation) {
        this.id = id;
        this.generation = generation;
    }

    /**
     * Returns the stable string ID of this substrate.
     *
     * @return the ID, never null
     */
    public String id() {
        return id;
    }

    /**
     * Returns the generation number of this substrate.
     *
     * @return the generation, {@code >= 1}
     */
    public int generation() {
        return generation;
    }

    /**
     * Returns the substrate with the given ID, or {@code null} if none matches.
     *
     * <p>This is case-insensitive to tolerate externally produced data.</p>
     *
     * @param id the substrate ID to look up, may be null
     * @return the matching substrate, or {@code null}
     */
    public static WaferSubstrate fromId(String id) {
        if (id == null) {
            return null;
        }
        for (WaferSubstrate substrate : values()) {
            if (substrate.id.equalsIgnoreCase(id)) {
                return substrate;
            }
        }
        return null;
    }

    /**
     * Returns the substrate with the given ID, defaulting to {@code ORDINARY}
     * when the ID is missing or unknown.
     *
     * <p>This is used when decoding legacy data that lacks a substrate field.</p>
     *
     * @param id the substrate ID to look up, may be null
     * @return the matching substrate, or {@code ORDINARY}
     */
    public static WaferSubstrate fromIdOrDefault(String id) {
        WaferSubstrate found = fromId(id);
        return found != null ? found : ORDINARY;
    }

    /**
     * Returns the substrate with the given ID, or throws if the ID is explicitly
     * present but invalid.
     *
     * <p>Unlike {@link #fromIdOrDefault(String)}, this method distinguishes
     * between "not present" (null) and "present but invalid" (non-null but no
     * match). A null input returns {@code ORDINARY} for backward compatibility;
     * a non-null invalid input throws.</p>
     *
     * @param id the substrate ID to look up, may be null
     * @return the matching substrate, or {@code ORDINARY} when null
     * @throws IllegalArgumentException if {@code id} is non-null but not a known ID
     */
    public static WaferSubstrate fromIdOrThrow(String id) {
        if (id == null) {
            return ORDINARY;
        }
        WaferSubstrate found = fromId(id);
        if (found == null) {
            throw new IllegalArgumentException("unknown substrate ID: " + id);
        }
        return found;
    }
}