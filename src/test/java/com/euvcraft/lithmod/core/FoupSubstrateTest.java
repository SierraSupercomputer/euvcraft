package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WaferSubstrate} and substrate-aware {@link FoupContents} mutations.
 */
class FoupSubstrateTest {

    private static final java.util.UUID LOT = java.util.UUID.fromString("00000000-0000-4000-8000-0000000000f8");

    private static com.euvcraft.lithmod.core.Wafer wafer(int layer) {
        return new com.euvcraft.lithmod.core.Wafer(layer, 0.9f, com.euvcraft.lithmod.core.Wafer.NO_FLAGS);
    }

    @Test
    @DisplayName("WaferSubstrate enum has stable IDs and generations")
    void substrateEnumStableIds() {
        assertEquals("ordinary", WaferSubstrate.ORDINARY.id());
        assertEquals(1, WaferSubstrate.ORDINARY.generation());
        assertEquals("pure", WaferSubstrate.PURE.id());
        assertEquals(2, WaferSubstrate.PURE.generation());
    }

    @Test
    @DisplayName("WaferSubstrate.fromId is case-insensitive")
    void substrateFromIdCaseInsensitive() {
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromId("ordinary"));
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromId("ORDINARY"));
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromId("Ordinary"));
        assertEquals(WaferSubstrate.PURE, WaferSubstrate.fromId("pure"));
        assertEquals(WaferSubstrate.PURE, WaferSubstrate.fromId("PURE"));
    }

    @Test
    @DisplayName("WaferSubstrate.fromId returns null for unknown ID")
    void substrateFromIdUnknownReturnsNull() {
        assertEquals(null, WaferSubstrate.fromId("unknown"));
        assertEquals(null, WaferSubstrate.fromId(null));
    }

    @Test
    @DisplayName("WaferSubstrate.fromIdOrDefault defaults to ORDINARY")
    void substrateFromIdOrDefault() {
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromIdOrDefault(null));
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromIdOrDefault("unknown"));
        assertEquals(WaferSubstrate.PURE, WaferSubstrate.fromIdOrDefault("pure"));
    }

    @Test
    @DisplayName("WaferSubstrate.fromIdOrThrow throws for invalid non-null ID")
    void substrateFromIdOrThrow() {
        assertEquals(WaferSubstrate.ORDINARY, WaferSubstrate.fromIdOrThrow(null));
        assertEquals(WaferSubstrate.PURE, WaferSubstrate.fromIdOrThrow("pure"));
        assertThrows(IllegalArgumentException.class, () -> WaferSubstrate.fromIdOrThrow("invalid"));
    }

    @Test
    @DisplayName("FoupContents legacy 3-arg constructor defaults to ORDINARY grade")
    void legacyConstructorDefaultsOrdinary() {
        FoupContents lot = new FoupContents(LOT, 25, java.util.List.of(wafer(0)));
        assertEquals(WaferSubstrate.ORDINARY, lot.grade());
    }

    @Test
    @DisplayName("FoupContents static factory with explicit grade")
    void staticFactoryWithGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(wafer(0)), WaferSubstrate.PURE);
        assertEquals(WaferSubstrate.PURE, lot.grade());
    }

    @Test
    @DisplayName("FoupContents 5-arg constructor preserves grade")
    void fiveArgConstructorPreservesGrade() {
        FoupContents lot = new FoupContents(LOT, 25, java.util.List.of(wafer(0)), null, WaferSubstrate.PURE);
        assertEquals(WaferSubstrate.PURE, lot.grade());
    }

    @Test
    @DisplayName("FoupContents.empty() defaults to ORDINARY")
    void emptyDefaultsOrdinary() {
        FoupContents lot = FoupContents.empty(LOT, 25);
        assertEquals(WaferSubstrate.ORDINARY, lot.grade());
    }

    @Test
    @DisplayName("FoupContents.empty() with explicit grade")
    void emptyWithGrade() {
        FoupContents lot = FoupContents.empty(LOT, 25, WaferSubstrate.PURE);
        assertEquals(WaferSubstrate.PURE, lot.grade());
    }

    @Test
    @DisplayName("withWafer preserves grade")
    void withWaferPreservesGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(), WaferSubstrate.PURE);
        FoupContents grown = lot.withWafer(wafer(0));
        assertEquals(WaferSubstrate.PURE, grown.grade());
    }

    @Test
    @DisplayName("withWafers preserves grade")
    void withWafersPreservesGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(), WaferSubstrate.PURE);
        FoupContents grown = lot.withWafers(java.util.List.of(wafer(0), wafer(0)));
        assertEquals(WaferSubstrate.PURE, grown.grade());
    }

    @Test
    @DisplayName("withoutWaferAt preserves grade")
    void withoutWaferAtPreservesGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(wafer(0), wafer(0)), WaferSubstrate.PURE);
        FoupContents shrunk = lot.withoutWaferAt(0);
        assertEquals(WaferSubstrate.PURE, shrunk.grade());
    }

    @Test
    @DisplayName("withRun preserves grade")
    void withRunPreservesGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(wafer(0)), WaferSubstrate.PURE);
        RunSpec spec = new RunSpec("logic", 1, 0, 0.9, 10);
        FoupContents withSpec = lot.withRun(spec);
        assertEquals(WaferSubstrate.PURE, withSpec.grade());
    }

    @Test
    @DisplayName("publicView includes grade")
    void publicViewIncludesGrade() {
        FoupContents lot = FoupContents.of(LOT, 25, java.util.List.of(wafer(0)), WaferSubstrate.PURE);
        FoupPublicView view = lot.publicView();
        assertEquals(WaferSubstrate.PURE, view.grade());
    }

    @Test
    @DisplayName("FoupPublicView legacy 3-arg constructor defaults to ORDINARY")
    void publicViewLegacyConstructorDefaultsOrdinary() {
        FoupPublicView view = new FoupPublicView(25, 10, 3);
        assertEquals(WaferSubstrate.ORDINARY, view.grade());
    }

    @Test
    @DisplayName("FoupPublicView 4-arg constructor with explicit grade")
    void publicViewFourArgConstructor() {
        FoupPublicView view = new FoupPublicView(25, 10, 3, WaferSubstrate.PURE);
        assertEquals(WaferSubstrate.PURE, view.grade());
    }

    @Test
    @DisplayName("FoupPublicView.empty() defaults to ORDINARY")
    void publicViewEmptyDefaultsOrdinary() {
        FoupPublicView view = FoupPublicView.empty(25);
        assertEquals(WaferSubstrate.ORDINARY, view.grade());
    }

    @Test
    @DisplayName("FoupPublicView.empty() with explicit grade")
    void publicViewEmptyWithGrade() {
        FoupPublicView view = FoupPublicView.empty(25, WaferSubstrate.PURE);
        assertEquals(WaferSubstrate.PURE, view.grade());
    }

    @Test
    @DisplayName("WaferSlicing legacy API defaults to ORDINARY")
    void waferSlicingLegacyDefaultsOrdinary() {
        FabSettings settings = FabSettings.defaults();
        int count = WaferSlicing.wafersFor(settings, 25);
        // Just verify it doesn't throw and returns a valid number
        assertTrue(count >= 1 && count <= 25);
    }

    @Test
    @DisplayName("WaferSlicing explicit grade overload works")
    void waferSlicingExplicitGrade() {
        FabSettings settings = FabSettings.defaults();
        int countOrdinary = WaferSlicing.wafersFor(settings, 25, WaferSubstrate.ORDINARY);
        int countPure = WaferSlicing.wafersFor(settings, 25, WaferSubstrate.PURE);
        assertEquals(countOrdinary, countPure);
    }

    @Test
    @DisplayName("WaferSlicing explicit grade rejects null grade")
    void waferSlicingRejectsNullGrade() {
        FabSettings settings = FabSettings.defaults();
        assertThrows(NullPointerException.class, () -> WaferSlicing.wafersFor(settings, 25, null));
    }
}