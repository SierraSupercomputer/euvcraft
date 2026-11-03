package com.euvcraft.lithmod.core.viewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ViewerPageTest {

    private static final String VALID_ID = "euvcraft:recipe/smelter_basic";
    private static final String VALID_MACHINE = "smelter";
    private static final ViewerPage.Ingredient ITEM_ING = ViewerPage.Ingredient.item("minecraft:iron_ingot", 1);
    private static final ViewerPage.Ingredient TAG_ING = new ViewerPage.Ingredient(ViewerPage.Kind.TAG, "forge:ingots/iron", 1, "", "", 0, 0, 0);
    private static final ViewerPage.Ingredient FLUID_ING = new ViewerPage.Ingredient(ViewerPage.Kind.FLUID, "minecraft:water", 1000, "Water", "", 0, 0, 0);
    private static final ViewerPage.Ingredient GAS_ING = new ViewerPage.Ingredient(ViewerPage.Kind.GAS, "mekanism:hydrogen", 500, "H2", "", 0, 0, 0);

    @Test
    void validRoundtripAllKinds() {
        ViewerPage page = new ViewerPage(
            VALID_ID,
            VALID_MACHINE,
            List.of(ITEM_ING, TAG_ING),
            List.of(FLUID_ING),
            List.of(GAS_ING),
            List.of("note1", "note2"),
            200L,
            1000L
        );

        assertEquals(VALID_ID, page.id());
        assertEquals(VALID_MACHINE, page.machine());
        assertEquals(2, page.inputs().size());
        assertEquals(1, page.outputs().size());
        assertEquals(1, page.catalysts().size());
        assertEquals(2, page.notes().size());
        assertEquals(200L, page.ticks());
        assertEquals(1000L, page.fePerTick());

        assertEquals(ViewerPage.Kind.ITEM, page.inputs().get(0).kind());
        assertEquals(ViewerPage.Kind.TAG, page.inputs().get(1).kind());
        assertEquals(ViewerPage.Kind.FLUID, page.outputs().get(0).kind());
        assertEquals(ViewerPage.Kind.GAS, page.catalysts().get(0).kind());
    }

    @Test
    void zeroTicksAndFeMeansVariable() {
        ViewerPage page = new ViewerPage(
            VALID_ID, VALID_MACHINE,
            List.of(), List.of(), List.of(),
            List.of(), 0L, 0L
        );
        assertEquals(0L, page.ticks());
        assertEquals(0L, page.fePerTick());
    }

    @Test
    void defensiveCopiesInputs() {
        var inputs = new java.util.ArrayList<>(List.of(ITEM_ING));
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, inputs, List.of(), List.of(), List.of(), 0, 0);
        inputs.clear(); // mutate the caller's collection, not just its reference
        assertEquals(1, page.inputs().size());
    }

    @Test
    void defensiveCopiesOutputs() {
        var outputs = List.of(ITEM_ING);
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), outputs, List.of(), List.of(), 0, 0);
        outputs = List.of();
        assertEquals(1, page.outputs().size());
    }

    @Test
    void defensiveCopiesCatalysts() {
        var catalysts = List.of(ITEM_ING);
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), catalysts, List.of(), 0, 0);
        catalysts = List.of();
        assertEquals(1, page.catalysts().size());
    }

    @Test
    void defensiveCopiesNotes() {
        var notes = List.of("note1");
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), notes, 0, 0);
        notes = List.of();
        assertEquals(1, page.notes().size());
    }

    @Test
    void mutationRejectionInputs() {
        var list = new java.util.ArrayList<>(List.of(ITEM_ING));
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, list, List.of(), List.of(), List.of(), 0, 0);
        assertThrows(UnsupportedOperationException.class, () -> page.inputs().add(ITEM_ING));
    }

    @Test
    void mutationRejectionOutputs() {
        var list = new java.util.ArrayList<>(List.of(ITEM_ING));
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), list, List.of(), List.of(), 0, 0);
        assertThrows(UnsupportedOperationException.class, () -> page.outputs().add(ITEM_ING));
    }

    @Test
    void mutationRejectionCatalysts() {
        var list = new java.util.ArrayList<>(List.of(ITEM_ING));
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), list, List.of(), 0, 0);
        assertThrows(UnsupportedOperationException.class, () -> page.catalysts().add(ITEM_ING));
    }

    @Test
    void mutationRejectionNotes() {
        var list = new java.util.ArrayList<>(List.of("note"));
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), list, 0, 0);
        assertThrows(UnsupportedOperationException.class, () -> page.notes().add("x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "mod:", ":path", "Mod:path", "mod:Path", "mod:path with space"})
    void rejectInvalidId(String id) {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(id, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Invalid", "machine-name", "machine.name"})
    void rejectInvalidMachine(String machine) {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, machine, List.of(), List.of(), List.of(), List.of(), 0, 0));
    }

    @Test
    void rejectNullLists() {
        assertThrows(NullPointerException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, null, List.of(), List.of(), List.of(), 0, 0));
        assertThrows(NullPointerException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), null, List.of(), List.of(), 0, 0));
        assertThrows(NullPointerException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), null, List.of(), 0, 0));
        assertThrows(NullPointerException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), null, 0, 0));
    }

    @Test
    void rejectNullElementInLists() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, java.util.Arrays.asList((ViewerPage.Ingredient) null), List.of(), List.of(), List.of(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), java.util.Arrays.asList((ViewerPage.Ingredient) null), List.of(), List.of(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), java.util.Arrays.asList((ViewerPage.Ingredient) null), List.of(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), java.util.Arrays.asList((String) null), 0, 0));
    }

    @Test
    void rejectTooManyInputs() {
        var list = java.util.Collections.nCopies(13, ITEM_ING);
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, list, List.of(), List.of(), List.of(), 0, 0));
    }

    @Test
    void rejectTooManyOutputs() {
        var list = java.util.Collections.nCopies(13, ITEM_ING);
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), list, List.of(), List.of(), 0, 0));
    }

    @Test
    void rejectTooManyCatalysts() {
        var list = java.util.Collections.nCopies(13, ITEM_ING);
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), list, List.of(), 0, 0));
    }

    @Test
    void rejectTooManyNotes() {
        var list = java.util.Collections.nCopies(9, "note");
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), list, 0, 0));
    }

    @Test
    void rejectNoteTooLong() {
        String longNote = "x".repeat(161);
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(longNote), 0, 0));
    }

    @Test
    void rejectNegativeTicks() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), -1, 0));
    }

    @Test
    void rejectTicksTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), 1_000_000_001L, 0));
    }

    @Test
    void rejectNegativeFePerTick() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), 0, -1));
    }

    @Test
    void rejectFePerTickTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), 0, 1_000_000_001L));
    }

    @Test
    void ingredientRejectNullKind() {
        assertThrows(NullPointerException.class, () -> new ViewerPage.Ingredient(null, "mod:item", 1, "", "", 0, 0, 0));
    }

    @Test
    void ingredientRejectNullId() {
        assertThrows(NullPointerException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, null, 1, "", "", 0, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "mod:", ":path", "Mod:path", "mod:Path"})
    void ingredientRejectInvalidId(String id) {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, id, 1, "", "", 0, 0, 0));
    }

    @Test
    void ingredientRejectAmountZero() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 0, "", "", 0, 0, 0));
    }

    @Test
    void ingredientRejectAmountTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1_000_001, "", "", 0, 0, 0));
    }

    @Test
    void ingredientRejectNullLabel() {
        assertThrows(NullPointerException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, null, "", 0, 0, 0));
    }

    @Test
    void ingredientRejectLabelTooLong() {
        String longLabel = "x".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, longLabel, "", 0, 0, 0));
    }

    @Test
    void ingredientRejectNullFamily() {
        assertThrows(NullPointerException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", null, 0, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "LOGIC", "Compute", "other"})
    void ingredientRejectInvalidFamily(String family) {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", family, 0, 0, 0));
    }

    @Test
    void ingredientRejectNegativeTier() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", -1, 0, 0));
    }

    @Test
    void ingredientRejectTierTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", 8, 0, 0));
    }

    @Test
    void ingredientRejectNegativeCores() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", 0, -1, 0));
    }

    @Test
    void ingredientRejectCoresTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", 0, 65, 0));
    }

    @Test
    void ingredientRejectNegativeUses() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", 0, 0, -1));
    }

    @Test
    void ingredientRejectUsesTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "", "", 0, 0, 1_000_001));
    }

    @Test
    void ingredientItemFactory() {
        ViewerPage.Ingredient ing = ViewerPage.Ingredient.item("mod:item", 5);
        assertEquals(ViewerPage.Kind.ITEM, ing.kind());
        assertEquals("mod:item", ing.id());
        assertEquals(5, ing.amount());
        assertEquals("", ing.label());
        assertEquals("", ing.family());
        assertEquals(0, ing.tier());
        assertEquals(0, ing.cores());
        assertEquals(0, ing.uses());
    }

    @Test
    void ingredientLabelledReturnsCopy() {
        ViewerPage.Ingredient original = new ViewerPage.Ingredient(ViewerPage.Kind.ITEM, "mod:item", 1, "old", "logic", 1, 2, 3);
        ViewerPage.Ingredient copied = original.labelled("new");
        assertEquals("new", copied.label());
        assertEquals("old", original.label()); // original unchanged
        assertEquals(original.kind(), copied.kind());
        assertEquals(original.id(), copied.id());
        assertEquals(original.amount(), copied.amount());
        assertEquals(original.family(), copied.family());
        assertEquals(original.tier(), copied.tier());
        assertEquals(original.cores(), copied.cores());
        assertEquals(original.uses(), copied.uses());
    }

    @Test
    void ingredientLabelledRejectNull() {
        ViewerPage.Ingredient ing = ViewerPage.Ingredient.item("mod:item", 1);
        assertThrows(NullPointerException.class, () -> ing.labelled(null));
    }

    @Test
    void emptyNotesAllowed() {
        ViewerPage page = new ViewerPage(VALID_ID, VALID_MACHINE, List.of(), List.of(), List.of(), List.of(), 0, 0);
        assertTrue(page.notes().isEmpty());
    }

    @Test
    void validMaxBoundaries() {
        ViewerPage page = new ViewerPage(
            "a:"+"b".repeat(190), // 192 total chars, one namespace separator
            "a".repeat(64),
            java.util.Collections.nCopies(12, ITEM_ING),
            java.util.Collections.nCopies(12, ITEM_ING),
            java.util.Collections.nCopies(12, ITEM_ING),
            java.util.Collections.nCopies(8, "x".repeat(160)),
            1_000_000_000L,
            1_000_000_000L
        );
        assertEquals(12, page.inputs().size());
        assertEquals(8, page.notes().size());
    }
}
