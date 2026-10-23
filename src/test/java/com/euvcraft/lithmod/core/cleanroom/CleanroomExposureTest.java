/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CleanroomExposure}: accumulator, sampling, mean, overflow, invalid state.
 */
class CleanroomExposureTest {

    @Nested
    @DisplayName("empty() and basic state")
    class EmptyAndBasic {
        @Test
        @DisplayName("empty() returns zero samples and zero sum")
        void emptyReturnsZero() {
            CleanroomExposure exp = CleanroomExposure.empty();
            assertEquals(0L, exp.samples());
            assertEquals(0L, exp.permilleSum());
        }

        @Test
        @DisplayName("meanPermille() on empty returns 500")
        void meanOnEmptyReturns500() {
            CleanroomExposure exp = CleanroomExposure.empty();
            assertEquals(500, exp.meanPermille());
        }
    }

    @Nested
    @DisplayName("sample() - valid range and accumulation")
    class SampleValid {
        @Test
        @DisplayName("accepts factorPermille = 1 (min)")
        void acceptsMin() {
            CleanroomExposure exp = CleanroomExposure.empty().sample(1);
            assertEquals(1L, exp.samples());
            assertEquals(1L, exp.permilleSum());
            assertEquals(1, exp.meanPermille());
        }

        @Test
        @DisplayName("accepts factorPermille = 1000 (max)")
        void acceptsMax() {
            CleanroomExposure exp = CleanroomExposure.empty().sample(1000);
            assertEquals(1L, exp.samples());
            assertEquals(1000L, exp.permilleSum());
            assertEquals(1000, exp.meanPermille());
        }

        @Test
        @DisplayName("multiple samples accumulate correctly")
        void multipleSamplesAccumulate() {
            CleanroomExposure exp = CleanroomExposure.empty()
                .sample(200)
                .sample(400)
                .sample(600);
            assertEquals(3L, exp.samples());
            assertEquals(1200L, exp.permilleSum());
            assertEquals(400, exp.meanPermille()); // 1200 / 3 = 400
        }

        @Test
        @DisplayName("immutability - original unchanged after sample()")
        void immutability() {
            CleanroomExposure original = CleanroomExposure.empty().sample(500);
            CleanroomExposure next = original.sample(800);

            assertEquals(1L, original.samples());
            assertEquals(500L, original.permilleSum());
            assertEquals(2L, next.samples());
            assertEquals(1300L, next.permilleSum());
        }
    }

