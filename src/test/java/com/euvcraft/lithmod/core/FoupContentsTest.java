package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.euvcraft.lithmod.core.cleanroom.CleanroomExposure;
import com.euvcraft.lithmod.core.t7.G3Preparation;

/**
 * Unit tests for {@link Wafer}, {@link FoupContents}, {@link FoupPublicView} and
 * {@link WaferPacking}.
 */
class FoupContentsTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");

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

    @Nested
    @DisplayName("Wafer")
    class WaferTest {

        @Test
        @DisplayName("accepts a roll of exactly 1 and rejects 0 and anything above 1")
        void rollRangeIsChecked() {
            assertEquals(1.0f, new Wafer(1, 1.0f, 0).cumulativeRoll());
            assertEquals(0.5f, new Wafer(1, 0.5f, 0).cumulativeRoll());

            assertThrows(IllegalArgumentException.class, () -> new Wafer(1, 0.0f, 0));
            assertThrows(IllegalArgumentException.class, () -> new Wafer(1, -0.5f, 0));
            assertThrows(IllegalArgumentException.class, () -> new Wafer(1, 1.0001f, 0));
            assertThrows(IllegalArgumentException.class, () -> new Wafer(1, Float.NaN, 0));
            assertThrows(IllegalArgumentException.class, () -> new Wafer(1, Float.POSITIVE_INFINITY, 0));
        }

        @Test
        @DisplayName("rejects a negative layer")
        void negativeLayerIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Wafer(-1, 1.0f, 0));
        }

        @Test
        @DisplayName("an unprocessed wafer is at layer 0 with no rolls")
        void unprocessedWafer() {
            assertTrue(Wafer.unprocessed().isUnprocessed());
            assertEquals(Wafer.UNPROCESSED_LAYER, Wafer.unprocessed().layerIndex());
            assertEquals(Wafer.NO_ROLLS, Wafer.unprocessed().cumulativeRoll());
            assertEquals(Wafer.NO_FLAGS, Wafer.unprocessed().flags());
        }

        @Test
        @DisplayName("toString never prints a roll or a flag")
        void toStringHidesHiddenValues() {
            String text = wafer(4, 0.123456f).toString();
            assertEquals("Wafer[layer=4]", text);
            assertTrue(!text.contains("0.12"), "the roll leaked into " + text);
        }
    }

    @Nested
    @DisplayName("capacity limits")
    class CapacityTest {

        @Test
        @DisplayName("a lot refuses more wafers than its capacity")
        void capacityIsEnforced() {
            FoupContents emptyLot = FoupContents.empty(LOT, 3);
            assertTrue(emptyLot.isEmpty());
            assertTrue(!emptyLot.isFull());

            FoupContents full = emptyLot.withWafers(wafers(3, 1, 0.9f));
            assertEquals(3, full.size());
            assertTrue(full.isFull());

            assertThrows(IllegalStateException.class, () -> full.withWafer(wafer(1, 0.5f)));
            assertThrows(IllegalStateException.class, () -> full.withWafers(wafers(1, 1, 0.5f)));
        }

        @Test
        @DisplayName("the constructor rejects a list longer than the capacity")
        void constructorEnforcesCapacity() {
            assertThrows(IllegalArgumentException.class,
                () -> new FoupContents(LOT, 2, wafers(3, 1, 0.9f)));
        }

        @Test
        @DisplayName("a capacity below one is rejected")
        void capacityMustBePositive() {
            assertThrows(IllegalArgumentException.class, () -> FoupContents.empty(LOT, 0));
            assertThrows(IllegalArgumentException.class, () -> FoupContents.empty(LOT, -1));
        }

        @Test
        @DisplayName("the configured capacity of 25 fits 25 wafers")
        void defaultCapacity() {
            FoupContents lot = FoupContents.empty(LOT, FabSettings.DEFAULT_FOUP_CAPACITY).withWafers(wafers(25, 6, 0.99f));
            assertEquals(25, lot.size());
            assertTrue(lot.isFull());
            assertEquals(6, lot.lotLayer());
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("the wafer list is copied on construction")
        void listIsCopied() {
            List<Wafer> source = new ArrayList<>(wafers(2, 1, 0.9f));
            FoupContents lot = new FoupContents(LOT, 5, source);
            source.add(wafer(1, 0.5f));
            source.clear();

            assertEquals(2, lot.size());
            assertEquals(wafers(2, 1, 0.9f), lot.wafers());
        }

        @Test
        @DisplayName("the wafer list accessor hands out an unmodifiable list")
        void accessorIsUnmodifiable() {
            FoupContents lot = new FoupContents(LOT, 5, wafers(2, 1, 0.9f));
            List<Wafer> exposed = lot.wafers();
            assertThrows(UnsupportedOperationException.class, () -> exposed.add(wafer(1, 0.5f)));
            assertThrows(UnsupportedOperationException.class, () -> exposed.clear());
        }

        @Test
        @DisplayName("withWafer and withoutWaferAt return new instances and leave the original alone")
        void updatesReturnNewInstances() {
            FoupContents empty = FoupContents.empty(LOT, 4);
            FoupContents grown = empty.withWafer(wafer(2, 0.75f));

            assertNotSame(empty, grown);
            assertEquals(0, empty.size());
            assertEquals(1, grown.size());

            FoupContents shrunk = grown.withoutWaferAt(0);
            assertEquals(1, grown.size());
            assertEquals(0, shrunk.size());
            assertEquals(empty, shrunk);
        }

        @Test
        @DisplayName("withoutWaferAt rejects a slot that is not in the lot")
        void removingAnAbsentSlotIsRejected() {
            FoupContents lot = FoupContents.empty(LOT, 4).withWafer(wafer(1, 0.75f));
            assertThrows(IndexOutOfBoundsException.class, () -> lot.withoutWaferAt(1));
            assertThrows(IndexOutOfBoundsException.class, () -> lot.withoutWaferAt(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> FoupContents.empty(LOT, 4).withoutWaferAt(0));
        }

        @Test
        @DisplayName("a null lot id, wafer list or wafer is rejected")
        void nullsAreRejected() {
            assertThrows(NullPointerException.class, () -> FoupContents.empty(null, 4));
            assertThrows(NullPointerException.class, () -> new FoupContents(LOT, 4, null));
            assertThrows(NullPointerException.class, () -> FoupContents.empty(LOT, 4).withWafer(null));
            List<Wafer> withNull = new ArrayList<>();
            withNull.add(null);
            assertThrows(NullPointerException.class, () -> new FoupContents(LOT, 4, withNull));
        }

        @Test
        @DisplayName("wafer order survives adding and removing")
        void orderIsStable() {
            FoupContents lot = FoupContents.empty(LOT, 8)
                .withWafer(wafer(1, 0.9f))
                .withWafer(wafer(2, 0.8f))
                .withWafer(wafer(3, 0.7f));

            assertEquals(List.of(0.9f, 0.8f, 0.7f),
                lot.wafers().stream().map(Wafer::cumulativeRoll).toList());
            assertEquals(3, lot.withoutWaferAt(1).wafers().get(1).layerIndex());
        }
    }

    @Nested
    @DisplayName("public view")
    class PublicViewTest {

        @Test
        @DisplayName("carries capacity, count and the lowest lot layer only")
        void viewCarriesThreeNumbers() {
            FoupContents lot = FoupContents.empty(LOT, 25).withWafers(List.of(
                wafer(0, 0.9f), wafer(9, 0.8f), wafer(4, 0.7f)));

            FoupPublicView view = lot.publicView();

            assertEquals(25, view.capacity());
            assertEquals(3, view.waferCount());
            assertEquals(0, view.lotLayer());
        }

        @Test
        @DisplayName("reports layer 0 for an empty lot")
        void emptyLotReportsZero() {
            assertEquals(FoupPublicView.EMPTY_LOT_LAYER, FoupContents.empty(LOT, 25).lotLayer());
            assertEquals(new FoupPublicView(25, 0, 0), FoupContents.empty(LOT, 25).publicView());
            assertTrue(FoupContents.empty(LOT, 25).publicView().isEmpty());
        }

        @Test
        @DisplayName("the view cannot be turned back into hidden values")
        void viewHasNoHiddenFields() {
            FoupContents lot = FoupContents.empty(LOT, 25).withWafers(List.of(wafer(3, 0.42f), wafer(6, 0.13f)));
            FoupPublicView view = lot.publicView();

            assertEquals(5, FoupPublicView.class.getRecordComponents().length);
            assertEquals(List.of("capacity", "waferCount", "lotLayer", "grade", "preparationGeneration"),
                java.util.Arrays.stream(FoupPublicView.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList());
            assertEquals(List.of(int.class, int.class, int.class, WaferSubstrate.class, int.class),
                java.util.Arrays.stream(FoupPublicView.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getType).toList());
            assertEquals("FoupPublicView[capacity=25, waferCount=2, lotLayer=3, grade=ORDINARY, preparationGeneration=1]", view.toString());
            assertTrue(!view.toString().contains("0.42"), "a roll leaked into " + view);
        }

        @Test
        @DisplayName("the view validates its own components")
        void viewValidates() {
            assertThrows(IllegalArgumentException.class, () -> new FoupPublicView(0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new FoupPublicView(4, 5, 0));
            assertThrows(IllegalArgumentException.class, () -> new FoupPublicView(4, -1, 0));
            assertThrows(IllegalArgumentException.class, () -> new FoupPublicView(4, 0, -1));
            assertTrue(new FoupPublicView(4, 4, 0).isFull());
        }
    }

    @Nested
    @DisplayName("packing round trip")
    class PackingTest {

        @Test
        @DisplayName("0 wafers round trip")
        void emptyRoundTrip() {
            WaferPacking.Packed packed = new WaferPacking.Packed(new int[0], new float[0], new int[0]);
            assertEquals(0, packed.size());
            assertTrue(packed.isEmpty());
            assertEquals(List.of(), WaferPacking.unpack(packed.layers(), packed.rolls(), packed.flags()));
        }

        @Test
        @DisplayName("1 wafer round trips exactly")
        void singleRoundTrip() {
            Wafer source = wafer(17, 0.9876543f);
            List<Wafer> back = WaferPacking.unpack(WaferPacking.packLayers(List.of(source)),
                new float[] {source.cumulativeRoll()}, WaferPacking.packFlags(List.of(source)));

            assertEquals(1, back.size());
            assertEquals(source, back.get(0));
        }

        @Test
        @DisplayName("25 wafers round trip exactly")
        void defaultCapacityRoundTrip() {
            List<Wafer> source = wafers(25, 12, 0.99f);
            List<Wafer> back = WaferPacking.unpack(WaferPacking.packLayers(source),
                WaferPacking.unpackRollBits(WaferPacking.packRollBits(source)),
                WaferPacking.packFlags(source));

            assertEquals(25, back.size());
            assertEquals(source, back);
            for (int i = 0; i < source.size(); i++) {
                assertTrue(source.get(i).cumulativeRoll() == back.get(i).cumulativeRoll(),
                    "roll " + i + " changed: " + source.get(i).cumulativeRoll() + " -> "
                        + back.get(i).cumulativeRoll());
            }
        }

        @Test
        @DisplayName("64 wafers round trip exactly")
        void maxCapacityRoundTrip() {
            List<Wafer> source = wafers(64, 90, 1.0f);
            List<Wafer> back = WaferPacking.unpack(WaferPacking.packLayers(source),
                WaferPacking.unpackRollBits(WaferPacking.packRollBits(source)),
                WaferPacking.packFlags(source));

            assertEquals(64, back.size());
            assertEquals(source, back);
        }

        @Test
        @DisplayName("the packed arrays are copies, not the caller's arrays")
        void arraysAreCopied() {
            int[] layers = {1, 2};
            float[] rolls = {0.5f, 0.25f};
            int[] flags = {0, 0};
            WaferPacking.Packed packed = new WaferPacking.Packed(layers, rolls, flags);

            layers[0] = 99;
            rolls[0] = 0.99f;
            packed.layers()[0] = 98;

            assertEquals(1, packed.layers()[0]);
            assertEquals(0.5f, packed.rolls()[0]);
        }

        @Test
        @DisplayName("the pack helpers write parallel arrays of the same length")
        void packHelpersAgree() {
            List<Wafer> source = wafers(4, 2, 0.8f);

            assertEquals(4, WaferPacking.packLayers(source).length);
            assertEquals(4, WaferPacking.packRollBits(source).length);
            assertEquals(4, WaferPacking.packFlags(source).length);
            assertEquals(source, WaferPacking.unpack(WaferPacking.packLayers(source),
                WaferPacking.unpackRollBits(WaferPacking.packRollBits(source)),
                WaferPacking.packFlags(source)));
        }
    }

    @Nested
    @DisplayName("invalid packed data")
    class InvalidDataTest {

        @Test
        @DisplayName("arrays of different lengths are rejected")
        void unequalArraysAreRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> new WaferPacking.Packed(new int[] {1, 2}, new float[] {0.5f}, new int[] {0, 0}));
            assertThrows(IllegalArgumentException.class,
                () -> new WaferPacking.Packed(new int[] {1}, new float[] {0.5f}, new int[] {0, 0}));
            assertThrows(IllegalArgumentException.class,
                () -> WaferPacking.unpack(new int[] {1, 2}, new float[] {0.5f}, new int[] {0, 0}));
        }

        @Test
        @DisplayName("a roll of 0 is rejected")
        void zeroRollIsRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> WaferPacking.unpack(new int[] {1}, new float[] {0.0f}, new int[] {0}));
        }

        @Test
        @DisplayName("a roll above 1 is rejected")
        void rollAboveOneIsRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> WaferPacking.unpack(new int[] {1}, new float[] {1.0001f}, new int[] {0}));
            assertThrows(IllegalArgumentException.class,
                () -> WaferPacking.unpack(new int[] {1}, new float[] {-0.25f}, new int[] {0}));
        }

        @Test
        @DisplayName("a null array is rejected")
        void nullArrayIsRejected() {
            assertThrows(NullPointerException.class,
                () -> new WaferPacking.Packed(null, new float[] {0.5f}, new int[] {0}));
            assertThrows(NullPointerException.class, () -> WaferPacking.packLayers(null));
            assertThrows(NullPointerException.class, () -> WaferPacking.packFlags(null));
            assertThrows(NullPointerException.class, () -> WaferPacking.unpackRollBits(null));
        }
    }

    @Test
    @DisplayName("packing is compared by contents, not by array identity")
    void packingEqualityIsByContents() {
        WaferPacking.Packed first = new WaferPacking.Packed(new int[] {1, 2, 3},
            new float[] {0.7f, 0.6f, 0.5f}, new int[] {0, 0, 0});
        WaferPacking.Packed second = new WaferPacking.Packed(new int[] {1, 2, 3},
            new float[] {0.7f, 0.6f, 0.5f}, new int[] {0, 0, 0});

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("Packed[wafers=3]", first.toString());
    }

    @Test
    @DisplayName("a lot prints its public view only")
    void lotToStringHidesHiddenValues() {
        FoupContents lot = FoupContents.empty(LOT, 25).withWafers(List.of(wafer(3, 0.42f), wafer(9, 0.13f)));
        String text = lot.toString();

        assertTrue(text.contains("capacity=25"), text);
        assertTrue(text.contains("wafers=2"), text);
        assertTrue(!text.contains("0.42"), "a roll leaked into " + text);
    }

    @Nested
    @DisplayName("G3 preparation evidence")
    class G3PreparationTest {

        private FoupContents freshElectronic(int count) {
            List<Wafer> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(Wafer.unprocessed());
            }
            return FoupContents.of(LOT, 25, list, WaferSubstrate.ELECTRONIC_GRADE);
        }

        private RunSpec spec() {
            return new RunSpec("logic", 3, 0, 0.9, 24);
        }

        @Test
        @DisplayName("the eight-argument legacy constructor still means no G3 evidence")
        void legacyConstructorDefaultsToNoEvidence() {
            FoupContents lot = new FoupContents(LOT, 25, List.of(Wafer.unprocessed()), null,
                WaferSubstrate.ELECTRONIC_GRADE, FoupContents.PREPARATION_LEGACY, CleanroomExposure.empty(), 0);

            assertEquals(FoupContents.PREPARATION_LEGACY, lot.preparationGeneration());
            assertEquals(G3Preparation.none(), lot.g3Preparation());
            assertFalse(lot.isG3Prepared());
        }

        @Test
        @DisplayName("G3 preparation is a single deterministic commit with one factor per wafer")
        void preparedG3CommitsOnce() {
            FoupContents fresh = freshElectronic(2);
            FoupContents prepared = fresh.preparedG3(800, new int[] {1000, 500});

            assertEquals(FoupContents.PREPARATION_G3, prepared.preparationGeneration());
            assertTrue(prepared.isG3Prepared());
            assertEquals(2, prepared.g3Preparation().factorCount());
            assertEquals(800, prepared.g3Preparation().factorPermilleAt(0));
            assertEquals(400, prepared.g3Preparation().factorPermilleAt(1));
            assertEquals(800, prepared.g3Preparation().cleanroomMeanPermille());
            assertThrows(IllegalStateException.class, () -> prepared.preparedG3(900, new int[] {1000, 1000}));
        }

        @Test
        @DisplayName("G3 factors are deterministic for the same paid mean and draws")
        void commitIsDeterministic() {
            G3Preparation first = G3Preparation.commit(800, new int[] {1000, 500, 0});
            G3Preparation second = G3Preparation.commit(800, new int[] {1000, 500, 0});

            assertEquals(first, second);
            assertEquals(800, first.factorPermilleAt(0));
            assertEquals(400, first.factorPermilleAt(1));
            assertEquals(1, first.factorPermilleAt(2));
            assertEquals(500, G3Preparation.commit(999, new int[] {500}).factorPermilleAt(0));
        }

        @Test
        @DisplayName("G3 rejects non-electronic, empty, started and processed lots")
        void preparedG3RejectsWrongLots() {
            assertThrows(IllegalArgumentException.class,
                () -> FoupContents.of(LOT, 25, List.of(Wafer.unprocessed()), WaferSubstrate.PURE)
                    .preparedG3(800, new int[] {1000}));
            assertThrows(IllegalArgumentException.class,
                () -> FoupContents.of(LOT, 25, List.of(Wafer.unprocessed()), WaferSubstrate.ORDINARY)
                    .preparedG3(800, new int[] {1000}));
            assertThrows(IllegalArgumentException.class,
                () -> FoupContents.empty(LOT, 25, WaferSubstrate.ELECTRONIC_GRADE).preparedG3(800, new int[] {1000}));
            assertThrows(IllegalArgumentException.class,
                () -> freshElectronic(1).withRun(spec()).preparedG3(800, new int[] {1000}));
            assertThrows(IllegalArgumentException.class,
                () -> freshElectronic(1).withWafer(new Wafer(1, 0.5f, 0)).preparedG3(800, new int[] {1000, 1000}));
            assertThrows(IllegalArgumentException.class,
                () -> freshElectronic(2).preparedG3(800, new int[] {1000}));
            assertThrows(IllegalArgumentException.class,
                () -> freshElectronic(2).preparedG3(800, new int[] {1001, 1000}));
            assertThrows(IllegalArgumentException.class,
                () -> freshElectronic(1).preparedG3(0, new int[] {1000}));
        }

        @Test
        @DisplayName("a prepared G3 lot cannot be mixed with fresh wafers or downgraded")
        void preparedG3CannotMixOrDowngrade() {
            FoupContents prepared = freshElectronic(2).preparedG3(800, new int[] {1000, 500});

            assertThrows(IllegalStateException.class, () -> prepared.withWafer(Wafer.unprocessed()));
            assertThrows(IllegalStateException.class,
                () -> prepared.withWafers(List.of(Wafer.unprocessed())));
            assertThrows(IllegalArgumentException.class, () -> prepared.withPreparationGeneration(2));
        }

        @Test
        @DisplayName("run, layer and room-exposure copies preserve the frozen factors")
        void copiesPreserveEvidence() {
            FoupContents prepared = freshElectronic(2).preparedG3(800, new int[] {1000, 500});
            RunSpec run = spec();
            CleanroomExposure exposure = CleanroomExposure.empty().sample(700);

            FoupContents withRun = prepared.withRun(run);
            FoupContents withExposure = prepared.withRoomExposure(exposure, 700);
            FoupContents processed = prepared.withProcessedWafers(List.of(
                new Wafer(1, 0.9f, 0), new Wafer(1, 0.8f, 0)));

            assertEquals(prepared.g3Preparation(), withRun.g3Preparation());
            assertEquals(prepared.g3Preparation(), withExposure.g3Preparation());
            assertEquals(prepared.g3Preparation(), processed.g3Preparation());
            assertEquals(FoupContents.PREPARATION_G3, withRun.preparationGeneration());
            assertEquals(FoupContents.PREPARATION_G3, withExposure.preparationGeneration());
            assertEquals(FoupContents.PREPARATION_G3, processed.preparationGeneration());
            assertEquals(run, withRun.run());
            assertEquals(exposure, withExposure.roomExposure());
            assertEquals(700, withExposure.roomYieldPermille());
            assertEquals(1, processed.lotLayer());
        }

        @Test
        @DisplayName("removing a wafer drops only its factor; emptying resets all evidence")
        void removalPreservesAndEmptyingResets() {
            FoupContents prepared = freshElectronic(2).preparedG3(800, new int[] {1000, 500});

            FoupContents oneLeft = prepared.withoutWaferAt(0);
            assertEquals(1, oneLeft.size());
            assertEquals(1, oneLeft.g3Preparation().factorCount());
            assertEquals(400, oneLeft.g3Preparation().factorPermilleAt(0));

            FoupContents empty = oneLeft.withoutWaferAt(0);
            assertTrue(empty.isEmpty());
            assertEquals(FoupContents.PREPARATION_LEGACY, empty.preparationGeneration());
            assertEquals(G3Preparation.none(), empty.g3Preparation());
        }

        @Test
        @DisplayName("the public view exposes generation 3 but no per-wafer factors")
        void publicViewExposesStateWithoutFactors() {
            FoupContents prepared = freshElectronic(2).preparedG3(800, new int[] {1000, 500});
            FoupPublicView view = prepared.publicView();

            assertEquals(FoupPublicView.PREPARATION_G3, view.preparationGeneration());
            assertTrue(view.isG3Prepared());
            assertEquals(5, FoupPublicView.class.getRecordComponents().length);
            for (java.lang.reflect.RecordComponent component : FoupPublicView.class.getRecordComponents()) {
                assertNotSame(G3Preparation.class, component.getType());
            }
            assertTrue(!prepared.toString().contains("waferFactorsPermille"), "a G3 factor leaked into " + prepared);
            assertTrue(!prepared.toString().contains("g3Preparation="), "a G3 factor leaked into " + prepared);
        }

        @Test
        @DisplayName("the constructor rejects inconsistent G3 evidence")
        void constructorValidatesEvidence() {
            G3Preparation evidence = G3Preparation.of(800, List.of(800, 800));

            assertThrows(IllegalArgumentException.class, () -> new FoupContents(LOT, 25,
                List.of(Wafer.unprocessed()), null, WaferSubstrate.ELECTRONIC_GRADE, FoupContents.PREPARATION_G3,
                CleanroomExposure.empty(), 0, G3Preparation.none()));
            assertThrows(IllegalArgumentException.class, () -> new FoupContents(LOT, 25,
                List.of(Wafer.unprocessed()), null, WaferSubstrate.ELECTRONIC_GRADE, FoupContents.PREPARATION_G3,
                CleanroomExposure.empty(), 0, evidence));
            assertThrows(IllegalArgumentException.class, () -> new FoupContents(LOT, 25,
                List.of(Wafer.unprocessed()), null, WaferSubstrate.PURE, FoupContents.PREPARATION_G3,
                CleanroomExposure.empty(), 0, G3Preparation.of(800, List.of(800))));
            assertThrows(IllegalArgumentException.class, () -> new FoupContents(LOT, 25,
                List.of(Wafer.unprocessed()), null, WaferSubstrate.ELECTRONIC_GRADE, FoupContents.PREPARATION_G2,
                CleanroomExposure.empty(), 0, G3Preparation.of(800, List.of(800))));
            assertThrows(IllegalArgumentException.class, () -> new FoupContents(LOT, 25,
                List.of(Wafer.unprocessed()), null, WaferSubstrate.ELECTRONIC_GRADE, FoupContents.PREPARATION_LEGACY,
                CleanroomExposure.empty(), 0, G3Preparation.of(800, List.of(800))));
        }

        @Test
        @DisplayName("the evidence record validates factors, redaction and copies")
        void evidenceValidates() {
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.of(0, List.of(800)));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.of(1001, List.of(800)));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.of(800, List.of()));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.of(800, List.of(0)));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.of(800, List.of(1001)));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.commit(800, new int[0]));
            assertThrows(IllegalArgumentException.class, () -> G3Preparation.commit(800, new int[] {-1}));

            G3Preparation exact = G3Preparation.of(800, List.of(800, 400));
            int[] factors = exact.factorArray();
            factors[0] = 1;
            assertEquals(800, exact.factorPermilleAt(0));
            assertEquals(G3Preparation.none(), exact.withoutFactorAt(1).withoutFactorAt(0));

            G3Preparation redacted = G3Preparation.redacted();
            assertTrue(redacted.isPrepared());
            assertFalse(redacted.hasFactors());
            assertEquals(0, redacted.factorCount());
            assertThrows(IllegalStateException.class, () -> redacted.factorPermilleAt(0));
            assertEquals(redacted, redacted.withoutFactorAt(0));
            assertEquals("G3Preparation[redacted]", redacted.toString());
        }

        @Test
        @DisplayName("the G3 evidence list is immutable")
        void evidenceListIsImmutable() {
            List<Integer> source = new ArrayList<>(List.of(800, 400));
            G3Preparation evidence = G3Preparation.of(800, source);
            source.set(0, 1);

            assertEquals(800, evidence.factorPermilleAt(0));
            assertThrows(UnsupportedOperationException.class, () -> evidence.waferFactorsPermille().set(0, 1));
        }
    }
}
