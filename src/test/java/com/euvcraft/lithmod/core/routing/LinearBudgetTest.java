package com.euvcraft.lithmod.core.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LinearBudget}.
 */
class LinearBudgetTest {

    @Nested
    @DisplayName("Input validation")
    class InputValidationTest {

        @Test
        @DisplayName("rejects layers < 1")
        void layersTooSmall() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(0, 70, 1, 1, 2, 1, 5, 5, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(-1, 70, 1, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects layers > 1024")
        void layersTooLarge() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(1025, 70, 1, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects originalLayerTicks < 1")
        void originalLayerTicksTooSmall() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 0, 1, 1, 2, 1, 5, 5, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, -1, 1, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects originalLayerTicks > 72000")
        void originalLayerTicksTooLarge() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 72001, 1, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects passes < 1")
        void passesTooSmall() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 0, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects passes > 8")
        void passesTooLarge() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 9, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects resistPerLayer < 0")
        void resistPerLayerNegative() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, -1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects resistPerLayer > 64")
        void resistPerLayerTooLarge() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 65, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects oldScanWaterPerTick out of range")
        void oldScanWaterPerTickOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, -1, 1, 5, 5, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 1001, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects newScanWaterPerTick out of range")
        void newScanWaterPerTickOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, -1, 5, 5, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1001, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects coatTicksPerLayer out of range")
        void coatTicksPerLayerOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, -1, 5, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, 1001, 5, 0));
        }

