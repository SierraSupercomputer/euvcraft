package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The pure core of the yield roll engine: one method that puts a lot through one layer
 * of a process, and one that judges a finished lot.
 *
 * <p><strong>Applying a layer.</strong> Every wafer in the lot draws
 * {@code rollsPerLayer} rolls, all at the same calibrated step weight, and each roll is
 * multiplied into that wafer's hidden cumulative roll. The rolls for layer
 * {@code n} and roll {@code j} of that layer come from step index
 * {@code n * rollsPerLayer + j}, which keeps every roll of a process at a distinct,
 * reproducible position: undoing the save and doing the step again gives the same
 * numbers, and no two wafers in a lot share a stream.</p>
 *
 * <p>The weight is calibrated once for the whole process, from the stored
 * {@link RunSpec}'s roll count and effective yield. A lot that has not been started
 * (no run spec) resolves its spec from the current settings at its first layer, and
 * that spec is frozen for the rest of the run. A line that is not perfectly healthy
 * carries a lower {@code defectFactor}, which divides the weight and lowers the pass
 * rate without any second table.</p>
 *
 * <p><strong>Judging a lot.</strong> {@link #evaluate(FoupContents)} reads the
 * hidden rolls and answers pass or fail per wafer against the spec's effective yield.
 * It does not change the lot, and the answers carry nothing a client could use to work
 * out a roll.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class YieldRollEngine {

    private YieldRollEngine() {
    }

    /**
     * Returns the number of rolls a whole process at this tier draws.
     *
     * @param tier     the tier being run, not null
     * @param settings the settings in force, not null
     * @return {@code tier.layers() * settings.rollsPerLayer()}
     */
    public static int rollCount(Tier tier, FabSettings settings) {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        return tier.layers() * settings.rollsPerLayer();
    }

    /**
     * Returns the step weight a layer of this process uses, calibrated so that a lot run
     * through every layer passes at the tier's effective yield.
     *
     * @param tier         the tier being run, not null
     * @param settings     the settings in force, not null
     * @param cores        the core count of the die, or {@code null} for none
     * @param defectFactor how healthy the line is
     * @return the step weight, strictly positive
     */
    static double stepWeight(Tier tier, FabSettings settings, Integer cores, double defectFactor) {
        double effectiveYield = YieldMath.resolveEffectiveYield(tier, settings, cores);
        return YieldCalibration.stepWeight(rollCount(tier, settings), effectiveYield, defectFactor);
    }

    /**
     * Returns the step index of one roll of one layer, {@code layerNumber * rollsPerLayer + roll}.
     *
     * @param layerNumber  the one-based layer being applied
     * @param rollsPerLayer how many rolls that layer draws, at least one
     * @param roll         which roll of that layer, {@code 0 <= roll < rollsPerLayer}
     * @return the step index the roll is drawn at
     * @throws IllegalArgumentException if the roll is outside the layer
     */
    public static int stepIndex(int layerNumber, int rollsPerLayer, int roll) {
        if (roll < 0 || roll >= rollsPerLayer) {
            throw new IllegalArgumentException(
                "roll " + roll + " is not in a layer of " + rollsPerLayer + " rolls");
        }
        return layerNumber * rollsPerLayer + roll;
    }

    /**
     * Puts every wafer of a lot through one more layer of a process.
     *
     * <p>If the lot has no run spec (it has not been started), this call creates one from
     * the current settings and stores it on the returned lot. For every later layer, the
     * stored spec is used and the tier and cores arguments must match it; the live
     * settings are ignored for the roll calibration.</p>
     *
     * <p>The lot comes back as a new instance; the one that was passed in is untouched.
     * Each wafer's cumulative roll is multiplied by the rolls of this layer and every
     * wafer is moved to {@code layerNumber}. The hidden rolls are never logged, sent or
     * copied into the public view.</p>
     *
     * @param lot          the lot to process, not null, not empty
     * @param tier         the tier being run, not null
     * @param cores        the core count of the die, or {@code null} for none
     * @param layerNumber  the one-based layer to apply; it must be the layer the lot is
     *                     on plus one and no higher than {@code tier.layers()}
     * @param defectFactor how healthy the line is; values below
     *                     {@link YieldCalibration#MIN_DEFECT_FACTOR} are raised to it,
     *                     values above 1.0 are refused
     * @param settings     the settings in force, not null
     * @param rolls        where the step draws its randomness from, not null
     * @return a new lot with the layer applied and (if first layer) a run spec attached
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the lot is empty, or the layer number is not
     *                                  the next one, or it is past the tier's layers, or
     *                                  the tier or cores do not match the stored spec,
     *                                  or defectFactor is above 1.0
     */
    public static FoupContents applyLayerStep(FoupContents lot, Tier tier, String dieType, Integer cores, int layerNumber,
            double defectFactor, FabSettings settings, RollSource rolls) {
        return applyLayerStep(lot, tier, dieType, cores, layerNumber, defectFactor, settings, 1.0, rolls);
    }

    /**
     * Puts every wafer of a lot through one more layer of a process with an explicit
     * frozen yield factor from scanner calibration.
     *
     * <p>The frozen factor is applied ONCE to the first layer's effective yield.
     * The resulting effective yield is preserved in the RunSpec for all subsequent
     * layers and for packaging/highbin. The factor is NOT multiplied into defectFactor
     * (which would be nonlinear and wrong), and NOT applied per layer or again on resume.</p>
     *
     * @param lot               the lot to process, not null, not empty
     * @param tier              the tier being run, not null
     * @param dieType           the die type being run, not null
     * @param cores             the core count of the die, or {@code null} for none
     * @param layerNumber       the one-based layer to apply; must be the next layer
     * @param defectFactor      how healthy the line is (0.0 to 1.0)
     * @param settings          the settings in force, not null
     * @param frozenYieldFactor the frozen calibration multiplier (0.0 to 1.0], applied once at first layer
     * @param rolls             where the step draws its randomness from, not null
     * @return a new lot with the layer applied and (if first layer) a run spec attached
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if validation fails
     */
    public static FoupContents applyLayerStep(FoupContents lot, Tier tier, String dieType, Integer cores, int layerNumber,
            double defectFactor, FabSettings settings, double frozenYieldFactor, RollSource rolls) {
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(dieType, "dieType must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(rolls, "rolls must not be null");

        if (defectFactor > 1.0) {
            throw new IllegalArgumentException("defectFactor must be <= 1.0, got " + defectFactor);
        }
        if (!Double.isFinite(frozenYieldFactor) || frozenYieldFactor <= 0.0 || frozenYieldFactor > 1.0) {
            throw new IllegalArgumentException("frozenYieldFactor must be finite in (0.0, 1.0], got " + frozenYieldFactor);
        }

        if (lot.isEmpty()) {
            throw new IllegalArgumentException("an empty lot cannot take a layer step");
        }

        int currentLayer = lot.lotLayer();
        if (layerNumber != currentLayer + 1) {
            throw new IllegalArgumentException(
                "layer " + layerNumber + " is not the next layer of a lot that is at layer " + currentLayer);
        }
        if (layerNumber > tier.layers()) {
            throw new IllegalArgumentException(
                "layer " + layerNumber + " is past the " + tier.layers() + " layers of " + tier.id());
        }

        RunSpec spec = lot.run();
        boolean firstLayer = spec == null;

        if (firstLayer) {
            // First layer: create the spec from current settings and store it
            if (cores != null && cores <= 0) {
                throw new IllegalArgumentException("cores must be > 0 when present, got " + cores);
            }
            // Apply frozen yield factor ONCE to the effective yield at first layer
            double baseEffectiveYield = YieldMath.resolveEffectiveYield(tier, settings, cores);
            double finalEffectiveYield = baseEffectiveYield * frozenYieldFactor;
            // Clamp to valid range
            // Calibration is a true multiplier, including when the base yield is already low.
            // RunSpec accepts any finite fraction in (0,1).
            spec = new RunSpec(dieType, tier.index(), RunSpec.coresOrZero(cores),
                finalEffectiveYield, YieldRollEngine.rollCount(tier, settings));
        } else {
            // Later layers: must match the stored spec exactly
            if (!spec.matches(tier, dieType, cores)) {
                throw new IllegalArgumentException(
                    "tier, die type or cores differ from the run spec (" + spec + "); got tier " + tier.index()
                        + ", die type " + dieType + " and cores " + (cores != null ? cores : "none"));
            }
        }

        int rollsPerLayer;
        double weight;
        if (firstLayer) {
            rollsPerLayer = settings.rollsPerLayer();
            weight = YieldCalibration.stepWeight(spec.rollCount(), spec.effectiveYield(), defectFactor);
        } else {
            // Later layers: derive rollsPerLayer from frozen spec rollCount
            rollsPerLayer = spec.rollCount() / tier.layers();
            // Use the stored spec's effective yield and roll count (already includes frozen factor)
            weight = YieldCalibration.stepWeight(spec.rollCount(), spec.effectiveYield(), defectFactor);
        }

        List<Wafer> processed = new ArrayList<>(lot.size());
        for (int waferIndex = 0; waferIndex < lot.size(); waferIndex++) {
            Wafer wafer = lot.wafers().get(waferIndex);
            double cumulative = wafer.cumulativeRoll();
            for (int roll = 0; roll < rollsPerLayer; roll++) {
                Rng rng = rolls.rngFor(waferIndex, stepIndex(layerNumber, rollsPerLayer, roll));
                cumulative *= YieldCalibration.stepRoll(rng, weight);
            }
            processed.add(new Wafer(layerNumber, stored(cumulative), wafer.flags()));
        }

        FoupContents next = new FoupContents(lot.lotId(), lot.capacity(), processed, lot.run(), lot.grade(), lot.preparationGeneration());
        if (firstLayer) {
            next = next.withRun(spec);
        }
        return next;
    }

    /**
     * Judges every wafer of a lot against its stored run spec's effective yield.
     *
     * <p>The lot must carry a run spec (i.e., it must have been started). The tier and
     * cores arguments are not used for the judgement; they are only checked for
     * consistency with the spec.</p>
     *
     * @param lot   the lot to judge, not null, must carry a run spec
     * @param tier  the tier it was processed at, not null, must match the spec
     * @param cores the core count of the die, or {@code null} for none, must match the spec
     * @return one answer per wafer, in slot order
     * @throws NullPointerException     if any argument is null
     * @throws IllegalStateException    if the lot has no run spec, or if the tier or cores
     *                                  do not match the spec, or if any wafer is not at the
     *                                  tier's last layer
     */
    public static boolean[] evaluate(FoupContents lot, Tier tier, String dieType, Integer cores) {
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(dieType, "dieType must not be null");

        RunSpec spec = lot.run();
        if (spec == null) {
            throw new IllegalStateException("lot has not been started (no run spec)");
        }
        if (!spec.matches(tier, dieType, cores)) {
            throw new IllegalStateException(
                "tier, die type or cores differ from the run spec (" + spec + "); got tier " + tier.index()
                    + ", die type " + dieType + " and cores " + (cores != null ? cores : "none"));
        }
        requireCompleteLot(lot, tier);

        boolean[] outcomes = new boolean[lot.size()];
        for (int waferIndex = 0; waferIndex < lot.size(); waferIndex++) {
            outcomes[waferIndex] = YieldMath.passes(lot.wafers().get(waferIndex).cumulativeRoll(), spec.effectiveYield());
        }
        return outcomes;
    }

    /**
     * Returns how many of a set of answers are passes.
     *
     * @param outcomes the answers from {@link #evaluate(FoupContents, Tier, Integer)}, not null
     * @return the number of passes
     * @throws NullPointerException if the answers are null
     */
    public static int countPassing(boolean[] outcomes) {
        Objects.requireNonNull(outcomes, "outcomes must not be null");
        int passing = 0;
        for (boolean outcome : outcomes) {
            if (outcome) {
                passing++;
            }
        }
        return passing;
    }

    /**
     * Checks that every wafer of a lot has been through every layer of a tier.
     *
     * <p>A lot that is short of its last layer has almost no rolls multiplied into it, so
     * its wafers would sail past any threshold. Judging one is refused instead.</p>
     *
     * @param lot  the lot to check, not null
     * @param tier the tier it is meant to be at, not null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalStateException    if any wafer is not at {@code tier.layers()}
     */
    public static void requireCompleteLot(FoupContents lot, Tier tier) {
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");

        for (int waferIndex = 0; waferIndex < lot.size(); waferIndex++) {
            int layer = lot.wafers().get(waferIndex).layerIndex();
            if (layer != tier.layers()) {
                throw new IllegalStateException(
                    "wafer " + waferIndex + " of a " + tier.id() + " lot is at layer " + layer
                        + ", not at " + tier.layers());
            }
        }
    }

    /**
     * Narrows a cumulative roll to the float a wafer stores.
     *
     * <p>A product of many rolls can fall below the smallest positive float, and a
     * {@link Wafer} rejects a roll of zero, so the smallest legal value is used instead.
     * The product can never exceed one because every roll is at most one.</p>
     */
    private static float stored(double cumulative) {
        float stored = (float) cumulative;
        if (stored <= 0.0f) {
            return Float.MIN_VALUE;
        }
        return Math.min(stored, 1.0f);
    }
}