package com.euvcraft.lithmod.core;

/**
 * The entrance rules of the test and package machine, checked before any judgment or
 * consumption happens.
 *
 * <p>A lot is only eligible when it is a non-empty, trusted-core lot that carries a
 * frozen {@link RunSpec}, every wafer sits exactly at the frozen tier's final layer, the
 * chip catalog has an item for the die type and tier, and the installed generation
 * satisfies the support matrix gate for the packaging line. The caller resolves the
 * tier, the catalog availability and the installed generation first (the Minecraft
 * wrapper later does this from the spec and the real catalog); this class takes them as
 * plain inputs so the whole gate stays pure and testable.</p>
 *
 * <p>The check is fail closed: an absent chip item and an unmet generation refuse the
 * lot rather than assume the best, and a structurally wrong caller (a null tier, a
 * mismatched tier, no matrix) throws instead of allowing anything through.</p>
 *
 * <p>The result is a typed nullable refusal: {@code null} means the lot may be judged
 * and packaged, anything else is a {@link PackagingRefusalReason}. No method here
 * consumes the lot, draws energy or prints hidden values; exception messages name
 * only public facts.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class PackagingRules {

    /** Support line the packaging gate is checked against. */
    public static final String LINE_ID = "test_packaging";

    private PackagingRules() {
    }

    /**
     * Validates a lot for the test and package machine.
     *
     * <p>The checks run in order, so at most one reason is reported per call: an
     * absent or empty lot is {@link PackagingRefusalReason#NO_LOT}; a lot without a run
     * spec is {@link PackagingRefusalReason#NO_SPEC}; a lot where any
     * {@link Wafer#layerIndex()} differs from the tier's final layer
     * ({@code tier.layers()}) is {@link PackagingRefusalReason#LOT_INCOMPLETE}; a die
     * type and tier the catalog cannot name is {@link PackagingRefusalReason#NO_CHIP_FOR_DIE};
     * and an installed generation below the matrix requirement for {@link #LINE_ID} is
     * {@link PackagingRefusalReason#GENERATION_UNMET}. Both the catalog and the matrix
     * checks fail closed: a negative or zero installed generation never passes.</p>
     *
     * @param lot               the lot to admit, as read by the caller's trusted-content
     *                          read; may be {@code null}
     * @param tier              the tier resolved for the lot, not null
     * @param chipItemAvailable whether the chip catalog has a chip item for the lot's
     *                          die type at this tier
     * @param matrix            the generation matrix of the world, not null
     * @param installedGeneration the generation the machine's packaging line runs at
     * @return {@code null} when the lot may be judged and packaged, otherwise the
     *         refusal reason
     * @throws IllegalArgumentException if the tier is {@code null}, the tier does not
     *                                  match the lot's frozen run spec, or the matrix is
     *                                  {@code null},
     */
    public static PackagingRefusalReason validate(FoupContents lot,
                                                  Tier tier,
                                                  boolean chipItemAvailable,
                                                  GenerationMatrix matrix,
                                                  int installedGeneration) {
        if (lot == null || lot.isEmpty()) {
            return PackagingRefusalReason.NO_LOT;
        }
        RunSpec spec = lot.run();
        if (spec == null) {
            return PackagingRefusalReason.NO_SPEC;
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier must not be null");
        }
        if (tier.index() != spec.tierIndex()) {
            throw new IllegalArgumentException("the presented tier does not match the lot's frozen run spec");
        }
        int finalLayer = tier.layers();
        for (Wafer wafer : lot.wafers()) {
            if (wafer.layerIndex() != finalLayer) {
                return PackagingRefusalReason.LOT_INCOMPLETE;
            }
        }
        if (!chipItemAvailable) {
            return PackagingRefusalReason.NO_CHIP_FOR_DIE;
        }
        if (matrix == null) {
            throw new IllegalArgumentException("generation matrix must not be null");
        }
        if (!matrix.isSatisfied(LINE_ID, installedGeneration, tier.index())) {
            return PackagingRefusalReason.GENERATION_UNMET;
        }
        return null;
    }
}
