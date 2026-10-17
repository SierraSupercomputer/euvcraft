package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WaferSlicing}, the wafer count the Wafer Slicer produces.
 *
 * <p>Each case names the claim it checks and asserts the count behind it. The two
 * "partially filled" cases also assert the property the name claims — a small FOUP is full,
 * a large one is not — because that is the part a reader is meant to take away, and the
 * refusal cases are ordinary input validation rather than evidence of anything.</p>
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
    @DisplayName("a FOUP smaller than the yield comes back filled to its own capacity")
    void smallFoup() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(25).build();
        int count = WaferSlicing.wafersFor(settings, 6);
        assertEquals(6, count, "a capacity 6 FOUP comes back holding its own capacity of 6, not 25");
        assertTrue(count < settings.wafersPerBoule(),
            "the boule's 25 must not survive a FOUP that cannot hold it");
    }

    @Test
    @DisplayName("a FOUP larger than the yield comes back partially filled")
    void largeFoup() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(25).build();
        int count = WaferSlicing.wafersFor(settings, 64);
        assertEquals(25, count, "a capacity 64 FOUP comes back holding only what one boule yields");
        assertTrue(count < 64, "a FOUP larger than the yield stays partially filled");
    }

    @Test
    @DisplayName("the default settings give the reference count of 25")
    void defaultsGiveTwentyFive() {
        assertEquals(25, WaferSlicing.wafersFor(FabSettings.defaults(), 25),
            "the default wafersPerBoule is 25 and the default FOUP capacity is 25");
    }

    @Test
    @DisplayName("the count follows a changed wafersPerBoule and stops caring about capacity above it")
    void followsSettings() {
        FabSettings settings = FabSettings.builder().wafersPerBoule(3).build();
        assertEquals(3, WaferSlicing.wafersFor(settings, 25),
            "three wafers per boule must give three wafers whatever the capacity is");
        assertEquals(WaferSlicing.wafersFor(settings, 25), WaferSlicing.wafersFor(settings, 64),
            "the capacity stops mattering once it exceeds wafersPerBoule");
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
