package com.euvcraft.lithmod.core;

import java.util.Objects;
import java.util.UUID;

/**
 * A deterministic random source bound to one yield-roll context.
 *
 * <p>A context is {@code (salt, lotId, waferIndex, stepIndex)}: the server-only salt,
 * the FOUP lot the wafer belongs to, the position of the wafer inside that lot, and
 * the index of the process step being rolled. The same context always produces the
 * same sequence and two contexts that differ in any component produce unrelated
 * sequences.</p>
 *
 * <p><strong>Why this exists.</strong> Rolls are deterministic per context so that
 * reloading the world and redoing a step gives the same result (no save-scumming),
 * while players cannot predict them without the salt. The salt is generated once on
 * the server from secure entropy, stored with the world, and never derived from the
 * world seed or from anything a client receives.</p>
 */
public final class ContextualRng implements Rng {

    private static final long SALT_OFFSET = 0x8EBC6AF09C88C6E3L;
    private static final long LOT_MSB_OFFSET = 0x9E3779B97F4A7C15L;
    private static final long LOT_LSB_OFFSET = 0xD6E8FEB86659FD93L;
    private static final long WAFER_OFFSET = 0xA0761D6478BD642FL;
    private static final long STEP_OFFSET = 0xE7037ED1A0B428DBL;

    private final long salt;
    private final UUID lotId;
    private final int waferIndex;
    private final int stepIndex;
    private final SplitMix64 generator;

    /**
     * Creates a generator for one context and starts it at the beginning of its stream.
     *
     * @param salt       server-only random salt; {@code 0} is a legal value
     * @param lotId      the sealed lot (FOUP) the wafer belongs to
     * @param waferIndex position of the wafer inside the lot, {@code >= 0}
     * @param stepIndex  index of the process step, {@code >= 0}
     * @throws NullPointerException     if {@code lotId} is null
     * @throws IllegalArgumentException if an index is negative
     */
    public ContextualRng(long salt, UUID lotId, int waferIndex, int stepIndex) {
        Objects.requireNonNull(lotId, "lotId must not be null");
        if (waferIndex < 0) {
            throw new IllegalArgumentException("waferIndex must be >= 0, got " + waferIndex);
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must be >= 0, got " + stepIndex);
        }
        this.salt = salt;
        this.lotId = lotId;
        this.waferIndex = waferIndex;
        this.stepIndex = stepIndex;
        this.generator = new SplitMix64(seedFor(salt, lotId, waferIndex, stepIndex));
    }

    /**
     * Folds a context into a single 64-bit SplitMix64 seed.
     *
     * <p>Every component is offset by a different odd constant and run through the
     * SplitMix64 finalizer before being combined, so changing any one component
     * changes the whole stream instead of a few low bits of it.</p>
     *
     * @param salt       server-only random salt
     * @param lotId      the sealed lot the wafer belongs to
     * @param waferIndex position of the wafer inside the lot
     * @param stepIndex  index of the process step
     * @return the derived seed
     */
    public static long seedFor(long salt, UUID lotId, int waferIndex, int stepIndex) {
        Objects.requireNonNull(lotId, "lotId must not be null");
        long combined = SplitMix64.mix64(salt + SALT_OFFSET);
        combined ^= SplitMix64.mix64(lotId.getMostSignificantBits() + LOT_MSB_OFFSET);
        combined ^= SplitMix64.mix64(lotId.getLeastSignificantBits() + LOT_LSB_OFFSET);
        combined ^= SplitMix64.mix64(waferIndex + WAFER_OFFSET);
        combined ^= SplitMix64.mix64(stepIndex + STEP_OFFSET);
        return SplitMix64.mix64(combined);
    }

    /**
     * Returns the next raw 64-bit output and advances the stream.
     *
     * @return the next 64-bit value
     */
    public long nextLong() {
        return this.generator.nextLong();
    }

    /**
     * Returns the next value, uniform over {@code (0, 1]}.
     *
     * @return a value in {@code (0, 1]}
     */
    @Override
    public double nextDouble() {
        return this.generator.nextDouble();
    }

    /**
     * Returns the salt this generator was seeded with. Package private on purpose: the
     * salt is a secret and must not be logged or sent to a client.
     *
     * @return the 64-bit salt
     */
    long salt() {
        return this.salt;
    }

    public UUID lotId() {
        return this.lotId;
    }

    public int waferIndex() {
        return this.waferIndex;
    }

    public int stepIndex() {
        return this.stepIndex;
    }

    @Override
    public String toString() {
        return "ContextualRng[lot=" + this.lotId + ", wafer=" + this.waferIndex + ", step=" + this.stepIndex + "]";
    }
}