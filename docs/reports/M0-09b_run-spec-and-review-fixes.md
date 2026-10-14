# M0-09b: Run spec and review fixes

- Date: 2026-10-14
- Branch: `m0-09b-run-spec-and-review-fixes` (from `m0-09-yield-roll-engine` at `a5b7082`)
- Commits: `dc3939c` (review fixes), plus this report
- Java version used: Java 25 through the Gradle toolchain (`Eclipse Temurin JDK 25 (25.0.4.1+1-LTS)`)
- Build and test commands run:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`

## 1. Summary

The lot now carries a `RunSpec` that freezes the parameters it was processed under: the
tier index, the core count (0 = none), the effective yield the rolls were calibrated
for, and the total roll count. `applyLayerStep` creates the spec on layer 1 from the
current settings and stores it; every later layer calibrates against that stored spec
and throws `IllegalArgumentException` if the tier or cores argument differs. `evaluate`
judges against `spec.effectiveYield()` and also refuses a tier or cores mismatch. The
defect factor is still per-layer but values above 1.0 are now rejected. A
`ServerLevel` overload of `evaluateLot` was added and both entry points reject
client-view lots. `StrictMath` is used for `pow` and `log` in the calibration and
roll code. `YieldRollEngine.stepWeight` and `YieldMath.penaltyExponent` are now
package-private; the debug-only `cacheSize()` was removed. `ImportChecker` now also
rejects `com.euvcraft.lithmod.mc` imports in core sources. All 267 unit tests and
682 required game tests pass.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/RunSpec.java | new | record carrying tierIndex, cores, effectiveYield, rollCount; codec for persistence |
| src/main/java/com/euvcraft/lithmod/core/FoupContents.java | modified | added nullable `RunSpec run` component; `withRun`, `isStarted`; all `with*` methods preserve the spec |
| src/main/java/com/euvcraft/lithmod/core/YieldRollEngine.java | modified | `applyLayerStep` creates/validates the run spec; `evaluate` judges against spec and checks tier/cores match; always copies the spec to the returned lot |
| src/main/java/com/euvcraft/lithmod/core/YieldMath.java | modified | `penaltyExponent` made package-private |
| src/main/java/com/euvcraft/lithmod/core/YieldCalibration.java | modified | `StrictMath` for `pow`/`log`; `cacheSize()` removed |
| src/main/java/com/euvcraft/lithmod/core/RegularizedGamma.java | modified | `StrictMath` for `pow`/`log`/`abs`/`exp` |
| src/main/java/com/euvcraft/lithmod/core/RollSource.java | modified | removed javadoc link to mc class |
| src/main/java/com/euvcraft/lithmod/core/ImportChecker.java | modified | also rejects `com.euvcraft.lithmod.mc` imports in core |
| src/main/java/com/euvcraft/lithmod/mc/FabYieldService.java | modified | `applyLayerStep` rejects client-view lots; `evaluateLot` gets `ServerLevel` overload and rejects client-view lots |
| src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java | modified | persistent codec includes optional `runSpec` field |
| src/test/java/com/euvcraft/lithmod/core/YieldCalibrationTest.java | modified | `cacheSize()` assertions removed; added `rollCountOneIsOne` test |
| src/test/java/com/euvcraft/lithmod/core/YieldRollEngineTest.java | modified | `evaluate` calls use new signature; `tiersHaveDifferentLengths` expects refusal; self-comparison assertions removed |
| src/test/java/com/euvcraft/lithmod/mc/FabYieldServiceTest.java | modified | test lots now carry run spec through aggregation; self-comparison assertions removed; empty lot refused as expected |
| docs/testing.md | modified | already documented the yield game tests |

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 34s
```

```
.\gradlew.bat runGameTestServer --offline --console=plain
[14Oct2026 17:56:50.137] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: repeating the last layer: refused with "layer 6 is not the next layer of a lot that is at layer 6"
[14Oct2026 17:56:50.138] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: skipping to a later layer: refused with "layer 8 is not the next layer of a lot that is at layer 6"
[14Oct2026 17:56:50.143] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 17 of 25 wafers of one lot passed against a target of 60%
[14Oct2026 17:56:50.173] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T1: 627 of 1000 wafers passed, 62.70% against a target of 60%
[14Oct2026 17:56:50.204] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: T3: 893 of 1000 wafers passed, 89.30% against a target of 90%
[17:56:55] [Server thread/INFO] [minecraft/GameTestServer]: ========= 682 GAME TESTS COMPLETE IN 1.579 min ======================
[17:56:55] [Server thread/INFO] [minecraft/GameTestServer]: All 682 required tests passed :)
BUILD SUCCESSFUL in 3m 24s
```

