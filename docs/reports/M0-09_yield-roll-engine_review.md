# M0-09: Yield roll engine (task review)

- Date: 2026-10-14
- Branch reviewed: `m0-09-yield-roll-engine` (working tree clean at review time)
- Commits reviewed: `7364577` (core), `a05f1ff` (mc service), `c3de23b` (game tests and
  docs), `a5b7082` (report); base `59d5c5b`
- Java on `PATH` for this review: Temurin 21.0.12.1; `build.gradle:41` pins the
  Gradle toolchain to Java 25
- Build and test commands run for this review:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`
  - `.\gradlew.bat build --offline --console=plain`
  - `.\gradlew.bat checkCoreImports --offline --console=plain --rerun-tasks`
- Independent numerical check: the reported `baseWeight` values re-simulated in
  PowerShell with `System.Random` (a different generator from the repo's
  `SplitMix64`), 200,000 wafers per row

## 1. Summary

Verdict: **accept with fixes**. Every bullet of the task prompt is implemented, the
calibration is real and I confirmed it independently of the repo's own test code, and
the report's tables reproduce exactly from my runs. `.\gradlew.bat build` passes, 249
unit tests pass with no failures, and all 682 required game tests pass including the
two new yield tests. The two majors are both about the same missing concept: nothing
records the parameters a lot was processed under. The core count that calibrates the
layer rolls and the core count that judges the lot are two independent arguments, and
the effective yield is re-resolved from live config at test time, so a mismatch is
silent and severe in both directions. Neither blocks the next task from starting, but
both should be settled before the test station exists. Everything else is minor. No
blockers.

## 2. Files changed

None. This is a review; no source file was modified.

The only file added by this review is this document,
`docs/reports/M0-09_yield-roll-engine_review.md`. It follows the M0-08 review
precedent and is left uncommitted so that it does not land on the branch that carries
the reviewed implementation.

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 32s
7 actionable tasks: 7 executed
```

```
.\gradlew.bat runGameTestServer --offline --console=plain
[09:19:41] [Server thread/INFO] [minecraft/GameTestRunner]: Running test environment 'lithmod:yield_default' batch 0 (2 tests)...
[09:19:42] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: repeating the last layer: refused with "layer 6 is not the next layer of a lot that is at layer 6"
[09:19:42] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: skipping to a later layer: refused with "layer 8 is not the next layer of a lot that is at layer 6"
[09:19:42] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 13 of 25 wafers of one lot passed against a target of 60%
[09:19:42] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 620 of 1000 wafers passed, 62.00% against a target of 60%
[09:19:42] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T3: 891 of 1000 wafers passed, 89.10% against a target of 90%
[09:21:04] [Server thread/INFO] [minecraft/GameTestServer]: ========= 682 GAME TESTS COMPLETE IN 1.402 min ======================
[09:21:04] [Server thread/INFO] [minecraft/GameTestServer]: All 682 required tests passed :)
[09:21:04] [Server thread/INFO] [minecraft/GameTestServer]: 1 optional tests failed
BUILD SUCCESSFUL in 3m 15s
```

The optional failure is `arcforge:creosote_moves` in the `arcforge:solar_controller_rebuild`
environment, exactly as the reviewed report says. The single-lot and thousand-wafer
numbers differ from the report's (59.70% and 91.40%) because the world salt and the
lot ids are random per run; the refusal messages, the structure and the two required
test outcomes are identical.

```
.\gradlew.bat build --offline --console=plain
BUILD SUCCESSFUL in 6s
```

Unit test totals, aggregated by me from `build/test-results/test/*.xml` after my run:

| test class group | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| `core.YieldMathTest` (+ 3 nested) | 26 | 0 | 0 | 0 | 26 |
| `core.YieldCalibrationTest` (+ 3 nested) | 17 | 0 | 0 | 0 | 17 |
| `core.YieldRollEngineTest` (+ 2 nested) | 16 | 0 | 0 | 0 | 16 |
| `mc.FabYieldServiceTest` | 8 | 0 | 0 | 0 | 8 |
| all pre-M0-09 classes | 182 | 0 | 0 | 0 | 182 |
| **Total** | **249** | **0** | **0** | **0** | **249** |

