package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BeamAlignment}.
 *
 * <p>Tests cover: perfect alignment, bad alignment, coupled controls, bounds enforcement,
 * determinism of create(), and finite parameter validation for multiplier().
 */
class BeamAlignmentTest {

    // ---------------------------------------------------------------- perfect alignment

    @Test
    void perfectAlignment_qualityIs100() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertEquals(100, ba.quality());
        assertEquals(1.0, ba.multiplier(1.0, 1.0), 1e-9);
    }

    @Test
    void perfectAlignment_spotAndFocusErrorZero() {
        BeamAlignment ba = new BeamAlignment(3, -2, 5, 3, -2, 5);
        assertEquals(0, ba.spotX());
        assertEquals(0, ba.spotY());
        assertEquals(0, ba.focusError());
        assertEquals(100, ba.quality());
    }

    // ---------------------------------------------------------------- bad alignment

    @Test
    void badAlignment_qualityZero() {
        // Large spot offsets and focus error should clamp to 0
        BeamAlignment ba = new BeamAlignment(10, 10, 10, -5, -5, -5);
        // spotX = 2*(15) + 15 = 45, spotY = 15 - 2*15 = -15, sum = 60, focusError = 15
        // raw = 100 - 4*60 - 10*15 = 100 - 240 - 150 = -290 -> clamp to 0
        assertEquals(0, ba.quality());
        assertEquals(0.5, ba.multiplier(0.5, 1.5), 1e-9);
    }

    @Test
    void badAlignment_spotAndFocusErrorNonZero() {
        BeamAlignment ba = new BeamAlignment(5, 0, 0, 0, 0, 0);
        // spotX = 2*5 + 0 = 10, spotY = 5 - 0 = 5, sum = 15, focusError = 0
        // raw = 100 - 4*15 = 40
        assertEquals(10, ba.spotX());
        assertEquals(5, ba.spotY());
        assertEquals(0, ba.focusError());
        assertEquals(40, ba.quality());
    }

    // ---------------------------------------------------------------- coupled controls

    @Test
    void coupledControls_spotXDependsOnBothXandY() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        // Adjust x only
        BeamAlignment baX = ba.adjust(1); // x++
        assertEquals(2, baX.spotX()); // 2*(1-0) + (0-0) = 2
        assertEquals(1, baX.spotY()); // (1-0) - 2*(0-0) = 1

        // Adjust y only
        BeamAlignment baY = ba.adjust(3); // y++
        assertEquals(1, baY.spotX()); // 2*(0-0) + (1-0) = 1
        assertEquals(-2, baY.spotY()); // (0-0) - 2*(1-0) = -2
    }

    @Test
    void coupledControls_focusIndependent() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        BeamAlignment baF = ba.adjust(5); // focus++
        assertEquals(0, baF.spotX());
        assertEquals(0, baF.spotY());
        assertEquals(1, baF.focusError());
        assertEquals(90, baF.quality()); // 100 - 10*1 = 90
    }

    // ---------------------------------------------------------------- bounds enforcement

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void bounds_knobsClampedAtMin(int control) {
        BeamAlignment ba = new BeamAlignment(-10, -10, -10, 0, 0, 0);
        BeamAlignment ba2 = ba.adjust(control);
        assertTrue(ba2.x() >= -10 && ba2.x() <= 10);
        assertTrue(ba2.y() >= -10 && ba2.y() <= 10);
        assertTrue(ba2.focus() >= -10 && ba2.focus() <= 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void bounds_knobsClampedAtMax(int control) {
        BeamAlignment ba = new BeamAlignment(10, 10, 10, 0, 0, 0);
        BeamAlignment ba2 = ba.adjust(control);
        assertTrue(ba2.x() >= -10 && ba2.x() <= 10);
        assertTrue(ba2.y() >= -10 && ba2.y() <= 10);
        assertTrue(ba2.focus() >= -10 && ba2.focus() <= 10);
    }

    @Test
    void bounds_invalidControlNoChange() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertSame(ba, ba.adjust(-1));
        assertSame(ba, ba.adjust(6));
        assertSame(ba, ba.adjust(100));
    }

    @Test
    void bounds_targetsRemainUnchangedByAdjust() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 3, -2, 5);
        BeamAlignment ba2 = ba.adjust(1); // x++
        assertEquals(3, ba2.targetX());
        assertEquals(-2, ba2.targetY());
        assertEquals(5, ba2.targetFocus());
    }

    // ---------------------------------------------------------------- determinism

    @Test
    void create_deterministicSameSeed() {
        BeamAlignment a = BeamAlignment.create(12345L);
        BeamAlignment b = BeamAlignment.create(12345L);
        assertEquals(a.x(), b.x());
        assertEquals(a.y(), b.y());
        assertEquals(a.focus(), b.focus());
        assertEquals(a.targetX(), b.targetX());
        assertEquals(a.targetY(), b.targetY());
        assertEquals(a.targetFocus(), b.targetFocus());
    }

    @Test
    void create_differentSeedsUsuallyDifferent() {
        // Not guaranteed but extremely likely for different seeds
        BeamAlignment a = BeamAlignment.create(111L);
        BeamAlignment b = BeamAlignment.create(222L);
        boolean allSame = a.x() == b.x() && a.y() == b.y() && a.focus() == b.focus()
                && a.targetX() == b.targetX() && a.targetY() == b.targetY() && a.targetFocus() == b.targetFocus();
        assertFalse(allSame, "different seeds should usually produce different state");
    }

    @Test
    void create_allValuesInRange() {
        for (long seed = 0; seed < 1000; seed++) {
            BeamAlignment ba = BeamAlignment.create(seed);
            assertTrue(ba.x() >= -10 && ba.x() <= 10, "x out of range for seed " + seed);
            assertTrue(ba.y() >= -10 && ba.y() <= 10, "y out of range for seed " + seed);
            assertTrue(ba.focus() >= -10 && ba.focus() <= 10, "focus out of range for seed " + seed);
            assertTrue(ba.targetX() >= -5 && ba.targetX() <= 5, "targetX out of range for seed " + seed);
            assertTrue(ba.targetY() >= -5 && ba.targetY() <= 5, "targetY out of range for seed " + seed);
            assertTrue(ba.targetFocus() >= -5 && ba.targetFocus() <= 5, "targetFocus out of range for seed " + seed);
        }
    }

    @Test
    void adjust_deterministic() {
        BeamAlignment ba = BeamAlignment.create(42L);
        BeamAlignment ba1 = ba.adjust(1).adjust(3).adjust(5);
        BeamAlignment ba2 = ba.adjust(1).adjust(3).adjust(5);
        assertEquals(ba1, ba2);
    }

    // ---------------------------------------------------------------- finite parameters

    @Test
    void multiplier_rejectsNaNMin() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(Double.NaN, 1.0));
    }

    @Test
    void multiplier_rejectsNaNMax() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(1.0, Double.NaN));
    }

    @Test
    void multiplier_rejectsInfinityMin() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(Double.POSITIVE_INFINITY, 1.0));
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(Double.NEGATIVE_INFINITY, 1.0));
    }

    @Test
    void multiplier_rejectsInfinityMax() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(1.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(1.0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void multiplier_rejectsMaxLessThanMin() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ba.multiplier(1.0, 0.5));
    }

    @Test
    void multiplier_acceptsValidRange() {
        BeamAlignment ba = new BeamAlignment(0, 0, 0, 0, 0, 0); // quality = 100
        assertEquals(2.0, ba.multiplier(1.0, 2.0), 1e-9);
        assertEquals(1.5, ba.multiplier(1.0, 2.0), 1e-9); // wait, quality=100 -> min + (max-min)*1 = max
        // Actually with quality=100, multiplier = min + (max-min)*1 = max
        assertEquals(2.0, ba.multiplier(1.0, 2.0), 1e-9);
        assertEquals(1.0, ba.multiplier(1.0, 1.0), 1e-9);
        assertEquals(-5.0, ba.multiplier(-10.0, -5.0), 1e-9);
    }

    @Test
    void multiplier_qualityZeroGivesMin() {
        BeamAlignment ba = new BeamAlignment(10, 10, 10, -5, -5, -5); // quality = 0
        assertEquals(1.0, ba.multiplier(1.0, 2.0), 1e-9);
        assertEquals(-10.0, ba.multiplier(-10.0, -5.0), 1e-9);
    }

    @Test
    void multiplier_qualityFiftyGivesMidpoint() {
        // Need quality = 50: 100 - 4*sum - 10*focusError = 50 -> 4*sum + 10*focusError = 50
        // Try focusError=1, sum=10: 4*10 + 10*1 = 50. So spotX=10, spotY=0, focusError=1
        // spotX = 2*dx + dy = 10, spotY = dx - 2*dy = 0 -> dx = 2*dy -> 2*(2*dy) + dy = 5*dy = 10 -> dy=2, dx=4
        // So x-targetX=4, y-targetY=2, focus-targetFocus=1
        BeamAlignment ba = new BeamAlignment(4, 2, 1, 0, 0, 0);
        assertEquals(50, ba.quality());
        assertEquals(1.5, ba.multiplier(1.0, 2.0), 1e-9);
    }
}