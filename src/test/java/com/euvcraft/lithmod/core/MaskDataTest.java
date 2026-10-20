package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskDataTest {

    // ---------- constructor boundaries ----------

    @Test
    void acceptsEachCanonicalDieType() {
        for (String type : DieTypes.ALL) {
            MaskData mask = new MaskData(type, 1, 1);
            assertEquals(type, mask.dieType());
            assertEquals(1, mask.tierIndex());
            assertEquals(1, mask.usesRemaining());
            assertEquals(1, mask.shopGeneration());
            assertFalse(mask.isSpent());
        }
    }

    @Test
    void threeArgConstructorDefaultsGenerationToOne() {
        MaskData mask = new MaskData("logic", 2, 5);
        assertEquals(1, mask.shopGeneration());
    }

    @Test
    void fourArgConstructorSetsGeneration() {
        MaskData mask = new MaskData("logic", 2, 5, 2);
        assertEquals(2, mask.shopGeneration());
    }

    @Test
    void rejectsNullDieType() {
        assertThrows(NullPointerException.class, () -> new MaskData(null, 1, 1));
        assertThrows(NullPointerException.class, () -> new MaskData(null, 1, 1, 1));
    }

    @Test
    void rejectsUnknownDieType() {
        assertThrows(IllegalArgumentException.class, () -> new MaskData("quantum", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("quantum", 1, 1, 1));
    }

    @Test
    void rejectsNoncanonicalDieType() {
        // variants that DieRules.normalize would accept must fail here:
        // MaskData requires an exact DieTypes.ALL member
        assertThrows(IllegalArgumentException.class, () -> new MaskData("COMPUTE", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("Logic", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData(" memory", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("nand ", 1, 1));
    }

    @Test
    void rejectsInvalidShopGeneration() {
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 1, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 1, 1, -1));
    }

    @Test
    void tierIndexMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", Integer.MIN_VALUE, 1));
        // boundary: lowest acceptable tier
        assertEquals(1, new MaskData("logic", 1, 1).tierIndex());
    }

    @Test
    void tierIndexHasNoUpperCap() {
        assertEquals(2, new MaskData("logic", 2, 1).tierIndex());
        assertEquals(8, new MaskData("logic", 8, 1).tierIndex());
        assertEquals(99, new MaskData("logic", 99, 1).tierIndex());
        assertEquals(Integer.MAX_VALUE, new MaskData("logic", Integer.MAX_VALUE, 1).tierIndex());
    }

    @Test
    void usesRemainingMustBeNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new MaskData("logic", 1, Integer.MIN_VALUE));
        // boundaries: zero allowed, large counts allowed
        assertEquals(0, new MaskData("logic", 1, 0).usesRemaining());
        assertEquals(1000, new MaskData("logic", 1, 1000).usesRemaining());
    }

    // ---------- isSpent ----------

    @Test
    void isSpentOnlyAtZero() {
        assertFalse(new MaskData("logic", 1, 3).isSpent());
        assertFalse(new MaskData("logic", 1, 1).isSpent());
        assertTrue(new MaskData("logic", 1, 0).isSpent());
    }

    // ---------- consumeUse ----------

    @Test
    void consumeUseReturnsNewRecordWithOneFewer() {
        MaskData start = new MaskData("compute", 3, 2);
        MaskData afterOne = start.consumeUse();
        assertNotSame(start, afterOne);
        assertEquals(start.dieType(), afterOne.dieType());
        assertEquals(start.tierIndex(), afterOne.tierIndex());
        assertEquals(start.shopGeneration(), afterOne.shopGeneration());
        assertEquals(1, afterOne.usesRemaining());
    }

    @Test
    void consumeUsePreservesGeneration() {
        MaskData gen1 = new MaskData("logic", 1, 3, 1);
        MaskData gen2 = new MaskData("logic", 1, 3, 2);
        assertEquals(1, gen1.consumeUse().shopGeneration());
        assertEquals(2, gen2.consumeUse().shopGeneration());
    }

    @Test
    void consumeUseIsImmutable() {
        MaskData start = new MaskData("memory", 2, 2);
        MaskData afterOne = start.consumeUse();
        MaskData afterTwo = afterOne.consumeUse();

        // every intermediate record is untouched
        assertEquals(2, start.usesRemaining());
        assertEquals(1, afterOne.usesRemaining());
        assertEquals(0, afterTwo.usesRemaining());
        assertFalse(start.isSpent());
        assertFalse(afterOne.isSpent());
        assertTrue(afterTwo.isSpent());
    }

    @Test
    void consumeUseToSpentThenThrows() {
        MaskData mask = new MaskData("nand", 3, 1);
        assertFalse(mask.isSpent());
        MaskData spent = mask.consumeUse();
        assertTrue(spent.isSpent());
        assertThrows(IllegalStateException.class, spent::consumeUse);
    }

    @Test
    void consumeUseOnAlreadySpentThrows() {
        MaskData spent = new MaskData("nand", 3, 0);
        assertTrue(spent.isSpent());
        assertThrows(IllegalStateException.class, spent::consumeUse);
    }
}