Identical to the table in `docs/reports/M0-09_yield-roll-engine.md`, and the 182
pre-existing tests match the M0-08 review's total.

Scope checks I ran myself:

```
git diff --name-only 59d5c5b..HEAD  -> 15 paths, all src/**/lithmod/** plus docs/
git diff --name-only 59d5c5b..HEAD -- build.gradle gradle.properties settings.gradle libs src/main/resources -> (empty)
Select-String core/*.java '^\s*import\s+(net\.minecraft|net\.neoforged|com\.mojang|io\.netty)' -> (no matches)
Select-String core/Yield*.java, core/RegularizedGamma.java, mc/FabYieldService.java
  '0\.85|0\.61|0\.44|0\.17|0\.72|0\.51|\b85\b|\b61\b|5000|248|193|13\.5' -> one hit, javadoc prose in FabYieldService.java:68
```

Encoded Logistics and Arcforge are untouched, no build file or data file changed, no
new dependency was added, and the core package imports nothing from Minecraft,
NeoForge, Mojang or Netty.

Independent confirmation of the calibration, simulated outside the repository's code
with a different generator (`System.Random`, 200,000 wafers per row):

```
T1  K=6  w=0.145630 -> 60.017%   (target 60%)      T6 8 cores  K=70 w=0.013204 -> 61.614%   (0.85^3  = 61.41%)
T8  K=90 w=0.016444 -> 79.949%   (target 80%)      T6 24 cores K=70 w=0.002957 -> 16.652%   (0.85^11 = 16.73%)
T5  K=42 w=0.051213 -> 92.993%   (target 93%)      T7 12 cores K=80 w=0.015073 -> 72.242%   (0.85^2  = 72.25%)
                                                     T8 24 cores K=90 w=0.007976 -> 51.155%   (0.80^3  = 51.20%)
```

So the reported weights, the reported simulated pass rates and the `effectiveYield`
exponents all agree with an outside generator. The `effectiveYield` expected numbers
in REFERENCE DATA also check by hand: `0.85^3 = 0.6141`, `0.85^5 = 0.4437`,
`0.85^11 = 0.1673`, `0.80^3 = 0.5120`, all inside the 0.005 tolerance the tests use.

Every Minecraft and NeoForge API the new code touches was either already in this
repository or was executed by the two yield game tests: `ServerLevel` and
`requireServerSide` (`mc/FabRandomService.java:37,93`), `rngFor` (M0-07, exercised by
`lithmod:yield_layer_step`), `EUVCRAFTMod.settings()` and
`FabConfig.addSettingsChangeListener` (`mc/FabConfig.java:193`), `TierDataLoader
.getRegistry()` and `TierRegistry.byIndex` (M0-03), `org.jspecify.annotations.Nullable`
(`mc/FoupItems.java:12`), and the whole `RegisterGameTestsEvent` / `TestData` /
`FunctionGameTestInstance` registration block copied from
`mc/gametest/FoupGameTests.java:101-129`. No unverified API was introduced.

## 4. Spec conformance

Findings are numbered and carry a severity. No blocker was found, so no fix is
required before the next task can start.

1. **[major] The core count that calibrates the rolls and the core count that judges
   the lot are unrelated arguments, and a mismatch is silent and total.**
   Files `src/main/java/com/euvcraft/lithmod/mc/FabYieldService.java:57` (the
   5-argument overload the task named, which passes `cores = null`) and `:111`
   (`evaluateLot`). The step weight is resolved from the effective yield at
   `YieldRollEngine.java:60`, and the pass threshold is resolved again at
   `YieldMath.java:144`. Nothing records which core count the lot was processed under.
   A machine that applies layers through the task's own 5-argument signature and then
   judges with `cores = 24` on T6 needs a cumulative roll above 0.833 from a
   distribution calibrated for 0.15, so essentially no wafer passes. The reverse gives
   about 84% instead of 17%. Both are wrong answers with no exception and no log.
   Fix: make one object carry the run's parameters (tier, cores, defect factor, roll
   count, effective yield) from the first layer to test, and have both
   `applyLayerStep` and `evaluateLot` take it; at the very least, document in the
   javadoc of both overloads and in the handoff that the two calls must pass the same
   `cores`, and add a test that a mismatched pair is either refused or produces the
   documented degraded answer.