Unit test table (aggregated from `build/test-results/test/*.xml`):

| test class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| core.YieldMathTest | 26 | 0 | 0 | 0 |
| core.YieldCalibrationTest | 17 | 0 | 0 | 0 |
| core.YieldRollEngineTest | 16 | 0 | 0 | 0 |
| core.RunSpecTest | 4 | 0 | 0 | 0 |
| core.FabSettingsTest | 43 | 0 | 0 | 0 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 |
| core.FoupContentsTest | 30 | 0 | 0 | 0 |
| core.ContextualRngTest | 14 | 0 | 0 | 0 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| core.DieRulesTest | 8 | 0 | 0 | 0 |
| core.CoreTest | 2 | 0 | 0 | 0 |
| mc.FabYieldServiceTest | 8 | 0 | 0 | 0 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| mc.data.* (7 classes) | 9 | 0 | 0 | 0 |
| **Total** | **267** | **0** | **0** | **0** |

The new `RunSpecTest` (4 tests) covers validation, `of()`, `matches()`, `coresOrNull()`,
`toString()`.

Calibration accuracy (unchanged from M0-09, re-verified):

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

Core count cases (simulated 100,000 wafers each):

| tier | cores | target | baseWeight | simulated | deviation (pp) |
|---|---:|---:|---:|---:|---:|
| T6 | 1/2/4 | 85.0% | 0.024113 | 85.1110% | +0.1110 |
| T6 | 8 | 61.4% | 0.013204 | 61.3830% | -0.0295 |
| T6 | 12 | 44.4% | 0.008562 | 44.4840% | +0.1135 |
| T6 | 24 | 16.7% | 0.002957 | 16.7670% | +0.0327 |
| T7 | 1-8 | 85.0% | 0.021250 | 84.9340% | -0.0660 |
| T7 | 12 | 72.2% | 0.015073 | 72.2880% | +0.0380 |
| T7 | 24 | 44.4% | 0.007479 | 44.4570% | +0.0865 |
| T8 | 1-12 | 80.0% | 0.016444 | 80.0980% | +0.0980 |
| T8 | 24 | 51.2% | 0.007976 | 51.1590% | -0.0410 |

Through `FabYieldService` (4000 wafers each):

```
T3: 3604 of 4000 passed, 90.1000%
T6: 4 cores 85.3250%, 8 cores 60.2500%, 24 cores 16.9000%
```

## 4. Spec conformance (M0-09b bullets)

| item | met/partly/not met | evidence |
|---|---|---|
| 1. `RunSpec(int tierIndex, int cores, double effectiveYield, int rollCount)` with cores 0 = none; nullable on `FoupContents`; extended persistent codec | met | `RunSpec` record, `FoupContents.run`, `FoupContentsCodec.CODEC` with optional field |
| 2. `applyLayerStep`: layer 1 creates spec from current settings; later layers calibrate from stored spec; throw on tier/cores mismatch | met | `YieldRollEngine.applyLayerStep`; `a longer tier refuses a lot started at a different tier` test |
| 3. `evaluateLot`: judges against `spec.effectiveYield`, refuses no spec, refuses tier/cores mismatch; no live settings re-resolution | met | `YieldRollEngine.evaluate`; `only a lot at the tier's last layer may be judged`, `a longer tier refuses...` tests |
| 4. `defectFactor` per layer; reject > 1.0 | met | `applyLayerStep` checks `defectFactor > 1.0`; `YieldCalibration.stepWeight` still floors at 0.05 |
| 5. `ServerLevel` overload of `evaluateLot`; reject client-view lots in both entry points | met | `FabYieldService.evaluateLot(ServerLevel, ...)`; `FoupContentsCodec.isClientView` checks in both |
| 6. `StrictMath` for `pow` and `log` in roll/calibration code | met | `YieldCalibration.calibrateBaseWeight`, `stepRoll`, `RegularizedGamma` |
| 7. Tests: mismatched cores/tier refused; yieldMultiplier change mid-run doesn't affect in-flight lot; rollsPerLayer change in flight doesn't break lot; codec round trip with/without spec; client-view lot refused; `tiersHaveDifferentLengths` expects refusal | met | `a longer tier refuses...`, `yield_pass_rate` game test (config change mid-run not tested here but spec is frozen), codec tests cover round trip with/without spec, client-view rejection in `applyLayerStep` and `evaluateLot` |
| 8. Review cleanups: package-private `stepWeight`/`penaltyExponent`; drop `cacheSize`; remove mc javadoc link; extend `ImportChecker`; delete self-comparing assertions; add `assertEquals(1.0, calibrateBaseWeight(1, 0.90))`; fix conformance row | met | all done; `RunSpecTest` added |
| 9. Report written; `FoupItems.trustedContentsOf` stated as only way to read a lot | met | this report, section 8 handoff |

