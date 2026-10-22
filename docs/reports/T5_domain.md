# T5 Diagnostic Fit Domain

DiagnosticFit is an immutable pure record for the diagnostic curve tuning challenge. No Minecraft types are involved.

## Record signature
`public record DiagnosticFit(int a, int b, int c, int targetA, int targetB, int targetC)`

All six fields are integer hundredths (e.g., 123 represents 1.23).

## Bounds
- `MIN = -200` (-2.00)
- `MAX = 200` (2.00)
- Constructor validates all six values; throws `IllegalArgumentException` if any is outside `[-200, 200]`.
- Target coefficients at creation are generated in `[-100, 100]` (-1.00 to 1.00). Candidate coefficients start at zero.

## Deterministic creation
`public static DiagnosticFit create(long seed)`
- Uses `java.util.Random(seed)` to generate three target coefficients in `[-100, 100]`.
- Same seed always produces the same targets.
- Candidates are zero.

## Plot action
`public DiagnosticFit plot(int a, int b, int c)`
- Returns a new record with candidate coefficients clamped to `[-200, 200]`.
- Target coefficients are retained unchanged.
- Original record is not mutated.

## Curve evaluation
`public double targetAt(double x)`
`public double candidateAt(double x)`
- Formula: `y = A*x² + b*x + c` with coefficients divided by `100.0`.
- `targetAt` uses target coefficients; `candidateAt` uses candidate coefficients.

## Quality metric
`public int quality()`
- Mean squared error over exactly 41 samples: `x = -2.0, -1.9, ..., 1.9, 2.0` (step 0.1).
- `MSE = mean((targetAt(x) - candidateAt(x))²)`
- `RMSE = sqrt(MSE)`
- `raw = 100 * (1 - RMSE / 4.0)`
- `quality = clamp(round(raw), 0, 100)`
- Perfect fit (MSE = 0) yields quality 100.
- Quality is an integer 0..100 inclusive.

## Multiplier
`public double multiplier(double min, double max)`
- Formula: `min + (max - min) * quality / 100`
- Preconditions (throw `IllegalArgumentException`):
  - `min` and `max` must be finite.
  - `0 < min <= max <= 1`
- Zero is explicitly unsupported; accepted process contracts require a positive factor.

## Design notes
- Pure domain logic; does not consume paper or apply yield. The native server owns burn-paper consumption and lot admission.
- The 41-sample fixed viewport prevents gaming by zoom or invalid numbers.
- Targets are generated at hundredth precision; scoring uses normalized curve error over a fixed domain, not raw coefficient error.
- No hidden lot yield, private rolls, timers, reflexes, or random equipment failure.
- Quality and multiplier are pure arithmetic: the same state always gives the same result.