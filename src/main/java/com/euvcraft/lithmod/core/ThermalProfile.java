package com.euvcraft.lithmod.core;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure thermal-profile model for a T6 reflow assembly run.
 *
 * <p><strong>The model.</strong> A run is described by three player-facing knobs,
 * {@link Controls heater / conveyor / airflow}, all in 0..100, and by the
 * <em>equipment response</em> of the individual machine, {@link Response}, a small
 * per-machine triple of heating scale, peak offset and cooling scale. Together they fix
 * four measured {@link Metrics}:
 *
 * <ul>
 *   <li>{@code ramp} = {@code (1 + heater * 0.02) * heatingScale} &mdash; how fast the wafer
 *       climbs toward the peak temperature,</li>
 *   <li>{@code peak} = {@code 180 + heater * 1.15 - (conveyor - 50) * 0.1 + peakOffset} &mdash; the
 *       top temperature reached,</li>
 *   <li>{@code dwell} = {@code 100 - conveyor} &mdash; how long the peak is held,</li>
 *   <li>{@code cooling} = {@code (0.5 + airflow * 0.03) * coolingScale} &mdash; how hard the
 *       exhaust pulls the temperature back down.</li>
 * </ul>
 *
 * <p>{@link Rules bands} say which metric values count as good; {@link #assess} then scores a
 * run on a 0..1000 quality scale that is best exactly at the band midpoints, lists the bands it
 * fell outside of as {@link Problem}s, and calls the run {@code acceptable} when it has no
 * problems and clears the rules' minimum quality. {@link #curve} renders the same model as a
 * temperature-over-time array for the UI preview.</p>
 *
 * <p><strong>Discipline.</strong> Everything here is pure arithmetic: no random rerolls, no
 * timers, no level or thread access, and no item, energy or consumable accounting. The live
 * client preview uses {@link Response#nominal()}; only server-paid measurements hand the UI a
 * measured {@link Response}. The UI reads temperatures through {@link #curve} only.</p>
 *
 * <p>This class has no Minecraft or NeoForge dependencies.</p>
 */
public final class ThermalProfile {

    /** Ambient (room) temperature the cycle starts and ends at, in degrees. */
    public static final double AMBIENT = 20.0;

    /** Lowest knob position, shared by all three {@link Controls} knobs. */
    public static final int KNOB_MIN = 0;

    /** Highest knob position, shared by all three {@link Controls} knobs. */
    public static final int KNOB_MAX = 100;

    /**
     * Quality lost at the far edge of a band relative to its midpoint, as a fraction of a full
     * score: a run sitting exactly on a band edge scores {@code 1 - 0.25 * 0.5 = 0.875}.
     */
    private static final double IN_BAND_SLOPE = 0.25;

    /** Score of a run exactly on a band edge (continuous with the in-band side). */
    private static final double EDGE_SCORE = 1.0 - IN_BAND_SLOPE * 0.5;

    /** How fast the score decays per half-width of travel beyond a band edge. */
    private static final double OUT_OF_BAND_SLOPE = 1.75;

    public static Response response(long seed) { return Response.of(seed); }

    private ThermalProfile() {
    }

    /** The three furnace knobs, each in {@link #KNOB_MIN}..{@link #KNOB_MAX}. */
    public record Controls(int heater, int conveyor, int airflow) {
        public Controls {
            if (heater < KNOB_MIN || heater > KNOB_MAX
                || conveyor < KNOB_MIN || conveyor > KNOB_MAX
                || airflow < KNOB_MIN || airflow > KNOB_MAX) {
                throw new IllegalArgumentException("controls must be in " + KNOB_MIN + ".." + KNOB_MAX
                    + ", got heater=" + heater + " conveyor=" + conveyor + " airflow=" + airflow);
            }
        }
    }

    /**
     * The measured equipment response of one machine: two strictly positive, finite scales and a
     * finite peak offset. The live preview uses {@link #nominal()}; a machine's real response is
     * only known after server-paid tests and is then fed back here as-is.
     */
    public record Response(double heatingScale, double peakOffset, double coolingScale) {
        public Response {
            if (!isPositiveFinite(heatingScale) || !isPositiveFinite(coolingScale)) {
                throw new IllegalArgumentException("scales must be finite and positive, got heatingScale="
                    + heatingScale + " coolingScale=" + coolingScale);
            }
            if (!Double.isFinite(peakOffset)) {
                throw new IllegalArgumentException("peakOffset must be finite, got " + peakOffset);
            }
        }

        /** The idealized response used when a machine has not been measured yet. */
        public static Response nominal() {
            return new Response(1.0, 0.0, 1.0);
        }

        /**
         * Returns a stable small equipment response derived from {@code seed}.
         *
         * <p>The result is a pure function of the seed, down to the last bit, mixed through
         * {@link SplitMix64#mix64}. Variations stay small enough that a mid-range control
         * schedule remains acceptable against {@link Rules#defaults()}, so every seeded
         * response admits a reachable acceptable solution; a UI or server hands the same
         * machine the same seed and therefore the same response every time.</p>
         *
         * @param seed the machine's stable identity seed
         * @return a response with both scales in {@code [0.9, 1.1]} and the peak offset in
         *         {@code [-5, 5]}
         */
        public static Response of(long seed) {
            double u1 = unit(SplitMix64.mix64(seed));
            double u2 = unit(SplitMix64.mix64(seed ^ 0x9E3779B97F4A7C15L));
            double u3 = unit(SplitMix64.mix64(seed ^ 0xC2B2AE3D27D4EB4FL));
            double heatingScale = 0.9 + 0.2 * u1;
            double peakOffset = 10.0 * (u2 - 0.5);
            double coolingScale = 0.9 + 0.2 * u3;
            return new Response(heatingScale, peakOffset, coolingScale);
        }

        private static double unit(long bits) {
            return (bits >>> 11) * 0x1.0p-53;
        }
    }

    /** The four measured numbers a run produces, all finite. */
    public record Metrics(double ramp, double peak, double dwell, double cooling) {
        public Metrics {
            if (!Double.isFinite(ramp) || !Double.isFinite(peak)
                || !Double.isFinite(dwell) || !Double.isFinite(cooling)) {
                throw new IllegalArgumentException("metrics must be finite, got ramp=" + ramp
                    + " peak=" + peak + " dwell=" + dwell + " cooling=" + cooling);
            }
        }
    }

    /**
     * The band each metric must land in, plus the minimum quality a run must still score
     * before it counts as acceptable. {@link #defaults()} carries the illustrative gameplay
     * bands; a tier or recipe can tighten them.
     */
    public record Rules(double minRamp, double maxRamp,
                        double minPeak, double maxPeak,
                        double minDwell, double maxDwell,
                        double minCooling, double maxCooling,
                        int minimumQuality) {
        public Rules {
            if (!isPairValid(minRamp, maxRamp, true)) {
                throw new IllegalArgumentException("ramp band must be 0 < min < max, got "
                    + minRamp + ".." + maxRamp);
            }
            if (!isPairValid(minPeak, maxPeak, false)) {
                throw new IllegalArgumentException("peak band must be 0 <= min < max with finite values, got "
                    + minPeak + ".." + maxPeak);
            }
            if (!isPairValid(minDwell, maxDwell, false)) {
                throw new IllegalArgumentException("dwell band must be 0 <= min < max with finite values, got "
                    + minDwell + ".." + maxDwell);
            }
            if (!isPairValid(minCooling, maxCooling, true)) {
                throw new IllegalArgumentException("cooling band must be 0 < min < max, got "
                    + minCooling + ".." + maxCooling);
            }
            if (minimumQuality < 0 || minimumQuality > 1000) {
                throw new IllegalArgumentException("minimumQuality must be in 0..1000, got " + minimumQuality);
            }
        }

        /**
         * Returns the illustrative default gameplay bands: ramp 1..3, peak 230..245,
         * dwell 30..70, cooling 1..4, and a minimum quality of 750.
         */
        public static Rules defaults() {
            return new Rules(1.0, 3.0, 230.0, 245.0, 30.0, 70.0, 1.0, 4.0, 750);
        }

        private static boolean isPairValid(double min, double max, boolean strictlyPositive) {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0 || min >= max) {
                return false;
            }
            return !strictlyPositive || min > 0.0;
        }
    }

    /** Which band a run fell outside of. */
    public enum Problem {
        /** The ramp was outside the ramp band. */
        HEATING,
        /** The peak temperature was outside the peak band. */
        PEAK,
        /** The dwell was outside the dwell band. */
        DWELL,
        /** The cooling was outside the cooling band. */
        COOLING
    }

    /**
     * The verdict for one control schedule against one rule set: a quality in 0..1000, the
     * measured {@link Metrics}, an unmodifiable set of {@link Problem}s, and whether the run
     * is acceptable (no problems and quality at or above the rules' minimum).
     */
    public record Assessment(int quality, boolean acceptable, Metrics metrics, Set<Problem> problems) {
        public Assessment {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(problems, "problems");
            if (quality < 0 || quality > 1000) {
                throw new IllegalArgumentException("quality must be in 0..1000, got " + quality);
            }
            problems = Set.copyOf(problems);
        }
    }

    /**
     * Computes the {@link Metrics} of one control schedule against one equipment response.
     *
     * @param controls the knob positions, not null
     * @param response the equipment response, not null
     * @return the four metrics, all finite
     */
    public static Metrics metrics(Controls controls, Response response) {
        Objects.requireNonNull(controls, "controls");
        Objects.requireNonNull(response, "response");
        double ramp = (1.0 + controls.heater() * 0.02) * response.heatingScale();
        double peak = 180.0 + controls.heater() * 1.15
            - (controls.conveyor() - 50) * 0.1
            + response.peakOffset();
        double dwell = 100.0 - controls.conveyor();
        double cooling = (0.5 + controls.airflow() * 0.03) * response.coolingScale();
        return new Metrics(ramp, peak, dwell, cooling);
    }

    /**
     * Renders the temperature curve of one run as {@code samples} temperatures over normalized
     * time 0..1.
     *
     * <p>The cycle has three phases. Its physical durations are the {@code ramp}, {@code dwell}
     * and {@code cooling} metrics; normalized phase boundaries are those durations divided by
     * their sum, so:
     *
     * <ul>
     *   <li>the temperature heats linearly from {@link #AMBIENT} to the peak over the first
     *       boundary,</li>
     *   <li>holds the peak flat through the dwell,</li>
     *   <li>and cools linearly back down to {@link #AMBIENT} at {@code t = 1}.</li>
     * </ul>
     *
     * <p>A zero dwell phase merely collapses the plateau; the ramp and cooling durations are
     * strictly positive, so the sum is never zero and no division by zero can occur. All
     * returned values are finite.</p>
     *
     * @param controls the knob positions, not null
     * @param response the equipment response, not null
     * @param samples  the number of curve points, at least 2 (positions 0 and 1 included)
     * @return exactly {@code samples} finite temperatures in degrees
     * @throws IllegalArgumentException if {@code samples} is below two or an argument is null
     */
    public static double[] curve(Controls controls, Response response, int samples) {
        Objects.requireNonNull(controls, "controls");
        Objects.requireNonNull(response, "response");
        if (samples < 2) {
            throw new IllegalArgumentException("samples must be at least 2, got " + samples);
        }
        Metrics m = metrics(controls, response);
        double total = m.ramp() + m.dwell() + m.cooling();
        double rampEnd = m.ramp() / total;
        double dwellEnd = rampEnd + m.dwell() / total;
        double coolingSpan = 1.0 - dwellEnd;

        double[] curve = new double[samples];
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (samples - 1);
            curve[i] = temperatureAt(t, m.peak(), rampEnd, dwellEnd, coolingSpan);
        }
        return curve;
    }

    /** Linear heating, flat plateau, linear cooling; every output is finite. */
    private static double temperatureAt(double t, double peak, double rampEnd, double dwellEnd,
                                        double coolingSpan) {
        if (t < rampEnd) {
            return AMBIENT + (peak - AMBIENT) * (t / rampEnd);
        }
        if (t <= dwellEnd) {
            return peak;
        }
        double cooled = (t - dwellEnd) / coolingSpan;
        return AMBIENT + (peak - AMBIENT) * (1.0 - cooled);
    }

    /**
     * Scores one control schedule against one rule set.
     *
     * <p><strong>Quality.</strong> Each of the four metrics is scored 0..1 against its band
     * {@code [lo, hi]}: the score is 1 at the band midpoint, in-band it falls linearly with the
     * distance from the midpoint to at least {@code 0.875} on the band edge, and beyond the edge
     * it keeps falling linearly and reaches 0 one half-band-width further out. The run's quality
     * is the rounded mean of the four scores on the 0..1000 scale, so the best runs sit close to
     * every band midpoint: drifting from a midpoint costs a little, crossing a band edge
     * costs a lot.</p>
     *
     * <p><strong>Acceptance.</strong> A run is acceptable exactly when it has no {@link Problem}
     * (all four metrics inside their bands) and its quality is at or above the rules'
     * {@code minimumQuality}. The {@link Assessment#problems()} set is unmodifiable.</p>
     *
     * @param controls the knob positions, not null
     * @param response the equipment response, not null
     * @param rules    the bands and minimum quality, not null
     * @return the assessment
     */
    public static Assessment assess(Controls controls, Response response, Rules rules) {
        Objects.requireNonNull(rules, "rules");
        Metrics m = metrics(controls, response);

        EnumSet<Problem> problems = EnumSet.noneOf(Problem.class);
        if (m.ramp() < rules.minRamp() || m.ramp() > rules.maxRamp()) {
            problems.add(Problem.HEATING);
        }
        if (m.peak() < rules.minPeak() || m.peak() > rules.maxPeak()) {
            problems.add(Problem.PEAK);
        }
        if (m.dwell() < rules.minDwell() || m.dwell() > rules.maxDwell()) {
            problems.add(Problem.DWELL);
        }
        if (m.cooling() < rules.minCooling() || m.cooling() > rules.maxCooling()) {
            problems.add(Problem.COOLING);
        }

        double total = bandScore(m.ramp(), rules.minRamp(), rules.maxRamp())
            + bandScore(m.peak(), rules.minPeak(), rules.maxPeak())
            + bandScore(m.dwell(), rules.minDwell(), rules.maxDwell())
            + bandScore(m.cooling(), rules.minCooling(), rules.maxCooling());
        int quality = clampQuality((int) Math.round(250.0 * total));

        boolean acceptable = problems.isEmpty() && quality >= rules.minimumQuality();
        return new Assessment(quality, acceptable, m, problems);
    }

    /**
     * Scores one metric against one band: 1 at the midpoint, at least {@code 0.875} anywhere
     * in-band, falling to 0 one half-width past the edge, and 0 beyond that.
     */
    private static double bandScore(double value, double lo, double hi) {
        double mid = (lo + hi) / 2.0;
        double half = (hi - lo) / 2.0;
        double error = (value - mid) / half;
        if (value >= lo && value <= hi) {
            return 1.0 - IN_BAND_SLOPE * Math.abs(error);
        }
        double beyond = Math.abs(error) - 0.5;
        return Math.max(0.0, EDGE_SCORE - OUT_OF_BAND_SLOPE * beyond);
    }

    private static int clampQuality(int quality) {
        return Math.min(1000, Math.max(0, quality));
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