2. **[major] The yield a lot is judged at is re-resolved from live config at test
   time, so a config change mid-run silently decalibrates lots in flight.**
   Files `mc/FabYieldService.java:116` and `core/YieldRollEngine.java:60`. The layer
   rolls are calibrated against the effective yield resolved at the moment of the layer
   step, while the test resolves it again from `EUVCRAFTMod.settings()`. `general.
   yieldMultiplier` and `general.tierYieldOverrides` are reloadable at runtime
   (`EUVCRAFTMod.java:81` wires `ModConfigEvent.Reloading`), and
   `FabYieldService.onSettingsChanged` only clears the cache. Raising
   `yieldMultiplier` to 2.0 mid-fab makes every in-flight lot pass at 99% although it
   was processed for its tier yield. The reviewed report's open question 3 notices this
   for `rollsPerLayer` only.
   Fix: freeze the resolved effective yield (and the roll count) into the run context
   of finding 1, or have the test station pass the yield the lot was processed at
   rather than resolving it again. If the answer is "config changes apply to lots in
   flight", say so in the report and accept the wrong numbers knowingly.

3. **[minor] `evaluateLot` cannot be checked for server-side execution, and on a
   client a placeholder lot judges as a perfect lot.**
   File `mc/FabYieldService.java:111`. The class javadoc says this is deliberate, and
   the spec's signature has no level either, but `evaluate` is a pure read of the hidden
   rolls, and a client-view lot from the M0-08 network codec holds
   `CLIENT_VIEW_ROLL = 1.0f` on every wafer, which passes every threshold. A future
   screen that calls this gets a 100% yield for every lot. The item boundary is safe
   today because `FoupItems.trustedContentsOf` (`mc/FoupItems.java:112`) substitutes a
   fresh empty lot for a client view, but that gate is invisible from here and the
   reviewed report's handoff never points the next task at it.
   Fix: add a `ServerLevel` overload of `evaluateLot` that calls
   `FabRandomService.requireServerSide`, and have the test station use it; add
   `FoupContentsCodec.isClientView(lot)` rejection to `applyLayerStep` and
   `evaluateLot` while the caller is still in this module; name `trustedContentsOf` in
   the handoff as the only way to read a lot out of an `ItemStack`.

4. **[minor] `defectFactor` is used without any bound check, and a value above one
   makes a line better than the calibration.**
   Files `core/YieldCalibration.java:104` and `core/YieldRollEngine.java:59`. Only the
   lower bound is applied (`Math.max(defectFactor, 0.05)`), so `defectFactor = 2.0`
   halves the weight and pushes the pass rate above the tier yield with no error.
   `LineStats` (`core/LineStats.java:16`) constrains the stat model's value to
   `[0, 1]`, so only a future caller can do this, but the engine is the last place that
   could refuse.
   Fix: reject `defectFactor > 1.0` in `stepWeight` with an
   `IllegalArgumentException`, next to the existing floor.

5. **[minor] Two public methods have no caller outside their own class.**
   `core/YieldRollEngine.java:59` `stepWeight(Tier, FabSettings, Integer, double)` and
   `core/YieldMath.java:98` `penaltyExponent(Integer, int, double)` are public and are
   called only from inside their own class and from tests. `YieldCalibration.cacheSize`
   (`core/YieldCalibration.java:140`) is called only by tests. This is the same
   speculative-surface pattern the M0-08 review flagged.
   Fix: make the first two package-private (or private) until a caller needs them;
   keep `cacheSize` only if a debug command will use it, and note the decision.

6. **[minor] A core-package javadoc link points at an mc class.**
   `core/RollSource.java:9` links `com.euvcraft.lithmod.mc.FabRandomService#rngFor`.
   It is the only `lithmod.mc` reference in the core package, it creates a package
   cycle in the source graph, and `checkCoreImports` does not see it, so the "core has
   no Minecraft dependencies" claim is enforced at the import level only.
   Fix: drop the link and describe the Minecraft side in words, or extend
   `core/ImportChecker.java` to also reject `com.euvcraft.lithmod.mc` references in
   core sources.

