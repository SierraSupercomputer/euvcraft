package com.euvcraft.lithmod.core.routing;

/** Pressure and speed have different effects on removal and uniformity. */
public final class PolishCalibration {
    public static final int MAX_SETTING = 20;
    public static final int INITIAL_SETTING = 10;

    private PolishCalibration() {}

    public record Measurement(int removalError, int edgeError, int quality) {}

    public static Measurement measure(int pressure, int speed, int targetPressure, int targetSpeed,
            int removalTolerance, int edgeTolerance) {
        int pressureError = pressure - targetPressure;
        int speedError = speed - targetSpeed;
        int removal = 2 * pressureError + speedError;
        int edge = pressureError - 2 * speedError;
        int quality = Math.min(G3EtchTuning.quality(removal, removalTolerance),
                G3EtchTuning.quality(edge, edgeTolerance));
        return new Measurement(removal, edge, quality);
    }
}
