# M1-02d: making `lithmod:yield_run_spec` deterministic

Date: 2026-10-16
Branch: `m1-02d-yield`
Base: `b991c48` (master, M1-03d merged)

## 1. Starting state (git proof)

```
=== git worktree list ===
C:/Users/ethan/OneDrive/Documents/Minecraft/euvcraftmk4nemo                    c44325b [m1-08-process-run-state-machine]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-03d-multiblock-blocks  b991c48 [m1-03d-multiblock-blocks]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02c-contents-drop                5e4b968 [m1-02c-contents-drop]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02d-yield                        b991c48 [m1-02d-yield]

=== git branch --show-current ===   (in the new worktree)
m1-02d-yield

=== git status --short ===
(empty = clean)

=== HEAD ===
b991c48 M1-03d: git proof and concerns for review
```

## 2. The flaky assertion

The test that failed ≈31% of the time:

```java
// YieldGameTests.runSpecTests, Test (a) extension
UUID lotId1 = UUID.randomUUID();
FoupContents contents1 = unprocessed(lotId1, 25);
...
for (int layer = 2; layer <= t1.layers(); layer++) {
    contents1 = FabYieldService.applyLayerStep(level, contents1, t1, "logic", null, layer, 1.0, changedSettings);
}
...
int passing1 = FabYieldService.countPassing(contents1, t1, "logic", null);
double rate1 = (double) passing1 / contents1.size();
helper.assertTrue(Math.abs(rate1 - originalYield) <= 0.10,
    "Lot 1 pass rate " + (rate1 * 100) + "% should match original yield " + (originalYield * 100) + "%");
```

and the analogous ones in Test (b) (25 wafers, ±10 points) and in `endToEndSimulation` (25 wafers × 40 lots, still ±10 points per tier). The name on the task is `yield_run_spec`, but the whole file had the same shape in three places.

## 3. What was changed

### 3.1 Production code

None. `RunSpec`, `YieldRollEngine`, `FabYieldService` and friends are unchanged — `git diff --stat` for `src/main` is empty.

### 3.2 `YieldGameTests.runSpecTests` — made structural

The three statistical assertions in the (a) extension and in Test (b) were replaced with exact,
non-statistical properties of the `RunSpec`:

- `contents1.run() != null`, and `|run().effectiveYield() - originalYield| <= 1e-12` — the lot
  froze the yield it was started under.
- `contents2.run() != null`, and `|run().effectiveYield() - changedYield| <= 1e-12` — a new lot
  started under the changed settings takes on the new yield.
- For Test (b): `contents.run() != null`, the effective yield stayed frozen at `expectedYield`
  and the roll count stayed at `specRollCount` (24).

These are the two structural facts the task named — *frozen spec*, *a new lot follows the new
setting*. The four `Math.abs(rate-yield) <= 0.10` assertions left in `endToEndSimulation`
are untouched: that test is a calibration harness reporting what the rolls gave, not a claim
about a specific lot, and the task scoped the change to `yield_run_spec`. They pass in
every run recorded below.

### 3.3 New core test

Added to `YieldRollEngineTest`:

```java
@Test
@DisplayName("a large run (fixed seed) stays within 4 standard deviations of the tier yield")
void aLargeRunStaysWithinFourStandardDeviations() {
    for (String id : new String[] { "T1", "T3", "T5" }) {
        Tier tier = ReferenceTiers.byId(id);
        FabSettings settings = FabSettings.defaults();
        double yield = YieldMath.resolveEffectiveYield(tier, settings, null);

        int wafersPerLot = 25;
        int lots = 48; // 1200 wafers, seeded SALT + lot
        int passing = 0;
        int simulated = 0;
        for (int lot = 0; lot < lots; lot++) {
            FoupContents processed = runThrough(tier, freshLot(wafersPerLot), null, 1.0,
                                               SALT + lot, settings);
            boolean[] outcomes = YieldRollEngine.evaluate(processed, tier, "logic", null);
            passing += YieldRollEngine.countPassing(outcomes);
            simulated += outcomes.length;
        }

        double rate = (double) passing / simulated;
        // Binomial standard deviation of the pass RATE: sigma(xbar) = sqrt(p*(1-p)/n).
        double standardDeviationOfRate = Math.sqrt(yield * (1.0 - yield) / simulated);
        double tolerance = 4.0 * standardDeviationOfRate;

        assertTrue(Math.abs(rate - yield) <= tolerance,
            id + " simulated " + (rate * 100.0) + "% against a target of " + (yield * 100.0) + "% "
                + "with " + simulated + " wafers; 4 sigma is " + (tolerance * 100.0) + "%");
    }
}
```

- **Fixed seed**: `SALT + lot`, same shape as the existing `YieldRollEngineTest` rolls.
- **At least 1000 wafers**: 1200 per tier, × 3 tiers = 3600.
- **4-σ tolerance computed from the binomial inside the test**: `4 * sqrt(p*(1-p)/n)`, with `p` the
  tier yield and `n` the realised wafer count. There is no magic ±0.1 written anywhere, so a
  future change to the tolerance has to show its work.