7. **[minor] Two assertions compare a pure function with itself.**
   `src/test/java/com/euvcraft/lithmod/core/YieldRollEngineTest.java:294` asserts
   `evaluate(finished, 0.90)` equals `evaluate(finished, 0.90)`, and
   `src/test/java/com/euvcraft/lithmod/mc/FabYieldServiceTest.java:155` does the same
   through the service. `evaluate` reads a value and returns a boolean with no state
   behind it, so both assertions hold for any implementation, including a wrong one.
   The meaningful version of the first one is already in
   `YieldRollEngineTest.java:310`, which recomputes each answer independently.
   Fix: drop the self-comparisons, or keep one and make it compare against a
   hand-written expectation.

8. **[minor] Two tests assert on a process-global static cache, and a third pins the
   cross-tier hand-over that the report leaves unresolved.**
   `core/YieldCalibrationTest.java:185` (`cachedWeightMatches`, expects exactly 1 then
   2 entries) and `mc/FabYieldServiceTest.java:140`
   (`settingsChangeClearsTheCalibration`, expects 0) both clear the cache first, so
   they pass with the current sequential runner and both break silently if JUnit
   parallel execution is ever enabled. `core/YieldRollEngineTest.java:240`
   (`tiersHaveDifferentLengths`) runs a T1 lot to layer 6 and then applies T3's layer
   7, asserting success; that is exactly the case the reviewed report's open question
   5 says is not calibrated and should perhaps be refused.
   Fix: leave the cache assertions as they are, and rename the hand-over test so it
   reads as a record of current behaviour rather than an endorsement.

9. **[minor] The smallest legal roll count has no direct test, and one conformance row
   cites the wrong test for its claim.**
   `core/YieldCalibrationTest.java:204` rejects `rollCount` 0 and -3 but never pins
   `rollCount = 1`, the one value with a closed form: `calibrateBaseWeight(1, y)` must
   be exactly 1.0, because the gamma quantile and the threshold are the same
   `-ln(1 - y)`. The K=1 case appears only inside a mean-cumulative sweep at a weight
   calibrated for K=24. Separately,
   `docs/reports/M0-09_yield-roll-engine.md:170` attributes the `publicView()` check to
   `judgingDoesNotChangeTheLot`; the core test of that name
   (`YieldRollEngineTest.java:317`) does not check `publicView`, the mc one
   (`FabYieldServiceTest.java:106`) does. The claim itself is true.
   Fix: add `assertEquals(1.0, YieldCalibration.calibrateBaseWeight(1, 0.90), 1e-12)`,
   and name `mc.FabYieldServiceTest.judgingDoesNotChangeTheLot` in that row.

Checklist results for the rules that are not per-bullet:

| rule | result | evidence |
|---|---|---|
| core package free of Minecraft and NeoForge | holds | `checkCoreImports` passed; independent grep for `net.minecraft`, `net.neoforged`, `com.mojang`, `io.netty` in `core/*.java` found nothing; one javadoc reference, finding 6 |
| no gameplay numbers hard-coded | holds | no reference value outside tests, tier JSON and config defaults; the only literals in the new production code are `MIN_DEFECT_FACTOR` 0.05, the 1%/99% clamp, and the Lanczos and bisection constants, which the spec or the algorithm fix |
| server-authoritative | holds with a caveat | `applyLayerStep` calls `FabRandomService.requireServerSide`, rolls come only from `FabRandomService.rngFor`, nothing in the task logs or transmits a roll; caveat is finding 3 |
| Encoded Logistics and Arcforge untouched | holds | the branch diff lists only `lithmod` source paths, and `libs/` is unchanged |
| no unreported new dependencies | holds | `build.gradle`, `gradle.properties`, `settings.gradle` and `libs` are absent from the branch diff |
| no alias classes, duplicate accessors or unused speculative code | fails in part | findings 5 and 7; no alias class and no duplicate accessor beyond the convenient `FabYieldService.countPassing` |
| tests meaningful, covering boundaries | holds with the gaps above | covered: strict threshold, the threshold value itself, both clamp ends, empty lot, out-of-order and past-the-end layers, defect factor floor and negative factor, roll at 1.0 and the float floor, every tier and every offered core count, 100,000-wafer simulations; gaps are findings 7, 8, 9 |
| test expected numbers match REFERENCE DATA | holds | 61% / 44% / 17% at T6, 72% / 44% at T7, 51% at T8, tier yield at or below comfortable and 90% at T3 all verified by hand and by outside simulation |
| data that could be lost or duplicated | no new risk found | `applyLayerStep` and `evaluate` are pure and return new instances; the input lot is asserted unchanged in three places |
| hidden yield values that could reach a client | does not hold open | this task adds no packet and no new codec; the M0-08 network form is still three varints (`mc/FoupContentsCodec.java:98`), and the placeholder it builds is the case in finding 3 |

