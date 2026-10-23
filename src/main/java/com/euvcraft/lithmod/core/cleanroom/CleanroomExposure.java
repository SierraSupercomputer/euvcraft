/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core.cleanroom;

/**
 * Accumulator for cleanroom exposure samples.
 * Counts only actual paid processing ticks. No item or wafer mutation here.
 * This class has no Minecraft or NeoForge dependencies.
 */
public record CleanroomExposure(long samples, long permilleSum) {
    public CleanroomExposure {
        if(samples<0||permilleSum<0||samples==0&&permilleSum!=0||samples>0&&(permilleSum<samples||permilleSum/samples>1000||(permilleSum/samples==1000&&permilleSum%samples!=0)))throw new IllegalArgumentException("Invalid room exposure");
    }

    /**
     * Returns an empty exposure accumulator.
     */
    public static CleanroomExposure empty() {
        return new CleanroomExposure(0L, 0L);
    }

    /**
     * Records a new sample and returns a new accumulator.
     *
     * @param factorPermille the yield factor in permille (1..1000)
     * @return new accumulator with the sample added
     * @throws IllegalArgumentException if factorPermille is not in range [1, 1000]
     * @throws ArithmeticException if the maximum number of samples would be exceeded
     */
    public CleanroomExposure sample(int factorPermille) {
        if (factorPermille < 1 || factorPermille > 1000) {
            throw new IllegalArgumentException("factorPermille must be in range [1, 1000], got " + factorPermille);
        }
        if (samples >= Long.MAX_VALUE) {
            throw new ArithmeticException("maximum number of samples exceeded");
        }
        // Check for overflow on sum
        if (permilleSum > Long.MAX_VALUE - factorPermille) {
            throw new ArithmeticException("permille sum would overflow");
        }
        return new CleanroomExposure(samples + 1, permilleSum + factorPermille);
    }

    /**
     * Returns the mean yield factor in permille.
     *
     * @return rounded mean, or 500 if no samples
     * @throws ArithmeticException if the mean cannot be computed exactly due to overflow
     */
    public int meanPermille() {
        if (samples == 0) {
            return 500;
        }
        // Bounded exact averaging: sum/samples fits in long, then round to int
        // Since samples <= Long.MAX_VALUE and each sample <= 1000, sum <= 1000 * Long.MAX_VALUE
        // But we already prevented overflow on sum, so sum/samples <= 1000
        long mean = permilleSum / samples;
        if (mean > Integer.MAX_VALUE) {
            throw new ArithmeticException("mean exceeds int range");
        }
        return (int) (mean + (permilleSum % samples >= (samples / 2 + samples % 2) ? 1 : 0));
    }
}