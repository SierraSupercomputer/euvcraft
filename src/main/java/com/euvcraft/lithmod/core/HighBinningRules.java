package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure-core high binning rules for the test and package machine.
 *
 * <p>High binning promotes a <strong>completed, trusted source lot</strong> from one tier
 * to the next by re-judging its wafers against a tighter effective yield. The logic
 * and compute families are supported; memory and nand are refused. The target tier
 * is always exactly {@code sourceTier + 1}; the caller supplies the target item
 * availability and an explicit {@link Transition} configuration. The method is
 * deterministic: it uses the source lot's original private cumulative rolls and the
 * source run spec's frozen effective yield and roll count multiplied by the transition's
 * yield multiplier. No new randomness is introduced and the source lot is never mutated.</p>
 *
 * <p>The transition's cost multipliers (ticks and energy) are validated on
 * construction and used by the caller to build a {@link PackagingRun} for the
 * promoted wafers. The returned {@link PackagingPlan} preserves the source
 * recipe amounts ({@code chipsPerWafer} and {@code packagesPerWafer}) and carries
 * the new pass flags. A failing wafer in the high-binning judgment yields scrap
 * and consumes no package. The plan is immutable and sufficient to freeze all
 * outcomes; the caller persists mode, target and cost with a {@link PackagingRun.Snapshot}.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class HighBinningRules {

    private HighBinningRules() {
    }

    /**
     * Validates and judges a source lot for high binning to the next tier.
     *
     * <p>The checks run in order and fail closed:</p>
     *
     * <ol>
     *   <li>Lot must be non-null, non-empty, and carry a {@link RunSpec}.</li>
     *   <li>Source tier must match the lot's run spec tier.</li>
     *   <li>All wafers must be at the source tier's final layer.</li>
     *   <li>Die type must be logic or compute. Memory and nand are refused.</li>
     *   <li>Source tier must be T3 or higher.</li>
     *   <li>Compute: exactly 1 core before T6; at T6+ preserve source core count.</li>
     *   <li>Logic: no core count (NO_CORES).</li>
     *   <li>Target tier must be exactly source tier + 1, not exceeding T8.</li>
     *   <li>Logic target tier must not exceed 5.</li>
     *   <li>Packager generation must be at least 2.</li>
     *   <li>Target chip item must be available (caller-supplied).</li>
     *   <li>An explicit {@link Transition} must be provided; no implicit fallback.</li>
     *   <li>Transition must match the source and target tiers exactly.</li>
     *   <li>Transition yield multiplier must be in (0, 1]; cost multipliers must be &ge; 1
     *       and not overflow scaled int costs for actual base values.</li>
     * </ol>
     *
     * @param lot                 the source lot to judge, not null
     * @param sourceTier          the source tier, not null, must match the lot's run spec
     * @param installedGeneration the installed generation of the test_packaging line
     * @param targetAvailable     whether the target tier's chip item exists in the catalog
     * @param transition          the explicit transition configuration, not null
     * @param chipsPerWafer       chips (or scrap) per wafer, at least 1
     * @param packagesPerWafer    packages a passing wafer consumes, at least 1
     * @return a new {@link PackagingPlan} with the high-binning pass flags
     * @throws IllegalArgumentException if any structural check fails (null lot/tier/transition,
     *         mismatched spec, invalid cores, bad transition, target tier > 8, etc.)
     * @throws IllegalStateException    if the lot is not started, incomplete, or the
     *         die family is not logic/compute, or packager generation < 2, or target unavailable
     */
    public static PackagingPlan judge(FoupContents lot,
                                      Tier sourceTier,
                                      int installedGeneration,
                                      boolean targetAvailable,
                                      Transition transition,
                                      int chipsPerWafer,
                                      int packagesPerWafer) {

        // 1. Basic lot structure (structural checks first, before layer checks)
        if (lot == null || sourceTier == null || transition == null) {
            throw new IllegalArgumentException("lot, source tier and transition are required");
        }
        if (lot.isEmpty()) {
            throw new IllegalArgumentException("lot must not be empty");
        }
        RunSpec spec = lot.run();
        if (spec == null) {
            throw new IllegalStateException("lot has not been started (no run spec)");
        }

        // 2. Tier must match the frozen spec (before layer check per brief)
        if (sourceTier.index() != spec.tierIndex()) {
            throw new IllegalArgumentException("source tier index " + sourceTier.index()
                + " does not match lot's run spec tier " + spec.tierIndex());
        }
        if (!spec.dieType().equals(lot.run().dieType())) {
            throw new IllegalArgumentException("die type mismatch with run spec");
        }

        // 3. All wafers at final layer
        int finalLayer = sourceTier.layers();
        for (int i = 0; i < lot.size(); i++) {
            if (lot.wafers().get(i).layerIndex() != finalLayer) {
                throw new IllegalStateException("wafer " + i + " is at layer " + lot.wafers().get(i).layerIndex()
                    + ", not at the final layer " + finalLayer);
            }
        }

        // 4. Die family: logic or compute only
        String dieType = spec.dieType();
        if (!DieTypes.LOGIC.equals(dieType) && !DieTypes.COMPUTE.equals(dieType)) {
            throw new IllegalStateException("high binning only supports logic and compute, got " + dieType);
        }

        // 5. Source tier must be T3 or higher
        if (sourceTier.index() < 3) {
            throw new IllegalStateException("high binning requires source tier T3 or higher, got " + sourceTier.id());
        }

        // 6. Core count rules
        if (DieTypes.COMPUTE.equals(dieType)) {
            // Compute: exactly 1 core before T6, preserve source count at T6+
            if (sourceTier.index() < 6) {
                if (spec.cores() != 1) {
                    throw new IllegalArgumentException("compute high binning before T6 requires exactly 1 core, got " + spec.cores());
                }
            } else {
                // T6+: preserve existing positive core count (spec.cores() > 0)
                if (spec.cores() <= 0) {
                    throw new IllegalArgumentException("compute high binning at T6+ requires a positive core count, got " + spec.cores());
                }
            }
        } else if (DieTypes.LOGIC.equals(dieType)) {
            // Logic: no core count
            if (spec.cores() != RunSpec.NO_CORES) {
                throw new IllegalArgumentException("logic die must not have a core count, got " + spec.cores());
            }
        }

        // 7. Target tier = source + 1 (derive from transition, cap logic at 5)
        int targetTierIndex = transition.targetTier();
        if (targetTierIndex > 8) {
            throw new IllegalArgumentException("target tier would exceed T8");
        }
        if (DieTypes.LOGIC.equals(dieType) && targetTierIndex > 5) {
            throw new IllegalStateException("logic high binning target tier must not exceed 5, got T" + targetTierIndex);
        }

        // 8. Packager generation gate (gen 2+)
        if (installedGeneration < 2) {
            throw new IllegalStateException("high binning requires generation 2+ packager, got generation " + installedGeneration);
        }

        // 9. Target item availability
        if (!targetAvailable) {
            throw new IllegalStateException("target chip item not available for " + dieType + " at T" + targetTierIndex);
        }

        // 10. Explicit transition required, must match tiers exactly
        Objects.requireNonNull(transition, "transition must not be null");
        if (transition.sourceTier() != sourceTier.index()) {
            throw new IllegalArgumentException("transition source tier " + transition.sourceTier()
                + " does not match lot's source tier " + sourceTier.index());
        }
        if (transition.targetTier() != targetTierIndex) {
            throw new IllegalArgumentException("transition target tier " + transition.targetTier()
                + " does not match required target tier " + targetTierIndex);
        }

        // 11. Compute high-binning effective yield and threshold using calibrated engine
        // frozen K = spec.rollCount, y = spec.effectiveYield
        // baseWeight = YieldCalibration.baseWeight(K, y)
        // desired highbin prob = y * transition.yieldMultiplier()
        // threshold = exp(-baseWeight * RegularizedGamma.quantile(K, prob))
        // pass if cumulativeRoll > threshold
        double sourceEffectiveYield = spec.effectiveYield();
        int rollCount = spec.rollCount();
        double yieldMultiplier = transition.yieldMultiplier();

        double highBinProb = sourceEffectiveYield * yieldMultiplier;

        // For penalty 0 all fail; for penalty 1 EXACTLY match existing normal YieldMath.passes
        double threshold;
        if (highBinProb <= 0.0) {
            threshold = 1.0; // All rolls in (0,1] fail against threshold 1.0 (strict >)
        } else if (yieldMultiplier == 1.0) {
            // Penalty 1: match YieldMath.passes(cumulative, sourceEffectiveYield) exactly
            // YieldMath.passes uses threshold = 1 - yield
            threshold = 1.0 - sourceEffectiveYield;
        } else {
            double baseWeight = YieldCalibration.baseWeight(rollCount, sourceEffectiveYield);
            double quantile = RegularizedGamma.quantile(rollCount, highBinProb);
            threshold = StrictMath.exp(-baseWeight * quantile);
        }

        // 12. Judge each wafer using original cumulative rolls against the tighter threshold
        List<Boolean> passFlags = new ArrayList<>(lot.size());
        for (int i = 0; i < lot.size(); i++) {
            float cumulativeRoll = lot.wafers().get(i).cumulativeRoll();
            boolean passes = cumulativeRoll > threshold;
            passFlags.add(passes);
        }

        // 13. Build the plan preserving the source recipe amounts
        return new PackagingPlan(passFlags, chipsPerWafer, packagesPerWafer);
    }

    /**
     * Immutable transition configuration for one high-binning step.
     *
     * <p>A transition defines the yield multiplier and cost multipliers for promoting
     * wafers from one tier to the next. All values are validated on construction with
     * finite bounds. The target tier must be exactly {@code sourceTier + 1}. The yield
     * multiplier is in {@code (0, 1]} (a penalty), and the ticks/energy multipliers are
     * {@code >= 1} with overflow-checked scaled integer costs using ceiling arithmetic.</p>
     *
     * <p>Configuration and resource parsing belongs to the coordinator/native worker
     * layer; this record is pure core and free of Minecraft dependencies.</p>
     *
     * @param sourceTier       the source tier index (1-7), at least 1
     * @param targetTier       the target tier index, must equal {@code sourceTier + 1}
     * @param yieldMultiplier  the yield penalty multiplier in {@code (0, 1]}
     * @param ticksMultiplier  the tick cost multiplier, at least 1
     * @param energyMultiplier the energy cost multiplier, at least 1
     */
    public record Transition(int sourceTier, int targetTier, double yieldMultiplier,
                             double ticksMultiplier, double energyMultiplier) {

        private static final double MIN_YIELD_MULTIPLIER = 0.0;
        private static final double MAX_YIELD_MULTIPLIER = 1.0;
        private static final double MIN_COST_MULTIPLIER = 1.0;
        private static final double MAX_COST_MULTIPLIER = 1000.0;

        /**
         * Validates the transition parameters.
         *
         * @throws IllegalArgumentException if any parameter is out of bounds
         */
        public Transition {
            if (sourceTier < 1 || sourceTier > 7) {
                throw new IllegalArgumentException("sourceTier must be in [1, 7], got " + sourceTier);
            }
            if (targetTier != sourceTier + 1) {
                throw new IllegalArgumentException("targetTier must be sourceTier + 1, got source="
                    + sourceTier + " target=" + targetTier);
            }
            if (!Double.isFinite(yieldMultiplier) || yieldMultiplier <= MIN_YIELD_MULTIPLIER
                || yieldMultiplier > MAX_YIELD_MULTIPLIER) {
                throw new IllegalArgumentException("yieldMultiplier must be in (0, 1], got " + yieldMultiplier);
            }
            if (!Double.isFinite(ticksMultiplier) || ticksMultiplier < MIN_COST_MULTIPLIER
                || ticksMultiplier > MAX_COST_MULTIPLIER) {
                throw new IllegalArgumentException("ticksMultiplier must be in [1, " + MAX_COST_MULTIPLIER + "], got " + ticksMultiplier);
            }
            if (!Double.isFinite(energyMultiplier) || energyMultiplier < MIN_COST_MULTIPLIER
                || energyMultiplier > MAX_COST_MULTIPLIER) {
                throw new IllegalArgumentException("energyMultiplier must be in [1, " + MAX_COST_MULTIPLIER + "], got " + energyMultiplier);
            }
        }

        /**
         * Returns the scaled integer tick cost for a wafer, given the base ticks per wafer.
         * Uses ceiling scaling with overflow check: throws if the result exceeds {@link Integer#MAX_VALUE}.
         *
         * @param baseTicksPerWafer the base tick cost per wafer, at least 1
         * @return the scaled tick cost
         * @throws ArithmeticException if the result overflows
         * @throws IllegalArgumentException if baseTicksPerWafer < 1
         */
        public int scaledTicksPerWafer(int baseTicksPerWafer) {
            if (baseTicksPerWafer < 1) {
                throw new IllegalArgumentException("baseTicksPerWafer must be >= 1, got " + baseTicksPerWafer);
            }
            // Ceil: (base * mult) rounded up
            // Use long for intermediate to detect overflow before it happens
            // ceil(a * b) = floor(a * b + (1 - epsilon))
            // But simpler: compute as long and check
            double exact = baseTicksPerWafer * ticksMultiplier;
            long result = (long) StrictMath.ceil(exact);
            if (result > Integer.MAX_VALUE) {
                throw new ArithmeticException("scaled ticks per wafer overflow: " + result);
            }
            return (int) result;
        }

        /**
         * Returns the scaled integer energy cost per tick, given the base energy per tick.
         * Uses ceiling scaling with overflow check: throws if the result exceeds {@link Integer#MAX_VALUE}.
         *
         * @param baseEnergyPerTick the base energy cost per tick, zero or more
         * @return the scaled energy cost per tick
         * @throws ArithmeticException if the result overflows
         * @throws IllegalArgumentException if baseEnergyPerTick < 0
         */
        public int scaledEnergyPerTick(int baseEnergyPerTick) {
            if (baseEnergyPerTick < 0) {
                throw new IllegalArgumentException("baseEnergyPerTick must be >= 0, got " + baseEnergyPerTick);
            }
            double exact = baseEnergyPerTick * energyMultiplier;
            long result = (long) StrictMath.ceil(exact);
            if (result > Integer.MAX_VALUE) {
                throw new ArithmeticException("scaled energy per tick overflow: " + result);
            }
            return (int) result;
        }

        @Override
        public String toString() {
            return "Transition[source=T" + sourceTier + ", target=T" + targetTier
                + ", yieldMul=" + yieldMultiplier + ", ticksMul=" + ticksMultiplier
                + ", energyMul=" + energyMultiplier + "]";
        }
    }
}
