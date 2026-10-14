package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Converts a wafer list to and from three parallel primitive arrays.
 *
 * <p>Both directions of the conversion are codec agnostic: the core decides what a
 * wafer is, and the storage layer (NBT data component, network buffer) decides how
 * the arrays are written down. Keeping the arrays primitive and parallel is what
 * makes the stored lot small: no per-wafer object header, no per-wafer field name
 * and no boxed element.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class WaferPacking {

    private WaferPacking() {
    }

    /**
     * Three parallel arrays holding the same wafers in the same order.
     *
     * <p>The arrays are copied on construction, so a {@code Packed} never aliases an
     * array a caller still holds, and {@link #layers()}, {@link #rolls()} and
     * {@link #flags()} hand out copies too. Every accessor therefore costs a copy;
     * that is the price of never handing out a mutable array.</p>
     *
     * <p>{@link #equals(Object)} is compared by contents, because a record would
     * compare the arrays by identity, which is never what a caller means here.</p>
     *
     * @param layers layer index of each wafer
     * @param rolls  cumulative roll of each wafer, in {@code (0, 1]}
     * @param flags  reserved flags of each wafer
     */
    public record Packed(int[] layers, float[] rolls, int[] flags) {

        /**
         * Validates the lengths and copies the arrays.
         *
         * @throws NullPointerException     if any array is null
         * @throws IllegalArgumentException if the three arrays differ in length
         */
        public Packed {
            Objects.requireNonNull(layers, "layers must not be null");
            Objects.requireNonNull(rolls, "rolls must not be null");
            Objects.requireNonNull(flags, "flags must not be null");
            if (layers.length != rolls.length || layers.length != flags.length) {
                throw new IllegalArgumentException("layers (" + layers.length + "), rolls (" + rolls.length
                    + ") and flags (" + flags.length + ") must have the same length");
            }
            layers = layers.clone();
            rolls = rolls.clone();
            flags = flags.clone();
        }

        /**
         * Returns the layer index of each wafer.
         *
         * @return a fresh copy of the layer array
         */
        @Override
        public int[] layers() {
            return this.layers.clone();
        }

        /**
         * Returns the cumulative roll of each wafer.
         *
         * @return a fresh copy of the roll array
         */
        @Override
        public float[] rolls() {
            return this.rolls.clone();
        }

        /**
         * Returns the flags of each wafer.
         *
         * @return a fresh copy of the flags array
         */
        @Override
        public int[] flags() {
            return this.flags.clone();
        }

        /**
         * Returns the number of wafers.
         *
         * @return the shared array length
         */
        public int size() {
            return this.layers.length;
        }

        /**
         * Returns whether no wafer is packed.
         *
         * @return {@code true} if all three arrays are empty
         */
        public boolean isEmpty() {
            return this.layers.length == 0;
        }

        /**
         * Compares the three arrays by contents. A record would compare them by
         * identity, which is never what a caller means here.
         *
         * @param other the object to compare with
         * @return {@code true} if all three arrays hold equal values
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Packed packed
                && Arrays.equals(this.layers, packed.layers)
                && Arrays.equals(this.rolls, packed.rolls)
                && Arrays.equals(this.flags, packed.flags);
        }

        /**
         * Returns a hash code over the contents of the three arrays.
         *
         * @return a content based hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(this.layers), Arrays.hashCode(this.rolls),
                Arrays.hashCode(this.flags));
        }

        /**
         * Prints the packing without its hidden values.
         *
         * @return the wafer count only
         */
        @Override
        public String toString() {
            return "Packed[wafers=" + this.layers.length + "]";
        }
    }

    /**
     * Writes the layer index of each wafer into a fresh array.
     *
     * @param wafers the wafers to read, not null
     * @return one layer index per wafer, in order
     */
    public static int[] packLayers(List<Wafer> wafers) {
        int size = requireSize(wafers);
        int[] layers = new int[size];
        for (int i = 0; i < size; i++) {
            layers[i] = requireWafer(wafers, i).layerIndex();
        }
        return layers;
    }

    /**
     * Writes the cumulative roll of each wafer into a fresh array of raw IEEE-754
     * bit patterns.
     *
     * @param wafers the wafers to read, not null
     * @return one roll bit pattern per wafer, in order
     */
    public static int[] packRollBits(List<Wafer> wafers) {
        int size = requireSize(wafers);
        int[] bits = new int[size];
        for (int i = 0; i < size; i++) {
            bits[i] = Float.floatToRawIntBits(requireWafer(wafers, i).cumulativeRoll());
        }
        return bits;
    }

    /**
     * Writes the flags of each wafer into a fresh array.
     *
     * @param wafers the wafers to read, not null
     * @return one flag value per wafer, in order
     */
    public static int[] packFlags(List<Wafer> wafers) {
        int size = requireSize(wafers);
        int[] flags = new int[size];
        for (int i = 0; i < size; i++) {
            flags[i] = requireWafer(wafers, i).flags();
        }
        return flags;
    }

    /**
     * Turns raw IEEE-754 bit patterns back into a fresh float array.
     *
     * @param bits one bit pattern per wafer, not null
     * @return the equivalent float values, in order
     */
    public static float[] unpackRollBits(int[] bits) {
        Objects.requireNonNull(bits, "bits must not be null");
        float[] rolls = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            rolls[i] = Float.intBitsToFloat(bits[i]);
        }
        return rolls;
    }

    /**
     * Unpacks three parallel arrays back into a wafer list.
     *
     * <p>The roll range is checked by {@link Wafer}'s constructor, so an out-of-range
     * or non-finite roll is rejected here rather than reaching a machine.</p>
     *
     * @param layers layer index of each wafer
     * @param rolls  cumulative roll of each wafer
     * @param flags  reserved flags of each wafer
     * @return the wafers in the given order
     * @throws NullPointerException     if any array is null
     * @throws IllegalArgumentException if the three arrays differ in length or a roll
     *                                  is outside {@code (0, 1]}
     */
    public static List<Wafer> unpack(int[] layers, float[] rolls, int[] flags) {
        Packed packed = new Packed(layers, rolls, flags);
        int size = packed.size();
        List<Wafer> wafers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            wafers.add(new Wafer(packed.layers[i], packed.rolls[i], packed.flags[i]));
        }
        return List.copyOf(wafers);
    }

    private static int requireSize(List<Wafer> wafers) {
        return Objects.requireNonNull(wafers, "wafers must not be null").size();
    }

    private static Wafer requireWafer(List<Wafer> wafers, int index) {
        return Objects.requireNonNull(wafers.get(index), "wafer " + index + " must not be null");
    }
}