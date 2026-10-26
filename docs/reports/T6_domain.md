# T6 Thermal Domain Implementation Report

## Overview

Implemented the scoped pure core helpers for the T6 thermal profile and exposure-tool
calibration per `build/t6/thermal/BRIEF.txt` and `SCOPE.json`. No Minecraft or NeoForge
types are imported; no commands, Git operations, network access, or delegation were
used. The coordinator owns executable verification.

## Files Created (all scoped files)

| File | Purpose |
|------|---------|
| `src/main/java/com/euvcraft/lithmod/core/ThermalProfile.java` | Pure thermal-profile model: nested `Controls`, `Response`, `Metrics`, `Rules`, `Problem`, `Assessment`; static `metrics`, `curve`, `assess` |
| `src/test/java/com/euvcraft/lithmod/core/ThermalProfileTest.java` | JUnit 5 tests in repo style (`@Nested`, `@DisplayName`): validation, determinism, causality, curve shape, scoring limits, solubility, immutability |
| `src/main/java/com/euvcraft/lithmod/core/CalibrationExposure.java` | Final helper with nested `State(int dirtyTicks, boolean invalidated)` and static `advance`, `recalibrated` |
| `src/test/java/com/euvcraft/lithmod/core/CalibrationExposureTest.java` | JUnit 5 tests: validation boundary, saturation/latch, paid-ticks-only, clean recovery, recalibration grace |
| `docs/reports/T6_domain.md` | This report |

## API Decisions

### ThermalProfile

- `Controls(int heater, int conveyor, int airflow)` — all rejected outside 0..100.
- `Response(double heatingScale, double peakOffset, double coolingScale)` — both scales
  must be finite and strictly positive; the peak offset only needs to be finite (it is an
  offset; 0 must be legal). `nominal()` = (1, 0, 1). The seeded factory is
  `Response.of(long seed)` (the brief's `response(seed)`); it is a pure function of the
  seed via `SplitMix64.mix64`, no rolling generator, with scales in [0.9, 1.1] and peak
  offset in [-5.0, 5.0].
- `Metrics(double ramp, double peak, double dwell, double cooling)` — all four must be
  finite.
- `Rules(...)` — every band must satisfy `0 <= min < max` with finite values, and the
  ramp/cooling band bottoms must additionally be strictly positive (they multiply
  positive scales); `minimumQuality` must be in 0..1000. `defaults()` = ramp 1..3,
  peak 230..245, dwell 30..70, cooling 1..4, minimum quality 750.
- `Problem` = `HEATING, PEAK, DWELL, COOLING`.
- `Assessment(int quality, boolean acceptable, Metrics metrics, Set<Problem> problems)`
  — rejects nulls and out-of-range quality; stores an unmodifiable copy of the problem
  set (`Set.copyOf`).
- Formulas exactly as briefed: ramp = (1 + heater*0.02) * heatingScale;
  peak = 180 + heater*1.15 - (conveyor-50)*0.1 + peakOffset; dwell = 100 - conveyor;
  cooling = (0.5 + airflow*0.03) * coolingScale.
- Curve: ambient 20.0; physical phase durations are ramp/dwell/cooling normalized by
  their sum (ramp and cooling are strictly positive, so no division by zero even at
  dwell 0); linear heating to the peak, flat plateau at the peak, linear cooling back to
  ambient at t = 1. `samples` must be >= 2; every value is finite.
- Quality scoring (the brief left the exact scoring to this implementation, documented
  here): each metric scores 0..1 against its band — 1.0 at the band midpoint, in-band it
  falls linearly to at least 0.875 at the band edge, beyond the edge it drops linearly to
  0 one half-band-width further out. Quality = round(250 * sum of the four scores), so
  0..1000. Acceptable = no problems AND quality >= minimumQuality.
- Consequence of the scoring: with the default bands, controls (50, 50, 67) score 1000
  against the nominal response (cooling 2.51 sits 0.01 under the 2.5 midpoint; the
  0.9983 sub-score rounds up), and controls (50, 50, 50) score 979 and are acceptable.

### Solubility of seeded responses

The brief requires every seeded response to admit a reachable acceptable solution. With
the variation ranges above, the fixed schedule heater=50, conveyor=50, airflow=67 is
acceptable against `Rules.defaults()` for every seed: ramp stays in [1.8, 2.2] within
1..3, peak stays within [232.5, 242.5] of 230..245, dwell stays 50, and cooling stays
within [2.26, 2.76] of 1..4. The test suite verifies this directly for seeds 1..20 and
also runs a coarse grid search (step 10, 1331 candidates) for seeds 1..10 and asserts a
solution is always found.

### CalibrationExposure

- `State(int dirtyTicks, boolean invalidated)` — dirtyTicks >= 0.
- `advance(current, dirty, paidTick, limitTicks)` — `limitTicks >= 1`, paid tick requires
  a non-null state (an unpaid tick returns the same instance untouched, so a null state
  with no paid tick is never dereferenced).
  - unpaid tick → returns the same state;
  - clean paid tick → `State(0, current.invalidated)` (counter cleared, latch preserved);
  - dirty paid tick → counter +1 saturating at the limit, latching invalidated the
    moment the counter reaches the limit.
- `recalibrated()` → `State(0, false)`, i.e. the grace budget restarts whether or not the
  tool is inside a room, as briefed. The caller blocks the NEXT batch when invalidated;
  the current batch keeps its frozen calibration (caller-side policy, documented on the
  class).

## Style Conformance

Read `YieldCalibration` / `YieldCalibrationTest` (Javadoc-rich style, `@Nested` +
`@DisplayName` JUnit 5), `BeamAlignment`, `CleanroomExposure`, and `ImportCheckerTest`
(core must not import Mojang/Minecraft/Netty types — none of the new files do) before
writing.

## Limitations

- **No execution evidence.** Per task constraints nothing was compiled or run. All
  expected values in the tests were derived by hand from the documented formulas;
  floating-point-sensitive assertions use 1e-9 tolerances or explicitly reachable
  margins (e.g. the expected quality values 979 / 985 / 219 sit far from rounding
  boundaries). The coordinator should run the Gradle test suite before integration.
- **`Response.of(long seed)` naming.** The brief writes `response(long seed)` without
  qualifying a receiver; it is implemented as `Response.of(long seed)`. If the
  coordinator wants the literal name, a one-line rename (or a static forwarder on
  `ThermalProfile`) is trivial.
- **Scoring default.** The brief left the exact quality function as a "reasonable
  documented implementation default"; the linear in-band/out-of-band scoring above is
  that default and is fully documented on `assess`. Tuning (e.g. weights per metric) is
  deliberately not included.
- **Band-edge inclusivity.** A metric exactly on a band edge is NOT a problem (edges
  inclusive). This is documented but was not stated either way in the brief; tests pin
  it down (heater 100 with nominal response gives ramp exactly 3.0 and no HEATING
  problem).
- **Not wired.** No machine block, menu, screen, codec or persistence touches these
  helpers yet; they are pure endpoints for later T6 integration, matching the scope.
- `Metrics` allows negative or physically odd values (e.g. a very negative peak from an
  extreme `peakOffset`); it validates finiteness only, and the curve renders such a case
  linearly without crashing. Callers gate reality via `Rules`.
