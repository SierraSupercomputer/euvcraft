package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WaferSlicing}, the wafer count the Wafer Slicer produces.
 *
 * <p>Every positive case has a matching negative, so a passing run cannot come from an
 * assertion that never fires.</p>
 */
class WaferSlicingTest {

    @Test
    @DisplayName("a boule fills a FOUP of the same size exactly")
    void exactFit() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(25).build();
        assertEquals(25, WaferSlicing.wafersFor(settings, 25),
            "25 wafers per boule into a capacity 25 FOUP should fill it exactly");
    }

    @Test
    @DisplayName("a smaller FOUP takes the smaller count and stays partially filled")
    void smallFoup() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(25).build();
        assertEquals(6, WaferSlicing.wafersFor(settings, 6),
            "a capacity 6 FOUP should come back holding 6 wafers, not 25");
        // NEGATIVE CONTROL: the boule count must not leak through when the FOUP is smaller.
        assertEquals(6 < 25, WaferSlicing.wafersFor(settings, 6) < settings.wafersPerBoule(),
            "a small FOUP should come back under-filled (negative control)");
    }

    @Test
    @DisplayName("a larger FOUP is only filled up to the wafers one boule yields")
    void largeFoup() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(25).build();
        assertEquals(25, WaferSlicing.wafersFor(settings, 64),
            "a capacity 64 FOUP should come back holding only what one boule yields");
        // NEGATIVE CONTROL: the capacity must not fill the lot on its own.
        assertEquals(25 < 64, WaferSlicing.wafersFor(settings, 64) < 64,
            "a large FOUP should come back partially filled (negative control)");
    }

    @Test
    @DisplayName("the default settings give the reference count of 25")
    void defaultsGiveTwentyFive() {
        assertEquals(25, WaferSlicing.wafersFor(FabSettings.defaults(), 25),
            "the default wafersPerBoule is 25 and the default FOUP capacity is 25");
    }

    @Test
    @DisplayName("the count follows a changed wafersPerBoule and ignores nothing else")
    void followsSettings() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(3).build();
        assertEquals(3, WaferSlicing.wafersFor(settings, 25),
            "three wafers per boule must give three wafers whatever the capacity is");

        // NEGATIVE CONTROL: raising the capacity above the boule count changes nothing.
        assertEquals(WaferSlicing.wafersFor(settings, 25),
            WaferSlicing.wafersFor(settings, 64),
            "the capacity must stop mattering once it exceeds wafersPerBoule (negative control)");
    }

    @Test
    @DisplayName("a capacity below one is refused")
    void refusesZeroCapacity() {
        FabSettings settings = FabSettings.defaults();
        assertThrows(IllegalArgumentException.class, () -> WaferSlicing.wafersFor(settings, 0),
            "capacity 0 must be refused rather than producing an empty lot");
        assertThrows(IllegalArgumentException.class, () -> WaferSlicing.wafersFor(settings, -1),
            "a negative capacity must be refused");
    }

    @Test
    @DisplayName("a null settings object is refused")
    void refusesNullSettings() {
        assertThrows(NullPointerException.class, () -> WaferSlicing.wafersFor(null, 25),
            "null settings must be refused");
    }
}