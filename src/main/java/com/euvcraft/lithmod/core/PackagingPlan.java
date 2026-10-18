package com.euvcraft.lithmod.core;

import java.util.List;
import java.util.Objects;

/**
 * The immutable pass flags of one judged lot plus the two per-wafer amounts of the
 * machine's recipe.
 *
 * <p>The flags are in the lot's wafer order, one per wafer: a passing wafer yields
 * {@code chipsPerWafer} chips and uses {@code packagesPerWafer} packages, a failing
 * wafer yields {@code chipsPerWafer} scrap and uses no package. The plan carries nothing
 * else about the lot: not the {@link FoupContents}, not a {@link RunSpec}, not the
 * hidden rolls, and no second copy of the yield judgment.</p>
 *
 * <p>The flag list is the hidden result data until each wafer is output, so it never
 * appears in {@link #toString()}; the list is defensively copied on construction and the
 * copy is immutable. Everything else about the plan is public recipe data the menu may
 * show.</p>
 *
 * <p>This record has no Minecraft or NeoForge dependencies.</p>
 *
 * @param passFlags        per-wafer pass flags in wafer order, at least one entry
 * @param chipsPerWafer    chips (or scrap) a wafer turns into, at least one
 * @param packagesPerWafer packages a passing wafer consumes, at least one
 */
public record PackagingPlan(List<Boolean> passFlags, int chipsPerWafer, int packagesPerWafer) {

    /**
     * Validates the plan and makes the flag list immutable.
     *
     * @throws NullPointerException     if {@code passFlags} is null or holds a null entry
     * @throws IllegalArgumentException if the flag list is empty, a per-wafer amount is
     *                                  below one, or the amounts multiplied by the wafer
     *                                  count would overflow the queue totals
     */
    public PackagingPlan {
        Objects.requireNonNull(passFlags, "passFlags must not be null");
        passFlags = List.copyOf(passFlags);
        if (passFlags.isEmpty()) {
            throw new IllegalArgumentException("a packaging plan needs at least one wafer");
        }
        if (chipsPerWafer < 1) {
            throw new IllegalArgumentException("chipsPerWafer must be >= 1, got " + chipsPerWafer);
        }
        if (packagesPerWafer < 1) {
            throw new IllegalArgumentException("packagesPerWafer must be >= 1, got " + packagesPerWafer);
        }
        if ((long) chipsPerWafer * passFlags.size() > Integer.MAX_VALUE
            || (long) packagesPerWafer * passFlags.size() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "per-wafer amounts overflow the totals for " + passFlags.size() + " wafers");
        }
    }

    /**
     * Returns the number of wafers the plan covers.
     *
     * @return the wafer count
     */
    public int size() {
        return this.passFlags.size();
    }

    /**
     * Returns whether the wafer at the given position passed the judgment.
     *
     * @param waferIndex position in wafer order, {@code 0 <= waferIndex < size()}
     * @return the wafer's pass flag
     * @throws IndexOutOfBoundsException if the position is not a wafer of this plan
     */
    public boolean passes(int waferIndex) {
        if (waferIndex < 0 || waferIndex >= this.passFlags.size()) {
            throw new IndexOutOfBoundsException(
                "wafer " + waferIndex + " is not in a plan of " + this.passFlags.size() + " wafers");
        }
        return this.passFlags.get(waferIndex);
    }

    /**
     * Returns the packages the wafer at the given position consumes: the frozen package
     * amount for a passing wafer, none for a failing one.
     *
     * @param waferIndex position in wafer order, {@code 0 <= waferIndex < size()}
     * @return the packages the wafer uses, zero when it fails
     * @throws IndexOutOfBoundsException if the position is not a wafer of this plan
     */
    public int packagesFor(int waferIndex) {
        return passes(waferIndex) ? this.packagesPerWafer : 0;
    }

    /**
     * Returns how many wafers passed.
     *
     * @return the passing wafer count
     */
    public int passingCount() {
        int count = 0;
        for (Boolean flag : this.passFlags) {
            if (flag) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns how many wafers failed.
     *
     * @return the failing wafer count
     */
    public int failingCount() {
        return size() - passingCount();
    }

    /**
     * Returns the total chips the plan outputs across all passing wafers.
     *
     * @return the chip total
     */
    public int totalChips() {
        return passingCount() * this.chipsPerWafer;
    }

    /**
     * Returns the total scrap the plan outputs across all failing wafers.
     *
     * @return the scrap total
     */
    public int totalScrap() {
        return failingCount() * this.chipsPerWafer;
    }

    /**
     * Returns the total packages the plan consumes across all passing wafers.
     *
     * @return the package total
     */
    public int totalPackages() {
        return passingCount() * this.packagesPerWafer;
    }

    /**
     * Prints the plan without its pass flags. Flags are hidden result data because a
     * plan can end up in a log line or an error message.
     *
     * @return a description with the recipe amounts and wafer count only
     */
    @Override
    public String toString() {
        return "PackagingPlan[wafers=" + size() + ", chipsPerWafer=" + this.chipsPerWafer
            + ", packagesPerWafer=" + this.packagesPerWafer + "]";
    }
}