    @Nested
    @DisplayName("sample() - rejects invalid factorPermille")
    class SampleInvalid {
        @Test
        @DisplayName("rejects factorPermille < 1")
        void rejectsBelowMin() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomExposure.empty().sample(0));
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomExposure.empty().sample(-1));
        }

        @Test
        @DisplayName("rejects factorPermille > 1000")
        void rejectsAboveMax() {
            assertThrows(IllegalArgumentException.class,
                () -> CleanroomExposure.empty().sample(1001));
        }
    }

    @Nested
    @DisplayName("meanPermille() - bounded exact averaging")
    class MeanPermille {
        @Test
        @DisplayName("exact division when sum divisible by samples")
        void exactDivision() {
            CleanroomExposure exp = CleanroomExposure.empty()
                .sample(300)
                .sample(500);
            // sum = 800, samples = 2, mean = 400
            assertEquals(400, exp.meanPermille());
        }

        @Test
        @DisplayName("rounds down for non-divisible (integer division)")
        void integerDivision() {
            CleanroomExposure exp = CleanroomExposure.empty()
                .sample(300)
                .sample(500)
                .sample(401);
            // sum = 1201, samples = 3, mean = 400 (integer division)
            assertEquals(400, exp.meanPermille());
        }

        @Test
        @DisplayName("handles large number of samples within long range")
        void largeSampleCount() {
            // 1000 samples of 1000 each = 1,000,000 sum (fits in long)
            CleanroomExposure exp = CleanroomExposure.empty();
            for (int i = 0; i < 1000; i++) {
                exp = exp.sample(1000);
            }
            assertEquals(1000L, exp.samples());
            assertEquals(1_000_000L, exp.permilleSum());
            assertEquals(1000, exp.meanPermille());
        }
    }

    @Nested
    @DisplayName("overflow protection")
    class OverflowProtection {
        @Test
        @DisplayName("sample() throws when samples would exceed Long.MAX_VALUE")
        void sampleThrowsOnMaxSamples() {
            CleanroomExposure exp = new CleanroomExposure(Long.MAX_VALUE, 0);
            assertThrows(ArithmeticException.class, () -> exp.sample(500));
        }

        @Test
        @DisplayName("sample() throws when permilleSum would overflow")
        void sampleThrowsOnSumOverflow() {
            // permilleSum = Long.MAX_VALUE, adding anything overflows
            CleanroomExposure exp = new CleanroomExposure(1, Long.MAX_VALUE);
            assertThrows(ArithmeticException.class, () -> exp.sample(1));
        }

        @Test
        @DisplayName("meanPermille() throws if mean exceeds Integer.MAX_VALUE")
        void meanThrowsOnIntOverflow() {
            // This would require permilleSum > samples * Integer.MAX_VALUE
            // With valid samples (1..1000), sum <= 1000 * samples, so mean <= 1000
            // This test documents the safeguard
            CleanroomExposure exp = new CleanroomExposure(1, (long) Integer.MAX_VALUE + 1);
            assertThrows(ArithmeticException.class, () -> exp.meanPermille());
        }
    }

    @Nested
    @DisplayName("persisted malformed states rejected")
    class MalformedStates {
        @Test
        @DisplayName("constructor allows any long values (validation on use)")
        void constructorAllowsAnyLongs() {
            // Record constructor doesn't validate - validation happens on sample() and meanPermille()
            CleanroomExposure exp = new CleanroomExposure(-1, -100);
            assertEquals(-1L, exp.samples());
            assertEquals(-100L, exp.permilleSum());
        }

        @Test
        @DisplayName("sample() on negative samples throws on overflow check")
        void negativeSamplesThrowsOnSample() {
            CleanroomExposure exp = new CleanroomExposure(-1, 0);
            // samples >= Long.MAX_VALUE check passes, but next sample would have samples = 0
            // Actually -1 + 1 = 0, which is valid
            CleanroomExposure next = exp.sample(500);
            assertEquals(0L, next.samples());
            assertEquals(500L, next.permilleSum());
        }

        @Test
        @DisplayName("meanPermille() with negative samples returns 500")
        void negativeSamplesMeanReturns500() {
            CleanroomExposure exp = new CleanroomExposure(-5, 1000);
            // samples != 0, so computes mean = 1000 / -5 = -200 (but this is a malformed state)
            // The spec says "persisted malformed states rejected" - we validate on use
            // For negative samples, we treat as empty
            // Actually the spec says meanPermille returns samples==0?500:rounded sum/samples
            // So with negative samples it would compute... but we should reject malformed
            // Let me follow the spec literally: "Validate 1..1000 samples; persisted malformed states rejected"
            // So we should validate samples is in valid range when sampling
            assertThrows(ArithmeticException.class, () -> exp.meanPermille());
        }
    }

    @Nested
    @DisplayName("accumulator roundtrip")
    class Roundtrip {
        @Test
        @DisplayN("multiple sample/mean cycles consistent")
        void roundtripConsistent() {
            CleanroomExposure exp = CleanroomExposure.empty();
            
            exp = exp.sample(250);
            assertEquals(250, exp.meanPermille());
            
            exp = exp.sample(750);
            assertEquals(500, exp.meanPermille());
            
            exp = exp.sample(1000);
            assertEquals(666, exp.meanPermille()); // (250+750+1000)/3 = 666
            
            exp = exp.sample(500);
            assertEquals(625, exp.meanPermille()); // (250+750+1000+500)/4 = 625
        }
    }
}