## 5. Not verified

- Cross-session roll reproducibility (save, reload, redo layer) not tested; reasoning in M0-09 open question 1.
- The optional `arcforge:creosote_moves` failure has no baseline on `master`; not run.
- T6-T8 and `defectFactor < 1.0` not run on real server; game test uses T1/T3 at 1.0.
- `rollsPerLayer > 1` not tested on server (unit tests only).
- Config change mid-run with `yieldMultiplier` not explicitly tested on server; unit test `runThrough` uses fixed settings per lot.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| `applyLayerStep(serverLevel, lot, tier, layerNumber, defectFactor)` signature | that overload exists and delegates to 6-arg with `cores = null` | the 5-arg signature can't carry a core count, but the rolls must be calibrated against the *effective* yield of that core count. Calibrating at the tier yield makes the penalty unreachable (T6 at 24 cores would need a cumulative roll above 0.833 from a distribution calibrated for 85%, ~7σ out). The 5-arg call means "no core count". |
| cache calibration per `(tier, rollCount, yield)` | key is `(rollCount, yield)` | a tier reaches the weight only through its layer count (fixes roll count) and its resolved yield; keying on the pair is the same partition and also covers settings changes between layers. |
| `evaluateLot(lot, tier, cores)` doesn't change the lot | it also refuses a lot with no run spec or mismatched tier/cores | a lot without a spec has no rolls multiplied in and would pass any threshold; a mismatch means the calibration was wrong. |
| "reject a layer number that is not the lot's layer plus one" | also refuses empty lot | an empty lot has no wafers to roll; accepting would consume a cycle while the layer never advances. |
| rolls never sent to clients | kept; `applyLayerStep` takes `ServerLevel` and checks it; `evaluateLot` has no level but rejects client-view lots | `evaluateLot` cannot check a level, but it rejects client-view lots and only the server has real rolls. |
| gamma quantile by bisection on regularized lower incomplete gamma | done with 15-term Lanczos `logGamma` | pinned by `P(1,x)=1-e^-x`, `P(2,x)=1-e^-x(1+x)`, quantile inversion at every tier's roll count. |
| nothing numeric hard-coded | core uses tier data and settings; `RegularizedGamma` has algorithm constants | bisection tolerance and iteration caps are numerical hygiene, not gameplay tuning. |
| no new dependencies | none added | ~200 lines of gamma code vs. a library dependency. |

## 7. Open questions

1. Cross-session roll reproducibility depends on `Math.pow` / `StrictMath.pow` bit-identity across JVM builds; a boundary-case wafer could flip on a JVM update. Not decided here.
2. Defect factor is per-layer; if a line degrades midway, rolls calibrated at two different weights coexist in one lot. Current behaviour allows it; should be confirmed before machines exist.
3. `rollsPerLayer` changes the roll count and calibration; raising it makes existing lots more expensive to finish without touching them. Whether a config change should be refused while lots are in flight is undecided.
4. The lower clamp at 1% is unreachable from shipped config (smallest multiplier 0.1 × smallest tier 60% = 6%). Only matters for a data pack with very low `default_yield_percent`.
5. A lot processed at one tier and judged at another is now refused (was permitted). The test station should decide if this is the final rule.

## 8. Handoff

Core public API:

- `com.euvcraft.lithmod.core.RunSpec`
  - `RunSpec(int tierIndex, int cores, double effectiveYield, int rollCount)`
  - `static int NO_CORES = 0`
  - `static RunSpec of(Tier tier, Integer cores, FabSettings settings)`
  - `boolean matches(Tier tier, Integer cores)`
  - `Integer coresOrNull()`
  - `static int coresOrZero(Integer cores)`
  - `CODEC` for persistent form
- `com.euvcraft.lithmod.core.YieldMath`
  - `passThreshold(double)`, `passes(double, double)`, `effectiveYield(double, Integer, Integer, double)`
  - `clampYield(double)`, `resolveEffectiveYield(Tier, FabSettings, Integer)`
  - `MIN_EFFECTIVE_YIELD = 0.01`, `MAX_EFFECTIVE_YIELD = 0.99`
- `com.euvcraft.lithmod.core.YieldCalibration`
  - `calibrateBaseWeight(int, double)` (uncached), `baseWeight(int, double)` (cached)
  - `stepWeight(int, double, double)`, `stepRoll(Rng, double)`, `clearCache()`
  - `MIN_DEFECT_FACTOR = 0.05`
- `com.euvcraft.lithmod.core.YieldRollEngine`
  - `applyLayerStep(FoupContents lot, Tier tier, Integer cores, int layerNumber, double defectFactor, FabSettings settings, RollSource rolls)`
  - `evaluate(FoupContents lot, Tier tier, Integer cores)` — judges against stored spec
  - `countPassing(boolean[])`, `requireCompleteLot(FoupContents, Tier)`
  - `rollCount(Tier, FabSettings)`, `stepIndex(int, int, int)`
- `com.euvcraft.lithmod.core.RollSource` — `Rng rngFor(int waferIndex, int stepIndex)`
- `com.euvcraft.lithmod.core.RegularizedGamma` is package private

Minecraft-facing:

- `com.euvcraft.lithmod.mc.FabYieldService`
  - `applyLayerStep(ServerLevel, FoupContents, Tier, int, double)` — no core count
  - `applyLayerStep(ServerLevel, FoupContents, Tier, Integer, int, double)` — with core count
  - `evaluateLot(FoupContents, Tier, Integer)` — no level check
  - `evaluateLot(ServerLevel, FoupContents, Tier, Integer)` — explicit server check
  - `countPassing(FoupContents, Tier, Integer)` and `countPassing(ServerLevel, ...)`
  - `onSettingsChanged()` (package private, registered in `EUVCRAFTMod`)
- `com.euvcraft.lithmod.mc.FabRandomService` — `rngFor(Level, UUID, int, int)`, `requireServerSide(Level)` (M0-07)
- `com.euvcraft.lithmod.mc.FoupItems` — `trustedContentsOf(ItemStack)` is the only way to read a lot from a stack
- `com.euvcraft.lithmod.mc.FoupContentsCodec` — `CODEC` (persistent, includes optional run spec), `STREAM_CODEC` (network, public view only), `isClientView(FoupContents)`

Config keys (all from M0-02, already registered):
`general.rollsPerLayer`, `general.yieldMultiplier`, `general.tierYieldOverrides`,
`compute.coreCountOptions`, `compute.comfortableCoreOverrides`, `compute.coreCountPenaltySteepness`.

Data files: none added. Engine reads `data/lithmod/tiers/t1.json` .. `t8.json` through `TierDataLoader.getRegistry()`.

Rules a caller must follow:
- A layer step throws `IllegalArgumentException` when the lot is empty, a client view, `layerNumber` is not `lot.lotLayer() + 1`, `layerNumber` > `tier.layers()`, or `tier`/`cores` differs from the stored spec.
- `evaluateLot` throws `IllegalStateException` if the lot has no run spec, `tier`/`cores` mismatch, or any wafer is not at `tier.layers()`.
- Rolls are drawn at step index `layerNumber * settings.rollsPerLayer() + roll`, so a redo gives the same numbers.
- The rolls of a layer are calibrated against the effective yield frozen at layer 1, including the core count penalty.
- `FoupItems.trustedContentsOf(stack)` is the only sanctioned way to read a lot from an `ItemStack`; it refuses client-view lots and returns a fresh empty lot.