package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link WaterTank}. */
class WaterTankTest {

    private static final int MAX = WaterTank.MAX_CAPACITY;

    // ---------------------------------------------------------------- constructors & getters

    @Test
    void constructor_validCapacity_createsEmptyTank() {
        WaterTank tank = new WaterTank(5000);
        assertEquals(5000, tank.capacity());
        assertEquals(0, tank.amount());
    }

    @Test
    void constructor_restore_validCapacityAndAmount_createsTank() {
        WaterTank tank = new WaterTank(5000, 3000);
        assertEquals(5000, tank.capacity());
        assertEquals(3000, tank.amount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, MAX + 1})
    void constructor_invalidCapacity_throws(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(capacity));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, MAX + 1})
    void constructor_restore_invalidAmount_throws(int amount) {
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(5000, amount));
    }

    @Test
    void constructor_restore_amountAboveCapacityButWithinHardBound_allowed() {
        // Restoring amount > capacity but <= MAX is valid (data pack capacity shrink scenario)
        WaterTank tank = new WaterTank(1000, 5000);
        assertEquals(1000, tank.capacity());
        assertEquals(5000, tank.amount());
    }

    // ---------------------------------------------------------------- fill (partial)

    @Test
    void fill_partialAccepted_returnsAcceptedAndMutates() {
        WaterTank tank = new WaterTank(1000);
        int accepted = tank.fill(500, false);
        assertEquals(500, accepted);
        assertEquals(500, tank.amount());
    }

    @Test
    void fill_exceedsCapacity_acceptsOnlyRemainingSpace() {
        WaterTank tank = new WaterTank(1000, 800);
        int accepted = tank.fill(500, false);
        assertEquals(200, accepted);
        assertEquals(1000, tank.amount());
    }

    @Test
    void fill_whenFull_returnsZero() {
        WaterTank tank = new WaterTank(1000, 1000);
        assertEquals(0, tank.fill(500, false));
        assertEquals(1000, tank.amount());
    }

    @Test
    void fill_zeroIsNoOp() {
        WaterTank tank = new WaterTank(1000, 500);
        assertEquals(0, tank.fill(0, false));
        assertEquals(500, tank.amount());
    }

    @Test
    void fill_negative_throws() {
        WaterTank tank = new WaterTank(1000);
        assertThrows(IllegalArgumentException.class, () -> tank.fill(-1, false));
    }

    @Test
    void fill_simulateNeverMutates() {
        WaterTank tank = new WaterTank(1000, 500);
        int accepted = tank.fill(300, true);
        assertEquals(300, accepted);
        assertEquals(500, tank.amount()); // unchanged
    }

    @Test
    void fill_simulateWhenFull_returnsZero() {
        WaterTank tank = new WaterTank(1000, 1000);
        assertEquals(0, tank.fill(500, true));
        assertEquals(1000, tank.amount());
    }

    // ---------------------------------------------------------------- fillExact (all-or-nothing)

    @Test
    void fillExact_fits_returnsTrueAndMutates() {
        WaterTank tank = new WaterTank(1000, 500);
        assertTrue(tank.fillExact(300, false));
        assertEquals(800, tank.amount());
    }

    @Test
    void fillExact_exceedsCapacity_returnsFalseNoMutation() {
        WaterTank tank = new WaterTank(1000, 800);
        assertFalse(tank.fillExact(300, false));
        assertEquals(800, tank.amount());
    }

    @Test
    void fillExact_zeroIsNoOp() {
        WaterTank tank = new WaterTank(1000, 500);
        assertTrue(tank.fillExact(0, false));
        assertEquals(500, tank.amount());
    }

    @Test
    void fillExact_negative_throws() {
        WaterTank tank = new WaterTank(1000);
        assertThrows(IllegalArgumentException.class, () -> tank.fillExact(-1, false));
    }

    @Test
    void fillExact_simulateNeverMutates() {
        WaterTank tank = new WaterTank(1000, 500);
        assertTrue(tank.fillExact(300, true));
        assertEquals(500, tank.amount());
    }

    @Test
    void fillExact_simulateWhenWouldExceed_returnsFalseNoMutation() {
        WaterTank tank = new WaterTank(1000, 800);
        assertFalse(tank.fillExact(300, true));
        assertEquals(800, tank.amount());
    }

    // ---------------------------------------------------------------- drainExact (all-or-nothing)

    @Test
    void drainExact_enoughWater_returnsTrueAndMutates() {
        WaterTank tank = new WaterTank(1000, 800);
        assertTrue(tank.drainExact(300, false));
        assertEquals(500, tank.amount());
    }

    @Test
    void drainExact_notEnoughWater_returnsFalseNoMutation() {
        WaterTank tank = new WaterTank(1000, 500);
        assertFalse(tank.drainExact(600, false));
        assertEquals(500, tank.amount());
    }

    @Test
    void drainExact_zeroIsNoOp() {
        WaterTank tank = new WaterTank(1000, 500);
        assertTrue(tank.drainExact(0, false));
        assertEquals(500, tank.amount());
    }

    @Test
    void drainExact_negative_throws() {
        WaterTank tank = new WaterTank(1000);
        assertThrows(IllegalArgumentException.class, () -> tank.drainExact(-1, false));
    }

    @Test
    void drainExact_simulateNeverMutates() {
        WaterTank tank = new WaterTank(1000, 800);
        assertTrue(tank.drainExact(300, true));
        assertEquals(800, tank.amount());
    }

    @Test
    void drainExact_simulateWhenNotEnough_returnsFalseNoMutation() {
        WaterTank tank = new WaterTank(1000, 500);
        assertFalse(tank.drainExact(600, true));
        assertEquals(500, tank.amount());
    }

    // ---------------------------------------------------------------- resize

    @Test
    void resize_grow_increasesCapacityPreservesAmount() {
        WaterTank tank = new WaterTank(1000, 800);
        tank.resize(2000);
        assertEquals(2000, tank.capacity());
        assertEquals(800, tank.amount());
    }

    @Test
    void resize_shrinkBelowAmount_preservesAmountAllowsDrainRejectsFill() {
        WaterTank tank = new WaterTank(2000, 1500);
        tank.resize(1000); // shrink below current amount
        assertEquals(1000, tank.capacity());
        assertEquals(1500, tank.amount()); // preserved
        // Further fill rejected until room returns
        assertEquals(0, tank.fill(100, false));
        assertEquals(1500, tank.amount());
        // Drain still allowed
        assertTrue(tank.drainExact(600, false));
        assertEquals(900, tank.amount());
        // Now fill works again
        assertEquals(100, tank.fill(200, false));
        assertEquals(1000, tank.amount());
    }

    @Test
    void resize_shrinkToEqualAmount_works() {
        WaterTank tank = new WaterTank(2000, 1000);
        tank.resize(1000);
        assertEquals(1000, tank.capacity());
        assertEquals(1000, tank.amount());
        assertEquals(0, tank.fill(1, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, MAX + 1})
    void resize_invalidCapacity_throws(int capacity) {
        WaterTank tank = new WaterTank(1000);
        assertThrows(IllegalArgumentException.class, () -> tank.resize(capacity));
    }

    // ---------------------------------------------------------------- snapshot round-trip

    @Test
    void snapshot_restoresIdenticalState() {
        WaterTank original = new WaterTank(5000, 3000);
        WaterTank.Snapshot snap = original.snapshot();
        WaterTank restored = snap.restore();
        assertEquals(original.capacity(), restored.capacity());
        assertEquals(original.amount(), restored.amount());
    }

    @Test
    void snapshot_overfullRestoredState_preservesAmount() {
        // Tank with amount > capacity (after resize shrink)
        WaterTank original = new WaterTank(1000, 1500);
        WaterTank.Snapshot snap = original.snapshot();
        WaterTank restored = snap.restore();
        assertEquals(1000, restored.capacity());
        assertEquals(1500, restored.amount());
    }

    // ---------------------------------------------------------------- conservation & boundaries

    @Test
    void conservation_fillThenDrainExact_returnsToOriginal() {
        WaterTank tank = new WaterTank(1000, 200);
        tank.fill(300, false); // 500
        tank.drainExact(300, false); // 200
        assertEquals(200, tank.amount());
    }

    @Test
    void conservation_fillExactThenDrainExact_returnsToOriginal() {
        WaterTank tank = new WaterTank(1000, 200);
        tank.fillExact(300, false); // 500
        tank.drainExact(300, false); // 200
        assertEquals(200, tank.amount());
    }

    @Test
    void boundary_fillToMaxCapacity() {
        WaterTank tank = new WaterTank(MAX);
        assertEquals(MAX, tank.fill(MAX, false));
        assertEquals(MAX, tank.amount());
    }

    @Test
    void boundary_drainToZero() {
        WaterTank tank = new WaterTank(MAX, MAX);
        assertTrue(tank.drainExact(MAX, false));
        assertEquals(0, tank.amount());
    }

    @Test
    void boundary_restoreAtMaxCapacityAndAmount() {
        WaterTank tank = new WaterTank(MAX, MAX);
        assertEquals(MAX, tank.capacity());
        assertEquals(MAX, tank.amount());
    }

    @Test
    void overflow_nearIntegerMaxValue_noArithmeticOverflow() {
        // MAX_CAPACITY is 1_000_000, well within int range
        // But verify operations near the bound don't overflow
        WaterTank tank = new WaterTank(MAX, MAX - 10);
        tank.fill(20, false); // tries to go to MAX + 10, but capped at MAX
        assertEquals(MAX, tank.amount());
        tank.drainExact(MAX, false);
        assertEquals(0, tank.amount());
    }

    // ---------------------------------------------------------------- overfull resize scenarios

    @Test
    void overfullResize_fillRejectedUntilRoom() {
        WaterTank tank = new WaterTank(10000, 5000);
        tank.resize(3000); // now overfull: amount=5000, capacity=3000
        assertEquals(3000, tank.capacity());
        assertEquals(5000, tank.amount());
        // Fill rejected
        assertEquals(0, tank.fill(1000, false));
        assertEquals(5000, tank.amount());
        // Drain works
        assertTrue(tank.drainExact(2000, false));
        assertEquals(3000, tank.amount());
        // Now at capacity, fill still rejected
        assertEquals(0, tank.fill(1, false));
        // Drain more
        assertTrue(tank.drainExact(1000, false));
        assertEquals(2000, tank.amount());
        // Now fill works
        assertEquals(1000, tank.fill(2000, false));
        assertEquals(3000, tank.amount());
    }

    @Test
    void overfullResize_fillExactRejected() {
        WaterTank tank = new WaterTank(1000, 800);
        tank.resize(500); // overfull
        assertFalse(tank.fillExact(100, false));
        assertEquals(800, tank.amount());
    }

    @Test
    void overfullResize_drainExactWorks() {
        WaterTank tank = new WaterTank(1000, 800);
        tank.resize(500); // overfull
        assertTrue(tank.drainExact(300, false));
        assertEquals(500, tank.amount());
    }

    // ---------------------------------------------------------------- restore boundary validation

    @Test
    void restore_amountAboveHardBound_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(1000, MAX + 1));
    }

    @Test
    void restore_negativeAmount_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(1000, -1));
    }

    @Test
    void restore_capacityOutOfBounds_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaterTank(MAX + 1, 0));
    }
}