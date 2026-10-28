package com.euvcraft.lithmod.core.t7;

import java.util.Objects;

/**
 * Supplied repair bill for a contaminated machine.
 *
 * <p>The three quantities are intentionally generic so the later machine
 * integration can map them to its own item/fluid/energy units without changing
 * this pure model. All values are non-negative integers; a zero is allowed for a
 * cost the coordinator has configured away.</p>
 *
 * @param materialUnits number of maintenance-material units
 * @param upwMilliBuckets UPW in millibuckets
 * @param energyFe        FE required
 */
public record ContaminationRepairCost(int materialUnits, int upwMilliBuckets, int energyFe) {

    /**
     * Validates the bill.
     *
     * @throws IllegalArgumentException if any quantity is negative
     */
    public ContaminationRepairCost {
        if (materialUnits < 0) {
            throw new IllegalArgumentException("materialUnits must be >= 0, got " + materialUnits);
        }
        if (upwMilliBuckets < 0) {
            throw new IllegalArgumentException("upwMilliBuckets must be >= 0, got " + upwMilliBuckets);
        }
        if (energyFe < 0) {
            throw new IllegalArgumentException("energyFe must be >= 0, got " + energyFe);
        }
    }

    /**
     * Returns the bill supplied for a repair.
     *
     * @param cost the configured bill
     * @return the same value
     * @throws NullPointerException if {@code cost} is null
     */
    public static ContaminationRepairCost require(ContaminationRepairCost cost) {
        return Objects.requireNonNull(cost, "cost must not be null");
    }
}
