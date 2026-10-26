package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThermalProfile}: validation of every record, determinism of the seeded
 * equipment response, causal knob behavior, the curve's shape and endpoints, the quality
 * scoring's perfect and poor limits, solubility of every seeded response against the default
 * bands, and the immutability of the reported problems.
 */
class ThermalProfileTest {

    private static final ThermalProfile.Controls NOMINAL =
        new ThermalProfile.Controls(50, 50, 50);

    private static final ThermalProfile.Rules DEFAULTS = ThermalProfile.Rules.defaults();

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("knobs are bounded to 0..100")
        void controlsAreBounded() {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(-1, 50, 50)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(101, 50, 50)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(50, -1, 50)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(50, 101, 50)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(50, 50, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Controls(50, 50, 101)),
                () -> assertNotNull(new ThermalProfile.Controls(0, 100, 0)));
        }

        @Test
        @DisplayName("scales must be finite and positive, the peak offset finite")
        void responseIsValidated() {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(0.0, 0.0, 1.0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(-1.0, 0.0, 1.0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(Double.NaN, 0.0, 1.0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(Double.POSITIVE_INFINITY, 0.0, 1.0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(1.0, 0.0, 0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(1.0, 0.0, Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Response(1.0, Double.NEGATIVE_INFINITY, 1.0)),
                () -> assertNotNull(new ThermalProfile.Response(1.0, -5.0, 1.0)),
                () -> assertNotNull(new ThermalProfile.Response(1.0, -1000.0, 1.0)));
        }

        @Test
        @DisplayName("metrics must be finite")
        void metricsAreFinite() {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Metrics(Double.NaN, 200, 50, 2)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Metrics(2, Double.NaN, 50, 2)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Metrics(2, 200, 50, Double.NaN)));
        }

        @Test
        @DisplayName("bands must rise strictly and stay non-negative; ramps and cooling strictly positive")
        void rulesAreValidated() {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 1, 230, 245, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(3, 1, 230, 245, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 245, 230, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, 70, 30, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, 30, 70, 4, 1, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(0, 3, 230, 245, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, 30, 70, 0, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(-1, 3, 230, 245, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(Double.NaN, 3, 230, 245, 30, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, -1, 70, 1, 4, 750)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, 30, 70, 1, 4, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> new ThermalProfile.Rules(1, 3, 230, 245, 30, 70, 1, 4, 1001)),
                () -> assertNotNull(new ThermalProfile.Rules(1, 3, 230, 245, 0, 70, 1, 4, 0)),
                () -> assertNotNull(new ThermalProfile.Rules(1, 3, 230, 245, 30, 70, 1, 4, 1000)));
        }

        @Test
        @DisplayName("defaults carry the illustrative gameplay bands")
        void defaultsCheck() {
            ThermalProfile.Rules rules = ThermalProfile.Rules.defaults();
            assertAll(
                () -> assertEquals(1.0, rules.minRamp()),
                () -> assertEquals(3.0, rules.maxRamp()),
                () -> assertEquals(230.0, rules.minPeak()),
                () -> assertEquals(245.0, rules.maxPeak()),
                () -> assertEquals(30.0, rules.minDwell()),
                () -> assertEquals(70.0, rules.maxDwell()),
                () -> assertEquals(1.0, rules.minCooling()),
                () -> assertEquals(4.0, rules.maxCooling()),
                () -> assertEquals(750, rules.minimumQuality()));
        }

        @Test
        @DisplayName("nominal response is 1, 0, 1 and the curve needs at least two samples")
        void nominalAndSampleValidation() {
            assertEquals(new ThermalProfile.Response(1.0, 0.0, 1.0),
                ThermalProfile.Response.nominal());
            assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                    () -> ThermalProfile.curve(NOMINAL, ThermalProfile.Response.nominal(), 1)),
                () -> assertThrows(IllegalArgumentException.class,
                    () -> ThermalProfile.curve(NOMINAL, ThermalProfile.Response.nominal(), 0)),
                () -> assertThrows(NullPointerException.class,
                    () -> ThermalProfile.assess(null, ThermalProfile.Response.nominal(), DEFAULTS)),
                () -> assertThrows(NullPointerException.class,
                    () -> ThermalProfile.metrics(null, ThermalProfile.Response.nominal())));
        }
    }

    @Nested
    @DisplayName("the metrics model")
    class MetricsModel {

        @Test
        @DisplayName("nominal 50/50/50 gives ramp 2, peak 237.5, dwell 50, cooling 2")
        void nominalMetrics() {
            ThermalProfile.Metrics m = ThermalProfile.metrics(NOMINAL,
                ThermalProfile.Response.nominal());
            assertAll(
                () -> assertEquals(2.0, m.ramp(), 1e-9),
                () -> assertEquals(237.5, m.peak(), 1e-9),
                () -> assertEquals(50.0, m.dwell(), 1e-9),
                () -> assertEquals(2.0, m.cooling(), 1e-9));
        }

        @Test
        @DisplayName("knobs act causally: heater up raises ramp and peak, conveyor up lowers dwell and peak, airflow up raises cooling")
        void knobsActCausally() {
            ThermalProfile.Metrics base = ThermalProfile.metrics(NOMINAL,
                ThermalProfile.Response.nominal());
            ThermalProfile.Metrics hot = ThermalProfile.metrics(
                new ThermalProfile.Controls(60, 50, 50), ThermalProfile.Response.nominal());
            ThermalProfile.Metrics slow = ThermalProfile.metrics(
                new ThermalProfile.Controls(50, 60, 50), ThermalProfile.Response.nominal());
            ThermalProfile.Metrics windy = ThermalProfile.metrics(
                new ThermalProfile.Controls(50, 50, 60), ThermalProfile.Response.nominal());
            assertAll(
                () -> assertTrue(hot.ramp() > base.ramp()),
                () -> assertTrue(hot.peak() > base.peak()),
                () -> assertTrue(slow.dwell() < base.dwell()),
                () -> assertTrue(slow.peak() < base.peak()),
                () -> assertTrue(windy.cooling() > base.cooling()));
        }

        @Test
        @DisplayName("the seeded response is deterministic and small")
        void responseIsDeterministicAndSmall() {
            ThermalProfile.Response a = ThermalProfile.Response.of(42);
            ThermalProfile.Response b = ThermalProfile.Response.of(42);
            assertEquals(a, b, "the same seed must rebuild the same response");
            assertEquals(Double.doubleToLongBits(a.heatingScale()),
                Double.doubleToLongBits(ThermalProfile.Response.of(42).heatingScale()));

            for (long seed = 0; seed <= 50; seed++) {
                ThermalProfile.Response r = ThermalProfile.Response.of(seed);
                assertTrue(r.heatingScale() >= 0.9 && r.heatingScale() <= 1.1,
                    "seed " + seed + " heatingScale " + r.heatingScale());
                assertTrue(r.coolingScale() >= 0.9 && r.coolingScale() <= 1.1,
                    "seed " + seed + " coolingScale " + r.coolingScale());
                assertTrue(Math.abs(r.peakOffset()) <= 5.0,
                    "seed " + seed + " peakOffset " + r.peakOffset());
            }
            assertNotEquals(ThermalProfile.Response.of(1), ThermalProfile.Response.of(2),
                "different seeds should differ");
        }

        @Test
        @DisplayName("every seeded response admits a reachable acceptable schedule")
        void seededResponsesStaySoluble() {
            for (long seed = 1; seed <= 20; seed++) {
                ThermalProfile.Response response = ThermalProfile.Response.of(seed);
                ThermalProfile.Assessment a = ThermalProfile.assess(
                    new ThermalProfile.Controls(50, 50, 67), response, DEFAULTS);
                assertTrue(a.acceptable(), "seed " + seed + " rejected the mid-range schedule: "
                    + a.metrics() + " " + a.problems());
            }
        }

        @Test
        @DisplayName("a coarse grid search always finds an acceptable schedule")
        void gridSearchAlwaysFindsASolution() {
            for (long seed = 1; seed <= 10; seed++) {
                ThermalProfile.Response response = ThermalProfile.Response.of(seed);
                boolean found = false;
                for (int heater = 0; heater <= 100 && !found; heater += 10) {
                    for (int conveyor = 0; conveyor <= 100 && !found; conveyor += 10) {
                        for (int airflow = 0; airflow <= 100; airflow += 10) {
                            ThermalProfile.Assessment a = ThermalProfile.assess(
                                new ThermalProfile.Controls(heater, conveyor, airflow),
                                response, DEFAULTS);
                            if (a.acceptable()) {
                                found = true;
                            }
                        }
                    }
                }
                assertTrue(found, "seed " + seed + " had no acceptable schedule on the grid");
            }
        }
    }

    @Nested
    @DisplayName("the temperature curve")
    class Curve {

        @Test
        @DisplayName("the curve has the requested length, all finite")
        void curveLengthAndFiniteness() {
            double[] curve = ThermalProfile.curve(NOMINAL, ThermalProfile.Response.nominal(), 101);
            assertEquals(101, curve.length);
            for (int i = 0; i < curve.length; i++) {
                assertTrue(Double.isFinite(curve[i]), "sample " + i + " was not finite");
            }
        }

        @Test
        @DisplayName("both endpoints are ambient")
        void curveEndpointsAreAmbient() {
            double[] curve = ThermalProfile.curve(NOMINAL, ThermalProfile.Response.nominal(), 7);
            assertEquals(ThermalProfile.AMBIENT, curve[0], 1e-9);
            assertEquals(ThermalProfile.AMBIENT, curve[curve.length - 1], 1e-9);
        }

        @Test
        @DisplayName("the curve heats, plateaus at the peak, then cools")
        void curvePhases() {
            ThermalProfile.Metrics m = ThermalProfile.metrics(NOMINAL,
                ThermalProfile.Response.nominal());
            double[] curve = ThermalProfile.curve(NOMINAL, ThermalProfile.Response.nominal(), 201);

            int peakIndex = 0;
            for (int i = 1; i < curve.length; i++) {
                if (curve[i] > curve[peakIndex]) {
                    peakIndex = i;
                }
            }
            assertEquals(m.peak(), curve[peakIndex], 1e-9, "the maximum must be the peak");
            assertTrue(peakIndex > 0 && peakIndex < curve.length - 1,
                "the peak must sit strictly inside the cycle");

            for (int i = 1; i <= peakIndex; i++) {
                assertTrue(curve[i] >= curve[i - 1], "heating fell at sample " + i);
            }
            for (int i = peakIndex + 1; i < curve.length; i++) {
                assertTrue(curve[i] <= curve[i - 1], "cooling rose at sample " + i);
            }
        }

        @Test
        @DisplayName("a zero dwell collapses the plateau without a division by zero")
        void zeroDwellPhaseStillRenders() {
            double[] curve = ThermalProfile.curve(new ThermalProfile.Controls(50, 100, 50),
                ThermalProfile.Response.nominal(), 33);
            assertEquals(33, curve.length);
            for (double value : curve) {
                assertTrue(Double.isFinite(value), "zero-dwell curve had a non-finite sample");
            }
            double max = Double.NEGATIVE_INFINITY;
            for (double value : curve) {
                max = Math.max(max, value);
            }
            assertEquals(232.5, max, 1e-9, "the zero-dwell curve must still reach the peak");
        }

        @Test
        @DisplayName("hot spots from the seeded response carry into the curve")
        void curveTracksThePeakFormula() {
            for (long seed = 1; seed <= 10; seed++) {
                ThermalProfile.Response response = ThermalProfile.Response.of(seed);
                double[] curve = ThermalProfile.curve(
                    new ThermalProfile.Controls(50, 50, 67), response, 101);
                double max = Double.NEGATIVE_INFINITY;
                for (double value : curve) {
                    max = Math.max(max, value);
                }
                ThermalProfile.Metrics m = ThermalProfile.metrics(
                    new ThermalProfile.Controls(50, 50, 67), response);
                assertEquals(m.peak(), max, 1e-9, "seed " + seed + " curve missed its peak");
            }
        }
    }

    @Nested
    @DisplayName("scoring")
    class Scoring {

        @Test
        @DisplayName("a schedule at every band midpoint scores 1000, is acceptable, and has no problems")
        void perfectSchedule() {
            ThermalProfile.Assessment a = ThermalProfile.assess(
                new ThermalProfile.Controls(50, 50, 67),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertAll(
                () -> assertEquals(1000, a.quality()),
                () -> assertTrue(a.acceptable()),
                () -> assertTrue(a.problems().isEmpty()));
        }

        @Test
        @DisplayName("the nominal 50/50/50 schedule is acceptable against the defaults")
        void nominalIsAcceptable() {
            ThermalProfile.Assessment a = ThermalProfile.assess(NOMINAL,
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertAll(
                () -> assertTrue(a.acceptable()),
                () -> assertTrue(a.problems().isEmpty()),
                () -> assertTrue(a.quality() >= DEFAULTS.minimumQuality()));
        }

        @Test
        @DisplayName("one band miss lists exactly that problem and blocks acceptance")
        void singleProblemsAreExact() {
            ThermalProfile.Assessment hot = ThermalProfile.assess(
                new ThermalProfile.Controls(100, 50, 100),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertEquals(Set.of(ThermalProfile.Problem.PEAK), hot.problems());
            assertFalse(hot.acceptable());

            ThermalProfile.Assessment fast = ThermalProfile.assess(
                new ThermalProfile.Controls(50, 100, 50),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertEquals(Set.of(ThermalProfile.Problem.DWELL), fast.problems());
            assertFalse(fast.acceptable());

            // The defaults' ramp band bottom (1.0) is exactly what heater 0 produces, so the
            // HEATING case needs a genuinely narrower band to be reachable.
            ThermalProfile.Rules tighter = new ThermalProfile.Rules(
                1.5, 3.0, 230, 245, 30, 70, 1, 4, 750);
            ThermalProfile.Assessment sluggish = ThermalProfile.assess(
                new ThermalProfile.Controls(0, 50, 50),
                ThermalProfile.Response.nominal(), tighter);
            assertEquals(Set.of(ThermalProfile.Problem.HEATING), sluggish.problems());
            assertFalse(sluggish.acceptable());

            ThermalProfile.Assessment still = ThermalProfile.assess(
                new ThermalProfile.Controls(50, 50, 0),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertEquals(Set.of(ThermalProfile.Problem.COOLING), still.problems());
            assertFalse(still.acceptable());
        }

        @Test
        @DisplayName("a schedule that misses every band scores near zero and is not acceptable")
        void poorScheduleScoresPoorly() {
            ThermalProfile.Assessment a = ThermalProfile.assess(
                new ThermalProfile.Controls(0, 100, 0),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertAll(
                () -> assertTrue(a.quality() < 400, "expected a poor score, got " + a.quality()),
                () -> assertEquals(Set.of(ThermalProfile.Problem.PEAK,
                    ThermalProfile.Problem.DWELL, ThermalProfile.Problem.COOLING), a.problems()),
                () -> assertFalse(a.acceptable()));
        }

        @Test
        @DisplayName("quality decays with distance from the band midpoints")
        void qualityTracksMidpointDistance() {
            ThermalProfile.Assessment centered = ThermalProfile.assess(
                new ThermalProfile.Controls(50, 50, 67),
                ThermalProfile.Response.nominal(), DEFAULTS);
            ThermalProfile.Assessment offRamp = ThermalProfile.assess(
                new ThermalProfile.Controls(40, 50, 67),
                ThermalProfile.Response.nominal(), DEFAULTS);
            ThermalProfile.Assessment farRamp = ThermalProfile.assess(
                new ThermalProfile.Controls(30, 50, 67),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertAll(
                () -> assertTrue(offRamp.quality() < centered.quality(),
                    "a cooler ramp scored " + offRamp.quality() + " against " + centered.quality()),
                () -> assertTrue(farRamp.quality() < offRamp.quality(),
                    "an even cooler ramp scored " + farRamp.quality() + " against "
                        + offRamp.quality()));
        }

        @Test
        @DisplayName("problems alone do not make a run acceptable; minimum quality gates it")
        void minimumQualityGatesWithoutProblems() {
            ThermalProfile.Controls schedule = new ThermalProfile.Controls(50, 50, 64);
            // Bands centered on this schedule's nominal metrics, so it has no problems.
            ThermalProfile.Rules centered = new ThermalProfile.Rules(
                1.0, 3.0, 227.5, 247.5, 40.0, 60.0, 1.8, 2.8, 0);
            ThermalProfile.Assessment a = ThermalProfile.assess(schedule,
                ThermalProfile.Response.nominal(), centered);
            assertAll(
                () -> assertTrue(a.problems().isEmpty()),
                () -> assertTrue(a.quality() < 1000),
                () -> assertTrue(a.quality() + 1 <= 1000));

            ThermalProfile.Rules oneBelowLimit = new ThermalProfile.Rules(
                1.0, 3.0, 227.5, 247.5, 40.0, 60.0, 1.8, 2.8, a.quality() + 1);
            ThermalProfile.Assessment rejected = ThermalProfile.assess(schedule,
                ThermalProfile.Response.nominal(), oneBelowLimit);
            assertAll(
                () -> assertTrue(rejected.problems().isEmpty()),
                () -> assertFalse(rejected.acceptable()));

            ThermalProfile.Rules atLimit = new ThermalProfile.Rules(
                1.0, 3.0, 227.5, 247.5, 40.0, 60.0, 1.8, 2.8, a.quality());
            ThermalProfile.Assessment admitted = ThermalProfile.assess(schedule,
                ThermalProfile.Response.nominal(), atLimit);
            assertTrue(admitted.acceptable());
        }

        @Test
        @DisplayName("assessment is deterministic for identical inputs")
        void assessmentIsDeterministic() {
            ThermalProfile.Response response = ThermalProfile.Response.of(7);
            ThermalProfile.Assessment first = ThermalProfile.assess(NOMINAL, response, DEFAULTS);
            ThermalProfile.Assessment second = ThermalProfile.assess(NOMINAL, response, DEFAULTS);
            assertEquals(first, second);
            assertArrayEquals(
                ThermalProfile.curve(NOMINAL, response, 25),
                ThermalProfile.curve(NOMINAL, response, 25), 0.0);
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("the reported problems cannot be modified by callers")
        void problemsAreImmutable() {
            ThermalProfile.Assessment withProblems = ThermalProfile.assess(
                new ThermalProfile.Controls(0, 100, 0),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertFalse(withProblems.problems().isEmpty());
            assertThrows(UnsupportedOperationException.class,
                () -> withProblems.problems().add(ThermalProfile.Problem.PEAK));

            ThermalProfile.Assessment clean = ThermalProfile.assess(
                new ThermalProfile.Controls(50, 50, 67),
                ThermalProfile.Response.nominal(), DEFAULTS);
            assertThrows(UnsupportedOperationException.class,
                () -> clean.problems().add(ThermalProfile.Problem.PEAK));
        }

        @Test
        @DisplayName("null problem sets are rejected, not silently accepted")
        void nullProblemsRejected() {
            assertThrows(NullPointerException.class,
                () -> new ThermalProfile.Assessment(500, true,
                    ThermalProfile.metrics(NOMINAL, ThermalProfile.Response.nominal()), null));
        }
    }
}
