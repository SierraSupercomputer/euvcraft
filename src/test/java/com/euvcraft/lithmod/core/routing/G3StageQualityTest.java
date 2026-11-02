package com.euvcraft.lithmod.core.routing;

import com.euvcraft.lithmod.core.t7.G3Preparation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class G3StageQualityTest {

    @Test
    void sameEvidenceIdempotent() {
        var prep = G3Preparation.of(500, List.of(800, 600, 400));
        var combined = G3StageQuality.combine(prep, prep);
        assertEquals(prep.cleanroomMeanPermille(), combined.cleanroomMeanPermille());
        for (int i = 0; i < prep.factorCount(); i++) {
            assertEquals(prep.factorPermilleAt(i), combined.factorPermilleAt(i));
        }
    }

    @Test
    void symmetry() {
        var dep = G3Preparation.of(800, List.of(1000, 900, 800));
        var etch = G3Preparation.of(400, List.of(500, 600, 700));
        var a = G3StageQuality.combine(dep, etch);
        var b = G3StageQuality.combine(etch, dep);
        assertEquals(a.cleanroomMeanPermille(), b.cleanroomMeanPermille());
        for (int i = 0; i < a.factorCount(); i++) {
            assertEquals(a.factorPermilleAt(i), b.factorPermilleAt(i));
        }
    }

    @Test
    void knownFactorResults() {
        // 1000 & 250 -> 500 (sqrt(250000) = 500)
        var p1 = G3Preparation.of(1000, List.of(1000));
        var p2 = G3Preparation.of(250, List.of(250));
        var c1 = G3StageQuality.combine(p1, p2);
        assertEquals(500, c1.cleanroomMeanPermille());
        assertEquals(500, c1.factorPermilleAt(0));

        // 900 & 400 -> 600 (sqrt(360000) = 600)
        var p3 = G3Preparation.of(900, List.of(900));
        var p4 = G3Preparation.of(400, List.of(400));
        var c2 = G3StageQuality.combine(p3, p4);
        assertEquals(600, c2.cleanroomMeanPermille());
        assertEquals(600, c2.factorPermilleAt(0));
    }

    @Test
    void preservesOrder() {
        var dep = G3Preparation.of(800, List.of(1000, 500, 200, 800));
        var etch = G3Preparation.of(500, List.of(800, 200, 500, 1000));
        var combined = G3StageQuality.combine(dep, etch);
        assertEquals(4, combined.factorCount());
        // order preserved: index 0 uses dep[0] & etch[0], etc.
        assertEquals(894, combined.factorPermilleAt(0)); // sqrt(1000*800) ≈ 894
        assertEquals(316, combined.factorPermilleAt(1)); // sqrt(500*200)  ≈ 316
        assertEquals(316, combined.factorPermilleAt(2)); // sqrt(200*500)  ≈ 316
        assertEquals(894, combined.factorPermilleAt(3)); // sqrt(800*1000)  ≈ 894
    }

    @Test
    void inputsImmutable() {
        var dep = G3Preparation.of(800, List.of(1000, 500));
        var etch = G3Preparation.of(500, List.of(800, 200));
        int depMeanBefore = dep.cleanroomMeanPermille();
        int etchMeanBefore = etch.cleanroomMeanPermille();
        int depFactor0Before = dep.factorPermilleAt(0);
        int etchFactor0Before = etch.factorPermilleAt(0);

        G3StageQuality.combine(dep, etch);

        assertEquals(depMeanBefore, dep.cleanroomMeanPermille());
        assertEquals(etchMeanBefore, etch.cleanroomMeanPermille());
        assertEquals(depFactor0Before, dep.factorPermilleAt(0));
        assertEquals(etchFactor0Before, etch.factorPermilleAt(0));
    }

    @Test
    void invalidNullInputs() {
        var prep = G3Preparation.of(500, List.of(500));
        assertThrows(NullPointerException.class, () -> G3StageQuality.combine(null, prep));
        assertThrows(NullPointerException.class, () -> G3StageQuality.combine(prep, null));
        assertThrows(NullPointerException.class, () -> G3StageQuality.combine(null, null));
    }

    @Test
    void invalidNoneInputs() {
        var prep = G3Preparation.of(500, List.of(500));
        var none = G3Preparation.none();
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(none, prep));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(prep, none));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(none, none));
    }

    @Test
    void invalidRedactedInputs() {
        var prep = G3Preparation.of(500, List.of(500));
        var redacted = G3Preparation.redacted();
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(redacted, prep));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(prep, redacted));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(redacted, redacted));
    }

    @Test
    void invalidMismatchedCounts() {
        var dep = G3Preparation.of(800, List.of(1000, 500));
        var etch = G3Preparation.of(500, List.of(800));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(dep, etch));

        var dep3 = G3Preparation.of(800, List.of(1000, 500, 200));
        assertThrows(IllegalArgumentException.class, () -> G3StageQuality.combine(dep, dep3));
    }

    @Test
    void roundingBoundaries() {
        // 1 & 1000 -> 32 (sqrt(1000) ≈ 31.62 -> 32)
        var p1 = G3Preparation.of(1, List.of(1));
        var p2 = G3Preparation.of(1000, List.of(1000));
        var c = G3StageQuality.combine(p1, p2);
        assertEquals(32, c.cleanroomMeanPermille());
        assertEquals(32, c.factorPermilleAt(0));

        // sqrt(999000) is 999.499..., just BELOW the half-up boundary.
        var p3 = G3Preparation.of(999, List.of(999));
        var p4 = G3Preparation.of(1000, List.of(1000));
        var c2 = G3StageQuality.combine(p3, p4);
        assertEquals(999, c2.cleanroomMeanPermille());
        assertEquals(999, c2.factorPermilleAt(0));

        // 2 & 2 -> 2 (exact)
        var p5 = G3Preparation.of(2, List.of(2));
        var c3 = G3StageQuality.combine(p5, p5);
        assertEquals(2, c3.cleanroomMeanPermille());
        assertEquals(2, c3.factorPermilleAt(0));
    }
}
