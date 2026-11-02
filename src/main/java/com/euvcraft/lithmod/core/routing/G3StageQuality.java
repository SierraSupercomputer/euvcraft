package com.euvcraft.lithmod.core.routing;

import com.euvcraft.lithmod.core.t7.G3Preparation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Combines two G3 preparation steps by rounded geometric mean.
 *
 * <p>Each step contributes half the preparation-quality budget. Identical inputs
 * produce identical outputs: e.g. combining 1000 and 250 yields 500 (not 250).
 */
public final class G3StageQuality {

    private G3StageQuality() {}

    /**
     * Combines deposition and etch preparations by rounded geometric mean per index.
     *
     * @param deposition the first preparation step
     * @param etch       the second preparation step
     * @return combined preparation with geometric-mean factors and mean
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if either is none/redacted, or factor counts differ
     */
    public static G3Preparation combine(G3Preparation deposition, G3Preparation etch) {
        Objects.requireNonNull(deposition, "deposition must not be null");
        Objects.requireNonNull(etch, "etch must not be null");

        if (!deposition.hasFactors() || !etch.hasFactors()) {
            throw new IllegalArgumentException("both preparations must have factors");
        }
        if (deposition.factorCount() != etch.factorCount()) {
            throw new IllegalArgumentException("preparation factor counts must match");
        }

        int n = deposition.factorCount();
        int combinedMean = geomMean(deposition.cleanroomMeanPermille(), etch.cleanroomMeanPermille());

        List<Integer> combinedFactors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int a = deposition.factorPermilleAt(i);
            int b = etch.factorPermilleAt(i);
            combinedFactors.add(geomMean(a, b));
        }

        return G3Preparation.of(combinedMean, combinedFactors);
    }

    private static int geomMean(int a, int b) {
        return (int) Math.round(StrictMath.sqrt((long) a * b));
    }
}