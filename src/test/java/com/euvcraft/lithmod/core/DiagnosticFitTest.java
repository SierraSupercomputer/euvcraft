package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticFitTest {

    @Test
    void constructorAcceptsValidBounds() {
        assertDoesNotThrow(() -> new DiagnosticFit(-200, -200, -200, -200, -200, -200));
        assertDoesNotThrow(() -> new DiagnosticFit(200, 200, 200, 200, 200, 200));
        assertDoesNotThrow(() -> new DiagnosticFit(0, 0, 0, 0, 0, 0));
    }

    @Test
    void constructorRejectsCandidateBelowMin() {
        for (int invalid : new int[]{-201, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(invalid, 0, 0, 0, 0, 0));
        }
    }

    @Test
    void constructorRejectsCandidateAboveMax() {
        for (int invalid : new int[]{201, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(invalid, 0, 0, 0, 0, 0));
        }
    }

    @Test
    void constructorRejectsTargetBelowMin() {
        for (int invalid : new int[]{-201, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, 0, invalid, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, 0, 0, invalid));
        }
    }

    @Test
    void constructorRejectsTargetAboveMax() {
        for (int invalid : new int[]{201, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, 0, invalid, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new DiagnosticFit(0, 0, 0, 0, 0, invalid));
        }
    }

    @Test
    void perfectFitGivesQuality100AndMultiplier1() {
        var fit = new DiagnosticFit(50, -30, 100, 50, -30, 100);
        assertEquals(100, fit.quality());
        assertEquals(1.0, fit.multiplier(0.5, 1.0));
        assertEquals(0.8, fit.multiplier(0.8, 0.8));
    }

    @Test
    void createIsDeterministicAndTargetsInRange() {
        for (long seed = 0; seed < 100; seed++) {
            var f1 = DiagnosticFit.create(seed);
            var f2 = DiagnosticFit.create(seed);
            assertEquals(f1, f2, "seed " + seed + " not deterministic");
            assertTrue(f1.targetA() >= -100 && f1.targetA() <= 100, "targetA out of range");
            assertTrue(f1.targetB() >= -100 && f1.targetB() <= 100, "targetB out of range");
            assertTrue(f1.targetC() >= -100 && f1.targetC() <= 100, "targetC out of range");
            assertEquals(0, f1.a());
            assertEquals(0, f1.b());
            assertEquals(0, f1.c());
        }
    }

    @Test
    void createProducesVariedTargetsAcrossSeeds() {
        var targets = new java.util.HashSet<String>();
        for (long seed = 0; seed < 1000; seed++) {
            var f = DiagnosticFit.create(seed);
            targets.add(f.targetA() + "," + f.targetB() + "," + f.targetC());
        }
        assertTrue(targets.size() > 500, "targets not sufficiently varied: " + targets.size());
    }

    @Test
    void imperfectFitQualityBetweenZeroAnd100() {
        var fit = new DiagnosticFit(0, 0, 0, 100, 0, 0);
        int q = fit.quality();
        assertTrue(q >= 0 && q <= 100, "quality out of range: " + q);
    }

    @Test
    void multiplierRejectsNonFiniteMin() {
        var fit = DiagnosticFit.create(42);
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(Double.POSITIVE_INFINITY, 1.0));
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(Double.NEGATIVE_INFINITY, 1.0));
    }

    @Test
    void multiplierRejectsNonFiniteMax() {
        var fit = DiagnosticFit.create(42);
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.5, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.5, Double.NEGATIVE_INFINITY));
    }

    @Test
    void multiplierRejectsMinNotPositive() {
        var fit = DiagnosticFit.create(42);
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(-0.1, 1.0));
    }

    @Test
    void multiplierRejectsMaxAboveOne() {
        var fit = DiagnosticFit.create(42);
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.5, 1.1));
    }

    @Test
    void multiplierRejectsMinAboveMax() {
        var fit = DiagnosticFit.create(42);
        assertThrows(IllegalArgumentException.class, () -> fit.multiplier(0.8, 0.7));
    }

    @Test
    void plotReturnsNewStateWithClampedCandidatesAndRetainsTargets() {
        var original = new DiagnosticFit(0, 0, 0, 50, -30, 100);
        var plotted = original.plot(250, -250, 300);
        assertEquals(200, plotted.a());
        assertEquals(-200, plotted.b());
        assertEquals(200, plotted.c());
        assertEquals(50, plotted.targetA());
        assertEquals(-30, plotted.targetB());
        assertEquals(100, plotted.targetC());
        assertNotSame(original, plotted);
    }

    @Test
    void plotDoesNotMutateOriginal() {
        var original = new DiagnosticFit(0, 0, 0, 50, -30, 100);
        original.plot(10, 20, 30);
        assertEquals(0, original.a());
        assertEquals(0, original.b());
        assertEquals(0, original.c());
    }

    @Test
    void targetAtAndCandidateAtUseHundredthPrecision() {
        var fit = new DiagnosticFit(123, -45, 67, 100, -200, 50);
        double t = fit.targetAt(1.0); // 100/100*1 + -200/100*1 + 50/100 = 1.0 - 2.0 + 0.5 = -0.5
        double c = fit.candidateAt(1.0); // 123/100*1 + -45/100*1 + 67/100 = 1.23 - 0.45 + 0.67 = 1.45
        assertEquals(-0.5, t, 1e-9);
        assertEquals(1.45, c, 1e-9);
    }

    @Test
    void qualityFormulaMatchesOracleAtSamplePoints() {
        // Perfect fit: MSE = 0, rmse = 0, quality = round(100 * (1 - 0/4)) = 100
        var perfect = new DiagnosticFit(100, -200, 50, 100, -200, 50);
        assertEquals(100, perfect.quality());

        // Completely wrong: target y = 1*x^2, candidate y = 0 => diff = x^2
        // MSE = mean(x^4) over [-2,2] step 0.1
        // x^4 at x=2 is 16, at x=0 is 0. Average roughly (1/41)*sum(x^4)
        // We don't test exact value, just that it's in range
        var zero = new DiagnosticFit(0, 0, 0, 100, 0, 0);
        int q = zero.quality();
        assertTrue(q >= 0 && q < 100, "unexpected quality: " + q);
    }

    @Test
    void coefficientsAreHundredthsPrecision() {
        var fit = new DiagnosticFit(1, 2, 3, 4, 5, 6);
        // At x=1: 1/100 + 2/100 + 3/100 = 0.06
        assertEquals(0.06, fit.candidateAt(1.0), 1e-9);
        // At x=2: 1/100*4 + 2/100*2 + 3/100 = 0.04 + 0.04 + 0.03 = 0.11
        assertEquals(0.11, fit.candidateAt(2.0), 1e-9);
    }

    @Test
    void qualityUsesExactly41Samples() {
        var fit = new DiagnosticFit(0, 0, 0, 100, 0, 0);
        // Target y = x^2 (A=1), candidate y = 0.
        // Diff = x^2, squared diff = x^4.
        // 41 samples: x = -2.0, -1.9, ..., 1.9, 2.0 (step 0.1)
        // Sum of x^4 = 2 * sum_{i=1..20} (i/10)^4 = 2 * 722666 / 10000 = 144.5332
        // MSE = 144.5332 / 41 ≈ 3.5252
        // RMSE ≈ 1.8776
        // raw = 100 * (1 - 1.8776/4) ≈ 53.06
        // round = 53
        int q = fit.quality();
        assertEquals(53, q, "quality formula or sample count changed");
    }

    @Test
    void multiplierFormulaWithKnownQuality() {
        // Perfect fit: quality = 100
        var perfect = new DiagnosticFit(10, 20, 30, 10, 20, 30);
        assertEquals(100, perfect.quality());
        assertEquals(1.0, perfect.multiplier(0.5, 1.0));
        assertEquals(0.75, new DiagnosticFit(0,0,200,0,0,0).multiplier(0.5,1.0));
        assertEquals(0.5, perfect.multiplier(0.5, 0.5));

        // Imperfect fit with known quality
        var imperfect = new DiagnosticFit(0, 0, 0, 100, 0, 0);
        int q = imperfect.quality(); // 53
        double expected = 0.5 + (1.0 - 0.5) * q / 100.0;
        assertEquals(expected, imperfect.multiplier(0.5, 1.0), 1e-9);
    }
}
