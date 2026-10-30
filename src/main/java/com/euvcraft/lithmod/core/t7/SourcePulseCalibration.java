package com.euvcraft.lithmod.core.t7;

import java.util.Random;

public final class SourcePulseCalibration {

    private SourcePulseCalibration() {}

    public record Controls(int phase, int delay, int amplitude) {}

    public record Target(int phase, int delay, int amplitude) {}

    public record Spec(int min, int max, int timingTolerance, int amplitudeTolerance,
                       int minimumQuality, int minMultiplierPermille, int maxMultiplierPermille) {

        public Spec {
            if (min < 0) throw new IllegalArgumentException("min must be >= 0");
            if (max > 100) throw new IllegalArgumentException("max must be <= 100");
            if (min >= max) throw new IllegalArgumentException("min must be < max");
            if (timingTolerance < 1 || timingTolerance > 100) throw new IllegalArgumentException("timingTolerance must be in 1..100");
            if (amplitudeTolerance < 1 || amplitudeTolerance > 100) throw new IllegalArgumentException("amplitudeTolerance must be in 1..100");
            if (minimumQuality < 0 || minimumQuality > 100) throw new IllegalArgumentException("minimumQuality must be in 0..100");
            if (minMultiplierPermille < 0 || minMultiplierPermille > 1000) throw new IllegalArgumentException("minMultiplierPermille must be in 0..1000");
            if (maxMultiplierPermille < 0 || maxMultiplierPermille > 1000) throw new IllegalArgumentException("maxMultiplierPermille must be in 0..1000");
            if (minMultiplierPermille > maxMultiplierPermille) throw new IllegalArgumentException("minMultiplierPermille must be <= maxMultiplierPermille");
        }
    }

    public record Measurement(int quality, int phaseError, int delayError, int amplitudeError, int responsePermille) {}

    public static Target target(long seed, Spec spec) {
        Random random = new Random(seed);
        int range = spec.max - spec.min + 1;
        int centralStart = spec.min + (int) Math.round(range * 0.2);
        int centralEnd = spec.max - (int) Math.round(range * 0.2);
        if (centralStart > centralEnd) {
            centralStart = centralEnd = (spec.min + spec.max) / 2;
        }
        int centralRange = centralEnd - centralStart + 1;
        int phase = centralStart + random.nextInt(centralRange);
        int delay = centralStart + random.nextInt(centralRange);
        int amplitude = centralStart + random.nextInt(centralRange);
        return new Target(phase, delay, amplitude);
    }

    public static Controls clamp(Controls controls, Spec spec) {
        int phase = Math.max(spec.min, Math.min(spec.max, controls.phase()));
        int delay = Math.max(spec.min, Math.min(spec.max, controls.delay()));
        int amplitude = Math.max(spec.min, Math.min(spec.max, controls.amplitude()));
        return new Controls(phase, delay, amplitude);
    }

    public static Measurement measure(Target target, Controls controls, Spec spec) {
        if (target.phase() < spec.min || target.phase() > spec.max ||
            target.delay() < spec.min || target.delay() > spec.max ||
            target.amplitude() < spec.min || target.amplitude() > spec.max) {
            throw new IllegalArgumentException("Target outside spec range");
        }
        if (controls.phase() < spec.min || controls.phase() > spec.max ||
            controls.delay() < spec.min || controls.delay() > spec.max ||
            controls.amplitude() < spec.min || controls.amplitude() > spec.max) {
            throw new IllegalArgumentException("Controls outside spec range");
        }

        int phaseError = controls.phase() - target.phase();
        int delayError = controls.delay() - target.delay();
        int amplitudeError = controls.amplitude() - target.amplitude();

        double phaseTerm = (double) phaseError * phaseError / (spec.timingTolerance() * spec.timingTolerance());
        double delayTerm = (double) delayError * delayError / (spec.timingTolerance() * spec.timingTolerance());
        double amplitudeTerm = (double) amplitudeError * amplitudeError / (spec.amplitudeTolerance() * spec.amplitudeTolerance());

        double exponent = -0.5 * (phaseTerm + delayTerm + amplitudeTerm);
        double qualityDouble = 100.0 * Math.exp(exponent);
        int quality = (int) Math.round(Math.max(0.0, Math.min(100.0, qualityDouble)));

        double amplitudeRatio = (double) (controls.amplitude() + 1) / (target.amplitude() + 1);
        double timingExponent = -0.5 * ((double) phaseError * phaseError + (double) delayError * delayError) / (spec.timingTolerance() * spec.timingTolerance());
        double responseDouble = 1000.0 * amplitudeRatio * Math.exp(timingExponent);
        int responsePermille = (int) Math.round(Math.max(0.0, Math.min(2000.0, responseDouble)));

        return new Measurement(quality, phaseError, delayError, amplitudeError, responsePermille);
    }

    public static int multiplierPermille(int quality, Spec spec) {
        if (quality < 0 || quality > 100) throw new IllegalArgumentException("quality must be in 0..100");
        double t = quality / 100.0;
        double result = spec.minMultiplierPermille() + t * (spec.maxMultiplierPermille() - spec.minMultiplierPermille());
        return (int) Math.round(result);
    }
}