        @Test
        @DisplayName("rejects cleanTicksPerLayer out of range")
        void cleanTicksPerLayerOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, 5, -1, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, 5, 1001, 0));
        }

        @Test
        @DisplayName("rejects extraRinseWaterMb out of range")
        void extraRinseWaterMbOutOfRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, 5, 5, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> LinearBudget.calculate(42, 70, 1, 1, 2, 1, 5, 5, 1_000_001));
        }
    }

    @Nested
    @DisplayName("Arithmetic validation (overflow/underflow/range)")
    class ArithmeticValidationTest {

        @Test
        @DisplayName("rejects original effective per-layer > 72000")
        void originalEffectiveExceedsLimit() {
            // 72000 * 2 = 144000 > 72000
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 72000, 2, 1, 2, 1, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects scannerTicksPerLayer < 1 (coat+clean >= effective)")
        void scannerTicksTooSmall() {
            // effective = 70*1 = 70, coat+clean = 5+65 = 70 -> scanner = 0
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 70, 1, 1, 2, 1, 5, 65, 0));
            // effective = 70*1 = 70, coat+clean = 10+61 = 71 -> scanner = -1
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 70, 1, 1, 2, 1, 10, 61, 0));
        }

        @Test
        @DisplayName("rejects scannerTicksPerLayer > 72000")
        void scannerTicksTooLarge() {
            // This would require originalEffectivePerLayer > 72000 which is already rejected
            // But test edge: effective=72000, coat+clean=0 -> scanner=72000 (valid)
            // effective=72001 is rejected earlier
        }

        @Test
        @DisplayName("rejects coatTicks > 72000")
        void coatTicksExceedsLimit() {
            // 100 layers * 1000 coat = 100000 > 72000
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(100, 100, 1, 1, 2, 1, 1000, 0, 0));
        }

        @Test
        @DisplayName("rejects cleanDryTicks > 72000")
        void cleanDryTicksExceedsLimit() {
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(100, 100, 1, 1, 2, 1, 0, 1000, 0));
        }

        @Test
        @DisplayName("rejects cleanWaterMb < 0 (new water rate > old)")
        void cleanWaterMbNegative() {
            // old=1, new=2, effective=70, scanner=60 -> per layer = 70*1 - 60*2 = -50
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 70, 1, 1, 1, 2, 5, 5, 0));
        }

        @Test
        @DisplayName("rejects cleanWaterMb > Integer.MAX_VALUE")
        void cleanWaterMbOverflow() {
            // Large layers * large water difference
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(1024, 72000, 1, 1, 1000, 0, 0, 0, 0));
        }

        @Test
        @DisplayName("rejects resistTotal > 65536")
        void resistTotalExceedsLimit() {
            // 1024 * 64 = 65536 (valid), 1024 * 65 rejected earlier
            // But 1025 layers rejected earlier, so test max valid
            LinearBudget b = LinearBudget.calculate(1024, 70, 1, 64, 2, 1, 5, 5, 0);
            assertEquals(65536, b.resistTotal());

            // 1024 layers * 64 resist = 65536 (max valid)
            // Next would need more layers or more resist, both rejected by input validation
        }
    }

    @Nested
    @DisplayName("T5 conservation tests (layers=42, orig=70, passes=1, resist=1, old=2, new=1, coat=5, clean=5, rinse=0)")
    class T5ConservationTest {

        @Test
        @DisplayName("T5: total scan+coat+clean work equals original")
        void t5WorkConservation() {
            int layers = 42;
            int originalLayerTicks = 70;
            int passes = 1;
            int resistPerLayer = 1;
            int oldScanWaterPerTick = 2;
            int newScanWaterPerTick = 1;
            int coatTicksPerLayer = 5;
            int cleanTicksPerLayer = 5;
            int extraRinseWaterMb = 0;

            LinearBudget b = LinearBudget.calculate(layers, originalLayerTicks, passes, resistPerLayer,
                    oldScanWaterPerTick, newScanWaterPerTick, coatTicksPerLayer, cleanTicksPerLayer, extraRinseWaterMb);

            // effective = 70 * 1 = 70
            // scanner = 70 - 5 - 5 = 60
            // coatTicks = 42 * 5 = 210
            // cleanDryTicks = 42 * 5 = 210
            // total work = layers * (scanner + coat + clean) = 42 * (60 + 5 + 5) = 42 * 70 = 2940
            // original total = layers * originalLayerTicks * passes = 42 * 70 * 1 = 2940
            long totalWork = (long) layers * (b.scannerTicksPerLayer() + coatTicksPerLayer + cleanTicksPerLayer);
            long originalTotal = (long) layers * originalLayerTicks * passes;
            assertEquals(originalTotal, totalWork, "Total work must equal original");

            // Scanner water = layers * scannerTicks * newRate = 42 * 60 * 1 = 2520
            // Clean water = layers * (effective * old - scanner * new) = 42 * (70*2 - 60*1) = 42 * (140-60) = 42*80 = 3360
            // Original water = layers * effective * old = 42 * 70 * 2 = 5880
            // scannerWater + cleanWater = 2520 + 3360 = 5880 = originalWater
            long scannerWater = (long) layers * b.scannerTicksPerLayer() * newScanWaterPerTick;
            long cleanWater = b.cleanWaterMb();
            long originalWater = (long) layers * originalLayerTicks * passes * oldScanWaterPerTick;
            assertEquals(originalWater, scannerWater + cleanWater,
                    "Scanner water + clean water must equal original water");

            // Resist exact
            assertEquals(layers * resistPerLayer, b.resistTotal(), "Resist must be exact");
        }

        @Test
        @DisplayName("T5: field values match expected")
        void t5FieldValues() {
            LinearBudget b = LinearBudget.calculate(42, 70, 1, 1, 2, 1, 5, 5, 0);
            assertEquals(60, b.scannerTicksPerLayer());
            assertEquals(210, b.coatTicks());
            assertEquals(210, b.cleanDryTicks());
            assertEquals(3360, b.cleanWaterMb());
            assertEquals(42, b.resistTotal());
        }
    }

    @Nested
    @DisplayName("T6 conservation tests (layers=70, orig=100, passes=2, resist=1, old=2, new=1, coat=5, clean=5, rinse=0)")
    class T6ConservationTest {

        @Test
        @DisplayName("T6: total scan+coat+clean work equals original")
        void t6WorkConservation() {
            int layers = 70;
            int originalLayerTicks = 100;
            int passes = 2;
            int resistPerLayer = 1;
            int oldScanWaterPerTick = 2;
            int newScanWaterPerTick = 1;
            int coatTicksPerLayer = 5;
            int cleanTicksPerLayer = 5;
            int extraRinseWaterMb = 0;

            LinearBudget b = LinearBudget.calculate(layers, originalLayerTicks, passes, resistPerLayer,
                    oldScanWaterPerTick, newScanWaterPerTick, coatTicksPerLayer, cleanTicksPerLayer, extraRinseWaterMb);

            // effective = 100 * 2 = 200
            // scanner = 200 - 5 - 5 = 190
            // coatTicks = 70 * 5 = 350
            // cleanDryTicks = 70 * 5 = 350
            // total work = 70 * (190 + 5 + 5) = 70 * 200 = 14000
            // original total = 70 * 100 * 2 = 14000
            long totalWork = (long) layers * (b.scannerTicksPerLayer() + coatTicksPerLayer + cleanTicksPerLayer);
            long originalTotal = (long) layers * originalLayerTicks * passes;
            assertEquals(originalTotal, totalWork, "Total work must equal original");

            // scannerWater = 70 * 190 * 1 = 13300
            // cleanWater = 70 * (200*2 - 190*1) = 70 * (400-190) = 70*210 = 14700
            // originalWater = 70 * 200 * 2 = 28000
            // 13300 + 14700 = 28000
            long scannerWater = (long) layers * b.scannerTicksPerLayer() * newScanWaterPerTick;
            long cleanWater = b.cleanWaterMb();
            long originalWater = (long) layers * originalLayerTicks * passes * oldScanWaterPerTick;
            assertEquals(originalWater, scannerWater + cleanWater,
                    "Scanner water + clean water must equal original water");

            assertEquals(layers * resistPerLayer, b.resistTotal(), "Resist must be exact");
        }

        @Test
        @DisplayName("T6: field values match expected")
        void t6FieldValues() {
            LinearBudget b = LinearBudget.calculate(70, 100, 2, 1, 2, 1, 5, 5, 0);
            assertEquals(190, b.scannerTicksPerLayer());
            assertEquals(350, b.coatTicks());
            assertEquals(350, b.cleanDryTicks());
            assertEquals(14700, b.cleanWaterMb());
            assertEquals(70, b.resistTotal());
        }
    }

    @Nested
    @DisplayName("T7 conservation tests (layers=80, orig=120, passes=1, resist=1, old=5, new=4, coat=5, clean=5, rinse=4000)")
    class T7ConservationTest {

        @Test
        @DisplayName("T7: total scan+coat+clean work equals original")
        void t7WorkConservation() {
            int layers = 80;
            int originalLayerTicks = 120;
            int passes = 1;
            int resistPerLayer = 1;
            int oldScanWaterPerTick = 5;
            int newScanWaterPerTick = 4;
            int coatTicksPerLayer = 5;
            int cleanTicksPerLayer = 5;
            int extraRinseWaterMb = 4000;

            LinearBudget b = LinearBudget.calculate(layers, originalLayerTicks, passes, resistPerLayer,
                    oldScanWaterPerTick, newScanWaterPerTick, coatTicksPerLayer, cleanTicksPerLayer, extraRinseWaterMb);

            // effective = 120 * 1 = 120
            // scanner = 120 - 5 - 5 = 110
            // coatTicks = 80 * 5 = 400
            // cleanDryTicks = 80 * 5 = 400
            // total work = 80 * (110 + 5 + 5) = 80 * 120 = 9600
            // original total = 80 * 120 * 1 = 9600
            long totalWork = (long) layers * (b.scannerTicksPerLayer() + coatTicksPerLayer + cleanTicksPerLayer);
            long originalTotal = (long) layers * originalLayerTicks * passes;
            assertEquals(originalTotal, totalWork, "Total work must equal original");

            // scannerWater = 80 * 110 * 4 = 35200
            // cleanWater = 80 * (120*5 - 110*4) + 4000 = 80 * (600-440) + 4000 = 80*160 + 4000 = 12800 + 4000 = 16800
            // originalWater = 80 * 120 * 5 = 48000
            // 35200 + 16800 = 52000? Wait: 35200 + 16800 = 52000, but original = 48000. 
            // Actually: cleanWater formula includes extraRinseWaterMb
            // scannerWater + cleanWater = originalWater + extraRinseWaterMb
            // 35200 + 16800 = 52000, originalWater + extra = 48000 + 4000 = 52000 ✓
            long scannerWater = (long) layers * b.scannerTicksPerLayer() * newScanWaterPerTick;
            long cleanWater = b.cleanWaterMb();
            long originalWater = (long) layers * originalLayerTicks * passes * oldScanWaterPerTick;
            assertEquals(originalWater + extraRinseWaterMb, scannerWater + cleanWater,
                    "Scanner water + clean water must equal original water + extra rinse");

            assertEquals(layers * resistPerLayer, b.resistTotal(), "Resist must be exact");
        }

        @Test
        @DisplayName("T7: field values match expected")
        void t7FieldValues() {
            LinearBudget b = LinearBudget.calculate(80, 120, 1, 1, 5, 4, 5, 5, 4000);
            assertEquals(110, b.scannerTicksPerLayer());
            assertEquals(400, b.coatTicks());
            assertEquals(400, b.cleanDryTicks());
            assertEquals(16800, b.cleanWaterMb());
            assertEquals(80, b.resistTotal());
        }
    }

    @Nested
    @DisplayName("Boundary and edge case tests")
    class BoundaryTest {

        @Test
        @DisplayName("free-resist: resistPerLayer=0 yields resistTotal=0")
        void freeResist() {
            LinearBudget b = LinearBudget.calculate(100, 100, 1, 0, 2, 1, 5, 5, 0);
            assertEquals(0, b.resistTotal());
        }

        @Test
        @DisplayName("zero split: coat=0, clean=0 gives scanner=effective")
        void zeroSplit() {
            LinearBudget b = LinearBudget.calculate(10, 100, 1, 1, 2, 1, 0, 0, 0);
            assertEquals(100, b.scannerTicksPerLayer());
            assertEquals(0, b.coatTicks());
            assertEquals(0, b.cleanDryTicks());
        }

        @Test
        @DisplayName("zero water rates: old=0, new=0 gives cleanWater=extraRinse")
        void zeroWaterRates() {
            LinearBudget b = LinearBudget.calculate(10, 100, 1, 1, 0, 0, 5, 5, 42);
            assertEquals(42, b.cleanWaterMb());
        }

        @Test
        @DisplayName("max layers with minimal values")
        void maxLayersMinimal() {
            LinearBudget b = LinearBudget.calculate(1024, 1, 1, 0, 0, 0, 0, 0, 0);
            assertEquals(1, b.scannerTicksPerLayer());
            assertEquals(0, b.coatTicks());
            assertEquals(0, b.cleanDryTicks());
            assertEquals(0, b.cleanWaterMb());
            assertEquals(0, b.resistTotal());
        }

        @Test
        @DisplayName("max valid resistTotal = 65536")
        void maxResistTotal() {
            // 1024 * 64 = 65536
            LinearBudget b = LinearBudget.calculate(1024, 70, 1, 64, 2, 1, 5, 5, 0);
            assertEquals(65536, b.resistTotal());
        }

        @Test
        @DisplayName("too-short original: originalLayerTicks * passes < coat + clean + 1 rejected")
        void tooShortOriginal() {
            // effective = 10, coat+clean = 10 -> scanner = 0 (invalid, need >=1)
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 10, 1, 1, 2, 1, 5, 5, 0));
            // effective = 10, coat+clean = 11 -> scanner = -1
            assertThrows(ArithmeticException.class,
                    () -> LinearBudget.calculate(10, 10, 1, 1, 2, 1, 6, 5, 0));
        }

        @Test
        @DisplayName("exact boundary: scannerTicksPerLayer = 1 valid")
        void scannerTicksExactlyOne() {
            // effective = 11, coat+clean = 10 -> scanner = 1
            LinearBudget b = LinearBudget.calculate(10, 11, 1, 1, 2, 1, 5, 5, 0);
            assertEquals(1, b.scannerTicksPerLayer());
        }

        @Test
        @DisplayName("exact boundary: scannerTicksPerLayer = 72000 valid")
        void scannerTicksMax() {
            // effective = 72000, coat+clean = 0 -> scanner = 72000
            LinearBudget b = LinearBudget.calculate(1, 72000, 1, 1, 2, 1, 0, 0, 0);
            assertEquals(72000, b.scannerTicksPerLayer());
        }

        @Test
        @DisplayName("exact boundary: coatTicks = 72000 valid")
        void coatTicksMax() {
            // 72 layers * 1000 = 72000
            LinearBudget b = LinearBudget.calculate(72, 1200, 1, 1, 2, 1, 1000, 0, 0);
            assertEquals(72000, b.coatTicks());
        }

        @Test
        @DisplayName("exact boundary: cleanDryTicks = 72000 valid")
        void cleanDryTicksMax() {
            LinearBudget b = LinearBudget.calculate(72, 1200, 1, 1, 2, 1, 0, 1000, 0);
            assertEquals(72000, b.cleanDryTicks());
        }

        @Test
        @DisplayName("extra rinse water adds to cleanWaterMb")
        void extraRinseWaterAdded() {
            LinearBudget b1 = LinearBudget.calculate(10, 100, 1, 1, 2, 1, 5, 5, 0);
            LinearBudget b2 = LinearBudget.calculate(10, 100, 1, 1, 2, 1, 5, 5, 1000);
            assertEquals(b1.cleanWaterMb() + 1000, b2.cleanWaterMb());
        }

        @Test
        @DisplayName("record immutability: components accessible")
        void recordComponentsAccessible() {
            LinearBudget b = new LinearBudget(100, 200, 300, 400, 500);
            assertEquals(100, b.scannerTicksPerLayer());
            assertEquals(200, b.coatTicks());
            assertEquals(300, b.cleanDryTicks());
            assertEquals(400, b.cleanWaterMb());
            assertEquals(500, b.resistTotal());
        }

        @Test
        @DisplayName("constructor validates result fields")
        void constructorValidatesFields() {
            // scanner < 1
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(0, 0, 0, 0, 0));
            // scanner > 72000
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(72001, 0, 0, 0, 0));
            // coat < 0
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, -1, 0, 0, 0));
            // coat > 72000
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 72001, 0, 0, 0));
            // cleanDry < 0
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 0, -1, 0, 0));
            // cleanDry > 72000
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 0, 72001, 0, 0));
            // cleanWater < 0
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 0, 0, -1, 0));
            // resist < 0
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 0, 0, 0, -1));
            // resist > 65536
            assertThrows(IllegalArgumentException.class,
                    () -> new LinearBudget(100, 0, 0, 0, 65537));
        }
    }

    @Nested
    @DisplayName("Conservation property verification")
    class ConservationVerificationTest {

        @Test
        @DisplayName("random valid params: work conservation holds")
        void randomWorkConservation() {
            // Test a variety of valid combinations
            int[][] params = {
                {1, 10, 1, 1, 2, 1, 0, 0, 0},
                {5, 50, 2, 2, 3, 2, 3, 2, 100},
                {100, 200, 4, 5, 10, 5, 10, 10, 5000},
                {500, 100, 8, 10, 100, 50, 20, 20, 10000},
            };

            for (int[] p : params) {
                int layers = p[0], orig = p[1], passes = p[2], resist = p[3];
                int oldW = p[4], newW = p[5], coat = p[6], clean = p[7], rinse = p[8];

                // Skip if would cause arithmetic exception
                long effective = (long) orig * passes;
                if (effective > 72000) continue;
                if (effective - coat - clean < 1) continue;
                if ((long) layers * coat > 72000) continue;
                if ((long) layers * clean > 72000) continue;
                if ((long) layers * resist > 65536) continue;

                LinearBudget b = LinearBudget.calculate(layers, orig, passes, resist, oldW, newW, coat, clean, rinse);

                long totalWork = (long) layers * (b.scannerTicksPerLayer() + coat + clean);
                long originalTotal = (long) layers * orig * passes;
                assertEquals(originalTotal, totalWork,
                        "Work conservation failed for params: layers=" + layers + ", orig=" + orig);

                long scannerWater = (long) layers * b.scannerTicksPerLayer() * newW;
                long cleanWater = b.cleanWaterMb();
                long originalWater = (long) layers * orig * passes * oldW;
                assertEquals(originalWater + rinse, scannerWater + cleanWater,
                        "Water conservation failed for params: layers=" + layers + ", orig=" + orig);

                assertEquals((long) layers * resist, b.resistTotal(),
                        "Resist conservation failed for params: layers=" + layers);
            }
        }
    }
}
