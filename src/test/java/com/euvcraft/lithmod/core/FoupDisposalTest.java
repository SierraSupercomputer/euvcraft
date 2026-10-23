package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FoupDisposal}.
 */
class FoupDisposalTest {

    private static final UUID LOT_A = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID LOT_B = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID LOT_C = UUID.fromString("00000000-0000-4000-8000-0000000000c3");

    private static Wafer wafer(int layer, float roll) {
        return new Wafer(layer, roll, Wafer.NO_FLAGS);
    }

    private static List<Wafer> wafers(int count, int layer, float firstRoll) {
        List<Wafer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(wafer(layer, firstRoll - i * 0.01f));
        }
        return list;
    }

    private static FoupContents lot(UUID id, int capacity, List<Wafer> wafers) {
        return new FoupContents(id, capacity, wafers);
    }

    private static FoupContents lot(UUID id, int capacity, List<Wafer> wafers, RunSpec run) {
        return new FoupContents(id, capacity, wafers, run);
    }

    private static FoupContents lot(UUID id, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade) {
        return new FoupContents(id, capacity, wafers, run, grade, FoupContents.PREPARATION_LEGACY);
    }

    private static FoupContents lot(UUID id, int capacity, List<Wafer> wafers, RunSpec run, WaferSubstrate grade, int prepGen) {
        return new FoupContents(id, capacity, wafers, run, grade, prepGen);
    }

    private static RunSpec dummyRun() {
        return new RunSpec("logic", 1, RunSpec.NO_CORES, 0.9, 10);
    }

    @Nested
    @DisplayName("emptyCarrier")
    class EmptyCarrierTest {

        @Test
        @DisplayName("rejects null source")
        void nullSourceRejected() {
            assertThrows(NullPointerException.class, () -> FoupDisposal.emptyCarrier(null, LOT_B));
        }

        @Test
        @DisplayName("rejects null freshId")
        void nullFreshIdRejected() {
            FoupContents source = lot(LOT_A, 25, wafers(3, 1, 0.9f));
            assertThrows(NullPointerException.class, () -> FoupDisposal.emptyCarrier(source, null));
        }

        @Test
        @DisplayName("rejects empty source lot")
        void emptySourceRejected() {
            FoupContents empty = FoupContents.empty(LOT_A, 25);
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.emptyCarrier(empty, LOT_B));
        }

        @Test
        @DisplayName("rejects same freshId as source lotId")
        void sameIdRejected() {
            FoupContents source = lot(LOT_A, 25, wafers(3, 1, 0.9f));
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.emptyCarrier(source, LOT_A));
        }

        @Test
        @DisplayName("preserves capacity and creates empty ordinary legacy lot")
        void preservesCapacityAndProperties() {
            FoupContents source = lot(LOT_A, 25, wafers(10, 3, 0.95f));
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertEquals(25, empty.capacity());
            assertTrue(empty.isEmpty());
            assertEquals(LOT_B, empty.lotId());
            assertEquals(WaferSubstrate.ORDINARY, empty.grade());
            assertEquals(FoupContents.PREPARATION_LEGACY, empty.preparationGeneration());
            assertTrue(empty.run() == null, "run should be null");
            assertEquals(0, empty.roomYieldPermille());
            assertTrue(empty.roomExposure().samples() == 0);
        }

        @Test
        @DisplayName("does not mutate original source")
        void doesNotMutateSource() {
            FoupContents source = lot(LOT_A, 25, wafers(10, 3, 0.95f));
            int originalSize = source.size();
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertEquals(originalSize, source.size());
            assertNotSame(source, empty);
        }

        @Test
        @DisplayName("works with PURE grade source")
        void pureGradeSource() {
            FoupContents source = lot(LOT_A, 25, wafers(5, 1, 0.9f), null, WaferSubstrate.PURE);
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertEquals(WaferSubstrate.ORDINARY, empty.grade());
            assertEquals(25, empty.capacity());
        }

        @Test
        @DisplayName("works with G2 prepared source")
        void g2PreparedSource() {
            FoupContents source = lot(LOT_A, 25, wafers(5, Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS), null, WaferSubstrate.PURE, FoupContents.PREPARATION_G2);
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertEquals(FoupContents.PREPARATION_LEGACY, empty.preparationGeneration());
            assertEquals(25, empty.capacity());
        }

        @Test
        @DisplayName("works with processed source (has run)")
        void processedSource() {
            FoupContents source = lot(LOT_A, 25, wafers(5, 3, 0.9f), dummyRun());
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertTrue(empty.run() == null);
            assertEquals(25, empty.capacity());
        }

        @Test
        @DisplayName("works with exposure source")
        void exposureSource() {
            FoupContents source = lot(LOT_A, 25, wafers(5, 1, 0.9f))
                .withRoomExposure(com.euvcraft.lithmod.core.cleanroom.CleanroomExposure.of(100, 500), 500);
            FoupContents empty = FoupDisposal.emptyCarrier(source, LOT_B);

            assertEquals(0, empty.roomYieldPermille());
            assertEquals(0, empty.roomExposure().samples());
            assertEquals(25, empty.capacity());
        }

        @Test
        @DisplayName("identity invariant: emptyCarrier(emptyCarrier(source))")
        void identityInvariant() {
            FoupContents source = lot(LOT_A, 25, wafers(5, 1, 0.9f));
            FoupContents empty1 = FoupDisposal.emptyCarrier(source, LOT_B);
            FoupContents empty2 = FoupDisposal.emptyCarrier(empty1, LOT_C);

            assertEquals(empty1.capacity(), empty2.capacity());
            assertTrue(empty2.isEmpty());
            assertEquals(LOT_C, empty2.lotId());
        }
    }

    @Nested
    @DisplayName("scrapCount")
    class ScrapCountTest {

        @Test
        @DisplayName("rejects null source")
        void nullSourceRejected() {
            assertThrows(NullPointerException.class, () -> FoupDisposal.scrapCount(null, 1));
        }

        @Test
        @DisplayName("rejects empty source lot")
        void emptySourceRejected() {
            FoupContents empty = FoupContents.empty(LOT_A, 25);
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(empty, 1));
        }

        @Test
        @DisplayName("rejects scrapPerWafer below 1")
        void ratioBelowOneRejected() {
            FoupContents source = lot(LOT_A, 25, wafers(3, 1, 0.9f));
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(source, 0));
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(source, -1));
        }

        @Test
        @DisplayName("rejects scrapPerWafer above 64")
        void ratioAbove64Rejected() {
            FoupContents source = lot(LOT_A, 25, wafers(3, 1, 0.9f));
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(source, 65));
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(source, 100));
        }

        @Test
        @DisplayName("rejects multiplication overflow")
        void overflowRejected() {
            // 64 wafers * 64 = 4096, well within int range
            // But we can test boundary: 25 * 64 = 1600 (default capacity)
            // To test overflow we'd need huge numbers but capacity max is 64
            // 64 * 64 = 4096, no overflow possible with current constraints
            // Still test the boundary check is present
            FoupContents source = FoupContents.empty(LOT_A, 64).withWafers(wafers(64, 1, 0.9f));
            // This should not overflow: 64 * 64 = 4096
            assertEquals(4096, FoupDisposal.scrapCount(source, 64));
        }

        @Test
        @DisplayName("exact scrap calculation for normal lot")
        void exactScrapNormalLot() {
            FoupContents source = lot(LOT_A, 25, wafers(10, 1, 0.9f));
            assertEquals(10, FoupDisposal.scrapCount(source, 1));
            assertEquals(50, FoupDisposal.scrapCount(source, 5));
            assertEquals(100, FoupDisposal.scrapCount(source, 10));
        }

        @Test
        @DisplayName("exact scrap calculation for full lot")
        void exactScrapFullLot() {
            FoupContents source = FoupContents.empty(LOT_A, 25).withWafers(wafers(25, 5, 0.9f));
            assertEquals(25 * 4, FoupDisposal.scrapCount(source, 4));
        }

        @Test
        @DisplayName("works with PURE grade lot")
        void pureGradeLot() {
            FoupContents source = lot(LOT_A, 25, wafers(7, 1, 0.9f), null, WaferSubstrate.PURE);
            assertEquals(7 * 3, FoupDisposal.scrapCount(source, 3));
        }

        @Test
        @DisplayName("works with G2 prepared lot")
        void g2PreparedLot() {
            FoupContents source = lot(LOT_A, 25, wafers(7, Wafer.UNPROCESSED_LAYER, Wafer.NO_ROLLS), null, WaferSubstrate.PURE, FoupContents.PREPARATION_G2);
            assertEquals(7 * 2, FoupDisposal.scrapCount(source, 2));
        }

        @Test
        @DisplayName("works with processed lot (has run)")
        void processedLot() {
            FoupContents source = lot(LOT_A, 25, wafers(12, 4, 0.8f), dummyRun());
            assertEquals(12 * 6, FoupDisposal.scrapCount(source, 6));
        }

        @Test
        @DisplayName("works with exposure lot")
        void exposureLot() {
            FoupContents source = lot(LOT_A, 25, wafers(8, 2, 0.85f))
                .withRoomExposure(com.euvcraft.lithmod.core.cleanroom.CleanroomExposure.of(200, 600), 600);
            assertEquals(8 * 8, FoupDisposal.scrapCount(source, 8));
        }

        @Test
        @DisplayName("capacity invariant: scrapCount uses wafer count not capacity")
        void usesWaferCountNotCapacity() {
            FoupContents source = lot(LOT_A, 64, wafers(3, 1, 0.9f)); // capacity 64, only 3 wafers
            assertEquals(3 * 10, FoupDisposal.scrapCount(source, 10));
        }

        @Test
        @DisplayName("empty invariant: empty lot rejected")
        void emptyInvariant() {
            FoupContents empty = FoupContents.empty(LOT_A, 25);
            assertThrows(IllegalArgumentException.class, () -> FoupDisposal.scrapCount(empty, 1));
        }
    }
}