## 5. Not verified

- A dev client was not run; no display is available to this session, and nothing in
  this task adds a screen. Not run.
- Cross-session reproducibility (save, reload, redo a layer) was not exercised. The
  reviewed report lists it as not run and the reasoning is in its open question 1; the
  determinism evidence I have is two runs inside one JVM. Not run.
- Whether `arcforge:creosote_moves` also fails on `master` without this branch was not
  established, so the optional failure cannot be attributed to the branch with
  certainty. Not run.
- T6 to T8 and any `defectFactor` below 1.0 were not run on a real server; the game
  test walks T1 and T3 at `defectFactor` 1.0 only. A 64-wafer lot through 90 layers in
  one tick was not timed. Not run.
- The `RegularizedGamma` series and continued fraction were not compared against an
  external gamma library; they are pinned by the `P(1, x)`, `P(2, x)` and
  quantile-inversion tests, and end-to-end by the 100,000-wafer simulations that I
  reproduced outside the repository. Not run.

## 6. Deviations

Deviations the reviewed report declared, and whether each reason holds up.

| spec says | what was done | reason holds up |
|---|---|---|
| `applyLayerStep(serverLevel, lot, tier, layerNumber, defectFactor)` | that overload exists and delegates to `applyLayerStep(level, lot, tier, cores, layerNumber, defectFactor)` | yes. The listed signature cannot carry a core count, and the report's argument is quantitatively right: a T6 lot calibrated for 85% and judged at 17% needs a roll above 0.833 from a distribution whose threshold is 0.15, so it is a left tail measured in standard deviations, not a small shift. The overload that puts the penalty in the rolls is the only reading under which the accuracy target holds. See finding 1 for the cost of the extra argument |
| cache calibration per `(tier, rollCount, yield)` | the key is `(rollCount, yield)` | yes. A tier reaches the weight only through its layer count, which fixes the roll count, and through its resolved yield. A changed yield misses the cache by itself, which also makes the `clearCache` listener a memory bound rather than a correctness mechanism |
| `evaluateLot(lot, tier, cores)` does not change the lot | it also refuses a lot whose wafers are not all at `tier.layers()` | yes, and it is the safer rule: a half-processed lot has almost nothing multiplied into its rolls and would pass any threshold. The exception type is `IllegalStateException` and it is unit tested |
| reject a layer number that is not the lot's layer plus one | it also refuses an empty lot | yes, and the reason given is real: an empty lot has no wafers, so the lot layer never moves and the same layer number would stay valid for ever |
| rolls never sent to clients | kept; `applyLayerStep` takes a `ServerLevel` and checks it | yes for this task, with the caveat in finding 3 that `evaluateLot` cannot check |
| gamma quantile by bisection on the regularized lower incomplete gamma | done in-repo with a 15-term Lanczos `logGamma` | yes. The closed forms `P(1, x) = 1 - e^-x` and `P(2, x) = 1 - e^-x(1 + x)` are pinned by tests at 1e-12, and the quantile is pinned by inverting `P` at every tier's roll count |
| nothing numeric hard-coded | the core uses only tier data and settings; `RegularizedGamma` holds algorithm constants | yes |
| no new dependencies | none added | yes; a gamma library would have been more code than the 200 lines written here |

Deviations I raised that the report did not declare: findings 1, 2, 3, 4, 5, 6.

## 7. Open questions

1. Does a lot carry the parameters it was processed under, or does the machine that
   owns it? Findings 1 and 2 are the same question. The answer decides whether a
   config change or a core-count mistake can silently change a lot's yield.