- At the produced `n`, the band is about ±2–3 percentage points depending on the tier yield, well
  inside what a binomial needs and far smaller than the old ±10 points on 25 wafers.

## 4. Why it was 31%: the math

The original assertion:

```
n := 25 wafers
X := number passing  ~ Binomial(25, 0.60)
assert |X/25 - 0.60| <= 0.10
       i.e. 13 <= X <= 17
```

P(pass) := P(13 ≤ X ≤ 17) = 0.6927, so P(fail) = 0.3073, about **once in three runs**.

The three failures seen in the M1-02c work reported pass rates of 80.0%, 72.0% and (in the
simulation) exactly the `k = 18` and `k = 20` binomial outcomes:

| outcome | k/25 | probability |
|---|---|---|
| fail (high)  | 18 / 25 = 0.72 | P(X=18) = 0.0800 |
| fail (worse) | 20 / 25 = 0.80 | P(X=20) = 0.0199 |

## 5. Task requirement 2 — script evidence

The command, its sources for the numbers, and its result, all in one place:

```text
$ javac .../Sim.java && java -cp ... Sim
old assertion (25 wafers, +-10 points) failures: 56 / 200
new assertion (1200 wafers, 4 sigma computed) failures: 0 / 200
```

Full script, so the trial is reproducible from the saved text alone:

```java
import java.util.Random;
class Sim {
  public static void main(String[] a) {
    Random R = new Random(0xDEADBEEFL);
    int N = 200;
    int wafersOld = 25;
    int oldFail = 0;
    for (int t = 0; t < N; t++) {          // 200 simulated trials
      int p = 0;
      for (int w = 0; w < wafersOld; w++) if (R.nextDouble() < 0.60) p++;
      double rate = (double) p / wafersOld;
      if (Math.abs(rate - 0.60) > 0.10) oldFail++;   // the old game-test band
    }

    int wafersNew = 1200;
    int newFail = 0;
    Random R2 = new Random(0xC0FFEE1L);
    for (int t = 0; t < N; t++) {
      int p = 0;
      for (int w = 0; w < wafersNew; w++) if (R2.nextDouble() < 0.60) p++;
      double rate = (double) p / wafersNew;
      double sd = Math.sqrt(0.60 * 0.40 / wafersNew);
      double tol = 4.0 * sd;              // the new band, computed from the binomial
      if (Math.abs(rate - 0.60) > tol) newFail++;
    }

    System.out.println("old assertion (25 wafers, +-10 points) failures: " + oldFail + " / " + N);
    System.out.println("new assertion (1200 wafers, 4 sigma computed) failures: " + newFail + " / " + N);
  }
}
```

The prediction was 0.3073 × 200 ≈ 62 failures; 56 is comfortably inside the binomial
confidence interval of a 31% event over 200 trials. 0/200 is exactly what a 4-σ band at n =
1200 is supposed to give (≈ 6 × 10⁻⁵ expected failures over 200 trials). The claimed
failure rate was reproduced to within noise; the proposed rule never fails.

## 6. Task requirement 3 — five runs

```
$ .\gradlew.bat runGameTestServer --offline   (×5)
run 1: ========= 705 GAME TESTS COMPLETE ... BUILD SUCCESSFUL
run 2: ========= 705 GAME TESTS COMPLETE ... BUILD SUCCESSFUL
run 3: ========= 705 GAME TESTS COMPLETE ... BUILD SUCCESSFUL
run 4: ========= 705 GAME TESTS COMPLETE ... BUILD SUCCESSFUL
run 5: ========= 705 GAME TESTS COMPLETE ... BUILD SUCCESSFUL
```

- `lithmod:yield_run_spec` failed **0 out of 5** times.
- No required test failed in any of the 5 runs.
- The baseline count is 705 rather than 711 because this branch is based on `master` at
  `b991c48`, before M1-02c was merged: 5 machine_drop tests fewer, not 1071 tests fewer.
  `lithmod` contributes 35 to the run count here (28 + M1-03d's 7 multiblock tests).

## 7. Real output

```
$ .\gradlew.bat test --tests "com.euvcraft.lithmod.core.YieldRollEngineTest" --offline
BUILD SUCCESSFUL in 6s

$ .\gradlew.bat test checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in ...
```

The new test takes ~0.25 s in its own run; the whole `YieldRollEngineTest` suite is green.

## 8. Files changed

| Path | Status |
|---|---|
| `src/main/java/com/euvcraft/lithmod/mc/gametest/YieldGameTests.java` | three statistical `assertTrue(Math.abs(rate - ...))` in `runSpecTests` replaced with exact structural assertions |
| `src/test/java/com/euvcraft/lithmod/core/YieldRollEngineTest.java` | `aLargeRunStaysWithinFourStandardDeviations` added |

No other file, including production code, was changed. `endToEndSimulation` keeps its
four `Math.abs(rate-yield) <= 0.10` lines, which the task did not ask to touch.