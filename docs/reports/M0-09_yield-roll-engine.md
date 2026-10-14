# M0-09: Yield roll engine

- Date: 2026-10-14
- Branch: `m0-09-yield-roll-engine` (from `master` at `59d5c5b`)
- Commits: `7364577` (core), `a05f1ff` (mc service), `c3de23b` (game tests and docs), plus this report
- Java version used: Java 25 through the Gradle toolchain (`Eclipse Temurin JDK 25 (25.0.4.1+1-LTS)`, provisioned by the Foojay resolver); the Gradle launcher itself runs on Temurin 21
- Build and test commands run:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain` (run twice, see section 3)

## 1. Summary

The engine gives every wafer of a lot a per-step roll of `u ^ w` and multiplies the rolls
into the wafer's hidden cumulative roll, which the existing `Wafer` record already carried. `YieldMath`
owns the pass rule (cumulative above `1 - yield`), the core count penalty and the four-step resolution of
a tier's effective yield out of the tier data and the settings. `YieldCalibration` turns a wanted pass
rate into the step weight: `-ln(1 - Y) / q`, where `q` is the `Y`-quantile of `Gamma(K, 1)` found by
bisection on a regularized incomplete gamma function written for the purpose, so the weight is pure
arithmetic, cached and reproducible bit for bit. `YieldRollEngine` applies one layer to a lot through an
injected `RollSource` and judges a finished lot; `FabRandomService` supplies the rolls in the game, keyed by
lot id, wafer index and step index, so nothing new touches the network. Every tier's default yield is hit
within 0.16 percentage points over 100,000 seeded wafers, the T6/T7/T8 core count cases land within 0.15
points of 17%, 44%, 44% and 51%, and 4000 wafers judged through the Minecraft-facing service pass at
90.1% at T3.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/YieldMath.java | new | `passThreshold`, `passes`, `effectiveYield`, `penaltyExponent`, `clampYield`, `resolveEffectiveYield`; holds the 1%..99% clamp range |
| src/main/java/com/euvcraft/lithmod/core/YieldCalibration.java | new | `calibrateBaseWeight`, the cached `baseWeight`, `stepWeight`, `stepRoll`, `clearCache`, `cacheSize`, `MIN_DEFECT_FACTOR` |
| src/main/java/com/euvcraft/lithmod/core/RegularizedGamma.java | new | package-private `lowerRegularized`, `quantile`, `logGamma` (Lanczos) behind the calibration |
| src/main/java/com/euvcraft/lithmod/core/RollSource.java | new | `Rng rngFor(int waferIndex, int stepIndex)`, the one seam between the engine and Minecraft |
| src/main/java/com/euvcraft/lithmod/core/YieldRollEngine.java | new | `applyLayerStep`, `evaluate`, `countPassing`, `requireCompleteLot`, `rollCount`, `stepWeight`, `stepIndex` |
| src/main/java/com/euvcraft/lithmod/mc/FabYieldService.java | new | the Minecraft-facing `applyLayerStep` and `evaluateLot` bound to `FabRandomService.rngFor`, plus the settings-change hook |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | registers `FabYieldService::onSettingsChanged` as a settings change listener and registers the new game tests |
| src/main/java/com/euvcraft/lithmod/mc/gametest/YieldGameTests.java | new | `lithmod:yield_layer_step` and `lithmod:yield_pass_rate` in the `lithmod:yield_default` environment |
| src/test/java/com/euvcraft/lithmod/core/ReferenceTiers.java | new | the eight reference tiers from the design table, shared by the new core tests |
| src/test/java/com/euvcraft/lithmod/core/YieldMathTest.java | new | threshold, core count penalty against the reference table, clamping, `resolveEffectiveYield` |
| src/test/java/com/euvcraft/lithmod/core/YieldCalibrationTest.java | new | gamma self-checks, the calibration accuracy tables, defect factor, mean cumulative roll, cache behaviour |
| src/test/java/com/euvcraft/lithmod/core/YieldRollEngineTest.java | new | layer step determinism, step index formula, rejections, judging |
| src/test/java/com/euvcraft/lithmod/mc/FabYieldServiceTest.java | new | 4000 wafers at T3, the T6 core count cases, the guards and the cache clear |
| docs/testing.md | modified | the two new game tests and what they prove |

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 28s
```

