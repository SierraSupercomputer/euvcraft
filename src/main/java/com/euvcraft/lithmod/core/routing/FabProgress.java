package com.euvcraft.lithmod.core.routing;

import java.util.Objects;

/**
 * Immutable fabrication progress tracker for a wafer lot through the fabrication
 * process flow. Each state transition enforces the correct tier constraints and order,
 * preventing skipping, replay or rebinding of the bound tier.
 *
 * <p>The progress starts at {@link Stage#UNPROCESSED} tier 0 and advances through
 * {@link Stage#DEPOSITED}, {@link Stage#COATED}, {@link Stage#EXPOSED},
 * {@link Stage#ETCHED} to {@link Stage#CLEANED}. The bound tier is chosen when
 * the wafer enters {@link Stage#COATED} (tiers 5-7) or {@link Stage#EXPOSED}
 * (tiers 1-4), and that tier then flows through all subsequent stages.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param stage  the current fabrication stage, never null
 * @param tier   the bound process tier, 0..8
 * @param version the schema version, must be {@link #CURRENT_VERSION}
 */
public record FabProgress(Stage stage, int tier, int version) {

    /**
     * The fabrication stages in process order.
     */
    public enum Stage {
        /** Wafer has not entered any process step. */
        UNPROCESSED,
        /** A material film has been deposited onto the wafers. */
        DEPOSITED,
        /** Resist has been coated (spin-coated) at a high tier (5-7). */
        COATED,
        /** An exposure run has started; its program is fixed but the lot is not complete. */
        EXPOSING,
        /** Exposure and developing have completed at the bound tier. */
        EXPOSED,
        /** The developed pattern has been transferred into the material film by etching. */
        ETCHED,
        /** Post-etch clean has been performed. */
        CLEANED,
        PLANARIZED,
        IMPLANTED
    }

