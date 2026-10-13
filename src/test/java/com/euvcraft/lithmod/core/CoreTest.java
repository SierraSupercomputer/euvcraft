package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify JUnit 5 test setup works.
 */
class CoreTest {

    @Test
    void testCorePackageLoads() {
        // Simple test to verify the test infrastructure works
        assertTrue(true);
    }

    @Test
    void testBasicMath() {
        assertEquals(4, 2 + 2);
        assertEquals(6, 2 * 3);
    }
}