2. Should a lot processed at one tier be judged at another be refused? The reviewed
   report raises this as its own open question 5 and
   `YieldRollEngineTest.tiersHaveDifferentLengths` currently permits the hand-over.
3. Should `general.rollsPerLayer` be allowed to change while lots are in flight? The
   reviewed report raises this as its open question 3; it is a special case of finding
   2, because it changes the roll count and therefore the calibration.
4. Is `defectFactor` per layer or per lot? A line that degrades midway leaves rolls
   calibrated at two different weights in one lot, which is currently allowed and is
   the reviewed report's open question 2.
5. Should `YieldCalibration` be per-world or global? The cache is a process-wide static
   keyed by `(rollCount, yield)`, which is correct for pure arithmetic, but it is
   cleared by a config listener and read by tests, so the two lifetimes are coupled.

## 8. Handoff

Public API reviewed and relied on by later tasks, unchanged by this review:

- `core.YieldMath` — `passThreshold(double)`, `passes(double, double)`,
  `effectiveYield(double, Integer, Integer, double)`,
  `penaltyExponent(Integer, int, double)`, `clampYield(double)`,
  `resolveEffectiveYield(Tier, FabSettings, Integer)`, `MIN_EFFECTIVE_YIELD` (0.01),
  `MAX_EFFECTIVE_YIELD` (0.99).
- `core.YieldCalibration` — `calibrateBaseWeight(int, double)`, `baseWeight(int, double)`,
  `stepWeight(int, double, double)`, `stepRoll(Rng, double)`, `clearCache()`,
  `cacheSize()`, `MIN_DEFECT_FACTOR` (0.05).
- `core.YieldRollEngine` — `rollCount(Tier, FabSettings)`,
  `stepWeight(Tier, FabSettings, Integer, double)`,
  `applyLayerStep(FoupContents, Tier, Integer, int, double, FabSettings, RollSource)`,
  `evaluate(FoupContents, double)`, `countPassing(boolean[])`,
  `requireCompleteLot(FoupContents, Tier)`, `stepIndex(int, int, int)`.
- `core.RollSource` — `Rng rngFor(int waferIndex, int stepIndex)`.
- `mc.FabYieldService` — `applyLayerStep(ServerLevel, FoupContents, Tier, int, double)`,
  `applyLayerStep(ServerLevel, FoupContents, Tier, Integer, int, double)`,
  `evaluateLot(FoupContents, Tier, Integer)`, `countPassing(FoupContents, Tier, Integer)`,
  `onSettingsChanged()` (package private).
- `mc.FabRandomService` — `rngFor(Level, UUID, int, int)`, `requireServerSide(Level)`,
  both from M0-07 and used here unchanged.
- `mc.FoupItems` — `trustedContentsOf(ItemStack)` is the only sanctioned way to get a
  lot out of a stack, see finding 3.

Config keys in play, all registered by M0-02 and unchanged here: `general.rollsPerLayer`,
`general.yieldMultiplier`, `general.tierYieldOverrides`, `compute.coreCountOptions`,
`compute.comfortableCoreOverrides`, `compute.coreCountPenaltySteepness`. Data files:
none added; the engine reads `data/lithmod/tiers/t1.json` .. `t8.json` through
`TierDataLoader.getRegistry()`.

Rules a caller has to know:

- A layer step throws `IllegalArgumentException` on an empty lot, on a layer number that
  is not `lot.lotLayer() + 1`, and on a layer past `tier.layers()`.
- `evaluateLot` throws `IllegalStateException` unless every wafer is at
  `tier.layers()`; `FabYieldService.countPassing` throws the same.
- Rolls are drawn at step index `layerNumber * settings.rollsPerLayer() + j`, so a redo
  of the same layer on the same lot gives the same numbers.
- The rolls of a layer are calibrated against the effective yield at that moment,
  including the core count penalty, so the two calls must be given the same `cores`
  (finding 1), and the effective yield is read from live settings at both the layer
  step and the test (finding 2).
- Tests may compare against the eight reference tiers in
  `src/test/java/com/euvcraft/lithmod/core/ReferenceTiers.java`, which exist so that a
  test reading the data files would not only prove the engine agrees with itself.