```
.\gradlew.bat runGameTestServer --offline --console=plain
[09:12:23.209] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: repeating the last layer: refused with "layer 6 is not the next layer of a lot that is at layer 6"
[09:12:23.209] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: skipping to a later layer: refused with "layer 8 is not the next layer of a lot that is at layer 6"
[09:12:23.210] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 13 of 25 wafers of one lot passed against a target of 60%
[09:12:23.241] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 597 of 1000 wafers passed, 59.70% against a target of 60%
[09:12:23.251] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T3: 914 of 1000 wafers passed, 91.40% against a target of 90%
[09:12:26] [Server thread/INFO] [minecraft/GameTestServer]: ========= 682 GAME TESTS COMPLETE IN 1.255 min ======================
[09:12:26] [Server thread/INFO] [minecraft/GameTestServer]: All 682 required tests passed :)
[09:12:26] [Server thread/INFO] [minecraft/GameTestServer]: 1 optional tests failed
BUILD SUCCESSFUL in 3m 5s
```

The optional failure is `arcforge:creosote_moves` ("Armor stand did not drift through creosote ...
on tick 602"), an Arcforge test in the `arcforge:solar_controller_rebuild` environment. It is marked
optional, so the run still succeeds, and it was also failing in the earlier run of this branch before
any of the log lines above were fixed. See section 5 for what was not checked.

Game tests, from `run/logs/latest.log`:

| test | environment | result |
|---|---|---|
| lithmod:yield_layer_step | lithmod:yield_default | passed |
| lithmod:yield_pass_rate | lithmod:yield_default | passed |

Unit test table, aggregated by top-level class from `build/test-results/test/*.xml`:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| core.YieldMathTest (+ 4 nested) | 26 | 0 | 0 | 0 | 26 |
| core.YieldCalibrationTest (+ 4 nested) | 17 | 0 | 0 | 0 | 17 |
| core.YieldRollEngineTest (+ 3 nested) | 16 | 0 | 0 | 0 | 16 |
| mc.FabYieldServiceTest | 8 | 0 | 0 | 0 | 8 |
| core.FabSettingsTest (+ nested) | 43 | 0 | 0 | 0 | 43 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| core.FoupContentsTest (+ nested) | 30 | 0 | 0 | 0 | 30 |
| core.ContextualRngTest | 14 | 0 | 0 | 0 | 14 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| core.DieRulesTest (+ nested) | 8 | 0 | 0 | 0 | 8 |
| core.CoreTest | 2 | 0 | 0 | 0 | 2 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 | 13 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 | 8 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 | 3 |
| mc.data.* (7 classes) | 9 | 0 | 0 | 0 | 9 |
| Total | 249 | 0 | 0 | 0 | 249 |

Calibration table, printed by `YieldCalibrationTest.everyTierHitsItsYield` from 100,000 seeded wafers per
tier at `defectFactor` 1.0 and `rollsPerLayer` 1, so `K = layers * 1`:

| tier | K | target | baseWeight | simulated | deviation (pp) |
|---|---:|---:|---:|---:|---:|
| T1 | 6 | 60.0% | 0.145630 | 59.8630% | -0.1370 |
| T2 | 12 | 80.0% | 0.108918 | 80.0490% | +0.0490 |
| T3 | 24 | 90.0% | 0.075610 | 90.0160% | +0.0160 |
| T4 | 32 | 92.0% | 0.062770 | 92.1550% | +0.1550 |
| T5 | 42 | 93.0% | 0.051213 | 93.1270% | +0.1270 |
| T6 | 70 | 85.0% | 0.024113 | 85.1110% | +0.1110 |
| T7 | 80 | 85.0% | 0.021250 | 84.9340% | -0.0660 |
| T8 | 90 | 80.0% | 0.016444 | 80.0980% | +0.0980 |

Core count table, printed by `YieldCalibrationTest.coreCountsLandOnItsReferenceYields`, same simulation
per row:

| tier | cores | target | baseWeight | simulated | deviation (pp) |
|---|---:|---:|---:|---:|---:|
| T6 | 1 / 2 / 4 | 85.0% | 0.024113 | 85.1110% | +0.1110 |
| T6 | 8 | 61.4% | 0.013204 | 61.3830% | -0.0295 |
| T6 | 12 | 44.4% | 0.008562 | 44.4840% | +0.1135 |
| T6 | 24 | 16.7% | 0.002957 | 16.7670% | +0.0327 |
| T7 | 1 / 2 / 4 / 8 | 85.0% | 0.021250 | 84.9340% | -0.0660 |
| T7 | 12 | 72.2% | 0.015073 | 72.2880% | +0.0380 |
| T7 | 24 | 44.4% | 0.007479 | 44.4570% | +0.0865 |
| T8 | 1 / 2 / 4 / 8 / 12 | 80.0% | 0.016444 | 80.0980% | +0.0980 |
| T8 | 24 | 51.2% | 0.007976 | 51.1590% | -0.0410 |

Defect factor and mean cumulative roll, printed by the two other simulations:

```
tier T5, K=42, yield 93.0%: defect 1.0 -> 93.1270%, 0.9 -> 77.4710%, 0.7 -> 19.4740%, 0.0 (floored at 0.05) -> 0.0000%
weight fixed at 0.07561035511160183: K=1 mean=0.92976 (theory 0.92970) K=2 mean=0.86443 (theory 0.86435) K=5 mean=0.69444 (theory 0.69458) K=12 mean=0.41737 (theory 0.41700) K=24 mean=0.17418 (theory 0.17389)
```

And through the Minecraft-facing service (`FabYieldServiceTest`), 4000 wafers per row:

```
T3, 4000 wafers, K=24: 3604 of 4000 passed, 90.1000%
T6, 4000 wafers each: 4 cores 85.3250%, 8 cores 60.2500%, 24 cores 16.9000%
```

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| `YieldMath.passThreshold(yield) = 1 - yield` | met | `YieldMath.passThreshold`; `passThreshold(0.90) == 0.10` in `pass or fail > a 90% yield needs a cumulative roll above 0.10` |
| `passes(cumulative, yield) = cumulative > passThreshold` | met | same test; the threshold itself fails, the comparison is strict |
| `effectiveYield(tierYield, cores, comfortableCores, k) = tierYield ^ (1 + k * max(0, cores / comfortable - 1))` | met | `core count penalty > the exact exponents are 1 + k * max(...)`, `YieldMath.penaltyExponent` |
| "when comfortableCores is null or not positive, return tierYield unchanged" | met | `a tier without comfortable cores keeps its yield at any core count` (null, 0, -4) |
| step roll model `r = u ^ w`, `w = baseWeight / max(defectFactor, 0.05)`, cumulative is the product | met | `YieldCalibration.stepRoll`, `YieldRollEngine.applyLayerStep`; `a roll is exactly u to the power of the weight`; `more rolls at the same weight lower the mean cumulative roll` |
| `-ln(cumulative)` is `w` times a `Gamma(K, 1)` variable | met | the derivation is in the `YieldCalibration` class javadoc; `the quantile inverts P at every tier's roll count` checks the gamma side directly |
| `calibrateBaseWeight(K, Y)` finds the weight so `P(cumulative > 1 - Y) = Y` at `defectFactor` 1.0 | met | `calibrateBaseWeight`; every row of the calibration table |
| bisection on the regularized lower incomplete gamma, no Monte Carlo | met | `RegularizedGamma.quantile`; the quantile self-checks: `P(1,x)`, `P(2,x)`, `the quantile of shape 1 is -ln(1 - p)`, `the quantile inverts P at every tier's roll count` |
| calibration deterministic and cached | met | `the weight is deterministic down to the last bit`, `a cached weight equals the computed one`; `ConcurrentHashMap` in `YieldCalibration` |
| accuracy: every tier's default yield within 1.5 pp over at least 100,000 seeded wafers | met | the calibration table, worst deviation 0.155 pp at 100,000 wafers per tier |
| `resolveEffectiveYield(tier, settings, cores)`: override as given, not multiplied | met | `an override is used as given`, `the core penalty applies after the override and the multiplier` |
| otherwise `tier.defaultYieldPercent * yieldMultiplier` | met | `the multiplier scales the tier's own yield` |
| clamp the base to 1%..99% | met | `the base is clamped at both ends` (upper through the multiplier, lower through a 5% tier) |
| comfortable count from `comfortableCoreOverrides` else the tier's value | met | `a comfortable core override replaces the tier's own value` |
| steepness from settings | met | `the steepness comes from the settings` |
| `applyLayerStep(serverLevel, lot, tier, layerNumber, defectFactor)` | met | `FabYieldService.applyLayerStep`; `lithmod:yield_layer_step` |
| `settings.rollsPerLayer` rolls per wafer, step index `layerNumber * rollsPerLayer + j` | met | `each roll is drawn at the step index of its layer and position`, `more rolls per layer draw more rolls` |
| rolls from `FabRandomService.rngFor(level, lotId, waferIndex, stepIndex)` | met | `FabYieldService.applyLayerStep` lambda; `lithmod:yield_layer_step` on a real level |
| multiply the cumulative roll, set `layerIndex` to the layer number, return a new `FoupContents` | met | `every wafer ends the layer at the layer number and below its old roll` (also checks the input lot is untouched) |
| reject a layer number that is not the lot's layer plus one | met | `a layer that is not the next one is refused`; the game test logs both refusals |
| reject layers beyond `tier.layers` | met | `past the last layer is refused`, message contains "past" |
| `evaluateLot(lot, tier, cores)` returns a pass or fail per wafer and does not change the lot | met | `one answer per wafer, and it is the roll against the resolved yield`, `judging a lot does not change it` |
| cache calibration per (tier, rollCount, yield) and recalibrate when settings change | met | `YieldCalibration.baseWeight` keyed by `(rollCount, yield)`; `a settings change drops the cached calibration`; listener wired in `EUVCRAFTMod` |
| rolls and cumulative values never sent to clients | met | nothing new leaves the server: the service returns a server-side `FoupContents`, the M0-08 `STREAM_CODEC` replaces rolls with `CLIENT_VIEW_ROLL`, and `judgingDoesNotChangeTheLot` checks `publicView()` carries only capacity, count and layer |
| test: effectiveYield against the reference table | met | `T6 past 4 cores gives 61%, 44% and 17%`, `T7 ...`, `T8 ...`, `counts at or below comfortable give the tier yield` |
| test: `passThreshold` example | met | `pass or fail` |
| test: calibration accuracy for all eight tiers, with the table | met | the calibration table above |
| test: a lower defect factor lowers the pass rate | met | `a lower defect factor lowers the pass rate` (93.13% / 77.47% / 19.47%) |
| test: more rolls lower the mean cumulative | met | `more rolls at the same weight lower the mean cumulative roll`, checked against `(1 / (1 + w))^K` |
| test: `resolveEffectiveYield` with an override, with the multiplier, and clamping at both ends | met | `resolveEffectiveYield` (10 tests) |
| test: `applyLayerStep` deterministic for the same salt and lot | met | `the same salt and lot give the same rolls every time`, `a different salt gives a different set of rolls` |
| test: an out-of-order layer is rejected | met | `a layer that is not the next one is refused` |
| test: `evaluateLot` over 4000 wafers at T3 within tolerance of 90% | met | `four thousand wafers at T3 pass within a point and a half of 90%`, 3604 of 4000 = 90.10% |
| done when: build and tests pass | met | `BUILD SUCCESSFUL in 28s` for the unit tests, `All 682 required tests passed :)` for the game tests |
| done when: report has the calibration table and the `resolveEffectiveYield` rules | met | sections 3 and 8 |
| out of scope: test station, scrap items, chip items, mask-making core count picker | met | none of these exist in the diff |

## 5. Not verified

- The one optional game test failure, `arcforge:creosote_moves`, was not investigated. It belongs to
  Arcforge, not to this mod, it is marked optional and the run reports success. Not run: a game test run
  on `master` without this branch to establish whether it already failed there.
- No client was run. Nothing in this task adds a screen, so the only client-visible claim is that the
  roll never leaves the server, which rests on the M0-08 codec rather than on anything observed here.
  Not run.
- Cross-session reproducibility of a roll was not tested: a world save and reload, then redoing a step,
  would show the same numbers. The reasoning is in section 7 item 1; the test above only covers two runs
  inside one JVM. Not run.
- The defect factor is only exercised through unit tests and at the value 1.0 in the game test. A real
  line with a defect factor below 1 was not simulated on a server. Not run.
- `rollsPerLayer` above 1 is covered by unit tests only, not by a game test. Not run.
- The engine was not run against T6 to T8 on a server; the game test walks T1 and T3 only, because those
  two are the cheapest and the calibration evidence for the other six is in the unit tests. Not run.
- No load test: a lot of 64 wafers through 90 layers in a single tick was not timed. Not run.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| `applyLayerStep(serverLevel, lot, tier, layerNumber, defectFactor)` | that exact overload exists and delegates to a second one, `applyLayerStep(serverLevel, lot, tier, cores, layerNumber, defectFactor)` | the listed signature cannot carry a core count, but the rolls have to be calibrated against the *effective* yield of that core count. Calibrating at the tier yield instead puts the penalty out of reach: T6 at 24 cores asks for a cumulative roll above 0.83, which is far out in the left tail of the distribution a tier-calibrated T6 lot has after 70 layers, so essentially no wafer would ever pass. The 5-argument call means "no core count", which is the right answer for every die that is not a compute die. |
| cache calibration per (tier, rollCount, yield) | the cache key is `(rollCount, yield)` | a tier reaches the weight only through its layer count, which fixes the roll count, and through its resolved yield. Keying on the pair is the same partition, and it also covers a lot whose settings changed between two layers. |
| `evaluateLot(lot, tier, cores)` | refuses a lot whose wafers are not all at `tier.layers()`, with `IllegalStateException` | a lot that is short of its last layer has almost nothing multiplied into its cumulative roll, so it would pass any threshold. Refusing is cheaper than trusting the caller to check, and the rule lives in the core engine so it is unit tested. |
| "reject a layer number that is not the lot's current layer plus one" | also refuses an empty lot | an empty lot has no wafers to roll, and accepting the call would consume a machine cycle while the lot layer never moved, so the same layer number would stay valid for ever. |
| "Rolls and cumulative values are never sent to clients" | kept, and `applyLayerStep` takes a `ServerLevel` and checks it | `evaluateLot` takes no level, so it cannot check one; it is still server-only in practice because only the server holds the settings and a lot with real rolls. Documented on the class rather than enforced. |
| gamma quantile by bisection on the regularized lower incomplete gamma | done, with `logGamma` by the 15-term Lanczos form | the first Lanczos coefficient set tried was wrong and showed up immediately as `P(2, x)` off by exactly a factor of two. The coefficients and the series seed are now pinned by `P(1, x) = 1 - e^-x` and `P(2, x) = 1 - e^-x (1 + x)`. |
| nothing numeric hard-coded | the core only uses tier data and settings; the numbers in `RegularizedGamma` are algorithm constants, not gameplay | the bisection tolerance and the iteration caps are numerical hygiene, not tuning. |
| no new dependencies | none added | the gamma functions are ~120 lines and a dependency would have been more code than the feature. |

## 7. Open questions

1. Cross-session roll reproducibility rests on `Math.pow` being bit-identical for the same inputs on the
   same JVM build, which is true for the fdlibm implementation HotSpot ships, and on the cumulative roll
   being narrowed to a `float` at every layer, which absorbs a one-ulp difference in all but a boundary
   case. A player cannot reach a boundary case on purpose, but a re-run across a JVM update could flip a
   wafer whose roll sits exactly on `1 - effectiveYield`. Worth deciding whether a future format change
   should widen `Wafer.cumulativeRoll`, and it is not decided here.
2. The step weight depends on `defectFactor` at every layer, but a lot's rolls were already multiplied in
   when the line later degrades. Re-running the machine does nothing, because the layer number no longer
   matches. That is probably the intended behaviour (a lot's fate is decided by the line's state as it
   went through), but nothing in the spec says so and it should be confirmed before machines exist.
3. `FabSettings.rollsPerLayer` changes the roll count and therefore the calibration, so raising it makes
   existing lots more expensive to finish without touching them. Whether a config change should be
   refused while lots are in flight, or accepted as it is now, is undecided.
4. The lower clamp at 1% is unreachable from the shipped configuration: the smallest multiplier is 0.1 and
   the smallest tier yield is 60%, so the product is never below 6%. It only matters for a tier data file
   with a very low `default_yield_percent`. The test that reaches it uses a synthetic tier; if a low-yield
   tier is ever shipped, that test should be pointed at it.
5. A lot processed at one tier and judged at another is not refused, and the two tiers do not agree:
   the rolls carry the layer count of the tier that processed the lot, so judging them against another
   tier's yield gives a number nobody calibrated. Whether the test station should refuse a tier mismatch
   is a decision for whoever builds it. Not run: the numbers for a concrete mismatch.

## 8. Handoff

Core, public API:

- `com.euvcraft.lithmod.core.YieldMath`
  - `static double passThreshold(double yield)`
  - `static boolean passes(double cumulative, double yield)`
  - `static double effectiveYield(double tierYield, Integer cores, Integer comfortableCores, double steepness)`
  - `static double penaltyExponent(Integer cores, int comfortableCores, double steepness)`
  - `static double clampYield(double yield)`
  - `static double resolveEffectiveYield(Tier tier, FabSettings settings, Integer cores)`
  - `MIN_EFFECTIVE_YIELD = 0.01`, `MAX_EFFECTIVE_YIELD = 0.99`
- `com.euvcraft.lithmod.core.YieldCalibration`
  - `static double calibrateBaseWeight(int rollCount, double yield)` (uncached)
  - `static double baseWeight(int rollCount, double yield)` (cached)
  - `static double stepWeight(int rollCount, double yield, double defectFactor)`
  - `static float stepRoll(Rng rng, double weight)`
  - `static void clearCache()`, `static int cacheSize()`
  - `MIN_DEFECT_FACTOR = 0.05`
- `com.euvcraft.lithmod.core.YieldRollEngine`
  - `static FoupContents applyLayerStep(FoupContents lot, Tier tier, Integer cores, int layerNumber, double defectFactor, FabSettings settings, RollSource rolls)`
  - `static boolean[] evaluate(FoupContents lot, double effectiveYield)`
  - `static int countPassing(boolean[] outcomes)`
  - `static void requireCompleteLot(FoupContents lot, Tier tier)`
  - `static int rollCount(Tier tier, FabSettings settings)`
  - `static double stepWeight(Tier tier, FabSettings settings, Integer cores, double defectFactor)`
  - `static int stepIndex(int layerNumber, int rollsPerLayer, int roll)`
- `com.euvcraft.lithmod.core.RollSource` — `Rng rngFor(int waferIndex, int stepIndex)`
- `com.euvcraft.lithmod.core.RegularizedGamma` is package private: `lowerRegularized`, `quantile`,
  `logGamma`. Not part of the API.

Minecraft-facing:

- `com.euvcraft.lithmod.mc.FabYieldService`
  - `static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, int layerNumber, double defectFactor)`
  - `static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, Integer cores, int layerNumber, double defectFactor)`
  - `static boolean[] evaluateLot(FoupContents lot, Tier tier, Integer cores)`
  - `static int countPassing(FoupContents lot, Tier tier, Integer cores)`
  - `static void onSettingsChanged()` (package private, registered as the settings change listener in `EUVCRAFTMod`)

Rules a caller has to know:

- A layer step is refused with `IllegalArgumentException` when the lot is empty, when `layerNumber` is not
  `lot.lotLayer() + 1`, or when `layerNumber` is past `tier.layers()`.
- `evaluateLot` is refused with `IllegalStateException` unless every wafer is at `tier.layers()`.
- Rolls are drawn per wafer at step index `layerNumber * settings.rollsPerLayer() + roll`, so a redo of a
  layer on the same lot gives the same numbers.
- The rolls of a layer are calibrated against the effective yield at that moment, including the core count
  penalty, so the pass rate a lot shows at test is the one the core count bought.

Config keys used (all from M0-02, all already registered):
`general.rollsPerLayer`, `general.yieldMultiplier`, `general.tierYieldOverrides`,
`compute.coreCountOptions`, `compute.comfortableCoreOverrides`, `compute.coreCountPenaltySteepness`.

Data files: none added. The engine reads the tier data that already exists at
`data/lithmod/tiers/t1.json` .. `t8.json` through `TierDataLoader.getRegistry()`, and nothing in it is
hard-coded.