    /** Current schema version for this record. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Creates a new fabrication progress state with validation.
     *
     * @param stage   the fabrication stage, must not be null
     * @param tier    the bound tier, must be in range 0..8 and valid for the stage
     * @param version the schema version, must be {@link #CURRENT_VERSION}
     * @throws NullPointerException     if stage is null
     * @throws IllegalArgumentException if version is not 1, tier is outside 0..8,
     *                                  or the tier is invalid for the given stage
     */
    public FabProgress {
        Objects.requireNonNull(stage, "stage must not be null");
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("version must be " + CURRENT_VERSION + ", got " + version);
        }
        if (tier < 0 || tier > 8) {
            throw new IllegalArgumentException("tier must be in range 0..8, got " + tier);
        }
        switch (stage) {
            case UNPROCESSED, DEPOSITED -> {
                if (tier != 0) {
                    throw new IllegalArgumentException(stage + " requires tier 0, got " + tier);
                }
            }
            case COATED -> {
                if (tier < 5 || tier > 8) {
                    throw new IllegalArgumentException("COATED requires tier 5..8, got " + tier);
                }
            }
            case EXPOSING, EXPOSED -> {
                if (tier < 1 || tier > 8) {
                    throw new IllegalArgumentException("EXPOSED requires tier 1..8, got " + tier);
                }
            }
            case ETCHED -> {
                if (tier < 4 || tier > 8) {
                    throw new IllegalArgumentException("ETCHED requires tier 4..8, got " + tier);
                }
            }
            case PLANARIZED, IMPLANTED -> {
                if (tier != 8) throw new IllegalArgumentException(stage + " requires T8");
            }
            case CLEANED -> {
                if (tier < 5 || tier > 8) {
                    throw new IllegalArgumentException("CLEANED requires tier 5..8, got " + tier);
                }
            }
        }
    }

    /**
     * Returns the initial fabrication progress: unprocessed at tier 0, version 1.
     *
     * @return a new {@code FabProgress} at the starting state
     */
    public static FabProgress initial() {
        return new FabProgress(Stage.UNPROCESSED, 0, CURRENT_VERSION);
    }

    /**
     * Transitions from {@link Stage#UNPROCESSED} to {@link Stage#DEPOSITED} at tier 0.
     *
     * @return a new {@code FabProgress} at the deposited stage
     * @throws IllegalStateException if the current stage is not {@link Stage#UNPROCESSED}
     */
    public FabProgress deposited() {
        if (stage != Stage.UNPROCESSED) {
            throw new IllegalStateException("deposited() requires UNPROCESSED, current stage is " + stage);
        }
        return new FabProgress(Stage.DEPOSITED, 0, CURRENT_VERSION);
    }

    /**
     * Transitions from {@link Stage#DEPOSITED} to {@link Stage#COATED} at the given tier.
     *
     * @param tier the coat tier, must be 5, 6 or 7
     * @return a new {@code FabProgress} at the coated stage with the bound tier
     * @throws IllegalStateException    if the current stage is not {@link Stage#DEPOSITED}
     * @throws IllegalArgumentException if tier is not in range 5..8
     */
    public FabProgress coated(int tier) {
        if (stage != (tier == 8 ? Stage.PLANARIZED : Stage.DEPOSITED)) {
            throw new IllegalStateException("Wrong stage for coating T" + tier + ": " + stage);
        }
        if (tier < 5 || tier > 8) {
            throw new IllegalArgumentException("coated() tier must be 5..8, got " + tier);
        }
        return new FabProgress(Stage.COATED, tier, CURRENT_VERSION);
    }

    /**
     * Transitions to {@link Stage#EXPOSED} at the given tier, binding the tier
     * according to the current stage:
     * <ul>
     *   <li>Tier 1-3: requires {@link Stage#UNPROCESSED} (direct exposure path)</li>
     *   <li>Tier 4: requires {@link Stage#DEPOSITED}</li>
     *   <li>Tier 5-7: requires {@link Stage#COATED} with matching bound tier</li>
     * </ul>
     *
     * @param tier the exposure tier, must be 1..8
     * @return a new {@code FabProgress} at the exposed stage with the bound tier
     * @throws IllegalStateException    if the current stage does not match the tier's requirement
     * @throws IllegalArgumentException if tier is not in range 1..8, or if tier 5-7
     *                                  does not match the bound coat tier
     */
    public FabProgress exposed(int tier) {
        if (tier < 1 || tier > 8) {
            throw new IllegalArgumentException("exposed() tier must be 1..8, got " + tier);
        }
        if(stage==Stage.EXPOSING){
            if(this.tier!=tier)throw new IllegalArgumentException("Cannot rebind an active exposure program");
            return new FabProgress(Stage.EXPOSED,tier,CURRENT_VERSION);
        }
        return switch (tier) {
            case 1, 2, 3 -> {
                if (stage != Stage.UNPROCESSED) {
                    throw new IllegalStateException("exposed(tier=" + tier + ") requires UNPROCESSED, current stage is " + stage);
                }
                yield new FabProgress(Stage.EXPOSED, tier, CURRENT_VERSION);
            }
            case 4 -> {
                if (stage != Stage.DEPOSITED) {
                    throw new IllegalStateException("exposed(tier=4) requires DEPOSITED, current stage is " + stage);
                }
                yield new FabProgress(Stage.EXPOSED, 4, CURRENT_VERSION);
            }
            default -> { // tier 5, 6, 7
                if (stage != Stage.COATED) {
                    throw new IllegalStateException("exposed(tier=" + tier + ") requires COATED, current stage is " + stage);
                }
                if (this.tier != tier) {
                    throw new IllegalArgumentException("exposed(tier=" + tier + ") requires matching COATED tier " + this.tier);
                }
                yield new FabProgress(Stage.EXPOSED, tier, CURRENT_VERSION);
            }
        };
    }

    /**
     * Transitions from {@link Stage#EXPOSED} (tier 4-7) to {@link Stage#ETCHED}
     * preserving the bound tier.
     *
     * @return a new {@code FabProgress} at the etched stage with the same tier
     * @throws IllegalStateException if the current stage is not {@link Stage#EXPOSED}
     *                               or the tier is not in range 4..8
     */
    public FabProgress etched() {
        if (stage != Stage.EXPOSED) {
            throw new IllegalStateException("etched() requires EXPOSED, current stage is " + stage);
        }
        if (tier < 4 || tier > 8) {
            throw new IllegalStateException("etched() requires EXPOSED tier 4..8, current tier is " + tier);
        }
        return new FabProgress(Stage.ETCHED, tier, CURRENT_VERSION);
    }

    /**
     * Transitions from {@link Stage#ETCHED} (tier 5-7) to {@link Stage#CLEANED}
     * preserving the bound tier.
     *
     * @return a new {@code FabProgress} at the cleaned stage with the same tier
     * @throws IllegalStateException if the current stage is not {@link Stage#ETCHED}
     *                               or the tier is not in range 5..8
     */
    public FabProgress cleaned() {
        if (stage != (tier == 8 ? Stage.IMPLANTED : Stage.ETCHED)) {
            throw new IllegalStateException("cleaned() requires ETCHED, current stage is " + stage);
        }
        if (tier < 5 || tier > 8) {
            throw new IllegalStateException("cleaned() requires ETCHED tier 5..8, current tier is " + tier);
        }
        return new FabProgress(Stage.CLEANED, tier, CURRENT_VERSION);
    }

    public FabProgress planarized() {
        if (stage != Stage.DEPOSITED) throw new IllegalStateException("Planarization requires deposition");
        return new FabProgress(Stage.PLANARIZED, 8, CURRENT_VERSION);
    }

    public FabProgress implanted() {
        if (stage != Stage.ETCHED || tier != 8) throw new IllegalStateException("Implantation requires etched T8 wafers");
        return new FabProgress(Stage.IMPLANTED, 8, CURRENT_VERSION);
    }

    /**
     * Returns whether this progress state is ready for packaging.
     * A wafer is ready for packaging exactly when it is at:
     * <ul>
     *   <li>{@link Stage#EXPOSED} tier 1, 2 or 3</li>
     *   <li>{@link Stage#ETCHED} tier 4</li>
     *   <li>{@link Stage#CLEANED} tier 5, 6 or 7</li>
     * </ul>
     *
     * @return {@code true} if the wafer can be packaged at this state
     */
    public boolean readyForPackaging() {
        return switch (stage) {
            case EXPOSED -> tier >= 1 && tier <= 3;
            case ETCHED -> tier == 4;
            case CLEANED -> tier >= 5 && tier <= 8;
            default -> false;
        };
    }

    /**
     * Returns a compact string representation suitable for logging.
     *
     * @return a short description like "EXPOSED@tier4v1"
     */
    @Override
    public String toString() {
        return stage + "@tier" + tier + "v" + version;
    }
}
