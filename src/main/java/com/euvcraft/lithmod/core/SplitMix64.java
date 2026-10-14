package com.euvcraft.lithmod.core;

/**
 * SplitMix64, the 64-bit mixing generator by Steele, Lea and Flood (Java 8
 * {@code SplittableRandom}).
 *
 * <p>Chosen because it is tiny, has no seeding ceremony, is fully specified by a
 * handful of well known constants, and produces a well studied equidistributed
 * stream. That matters here: the yield rolls must be reproducible from a context on
 * any machine and in any JVM version.</p>
 *
 * <p>The generator is not a CSPRNG. It is never seeded from anything the player can
 * observe; the seed comes from a server-only salt (see {@code FabRandomData}).</p>
 */
public class SplitMix64 implements Rng {

    /** The SplitMix64 gamma constant (2^64 / phi). */
    public static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    /**
     * Returns the SplitMix64 finalizer for {@code value}: a bijective avalanche
     * function that spreads every input bit across every output bit.
     *
     * @param value the value to scramble
     * @return the scrambled value
     */
    public static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Returns the next raw 64-bit output and advances the stream.
     *
     * @return the next 64-bit value
     */
    public long nextLong() {
        this.state += GAMMA;
        return mix64(this.state);
    }

    /**
     * Maps a raw 64-bit output onto {@code (0, 1]}.
     *
     * <p>The top 53 bits give a uniform double in {@code [0, 1)}; the complement
     * {@code 1 - u} is a bijection onto {@code (0, 1]}, so the result is still
     * uniform and can never be {@code 0}.</p>
     *
     * @return a value in {@code (0, 1]}
     */
    @Override
    public double nextDouble() {
        long bits = nextLong() >>> 11;
        double unit = bits * 0x1.0p-53;
        return 1.0 - unit;
    }

    /**
     * Returns the current internal state, for diagnostics and tests.
     *
     * @return the internal SplitMix64 state
     */
    public long state() {
        return this.state;
    }
}