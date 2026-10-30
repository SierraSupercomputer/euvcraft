package com.euvcraft.lithmod.core.t7;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SourcePulseCalibrationTest {

    private static final SourcePulseCalibration.Spec DEFAULT_SPEC = new SourcePulseCalibration.Spec(
            0, 100, 10, 10, 0, 500, 1500
    );

    @Test
    void targetSeededReproducibility() {
        SourcePulseCalibration.Target t1 = SourcePulseCalibration.target(42L, DEFAULT_SPEC);
        SourcePulseCalibration.Target t2 = SourcePulseCalibration.target(42L, DEFAULT_SPEC);
        assertEquals(t1, t2, "Same seed must produce identical target");
    }

    @Test
    void targetDifferentSeedsProduceDifferentValues() {
        SourcePulseCalibration.Target t1 = SourcePulseCalibration.target(1L, DEFAULT_SPEC);
        SourcePulseCalibration.Target t2 = SourcePulseCalibration.target(2L, DEFAULT_SPEC);
        assertNotEquals(t1, t2, "Different seeds should produce different targets (probabilistically)");
    }

    @Test
    void targetInCentralSixtyPercent() {
        for (long seed = 0; seed < 1000; seed++) {
            SourcePulseCalibration.Target t = SourcePulseCalibration.target(seed, DEFAULT_SPEC);
            int range = DEFAULT_SPEC.max() - DEFAULT_SPEC.min() + 1;
            int centralStart = DEFAULT_SPEC.min() + (int) Math.round(range * 0.2);
            int centralEnd = DEFAULT_SPEC.max() - (int) Math.round(range * 0.2);
            assertTrue(t.phase() >= centralStart && t.phase() <= centralEnd, "Phase in central 60%");
            assertTrue(t.delay() >= centralStart && t.delay() <= centralEnd, "Delay in central 60%");
            assertTrue(t.amplitude() >= centralStart && t.amplitude() <= centralEnd, "Amplitude in central 60%");
        }
    }

    @Test
    void targetSolvabilityAt100() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.target(12345L, DEFAULT_SPEC);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(
                target.phase(), target.delay(), target.amplitude()
        );
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC);
        assertEquals(100, m.quality(), "Perfect match must yield quality 100");
        assertEquals(0, m.phaseError());
        assertEquals(0, m.delayError());
        assertEquals(0, m.amplitudeError());
    }

    @Test
    void phaseKnobPenalty() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(60, 50, 50);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC);
        assertTrue(m.quality() < 100, "Phase error must reduce quality");
        assertEquals(10, m.phaseError());
        assertEquals(0, m.delayError());
        assertEquals(0, m.amplitudeError());
    }

    @Test
    void delayKnobPenalty() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(50, 60, 50);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC);
        assertTrue(m.quality() < 100, "Delay error must reduce quality");
        assertEquals(0, m.phaseError());
        assertEquals(10, m.delayError());
        assertEquals(0, m.amplitudeError());
    }

    @Test
    void amplitudeKnobPenalty() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(50, 50, 60);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC);
        assertTrue(m.quality() < 100, "Amplitude error must reduce quality");
        assertEquals(0, m.phaseError());
        assertEquals(0, m.delayError());
        assertEquals(10, m.amplitudeError());
    }

    @Test
    void excessiveAmplitudePenalizesResponse() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.Target(50, 50, 10);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(50, 50, 100);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC);
        assertEquals(90, m.amplitudeError());
        assertTrue(m.responsePermille() > 1000, "Excessive amplitude increases response");
    }

    @Test
    void clampIntoSpecRange() {
        SourcePulseCalibration.Controls c1 = new SourcePulseCalibration.Controls(-10, 50, 150);
        SourcePulseCalibration.Controls clamped = SourcePulseCalibration.clamp(c1, DEFAULT_SPEC);
        assertEquals(0, clamped.phase());
        assertEquals(50, clamped.delay());
        assertEquals(100, clamped.amplitude());
    }

    @Test
    void clampPreservesInRangeValues() {
        SourcePulseCalibration.Controls c1 = new SourcePulseCalibration.Controls(25, 50, 75);
        SourcePulseCalibration.Controls clamped = SourcePulseCalibration.clamp(c1, DEFAULT_SPEC);
        assertEquals(c1, clamped);
    }

    @Test
    void measureRejectsTargetOutsideSpec() {
        SourcePulseCalibration.Target target = new SourcePulseCalibration.Target(-1, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(50, 50, 50);
        assertThrows(IllegalArgumentException.class, () -> SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC));
    }

    @Test
    void measureRejectsControlsOutsideSpec() {
        SourcePulseCalibration.Target target = new SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(101, 50, 50);
        assertThrows(IllegalArgumentException.class, () -> SourcePulseCalibration.measure(target, controls, DEFAULT_SPEC));
    }

    @Test
    void qualityClampedTo0To100() {
        SourcePulseCalibration.Spec tightSpec = new SourcePulseCalibration.Spec(0, 100, 1, 1, 0, 500, 1500);
        SourcePulseCalibration.Target target = new SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(100, 100, 100);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, tightSpec);
        assertTrue(m.quality() >= 0 && m.quality() <= 100);
    }

    @Test
    void responsePermilleClampedTo0To2000() {
        SourcePulseCalibration.Spec spec = new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 500, 1500);
        SourcePulseCalibration.Target target = new SourcePulseCalibration.Target(50, 50, 0);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(50, 50, 100);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, spec);
        assertTrue(m.responsePermille() >= 0 && m.responsePermille() <= 2000);
    }

    @Test
    void deterministicIndependentScoring() {
        SourcePulseCalibration.Target target = SourcePulseCalibration.Target(50, 50, 50);
        SourcePulseCalibration.Controls c1 = new SourcePulseCalibration.Controls(55, 50, 50);
        SourcePulseCalibration.Controls c2 = new SourcePulseCalibration.Controls(50, 55, 50);
        SourcePulseCalibration.Controls c3 = new SourcePulseCalibration.Controls(50, 50, 55);

        SourcePulseCalibration.Measurement m1 = SourcePulseCalibration.measure(target, c1, DEFAULT_SPEC);
        SourcePulseCalibration.Measurement m2 = SourcePulseCalibration.measure(target, c2, DEFAULT_SPEC);
        SourcePulseCalibration.Measurement m3 = SourcePulseCalibration.measure(target, c3, DEFAULT_SPEC);

        assertEquals(m1.quality(), m2.quality());
        assertEquals(m2.quality(), m3.quality());
    }

    @Test
    void multiplierPermilleInterpolation() {
        SourcePulseCalibration.Spec spec = new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 500, 1500);
        assertEquals(500, SourcePulseCalibration.multiplierPermille(0, spec));
        assertEquals(1500, SourcePulseCalibration.multiplierPermille(100, spec));
        assertEquals(1000, SourcePulseCalibration.multiplierPermille(50, spec));
        assertEquals(750, SourcePulseCalibration.multiplierPermille(25, spec));
        assertEquals(1250, SourcePulseCalibration.multiplierPermille(75, spec));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void multiplierPermilleRejectsOutOfRangeQuality(int quality) {
        assertThrows(IllegalArgumentException.class, () -> SourcePulseCalibration.multiplierPermille(quality, DEFAULT_SPEC));
    }

    @Test
    void specValidationMinMax() {
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(-1, 100, 10, 10, 0, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 101, 10, 10, 0, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(50, 50, 10, 10, 0, 500, 1500));
    }

    @Test
    void specValidationTolerances() {
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 0, 10, 0, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 101, 10, 0, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 0, 0, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 101, 0, 500, 1500));
    }

    @Test
    void specValidationQualityAndMultipliers() {
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, -1, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 101, 500, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, -1, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 1001, 1500));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 500, -1));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 500, 1001));
        assertThrows(IllegalArgumentException.class, () -> new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 1500, 500));
    }

    @Test
    void measureNoNaNOrOverflow() {
        SourcePulseCalibration.Spec spec = new SourcePulseCalibration.Spec(0, 100, 10, 10, 0, 500, 1500);
        SourcePulseCalibration.Target target = new SourcePulseCalibration.Target(0, 0, 0);
        SourcePulseCalibration.Controls controls = new SourcePulseCalibration.Controls(100, 100, 100);
        SourcePulseCalibration.Measurement m = SourcePulseCalibration.measure(target, controls, spec);
        assertTrue(m.quality() >= 0 && m.quality() <= 100);
        assertTrue(m.responsePermille() >= 0 && m.responsePermille() <= 2000);
    }
}